package com.droidslife.screensaver.modes.cinematic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.settings.CinematicVariant
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.CornerButtons
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import com.droidslife.screensaver.weather.WeatherState
import com.droidslife.screensaver.weather.WeatherViewModel
import com.droidslife.screensaver.widget.host.WidgetRegistry
import org.koin.compose.viewmodel.koinViewModel
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
    val weatherViewModel = koinViewModel<WeatherViewModel>()

    // Clock band at 26 % top, meta band trailing under it. Both pinned to the
    // same 8 % left gutter so the meta optically lines up with the clock —
    // the mockup's 8.5 % offset reads as misalignment on the actual viewport.
    // Anchoring each row with its own fractional Spacer is more robust than
    // BoxScope.align inside BoxWithConstraints, which produced a top-pinned
    // meta line in practice.
    val gutter = 0.08f
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.fillMaxHeight(0.26f))
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.fillMaxWidth(gutter))
            ClockText(
                now = now,
                is24Hour = settings.is24HourFormat,
                showSeconds = settings.showSeconds,
                fontFamily = DwellFonts.interTight(),
                fontWeight = FontWeight.Bold,
                fontSize = 200.sp,
                color = DwellColors.TextHigh,
                lineHeight = (200f * 0.85f).sp,
                letterSpacing = (-0.06).em,
            )
        }
        if (settings.showDate) {
            Spacer(Modifier.height(40.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.fillMaxWidth(gutter))
                CinematicMetaLine(
                    now = now,
                    weatherViewModel = weatherViewModel,
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
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
        )
    }
}

// Row with explicit · spans so we can apply the mockup's 12 dp padding +
// 0.4 opacity around each separator (joinToString gives only one space).
@Composable
private fun CinematicMetaLine(
    now: LocalDateTime,
    weatherViewModel: WeatherViewModel,
    modifier: Modifier = Modifier,
) {
    val segments = buildList {
        add(formatMetaLine(now))
        val state = weatherViewModel.state
        if (state is WeatherState.Success) {
            val current = state.current
            val condition = current.conditionText.takeIf { it.isNotBlank() }
            add(buildString {
                append("${current.tempC.toInt()}°")
                if (condition != null) append(" $condition")
            })
        }
        val city = weatherViewModel.selectedCity?.ifBlank { null }
        if (city != null) add(city)
    }
    val metaColor = DwellColors.TextHigh.copy(alpha = 0.78f)
    val dotColor = DwellColors.TextHigh.copy(alpha = 0.4f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        segments.forEachIndexed { index, segment ->
            if (index > 0) {
                Text(
                    text = "·",
                    fontFamily = DwellFonts.interTight(),
                    fontSize = 16.sp,
                    color = dotColor,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            Text(
                text = segment,
                fontFamily = DwellFonts.interTight(),
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                letterSpacing = 0.06.em,
                color = metaColor,
            )
        }
    }
}

private fun formatMetaLine(now: LocalDateTime): String {
    val dow = now.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
    val month = now.month.name.lowercase().replaceFirstChar { it.uppercase() }
    return "$dow, ${now.day} $month"
}

/**
 * Render the dashboard clock with the primary HH:MM as the headline, and
 * optional `:SS` + ` AM/PM` rendered as a *secondary* glyph: ~42 % of the
 * primary size, 55 % alpha. Keeps the time legible at a glance while not
 * forcing the smaller details to compete with the hours.
 *
 * Shared by Cinematic, Lumen, and Borealis (each passes its own font / weight /
 * size / color).
 */
@Composable
internal fun ClockText(
    now: LocalDateTime,
    is24Hour: Boolean,
    showSeconds: Boolean,
    fontFamily: FontFamily,
    fontWeight: FontWeight,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    tightLineHeight: Boolean = false,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    val hour24 = now.hour
    val hour = if (is24Hour) hour24 else (hour24 % 12).let { if (it == 0) 12 else it }
    val hh = hour.toString().padStart(2, '0')
    val mm = now.minute.toString().padStart(2, '0')
    val ss = now.second.toString().padStart(2, '0')
    val ampm = if (!is24Hour) (if (hour24 < 12) "AM" else "PM") else null

    // Render primary + seconds + AM/PM as separate Text composables in a Row
    // so the secondary parts are guaranteed to share the *same* fontSize. An
    // earlier AnnotatedString approach used a single Text with em-relative
    // sub-spans; that rendered "35" smaller than "PM" because Inter Tight's
    // lining-digit cap-height is ~70% of its letter cap-height, so the same
    // em produced different visual sizes for digits vs letters.
    val secondaryFontSize = (fontSize.value * 0.36f).sp
    val secondaryColor = color.copy(alpha = 0.55f)
    // Lift the small text off the baseline so its cap-line lands near the
    // primary's cap-line (looks tucked next to HH:MM rather than dropped under).
    val secondaryBottomPad = (fontSize.value * 0.20f).dp

    val primaryLineHeight = when {
        lineHeight != TextUnit.Unspecified -> lineHeight
        tightLineHeight -> fontSize
        else -> TextUnit.Unspecified
    }
    val secondaryLineHeight = if (tightLineHeight) secondaryFontSize else TextUnit.Unspecified
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = "$hh:$mm",
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            fontSize = fontSize,
            lineHeight = primaryLineHeight,
            letterSpacing = letterSpacing,
            color = color,
        )
        if (showSeconds) {
            Text(
                text = ss,
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                fontSize = secondaryFontSize,
                lineHeight = secondaryLineHeight,
                color = secondaryColor,
                modifier = Modifier.padding(start = 12.dp, bottom = secondaryBottomPad),
            )
        }
        if (ampm != null) {
            Text(
                text = ampm,
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                fontSize = secondaryFontSize,
                lineHeight = secondaryLineHeight,
                color = secondaryColor,
                modifier = Modifier.padding(start = 12.dp, bottom = secondaryBottomPad),
            )
        }
    }
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
