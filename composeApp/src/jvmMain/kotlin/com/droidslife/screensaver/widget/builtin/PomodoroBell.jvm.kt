package com.droidslife.screensaver.widget.builtin

import java.awt.Toolkit

/**
 * JVM bell: delegate to the AWT toolkit beep. Wrapped in runCatching so a
 * headless or audio-less environment never propagates an exception into the
 * widget's tick loop.
 */
actual fun playPomodoroBell() {
    runCatching { Toolkit.getDefaultToolkit().beep() }
}
