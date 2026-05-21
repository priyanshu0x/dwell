package com.droidslife.screensaver.widget.host

import com.droidslife.screensaver.widget.api.WidgetStorage

class ScopedStorage(private val widgetId: String) : WidgetStorage {
    private val values = mutableMapOf<String, Any>()

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> read(key: String, type: Class<T>): T? {
        val value = values[key] ?: return null
        return if (type.isInstance(value)) value as T else null
    }

    override suspend fun <T : Any> write(key: String, value: T) {
        values[key] = value
    }

    override suspend fun delete(key: String) {
        values.remove(key)
    }

    override fun toString(): String = "ScopedStorage(widgetId=$widgetId)"
}
