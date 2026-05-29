package com.droidslife.screensaver.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import screen_saver_app.composeapp.generated.resources.InterTight_Bold
import screen_saver_app.composeapp.generated.resources.InterTight_ExtraLight
import screen_saver_app.composeapp.generated.resources.InterTight_Light
import screen_saver_app.composeapp.generated.resources.InterTight_Medium
import screen_saver_app.composeapp.generated.resources.InterTight_Regular
import screen_saver_app.composeapp.generated.resources.InterTight_SemiBold
import screen_saver_app.composeapp.generated.resources.JetBrainsMono_ExtraLight
import screen_saver_app.composeapp.generated.resources.JetBrainsMono_Light
import screen_saver_app.composeapp.generated.resources.JetBrainsMono_Medium
import screen_saver_app.composeapp.generated.resources.JetBrainsMono_Regular
import screen_saver_app.composeapp.generated.resources.Res

/** Bundled font families. Loaded via Compose Resources. */
object DwellFonts {
    @Composable
    fun interTight(): FontFamily {
        val slot = remember { FontFamilySlot() }
        return slot.family ?: FontFamily(
            Font(Res.font.InterTight_ExtraLight, FontWeight.ExtraLight),
            Font(Res.font.InterTight_Light, FontWeight.Light),
            Font(Res.font.InterTight_Regular, FontWeight.Normal),
            Font(Res.font.InterTight_Medium, FontWeight.Medium),
            Font(Res.font.InterTight_SemiBold, FontWeight.SemiBold),
            Font(Res.font.InterTight_Bold, FontWeight.Bold),
        ).also { slot.family = it }
    }

    @Composable
    fun jetBrainsMono(): FontFamily {
        val slot = remember { FontFamilySlot() }
        return slot.family ?: FontFamily(
            Font(Res.font.JetBrainsMono_ExtraLight, FontWeight.ExtraLight),
            Font(Res.font.JetBrainsMono_Light, FontWeight.Light),
            Font(Res.font.JetBrainsMono_Regular, FontWeight.Normal),
            Font(Res.font.JetBrainsMono_Medium, FontWeight.Medium),
        ).also { slot.family = it }
    }
}

private class FontFamilySlot {
    var family: FontFamily? = null
}
