# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Build & Run

This is a Kotlin/Compose Multiplatform project with a single `:composeApp` module currently targeting JVM (desktop) only.

- Developer production smoke test: `./scripts/dwell show` (Windows: `scripts\dwell.cmd show`). On Linux/macOS this builds current source, temporarily stops the registered background daemon, opens the production dashboard, then restores the daemon when the show session exits.
- Direct dashboard run without launcher daemon handling: `./gradlew :composeApp:run --args="--show"` (Windows: `.\gradlew.bat :composeApp:run --args="--show"`)
- Run tray daemon: `./gradlew :composeApp:run` (Windows: `.\gradlew.bat :composeApp:run`)
- Run with Compose hot-reload (uses `DevMainKt`): `./gradlew --no-configuration-cache :composeApp:runHot`
- Test (commonTest + jvmTest): `./gradlew :composeApp:jvmTest`
- Single test class: `./gradlew :composeApp:jvmTest --tests "com.droidslife.screensaver.ArgsTest"`
- Generate Compose compiler reports: `./gradlew :composeApp:compileKotlinJvm -PcomposeCompilerReports=true` (inspect `composeApp/build/compose_compiler/jvm/main/composeApp-module.json`)
- Package native installer: `./gradlew :composeApp:packageDistributionForCurrentOS` (formats: Dmg, Msi, Deb, Exe — see `composeApp/build.gradle.kts`)

The app uses Gradle configuration cache and parallel builds for normal builds (see `gradle.properties`). Keep Compose Hot Reload runs on `--no-configuration-cache`; its long-lived recompiler can otherwise reuse stale task state and leave the dev window on old classes.

### Developer launcher semantics

- Use `./scripts/dwell dev` for fast UI iteration. It runs `DevMainKt` with Compose Hot Reload and `--no-configuration-cache`, opens a normal decorated/resizable window, remembers its last size and position, stays on top while visible, and intentionally skips daemon, tray, idle-monitor, and startup plumbing.
- Use `./scripts/dwell show` as the production-path smoke test from source. It must build and launch the current checkout, not ask an already-running daemon to show its existing window.
- Do not add app-side IPC or daemon reuse to implement `dwell show`. If a registered daemon is running, the launcher owns the pause/restore lifecycle so the developer build does not race with the background daemon or show two dashboards.

### Compose Desktop window transparency

- `Window(transparent = true)` must be paired with `undecorated = true`; Compose Desktop enforces this in `ComposeWindowPanel.setWindowTransparent`.
- Do not toggle `Window.transparent` on an already displayed window. When changing between opaque and Liquid Glass modes, first remove the dashboard `Window` from composition so Compose disposes the old AWT window, then create the replacement with the new transparency mode. A `key(...)` alone is not enough if the live `Window` receives an updated `transparent` value first.
- Linux Liquid Glass runs with Skiko `SOFTWARE_FAST`; Skiko's default Linux `OPENGL` redrawer has crashed during transparent-window recreation.
- Liquid Glass blur behind other apps is compositor-owned. On Linux/X11, Dwell requests KWin blur with `_KDE_NET_WM_BLUR_BEHIND_REGION`; unsupported compositors will still show the app-side translucent tint without real background blur.

### Runtime requirements

- Weather works out of the box through the default `wttr.in` provider. The optional WeatherAPI.com provider requires an API key stored through widget settings/secrets; if it is missing or invalid, the UI surfaces an unconfigured/error state instead of silently returning mock data.

## Architecture

### Entry points
- `composeApp/src/jvmMain/kotlin/main.kt` — production entry (`Dwell`). It owns the shared `ApplicationScope.runDwell(...)` implementation used by both normal and hot-reload launches.
- `composeApp/src/jvmMain/kotlin/devMain.kt` — hot-reload entry (`DevMainKt`). It calls `runDwell(devMode = true)`, which uses a decorated/resizable window and skips daemon/tray/idle plumbing.

### Composition flow
`Dwell.main` / `DevMainKt.main` → `Window` → `App` (AppTheme + settings/widget sync) → `ModeHost` (Cinematic, Ambient, Console) plus `SettingsSidebar`, `WidgetConfigDialog`, and `ShortcutsHelpDialog` overlays.

### DI (Koin)
All graph wiring is in `di/AppModule.kt`. `runDwell(...)` initializes Koin once with `initKoin { modules(appModule) }` inside the Compose application scope and provides a desktop `LocalViewModelStoreOwner` for Compose lifecycle ViewModels. `SettingsViewModel` and `WeatherViewModel` extend `androidx.lifecycle.ViewModel`, use `viewModelScope`, are exposed through `viewModelOf`, and should be pulled into composables with `koinViewModel<T>()`. Their Koin definitions are backed by app-scoped holders so service singletons and composables share the same instances. Plain service singletons such as `WidgetRegistry` still use `koinInject<T>()`.

### Widget system
Built-in widgets are registered in `di/AppModule.kt` through factory singletons (`ClockWidgetFactory`, `WeatherWidgetFactory`, `TodosWidgetFactory`, `ExpensesWidgetFactory`, `CalendarWidgetFactory`, `IdleCounterWidgetFactory`, `PomodoroWidgetFactory`) and collected by `WidgetRegistry`. Console layout sizing is driven by `WidgetSize`; Cinematic and Ambient use the same widget instances with chip/minimal render targets where a widget opts in.

### Settings persistence
`PreferencesRepositoryImpl.jvm.kt` persists settings with `kstore` through `SettingsFileCodec`, writing JSON to `~/.screensaver/settings.json`. The codec migrates legacy `currentCity` and `idleTimeoutMinutes` fields before decoding with `ignoreUnknownKeys = true`.

### Keyboard shortcuts
Defined centrally in `components/KeyEventHandler.kt`, dispatched as `KeyEventAction` sealed-class instances handled by `rememberWindowEventHandlers(...)`:

- Esc = contextual dismiss / hide dashboard
- F1 or `?` = Help
- M = cycle mode · 1/2/3 = jump Cinematic/Ambient/Console
- V = cycle variant · W = toggle Cinematic widget drawer · L = toggle Console edit mode
- S, Ctrl+, or Cmd+, = Settings
- Ctrl+R or Cmd+R = reload widgets
- Ctrl+T = toggle theme
- Ctrl+X, Ctrl+Q, or Cmd+Q = request exit

Window-level shortcuts stand down while `TextInputFocus` is active so form fields receive normal typing and edit chords.

### Network / data
- `network/KtorClient.kt` — Ktor client factory (OkHttp engine on JVM, with retry).
- `weather/WeatherApi.kt` — WeatherAPI.com client used by `WeatherApiProvider`; failures become `WeatherApiException` and then provider-level unconfigured/credential/error states.
- `weather/WeatherRepository.kt` + `WeatherViewModel.kt` — provider-aware state machine (`WeatherState.Loading/Success/Error`, `CitySearchState.*`) consumed by the weather and clock/weather widgets.
- `location/LocationService.kt` + `TimeZoneUtils.kt` — used to derive the displayed timezone from the selected city's `tz_id`.

### Theming
`theme/Theme.kt` defines the Material 3 color scheme; `theme/Theme.jvm.kt` is the platform-actual. Dark/light toggle goes through `SettingsViewModel.toggleTheme()` → `AppTheme(isDark = ...)`.

### Resources
Custom fonts (Orbitron, Seven-Segment, Technology, IndieFlower) and SVG icons live under `composeApp/src/commonMain/composeResources/`. These are consumed via the Compose resources plugin's generated `Res` object.

## Comments

Use industry-standard comments: explain **why** (not what), skip the obvious, keep them short. If removing it wouldn't confuse a future reader, don't write it.

## Form UX

Do not disable form submit/action buttons for validation failures. Keep the action clickable, then show the error and the concrete fix after the user presses it. Put that feedback near the related fields or in the dialog/footer, whichever is clearer for the specific form.
