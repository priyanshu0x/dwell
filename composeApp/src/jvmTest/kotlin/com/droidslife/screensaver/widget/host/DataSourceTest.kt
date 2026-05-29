package com.droidslife.screensaver.widget.host

import com.droidslife.screensaver.widget.api.WidgetConfig
import com.droidslife.screensaver.widget.api.WidgetLogger
import com.droidslife.screensaver.widget.api.WidgetScope
import com.droidslife.screensaver.widget.api.WidgetStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DataSourceTest {
    @Test
    fun commandDataSourceInjectsResolvedSecretValues() = runTest {
        val config = WidgetConfig(
            JsonObject(mapOf("apiKey" to JsonPrimitive("secret-id"))),
            secretResolver = { key -> if (key == "apiKey") "real-secret" else null },
        )
        val source = SourceManifest(
            type = "command",
            command = envEchoCommand("WIDGET_APIKEY"),
            timeout = "5s",
        ).toDataSource(createTempDirectory(), config, TestScope)

        assertEquals("real-secret", source.fetch().trim())
    }

    @Test
    fun widgetConfigSecretDoesNotExposeStoredSecretIds() {
        val config = WidgetConfig(JsonObject(mapOf("apiKey" to JsonPrimitive("secret-id"))))

        assertNull(config.secret("apiKey"))
    }

    @Test
    fun fileDataSourceRejectsPathsOutsideWidgetFolder() {
        val folder = createTempDirectory(prefix = "screen-saver-widget")
        folder.resolve("data.json").writeText("""{"ok":true}""")

        assertFailsWith<IllegalArgumentException> {
            SourceManifest(
                type = "file",
                path = "../secret.json",
            ).toDataSource(folder, WidgetConfig(JsonObject(emptyMap())), TestScope)
        }
    }
}

private fun envEchoCommand(name: String): List<String> {
    return if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        listOf("cmd", "/c", "echo %$name%")
    } else {
        listOf("sh", "-c", "printf '%s' \"\$$name\"")
    }
}

private object TestScope : WidgetScope {
    override val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
    override val httpClient: HttpClient = HttpClient(OkHttp)
    override val storage: WidgetStorage = InMemoryWidgetStorage
    override val log: WidgetLogger = object : WidgetLogger {
        override fun info(msg: String) = Unit
        override fun warn(msg: String, error: Throwable?) = Unit
        override fun error(msg: String, error: Throwable?) = Unit
    }
}

private object InMemoryWidgetStorage : WidgetStorage {
    override suspend fun <T : Any> read(key: String, type: Class<T>): T? = null
    override suspend fun <T : Any> write(key: String, value: T) = Unit
    override suspend fun delete(key: String) = Unit
}
