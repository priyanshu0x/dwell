import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
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
import com.droidslife.screensaver.settings.ConsoleBackgroundStyle
import com.droidslife.screensaver.settings.Mode
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.DwellIconLoader
import com.droidslife.screensaver.ui.LinuxLiquidGlassHints
import com.droidslife.screensaver.ui.LinuxWindowManagerHints
import com.droidslife.screensaver.ui.TransparentWindowSurface
import com.droidslife.screensaver.widget.host.WidgetRegistry
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.core.context.stopKoin
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.prefs.Preferences

private const val IDLE_MONITOR_POLL_MS = 1_000L
private const val DEV_WINDOW_DEFAULT_WIDTH_PX = 1280
private const val DEV_WINDOW_DEFAULT_HEIGHT_PX = 800
private const val DEV_WINDOW_MIN_WIDTH_PX = 480
private const val DEV_WINDOW_MIN_HEIGHT_PX = 320
private const val DEV_WINDOW_PREFS_NODE = "/com/droidslife/screensaver/devWindow"

object Dwell {
    @JvmStatic
    fun main(args: Array<String>) {
        LinuxWindowManagerHints.configureDwellAppClassName()
        application {
            runDwell(args = args)
        }
    }
}

fun main(args: Array<String>) {
    LinuxWindowManagerHints.configureDwellAppClassName()
    application {
        runDwell(args = args)
    }
}

@Composable
internal fun ApplicationScope.runDwell(
    args: Array<String> = emptyArray(),
    devMode: Boolean = false,
) {
    val viewModelStoreOwner = remember { DwellViewModelStoreOwner() }
    DisposableEffect(viewModelStoreOwner) {
        onDispose {
            viewModelStoreOwner.clear()
            stopKoin()
        }
    }

    CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
        runDwellContent(args = args, devMode = devMode)
    }
}

@Composable
private fun ApplicationScope.runDwellContent(
    args: Array<String> = emptyArray(),
    devMode: Boolean = false,
) {
    val launchArgs = remember(devMode, args.toList()) {
        if (devMode) Args(LaunchMode.Screensaver) else Args.parse(args)
    }

    if (!devMode && launchArgs.mode == LaunchMode.Preview) {
        exitApplication()
        return
    }

    remember {
        initKoin {
            modules(appModule)
        }
        true
    }

    val settingsViewModel = koinViewModel<SettingsViewModel>()
    val widgetRegistry = koinInject<WidgetRegistry>()
    val coroutineScope = rememberCoroutineScope()
    val settings = settingsViewModel.settings
    var dashboardVisible by remember { mutableStateOf(devMode || launchArgs.mode != LaunchMode.Daemon) }
    var exitRequested by remember { mutableStateOf(false) }
    var dashboardActivationRequest by remember { mutableIntStateOf(0) }
    var activeTransparentWindow by remember { mutableStateOf<Boolean?>(null) }
    var transparentWindowSwapPending by remember { mutableStateOf(false) }
    val requestDashboardExit = { exitRequested = true }
    val keepRunningInTray = !devMode && (launchArgs.mode == LaunchMode.Daemon || launchArgs.mode == LaunchMode.Show)
    // `dwell show` must leave an affordance after Esc even if the daemon tray
    // preference is off; otherwise the app would keep running with no way back.
    val showTrayIcon = keepRunningInTray && (settings.trayIconEnabled || launchArgs.mode == LaunchMode.Show)
    val dwellWindowIcon = remember { loadDwellWindowIcon() }

    if (keepRunningInTray && !dashboardVisible) {
        Window(
            title = "Dwell Daemon",
            icon = dwellWindowIcon,
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
        coroutineScope.launch {
            widgetRegistry.syncWithSettings(settingsViewModel.settings)
        }
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

    val desiredTransparentWindow =
        settingsViewModel.settingsLoaded &&
            settings.mode == Mode.Console &&
            settings.consoleBackgroundStyle == ConsoleBackgroundStyle.LiquidGlass
    LaunchedEffect(dashboardVisible, settingsViewModel.settingsLoaded, desiredTransparentWindow) {
        if (!dashboardVisible || !settingsViewModel.settingsLoaded) {
            activeTransparentWindow = null
            transparentWindowSwapPending = false
            return@LaunchedEffect
        }

        val active = activeTransparentWindow
        when {
            active == null -> {
                if (transparentWindowSwapPending) {
                    withFrameNanos { }
                    transparentWindowSwapPending = false
                    dashboardActivationRequest += 1
                }
                activeTransparentWindow = desiredTransparentWindow
            }
            active != desiredTransparentWindow -> {
                // Compose Desktop applies Window.transparent through a live
                // ComposeWindow updater, so switching transparency must dispose
                // the native window before creating the replacement.
                transparentWindowSwapPending = true
                activeTransparentWindow = null
                withFrameNanos { }
                transparentWindowSwapPending = false
                activeTransparentWindow = desiredTransparentWindow
                dashboardActivationRequest += 1
            }
        }
    }

    val windowTransparency = activeTransparentWindow
    if (dashboardVisible && (!settingsViewModel.settingsLoaded || windowTransparency == null)) {
        Window(
            title = "Dwell",
            icon = dwellWindowIcon,
            visible = false,
            onCloseRequest = {},
        ) {}
    }

    if (dashboardVisible && settingsViewModel.settingsLoaded && windowTransparency != null) {
        val windowEvents = rememberWindowEventHandlers(
            onExitApplication = requestDashboardExit,
            openSettingsOnStart = launchArgs.mode == LaunchMode.Config,
        )
        val density = LocalDensity.current
        val savedDevWindowBounds = remember(devMode) {
            if (devMode) loadDevWindowBounds() else null
        }
        val transparentProductionBounds = remember(devMode, windowTransparency) {
            if (!devMode && windowTransparency) loadPrimaryScreenBounds() else null
        }
        val devWindowSize = with(density) {
            savedDevWindowBounds
                ?.let { DpSize(it.width.toDp(), it.height.toDp()) }
                ?: DpSize(DEV_WINDOW_DEFAULT_WIDTH_PX.toDp(), DEV_WINDOW_DEFAULT_HEIGHT_PX.toDp())
        }
        val productionWindowSize = with(density) {
            transparentProductionBounds
                ?.let { DpSize(it.width.toDp(), it.height.toDp()) }
                ?: DpSize.Unspecified
        }
        val devWindowPosition = with(density) {
            savedDevWindowBounds
                ?.let { WindowPosition(it.x.toDp(), it.y.toDp()) }
                ?: WindowPosition(Alignment.Center)
        }
        val productionWindowPosition = with(density) {
            transparentProductionBounds
                ?.let { WindowPosition(it.x.toDp(), it.y.toDp()) }
                ?: WindowPosition(Alignment.Center)
        }
        var windowMinimized by remember { mutableStateOf(false) }
        // Compose Desktop rejects transparent windows unless they are undecorated
        // and also rejects changing transparency after the AWT window is shown.
        val usesTransparentWindow = windowTransparency
        key(usesTransparentWindow) {
            Window(
                title = "Dwell",
                icon = dwellWindowIcon,
                state = rememberWindowState(
                    placement = when {
                        devMode -> WindowPlacement.Floating
                        usesTransparentWindow -> WindowPlacement.Floating
                        else -> WindowPlacement.Fullscreen
                    },
                    position = if (devMode) devWindowPosition else productionWindowPosition,
                    size = if (devMode) devWindowSize else productionWindowSize,
                ),
                onCloseRequest = requestDashboardExit,
                resizable = devMode,
                alwaysOnTop = !windowMinimized,
                undecorated = !devMode || usesTransparentWindow,
                transparent = usesTransparentWindow,
                onKeyEvent = { event -> windowEvents.keyEventHandler.handleWindowKeyEvent(event) }
            ) {
                DisposableEffect(window) {
                    TransparentWindowSurface.apply(window, enabled = usesTransparentWindow)
                    LinuxWindowManagerHints.applyDwellWindowHints(window)
                    LinuxLiquidGlassHints.applyBlurBehind(window, enabled = usesTransparentWindow)

                    fun refreshMinimizedState() {
                        windowMinimized = (window.extendedState and Frame.ICONIFIED) != 0
                    }

                    val listener = object : WindowAdapter() {
                        override fun windowStateChanged(event: WindowEvent) {
                            refreshMinimizedState()
                        }

                        override fun windowIconified(event: WindowEvent) {
                            windowMinimized = true
                        }

                        override fun windowDeiconified(event: WindowEvent) {
                            windowMinimized = false
                        }
                    }

                    window.addWindowStateListener(listener)
                    window.addWindowListener(listener)
                    refreshMinimizedState()
                    onDispose {
                        window.removeWindowStateListener(listener)
                        window.removeWindowListener(listener)
                    }
                }

                DisposableEffect(window, devMode) {
                    if (!devMode) {
                        onDispose {}
                    } else {
                        val listener = object : ComponentAdapter() {
                            override fun componentMoved(event: ComponentEvent) {
                                saveDevWindowBounds(window)
                            }

                            override fun componentResized(event: ComponentEvent) {
                                saveDevWindowBounds(window)
                                LinuxLiquidGlassHints.applyBlurBehind(window, enabled = usesTransparentWindow)
                            }
                        }

                        window.addComponentListener(listener)
                        saveDevWindowBounds(window)
                        onDispose {
                            window.removeComponentListener(listener)
                            saveDevWindowBounds(window)
                        }
                    }
                }

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
}

private data class DevWindowBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

private fun loadDevWindowBounds(): DevWindowBounds? {
    return runCatching {
        val prefs = devWindowPrefs()
        val width = prefs.getInt("width", 0)
        val height = prefs.getInt("height", 0)
        if (width < DEV_WINDOW_MIN_WIDTH_PX || height < DEV_WINDOW_MIN_HEIGHT_PX) return@runCatching null
        DevWindowBounds(
            x = prefs.getInt("x", 0),
            y = prefs.getInt("y", 0),
            width = width,
            height = height,
        )
    }.getOrNull()
}

private fun loadPrimaryScreenBounds(): Rectangle? {
    return runCatching {
        GraphicsEnvironment
            .getLocalGraphicsEnvironment()
            .defaultScreenDevice
            .defaultConfiguration
            .bounds
    }.getOrNull()
}

private fun saveDevWindowBounds(window: Frame) {
    if ((window.extendedState and Frame.ICONIFIED) != 0) return
    val bounds = window.bounds
    if (bounds.width < DEV_WINDOW_MIN_WIDTH_PX || bounds.height < DEV_WINDOW_MIN_HEIGHT_PX) return
    saveDevWindowBounds(bounds)
}

private fun saveDevWindowBounds(bounds: Rectangle) {
    runCatching {
        devWindowPrefs().apply {
            putInt("x", bounds.x)
            putInt("y", bounds.y)
            putInt("width", bounds.width)
            putInt("height", bounds.height)
        }
    }
}

private fun devWindowPrefs(): Preferences =
    Preferences.userRoot().node(DEV_WINDOW_PREFS_NODE)

private class DwellViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    fun clear() {
        viewModelStore.clear()
    }
}

/**
 * Load the bundled brand icon for the OS taskbar / Alt-Tab. Falls back to
 * the JVM default if the file isn't present (e.g. during a partial build).
 */
private fun loadDwellWindowIcon(): Painter? {
    return DwellIconLoader.load()?.let { image ->
        BitmapPainter(image.toComposeImageBitmap())
    }
}
