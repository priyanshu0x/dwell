# Screen Saver App

Idle-triggered desktop dashboard and screen saver built with Kotlin, Compose Multiplatform, and a public widget API.

The app runs as a tray daemon on Windows/Linux, opens a fullscreen dashboard after OS idle time, and renders built-in or user-installed widgets from `~/.screensaver/widgets`.

## Features

- Idle-triggered fullscreen dashboard with fade in/out.
- Tray daemon with show, settings, widget reload, and quit actions.
- Windows `.scr`/screensaver launch support.
- Built-in widgets implemented through the same API as third-party widgets:
  - Clock
  - Weather
  - Todos
  - Expenses
- Tier 1 widgets: JVM JARs loaded with `ServiceLoader`.
- Tier 2 widgets: `widget.yaml` + file/http/command data source.
- Persistent settings at `~/.screensaver/settings.json`.
- Scoped widget storage at `~/.screensaver/widget-data/<widget-id>/`.
- Secret storage through Windows Credential Manager, Linux `secret-tool`/libsecret, or obfuscated fallback file.
- Optional backend sync foundation for todos and expenses with local outbox retry.
- Native packaging for MSI/EXE, Debian packages, and a Windows `.scr` bundle.

## Requirements

- JDK 21.
- Windows or Linux.
- Weather widget: set the WeatherAPI.com key in Settings -> Display -> Weather, or provide `WEATHERAPI` in the process environment.
- Linux idle detection works best with `gdbus` on GNOME Wayland, XScreenSaver/libXss on X11, or `xprintidle` as a fallback.
- Linux secret storage uses `secret-tool` when available and falls back to a local obfuscated file when no OS keychain is reachable.

## Run

Windows:

```powershell
.\gradlew.bat :composeApp:run
```

Unix shell syntax:

```sh
./gradlew :composeApp:run
```

Launch modes:

```text
--daemon   default; tray daemon, no dashboard until idle/show
--show     open dashboard once
/s         Windows screen saver show
/p         Windows screen saver preview no-op
/c         open dashboard with settings
```

Hot reload:

```powershell
.\gradlew.bat :composeApp:runHot
```

## Test

```powershell
.\gradlew.bat --no-daemon --no-configuration-cache :composeApp:jvmTest
```

Build sample widget:

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

Widget authoring starts in [docs/widget-authors.md](docs/widget-authors.md).

Filesystem discovery location:

```text
~/.screensaver/widgets/
```

Supported widget types:

- JAR widget implementing `com.droidslife.screensaver.widget.api.WidgetFactory`.
- Declarative folder widget containing `widget.yaml`.

Installed widgets can be reloaded from the tray menu or Settings -> Widgets.

## Project Structure

```text
composeApp/                  host app
widget-api/                  public widget API
samples/sample-kotlin-widget Tier 1 sample
samples/sample-declarative-widget Tier 2 sample
docs/                        packaging and widget author docs
```

## Current Limits

- v1 targets Windows and Linux.
- Third-party JAR widgets are not sandboxed; only run code you trust.
- Backend sync uses a generic JSON/bearer-token client until a concrete backend contract is supplied.
