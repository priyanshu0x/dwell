package com.droidslife.screensaver.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CornerButtons(
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var hovered by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (hovered) 0.95f else 0.32f,
        animationSpec = tween(DwellMotion.CornerHover, easing = DwellMotion.Standard),
        label = "corner-alpha",
    )

    Row(
        modifier = modifier
            .padding(20.dp)
            .alpha(alpha)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CornerIconButton(Icons.Filled.Settings, "Settings", onSettings)
        CornerIconButton(Icons.AutoMirrored.Filled.HelpOutline, "Help", onHelp)
    }
}

@Composable
private fun CornerIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(DwellColors.Surface1)
            .border(1.dp, DwellColors.Stroke, CircleShape),
    ) {
        Icon(icon, contentDescription = label, tint = DwellColors.TextMid)
    }
}
