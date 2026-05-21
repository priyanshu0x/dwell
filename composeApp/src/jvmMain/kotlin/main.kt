import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.*
import com.droidslife.screensaver.App
import com.droidslife.screensaver.components.ShortcutToast
import com.droidslife.screensaver.components.rememberWindowEventHandlers
import com.droidslife.screensaver.di.appModule
import com.droidslife.screensaver.di.initKoin
import org.koin.compose.KoinApplication

fun main() = application {
    initKoin {
        modules(appModule)
    }

    val windowEvents = rememberWindowEventHandlers(::exitApplication)

    Window(
        title = "Screen Saver App",
        state = rememberWindowState(
            placement = WindowPlacement.Fullscreen,
            position = WindowPosition(Alignment.Center),
        ),
        onCloseRequest = ::exitApplication,
        resizable = false,
        alwaysOnTop = true,
        undecorated = true,
        onKeyEvent = { event -> windowEvents.keyEventHandler.handleWindowKeyEvent(event) }
    ) {
        ShortcutToast(toastState = windowEvents.toastState)

        App(
            showCitySelectionDialog = windowEvents.showCitySelectionDialog,
            onCityDialogDismiss = windowEvents.onCityDialogDismiss,
            onShowCityDialog = windowEvents.onShowCityDialog,
            exitOnMouseMovementEnabled = windowEvents.exitOnMouseMovementEnabled,
            onExitApplication = { exitApplication() },
            showHelpDialog = windowEvents.showHelpDialog,
            onHelpDialogDismiss = windowEvents.onHelpDialogDismiss,
            modifier = windowEvents.mouseEventModifier
        )
    }
}

@Preview
@Composable
fun AppPreview() { 
    KoinApplication(application = {}) {
        App(
            showCitySelectionDialog = false, 
            onCityDialogDismiss = {},
            onShowCityDialog = {},
            showHelpDialog = false,
            onHelpDialogDismiss = {}
        ) 
    }
}
