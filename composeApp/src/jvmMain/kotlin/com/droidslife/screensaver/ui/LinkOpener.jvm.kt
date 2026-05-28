package com.droidslife.screensaver.ui

import java.awt.Desktop
import java.net.URI

/**
 * Opens links through the OS-provided handler.
 *
 * Compose Desktop does not ship a native WebView control; using the system
 * handler avoids a partial in-app browser and lets Windows/macOS/Linux use the
 * browser or mail client the user already configured.
 */
actual fun openLink(url: String) {
    val uri = runCatching { URI(url) }.getOrNull() ?: return
    // Only open web/mail links. This blocks flag injection (a leading '-' that
    // xdg-open would parse as an option) and dangerous schemes (file:, jar:,
    // javascript:) that Desktop.browse / xdg-open would otherwise honor.
    if (uri.scheme?.lowercase() !in ALLOWED_SCHEMES) return
    if (browse(uri)) return
    // Last resort, common on Linux desktops where Desktop.browse is unsupported.
    // "--" stops xdg-open from treating the URL as an option.
    runCatching { ProcessBuilder("xdg-open", "--", uri.toString()).start() }
}

private val ALLOWED_SCHEMES = setOf("http", "https", "mailto")

private fun browse(uri: URI): Boolean = runCatching {
    if (Desktop.isDesktopSupported()) {
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.BROWSE)) {
            desktop.browse(uri)
            return@runCatching true
        }
    }
    false
}.getOrDefault(false)
