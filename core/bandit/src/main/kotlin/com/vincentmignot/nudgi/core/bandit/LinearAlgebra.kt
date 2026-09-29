package com.vincentmignot.nudgi.core.bandit

import kotlin.math.sqrt

// The little linear algebra a Bayesian linear regression needs, on dense row-major matrices. The
// matrices here are small (one row and column per feature) and symmetric positive definite, so a
// Cholesky factorization is both the fastest and the most stable way through them.

/** The lower-triangular `L` with `L Lᵀ = a`, for a symmetric positive definite [a]. */
internal fun cholesky(a: Array<DoubleArray>): Array<DoubleArray> {
    val n = a.size
    val l = Array(n) { DoubleArray(n) }
    for (i in 0 until n) {
        for (j in 0..i) {
            var sum = a[i][j]
            for (k in 0 until j) sum -= l[i][k] * l[j][k]
            if (i == j) {
                require(sum > 0.0) { "Matrix is not positive definite" }
                l[i][i] = sqrt(sum)
            } else {
                l[i][j] = sum / l[j][j]
            }
        }
    }
    return l
}

/** Solves `L y = b` for a lower-triangular [l]. */
internal fun forwardSubstitute(
    l: Array<DoubleArray>,
    b: DoubleArray,
): DoubleArray {
    val y = DoubleArray(b.size)
    for (i in b.indices) {
        var sum = b[i]
        for (k in 0 until i) sum -= l[i][k] * y[k]
        y[i] = sum / l[i][i]
    }
    return y
}

/** Solves `Lᵀ x = y` for a lower-triangular [l]. */
internal fun backSubstituteTransposed(
    l: Array<DoubleArray>,
    y: DoubleArray,
): DoubleArray {
    val x = DoubleArray(y.size)
    for (i in y.indices.reversed()) {
        var sum = y[i]
        for (k in i + 1 until y.size) sum -= l[k][i] * x[k]
        x[i] = sum / l[i][i]
    }
    return x
}

internal fun dot(
    a: DoubleArray,
    b: DoubleArray,
): Double {
    var sum = 0.0
    for (i in a.indices) sum += a[i] * b[i]
    return sum
}
