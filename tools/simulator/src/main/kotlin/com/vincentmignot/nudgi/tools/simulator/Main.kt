package com.vincentmignot.nudgi.tools.simulator

import com.vincentmignot.nudgi.core.bandit.BanditAction
import java.io.File
import java.util.Locale
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.system.exitProcess

private val USAGE =
    """
    |Runs the shadow bandit against a simulated user (decision 0022).
    |
    |Usage: ./gradlew :tools:simulator:run --args="<scenario> [options]"
    |
    |Options:
    |  --export path    an export to resample decisions from and calibrate on
    |  --rules v4|v5    the rules collecting the data (default v4); with exploration the bandit
    |                   only chooses among the actions the rules can take
    |  --holdout p, --neighbour p, --forced p
    |                   override the rules' holdout, neighbour-level and forced-close probabilities
    |  --prior-precision p, --prior-benefit b, --noise-sd s
    |                   override the bandit's prior and noise (default 4, 0.5, 0.3, as in the app)
    |  --grid           compare exploration rates instead of running one configuration
    |  --prior-grid     compare prior precisions under the chosen rules instead
    |  --seeds N        random seeds per study (default 30)
    |  --out dir        where the CSV files and the synthetic export go
    |
    |Scenarios:
    |${Scenario.entries.joinToString("\n") { "  ${it.id.padEnd(16)} ${it.description}" }}
    |
    |With an export, decisions are resampled from it, the calibrated scenarios fitted on it and the
    |decisions per day measured on it; without one, decisions are invented and the rest assumed.
    """.trimMargin()

/** The share of seeds where the bandit must beat the ladder before it goes live (decision 0022). */
private const val GO_LIVE_SHARE = 0.8

private val SHADOW_CHECKPOINTS = listOf(50, 100, 200, 300, 400, 600, 800, 1000, 1500, 2000, 3000)
private val IPS_CHECKPOINTS = listOf(50, 100, 200, 300, 500, 1000)
private const val ONLINE_DECISIONS = 1500
private const val EXPORT_DECISIONS = 500

private val GRID_HOLDOUTS = listOf(0.2, 0.3, 0.5)
private val GRID_NEIGHBOURS = listOf(0.1, 0.2, 0.3)
private val GRID_FORCED_CLOSES = listOf(0.02, 0.05)

private val PRIOR_PRECISIONS = listOf(4.0, 16.0, 64.0, 256.0, 1024.0, 4096.0)

/** Where the grid reads the report's estimate: a few weeks of real decisions. */
private const val GRID_IPS_DECISIONS = 300

fun main(args: Array<String>) {
    val scenario = args.firstOrNull()?.let(Scenario::fromId) ?: fail("Unknown or missing scenario")
    val rest = args.drop(1)
    val flags = setOf("--grid", "--prior-grid")
    val grid = "--grid" in rest
    val priorGrid = "--prior-grid" in rest
    val options = rest.filter { it !in flags }.chunked(2).associate { it.first() to it.getOrNull(1) }
    val seeds = options["--seeds"]?.toIntOrNull() ?: 30
    val exportPath = options["--export"]?.let(::File)
    val baseRules =
        when (options["--rules"] ?: "v4") {
            "v4" -> RulesPolicy.V4
            "v5" -> RulesPolicy.V5
            else -> fail("Unknown rules: ${options["--rules"]}")
        }
    val rules =
        baseRules.copy(
            holdoutProbability = options["--holdout"]?.toDouble() ?: baseRules.holdoutProbability,
            neighbourProbability = options["--neighbour"]?.toDouble() ?: baseRules.neighbourProbability,
            forcedCloseProbability = options["--forced"]?.toDouble() ?: baseRules.forcedCloseProbability,
        )
    val prior =
        BanditPrior().let { default ->
            BanditPrior(
                priorBenefit = options["--prior-benefit"]?.toDouble() ?: default.priorBenefit,
                priorPrecision = options["--prior-precision"]?.toDouble() ?: default.priorPrecision,
                noiseSd = options["--noise-sd"]?.toDouble() ?: default.noiseSd,
            )
        }
    val out = File(options["--out"] ?: "tools/simulator/build/simulation/${scenario.id}")

    val aftermath = Aftermath()
    val exported = exportPath?.let { readExport(it, readExcludedIds(File("tools/excluded_decisions.json"))) }
    val calibration = exported?.let { Calibration.fit(it, aftermath) } ?: Calibration.DEFAULT
    val source = exported?.let { ResampledDecisions(it.map(ExportedDecision::decision)) } ?: SyntheticDecisions
    val perDay = exported?.let(::decisionsPerDay) ?: DECISIONS_PER_DAY.toDouble()
    val user = scenario.user(calibration, aftermath)

    println("Scenario ${scenario.id}: ${scenario.description}")
    if (exported == null) {
        println("Decisions: invented (no export given), ${f1(perDay)} a day assumed; calibration assumed")
    } else {
        println("Decisions: resampled from ${exported.size} real ones in $exportPath, ${f1(perDay)} a day")
    }
    printCalibration(calibration)

    if (grid) {
        runGrid(user, source, perDay, seeds, prior)
        return
    }

    val world = World(user, source, rules, restrictBandit = rules.explores)
    println("Rules: ${describe(rules)}" + if (world.restrictBandit) "; bandit limited to the rules' actions" else "")

    if (priorGrid) {
        runPriorGrid(world, perDay, seeds, prior)
        return
    }
    println("Bandit: ${describe(prior)}")

    val shadow = shadowStudy(world, seeds, SHADOW_CHECKPOINTS, prior = prior)
    printShadow(shadow, perDay)
    val online = onlineStudy(world, seeds, ONLINE_DECISIONS, prior = prior)
    printOnline(online)
    val ips = ipsStudy(world, seeds, IPS_CHECKPOINTS, prior = prior)
    printIps(ips)

    out.mkdirs()
    writeCsv(File(out, "shadow.csv"), "seed,decisions,bandit,rules,ladder,best", shadow) {
        listOf(it.seed, it.decisions, it.bandit, it.rules, it.ladder, it.best)
    }
    writeCsv(File(out, "online.csv"), "seed,decisions,bandit_regret,ladder_regret", online) {
        listOf(it.seed, it.decisions, it.banditRegret, it.ladderRegret)
    }
    writeCsv(
        File(out, "ips.csv"),
        "seed,decisions,true_value,ips,self_normalized,rules_actual,matches,supported",
        ips,
    ) {
        listOf(it.seed, it.decisions, it.trueValue, it.ips, it.selfNormalized, it.rulesActual, it.matches, it.supported)
    }

    if (rules.explores) {
        // bandit_report.py only knows the propensities of rules_v4; the export would mislead it.
        println("\nNo synthetic export: the report cannot read the exploration of these rules yet.")
    } else {
        // One synthetic export, with the app's own propensity draws, for bandit_report.py to read.
        val logged = runShadow(world, prior.bandit(propensityDraws = 200), EXPORT_DECISIONS, Random(1))
        val exportFile = File(out, "synthetic-export.zip")
        writeSyntheticExport(exportFile, logged, world.rules.holdoutProbability, world.rules.escalationProbability)
        val estimate = ipsEstimates(world, logged, seed = 1)
        println("\nSynthetic export: $exportFile ($EXPORT_DECISIONS decisions)")
        println("  python3 tools/bandit_report.py $exportFile")
        println(
            "  should report IPS ${f(estimate.ips)}, self-normalized ${f(estimate.selfNormalized)}, " +
                "rules ${f(estimate.rulesActual)}; the shadow's true value is ${f(estimate.trueValue)}",
        )
    }
    println("\nCSV files in $out")
}

/**
 * Every combination of exploration rates, against the rules of today with an unrestricted bandit:
 * how soon the shadow goes live, how good the report's estimate is after a few weeks, and what the
 * exploration costs the user in forced closes.
 */
private fun runGrid(
    user: SimulatedUser,
    source: DecisionSource,
    perDay: Double,
    seeds: Int,
    prior: BanditPrior,
) {
    val configurations =
        listOf(RulesPolicy.V4) +
            GRID_HOLDOUTS.flatMap { holdout ->
                GRID_NEIGHBOURS.flatMap { neighbour ->
                    GRID_FORCED_CLOSES.map { forced ->
                        RulesPolicy(
                            holdoutProbability = holdout,
                            neighbourProbability = neighbour,
                            forcedCloseProbability = forced,
                        )
                    }
                }
            }
    println("\nGrid over $seeds seeds; IPS read at $GRID_IPS_DECISIONS decisions; ${describe(prior)}")
    println(
        "  holdout  neighbour  forced   go live  days   gain@1000  supported   IPS bias  IPS sd  " +
            "SNIPS bias  SNIPS sd   forced/week",
    )
    for (rules in configurations) {
        val world = World(user, source, rules, restrictBandit = rules.explores)
        val shadow = shadowStudy(world, seeds, SHADOW_CHECKPOINTS, prior = prior)
        val goLive = goLiveDecisions(shadow)
        val gain = median(shadow.filter { it.decisions == 1000 }.map { it.bandit - it.ladder })
        val ips = ipsStudy(world, seeds, listOf(GRID_IPS_DECISIONS), prior = prior)
        val ipsErrors = ips.map { it.ips - it.trueValue }
        val snipsErrors = ips.map { it.selfNormalized - it.trueValue }.filter { !it.isNaN() }
        val supported = median(ips.map { it.supported.toDouble() / it.decisions })
        val random = Random(0)
        val forcedShare =
            List(5000) { source.next(random) }
                .map { rules.distribution(it)[BanditAction.ForcedClose] ?: 0.0 }
                .average()
        val columns =
            listOf(
                pct(rules.holdoutProbability).padStart(7),
                pct(rules.neighbourProbability).padStart(9),
                pct(rules.forcedCloseProbability).padStart(6),
                (goLive?.toString() ?: "never").padStart(8),
                (goLive?.let { (it / perDay).toInt().toString() } ?: "-").padStart(5),
                f(gain).padStart(10),
                pct(supported).padStart(10),
                f(ipsErrors.average()).padStart(9),
                f(sd(ipsErrors)).padStart(6),
                f(snipsErrors.average()).padStart(10),
                f(sd(snipsErrors)).padStart(8),
                f1(forcedShare * perDay * 7).padStart(12),
            )
        println("  " + columns.joinToString(" ") + if (!rules.explores) "   (rules_v4, bandit unrestricted)" else "")
    }
}

/**
 * The same rules with priors of growing precision: how soon the shadow gets ahead, and how well the
 * bandit does once live, at the end of the online study.
 */
private fun runPriorGrid(
    world: World,
    perDay: Double,
    seeds: Int,
    base: BanditPrior,
) {
    println("\nPrior grid over $seeds seeds; prior benefit ${f(base.priorBenefit)}, noise sd ${f(base.noiseSd)}")
    println("  precision  weight sd   go live  days   gain@1000   live regret  ladder regret")
    for (precision in PRIOR_PRECISIONS) {
        val prior = base.copy(priorPrecision = precision)
        val shadow = shadowStudy(world, seeds, SHADOW_CHECKPOINTS, prior = prior)
        val goLive = goLiveDecisions(shadow)
        val gain = median(shadow.filter { it.decisions == 1000 }.map { it.bandit - it.ladder })
        // The live bandit once settled: its last 300 decisions.
        val settled =
            onlineStudy(world, seeds, ONLINE_DECISIONS, prior = prior).filter {
                it.decisions >
                    ONLINE_DECISIONS - 300
            }
        val columns =
            listOf(
                f1(precision).padStart(9),
                f(1 / sqrt(precision)).padStart(9),
                (goLive?.toString() ?: "never").padStart(8),
                (goLive?.let { (it / perDay).toInt().toString() } ?: "-").padStart(5),
                f(gain).padStart(10),
                f(median(settled.map { it.banditRegret })).padStart(12),
                f(median(settled.map { it.ladderRegret })).padStart(13),
            )
        println("  " + columns.joinToString(" "))
    }
}

private fun describe(prior: BanditPrior) =
    "prior benefit ${f(prior.priorBenefit)}, prior precision ${f1(prior.priorPrecision)}, noise sd ${f(prior.noiseSd)}"

private fun describe(rules: RulesPolicy) =
    "holdout ${pct(rules.holdoutProbability)}, escalation ${pct(rules.escalationProbability)}, " +
        "neighbour level ${pct(rules.neighbourProbability)}, forced close ${pct(rules.forcedCloseProbability)}"

private fun printCalibration(calibration: Calibration) {
    fun cell(fitted: Calibration.Fitted) =
        "${f(fitted.stopProbability)} " + if (fitted.count > 0) "(fitted on ${fitted.count})" else "(assumed)"
    println(
        "Stop probability: nothing ${cell(calibration.nothingDay)} by day, ${cell(calibration.nothingNight)} at night",
    )
    println(
        "                  notification ${cell(calibration.notificationDay)} by day, " +
            "${cell(calibration.notificationNight)} at night",
    )
}

/** The first checkpoint from which the bandit stays ahead of the ladder in enough seeds, if any. */
private fun goLiveDecisions(points: List<ShadowPoint>): Int? {
    val shares =
        points.groupBy { it.decisions }.toSortedMap().map { (decisions, group) ->
            decisions to group.count { it.bandit > it.ladder }.toDouble() / group.size
        }
    val first = shares.indices.firstOrNull { i -> shares.drop(i).all { it.second >= GO_LIVE_SHARE } }
    return first?.let { shares[it].first }
}

private fun printShadow(
    points: List<ShadowPoint>,
    perDay: Double,
) {
    println("\nShadow: the bandit trained on the rules' decisions, judged as if it went live")
    println("  decisions  days   bandit  rules  ladder   best   ahead of the ladder")
    points.groupBy { it.decisions }.toSortedMap().forEach { (decisions, group) ->
        val share = group.count { it.bandit > it.ladder }.toDouble() / group.size
        val columns =
            listOf(
                decisions.toString().padStart(9),
                (decisions / perDay).toInt().toString().padStart(5),
                f(median(group.map { it.bandit })).padStart(7),
                f(median(group.map { it.rules })).padStart(6),
                f(median(group.map { it.ladder })).padStart(6),
                f(median(group.map { it.best })).padStart(6),
                "${pct(share)} of seeds".padStart(14),
            )
        println("  " + columns.joinToString(" "))
    }
    val goLive = goLiveDecisions(points)
    if (goLive == null) {
        println("  Never ahead in ${pct(GO_LIVE_SHARE)} of the seeds up to ${points.maxOf { it.decisions }} decisions.")
    } else {
        println(
            "  Ahead in ${pct(GO_LIVE_SHARE)} of the seeds from $goLive decisions on: " +
                "about ${(goLive / perDay).toInt()} days at ${f1(perDay)} a day.",
        )
    }
}

private fun printOnline(points: List<OnlinePoint>) {
    println("\nOnline: the bandit live from the start, regret per decision (lower is better)")
    println("  decisions  bandit  ladder")
    points.groupBy { it.decisions }.toSortedMap().forEach { (decisions, group) ->
        println(
            "  ${decisions.toString().padStart(9)}  ${f(median(group.map { it.banditRegret }))}   " +
                f(median(group.map { it.ladderRegret })),
        )
    }
}

private fun printIps(points: List<IpsPoint>) {
    println("\nThe report's estimate of the shadow against its true value, over the seeds")
    println("  decisions  supported  matches   IPS bias  IPS sd   SNIPS bias  SNIPS sd")
    points.groupBy { it.decisions }.toSortedMap().forEach { (decisions, group) ->
        val supported = median(group.map { it.supported.toDouble() / it.decisions })
        val matches = median(group.map { it.matches.toDouble() }).toInt()
        val ipsErrors = group.map { it.ips - it.trueValue }
        val snipsErrors = group.map { it.selfNormalized - it.trueValue }.filter { !it.isNaN() }
        val columns =
            listOf(
                decisions.toString().padStart(9),
                pct(supported).padStart(9),
                matches.toString().padStart(7),
                f(ipsErrors.average()).padStart(8),
                f(sd(ipsErrors)).padStart(6),
                f(snipsErrors.average()).padStart(10),
                f(sd(snipsErrors)).padStart(8),
            )
        println("  " + columns.joinToString("  "))
    }
}

private fun <T> writeCsv(
    file: File,
    header: String,
    rows: List<T>,
    values: (T) -> List<Any>,
) {
    file.printWriter().use { writer ->
        writer.println(header)
        rows.forEach { row ->
            writer.println(values(row).joinToString(",") { if (it is Double) f(it) else it.toString() })
        }
    }
}

private fun median(values: List<Double>): Double = values.sorted().let { (it[(it.size - 1) / 2] + it[it.size / 2]) / 2 }

private fun sd(values: List<Double>): Double {
    val mean = values.average()
    return sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1).coerceAtLeast(1))
}

private fun f(value: Double) = String.format(Locale.ROOT, "%.3f", value)

private fun f1(value: Double) = String.format(Locale.ROOT, "%.1f", value)

private fun pct(share: Double) = "${Math.round(share * 100)}%"

private fun fail(message: String): Nothing {
    System.err.println("$message\n\n$USAGE")
    exitProcess(1)
}
