# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Build & Run

This is a Kotlin/Compose Multiplatform project with a single `:composeApp` module currently targeting JVM (desktop) only.

- Run dashboard once: `./gradlew :composeApp:run --args="--show"` (Windows: `.\gradlew.bat :composeApp:run --args="--show"`)
- Run tray daemon: `./gradlew :composeApp:run` (Windows: `.\gradlew.bat :composeApp:run`)
- Run with Compose hot-reload (uses `DevMainKt`): `./gradlew :composeApp:runHot`
- Test (commonTest + jvmTest): `./gradlew :composeApp:jvmTest`
- Single test class: `./gradlew :composeApp:jvmTest --tests "com.droidslife.screensaver.clock.ClockViewModelTest"`
- Package native installer: `./gradlew :composeApp:packageDistributionForCurrentOS` (formats: Dmg, Msi, Deb, Exe — see `composeApp/build.gradle.kts`)

The app uses Gradle configuration cache and parallel builds (see `gradle.properties`) — if you edit Gradle files and see stale-config errors, run with `--no-configuration-cache` once to debug.

### Runtime requirements

- The WeatherAPI.com integration reads `WEATHERAPI` from the process environment (`System.getenv("WEATHERAPI")` in `weather/WeatherApi.kt`). Without it, every API call falls through `catch` and returns mock data — useful for development, but a silent failure mode. There is no `.env` mechanism; the env var must be set in the shell or run configuration.

## Architecture

### Entry points
- `composeApp/src/jvmMain/kotlin/main.kt` — production entry (`MainKt`). Fullscreen, undecorated, always-on-top window with mouse/key handlers that trigger `exitApplication()`.
- `composeApp/src/jvmMain/kotlin/devMain.kt` — hot-reload entry (`DevMainKt`). Maximized (not fullscreen) and wrapped in `DevelopmentEntryPoint { }`. **It duplicates the key/mouse handling logic from `main.kt` inline rather than reusing `KeyEventHandler`** — when adding new shortcuts, update both files.

### Composition flow
`main` → `Window` → `App` (KoinContext + AppTheme) → `DigitalClockApp` (the actual screen-saver UI, including the digit row, weather panel, control icons, and all dialogs).

### DI (Koin)
All graph wiring is in `di/AppModule.kt`. `initKoin { modules(appModule) }` is called from each entry point before `application { }`. ViewModels are plain classes (not `androidx.lifecycle.ViewModel`) that hold their own `CoroutineScope(SupervisorJob() + Dispatchers.Main)` and expose Compose `MutableState` directly — pulled into composables with `koinInject<T>()`.

### Clock-design system
There are 11 clock designs (`clockdigits/DigitalClockDigit.kt`, `DigitalClockDigit2.kt` … `DigitalClockDigit11.kt`), dispatched by an `Int` in `DigitalClockApp.kt`'s `when (clockViewModel.clockDesign)` block.

**Known inconsistency:** `ClockViewModel.cycleClockDesign()` and `updateClockDesign()` validate against `1..8` only — designs 9, 10, 11 exist in the UI dispatch but cannot be reached via the Ctrl+N shortcut or auto-cycle/shuffle. When adding designs, update both the `when` block in `DigitalClockApp.kt` *and* the range in `ClockViewModel`.

### Settings persistence
`PreferencesRepositoryImpl` is **in-memory only** — it holds a `MutableStateFlow<SettingsModel>` with no disk writes. The `kstore` dependency is included and the file has a TODO comment explaining the intended migration. Settings reset on each restart.

### Keyboard shortcuts (all Ctrl + ...)
Defined centrally in `components/KeyEventHandler.kt`, dispatched as `KeyEventAction` sealed-class instances handled in `main.kt`'s `onAction` lambda:

- N = cycle clock design · P = toggle auto-change · R = toggle shuffle · S = city dialog
- C = settings dialog · H = help dialog · T = toggle theme
- Z = toggle exit-on-mouse-movement · X = exit (only when Z-toggle is enabled)

`devMain.kt` re-implements this inline (see note above).

### Exit-on-movement behavior
`main.kt` tracks `lastMousePosition` and only exits when the pointer moves more than `movementThreshold = 5f` pixels — this avoids spurious exits from cursor jitter when the window first gains focus. The same threshold logic is duplicated in `devMain.kt`.

### Network / data
- `network/KtorClient.kt` — Ktor client factory (OkHttp engine on JVM, with retry).
- `weather/WeatherApi.kt` — WeatherAPI.com client. Catches all exceptions and substitutes mock data; **errors are silent in the UI**.
- `weather/WeatherRepository.kt` + `WeatherViewModel.kt` — state machine (`WeatherState.Loading/Success/Error`, `CitySearchState.*`) consumed in `DigitalClockApp`.
- `location/LocationService.kt` + `TimeZoneUtils.kt` — used to derive the displayed timezone from the selected city's `tz_id`.

### Theming
`theme/Theme.kt` defines the Material 3 color scheme; `theme/Theme.jvm.kt` is the platform-actual. Dark/light toggle goes through `SettingsViewModel.toggleTheme()` → `AppTheme(isDark = ...)`.

### Resources
Custom fonts (Orbitron, Seven-Segment, Technology, IndieFlower) and SVG icons live under `composeApp/src/commonMain/composeResources/`. These are consumed via the Compose resources plugin's generated `Res` object.

## Comments

Use industry-standard comments: explain **why** (not what), skip the obvious, keep them short. If removing it wouldn't confuse a future reader, don't write it.

## Form UX

Do not disable form submit/action buttons for validation failures. Keep the action clickable, then show the error and the concrete fix after the user presses it. Put that feedback near the related fields or in the dialog/footer, whichever is clearer for the specific form.
