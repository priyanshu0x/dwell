package com.droidslife.screensaver.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.droidslife.screensaver.widget.host.WidgetInstance
import com.droidslife.screensaver.widget.host.WidgetRegistry
import org.koin.compose.koinInject

@Composable
fun WidgetGrid(
    modifier: Modifier = Modifier,
    registry: WidgetRegistry = koinInject(),
    showChrome: Boolean = true,
) {
    val instances by registry.instances.collectAsState()
    val orderedInstances = instances.values.sortedBy { it.descriptor.displayName }

    if (orderedInstances.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "No widgets enabled",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        return
    }

    if (!showChrome) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            orderedInstances.forEach { instance ->
                ResumedWidgetCard(
                    instance = instance,
                    showChrome = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(320.dp),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(
            items = orderedInstances,
            key = { it.descriptor.id },
            span = { instance -> GridItemSpan(instance.widget.preferredSpan.coerceIn(1, maxLineSpan)) },
        ) { instance ->
            ResumedWidgetCard(
                instance = instance,
                showChrome = showChrome,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ResumedWidgetCard(
    instance: WidgetInstance,
    showChrome: Boolean,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(instance) {
        instance.widget.onResume()
        onDispose { instance.widget.onSuspend() }
    }

    WidgetCard(
        instance = instance,
        modifier = modifier,
        showChrome = showChrome,
    )
}
