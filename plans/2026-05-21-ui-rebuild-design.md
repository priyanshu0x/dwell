# Dwell UI Rebuild — Design Spec

**Status:** Approved for implementation planning · 2026-05-21
**Brainstorm artifacts:** `.superpowers/brainstorm/623449-1779383790/` (mockups, not committed)
**Related:** `plan/user-perspective-issues.md` (the 40-issue audit that motivated this)

## 1. Goal

Replace the current dashboard, settings, and chrome with a coherent three-mode screensaver. The current UI has been described as "mess and garbage"; the rebuild swaps it for one shared visual system, three switchable modes (Cinematic / Ambient / Console), a rebuilt Settings sheet, and a richer widget rendering contract that supports per-mode views, author-declared sizes, and Console drag-drop layouts.

## 2. Non-goals

- Not redesigning the tray menu (already says "Dwell"; the menu items stay).
- Not implementing Windows-specific `.scr` rendering changes; the new modes use the same fullscreen `Window` as today.
- Not adding a global Light theme. Light/dark choice happens per-design later: future variants will ship light versions of the same mode (e.g. "Dusk Light", "Borealis Day"). v1 ships dark variants only.
- Not adding telemetry, account auth, or backend changes.
- Not touching the idle detection (`IdleMonitor`, `LinuxIdleMonitor`, `WindowsIdleMonitor`), tray daemon (`TrayDaemon`), or launch-mode plumbing (`Args.kt`, JVM-side `main.kt` argument parsing). The rebuild lives *inside* the existing fullscreen `Window`; how that window opens is unchanged.
- Not implementing legacy compatibility for third-party JAR widgets that pre-date the new render contract — there are zero such widgets in the wild, so the new API is the only API.

## 3. Concept

Dwell becomes a three-mode dashboard:

- **Cinematic** *(default on first run)* — large typographic clock on an animated mesh-gradient backdrop. Widgets live in a peek-on-hover bottom drawer.
- **Ambient** — calm presence. Two variants in v1:
  - **Lumen** *(default variant)* — sci-fi HUD: cyan corner brackets, perspective grid floor, tracked-monospace telemetry along three edges, orbital dial with a pulsing current-minute tick, thin glowing clock at center.
  - **Borealis** — soft-aurora: deep night sky with star field, 2–3 slowly drifting aurora ribbons (green / teal / magenta) painted with screen-blend, soft floating clock.
- **Console** — modular tile grid; every enabled widget visible at once; tiles are author-sized and user-resizable/reorderable.

All three modes share:
- The same neutral palette and type system (Section 4).
- Edge-anchored corner buttons (Settings ⚙ + Help ?) at bottom-right, 32% opacity, fade to 95% on hover.
- The same widget API (Section 5).
- The same Settings sheet (Section 7) and keyboard map (Section 8).

City selection is **not** global chrome — it's a control inside the Weather widget.

## 4. Visual system

### Palette (shared)

| Token | Value | Use |
|---|---|---|
| `bg.void` | `#050507` | Page background |
| `surface.0` | `#0b0b0c` | Cards, tiles, Settings sheet body |
| `surface.1` | `#131316` | Inner panels, tab pills |
| `stroke` | `#1f1f22` | Hairline borders |
| `text.high` | `#eaeaea` | Body text |
| `text.mid` | `#8d92a2` | Secondary text |
| `text.low` | `#6f7686` | Labels, hints |
| `text.faint` | `#5c6173` | Disabled, decorative |
| `status.accent` | `#f3a280` | CTAs, focus |
| `status.error` | `#c46c6c` | Errors, retry |
| `status.ok` | `#6cbb8a` | Saved, success |

### Mode accents

| Mode | Variant | Accent |
|---|---|---|
| Cinematic | (single) | Peach `#f3a280` → Violet `#b46cc4` mesh gradient, drifting on a 60s loop |
| Ambient | Lumen | Cyan `#7adcff` on midnight (`#0a1228 → #2a3a5c`) |
| Ambient | Borealis | Aurora colors: green `#7befb1`, magenta `#d779e5`, teal `#52c8dc` on near-black (`#02030a → #010108`) |
| Console | (single) | No accent; muted terminal green `#9ecda0` reserved for headline numerics |

### Typography

Bundled in `composeApp/src/commonMain/composeResources/font/`:

- **Inter Tight** (200, 250, 400, 600, 700) — clock display in Cinematic, Ambient Borealis, Console clock tile.
- **JetBrains Mono** (200, 400, 500) — Ambient Lumen clock + telemetry, Console numeric tiles.
- **Inter** (400, 500, 600) — all body, UI labels, Settings.

Section labels are 11px Inter 600 uppercase, 0.2em tracking. Section labels in Lumen telemetry use JetBrains Mono at 10px with 0.4em tracking.

### Motion

| Action | Duration | Curve |
|---|---|---|
| Dashboard mount fade-in | 800ms | M3 `standard` |
| Dashboard unmount fade-out | 400ms | M3 `standard` |
| Mode change (cross-fade with 12px midpoint blur) | 600ms | M3 `standard` |
| Cinematic mesh gradient drift | ~60s linear loop | linear |
| Borealis aurora ribbon drift | ~90s linear loop | linear |
| Lumen minute-tick pulse | 2.4s ease in-out infinite | ease-in-out |
| Settings sheet slide from right | 350ms | M3 `emphasized` |
| Corner buttons hover reveal | 180ms | M3 `standard` |
| Toast / shortcut hint | 200ms in · 1200ms hold · 200ms out | M3 `standard` |
| Console tile reflow when reordered | 200ms | M3 `standard` |

No bouncy springs anywhere.

### Spacing & geometry

- 8pt grid (4 / 8 / 16 / 24 / 32 / 48 / 64 / 96 dp).
- 5% safe-area margin from each screen edge in Cinematic and Ambient.
- 32dp screen padding in Console; 12dp tile gap.
- Corner radii: 8dp (small chips, pills), 12dp (cards, tiles), 16dp (Borealis backplate), 24dp (Settings sheet).
- Settings sheet width: `min(560dp, 60vw)`.

## 5. Widget rendering contract

### Three render targets

```kotlin
// widget-api/src/commonMain/kotlin/com/droidslife/screensaver/widget/api/WidgetRender.kt
enum class WidgetRenderTarget {
    Tile,    // Console mode — full card; author-sized; resizable in edit mode
    Chip,    // Cinematic mode drawer — ~60dp tall row; one label + one value
    Minimal, // Ambient mode — one line of dim text, no chrome
}

enum class WidgetAccent { Default, Positive, Negative, Neutral }

data class WidgetSummary(
    val primaryValue: String,         // e.g. "$412.30"
    val primaryLabel: String? = null, // e.g. "May spend"
    val subtitle: String? = null,     // e.g. "food $180 · transport $98 · bills $134"
    val accent: WidgetAccent = WidgetAccent.Default,
)
```

The `DwellWidget` interface becomes:

```kotlin
interface DwellWidget {
    /** Default = renders into target using host-provided defaults built from summary(). */
    @Composable fun Render(target: WidgetRenderTarget, scope: WidgetScope) {
        val s = summary()
        when (target) {
            WidgetRenderTarget.Tile    -> DefaultTileRender(s, scope)
            WidgetRenderTarget.Chip    -> DefaultChipRender(s, scope)
            WidgetRenderTarget.Minimal -> DefaultMinimalRender(s, scope)
        }
    }

    fun summary(): WidgetSummary
}
```

Built-in widgets (Clock, Weather, WeatherForecast, Todos, Expenses, Calendar, IdleCounter) will implement custom `Render(target, scope)` for the targets that matter to them. Widgets that want host-default rendering for a given target simply don't override that branch — the `when` falls through to the host-provided default.

There is no legacy `Render(scope)` fallback. The repo currently has zero third-party JAR widgets in the wild, so the new contract is the only contract.

### Author-declared sizes

```kotlin
// widget-api ... WidgetDescriptor.kt — new field
data class WidgetSize(
    val minCols: Int = 2,  val minRows: Int = 1,
    val defaultCols: Int = 4, val defaultRows: Int = 2,
    val maxCols: Int = 12,  val maxRows: Int = 6,
)

data class WidgetDescriptor(
    val id: String,
    val displayName: String,
    val preferredSize: WidgetSize = WidgetSize(), // new, default = 4×2
    // ... existing fields ...
)
```

Built-in defaults:
- Clock: `WidgetSize(min=4×3, default=7×4, max=12×6)`
- Weather: `WidgetSize(min=3×2, default=5×2, max=8×3)`
- Todos: `WidgetSize(min=3×2, default=5×2, max=8×4)`
- Expenses: `WidgetSize(min=3×2, default=4×2, max=8×3)`

### Console layout edit mode

Layout state lives in `SettingsModel.widgetLayouts: Map<WidgetId, GridRect>`, where `GridRect(col, row, cols, rows)`. Empty map = author defaults.

Edit mode (toggle: `L` while in Console mode, or Settings → Widgets → "Edit Console layout"):
- Each tile shows a size badge (top-left), drag handle (top-right `⋮⋮`), and a corner resize handle (cyan triangle, bottom-right).
- Drag header → tile moves; other tiles reflow over 200ms.
- Drag corner → tile resizes, clamped to `WidgetSize` bounds; snaps to 12×6 grid.
- Banner at top: `EDIT LAYOUT · L to exit · drag to move · ⌥drag to resize`.
- "Reset layout" button in Settings restores all to author defaults.

When edit mode is off, tiles ignore drag events.

## 6. The three modes (rendering specs)

### 6.1 Cinematic (default mode)

Two variants in v1: **Dusk** (default) and **Noir**.

#### 6.1.1 Dusk (default variant)

**Layout** *(referenced from origin at top-left of safe-area)*:
- Backdrop: full-screen mesh gradient.
- Clock: anchored at (8% screen-width, 26% screen-height), Inter Tight 700, font-size `min(280pt, 18vw)`, tabular nums. Updates per minute, not per second.
- Meta line: 16px Inter, anchored at (8.5%, just-below-clock + 14dp). Format: `{weekday}, {day} {month} · {temp}° {condition} · {city}`. Weather and city sections only render when the Weather widget is enabled and configured.
- Drawer hint: `↓ widgets` in 9px tracked uppercase at bottom-center, 32% alpha. Suppressed when no widgets are enabled.
- Corner buttons: bottom-right.

**Backdrop algorithm:**
- Three radial-gradient blobs with peach `rgba(243,162,128,0.55)`, violet `rgba(180,108,196,0.50)`, midnight `rgba(60,80,160,0.35)`.
- Centers drift along sine curves with periods 47s, 53s, 61s (relatively prime so motion never repeats visibly).
- Composited into a single Compose `Modifier.drawWithCache` over `#050307`. Each frame computes blob centers from a `time % 60_000` long.
- Repaint rate: 30Hz max (every other frame).

**Drawer:**
- Hidden by default.
- Triggers: mouse enters bottom 10% of screen, OR keypress `W`.
- 60dp tall, 6% horizontal inset from screen edges, glass-blur (Compose: `Modifier.blur(20.dp)` over an 85%-alpha surface).
- Contents: enabled widgets rendered as `Chip` target, laid out in `Row(spacedBy = 24.dp)`.
- Auto-hide 2s after mouse leaves the bottom region.

#### 6.1.2 Noir (alternate variant)

A theatrical, near-monochrome counterpoint to Dusk's painterly warmth.

**Layout:**
- Backdrop: solid `#020203` (near-pure black).
- A single warm-white radial glow, 600dp radius, at ~30% opacity (`rgba(255,245,225,0.30)`), centered at (38% screen-width, 42% screen-height). Drifts on a 90s linear loop along a horizontal sine path of amplitude 10% screen-width, vertical sine of 4% screen-height (very gentle).
- Clock: same anchor (8%, 26%), same Inter Tight 700, same `min(280pt, 18vw)` size, but color `#fafafa` (pure white) and **no text-shadow**. The glow behind it is what gives the clock depth.
- Meta line: same position, but in `#a0a0a0` (cool grey) — no warm tinting.
- Drawer hint, drawer contents, corner buttons: identical to Dusk.

**Motion:** the glow drift is the only animation. Even calmer than Dusk because the mesh-gradient is gone.

**Use case:** users who find Dusk's color too painterly or whose monitors have aggressive color reproduction (e.g. OLEDs that bloom warm colors).

### 6.2 Ambient

#### 6.2.1 Lumen (default variant)

**Layout**:
- Backdrop: radial gradient centered at viewport center, `#0d2238` → `#050a18` → `#03060d`.
- Perspective grid floor at bottom 40% of viewport: 60×60dp cells, `transform: perspective(800px) rotateX(70deg)`, `rgba(122,220,255,0.06)` lines, mask-fades to transparent at the top.
- Cyan corner brackets at all four corners: 28×28dp, 1dp stroke, `rgba(122,220,255,0.3)`.
- Top telemetry strip (center-aligned): `DWELL · {time} {tz} · {temp}°C {city} · {weatherStatus} · {syncStatus}` in JetBrains Mono 10px / 0.4em tracking / `rgba(122,220,255,0.6)`. Status pieces appear only when their underlying source is configured.
- Bottom-left telemetry: ISO-style timestamp `{yyyy}-{MM}-{dd}T{HH}:{mm}Z · idle {mm}'{ss}"` in JetBrains Mono 10px.
- Right-side telemetry (rotated 90°): a slow numeric drift counter, e.g. `ν 0.000017 hz · drift +0.0s`, purely decorative.
- Orbital dial: 460dp wide circle centered on the viewport. Concentric rings, 60 minute-tick marks (every 5th heavier), the current minute pulses at 2.4s ease-in-out infinite.
- Clock: Inter Tight 200, ~152pt, centered just above viewport midline, with soft cyan text-shadow.
- Clock subline: `THU · 21 MAY · 2026` in JetBrains Mono 11px / 0.4em / `rgba(122,220,255,0.55)`.
- Corner buttons: bottom-right.

**Power-user toggle:** Settings → Display → Ambient → "Quieter Lumen" hides the four corner brackets, the side telemetry, and reduces top/bottom telemetry to just `{time} · {temp}°{city}`.

#### 6.2.2 Borealis (alternate variant)

**Layout**:
- Backdrop: radial gradient centered at bottom of viewport, `#061026` → `#02030a` → `#010108`.
- Star field: ~10 sub-pixel stars at fixed positions, each randomly tinted between `rgba(255,255,255,0.25)` and `rgba(255,255,255,0.65)`. Stars are static (no twinkle in v1).
- Aurora ribbons: 3 SVG-style `Path`s drawn on Compose `Canvas` with screen-blend (Compose `BlendMode.Screen`), gaussian-blurred (`Modifier.blur(20.dp)`), saturated to 1.1x.
  - Ribbon 1 (upper, green→teal): horizontal bezier, drifts left→right on a 90s loop.
  - Ribbon 2 (mid, magenta→blue): drifts right→left on a 90s loop.
  - Ribbon 3 (lower, faint green): drifts left→right on a 90s loop with a different phase.
- Clock: Inter Tight 250, ~158pt, centered at 54% viewport-height (slightly above midline), `rgba(255,255,255,0.92)` with soft blue-white glow.
- Date: `Thu · 21 May` in Inter 300, 14px, 0.3em tracking, `rgba(255,255,255,0.55)`, anchored below clock + 100dp.
- Place (city): Inter 300, 12px, 0.2em tracking, `rgba(255,255,255,0.32)`, anchored below clock + 130dp.
- Bottom-edge vignette: linear gradient from `rgba(2,4,12,1)` → transparent over the bottom 12%.
- Corner buttons: bottom-right.

**Widgets in Ambient (both variants):**
- Disabled by default — when a user toggles a widget on in Settings → Widgets, it appears as a `Minimal` render below the clock subline (Lumen) or below the place line (Borealis).
- Minimal render is one short line of dim Inter 300, 12px, e.g. `3 todos · 1 due today`. No card, no icon.

### 6.3 Console

Two variants in v1: **Standard** (default, terminal green) and **Amber** (vintage CRT amber). Layout and grid behavior are identical; only the accent color and a couple of subtle border tints change.

#### 6.3.1 Standard (default variant)

**Layout**:
- Backdrop: solid `surface.0` (`#0b0b0c`). No gradient.
- 12-col × 6-row grid, 32dp padding, 12dp gap.
- Built-in default placements (used when `settings.widgetLayouts` is empty):

  | Widget | Grid rect (col, row, cols, rows) |
  |---|---|
  | Clock | (0, 0, 7, 4) |
  | Weather (current) | (7, 0, 5, 2) |
  | Todos | (7, 2, 5, 2) |
  | Spend (Expenses) | (0, 4, 4, 2) |
  | Calendar (built-in, simple month) | (4, 4, 4, 2) |
  | Idle counter (Console-only built-in) | (8, 4, 4, 2) |

- WeatherForecast is **off by default** in Console (and everywhere); when the user enables it in Settings → Widgets, it appears in layout-edit mode as a floating tile they can drop into the grid. Default size when first placed: `12×1` (full-width thin strip).

- Tile chrome: `surface.1` background, 1dp `stroke` border, 12dp corner radius. Internal padding 14dp / 16dp. Small uppercase label (9px Inter 600, 0.25em tracking, `text.low`) at top; large value in JetBrains Mono 500 (or Inter Tight for clock); small subtitle in `text.mid`.

**Accent color** in Standard is `#9ecda0` (terminal green), used sparingly: weather temperature value, today-cell in the calendar, layout-edit handles.

**Built-in widgets new in this spec:**
- **Idle counter** — rendered only when in Console mode. Shows total idle time + "screensaver since {HH:mm}". Its `summary()` returns `WidgetSummary(primaryValue = "—")` and `Render(WidgetRenderTarget.Chip / Minimal, …)` is a no-op so it's effectively hidden in Cinematic and Ambient.
- **Calendar** — a simple month-grid showing the current month with today highlighted in the active accent. No event integration in v1.
- **WeatherForecast** — see Section 6.4 for the full spec (it's a new built-in shared across modes, not Console-specific).

#### 6.3.2 Amber (alternate variant)

A vintage CRT homage. Same grid, same widgets, same layout — only the accent changes.

- Accent color: `#f3b95e` (warm amber) in place of `#9ecda0` (terminal green).
- Tile border gains a 2%-alpha amber tint (`rgba(243,185,94,0.02)` on top of the existing `stroke`) so the chrome itself reads slightly warm without distracting.
- Layout-edit handles, weather temperature, and today-cell of the calendar all switch to amber.

**Use case:** users who find Standard's green too bedside-clock; want something warmer-feeling at a glance.

### 6.4 WeatherForecast widget (new built-in)

A multi-day forecast widget, shared across modes. Lives next to the existing single-condition `Weather` widget.

**Data source:** WeatherAPI.com `forecast.json?days=5&q={city}&aqi=no&alerts=no`. Same API key as the current Weather widget — they share the secret. `WeatherRepository` gains a `forecast(city, days=5): Result<List<DayForecast>>` method; `WeatherViewModel` gains a `forecast: StateFlow<ForecastState>` mirroring the existing `weather: StateFlow<WeatherState>` triad of states (Loading / Failed / Loaded).

```kotlin
data class DayForecast(
    val date: LocalDate,
    val high: Int,     // celsius (or fahrenheit per settings)
    val low: Int,
    val conditionCode: Int,
    val conditionText: String,
    val iconUrl: String,
)
```

**Rendering:**

- **Tile (Console)** — horizontal row of 5 day cards inside the tile. Each card: short weekday name ("Fri"), small condition icon, `H22°` / `L14°` in tabular nums. Today's card is highlighted with the active accent border.
- **Chip (Cinematic drawer)** — one condensed line: `5d: H22 H24 H21 H20 H18`. Each value separated by 8dp; today inferred from position. No icons.
- **Minimal (Ambient)** — one line: `Tomorrow: 24° partly cloudy · Sat 21° rain`. Shows tomorrow + day after only.

**Default size in Console:** `min=8×1, default=12×1, max=12×2` (wide horizontal strip).

**Empty/error states** mirror the current Weather widget (Section 6.1 of the audit fix): unconfigured → "Add a WeatherAPI key →"; failed → subdued retry; loading → 5 skeleton boxes for the 5 day cards.

## 7. Settings sheet (rebuilt)

The Material 3 `Dialog` from the audit-pass rewrite is replaced by a **full-height side-sheet** that slides in from the right edge:

- Width: `min(560dp, 60vw)`.
- Height: 100vh.
- Backdrop: scrim `rgba(0,0,0,0.55)`.
- Background: `surface.0` (`#0b0b0c`), 24dp left border-radius, 1dp left stroke.
- Slide-in: 350ms M3 emphasized curve from `x = 100%` to `x = 0`.
- Esc, click-outside (on scrim), and a header `X` icon all close it.

### Tabs

1. **Display**
   - Mode (radio: Cinematic / Ambient / Console)
   - Variant picker (always visible — every mode has variants in v1):
     - Cinematic → Dusk / Noir
     - Ambient → Lumen / Borealis (plus a "Quieter Lumen" toggle when Lumen is selected)
     - Console → Standard / Amber
   - Clock format (12h / 24h)
   - Show seconds (toggle) — adds `:ss` after the minute in all modes
   - Show date (toggle, default on)
2. **Widgets**
   - List of installed widgets (id, displayName, description)
   - Per-widget on/off toggle
   - Drag-handle to reorder (affects Cinematic drawer order and Console default layout)
   - Per-widget gear → opens widget's own config (passed through from `WidgetFactory.configFields`)
   - "Edit Console layout" button → enters Console layout edit mode (also opens Console if not already there)
   - "Reset Console layout" button
3. **Triggers** *(renamed from "Activation")*
   - Idle timeout (slider: 1 / 2 / 5 / 10 / 15 / 30 / 60 minutes)
   - Start with system (toggle)
   - Mouse-movement dismiss (toggle)
   - Exit-on-keypress (toggle)
   - Run on lock screen (Windows-only, future)
4. **Sync** *(renamed from "Backend")*
   - Banner: "Off by default — nothing leaves your machine unless you turn this on."
   - Enable toggle
   - Backend URL (text)
   - API key (secret)
5. **About**
   - Version + build hash
   - License
   - Credits / acknowledgements
   - Links: GitHub, docs, troubleshooting

### Per-tab visual

Tabs are pill buttons in a `Row` at the top of the sheet. Each pill: `Modifier.weight(1f)`, `maxLines = 1`, padding 6dp/10dp. Selected pill uses `status.accent` background at 14% alpha with full-opacity text.

Tab row is sticky at the top of the sheet's scroll viewport. Content scrolls under it.

City selection is **removed entirely** from Settings. It's a control inside the Weather widget's own surface.

## 8. Chrome (corner buttons)

- Container: `Box(modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp))`.
- Two `IconButton`s: ⚙ Settings, ? Help.
- 28dp circle each, 10dp gap.
- Default state: `alpha = 0.32f` on the entire container; on pointer-enter → `alpha = 0.95f` over 180ms.
- Same rendering in all three modes (so mode switches don't move the buttons).
- The buttons themselves use `surface.1` background and `stroke`-colored border.

## 9. Keyboard map + Help dialog

Wired in `KeyEventHandler.handleWindowKeyEvent`:

| Key | Action |
|---|---|
| `Esc` | Dismiss dashboard |
| `Ctrl+Q` / `Cmd+Q` | Quit application |
| `Ctrl+,` | Open Settings sheet |
| `F1` / `?` (Shift+/) | Open Help dialog |
| `M` | Cycle mode (Cinematic → Ambient → Console → Cinematic) |
| `1` | Jump to Cinematic |
| `2` | Jump to Ambient |
| `3` | Jump to Console |
| `V` | Cycle variant within current mode |
| `W` | Toggle widget drawer (Cinematic only) |
| `L` | Toggle layout edit mode (Console only) |
| `Ctrl+R` | Reload widgets from disk |
| Other keys | Dismiss (when `settings.exitOnKeypress = true`) |

Help dialog is also rebuilt to match the new Settings sheet style (side-sheet, smaller width = `min(400dp, 40vw)`). Body renders the keyboard table grouped: Navigation / Mode / Widgets / System.

## 10. Data model & migration

### New / changed fields in `SettingsModel`

```kotlin
data class SettingsModel(
    // existing fields preserved
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
    // removed: selectedDesignId (the old 1..11 clock designs)
)

enum class Mode { Cinematic, Ambient, Console }
enum class CinematicVariant { Dusk, Noir }
enum class AmbientVariant { Lumen, Borealis }
enum class ConsoleVariant { Standard, Amber }
data class GridRect(val col: Int, val row: Int, val cols: Int, val rows: Int)
```

The `V` key cycles whichever variant enum belongs to the current `mode`. The variant picker in Settings → Display dynamically renders the correct enum's values.

### Removed

- `selectedDesignId` — the old 11-design clock chooser. Each new mode renders the clock its own way; users no longer pick "Design 5."
- `currentCity` — moves into the Weather widget's own config (`widgetConfigs["...weather"]["city"]`).
- `autoPlayEnabled`, `shuffleEnabled` — corresponded to the deleted toolbar buttons.
- The `Display` tab's old "Design 1..11" list — replaced with mode + variant pickers.

### Persistence migration

On settings load, if `mode` field is missing (old settings):
- Set `mode = Cinematic`.
- If old `selectedDesignId` is in (1..11), record it once in a one-off `migration.log` but otherwise drop it.
- If old `currentCity` is set, write it into `widgetConfigs["com.droidslife.screensaver.weather"]["city"]`.
- Drop `autoPlayEnabled` / `shuffleEnabled` silently.

`PreferencesRepository.getSettings()` runs this migration synchronously on first read.

### Removed code

The following files become entirely unused after the rebuild and are deleted:

- `composeApp/src/commonMain/kotlin/com/droidslife/screensaver/clockdigits/DigitalClockDigit*.kt` (except whichever the Cinematic clock typography reuses — likely none, since Cinematic uses Inter Tight directly)
- The Auto-cycle and Shuffle toolbar buttons (already toolbar-less now)
- The 11-design switching logic in `ClockViewModel.kt`

The clock-digit composables under `clockdigits/` are deleted in full because no mode in v1 uses the 7-segment / decorative number renderers. If we miss them later we can recover from git.

## 11. Component breakdown (file plan)

**New files:**
```
composeApp/src/commonMain/kotlin/com/droidslife/screensaver/
  ui/
    DwellTheme.kt                  // palette/type/motion tokens (replaces existing theme/Theme.kt usage)
    Tokens.kt                      // color, type, motion, spacing const
    Fonts.kt                       // bundled font references
    CornerButtons.kt               // shared chrome
  modes/
    ModeHost.kt                    // top-level switcher + cross-fade animator
    cinematic/
      CinematicMode.kt             // dispatches by cinematicVariant
      MeshGradientBackdrop.kt      // shared Dusk implementation
      DuskBackdrop.kt              // Dusk-specific configuration
      NoirBackdrop.kt              // Noir-specific implementation (single drifting glow)
      WidgetDrawer.kt
    ambient/
      AmbientMode.kt               // dispatches by ambientVariant
      Lumen.kt
      Borealis.kt
      AuroraRibbons.kt             // Compose Canvas drawing for Borealis
      OrbitalDial.kt               // Compose Canvas drawing for Lumen
    console/
      ConsoleMode.kt               // dispatches by consoleVariant
      ConsoleGrid.kt               // 12x6 layout engine, drag-resize
      ConsoleEditOverlay.kt        // edit-mode chrome (size badges, handles)
      ConsoleAccents.kt            // Standard (green) / Amber (warm) accent tokens
  widgets/
    DefaultRenderers.kt            // DefaultTileRender, DefaultChipRender, DefaultMinimalRender
    builtins/
      CalendarWidget.kt            // new
      IdleCounterWidget.kt         // new (Console-only)
      WeatherForecastWidget.kt     // new (5-day forecast)
  settings/
    SettingsSheet.kt               // replaces SettingsDialog.kt
    sections/
      DisplaySection.kt
      WidgetsSection.kt
      TriggersSection.kt
      SyncSection.kt
      AboutSection.kt

composeApp/src/commonMain/composeResources/font/
  InterTight-Light.ttf, -Regular.ttf, -Medium.ttf, -SemiBold.ttf, -Bold.ttf
  JetBrainsMono-ExtraLight.ttf, -Regular.ttf, -Medium.ttf

widget-api/src/commonMain/kotlin/com/droidslife/screensaver/widget/api/
  WidgetRender.kt                  // WidgetRenderTarget, WidgetSummary, WidgetAccent
  WidgetSize.kt                    // WidgetSize, GridRect
```

**Modified files:**
```
composeApp/src/commonMain/kotlin/com/droidslife/screensaver/
  DigitalClockApp.kt               // becomes a thin host that delegates to ModeHost
  App.kt                           // mounts new theme + ModeHost
  settings/SettingsModel.kt        // new fields + migration
  settings/SettingsViewModel.kt    // setters for mode/variant/layout, migration hook
  settings/PreferencesRepository.kt// migration on read
  components/KeyEventHandler.kt    // new shortcuts (M, 1/2/3, V, W, L, Ctrl+,)
  components/KeyEventAction.kt     // new action enums
  dashboard/WidgetGrid.kt          // becomes Console-only; deleted if ConsoleGrid replaces it
  dashboard/WidgetCard.kt          // deleted (replaced by mode-specific renderers)
  widget/builtin/ClockWidgetFactory.kt    // Tile/Chip/Minimal implementations
  widget/builtin/WeatherWidgetFactory.kt  // Tile/Chip/Minimal implementations, City picker control
  widget/builtin/TodosWidgetFactory.kt    // Tile/Chip/Minimal implementations
  widget/builtin/ExpensesWidgetFactory.kt // Tile/Chip/Minimal implementations
  weather/WeatherRepository.kt     // add forecast(city, days) method
  weather/WeatherApi.kt            // add forecast endpoint mapping
  weather/WeatherViewModel.kt      // add forecast: StateFlow<ForecastState>
  jvmMain/kotlin/main.kt           // remove showHelpDialog/showCitySelectionDialog plumbing; replaced by KeyEventAction routing

widget-api/src/commonMain/kotlin/com/droidslife/screensaver/widget/api/
  WidgetDescriptor.kt              // add preferredSize: WidgetSize
  Widget.kt                        // add Render(target, scope) default + summary()
```

**Deleted files:**
```
composeApp/src/commonMain/kotlin/com/droidslife/screensaver/clockdigits/*  (all 11 digit composables)
composeApp/src/commonMain/kotlin/com/droidslife/screensaver/clock/ClockViewModel.kt  (design-cycle logic)
composeApp/src/commonMain/kotlin/com/droidslife/screensaver/settings/SettingsDialog.kt  (replaced by SettingsSheet)
composeApp/src/commonMain/kotlin/com/droidslife/screensaver/dashboard/WidgetCard.kt
composeApp/src/commonMain/kotlin/com/droidslife/screensaver/dashboard/WidgetGrid.kt
```

## 12. Open questions / explicit decisions

| # | Question | Decision |
|---|---|---|
| 1 | What's the v1 default mode? | **Cinematic** |
| 2 | What's the v1 default Ambient variant? | **Lumen** |
| 3 | How does user switch modes? | Keyboard (`M` / `1` / `2` / `3`) + Settings → Display |
| 4 | Does Settings rebuild reuse M3 components inside? | No — full visual rebuild (side-sheet, custom chrome). Internal form components can still reuse M3 primitives but styled with Dwell tokens. |
| 5 | Are the 11 old clock designs preserved as a Cinematic variant? | No — deleted entirely. |
| 6 | Variants per mode in v1 | **All three modes get two variants in v1**: Cinematic = Dusk (default) + Noir; Ambient = Lumen (default) + Borealis; Console = Standard (default) + Amber. |
| 7 | Does Console drag-resize require the user to enter an "edit mode"? | Yes — `L` toggle. Reduces accidental layout changes during normal viewing. |
| 8 | City picker: global control or part of Weather widget? | **Part of Weather widget** — removed from Settings. |
| 9 | Are widget authors required to override `summary()`? | Yes — `summary()` is mandatory on the interface. Widgets that want host defaults for a given target just don't override the matching `Render(target, …)` branch. |
| 10 | Light theme? | **Not a global toggle.** Instead, future variants ship pre-baked light versions of the same mode (e.g. "Dusk Light", "Borealis Day"). v1 ships dark variants only. |
| 11 | Backend / sync UI? | Kept but moved to its own "Sync" tab with the "off by default" disclaimer. |
| 12 | Legacy compatibility for third-party JAR widgets? | **None.** The repo currently has zero third-party widgets in the wild, so the new `Render(target, scope)` + `summary()` contract is the only contract. |

## 13. Success criteria (acceptance for the rebuild)

A normal user opens Dwell for the first time and:

1. Sees the Cinematic mode dashboard within ~2s of trigger (no settings dialog, no red weather error).
2. Can press `M` and watch the mode cross-fade smoothly to Ambient → Console.
3. Can press `V` in any mode and cycle variants — Dusk ↔ Noir in Cinematic, Lumen ↔ Borealis in Ambient, Standard ↔ Amber in Console.
4. Can press `3 L` to enter Console edit mode and drag/resize tiles.
5. Can open Settings with `Ctrl+,` or the corner button; the sheet slides in from the right and dismisses with Esc or click-outside.
6. No widget renders with a debug-looking "Clock" / "Weather" / etc. header in Cinematic or Ambient.
7. The Settings dialog tab labels never truncate.
8. The Weather widget, when no API key is configured, shows a friendly empty state with an "Add key →" CTA (already in place after the audit pass — verify it still works after the rebuild).

## 14. Out-of-scope follow-ups

- More than two variants per mode (the spec ships two of each; further variants like Plasma / Editorial / Mondrian come later).
- Calendar integration with real OS calendars (CalDAV / Outlook / Google) — v1 ships a month-grid only.
- Light-design variants (the framework supports them; first light variant is a follow-up after dark v1 ships).
- Wallpaper integration (use desktop wallpaper as a fallback Cinematic backdrop).
- Per-monitor positioning on multi-monitor setups.
- WebGL-quality particle systems for Ambient variants (current SVG/Canvas-on-Skia is the v1 ceiling).
- Long-range weather forecast (>5 days), hourly forecast, weather radar.
