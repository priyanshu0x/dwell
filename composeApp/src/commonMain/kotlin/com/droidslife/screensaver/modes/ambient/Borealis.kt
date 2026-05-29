package com.droidslife.screensaver.modes.ambient

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.droidslife.screensaver.weather.WeatherViewModel
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val starPositions: List<Offset> = listOf(
    Offset(0.12f, 0.22f),
    Offset(0.78f, 0.14f),
    Offset(0.33f, 0.78f),
    Offset(0.88f, 0.64f),
    Offset(0.05f, 0.60f),
    Offset(0.50f, 0.08f),
    Offset(0.62f, 0.88f),
    Offset(0.23f, 0.40f),
    Offset(0.95f, 0.30f),
    Offset(0.70f, 0.50f),
)
private val starAlphas: List<Float> = listOf(
    0.55f, 0.40f, 0.35f, 0.50f, 0.30f, 0.45f, 0.30f, 0.65f, 0.55f, 0.25f,
)

@Composable
fun Borealis(
    settingsViewModel: com.droidslife.screensaver.settings.SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val settings = settingsViewModel.settings
    val now by produceTickerBorealis(includeSeconds = settings.showSeconds)
    // City lives on the Weather widget config; we just read whatever the VM is
    // showing weather for so the label stays in sync as the user changes it.
    val weatherViewModel = koinViewModel<WeatherViewModel>()
    val cityLabel = weatherViewModel.selectedCity
        ?: com.droidslife.screensaver.location.FALLBACK_CITY

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                // Radial gradient anchored at bottom-center: brightest #061026 at bottom,
                // deepening to #02030a → #010108 as we move up/outward.
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF061026),
                        DwellColors.BorealisNight,
                        DwellColors.BorealisNightDeep,
                    ),
                ),
            ),
    ) {
        // Star field — fixed positions, fixed alphas. Radius is dp-scaled so the dots
        // actually render at HiDPI / large screens; a hard-coded 0.8f became sub-pixel
        // on a 1080p+ display and disappeared entirely.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val starRadius = 1.2.dp.toPx()
            starPositions.forEachIndexed { i, pos ->
                drawCircle(
                    color = Color.White.copy(alpha = starAlphas[i]),
                    radius = starRadius,
                    center = Offset(pos.x * w, pos.y * h),
                )
            }
        }

        // Aurora ribbons
        AuroraRibbons(modifier = Modifier.fillMaxSize())

        // Bottom-edge vignette
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.12f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, DwellColors.BorealisNight),
                    ),
                ),
        )

        // Clock + date + place
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            com.droidslife.screensaver.modes.cinematic.ClockText(
                now = now,
                is24Hour = settings.is24HourFormat,
                showSeconds = settings.showSeconds,
                fontFamily = DwellFonts.interTight(),
                fontWeight = FontWeight.Light,
                fontSize = 158.sp,
                color = Color(0xEAFFFFFF),
            )
            if (settings.showDate) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = formatBorealisDate(now),
                    fontFamily = DwellFonts.interTight(),
                    fontWeight = FontWeight.Light,
                    fontSize = 14.sp,
                    letterSpacing = 0.3.sp,
                    color = Color(0x8CFFFFFF),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = cityLabel,
                    fontFamily = DwellFonts.interTight(),
                    fontWeight = FontWeight.Light,
                    fontSize = 12.sp,
                    letterSpacing = 0.2.sp,
                    color = Color(0x80FFFFFF),
                )
            }
        }
    }
}

private fun formatBorealisDate(now: LocalDateTime): String {
    val dow = now.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    val month = now.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return "$dow · ${now.day} $month"
}

@Composable
private fun produceTickerBorealis(includeSeconds: Boolean = false): State<LocalDateTime> {
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
