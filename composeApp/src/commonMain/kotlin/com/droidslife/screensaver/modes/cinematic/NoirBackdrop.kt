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
import androidx.compose.ui.unit.dp
import com.droidslife.screensaver.ui.DwellColors

@Composable
fun NoirBackdrop(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "noir-drift")
    val t by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(90_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "noir-t",
    )
    Canvas(modifier.fillMaxSize().background(Color(0xFF020203))) {
        val cx = (0.38f + 0.10f * kotlin.math.sin(t)) * size.width
        val cy = (0.42f + 0.04f * kotlin.math.sin(t + Math.PI.toFloat() / 2f)) * size.height
        val r = 600.dp.toPx()
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(DwellColors.NoirGlow.copy(alpha = 0.30f), Color.Transparent),
                center = Offset(cx, cy),
                radius = r,
            ),
            size = size,
        )
    }
}
