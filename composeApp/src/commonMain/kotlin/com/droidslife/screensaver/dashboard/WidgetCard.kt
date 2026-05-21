package com.droidslife.screensaver.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.droidslife.screensaver.widget.host.WidgetInstance

@Composable
fun WidgetCard(
    instance: WidgetInstance,
    modifier: Modifier = Modifier,
    showChrome: Boolean = true,
    showHeader: Boolean = false,
) {
    if (!showChrome) {
        WidgetContent(instance = instance, modifier = modifier)
        return
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            WidgetContent(instance = instance, modifier = Modifier.fillMaxWidth())
            if (showHeader) {
                Text(
                    text = (instance.widget.header ?: instance.descriptor.displayName).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    ),
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun WidgetContent(instance: WidgetInstance, modifier: Modifier) {
    instance.widget.Content(modifier)
}
