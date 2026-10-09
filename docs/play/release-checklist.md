# Google Play Release Checklist

## 1. Repository Gate

- [ ] `main` is clean and synchronized with `origin/main`.
- [ ] GitHub Actions Android CI passes on the release commit.
- [ ] `scripts/validate.ps1` passes locally: unit tests, lint, debug APK, and release AAB.
- [ ] No secrets, keystores, phone data, backups, or local `outputs/` files are committed.
- [ ] Release notes and privacy/data-safety documents match the final behavior.

## 2. Version And Binary

- [ ] Confirm application ID `com.wildtwins.deerdeck`. It becomes permanent after first Play upload.
- [ ] Confirm `versionCode 32` and `versionName 2.4.4` are unique.
- [ ] Confirm `targetSdk 36`, `minSdk 26`, and JDK 17.
- [ ] Create the upload key once with `scripts/create-upload-key.ps1`.
- [ ] Store encrypted recovery copies of the keystore and passwords in two separate trusted locations.
- [ ] Build the signed bundle with `scripts/build-play-release.ps1`.
- [ ] Enroll the app in Play App Signing and upload the signed `.aab`, not the APK.

## 3. Store Presence

- [ ] Enable GitHub Pages from the repository `/docs` folder and verify the privacy URL loads without authentication.
- [ ] Enter the app name, descriptions, category, and support email from `store-listing.md`.
- [ ] Upload `play-assets/app-icon-512.png`.
- [ ] Upload `play-assets/feature-graphic-1024x500.png`.
- [ ] Upload at least two final phone screenshots. Capture fresh Observe, Stats, Profiles, and History screens from 2.4.4 before submission.
- [ ] Complete Data Safety using `data-safety.md`, then compare every answer with the current Play definitions.
- [ ] Complete the IARC questionnaire using `content-rating.md`.
- [ ] Set target audience to the intended 13+ groups and mark the app as not designed for children.
- [ ] Declare no ads and no in-app purchases.

## 4. Testing Tracks

- [ ] Upload to Internal testing first and exercise install/update/backup/restore on a non-primary test dataset.
- [ ] Run `docs/beta-smoke-test.md` plus the privacy and deletion checks in the generated production-candidate checklist.
- [ ] Test light/dark mode, denied permissions, offline startup, camera/microphone unavailable, and process death.
- [ ] Test one in-place upgrade from the currently installed version and confirm existing sessions remain intact.
- [ ] Test one fresh install and confirm the first-run privacy choice appears.
- [ ] Complete the account-specific testing and production-access requirements currently shown in Play Console.

## 5. Production Rollout

- [ ] Review Play's pre-launch report and fix crashes, ANRs, accessibility blockers, or policy findings.
- [ ] Start with a staged production rollout instead of 100 percent.
- [ ] Confirm install and critical flows from the Play-delivered build.
- [ ] Monitor Android vitals and user feedback during rollout.
- [ ] Increase rollout only while crash and ANR health remain acceptable.
- [ ] Preserve the exact source commit, mapping file, signed AAB, upload key recovery material, and Play release notes.

## External Actions Still Requiring Brian

These actions require private credentials, account acceptance, or public publishing and are intentionally not automated in the repo:

1. Create/confirm the Play Console developer account and accept agreements.
2. Enable GitHub Pages for the public privacy-policy URL.
3. Create and securely preserve the upload key/passwords.
4. Complete Play Console forms and upload the signed AAB/assets.
5. Recruit testers and run any account-specific closed-testing requirement.
