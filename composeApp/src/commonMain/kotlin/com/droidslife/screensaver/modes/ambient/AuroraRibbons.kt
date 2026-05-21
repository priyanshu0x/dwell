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

    Canvas(modifier.blur(20.dp)) {
        val h = size.height

        // Ribbon 1: green → teal, upper, drifts L→R
        drawRibbon(
            yBase = h * 0.28f,
            amplitude = h * 0.06f,
            phase = t * 2f * PI.toFloat(),
            color = DwellColors.BorealisGreen,
            secondaryColor = DwellColors.BorealisTeal,
        )
        // Ribbon 2: magenta → blue-ish, mid, drifts R→L
        drawRibbon(
            yBase = h * 0.50f,
            amplitude = h * 0.08f,
            phase = -t * 2f * PI.toFloat() + 1.5f,
            color = DwellColors.BorealisMagenta,
            secondaryColor = Color(0xFF7BB4EF),
        )
        // Ribbon 3: faint green, lower, drifts L→R with different phase
        drawRibbon(
            yBase = h * 0.75f,
            amplitude = h * 0.05f,
            phase = t * 2f * PI.toFloat() + 3f,
            color = DwellColors.BorealisGreen.copy(alpha = 0.5f),
            secondaryColor = DwellColors.BorealisGreen.copy(alpha = 0f),
        )
    }
}

private fun DrawScope.drawRibbon(
    yBase: Float,
    amplitude: Float,
    phase: Float,
    color: Color,
    secondaryColor: Color,
) {
    val w = size.width
    val steps = 64
    val ribbonHalfHeight = 40f
    val path = Path()
    // Top edge — left to right
    val startY0 = yBase + amplitude * sin(phase + 0f)
    path.moveTo(-50f, startY0 - ribbonHalfHeight)
    for (i in 0..steps) {
        val x = -50f + (w + 100f) * i / steps
        val y = yBase + amplitude * sin(phase + (x / w) * 3f)
        path.lineTo(x, y - ribbonHalfHeight)
    }
    // Bottom edge — right to left, closing the shape
    for (i in steps downTo 0) {
        val x = -50f + (w + 100f) * i / steps
        val y = yBase + amplitude * sin(phase + (x / w) * 3f)
        path.lineTo(x, y + ribbonHalfHeight)
    }
    path.close()
    drawPath(
        path = path,
        brush = Brush.horizontalGradient(
            colors = listOf(
                color.copy(alpha = 0f),
                color,
                secondaryColor,
                secondaryColor.copy(alpha = 0f),
            ),
        ),
        blendMode = BlendMode.Screen,
    )
}
