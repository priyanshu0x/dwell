package com.droidslife.screensaver.daemon

import java.util.concurrent.TimeUnit

private const val PORTAL_DESTINATION = "org.freedesktop.portal.Desktop"
private const val PORTAL_OBJECT_PATH = "/org/freedesktop/portal/desktop"
private const val BACKGROUND_REASON = "Keeps the idle dashboard daemon ready while Dwell is hidden."
private const val BACKGROUND_STATUS = "Idle dashboard daemon"
private const val PORTAL_TIMEOUT_MS = 1_500L

internal fun registerLinuxBackgroundApp() {
    if (!isLinux()) return

    listOf(
        portalRequestBackgroundCommand(),
        portalSetBackgroundStatusCommand(),
    ).forEach(::runPortalCommand)
}

internal fun portalRequestBackgroundCommand(): List<String> = basePortalCommand(
    "org.freedesktop.portal.Background.RequestBackground",
    "",
    "{'reason': ${gVariantString(BACKGROUND_REASON)}, 'autostart': <false>}",
)

internal fun portalSetBackgroundStatusCommand(): List<String> = basePortalCommand(
    "org.freedesktop.portal.Background.SetStatus",
    "{'message': ${gVariantString(BACKGROUND_STATUS)}}",
)

private fun basePortalCommand(method: String, vararg arguments: String): List<String> = listOf(
    "gdbus",
    "call",
    "--session",
    "--dest",
    PORTAL_DESTINATION,
    "--object-path",
    PORTAL_OBJECT_PATH,
    "--method",
    method,
) + arguments

internal fun gVariantString(value: String): String {
    val escaped = buildString {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '\'' -> append("\\'")
                else -> append(char)
            }
        }
    }
    return "<'$escaped'>"
}

private fun runPortalCommand(command: List<String>): Boolean = runCatching {
    val process = ProcessBuilder(command)
        .redirectErrorStream(true)
        .start()
    if (!process.waitFor(PORTAL_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
        process.destroyForcibly()
        false
    } else {
        process.exitValue() == 0
    }
}.getOrDefault(false)

private fun isLinux(): Boolean =
    System.getProperty("os.name").contains("Linux", ignoreCase = true)
