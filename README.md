# Withrow DeerDeck

[Source](https://github.com/wildtwins/DeerDeck-public) · [MIT license](LICENSE)

Withrow DeerDeck is a production-candidate Android app for quickly logging wildlife observations in the field. It is local-first: observations, media references, conditions, profiles, and settings stay on the device unless the user explicitly exports, backs up, or shares them.

## What It Does

- Opens directly to a fast wildlife counting grid.
- Captures optional GPS, sunset, weather, pressure, humidity, wind, and moon context.
- Records photos, videos, voice notes, descriptors, tags, and session notes.
- Includes locked Wildlife and Birds profiles that choose offline regional defaults from session GPS.
- Supports unlimited named custom profiles with editable wildlife, colors, and ordering.
- Shows 30-day trends, wildlife share, similar days, best conditions, and historical context.
- Adds Field IQ with personal forecasts, weekly windows, wildlife pairings, location scorecards, condition response, records, discoveries, and return-watch insights.
- Searches and filters session history, including wildlife, tags, dates, locations, and conditions.
- Saves media to the camera roll or app storage, according to the user's setting.
- Creates verified phone or Google Drive backups and full ZIP exports with available media.
- Restores guarded backups after first creating a safety backup.
- Automatically ends an abandoned session after three hours of inactivity.

## Stack

- Kotlin 2.0.21 and Jetpack Compose
- Android Gradle Plugin 8.13.2 and Gradle 8.13
- Room local database
- CameraX photo/video capture
- WorkManager background scheduling
- `minSdk 26`, `targetSdk 36`
- GitHub Actions CI for tests, release lint, AAB, and debug APK artifacts

## Build and Validate

The source snapshot is version **2.4.4** (`versionCode 32`). Install JDK 17 and
an Android SDK with platform-tools, `platforms;android-36`, and
`build-tools;36.0.0`. Android Studio's SDK Manager or Android command-line tools
can install these packages. Accept the SDK licenses locally.

Set `JAVA_HOME` to your JDK and `ANDROID_HOME` to your SDK. Alternatively, copy
`local.properties.example` to `local.properties` and update `sdk.dir`. Do not
commit machine-specific configuration. Run all commands from the repository root.
Gradle downloads pinned build dependencies on the first run, so internet access
is needed to populate its cache.

Windows PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\validate.ps1
```

Linux or macOS with JDK 17 and Android SDK environment variables set:

```sh
chmod +x gradlew
./gradlew --no-daemon testDebugUnitTest lintDebug lintRelease assembleDebug bundleRelease assembleDebugAndroidTest
```

The Windows helper stores outputs under `.build-codex/app`; direct Gradle
commands use `app/build`. Unit tests and both lint variants must pass. The gate
also builds a debug APK, an unsigned release AAB, and the instrumentation test
APK. Building the test APK does not execute device tests.

No API key or server is required for the normal app. Optional weather requests
send the session coordinates to the U.S. National Weather Service and may be
unavailable outside its coverage area. Optional elevation lookup also sends coordinates to the USGS Elevation Point Query Service. The request User-Agent identifies the app
using the developer's public GitHub profile. Google Drive backups use Android's
document picker and the user's selected folder.

For manual installation and testing, use a test phone or emulator with disposable
records. `scripts/check-device.ps1` and `scripts/install-debug.ps1` are explicit
device actions, separate from validation. See [the smoke checklist](docs/beta-smoke-test.md).

## Source Privacy

This distribution excludes field records, camera captures, device screenshots,
backups, runtime databases, credentials, signing keys, local agent state, and
prior private Git history. Only the app's launcher artwork and generated store
branding images are included. See [publication notes](docs/publication.md).

## License

Released under the [MIT license](LICENSE). Third-party dependencies retain their
respective licenses.

## Google Play Release

The Play submission materials are under [`docs/play`](docs/play), and generated listing assets are under [`play-assets`](play-assets). Start with [`docs/play/release-checklist.md`](docs/play/release-checklist.md).

Create the private upload keystore once, then build a signed AAB:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\create-upload-key.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\build-play-release.ps1
```

The keystore and passwords are intentionally excluded from Git. Enroll the upload key in Play App Signing and preserve an encrypted offline recovery copy.

## Planned Later

- Continuous cloud synchronization and conflict resolution
- Opt-in wildlife photo identification
- Additional regional profile packs
