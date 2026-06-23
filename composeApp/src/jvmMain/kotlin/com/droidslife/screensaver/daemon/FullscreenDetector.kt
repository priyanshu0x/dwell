package com.droidslife.screensaver.daemon

import java.util.concurrent.TimeUnit

/**
 * Detects whether Dwell should suppress idle-triggered auto-show.
 *
 * Suppresses when:
 * - a fullscreen window is active (X11 via xprop, GNOME Shell Eval fallback)
 * - a screensaver inhibit is held (org.gnome.SessionManager / org.freedesktop.ScreenSaver)
 *
 * Manual `dwell show` bypasses this check.
 * Returns false on any error (fail-open: show dwell if we can't detect).
 */
internal object FullscreenDetector {

    fun shouldSuppressAutoShow(): Boolean {
        return isFullscreenOrInhibited()
    }

    private fun isFullscreenOrInhibited(): Boolean {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("linux") -> isLinuxSuppressed()
            os.contains("windows") -> isWindowsFullscreen()
            else -> false
        }
    }

    // ---- Linux ----

    private fun isLinuxSuppressed(): Boolean {
        // 1. X11 fullscreen via xprop (covers X11 and XWayland)
        if (isX11FullscreenViaXprop() == true) return true
        // 2. GNOME SessionManager inhibitors (covers Wayland video players, browsers, presentations)
        if (isGnomeInhibited() == true) return true
        // 3. GNOME Shell fullscreen via Eval (best effort for native Wayland)
        if (isGnomeShellFullscreen() == true) return true
        return false
    }

    internal fun isX11FullscreenViaXprop(): Boolean? {
        return runCatching {
            val active = runProcess("xprop", "-root", "_NET_ACTIVE_WINDOW") ?: return@runCatching null
            val id = parseActiveWindowId(active) ?: return@runCatching null
            if (id == "0x0" || id == "0x0 (0x0)") return@runCatching null
            val state = runProcess("xprop", "-id", id, "_NET_WM_STATE") ?: return@runCatching null
            state.contains("_NET_WM_STATE_FULLSCREEN")
        }.getOrNull()
    }

    internal fun parseActiveWindowId(output: String): String? {
        // e.g. "_NET_ACTIVE_WINDOW(WINDOW): window id # 0x4a00003" or "0x4a00003"
        val regex = Regex("""0x[0-9a-fA-F]+""")
        return regex.find(output)?.value
    }

    internal fun isGnomeInhibited(): Boolean? {
        return runCatching {
            val out = runProcess(
                "gdbus", "call", "--session",
                "--dest", "org.gnome.SessionManager",
                "--object-path", "/org/gnome/SessionManager",
                "--method", "org.gnome.SessionManager.GetInhibitors",
            ) ?: return@runCatching null
            // Non-empty list means at least one inhibitor exists.
            // Example: "([objectpath '/org/gnome/SessionManager/Inhibitor123'],)"
            // We conservatively suppress on any inhibitor; video players
            // inhibit idle (flag 8) and fullscreen apps often inhibit as well.
            // Parsing: if it contains "/org/gnome/SessionManager/Inhibitor" -> inhibited
            if (out.contains("/org/gnome/SessionManager/Inhibitor")) {
                return@runCatching true
            }
            // Also try freedesktop ScreenSaver inhibit check as fallback
            val fsOut = runProcess(
                "gdbus", "call", "--session",
                "--dest", "org.freedesktop.ScreenSaver",
                "--object-path", "/org/freedesktop/ScreenSaver",
                "--method", "org.freedesktop.ScreenSaver.GetInhibitors",
            )
            if (fsOut != null && fsOut.contains("/org/")) true else false
        }.getOrNull()
    }

    internal fun isGnomeShellFullscreen(): Boolean? {
        return runCatching {
            val out = runProcess(
                "gdbus", "call", "--session",
                "--dest", "org.gnome.Shell",
                "--object-path", "/org/gnome/Shell",
                "--method", "org.gnome.Shell.Eval",
                "global.display.focus_window ? global.display.focus_window.is_fullscreen() : false",
            ) ?: return@runCatching null
            // Output: "(true, 'false')" or "(true, 'true')" — first bool is success, second is result
            out.contains("'true'") || out.contains("true") && out.contains("is_fullscreen")
        }.getOrNull()
    }

    private fun runProcess(vararg cmd: String): String? {
        return runCatching {
            val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
            if (!p.waitFor(800, TimeUnit.MILLISECONDS)) {
                p.destroyForcibly()
                return@runCatching null
            }
            if (p.exitValue() != 0) return@runCatching null
            p.inputStream.readBytes().decodeToString().trim().takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    // ---- Windows ----

    private fun isWindowsFullscreen(): Boolean {
        return runCatching {
            val hwnd = FullscreenUser32.INSTANCE.GetForegroundWindow() ?: return@runCatching false
            val rect = WinDef.RECT()
            val monitor = FullscreenUser32.INSTANCE.MonitorFromWindow(hwnd, 2) // MONITOR_DEFAULTTONEAREST
            if (monitor == null) return@runCatching false
            val info = WinDef.MONITORINFO()
            info.cbSize = 40
            if (FullscreenUser32.INSTANCE.GetMonitorInfo(monitor, info) == 0) return@runCatching false
            if (FullscreenUser32.INSTANCE.GetWindowRect(hwnd, rect) == 0) return@runCatching false

            val winW = rect.right - rect.left
            val winH = rect.bottom - rect.top
            val monW = info.rcMonitor.right - info.rcMonitor.left
            val monH = info.rcMonitor.bottom - info.rcMonitor.top

            // Fullscreen if window rect matches monitor rect (allow 2px tolerance for borders)
            kotlin.math.abs(winW - monW) <= 2 && kotlin.math.abs(winH - monH) <= 2
        }.getOrDefault(false)
    }
}

// Minimal JNA bindings for Windows fullscreen check (loaded lazily only on Windows)
private interface FullscreenUser32 : com.sun.jna.win32.StdCallLibrary {
    fun GetForegroundWindow(): com.sun.jna.Pointer?
    fun GetWindowRect(hwnd: com.sun.jna.Pointer, rect: WinDef.RECT): Int
    fun MonitorFromWindow(hwnd: com.sun.jna.Pointer, flags: Int): com.sun.jna.Pointer?
    fun GetMonitorInfo(monitor: com.sun.jna.Pointer, info: WinDef.MONITORINFO): Int
    companion object {
        val INSTANCE: FullscreenUser32 = com.sun.jna.Native.load("user32", FullscreenUser32::class.java)
    }
}

private object WinDef {
    class RECT : com.sun.jna.Structure() {
        @JvmField var left: Int = 0
        @JvmField var top: Int = 0
        @JvmField var right: Int = 0
        @JvmField var bottom: Int = 0
        override fun getFieldOrder() = listOf("left", "top", "right", "bottom")
    }
    class MONITORINFO : com.sun.jna.Structure() {
        @JvmField var cbSize: Int = 0
        @JvmField var rcMonitor: RECT = RECT()
        @JvmField var rcWork: RECT = RECT()
        @JvmField var dwFlags: Int = 0
        override fun getFieldOrder() = listOf("cbSize", "rcMonitor", "rcWork", "dwFlags")
    }
}
