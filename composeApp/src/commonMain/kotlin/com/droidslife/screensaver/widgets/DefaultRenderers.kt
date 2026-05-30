package com.droidslife.screensaver.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.modes.console.LocalConsoleWidgetBorderStyle
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import com.droidslife.screensaver.ui.DwellRadius
import com.droidslife.screensaver.widget.api.WidgetSummary

@Composable
fun DefaultTileRender(s: WidgetSummary, modifier: Modifier = Modifier) {
    val consoleBorderStyle = LocalConsoleWidgetBorderStyle.current
    val shape = RoundedCornerShape(DwellRadius.m)
    Column(
        modifier = modifier
            .clip(shape)
            .background(DwellColors.Surface1)
            .then(
                if (consoleBorderStyle == null) {
                    Modifier.border(1.dp, DwellColors.Stroke, shape)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        s.primaryLabel?.let {
            Text(
                text = it.uppercase(),
                fontSize = 9.sp,
                letterSpacing = 0.25.sp,
                color = DwellColors.TextLow,
                fontFamily = DwellFonts.interTight(),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = s.primaryValue,
            fontSize = 28.sp,
            color = DwellColors.TextHigh,
            fontFamily = DwellFonts.jetBrainsMono(),
            fontWeight = FontWeight.Medium,
        )
        s.subtitle?.let {
            Text(
                text = it,
                fontSize = 10.sp,
                color = DwellColors.TextMid,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = DwellFonts.interTight(),
            )
        }
    }
}

@Composable
fun DefaultChipRender(s: WidgetSummary, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        s.primaryLabel?.let {
            Text(
                text = it.uppercase(),
                fontSize = 10.sp,
                letterSpacing = 0.15.sp,
                color = DwellColors.TextMid,
                fontFamily = DwellFonts.interTight(),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = s.primaryValue,
            fontSize = 14.sp,
            color = DwellColors.TextHigh,
            fontFamily = DwellFonts.jetBrainsMono(),
        )
    }
}

@Composable
fun DefaultMinimalRender(s: WidgetSummary, modifier: Modifier = Modifier) {
    val label = listOfNotNull(s.primaryLabel?.lowercase(), s.subtitle ?: s.primaryValue)
        .joinToString(" · ")
    Text(
        text = label,
        fontSize = 12.sp,
        color = DwellColors.TextMid,
        fontFamily = DwellFonts.interTight(),
        modifier = modifier,
    )
}
