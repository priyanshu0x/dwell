import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.*
import com.droidslife.screensaver.App
import com.droidslife.screensaver.components.ShortcutToast
import com.droidslife.screensaver.components.rememberWindowEventHandlers
import com.droidslife.screensaver.di.appModule
import com.droidslife.screensaver.di.initKoin

fun main() = application {
    remember {
        initKoin {
            modules(appModule)
        }
        true
    }

    var exitRequested by remember { mutableStateOf(false) }
    val requestExit = { exitRequested = true }
    val windowEvents = rememberWindowEventHandlers(requestExit)

    Window(
        title = "Screen Saver App",
        state = rememberWindowState(
            placement = WindowPlacement.Maximized,
            position = WindowPosition(Alignment.Center),
        ),
        onCloseRequest = requestExit,
        resizable = false,
        alwaysOnTop = true,
        undecorated = true,
        transparent = true,
        onKeyEvent = { event -> windowEvents.keyEventHandler.handleWindowKeyEvent(event) }
    ) {
        ShortcutToast(toastState = windowEvents.toastState)

        App(
            onExitApplication = requestExit,
            exitRequested = exitRequested,
            onExited = { exitApplication() },
            showHelpDialog = windowEvents.showHelpDialog,
            onHelpDialogDismiss = windowEvents.onHelpDialogDismiss,
            onShowHelpDialog = windowEvents.onShowHelpDialog,
            modifier = windowEvents.mouseEventModifier
        )
    }
}
