package com.droidslife.screensaver.ui

/**
 * Opens [url] in a fast in-app webview when the platform has one available,
 * otherwise falls back to the system default browser. Used for markdown links
 * in task notes.
 */
expect fun openLink(url: String)
