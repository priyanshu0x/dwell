package com.droidslife.screensaver.widget.host

import com.droidslife.screensaver.widget.api.WidgetLogger
import com.droidslife.screensaver.widget.api.WidgetScope
import com.droidslife.screensaver.widget.api.WidgetStorage
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope

class WidgetScopeImpl(
    widgetId: String,
    override val coroutineScope: CoroutineScope,
    override val httpClient: HttpClient,
    override val storage: WidgetStorage = ScopedStorage(widgetId),
    override val log: WidgetLogger = PrintlnWidgetLogger(widgetId),
) : WidgetScope

private class PrintlnWidgetLogger(private val widgetId: String) : WidgetLogger {
    override fun info(msg: String) {
        println("[widget:$widgetId] INFO $msg")
    }

    override fun warn(msg: String, error: Throwable?) {
        println("[widget:$widgetId] WARN $msg${error?.let { ": ${it.message}" } ?: ""}")
    }

    override fun error(msg: String, error: Throwable?) {
        println("[widget:$widgetId] ERROR $msg${error?.let { ": ${it.message}" } ?: ""}")
    }
}
