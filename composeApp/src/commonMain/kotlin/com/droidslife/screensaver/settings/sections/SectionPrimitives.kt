package com.droidslife.screensaver.settings.sections

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.ui.DwellActionButton
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
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(DwellColors.DialogControlSurface.copy(alpha = if (enabled) 1f else 0.55f))
            .border(1.dp, DwellColors.Stroke.copy(alpha = 0.72f), shape)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (enabled) DwellColors.TextHigh else DwellColors.TextLow,
                fontFamily = DwellFonts.interTight(),
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            )
            if (description != null) {
                Text(
                    text = description,
                    color = DwellColors.TextLow,
                    fontFamily = DwellFonts.interTight(),
                    fontSize = 11.sp,
                )
            }
        }
        DwellSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/**
 * Minimal pill switch. Compact (36×20) with the variant accent — replaces
 * Material's bulky Switch so the toggle reads as a setting affordance rather
 * than a primary control.
 */
@Composable
internal fun DwellSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val accent = DwellColors.StatusAccent
    val trackBg by animateColorAsState(
        targetValue = when {
            !enabled -> DwellColors.DialogControlSurface
            checked -> accent.copy(alpha = 0.45f)
            else -> DwellColors.DialogControlSurface
        },
        animationSpec = tween(160),
        label = "switch-track",
    )
    val thumbColor by animateColorAsState(
        targetValue = when {
            !enabled -> DwellColors.TextFaint
            checked -> accent
            else -> DwellColors.TextMid
        },
        animationSpec = tween(160),
        label = "switch-thumb",
    )
    val thumbOffsetX by animateDpAsState(
        targetValue = if (checked) 16.dp else 0.dp,
        animationSpec = tween(160),
        label = "switch-offset",
    )
    Box(
        modifier = Modifier
            .size(width = 36.dp, height = 20.dp)
            .clip(RoundedCornerShape(50))
            .background(trackBg)
            .border(1.dp, DwellColors.Stroke.copy(alpha = 0.5f), RoundedCornerShape(50))
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = 2.dp)
                .offset(x = thumbOffsetX)
                .size(16.dp)
                .clip(CircleShape)
                .background(thumbColor),
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
    val accent = DwellColors.StatusAccent
    val shape = RoundedCornerShape(10.dp)
    val background by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.10f) else DwellColors.DialogControlSurface,
        animationSpec = tween(140),
        label = "settings-radio-bg",
    )
    val border by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.62f) else DwellColors.Stroke.copy(alpha = 0.72f),
        animationSpec = tween(140),
        label = "settings-radio-border",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(15.dp)
                .clip(CircleShape)
                .border(1.dp, if (selected) accent else DwellColors.Stroke, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = DwellColors.TextHigh,
                fontFamily = DwellFonts.interTight(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 10.dp),
            )
            if (description != null) {
                Text(
                    text = description,
                    color = DwellColors.TextMid,
                    fontFamily = DwellFonts.interTight(),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 10.dp, top = 2.dp),
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
    DwellActionButton(
        label = label,
        onClick = onClick,
        modifier = modifier,
        primary = accent,
        enabled = enabled,
        minWidth = 92.dp,
    )
}

@Composable
internal fun ComingSoonChip(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(DwellColors.DialogControlSurface)
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
