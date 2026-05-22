package com.droidslife.screensaver.modes.cinematic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.settings.CinematicVariant
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.CornerButtons
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import com.droidslife.screensaver.widget.host.WidgetRegistry
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun CinematicMode(
    settingsViewModel: SettingsViewModel,
    registry: WidgetRegistry,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (settingsViewModel.settings.cinematicVariant) {
            CinematicVariant.Dusk -> MeshGradientBackdrop(Modifier.fillMaxSize())
            CinematicVariant.Noir -> NoirBackdrop(Modifier.fillMaxSize())
        }
        CinematicForeground(settingsViewModel, registry)
        CornerButtons(
            onSettings = onOpenSettings,
            onHelp = onOpenHelp,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun BoxScope.CinematicForeground(
    settingsViewModel: SettingsViewModel,
    registry: WidgetRegistry,
) {
    val settings = settingsViewModel.settings
    val now by produceTicker(includeSeconds = settings.showSeconds)
    val time = formatClock(now, is24Hour = settings.is24HourFormat, showSeconds = settings.showSeconds)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val startPad = maxWidth * 0.08f
        val topPad = maxHeight * 0.26f
        Column(modifier = Modifier.padding(start = startPad, top = topPad)) {
            Text(
                text = time,
                fontFamily = DwellFonts.interTight(),
                fontWeight = FontWeight.Bold,
                fontSize = 280.sp,
                color = DwellColors.TextHigh,
            )
            if (settings.showDate) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = formatMetaLine(now),
                    fontFamily = DwellFonts.interTight(),
                    fontWeight = FontWeight.Normal,
                    fontSize = 18.sp,
                    color = DwellColors.TextHigh.copy(alpha = 0.75f),
                )
            }
        }
    }
    WidgetDrawer(settingsViewModel, registry)
    val instances by registry.instances.collectAsState()
    if (instances.isNotEmpty()) {
        Text(
            text = "↓ WIDGETS",
            fontFamily = DwellFonts.interTight(),
            fontWeight = FontWeight.Normal,
            fontSize = 9.sp,
            letterSpacing = 2.7.sp,
            color = DwellColors.TextHigh.copy(alpha = 0.32f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        )
    }
}

private fun formatMetaLine(now: LocalDateTime): String {
    val dow = now.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
    val month = now.month.name.lowercase().replaceFirstChar { it.uppercase() }
    return "$dow, ${now.day} $month"
}

/**
 * Format HH:MM (or HH:MM:SS) with optional AM/PM suffix.
 * Shared by all three modes via [DwellClock].
 */
internal fun formatClock(now: LocalDateTime, is24Hour: Boolean, showSeconds: Boolean): String {
    val hour24 = now.hour
    val hour = if (is24Hour) hour24 else (hour24 % 12).let { if (it == 0) 12 else it }
    val hh = hour.toString().padStart(2, '0')
    val mm = now.minute.toString().padStart(2, '0')
    val core = if (showSeconds) "$hh:$mm:${now.second.toString().padStart(2, '0')}" else "$hh:$mm"
    return if (is24Hour) core else "$core ${if (hour24 < 12) "AM" else "PM"}"
}

@Composable
internal fun produceTicker(includeSeconds: Boolean = false): State<LocalDateTime> {
    val tz = TimeZone.currentSystemDefault()
    val interval = if (includeSeconds) 1_000L else 15_000L
    return produceState(
        initialValue = Clock.System.now().toLocalDateTime(tz),
        includeSeconds,
    ) {
        while (true) {
            value = Clock.System.now().toLocalDateTime(tz)
            delay(interval)
        }
    }
}
