package com.droidslife.screensaver.modes.ambient

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.droidslife.screensaver.ui.DwellColors
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun AuroraRibbons(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "aurora")
    val t by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 90_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "aurora-t",
    )

    // Mockup uses `mix-blend-mode: screen + filter: blur(20px) saturate(1.1)` on the whole
    // SVG. Compose Desktop's Modifier.blur only blurs the rasterised layer, so to match the
    // diffuse "luminous mist" look we (a) crank the blur up, (b) layer several overlapping
    // bands per ribbon with decreasing alpha and increasing half-height to create a
    // vertical falloff, and (c) feather each band with a vertical gradient so the edges
    // fade to transparent rather than sit as a slab.
    Canvas(modifier.blur(60.dp)) {
        val h = size.height

        // Ribbon 1: green → teal, upper, drifts L→R
        drawSoftRibbon(
            yBase = h * 0.30f,
            amplitude = h * 0.14f,
            phase = t * 2f * PI.toFloat(),
            color = DwellColors.BorealisGreen,
            secondaryColor = DwellColors.BorealisTeal,
            halfHeight = h * 0.08f,
            peakAlpha = 0.65f,
            waveFrequency = 2.2f,
        )
        // Ribbon 2: magenta → blue-ish, mid, drifts R→L
        drawSoftRibbon(
            yBase = h * 0.52f,
            amplitude = h * 0.16f,
            phase = -t * 2f * PI.toFloat() + 1.5f,
            color = DwellColors.BorealisMagenta,
            secondaryColor = Color(0xFF7BB4EF),
            halfHeight = h * 0.09f,
            peakAlpha = 0.55f,
            waveFrequency = 1.8f,
        )
        // Ribbon 3: faint green, lower, drifts L→R with different phase
        drawSoftRibbon(
            yBase = h * 0.78f,
            amplitude = h * 0.09f,
            phase = t * 2f * PI.toFloat() + 3f,
            color = DwellColors.BorealisGreen,
            secondaryColor = DwellColors.BorealisGreen,
            halfHeight = h * 0.06f,
            peakAlpha = 0.35f,
            waveFrequency = 1.4f,
        )
    }
}

/**
 * Draw a single aurora ribbon as several overlapping layers: a tight bright core,
 * a mid-thickness mid-alpha mid-layer, and one or two wide low-alpha halos. The
 * subsequent Modifier.blur turns the stack into soft luminous mist.
 */
private fun DrawScope.drawSoftRibbon(
    yBase: Float,
    amplitude: Float,
    phase: Float,
    color: Color,
    secondaryColor: Color,
    halfHeight: Float,
    peakAlpha: Float,
    waveFrequency: Float,
) {
    val layers = listOf(
        0.35f to peakAlpha,          // bright core
        0.60f to peakAlpha * 0.55f,
        1.00f to peakAlpha * 0.28f,
        1.60f to peakAlpha * 0.14f,  // wide diffuse halo
    )
    layers.forEach { (heightScale, alpha) ->
        drawRibbonBand(
            yBase = yBase,
            amplitude = amplitude,
            phase = phase,
            color = color,
            secondaryColor = secondaryColor,
            ribbonHalfHeight = halfHeight * heightScale,
            alphaScale = alpha,
            waveFrequency = waveFrequency,
        )
    }
}

private fun DrawScope.drawRibbonBand(
    yBase: Float,
    amplitude: Float,
    phase: Float,
    color: Color,
    secondaryColor: Color,
    ribbonHalfHeight: Float,
    alphaScale: Float,
    waveFrequency: Float,
) {
    val w = size.width
    val steps = 96
    val path = Path()

    // Two superimposed sines give an aurora-like curve with more "swoop" variation than
    // a single sine, matching the cubic-bezier shapes in the mockup.
    fun curveY(x: Float): Float {
        val u = x / w
        return yBase +
            amplitude * sin(phase + u * waveFrequency * PI.toFloat()) +
            amplitude * 0.45f * sin(phase * 1.3f + u * waveFrequency * 2.1f * PI.toFloat())
    }

    path.moveTo(-50f, curveY(-50f) - ribbonHalfHeight)
    for (i in 0..steps) {
        val x = -50f + (w + 100f) * i / steps
        path.lineTo(x, curveY(x) - ribbonHalfHeight)
    }
    for (i in steps downTo 0) {
        val x = -50f + (w + 100f) * i / steps
        path.lineTo(x, curveY(x) + ribbonHalfHeight)
    }
    path.close()

    // Horizontal fade (matches mockup stops 0 → peak → peak → 0).
    val horizontalBrush = Brush.horizontalGradient(
        colorStops = arrayOf(
            0.00f to color.copy(alpha = 0f),
            0.30f to color.copy(alpha = alphaScale),
            0.65f to secondaryColor.copy(alpha = alphaScale * 0.85f),
            1.00f to secondaryColor.copy(alpha = 0f),
        ),
    )
    drawPath(
        path = path,
        brush = horizontalBrush,
        blendMode = BlendMode.Screen,
    )

    // Vertical soft-fade overlay — feathers the top/bottom edges so each layer reads as
    // mist rather than a slab. The Y range covers the full extent the curve can occupy.
    val verticalBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to Color.Black.copy(alpha = 0f),
            0.5f to Color.Black.copy(alpha = alphaScale * 0.35f),
            1.0f to Color.Black.copy(alpha = 0f),
        ),
        startY = yBase - ribbonHalfHeight - amplitude,
        endY = yBase + ribbonHalfHeight + amplitude,
    )
    drawPath(
        path = path,
        brush = verticalBrush,
        blendMode = BlendMode.Screen,
    )
}
