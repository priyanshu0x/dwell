package com.droidslife.screensaver.widget.host

interface WidgetLoader {
    fun discoverAll(): List<WidgetDescriptor>
}

expect fun createWidgetLoader(): WidgetLoader
