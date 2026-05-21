package com.droidslife.screensaver.widget.api

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope

interface WidgetScope {
    val coroutineScope: CoroutineScope
    val httpClient: HttpClient
    val storage: WidgetStorage
    val log: WidgetLogger
}

interface WidgetStorage {
    suspend fun <T : Any> read(key: String, type: Class<T>): T?
    suspend fun <T : Any> write(key: String, value: T)
    suspend fun delete(key: String)
}

interface WidgetLogger {
    fun info(msg: String)
    fun warn(msg: String, error: Throwable? = null)
    fun error(msg: String, error: Throwable? = null)
}
