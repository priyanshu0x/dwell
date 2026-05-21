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
    val now by produceTicker()
    val hh = now.hour.toString().padStart(2, '0')
    val mm = now.minute.toString().padStart(2, '0')
    val time = "$hh:$mm"

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

@Composable
private fun produceTicker(): State<LocalDateTime> {
    return produceState(
        initialValue = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    ) {
        while (true) {
            value = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            delay(15_000)
        }
    }
}
