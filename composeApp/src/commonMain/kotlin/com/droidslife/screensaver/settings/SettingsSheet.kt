package com.droidslife.screensaver.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Right-edge **sidebar** for application settings — renders as an in-window
 * overlay so the dashboard behind it stays fully visible and live: as the user
 * toggles a mode, variant, or display option, the change is reflected on the
 * remaining dashboard area immediately.
 *
 * Earlier this was a Compose `Dialog` with a black scrim; the scrim hid the
 * dashboard, which made the "did my toggle do anything?" feedback loop useless.
 * The dialog also opens as a separate OS window on desktop, which clashed with
 * the fullscreen always-on-top dashboard window.
 *
 * Dismissal:
 * - Header `X` button → [onDismiss]
 * - `Esc` (handled by the KeyEventHandler in App / main.kt)
 * - There is intentionally **no** scrim-click dismiss — the area to the left
 *   of the sidebar is the live dashboard.
 */
@Composable
fun BoxScope.SettingsSidebar(
    settingsViewModel: SettingsViewModel,
    widgetRegistry: WidgetRegistry,
    onDismiss: () -> Unit,
) {
    // Drive a one-shot mount animation: panel slides in from the right edge
    // after mount.
    val visible = remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { visible.value = true }

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
        SidebarPanel(
            settingsViewModel = settingsViewModel,
            widgetRegistry = widgetRegistry,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun SidebarPanel(
    settingsViewModel: SettingsViewModel,
    widgetRegistry: WidgetRegistry,
    onDismiss: () -> Unit,
) {
    // Width: min(560dp, 40% of available window width). Slightly narrower than
    // the dialog era (60%) so more dashboard is visible alongside.
    BoxWithConstraints(modifier = Modifier.fillMaxHeight()) {
        val maxW = maxWidth
        val sheetWidth = min(560.dp.value, (maxW.value * 0.40f)).dp.coerceAtLeast(360.dp)

        val shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)

        Column(
            modifier = Modifier
                .width(sheetWidth)
                .fillMaxHeight()
                .shadow(elevation = 24.dp, shape = shape, clip = false)
                .clip(shape)
                .background(DwellColors.Surface0.copy(alpha = 0.97f))
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
                CloseButton(onClick = onDismiss)
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
private fun CloseButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(DwellColors.Surface1)
            .border(1.dp, DwellColors.Stroke, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Close settings",
            tint = DwellColors.TextMid,
            modifier = Modifier.size(18.dp),
        )
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
