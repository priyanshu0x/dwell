import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.*
import com.droidslife.screensaver.App
import com.droidslife.screensaver.Args
import com.droidslife.screensaver.LaunchMode
import com.droidslife.screensaver.components.ShortcutToast
import com.droidslife.screensaver.components.rememberWindowEventHandlers
import com.droidslife.screensaver.daemon.IdleState
import com.droidslife.screensaver.daemon.TrayDaemon
import com.droidslife.screensaver.daemon.createIdleMonitor
import com.droidslife.screensaver.daemon.watch
import com.droidslife.screensaver.di.appModule
import com.droidslife.screensaver.di.initKoin
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.widget.host.WidgetRegistry
import org.koin.compose.koinInject

fun main(args: Array<String>) = application {
    val launchArgs = remember(args.toList()) { Args.parse(args) }

    if (launchArgs.mode == LaunchMode.Preview) {
        exitApplication()
        return@application
    }

    remember {
        initKoin {
            modules(appModule)
        }
        true
    }

    val settingsViewModel = koinInject<SettingsViewModel>()
    val widgetRegistry = koinInject<WidgetRegistry>()
    val settings = settingsViewModel.settings
    var dashboardVisible by remember { mutableStateOf(launchArgs.mode != LaunchMode.Daemon) }
    var exitRequested by remember { mutableStateOf(false) }
    val requestDashboardExit = { exitRequested = true }

    if (launchArgs.mode == LaunchMode.Daemon && !dashboardVisible) {
        Window(
            title = "Dwell Daemon",
            visible = false,
            onCloseRequest = {},
        ) {}
    }

    val tray = remember { TrayDaemon() }
    DisposableEffect(launchArgs.mode, settings.trayIconEnabled) {
        if (launchArgs.mode == LaunchMode.Daemon && settings.trayIconEnabled) {
            tray.install(
                onShow = {
                    dashboardVisible = true
                    exitRequested = false
                },
                onSettings = {
                    dashboardVisible = true
                    exitRequested = false
                    settingsViewModel.openSettingsDialog()
                },
                onReloadWidgets = {
                    widgetRegistry.reload()
                    widgetRegistry.syncWithSettings(settingsViewModel.settings)
                },
                onQuit = { exitApplication() },
            )
        }
        onDispose { tray.remove() }
    }

    LaunchedEffect(launchArgs.mode, settings.idleTimeoutMinutes) {
        if (launchArgs.mode != LaunchMode.Daemon) return@LaunchedEffect
        createIdleMonitor()
            .watch(settings.idleTimeoutMinutes * 60_000L)
            .collect { state ->
                when (state) {
                    IdleState.Idle -> {
                        dashboardVisible = true
                        exitRequested = false
                    }
                    IdleState.Active -> if (dashboardVisible) {
                        exitRequested = true
                    }
                }
            }
    }

    if (dashboardVisible) {
        val windowEvents = rememberWindowEventHandlers(
            onExitApplication = requestDashboardExit,
            openSettingsOnStart = launchArgs.mode == LaunchMode.Config,
        )

        Window(
            title = "Dwell",
            state = rememberWindowState(
                placement = WindowPlacement.Fullscreen,
                position = WindowPosition(Alignment.Center),
            ),
            onCloseRequest = requestDashboardExit,
            resizable = false,
            alwaysOnTop = true,
            undecorated = true,
            transparent = true,
            onKeyEvent = { event -> windowEvents.keyEventHandler.handleWindowKeyEvent(event) }
        ) {
            ShortcutToast(toastState = windowEvents.toastState)

            App(
                exitOnMouseMovementEnabled = windowEvents.exitOnMouseMovementEnabled,
                onExitApplication = requestDashboardExit,
                exitRequested = exitRequested,
                onExited = {
                    if (launchArgs.mode == LaunchMode.Daemon) {
                        dashboardVisible = false
                        exitRequested = false
                    } else {
                        exitApplication()
                    }
                },
                showHelpDialog = windowEvents.showHelpDialog,
                onHelpDialogDismiss = windowEvents.onHelpDialogDismiss,
                onShowHelpDialog = windowEvents.onShowHelpDialog,
                modifier = windowEvents.mouseEventModifier
            )
        }
    }
}
