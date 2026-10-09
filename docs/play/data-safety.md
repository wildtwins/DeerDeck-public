# Google Play Data Safety Worksheet

Use this as the source of truth when completing Play Console. Revalidate it whenever permissions, SDKs, or network behavior change.

## Top-Level Answers

- **Does the app collect or share any required user data types?** Yes.
- **Is all collected user data encrypted in transit?** Yes. Weather and elevation requests use HTTPS.
- **Can users request deletion?** Yes. Users can erase the local dataset in the app. The developer does not operate an account or server holding user data.
- **Independent security review?** No.
- **Account creation?** No account system exists.

## Disclosed Data

| Data type | Collected | Shared | Required | Processing | Purpose |
| --- | --- | --- | --- | --- | --- |
| Approximate location | Yes | Yes, with the National Weather Service and USGS | No | Ephemeral network processing plus optional local session storage | App functionality |
| Precise location | Yes | Yes, with the National Weather Service and USGS | No | Ephemeral network processing plus optional local session storage | App functionality |

Location is optional. The user may decline the first-run field-condition choice, deny Android location permission, or disable field conditions later. Wildlife and bird regional defaults are selected locally from the captured coordinates; this does not create another network request. There is no user account, advertising ID, or developer backend that links a weather request to a profile maintained by the developer.

## Local Or User-Directed Data

The following data remains on-device and is not transmitted to the developer: observations, wildlife counts, tags, notes, locally recorded voice notes, photos, videos, media references, profile choices, and settings.

User-directed backup/export destinations include phone storage, a folder selected through Android's Storage Access Framework, Google Drive when the user selects Drive in that picker, or a share target selected by the user. These transfers are initiated or configured by the user and do not pass through a developer-operated service.

Optional Android speech-to-text uses the speech recognition provider configured on the device. DeerDeck does not embed an analytics, advertising, authentication, or speech cloud SDK.

## Third Parties And SDK Inventory

- United States National Weather Service (`api.weather.gov`) for optional current weather and forecast details.
- USGS Elevation Point Query Service (`epqs.nationalmap.gov`) for optional elevation lookup.
- Android system location, media, document picker, and speech-recognition services.
- No ad SDK, analytics SDK, crash-reporting SDK, social SDK, or developer cloud backend.

## Revalidation Commands

```powershell
rg "https?://|INTERNET|ACCESS_.*LOCATION|RECORD_AUDIO|CAMERA" app/src/main
.\gradlew.bat :app:dependencies
```

Check the final Play Console declarations against Google's current definitions before every production submission. When uncertain, disclose conservatively.
