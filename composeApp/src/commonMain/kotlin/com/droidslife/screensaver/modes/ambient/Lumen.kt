package com.droidslife.screensaver.modes.ambient

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun Lumen(
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val quieter = settingsViewModel.settings.quieterLumen
    val now by produceTickerLumen()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        DwellColors.LumenNavy,
                        DwellColors.LumenMidnight,
                        DwellColors.LumenMidnightDeep,
                    ),
                ),
            ),
    ) {
        if (!quieter) {
            // Corner brackets
            CornerBracket(top = true, start = true, modifier = Modifier.align(Alignment.TopStart))
            CornerBracket(top = true, start = false, modifier = Modifier.align(Alignment.TopEnd))
            CornerBracket(top = false, start = true, modifier = Modifier.align(Alignment.BottomStart))
            CornerBracket(top = false, start = false, modifier = Modifier.align(Alignment.BottomEnd))
        }

        // Top telemetry. Spec § 6.2.1: in quieter mode, reduce to "{time} · {temp}°{city}" —
        // drop the "DWELL · " prefix and the weather/sync status fragments. Temp/city wiring
        // is deferred (no real weather feed yet), so quieter renders just the time for now.
        // Side telemetry and bottom-left telemetry are not yet rendered by this variant;
        // nothing to gate there.
        Text(
            text = if (quieter) {
                fmtTelemetry(now)
            } else {
                "DWELL · ${fmtTelemetry(now)} · ${weatherStrip()}"
            },
            fontFamily = DwellFonts.jetBrainsMono(),
            fontSize = 10.sp,
            letterSpacing = 0.4.sp,
            color = DwellColors.LumenCyan.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
        )

        // Orbital dial behind clock
        Box(modifier = Modifier.align(Alignment.Center).size(460.dp)) {
            OrbitalDial(currentMinute = now.minute, modifier = Modifier.fillMaxSize())
        }

        // Clock + subline
        Column(
            modifier = Modifier.align(Alignment.Center).offset(y = (-20).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}",
                fontFamily = DwellFonts.interTight(),
                fontWeight = FontWeight.ExtraLight,
                fontSize = 152.sp,
                color = Color(0xFFDDF0FF),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatSubline(now),
                fontFamily = DwellFonts.jetBrainsMono(),
                fontSize = 11.sp,
                letterSpacing = 0.4.sp,
                color = DwellColors.LumenCyan.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun CornerBracket(top: Boolean, start: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.padding(18.dp).size(28.dp)) {
        val w = size.width
        val h = size.height
        val color = DwellColors.LumenCyan.copy(alpha = 0.3f)
        when {
            top && start -> {
                drawLine(color, Offset(0f, 0f), Offset(w, 0f), 1f)
                drawLine(color, Offset(0f, 0f), Offset(0f, h), 1f)
            }
            top && !start -> {
                drawLine(color, Offset(0f, 0f), Offset(w, 0f), 1f)
                drawLine(color, Offset(w, 0f), Offset(w, h), 1f)
            }
            !top && start -> {
                drawLine(color, Offset(0f, h), Offset(w, h), 1f)
                drawLine(color, Offset(0f, 0f), Offset(0f, h), 1f)
            }
            else -> {
                drawLine(color, Offset(0f, h), Offset(w, h), 1f)
                drawLine(color, Offset(w, 0f), Offset(w, h), 1f)
            }
        }
    }
}

private fun fmtTelemetry(now: LocalDateTime): String {
    val hh = now.hour.toString().padStart(2, '0')
    val mm = now.minute.toString().padStart(2, '0')
    return "$hh:$mm IST"
}

private fun formatSubline(now: LocalDateTime): String {
    val dow = now.dayOfWeek.name.take(3).uppercase()
    val month = now.month.name.take(3).uppercase()
    return "$dow · ${now.day} $month · ${now.year}"
}

private fun weatherStrip(): String = "WTH OK" // Phase 9/12 wires actual weather/sync status

@Composable
private fun produceTickerLumen(): State<LocalDateTime> {
    return produceState(
        initialValue = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    ) {
        while (true) {
            value = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            delay(15_000)
        }
    }
}
