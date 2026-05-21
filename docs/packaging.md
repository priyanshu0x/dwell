# Packaging

## Windows

Build the native app and full `.scr` bundle:

```powershell
.\gradlew.bat --no-daemon --no-configuration-cache :composeApp:packageScrZip
```

The zip is written to:

```text
composeApp/build/compose/binaries/main/ScreenSaverApp-scr-1.0.0.zip
```

The `.scr` launcher requires the adjacent `runtime` and `app` folders. Do not copy only `Screen Saver App.scr`.

Install for the current user:

```powershell
.\composeApp\dist\windows\install-screensaver.ps1 -OpenSettings
```

The installer copies the full bundle to:

```text
%LOCALAPPDATA%\ScreenSaverApp\Screen Saver App
```

Then it sets `HKCU\Control Panel\Desktop\SCRNSAVE.EXE` to the installed `.scr`.

To smoke-test only the copy/validation path without changing screen saver registry settings:

```powershell
.\composeApp\dist\windows\install-screensaver.ps1 -InstallDir "$env:TEMP\ScreenSaverAppInstallTest" -NoRegister
```

## Linux

Build the current OS package:

```sh
./gradlew --no-daemon --no-configuration-cache :composeApp:packageDistributionForCurrentOS
```

After installing the package, register the user daemon:

```sh
APP_BIN=/usr/bin/screensaver-app composeApp/dist/linux/install.sh
```

The script writes:

- `~/.config/autostart/screensaver-app.desktop`
- `~/.config/systemd/user/screensaver-app.service`

If `systemctl --user` is available, it reloads the user daemon and starts the service.
