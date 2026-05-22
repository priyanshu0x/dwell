package com.droidslife.screensaver.samples

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import com.droidslife.screensaver.widget.api.Widget
import com.droidslife.screensaver.widget.api.WidgetCategory
import com.droidslife.screensaver.widget.api.WidgetConfig
import com.droidslife.screensaver.widget.api.WidgetFactory
import com.droidslife.screensaver.widget.api.WidgetRenderTarget
import com.droidslife.screensaver.widget.api.WidgetScope
import com.droidslife.screensaver.widget.api.WidgetSize
import com.droidslife.screensaver.widget.api.WidgetSummary
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Worked sample: minimal Kotlin/JVM widget that rotates a quote every 15s.
 *
 * Demonstrates the Dwell widget contract:
 *   - WidgetFactory.preferredSize declares Console-grid bounds.
 *   - Widget.summary() returns at-a-glance text used by Cinematic chip /
 *     Ambient minimal renderers.
 *   - Widget.Render(target, scope, modifier) decides per-target rendering.
 *     (For most widgets the default behaviour — delegate to Content() — is fine.)
 */
class RandomQuoteWidgetFactory : WidgetFactory {
    override val id: String = "com.droidslife.samples.randomquote"
    override val displayName: String = "Random Quote"
    override val description: String = "Shows a rotating local quote."
    override val category: WidgetCategory = WidgetCategory.INFORMATION
    override val preferredSize: WidgetSize = WidgetSize(
        minCols = 3, minRows = 1,
        defaultCols = 6, defaultRows = 1,
        maxCols = 12, maxRows = 2,
    )

    override fun create(config: WidgetConfig, scope: WidgetScope): Widget = RandomQuoteWidget
}

private object RandomQuoteWidget : Widget {
    private val quotes = listOf(
        "Make it work, then make it calm.",
        "Small interfaces age better.",
        "A clean boundary is a gift to future you.",
    )

    // summary() is called by the host on every recomposition. Keep it cheap; it
    // should NOT do IO. Read in-memory snapshots only.
    private var current: String = quotes.first()
    override fun summary(): WidgetSummary = WidgetSummary(
        primaryValue = current,
        primaryLabel = "Quote",
    )

    @Composable
    override fun Content(modifier: Modifier) {
        val quote by produceState(initialValue = quotes.first()) {
            while (true) {
                delay(15_000)
                value = quotes[Random.nextInt(quotes.size)].also { current = it }
            }
        }

        Text(
            text = quote,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier,
        )
    }

    // Override Render only if you want target-specific layouts. The default
    // implementation in the Widget interface forwards every target to Content(),
    // and the host wraps Chip/Minimal targets with its own chrome built from
    // summary(). For a one-line widget like this, the default is fine — we
    // could omit this override entirely.
    @Composable
    override fun Render(
        target: WidgetRenderTarget,
        scope: WidgetScope,
        modifier: Modifier,
    ) {
        when (target) {
            WidgetRenderTarget.Tile,
            WidgetRenderTarget.Chip,
            WidgetRenderTarget.Minimal -> Content(modifier)
        }
    }
}
