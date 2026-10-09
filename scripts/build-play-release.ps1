param(
    [string]$KeystorePath = (Join-Path $PSScriptRoot "..\play-signing\withrow-deerdeck-upload.jks"),
    [string]$Alias = "withrow-deerdeck-upload"
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "env.ps1")

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$resolvedKeystore = (Resolve-Path -LiteralPath $KeystorePath -ErrorAction Stop).Path
$securePassword = Read-Host "Upload-key password" -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)

try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    $env:DEERDECK_UPLOAD_STORE_FILE = $resolvedKeystore
    $env:DEERDECK_UPLOAD_STORE_PASSWORD = $password
    $env:DEERDECK_UPLOAD_KEY_ALIAS = $Alias
    $env:DEERDECK_UPLOAD_KEY_PASSWORD = $password

    & (Join-Path $projectRoot "gradlew.bat") --no-daemon clean testDebugUnitTest lintRelease bundleRelease
    if ($LASTEXITCODE -ne 0) {
        throw "The Play release build failed with exit code $LASTEXITCODE"
    }

    $sourceBundle = Join-Path $projectRoot "app\build\outputs\bundle\release\app-release.aab"
    $destinationDirectory = Join-Path $projectRoot "dist\play"
    $appBuildFile = Get-Content -Raw (Join-Path $projectRoot "app\build.gradle.kts")
    if ($appBuildFile -notmatch 'versionName\s*=\s*"([^"]+)"') {
        throw "Unable to read versionName from app\build.gradle.kts."
    }
    $destinationBundle = Join-Path $destinationDirectory "Withrow-DeerDeck-$($Matches[1]).aab"
    New-Item -ItemType Directory -Force -Path $destinationDirectory | Out-Null
    Copy-Item -LiteralPath $sourceBundle -Destination $destinationBundle -Force

    $jarsigner = Join-Path $env:JAVA_HOME "bin\jarsigner.exe"
    $strictErrorPreference = $ErrorActionPreference
    $jarsignerExitCode = -1
    $verificationOutput = @()
    try {
        $ErrorActionPreference = "Continue"
        $verificationOutput = @(& $jarsigner -verify -verbose -certs $destinationBundle 2>&1)
        $jarsignerExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $strictErrorPreference
    }
    $verificationOutput | ForEach-Object { Write-Host $_ }
    $verificationText = $verificationOutput -join [Environment]::NewLine
    $isVerified = $verificationText -match "(?m)^\s*jar verified\.\s*$"
    $isUnsigned = $verificationText -match "(?i)jar is unsigned"
    if ($jarsignerExitCode -ne 0 -or -not $isVerified -or $isUnsigned) {
        throw "The generated Android App Bundle did not pass signature verification."
    }

    Write-Host "Signed Play bundle ready: $destinationBundle"
} finally {
    $env:DEERDECK_UPLOAD_STORE_FILE = $null
    $env:DEERDECK_UPLOAD_STORE_PASSWORD = $null
    $env:DEERDECK_UPLOAD_KEY_ALIAS = $null
    $env:DEERDECK_UPLOAD_KEY_PASSWORD = $null
    if ($pointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}
