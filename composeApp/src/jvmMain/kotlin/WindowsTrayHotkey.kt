import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.platform.win32.WinUser.MSG
import javax.swing.SwingUtilities

internal class WindowsTrayHotkey(
    private val onShowDashboard: () -> Unit,
) : AutoCloseable {
    @Volatile private var closed = false
    @Volatile private var threadId = 0
    private var thread: Thread? = null

    fun start() {
        if (!isWindows() || thread != null) return
        thread = Thread(::runMessageLoop, "Dwell tray restore hotkey").apply {
            isDaemon = true
            start()
        }
    }

    private fun runMessageLoop() {
        threadId = Kernel32.INSTANCE.GetCurrentThreadId()
        if (!User32.INSTANCE.RegisterHotKey(null, HOTKEY_ID, MOD_CONTROL or MOD_ALT or MOD_NOREPEAT, VK_SPACE)) return

        try {
            val msg = MSG()
            while (!closed && User32.INSTANCE.GetMessage(msg, null, 0, 0) != 0) {
                if (msg.message == WinUser.WM_HOTKEY && msg.wParam.toInt() == HOTKEY_ID) {
                    SwingUtilities.invokeLater(onShowDashboard)
                }
            }
        } finally {
            User32.INSTANCE.UnregisterHotKey(null, HOTKEY_ID)
        }
    }

    override fun close() {
        closed = true
        val id = threadId
        if (id != 0) {
            User32.INSTANCE.PostThreadMessage(id, WinUser.WM_QUIT, WPARAM(0), LPARAM(0))
        }
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    private companion object {
        const val HOTKEY_ID = 0x4457
        const val MOD_ALT = 0x0001
        const val MOD_CONTROL = 0x0002
        const val MOD_NOREPEAT = 0x4000
        const val VK_SPACE = 0x20
    }
}
