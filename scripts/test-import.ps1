$ErrorActionPreference = "Stop"
$Root = Split-Path $PSScriptRoot -Parent
$Fixture = Join-Path $Root "app\src\test\resources\import\overwatch-import-two.json"
if (-not (Test-Path $Fixture)) {
    throw "Fixture not found: $Fixture"
}

$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
$Package = "com.grandsphere.overwatch"

& $adb wait-for-device

$apk = Get-ChildItem -Path (Join-Path $Root "compile\app\outputs\apk\debug\*.apk") -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $apk) {
    $apk = Get-ChildItem -Path (Join-Path $Root "release\*.apk") -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
}
if ($apk) {
    Write-Host "Installing $($apk.Name) ..."
    & $adb install -r $apk.FullName | Out-Null
}

$remote = "/sdcard/Download/overwatch-import-two.json"
Write-Host "Pushing fixture -> $remote"
& $adb push $Fixture $remote

$json = Get-Content -Raw -Encoding UTF8 $Fixture
$jsonOneLine = ($json -replace "`r`n", "" -replace "\s{2,}", " ").Trim()

Write-Host "Sending overwatch import JSON via ACTION_SEND ($Package) ..."
& $adb shell am start -W -a android.intent.action.SEND -t "text/plain" `
    -n "$Package/.MainActivity" `
    --es android.intent.extra.TEXT "$jsonOneLine"

Write-Host "Fixture on device: $remote"
Write-Host "Check the emulator for the import checklist (Import Test Alpha / Beta)."
