package com.droidslife.screensaver

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.theme.AppTheme
import kotlinx.datetime.Instant
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import org.koin.compose.KoinContext
import org.koin.compose.koinInject

@Composable
internal fun App(
    showCitySelectionDialog: Boolean = false, 
    onCityDialogDismiss: () -> Unit = {},
    onShowCityDialog: () -> Unit = {},
    exitOnMouseMovementEnabled: Boolean = true,
    onExitApplication: () -> Unit = {},
    exitRequested: Boolean = false,
    onExited: () -> Unit = onExitApplication,
    showHelpDialog: Boolean = false,
    onHelpDialogDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) = KoinContext {
        // Get the settings view model to access the theme preference
        val settingsViewModel = koinInject<SettingsViewModel>()
        var visible by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            visible = true
        }

        LaunchedEffect(exitRequested) {
            if (exitRequested) {
                visible = false
            }
        }

        LaunchedEffect(visible, exitRequested) {
            if (exitRequested && !visible) {
                kotlinx.coroutines.delay(450)
                onExited()
            }
        }

        AppTheme(isDark = settingsViewModel.settings.isDarkTheme) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(800)) + scaleIn(
                    animationSpec = tween(800),
                    initialScale = 0.96f,
                ),
                exit = fadeOut(tween(400)),
            ) {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DigitalClockApp(
                        showCitySelectionDialog = showCitySelectionDialog,
                        onCityDialogDismiss = onCityDialogDismiss,
                        onShowCityDialog = onShowCityDialog,
                        exitOnMouseMovementEnabled = exitOnMouseMovementEnabled,
                        onExitApplication = onExitApplication,
                        showHelpDialog = showHelpDialog,
                        onHelpDialogDismiss = onHelpDialogDismiss,
                        onShowHelpDialog = { /* This is a no-op because we can't set showHelpDialog to true here */ }
                    )
                }
            }
        }
    }

@OptIn(FormatStringsInDatetimeFormats::class)
private fun Instant.dateFormat(format:String? =null): String {
    return format(DateTimeComponents.Format {
        byUnicodePattern(format?:"dd/MM/yyyy")
    })
}
