param(
    [string]$SourceDir = "$PSScriptRoot\..\..\build\compose\binaries\main\scr\Screen Saver App",
    [string]$InstallDir = "$env:LOCALAPPDATA\ScreenSaverApp\Screen Saver App",
    [switch]$NoRegister,
    [switch]$OpenSettings
)

$ErrorActionPreference = "Stop"

$sourcePath = Resolve-Path -LiteralPath $SourceDir -ErrorAction SilentlyContinue
if (-not $sourcePath) {
    throw "Screen saver bundle not found: $SourceDir. Run .\gradlew.bat :composeApp:packageScrZip first."
}

$sourceRoot = $sourcePath.Path
$sourceScr = Join-Path $sourceRoot "Screen Saver App.scr"
$sourceRuntime = Join-Path $sourceRoot "runtime"
$sourceApp = Join-Path $sourceRoot "app"

if (-not (Test-Path -LiteralPath $sourceScr -PathType Leaf)) {
    throw "Screen saver launcher not found: $sourceScr"
}
if (-not (Test-Path -LiteralPath $sourceRuntime -PathType Container)) {
    throw "Packaged runtime directory not found: $sourceRuntime"
}
if (-not (Test-Path -LiteralPath $sourceApp -PathType Container)) {
    throw "Packaged app directory not found: $sourceApp"
}

New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
Copy-Item -Path (Join-Path $sourceRoot "*") -Destination $InstallDir -Recurse -Force

$installedScr = Join-Path $InstallDir "Screen Saver App.scr"
if (-not (Test-Path -LiteralPath $installedScr -PathType Leaf)) {
    throw "Install failed; launcher missing after copy: $installedScr"
}

if (-not $NoRegister) {
    Set-ItemProperty -Path "HKCU:\Control Panel\Desktop" -Name SCRNSAVE.EXE -Value (Resolve-Path -LiteralPath $installedScr).Path
    Set-ItemProperty -Path "HKCU:\Control Panel\Desktop" -Name ScreenSaveActive -Value "1"
}

if ($OpenSettings -and -not $NoRegister) {
    Start-Process rundll32.exe "shell32.dll,Control_RunDLL desk.cpl,,@screensaver"
}

Write-Host "Installed Screen Saver App screensaver bundle at $InstallDir"
