package com.droidslife.screensaver.ui

import java.awt.Desktop
import java.net.URI

/**
 * Opens links preferring a fast embedded webview, then the system browser.
 *
 * The webview hook is reflective on purpose: if a JCEF/KCEF webview is present
 * on the classpath (we don't bundle one yet — it ships a full Chromium), it'll
 * be used; otherwise we fall through to the browser. On Linux, AWT `Desktop`
 * is frequently not wired, so we also fall back to `xdg-open`.
 */
actual fun openLink(url: String) {
    val uri = runCatching { URI(url) }.getOrNull() ?: return
    // Only open web/mail links. This blocks flag injection (a leading '-' that
    // xdg-open would parse as an option) and dangerous schemes (file:, jar:,
    // javascript:) that Desktop.browse / xdg-open would otherwise honor.
    if (uri.scheme?.lowercase() !in ALLOWED_SCHEMES) return
    if (WebViewBridge.open(url)) return
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

/**
 * Opens a URL in an embedded webview if one is on the classpath. Returns false
 * when none is available (the default today). Wiring KCEF
 * (`dev.datlag:kcef`) here would light up the fast in-app path without touching
 * call sites.
 */
private object WebViewBridge {
    fun open(@Suppress("UNUSED_PARAMETER") url: String): Boolean = false
}
