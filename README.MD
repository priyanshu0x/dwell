# Screen Saver App

> Dwell is the codename for this project; the product itself is called Screen Saver App. The repository name and the user-facing name will line up in a future release.

Idle-triggered desktop dashboard and screen saver built with Kotlin, Compose Multiplatform, and a public widget API.

The app runs as a tray daemon on Windows and Linux, fades in a fullscreen dashboard after the OS has been idle for a while, and renders built-in or user-installed widgets from `~/.screensaver/widgets/`.

![Dashboard with clock and weather](screenshots/Screenshot%202025-03-31%20164121.png)

> The screenshots in this repository (dated 2025-03-31) may show an older UI than the current build. They are kept for reference until a fresh set is captured.

## Install

Prebuilt installers (MSI / EXE for Windows, DEB for Linux, plus a Windows `.scr` bundle) are published on the project releases page:

- Releases: `<TODO releases URL>`

After downloading:

- **Windows MSI / EXE** — run the installer, then launch "Screen Saver App" from the Start menu. To register it as the system screen saver, run `install-screensaver.ps1` from the install directory (see [docs/packaging.md](docs/packaging.md)).
- **Windows `.scr` bundle** — unzip the `ScreenSaverApp-scr-*.zip` somewhere persistent (the `.scr`, `app/`, and `runtime/` folders must stay together), then run the bundled `install-screensaver.ps1`. See [docs/packaging.md](docs/packaging.md) for details.
- **Linux DEB** — install with `sudo apt install ./screensaver-app_<version>_amd64.deb`, then register the user daemon with `composeApp/dist/linux/install.sh` from the source tree (or the equivalent script shipped with the package).

If you would rather build it yourself, jump to [Build from source](#build-from-source).

## First run

When the app starts in its default `--daemon` mode:

1. A tray icon (Screen Saver App) appears in the system tray / notification area.
2. The dashboard does **not** open immediately — the daemon waits for the OS to report idle time.
3. After the configured idle threshold elapses, the fullscreen dashboard fades in and starts displaying widgets (clock, weather, todos, expenses, plus any user-installed widgets).
4. Moving the mouse, pressing a key, or pressing Esc dismisses the dashboard and the daemon returns to waiting.

You can also force the dashboard open at any time:

- Tray menu → **Show** opens it on demand.
- `--show` on the command line opens it once and exits.

Settings (idle threshold, clock style, weather location, widget toggles, etc.) live in **Tray → Settings** and persist to `~/.screensaver/settings.json`.

![Settings dialog](screenshots/Screenshot%202025-03-31%20163721.png)

### Controls

While the dashboard is visible:

- **Esc** — dismiss the dashboard.
- **Mouse movement** — dismiss the dashboard (this can be disabled in Settings).
- **Any key** — dismiss the dashboard.

To stop the daemon entirely, right-click the tray icon and choose **Quit**. If the tray icon is missing on your desktop environment, see [docs/troubleshooting.md](docs/troubleshooting.md).

### Weather setup

The weather widget uses [WeatherAPI.com](https://www.weatherapi.com/), which requires a **free** API key:

1. Sign up at [weatherapi.com](https://www.weatherapi.com/) and copy your key from the dashboard.
2. Open **Tray → Settings → Display → Weather** and paste the key into the field, **or** set the `WEATHERAPI` environment variable before launching the app.
3. Optionally change the city.

Without a key, the weather widget will not load. Your key is stored in the OS keychain when available (Windows Credential Manager / Linux libsecret) and falls back to an obfuscated local file otherwise.

## Privacy

- **No telemetry.** The app does not collect or report usage data.
- **Network access is opt-in.** Out of the box, the only network call is to WeatherAPI.com — and only if you configure a key.
- **Backend sync is OFF by default.** The optional todos/expenses sync foundation exists but is inert unless a backend URL is configured. **No data leaves your machine** unless you explicitly point the app at a backend.
- **Local storage only.** Settings live at `~/.screensaver/settings.json`, widget data at `~/.screensaver/widget-data/<widget-id>/`, and installed widgets at `~/.screensaver/widgets/`.

## Build from source

Requirements:

- **JDK 21** (this is a hard requirement — the build fails on JDK 17 or earlier). On most Linux distros the default `java` is still JDK 8/11/17, so you will need to install JDK 21 separately. Recommended sources: [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=21), or `sdk install java 21-tem` if you use [SDKMAN!](https://sdkman.io/).
- Point Gradle at JDK 21 by setting `JAVA_HOME` (e.g. `export JAVA_HOME=/path/to/jdk-21`) before invoking the wrapper. The Gradle wrapper itself will then download the rest of the toolchain.

Then run the dashboard once:

Windows:

```powershell
.\gradlew.bat :composeApp:run --args="--show"
```

Unix shell:

```sh
./gradlew :composeApp:run --args="--show"
```

> The first build downloads roughly a few hundred MB of dependencies and takes about 1–2 minutes on a modern machine before the window appears. Gradle does not stream much output during this — it has not hung.
>
> Running `:composeApp:run` with no `--args` starts the default tray daemon. In daemon mode the dashboard intentionally stays hidden until the OS idle threshold is reached or the tray menu's **Show** action is used.

Launch modes:

```text
--daemon   default; tray daemon, no dashboard until idle/show
--show     open dashboard once
/s         Windows screen saver show
/p         Windows screen saver preview no-op
/c         open dashboard with settings
```

Hot reload during development:

```powershell
.\gradlew.bat :composeApp:runHot
```

Tests:

```powershell
.\gradlew.bat --no-daemon --no-configuration-cache :composeApp:jvmTest
```

Build the sample widget:

```powershell
.\gradlew.bat --no-daemon --no-configuration-cache :samples:sample-kotlin-widget:jvmJar
```

## Package

Windows `.scr` bundle:

```powershell
.\gradlew.bat --no-daemon --no-configuration-cache :composeApp:packageScrZip
```

Native package for the current OS:

```powershell
.\gradlew.bat --no-daemon --no-configuration-cache :composeApp:packageDistributionForCurrentOS
```

See [docs/packaging.md](docs/packaging.md) for install details.

Release outputs are written under:

```text
composeApp/build/compose/binaries/main/ScreenSaverApp-scr-<version>.zip
composeApp/build/compose/binaries/main/msi/
composeApp/build/compose/binaries/main/exe/
composeApp/build/compose/binaries/main/deb/
```

## Widgets

Widget authoring starts in [docs/widget-authors.md](docs/widget-authors.md). Working samples are in [`samples/sample-kotlin-widget`](samples/sample-kotlin-widget) (JAR / Tier 1) and [`samples/sample-declarative-widget`](samples/sample-declarative-widget) (YAML / Tier 2).

Filesystem discovery location:

```text
~/.screensaver/widgets/
```

Supported widget types:

- JAR widget implementing `com.droidslife.screensaver.widget.api.WidgetFactory`.
- Declarative folder widget containing `widget.yaml`.

Installed widgets can be reloaded from the tray menu or **Settings → Widgets**.

## Project structure

```text
composeApp/                       host app
widget-api/                       public widget API
samples/sample-kotlin-widget/     Tier 1 sample (JAR)
samples/sample-declarative-widget/ Tier 2 sample (YAML)
docs/                             packaging, widget-author, troubleshooting docs
screenshots/                      reference screenshots
```

## Limits / requirements

- v1 targets Windows and Linux. macOS is not supported.
- Building from source requires **JDK 21**.
- Third-party JAR widgets are **not** sandboxed — they run with full JVM permissions. Only install widgets from sources you trust.
- Backend sync uses a generic JSON / bearer-token client until a concrete backend contract is supplied.

### Linux idle detection

The dashboard uses external utilities to read idle time on Linux. Depending on your session, install one or more of these:

- **GNOME / Wayland** — uses `gdbus` (shipped with `glib2`, usually preinstalled).
- **X11 with XScreenSaver / libXss**:
  - Debian / Ubuntu: `sudo apt install libxss1`
  - Fedora / RHEL: `sudo dnf install libXScrnSaver`
  - Arch: `sudo pacman -S libxss`
- **`xprintidle` fallback** (any X11):
  - Debian / Ubuntu: `sudo apt install xprintidle x11-utils`
  - Fedora: `sudo dnf install xprintidle xorg-x11-utils`
  - Arch: `sudo pacman -S xprintidle xorg-xprop`

If none of these are present the dashboard will never fade in automatically; you can still trigger it via **Tray → Show**.

## Troubleshooting

If the tray icon never shows up, the dashboard never triggers, the weather is blank, or you want to reset all settings — see [docs/troubleshooting.md](docs/troubleshooting.md).
