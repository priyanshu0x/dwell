package com.droidslife.screensaver.daemon

import com.sun.jna.Native
import com.sun.jna.Structure
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.win32.StdCallLibrary

internal class WindowsIdleMonitor : IdleMonitor {
    override fun idleTimeMillis(): Long {
        val info = LastInputInfo()
        if (!User32.INSTANCE.GetLastInputInfo(info)) return 0L
        val tick = Kernel32.INSTANCE.GetTickCount().toLong() and 0xffffffffL
        val lastInput = info.dwTime.toLong() and 0xffffffffL
        return (tick - lastInput).coerceAtLeast(0L)
    }
}

private interface User32 : StdCallLibrary {
    fun GetLastInputInfo(info: LastInputInfo): Boolean

    companion object {
        val INSTANCE: User32 = Native.load("user32", User32::class.java)
    }
}

class LastInputInfo : Structure() {
    @JvmField
    var cbSize: Int = 0

    @JvmField
    var dwTime: Int = 0

    init {
        cbSize = size()
    }

    override fun getFieldOrder(): List<String> = listOf("cbSize", "dwTime")
}
