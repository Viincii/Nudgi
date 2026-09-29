package com.vincentmignot.nudgi.core.nudge

import org.junit.Assert.assertEquals
import org.junit.Test

class CoachExpressionTest {
    private val config = NudgeConfig()

    @Test
    fun `the draw splits evenly among the expressions`() {
        assertEquals(CoachExpression.Encouraging, drawExpression(config, 0.0))
        assertEquals(CoachExpression.Neutral, drawExpression(config, 0.4))
        assertEquals(CoachExpression.Worried, drawExpression(config, 0.99))
        assertEquals(1.0 / 3, expressionProbability(config), 1e-9)
    }

    @Test
    fun `a narrower set only draws among its own expressions`() {
        val narrow = config.copy(coachExpressions = listOf(CoachExpression.Worried))

        assertEquals(CoachExpression.Worried, drawExpression(narrow, 0.0))
        assertEquals(1.0, expressionProbability(narrow), 1e-9)
    }
}
