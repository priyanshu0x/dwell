package com.droidslife.screensaver.serialization

import kotlinx.serialization.json.Json

object DwellJson {
    val Api: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    val Lenient: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val IgnoreUnknown: Json = Json {
        ignoreUnknownKeys = true
    }

    val Persisted: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val PrettyPersisted: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    val Pretty: Json = Json {
        prettyPrint = true
    }
}
