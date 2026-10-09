$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

# JAVA_HOME can point to any JDK 17 installation. Discover Temurin on Windows
# only when the caller has not supplied an explicit path.
if (-not $env:JAVA_HOME) {
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaCommand) {
        $env:JAVA_HOME = Split-Path (Split-Path $javaCommand.Source -Parent) -Parent
    } elseif (Test-Path 'C:\Program Files\Eclipse Adoptium') {
        $installedJdk = Get-ChildItem -Directory 'C:\Program Files\Eclipse Adoptium' |
            Where-Object { $_.Name -like 'jdk-17*' } | Sort-Object Name -Descending | Select-Object -First 1
        if ($installedJdk) { $env:JAVA_HOME = $installedJdk.FullName }
    }
}
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

$localSdk = Join-Path $repoRoot '.android-sdk'
$configuredSdk = $null
if (Test-Path 'local.properties') {
    $sdkSetting = Get-Content -LiteralPath 'local.properties' | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1
    if ($sdkSetting) {
        $configuredSdk = ($sdkSetting -replace '^\s*sdk\.dir\s*=\s*', '').Replace('\\', '\').Replace('\:', ':')
    }
}
$sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) {
    $env:ANDROID_SDK_ROOT
} elseif ($configuredSdk) { $configuredSdk } elseif (Test-Path $localSdk) { $localSdk } else { $null }
if ($sdkRoot -and (Test-Path $sdkRoot)) {
    $env:ANDROID_HOME = $sdkRoot
    $env:ANDROID_SDK_ROOT = $sdkRoot
    $env:Path = "$sdkRoot\cmdline-tools\latest\bin;$sdkRoot\platform-tools;$env:Path"
}
$githubCliHome = 'C:\Program Files\GitHub CLI'
if (Test-Path (Join-Path $githubCliHome 'gh.exe')) { $env:Path = "$githubCliHome;$env:Path" }
