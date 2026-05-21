# Troubleshooting

Common issues and how to work around them. If something here doesn't match what you're seeing, please file an issue.

## Tray icon does not appear (especially on GNOME)

GNOME removed legacy system-tray (AppIndicator) support several releases ago. If you're on stock GNOME (Ubuntu, Fedora Workstation, Debian GNOME, etc.) and don't see the Screen Saver App tray icon, install the **AppIndicator and KStatusNotifierItem Support** GNOME Shell extension:

- Debian / Ubuntu: `sudo apt install gnome-shell-extension-appindicator` and then enable it via the Extensions app or `gnome-extensions enable ubuntu-appindicators@ubuntu.com` (Ubuntu) / `appindicatorsupport@rgcjonas.gmail.com` (the upstream extension on other distros).
- Fedora: `sudo dnf install gnome-shell-extension-appindicator` then log out and back in.
- Other GNOME setups: install the [AppIndicator extension from extensions.gnome.org](https://extensions.gnome.org/extension/615/appindicator-support/).

After enabling the extension, log out and log back in (or restart GNOME Shell on X11 with `Alt+F2`, `r`, Enter) so the tray icon appears.

KDE Plasma, XFCE, Cinnamon, MATE, and Budgie ship a tray out of the box and should not need this step.

If you cannot get a tray icon to show, you can still launch the dashboard manually with `--show` on the command line — but you won't be able to quit via the tray. Use `pkill -f screensaver-app` or close the launching terminal.

## Dashboard never triggers automatically

The dashboard waits for the OS to report user idle time. If it never fades in:

1. **Check the idle threshold** in Tray → Settings → Activation. If it's set to "Never" or a very high value, lower it.
2. **Linux only — install an idle detector.** On Linux, idle time is read via `gdbus` (GNOME / Wayland), `libXss` (X11), or `xprintidle` (X11 fallback). If none are installed, the daemon has no way to detect idle. See the *Linux idle detection* section in the main README for per-distro install lines.
3. **Force-open** from the tray (Tray → Show) or run with `--show` to confirm the dashboard itself works. If `--show` opens a working dashboard, the issue is idle detection, not the renderer.
4. **Watch the console** when launching with `./gradlew :composeApp:run` — the app logs which idle detector it selected on startup.

## Weather widget shows nothing / "Error loading weather data"

The weather widget requires a free [WeatherAPI.com](https://www.weatherapi.com/) API key:

1. Sign up at [weatherapi.com](https://www.weatherapi.com/) and copy your key.
2. Paste it into **Tray → Settings → Display → Weather**, **or** set the `WEATHERAPI` environment variable before launching the app.
3. Restart the dashboard (Tray → Show, or wait for the next idle trigger).

If you have a key configured and the widget still errors:

- Confirm the key is valid by hitting `https://api.weatherapi.com/v1/current.json?key=YOUR_KEY&q=London` in a browser.
- Check that the configured city / location is spelled correctly in Settings → Display → Weather.
- Confirm the machine has internet access and is not behind a proxy that blocks `api.weatherapi.com`.

## Where are my settings stored?

All user state lives under `~/.screensaver/`:

```text
~/.screensaver/settings.json          # user settings (idle threshold, theme, weather city, etc.)
~/.screensaver/widgets/               # installed third-party widgets (JARs and declarative folders)
~/.screensaver/widget-data/<widget-id>/  # per-widget scoped storage
```

Secrets (e.g. the WeatherAPI key) are stored in the OS keychain when available (Windows Credential Manager / Linux libsecret via `secret-tool`) and fall back to an obfuscated local file when no keychain is reachable.

## How do I reset everything?

Quit the app first (Tray → Quit), then remove the state directory:

```sh
rm -rf ~/.screensaver/
```

On Windows the equivalent directory is `%USERPROFILE%\.screensaver\`.

The next launch will recreate `settings.json` with defaults. Note that this also removes any installed third-party widgets and any per-widget data they stored. If you want to keep your installed widgets, move `~/.screensaver/widgets/` aside before deleting.

Secrets stored in the OS keychain are **not** removed by deleting `~/.screensaver/`. To clear them:

- **Windows** — open *Credential Manager* → *Windows Credentials* and remove entries named `ScreenSaverApp:*`.
- **Linux (libsecret)** — `secret-tool clear application ScreenSaverApp` or use *Seahorse* / *KWalletManager* to remove the relevant entries.
