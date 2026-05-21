# Full Implementation Plan: Idle-Triggered Interactive Dashboard

## Context

Evolve the current Compose Multiplatform desktop screensaver into an idle-triggered, interactive, plugin-extensible dashboard for PC developers. Stay in Kotlin/Compose (Rust was considered only for lock-screen access, which is dropped). Ship Windows + Linux at parity. Widgets are first-class extensibility — todos/clock/weather/expenses are *built-in implementations of the same public widget API* that third parties use.

**Audience:** PC users, mostly developers. Optimize for "drop a file, get a widget."
**Non-goals (v1):** macOS, mobile, lock-screen overlay, multi-instance widgets, widget marketplace UI, widget hot-reload of JARs, widget sandboxing (documented risk).

---

## Architecture overview

```
                     ┌─────────────────────────────────────────┐
                     │            JVM process (one)            │
                     │                                         │
       OS idle ─────▶│  IdleMonitor (JNA / D-Bus per platform) │
                     │             │                           │
                     │             ▼                           │
        Tray icon ──▶│  TrayDaemon  ──spawns──▶  Dashboard     │
                     │     ▲                       Window      │
                     │     │                          │        │
                     │     │                  ┌───────┴──────┐ │
                     │     │                  │ WidgetGrid   │ │
                     │     │                  │ (LazyGrid)   │ │
                     │     │                  └───────┬──────┘ │
                     │     │                          │        │
                     │     │              uses ──────▶│        │
                     │     │                          ▼        │
                     │     │              ┌───────────────────┐│
                     │     │              │ WidgetRegistry    ││
                     │     │              │ (Koin singleton)  ││
                     │     │              └────────┬──────────┘│
                     │     │                       │           │
                     │     │   ┌───────────────────┴─────────┐ │
                     │     │   ▼                             ▼ │
                     │     │ Built-in widgets         Loaded   │
                     │     │ (clock/weather/          widgets  │
                     │     │  todos/expenses)         from FS  │
                     │     │                             │     │
                     │     │   ┌─────────────────────────┴───┐ │
                     │     │   ▼                             ▼ │
                     │     │ Tier 1: JAR via              Tier 2: │
                     │     │ ServiceLoader                folder + │
                     │     │ (~/.screensaver/widgets/     YAML + script │
                     │     │  *.jar)                      → built-in │
                     │     │                              template │
                     │     │                                       │
                     │     └─────────  Settings / Esc / tray ──────┘
                     └─────────────────────────────────────────┘
```

---

## Gradle module structure (after refactor)

```
Screen-Saver-App/
├── composeApp/              ← existing; the host application
│   └── src/{commonMain,jvmMain,jvmTest}/...
├── widget-api/              ← NEW; public plugin contract (no host impls)
│   └── src/commonMain/kotlin/com/droidslife/screensaver/widget/api/
│       ├── WidgetFactory.kt
│       ├── Widget.kt
│       ├── WidgetConfig.kt
│       ├── WidgetScope.kt
│       ├── ConfigField.kt
│       └── ApiVersion.kt
├── samples/
│   ├── sample-kotlin-widget/   ← reference Tier-1 widget; depends on widget-api only
│   │   ├── build.gradle.kts
│   │   └── src/main/kotlin/...
│   └── sample-declarative-widget/   ← reference Tier-2 widget; just YAML + script
│       ├── widget.yaml
│       └── fetch.py
└── settings.gradle.kts      ← include(":composeApp", ":widget-api", ":samples:sample-kotlin-widget")
```

`widget-api` is its own Kotlin/JVM Gradle subproject so third-party authors can depend on it as a coordinate without dragging in the whole host. Publishing to Maven is a v2 concern; for v1 it just exists as a subproject.

`composeApp` package layout (additions):
```
com.droidslife.screensaver/
├── daemon/
│   ├── IdleMonitor.kt              (interface, expect on JVM)
│   ├── WindowsIdleMonitor.kt       (JNA GetLastInputInfo)
│   ├── X11IdleMonitor.kt           (JNA XScreenSaverQueryInfo)
│   ├── DBusIdleMonitor.kt          (gdbus shell-out or dbus-java)
│   ├── IdleMonitorFactory.kt       (selects impl per OS/display server)
│   └── TrayDaemon.kt               (java.awt.SystemTray + lifecycle)
├── widget/
│   ├── api/  ←  (moved to :widget-api subproject)
│   ├── host/
│   │   ├── WidgetRegistry.kt
│   │   ├── WidgetLoader.kt
│   │   ├── WidgetDescriptor.kt
│   │   ├── WidgetInstance.kt
│   │   ├── JarWidgetLoader.kt
│   │   ├── DeclarativeWidgetLoader.kt
│   │   ├── DeclarativeWidgetFactory.kt
│   │   ├── DataSource.kt           (sealed: Command, Http, File)
│   │   ├── ManifestParser.kt
│   │   ├── JsonPathBinder.kt
│   │   ├── WidgetScopeImpl.kt
│   │   └── templates/
│   │       ├── TextTemplate.kt
│   │       ├── KvTemplate.kt
│   │       ├── ListTemplate.kt
│   │       ├── ChartTemplate.kt
│   │       ├── ImageTemplate.kt
│   │       └── GridTemplate.kt
│   └── builtin/
│       ├── ClockWidgetFactory.kt
│       ├── WeatherWidgetFactory.kt
│       ├── TodosWidgetFactory.kt
│       └── ExpensesWidgetFactory.kt
├── storage/
│   ├── LocalStore.kt               (kstore wrapper; replaces in-mem PreferencesRepoImpl)
│   ├── ScopedStorage.kt            (per-widget storage instances)
│   └── SecretStorage.kt            (OS keychain via JNA; fallback to obfuscated file)
├── dashboard/
│   ├── DashboardWindow.kt          (replaces fullscreen logic in DigitalClockApp.kt)
│   ├── WidgetGrid.kt
│   └── WidgetCard.kt
└── settings/                       ← existing; expanded
    ├── SettingsModel.kt            (extended)
    ├── SettingsDialog.kt           (auto-generated panels)
    └── ConfigFieldRenderer.kt      (renders ConfigField → Compose UI)
```

---

## Dependency additions

`gradle/libs.versions.toml`:

```toml
[versions]
jna = "5.15.0"
charleskorn-kaml = "0.66.0"      # YAML for Tier 2 manifests
dbus-java = "5.1.0"              # OPTIONAL; v1 may shell out to gdbus instead

[libraries]
jna = { module = "net.java.dev.jna:jna", version.ref = "jna" }
jna-platform = { module = "net.java.dev.jna:jna-platform", version.ref = "jna" }
charleskorn-kaml = { module = "com.charleskorn.kaml:kaml", version.ref = "charleskorn-kaml" }
dbus-java-core = { module = "com.github.hypfvieh:dbus-java-core", version.ref = "dbus-java" }  # if needed
```

Wired into `composeApp/build.gradle.kts` `jvmMain.dependencies`:
- `jna`, `jna-platform` (Windows + Linux idle detection)
- `charleskorn-kaml` (Tier 2 manifest parsing)
- `project(":widget-api")` (host depends on the public API)

`samples/sample-kotlin-widget/build.gradle.kts`:
- `project(":widget-api")`
- `compose.runtime`, `compose.foundation`, `compose.material3` (compileOnly — the host provides them)

---

## Public Widget API (frozen contract — subproject `:widget-api`)

### `WidgetFactory.kt`
```kotlin
package com.droidslife.screensaver.widget.api

interface WidgetFactory {
    /** Stable unique ID, lowercase reverse-DNS recommended. */
    val id: String

    /** Human label shown in settings + as default header. */
    val displayName: String

    /** Optional one-line description. */
    val description: String get() = ""

    /** Used for settings grouping. */
    val category: WidgetCategory get() = WidgetCategory.OTHER

    /** Widget-API version this widget targets. Host refuses widgets with major mismatch. */
    val apiVersion: Int get() = WIDGET_API_VERSION

    /** Per-widget config schema; renders the per-widget settings panel. */
    val configSchema: List<ConfigField> get() = emptyList()

    /** Creates a fresh, ready-to-use widget instance. */
    fun create(config: WidgetConfig, scope: WidgetScope): Widget
}

const val WIDGET_API_VERSION = 1

enum class WidgetCategory { CLOCK, INFORMATION, PRODUCTIVITY, MEDIA, FINANCE, SYSTEM, OTHER }
```

### `Widget.kt`
```kotlin
package com.droidslife.screensaver.widget.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

interface Widget {
    /** Render inside a host-provided Card. The host handles header + chrome. */
    @Composable fun Content(modifier: Modifier)

    /** Optional custom header. Default: factory.displayName. */
    val header: String? get() = null

    /** Cell width in the grid: 1 (standard), 2 (wide), 3 (full). */
    val preferredSpan: Int get() = 1

    /** Called when the dashboard becomes visible. Start polling here. */
    fun onResume() {}

    /** Called when the dashboard fades out. Stop polling here. */
    fun onSuspend() {}

    /** Final teardown when widget is disabled / app exits. */
    fun onDispose() {}
}
```

### `WidgetConfig.kt`
```kotlin
package com.droidslife.screensaver.widget.api

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class WidgetConfig(private val values: JsonObject) {
    fun string(key: String, default: String = ""): String
    fun int(key: String, default: Int = 0): Int
    fun bool(key: String, default: Boolean = false): Boolean
    fun durationMillis(key: String, default: Long = 0): Long  // parses "30s", "5m"
    fun enum(key: String, default: String): String
    fun secret(key: String): String?  // resolved from SecretStorage
    fun raw(key: String): JsonElement?
}
```

### `WidgetScope.kt`
```kotlin
package com.droidslife.screensaver.widget.api

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope

interface WidgetScope {
    val coroutineScope: CoroutineScope        // cancels on dispose
    val httpClient: HttpClient                // shared host client
    val storage: WidgetStorage                // scoped to this widget id
    val log: WidgetLogger
}

interface WidgetStorage {
    suspend fun <T : Any> read(key: String, type: Class<T>): T?
    suspend fun <T : Any> write(key: String, value: T)
    suspend fun delete(key: String)
}

interface WidgetLogger {
    fun info(msg: String)
    fun warn(msg: String, error: Throwable? = null)
    fun error(msg: String, error: Throwable? = null)
}
```

### `ConfigField.kt`
```kotlin
package com.droidslife.screensaver.widget.api

sealed interface ConfigField {
    val key: String
    val label: String
    val required: Boolean
    val help: String?

    data class Text(override val key: String, override val label: String,
                    val default: String = "", val placeholder: String? = null,
                    override val required: Boolean = false, override val help: String? = null) : ConfigField

    data class Secret(override val key: String, override val label: String,
                      override val required: Boolean = false, override val help: String? = null) : ConfigField

    data class IntField(override val key: String, override val label: String,
                        val default: Int = 0, val min: Int? = null, val max: Int? = null,
                        override val required: Boolean = false, override val help: String? = null) : ConfigField

    data class Bool(override val key: String, override val label: String,
                    val default: Boolean = false,
                    override val required: Boolean = false, override val help: String? = null) : ConfigField

    data class Enum(override val key: String, override val label: String,
                    val options: List<EnumOption>, val default: String,
                    override val required: Boolean = false, override val help: String? = null) : ConfigField

    data class Duration(override val key: String, override val label: String,
                        val default: String = "30s",  // "30s", "5m", "1h"
                        override val required: Boolean = false, override val help: String? = null) : ConfigField

    data class EnumOption(val value: String, val label: String)
}
```

This contract is frozen at apiVersion = 1. Future additions add new optional methods; breaking changes bump major version.

---

## Tier 2 declarative widget manifest

`widget.yaml` schema (parsed into a `WidgetManifest` data class via kaml):

```yaml
id: com.example.stocks            # required, unique
title: Stock Ticker               # required
description: Real-time stock prices
category: finance                 # optional, maps to WidgetCategory
apiVersion: 1                     # required

template: list                    # required; one of: text|kv|list|chart|image|grid
preferredSpan: 1                  # 1|2|3

refresh: 60s                      # poll interval; "30s","5m","1h"

source:                           # required
  type: command                   # command | http | file
  command: ["python3", "fetch.py"]   # for command
  # url: "https://api.example.com/data"   # for http
  # method: GET
  # headers: { Authorization: "Bearer $apiKey" }   # $key references config keys
  # path: "data.json"                              # for file (relative to widget dir)
  timeout: 10s
  cacheBust: false                  # default true; sends ?_=now param on http

bindings:                         # required; JSONPath into the response
  # keys depend on the template:
  # text: { value: "..." }
  # kv:   { items: "...", item.key: "...", item.value: "..." }
  # list: { items: "...", item.label: "...", item.value?: "...", item.trend?: "..." }
  # chart:{ series: "...", series.x: "...", series.y: "..." }
  # image:{ url: "..." }
  # grid: { items: "...", item.label: "...", item.value: "..." }
  items: "$.holdings"
  item.label: "$.symbol"
  item.value: "$.price"
  item.trend: "$.change"

config:                           # optional; per-widget user settings
  - { key: apiKey, label: API key, type: secret, required: true }
  - { key: symbols, label: Symbols, type: text, default: "AAPL,GOOG" }
  - { key: refreshOverride, label: Refresh, type: duration, default: 60s }
```

Internal flow: `DeclarativeWidgetLoader` reads `widget.yaml` → builds a `DeclarativeWidgetFactory(manifest, folder)`. That factory implements the public `WidgetFactory` interface, so the rest of the host treats it identically to a Tier 1 JAR widget.

---

## Phases

Phase numbering is the suggested execution order. Each phase is independently verifiable.

### Milestone A — Foundations

#### Phase 0: Pre-work (small)
**Goal:** clean up the existing duplication so subsequent changes stay single-source.

Tasks:
1. Extract the duplicated key/mouse-handling logic from `main.kt` and `devMain.kt` into a new commonMain helper `components/WindowEventHandlers.kt`. Both entry points become thin wrappers.
2. Move kstore wiring: `settings/PreferencesRepositoryImpl` reads/writes a single `SettingsModel` blob to `kstore` at `~/.screensaver/settings.json` instead of `MutableStateFlow`-only memory. Keep the same interface so callers don't change.
3. Add the `:widget-api` Gradle subproject (empty for now), wire it into `settings.gradle.kts`. Create the file skeletons listed above.

Files:
- new: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/components/WindowEventHandlers.kt`
- mod: `composeApp/src/jvmMain/kotlin/main.kt`, `devMain.kt`
- mod: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/settings/PreferencesRepository.kt`
- new: `widget-api/build.gradle.kts`, `widget-api/src/commonMain/kotlin/.../api/*.kt`
- mod: `settings.gradle.kts`

Acceptance:
- `./gradlew :composeApp:jvmTest` passes.
- `./gradlew :composeApp:run` still launches the existing fullscreen clock.
- Add a city via city dialog, restart, city persists.

#### Phase 1: Args dispatcher + interactive dismiss (small)
**Goal:** the app understands different launch modes (daemon / show-once / screensaver-fallback).

Tasks:
1. Define a small `Args` parser in `jvmMain/Args.kt`. Modes: `--daemon` (default), `--show`, `/s` (screensaver show), `/p` (screensaver preview, no-op for v1), `/c` (screensaver config — open settings).
2. Branch in `main.kt` based on parsed mode. For now only `--show` and the existing fullscreen do anything; `--daemon` will be filled in Phase 7.
3. Add Esc → exit. Extend `KeyEventAction` with `RequestExit`. Bind Esc in `KeyEventHandler`. Wire it through to a host-level fade-out-then-exitApplication callback (the fade-out itself comes in Phase 2; for now it's instant exit).
4. Remove the mouse-movement exit (`mouseEventModifier` in `main.kt`).

Files:
- new: `composeApp/src/jvmMain/kotlin/com/droidslife/screensaver/Args.kt`
- mod: `composeApp/src/jvmMain/kotlin/main.kt`, `devMain.kt`
- mod: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/components/KeyEventAction.kt`, `KeyEventHandler.kt`

Acceptance:
- `./gradlew :composeApp:run` shows fullscreen clock.
- Esc exits immediately.
- Mouse movement no longer exits.

#### Phase 2: Fade in / out (small)
**Goal:** smooth entry/exit animations.

Tasks:
1. Wrap the root composable in `App.kt` with `AnimatedVisibility(visible, enter = fadeIn(tween(800)) + scaleIn(0.96f), exit = fadeOut(tween(400)))`.
2. `visible` is a `MutableState<Boolean>` initialized to `false` and flipped to `true` on first composition (via `LaunchedEffect(Unit)`).
3. Exit flow: caller sets `visible = false`; a `LaunchedEffect(visible)` waits for the exit animation duration + a small buffer, then invokes `onExited()` which calls `exitApplication()`.
4. Make the JVM window background fully transparent (`backgroundColor = Color(0,0,0,0)`) so the fade has nothing opaque behind it.

Files:
- mod: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/App.kt`
- mod: `composeApp/src/jvmMain/kotlin/main.kt`

Acceptance:
- `./gradlew :composeApp:run` fades in over ~800ms.
- Esc → fades out over ~400ms → process exits.

### Milestone B — Widget system

#### Phase 3: Widget API + registry + first built-in widget (medium)
**Goal:** the foundation everything else uses. **Critical phase — get the API right.**

Tasks:
1. Implement the full API in `:widget-api` (all signatures above).
2. In `composeApp` add `widget/host/`:
   - `WidgetDescriptor` (id, displayName, category, factory, source: BuiltIn / Jar / Declarative).
   - `WidgetInstance` (descriptor + Widget + config + Koin scope).
   - `WidgetRegistry` — Koin singleton; exposes `StateFlow<List<WidgetDescriptor>>` of discovered widgets + `Map<String, WidgetInstance>` of instantiated ones. Public ops: `enable(id)`, `disable(id)`, `updateConfig(id, JsonObject)`.
   - `WidgetScopeImpl` — backed by a per-widget `CoroutineScope(SupervisorJob() + Dispatchers.Main)` and a `ScopedStorage` instance.
   - `ScopedStorage` — kstore subdirectory per widget (`~/.screensaver/widgets-data/<id>/`).
3. Implement `ClockWidgetFactory` as a built-in widget. Move the 11 clock-design renderers and `ClockViewModel` logic into a self-contained `ClockWidget` class behind the `Widget` interface. Clock design becomes part of widget config (Enum field). Auto-cycle/shuffle become Bool/Duration config fields.
4. Build the grid: new `dashboard/WidgetGrid.kt` uses `LazyVerticalGrid(GridCells.Adaptive(320.dp))`. Renders enabled widgets from the registry. Each widget rendered inside `WidgetCard.kt` (the host-managed chrome: header, padding, error boundary).
5. Add an error boundary around `Widget.Content`: if it throws, render a small "widget error" placeholder; the rest of the dashboard stays up.
6. Replace `DigitalClockApp.kt`'s hardcoded `when (clockDesign) {...}` with a `WidgetGrid` call.

Files:
- complete: `widget-api/src/commonMain/kotlin/.../api/*.kt`
- new: all of `composeApp/.../widget/host/*.kt` (registry, scope impl, scoped storage)
- new: `composeApp/.../widget/builtin/ClockWidgetFactory.kt`
- new: `composeApp/.../dashboard/WidgetGrid.kt`, `WidgetCard.kt`
- mod: `composeApp/.../DigitalClockApp.kt` (drastically simplified)
- mod: `composeApp/.../di/AppModule.kt` (register WidgetRegistry + built-in factories)
- new tests: `WidgetRegistryTest`, `ClockWidgetTest`

Acceptance:
- `./gradlew :composeApp:run` → renders clock via the widget API (visually identical).
- Toggling clock-design config via a temporary debug button changes the design.
- A test widget that throws in `Content()` doesn't break the dashboard.

#### Phase 4: Tier 1 JAR loading (medium)
**Goal:** drop a JAR → widget appears.

Tasks:
1. `JarWidgetLoader.load(dir: Path): List<WidgetDescriptor>`:
   - Scans `dir` for `*.jar`.
   - For each JAR, creates a child `URLClassLoader(arrayOf(jar.toUri().toURL()), hostClassLoader)`.
   - Uses `ServiceLoader.load(WidgetFactory::class.java, childLoader)` to discover factories.
   - Validates `factory.apiVersion <= WIDGET_API_VERSION` (refuses newer).
   - Wraps each discovered factory as a `WidgetDescriptor(source = Jar(path), factory)`.
   - Logs and skips JARs that fail to load (no factories / wrong api version / class errors).
2. `WidgetLoader.discoverAll()` calls both `JarWidgetLoader` and `DeclarativeWidgetLoader` (next phase) on `~/.screensaver/widgets/`.
3. Discovery runs once at startup + on tray "Reload widgets" action (no file-watcher in v1 — keeps things simple).
4. Build `samples/sample-kotlin-widget` Gradle subproject:
   - Depends on `:widget-api` only.
   - Implements a `RandomQuoteWidgetFactory` registered via `META-INF/services/com.droidslife.screensaver.widget.api.WidgetFactory`.
   - Single composable that shows a quote, refreshed via `WidgetScope.coroutineScope`.
5. Document: `docs/widget-authors.md` with a 10-line Tier 1 quickstart.

Files:
- new: `composeApp/.../widget/host/JarWidgetLoader.kt`, `WidgetLoader.kt`
- new: `samples/sample-kotlin-widget/` (full subproject)
- new: `docs/widget-authors.md`
- mod: `settings.gradle.kts` (include sample)

Acceptance:
- `./gradlew :samples:sample-kotlin-widget:jar` produces a JAR.
- Copy JAR to `~/.screensaver/widgets/`, run app, widget appears in settings, enable, it renders.
- Remove JAR + restart → widget gone, no leaked Koin scope (check via debug log).
- A JAR with `apiVersion = 99` is rejected with a warning log; other widgets still load.

#### Phase 5: Grid layout migration (small)
**Goal:** weather is the second widget. Existing weather UI moves behind the API.

Tasks:
1. Implement `WeatherWidgetFactory` analogous to `ClockWidgetFactory`. Keep using the existing `WeatherViewModel` + `WeatherRepository` + `WeatherApi` — they get wrapped by the new widget shell, not rewritten.
2. Move city selection out of the global dashboard chrome into the weather widget's config schema (city picker as a custom ConfigField type, OR a separate "city" Text field plus an inline picker accessible from the widget). For v1: simplest path — keep the existing city-search dialog, trigger it from a button inside the weather widget.
3. Built-in widgets are enabled by default; user can disable via settings.

Files:
- new: `composeApp/.../widget/builtin/WeatherWidgetFactory.kt`
- mod: `composeApp/.../weather/*` (no logic changes; just rewiring)
- mod: `composeApp/.../di/AppModule.kt`

Acceptance:
- Clock + weather render side-by-side in the grid (or stacked, depending on window width).
- Weather still shows city/temp/condition.
- Disable weather in settings → only clock renders.

#### Phase 6: Tier 2 declarative widgets (medium-large)
**Goal:** any-language widgets via YAML + script.

Tasks:
1. `ManifestParser` — uses `kaml` to parse `widget.yaml` into `WidgetManifest` (sealed-class hierarchy for source types).
2. `DeclarativeWidgetLoader.load(dir)` scans `dir/*/widget.yaml`, builds `WidgetDescriptor(source = Declarative(folder), factory = DeclarativeWidgetFactory(...))`.
3. `DeclarativeWidgetFactory.create(config, scope)` returns a `DeclarativeWidget` that:
   - Spawns a polling coroutine on `onResume()`, cancelled on `onSuspend()`.
   - Each tick: invoke the data source, parse JSON, apply bindings, push a `WidgetUiState` to a `MutableState`.
   - `Content()` renders the appropriate template composable, passing the parsed state.
4. Data sources:
   - `CommandDataSource` — uses `ProcessBuilder`, captures stdout (UTF-8), enforces timeout, kills on cancellation. Working dir = widget folder. Env includes resolved secrets as `$WIDGET_<KEY>` env vars.
   - `HttpDataSource` — uses the shared Ktor client; supports header templating with `$key` config references; respects timeout.
   - `FileDataSource` — reads a file from the widget folder once (or on every tick if `refresh:` set).
5. JSONPath binder: a minimal subset (`$.path.to.field`, array `$.list[*]`, no advanced features). Hand-rolled — no third-party JsonPath library.
6. Templates (each is a Compose composable + a typed `*State` class):
   - `TextTemplate(state: TextState)` — big number or string.
   - `KvTemplate(state: KvState)` — two-column key/value list.
   - `ListTemplate(state: ListState)` — rows of label + value + optional trend arrow.
   - `ChartTemplate(state: ChartState)` — sparkline + bar chart via `Canvas`. Reuse drawing primitives from `ui/MeshGradientModifier.kt`.
   - `ImageTemplate(state: ImageState)` — Coil-backed image from URL.
   - `GridTemplate(state: GridState)` — uniform tiles.
7. Build `samples/sample-declarative-widget/` with a Python script that returns fake stock data; wire as a `list` template.

Files:
- new: `composeApp/.../widget/host/{ManifestParser,DeclarativeWidgetLoader,DeclarativeWidgetFactory,JsonPathBinder,DataSource}.kt`
- new: `composeApp/.../widget/host/templates/*.kt` (six files)
- new: `samples/sample-declarative-widget/{widget.yaml,fetch.py}`
- mod: `docs/widget-authors.md` (add Tier 2 quickstart)
- new tests: `ManifestParserTest`, `JsonPathBinderTest`, `DeclarativeWidgetTest` (with fake data source)

Acceptance:
- Drop `sample-declarative-widget/` folder into `~/.screensaver/widgets/`, restart app, enable in settings, it polls the Python script + renders in `list` template.
- Edit `refresh: 60s` → `10s` in `widget.yaml`, hit tray "Reload widgets" → poll interval changes.
- Bad YAML → loader logs error, skips; other widgets unaffected.
- Script that exits non-zero → widget renders an error chrome; other widgets unaffected.

### Milestone C — Idle activation

#### Phase 7: Tray daemon scaffold + stub idle monitor (small)
**Goal:** the app runs in the tray; can summon the dashboard on demand.

Tasks:
1. `TrayDaemon` — uses `java.awt.SystemTray.getSystemTray()`. Icon (small 32x32 PNG). Popup menu: "Show now", "Settings", "Reload widgets", "Quit". On Wayland where `SystemTray.isSupported()` returns false: log a warning, run headless with stdin-readable commands (`show`, `quit`) for testing.
2. `IdleMonitor` interface: `suspend fun watch(thresholdMs: Long): Flow<IdleEvent>` where `IdleEvent = { Idle | Active }`.
3. `StubIdleMonitor` — for now, fires `Idle` immediately on "Show now" and `Active` on any input. Real OS hooks come in subsequent phases.
4. `--daemon` mode in `main.kt` (default for `./gradlew :composeApp:run` going forward): instantiates `TrayDaemon`, doesn't show window. On idle event or "Show now" → spawns a fresh `Window {...}` instance for the dashboard. On exit trigger → fades out, disposes window, daemon stays alive.
5. The window-spawn API: `DashboardController` (Koin singleton) — exposes `showDashboard()` / `hideDashboard()`. Holds the current `Window` reference.

Files:
- new: `composeApp/.../daemon/{TrayDaemon,IdleMonitor,StubIdleMonitor,DashboardController}.kt`
- mod: `composeApp/.../di/AppModule.kt`
- mod: `composeApp/src/jvmMain/kotlin/main.kt`

Acceptance:
- `./gradlew :composeApp:run` (no args) shows tray icon, no window.
- Right-click tray → "Show now" → window fades in.
- Esc / "Hide" → window fades out, tray stays.
- "Quit" exits the process.

#### Phase 8: Windows idle (small)
**Goal:** real idle detection on Windows.

Tasks:
1. `WindowsIdleMonitor` uses JNA to call `user32.GetLastInputInfo`. Wraps in a polling coroutine (2s interval). Emits `Idle` when delta > threshold, `Active` when it crosses back.
2. `IdleMonitorFactory.create()` returns `WindowsIdleMonitor` when `System.getProperty("os.name").startsWith("Windows")`, else `StubIdleMonitor` (for now).
3. Hook into Koin module.

Files:
- new: `composeApp/.../daemon/WindowsIdleMonitor.kt`, `IdleMonitorFactory.kt`
- mod: `composeApp/.../di/AppModule.kt`

Acceptance:
- On Windows: leave the machine idle for the configured timeout → dashboard appears. Move mouse → fades out.
- Tray "Show now" still works as manual override.

#### Phase 9: Linux X11 idle (small-medium)
**Goal:** real idle on X11.

Tasks:
1. `X11IdleMonitor` uses JNA to call `XScreenSaverQueryInfo` on `libXss`. Same polling shape as the Windows impl.
2. Detect X11 vs Wayland via `$XDG_SESSION_TYPE` env var; pick X11 impl if `x11`.

Files:
- new: `composeApp/.../daemon/X11IdleMonitor.kt`
- mod: `IdleMonitorFactory.kt`

Acceptance:
- On a Linux X11 desktop: idle threshold triggers dashboard appear.

#### Phase 10: Linux Wayland / GNOME idle (medium)
**Goal:** Wayland idle via D-Bus.

Tasks:
1. `DBusIdleMonitor` — for v1, shell out to `gdbus` rather than bundle dbus-java:
   ```
   gdbus call --session --dest org.gnome.Mutter.IdleMonitor \
     --object-path /org/gnome/Mutter/IdleMonitor/Core \
     --method org.gnome.Mutter.IdleMonitor.GetIdletime
   ```
   Polls every 2s. Same emission shape.
2. For KDE: try `org.freedesktop.ScreenSaver.GetSessionIdleTime` as fallback.
3. If neither works, fall back to `StubIdleMonitor` with a logged warning so the app still functions.

Files:
- new: `composeApp/.../daemon/DBusIdleMonitor.kt`
- mod: `IdleMonitorFactory.kt`

Acceptance:
- On GNOME Wayland: idle threshold triggers dashboard appear.
- If `gdbus` not present: warning logged, stub fallback in place, app still runs.

### Milestone D — First-party widgets

#### Phase 11: Todos widget (small-medium)
**Goal:** the first productivity widget, fully built using the public API.

Tasks:
1. Data model: `Todo(id: String, text: String, done: Boolean, createdAt: Long, updatedAt: Long)`.
2. `TodosWidgetFactory` + `TodosWidget`:
   - Uses `scope.storage` to read/write `List<Todo>` (kstore).
   - `onResume()` loads the list; `Content()` renders an inline text input + checkbox list with swipe-to-delete.
   - All mutations write back optimistically, then queue a background sync (Phase 14 wires the backend).
3. ConfigSchema: `Bool("hideDone", "Hide completed")`, `Enum("sort", "Sort by", [newest|oldest|alphabetical])`.

Files:
- new: `composeApp/.../widget/builtin/TodosWidgetFactory.kt`
- new: `composeApp/.../widget/builtin/todos/{Todo,TodosWidget}.kt`
- new tests: `TodosWidgetTest` (storage round-trip, sort, hideDone)

Acceptance:
- Enable widget → empty list.
- Add todo → checkbox + text appear.
- Toggle done → strikethrough.
- Swipe-delete → removed.
- Restart app → todos persist.

#### Phase 12: Expense widget (medium)
**Goal:** quick-add expenses + recent-history visualization.

Tasks:
1. Data model: `Expense(id, amount: Double, category: String, note: String?, occurredAt: Long, createdAt: Long, updatedAt: Long)`.
2. `ExpensesWidgetFactory` + `ExpensesWidget`:
   - Quick-add form: amount + category dropdown + optional note.
   - Below the form: sparkline of last-7-days totals (uses `ChartTemplate` primitives — extract the canvas logic to a shared `widget/charts/Sparkline.kt`).
   - Tap a day in the sparkline → shows that day's items in a sheet.
3. ConfigSchema: `Enum("currency", "Currency", [USD|EUR|GBP|INR|...])`, `Text("categories", "Categories", default="food,transport,entertainment")`.
4. Storage: kstore keyed by month (`expenses/2026-05.json`) so we never load the entire history.

Files:
- new: `composeApp/.../widget/builtin/ExpensesWidgetFactory.kt`
- new: `composeApp/.../widget/builtin/expenses/{Expense,ExpensesWidget,Sparkline}.kt`
- new tests: `ExpensesWidgetTest`

Acceptance:
- Enable widget → empty.
- Add expense → appears in sparkline (today's bar grows).
- Restart → persists.
- Across-month logic: add an expense with an `occurredAt` from last month → routed to last month's file; current month's sparkline unaffected.

### Milestone E — Polish

#### Phase 13: Settings UI (medium)
**Goal:** users configure everything in-app, no JSON editing.

Tasks:
1. Restructure `SettingsDialog` into a tabbed layout: "Display" | "Widgets" | "Activation" | "Backend" | "About".
2. **Display tab:** existing theme toggle + 24h toggle. Move clock design out — it's now per-widget config.
3. **Widgets tab:** list of all discovered widgets with on/off toggle. Clicking a widget expands an inline config panel auto-generated from its `configSchema` (Phase 3 contract). `ConfigFieldRenderer` maps each `ConfigField` subtype to its Compose UI (TextField, Switch, Slider, etc.). Secrets render as password fields; values stored in `SecretStorage`.
4. **Activation tab:** idle timeout slider (1m / 5m / 15m / 30m / 60m, plus custom), tray-icon toggle, "start with system" toggle (Windows: writes to registry Run key; Linux: enables systemd-user unit).
5. **Backend tab:** base URL + API key. Placeholder until Phase 14.
6. **About tab:** version, links, widget-author quickstart link.
7. `SecretStorage` impl: on Windows, JNA call into `Credential Manager` (CredWrite/CredRead). On Linux, JNA into `libsecret` if available; fallback to an obfuscated file at `~/.screensaver/secrets.dat` (XOR with a per-install salt; documented as not-real-encryption).

Files:
- new: `composeApp/.../settings/{ConfigFieldRenderer,SecretStorage}.kt`
- mod: `composeApp/.../settings/SettingsDialog.kt` (major rework)
- mod: `composeApp/.../settings/SettingsModel.kt` (add fields)
- new tests: `ConfigFieldRendererTest`, `SecretStorageTest`

Acceptance:
- Settings dialog tabs render.
- Toggle a widget on/off → grid updates.
- Edit a widget config field → widget receives the new config on next refresh (no app restart).
- Set a secret in a widget config → stored in OS keychain (verifiable via `cmdkey /list` on Windows).
- Change idle timeout → daemon picks up new threshold (without restart, ideally).

### Milestone F — Backend (deferred / partial)

#### Phase 14: Backend sync (size depends on backend specs)
**Goal:** todos + expenses sync to user's backend.

Tasks (when user shares the repo specs):
1. `BackendClient` — wraps the existing `KtorClient`. Holds base URL + token from settings.
2. Generic `SyncRepository<T>(localStore, backendClient, endpoint)`:
   - Reads from local on `getAll()` (fast).
   - On any mutation: write local, enqueue sync task.
   - Background coroutine drains the queue: POST/PUT to backend, on 200 mark synced; on failure, retry with exponential backoff.
   - On startup + periodically: pull from backend, last-write-wins by `updatedAt`.
3. Replace todo/expense raw storage calls with `SyncRepository`.
4. Conflict UI: if a sync conflict surface is needed, a small badge on the widget showing "N items unsynced" / "conflict".

Files:
- new: `composeApp/.../network/BackendClient.kt`
- new: `composeApp/.../storage/SyncRepository.kt`
- mod: todo/expense widget storage paths

Acceptance:
- Add todo offline → appears locally with "unsynced" badge.
- Reconnect → badge clears.
- Backend has a newer version of the same todo → local takes the backend version (last-write-wins by `updatedAt`).

### Milestone G — Packaging

#### Phase 15: Native packaging (medium)
**Goal:** users install with one click on Windows + Linux.

Tasks:
1. Windows MSI (existing `targetFormats(TargetFormat.Msi)` already produces one) — verify it includes:
   - Shortcut + Start Menu entry (already configured).
   - Auto-start option via registry Run key (offered by installer).
2. Windows `.scr` task — `tasks.register("packageScr") { dependsOn(...packageMsi); doLast { copy/rename the bundled exe to .scr } }`. The .scr exe accepts `/s` (show), `/p` (preview), `/c` (config) and routes via the Args parser.
3. Windows installer helper PowerShell script `dist/windows/install-screensaver.ps1`:
   - Copies the `.scr` to `C:\Windows\System32\`.
   - Opens `desk.cpl,,@screensaver` so the user can select it.
4. Linux:
   - `.deb` (existing `targetFormats(TargetFormat.Deb)`) — verify it installs to `/opt/screensaver-app/` and registers a desktop file.
   - `dist/linux/screensaver-app.desktop` — installed under `/usr/share/applications/`.
   - `dist/linux/screensaver-app.service` (systemd-user unit) — installed under `~/.config/systemd/user/`, started via `systemctl --user enable --now`.
   - `dist/linux/install.sh` — copies the desktop file + systemd unit + (optional) registers with xscreensaver via writing to `~/.xscreensaver`.
5. Verify packages install + run on a clean VM for each OS.

Files:
- mod: `composeApp/build.gradle.kts` (new Gradle tasks)
- new: `composeApp/dist/windows/install-screensaver.ps1`
- new: `composeApp/dist/linux/screensaver-app.desktop`
- new: `composeApp/dist/linux/screensaver-app.service`
- new: `composeApp/dist/linux/install.sh`

Acceptance:
- Fresh Windows 11 VM: install MSI → tray icon appears at next login → idle triggers dashboard.
- Fresh Ubuntu VM: install `.deb`, run install.sh → systemd-user unit active → idle triggers dashboard.
- `.scr` route: copy to System32, set in Windows screensaver settings, idle, dashboard appears as the screensaver.

---

## Cross-cutting concerns

### Logging
- All host logging via Kermit (already a dep).
- Per-widget `WidgetLogger` is a Kermit child logger tagged with widget id.
- On Windows, append a rotating log at `%APPDATA%\screensaver\logs\app.log`. On Linux, `~/.local/state/screensaver/logs/app.log`.

### Error handling
- The widget `Content()` composable is always wrapped in an error boundary in `WidgetCard.kt`. Crash → small error chrome with the message; widget stays alive (you can disable via settings).
- Data source failures (Tier 2) are caught inside `DeclarativeWidget` — render `state = Error(message)`; template renders an error placeholder.
- Loader failures (bad YAML, missing classes) are logged + the widget is skipped; other widgets unaffected. Never block startup on a bad widget.

### Threading
- All widget UI runs on the main thread (Compose default).
- Background work (data fetch, kstore I/O) on `Dispatchers.IO`.
- Each widget gets its own `SupervisorJob` scope so one widget's exception doesn't cancel others.

### Testing
- `commonTest` (jvmTest): API contract tests, manifest parser, JSONPath, registry semantics, individual widget logic.
- Integration tests via `compose-ui-test` for the dashboard grid + widget rendering.
- Manual platform tests on actual OS (Windows + at least one X11 + Wayland Linux box) — automate the assertions where possible; idle-detection itself is hard to unit-test, so we test the polling logic with a fake `LastInputInfo` provider.

### CI
- Out of scope for v1. Document a single `./gradlew :composeApp:jvmTest && ./gradlew :composeApp:run` smoke command.

### Documentation
- `docs/widget-authors.md`: Tier 1 + Tier 2 quickstarts with full sample code.
- `docs/architecture.md`: this plan, condensed, kept up to date.
- Inline KDoc on every public API symbol in `:widget-api`.

---

## Decision log (locked in)

| Question | Decision | Reasoning |
|---|---|---|
| Language | Kotlin / Compose Multiplatform | Lock-screen-overlay (the only Rust argument) is dropped |
| Platforms | Windows + Linux at parity | User decision |
| Activation | Tray daemon (primary) + `.scr`/screensaver-hook fallback | User decision |
| Widget tiers in v1 | Tier 1 (JAR) + Tier 2 (YAML+script) | User decision |
| Widget distribution | Filesystem only in v1 (`~/.screensaver/widgets/`) | User decision; marketplace deferred |
| Inter-widget communication | None (widgets are isolated) | User decision; avoids MagicMirror's notification spaghetti |
| Multi-instance widgets | No (one instance per widget id) | Simplification for v1; revisit |
| Widget sandboxing | None in v1 (documented risk) | JVM widget hosts don't sandbox; raising bar for v2 |
| Settings storage | kstore (file-based JSON) | Already a dep, fits the repository pattern |
| Backend data store | Same as widget storage (sync layer wraps it) | Defer until backend specs arrive |
| Secret storage | OS keychain (Windows CredentialManager / libsecret) with obfuscated-file fallback | Reasonable for a desktop app |
| JSONPath impl | Hand-rolled minimal subset | Avoid pulling in a 300KB library for ~50 lines of logic |
| YAML lib | charleskorn/kaml | Pure Kotlin, works on JVM, kotlinx-serialization-style |
| Idle detection (Linux Wayland) | `gdbus` shell-out v1; consider dbus-java if shell-out is unreliable | Avoid bundling 1.5MB of D-Bus deps unless needed |
| Linux ClassLoader isolation | None in v1 (widgets share host classpath) | Filtering correctly is fiddly; v1 audience is devs running their own code |

---

## Open questions / deferred to user

- **Backend repo URLs + API contracts.** Blocks Phase 14 (todo/expense sync). Local storage works fine without.
- **Auth model for backend.** OAuth? Bearer token? None (local-network only)? Affects Phase 14 + Settings backend tab.
- **Which Linux DEs to prioritize for testing.** Code paths cover X11 + GNOME Wayland; KDE Wayland and sway can be tested as users hit them.
- **App icon / branding.** Currently the icons in `composeApp/desktopAppIcons/` are placeholders.
- **License.** Project currently has no LICENSE file. Affects whether third parties can write widgets that depend on `:widget-api`.

---

## Effort estimate (rough)

| Milestone | Phases | Size |
|---|---|---|
| A — Foundations | 0, 1, 2 | Small |
| B — Widget system | 3, 4, 5, 6 | **Largest** (Phase 6 alone is ~1/4 of total work) |
| C — Idle activation | 7, 8, 9, 10 | Medium |
| D — First-party widgets | 11, 12 | Small-Medium |
| E — Polish (settings UI) | 13 | Medium |
| F — Backend | 14 | Unknown until specs arrive |
| G — Packaging | 15 | Medium |

The critical path is **Phase 3 → 4 → 6** (widget API + JAR loading + declarative). Get those right and the rest is incremental.

---

## Verification protocol

Per phase, run:
1. `./gradlew :composeApp:jvmTest` — unit tests pass.
2. `./gradlew :composeApp:run` — app launches in the relevant mode.
3. Manual smoke test specific to the phase (acceptance criteria above).

Per milestone:
- Cross-platform smoke: actually run the latest build on Windows AND one Linux box. Don't trust "compiles on Windows == works on Linux."

Before declaring v1 done:
- Fresh-VM install on Windows 11 + Ubuntu 24.04 + Fedora KDE.
- Five hours of "leave it running" on a real machine. No crashes, no leaks, idle detection stays accurate, widgets keep polling correctly.
