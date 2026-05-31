package com.droidslife.screensaver.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DashboardActionBar(
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var hovered by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (hovered) 0.96f else 0.72f,
        animationSpec = tween(DwellMotion.CornerHover, easing = DwellMotion.Standard),
        label = "dashboard-actions-alpha",
    )
    val shape = RoundedCornerShape(8.dp)

    Row(
        modifier = modifier
            .padding(end = 20.dp, bottom = 4.dp)
            .alpha(alpha)
            .clip(shape)
            .background(DwellColors.Surface1.copy(alpha = 0.94f))
            .border(1.dp, DwellColors.Stroke.copy(alpha = 0.9f), shape)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .padding(horizontal = 5.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ShortcutHint(shortcut = "S · Ctrl+,") {
            DashboardActionButton(Icons.Filled.Settings, "Settings", onSettings)
        }
        ShortcutHint(shortcut = "F1 · ?") {
            DashboardActionButton(Icons.AutoMirrored.Filled.HelpOutline, "Help", onHelp)
        }
    }
}

@Composable
private fun DashboardActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(DwellColors.Surface0.copy(alpha = 0.86f))
            .border(1.dp, DwellColors.Stroke.copy(alpha = 0.76f), RoundedCornerShape(6.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = DwellColors.TextMid,
            modifier = Modifier.size(13.dp),
        )
    }
}
