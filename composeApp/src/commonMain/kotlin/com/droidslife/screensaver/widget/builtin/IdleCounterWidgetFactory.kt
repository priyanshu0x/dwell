package com.droidslife.screensaver.widget.builtin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import com.droidslife.screensaver.widget.api.ConfigField
import com.droidslife.screensaver.widget.api.Widget
import com.droidslife.screensaver.widget.api.WidgetCategory
import com.droidslife.screensaver.widget.api.WidgetConfig
import com.droidslife.screensaver.widget.api.WidgetFactory
import com.droidslife.screensaver.widget.api.WidgetRenderTarget
import com.droidslife.screensaver.widget.api.WidgetScope
import com.droidslife.screensaver.widget.api.WidgetSize
import com.droidslife.screensaver.widget.api.WidgetSummary
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

class IdleCounterWidgetFactory : WidgetFactory {
    override val id: String = "com.droidslife.screensaver.idle"
    override val displayName: String = "Idle Counter"
    override val description: String = "Console-only: shows time since the screensaver started"
    override val category: WidgetCategory = WidgetCategory.SYSTEM
    override val preferredSize: WidgetSize = WidgetSize(
        minCols = 3, minRows = 1,
        defaultCols = 4, defaultRows = 2,
        maxCols = 6, maxRows = 2,
    )
    override val configSchema: List<ConfigField> = emptyList()

    override fun create(config: WidgetConfig, scope: WidgetScope): Widget {
        return IdleCounterWidget(mountTime = Clock.System.now())
    }
}

private class IdleCounterWidget(private val mountTime: Instant) : Widget {

    override fun summary(): WidgetSummary {
        // No-op summary; this widget is Console-only and should not render in
        // chip/minimal slots.
        return WidgetSummary(primaryValue = "—", primaryLabel = "Idle")
    }

    @Composable
    override fun Render(target: WidgetRenderTarget, scope: WidgetScope, modifier: Modifier) {
        when (target) {
            WidgetRenderTarget.Tile -> IdleTile(mountTime, modifier)
            WidgetRenderTarget.Chip, WidgetRenderTarget.Minimal -> Box(modifier) // no-op
        }
    }
}

@Composable
private fun IdleTile(mountTime: Instant, modifier: Modifier) {
    val now by produceState(initialValue = Clock.System.now()) {
        while (true) {
            value = Clock.System.now()
            delay(1000)
        }
    }
    val elapsedSec = (now - mountTime).inWholeSeconds.coerceAtLeast(0)
    val hours = elapsedSec / 3600
    val minutes = (elapsedSec % 3600) / 60
    val seconds = elapsedSec % 60
    val elapsedText = if (hours > 0) {
        "${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }

    val mountLocal = mountTime.toLocalDateTime(TimeZone.currentSystemDefault())
    val mountStamp = "${mountLocal.hour.toString().padStart(2, '0')}:" +
        mountLocal.minute.toString().padStart(2, '0')

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "IDLE",
            fontSize = 9.sp,
            letterSpacing = 2.25.sp,
            color = DwellColors.TextLow,
            fontFamily = DwellFonts.interTight(),
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = elapsedText,
            fontSize = 28.sp,
            color = DwellColors.TextHigh,
            fontFamily = DwellFonts.jetBrainsMono(),
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "screensaver since $mountStamp",
            fontSize = 10.sp,
            color = DwellColors.TextMid,
            fontFamily = DwellFonts.interTight(),
        )
    }
}
