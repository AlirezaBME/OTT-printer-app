# Release runbook

This is a testing candidate. The Canon LBP6030 NCAP/CPCA backend is implemented and software-verified. Physical printer acceptance is required before production claims. See AUDIT.md.

## Artifacts and identity

- Application ID remains `com.aistudio.lbpotgprint.kxvlzp` to preserve the repository's identity.
- Candidate: version code 4, version name 1.1.0-rc3; Android 8.0+, target SDK 36. This adds direct Canon USB job generation, automatic driver selection, official-driver oracle tests, and a separate Android print/save action.
- The downloadable candidate APK is an optimized, non-debuggable release build signed with a newly generated **candidate testing certificate**. That certificate is not an existing Play upload key. Devices with an earlier APK signed by another certificate require uninstalling it before installing this candidate; uninstall removes app data.
- `app-release.aab` is unsigned unless the release-signing environment is supplied. Preserve the owner-controlled production upload key and enable Play App Signing. Never commit keys or passwords.

```sh
export KEYSTORE_PATH=/secure/path/upload.jks
export KEY_ALIAS=upload
# Supply STORE_PASSWORD and KEY_PASSWORD securely through your build environment.
./gradlew :app:testDebugUnitTest :app:lintRelease :app:bundleRelease
```

Increase versionCode above any previously uploaded Play version. An existing published package must retain its established signing identity. The candidate APK is for sideloading/testing, not a production identity choice.

## Play Console submission

1. Complete the physical acceptance gates in AUDIT.md for the functionality advertised by the final app.
2. Obtain access to the developer's Play Console account and this app, verify developer details and enroll in Play App Signing. The build workspace has no Play publishing credentials.
3. Upload the owner-signed AAB to internal testing first. Run Play's pre-launch report on physical device configurations. Complete any closed-testing requirement imposed on the account.
4. Provide the store icon, feature graphic, phone/tablet screenshots, support details, accessible privacy-policy URL, app-access/content-rating/ads/target-audience/data-safety declarations and regional distribution settings. PRIVACY.md and STORE_LISTING.md are prepared as drafts; the owner must verify account-specific declarations.
5. Check target API requirements in the Console at submission time. Validate APK zip/native-library alignment for the 16 KB page-size requirement. This app does not add proprietary native printer libraries.
6. Publish only after acceptance evidence matches the listing. Google review/approval is separate from building an APK/AAB.

## Release verification

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:bundleRelease
./gradlew :app:connectedDebugAndroidTest  # attached Android emulator/device
apksigner verify --verbose candidate.apk
zipalign -c -P 16 -v 4 candidate.apk
```

GitHub Actions retains unsigned release artifacts and test/lint reports; it does not pretend to deploy to Google Play. Production signing can be configured securely in a release environment using the documented variables.
