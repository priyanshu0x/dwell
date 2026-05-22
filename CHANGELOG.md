# Changelog

Notable user-facing changes. The project does not yet ship versioned releases; this file reflects what's on `main`.

## Unreleased

### Added

- **`scripts/dwell` CLI** with one-line subcommands — `show`, `daemon`, `config`, `dev`, `build`, `install`, `uninstall`, `register`, `unregister`, `status`, `help`. Auto-installs Temurin JDK 21 to `~/jdks/` on first run; no `JAVA_HOME` setup required.
- **`scripts/dwell.cmd`** — same CLI for Windows.
- **`dwell register` / `unregister`** — autostart on login. Linux: writes `~/.config/autostart/dwell.desktop` + `~/.config/systemd/user/dwell.service`. macOS: `~/Library/LaunchAgents/dev.dwell.daemon.plist`. Windows: Startup-folder shortcut.
- **Three modes** with two variants each:
  - **Cinematic** (default) — *Dusk* (peach/violet mesh) and *Noir* (warm-white drifting glow).
  - **Ambient** — *Lumen* (sci-fi HUD with orbital dial, perspective grid, telemetry) and *Borealis* (drifting aurora ribbons).
  - **Console** — *Standard* (terminal-green accent) and *Amber* (vintage-CRT accent), with 12×6 tile grid and a drag-resize Edit Layout mode (`L`).
- **Settings sheet** — full-height right-side slide-in panel with sticky tab row, scrim/Esc/click-outside dismiss, persistent close icon, and 5 tabs (Display / Widgets / Triggers / Sync / About).
- **New widget contract** — `Render(target: WidgetRenderTarget, scope, modifier)` + mandatory `summary(): WidgetSummary` so the same widget can render as a Console tile, a Cinematic-drawer chip, or an Ambient minimal line.
- **Author-declared widget sizes** — `WidgetSize(minCols, minRows, defaultCols, defaultRows, maxCols, maxRows)` on `WidgetFactory`. The Console grid clamps user-resized rects to these bounds.
- **New built-in widgets** — Calendar (month grid), Idle Counter (Console-only), Weather Forecast (5-day, opt-in, shares the Weather API key).
- **Friendly Weather empty state** — when no API key is configured the widget shows "Add a WeatherAPI key →" with an Open Settings link instead of a red error.
- **Tray menu** — Mode + Variant submenus with checkmarks, Start-at-login toggle, Reload Widgets, Quit.
- **Keyboard shortcuts**:
  - `Esc`, `Alt`, mouse-move, or any key → dismiss dashboard (configurable).
  - `Ctrl+Q` / `Cmd+Q` → quit.
  - `Ctrl+,` → Settings sheet.
  - `F1` / `?` → Help dialog.
  - `M` → cycle mode; `1` / `2` / `3` → jump to Cinematic / Ambient / Console.
  - `V` → cycle variant within current mode.
  - `W` → toggle Cinematic widget drawer.
  - `L` → toggle Console layout edit mode.
  - `Ctrl+R` → reload widgets from `~/.screensaver/widgets/`.
- **Welcome toast** on first launch ("press M / V / F1") that auto-dismisses after 4.5s and never repeats.
- **Mode-change motion** — 600ms cross-fade with a 12px midpoint blur.
- **Real build metadata** — Gradle generates `BuildInfo.kt` with the current version, short git commit, and build date; the About screen reads from it.
- **Bundled fonts** — Inter Tight (six weights) and JetBrains Mono (four weights), loaded via Compose Resources. No system font fallback for the clock.

### Changed

- **Tray text + window title**: "Screen Saver App" → **Dwell**.
- **`gradlew`** is now executable in the index (no more `chmod +x` after clone).
- **README** rewritten around the CLI with per-mode/per-variant screenshots.
- **Settings dialog**: full visual rebuild as a side-sheet. Tab labels no longer truncate; Esc and click-outside both dismiss.
- **Cycle interval** in widget config — now a structured `DurationChoice` dropdown (5s / 10s / 30s / 1m / 5m / 15m) instead of free text.
- **Currency** in Expenses widget — full ISO 4217 dropdown with USD/EUR/GBP/INR pinned, replacing the hardcoded 4-option radio.
- **Categories** in Expenses widget — removable chip editor instead of a comma-separated string.
- **WeatherForecast** is **off by default** (opt-in via Settings → Widgets).

### Fixed

- Cinematic widget drawer no longer renders full widget tiles (it now renders compact summary chips).
- Console tiles now have visible chrome (`Surface1` background, 1dp `Stroke` border, 12dp rounded corners) — previously widgets floated on bare black.
- Windows idle monitor — `LastInputInfo.cbSize` initialization on JDK 21 (upstream fix from `31f264f`).
- `LICENSE` placeholder in About — now reads from `BuildInfo.VERSION`.

### Removed

- The 11 numbered "Design 1..11" clock designs and the `selectedDesignId` setting. Each mode renders the clock its own way (Inter Tight for Cinematic/Borealis, JetBrains Mono ExtraLight for Lumen).
- `currentCity` global setting — city now lives inside the Weather widget's own config (migrated on first read).
- `autoPlayEnabled`, `shuffleEnabled` — corresponded to the deleted toolbar buttons.
- Legacy files: `clockdigits/*`, `clock/ClockViewModel.kt`, `settings/SettingsDialog.kt`, `dashboard/WidgetCard.kt`, `dashboard/WidgetGrid.kt`, `DigitalClockApp.kt`.
- Deprecated `Widget.Content(modifier)` — `Render(target, scope, modifier)` is the only contract.

### Deferred (known limitations)

- Forecast widget hardcodes its empty-state accent.
- Settings → Widgets drag-reorder gesture is decorative only.
- Some Settings field editors (`Enum`, `DurationChoice`, `Currency`, `StringList`, `DesignPicker`) show "editor not yet available" until full porting lands.
- "Mouse-movement dismiss" toggle in Triggers is a disabled placeholder.
- "Run on lock screen" (Windows) is marked future.
- JVM cold start adds ~3–4s of black before first paint; the terminal cheatsheet from `./scripts/dwell show` covers the gap for source users.
