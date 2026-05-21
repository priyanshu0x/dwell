package com.droidslife.screensaver.widget.api

/**
 * Current stable widget API version understood by this host.
 *
 * Third-party widgets should return this value from [WidgetFactory.apiVersion]
 * unless they intentionally target an older compatibility surface.
 */
const val WIDGET_API_VERSION = 1
