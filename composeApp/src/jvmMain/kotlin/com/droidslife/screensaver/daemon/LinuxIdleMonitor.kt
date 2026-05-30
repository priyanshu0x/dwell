package com.droidslife.screensaver.daemon

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.Structure
import java.util.concurrent.TimeUnit

internal class LinuxIdleMonitor : IdleMonitor {
    override fun idleTimeMillis(): Long {
        val sessionType = System.getenv("XDG_SESSION_TYPE")?.lowercase()
        val hasWaylandDisplay = !System.getenv("WAYLAND_DISPLAY").isNullOrBlank()
        return when {
            sessionType == "wayland" || hasWaylandDisplay ->
                waylandIdleMillis() ?: x11IdleMillis() ?: xprintIdleMillis() ?: 0L
            else ->
                x11IdleMillis() ?: xprintIdleMillis() ?: waylandIdleMillis() ?: 0L
        }
    }

    private fun waylandIdleMillis(): Long? {
        return runCatching {
            val process = ProcessBuilder(
                "gdbus",
                "call",
                "--session",
                "--dest",
                "org.gnome.Mutter.IdleMonitor",
                "--object-path",
                "/org/gnome/Mutter/IdleMonitor/Core",
                "--method",
                "org.gnome.Mutter.IdleMonitor.GetIdletime",
            ).redirectErrorStream(true).start()
            if (!process.waitFor(800, TimeUnit.MILLISECONDS) || process.exitValue() != 0) return@runCatching null
            parseGnomeIdleMillis(process.inputStream.readBytes().decodeToString())
        }.getOrNull()
    }

    private fun x11IdleMillis(): Long? {
        return runCatching {
            val display = X11.INSTANCE.XOpenDisplay(null) ?: return@runCatching null
            try {
                val root = X11.INSTANCE.XDefaultRootWindow(display)
                val info = Xss.INSTANCE.XScreenSaverAllocInfo() ?: return@runCatching null
                val ok = Xss.INSTANCE.XScreenSaverQueryInfo(display, root, info) != 0
                if (ok) info.idle.toLong() else null
            } finally {
                X11.INSTANCE.XCloseDisplay(display)
            }
        }.getOrNull()
    }

    private fun xprintIdleMillis(): Long? {
        return runCatching {
            val process = ProcessBuilder("xprintidle").redirectErrorStream(true).start()
            if (!process.waitFor(800, TimeUnit.MILLISECONDS) || process.exitValue() != 0) return@runCatching null
            process.inputStream.readBytes().decodeToString().trim().toLongOrNull()
        }.getOrNull()
    }
}

internal fun parseGnomeIdleMillis(output: String): Long? {
    return Regex("""uint64\s+(\d+)""")
        .find(output)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()
}

private interface X11 : Library {
    fun XOpenDisplay(displayName: String?): Pointer?
    fun XDefaultRootWindow(display: Pointer): NativeLong
    fun XCloseDisplay(display: Pointer): Int

    companion object {
        val INSTANCE: X11 = Native.load("X11", X11::class.java)
    }
}

private interface Xss : Library {
    fun XScreenSaverAllocInfo(): XScreenSaverInfo?
    fun XScreenSaverQueryInfo(display: Pointer, drawable: NativeLong, info: XScreenSaverInfo): Int

    companion object {
        val INSTANCE: Xss = Native.load("Xss", Xss::class.java)
    }
}

private class XScreenSaverInfo : Structure() {
    @JvmField
    var window: NativeLong = NativeLong(0)

    @JvmField
    var state: Int = 0

    @JvmField
    var kind: Int = 0

    @JvmField
    var til_or_since: NativeLong = NativeLong(0)

    @JvmField
    var idle: NativeLong = NativeLong(0)

    @JvmField
    var event_mask: NativeLong = NativeLong(0)

    override fun getFieldOrder(): List<String> = listOf(
        "window",
        "state",
        "kind",
        "til_or_since",
        "idle",
        "event_mask",
    )
}
