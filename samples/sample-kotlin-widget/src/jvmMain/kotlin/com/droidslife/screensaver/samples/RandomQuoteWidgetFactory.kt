package com.droidslife.screensaver.samples

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import com.droidslife.screensaver.widget.api.Widget
import com.droidslife.screensaver.widget.api.WidgetCategory
import com.droidslife.screensaver.widget.api.WidgetConfig
import com.droidslife.screensaver.widget.api.WidgetFactory
import com.droidslife.screensaver.widget.api.WidgetScope
import kotlinx.coroutines.delay
import kotlin.random.Random

class RandomQuoteWidgetFactory : WidgetFactory {
    override val id: String = "com.droidslife.samples.randomquote"
    override val displayName: String = "Random Quote"
    override val description: String = "Shows a rotating local quote."
    override val category: WidgetCategory = WidgetCategory.INFORMATION

    override fun create(config: WidgetConfig, scope: WidgetScope): Widget = RandomQuoteWidget
}

private object RandomQuoteWidget : Widget {
    private val quotes = listOf(
        "Make it work, then make it calm.",
        "Small interfaces age better.",
        "A clean boundary is a gift to future you.",
    )

    @Composable
    override fun Content(modifier: Modifier) {
        val quote by produceState(initialValue = quotes.first()) {
            while (true) {
                delay(15_000)
                value = quotes[Random.nextInt(quotes.size)]
            }
        }

        Text(
            text = quote,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier,
        )
    }
}
