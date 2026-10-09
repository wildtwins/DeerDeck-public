$ErrorActionPreference = "Stop"

. "$PSScriptRoot\env.ps1"

$apkPath = Join-Path $repoRoot ".build-codex\app\outputs\apk\debug\app-debug.apk"

if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    throw "JDK 17 not found. Install Temurin 17 or set JAVA_HOME to a valid JDK."
}

if (-not $sdkRoot -or -not (Test-Path (Join-Path $sdkRoot 'platform-tools\adb.exe'))) {
    throw "Android platform-tools not found. Set ANDROID_HOME or sdk.dir in local.properties."
}

& .\gradlew.bat --no-daemon --no-configuration-cache --project-cache-dir .gradle-codex "-Pdeerdeck.isolatedBuildRoot=.build-codex" "-Pkotlin.project.persistent.dir=.kotlin-codex" "-Pdeerdeck.roomSchemaLocation=.build-codex/room-schemas" assembleDebug
if ($LASTEXITCODE -ne 0) {
    throw "Debug APK build failed."
}

if (-not (Test-Path $apkPath)) {
    throw "Debug APK was not produced at $apkPath."
}

$deviceLines = & adb devices | Select-Object -Skip 1 | Where-Object { $_.Trim().Length -gt 0 }
$devices = @(
    foreach ($line in $deviceLines) {
        $parts = $line -split "\s+"
        [pscustomobject]@{
            Id = $parts[0]
            State = $parts[1]
        }
    }
)

if ($devices.Count -eq 0) {
    throw "No Android device detected. Plug in your phone by USB, enable Developer options > USB debugging, then rerun scripts\install-debug.ps1."
}

$unauthorized = @($devices | Where-Object { $_.State -eq "unauthorized" })
if ($unauthorized.Count -gt 0) {
    $unauthorized | Format-Table -AutoSize | Out-String | Write-Host
    throw "Phone is connected but not authorized. Unlock the phone, accept the 'Allow USB debugging' prompt, then rerun scripts\install-debug.ps1."
}

$offline = @($devices | Where-Object { $_.State -eq "offline" })
if ($offline.Count -gt 0) {
    $offline | Format-Table -AutoSize | Out-String | Write-Host
    throw "Phone is connected but offline. Unplug/replug USB, confirm USB debugging is enabled, then rerun scripts\install-debug.ps1."
}

$readyDevices = @($devices | Where-Object { $_.State -eq "device" })
if ($readyDevices.Count -ne 1) {
    $devices | Format-Table -AutoSize | Out-String | Write-Host
    throw "Expected exactly one authorized Android device, found $($readyDevices.Count). Leave one phone connected and rerun scripts\install-debug.ps1."
}

& adb install -r $apkPath
if ($LASTEXITCODE -ne 0) {
    throw "APK install failed. If Android reports INSTALL_FAILED_UPDATE_INCOMPATIBLE, the installed app was signed with a different certificate. Back up data before uninstalling/reinstalling."
}

& adb shell monkey -p com.wildtwins.deerdeck -c android.intent.category.LAUNCHER 1 | Write-Host
if ($LASTEXITCODE -ne 0) {
    throw "DeerDeck installed, but launch command failed."
}

Write-Host ""
Write-Host "DeerDeck installed and launch requested."
Write-Host "Smoke test:"
Write-Host "1. Confirm the app opens to Observe and the bottom nav order is Observe, Stats, History, Profiles, Settings."
Write-Host "2. Confirm Stats shows One year ago today, a 30-day wildlife line graph, and the wildlife share chart."
Write-Host "3. Confirm Profiles contains locked Wildlife and Birds choices plus an add-profile action."
Write-Host "4. Start session and accept location permission."
Write-Host "5. Confirm GPS, sunset, weather, pressure/humidity when available, and moon phase appear in the session header."
Write-Host "6. In a custom profile, long-press and drag wildlife tiles; confirm the order persists after switching tabs."
Write-Host "7. Add sightings and a detailed observation."
Write-Host "8. Take a photo and video, close the camera with Android Back, then confirm both appear in the phone gallery and History media."
Write-Host "9. End session with notes, Cloudy or Snow, a custom tag, and voice input if available."
Write-Host "10. Check History timeline/media/notes/GPS/sunset/weather/pressure/moon phase."
Write-Host "11. Delete one History observation and one History session with visible delete controls."
Write-Host "12. Share CSV or Save CSV and confirm GPS/weather/pressure/moon columns are included."
Write-Host "13. Leave a session inactive for 3 hours or relaunch after an old open session; confirm auto-end."
Write-Host "14. Choose Google Drive backup; confirm phone folders are rejected and a Drive folder can complete Backup now."
