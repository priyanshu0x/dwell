package com.droidslife.screensaver.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File

actual suspend fun runDwellDoctor(): String = withContext(Dispatchers.IO) {
    val cmd = if (isWindows()) {
        listOf("cmd.exe", "/c", "scripts\\dwell.cmd", "doctor")
    } else {
        listOf("sh", "./scripts/dwell", "doctor")
    }
    runCatching {
        val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        output
    }.getOrElse { e -> "Couldn't run dwell doctor: ${e.message}" }
}

actual fun openDwellConfigFolder(): Boolean {
    val candidate = if (isWindows()) {
        val appData = System.getenv("APPDATA") ?: return false
        File(appData, "dwell")
    } else {
        File(System.getProperty("user.home"), ".screensaver")
    }
    if (!candidate.exists()) candidate.mkdirs()
    return runCatching {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(candidate)
            true
        } else false
    }.getOrDefault(false)
}

private fun isWindows(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase().contains("win")
