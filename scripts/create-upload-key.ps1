param(
    [string]$KeystorePath = (Join-Path $PSScriptRoot "..\play-signing\withrow-deerdeck-upload.jks"),
    [string]$Alias = "withrow-deerdeck-upload"
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "env.ps1")

if (Test-Path -LiteralPath $KeystorePath) {
    throw "Upload keystore already exists: $KeystorePath"
}

$keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe"
if (-not (Test-Path -LiteralPath $keytool)) {
    throw "keytool was not found under JAVA_HOME: $env:JAVA_HOME"
}

$securePassword = Read-Host "Create a strong upload-key password" -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    if ($password.Length -lt 12) {
        throw "Use a password with at least 12 characters."
    }

    $directory = Split-Path -Parent $KeystorePath
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    $env:DEERDECK_TEMP_KEY_PASSWORD = $password
    $strictErrorPreference = $ErrorActionPreference
    $keytoolExitCode = -1
    try {
        $ErrorActionPreference = "Continue"
        & $keytool -genkeypair -v `
            -keystore $KeystorePath `
            -storetype PKCS12 `
            -alias $Alias `
            -keyalg RSA `
            -keysize 4096 `
            -validity 10000 `
            -dname "CN=Withrow DeerDeck, OU=Mobile, O=Wildtwins, C=US" `
            -storepass:env DEERDECK_TEMP_KEY_PASSWORD `
            -keypass:env DEERDECK_TEMP_KEY_PASSWORD
        $keytoolExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $strictErrorPreference
    }
    if ($keytoolExitCode -ne 0) {
        throw "keytool failed with exit code $keytoolExitCode"
    }
} finally {
    $env:DEERDECK_TEMP_KEY_PASSWORD = $null
    if ($pointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

Write-Host "Upload key created: $KeystorePath"
Write-Host "Back up this keystore and its password in two secure locations before publishing."
