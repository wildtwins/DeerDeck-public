# Try DeerDeck 2.4.4

[Download the public preview](https://github.com/wildtwins/DeerDeck-public/releases/tag/v2.4.4-preview.1)

**This is a debug-signed preview for evaluation, not a production-signed or
Google Play release. Physical-device and emulator testing were unavailable for
this public release.** Use a spare Android device or disposable emulator with
no valuable DeerDeck records. Android 8.0 (API 26) or newer is required.

## Download and verify

Download `DeerDeck-2.4.4-preview-debug.apk` and `SHA256SUMS.txt` from the release
above. The APK's SHA-256 is:

```text
0b797e1d3e7015c757b574f3068781c43861cb0b474bafcd38b2537a237d498c
```

On Windows, compare the output of:

```powershell
Get-FileHash .\DeerDeck-2.4.4-preview-debug.apk -Algorithm SHA256
```

On Linux use `sha256sum -c SHA256SUMS.txt`; on macOS use
`shasum -a 256 -c SHA256SUMS.txt`. A matching hash confirms the downloaded file
matches this release; download it from the official repository above.

## Install and try

1. Open the APK on your test device. Android may ask you to allow installation
   from the specific browser or file manager you used. Allow that source only
   if you choose to proceed, then revoke that source's permission afterward.
   Keep Play Protect enabled; do not bypass a harmful-app warning.
2. If Android reports a conflicting package or signature, stop. This preview
   uses the same application ID as existing DeerDeck installations. Do not
   uninstall an app containing records to force installation; use a separate
   test device instead. Future CI debug builds may use a different signing key
   and may not update this preview in place.
3. Open Withrow DeerDeck and choose **Use without location** for an offline
   trial. The APK starts empty; the fictional gallery records are test fixtures
   and are not shipped in the application.
4. Tap **Start**, tap wildlife tiles to count sightings, then explore **Stats**,
   **History**, and **Profiles**. End the session when finished.
5. Camera, microphone, and location are optional. Enabling field conditions
   sends your session coordinates to the NWS and USGS. Google Drive backup
   requires your own folder selection and Android permission; no developer
   account, folder, or credentials are embedded.

Developers with an already configured test device can install using
`adb install DeerDeck-2.4.4-preview-debug.apk`. Do not use uninstall or downgrade
flags to work around a conflicting existing installation.

## What was tested

The downloadable APK is the unchanged artifact from
[successful Android CI run 37977081349](https://github.com/wildtwins/DeerDeck-public/actions/runs/37977081349),
built from commit `53f4682958abe71c324b1faacf4d0aac3a3e5e98` (version 2.4.4,
version code 32). That revision passed 51 unit tests, release lint, and debug
APK/release bundle builds. Local validation also passed debug lint and compiled
the instrumentation test APK. Lint reported warnings, with no errors.

The later README/gallery update adds host-side UI checks against unchanged app
source. Host rendering does not validate Android hardware, camera/audio, GPS,
Google Drive integration, Android installation, or real-device lifecycle behavior.
The [device smoke checklist](beta-smoke-test.md) describes those remaining checks.

No new signing credential was created for this release: the existing, previously
audited CI APK is published byte-for-byte. The release tag identifies that APK's
source commit; the default branch may contain newer documentation and tests.

## Report a problem

[Open an issue](https://github.com/wildtwins/DeerDeck-public/issues) with your
Android version, app version, steps, and expected/actual behavior. Use fictional
records. Remove names, coordinates, account details, backup paths, and private
photos from screenshots or logs before posting. Do not upload a real backup.
