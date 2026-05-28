package com.droidslife.screensaver.daemon

import com.droidslife.screensaver.settings.AmbientVariant
import com.droidslife.screensaver.settings.CinematicVariant
import com.droidslife.screensaver.settings.ConsoleVariant
import com.droidslife.screensaver.settings.Mode
import com.droidslife.screensaver.settings.SettingsModel
import java.awt.CheckboxMenuItem
import java.awt.Image
import java.awt.Menu
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * System-tray entry point for Dwell's daemon mode.
 *
 * The menu mirrors the most-used settings so users don't have to open the
 * dashboard to switch mode/variant or toggle start-at-login.
 */
class TrayDaemon {
    private var trayIcon: TrayIcon? = null

    fun install(
        getSettings: () -> SettingsModel,
        onShow: () -> Unit,
        onSettings: () -> Unit,
        onSetMode: (Mode) -> Unit,
        onSetCinematicVariant: (CinematicVariant) -> Unit,
        onSetAmbientVariant: (AmbientVariant) -> Unit,
        onSetConsoleVariant: (ConsoleVariant) -> Unit,
        onSetStartWithSystem: (Boolean) -> Unit,
        onReloadWidgets: () -> Unit,
        onQuit: () -> Unit,
    ) {
        if (!SystemTray.isSupported() || trayIcon != null) return

        val popup = buildPopup(
            getSettings,
            onShow, onSettings,
            onSetMode, onSetCinematicVariant, onSetAmbientVariant, onSetConsoleVariant,
            onSetStartWithSystem, onReloadWidgets, onQuit,
        )

        val icon = TrayIcon(loadIcon(), "Dwell", popup).apply {
            isImageAutoSize = true
            addActionListener { onShow() }
        }
        runCatching {
            SystemTray.getSystemTray().add(icon)
            trayIcon = icon
        }
    }

    /**
     * Rebuild the popup so checkmarks reflect the current settings. Call after
     * settings change (e.g. from the dashboard or another tray menu click).
     */
    fun refresh(
        getSettings: () -> SettingsModel,
        onShow: () -> Unit,
        onSettings: () -> Unit,
        onSetMode: (Mode) -> Unit,
        onSetCinematicVariant: (CinematicVariant) -> Unit,
        onSetAmbientVariant: (AmbientVariant) -> Unit,
        onSetConsoleVariant: (ConsoleVariant) -> Unit,
        onSetStartWithSystem: (Boolean) -> Unit,
        onReloadWidgets: () -> Unit,
        onQuit: () -> Unit,
    ) {
        val icon = trayIcon ?: return
        icon.popupMenu = buildPopup(
            getSettings,
            onShow, onSettings,
            onSetMode, onSetCinematicVariant, onSetAmbientVariant, onSetConsoleVariant,
            onSetStartWithSystem, onReloadWidgets, onQuit,
        )
    }

    fun remove() {
        trayIcon?.let { icon -> runCatching { SystemTray.getSystemTray().remove(icon) } }
        trayIcon = null
    }

    private fun buildPopup(
        getSettings: () -> SettingsModel,
        onShow: () -> Unit,
        onSettings: () -> Unit,
        onSetMode: (Mode) -> Unit,
        onSetCinematicVariant: (CinematicVariant) -> Unit,
        onSetAmbientVariant: (AmbientVariant) -> Unit,
        onSetConsoleVariant: (ConsoleVariant) -> Unit,
        onSetStartWithSystem: (Boolean) -> Unit,
        onReloadWidgets: () -> Unit,
        onQuit: () -> Unit,
    ): PopupMenu {
        val s = getSettings()
        return PopupMenu().apply {
            add(MenuItem("Show now (Ctrl+Alt+Space)").also { it.addActionListener { onShow() } })
            add(MenuItem("Settings…").also { it.addActionListener { onSettings() } })
            addSeparator()

            // Mode submenu
            add(Menu("Mode").apply {
                Mode.entries.forEach { mode ->
                    val item = CheckboxMenuItem(mode.label(), s.mode == mode)
                    item.addItemListener { onSetMode(mode) }
                    add(item)
                }
            })

            // Variant submenu — only the variants for the current mode
            add(Menu("Variant").apply {
                when (s.mode) {
                    Mode.Cinematic -> CinematicVariant.entries.forEach { v ->
                        val item = CheckboxMenuItem(v.label(), s.cinematicVariant == v)
                        item.addItemListener { onSetCinematicVariant(v) }
                        add(item)
                    }
                    Mode.Ambient -> AmbientVariant.entries.forEach { v ->
                        val item = CheckboxMenuItem(v.label(), s.ambientVariant == v)
                        item.addItemListener { onSetAmbientVariant(v) }
                        add(item)
                    }
                    Mode.Console -> ConsoleVariant.entries.forEach { v ->
                        val item = CheckboxMenuItem(v.label(), s.consoleVariant == v)
                        item.addItemListener { onSetConsoleVariant(v) }
                        add(item)
                    }
                }
            })

            addSeparator()
            add(CheckboxMenuItem("Start at login", s.startWithSystem).also {
                it.addItemListener { e -> onSetStartWithSystem(e.stateChange == java.awt.event.ItemEvent.SELECTED) }
            })
            add(MenuItem("Reload widgets").also { it.addActionListener { onReloadWidgets() } })
            add(MenuItem("Run doctor…").also { it.addActionListener { runDoctorInBackground() } })
            add(MenuItem("About Dwell…").also { it.addActionListener { showAboutDialog() } })

            addSeparator()
            add(MenuItem("Quit Dwell").also { it.addActionListener { onQuit() } })
        }
    }

    /**
     * Invokes the bundled `dwell doctor` CLI and shows its output in a Swing
     * dialog so non-CLI users can run the same diagnostic the docs reference.
     */
    private fun runDoctorInBackground() {
        Thread {
            val cmd = if (isWindows()) {
                listOf("cmd.exe", "/c", "scripts\\dwell.cmd", "doctor")
            } else {
                listOf("sh", "./scripts/dwell", "doctor")
            }
            val output = runCatching {
                val process = ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start()
                process.inputStream.bufferedReader().use { it.readText() }
                    .also { process.waitFor() }
            }.getOrElse { e -> "Couldn't run dwell doctor: ${e.message}" }

            javax.swing.SwingUtilities.invokeLater {
                val textArea = javax.swing.JTextArea(output).apply {
                    isEditable = false
                    font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12)
                }
                val scroll = javax.swing.JScrollPane(textArea).apply {
                    preferredSize = java.awt.Dimension(640, 360)
                }
                javax.swing.JOptionPane.showMessageDialog(
                    null, scroll, "Dwell doctor", javax.swing.JOptionPane.PLAIN_MESSAGE,
                )
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun showAboutDialog() {
        val version = runCatching {
            Class.forName("com.droidslife.screensaver.BuildInfo")
                .getField("VERSION").get(null) as? String
        }.getOrNull().orEmpty()
        val commit = runCatching {
            Class.forName("com.droidslife.screensaver.BuildInfo")
                .getField("COMMIT").get(null) as? String
        }.getOrNull().orEmpty()
        val message = buildString {
            appendLine("Dwell — a calm three-mode dashboard for the screensaver hour.")
            appendLine()
            if (version.isNotBlank()) appendLine("Version: $version")
            if (commit.isNotBlank()) appendLine("Build:   $commit")
            appendLine("License: MIT")
        }
        javax.swing.SwingUtilities.invokeLater {
            javax.swing.JOptionPane.showMessageDialog(
                null, message, "About Dwell", javax.swing.JOptionPane.INFORMATION_MESSAGE,
            )
        }
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("win")

    private fun loadIcon(): Image {
        // Prefer the bundled brand icon; fall back to the dot-glyph if unavailable.
        val resourcePaths = listOf(
            "/icons/tray.png",
            "/LinuxIcon.png",
        )
        for (path in resourcePaths) {
            javaClass.getResourceAsStream(path)?.use { stream ->
                runCatching { return ImageIO.read(stream) }
            }
        }
        // File-system fallback (works during gradle :composeApp:run in dev mode)
        val devFile = java.io.File("composeApp/desktopAppIcons/LinuxIcon.png")
        if (devFile.exists()) {
            runCatching { return Toolkit.getDefaultToolkit().getImage(devFile.absolutePath) }
        }
        return fallbackImage()
    }

    private fun fallbackImage(): BufferedImage {
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

private fun Mode.label(): String = when (this) {
    Mode.Cinematic -> "Cinematic"
    Mode.Ambient -> "Ambient"
    Mode.Console -> "Console"
}

private fun CinematicVariant.label(): String = when (this) {
    CinematicVariant.Dusk -> "Dusk"
    CinematicVariant.Noir -> "Noir"
}

private fun AmbientVariant.label(): String = when (this) {
    AmbientVariant.Lumen -> "Lumen"
    AmbientVariant.Borealis -> "Borealis"
}

private fun ConsoleVariant.label(): String = when (this) {
    ConsoleVariant.Standard -> "Standard"
    ConsoleVariant.Amber -> "Amber"
}
