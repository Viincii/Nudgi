package com.vincentmignot.nudgi.core.nudge

import com.vincentmignot.nudgi.core.mascot.MascotMood

/**
 * Nudgi's expression in an intervention (decision 0021), recorded by [id]. It is chosen to help
 * the user stop, not to reflect the day: that is the home mood's job.
 */
enum class CoachExpression(
    val id: String,
    val mood: MascotMood,
) {
    Encouraging("happy", MascotMood.Happy),
    Neutral("neutral", MascotMood.Neutral),
    Worried("worried", MascotMood.Worried),
    ;

    companion object {
        fun fromId(id: String?): CoachExpression? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The expression for an intervention, drawn uniformly among [NudgeConfig.coachExpressions] from
 * [draw], a uniform draw in `[0, 1)`, until the bandit chooses it.
 */
fun drawExpression(
    config: NudgeConfig,
    draw: Double,
): CoachExpression {
    val expressions = config.coachExpressions
    return expressions[(draw * expressions.size).toInt().coerceIn(0, expressions.lastIndex)]
}

fun expressionProbability(config: NudgeConfig): Double = 1.0 / config.coachExpressions.size
