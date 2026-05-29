package com.droidslife.screensaver.modes.cinematic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.widget.api.WidgetRenderTarget
import com.droidslife.screensaver.widget.host.WidgetRegistry
import com.droidslife.screensaver.widgets.DefaultChipRender
import kotlinx.coroutines.delay

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BoxScope.WidgetDrawer(
    settingsViewModel: SettingsViewModel,
    registry: WidgetRegistry,
) {
    var hoverVisible by remember { mutableStateOf(false) }
    var lastHover by remember { mutableLongStateOf(0L) }

    // Hover-area detector at bottom 10% of screen
    Box(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.10f)
            .onPointerEvent(PointerEventType.Enter) {
                hoverVisible = true
                lastHover = System.currentTimeMillis()
            }
            .onPointerEvent(PointerEventType.Move) {
                lastHover = System.currentTimeMillis()
            }
            .onPointerEvent(PointerEventType.Exit) {
                lastHover = System.currentTimeMillis()
            }
    )

    LaunchedEffect(lastHover) {
        if (hoverVisible) {
            delay(2000)
            if (System.currentTimeMillis() - lastHover >= 2000) hoverVisible = false
        }
    }

    val instances by registry.instances.collectAsState()
    val enabled = instances.values.toList()

    if (enabled.isEmpty()) return

    val visible = hoverVisible || settingsViewModel.drawerVisible

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it },
        exit = fadeOut(tween(220)) + slideOutVertically(tween(220)) { it },
        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 60.dp).fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp, 12.dp, 0.dp, 0.dp))
                .background(Color(0xD9080812))
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            enabled.forEach { instance ->
                val widget = instance.widget
                key(instance.scope) {
                    if (widget.rendersOwnChip) {
                        widget.Render(WidgetRenderTarget.Chip, instance.scope, Modifier)
                    } else {
                        DefaultChipRender(widget.summary())
                    }
                }
            }
        }
    }
}
