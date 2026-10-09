$ErrorActionPreference = "Stop"

. "$PSScriptRoot\env.ps1"

if (-not $sdkRoot -or -not (Test-Path (Join-Path $sdkRoot 'platform-tools\adb.exe'))) {
    throw "Android platform-tools not found. Set ANDROID_HOME or sdk.dir in local.properties."
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
    throw "No Android device detected. Plug in your phone by USB, enable Developer options > USB debugging, then rerun scripts\check-device.ps1."
}

$unauthorized = @($devices | Where-Object { $_.State -eq "unauthorized" })
if ($unauthorized.Count -gt 0) {
    $unauthorized | Format-Table -AutoSize | Out-String | Write-Host
    throw "Phone is connected but not authorized. Unlock the phone, accept the 'Allow USB debugging' prompt, then rerun scripts\check-device.ps1."
}

$offline = @($devices | Where-Object { $_.State -eq "offline" })
if ($offline.Count -gt 0) {
    $offline | Format-Table -AutoSize | Out-String | Write-Host
    throw "Phone is connected but offline. Unplug/replug USB, confirm USB debugging is enabled, then rerun scripts\check-device.ps1."
}

$ready = @($devices | Where-Object { $_.State -eq "device" })
if ($ready.Count -ne 1) {
    $devices | Format-Table -AutoSize | Out-String | Write-Host
    throw "Expected exactly one authorized Android device, found $($ready.Count). Leave one phone connected and rerun scripts\check-device.ps1."
}

Write-Host "Authorized Android device ready: $($ready[0].Id)"
