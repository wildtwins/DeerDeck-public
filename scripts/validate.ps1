$ErrorActionPreference = "Stop"
. "$PSScriptRoot\env.ps1"
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    throw 'JDK 17 not found. Install it and set JAVA_HOME.'
}
if ((-not $sdkRoot -or -not (Test-Path $sdkRoot)) -and -not (Test-Path 'local.properties')) {
    throw 'Set ANDROID_HOME to an Android SDK or copy local.properties.example to local.properties and set sdk.dir.'
}
& .\gradlew.bat --no-daemon --no-configuration-cache --max-workers=2 --project-cache-dir .gradle-codex '-Pdeerdeck.isolatedBuildRoot=.build-codex' '-Pkotlin.project.persistent.dir=.kotlin-codex' '-Pdeerdeck.roomSchemaLocation=.build-codex/room-schemas' '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8' '-Pdeerdeck.uploadStoreFile=' '-Pdeerdeck.uploadStorePassword=' '-Pdeerdeck.uploadKeyAlias=' '-Pdeerdeck.uploadKeyPassword=' testDebugUnitTest lintDebug lintRelease assembleDebug bundleRelease assembleDebugAndroidTest
if ($LASTEXITCODE -ne 0) { throw 'Validation failed.' }
