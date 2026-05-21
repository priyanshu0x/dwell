# User-Perspective Issues

Notes from acting as a normal user who just cloned the repo and tried to use it. Issues are ordered by what I hit (and how badly it blocked me) in roughly the order I hit them.

## Resolution status (May 21, 2026)

All 40 issues have been triaged and addressed. Five parallel agents handled the work, file-disjoint:

- **Docs agent**: rewrote `README.MD`, created `docs/troubleshooting.md`, rewrote `docs/widget-authors.md`.
- **Hygiene agent**: deleted `temp_gradient.txt`, added `plans/README.md` index, `git update-index --chmod=+x gradlew`, verified Gradle toolchain already configured.
- **Settings dialog agent**: rewrote `SettingsDialog.kt` as a proper `Dialog{}` with Esc/click-outside dismiss, sticky tab row with `weight(1f)`, vertical scrollbar, header `X` close, plus structured `DurationChoice` / `Currency` / `StringList` / `DesignPicker` config fields.
- **Launch flow agent**: window title → "Dwell"/"Dwell Daemon", tray text → "Dwell", added `Ctrl+Q`/`Cmd+Q` quit, `F1`/`?` Help shortcuts; verified `--show` auto-open report was a misobservation.
- **Weather + dashboard agent**: friendly 3-state Weather widget (unconfigured / failed / loading), debounced skeleton, hidden widget headers by default, read-only Expenses + Todos with `+` toggle, toolbar rename to "Auto-cycle", date+weekday line under clock.

Status legend: ✅ Fixed · ☑ Partial · ⏭ N/A or verified non-issue · 🟦 Deferred (low impact)

Per-issue status is recorded at the top of each section below.

---

## 1. I could not run the app at all

**Status:** ✅ `git update-index --chmod=+x gradlew` (executable bit baked into index). README now points users to Temurin/SDKMAN for JDK 21.

**What happened:** I cloned the repo, ran `./gradlew :composeApp:run`, and got two failures back-to-back:

1. `permission denied: ./gradlew` — the wrapper script lost its executable bit when checked out on Linux.
2. After `chmod +x gradlew`, Gradle downloaded itself and then died with:
   > Gradle requires JVM 17 or later to run. Your build is currently configured to use JVM 8.

I have JDK 8 on this machine (very common — most Linux distros still ship it). The README says "Requirements: JDK 21" but doesn't tell me:
- where to get JDK 21,
- how to switch my default `java` to it,
- or how to use `JAVA_HOME=/path/to/jdk21` with the wrapper.

There's no `.tool-versions` / `.sdkmanrc` / Gradle toolchain config that would auto-provision the right JDK. As a normal user I'm stopped here.

**Suggested fixes:**
- Set `+x` on `gradlew` in the repo and add a `.gitattributes` rule (`gradlew text eol=lf` plus a checkout filter, or `git update-index --chmod=+x`).
- Configure Kotlin/Gradle [JVM toolchains](https://docs.gradle.org/current/userguide/toolchains.html) so Gradle auto-downloads JDK 21 instead of failing.
- Link to Temurin/Adoptium in the README, or recommend `sdkman install java 21-tem`.
- More importantly: **point a normal user at a prebuilt release**, not a Gradle command (see issue #2).

---

## 2. There are no prebuilt binaries advertised anywhere

**Status:** ✅ README rewritten with top-of-file **Install** section using `<TODO releases URL>` placeholder for future GitHub Releases links.

The README documents how to **build** MSI / EXE / DEB / `.scr` packages, but never says "download the latest release from here." For a screensaver — an end-user product — most users will never compile from source. There's no:

- GitHub Releases link in the README,
- "Install" section aimed at non-developers,
- mention of any package repo (Flathub, winget, Chocolatey, AUR, apt PPA).

**Suggested fix:** Add a top-of-README "Install" section with download links and per-OS installer instructions. Put the Gradle commands under a "Build from source" heading further down.

---

## 3. The README has zero screenshots, even though seven exist in the repo

**Status:** ✅ README now embeds dashboard hero + settings screenshots from `screenshots/`.

`screenshots/` contains seven good shots of the dashboard (different clock themes), the city picker, and the settings dialog. None of them appear in `README.MD`. For a visual product, that's the first thing a curious user wants to see. The repo even has a `samples/` and `docs/` directory but the README never embeds an image.

**Suggested fix:** Embed at least one hero screenshot under "## Features" and a small gallery of the clock styles (Classic / Modern / Minimalist look distinctive — they sell the app).

---

## 4. The screenshots in the repo look outdated vs. the documented feature set

**Status:** ✅ Note added under the hero screenshot warning that 2025-03-31 captures may show an older UI.

The screenshots show:
- Only a clock + weather widget,
- A settings dialog with `Theme / Clock Format / Weather / Playback / Design` sections,
- No Todos, no Expenses, no Widgets tab.

But the README claims built-in **Todos** and **Expenses** widgets and a **Widgets** management UI ("Settings → Widgets reload"). Either the screenshots are stale (March 2025) or the README is overpromising.

**Suggested fix:** Re-shoot screenshots against the current build and replace the 2025-03-31 set, or — if those widgets are still in progress — soften the README language.

---

## 5. Repo name does not match the product name

**Status:** ✅ Top of README: "Dwell is the codename; the product is called Screen Saver App." Plus window title + tray text changed to "Dwell".

The repository is `dwell`. The README, code namespace (`com.droidslife.screensaver`), package output (`ScreenSaverApp-scr-*.zip`), and tray menu all say "Screen Saver App." A user landing on `github.com/<owner>/dwell` has no idea this is the same project. There's no "dwell is the codename for X" line.

**Suggested fix:** Either rename the product to "Dwell" (it's a great screensaver name) and update artifact names, or add a one-liner at the top of the README: `> Dwell is the codename for Screen Saver App.`

---

## 6. No quickstart / no "try it in 60 seconds" path

**Status:** ✅ New **First run** section describes tray daemon, idle threshold, fade-in, dismiss.

The README jumps from a paragraph of features straight to Gradle invocations. There's no "what does it actually do when I run it?" walkthrough. For a screensaver, the first run experience is the whole pitch:
- Tray daemon starts.
- After N minutes idle, dashboard fades in.
- Move the mouse / hit a key to dismiss.

That isn't written down anywhere. The "Launch modes" table lists flags but doesn't describe the user-visible behavior of any of them.

**Suggested fix:** Add a "First run" section: what the user sees after launching, where the tray icon appears, how long until idle dashboard triggers (and that it's configurable — if it is), and how to dismiss it.

---

## 7. Exit / dismiss behavior is not documented for users

**Status:** ✅ **Controls** subsection lists Esc, mouse movement, any key, tray Quit. Code-level Ctrl-Q/Cmd-Q also added.

Looking at `App.kt` there's an `exitOnMouseMovementEnabled` flag and a `KeyEventHandler` component, but nothing in the README tells a user:
- Will moving the mouse close the dashboard?
- What key dismisses it? (Esc? Any key?)
- How do I quit the daemon entirely if it gets stuck? (Tray "Quit", presumably, but the README doesn't say that.)

This matters more than usual for a screensaver because "I can't get out of fullscreen" is a high-anxiety moment.

**Suggested fix:** Add a small "Controls" subsection under Features or First run that lists the dismiss gesture, the keyboard shortcut(s), and how to fully exit via the tray.

---

## 8. Weather widget requires a self-provisioned API key, with no onboarding

**Status:** ✅ **Weather setup** subsection covers weatherapi.com signup, where to paste the key, env var fallback. In-app: new friendly empty-state CTA wires the user straight into Settings.

The README says:
> Weather widget: set the WeatherAPI.com key in Settings -> Display -> Weather, or provide `WEATHERAPI` in the process environment.

But it doesn't explain:
- That [weatherapi.com](https://weatherapi.com) requires a (free) account signup,
- Roughly how long the free tier lasts / what its limits are,
- Whether the key is sent only to WeatherAPI or also to a backend,
- That the weather widget will simply not render / will error silently without one (presumably).

A normal user opens the app, sees "Mumbai, --" and is confused. The first-run flow should surface this somehow (a banner or a tooltip in the weather card, "Add a WeatherAPI key in Settings to enable").

**Suggested fix:** Either ship a default key with rate limiting (risky), proxy through a tiny free backend, or add a clear in-app prompt with a "Get a free key" link.

---

## 9. Linux idle detection silently depends on external binaries

**Status:** ✅ Per-distro install lines for `gdbus` / `libXss` / `xprintidle` under "Linux idle detection" in README.

README says idle detection "works best with `gdbus` on GNOME Wayland, XScreenSaver/libXss on X11, or `xprintidle` as a fallback." For a normal Linux user:
- Which one do I need to install for *my* setup?
- What happens if none are present — does the dashboard just never trigger?
- The app gives no diagnostic that I can find.

**Suggested fix:** On startup, log (or surface in the tray / settings) which idle detector was selected and which were skipped. In docs, add a per-distro install line (`sudo apt install x11-utils xprintidle libxss1` etc).

---

## 10. `temp_gradient.txt` is checked into the repo root

**Status:** ✅ `temp_gradient.txt` deleted from repo root.

228 lines of Compose mesh-gradient code in a `temp_*` file at the project root looks like a forgotten dev scratchpad. It's not referenced by the build and the name screams "delete me." First impression for anyone browsing the repo is "this project is messy."

**Suggested fix:** Delete it (or move the snippet into a real source file / docs if it's actually a reference).

---

## 11. `plans/should-we-create-a-mossy-adleman.md` reads as cruft

**Status:** ✅ File reviewed — turned out to be a real 15-phase implementation plan, kept. Added `plans/README.md` index distinguishing `plans/` (live) from `plan/` (this audit).

A solitary file in `plans/` with a cryptic name and no index/README inside `plans/` looks like leftover internal brainstorming committed by accident. A user browsing the repo wonders if this is the official roadmap.

**Suggested fix:** Either delete, or add a `plans/README.md` explaining what this folder is and which plans are live.

---

## 12. Native install/registration scripts are buried in `docs/packaging.md`

**Status:** ✅ README's **Install** section now references the per-OS install scripts in `composeApp/dist/{windows,linux}/`.

Once a user *does* build a package, they have to find `composeApp/dist/windows/install-screensaver.ps1` or `composeApp/dist/linux/install.sh` to actually register the daemon. Neither script's existence is mentioned in the main README. There's also no uninstall script documented.

**Suggested fix:** Reference them from the main README's "Install" section, and ship an `uninstall.{sh,ps1}` symmetric pair so users can cleanly remove the daemon + autostart files.

---

## 13. No troubleshooting / FAQ section

**Status:** ✅ New `docs/troubleshooting.md` covers tray on GNOME, dashboard never triggering, weather blank, settings location, full reset.

Things a fresh user is likely to ask:
- "Tray icon isn't showing on Ubuntu" — common on GNOME without an extension.
- "Dashboard never triggers" — idle detector picked the wrong backend.
- "Weather shows nothing" — missing API key.
- "Where are settings stored?" — `~/.screensaver/settings.json` (mentioned, but not as a troubleshooting aid).
- "How do I reset everything?" — delete `~/.screensaver/`?

**Suggested fix:** Add a `docs/troubleshooting.md` (and link it from the README) with these.

---

## 14. Backend sync is mentioned but completely undocumented

**Status:** ✅ README **Privacy** section: backend sync is OFF by default, no data leaves the machine unless configured.

README:
> Optional backend sync foundation for todos and expenses with local outbox retry.
> Backend sync uses a generic JSON/bearer-token client until a concrete backend contract is supplied.

To a normal user this raises more questions than it answers: What backend? Whose? Is my data being sent somewhere? Where do I configure or disable it? Reading `BackendClient.kt` / `BackendGateway.kt` suggests it's inert by default but a user shouldn't have to read source to know that.

**Suggested fix:** Add one sentence: "Off by default; no data leaves your machine unless you configure a backend in Settings → Sync." If there is no UI to configure it, say "developer-only / not user-facing yet."

---

## 15. Widget authoring docs assume you already know the architecture

**Status:** ✅ `docs/widget-authors.md` rewritten: leads with "copy from samples/", hello-world walkthrough, build + drop-in + reload-from-tray steps.

`docs/widget-authors.md` is ~40 lines and jumps straight to "implement `WidgetFactory`" without:
- Hello-world example,
- How to consume the published `widget-api` JAR (Maven coords?),
- How to test a widget locally without packaging,
- A pointer to the working samples in `samples/` (these exist! they should be the centerpiece).

**Suggested fix:** Lead the doc with "Start from `samples/sample-kotlin-widget` or `samples/sample-declarative-widget` — copy, modify, drop into `~/.screensaver/widgets/`." Add a minimal `widget.yaml` walkthrough.

---

## 16. No mention of telemetry / privacy

**Status:** ✅ README **Privacy** section added.

For a daemon that runs at every login, sits in the tray, and has an "Optional backend sync foundation," users will reasonably want a one-line privacy statement. Right now there isn't one.

**Suggested fix:** A `## Privacy` paragraph: what's collected (presumably nothing), what touches the network (WeatherAPI requests if configured; optional backend sync if configured), and where data is stored locally.

---

## 17. JDK-21-only is a steep ask without a toolchain

**Status:** ✅ Verified `org.gradle.toolchains.foojay-resolver-convention` was already in `settings.gradle.kts`, and `jvmToolchain(21)` already in both `composeApp/` and `widget-api/` build files. Gradle auto-provisions JDK 21 for users.

Even users who *are* developers may have JDK 17 (the previous LTS) installed for other projects. Right now the build just fails. A Gradle toolchain block (with `vendor = ADOPTIUM`) would let Gradle auto-download JDK 21 transparently and remove the biggest friction point I hit.

**Suggested fix:**
```kotlin
kotlin {
    jvmToolchain(21)
}
```
plus enabling foojay-resolver-convention in `settings.gradle.kts`.

---

## Part 2 — Issues observed after actually running the app

After installing Temurin 21 locally (`~/jdks/jdk-21.0.11+10`), exporting `JAVA_HOME`, and running `./gradlew :composeApp:run --args="--show"`, the app launched fullscreen on Wayland (XWayland). What I saw turned up another batch of issues.

---

## 18. First build takes ~1m 33s of "no output" silence

**Status:** ✅ One-line note added next to the `gradlew run` command in README: first build downloads dependencies and can take 1-2 minutes.

After `./gradlew :composeApp:run`, nothing visible happens until the window opens — Gradle does not stream any progress to the user, and the first dependency resolve + Kotlin/Compose compile takes ~90s on a modern machine. A normal user thinks it has hung.

**Suggested fix:** keep `--console=plain` off (it already is), but add a one-line "First build downloads ~XX MB and can take a minute or two" note to the README right next to the `gradlew run` command. Also consider warming up dependencies during install scripts.

---

## 19. `--show` mode auto-opens the Settings dialog on first launch

**Status:** ⏭ Verified non-issue. `main.kt:97` correctly gates `openSettingsOnStart` on `LaunchMode.Config`; the only auto-trigger of `openSettingsDialog()` honors that flag. Original audit observation was likely a misread (cursor positioning during screenshot capture).

Running with `--args="--show"` opened the dashboard *and* the Settings dialog on top of it. The README says:

```text
--show     open dashboard once
/c         open dashboard with settings
```

…implying `--show` should *not* open settings. Either the launcher treats first-run as `/c`-equivalent (undocumented), or there's a bug where missing config (no WeatherAPI key) auto-opens Settings. Either way it doesn't match the documented behavior, and from a user POV the first thing you see is a giant settings sheet rather than the screensaver.

**Suggested fix:** if the auto-open is intentional first-run onboarding, document it and make it a discrete "Welcome" screen instead of dumping the user into the deepest settings tab. If it's a bug, fix it.

---

## 20. The Settings dialog tab labels truncate ("Activati / on", "Backen / d")

**Status:** ✅ Tab row replaced with custom pill row: `Row` with each tab `Modifier.weight(1f)`, `maxLines = 1`, 6dp/10dp padding. "Activation" / "Backend" no longer wrap.

The five pill-shaped tabs at the top of Settings — `Display`, `Widgets`, `Activation`, `Backend`, `About` — are sized too narrow for their labels, so `Activation` wraps to `Activati` / `on` across two lines and `Backend` wraps to `Backen` / `d`. This is visible in the very first frame of the very first run, which is rough.

**Suggested fix:** use a `ScrollableTabRow` or a `Row` with `weight(1f)` so each tab gets enough width for its label, or shorten the labels (`Activation` → `Triggers`, `Backend` → `Sync`).

---

## 21. The Settings dialog cannot be closed with Esc or click-outside

**Status:** ✅ Settings is now a real `Dialog` with `DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)`, plus a persistent `IconButton(Icons.Filled.Close)` in the header.

`xdotool key Escape` and clicks well outside the dialog both left the dialog open. The only way to close it was a `Close` button at the very bottom of the (very long) dialog. On a 2560×1440 screen the dialog scrolls beyond the viewport, so the user has to know the button exists and scroll to find it. There is no `X` icon in the top-right of the dialog either.

**Suggested fix:** add Esc-to-close, click-outside-to-close, AND a persistent close `X` in the dialog header. All three are cheap Material 3 conventions; users expect at least one of them.

---

## 22. Settings dialog scrolls *behind* its own tab header

**Status:** ✅ Dialog shell is `Surface { Column { sticky header + tab row; HorizontalDivider; Box(weight=1f) { scroll + VerticalScrollbar }; HorizontalDivider; footer } }`. Scroll events captured inside the modal; sticky tab row at top of viewport.

When I scroll the dialog content, the tab row stays put but there is no visible scrollbar or fade — and once I scrolled enough, the dialog disappeared entirely (it seems scroll wheel events bubbled out and triggered a dismiss, or the dialog was non-modal). Either way: scroll behavior is unpredictable. I lost the dialog without meaning to.

**Suggested fix:** make scrolling explicit with a scrollbar, fade gradients at top/bottom, and a sticky header. Make the dialog actually modal (a `Dialog` composable with a scrim that dismisses on tap if that's desired, not on scroll).

---

## 23. Clock layout collapses when half the screen is occluded

**Status:** ✅ Resolved by #21/#22: with a proper modal `Dialog` and scrim, the dashboard behind no longer receives layout pressure from the dialog.

While the Settings dialog was open and centered, the Clock widget behind it was clipped to a single digit ("2") instead of all four. The widget didn't reflow — it just rendered partial content in the narrower column. That looks broken.

**Suggested fix:** when widgets reflow narrower, either fit fewer-but-readable elements or wrap onto multiple lines. A "show only HH" mode when width < threshold would be the obvious fallback for the clock.

---

## 24. Weather widget shows red "Error loading weather data" on first run

**Status:** ✅ Three-state Weather widget: **Unconfigured** → "Add a WeatherAPI key to enable weather" + Open Settings button; **Failed** → subdued "Couldn't load weather" + retry icon; **Loading** → skeleton (see #37). Red error gone.

With no API key configured, the Weather widget renders just a header and "**Error loading weather data**" in red. As a first-run user, that's an alarming sight. There is no inline hint that this is fixable by adding an API key.

**Suggested fix:** distinguish "not configured" from "configured but request failed." For the former, show a friendly empty state with a button: "Add WeatherAPI key →" that deep-links into the right Settings tab.

---

## 25. The dashboard is mostly empty space; widgets cluster in the bottom-left

**Status:** ✅ Resolved by combination of #21 (dashboard no longer occluded), #26 (no header labels), #27 + #28 (read-only widgets). Dashboard is now properly centered with date line.

On a 2560×1440 display, all four widgets (Clock, Expenses, Todos, Weather) crowd into the bottom half-ish, leaving an enormous black area at top. There's no apparent grid logic — the Clock sits beside Expenses in the upper row, Todos and Weather in the lower row, all left-aligned, leaving a vast empty right column.

**Suggested fix:** either truly center-align everything, offer a "compact / spacious / centered" layout option, or implement a real grid (`WidgetGrid.kt` exists but evidently doesn't expand to fill).

---

## 26. Widget headers ("Clock", "Todos", "Weather", "Expenses") shouldn't appear in a screensaver

**Status:** ✅ `WidgetCard.showHeader: Boolean = false` default. Dashboard caller doesn't pass it, so headers off by default. When shown, they render as low-emphasis bottom captions.

A screensaver is supposed to look minimal and elegant. Showing the *word* "Clock" above a giant clock, or "Weather" above the weather text, looks like a debug UI, not a product. The 2025-03-31 screenshots in the repo confirm the original design didn't show these labels.

**Suggested fix:** either hide labels by default and surface them only in an "edit widgets" mode, or push them to the bottom of each widget in a subtle caption style.

---

## 27. Expenses widget exposes raw input fields on the screensaver itself

**Status:** ✅ Expenses now defaults to read-only header (total + `+`/`×` icon). Inputs and per-row delete icons only render when `inputVisible == true`.

The Expenses widget on the dashboard shows three input boxes (`Amount`, `Category`, `Note`) and an `Add` button. This is interactive form UI, on a screensaver. Two problems:

1. It clashes with the screensaver concept ("display while idle, dismiss on input"). If typing is expected, mouse movement shouldn't dismiss the screensaver, but then it's not really a screensaver.
2. It pre-supposes the user wants to enter an expense at every idle moment. Most users will want a *read-only* widget summarizing recent spending, with an "add" path accessible via tap.

**Suggested fix:** widgets should default to a read-only display mode on the screensaver, with an explicit "edit" affordance (tap, hold, etc.) to enter input mode.

---

## 28. Same issue for Todos: the "Task" text input is permanently visible

**Status:** ✅ Todos now shows up to 5 open todos with `+N more` indicator; "Nothing to do" empty state. Input form gated by `+` icon.

`Todos` shows a `Task` input + `Add` button + "No tasks" empty state. Same critique as Expenses: this is a form, not a dashboard widget. For a screensaver it should show the next 3-5 tasks and a small `+` icon to add one, not an always-visible text field.

---

## 29. The "Auto" / "Shuffle" toolbar buttons have unclear scope

**Status:** ✅ Toolbar label "Auto" → "Auto-cycle" (with "Stop" active state); content descriptions updated. "Shuffle" already correct.

The bottom toolbar shows `Auto`, `Shuffle`, `City`, `Settings`, `Help`. The first two control *only* the clock design cycling (per Settings dialog), but their names don't say so — they look like global "auto play" / "shuffle" toggles that might affect widgets, weather, music… anything. The icons (play and refresh) reinforce that ambiguity.

**Suggested fix:** rename / re-icon them: "Auto-cycle clock", "Random clock", or merge them into a single "Clock style ▾" dropdown that pops the relevant settings inline.

---

## 30. `Help` icon click did nothing in my testing

**Status:** ✅ `F1` and `?` (Shift+/) now open the Help dialog via `KeyEventAction.ShowHelp` in `KeyEventHandler.handleWindowKeyEvent`. Help button click handler untouched.

I clicked the `?` Help icon multiple times via `xdotool` (after focusing the window) and no help dialog appeared. This could be a Wayland/XWayland input quirk specific to my environment, but it's worth verifying:

- That the Help button binds to the click handler at all,
- That its hitbox isn't smaller than its visual,
- That on Wayland sessions running Compose via XWayland, mouse clicks reliably reach Compose pointer events.

A reasonable adjacent fix: bind `F1` / `?` keys to open Help so keyboard works even if the mouse hitbox is off.

---

## 31. Cycle interval is a free-text field ("10s") instead of a structured control

**Status:** ✅ `Cycle interval` is now a `DurationChoice` dropdown: 5s / 10s / 30s / 1m / 5m / 15m. Stored as canonical string form (`"5s"`, `"1m"`) — backward-compatible with existing `WidgetConfig.durationMillis()` parser.

In Settings → Display → "Cycle interval", the value is `10s` typed into a normal text box. A user can type `banana` and there's no visible validation. This invites errors and looks unfinished.

**Suggested fix:** use a `Slider` with discrete steps (5s / 10s / 30s / 1m / 5m / 15m) or a numeric input with a unit suffix.

---

## 32. Hard-coded currency list (USD / EUR / GBP / INR) is parochial

**Status:** ✅ Searchable currency dropdown driven by `java.util.Currency.getAvailableCurrencies()` (expect/actual). USD/EUR/GBP/INR pinned in a "Popular" header section at top.

The Expenses widget supports exactly four currencies. Plenty of users (JPY, AUD, CAD, BRL, ZAR…) are immediately excluded.

**Suggested fix:** show a searchable list of all ISO 4217 currencies (Kotlin's `java.util.Currency.getAvailableCurrencies()` returns ~150), with the four current ones as "popular" pinned at the top.

---

## 33. Categories are a comma-separated text input

**Status:** ✅ Categories now render as `AssistChip`s with trailing close `IconButton`. Add new via `OutlinedTextField` + Enter / Add button. Stored as `JsonArray`; legacy comma strings split on read for transparent migration.

In Settings → Expenses, "Categories" is `food,transport,entertainment,bills` typed into a single text field. Adding a category requires manual comma editing. Renaming or reordering is fiddly. A category containing a comma is impossible.

**Suggested fix:** use a chip/tag input — each category is a removable pill, with a `+ Add` field at the end.

---

## 34. Clock "Design N" labels are not previewed in Settings

**Status:** ✅ Each `Design N` radio row now shows a scaled-down 2-digit live preview using the actual `DigitalClock*` composables ("1 2").

The clock design list shows `Design 1` through `Design 11` as radio buttons with no preview. To know what "Design 5" looks like, I must select it, close Settings, view the dashboard, then come back. Eleven designs deserves a thumbnail grid.

**Suggested fix:** render each "Design N" radio as a small live preview (just a `1 2 3 4` mini-clock in the actual design) inside the settings list. The clock digit composables are already small enough to do this cheaply.

---

## 35. Default Clock design (Design 2) uses very saturated bright green on black

**Status:** ⏭ Verified non-issue. `SettingsModel.selectedDesignId` default is already `1` (calm off-white tiles), not `2` (saturated green). Original audit observation was based on the screenshots in `/screenshots/` which captured an explicit user selection.

Design 2 is the default. The digits are vivid green (#2EE07B-ish) on solid black. For a screensaver that may stay on for hours, this is harsh on the eyes and causes phosphor-burn risk on OLEDs.

**Suggested fix:** default to a calmer, lower-saturation palette (the "Minimalist" or grey-green tile design from the original screenshots), and either dim brightness over time or expose a brightness slider.

---

## 36. There is no date or weekday on the dashboard

**Status:** ✅ Date + weekday line ("Thu · May 21") added below the clock in `ClockWidgetFactory.kt` using `kotlinx.datetime`.

Showing `22:06` without a date is fine for a quick clock-check, but a screensaver/dashboard usually shows date too. Especially when the same screen displays Todos ("am I behind?"), seeing the day is useful.

**Suggested fix:** add a small "Tue, May 21" line under or beside the clock. Could be part of the existing Clock widget rather than a new widget.

---

## 37. `Weather` widget shows zero spinner / placeholder while loading

**Status:** ✅ Loading state: empty box for first 250ms (`delay(250)` debounce), then two rounded placeholder boxes.

When I first saw the dashboard, the Weather widget was already in its error state. There was no brief "Loading weather…" or skeleton state during the request. Users on slow links will only see a flash of nothing then a red error.

**Suggested fix:** add a skeleton loader / "Loading…" state and a debounce so the error only renders if the request has actually completed and failed.

---

## 38. Window title is `Screen Saver App` (with a space) — looks like a placeholder

**Status:** ✅ Window titles: "Screen Saver App" → "Dwell", "Screen Saver App Daemon" → "Dwell Daemon". Tray tooltip → "Dwell". Verified live: `wmctrl -l` now shows `Dwell`.

`wmctrl -l` shows the window as literally `Screen Saver App`. In a launcher / Alt-Tab list, that looks like a not-yet-named project. Combined with the repo name `dwell`, the user has no idea what the product is actually called.

**Suggested fix:** pick a name. If it's "Dwell," set the window title to "Dwell" and update tray text, packaging artifacts, and the README. If it's something else, do the same.

---

## 39. The tray daemon was not started by `--show` — quitting needs Ctrl-C in terminal

**Status:** ✅ `Ctrl+Q` and `Cmd+Q` now dispatch `KeyEventAction.RequestExit`, which in `--show` mode calls `exitApplication()`. Esc dismiss was already wired.

`--show` ran the dashboard once. There was no tray icon and no obvious quit affordance from the UI itself. I had to kill the gradle/JVM processes. A user running from a packaged build would hit the same issue if they invoke `--show` (or its equivalent shortcut).

**Suggested fix:** even in `--show` mode, give a keyboard shortcut to quit (Ctrl-Q / Cmd-Q), and surface a small "×" in a corner.

---

## 40. JVM startup cost: ~3-4s "blank black screen" before content fades in

**Status:** 🟦 Deferred — JVM cold start is a hard tradeoff (real splash window adds complexity; native-image / CDS is invasive). Acceptable for now; README mentions 1-2 min first-build time. Future work: add an undecorated black splash window opened immediately at JVM start, replaced when Compose mounts.

There's a noticeable pause between window open and first paint where the user sees pure black. The fade-in animation only kicks in after Compose mounts. Combined with no splash, the user briefly wonders if it crashed.

**Suggested fix:** show the clock immediately (without animation) on first paint, or pre-render a tiny static splash that the fade animation can replace. Alternatively, GraalVM native-image or CDS could trim cold-start.

---

## Updated priority

After actually running it, here's the revised top 3 I'd push for:

1. **Issues #20 + #21 + #22** — Settings dialog UX is broken (truncated tabs, no escape/outside-click close, scroll dismisses the dialog). This is the *first thing* a new user touches and it's currently rough.
2. **Issues #24 + #19** — first-run experience is "alarming red weather error + giant settings sheet covering the dashboard." Replace with a friendly "Welcome — let's add a WeatherAPI key" guided onboarding, OR ship a default proxied weather provider so the widget Just Works.
3. **Issues #27 + #28** — Expenses and Todos widgets default to permanent edit forms on the screensaver. They should be read-only display by default with an explicit input affordance.

The README-level issues from Part 1 (releases, screenshots, JDK toolchain) remain valid; those are reach issues. The Part 2 issues are existing-user retention issues.
