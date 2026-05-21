package com.droidslife.screensaver.widget.host

import com.droidslife.screensaver.widget.api.WidgetConfig
import com.droidslife.screensaver.widget.api.WidgetScope
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText

interface DataSource {
    suspend fun fetch(): String
}

fun SourceManifest.toDataSource(
    folder: Path,
    config: WidgetConfig,
    scope: WidgetScope,
): DataSource {
    return when (type.lowercase()) {
        "command" -> CommandDataSource(folder, command, durationMillis(timeout, 10_000L), config)
        "http" -> HttpDataSource(scope, url, method, headers, durationMillis(timeout, 10_000L), config)
        "file" -> FileDataSource(folder.resolve(path))
        else -> error("Unsupported source type: $type")
    }
}

private class CommandDataSource(
    private val folder: Path,
    private val command: List<String>,
    private val timeoutMs: Long,
    private val config: WidgetConfig,
) : DataSource {
    override suspend fun fetch(): String = withContext(Dispatchers.IO) {
        require(command.isNotEmpty()) { "Command source requires command" }
        val process = ProcessBuilder(command)
            .directory(folder.toFile())
            .apply {
                config.rawJson.keys.forEach { key ->
                    environment()["WIDGET_${key.uppercase()}"] = config.string(key)
                }
            }
            .redirectErrorStream(true)
            .start()

        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            error("Command timed out after ${timeoutMs}ms")
        }

        val output = process.inputStream.readBytes().toString(StandardCharsets.UTF_8)
        if (process.exitValue() != 0) {
            error("Command exited with ${process.exitValue()}: $output")
        }
        output
    }
}

private class HttpDataSource(
    private val scope: WidgetScope,
    private val url: String,
    private val httpMethod: String,
    private val headerMap: Map<String, String>,
    private val timeoutMs: Long,
    private val config: WidgetConfig,
) : DataSource {
    override suspend fun fetch(): String = withTimeout(timeoutMs) {
        val resolvedUrl = template(url)
        val response = scope.httpClient.request(resolvedUrl) {
            this.method = HttpMethod.parse(httpMethod)
            headerMap.forEach { (name, value) -> header(name, template(value)) }
        }
        response.bodyAsText()
    }

    private fun template(value: String): String {
        return config.rawJson.keys.fold(value) { acc, key ->
            acc.replace("$" + key, config.string(key))
        }
    }
}

private class FileDataSource(private val path: Path) : DataSource {
    override suspend fun fetch(): String = withContext(Dispatchers.IO) {
        path.readText()
    }
}

fun durationMillis(value: String, default: Long): Long {
    val trimmed = value.trim()
    trimmed.toLongOrNull()?.let { return it }
    val amount = trimmed.dropLast(1).toLongOrNull() ?: return default
    return when (trimmed.lastOrNull()?.lowercaseChar()) {
        's' -> amount * 1_000L
        'm' -> amount * 60_000L
        'h' -> amount * 3_600_000L
        else -> default
    }
}
