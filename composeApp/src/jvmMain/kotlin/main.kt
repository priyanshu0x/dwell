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
import com.droidslife.screensaver.daemon.TrayDaemon
import com.droidslife.screensaver.daemon.IdleState
import com.droidslife.screensaver.daemon.createIdleMonitor
import com.droidslife.screensaver.daemon.watch
import com.droidslife.screensaver.di.appModule
import com.droidslife.screensaver.di.initKoin
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.widget.host.WidgetRegistry
import kotlinx.coroutines.flow.collect
import org.koin.compose.koinInject
import java.awt.Frame

private const val IDLE_MONITOR_POLL_MS = 1_000L

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
    var dashboardActivationRequest by remember { mutableStateOf(0) }
    val requestDashboardExit = { exitRequested = true }
    val keepRunningInTray = launchArgs.mode == LaunchMode.Daemon || launchArgs.mode == LaunchMode.Show
    // `dwell show` must leave an affordance after Esc even if the daemon tray
    // preference is off; otherwise the app would keep running with no way back.
    val showTrayIcon = keepRunningInTray && (settings.trayIconEnabled || launchArgs.mode == LaunchMode.Show)

    if (keepRunningInTray && !dashboardVisible) {
        Window(
            title = "Dwell Daemon",
            visible = false,
            onCloseRequest = {},
        ) {}
    }

    val tray = remember { TrayDaemon() }

    val onShow = {
        dashboardVisible = true
        exitRequested = false
        dashboardActivationRequest += 1
    }
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
        if (showTrayIcon) {
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

    DisposableEffect(keepRunningInTray) {
        val hotkey = WindowsTrayHotkey(onShowDashboard = onShow)
        if (keepRunningInTray) hotkey.start()
        onDispose { hotkey.close() }
    }

    // Keep tray checkmarks in sync when settings change (from dashboard or another tray click).
    LaunchedEffect(settings.mode, settings.cinematicVariant, settings.ambientVariant, settings.consoleVariant, settings.startWithSystem) {
        if (showTrayIcon) {
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

    LaunchedEffect(keepRunningInTray, settings.idleTimeoutSeconds) {
        if (!keepRunningInTray) return@LaunchedEffect
        val idleMonitor = createIdleMonitor()
        val thresholdMillis = settings.idleTimeoutSeconds * 1_000L

        idleMonitor.watch(thresholdMillis, IDLE_MONITOR_POLL_MS).collect { state ->
            if (state == IdleState.Idle) {
                dashboardVisible = true
                exitRequested = false
                dashboardActivationRequest += 1
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
            // Keep Dwell in the normal window stack so Alt-Tab behaves like a regular app.
            alwaysOnTop = false,
            undecorated = true,
            transparent = true,
            onKeyEvent = { event -> windowEvents.keyEventHandler.handleWindowKeyEvent(event) }
        ) {
            LaunchedEffect(dashboardActivationRequest) {
                if (dashboardActivationRequest > 0) {
                    if ((window.extendedState and Frame.ICONIFIED) != 0) {
                        window.extendedState = window.extendedState and Frame.ICONIFIED.inv()
                    }
                    window.toFront()
                    window.requestFocus()
                }
            }

            ShortcutToast(toastState = windowEvents.toastState)

            App(
                onExitApplication = requestDashboardExit,
                exitRequested = exitRequested,
                onExited = {
                    if (keepRunningInTray) {
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
