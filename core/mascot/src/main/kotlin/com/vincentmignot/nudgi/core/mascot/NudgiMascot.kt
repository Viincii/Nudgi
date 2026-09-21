package com.vincentmignot.nudgi.core.mascot

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.max

private val BodyColor = Color(0xFF7BD8C4)
private val BodyOutlineColor = Color(0xFF3E9C8A)
private val BellyColor = Color(0xFFC8F2E8)
private val EyeColor = Color(0xFF1F2A2E)
private val CheekColor = Color(0xFFFF9FB2)
private val BulbColor = Color(0xFFFFD166)
private val ShadowColor = Color(0x1F000000)

private const val BOB_PERIOD_MS = 1600
private const val BLINK_PERIOD_MS = 4200
private const val BLINK_CLOSED_SCALE = 0.08f

/**
 * Nudgi, drawn entirely with Compose vector primitives: no bitmap or SVG asset, so it stays sharp at any size and
 * every part (mouth, eyes, antenna) can be animated on its own.
 */
@Composable
fun NudgiMascot(
    mood: MascotMood,
    modifier: Modifier = Modifier,
) {
    val face = animateFace(mood.toFace())
    val idle = rememberInfiniteTransition(label = "nudgi-idle")
    val bob by idle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BOB_PERIOD_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    val blink by idle.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                keyframes {
                    durationMillis = BLINK_PERIOD_MS
                    1f at 0
                    1f at BLINK_PERIOD_MS - 300
                    BLINK_CLOSED_SCALE at BLINK_PERIOD_MS - 200
                    1f at BLINK_PERIOD_MS - 50
                },
            ),
        label = "blink",
    )
    val description = stringResource(mood.descriptionRes())

    Canvas(
        modifier =
            modifier
                .aspectRatio(1f)
                .semantics { contentDescription = description },
    ) {
        drawNudgi(face = face, bob = bob, blink = blink)
    }
}

private fun MascotMood.descriptionRes(): Int =
    when (this) {
        MascotMood.Happy -> R.string.mascot_description_happy
        MascotMood.Neutral -> R.string.mascot_description_neutral
        MascotMood.Worried -> R.string.mascot_description_worried
    }

@Composable
private fun animateFace(target: MascotFace): MascotFace {
    val spec = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    val mouthCurve by animateFloatAsState(target.mouthCurve, spec, label = "mouth")
    val browTilt by animateFloatAsState(target.browTilt, spec, label = "brows")
    val eyeScale by animateFloatAsState(target.eyeScale, spec, label = "eyes")
    val antennaDroop by animateFloatAsState(target.antennaDroop, spec, label = "antenna")
    val cheekAlpha by animateFloatAsState(target.cheekAlpha, spec, label = "cheeks")
    return MascotFace(mouthCurve, browTilt, eyeScale, antennaDroop, cheekAlpha)
}

/**
 * Draws Nudgi in a unit square scaled to the canvas.
 *
 * @param bob 0..1 idle breathing phase.
 * @param blink 1 for open eyes down to [BLINK_CLOSED_SCALE] for a blink.
 */
private fun DrawScope.drawNudgi(
    face: MascotFace,
    bob: Float,
    blink: Float,
) {
    val s = size.minDimension

    fun pt(
        x: Float,
        y: Float,
    ) = Offset(x * s, y * s)

    // Ground shadow shrinks while the body rises.
    val shadowWidth = 0.5f - 0.04f * bob
    drawOval(
        color = ShadowColor,
        topLeft = pt(0.5f - shadowWidth / 2, 0.9f),
        size = Size(shadowWidth * s, 0.06f * s),
    )

    withTransform({
        translate(top = -0.03f * s * bob)
        scale(scaleX = 1f + 0.02f * bob, scaleY = 1f - 0.02f * bob, pivot = pt(0.5f, 0.88f))
    }) {
        drawAntenna(face.antennaDroop, bob)

        drawOval(BodyColor, topLeft = pt(0.14f, 0.26f), size = Size(0.72f * s, 0.62f * s))
        drawOval(BellyColor, topLeft = pt(0.28f, 0.5f), size = Size(0.44f * s, 0.34f * s))

        val cheekAlpha = face.cheekAlpha.coerceIn(0f, 1f)
        drawCircle(CheekColor.copy(alpha = cheekAlpha), radius = 0.05f * s, center = pt(0.27f, 0.63f))
        drawCircle(CheekColor.copy(alpha = cheekAlpha), radius = 0.05f * s, center = pt(0.73f, 0.63f))

        drawEyes(face.eyeScale, blink)
        drawBrows(face.browTilt)
        drawMouth(face.mouthCurve)
    }
}

private fun DrawScope.drawAntenna(
    droop: Float,
    bob: Float,
) {
    val s = size.minDimension
    val sway = (bob - 0.5f) * 0.03f
    val base = Offset(0.5f * s, 0.27f * s)
    val tip = Offset((0.5f + 0.12f * droop + sway) * s, (0.1f + 0.1f * droop) * s)
    val stem =
        Path().apply {
            moveTo(base.x, base.y)
            quadraticTo(0.5f * s, 0.17f * s, tip.x, tip.y)
        }
    drawPath(stem, BodyOutlineColor, style = Stroke(width = 0.02f * s, cap = StrokeCap.Round))
    // The bulb dims as the antenna droops.
    drawCircle(BulbColor.copy(alpha = 0.3f * (1f - droop)), radius = 0.075f * s, center = tip)
    drawCircle(BulbColor, radius = 0.045f * s, center = tip)
}

private fun DrawScope.drawEyes(
    eyeScale: Float,
    blink: Float,
) {
    val s = size.minDimension
    val radiusX = 0.04f * eyeScale * s
    val radiusY = radiusX * 1.25f * max(blink, BLINK_CLOSED_SCALE)
    listOf(0.38f, 0.62f).forEach { centerX ->
        val center = Offset(centerX * s, 0.5f * s)
        drawOval(EyeColor, topLeft = center - Offset(radiusX, radiusY), size = Size(radiusX * 2, radiusY * 2))
        if (blink > 0.5f) {
            drawCircle(Color.White, radius = 0.012f * s, center = center + Offset(0.012f * s, -0.014f * s))
        }
    }
}

private fun DrawScope.drawBrows(tilt: Float) {
    val alpha = tilt.coerceIn(0f, 1f)
    if (alpha <= 0f) return
    val s = size.minDimension
    val color = EyeColor.copy(alpha = alpha)
    val stroke = Stroke(width = 0.014f * s, cap = StrokeCap.Round)
    val innerY = 0.43f - 0.05f * tilt
    drawPath(
        Path().apply {
            moveTo(0.3f * s, 0.43f * s)
            lineTo(0.44f * s, innerY * s)
        },
        color,
        style = stroke,
    )
    drawPath(
        Path().apply {
            moveTo(0.56f * s, innerY * s)
            lineTo(0.7f * s, 0.43f * s)
        },
        color,
        style = stroke,
    )
}

private fun DrawScope.drawMouth(curve: Float) {
    val s = size.minDimension
    val mouth =
        Path().apply {
            moveTo(0.43f * s, 0.64f * s)
            quadraticTo(0.5f * s, (0.64f + 0.1f * curve) * s, 0.57f * s, 0.64f * s)
        }
    drawPath(mouth, EyeColor, style = Stroke(width = 0.016f * s, cap = StrokeCap.Round))
}
