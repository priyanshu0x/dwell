package com.droidslife.screensaver.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Base neutral palette shared across all modes. */
object DwellColors {
    val BgVoid     = Color(0xFF050507)
    val Surface0   = Color(0xFF0B0B0C)
    val Surface1   = Color(0xFF131316)
    val Stroke     = Color(0xFF1F1F22)
    val TextHigh   = Color(0xFFEAEAEA)
    val TextMid    = Color(0xFF8D92A2)
    val TextLow    = Color(0xFF6F7686)
    val TextFaint  = Color(0xFF5C6173)
    val StatusAccent = Color(0xFFF3A280)
    val StatusError  = Color(0xFFC46C6C)
    val StatusOk     = Color(0xFF6CBB8A)

    // Mode accents
    val DuskPeach   = Color(0xFFF3A280)
    val DuskViolet  = Color(0xFFB46CC4)
    val DuskMidnight= Color(0xFF3C50A0)
    val NoirGlow    = Color(0xFFFFF5E1) // warm white
    val LumenCyan   = Color(0xFF7ADCFF)
    val LumenMidnightDeep = Color(0xFF03060D)
    val LumenMidnight     = Color(0xFF050A18)
    val LumenNavy   = Color(0xFF0D2238)
    val BorealisNight    = Color(0xFF02030A)
    val BorealisNightDeep= Color(0xFF010108)
    val BorealisGreen    = Color(0xFF7BEFB1)
    val BorealisMagenta  = Color(0xFFD779E5)
    val BorealisTeal     = Color(0xFF52C8DC)
    val ConsoleGreen     = Color(0xFF9ECDA0)
    val ConsoleAmber     = Color(0xFFF3B95E)
}

/** 8pt spacing grid. */
object DwellSpacing {
    val xs: Dp = 4.dp
    val s : Dp = 8.dp
    val m : Dp = 16.dp
    val l : Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 48.dp
    val xxxl: Dp = 64.dp
}

/** Corner radii. */
object DwellRadius {
    val xs: Dp = 8.dp
    val m : Dp = 12.dp
    val l : Dp = 16.dp
    val xl: Dp = 24.dp
}

/** Animation durations (ms). */
object DwellMotion {
    const val MountFade = 800
    const val UnmountFade = 400
    const val ModeChange = 600
    const val SettingsSheetSlide = 350
    const val CornerHover = 180
    const val ToastFade = 200
    const val TileReflow = 200

    /** M3 standard easing. */
    val Standard = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** M3 emphasized easing (steeper start). */
    val Emphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
}
