package com.droidslife.screensaver.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.droidslife.screensaver.settings.sections.AboutSection
import com.droidslife.screensaver.settings.sections.DisplaySection
import com.droidslife.screensaver.settings.sections.SyncSection
import com.droidslife.screensaver.settings.sections.TriggersSection
import com.droidslife.screensaver.settings.sections.WidgetsSection
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import com.droidslife.screensaver.ui.DwellMotion
import com.droidslife.screensaver.widget.host.WidgetRegistry
import kotlin.math.min

private enum class SettingsTab(val label: String) {
    Display("Display"),
    Widgets("Widgets"),
    Triggers("Triggers"),
    Sync("Sync"),
    About("About"),
}

/**
 * Full-height right-edge side-sheet for application settings. Replaces the
 * older Material-3 [SettingsDialog].
 *
 * The outer [Dialog] is used purely as a focus / Esc-handler container — its
 * contents fill the whole screen and the visible chrome is rendered manually
 * inside (scrim + sliding panel).
 *
 * Dismissal:
 * - `Esc` key → routed via [DialogProperties.dismissOnBackPress] → [onDismiss]
 * - Click on scrim → handled by the scrim [Box]'s `clickable` → [onDismiss]
 * - Header `X` button → [onDismiss]
 *
 * We deliberately set [DialogProperties.dismissOnClickOutside] to `false`
 * because in this layout the whole window is "inside" the Dialog content; the
 * scrim is what intercepts outside-area clicks and we want to control that
 * ourselves so the panel itself doesn't accidentally trigger dismiss.
 */
@Composable
fun SettingsSheet(
    settingsViewModel: SettingsViewModel,
    widgetRegistry: WidgetRegistry,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        // Drive a one-shot mount animation: panel slides in from the right
        // edge after the Dialog mounts. Visible defaults to false, then flips
        // to true on first composition.
        val visible = remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible.value = true }

        SheetScaffold(
            visible = visible,
            settingsViewModel = settingsViewModel,
            widgetRegistry = widgetRegistry,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun SheetScaffold(
    visible: MutableState<Boolean>,
    settingsViewModel: SettingsViewModel,
    widgetRegistry: WidgetRegistry,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // Scrim — fills the whole window, intercepts click-outside.
        val scrimInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                ) { onDismiss() },
        )

        // Sliding sheet
        AnimatedVisibility(
            visible = visible.value,
            enter = slideInHorizontally(
                animationSpec = tween(
                    durationMillis = DwellMotion.SettingsSheetSlide,
                    easing = DwellMotion.Emphasized,
                ),
            ) { fullWidth -> fullWidth },
            exit = slideOutHorizontally(
                animationSpec = tween(
                    durationMillis = DwellMotion.SettingsSheetSlide,
                    easing = DwellMotion.Emphasized,
                ),
            ) { fullWidth -> fullWidth },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            SheetContent(
                settingsViewModel = settingsViewModel,
                widgetRegistry = widgetRegistry,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun SheetContent(
    settingsViewModel: SettingsViewModel,
    widgetRegistry: WidgetRegistry,
    onDismiss: () -> Unit,
) {
    // Width: min(560dp, 60% of available window width).
    BoxWithConstraints(modifier = Modifier.fillMaxHeight()) {
        val maxW = maxWidth
        val sheetWidth = min(560.dp.value, (maxW.value * 0.6f)).dp

        val shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)

        Column(
            modifier = Modifier
                .width(sheetWidth)
                .fillMaxHeight()
                .clip(shape)
                .background(DwellColors.Surface0)
                .border(1.dp, DwellColors.Stroke, shape),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Settings",
                    color = DwellColors.TextHigh,
                    fontFamily = DwellFonts.interTight(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close settings",
                        tint = DwellColors.TextMid,
                    )
                }
            }

            // Sticky tab row
            var selectedTab by remember { mutableStateOf(SettingsTab.Display) }
            TabPillRow(
                tabs = SettingsTab.entries,
                selected = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            HorizontalDivider(color = DwellColors.Stroke)

            // Scrollable body with vertical scrollbar
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                ) {
                    when (selectedTab) {
                        SettingsTab.Display -> DisplaySection(settingsViewModel)
                        SettingsTab.Widgets -> WidgetsSection(settingsViewModel, widgetRegistry)
                        SettingsTab.Triggers -> TriggersSection(settingsViewModel)
                        SettingsTab.Sync -> SyncSection(settingsViewModel)
                        SettingsTab.About -> AboutSection()
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp),
                )
            }

            HorizontalDivider(color = DwellColors.Stroke)
        }
    }
}

@Composable
private fun TabPillRow(
    tabs: List<SettingsTab>,
    selected: SettingsTab,
    onSelect: (SettingsTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tabs.forEach { tab ->
            val isSelected = tab == selected
            val bg = if (isSelected) {
                DwellColors.StatusAccent.copy(alpha = 0.14f)
            } else {
                DwellColors.Surface1
            }
            val fg = if (isSelected) DwellColors.TextHigh else DwellColors.TextMid
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab.label,
                    color = fg,
                    fontFamily = DwellFonts.interTight(),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
    }
}
