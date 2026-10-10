package com.vincentmignot.nudgi.tools.simulator

import com.vincentmignot.nudgi.core.bandit.BanditAction
import com.vincentmignot.nudgi.core.bandit.BanditChoice
import com.vincentmignot.nudgi.core.bandit.LinearThompsonSampling
import com.vincentmignot.nudgi.core.bandit.Observation
import com.vincentmignot.nudgi.core.bandit.RewardV1
import kotlin.random.Random

/** Real decisions per day on the device (decision 0020), to turn decisions into days. */
const val DECISIONS_PER_DAY = 13

/** A simulated user, the decisions they meet and the rules that act on them. */
class World(
    val user: SimulatedUser,
    val source: DecisionSource,
    val rules: RulesPolicy = RulesPolicy.V4,
    /**
     * Whether the bandit only chooses among the actions the rules can take, so that every choice
     * it makes can be evaluated from the rules' data, and learned from.
     */
    val restrictBandit: Boolean = false,
) {
    fun allowed(decision: SimDecision): Set<BanditAction> =
        if (restrictBandit) decision.allowed intersect rules.support(decision) else decision.allowed

    fun reward(
        decision: SimDecision,
        action: BanditAction,
    ): Double = user.expectedReward(decision.context, action)

    fun bestReward(decision: SimDecision): Double = decision.allowed.maxOf { reward(decision, it) }

    fun rulesReward(decision: SimDecision): Double = expectedReward(rules, decision)

    fun ladderReward(decision: SimDecision): Double = expectedReward(RulesPolicy.LADDER, decision)

    private fun expectedReward(
        policy: RulesPolicy,
        decision: SimDecision,
    ): Double =
        policy.distribution(decision).entries.sumOf { (action, probability) -> probability * reward(decision, action) }
}

/** The bandit's prior and noise (decision 0020); the defaults are those the app ships. */
data class BanditPrior(
    val priorBenefit: Double = 0.5,
    val priorPrecision: Double = 4.0,
    val noiseSd: Double = 0.3,
) {
    fun bandit(propensityDraws: Int = 1) =
        LinearThompsonSampling(priorBenefit, priorPrecision, noiseSd, propensityDraws)
}

/** One decision as the app records it: the rules acted, the shadow chose, the user reacted. */
data class LoggedDecision(
    val decision: SimDecision,
    val rulesAction: BanditAction,
    /** Probability the rules had of taking [rulesAction]. */
    val propensity: Double,
    val outcome: Outcome,
    val benefit: Double,
    val shadow: BanditChoice,
) {
    val reward: Double get() = RewardV1.reward(rulesAction, decision.context.localHour, benefit)
}

/**
 * [count] decisions taken by the rules, each with the choice of a shadow bandit trained on every
 * decision before it, as on the device. Every earlier decision is learned from at once: on the
 * device one whose reward window is still open is not, a delay the simulation leaves out.
 */
fun runShadow(
    world: World,
    bandit: LinearThompsonSampling,
    count: Int,
    random: Random,
): List<LoggedDecision> {
    val history = mutableListOf<Observation>()
    return List(count) {
        val decision = world.source.next(random)
        val shadow = bandit.choose(decision.context, world.allowed(decision), history, random)
        val action = world.rules.sample(decision, random)
        val outcome = world.user.sample(decision.context, action, random)
        val benefit = world.user.benefit(decision.context, outcome)
        history += Observation(decision.context, action, benefit)
        LoggedDecision(decision, action, world.rules.distribution(decision).getValue(action), outcome, benefit, shadow)
    }
}

data class ShadowPoint(
    val seed: Int,
    val decisions: Int,
    /** Expected reward per decision of the bandit trained on [decisions] rules' decisions, were it live. */
    val bandit: Double,
    /** The rules collecting the data, exploration included. */
    val rules: Double,
    /** The ladder alone, which the bandit has to beat to go live: see [RulesPolicy.LADDER]. */
    val ladder: Double,
    val best: Double,
)

/**
 * When to go live: the bandit is trained, as a shadow, on decisions the rules took, then judged on
 * fresh decisions by the expected reward of its choices against the ladder's.
 */
fun shadowStudy(
    world: World,
    seeds: Int,
    checkpoints: List<Int>,
    evaluated: Int = 200,
    prior: BanditPrior = BanditPrior(),
): List<ShadowPoint> {
    val bandit = prior.bandit()
    return (1..seeds).flatMap { seed ->
        val random = Random(seed)
        val history = mutableListOf<Observation>()
        checkpoints.sorted().map { checkpoint ->
            while (history.size < checkpoint) {
                val decision = world.source.next(random)
                val action = world.rules.sample(decision, random)
                val outcome = world.user.sample(decision.context, action, random)
                history += Observation(decision.context, action, world.user.benefit(decision.context, outcome))
            }
            val fitted = bandit.fit(history)
            val judged = List(evaluated) { world.source.next(random) }
            ShadowPoint(
                seed = seed,
                decisions = checkpoint,
                bandit =
                    judged
                        .map {
                            world.reward(
                                it,
                                fitted.choose(it.context, world.allowed(it), random).action,
                            )
                        }.average(),
                rules = judged.map(world::rulesReward).average(),
                ladder = judged.map(world::ladderReward).average(),
                best = judged.map(world::bestReward).average(),
            )
        }
    }
}

data class OnlinePoint(
    val seed: Int,
    /** Decisions taken so far; the regrets are averaged over the [block][onlineStudy] ending here. */
    val decisions: Int,
    val banditRegret: Double,
    val ladderRegret: Double,
)

/** The bandit live from its first decision, learning only from its own choices. */
fun onlineStudy(
    world: World,
    seeds: Int,
    decisions: Int,
    block: Int = 100,
    prior: BanditPrior = BanditPrior(),
): List<OnlinePoint> {
    val bandit = prior.bandit()
    return (1..seeds).flatMap { seed ->
        val random = Random(seed)
        val history = mutableListOf<Observation>()
        val points = mutableListOf<OnlinePoint>()
        var banditRegret = 0.0
        var ladderRegret = 0.0
        for (step in 1..decisions) {
            val decision = world.source.next(random)
            val action = bandit.choose(decision.context, world.allowed(decision), history, random).action
            val outcome = world.user.sample(decision.context, action, random)
            history += Observation(decision.context, action, world.user.benefit(decision.context, outcome))
            val best = world.bestReward(decision)
            banditRegret += best - world.reward(decision, action)
            ladderRegret += best - world.ladderReward(decision)
            if (step % block == 0) {
                points += OnlinePoint(seed, step, banditRegret / block, ladderRegret / block)
                banditRegret = 0.0
                ladderRegret = 0.0
            }
        }
        points
    }
}

data class IpsPoint(
    val seed: Int,
    val decisions: Int,
    /** What the shadow's choices would really have scored, known only in a simulation. */
    val trueValue: Double,
    val ips: Double,
    val selfNormalized: Double,
    val rulesActual: Double,
    val matches: Int,
    /**
     * Decisions where the rules could have taken the shadow's action. IPS counts the others as 0:
     * it is unbiased only when every action the shadow chooses has a chance under the rules.
     */
    val supported: Int,
)

/**
 * The estimates of `tools/bandit_report.py`, computed as it does, against the true value of the
 * shadow's choices on the same decisions.
 */
fun ipsEstimates(
    world: World,
    logged: List<LoggedDecision>,
    seed: Int,
): IpsPoint {
    val matches = logged.filter { it.shadow.action == it.rulesAction }
    val weighted = matches.sumOf { it.reward / it.propensity }
    val weights = matches.sumOf { 1 / it.propensity }
    return IpsPoint(
        seed = seed,
        decisions = logged.size,
        trueValue = logged.map { world.reward(it.decision, it.shadow.action) }.average(),
        ips = weighted / logged.size,
        selfNormalized = if (weights > 0) weighted / weights else Double.NaN,
        rulesActual = logged.map { it.reward }.average(),
        matches = matches.size,
        supported = logged.count { world.rules.distribution(it.decision).containsKey(it.shadow.action) },
    )
}

fun ipsStudy(
    world: World,
    seeds: Int,
    checkpoints: List<Int>,
    prior: BanditPrior = BanditPrior(),
): List<IpsPoint> {
    val bandit = prior.bandit()
    return (1..seeds).flatMap { seed ->
        val logged = runShadow(world, bandit, checkpoints.max(), Random(seed))
        checkpoints.sorted().map { ipsEstimates(world, logged.take(it), seed) }
    }
}
