package com.droidslife.screensaver.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.modes.console.LocalConsoleAccent
import com.droidslife.screensaver.modes.console.consoleAccentFor
import com.droidslife.screensaver.settings.sections.WidgetConfigPanel
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import com.droidslife.screensaver.widget.host.WidgetRegistry
import kotlinx.serialization.json.JsonObject

/**
 * Centered modal that renders only one widget's config schema. Opened from the
 * gear icon in [WidgetHeader]; lighter alternative to popping the full Settings
 * sidebar when the user just wants to tweak a single widget.
 */
@Composable
fun BoxScope.WidgetConfigDialog(
    widgetId: String,
    settingsViewModel: SettingsViewModel,
    widgetRegistry: WidgetRegistry,
    onDismiss: () -> Unit,
) {
    val descriptors by widgetRegistry.descriptors.collectAsState()
    val descriptor = descriptors.firstOrNull { it.id == widgetId } ?: return
    val schema = descriptor.factory.configSchema
    if (schema.isEmpty()) return

    val config = settingsViewModel.settings.widgetConfigs[widgetId] ?: JsonObject(emptyMap())

    // The dialog can be opened from any mode (Cinematic / Ambient / Console).
    // Console mode publishes its own LocalConsoleAccent in its scope, but this
    // dialog is rendered at the App root — outside that scope — so we resolve
    // the active console variant's accent directly so focus rings + accent
    // touches stay consistent with the dashboard underneath.
    val accent = consoleAccentFor(settingsViewModel.settings.consoleVariant)
    val accentPrimary = accent.primary

    // Scrim: dims the dashboard and absorbs clicks for tap-outside-to-dismiss.
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                onClick = onDismiss,
            ),
    )

    val shape = RoundedCornerShape(14.dp)
    // Accent-tinted border so the dialog reads as "of a piece" with the
    // current console variant (green / amber). Subtle by design — the goal is
    // a hint of the variant, not a frame around it.
    val borderColor = accentPrimary.copy(alpha = 0.30f).compositeOver(DwellColors.Stroke)

    CompositionLocalProvider(LocalConsoleAccent provides accent) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(min = 360.dp, max = 480.dp)
                .heightIn(max = 640.dp)
                .shadow(elevation = 28.dp, shape = shape, clip = false)
                .clip(shape)
                .background(DwellColors.Surface0)
                .border(1.dp, borderColor, shape)
                // Swallow clicks so they don't fall through to the scrim.
                .pointerInput(Unit) { },
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Small accent dot picks up the variant tint so the
                        // dialog identity always matches the dashboard chrome.
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(accentPrimary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = descriptor.displayName.uppercase(),
                            color = DwellColors.TextHigh,
                            fontFamily = DwellFonts.interTight(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            letterSpacing = 2.25.sp,
                        )
                    }
                    if (descriptor.factory.description.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = descriptor.factory.description,
                            color = DwellColors.TextLow,
                            fontFamily = DwellFonts.interTight(),
                            fontSize = 12.sp,
                        )
                    }
                }
                CloseButton(onClick = onDismiss)
            }

            // Hairline divider under the header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DwellColors.Stroke),
            )

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                WidgetConfigPanel(
                    schema = schema,
                    config = config,
                    savedSecretIds = settingsViewModel.savedSecretIds,
                    onConfigChange = { newConfig ->
                        settingsViewModel.updateWidgetConfig(widgetId, newConfig)
                    },
                    onSecretChange = { key, value ->
                        settingsViewModel.updateWidgetSecret(widgetId, key, value)
                    },
                )
            }
        }
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(DwellColors.Surface1)
            .border(1.dp, DwellColors.Stroke, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Close widget settings",
            tint = DwellColors.TextMid,
            modifier = Modifier.size(16.dp),
        )
    }
}
