import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.*
import javax.imageio.ImageIO
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

    val onShow = { dashboardVisible = true; exitRequested = false }
    val onSettings = {
        dashboardVisible = true
        exitRequested = false
        settingsViewModel.openSettingsDialog()
    }
    val onReloadWidgets = {
        widgetRegistry.reload()
        widgetRegistry.syncWithSettings(settingsViewModel.settings)
    }

    DisposableEffect(launchArgs.mode, settings.trayIconEnabled) {
        if (launchArgs.mode == LaunchMode.Daemon && settings.trayIconEnabled) {
            tray.install(
                getSettings = { settingsViewModel.settings },
                onShow = { onShow() },
                onSettings = { onSettings() },
                onSetMode = { settingsViewModel.setMode(it) },
                onSetCinematicVariant = { settingsViewModel.setCinematicVariant(it) },
                onSetAmbientVariant = { settingsViewModel.setAmbientVariant(it) },
                onSetConsoleVariant = { settingsViewModel.setConsoleVariant(it) },
                onSetStartWithSystem = { settingsViewModel.setStartWithSystem(it) },
                onReloadWidgets = { onReloadWidgets() },
                onQuit = { exitApplication() },
            )
        }
        onDispose { tray.remove() }
    }

    // Keep tray checkmarks in sync when settings change (from dashboard or another tray click).
    LaunchedEffect(settings.mode, settings.cinematicVariant, settings.ambientVariant, settings.consoleVariant, settings.startWithSystem) {
        if (launchArgs.mode == LaunchMode.Daemon && settings.trayIconEnabled) {
            tray.refresh(
                getSettings = { settingsViewModel.settings },
                onShow = { onShow() },
                onSettings = { onSettings() },
                onSetMode = { settingsViewModel.setMode(it) },
                onSetCinematicVariant = { settingsViewModel.setCinematicVariant(it) },
                onSetAmbientVariant = { settingsViewModel.setAmbientVariant(it) },
                onSetConsoleVariant = { settingsViewModel.setConsoleVariant(it) },
                onSetStartWithSystem = { settingsViewModel.setStartWithSystem(it) },
                onReloadWidgets = { onReloadWidgets() },
                onQuit = { exitApplication() },
            )
        }
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

        val dwellWindowIcon = remember { loadDwellWindowIcon() }
        Window(
            title = "Dwell",
            icon = dwellWindowIcon,
            state = rememberWindowState(
                placement = WindowPlacement.Fullscreen,
                position = WindowPosition(Alignment.Center),
            ),
            onCloseRequest = requestDashboardExit,
            resizable = false,
            // Dropped alwaysOnTop: the OS should be able to switch focus
            // (Super, Alt-Tab) without our window staying floated over the
            // user's actual work. We hide ourselves below when focus is lost.
            alwaysOnTop = false,
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

/**
 * Load the bundled brand icon for the OS taskbar / Alt-Tab. Falls back to
 * the JVM default if the file isn't present (e.g. during a partial build).
 */
private fun loadDwellWindowIcon(): Painter? {
    val candidates = listOf(
        java.io.File("composeApp/desktopAppIcons/LinuxIcon.png"),
        java.io.File("desktopAppIcons/LinuxIcon.png"),
    )
    val source = candidates.firstOrNull { it.exists() } ?: return null
    return runCatching {
        val image = ImageIO.read(source) ?: return null
        BitmapPainter(image.toComposeImageBitmap())
    }.getOrNull()
}
