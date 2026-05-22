package com.droidslife.screensaver.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.BuildInfo
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val AppName = "Dwell"
private const val AppLicense = "MIT"
private const val Tagline = "A calm three-mode dashboard for the screensaver hour."

private val Credits = listOf(
    "Built with Kotlin Multiplatform + Compose Desktop.",
    "Inter Tight and JetBrains Mono are licensed under the SIL Open Font License.",
    "Weather data courtesy of WeatherAPI.com.",
)

private val Links = listOf(
    "GitHub" to "https://github.com/droidslife/dwell",
    "Documentation" to "https://github.com/droidslife/dwell#readme",
    "Troubleshooting" to "https://github.com/droidslife/dwell/issues",
)

@Composable
fun AboutSection() {
    SectionContainer {
        // App identity card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DwellColors.Surface1)
                .border(1.dp, DwellColors.Stroke, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = AppName,
                    color = DwellColors.TextHigh,
                    fontFamily = DwellFonts.interTight(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                )
                Text(
                    text = Tagline,
                    color = DwellColors.TextMid,
                    fontFamily = DwellFonts.interTight(),
                    fontSize = 13.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MetaLine("Version", BuildInfo.VERSION)
                    MetaLine("Build", BuildInfo.COMMIT)
                    MetaLine("Built", formatBuildDate(BuildInfo.BUILD_EPOCH_S))
                    MetaLine("License", AppLicense)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader("Credits")
            Credits.forEach { line ->
                BodyText(line, dim = true)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader("Links")
            // TODO(phase 11+): open URLs via Desktop.getDesktop().browse() in jvmMain.
            // For now we surface the URLs as monospace text so users can copy them.
            Links.forEach { (label, url) ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = label,
                        color = DwellColors.TextHigh,
                        fontFamily = DwellFonts.interTight(),
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        text = url,
                        color = DwellColors.TextMid,
                        fontFamily = DwellFonts.jetBrainsMono(),
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

private fun formatBuildDate(epochS: Long): String {
    val ldt = Instant.fromEpochSeconds(epochS).toLocalDateTime(TimeZone.currentSystemDefault())
    val mm = ldt.monthNumber.toString().padStart(2, '0')
    val dd = ldt.day.toString().padStart(2, '0')
    return "${ldt.year}-$mm-$dd"
}

@Composable
private fun MetaLine(label: String, value: String) {
    Column {
        Text(
            text = label.uppercase(),
            color = DwellColors.TextLow,
            fontFamily = DwellFonts.interTight(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.sp,
            letterSpacing = 0.8.sp,
        )
        Text(
            text = value,
            color = DwellColors.TextHigh,
            fontFamily = DwellFonts.jetBrainsMono(),
            fontSize = 12.sp,
        )
    }
}
