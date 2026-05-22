package com.droidslife.screensaver.modes.ambient

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    val settings = settingsViewModel.settings
    val now by produceTickerLumen(includeSeconds = settings.showSeconds)

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
        // Perspective grid floor — bottom 40% of viewport, receding into the horizon.
        // Suppressed in Quieter mode.
        if (!quieter) {
            PerspectiveGridFloor(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .align(Alignment.BottomCenter),
            )
        }

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

        // Side telemetry (right edge, rotated 90°). Hidden in Quieter.
        if (!quieter) {
            Text(
                text = "ν 0.000017 hz · drift +0.0s",
                fontFamily = DwellFonts.jetBrainsMono(),
                fontSize = 9.sp,
                letterSpacing = 0.4.sp,
                color = DwellColors.LumenCyan.copy(alpha = 0.3f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 28.dp)
                    .graphicsLayer { rotationZ = 90f },
            )
        }

        // Bottom-left telemetry. Hidden in Quieter.
        if (!quieter) {
            Text(
                text = formatBottomLeftTelemetry(now),
                fontFamily = DwellFonts.jetBrainsMono(),
                fontSize = 10.sp,
                letterSpacing = 0.3.sp,
                color = DwellColors.LumenCyan.copy(alpha = 0.35f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 28.dp, bottom = 24.dp),
            )
        }

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
                text = com.droidslife.screensaver.modes.cinematic.formatClock(
                    now,
                    is24Hour = settings.is24HourFormat,
                    showSeconds = settings.showSeconds,
                ),
                fontFamily = DwellFonts.interTight(),
                // Force ExtraLight (200) explicitly; the symbolic FontWeight.ExtraLight
                // was rendering as Regular in some Skia builds.
                fontWeight = FontWeight(200),
                fontSize = 152.sp,
                color = Color(0xFFDDF0FF),
            )
            if (settings.showDate) {
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
}

@Composable
private fun PerspectiveGridFloor(modifier: Modifier = Modifier) {
    // Lines kept faint: spec calls for rgba(122,220,255,0.06) but at typical
    // monitor brightness that disappears. 0.14 reads as a quiet recede.
    val gridColor = DwellColors.LumenCyan.copy(alpha = 0.14f)
    Box(
        modifier = modifier.drawWithCache {
            val w = size.width
            val h = size.height
            // Vanishing point at top-center of this box; ground plane recedes upward.
            // Horizontal lines: closer to viewer (bottom) are spaced further apart,
            // nearer the horizon (top) they bunch together. Use a 1/z-style mapping.
            val rows = 14
            // Vertical fan: 21 columns from left to right vanishing toward center top.
            val cols = 21
            val cx = w / 2f
            // Strength of perspective; higher = more compressed near horizon.
            val perspective = 6f

            onDrawBehind {
                // Horizontal lines (depth bands).
                for (i in 1..rows) {
                    val t = i.toFloat() / rows.toFloat()
                    // 1/(perspective*(1-t) + 1) compresses near top.
                    val y = h - (h * (1f - 1f / (perspective * (1f - t) + 1f)) /
                        (1f - 1f / (perspective + 1f)))
                    val alphaFalloff = (1f - t).coerceIn(0f, 1f) * 0.9f + 0.1f
                    drawLine(
                        color = gridColor.copy(alpha = gridColor.alpha * alphaFalloff),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f,
                    )
                }
                // Vertical lines fanning out from a vanishing point at top-center.
                // Lines connect a baseline x at the bottom edge to the vanishing point.
                // Spacing at the bottom is uniform; near the top they converge.
                val baseSpread = w * 1.6f
                val vanishY = -h * 0.15f
                for (j in -cols..cols) {
                    val xBottom = cx + (j.toFloat() / cols.toFloat()) * (baseSpread / 2f)
                    val t0 = (0f - h) / (vanishY - h)
                    val xTop = xBottom + (cx - xBottom) * t0
                    val centerDist = kotlin.math.abs(j.toFloat() / cols.toFloat())
                    val alphaFalloff = (1f - centerDist * 0.4f).coerceIn(0.3f, 1f)
                    drawLine(
                        color = gridColor.copy(alpha = gridColor.alpha * alphaFalloff),
                        start = Offset(xTop, 0f),
                        end = Offset(xBottom, h),
                        strokeWidth = 1f,
                    )
                }
            }
        },
    )
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

/**
 * Bottom-left telemetry: ISO-style timestamp + idle counter.
 * Idle counter is hard-coded to 0′00″ for now — wiring real idle tracking
 * is a future task. Format: 2026-05-22T01:09Z · idle 0′00″
 */
private fun formatBottomLeftTelemetry(now: LocalDateTime): String {
    val yyyy = now.year.toString().padStart(4, '0')
    // kotlinx-datetime 0.8.0: `month.number` is the canonical accessor on
    // java.time.Month but not exposed on the kotlinx Month class directly;
    // ordinal+1 is enum-safe and avoids the deprecated `monthNumber`.
    val mm = (now.month.ordinal + 1).toString().padStart(2, '0')
    val dd = now.day.toString().padStart(2, '0')
    val hh = now.hour.toString().padStart(2, '0')
    val min = now.minute.toString().padStart(2, '0')
    return "$yyyy-$mm-${dd}T$hh:${min}Z · idle 0′00″"
}

private fun weatherStrip(): String = "WTH OK" // Phase 9/12 wires actual weather/sync status

@Composable
private fun produceTickerLumen(includeSeconds: Boolean = false): State<LocalDateTime> {
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
