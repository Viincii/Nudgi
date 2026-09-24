package com.vincentmignot.nudgi.core.mascot

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Renders Nudgi at rest in [mood] as a square bitmap of [sizePx], for surfaces that cannot host a
 * Compose `Canvas`, such as Glance widgets. It is the same drawing as [NudgiMascot], frozen at the
 * bottom of its breathing with open eyes.
 */
fun renderMascot(
    mood: MascotMood,
    sizePx: Int,
): Bitmap {
    val image = ImageBitmap(sizePx, sizePx)
    // Every dimension is a fraction of the canvas size, so the density plays no part.
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(image),
        size = Size(sizePx.toFloat(), sizePx.toFloat()),
    ) {
        drawNudge(face = mood.face, bob = 0f, blink = 1f)
    }
    return image.asAndroidBitmap()
}
