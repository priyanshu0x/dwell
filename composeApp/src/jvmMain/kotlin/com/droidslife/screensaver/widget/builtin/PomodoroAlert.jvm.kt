package com.droidslife.screensaver.widget.builtin

import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * JVM alert: a soft synthesized two-tone bell plus an optional tray
 * notification. Everything runs on a daemon thread and is wrapped in
 * runCatching so a headless or audio-less environment never propagates an
 * exception into the widget's tick loop.
 */
actual fun firePomodoroAlert(alert: PomodoroAlert) {
    Thread {
        if (alert.playSound) runCatching { playChime() }
        if (alert.showNotification) runCatching { showNotification(alert.title, alert.message) }
    }.apply { isDaemon = true }.start()
}

private const val SAMPLE_RATE = 44_100f

/** Two descending bell tones (G5 then C5) with an exponential decay envelope. */
private fun playChime() {
    val format = AudioFormat(SAMPLE_RATE, 16, 1, true, false)
    val line: SourceDataLine = AudioSystem.getSourceDataLine(format)
    line.open(format)
    line.start()
    try {
        line.writeTone(frequencyHz = 784.0, seconds = 0.22, gain = 0.5)
        line.writeTone(frequencyHz = 523.0, seconds = 0.42, gain = 0.5)
        line.drain()
    } finally {
        line.stop()
        line.close()
    }
}

private fun SourceDataLine.writeTone(frequencyHz: Double, seconds: Double, gain: Double) {
    val sampleCount = (SAMPLE_RATE * seconds).toInt()
    val buffer = ByteArray(sampleCount * 2)
    for (i in 0 until sampleCount) {
        val t = i / SAMPLE_RATE.toDouble()
        // Decay envelope so the tone rings out like a bell instead of clicking.
        val envelope = exp(-3.0 * t / seconds)
        val sample = sin(2.0 * PI * frequencyHz * t) * envelope * gain
        val value = (sample * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767)
        buffer[i * 2] = (value and 0xFF).toByte()
        buffer[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
    }
    write(buffer, 0, buffer.size)
}

// Reused across alerts so we don't accrue tray icons. Alerts fire on separate
// daemon threads, so create-once is guarded to avoid adding two icons in a race.
@Volatile
private var notifierIcon: TrayIcon? = null
private val notifierLock = Any()

private fun showNotification(title: String, message: String) {
    if (!SystemTray.isSupported()) return
    val icon = notifierIcon ?: synchronized(notifierLock) {
        notifierIcon ?: run {
            val image = Toolkit.getDefaultToolkit().createImage(ByteArray(0))
            TrayIcon(image, "Dwell Pomodoro").apply {
                isImageAutoSize = true
                SystemTray.getSystemTray().add(this)
                notifierIcon = this
            }
        }
    }
    icon.displayMessage(title, message, TrayIcon.MessageType.INFO)
}
