param(
    [string]$ScrPath = "$PSScriptRoot\..\..\build\compose\binaries\main\scr\Screen Saver App\ScreenSaverApp.scr"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $ScrPath)) {
    throw "Screen saver file not found: $ScrPath. Run .\gradlew.bat :composeApp:packageScr first."
}

Set-ItemProperty -Path "HKCU:\Control Panel\Desktop" -Name SCRNSAVE.EXE -Value (Resolve-Path -LiteralPath $ScrPath).Path
Set-ItemProperty -Path "HKCU:\Control Panel\Desktop" -Name ScreenSaveActive -Value "1"

Write-Host "Installed Screen Saver App from $ScrPath"
