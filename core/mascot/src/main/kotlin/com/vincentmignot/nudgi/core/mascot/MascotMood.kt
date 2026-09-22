package com.vincentmignot.nudgi.core.mascot

import androidx.annotation.StringRes

/** How Nudgi currently feels; drives its facial expression via [face]. */
enum class MascotMood(
    val face: MascotFace,
    @StringRes val descriptionRes: Int,
) {
    Happy(
        face =
            MascotFace(
                eyeWidth = 1f,
                eyeHeight = 1.1f,
                eyeTilt = -6f,
                eyeLift = -0.01f,
                antennaDroop = 0f,
                bodyStretch = 1.05f,
                bodyLean = 0f,
            ),
        descriptionRes = R.string.mascot_description_happy,
    ),
    Neutral(
        face =
            MascotFace(
                eyeWidth = 1f,
                eyeHeight = 0.85f,
                eyeTilt = 0f,
                eyeLift = 0f,
                antennaDroop = 0.3f,
                bodyStretch = 1f,
                bodyLean = 0f,
            ),
        descriptionRes = R.string.mascot_description_neutral,
    ),
    Worried(
        face =
            MascotFace(
                eyeWidth = 1.1f,
                eyeHeight = 1.2f,
                eyeTilt = 14f,
                eyeLift = 0.02f,
                antennaDroop = 1f,
                bodyStretch = 0.94f,
                bodyLean = 4f,
            ),
        descriptionRes = R.string.mascot_description_worried,
    ),
}
