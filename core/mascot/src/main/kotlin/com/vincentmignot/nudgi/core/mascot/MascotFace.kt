package com.vincentmignot.nudgi.core.mascot

import androidx.compose.runtime.Immutable

/**
 * Continuous parameters of Nudgi's look, so that switching mood animates smoothly instead of swapping drawings.
 * Everything is expressed relative to the resting pose, where every scale is 1 and every angle is 0.
 *
 * @property eyeWidth width scale of the pill-shaped eyes.
 * @property eyeHeight height scale of the eyes.
 * @property eyeTilt rotation in degrees; positive tips the top of each eye toward the other, negative away from it.
 * @property eyeLift vertical shift of the eyes as a fraction of the mascot size; negative moves them up.
 * @property antennaDroop 0 keeps the antenna upright, 1 lets it hang to the side.
 * @property bodyStretch vertical scale of the body; the width follows so the volume feels constant.
 * @property bodyLean rotation of the body in degrees around its base.
 */
@Immutable
data class MascotFace(
    val eyeWidth: Float,
    val eyeHeight: Float,
    val eyeTilt: Float,
    val eyeLift: Float,
    val antennaDroop: Float,
    val bodyStretch: Float,
    val bodyLean: Float,
)
