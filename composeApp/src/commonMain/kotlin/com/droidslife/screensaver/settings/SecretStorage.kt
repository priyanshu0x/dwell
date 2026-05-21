package com.droidslife.screensaver.settings

interface SecretStorage {
    suspend fun read(id: String): String?
    suspend fun write(id: String, value: String)
    suspend fun delete(id: String)
}

expect fun createSecretStorage(): SecretStorage

const val WEATHER_API_KEY_SECRET_ID: String = "weather.apiKey"

fun widgetSecretId(widgetId: String, key: String): String = "widget.$widgetId.$key"
