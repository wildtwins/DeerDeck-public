$ErrorActionPreference = "Stop"

. "$PSScriptRoot\env.ps1"

$appBuildFile = Get-Content -Raw (Join-Path $repoRoot "app\build.gradle.kts")
if ($appBuildFile -notmatch 'versionName\s*=\s*"([^"]+)"') {
    throw "Unable to read versionName from app\build.gradle.kts."
}
$versionName = $Matches[1]
$apkPath = Join-Path $repoRoot ".build-codex\app\outputs\apk\debug\app-debug.apk"
$aabPath = Join-Path $repoRoot ".build-codex\app\outputs\bundle\release\app-release.aab"
$distRoot = Join-Path $repoRoot ".dist-codex\withrow-deerdeck-$versionName"
$distApk = Join-Path $distRoot "Withrow-DeerDeck-$versionName-debug.apk"
$distAab = Join-Path $distRoot "Withrow-DeerDeck-$versionName-unsigned.aab"
$smokeTestPath = Join-Path $distRoot "SMOKE-TEST.txt"

if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    throw "JDK 17 not found. Install Temurin 17 or set JAVA_HOME to a valid JDK."
}

if (-not $env:ANDROID_HOME -or -not (Test-Path $env:ANDROID_HOME)) {
    throw "Android SDK not found. Run scripts\validate.ps1 after restoring the SDK."
}

& .\gradlew.bat --no-daemon --no-configuration-cache --project-cache-dir .gradle-codex "-Pdeerdeck.isolatedBuildRoot=.build-codex" "-Pkotlin.project.persistent.dir=.kotlin-codex" "-Pdeerdeck.roomSchemaLocation=.build-codex/room-schemas" '-Pdeerdeck.uploadStoreFile=' '-Pdeerdeck.uploadStorePassword=' '-Pdeerdeck.uploadKeyAlias=' '-Pdeerdeck.uploadKeyPassword=' assembleDebug bundleRelease
if ($LASTEXITCODE -ne 0) {
    throw "Debug APK build failed."
}

if (-not (Test-Path $apkPath)) {
    throw "Debug APK was not produced at $apkPath."
}
if (-not (Test-Path $aabPath)) {
    throw "Release AAB was not produced at $aabPath."
}

New-Item -ItemType Directory -Force -Path $distRoot | Out-Null
Copy-Item -Force -Path $apkPath -Destination $distApk
Copy-Item -Force -Path $aabPath -Destination $distAab
Copy-Item -Force -Recurse -Path (Join-Path $repoRoot "docs\play") -Destination $distRoot
$publicAssetDestination = Join-Path $distRoot 'play-assets'
New-Item -ItemType Directory -Force -Path $publicAssetDestination | Out-Null
foreach ($assetName in @('README.md', 'app-icon-512.png', 'feature-graphic-1024x500.png')) {
    Copy-Item -LiteralPath (Join-Path $repoRoot "play-assets\$assetName") -Destination $publicAssetDestination -Force
}

$smokeTest = @"
Withrow DeerDeck $versionName production-candidate smoke test
=======================================================

APK:
$distApk

Unsigned AAB for structural review only:
$distAab

Play Console upload:
Run scripts\create-upload-key.ps1 once, then scripts\build-play-release.ps1.
Upload the resulting signed dist\play\Withrow-DeerDeck-$versionName.aab.

Install path ADB:
1. Plug in one Android phone by USB.
2. Enable Developer options > USB debugging.
3. Accept the Allow USB debugging prompt on the phone.
4. From the repository root, run:
   powershell -ExecutionPolicy Bypass -File .\scripts\check-device.ps1
   powershell -ExecutionPolicy Bypass -File .\scripts\install-debug.ps1

Install path manual:
1. Move Withrow-DeerDeck-$versionName-debug.apk to the phone.
2. Open it on the phone and allow install from this source if prompted.
3. Open DeerDeck after install.

Smoke test:
1. On a fresh install, read the privacy notice. Choose whether to enable field conditions and confirm the app remembers the choice.
2. Confirm the app opens to Observe and the bottom nav order is Observe, Stats, History, Profiles, Settings.
3. Confirm Stats shows One year ago today, 30-day wildlife trend, wildlife share, Similar Days, and Best Conditions.
4. Confirm Profiles contains locked Wildlife and Birds choices plus an add-profile action.
5. Start a session. When field conditions are enabled, grant location and confirm GPS, sunset, weather, pressure/humidity when available, and moon phase appear.
6. Disable field conditions in Settings, start another session, and confirm location permission is not requested while core logging still works.
7. Create multiple custom profiles and confirm their names, wildlife, colors, and dragged tile order persist.
8. Confirm built-in profiles cannot be edited and update to a GPS-regional list after starting a location-enabled session.
9. Tap Doe three times and another tile once; confirm Sightings and Types update correctly.
10. Long-press a wildlife tile, choose descriptors, and save a detailed observation.
11. Capture photo, video, and voice media. Confirm each is attached to the session and appears in its configured destination.
12. Confirm camera zoom works and the camera remains open after each photo until closed by the user.
13. End a session with notes, Cloudy or Snow, a custom tag, and speech input.
14. Search History by wildlife, tag, condition, and location; exercise 30-day, one-year, and all-time filters.
15. Confirm timeline/media/notes/GPS/sunset/weather/pressure/humidity/moon details render without overlap.
16. Delete one observation and one session; confirm each destructive action requires confirmation.
17. Use Share CSV and Save CSV; confirm privacy controls and environment columns behave as configured.
18. Verify auto backup, manual phone backup, Google Drive folder backup, full ZIP export, and guarded restore on a disposable test dataset.
19. Leave a session inactive for 3 hours or relaunch after an old open session; confirm it auto-ends.
20. Type DELETE in Settings > Privacy & Data > Erase local data on a disposable dataset. Confirm sessions and app-owned media are removed while camera-roll media and exported backups remain.
21. Deny camera, microphone, and location permissions individually; confirm the app explains the missing capability and does not crash.
22. Switch light, dark, and system themes and verify compact/large text layouts on the test phone.
"@

Set-Content -Path $smokeTestPath -Value $smokeTest -Encoding UTF8

Write-Host "Package ready:"
Write-Host $distRoot
Write-Host $distApk
Write-Host $distAab
Write-Host $smokeTestPath
