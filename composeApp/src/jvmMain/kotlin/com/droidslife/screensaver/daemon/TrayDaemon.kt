package com.droidslife.screensaver.daemon

import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

class TrayDaemon {
    private var trayIcon: TrayIcon? = null

    fun install(
        onShow: () -> Unit,
        onSettings: () -> Unit,
        onReloadWidgets: () -> Unit,
        onQuit: () -> Unit,
    ) {
        if (!SystemTray.isSupported() || trayIcon != null) return

        val popup = PopupMenu().apply {
            add(MenuItem("Show").also { it.addActionListener { onShow() } })
            add(MenuItem("Settings").also { it.addActionListener { onSettings() } })
            add(MenuItem("Reload Widgets").also { it.addActionListener { onReloadWidgets() } })
            addSeparator()
            add(MenuItem("Quit").also { it.addActionListener { onQuit() } })
        }
        val icon = TrayIcon(defaultImage(), "Dwell", popup).apply {
            isImageAutoSize = true
            addActionListener { onShow() }
        }
        runCatching {
            SystemTray.getSystemTray().add(icon)
            trayIcon = icon
        }
    }

    fun remove() {
        trayIcon?.let { icon ->
            runCatching { SystemTray.getSystemTray().remove(icon) }
        }
        trayIcon = null
    }

    private fun defaultImage(): BufferedImage {
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.color = java.awt.Color(40, 132, 255)
        graphics.fillOval(2, 2, 12, 12)
        graphics.color = java.awt.Color.WHITE
        graphics.fillOval(6, 6, 4, 4)
        graphics.dispose()
        return image
    }
}
