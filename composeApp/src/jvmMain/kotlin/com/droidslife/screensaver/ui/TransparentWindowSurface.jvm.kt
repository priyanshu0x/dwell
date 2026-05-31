package com.droidslife.screensaver.ui

import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Window
import javax.swing.JComponent
import javax.swing.RootPaneContainer

internal object TransparentWindowSurface {
    private val Transparent = Color(0, 0, 0, 0)

    fun apply(window: Window, enabled: Boolean) {
        if (!enabled) return

        runCatching {
            window.background = Transparent
            val rootPaneContainer = window as? RootPaneContainer
            rootPaneContainer?.rootPane?.isOpaque = false
            (rootPaneContainer?.contentPane as? JComponent)?.isOpaque = false
            rootPaneContainer?.layeredPane?.isOpaque = false
            (rootPaneContainer?.glassPane as? JComponent)?.isOpaque = false
            window.components.forEach(::makeTransparent)
        }
    }

    private fun makeTransparent(component: Component) {
        (component as? JComponent)?.isOpaque = false
        (component as? Container)?.components?.forEach(::makeTransparent)
    }
}
