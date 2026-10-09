# Withrow DeerDeck 2.4.4 Production Smoke Test

Use this checklist after installing the debug APK on an Android phone. Create a verified backup before destructive tests.

## Install

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check-device.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\install-debug.ps1
```

Manual fallback:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package-beta.ps1
```

Then transfer `.dist-codex\withrow-deerdeck-2.4.4\Withrow-DeerDeck-2.4.4-debug.apk` to the phone.

## Smoke Test

1. On a fresh install, confirm the privacy notice blocks the app until field conditions or no-location use is selected.
2. Confirm the app uses the blue theme, opens to `Observe`, and the nav order is `Observe`, `Stats`, `History`, `Profiles`, `Settings`.
3. Confirm Stats shows activity score, one-year-ago context, Similar Days, Best Conditions, 30-day trend, and wildlife share.
4. Confirm Settings includes theme, Private Sharing, Field Mode, keep-screen-awake, media destination, and haptics; profile switching belongs only in Profiles.
5. Confirm Profiles starts with locked Wildlife and Birds profiles and an add-profile button.
6. Create, rename, switch, and delete multiple custom profiles. Confirm each custom profile has an independent editable wildlife list.
7. Confirm built-in wildlife cannot be added, removed, recolored, or reordered, while custom-profile tiles can be reordered by long-pressing and dragging.
8. Start a location-enabled session and confirm the selected built-in profile updates to a regional list without sending an additional profile request.
9. Start a session, open Profiles, and confirm switching or deleting profiles is blocked until the session ends.
10. Enable Field Mode and confirm the larger controls remain readable without overlap.
11. Start a session, grant location, and confirm GPS, sunset, weather, pressure/humidity when available, and moon phase populate.
12. Disable field conditions, start another session, and confirm no location permission is requested while logging still works.
13. Tap Doe three times and another tile once; confirm Sightings and Types update correctly.
14. Long-press a tile, choose descriptors, save a detailed observation, and undo one observation.
15. Capture multiple photos with zoom and confirm the camera remains open until closed. Verify session attachment and configured media destination.
16. Record a video and confirm it appears in the session.
17. Record an AAC session audio note and confirm History can open it.
18. End a session with notes, Cloudy or Snow, a custom tag, and speech-to-text input.
19. Search History by wildlife, tag, condition, and location; exercise 30-day, one-year, and all-time filters.
20. Confirm timeline, gallery, media, notes, GPS, weather, pressure/humidity, and moon details render without overlap.
21. Delete one observation and one session; confirm both require confirmation.
22. Open a GPS-backed session in Maps and confirm the point.
23. Exercise privacy-filtered CSV/ZIP sharing and confirm location/name redaction matches Settings.
24. Export a full archive and confirm backup JSON plus readable photo, video, and audio files are present.
25. Verify phone, Google Drive, and Both backup destinations using folders selected through Android's picker.
26. Restore a full package on a disposable dataset and confirm the pre-restore safety backup is reported.
27. Leave a session inactive for three hours or relaunch an old open session; confirm it auto-ends.
28. Deny camera, microphone, and location permissions individually; confirm the app explains the missing capability and does not crash.
29. Switch light, dark, and system themes and inspect compact and large text layouts.
30. Type `DELETE` in Settings > Privacy & Data > Erase local data on a disposable dataset. Confirm app-managed media is removed while camera-roll media and exported backups remain.
