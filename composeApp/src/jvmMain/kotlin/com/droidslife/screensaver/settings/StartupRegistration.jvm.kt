package com.droidslife.screensaver.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText

actual fun createStartupRegistration(): StartupRegistration = JvmStartupRegistration()

private class JvmStartupRegistration : StartupRegistration {
    override suspend fun setEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        when {
            isWindows() -> setWindowsRunKey(enabled)
            isLinux() -> setLinuxAutostart(enabled)
            else -> Unit
        }
    }

    private fun setWindowsRunKey(enabled: Boolean) {
        val valueName = "ScreenSaverApp"
        if (enabled) {
            runCatching {
                ProcessBuilder(
                    "reg",
                    "add",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                    "/v",
                    valueName,
                    "/t",
                    "REG_SZ",
                    "/d",
                    windowsRunCommand(appCommand(), "--daemon"),
                    "/f",
                ).redirectErrorStream(true).start().waitFor()
            }
        } else {
            runCatching {
                ProcessBuilder(
                    "reg",
                    "delete",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                    "/v",
                    valueName,
                    "/f",
                ).redirectErrorStream(true).start().waitFor()
            }
        }
    }

    private fun setLinuxAutostart(enabled: Boolean) {
        val autostartDir = Path.of(System.getProperty("user.home"), ".config", "autostart")
        val desktopFile = autostartDir.resolve("screensaver-app.desktop")
        if (!enabled) {
            desktopFile.deleteIfExists()
            return
        }
        autostartDir.createDirectories()
        desktopFile.writeText(buildString {
            appendLine("[Desktop Entry]")
            appendLine("Type=Application")
            appendLine("Name=Screen Saver App")
            appendLine("Exec=${desktopExecCommand(appCommand(), "--daemon")}")
            appendLine("Icon=dwell-real")
            appendLine("StartupWMClass=Dwell")
            appendLine("StartupNotify=true")
            appendLine("X-GNOME-Autostart-enabled=true")
            appendLine("Terminal=false")
        })
    }

    private fun appCommand(): String {
        return System.getProperty("jpackage.app-path")
            ?: ProcessHandle.current().info().command().orElse("Screen Saver App")
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    private fun isLinux(): Boolean = System.getProperty("os.name").contains("Linux", ignoreCase = true)
}

internal fun windowsRunCommand(executable: String, arguments: String): String {
    return "\"${executable.replace("\"", "\\\"")}\" $arguments"
}

internal fun desktopExecCommand(executable: String, arguments: String): String {
    return "${desktopExecArg(executable)} $arguments"
}

private fun desktopExecArg(value: String): String {
    if (value.none { it.isWhitespace() || it in "\"\\`$" }) return value
    val escaped = buildString {
        value.forEach { char ->
            if (char in "\"\\`$") append('\\')
            append(char)
        }
    }
    return "\"$escaped\""
}
