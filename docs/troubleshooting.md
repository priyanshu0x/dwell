# Troubleshooting

Common issues and how to work around them. If something here doesn't match what you're seeing, please file an issue.

> Most commands assume the Dwell CLI: `./scripts/dwell <subcommand>` (or `scripts\dwell.cmd <subcommand>` on Windows). Run `./scripts/dwell help` for the full list. If you've run `./scripts/dwell install`, you can drop the `./scripts/` prefix and just type `dwell …`.

## "No JDK 21+ found" on first run

The CLI auto-installs Temurin JDK 21 to `~/jdks/` on the first launch. If the install fails:

- **Slow / failing download** — the JDK is ~200 MB from `api.adoptium.net`. On a flaky connection, retry once. You can also pre-install via SDKMAN (`sdk install java 21-tem`), Homebrew (`brew install --cask temurin@21`), or your distro's package manager. The CLI auto-detects any JDK 21 under `~/jdks/jdk-21*`, `/usr/lib/jvm/`, `/opt/`, or wherever `JAVA_HOME` points.
- **Unsupported architecture** — currently auto-install supports `x86_64` / `amd64` and `aarch64` / `arm64` on Linux and macOS, and `x64` on Windows. On other architectures (e.g. armv7), install a JDK 21 manually and set `JAVA_HOME`.
- **Behind a corporate proxy** — `curl` honors `https_proxy`. If your proxy intercepts TLS, install the JDK manually and set `JAVA_HOME`.

Confirm with `./scripts/dwell status` — it prints the detected JDK path and the settings file location.

## Tray icon does not appear (especially on GNOME)

GNOME removed legacy system-tray (AppIndicator) support several releases ago. If you're on stock GNOME (Ubuntu, Fedora Workstation, Debian GNOME, etc.) and don't see the Dwell tray icon, install the **AppIndicator and KStatusNotifierItem Support** GNOME Shell extension:

- Debian / Ubuntu: `sudo apt install gnome-shell-extension-appindicator` and then enable it via the Extensions app or `gnome-extensions enable ubuntu-appindicators@ubuntu.com` (Ubuntu) / `appindicatorsupport@rgcjonas.gmail.com` (the upstream extension on other distros).
- Fedora: `sudo dnf install gnome-shell-extension-appindicator` then log out and back in.
- Other GNOME setups: install the [AppIndicator extension from extensions.gnome.org](https://extensions.gnome.org/extension/615/appindicator-support/).

After enabling the extension, log out and log back in (or restart GNOME Shell on X11 with `Alt+F2`, `r`, Enter) so the tray icon appears.

KDE Plasma, XFCE, Cinnamon, MATE, and Budgie ship a tray out of the box and should not need this step.

If you cannot get a tray icon to show, you can still launch the dashboard manually with `./scripts/dwell show` — but you won't be able to quit via the tray. Close the launching terminal or `pkill -f MainKt`.

## Dashboard never triggers automatically

The dashboard waits for the OS to report user idle time. If it never fades in:

1. **Check the idle threshold** in Tray → Settings → Triggers. If it's set too high, lower it.
2. **Linux only — install an idle detector.** On Linux, idle time is read via `gdbus` (GNOME / Wayland), `libXss` (X11), or `xprintidle` (X11 fallback). If none are installed, the daemon has no way to detect idle. Install: `sudo apt install libxss1 xprintidle`.
3. **Force-open** from the tray (Tray → Show now) or run `./scripts/dwell show` to confirm the dashboard itself works. If `show` opens a working dashboard, the issue is idle detection, not the renderer.
4. **Watch the console** when launching with `./scripts/dwell daemon` — the app logs which idle detector it selected on startup.

## Weather widget shows nothing / "Couldn't load"

The weather widget requires a free [WeatherAPI.com](https://www.weatherapi.com/) API key:

1. Sign up at [weatherapi.com](https://www.weatherapi.com/) and copy your key.
2. Open Dwell → Settings → Widgets → Weather → paste the key, **or** set the `WEATHERAPI` environment variable before launching the app.
3. Reload widgets: tray → Reload widgets, or press `Ctrl+R` in the dashboard.

If you have a key configured and the widget still errors:

- Confirm the key is valid by hitting `https://api.weatherapi.com/v1/current.json?key=YOUR_KEY&q=London` in a browser.
- Check that the configured city is spelled correctly in Settings → Widgets → Weather. (City is widget-scoped now, not a global setting.)
- Confirm the machine has internet access and is not behind a proxy that blocks `api.weatherapi.com`.

## Mode / variant doesn't change when I press M / V

- Make sure the dashboard window has focus. On some Wayland/XWayland setups, focus on a fullscreen alwaysOnTop window can be flaky — click anywhere on the dashboard once before pressing the shortcut.
- The tray menu offers the same controls if keys aren't working: **Mode** submenu cycles Cinematic / Ambient / Console; **Variant** cycles within the active mode.

## My settings reset on every launch

Settings live in `~/.screensaver/settings.json`. Check that the file exists and is writable. If it's owned by `root` (because you ran the app with `sudo` once), `sudo chown -R $USER:$USER ~/.screensaver`.

## "Edit Layout" mode in Console keeps re-arranging tiles when I drag

The 12×6 grid snaps tiles to cell boundaries; nearby tiles reflow when you drop. Press `L` to exit Edit Layout mode when done. To start over, click **Reset layout** in Settings → Widgets.

## Where are my settings stored?

All user state lives under `~/.screensaver/`:

```text
~/.screensaver/settings.json             # user settings (mode, variant, idle timeout, etc.)
~/.screensaver/widgets/                  # installed third-party widgets (JARs and declarative folders)
~/.screensaver/widget-data/<widget-id>/  # per-widget scoped storage
```

Secrets (e.g. the WeatherAPI key) are stored in the OS keychain when available (Windows Credential Manager / Linux libsecret via `secret-tool`) and fall back to an obfuscated local file when no keychain is reachable.

## How do I reset everything?

Quit the app first (tray → Quit, or close the terminal), then remove the state directory:

```sh
rm -rf ~/.screensaver/
```

On Windows the equivalent directory is `%USERPROFILE%\.screensaver\`.

The next launch will recreate `settings.json` with defaults. Note that this also removes any installed third-party widgets and any per-widget data they stored. If you want to keep your installed widgets, move `~/.screensaver/widgets/` aside before deleting.

Secrets stored in the OS keychain are **not** removed by deleting `~/.screensaver/`. To clear them:

- **Windows** — open *Credential Manager* → *Windows Credentials* and remove entries named `dwell:*` (legacy `ScreenSaverApp:*` may also exist).
- **Linux (libsecret)** — `secret-tool clear application Dwell` or use *Seahorse* / *KWalletManager* to remove the relevant entries.

## How do I completely uninstall?

If you ran `./scripts/dwell register` to autostart at login:

```sh
./scripts/dwell unregister   # remove autostart + systemd/LaunchAgent
./scripts/dwell uninstall    # remove the `dwell` symlink from ~/.local/bin (if installed)
rm -rf ~/.screensaver/       # remove all settings + installed widgets + per-widget data
```

Then delete the repo clone if you want to remove the code too.
