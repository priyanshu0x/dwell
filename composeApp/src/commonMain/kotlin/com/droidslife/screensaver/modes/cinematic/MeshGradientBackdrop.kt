package com.droidslife.screensaver.modes.cinematic

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun MeshGradientBackdrop(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "dusk-drift")
    val t by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 60_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dusk-t",
    )

    Canvas(modifier = modifier.fillMaxSize().background(Color(0xFF050307))) {
        // Three blobs drifting on relatively-prime periods (47s, 53s, 61s).
        // Tight radii + spread-out centers so the colors stay legibly separate
        // and only blend in the middle of the canvas.
        drawBlob(
            centerX(0.75f, 0.05f, t * (60f / 47f)),
            centerY(0.25f, 0.04f, t * (60f / 47f)),
            Color(0xFFF3A280).copy(alpha = 0.55f),
        )
        drawBlob(
            centerX(0.15f, 0.06f, t * (60f / 53f)),
            centerY(0.80f, 0.05f, t * (60f / 53f)),
            Color(0xFFB46CC4).copy(alpha = 0.50f),
        )
        drawBlob(
            centerX(0.90f, 0.04f, t * (60f / 61f)),
            centerY(0.95f, 0.03f, t * (60f / 61f)),
            Color(0xFF3C50A0).copy(alpha = 0.35f),
        )
    }
}

private fun centerX(base: Float, amp: Float, phase: Float): Float = base + amp * sin(phase)
private fun centerY(base: Float, amp: Float, phase: Float): Float = base + amp * sin(phase + 1.2f)

private fun DrawScope.drawBlob(cx: Float, cy: Float, color: Color) {
    val w = size.width
    val h = size.height
    val r = w * 0.40f
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = 0f)),
            center = Offset(cx * w, cy * h),
            radius = r,
        ),
        size = size,
    )
}
