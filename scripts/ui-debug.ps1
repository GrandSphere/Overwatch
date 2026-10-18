param(
    [switch]$Build
)

$ErrorActionPreference = "Stop"
$Root = Split-Path $PSScriptRoot -Parent
$CompileDir = Join-Path $Root "compile"
$ReleaseDir = Join-Path $Root "release"
$AvdName = "Pixel_10"
$Package = "com.grandsphere.overwatch"
$MainActivity = "$Package/.MainActivity"

New-Item -ItemType Directory -Force -Path $CompileDir, $ReleaseDir | Out-Null

$sdk = $env:ANDROID_HOME
if (-not $sdk) {
    $sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
}
if (-not (Test-Path $sdk)) {
    throw "Android SDK not found. Set ANDROID_HOME or install the SDK."
}

$adb = Join-Path $sdk "platform-tools\adb.exe"
$emulator = Join-Path $sdk "emulator\emulator.exe"
$gradlew = Join-Path $Root "scripts\gradlew.bat"

$env:ANDROID_HOME = $sdk
$env:GRADLE_USER_HOME = Join-Path $CompileDir ".gradle-home"

function Wait-For-Device {
    & $adb wait-for-device
    $deadline = (Get-Date).AddMinutes(3)
    do {
        $boot = & $adb shell getprop sys.boot_completed 2>$null
        if ($boot -match "1") { return }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Emulator did not finish booting within 3 minutes."
}

function Save-AdbScreenshot {
    param([string]$Path)
    $remote = "/sdcard/overwatch-ui-cap.png"
    & $adb shell screencap -p $remote | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "adb screencap failed." }
    & $adb pull $remote $Path | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "adb pull screenshot failed." }
    & $adb shell rm $remote | Out-Null
}

function Tap-UiText {
    param([string]$Text)
    $remoteDump = "/sdcard/uidump.xml"
    $localDump = Join-Path $CompileDir "uidump.xml"
    & $adb shell uiautomator dump $remoteDump 2>$null | Out-Null
    & $adb pull $remoteDump $localDump | Out-Null
    if (-not (Test-Path $localDump)) { return $false }
    [xml]$doc = Get-Content -Raw $localDump
    $escaped = [System.Security.SecurityElement]::Escape($Text)
    $node = $doc.SelectSingleNode("//node[@text='$escaped' or @content-desc='$escaped']")
    if (-not $node) { return $false }
    if ($node.bounds -notmatch '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') { return $false }
    $x1 = [int]$Matches[1]; $y1 = [int]$Matches[2]
    $x2 = [int]$Matches[3]; $y2 = [int]$Matches[4]
    $x = ($x1 + $x2) / 2
    $y = ($y1 + $y2) / 2
    & $adb shell input tap $x $y | Out-Null
    return $true
}

function Open-SettingsExportSection {
    Start-Sleep -Seconds 1
    if (-not (Tap-UiText "Menu")) {
        Tap-UiText "Open navigation drawer" | Out-Null
    }
    Start-Sleep -Milliseconds 800
    if (-not (Tap-UiText "Settings")) { return $false }
    Start-Sleep -Seconds 1
    & $adb shell input swipe 500 1400 500 400 400 | Out-Null
    Start-Sleep -Milliseconds 500
    if (-not (Tap-UiText "Export")) { return $false }
    Start-Sleep -Milliseconds 600
    return $true
}

function Find-DebugApk {
    $candidates = @()
    $debugDir = Join-Path $Root "compile\app\outputs\apk\debug"
    if (Test-Path $debugDir) {
        $candidates += Get-ChildItem -Path $debugDir -Filter "*.apk" -ErrorAction SilentlyContinue
    }
    if (Test-Path $ReleaseDir) {
        $candidates += Get-ChildItem -Path $ReleaseDir -Filter "*.apk" -ErrorAction SilentlyContinue
    }
    return $candidates | Sort-Object LastWriteTime -Descending | Select-Object -First 1
}

$devices = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "device$" }
if (-not $devices) {
    if (-not (Test-Path $emulator)) {
        throw "No device connected and emulator not found at $emulator"
    }
    Write-Host "Starting AVD $AvdName ..."
    Start-Process -FilePath $emulator -ArgumentList @("-avd", $AvdName, "-no-snapshot-load") -WindowStyle Minimized
    Wait-For-Device
}

Push-Location $Root
if ($Build) {
    Write-Host "Building and installing debug APK (Gradle) ..."
    & $gradlew installDebug --no-daemon
    if ($LASTEXITCODE -ne 0) { Pop-Location; exit $LASTEXITCODE }
    $apk = Find-DebugApk
    if ($apk) {
        Copy-Item -Force $apk.FullName (Join-Path $ReleaseDir $apk.Name)
    }
} else {
    $apk = Find-DebugApk
    if (-not $apk) {
        Write-Host "No debug APK found under compile\app\outputs\apk\debug or release\."
        Write-Host "Run scripts\debug-build.bat once to compile, or pass -Build to this script."
        Pop-Location
        exit 1
    }
    Write-Host "Installing existing APK (no compile): $($apk.FullName)"
    & $adb install -r $apk.FullName
    if ($LASTEXITCODE -ne 0) { Pop-Location; exit $LASTEXITCODE }
}

Write-Host "Launching $MainActivity ..."
& $adb shell am start -n $MainActivity -W
Start-Sleep -Seconds 3

$flows = Join-Path $Root "flows"
if ((Test-Path $flows) -and (Get-Command maestro -ErrorAction SilentlyContinue)) {
    Write-Host "Running Maestro flows ..."
    maestro test $flows
    Pop-Location
    exit $LASTEXITCODE
}

if (Open-SettingsExportSection) {
    Write-Host "Opened Settings > Export for screenshot."
} else {
    Write-Host "Could not auto-open Settings > Export; capturing home screen."
}

$shot = Join-Path $CompileDir ("ui-" + (Get-Date -Format "yyyyMMdd-HHmmss") + ".png")
Write-Host "Capturing screenshot -> $shot"
Save-AdbScreenshot -Path $shot
Pop-Location
Write-Host "Done."
