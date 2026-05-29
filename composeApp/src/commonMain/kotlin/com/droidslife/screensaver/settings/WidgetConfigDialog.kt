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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.settings.sections.WidgetConfigPanel
import com.droidslife.screensaver.ui.DwellActionButton
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

    val cancelAndDismiss = {
        settingsViewModel.cancelSettingsDraft()
        onDismiss()
    }
    val saveAndDismiss = {
        settingsViewModel.saveSettingsDraft()
        onDismiss()
    }

    // Scrim: dims the dashboard and absorbs clicks for tap-outside-to-dismiss.
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                onClick = cancelAndDismiss,
            ),
    )

    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .widthIn(min = 360.dp, max = 480.dp)
            .heightIn(max = 640.dp)
            .shadow(elevation = 28.dp, shape = shape, clip = false)
            .clip(shape)
            .background(DwellColors.DialogSurface)
            .border(1.dp, DwellColors.Stroke, shape)
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
                Text(
                    text = descriptor.displayName.uppercase(),
                    color = DwellColors.TextHigh,
                    fontFamily = DwellFonts.interTight(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 2.25.sp,
                )
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
            CloseButton(onClick = cancelAndDismiss)
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
                // weight(fill = false) bounds this region to the space left
                // under the header (capped by the dialog's heightIn max) so
                // verticalScroll gets a finite viewport. Without it the column
                // grows to its full content height and the lower fields (the
                // API key) are pushed off-screen with nothing to scroll.
                .weight(1f, fill = false)
                .verticalScroll(scrollState)
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            WidgetConfigPanel(
                schema = schema,
                config = config,
                savedSecretIds = settingsViewModel.savedSecretIds,
                startIndent = 0.dp,
                onConfigChange = { newConfig ->
                    settingsViewModel.updateWidgetConfig(widgetId, newConfig)
                },
                onSecretChange = { key, value ->
                    settingsViewModel.updateWidgetSecret(widgetId, key, value)
                },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DwellColors.Stroke),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DwellColors.DialogControlSurface)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DwellActionButton(
                label = "Cancel",
                onClick = cancelAndDismiss,
                minWidth = 82.dp,
            )
            Spacer(Modifier.width(8.dp))
            DwellActionButton(
                label = "Save",
                onClick = saveAndDismiss,
                primary = true,
                leadingIcon = Icons.Filled.Check,
                minWidth = 86.dp,
            )
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
