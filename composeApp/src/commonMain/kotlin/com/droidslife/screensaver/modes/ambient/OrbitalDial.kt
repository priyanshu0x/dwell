package com.droidslife.screensaver.modes.ambient

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.droidslife.screensaver.ui.DwellColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun OrbitalDial(currentMinute: Int, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "dial-pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-alpha",
    )

    val cyan = DwellColors.LumenCyan

    Canvas(modifier = modifier) {
        val cx = size.width / 2
        val cy = size.height / 2
        val r = (size.minDimension / 2) - 10.dp.toPx()

        // glow background
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(cyan.copy(alpha = 0.06f), Color.Transparent),
                center = Offset(cx, cy),
                radius = r * 1.1f,
            ),
            radius = r * 1.1f,
            center = Offset(cx, cy),
        )

        // outer + inner rings
        drawCircle(
            color = cyan.copy(alpha = 0.18f),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(1f),
        )
        drawCircle(
            color = cyan.copy(alpha = 0.10f),
            radius = r * 0.83f,
            center = Offset(cx, cy),
            style = Stroke(1f),
        )

        // 60 minute ticks
        for (i in 0 until 60) {
            val angleRad = (i * 6 - 90).toFloat() * PI.toFloat() / 180f
            val major = (i % 5 == 0)
            val tickLen = if (major) 10.dp.toPx() else 4.dp.toPx()
            val x1 = cx + (r - tickLen) * cos(angleRad)
            val y1 = cy + (r - tickLen) * sin(angleRad)
            val x2 = cx + r * cos(angleRad)
            val y2 = cy + r * sin(angleRad)
            drawLine(
                color = cyan.copy(alpha = if (major) 0.5f else 0.25f),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = if (major) 1.2f else 0.6f,
            )
        }

        // active minute tick (pulses)
        val activeAngle = (currentMinute * 6 - 90).toFloat() * PI.toFloat() / 180f
        val activeTickLen = 14.dp.toPx()
        val ax1 = cx + (r - activeTickLen) * cos(activeAngle)
        val ay1 = cy + (r - activeTickLen) * sin(activeAngle)
        val ax2 = cx + r * cos(activeAngle)
        val ay2 = cy + r * sin(activeAngle)
        drawLine(
            color = cyan.copy(alpha = pulse),
            start = Offset(ax1, ay1),
            end = Offset(ax2, ay2),
            strokeWidth = 2f,
        )
    }
}
