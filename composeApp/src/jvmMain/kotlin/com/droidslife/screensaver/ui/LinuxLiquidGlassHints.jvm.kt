package com.droidslife.screensaver.ui

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.platform.unix.X11
import java.awt.Window
import javax.swing.SwingUtilities
import javax.swing.Timer

internal object LinuxLiquidGlassHints {
    fun applyBlurBehind(
        window: Window,
        enabled: Boolean,
    ) {
        if (!isLinuxX11()) return

        applyBlurBehindOnce(window, enabled)
        SwingUtilities.invokeLater { applyBlurBehindOnce(window, enabled) }
        listOf(150, 500, 1_200).forEach { delayMillis ->
            Timer(delayMillis) { applyBlurBehindOnce(window, enabled) }.apply {
                isRepeats = false
                start()
            }
        }
    }

    private fun applyBlurBehindOnce(
        window: Window,
        enabled: Boolean,
    ) {
        runCatching {
            if (!window.isDisplayable) return
            val windowId = Native.getWindowID(window)
            if (windowId == 0L) return

            val x11 = X11.INSTANCE
            val display = x11.XOpenDisplay(null) ?: return
            try {
                val xWindow = X11.Window(windowId)
                val blurRegion = x11.XInternAtom(display, "_KDE_NET_WM_BLUR_BEHIND_REGION", false)
                if (blurRegion == X11.Atom.None) return

                if (!enabled) {
                    x11.XDeleteProperty(display, xWindow, blurRegion)
                    x11.XFlush(display)
                    return
                }

                val cardinal = x11.XInternAtom(display, "CARDINAL", false)
                if (cardinal == X11.Atom.None) return

                val data = Memory(16)
                data.setInt(0, 0)
                data.setInt(4, 0)
                data.setInt(8, window.width.coerceAtLeast(1))
                data.setInt(12, window.height.coerceAtLeast(1))
                x11.XChangeProperty(
                    display,
                    xWindow,
                    blurRegion,
                    cardinal,
                    32,
                    X11.PropModeReplace,
                    data,
                    4,
                )
                x11.XFlush(display)
            } finally {
                x11.XCloseDisplay(display)
            }
        }
    }

    private fun isLinuxX11(): Boolean {
        if (!System.getProperty("os.name").contains("Linux", ignoreCase = true)) return false
        if (System.getenv("DISPLAY").isNullOrBlank()) return false
        if (!System.getenv("WAYLAND_DISPLAY").isNullOrBlank()) return false

        val sessionType = System.getenv("XDG_SESSION_TYPE").orEmpty().lowercase()
        if (sessionType.isNotBlank() && sessionType != "x11") return false

        val desktop = listOf(
            System.getenv("XDG_CURRENT_DESKTOP"),
            System.getenv("XDG_SESSION_DESKTOP"),
            System.getenv("DESKTOP_SESSION"),
        ).filterNotNull().joinToString(":").lowercase()
        return desktop.contains("kde") || desktop.contains("plasma") || desktop.contains("kwin")
    }
}
