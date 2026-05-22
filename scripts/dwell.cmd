@echo off
:: Dwell CLI — Windows launch helper.
:: Auto-installs Temurin JDK 21 to %USERPROFILE%\jdks\ if missing, then runs the requested mode.
::
:: Usage:
::   scripts\dwell.cmd show     -- open dashboard once
::   scripts\dwell.cmd daemon   -- run as tray daemon
::   scripts\dwell.cmd config   -- open dashboard with Settings pre-opened
::   scripts\dwell.cmd dev      -- hot-reload dev mode
::   scripts\dwell.cmd help     -- show this help
setlocal enableextensions

set "HERE=%~dp0"
set "ROOT=%HERE%.."
pushd "%ROOT%"

set "CMD=%~1"
if "%CMD%"=="" set "CMD=help"

if /I "%CMD%"=="help" goto :usage
if /I "%CMD%"=="-h"   goto :usage
if /I "%CMD%"=="--help" goto :usage

call :bootstrap_java || goto :end

if /I "%CMD%"=="show" (
    call :first_build_hint
    echo Dwell — opening dashboard.
    call :cheatsheet
    echo.
    call gradlew.bat :composeApp:run --args="--show" --console=plain
    goto :end
)
if /I "%CMD%"=="daemon" (
    call :first_build_hint
    echo Dwell — starting tray daemon. Look for the icon in your system tray.
    echo   Dashboard auto-opens after the idle timeout ^(Settings ^> Triggers^).
    echo.
    call gradlew.bat :composeApp:run --args="--daemon" --console=plain
    goto :end
)
if /I "%CMD%"=="config" (
    call :first_build_hint
    echo Dwell — opening dashboard with Settings.
    echo   Tip: add a WeatherAPI key to enable the weather widget.
    echo.
    call gradlew.bat :composeApp:run --args="/c" --console=plain
    goto :end
)
if /I "%CMD%"=="dev" (
    call :first_build_hint
    echo Dwell — Compose Hot Reload dev mode.
    echo.
    call gradlew.bat :composeApp:runHot --console=plain
    goto :end
)
if /I "%CMD%"=="register" (
    :: Drop a Startup-folder shortcut that runs `dwell.cmd daemon` at login.
    set "STARTUP=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
    powershell -NoProfile -Command ^
        "$s = (New-Object -ComObject WScript.Shell).CreateShortcut('%STARTUP%\Dwell.lnk');" ^
        "$s.TargetPath = '%HERE%dwell.cmd';" ^
        "$s.Arguments = 'daemon';" ^
        "$s.WorkingDirectory = '%ROOT%';" ^
        "$s.IconLocation = '%ROOT%\composeApp\desktopAppIcons\WindowsIcon.ico';" ^
        "$s.Save();"
    if errorlevel 1 (
        echo ✗ Failed to write Startup shortcut.
        exit /b 1
    )
    echo ✓ Wrote Startup shortcut: %STARTUP%\Dwell.lnk
    echo   To stop and remove later, run: scripts\dwell.cmd unregister
    goto :end
)
if /I "%CMD%"=="unregister" (
    set "STARTUP=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
    if exist "%STARTUP%\Dwell.lnk" (
        del "%STARTUP%\Dwell.lnk"
        echo ✓ Removed Startup shortcut.
    ) else (
        echo   No Startup shortcut found.
    )
    goto :end
)
if /I "%CMD%"=="install" (
    if not defined DWELL_BIN_DIR set "DWELL_BIN_DIR=%USERPROFILE%\bin"
    if not exist "%DWELL_BIN_DIR%" mkdir "%DWELL_BIN_DIR%"
    > "%DWELL_BIN_DIR%\dwell.cmd" echo @echo off
    >> "%DWELL_BIN_DIR%\dwell.cmd" echo call "%HERE%dwell.cmd" %%*
    echo ✓ Installed shim: %DWELL_BIN_DIR%\dwell.cmd ^→ %HERE%dwell.cmd
    echo   Make sure %DWELL_BIN_DIR% is on your PATH so `dwell show` works anywhere.
    goto :end
)
if /I "%CMD%"=="uninstall" (
    if not defined DWELL_BIN_DIR set "DWELL_BIN_DIR=%USERPROFILE%\bin"
    if exist "%DWELL_BIN_DIR%\dwell.cmd" (
        del "%DWELL_BIN_DIR%\dwell.cmd"
        echo ✓ Removed: %DWELL_BIN_DIR%\dwell.cmd
    ) else (
        echo   No shim at %DWELL_BIN_DIR%\dwell.cmd — nothing to remove.
    )
    echo   ^(Your settings + widget data at %%USERPROFILE%%\.screensaver remain untouched.^)
    goto :end
)
if /I "%CMD%"=="status" (
    echo Dwell — status
    echo   Project root: %ROOT%
    tasklist /FI "IMAGENAME eq java.exe" /V | findstr /I "MainKt" >nul 2>nul
    if %ERRORLEVEL%==0 ( echo   Running: yes ) else ( echo   Running: no )
    if defined JAVA_HOME ( echo   JDK 21:  %JAVA_HOME% ) else ( echo   JDK 21:  not detected )
    if exist "%USERPROFILE%\.screensaver\settings.json" (
        echo   Settings: %USERPROFILE%\.screensaver\settings.json
    ) else (
        echo   Settings: not yet written ^(first run hasn't completed^)
    )
    goto :end
)

echo Unknown command: %CMD%
echo.
goto :usage

:bootstrap_java
:: Try JAVA_HOME first
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        for /f "tokens=3 delims= " %%v in ('"%JAVA_HOME%\bin\java.exe" -version 2^>^&1 ^| findstr /R "version"') do (
            set "JVER=%%v"
        )
        :: JVER looks like "21.0.1" or "21" — extract major version
        for /f "tokens=1 delims=." %%m in ("%JVER:"=%") do set "JMAJ=%%m"
        if %JMAJ% GEQ 21 goto :have_java
    )
)
:: Look under %USERPROFILE%\jdks\jdk-21*
for /d %%d in ("%USERPROFILE%\jdks\jdk-21*") do (
    if exist "%%d\bin\java.exe" (
        set "JAVA_HOME=%%d"
        goto :have_java
    )
)
:: Auto-install via PowerShell
echo. ▶ No JDK 21 found. Installing Temurin JDK 21 to %USERPROFILE%\jdks\ ^(~200MB, one-time^)…
if not exist "%USERPROFILE%\jdks" mkdir "%USERPROFILE%\jdks"
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$url='https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse';" ^
    "$zip='%USERPROFILE%\jdks\temurin21.zip';" ^
    "Invoke-WebRequest -Uri $url -OutFile $zip;" ^
    "Expand-Archive -Path $zip -DestinationPath '%USERPROFILE%\jdks\' -Force;" ^
    "Remove-Item $zip"
if errorlevel 1 (
    echo ✗ Download / extraction failed. Install JDK 21 manually and re-run.
    exit /b 1
)
for /d %%d in ("%USERPROFILE%\jdks\jdk-21*") do set "JAVA_HOME=%%d"
if not defined JAVA_HOME (
    echo ✗ JDK extracted but not found. Set JAVA_HOME manually.
    exit /b 1
)
echo ✓ JDK 21 installed at %JAVA_HOME%

:have_java
set "PATH=%JAVA_HOME%\bin;%PATH%"
goto :eof

:first_build_hint
if not exist "composeApp\build\libs" echo ▶ First build — fetching dependencies ^(~1-2 min on a fast connection^).
goto :eof

:cheatsheet
echo   M       cycle mode ^(Cinematic ^> Ambient ^> Console^)
echo   V       cycle variant
echo   Esc     dismiss
echo   Ctrl+,  Settings    F1 / ?  Help    Ctrl+Q  quit
goto :eof

:usage
echo Dwell — screensaver-with-widgets
echo.
echo Usage: scripts\dwell.cmd ^<command^>
echo.
echo Commands:
echo   show       Open the dashboard once.
echo   daemon     Run as a tray daemon ^(dashboard appears after idle timeout^).
echo   config     Open the dashboard with Settings pre-opened.
echo   dev        Run with Compose Hot Reload ^(for development^).
echo   register   Register Dwell to run at login ^(Startup folder shortcut^).
echo   unregister Remove the Startup shortcut.
echo   install    Drop a `dwell.cmd` shim into %%USERPROFILE%%\bin.
echo   uninstall  Remove the shim ^(settings stay^).
echo   status     Show whether Dwell is running + which JDK / settings is in use.
echo   help       Show this help.
echo.
echo First run will auto-install JDK 21 to %%USERPROFILE%%\jdks\.
goto :end

:end
popd
endlocal
