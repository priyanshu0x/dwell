@echo off
:: Dwell CLI - Windows launch helper.
:: Auto-installs Temurin JDK 21 to %USERPROFILE%\jdks\ if missing, then runs the requested mode.
::
:: Usage:
::   scripts\dwell.cmd show     -- open dashboard and keep Dwell in the tray
::   scripts\dwell.cmd daemon   -- run as tray daemon
::   scripts\dwell.cmd config   -- open dashboard with Settings pre-opened
::   scripts\dwell.cmd dev      -- hot-reload dev mode
::   scripts\dwell.cmd help     -- show this help
setlocal enableextensions enabledelayedexpansion

set "HERE=%~dp0"
pushd "%HERE%.." || (
    echo ERROR: Failed to enter project root: %HERE%..
    exit /b 1
)
set "ROOT=%CD%"

set "CMD=%~1"
if /I "%CMD%"=="--debug" (
    set "DWELL_DEBUG=1"
    set "CMD=%~2"
)
if /I "%CMD%"=="--hidden" (
    set "DWELL_HIDDEN=1"
    set "CMD=%~2"
)
if "%CMD%"=="" set "CMD=help"
if /I "%~2"=="--debug" set "DWELL_DEBUG=1"
if /I "%~2"=="-v" set "DWELL_DEBUG=1"
if /I "%~2"=="--hidden" set "DWELL_HIDDEN=1"
set "DWELL_EXIT=0"

if /I "%CMD%"=="help" goto :usage
if /I "%CMD%"=="-h"   goto :usage
if /I "%CMD%"=="--help" goto :usage
if /I "%CMD%"=="install" (
    call :install
    goto :end
)
if /I "%CMD%"=="uninstall" (
    call :uninstall
    goto :end
)
if /I "%CMD%"=="register" (
    call :register
    goto :end
)
if /I "%CMD%"=="unregister" (
    call :unregister
    goto :end
)
if /I "%CMD%"=="version" (
    call :version
    goto :end
)
if /I "%CMD%"=="status" (
    call :status
    goto :end
)

if defined DWELL_HIDDEN if not defined DWELL_DETACHED (
    if /I "%CMD%"=="show" goto :detach
    if /I "%CMD%"=="daemon" goto :detach
    if /I "%CMD%"=="config" goto :detach
    if /I "%CMD%"=="dev" goto :detach
)

call :bootstrap_java || goto :end

if /I "%CMD%"=="show" (
    call :first_build_hint
    echo Dwell - opening dashboard.
    echo   Esc hides it to the tray; use the tray menu to show or quit.
    call :cheatsheet
    echo.
    call :run_gradle --no-daemon :composeApp:run --args=--show
    goto :end
)
if /I "%CMD%"=="daemon" (
    call :first_build_hint
    echo Dwell - starting tray daemon. Look for the icon in your system tray.
    echo   Dashboard auto-opens after the idle timeout ^(Settings ^> Triggers^).
    echo.
    call :run_gradle :composeApp:run --args=--daemon
    goto :end
)
if /I "%CMD%"=="config" (
    call :first_build_hint
    echo Dwell - opening dashboard with Settings.
    echo   Tip: add a WeatherAPI key to enable the weather widget.
    echo.
    call :run_gradle :composeApp:run --args=/c
    goto :end
)
if /I "%CMD%"=="dev" (
    call :first_build_hint
    echo Dwell - Compose Hot Reload dev mode.
    echo.
    call :run_gradle --no-configuration-cache :composeApp:runHot
    goto :end
)
echo Unknown command: %CMD%
echo.
goto :usage

:bootstrap_java
:: Look under %USERPROFILE%\jdks\jdk-21*
for /d %%d in ("%USERPROFILE%\jdks\jdk-21*") do (
    if exist "%%d\bin\java.exe" (
        set "JAVA_HOME=%%d"
        goto :have_java
    )
)
for /d %%d in ("%USERPROFILE%\.jdks\*21*") do (
    if exist "%%d\bin\java.exe" (
        set "JAVA_HOME=%%d"
        goto :have_java
    )
)
for /d %%d in ("%ProgramFiles%\Eclipse Adoptium\jdk-21*" "%ProgramFiles%\Java\jdk-21*") do (
    if exist "%%d\bin\java.exe" (
        set "JAVA_HOME=%%d"
        goto :have_java
    )
)
:: Fall back to JAVA_HOME after known JDK 21 locations. Some shells leave a
:: trailing space in JAVA_HOME, which makes the version probe noisy in cmd.exe.
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" goto :have_java
)
:: Auto-install via PowerShell
echo. No JDK 21 found. Installing Temurin JDK 21 to %USERPROFILE%\jdks\ ^(~200MB, one-time^)...
if not exist "%USERPROFILE%\jdks" mkdir "%USERPROFILE%\jdks"
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$url='https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse';" ^
    "$zip='%USERPROFILE%\jdks\temurin21.zip';" ^
    "Invoke-WebRequest -Uri $url -OutFile $zip;" ^
    "Expand-Archive -Path $zip -DestinationPath '%USERPROFILE%\jdks\' -Force;" ^
    "Remove-Item $zip"
if errorlevel 1 (
    echo ERROR: Download / extraction failed. Install JDK 21 manually and re-run.
    exit /b 1
)
for /d %%d in ("%USERPROFILE%\jdks\jdk-21*") do set "JAVA_HOME=%%d"
if not defined JAVA_HOME (
    echo ERROR: JDK extracted but not found. Set JAVA_HOME manually.
    exit /b 1
)
echo OK: JDK 21 installed at %JAVA_HOME%

:have_java
set "PATH=%JAVA_HOME%\bin;%PATH%"
goto :eof

:first_build_hint
if not exist "composeApp\build\libs" echo First build - fetching dependencies ^(~1-2 min on a fast connection^).
goto :eof

:resolve_log_file
if defined DWELL_LOG goto :eof
if defined LOCALAPPDATA (
    set "DWELL_LOG_DIR=%LOCALAPPDATA%\Dwell\logs"
) else if defined TEMP (
    set "DWELL_LOG_DIR=%TEMP%\Dwell\logs"
) else (
    set "DWELL_LOG_DIR=%ROOT%\build\dwell-logs"
)
if not exist "!DWELL_LOG_DIR!" mkdir "!DWELL_LOG_DIR!" >nul 2>nul
if not exist "!DWELL_LOG_DIR!\." (
    set "DWELL_LOG_DIR=%ROOT%\build\dwell-logs"
    if not exist "!DWELL_LOG_DIR!" mkdir "!DWELL_LOG_DIR!" >nul 2>nul
)
set "DWELL_LOG=!DWELL_LOG_DIR!\launcher.log"

:detach
call :detach_hidden %*
goto :end

:detach_hidden
rem Relaunch this script with no console window. Output already goes to
rem the launcher log in quiet mode, so nothing is lost. The child inherits
rem DWELL_DETACHED and runs normally.
if not defined DWELL_ROOT set "DWELL_ROOT=%ROOT%"
set "DWELL_SCRIPT=%~f0"
set "DWELL_ARGS=%*"
powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -Command "$env:DWELL_DETACHED='1'; Start-Process -FilePath 'cmd.exe' -ArgumentList '/D','/C',('\"' + $env:DWELL_SCRIPT + '\" ' + $env:DWELL_ARGS) -WindowStyle Hidden -WorkingDirectory $env:DWELL_ROOT"
if errorlevel 1 (
    echo ERROR: Hidden launch failed.
    set "DWELL_EXIT=1"
)
goto :eof
goto :eof

:run_gradle
set "GRADLE_JAVA=%JAVA_HOME%\bin\java.exe"
if not exist "!GRADLE_JAVA!" set "GRADLE_JAVA=java.exe"
if defined DWELL_DEBUG (
    "!GRADLE_JAVA!" "-Xmx64m" "-Xms64m" "-Dorg.gradle.appname=gradlew" -jar "%ROOT%\gradle\wrapper\gradle-wrapper.jar" %* --console=plain
    set "DWELL_EXIT=!ERRORLEVEL!"
    goto :eof
)
call :resolve_log_file
echo   Starting quietly. For build logs, run with --debug or set DWELL_DEBUG=1.
"!GRADLE_JAVA!" "-Xmx64m" "-Xms64m" "-Dorg.gradle.appname=gradlew" -jar "%ROOT%\gradle\wrapper\gradle-wrapper.jar" %* --console=plain > "!DWELL_LOG!" 2>&1
set "DWELL_EXIT=%ERRORLEVEL%"
if not "%DWELL_EXIT%"=="0" (
    echo ERROR: Dwell failed to start. Full log: !DWELL_LOG!
)
goto :eof

:resolve_bin_dir
if defined DWELL_BIN_DIR goto :eof
if defined USERPROFILE (
    set "DWELL_BIN_DIR=%USERPROFILE%\bin"
) else if defined LOCALAPPDATA (
    set "DWELL_BIN_DIR=%LOCALAPPDATA%\Dwell\bin"
) else (
    echo ERROR: USERPROFILE and LOCALAPPDATA are not set. Set DWELL_BIN_DIR and retry.
    exit /b 1
)
goto :eof

:resolve_startup_dir
if defined APPDATA (
    set "STARTUP=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
) else if defined LOCALAPPDATA (
    set "STARTUP=%LOCALAPPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
) else (
    echo ERROR: APPDATA and LOCALAPPDATA are not set. Cannot find Startup folder.
    exit /b 1
)
goto :eof

:register
call :resolve_startup_dir || (
    set "DWELL_EXIT=1"
    goto :eof
)
rem ver clears a stale errorlevel when mkdir is skipped.
if not exist "!STARTUP!" ( mkdir "!STARTUP!" ) else ( ver >nul )
if errorlevel 1 (
    echo ERROR: Failed to create Startup folder: !STARTUP!
    set "DWELL_EXIT=1"
    goto :eof
)
call :bootstrap_java || goto :end
call :first_build_hint
echo Dwell - building and installing the app.
call :run_gradle --no-daemon --no-configuration-cache :composeApp:createDistributable
if not "!DWELL_EXIT!"=="0" goto :eof
call :resolve_app_dir
if not "!DWELL_EXIT!"=="0" goto :eof
call :sync_app_image
if not "!DWELL_EXIT!"=="0" goto :eof
echo OK: Installed: !DWELL_APP_DIR!
set "DWELL_STARTUP=!STARTUP!"
set "DWELL_TARGET=!DWELL_APP_DIR!\Screen Saver App.exe"
set "DWELL_WORKDIR=!DWELL_APP_DIR!"
set "DWELL_ICON=!DWELL_APP_DIR!\Screen Saver App.exe,0"
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$startup=$env:DWELL_STARTUP;" ^
    "$target=$env:DWELL_TARGET;" ^
    "$workdir=$env:DWELL_WORKDIR;" ^
    "$icon=$env:DWELL_ICON;" ^
    "$path=Join-Path $startup 'Dwell.lnk';" ^
    "$shell=New-Object -ComObject WScript.Shell;" ^
    "$s=$shell.CreateShortcut($path);" ^
    "$s.TargetPath=$target;" ^
    "$s.Arguments='daemon';" ^
    "$s.WorkingDirectory=$workdir;" ^
    "$s.WindowStyle=1;" ^
    "$s.IconLocation=$icon;" ^
    "$s.Save();"
if errorlevel 1 (
    echo ERROR: Failed to write Startup shortcut: !STARTUP!\Dwell.lnk
    set "DWELL_EXIT=1"
    goto :eof
)
echo OK: Wrote Startup shortcut: !STARTUP!\Dwell.lnk
echo   It takes effect at next login. To start now: scripts\dwell.cmd daemon --hidden
echo   To stop and remove later, run: scripts\dwell.cmd unregister
goto :eof

:unregister
call :resolve_startup_dir || (
    set "DWELL_EXIT=1"
    goto :eof
)
call :remove_app_image
if not "!DWELL_EXIT!"=="0" goto :eof
if exist "!STARTUP!\Dwell.lnk" (
    del "!STARTUP!\Dwell.lnk"
    if errorlevel 1 (
        echo ERROR: Failed to remove Startup shortcut: !STARTUP!\Dwell.lnk
        set "DWELL_EXIT=1"
        goto :eof
    )
    echo OK: Removed Startup shortcut: !STARTUP!\Dwell.lnk
) else (
    echo   No Startup shortcut found at !STARTUP!\Dwell.lnk.
)
goto :eof

:version
echo Dwell 1.0.0
for /f %%c in ('git -C "%ROOT%" rev-parse --short HEAD 2^>nul') do echo   commit: %%c
for /f %%b in ('git -C "%ROOT%" rev-parse --abbrev-ref HEAD 2^>nul') do echo   branch: %%b
goto :eof

:status
echo Dwell - status
echo   Project root: %ROOT%
tasklist /FI "IMAGENAME eq java.exe" /V 2>nul | findstr /I "Dwell MainKt" >nul 2>nul
if errorlevel 1 ( echo   Running: no ) else ( echo   Running: yes )
if defined JAVA_HOME ( echo   JDK 21:  %JAVA_HOME% ) else ( echo   JDK 21:  not detected )
if defined USERPROFILE (
    if exist "%USERPROFILE%\.screensaver\settings.json" (
        echo   Settings: %USERPROFILE%\.screensaver\settings.json
    ) else (
        echo   Settings: not yet written ^(first run hasn't completed^)
    )
) else (
    echo   Settings: unknown ^(USERPROFILE is not set^)
)
goto :eof

:install
call :resolve_bin_dir || (
    set "DWELL_EXIT=1"
    goto :eof
)
rem ver clears a stale errorlevel when mkdir is skipped.
if not exist "!DWELL_BIN_DIR!" ( mkdir "!DWELL_BIN_DIR!" ) else ( ver >nul )
if errorlevel 1 (
    echo ERROR: Failed to create install directory: !DWELL_BIN_DIR!
    set "DWELL_EXIT=1"
    goto :eof
)
> "!DWELL_BIN_DIR!\dwell.cmd" echo @echo off
if errorlevel 1 (
    echo ERROR: Failed to write shim: !DWELL_BIN_DIR!\dwell.cmd
    set "DWELL_EXIT=1"
    goto :eof
)
>> "!DWELL_BIN_DIR!\dwell.cmd" echo call "%HERE%dwell.cmd" %%*
if errorlevel 1 (
    echo ERROR: Failed to finish shim: !DWELL_BIN_DIR!\dwell.cmd
    set "DWELL_EXIT=1"
    goto :eof
)
echo OK: Installed shim: !DWELL_BIN_DIR!\dwell.cmd -^> %HERE%dwell.cmd
echo   Make sure !DWELL_BIN_DIR! is on your PATH so "dwell show" works anywhere.
goto :eof

:uninstall
call :resolve_bin_dir || (
    set "DWELL_EXIT=1"
    goto :eof
)
if exist "!DWELL_BIN_DIR!\dwell.cmd" (
    del "!DWELL_BIN_DIR!\dwell.cmd"
    if errorlevel 1 (
        echo ERROR: Failed to remove: !DWELL_BIN_DIR!\dwell.cmd
        set "DWELL_EXIT=1"
        goto :eof
    )
    echo OK: Removed: !DWELL_BIN_DIR!\dwell.cmd
) else (
    echo   No shim at !DWELL_BIN_DIR!\dwell.cmd - nothing to remove.
)
echo   ^(Your settings + widget data at %%USERPROFILE%%\.screensaver remain untouched.^)
goto :eof

:cheatsheet
echo   M       cycle mode ^(Cinematic ^> Ambient ^> Console^)
echo   V       cycle variant
echo   Esc     hide to tray
echo   Ctrl+Alt+Space  show from tray
echo   Ctrl+,  Settings    F1 / ?  Help    Ctrl+Q  quit
goto :eof

:usage
echo Dwell - screensaver-with-widgets
echo.
echo Usage: scripts\dwell.cmd ^<command^>
echo.
echo Commands:
echo   show       Open the dashboard; Esc hides it to the tray.
echo   daemon     Run as a tray daemon ^(dashboard appears after idle timeout^).
echo   config     Open the dashboard with Settings pre-opened.
echo   dev        Run with Compose Hot Reload ^(for development^).
echo   register   Install the app and run it at login ^(no console window^).
echo   unregister Remove the Startup shortcut and the installed app.
echo   install    Drop a `dwell.cmd` shim into %%USERPROFILE%%\bin.
echo   uninstall  Remove the shim ^(settings stay^).
echo   status     Show whether Dwell is running + which JDK / settings is in use.
echo   help       Show this help.
echo.
echo First run will auto-install JDK 21 to %%USERPROFILE%%\jdks\.
echo Add --debug or set DWELL_DEBUG=1 to show Gradle and Kotlin output.
echo Add --hidden to run show/daemon/config/dev with no console window.
goto :end

:end
popd 2>nul
set "EXIT_CODE=%DWELL_EXIT%"
endlocal & exit /b %EXIT_CODE%

:resolve_app_dir
if defined DWELL_APP_DIR goto :eof
if defined LOCALAPPDATA (
    set "DWELL_APP_DIR=%LOCALAPPDATA%\Dwell\app"
    goto :eof
)
echo ERROR: LOCALAPPDATA is not set. Set DWELL_APP_DIR and retry.
set "DWELL_EXIT=1"
goto :eof

:sync_app_image
rem Mirror the packaged app image into the per-user install dir, stopping
rem the installed copy first so the locked exe can be replaced.
set "DWELL_IMAGE="
for /d %%I in ("%ROOT%\composeApp\build\compose\binaries\main\app\*") do set "DWELL_IMAGE=%%I"
if not defined DWELL_IMAGE (
    echo ERROR: App image not found. The build above may have failed.
    set "DWELL_EXIT=1"
    goto :eof
)
echo   Stopping installed copy, if running...
taskkill /F /IM "Screen Saver App.exe" >nul 2>nul
rem ver clears a stale errorlevel when mkdir is skipped.
if not exist "!DWELL_APP_DIR!" ( mkdir "!DWELL_APP_DIR!" ) else ( ver >nul )
if errorlevel 1 (
    echo ERROR: Failed to create install dir: !DWELL_APP_DIR!
    set "DWELL_EXIT=1"
    goto :eof
)
echo   Copying app image to !DWELL_APP_DIR! ...
robocopy "!DWELL_IMAGE!" "!DWELL_APP_DIR!" /MIR /NFL /NDL /NJH /NJS /R:2 /W:1 >nul
if errorlevel 8 (
    echo ERROR: Copy failed. Close the app and retry.
    set "DWELL_EXIT=1"
    goto :eof
)
goto :eof

:remove_app_image
rem Stop the installed copy and remove the per-user install, if present.
call :resolve_app_dir
if not "!DWELL_EXIT!"=="0" goto :eof
taskkill /F /IM "Screen Saver App.exe" >nul 2>nul
if not exist "!DWELL_APP_DIR!\" goto :eof
echo   Removing installed app: !DWELL_APP_DIR!
rmdir /S /Q "!DWELL_APP_DIR!"
if errorlevel 1 (
    echo ERROR: Failed to remove !DWELL_APP_DIR! - is the app still running?
    set "DWELL_EXIT=1"
)
goto :eof
