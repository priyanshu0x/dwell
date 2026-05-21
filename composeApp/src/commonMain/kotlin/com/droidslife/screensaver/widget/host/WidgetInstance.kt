package com.droidslife.screensaver.widget.host

import com.droidslife.screensaver.widget.api.Widget
import com.droidslife.screensaver.widget.api.WidgetConfig
import com.droidslife.screensaver.widget.api.WidgetScope

data class WidgetInstance(
    val descriptor: WidgetDescriptor,
    val widget: Widget,
    val config: WidgetConfig,
    val scope: WidgetScope,
)
