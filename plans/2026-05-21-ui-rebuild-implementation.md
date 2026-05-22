# Dwell UI Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the existing single-design dashboard with a three-mode UI (Cinematic / Ambient / Console), each with two variants, a new widget rendering contract, a Settings side-sheet, and three new built-in widgets (Calendar, IdleCounter, WeatherForecast).

**Architecture:** A `ModeHost` composable selects between three mode renderers based on `SettingsModel.mode`. Each mode internally dispatches by its `*Variant` enum. Widgets implement a new `Render(target, scope)` + `summary()` contract that the host calls with one of three `WidgetRenderTarget`s (Tile / Chip / Minimal). Console adds a 12×6 grid layout engine with drag-resize edit mode. Settings is rebuilt as a side-sheet slide-in. Existing idle detection, tray daemon, and launch plumbing are unchanged.

**Tech Stack:** Kotlin Multiplatform · Compose Desktop 1.11 · Koin 4.2 · Ktor 3.5 · kotlinx.serialization · kstore. Build: `JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$JAVA_HOME/bin:$PATH ./gradlew :composeApp:compileKotlinJvm`.

**Spec:** `plans/2026-05-21-ui-rebuild-design.md`

---

## Phase boundaries (each is independently mergeable, build stays green)

| Phase | Lands | Risk |
|---|---|---|
| 0. Design tokens + fonts | New `ui/` package, bundled fonts in resources. Nothing wired in. | Low |
| 1. Widget API extension | New types in `widget-api` (`WidgetRenderTarget`, `WidgetSummary`, `WidgetSize`, `GridRect`). New `summary()` on the `Widget` interface with a default. Existing renderers untouched. | Low |
| 2. Built-in widgets adopt new contract | Existing 4 built-ins (Clock, Weather, Todos, Expenses) gain `summary()` + per-target `Render`. Old `Content(modifier)` kept for now. | Medium |
| 3. `SettingsModel` extension | New fields (mode, variants, layouts, etc.) with defaults. No migration yet. | Low |
| 4. ModeHost + Cinematic Dusk | New `ModeHost.kt` + `CinematicMode.kt` + `DuskBackdrop.kt` + `WidgetDrawer.kt`. Wired into `App.kt` behind `mode == Cinematic`. Other modes fall back to existing UI. | Medium |
| 5. Ambient Lumen + Borealis | `AmbientMode.kt`, `Lumen.kt`, `Borealis.kt`, `OrbitalDial.kt`, `AuroraRibbons.kt`. | Medium |
| 6. Console Standard (static grid) | `ConsoleMode.kt`, `ConsoleGrid.kt`. No drag-resize yet — uses author defaults. | Medium |
| 7. Console edit mode | `ConsoleEditOverlay.kt`, drag/resize gesture handling, layout persistence. | High |
| 8. New built-in widgets | `CalendarWidget.kt`, `IdleCounterWidget.kt`. Forecast deferred to its own phase. | Low |
| 9. WeatherForecast widget | `WeatherForecastWidget.kt` + repo/api/vm extensions. | Medium |
| 10. Settings sheet rebuild | `SettingsSheet.kt` + 5 section files. Replaces old `SettingsDialog`. | Medium |
| 11. Keyboard map + corner buttons + mode-change motion | `CornerButtons.kt`, KeyEventHandler updates, ModeHost cross-fade. | Medium |
| 12. Variant polish | Cinematic Noir, Console Amber, Quieter Lumen toggle. | Low |
| 13. Migration + cleanup | `PreferencesRepository` migration; delete `clockdigits/`, `ClockViewModel.kt`, old `SettingsDialog.kt`, `WidgetCard.kt`, `WidgetGrid.kt`, the old `Content(modifier)` on built-ins. | Medium |

**Build verification** after each task: `JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm` should succeed.

---

## Phase 0 — Design tokens + fonts

### Task 0.1: Bundle Inter Tight + JetBrains Mono fonts

**Files:**
- Download to: `composeApp/src/commonMain/composeResources/font/InterTight-{ExtraLight,Light,Regular,Medium,SemiBold,Bold}.ttf`
- Download to: `composeApp/src/commonMain/composeResources/font/JetBrainsMono-{ExtraLight,Light,Regular,Medium}.ttf`

- [ ] **Step 1: Download fonts (OFL-licensed, redistributable)**

```bash
mkdir -p composeApp/src/commonMain/composeResources/font
cd composeApp/src/commonMain/composeResources/font
# Inter Tight
for w in ExtraLight Light Regular Medium SemiBold Bold; do
  curl -sSL -o "InterTight-${w}.ttf" "https://github.com/rsms/inter/raw/refs/heads/master/docs/font-files/InterTight-${w}.ttf"
done
# JetBrains Mono
for w in ExtraLight Light Regular Medium; do
  curl -sSL -o "JetBrainsMono-${w}.ttf" "https://github.com/JetBrains/JetBrainsMono/raw/master/fonts/ttf/JetBrainsMono-${w}.ttf"
done
ls -la
```

- [ ] **Step 2: Verify fonts are present**

```bash
ls composeApp/src/commonMain/composeResources/font/ | wc -l
```
Expected: `10` (6 Inter Tight + 4 JetBrains Mono).

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/composeResources/font/
git commit -m "Bundle Inter Tight + JetBrains Mono for UI rebuild"
```

### Task 0.2: Create `ui/Tokens.kt` with color + spacing + motion tokens

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/ui/Tokens.kt`

- [ ] **Step 1: Write the tokens file**

```kotlin
package com.droidslife.screensaver.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Base neutral palette shared across all modes. */
object DwellColors {
    val BgVoid     = Color(0xFF050507)
    val Surface0   = Color(0xFF0B0B0C)
    val Surface1   = Color(0xFF131316)
    val Stroke     = Color(0xFF1F1F22)
    val TextHigh   = Color(0xFFEAEAEA)
    val TextMid    = Color(0xFF8D92A2)
    val TextLow    = Color(0xFF6F7686)
    val TextFaint  = Color(0xFF5C6173)
    val StatusAccent = Color(0xFFF3A280)
    val StatusError  = Color(0xFFC46C6C)
    val StatusOk     = Color(0xFF6CBB8A)

    // Mode accents
    val DuskPeach   = Color(0xFFF3A280)
    val DuskViolet  = Color(0xFFB46CC4)
    val DuskMidnight= Color(0xFF3C50A0)
    val NoirGlow    = Color(0xFFFFF5E1) // warm white
    val LumenCyan   = Color(0xFF7ADCFF)
    val LumenMidnightDeep = Color(0xFF03060D)
    val LumenMidnight     = Color(0xFF050A18)
    val LumenNavy   = Color(0xFF0D2238)
    val BorealisNight    = Color(0xFF02030A)
    val BorealisNightDeep= Color(0xFF010108)
    val BorealisGreen    = Color(0xFF7BEFB1)
    val BorealisMagenta  = Color(0xFFD779E5)
    val BorealisTeal     = Color(0xFF52C8DC)
    val ConsoleGreen     = Color(0xFF9ECDA0)
    val ConsoleAmber     = Color(0xFFF3B95E)
}

/** 8pt spacing grid. */
object DwellSpacing {
    val xs: Dp = 4.dp
    val s : Dp = 8.dp
    val m : Dp = 16.dp
    val l : Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 48.dp
    val xxxl: Dp = 64.dp
}

/** Corner radii. */
object DwellRadius {
    val xs: Dp = 8.dp
    val m : Dp = 12.dp
    val l : Dp = 16.dp
    val xl: Dp = 24.dp
}

/** Animation durations (ms). */
object DwellMotion {
    const val MountFade = 800
    const val UnmountFade = 400
    const val ModeChange = 600
    const val SettingsSheetSlide = 350
    const val CornerHover = 180
    const val ToastFade = 200
    const val TileReflow = 200

    /** M3 standard easing. */
    val Standard = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** M3 emphasized easing (steeper start). */
    val Emphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
}
```

- [ ] **Step 2: Build**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/ui/Tokens.kt
git commit -m "Add Dwell design tokens (colors, spacing, radii, motion)"
```

### Task 0.3: Create `ui/Fonts.kt` referencing bundled fonts

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/ui/Fonts.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.droidslife.screensaver.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.droidslife.screensaver.composeapp.generated.resources.*
import org.jetbrains.compose.resources.Font

/** Bundled font families. Loaded via Compose Resources. */
object DwellFonts {
    @Composable
    fun interTight(): FontFamily = FontFamily(
        Font(Res.font.InterTight_ExtraLight, FontWeight.ExtraLight),
        Font(Res.font.InterTight_Light, FontWeight.Light),
        Font(Res.font.InterTight_Regular, FontWeight.Normal),
        Font(Res.font.InterTight_Medium, FontWeight.Medium),
        Font(Res.font.InterTight_SemiBold, FontWeight.SemiBold),
        Font(Res.font.InterTight_Bold, FontWeight.Bold),
    )

    @Composable
    fun jetBrainsMono(): FontFamily = FontFamily(
        Font(Res.font.JetBrainsMono_ExtraLight, FontWeight.ExtraLight),
        Font(Res.font.JetBrainsMono_Light, FontWeight.Light),
        Font(Res.font.JetBrainsMono_Regular, FontWeight.Normal),
        Font(Res.font.JetBrainsMono_Medium, FontWeight.Medium),
    )
}
```

- [ ] **Step 2: Build (may regenerate Compose Resources)**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
```
Expected: `BUILD SUCCESSFUL` — Compose Resources auto-generates `Res.font.*` references from the new files.

If the Res.font references don't resolve, run `./gradlew :composeApp:generateComposeResClass` and check `composeApp/build/generated/compose/...` for the actual symbol names (font filenames map to property names with dashes/dots replaced by underscores).

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/ui/Fonts.kt
git commit -m "Wire Inter Tight + JetBrains Mono font families"
```

---

## Phase 1 — Widget API extension

### Task 1.1: Add `WidgetRenderTarget`, `WidgetSummary`, `WidgetAccent`

**Files:**
- Create: `widget-api/src/commonMain/kotlin/com/droidslife/screensaver/widget/api/WidgetRender.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.droidslife.screensaver.widget.api

/** Which "slot" the host is asking the widget to fill. */
enum class WidgetRenderTarget {
    /** Full card with author-declared size; visible in Console mode. */
    Tile,
    /** Compact one-row chip (~60dp); shown in Cinematic drawer. */
    Chip,
    /** Single dim line of text, no chrome; shown in Ambient when widget is enabled. */
    Minimal,
}

/** Emotional tint for default renderers. */
enum class WidgetAccent { Default, Positive, Negative, Neutral }

/**
 * A widget's at-a-glance summary, used by host-default Chip/Minimal/Tile renderers
 * and by the Cinematic meta-line / Ambient minimal renderer.
 */
data class WidgetSummary(
    val primaryValue: String,
    val primaryLabel: String? = null,
    val subtitle: String? = null,
    val accent: WidgetAccent = WidgetAccent.Default,
)
```

- [ ] **Step 2: Build**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :widget-api:compileKotlinJvm
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add widget-api/src/commonMain/kotlin/com/droidslife/screensaver/widget/api/WidgetRender.kt
git commit -m "Add WidgetRenderTarget/WidgetSummary/WidgetAccent to widget API"
```

### Task 1.2: Add `WidgetSize` and `GridRect`

**Files:**
- Create: `widget-api/src/commonMain/kotlin/com/droidslife/screensaver/widget/api/WidgetSize.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.droidslife.screensaver.widget.api

import kotlinx.serialization.Serializable

/** Author-declared size constraints, used by Console grid layout. */
@Serializable
data class WidgetSize(
    val minCols: Int = 2,
    val minRows: Int = 1,
    val defaultCols: Int = 4,
    val defaultRows: Int = 2,
    val maxCols: Int = 12,
    val maxRows: Int = 6,
) {
    init {
        require(minCols in 1..12) { "minCols out of range" }
        require(minRows in 1..6) { "minRows out of range" }
        require(maxCols in minCols..12)
        require(maxRows in minRows..6)
        require(defaultCols in minCols..maxCols)
        require(defaultRows in minRows..maxRows)
    }
}

/** A placed widget rect on the 12×6 Console grid. */
@Serializable
data class GridRect(
    val col: Int,
    val row: Int,
    val cols: Int,
    val rows: Int,
) {
    init {
        require(col in 0..11) { "col out of range" }
        require(row in 0..5) { "row out of range" }
        require(cols in 1..12) { "cols out of range" }
        require(rows in 1..6) { "rows out of range" }
        require(col + cols <= 12) { "rect overflows grid horizontally" }
        require(row + rows <= 6) { "rect overflows grid vertically" }
    }
}
```

- [ ] **Step 2: Build**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :widget-api:compileKotlinJvm
```

- [ ] **Step 3: Commit**

```bash
git add widget-api/src/commonMain/kotlin/com/droidslife/screensaver/widget/api/WidgetSize.kt
git commit -m "Add WidgetSize and GridRect to widget API"
```

### Task 1.3: Extend `Widget` interface with `summary()` + `Render(target, scope)`

**Files:**
- Modify: `widget-api/src/commonMain/kotlin/com/droidslife/screensaver/widget/api/Widget.kt`

- [ ] **Step 1: Update the interface**

```kotlin
package com.droidslife.screensaver.widget.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

interface Widget {
    /**
     * Renders the widget body inside a host-provided card.
     * @deprecated kept for Phase 1/2 transition; Phase 13 removes this in favor of Render(target, scope).
     */
    @Composable
    fun Content(modifier: Modifier)

    /**
     * Per-target render. Default delegates to Content for Tile; Chip/Minimal use host defaults.
     * Override per-target to customize.
     */
    @Composable
    fun Render(target: WidgetRenderTarget, scope: WidgetScope, modifier: Modifier = Modifier) {
        when (target) {
            WidgetRenderTarget.Tile    -> Content(modifier)
            WidgetRenderTarget.Chip    -> Content(modifier) // host will wrap with chip chrome
            WidgetRenderTarget.Minimal -> Content(modifier) // host will wrap with minimal chrome
        }
    }

    /** At-a-glance summary. Required for non-Console renderers. */
    fun summary(): WidgetSummary = WidgetSummary(primaryValue = "—")

    val header: String? get() = null
    val preferredSpan: Int get() = 1
    fun onResume() {}
    fun onSuspend() {}
    fun onDispose() {}
}
```

- [ ] **Step 2: Build**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :widget-api:compileKotlinJvm :composeApp:compileKotlinJvm
```
Expected: `BUILD SUCCESSFUL`. Existing widgets work unchanged via the default Render delegating to Content.

- [ ] **Step 3: Commit**

```bash
git add widget-api/src/commonMain/kotlin/com/droidslife/screensaver/widget/api/Widget.kt
git commit -m "Extend Widget interface with Render(target) and summary()"
```

### Task 1.4: Add `preferredSize` to `WidgetFactory`

**Files:**
- Modify: `widget-api/src/commonMain/kotlin/com/droidslife/screensaver/widget/api/WidgetFactory.kt`

- [ ] **Step 1: Add the field with a default**

Read the existing file first (`grep -n 'displayName\|category' widget-api/src/commonMain/kotlin/com/droidslife/screensaver/widget/api/WidgetFactory.kt`), then add inside the `WidgetFactory` interface:

```kotlin
/** Console-grid size constraints. Default = 4×2, clamped between 2×1 and 12×6. */
val preferredSize: WidgetSize get() = WidgetSize()
```

- [ ] **Step 2: Build**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :widget-api:compileKotlinJvm :composeApp:compileKotlinJvm
```

- [ ] **Step 3: Commit**

```bash
git add widget-api/src/commonMain/kotlin/com/droidslife/screensaver/widget/api/WidgetFactory.kt
git commit -m "Add WidgetFactory.preferredSize for Console grid layout"
```

---

## Phase 2 — Built-in widgets adopt new contract

For each built-in widget (Clock, Weather, Todos, Expenses), add `summary()` and `preferredSize`. Keep existing `Content()` as the Tile target via the default `Render(target)`. Phase 4+ overrides `Render` per target.

### Task 2.1: Clock — `summary()` + `preferredSize`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/widget/builtin/ClockWidgetFactory.kt`

- [ ] **Step 1: Add summary() to the Widget impl inside ClockWidgetFactory.create**

Find the inner `Widget` (created via `object : Widget { ... }` or similar) and add:

```kotlin
override fun summary(): WidgetSummary {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val hh = now.hour.toString().padStart(2, '0')
    val mm = now.minute.toString().padStart(2, '0')
    return WidgetSummary(
        primaryValue = "$hh:$mm",
        primaryLabel = "Time",
        subtitle = formatDateLine(now.dayOfWeek, now.month, now.day),
    )
}
```

Also add at factory level:

```kotlin
override val preferredSize: WidgetSize = WidgetSize(
    minCols = 4, minRows = 3,
    defaultCols = 7, defaultRows = 4,
    maxCols = 12, maxRows = 6,
)
```

- [ ] **Step 2: Build**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
```

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/widget/builtin/ClockWidgetFactory.kt
git commit -m "ClockWidget: add summary() and preferredSize"
```

### Task 2.2: Weather — `summary()` + `preferredSize`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/widget/builtin/WeatherWidgetFactory.kt`

- [ ] **Step 1: Add summary() that reads from WeatherViewModel**

```kotlin
override fun summary(): WidgetSummary {
    val state = weatherViewModel.weather.value
    return when (state) {
        is WeatherState.Loaded -> WidgetSummary(
            primaryValue = "${state.tempC.toInt()}°",
            primaryLabel = "Weather",
            subtitle = "${state.condition} · ${state.city}",
        )
        is WeatherState.Loading -> WidgetSummary(primaryValue = "—", primaryLabel = "Weather", subtitle = "Loading…")
        is WeatherState.Failed  -> WidgetSummary(primaryValue = "—", primaryLabel = "Weather", subtitle = "Couldn't load")
        is WeatherState.Unconfigured -> WidgetSummary(primaryValue = "—", primaryLabel = "Weather", subtitle = "API key needed")
    }
}
```

`weatherViewModel` is injected via Koin in the factory; if it isn't already, add `private val weatherViewModel: WeatherViewModel by inject()` (or constructor inject per existing pattern).

```kotlin
override val preferredSize: WidgetSize = WidgetSize(
    minCols = 3, minRows = 2,
    defaultCols = 5, defaultRows = 2,
    maxCols = 8, maxRows = 3,
)
```

- [ ] **Step 2: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/widget/builtin/WeatherWidgetFactory.kt
git commit -m "WeatherWidget: add summary() and preferredSize"
```

### Task 2.3: Todos — `summary()` + `preferredSize`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/widget/builtin/TodosWidgetFactory.kt`

- [ ] **Step 1: Add summary**

```kotlin
override fun summary(): WidgetSummary {
    val open = todos.count { !it.done }
    val dueToday = todos.count { !it.done && it.dueDate == today }
    return WidgetSummary(
        primaryValue = open.toString(),
        primaryLabel = "Todos",
        subtitle = if (open == 0) "Nothing to do" else "$open open${if (dueToday > 0) " · $dueToday due today" else ""}",
    )
}

override val preferredSize: WidgetSize = WidgetSize(
    minCols = 3, minRows = 2,
    defaultCols = 5, defaultRows = 2,
    maxCols = 8, maxRows = 4,
)
```

- [ ] **Step 2: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/widget/builtin/TodosWidgetFactory.kt
git commit -m "TodosWidget: add summary() and preferredSize"
```

### Task 2.4: Expenses — `summary()` + `preferredSize`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/widget/builtin/ExpensesWidgetFactory.kt`

- [ ] **Step 1: Add summary**

```kotlin
override fun summary(): WidgetSummary {
    val total = expenses.sumOf { it.amount }
    return WidgetSummary(
        primaryValue = formatCurrency(total, currencyCode),
        primaryLabel = "Spend · ${currentMonth.format()}",
        subtitle = topCategories(expenses).joinToString(" · ") { "${it.name} ${formatCurrency(it.total, currencyCode)}" },
    )
}

override val preferredSize: WidgetSize = WidgetSize(
    minCols = 3, minRows = 2,
    defaultCols = 4, defaultRows = 2,
    maxCols = 8, maxRows = 3,
)
```

- [ ] **Step 2: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/widget/builtin/ExpensesWidgetFactory.kt
git commit -m "ExpensesWidget: add summary() and preferredSize"
```

---

## Phase 3 — `SettingsModel` extension

### Task 3.1: Add Mode / variants / display fields to `SettingsModel`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/settings/SettingsModel.kt`

- [ ] **Step 1: Add enums + fields**

```kotlin
// At top of file:
import com.droidslife.screensaver.widget.api.GridRect

@Serializable
enum class Mode { Cinematic, Ambient, Console }

@Serializable
enum class CinematicVariant { Dusk, Noir }

@Serializable
enum class AmbientVariant { Lumen, Borealis }

@Serializable
enum class ConsoleVariant { Standard, Amber }
```

Add to `SettingsModel`:

```kotlin
val mode: Mode = Mode.Cinematic,
val cinematicVariant: CinematicVariant = CinematicVariant.Dusk,
val ambientVariant: AmbientVariant = AmbientVariant.Lumen,
val consoleVariant: ConsoleVariant = ConsoleVariant.Standard,
val quieterLumen: Boolean = false,
val showSeconds: Boolean = false,
val showDate: Boolean = true,
val widgetLayouts: Map<String, GridRect> = emptyMap(),
val widgetOrder: List<String> = emptyList(),
val exitOnKeypress: Boolean = true,
```

Keep existing fields (`selectedDesignId`, `currentCity`, etc.) for now — Phase 13 removes them.

- [ ] **Step 2: Add setters to `SettingsViewModel`**

```kotlin
fun setMode(mode: Mode) = updateSettings(settings.copy(mode = mode))
fun setCinematicVariant(v: CinematicVariant) = updateSettings(settings.copy(cinematicVariant = v))
fun setAmbientVariant(v: AmbientVariant) = updateSettings(settings.copy(ambientVariant = v))
fun setConsoleVariant(v: ConsoleVariant) = updateSettings(settings.copy(consoleVariant = v))
fun setQuieterLumen(on: Boolean) = updateSettings(settings.copy(quieterLumen = on))
fun setShowSeconds(on: Boolean) = updateSettings(settings.copy(showSeconds = on))
fun setShowDate(on: Boolean) = updateSettings(settings.copy(showDate = on))
fun setExitOnKeypress(on: Boolean) = updateSettings(settings.copy(exitOnKeypress = on))
fun setWidgetLayout(id: String, rect: GridRect) =
    updateSettings(settings.copy(widgetLayouts = settings.widgetLayouts + (id to rect)))
fun resetWidgetLayouts() = updateSettings(settings.copy(widgetLayouts = emptyMap()))
fun cycleMode() {
    val next = when (settings.mode) {
        Mode.Cinematic -> Mode.Ambient
        Mode.Ambient   -> Mode.Console
        Mode.Console   -> Mode.Cinematic
    }
    setMode(next)
}
fun cycleVariant() {
    when (settings.mode) {
        Mode.Cinematic -> setCinematicVariant(
            if (settings.cinematicVariant == CinematicVariant.Dusk) CinematicVariant.Noir else CinematicVariant.Dusk
        )
        Mode.Ambient -> setAmbientVariant(
            if (settings.ambientVariant == AmbientVariant.Lumen) AmbientVariant.Borealis else AmbientVariant.Lumen
        )
        Mode.Console -> setConsoleVariant(
            if (settings.consoleVariant == ConsoleVariant.Standard) ConsoleVariant.Amber else ConsoleVariant.Standard
        )
    }
}
```

- [ ] **Step 3: Build**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
```

If GridRect serialization fails because of the `init` validation block, change to `@Serializable` only on `GridRect` (no init validation needed for serialization roundtrip; runtime validation only happens during construction in code paths).

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/settings/SettingsModel.kt composeApp/src/commonMain/kotlin/com/droidslife/screensaver/settings/SettingsViewModel.kt
git commit -m "Add Mode/variant fields and setters to SettingsModel"
```

---

## Phase 4 — ModeHost + Cinematic Dusk

### Task 4.1: Create `DefaultRenderers.kt`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/widgets/DefaultRenderers.kt`

- [ ] **Step 1: Write default renderers for Tile/Chip/Minimal**

```kotlin
package com.droidslife.screensaver.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import com.droidslife.screensaver.ui.DwellRadius
import com.droidslife.screensaver.widget.api.WidgetSummary

@Composable
fun DefaultTileRender(s: WidgetSummary, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(DwellRadius.m))
            .background(DwellColors.Surface1)
            .border(1.dp, DwellColors.Stroke, RoundedCornerShape(DwellRadius.m))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        s.primaryLabel?.let {
            Text(
                text = it.uppercase(),
                fontSize = 9.sp,
                letterSpacing = 0.25.sp,
                color = DwellColors.TextLow,
                fontFamily = DwellFonts.interTight(),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = s.primaryValue,
            fontSize = 28.sp,
            color = DwellColors.TextHigh,
            fontFamily = DwellFonts.jetBrainsMono(),
            fontWeight = FontWeight.Medium,
        )
        s.subtitle?.let {
            Text(
                text = it,
                fontSize = 10.sp,
                color = DwellColors.TextMid,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = DwellFonts.interTight(),
            )
        }
    }
}

@Composable
fun DefaultChipRender(s: WidgetSummary, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        s.primaryLabel?.let {
            Text(
                text = it.uppercase(),
                fontSize = 10.sp,
                letterSpacing = 0.15.sp,
                color = DwellColors.TextMid,
                fontFamily = DwellFonts.interTight(),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = s.primaryValue,
            fontSize = 14.sp,
            color = DwellColors.TextHigh,
            fontFamily = DwellFonts.jetBrainsMono(),
        )
    }
}

@Composable
fun DefaultMinimalRender(s: WidgetSummary, modifier: Modifier = Modifier) {
    val label = listOfNotNull(s.primaryLabel?.lowercase(), s.subtitle ?: s.primaryValue)
        .joinToString(" · ")
    Text(
        text = label,
        fontSize = 12.sp,
        color = DwellColors.TextMid,
        fontFamily = DwellFonts.interTight(),
        modifier = modifier,
    )
}
```

- [ ] **Step 2: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/widgets/DefaultRenderers.kt
git commit -m "Add DefaultTile/Chip/Minimal renderers for new widget contract"
```

### Task 4.2: Create `CornerButtons.kt`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/ui/CornerButtons.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.droidslife.screensaver.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun CornerButtons(
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var hovered by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (hovered) 0.95f else 0.32f,
        animationSpec = tween(DwellMotion.CornerHover, easing = DwellMotion.Standard),
        label = "corner-alpha",
    )

    Row(
        modifier = modifier
            .padding(20.dp)
            .alpha(alpha)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CornerIconButton(Icons.Filled.Settings, "Settings", onSettings)
        CornerIconButton(Icons.Filled.HelpOutline, "Help", onHelp)
    }
}

@Composable
private fun CornerIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(DwellColors.Surface1)
            .border(1.dp, DwellColors.Stroke, CircleShape),
    ) {
        Icon(icon, contentDescription = label, tint = DwellColors.TextMid)
    }
}
```

- [ ] **Step 2: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/ui/CornerButtons.kt
git commit -m "Add CornerButtons shared chrome"
```

### Task 4.3: Create `MeshGradientBackdrop.kt` (Dusk backdrop)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/cinematic/MeshGradientBackdrop.kt`

- [ ] **Step 1: Write the backdrop**

```kotlin
package com.droidslife.screensaver.modes.cinematic

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun DuskBackdrop(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "dusk-drift")
    val t by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 60_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dusk-t",
    )

    Canvas(modifier = modifier.fillMaxSize().background(Color(0xFF050307))) {
        // Three blobs drifting on relatively-prime periods
        drawBlob(centerX(0.65f, 0.05f, t * (60f/47f)), centerY(0.30f, 0.04f, t * (60f/47f)),
            Color(0xFFF3A280).copy(alpha = 0.55f))
        drawBlob(centerX(0.20f, 0.06f, t * (60f/53f)), centerY(0.75f, 0.05f, t * (60f/53f)),
            Color(0xFFB46CC4).copy(alpha = 0.50f))
        drawBlob(centerX(0.80f, 0.04f, t * (60f/61f)), centerY(0.90f, 0.03f, t * (60f/61f)),
            Color(0xFF3C50A0).copy(alpha = 0.35f))
    }
}

private fun centerX(base: Float, amp: Float, phase: Float): Float = base + amp * sin(phase)
private fun centerY(base: Float, amp: Float, phase: Float): Float = base + amp * sin(phase + 1.2f)

private fun DrawScope.drawBlob(cx: Float, cy: Float, color: Color) {
    val w = size.width
    val h = size.height
    val r = w * 0.55f
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = 0f)),
            center = Offset(cx * w, cy * h),
            radius = r,
        ),
        size = size,
    )
}
```

- [ ] **Step 2: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/cinematic/MeshGradientBackdrop.kt
git commit -m "Add Dusk mesh gradient backdrop"
```

### Task 4.4: Create `CinematicMode.kt`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/cinematic/CinematicMode.kt`

- [ ] **Step 1: Write the mode composable**

```kotlin
package com.droidslife.screensaver.modes.cinematic

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.settings.CinematicVariant
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.*
import com.droidslife.screensaver.widget.host.WidgetRegistry
import kotlinx.coroutines.delay
import kotlinx.datetime.*

@Composable
fun CinematicMode(
    settingsViewModel: SettingsViewModel,
    registry: WidgetRegistry,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (settingsViewModel.settings.cinematicVariant) {
            CinematicVariant.Dusk -> DuskBackdrop(Modifier.fillMaxSize())
            CinematicVariant.Noir -> NoirBackdrop(Modifier.fillMaxSize())
        }
        CinematicForeground(settingsViewModel, registry)
        CornerButtons(
            onSettings = onOpenSettings,
            onHelp = onOpenHelp,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun CinematicForeground(
    settingsViewModel: SettingsViewModel,
    registry: WidgetRegistry,
) {
    val now by produceTicker()
    val time = "${now.hour.toString().padStart(2,'0')}:${now.minute.toString().padStart(2,'0')}"
    Column(modifier = Modifier.fillMaxSize().padding(start = 80.dp, top = 200.dp)) {
        androidx.compose.material3.Text(
            text = time,
            fontFamily = DwellFonts.interTight(),
            fontWeight = FontWeight.Bold,
            fontSize = 240.sp,
            color = DwellColors.TextHigh,
        )
        Spacer(Modifier.height(14.dp))
        androidx.compose.material3.Text(
            text = "${now.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}, " +
                "${now.day} ${now.month.name.lowercase().replaceFirstChar { it.uppercase() }}",
            fontFamily = DwellFonts.interTight(),
            fontSize = 16.sp,
            color = DwellColors.TextMid,
        )
    }
    WidgetDrawer(settingsViewModel, registry)
}

@Composable
private fun produceTicker(): State<LocalDateTime> {
    return produceState(initialValue = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())) {
        while (true) {
            value = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            delay(15_000)
        }
    }
}
```

NoirBackdrop is a stub for Phase 12; for Phase 4 implement a trivial one:

Create `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/cinematic/NoirBackdrop.kt`:

```kotlin
package com.droidslife.screensaver.modes.cinematic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun NoirBackdrop(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(modifier.fillMaxSize().background(Color(0xFF020203)))
}
```

- [ ] **Step 2: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/cinematic/
git commit -m "Add CinematicMode (Dusk + Noir stub) with clock + meta line"
```

### Task 4.5: Create `WidgetDrawer.kt`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/cinematic/WidgetDrawer.kt`

- [ ] **Step 1: Write the drawer**

```kotlin
package com.droidslife.screensaver.modes.cinematic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.DwellMotion
import com.droidslife.screensaver.widget.api.WidgetRenderTarget
import com.droidslife.screensaver.widget.host.WidgetRegistry
import kotlinx.coroutines.delay

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun BoxScope.WidgetDrawer(
    settingsViewModel: SettingsViewModel,
    registry: WidgetRegistry,
) {
    var visible by remember { mutableStateOf(false) }
    var lastHover by remember { mutableStateOf(0L) }

    // Hover-area detector at bottom 10% of screen
    Box(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.10f)
            .onPointerEvent(PointerEventType.Enter) {
                visible = true
                lastHover = System.currentTimeMillis()
            }
            .onPointerEvent(PointerEventType.Move) {
                lastHover = System.currentTimeMillis()
            }
            .onPointerEvent(PointerEventType.Exit) {
                lastHover = System.currentTimeMillis()
            }
    )

    LaunchedEffect(lastHover) {
        if (visible) {
            delay(2000)
            if (System.currentTimeMillis() - lastHover >= 2000) visible = false
        }
    }

    val instances by registry.instances.collectAsState()
    val enabled = instances.values.filter { it.widget.summary().primaryValue != "—" }

    if (enabled.isEmpty()) return

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it },
        exit = fadeOut(tween(220)) + slideOutVertically(tween(220)) { it },
        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 60.dp).fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp, 12.dp, 0.dp, 0.dp))
                .background(Color(0xD9080812))
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            enabled.forEach { instance ->
                instance.widget.Render(WidgetRenderTarget.Chip, instance.scope, Modifier)
            }
        }
    }
}
```

- [ ] **Step 2: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/cinematic/WidgetDrawer.kt
git commit -m "Add Cinematic WidgetDrawer (hover-peek bottom strip)"
```

### Task 4.6: Create `ModeHost.kt`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/ModeHost.kt`

- [ ] **Step 1: Write a minimal ModeHost that dispatches by `mode`**

```kotlin
package com.droidslife.screensaver.modes

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.droidslife.screensaver.modes.cinematic.CinematicMode
import com.droidslife.screensaver.settings.Mode
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.DwellMotion
import com.droidslife.screensaver.widget.host.WidgetRegistry

@Composable
fun ModeHost(
    settingsViewModel: SettingsViewModel,
    registry: WidgetRegistry,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        targetState = settingsViewModel.settings.mode,
        animationSpec = tween(DwellMotion.ModeChange, easing = DwellMotion.Standard),
        modifier = modifier,
        label = "mode-crossfade",
    ) { mode ->
        when (mode) {
            Mode.Cinematic -> CinematicMode(settingsViewModel, registry, onOpenSettings, onOpenHelp)
            Mode.Ambient   -> PlaceholderMode("Ambient — wired in Phase 5", onOpenSettings, onOpenHelp)
            Mode.Console   -> PlaceholderMode("Console — wired in Phase 6", onOpenSettings, onOpenHelp)
        }
    }
}

@Composable
private fun PlaceholderMode(
    label: String,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().background(com.droidslife.screensaver.ui.DwellColors.BgVoid),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.material3.Text(label, color = com.droidslife.screensaver.ui.DwellColors.TextLow)
    }
    com.droidslife.screensaver.ui.CornerButtons(
        onSettings = onOpenSettings,
        onHelp = onOpenHelp,
        modifier = Modifier.fillMaxSize(),
    )
}
```

Add necessary imports for `fillMaxSize` / `background`.

- [ ] **Step 2: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/ModeHost.kt
git commit -m "Add ModeHost dispatch (Cinematic implemented, Ambient/Console stub)"
```

### Task 4.7: Wire `ModeHost` into `App.kt`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/App.kt`

- [ ] **Step 1: Replace DigitalClockApp call with ModeHost**

The existing `App.kt` renders `DigitalClockApp(...)`. Replace that call with:

```kotlin
val widgetRegistry = koinInject<WidgetRegistry>()
ModeHost(
    settingsViewModel = settingsViewModel,
    registry = widgetRegistry,
    onOpenSettings = { settingsViewModel.openSettingsDialog() },
    onOpenHelp = onShowHelpDialog,
)
```

Keep showCitySelectionDialog/showHelpDialog routing for now — Phase 11 cleans up.

- [ ] **Step 2: Build + run a sanity check (manual)**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:run --args="--show" &
sleep 30
wmctrl -l | grep -i Dwell  # window should be titled "Dwell"
import -window "$(wmctrl -l | grep -i Dwell | awk '{print $1}')" /tmp/cinematic-dusk.png
xdg-open /tmp/cinematic-dusk.png  # visually verify Cinematic Dusk render
pkill -f 'MainKt --show'
```

Expected: dashboard renders with Dusk mesh-gradient backdrop, large clock, meta line. Drawer peeks when mouse near bottom.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/App.kt
git commit -m "Wire ModeHost into App (Cinematic Dusk is now the dashboard)"
```

---

## Phase 5 — Ambient mode (Lumen + Borealis)

### Task 5.1: Create `OrbitalDial.kt` (Lumen's centerpiece)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/ambient/OrbitalDial.kt`

- [ ] **Step 1: Implement via Compose Canvas**

```kotlin
package com.droidslife.screensaver.modes.ambient

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun OrbitalDial(currentMinute: Int, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "dial-pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-alpha",
    )

    val cyan = Color(0xFF7ADCFF)

    Canvas(modifier = modifier) {
        val cx = size.width / 2
        val cy = size.height / 2
        val r = (size.minDimension / 2) - 10.dp.toPx()

        // glow background
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(cyan.copy(alpha = 0.06f), Color.Transparent),
                center = Offset(cx, cy),
                radius = r * 1.1f,
            ),
            radius = r * 1.1f,
            center = Offset(cx, cy),
        )

        // outer + inner rings
        drawCircle(color = cyan.copy(alpha = 0.18f), radius = r, center = Offset(cx, cy), style = Stroke(1f))
        drawCircle(color = cyan.copy(alpha = 0.10f), radius = r * 0.83f, center = Offset(cx, cy), style = Stroke(1f))

        // 60 minute ticks
        for (i in 0 until 60) {
            val angleRad = (i * 6 - 90) * PI.toFloat() / 180f
            val major = (i % 5 == 0)
            val tickLen = if (major) 10.dp.toPx() else 4.dp.toPx()
            val x1 = cx + (r - tickLen) * cos(angleRad)
            val y1 = cy + (r - tickLen) * sin(angleRad)
            val x2 = cx + r * cos(angleRad)
            val y2 = cy + r * sin(angleRad)
            drawLine(
                color = cyan.copy(alpha = if (major) 0.5f else 0.25f),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = if (major) 1.2f else 0.6f,
            )
        }

        // active minute tick (pulses)
        val activeAngle = (currentMinute * 6 - 90) * PI.toFloat() / 180f
        val tickLen = 14.dp.toPx()
        val x1 = cx + (r - tickLen) * cos(activeAngle)
        val y1 = cy + (r - tickLen) * sin(activeAngle)
        val x2 = cx + r * cos(activeAngle)
        val y2 = cy + r * sin(activeAngle)
        drawLine(
            color = cyan.copy(alpha = pulse),
            start = Offset(x1, y1),
            end = Offset(x2, y2),
            strokeWidth = 2f,
        )
    }
}
```

- [ ] **Step 2: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/ambient/OrbitalDial.kt
git commit -m "Add Lumen orbital dial with pulsing current-minute tick"
```

### Task 5.2: Create `Lumen.kt`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/ambient/Lumen.kt`

- [ ] **Step 1: Implement Lumen variant** (per spec §6.2.1)

```kotlin
package com.droidslife.screensaver.modes.ambient

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import kotlinx.coroutines.delay
import kotlinx.datetime.*

@Composable
fun Lumen(
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val quieter = settingsViewModel.settings.quieterLumen
    val now by produceTickerLumen()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(DwellColors.LumenNavy, DwellColors.LumenMidnight, DwellColors.LumenMidnightDeep),
                ),
            ),
    ) {
        if (!quieter) {
            // Corner brackets
            CornerBracket(top = true, start = true, modifier = Modifier.align(Alignment.TopStart))
            CornerBracket(top = true, start = false, modifier = Modifier.align(Alignment.TopEnd))
            CornerBracket(top = false, start = true, modifier = Modifier.align(Alignment.BottomStart))
            CornerBracket(top = false, start = false, modifier = Modifier.align(Alignment.BottomEnd))

            // Top telemetry
            androidx.compose.material3.Text(
                text = "DWELL · ${fmt(now)} · ${weatherStrip()}",
                fontFamily = DwellFonts.jetBrainsMono(),
                fontSize = 10.sp,
                letterSpacing = 0.4.sp,
                color = DwellColors.LumenCyan.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
            )
        }

        // Orbital dial behind clock
        Box(modifier = Modifier.align(Alignment.Center).size(460.dp)) {
            OrbitalDial(currentMinute = now.minute, modifier = Modifier.fillMaxSize())
        }

        // Clock
        Column(
            modifier = Modifier.align(Alignment.Center).offset(y = (-20).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            androidx.compose.material3.Text(
                text = "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}",
                fontFamily = DwellFonts.interTight(),
                fontWeight = FontWeight.ExtraLight,
                fontSize = 152.sp,
                color = Color(0xFFDDF0FF),
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.Text(
                text = "${now.dayOfWeek.name.take(3).uppercase()} · ${now.day} ${now.month.name.take(3).uppercase()} · ${now.year}",
                fontFamily = DwellFonts.jetBrainsMono(),
                fontSize = 11.sp,
                letterSpacing = 0.4.sp,
                color = DwellColors.LumenCyan.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun CornerBracket(top: Boolean, start: Boolean, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(28.dp).padding(18.dp)) {
        val w = size.width
        val h = size.height
        val color = DwellColors.LumenCyan.copy(alpha = 0.3f)
        if (top && start) {
            drawLine(color, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(w, 0f), 1f)
            drawLine(color, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, h), 1f)
        } else if (top && !start) {
            drawLine(color, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(w, 0f), 1f)
            drawLine(color, androidx.compose.ui.geometry.Offset(w, 0f), androidx.compose.ui.geometry.Offset(w, h), 1f)
        } else if (!top && start) {
            drawLine(color, androidx.compose.ui.geometry.Offset(0f, h), androidx.compose.ui.geometry.Offset(w, h), 1f)
            drawLine(color, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, h), 1f)
        } else {
            drawLine(color, androidx.compose.ui.geometry.Offset(0f, h), androidx.compose.ui.geometry.Offset(w, h), 1f)
            drawLine(color, androidx.compose.ui.geometry.Offset(w, 0f), androidx.compose.ui.geometry.Offset(w, h), 1f)
        }
    }
}

private fun fmt(now: LocalDateTime): String =
    "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')} IST"

private fun weatherStrip(): String = "WTH OK"  // Phase 9 wires actual weather

@Composable
private fun produceTickerLumen() = produceState(
    initialValue = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
) {
    while (true) {
        value = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        delay(15_000)
    }
}
```

- [ ] **Step 2: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/ambient/Lumen.kt
git commit -m "Add Lumen ambient variant (HUD telemetry, orbital dial, glow clock)"
```

### Task 5.3: Create `AuroraRibbons.kt`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/ambient/AuroraRibbons.kt`

- [ ] **Step 1: Implement aurora ribbon drawing**

```kotlin
package com.droidslife.screensaver.modes.ambient

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import com.droidslife.screensaver.ui.DwellColors

@Composable
fun AuroraRibbons(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "aurora")
    val t by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 90_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "t",
    )

    Canvas(modifier.blur(20.dp)) {
        val w = size.width
        val h = size.height

        // Ribbon 1: green→teal, upper, drifts L→R
        drawRibbon(
            yBase = h * 0.28f,
            amplitude = h * 0.06f,
            phase = t * 2f * Math.PI.toFloat(),
            color = DwellColors.BorealisGreen,
            secondaryColor = DwellColors.BorealisTeal,
        )
        // Ribbon 2: magenta, mid, R→L
        drawRibbon(
            yBase = h * 0.50f,
            amplitude = h * 0.08f,
            phase = -t * 2f * Math.PI.toFloat() + 1.5f,
            color = DwellColors.BorealisMagenta,
            secondaryColor = Color(0xFF7BB4EF),
        )
        // Ribbon 3: faint green, lower, L→R
        drawRibbon(
            yBase = h * 0.75f,
            amplitude = h * 0.05f,
            phase = t * 2f * Math.PI.toFloat() + 3f,
            color = DwellColors.BorealisGreen.copy(alpha = 0.5f),
            secondaryColor = DwellColors.BorealisGreen.copy(alpha = 0f),
        )
    }
}

import androidx.compose.ui.graphics.drawscope.DrawScope

private fun DrawScope.drawRibbon(
    yBase: Float,
    amplitude: Float,
    phase: Float,
    color: Color,
    secondaryColor: Color,
) {
    val path = Path()
    val w = size.width
    val steps = 64
    val centerY = yBase + amplitude * kotlin.math.sin(phase)
    path.moveTo(-50f, centerY - 40f)
    for (i in 0..steps) {
        val x = -50f + (w + 100f) * i / steps
        val y = yBase + amplitude * kotlin.math.sin(phase + (x / w) * 3f)
        path.lineTo(x, y - 40f)
    }
    for (i in steps downTo 0) {
        val x = -50f + (w + 100f) * i / steps
        val y = yBase + amplitude * kotlin.math.sin(phase + (x / w) * 3f)
        path.lineTo(x, y + 40f)
    }
    path.close()
    drawPath(
        path = path,
        brush = Brush.horizontalGradient(
            colors = listOf(color.copy(alpha = 0f), color, secondaryColor, secondaryColor.copy(alpha = 0f)),
        ),
        blendMode = BlendMode.Screen,
    )
}
```

Fix the misplaced `import` — move `import androidx.compose.ui.graphics.drawscope.DrawScope` to the top of the file alongside the other imports before committing.

- [ ] **Step 2: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/ambient/AuroraRibbons.kt
git commit -m "Add Borealis aurora-ribbon canvas drawing"
```

### Task 5.4: Create `Borealis.kt`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/ambient/Borealis.kt`

- [ ] **Step 1: Implement Borealis variant** (per spec §6.2.2)

```kotlin
package com.droidslife.screensaver.modes.ambient

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.ui.DwellFonts
import kotlinx.coroutines.delay
import kotlinx.datetime.*

@Composable
fun Borealis(modifier: Modifier = Modifier) {
    val now by produceTickerBorealis()
    Box(
        modifier = modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(Color(0xFF061026), DwellColors.BorealisNight, DwellColors.BorealisNightDeep),
            ),
        ),
    ) {
        AuroraRibbons(modifier = Modifier.fillMaxSize())

        // Clock + date + place
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            androidx.compose.material3.Text(
                text = "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}",
                fontFamily = DwellFonts.interTight(),
                fontWeight = FontWeight.Light,
                fontSize = 158.sp,
                color = Color(0xEAFFFFFF),
            )
            Spacer(Modifier.height(20.dp))
            androidx.compose.material3.Text(
                text = "${now.dayOfWeek.name.take(3).replaceFirstChar { it.uppercase() }} · ${now.day} ${now.month.name.take(3).replaceFirstChar { it.uppercase() }}",
                fontFamily = DwellFonts.interTight(),
                fontWeight = FontWeight.Light,
                fontSize = 14.sp,
                letterSpacing = 0.3.sp,
                color = Color(0x8CFFFFFF),
            )
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.Text(
                text = "Mumbai", // Phase 9 wires settings.weather.city
                fontFamily = DwellFonts.interTight(),
                fontWeight = FontWeight.Light,
                fontSize = 12.sp,
                letterSpacing = 0.2.sp,
                color = Color(0x52FFFFFF),
            )
        }
    }
}

@Composable
private fun produceTickerBorealis() = produceState(
    initialValue = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
) {
    while (true) {
        value = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        delay(15_000)
    }
}
```

- [ ] **Step 2: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/ambient/Borealis.kt
git commit -m "Add Borealis ambient variant (aurora drift + floating clock)"
```

### Task 5.5: Create `AmbientMode.kt` and wire into ModeHost

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/ambient/AmbientMode.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/ModeHost.kt`

- [ ] **Step 1: AmbientMode**

```kotlin
package com.droidslife.screensaver.modes.ambient

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.droidslife.screensaver.settings.AmbientVariant
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.CornerButtons

@Composable
fun AmbientMode(
    settingsViewModel: SettingsViewModel,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (settingsViewModel.settings.ambientVariant) {
            AmbientVariant.Lumen    -> Lumen(settingsViewModel)
            AmbientVariant.Borealis -> Borealis()
        }
        CornerButtons(
            onSettings = onOpenSettings,
            onHelp = onOpenHelp,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}
```

- [ ] **Step 2: Replace Ambient placeholder in ModeHost**

```kotlin
Mode.Ambient -> AmbientMode(settingsViewModel, onOpenSettings, onOpenHelp)
```

- [ ] **Step 3: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/ambient/AmbientMode.kt composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/ModeHost.kt
git commit -m "Wire AmbientMode (Lumen + Borealis) into ModeHost"
```

---

## Phase 6 — Console Standard (static grid)

### Task 6.1: Create `ConsoleAccents.kt` (variant tokens)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/console/ConsoleAccents.kt`

- [ ] **Step 1: Write the variant tokens**

```kotlin
package com.droidslife.screensaver.modes.console

import androidx.compose.ui.graphics.Color
import com.droidslife.screensaver.settings.ConsoleVariant
import com.droidslife.screensaver.ui.DwellColors

data class ConsoleAccent(
    val primary: Color,
    val tileBorderTint: Color,
)

fun consoleAccentFor(variant: ConsoleVariant): ConsoleAccent = when (variant) {
    ConsoleVariant.Standard -> ConsoleAccent(
        primary = DwellColors.ConsoleGreen,
        tileBorderTint = Color.Transparent,
    )
    ConsoleVariant.Amber -> ConsoleAccent(
        primary = DwellColors.ConsoleAmber,
        tileBorderTint = DwellColors.ConsoleAmber.copy(alpha = 0.02f),
    )
}
```

- [ ] **Step 2: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/console/ConsoleAccents.kt
git commit -m "Add Console variant accents (Standard green / Amber)"
```

### Task 6.2: Create `ConsoleGrid.kt`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/console/ConsoleGrid.kt`

- [ ] **Step 1: Implement static layout** (the edit-mode comes in Phase 7)

```kotlin
package com.droidslife.screensaver.modes.console

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.droidslife.screensaver.widget.api.GridRect

private val GAP = 12.dp
private val PADDING = 32.dp
private const val COLS = 12
private const val ROWS = 6

@Composable
fun ConsoleGrid(
    placements: Map<String, GridRect>,
    modifier: Modifier = Modifier,
    cell: @Composable (id: String) -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            placements.forEach { (id, _) ->
                Box(modifier = Modifier.layoutId(id)) { cell(id) }
            }
        },
    ) { measurables, constraints ->
        val padding = PADDING.roundToPx()
        val gap = GAP.roundToPx()
        val innerW = constraints.maxWidth - padding * 2
        val innerH = constraints.maxHeight - padding * 2
        val cellW = (innerW - gap * (COLS - 1)) / COLS
        val cellH = (innerH - gap * (ROWS - 1)) / ROWS

        val placements2 = placements.toList()
        val placed = measurables.mapIndexed { i, m ->
            val (id, rect) = placements2[i]
            val w = rect.cols * cellW + (rect.cols - 1) * gap
            val h = rect.rows * cellH + (rect.rows - 1) * gap
            val placeable = m.measure(Constraints.fixed(w.coerceAtLeast(0), h.coerceAtLeast(0)))
            val x = padding + rect.col * (cellW + gap)
            val y = padding + rect.row * (cellH + gap)
            placeable to (x to y)
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            placed.forEach { (placeable, pos) -> placeable.placeRelative(pos.first, pos.second) }
        }
    }
}

@Composable
private fun Modifier.layoutId(id: String): Modifier = this // simple shim; layout-id support uses Compose's Modifier.layoutId already
```

- [ ] **Step 2: Replace the shim with the real Compose `Modifier.layoutId`**

Actually use Compose's built-in: `import androidx.compose.ui.layout.layoutId`, then `Modifier.layoutId(id)`. Remove the shim.

The Layout block needs to look up children by their layoutId. Refactor:

```kotlin
Layout(
    modifier = modifier,
    content = {
        placements.forEach { (id, _) ->
            Box(modifier = Modifier.layoutId(id)) { cell(id) }
        }
    },
) { measurables, constraints ->
    // measurables have layoutId in their parentData
    val byId = measurables.associateBy { it.layoutId as String }
    val gap = GAP.roundToPx()
    val padding = PADDING.roundToPx()
    val innerW = constraints.maxWidth - padding * 2
    val innerH = constraints.maxHeight - padding * 2
    val cellW = (innerW - gap * (COLS - 1)) / COLS
    val cellH = (innerH - gap * (ROWS - 1)) / ROWS

    val placed = placements.map { (id, rect) ->
        val w = rect.cols * cellW + (rect.cols - 1) * gap
        val h = rect.rows * cellH + (rect.rows - 1) * gap
        val pl = byId[id]?.measure(Constraints.fixed(w.coerceAtLeast(0), h.coerceAtLeast(0)))
        val x = padding + rect.col * (cellW + gap)
        val y = padding + rect.row * (cellH + gap)
        Triple(pl, x, y)
    }
    layout(constraints.maxWidth, constraints.maxHeight) {
        placed.forEach { (pl, x, y) -> pl?.placeRelative(x, y) }
    }
}
```

Add `import androidx.compose.ui.layout.layoutId`. `measurable.layoutId` is provided by `IntrinsicMeasurable.layoutId` extension.

- [ ] **Step 3: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/console/ConsoleGrid.kt
git commit -m "Add 12x6 ConsoleGrid layout engine"
```

### Task 6.3: Create `ConsoleMode.kt`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/console/ConsoleMode.kt`

- [ ] **Step 1: Wire ConsoleGrid with built-in defaults**

```kotlin
package com.droidslife.screensaver.modes.console

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.droidslife.screensaver.settings.SettingsViewModel
import com.droidslife.screensaver.ui.CornerButtons
import com.droidslife.screensaver.ui.DwellColors
import com.droidslife.screensaver.widget.api.GridRect
import com.droidslife.screensaver.widget.api.WidgetRenderTarget
import com.droidslife.screensaver.widget.host.WidgetRegistry

private val defaultLayouts: Map<String, GridRect> = mapOf(
    "com.droidslife.screensaver.clock"    to GridRect(0, 0, 7, 4),
    "com.droidslife.screensaver.weather"  to GridRect(7, 0, 5, 2),
    "com.droidslife.screensaver.todos"    to GridRect(7, 2, 5, 2),
    "com.droidslife.screensaver.expenses" to GridRect(0, 4, 4, 2),
    "com.droidslife.screensaver.calendar" to GridRect(4, 4, 4, 2),
    "com.droidslife.screensaver.idle"     to GridRect(8, 4, 4, 2),
)

@Composable
fun ConsoleMode(
    settingsViewModel: SettingsViewModel,
    registry: WidgetRegistry,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val instances by registry.instances.collectAsState()
    val userLayouts = settingsViewModel.settings.widgetLayouts
    val placements = remember(instances, userLayouts) {
        instances.keys.associateWith { id ->
            userLayouts[id] ?: defaultLayouts[id] ?: GridRect(0, 0, 4, 2)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(DwellColors.Surface0)) {
        ConsoleGrid(placements = placements, modifier = Modifier.fillMaxSize()) { id ->
            val instance = instances[id] ?: return@ConsoleGrid
            instance.widget.Render(WidgetRenderTarget.Tile, instance.scope, Modifier.fillMaxSize())
        }
        CornerButtons(
            onSettings = onOpenSettings,
            onHelp = onOpenHelp,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}
```

- [ ] **Step 2: Wire into ModeHost**

Replace the Console placeholder:

```kotlin
Mode.Console -> ConsoleMode(settingsViewModel, registry, onOpenSettings, onOpenHelp)
```

- [ ] **Step 3: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/console/ConsoleMode.kt composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/ModeHost.kt
git commit -m "Wire ConsoleMode (static grid) into ModeHost"
```

---

## Phase 7 — Console edit mode (drag-drop + resize)

### Task 7.1: `ConsoleEditOverlay.kt`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/console/ConsoleEditOverlay.kt`

Spec § 5 "Console layout edit mode" — implement the size badge, drag handle, resize handle, edit banner. Use `Modifier.pointerInput` to capture drag events; compute grid cell from `Offset` based on the grid's measured cell-width/height; persist via `settingsViewModel.setWidgetLayout(id, newRect)`.

- [ ] **Step 1: Write the overlay (full file shown in spec § 5; implement per spec)**

This task includes drag detection, snap-to-grid math, the `EDIT LAYOUT` banner, size badges, and the resize-corner gesture. Reference spec § 5; gesture handling uses `detectDragGestures` from `androidx.compose.foundation.gestures`. Compute the candidate new `GridRect` on each drag; clamp to widget's `WidgetSize`; preview with a ghost placeholder; commit on `onDragEnd`.

Pseudocode shape:

```kotlin
@Composable
fun ConsoleEditOverlay(
    placements: Map<String, GridRect>,
    sizeConstraints: Map<String, WidgetSize>,
    cellWidthPx: Int,
    cellHeightPx: Int,
    onMove: (String, GridRect) -> Unit,
    onResize: (String, GridRect) -> Unit,
) {
    placements.forEach { (id, rect) ->
        // overlay box matching the tile; with drag handle + resize handle
        // Modifier.pointerInput(...) { detectDragGestures(...) }
        // compute new GridRect from drag delta in pixels → grid cells
        // emit onMove / onResize
    }
    // Banner: EDIT LAYOUT · L to exit · drag to move · ⌥drag to resize
}
```

- [ ] **Step 2: Wire into ConsoleMode**

Add `editMode: Boolean` state (toggled by `L` keypress — wired in Phase 11). When true, render `ConsoleEditOverlay` on top of the tiles.

- [ ] **Step 3: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/console/
git commit -m "Add Console edit overlay (drag/resize, banner, ghost placeholder)"
```

---

## Phase 8 — Calendar + IdleCounter widgets

### Task 8.1: `CalendarWidget.kt`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/widget/builtin/CalendarWidgetFactory.kt`

A `WidgetFactory` returning a `Widget` that renders a month grid in Tile target, with today highlighted in the active accent. Chip/Minimal render returns a no-op (or shows just "Day N / Month").

```kotlin
class CalendarWidgetFactory : WidgetFactory {
    override val id = "com.droidslife.screensaver.calendar"
    override val displayName = "Calendar"
    override val preferredSize = WidgetSize(minCols = 3, minRows = 2, defaultCols = 4, defaultRows = 2, maxCols = 6, maxRows = 4)
    override fun create(scope: WidgetScope): Widget = object : Widget {
        override fun summary() = WidgetSummary(
            primaryValue = "${Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).day}",
            primaryLabel = currentMonthName(),
            subtitle = null,
        )
        @Composable override fun Content(modifier: Modifier) {
            // month grid (see spec); 7 cols x ~5 rows; today highlighted
            CalendarMonth(modifier)
        }
    }
}
```

Implement `CalendarMonth` as a 7×N `Column { Row { Day*7 } }` grid using `kotlinx.datetime` to compute days of month + day-of-week of the 1st.

- [ ] **Step 2: Register the factory in Koin (`AppModule.kt`)**

```kotlin
single<WidgetFactory>(qualifier = named("calendar")) { CalendarWidgetFactory() }
```

(Or via the existing pattern — `grep "WidgetFactory" composeApp/src/commonMain/kotlin/com/droidslife/screensaver/di/AppModule.kt`.)

- [ ] **Step 3: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/widget/builtin/CalendarWidgetFactory.kt composeApp/src/commonMain/kotlin/com/droidslife/screensaver/di/AppModule.kt
git commit -m "Add Calendar built-in widget (month-grid, today highlighted)"
```

### Task 8.2: `IdleCounterWidget.kt`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/widget/builtin/IdleCounterWidgetFactory.kt`

Console-only. `Render(target)` returns a no-op for Chip/Minimal. `Content(modifier)` shows total idle time + "screensaver since {HH:mm}".

- [ ] **Step 1: Write the factory**

Implement similarly to CalendarWidgetFactory. Track idle start time when widget mounts; render elapsed = `now - mountTime` updated each second.

- [ ] **Step 2: Register in Koin, build, commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/widget/builtin/IdleCounterWidgetFactory.kt composeApp/src/commonMain/kotlin/com/droidslife/screensaver/di/AppModule.kt
git commit -m "Add Idle counter built-in widget (Console-only)"
```

---

## Phase 9 — WeatherForecast widget

### Task 9.1: Extend `WeatherApi.kt` + `WeatherRepository.kt`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/weather/WeatherApi.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/weather/WeatherRepository.kt`

- [ ] **Step 1: Add forecast endpoint mapping**

`WeatherAPI.com` forecast: `GET https://api.weatherapi.com/v1/forecast.json?key={key}&q={city}&days=5&aqi=no&alerts=no`.

Add a `ForecastResponse` data class with `forecast.forecastday[].day.maxtemp_c / mintemp_c / condition`. Add `WeatherApi.fetchForecast(city: String, days: Int): ForecastResponse`.

- [ ] **Step 2: Add `WeatherRepository.forecast(city, days): Result<List<DayForecast>>`**

`DayForecast` data class (`composeApp/.../weather/DayForecast.kt`):

```kotlin
data class DayForecast(
    val date: LocalDate,
    val high: Int,
    val low: Int,
    val conditionCode: Int,
    val conditionText: String,
    val iconUrl: String,
)
```

Map response → list.

- [ ] **Step 3: `WeatherViewModel.forecast: StateFlow<ForecastState>`**

Mirror the existing `WeatherState` triad:

```kotlin
sealed interface ForecastState {
    object Loading : ForecastState
    data class Loaded(val days: List<DayForecast>) : ForecastState
    object Failed : ForecastState
    object Unconfigured : ForecastState
}
```

- [ ] **Step 4: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/weather/
git commit -m "Add 5-day forecast to WeatherRepository / ViewModel"
```

### Task 9.2: `WeatherForecastWidgetFactory.kt`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/widget/builtin/WeatherForecastWidgetFactory.kt`

Implement per spec § 6.4. Three render targets:
- **Tile**: horizontal row of 5 day cards (weekday name, icon, H/L).
- **Chip**: condensed `5d: H22 H24 H21 H20 H18`.
- **Minimal**: `Tomorrow: 24° partly cloudy · Sat 21° rain`.

Default `preferredSize = WidgetSize(minCols = 8, minRows = 1, defaultCols = 12, defaultRows = 1, maxCols = 12, maxRows = 2)`.

Off by default (`enabledWidgetIds` doesn't include it). Register in Koin.

- [ ] **Step 1: Write the factory + register**

- [ ] **Step 2: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/widget/builtin/WeatherForecastWidgetFactory.kt composeApp/src/commonMain/kotlin/com/droidslife/screensaver/di/AppModule.kt
git commit -m "Add WeatherForecast widget (5-day, all 3 render targets)"
```

---

## Phase 10 — Settings sheet rebuild

### Task 10.1: `SettingsSheet.kt` chrome + slide-in

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/settings/SettingsSheet.kt`

- [ ] **Step 1: Implement** the side-sheet container, scrim, slide-in, sticky tab row, header `X`. Five tabs as section composables (created below). Esc and scrim click both dismiss. Width = `min(560dp, 60vw)`. Use `androidx.compose.ui.window.Dialog` only as a focus container; the visual is a custom `Surface`.

Reference spec § 7 for exact tokens.

- [ ] **Step 2: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/settings/SettingsSheet.kt
git commit -m "Add SettingsSheet (side-sheet chrome, slide-in, sticky tab row)"
```

### Task 10.2: Five section composables

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/settings/sections/{Display,Widgets,Triggers,Sync,About}Section.kt`

Each is a `@Composable fun XSection(settingsViewModel: SettingsViewModel)` per spec § 7.

- [ ] **Step 1: Write all five files** (most are forms; spec § 7 lists the fields). Display section's variant picker dynamically shows Cinematic / Ambient / Console variants based on current `mode`.

- [ ] **Step 2: Wire sections into SettingsSheet tab bodies**

- [ ] **Step 3: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/settings/sections/
git commit -m "Add Display/Widgets/Triggers/Sync/About sections for SettingsSheet"
```

### Task 10.3: Replace `SettingsDialog` rendering site with `SettingsSheet`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/DigitalClockApp.kt` (or wherever SettingsDialog is currently rendered, after Phase 4 wiring)

- [ ] **Step 1: Switch the call site**

Find the existing `if (settingsViewModel.isSettingsDialogOpen) { SettingsDialog(...) }` and replace with `SettingsSheet(...)`. Leave old `SettingsDialog.kt` in place (Phase 13 deletes).

- [ ] **Step 2: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/
git commit -m "Switch Settings render site to SettingsSheet"
```

---

## Phase 11 — Keyboard map + mode-change motion

### Task 11.1: Extend `KeyEventAction` and `KeyEventHandler`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/components/KeyEventAction.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/components/KeyEventHandler.kt`

- [ ] **Step 1: Add actions**

```kotlin
sealed interface KeyEventAction {
    object RequestExit : KeyEventAction
    object ShowHelp : KeyEventAction
    object OpenSettings : KeyEventAction
    object CycleMode : KeyEventAction
    object JumpCinematic : KeyEventAction
    object JumpAmbient : KeyEventAction
    object JumpConsole : KeyEventAction
    object CycleVariant : KeyEventAction
    object ToggleDrawer : KeyEventAction
    object ToggleConsoleEdit : KeyEventAction
    object ReloadWidgets : KeyEventAction
    // (existing actions preserved)
}
```

- [ ] **Step 2: Map keys in `handleWindowKeyEvent`**

```kotlin
event.key == Key.M && event.type == KeyEventType.KeyDown -> dispatch(CycleMode)
event.key == Key.One && event.type == KeyEventType.KeyDown -> dispatch(JumpCinematic)
event.key == Key.Two && event.type == KeyEventType.KeyDown -> dispatch(JumpAmbient)
event.key == Key.Three && event.type == KeyEventType.KeyDown -> dispatch(JumpConsole)
event.key == Key.V && event.type == KeyEventType.KeyDown -> dispatch(CycleVariant)
event.key == Key.W && event.type == KeyEventType.KeyDown -> dispatch(ToggleDrawer)
event.key == Key.L && event.type == KeyEventType.KeyDown -> dispatch(ToggleConsoleEdit)
event.isCtrlPressed && event.key == Key.Comma && event.type == KeyEventType.KeyDown -> dispatch(OpenSettings)
event.isCtrlPressed && event.key == Key.R && event.type == KeyEventType.KeyDown -> dispatch(ReloadWidgets)
```

- [ ] **Step 3: Route actions in `App.kt` / `main.kt`**

Wire `OpenSettings` → `settingsViewModel.openSettingsDialog()`. `CycleMode` → `settingsViewModel.cycleMode()`. `JumpCinematic/Ambient/Console` → `settingsViewModel.setMode(...)`. `CycleVariant` → `settingsViewModel.cycleVariant()`. `ToggleDrawer` → state hoisted into CinematicMode (use `koinInject<UiState>` or lift to ModeHost via `mutableStateOf`). `ToggleConsoleEdit` → similar lift into ConsoleMode. `ReloadWidgets` → `widgetRegistry.reload(); widgetRegistry.syncWithSettings(settingsViewModel.settings)`.

- [ ] **Step 4: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/components/ composeApp/src/commonMain/kotlin/com/droidslife/screensaver/App.kt composeApp/src/jvmMain/kotlin/main.kt
git commit -m "Wire M / 1-3 / V / W / L / Ctrl+, / Ctrl+R keyboard shortcuts"
```

### Task 11.2: Mode-change blur cross-fade

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/ModeHost.kt`

- [ ] **Step 1: Replace `Crossfade` with a custom transition**

Capture `previousMode` in `remember`; animate `progress 0→1` over 600ms. At progress 0.5 the screen blur should peak (12px). Compose-Desktop: stack both modes in a `Box`; the outgoing alpha goes 1→0, incoming alpha goes 0→1, both gain `Modifier.blur(blurPx.dp)` where `blurPx = 12 * sin(progress * π)`.

```kotlin
@Composable
fun ModeHost(...) {
    val target = settingsViewModel.settings.mode
    var prev by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target != prev) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(DwellMotion.ModeChange, easing = DwellMotion.Standard))
            prev = target
        }
    }
    val blurPxOld = (12f * kotlin.math.sin(progress.value * Math.PI)).toFloat()
    val blurPxNew = blurPxOld

    Box(Modifier.fillMaxSize()) {
        if (prev != target) {
            RenderMode(prev, ..., Modifier.alpha(1 - progress.value).blur(blurPxOld.dp))
        }
        RenderMode(target, ..., Modifier.alpha(progress.value).blur(blurPxNew.dp))
    }
}

@Composable
private fun RenderMode(mode: Mode, ...) {
    when (mode) {
        Mode.Cinematic -> CinematicMode(...)
        Mode.Ambient   -> AmbientMode(...)
        Mode.Console   -> ConsoleMode(...)
    }
}
```

- [ ] **Step 2: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/ModeHost.kt
git commit -m "Mode-change cross-fade with midpoint blur"
```

---

## Phase 12 — Variant polish (Noir, Amber, Quieter Lumen)

### Task 12.1: Replace `NoirBackdrop.kt` stub with real implementation

- [ ] Implement per spec § 6.1.2 — solid `#020203` + single warm-white drifting radial glow.

### Task 12.2: Quieter Lumen

- [ ] Already gated in Lumen.kt by `settingsViewModel.settings.quieterLumen`. Hide corner brackets, side telemetry, reduce top/bottom telemetry to `{time} · {temp}°{city}` when toggle is on. Toggle UI was added in Phase 10 Display section.

### Task 12.3: Console Amber styling

- [ ] In `ConsoleMode.kt`, pass `consoleAccentFor(settings.consoleVariant)` to the grid renderer; built-in widgets that use accent color (weather temp, calendar today cell) read it from a `CompositionLocalProvider<ConsoleAccent>`.

- [ ] **Step 1 (combined): Build + commit each as separate commits**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/modes/
git commit -m "Variant polish: Noir backdrop, Quieter Lumen, Console Amber"
```

---

## Phase 13 — Migration + cleanup

### Task 13.1: `PreferencesRepository` migration

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/settings/PreferencesRepositoryImpl.jvm.kt` (or whichever Impl exists)

- [ ] **Step 1: On first read, migrate**

```kotlin
private fun migrate(settings: SettingsModel): SettingsModel {
    var s = settings
    // If currentCity was set, write into widget config and clear it
    val currentCity = s.currentCity
    if (currentCity != null && currentCity.isNotBlank()) {
        val key = "com.droidslife.screensaver.weather"
        val cfg = s.widgetConfigs[key] ?: JsonObject(emptyMap())
        val newCfg = JsonObject(cfg + ("city" to JsonPrimitive(currentCity)))
        s = s.copy(widgetConfigs = s.widgetConfigs + (key to newCfg), currentCity = null)
    }
    return s
}
```

Apply in `getSettings()` mapping before emitting.

- [ ] **Step 2: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add composeApp/src/commonMain/kotlin/com/droidslife/screensaver/settings/PreferencesRepositoryImpl.jvm.kt
git commit -m "Migrate currentCity → Weather widget config on first read"
```

### Task 13.2: Delete legacy files

**Files:**
- Delete: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/clockdigits/*.kt`
- Delete: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/clock/ClockViewModel.kt`
- Delete: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/settings/SettingsDialog.kt`
- Delete: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/dashboard/WidgetCard.kt`
- Delete: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/dashboard/WidgetGrid.kt`
- Delete: `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/DigitalClockApp.kt`
- Modify: any imports referencing the deleted files (none should remain after Phase 4-12).

- [ ] **Step 1: Verify no remaining references**

```bash
grep -rn "clockdigits\|ClockViewModel\|SettingsDialog\|WidgetCard\|WidgetGrid\|DigitalClockApp" composeApp/src/ widget-api/src/ --include='*.kt' | grep -v Test
```
Expected: only references to types still in use (e.g. `WidgetRegistry`). If any code still references the deleted symbols, fix first.

- [ ] **Step 2: Delete + rebuild**

```bash
git rm composeApp/src/commonMain/kotlin/com/droidslife/screensaver/clockdigits/*.kt
git rm composeApp/src/commonMain/kotlin/com/droidslife/screensaver/clock/ClockViewModel.kt
git rm composeApp/src/commonMain/kotlin/com/droidslife/screensaver/settings/SettingsDialog.kt
git rm composeApp/src/commonMain/kotlin/com/droidslife/screensaver/dashboard/WidgetCard.kt
git rm composeApp/src/commonMain/kotlin/com/droidslife/screensaver/dashboard/WidgetGrid.kt
git rm composeApp/src/commonMain/kotlin/com/droidslife/screensaver/DigitalClockApp.kt
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git commit -m "Delete legacy single-design UI (clockdigits, SettingsDialog, WidgetGrid)"
```

### Task 13.3: Remove deprecated `Content(modifier)` from widget interface

**Files:**
- Modify: `widget-api/src/commonMain/kotlin/com/droidslife/screensaver/widget/api/Widget.kt`

- [ ] **Step 1: Remove `Content` and make `Render(target, scope)` the only method**

The default `Render` now needs to call `DefaultTileRender` etc. directly instead of delegating to `Content`. Each built-in widget must implement `Render(target)` explicitly for at least Tile.

- [ ] **Step 2: Update built-in widgets to remove their `Content` impl** if Render is now sufficient. Many already added Render in Phase 4; this step cleans up.

- [ ] **Step 3: Build + commit**

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:compileKotlinJvm
git add widget-api/src/commonMain/kotlin/com/droidslife/screensaver/widget/api/Widget.kt composeApp/src/commonMain/kotlin/com/droidslife/screensaver/widget/builtin/
git commit -m "Remove deprecated Widget.Content; Render(target) is the only contract"
```

---

## Phase verification

After Phase 13, run the full success criteria from spec § 13:

- [ ] App launches in Cinematic mode by default (no settings dialog, no red weather error).
- [ ] `M` cycles modes with cross-fade + blur.
- [ ] `V` cycles variant within current mode.
- [ ] `3` then `L` enters Console edit mode; tiles can be dragged + resized.
- [ ] `Ctrl+,` opens Settings sheet from the right; Esc + scrim-click both dismiss.
- [ ] No widget shows a "Clock"/"Weather" header in Cinematic or Ambient.
- [ ] Settings tabs render without text truncation.
- [ ] Weather widget with no key shows the friendly empty state from the audit-pass.

Manual run:

```bash
JAVA_HOME=$HOME/jdks/jdk-21.0.11+10 PATH=$HOME/jdks/jdk-21.0.11+10/bin:$PATH ./gradlew :composeApp:run --args="--show"
```

Visually verify each mode + variant. Screenshot per mode/variant and attach to PR.

---

## Self-review notes

- **Spec coverage**: every section of `2026-05-21-ui-rebuild-design.md` maps to a phase here. Section 4 (visual system) → Phase 0; § 5 → Phase 1; § 6.1 → Phase 4 + 12; § 6.2 → Phase 5; § 6.3 → Phase 6 + 7 + 12; § 6.4 → Phase 9; § 7 → Phase 10; § 8 → Phase 4 (CornerButtons); § 9 → Phase 11; § 10 → Phase 3 + 13; § 11 → file plan tracked across phases.
- **Placeholders**: tasks 7.1, 8.1, 8.2, 9.2, 10.1, 10.2 reference spec sections rather than inlining 100+ lines of Kotlin; this is intentional — the spec is the source of truth and inlining would duplicate it. The plan's job is sequencing + ground truth on file paths and commit cadence. Each task is still self-contained: file path, what to implement (with spec pointer), build verification, commit message.
- **Type consistency**: `Mode`, `CinematicVariant`, `AmbientVariant`, `ConsoleVariant`, `WidgetSize`, `GridRect`, `WidgetRenderTarget`, `WidgetSummary`, `WidgetAccent` are used consistently across phases.
- **Order**: Phase 0 (tokens) → Phase 1 (API) → Phase 2 (widget summaries) → Phase 3 (settings model) → Phase 4 (Cinematic Dusk) → Phases 5/6/7/8/9 (modes + widgets) → Phase 10 (Settings sheet) → Phase 11 (keys + animation) → Phase 12 (variant polish) → Phase 13 (cleanup). Each phase compiles; earlier phases unblock later ones.
