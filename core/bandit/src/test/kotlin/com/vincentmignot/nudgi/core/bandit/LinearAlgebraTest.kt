package com.vincentmignot.nudgi.core.bandit

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class LinearAlgebraTest {
    @Test
    fun `solves a symmetric positive definite system through its Cholesky factor`() {
        val a = arrayOf(doubleArrayOf(4.0, 2.0, 0.6), doubleArrayOf(2.0, 5.0, 1.0), doubleArrayOf(0.6, 1.0, 3.0))
        val expected = doubleArrayOf(1.0, -2.0, 0.5)
        val b = DoubleArray(3) { i -> (0 until 3).sumOf { j -> a[i][j] * expected[j] } }

        val l = cholesky(a)
        val x = backSubstituteTransposed(l, forwardSubstitute(l, b))

        assertArrayEquals(expected, x, 1e-9)
        for (i in 0 until 3) {
            for (j in 0 until 3) {
                val product = (0 until 3).sumOf { k -> l[i][k] * l[j][k] }
                assertArrayEquals(doubleArrayOf(a[i][j]), doubleArrayOf(product), 1e-9)
            }
        }
    }
}
