package com.vincentmignot.nudgi.core.mascot

import androidx.compose.runtime.Immutable

/**
 * Continuous facial parameters, so that switching mood animates smoothly instead of swapping drawings.
 *
 * @property mouthCurve -1 is a full frown, 0 a flat line, 1 a full smile.
 * @property browTilt 0 hides the eyebrows, 1 shows them fully raised at the inner ends.
 * @property eyeScale 1 is the resting eye size.
 * @property antennaDroop 0 keeps the antenna upright, 1 lets it hang to the side.
 * @property cheekAlpha opacity of the blush, from 0 to 1.
 */
@Immutable
data class MascotFace(
    val mouthCurve: Float,
    val browTilt: Float,
    val eyeScale: Float,
    val antennaDroop: Float,
    val cheekAlpha: Float,
)

fun MascotMood.toFace(): MascotFace =
    when (this) {
        MascotMood.Happy -> {
            MascotFace(
                mouthCurve = 1f,
                browTilt = 0f,
                eyeScale = 1f,
                antennaDroop = 0f,
                cheekAlpha = 0.6f,
            )
        }

        MascotMood.Neutral -> {
            MascotFace(
                mouthCurve = 0.1f,
                browTilt = 0f,
                eyeScale = 1f,
                antennaDroop = 0.3f,
                cheekAlpha = 0.25f,
            )
        }

        MascotMood.Worried -> {
            MascotFace(
                mouthCurve = -0.7f,
                browTilt = 1f,
                eyeScale = 1.15f,
                antennaDroop = 1f,
                cheekAlpha = 0.1f,
            )
        }
    }
