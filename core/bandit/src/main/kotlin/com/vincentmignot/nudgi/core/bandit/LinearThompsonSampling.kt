package com.vincentmignot.nudgi.core.bandit

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/** One past decision the bandit learns from: the action actually taken and its benefit. */
data class Observation(
    val context: BanditContext,
    val action: BanditAction,
    val benefit: Double,
)

data class BanditChoice(
    val action: BanditAction,
    /** Share of posterior draws that chose [action]: its probability under this policy. */
    val propensity: Double,
    val trainedOn: Int,
)

/**
 * Linear Thompson sampling, one Bayesian linear regression per action (decision 0020).
 *
 * Each action's benefit is modelled as `wᵀx + ε`, with a Gaussian prior on `w` centred on
 * [priorBenefit] for every context and a Gaussian noise of standard deviation [noiseSd]. To
 * choose, one `w` is drawn per action from its posterior, and the action whose
 * `weight × wᵀx − cost` is highest wins. The draw is what explores: an action with few observations
 * has a wide posterior, so its draws are spread out and it sometimes wins.
 *
 * The model is refitted from [Observation]s at every call rather than kept: at a few hundred
 * observations that takes milliseconds, and it cannot drift from the data.
 */
class LinearThompsonSampling(
    private val priorBenefit: Double = 0.5,
    /** Precision of the prior on each weight: 4 is a standard deviation of 0.5. */
    private val priorPrecision: Double = 4.0,
    private val noiseSd: Double = 0.3,
    /** Draws used to estimate the propensity of the chosen action. */
    private val propensityDraws: Int = 200,
) {
    private class Posterior(
        val mean: DoubleArray,
        /** Cholesky factor of the posterior precision. */
        val precisionFactor: Array<DoubleArray>,
    ) {
        fun draw(random: Random): DoubleArray {
            // With precision A = L Lᵀ, L⁻ᵀz has covariance A⁻¹ for a standard normal z.
            val z = DoubleArray(mean.size) { random.nextGaussian() }
            val offset = backSubstituteTransposed(precisionFactor, z)
            return DoubleArray(mean.size) { mean[it] + offset[it] }
        }
    }

    fun choose(
        context: BanditContext,
        allowed: Set<BanditAction>,
        observations: List<Observation>,
        random: Random,
    ): BanditChoice {
        require(allowed.isNotEmpty()) { "No action to choose from" }
        val x = features(context)
        val posteriors = allowed.associateWith { action -> fit(observations.filter { it.action == action }) }

        fun drawBest(): BanditAction =
            allowed.maxBy { action ->
                RewardV1.weight(context.localHour) * dot(posteriors.getValue(action).draw(random), x) -
                    RewardV1.cost(action)
            }

        val chosen = drawBest()
        val wins = 1 + (1 until propensityDraws).count { drawBest() == chosen }
        return BanditChoice(chosen, wins.toDouble() / propensityDraws, observations.size)
    }

    private fun fit(observations: List<Observation>): Posterior {
        val n = FEATURE_COUNT
        val noisePrecision = 1.0 / (noiseSd * noiseSd)
        // Precision: λI + XᵀX / σ². Right-hand side: λ·w₀ + Xᵀy / σ², with w₀ putting the prior
        // benefit on the constant feature and zero elsewhere.
        val precision = Array(n) { i -> DoubleArray(n) { j -> if (i == j) priorPrecision else 0.0 } }
        val rhs = DoubleArray(n)
        rhs[0] = priorPrecision * priorBenefit
        for (observation in observations) {
            val x = features(observation.context)
            for (i in 0 until n) {
                rhs[i] += noisePrecision * x[i] * observation.benefit
                for (j in 0 until n) precision[i][j] += noisePrecision * x[i] * x[j]
            }
        }
        val factor = cholesky(precision)
        val mean = backSubstituteTransposed(factor, forwardSubstitute(factor, rhs))
        return Posterior(mean, factor)
    }
}

/** A standard normal draw, by the Box-Muller transform. */
internal fun Random.nextGaussian(): Double {
    var u = nextDouble()
    while (u == 0.0) u = nextDouble()
    return sqrt(-2.0 * ln(u)) * cos(2.0 * PI * nextDouble())
}
