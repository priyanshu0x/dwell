package com.droidslife.screensaver.ui

import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Point
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.image.BufferedImage

actual val ResizeSEPointerIcon: PointerIcon = PointerIcon(Cursor(Cursor.SE_RESIZE_CURSOR))

/**
 * Closed-fist "grabbing" cursor for active drag. AWT has no built-in
 * grab/grabbing cursor, so we paint a minimal 24×24 fist with knuckles and
 * register it via [Toolkit.createCustomCursor]. Falls back to the system
 * HAND cursor if the toolkit rejects the size (older X servers).
 */
actual val GrabbingPointerIcon: PointerIcon by lazy {
    val cursor = runCatching {
        val size = 24
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        // Wrist
        g.color = Color(245, 220, 200)
        g.fillRoundRect(7, 18, 10, 5, 4, 4)
        // Fist
        g.color = Color(245, 220, 200)
        g.fillRoundRect(4, 6, 16, 14, 8, 8)
        // Outline
        g.color = Color(0, 0, 0, 220)
        g.stroke = BasicStroke(1.4f)
        g.drawRoundRect(4, 6, 16, 14, 8, 8)
        g.drawRoundRect(7, 18, 10, 5, 4, 4)
        // Knuckle lines
        g.color = Color(0, 0, 0, 160)
        g.stroke = BasicStroke(1f)
        g.drawLine(8, 10, 8, 14)
        g.drawLine(12, 10, 12, 14)
        g.drawLine(16, 10, 16, 14)
        g.dispose()
        Toolkit.getDefaultToolkit().createCustomCursor(image, Point(12, 12), "grabbing")
    }.getOrElse { Cursor(Cursor.HAND_CURSOR) }
    PointerIcon(cursor)
}
