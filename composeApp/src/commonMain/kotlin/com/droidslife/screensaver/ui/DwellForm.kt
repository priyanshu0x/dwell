package com.droidslife.screensaver.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.droidslife.screensaver.components.pausesShortcutsWhileFocused

@Composable
fun DwellFormLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        fontFamily = DwellFonts.interTight(),
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        color = DwellColors.TextHigh,
    )
}

@Composable
fun DwellFormHelperText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        color = DwellColors.TextLow,
        fontFamily = DwellFonts.interTight(),
        fontSize = 11.sp,
        lineHeight = 15.sp,
    )
}

@Composable
fun DwellAnchoredDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    minWidth: Dp = 420.dp,
    maxWidth: Dp = 680.dp,
    maxHeight: Dp = 560.dp,
    content: @Composable () -> Unit,
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = modifier
                .widthIn(min = minWidth, max = maxWidth)
                .heightIn(max = maxHeight)
                .clip(RoundedCornerShape(14.dp))
                .background(DwellColors.DialogSurface)
                .border(1.dp, DwellColors.Stroke, RoundedCornerShape(14.dp)),
        ) {
            content()
        }
    }
}

@Composable
fun DwellControlFrame(
    modifier: Modifier = Modifier,
    minHeight: Dp = 34.dp,
    borderColor: Color = DwellColors.Stroke,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(DwellColors.DialogControlSurface)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

@Composable
fun DwellTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    helper: String? = null,
    password: Boolean = false,
    numeric: Boolean = false,
    minHeight: Dp = 40.dp,
    singleLine: Boolean = true,
    accent: Color = DwellColors.StatusAccent,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) accent else DwellColors.Stroke.copy(alpha = 0.5f),
        animationSpec = tween(durationMillis = 140),
        label = "dwell-field-border",
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DwellFormLabel(label)
        DwellControlFrame(minHeight = minHeight, borderColor = borderColor) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                interactionSource = interactionSource,
                textStyle = TextStyle(
                    fontFamily = DwellFonts.interTight(),
                    fontSize = 14.sp,
                    color = DwellColors.TextHigh,
                ),
                cursorBrush = SolidColor(accent),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = if (numeric) {
                    KeyboardOptions(keyboardType = KeyboardType.Number)
                } else {
                    KeyboardOptions.Default
                },
                singleLine = singleLine,
                modifier = Modifier
                    .fillMaxWidth()
                    .pausesShortcutsWhileFocused(),
                decorationBox = { inner ->
                    if (value.isEmpty() && !placeholder.isNullOrBlank()) {
                        Text(
                            text = placeholder,
                            color = DwellColors.TextFaint,
                            fontFamily = DwellFonts.interTight(),
                            fontSize = 14.sp,
                        )
                    }
                    inner()
                },
            )
        }
        if (!helper.isNullOrBlank()) {
            DwellFormHelperText(helper)
        }
    }
}

@Composable
fun DwellChoiceChip(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (selected) color.copy(alpha = 0.72f) else DwellColors.Stroke
    val background = if (selected) color.copy(alpha = 0.10f) else DwellColors.BgVoid
    Row(
        modifier = modifier
            .heightIn(min = 28.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .border(1.dp, if (selected) color else DwellColors.Stroke, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
        Text(
            text = label,
            fontFamily = DwellFonts.interTight(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = if (selected) DwellColors.TextHigh else DwellColors.TextMid,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun DwellChoiceGroup(
    label: String,
    options: List<Pair<String, String>>,
    selectedValue: String,
    onSelect: (String) -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    helper: String? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DwellFormLabel(label)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (value, displayLabel) ->
                DwellChoiceChip(
                    label = displayLabel,
                    selected = value == selectedValue,
                    color = color,
                    onClick = { onSelect(value) },
                )
            }
        }
        if (!helper.isNullOrBlank()) {
            DwellFormHelperText(helper)
        }
    }
}

@Composable
fun DwellActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    minWidth: Dp = if (primary) 104.dp else 72.dp,
    height: Dp = 32.dp,
    fontSize: TextUnit = 14.sp,
    horizontalPadding: Dp = if (primary) 13.dp else 11.dp,
    iconSize: Dp = 14.dp,
    cornerRadius: Dp = 10.dp,
) {
    val alpha = if (enabled) 1f else 0.42f
    val background = when {
        primary -> DwellColors.TextHigh.copy(alpha = alpha)
        else -> DwellColors.DialogControlSurface.copy(alpha = alpha)
    }
    val border = when {
        primary -> DwellColors.TextHigh.copy(alpha = alpha)
        danger -> DwellColors.StatusError.copy(alpha = 0.48f * alpha)
        else -> DwellColors.Stroke.copy(alpha = alpha)
    }
    val contentColor = when {
        primary -> DwellColors.Surface0.copy(alpha = alpha)
        danger -> DwellColors.StatusError.copy(alpha = alpha)
        else -> DwellColors.TextHigh.copy(alpha = alpha)
    }
    Row(
        modifier = modifier
            .height(height)
            .widthIn(min = minWidth)
            .clip(RoundedCornerShape(cornerRadius))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(cornerRadius))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                leadingIcon,
                null,
                tint = contentColor,
                modifier = Modifier.size(iconSize),
            )
        }
        Text(
            text = label,
            fontFamily = DwellFonts.interTight(),
            fontWeight = FontWeight.SemiBold,
            fontSize = fontSize,
            color = contentColor,
        )
    }
}
