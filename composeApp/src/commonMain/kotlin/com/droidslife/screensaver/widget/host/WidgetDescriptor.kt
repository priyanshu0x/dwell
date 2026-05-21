package com.droidslife.screensaver.widget.host

import com.droidslife.screensaver.widget.api.WidgetCategory
import com.droidslife.screensaver.widget.api.WidgetFactory

data class WidgetDescriptor(
    val id: String,
    val displayName: String,
    val category: WidgetCategory,
    val factory: WidgetFactory,
    val source: WidgetSource,
)

sealed interface WidgetSource {
    data object BuiltIn : WidgetSource
    data class Jar(val path: String) : WidgetSource
    data class Declarative(val folder: String) : WidgetSource
}
