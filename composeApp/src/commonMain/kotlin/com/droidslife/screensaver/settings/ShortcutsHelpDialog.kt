package com.droidslife.screensaver.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Dialog displaying the current keyboard shortcuts for the dashboard.
 *
 * Replaces the older Material-3 [SettingsDialog]'s help section. Lists the
 * three-mode UI shortcuts (M / 1-3 / V / W / L) plus the global Ctrl chords.
 */
@Composable
fun ShortcutsHelpDialog(
    onDismiss: () -> Unit,
    onExitApplication: () -> Unit = {},
) {
    val shortcuts = listOf(
        "M" to "Cycle Mode (Cinematic → Ambient → Console)",
        "1" to "Jump to Cinematic",
        "2" to "Jump to Ambient",
        "3" to "Jump to Console",
        "V" to "Cycle Variant",
        "W" to "Toggle Widget Drawer (Cinematic)",
        "L" to "Toggle Console Edit Mode",
        "Ctrl + ," to "Open Settings",
        "Ctrl + R" to "Reload Widgets",
        "Ctrl + T" to "Toggle Theme",
        "Ctrl + Q" to "Quit",
        "F1 / ?" to "Show this help",
        "Esc" to "Exit screensaver",
    )

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp),
            ) {
                Button(
                    onClick = onExitApplication,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Exit Application",
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(text = "Exit App", fontWeight = FontWeight.Bold)
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Keyboard Shortcuts",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                shortcuts.forEach { (key, desc) ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = key,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(96.dp),
                        )
                        Text(text = desc)
                    }
                }
            }
        }
    }
}
