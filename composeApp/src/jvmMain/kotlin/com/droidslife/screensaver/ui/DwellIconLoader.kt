package com.droidslife.screensaver.ui

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

internal object DwellIconLoader {
    private val resourcePaths = listOf(
        "/LinuxIcon.png",
        "/icons/tray.png",
    )

    private val filePaths = listOf(
        "desktopAppIcons/LinuxIcon.png",
        "composeApp/desktopAppIcons/LinuxIcon.png",
        "../composeApp/desktopAppIcons/LinuxIcon.png",
    )

    fun load(anchor: Class<*> = DwellIconLoader::class.java): BufferedImage? {
        for (path in resourcePaths) {
            anchor.getResourceAsStream(path)?.use { stream ->
                runCatching { ImageIO.read(stream) }.getOrNull()?.let { return it }
            }
        }

        for (path in filePaths) {
            val file = File(path)
            if (file.exists()) {
                runCatching { ImageIO.read(file) }.getOrNull()?.let { return it }
            }
        }

        return null
    }
}
