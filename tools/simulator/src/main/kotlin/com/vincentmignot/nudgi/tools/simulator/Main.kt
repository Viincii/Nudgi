package com.vincentmignot.nudgi.tools.simulator

import com.vincentmignot.nudgi.core.bandit.LinearThompsonSampling
import java.io.File
import java.util.Locale
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.system.exitProcess

private val USAGE =
    """
    |Runs the shadow bandit against a simulated user (decision 0022).
    |
    |Usage: ./gradlew :tools:simulator:run --args="<scenario> [--export path/to/nudgi-export.zip] [--seeds N] [--out dir]"
    |
    |Scenarios:
    |${Scenario.entries.joinToString("\n") { "  ${it.id.padEnd(16)} ${it.description}" }}
    |
    |With an export, decisions are resampled from it and the calibrated scenarios fitted on it;
    |without one, decisions are invented and the calibration assumed.
    """.trimMargin()

/** The share of seeds where the bandit must beat the rules before it goes live (decision 0022). */
private const val GO_LIVE_SHARE = 0.8

private val SHADOW_CHECKPOINTS = listOf(50, 100, 200, 300, 400, 600, 800, 1000, 1500, 2000, 3000)
private val IPS_CHECKPOINTS = listOf(50, 100, 200, 300, 500, 1000)
private const val ONLINE_DECISIONS = 1500
private const val EXPORT_DECISIONS = 500

fun main(args: Array<String>) {
    val scenario = args.firstOrNull()?.let(Scenario::fromId) ?: fail("Unknown or missing scenario")
    val options = args.drop(1).chunked(2).associate { it.first() to it.getOrNull(1) }
    val seeds = options["--seeds"]?.toIntOrNull() ?: 30
    val exportPath = options["--export"]?.let(::File)
    val out = File(options["--out"] ?: "tools/simulator/build/simulation/${scenario.id}")

    val aftermath = Aftermath()
    val exported = exportPath?.let { readExport(it, readExcludedIds(File("tools/excluded_decisions.json"))) }
    val calibration = exported?.let { Calibration.fit(it, aftermath) } ?: Calibration.DEFAULT
    val source = exported?.let { ResampledDecisions(it.map(ExportedDecision::decision)) } ?: SyntheticDecisions
    val world = World(scenario.user(calibration, aftermath), source)

    println("Scenario ${scenario.id}: ${scenario.description}")
    if (exported == null) {
        println("Decisions: invented (no export given); calibration assumed")
    } else {
        println("Decisions: resampled from ${exported.size} real ones in $exportPath")
    }
    printCalibration(calibration)

    val shadow = shadowStudy(world, seeds, SHADOW_CHECKPOINTS)
    printShadow(shadow)
    val online = onlineStudy(world, seeds, ONLINE_DECISIONS)
    printOnline(online)
    val ips = ipsStudy(world, seeds, IPS_CHECKPOINTS)
    printIps(ips)

    out.mkdirs()
    writeCsv(File(out, "shadow.csv"), "seed,decisions,bandit,rules,best", shadow) {
        listOf(it.seed, it.decisions, it.bandit, it.rules, it.best)
    }
    writeCsv(File(out, "online.csv"), "seed,decisions,bandit_regret,rules_regret", online) {
        listOf(it.seed, it.decisions, it.banditRegret, it.rulesRegret)
    }
    writeCsv(File(out, "ips.csv"), "seed,decisions,true_value,ips,self_normalized,rules_actual,matches", ips) {
        listOf(it.seed, it.decisions, it.trueValue, it.ips, it.selfNormalized, it.rulesActual, it.matches)
    }

    // One synthetic export, with the app's own propensity draws, for bandit_report.py to read.
    val logged = runShadow(world, LinearThompsonSampling(), EXPORT_DECISIONS, Random(1))
    val exportFile = File(out, "synthetic-export.zip")
    writeSyntheticExport(exportFile, logged, world.rules.holdoutProbability, world.rules.escalationProbability)
    val estimate = ipsEstimates(world, logged, seed = 1)
    println("\nSynthetic export: $exportFile ($EXPORT_DECISIONS decisions)")
    println("  python3 tools/bandit_report.py $exportFile")
    println(
        "  should report IPS ${f(estimate.ips)}, self-normalized ${f(estimate.selfNormalized)}, " +
            "rules ${f(estimate.rulesActual)}; the shadow's true value is ${f(estimate.trueValue)}",
    )
    println("\nCSV files in $out")
}

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

private fun printShadow(points: List<ShadowPoint>) {
    println("\nShadow: the bandit trained on the rules' decisions, judged as if it went live")
    println("  decisions  days   bandit  rules   best   bandit ahead")
    val shares =
        points.groupBy { it.decisions }.toSortedMap().map { (decisions, group) ->
            val share = group.count { it.bandit > it.rules }.toDouble() / group.size
            println(
                "  ${decisions.toString().padStart(9)} ${(decisions / DECISIONS_PER_DAY).toString().padStart(5)}  " +
                    "${f(median(group.map { it.bandit }))}   ${f(median(group.map { it.rules }))}  " +
                    "${f(median(group.map { it.best }))}   ${pct(share)} of seeds",
            )
            decisions to share
        }
    // The first checkpoint from which the bandit stays ahead in enough seeds.
    val goLive = shares.indices.firstOrNull { i -> shares.drop(i).all { it.second >= GO_LIVE_SHARE } }
    if (goLive == null) {
        println("  Never ahead in ${pct(GO_LIVE_SHARE)} of the seeds up to ${shares.last().first} decisions.")
    } else {
        val decisions = shares[goLive].first
        println(
            "  Ahead in ${pct(GO_LIVE_SHARE)} of the seeds from $decisions decisions on: " +
                "about ${decisions / DECISIONS_PER_DAY} days at $DECISIONS_PER_DAY a day.",
        )
    }
}

private fun printOnline(points: List<OnlinePoint>) {
    println("\nOnline: the bandit live from the start, regret per decision (lower is better)")
    println("  decisions  bandit   rules")
    points.groupBy { it.decisions }.toSortedMap().forEach { (decisions, group) ->
        println(
            "  ${decisions.toString().padStart(9)}  ${f(median(group.map { it.banditRegret }))}   " +
                f(median(group.map { it.rulesRegret })),
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

private fun pct(share: Double) = "${(share * 100).toInt()}%"

private fun fail(message: String): Nothing {
    System.err.println("$message\n\n$USAGE")
    exitProcess(1)
}
