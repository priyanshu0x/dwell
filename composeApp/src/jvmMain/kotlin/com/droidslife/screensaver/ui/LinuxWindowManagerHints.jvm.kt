package com.droidslife.screensaver.ui

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.platform.unix.X11
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.NativeLongByReference
import com.sun.jna.ptr.PointerByReference
import java.awt.Toolkit
import java.awt.Window
import javax.swing.SwingUtilities
import javax.swing.Timer

internal object LinuxWindowManagerHints {
    private const val DWELL_WM_CLASS = "Dwell"

    fun configureDwellAppClassName() {
        if (!isLinuxX11()) return

        runCatching {
            Toolkit.getDefaultToolkit()
            val xToolkit = Class.forName("sun.awt.X11.XToolkit")
            val appClassName = xToolkit.getDeclaredField("awtAppClassName")
            appClassName.isAccessible = true
            appClassName.set(null, DWELL_WM_CLASS)
        }
    }

    fun applyDwellWindowHints(window: Window) {
        if (!isLinuxX11()) return

        applyDwellWindowHintsOnce(window)
        SwingUtilities.invokeLater { applyDwellWindowHintsOnce(window) }
        listOf(150, 500, 1_200).forEach { delayMillis ->
            Timer(delayMillis) { applyDwellWindowHintsOnce(window) }.apply {
                isRepeats = false
                start()
            }
        }
    }

    private fun applyDwellWindowHintsOnce(window: Window) {
        runCatching {
            if (!window.isDisplayable) return
            val windowId = Native.getWindowID(window)
            if (windowId == 0L) return

            val x11 = X11.INSTANCE
            val display = x11.XOpenDisplay(null) ?: return
            try {
                val xWindow = X11.Window(windowId)
                setWindowClass(x11, display, xWindow)
                clearDemandsAttention(x11, display, xWindow)
                x11.XFlush(display)
            } finally {
                x11.XCloseDisplay(display)
            }
        }
    }

    private fun setWindowClass(x11: X11, display: X11.Display, window: X11.Window) {
        val bytes = "$DWELL_WM_CLASS\u0000$DWELL_WM_CLASS\u0000".toByteArray(Charsets.ISO_8859_1)
        val data = Memory(bytes.size.toLong())
        data.write(0, bytes, 0, bytes.size)
        x11.XChangeProperty(
            display,
            window,
            X11.XA_WM_CLASS,
            X11.XA_STRING,
            8,
            X11.PropModeReplace,
            data,
            bytes.size,
        )
    }

    private fun clearDemandsAttention(x11: X11, display: X11.Display, window: X11.Window) {
        val netWmState = x11.XInternAtom(display, "_NET_WM_STATE", false)
        val demandsAttention = x11.XInternAtom(display, "_NET_WM_STATE_DEMANDS_ATTENTION", false)
        if (netWmState == X11.Atom.None || demandsAttention == X11.Atom.None) return

        val actualType = X11.AtomByReference()
        val actualFormat = IntByReference()
        val itemCount = NativeLongByReference()
        val bytesAfter = NativeLongByReference()
        val property = PointerByReference()

        val result = x11.XGetWindowProperty(
            display,
            window,
            netWmState,
            NativeLong(0),
            NativeLong(1024),
            false,
            X11.XA_ATOM,
            actualType,
            actualFormat,
            itemCount,
            bytesAfter,
            property,
        )
        if (result != X11.Success) return

        val pointer = property.value ?: return
        try {
            if (actualType.value != X11.XA_ATOM || actualFormat.value != 32) return
            val existingStates = (0 until itemCount.value.toInt()).map { index ->
                pointer.getNativeLong((index * NativeLong.SIZE).toLong())
            }
            val filteredStates = existingStates.filter { it.toLong() != demandsAttention.toLong() }
            if (filteredStates.size == existingStates.size) return

            if (filteredStates.isEmpty()) {
                x11.XDeleteProperty(display, window, netWmState)
                return
            }

            val data = Memory((filteredStates.size * NativeLong.SIZE).toLong())
            filteredStates.forEachIndexed { index, state ->
                data.setNativeLong((index * NativeLong.SIZE).toLong(), state)
            }
            x11.XChangeProperty(
                display,
                window,
                netWmState,
                X11.XA_ATOM,
                32,
                X11.PropModeReplace,
                data,
                filteredStates.size,
            )
        } finally {
            x11.XFree(pointer)
        }
    }

    private fun isLinuxX11(): Boolean =
        System.getProperty("os.name").contains("Linux", ignoreCase = true) &&
            !System.getenv("DISPLAY").isNullOrBlank()
}
