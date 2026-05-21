package com.droidslife.screensaver.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts

/**
 * Small visual helpers used by every Settings section. Kept local to the
 * sections package so each section file can stay focused on layout/wiring.
 */

@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = DwellColors.TextLow,
        fontFamily = DwellFonts.interTight(),
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun BodyText(text: String, modifier: Modifier = Modifier, dim: Boolean = false) {
    Text(
        text = text,
        color = if (dim) DwellColors.TextMid else DwellColors.TextHigh,
        fontFamily = DwellFonts.interTight(),
        fontSize = 14.sp,
        modifier = modifier,
    )
}

@Composable
internal fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (enabled) DwellColors.TextHigh else DwellColors.TextLow,
                fontFamily = DwellFonts.interTight(),
                fontSize = 14.sp,
            )
            if (description != null) {
                Text(
                    text = description,
                    color = DwellColors.TextMid,
                    fontFamily = DwellFonts.interTight(),
                    fontSize = 11.sp,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = DwellColors.StatusAccent,
                checkedTrackColor = DwellColors.StatusAccent.copy(alpha = 0.35f),
            ),
        )
    }
}

@Composable
internal fun RadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    description: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = DwellColors.StatusAccent,
                unselectedColor = DwellColors.TextLow,
            ),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = DwellColors.TextHigh,
                fontFamily = DwellFonts.interTight(),
                fontSize = 14.sp,
            )
            if (description != null) {
                Text(
                    text = description,
                    color = DwellColors.TextMid,
                    fontFamily = DwellFonts.interTight(),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
internal fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
) {
    val bg = when {
        !enabled -> DwellColors.Surface1
        accent -> DwellColors.StatusAccent.copy(alpha = 0.18f)
        else -> DwellColors.Surface1
    }
    val fg = when {
        !enabled -> DwellColors.TextLow
        accent -> DwellColors.StatusAccent
        else -> DwellColors.TextHigh
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, DwellColors.Stroke, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            fontFamily = DwellFonts.interTight(),
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
        )
    }
}

@Composable
internal fun ComingSoonChip(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(DwellColors.Surface1)
            .border(1.dp, DwellColors.Stroke, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = "COMING SOON",
            color = DwellColors.TextLow,
            fontFamily = DwellFonts.interTight(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.sp,
            letterSpacing = 0.8.sp,
        )
    }
}

internal val SectionVerticalSpacing = 16.dp

@Composable
internal fun SectionContainer(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(SectionVerticalSpacing),
    ) {
        content()
    }
}
