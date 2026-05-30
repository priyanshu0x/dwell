package com.droidslife.screensaver.widget.host

import co.touchlab.kermit.Logger
import com.droidslife.screensaver.network.isTransientNetworkFailure
import com.droidslife.screensaver.network.networkFailureSummary
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
    private val logger = Logger.withTag("widget:$widgetId")

    override fun info(msg: String) {
        logger.i { msg }
    }

    override fun warn(msg: String, error: Throwable?) {
        if (error?.isTransientNetworkFailure() == true) {
            logger.w { "$msg - ${error.networkFailureSummary()}" }
        } else {
            logger.w(error) { msg }
        }
    }

    override fun error(msg: String, error: Throwable?) {
        if (error?.isTransientNetworkFailure() == true) {
            logger.e { "$msg - ${error.networkFailureSummary()}" }
        } else {
            logger.e(error) { msg }
        }
    }
}
