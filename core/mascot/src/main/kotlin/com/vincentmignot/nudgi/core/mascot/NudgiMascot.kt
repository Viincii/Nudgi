package com.vincentmignot.nudgi.core.mascot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.random.Random

private val BodyColor = Color(0xFF7BD8C4)
private val StemColor = Color(0xFF3E9C8A)
private val EyeColor = Color(0xFF1F2A2E)
private val BulbColor = Color(0xFFFFD166)
private val ShadowColor = Color(0x1F000000)

private const val BOB_PERIOD_MS = 1600

/** Distance of each eye from the vertical axis, in unit-square coordinates. */
private const val EYE_OFFSET_X = 0.13f
private const val EYE_CENTER_Y = 0.56f
private const val EYE_BASE_WIDTH = 0.075f
private const val EYE_BASE_HEIGHT = 0.15f

/**
 * Nudgi, drawn entirely with Compose vector primitives: a flat blob, two pill-shaped eyes and a tiny antenna.
 * There is no bitmap or SVG asset, so it stays sharp at any size and every part can be animated on its own.
 */
@Composable
fun NudgiMascot(
    mood: MascotMood,
    modifier: Modifier = Modifier,
) {
    val face = animateFace(mood.face)
    val idle = rememberInfiniteTransition(label = "nudgi-idle")
    val bob by idle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BOB_PERIOD_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    val blink = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        val random = Random.Default
        delay(FIRST_BLINK_DELAY_MS)
        while (true) {
            blink.animateTo(BLINK_CLOSED_SCALE, tween(BLINK_HALF_DURATION_MS))
            blink.animateTo(1f, tween(BLINK_HALF_DURATION_MS))
            delay(nextBlinkDelayMillis(random))
        }
    }
    val description = stringResource(mood.descriptionRes)

    Canvas(
        modifier =
            modifier
                .aspectRatio(1f)
                .semantics { contentDescription = description },
    ) {
        drawNudge(face = face, bob = bob, blink = blink.value)
    }
}

@Composable
private fun animateFace(target: MascotFace): MascotFace {
    val spec = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    val eyeWidth by animateFloatAsState(target.eyeWidth, spec, label = "eye-width")
    val eyeHeight by animateFloatAsState(target.eyeHeight, spec, label = "eye-height")
    val eyeTilt by animateFloatAsState(target.eyeTilt, spec, label = "eye-tilt")
    val eyeLift by animateFloatAsState(target.eyeLift, spec, label = "eye-lift")
    val antennaDroop by animateFloatAsState(target.antennaDroop, spec, label = "antenna")
    val bodyStretch by animateFloatAsState(target.bodyStretch, spec, label = "stretch")
    val bodyLean by animateFloatAsState(target.bodyLean, spec, label = "lean")
    return MascotFace(eyeWidth, eyeHeight, eyeTilt, eyeLift, antennaDroop, bodyStretch, bodyLean)
}

/**
 * Draws Nudgi in a unit square scaled to the canvas.
 *
 * @param bob 0..1 idle breathing phase.
 * @param blink 1 for open eyes down to [BLINK_CLOSED_SCALE] mid-blink.
 */
private fun DrawScope.drawNudge(
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

    val base = pt(0.5f, 0.88f)
    // Squash and stretch keep the volume roughly constant.
    val scaleY = face.bodyStretch * (1f - 0.02f * bob)
    val scaleX = (1f + (1f - face.bodyStretch) * 0.5f) * (1f + 0.02f * bob)
    withTransform({
        translate(top = -0.03f * s * bob)
        rotate(degrees = face.bodyLean, pivot = base)
        scale(scaleX = scaleX, scaleY = scaleY, pivot = base)
    }) {
        drawAntenna(face.antennaDroop, bob)
        drawBody()
        drawEyes(face, blink)
    }
}

private fun DrawScope.drawBody() {
    val s = size.minDimension
    val body =
        Path().apply {
            moveTo(0.5f * s, 0.26f * s)
            cubicTo(0.72f * s, 0.26f * s, 0.87f * s, 0.42f * s, 0.87f * s, 0.62f * s)
            cubicTo(0.87f * s, 0.8f * s, 0.72f * s, 0.88f * s, 0.5f * s, 0.88f * s)
            cubicTo(0.28f * s, 0.88f * s, 0.13f * s, 0.8f * s, 0.13f * s, 0.62f * s)
            cubicTo(0.13f * s, 0.42f * s, 0.28f * s, 0.26f * s, 0.5f * s, 0.26f * s)
            close()
        }
    drawPath(body, BodyColor)
}

private fun DrawScope.drawAntenna(
    droop: Float,
    bob: Float,
) {
    val s = size.minDimension
    val sway = (bob - 0.5f) * 0.02f
    val base = Offset(0.5f * s, 0.28f * s)
    val tip = Offset((0.5f + 0.07f * droop + sway) * s, (0.19f + 0.05f * droop) * s)
    val stem =
        Path().apply {
            moveTo(base.x, base.y)
            quadraticTo(0.5f * s, 0.22f * s, tip.x, tip.y)
        }
    drawPath(stem, StemColor, style = Stroke(width = 0.016f * s, cap = StrokeCap.Round))
    // The bulb dims as the antenna droops.
    drawCircle(BulbColor.copy(alpha = 0.3f * (1f - droop)), radius = 0.055f * s, center = tip)
    drawCircle(BulbColor, radius = 0.032f * s, center = tip)
}

private fun DrawScope.drawEyes(
    face: MascotFace,
    blink: Float,
) {
    val s = size.minDimension
    val width = EYE_BASE_WIDTH * face.eyeWidth * s
    val height = EYE_BASE_HEIGHT * face.eyeHeight * blink * s
    val centerY = (EYE_CENTER_Y + face.eyeLift) * s
    val radius = CornerRadius(min(width, height) / 2)
    // The left eye tips clockwise for a positive tilt, the right one counter-clockwise, so the tops move together.
    listOf(-1f to face.eyeTilt, 1f to -face.eyeTilt).forEach { (side, degrees) ->
        val center = Offset((0.5f + side * EYE_OFFSET_X) * s, centerY)
        rotate(degrees = degrees, pivot = center) {
            drawRoundRect(
                color = EyeColor,
                topLeft = center - Offset(width / 2, height / 2),
                size = Size(width, height),
                cornerRadius = radius,
            )
        }
    }
}
