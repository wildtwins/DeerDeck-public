# Validation Notes

## Production Gate

Run from the repository root after following the JDK/SDK setup in `README.md`:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\validate.ps1
```

The gate runs unit tests, debug and release lint, the debug APK build, the release AAB build, and instrumentation APK assembly. Device tests require a separate device run. Isolated outputs are written under `.build-codex`:

- `.build-codex\app\outputs\apk\debug\app-debug.apk`
- `.build-codex\app\outputs\bundle\release\app-release.aab`
- `.build-codex\app\outputs\mapping\release\mapping.txt`
- `.build-codex\app\reports\lint-results-release.html`
- `.build-codex\app\reports\tests\testDebugUnitTest\index.html`

## Historical 2.1.0 Baseline

Verified July 25, 2026:

- Build successful with AGP 8.13.2, Gradle 8.13, JDK 17, and Android API 36.
- Unit tests: 40 passed, 0 failures, 0 errors.
- Release lint: 0 errors. The 44 warnings and one hint are non-blocking Kotlin convenience, dependency-cap, launcher-icon, AGP, and KSP suggestions.
- Package: `com.wildtwins.deerdeck`.
- Version: `versionCode 24`, `versionName 2.1.0`.
- SDK: minimum 26, target 36, compile 36.
- Release AAB is minified and resource-shrunk. It remains unsigned until the private upload key is supplied.

## Device Install

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check-device.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\install-debug.ps1
```

The install helper updates the existing app without clearing its local database. Always create a verified backup before testing destructive flows.

## Production Candidate Package

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package-beta.ps1
```

The backward-compatible script name reads the current Gradle version and creates `.dist-codex\withrow-deerdeck-2.1.0` with a named debug APK, explicitly unsigned AAB, Play documents/assets, and a current smoke-test checklist.

## Signed Play Bundle

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\create-upload-key.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\build-play-release.ps1
```

The first command is a one-time private credential operation. The second prompts for the upload-key password, runs a clean release gate, verifies the AAB signature, and writes a versioned bundle such as `dist\play\Withrow-DeerDeck-2.1.0.aab`.

## GitHub Readiness

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check-github.ps1
```

CI independently runs unit tests, release lint, AAB, and debug APK generation on Linux and uploads the candidates plus R8 mapping file.
