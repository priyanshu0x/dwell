# Visual deltas: implementation vs. mockups

Reference mockups: `.superpowers/brainstorm/623449-1779383790/content/*.html` (still served at the brainstorm server while it was running).

Test screenshots from the running app: `/tmp/dwell_*.png` (one per mode/variant).

## Cinematic Dusk

**Mockup**
- Clock at ~200pt-equivalent anchored at the rule-of-thirds intersection (8% width, 26% height); takes ~25% of the viewport's vertical real-estate.
- Mesh gradient is rich peach + violet + midnight, with visible color *separation* between blobs.
- Meta line under the clock is clearly readable (Inter 16sp, mid-grey).
- Corner buttons visible but recessed at 32% alpha.

**Actual**
- ❌ Clock font weight looks less Bold than spec demands; possibly Compose is falling back to a system font (Skia text rendering can substitute when an Inter Tight 700 glyph is unavailable for the specific code-points).
- ⚠️ Clock proportionally *smaller* than mockup — looks more like 150pt-equivalent at fullscreen.
- ⚠️ Mesh gradient blends to almost a single mood (warm purple) rather than three distinct color zones — the peach/violet/midnight blobs don't read as separate.
- ❌ Meta line "Friday, 22 May" is **almost invisible** at fullscreen — rendered too small + too low contrast.

## Cinematic Noir

**Mockup**
- Pure black with a single warm-white drifting radial glow centered around the clock; glow is soft and large.
- Clock is pure white Inter Tight Bold, dramatic.

**Actual (after WidgetDrawer fix)**
- ✅ Backdrop is pure black.
- ⚠️ Warm-white glow appears more grey than warm — DwellColors.NoirGlow is `#FFF5E1` but the displayed glow doesn't read as warm. Could be alpha (30%) + dark backdrop muting the warmth, or `Brush.radialGradient`'s falloff curve.
- ⚠️ Glow position visible mid-screen on second screenshot, but at first appeared at top — animation may have a sin() phase issue or first-frame layout bug.
- ✅ Clock visible upper-left in white.
- ❌ Meta line same readability issue.

## Ambient Lumen

**Mockup**
- Full HUD: cyan corner brackets, perspective grid floor receding into the distance, top + bottom + side telemetry strips in JetBrains Mono.
- Orbital dial with 60 minute-ticks, current minute pulsing.
- Clock in thin Inter Tight 200 with soft cyan glow.

**Actual**
- ✅ Corner brackets visible (4 corners).
- ✅ Top telemetry visible: `DWELL · 01:09 IST · WTH OK`.
- ✅ Orbital dial visible with rings + minute ticks.
- ✅ Clock visible with subline `FRI · 22 MAY · 2026`.
- ❌ **Perspective grid floor MISSING** — spec/mockup had a receding grid; agent dropped it.
- ❌ **Side telemetry (right edge, vertical) MISSING** — spec mentioned `ν 0.000017 hz · drift +0.0s`.
- ❌ **Bottom-left telemetry MISSING** — spec mentioned ISO timestamp `2026-05-22T01:09Z · idle 14′22″`.
- ⚠️ Clock font weight may not be ExtraLight (200) — looks more like Regular/Medium.

## Ambient Lumen (Quieter)

**Mockup-style spec**
- Brackets hidden, side telemetry hidden, top/bottom reduced to `{time} · {temp}°{city}`.

**Actual**
- ✅ Corner brackets hidden.
- ✅ Top telemetry reduced to `01:12 IST` (city + temp not shown because weather widget isn't loaded).
- ✅ Otherwise identical to Lumen minus brackets.

## Ambient Borealis

**Mockup**
- Three aurora ribbons drifting horizontally, screen-blended, deeply blurred (soft).
- Star field of ~10 sub-pixel stars scattered across upper portion.
- Clock + date + place (`Mumbai`) stacked center.
- Bottom-edge vignette so ribbons fade off the bottom.

**Actual**
- ✅ Three aurora ribbons visible.
- ⚠️ Ribbons read as **thick, hard-edged bands** rather than soft drifting auroras. The 20dp `Modifier.blur` isn't softening edges enough; `BlendMode.Screen` on a `drawPath` may not be giving the same effect as the HTML mockup's `mix-blend-mode: screen + filter: blur(20px) saturate(1.1)`.
- ❌ **Star field MISSING** — spec called for 10 stars; not visible.
- ❌ **Place line "Mumbai" MISSING** — only date is shown below the clock.
- ❌ **Bottom-edge vignette MISSING** — ribbons cut off abruptly at the bottom edge.

## Console Standard

**Mockup**
- 6 tiles in 12×6 grid, each with `Surface1` (`#131316`) background + 1dp `Stroke` border + 12dp rounded corners + 14/16dp internal padding.
- Tiny uppercase 0.25em-tracked Inter 9px label at top of each tile.
- Big numeric value in JetBrains Mono (clock uses Inter Tight).
- Small subtitle in `text.mid`.
- Console green `#9ecda0` on weather temp + today calendar cell + edit-layout handles.

**Actual**
- ❌ **Tile chrome (background, border, corner radius) completely MISSING**. Tiles render as floating text on the bare `Surface0` background. The `DefaultTileRender` IS doing `.background(Surface1).border(...)` but the built-in widgets' `Content(modifier)` overrides bypass that — they render directly without chrome. So Console widgets look bare.
- ❌ **WeatherForecast widget IS RENDERING despite being off by default**. It shows "Couldn't load forecast" in the top-left, where there should be empty space (or no widget at all per the default layout).
- ✅ Calendar today cell (`22`) is highlighted with green tint.
- ⚠️ Weather widget shows full multiline empty-state text inside the tile area without chrome, looks like body copy floating on the screen.
- ⚠️ Idle counter renders with seconds (`00:02`) — works correctly.
- ❌ No visible gap-spacing between tiles because there are no tile backgrounds to delineate them.

## Console Amber

**Not tested yet** but the same chrome-missing bug applies. Amber accent should swap green for `#f3b95e`.

## Settings sheet, keyboard map, mode-change animation

**Not visually verified** because xdotool keypresses are unreliable under XWayland on this machine. Code-level the SettingsSheet was wired (slides in from right with scrim, Esc/click-outside dismiss, 5 tab pills). Mode-change cross-fade with midpoint blur was wired in `ModeHost.kt`. Both need visual verification once the modes themselves are polished.

## Drag-resize Console edit mode

**Not visually verified** — would need to press `L` while in Console mode and screenshot, then attempt a drag with mouse events through XWayland. Same input-reliability problem.

---

# Root-cause analysis

1. **Tile chrome missing in Console**: built-in widgets implement `Content(modifier)` directly (legacy method that pre-dates the rebuild). When called as `Render(Tile, scope, modifier)`, the default implementation forwards to `Content(modifier)` — which renders widget body but **does not wrap it in a tile card**. The tile chrome only lives in `DefaultTileRender(summary)` and isn't applied unless the widget uses that.

   Fix: either (a) wrap every `Render(Tile, ...)` call in the host (ConsoleMode) with the tile chrome, OR (b) have each built-in widget override `Render(Tile, ...)` to wrap its `Content(modifier)` with the chrome themselves, OR (c) make `DefaultTileRender` accept a content slot and have widgets render through it.

   Cleanest: option (a) — `ConsoleGrid`'s `cell` lambda wraps its child with `TileChrome { instance.widget.Render(Tile, ...) }`.

2. **WeatherForecast renders in Console despite being off**: somewhere in widget registration / default enabled IDs, WeatherForecast is being included. Need to check `WidgetRegistry.syncWithSettings` and the `defaultWidgetIds` set.

3. **Missing visual elements (perspective grid, side telemetry, star field, vignette, place line, etc.)**: agents implemented what was explicitly in the plan's code snippets but skipped or simplified what was only in the spec's prose. The plan task descriptions sometimes said "include X per spec" without re-stating X's implementation details.

4. **Font weight / size mismatches**: hard to debug remotely. Compose Desktop's font loading via Compose Resources should work, but Skia text rendering may be falling back to system fonts for some weights when bundled fonts aren't matching. Need to log `FontFamily` resolution.

5. **Aurora ribbon softness**: the HTML mockup used CSS `filter: blur(20px) saturate(1.1)` which is a post-rasterization filter on a screen-blended composite. Compose Desktop's `Modifier.blur(20.dp)` applies blur to a single composable layer; combined with `BlendMode.Screen` on each `drawPath`, the result is different.

# Polish plan (proposed)

Given the user wants pixel-perfect:

1. **Stop running new mode tests.** Fix the major chrome bug (Console tiles) and forecast-by-default bug first — those are functional/data correctness, not just polish.
2. **Run the brainstorm server** so the mockups are available side-by-side for comparison: `.superpowers/brainstorm/623449-1779383790/content/*.html`.
3. **Dispatch one focused agent per mode** with the mockup HTML pinned as the reference. Each agent: (a) reads its mockup, (b) reads the current implementation, (c) lists deltas, (d) fixes, (e) screenshots after, (f) reports.
4. **Visual regression**: capture screenshots, diff against a "known-good" baseline once we have one.
5. **Font verification**: add a small diagnostic LaunchedEffect that logs `FontFamily` selection at first paint to confirm Inter Tight is actually being used. Or just verify visually that the digit shapes match Inter Tight (compare the `0` glyph specifically).

Honest expectation: "pixel-perfect" between HTML+CSS mockups and Compose Desktop is unachievable — text metrics, blur passes, blend modes, and font hinting all differ. **Achievable: visual proportions, hierarchy, and palette match the mockups to within ~5% deviation by eye.**
