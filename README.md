# LBP OTG Print

Native Android utility for opening PDFs and images, saving PDFs through Android's print dialog, USB printer diagnostics, and direct monochrome printing to devices that explicitly advertise **PCL 5**.

**Release status: candidate for testing, not a validated Canon LBP6030 driver.** The original AI-generated CARPS2/UFRII LT commands were fabricated. They have been removed. Detecting a Canon printer does not mean the app can print to it. The LBP6030/6040/6018L family needs a genuine, compatible driver and physical validation before it can be supported.

For the reported `04A9:2795`, `CID:CA_UFRIILT_OIP`, `CMD:LIPSLX,CPCA` device, the missing backend is now identified as Canon UFRII LT / NCAP, including its CPCA session layer. Canon's official Linux driver has been obtained and an offline reference harness plus a decoder-validated portable SLIM raster component are included. That component is not a complete USB driver and is not connected to the Print button. See [driver findings](DRIVER_RESEARCH.md) and [reference tooling](tools/canon/README.md).

## Use

1. Choose a PDF, photo, or the built-in test page.
2. Select paper, orientation, scaling, copies, and page ranges. Persian and Arabic digits are accepted.
3. **Print / Save PDF** opens Android's print dialog. Save as PDF works offline. Physical printing needs an installed service compatible with the destination printer; this does not add Canon USB support.
4. For a PCL 5 USB printer, connect an OTG cable, grant USB permission, and run the safe probe in Diagnostics. **Send via USB (PCL 5)** checks compatibility again before transmitting.
5. **Export PCL file** renders a real PCL 5 stream without a printer. Use Share PCL file to export it. “Data sent” is transport delivery, not proof that paper printed.

Supported input: one PDF or JPEG/PNG/WebP image at a time, up to 64 MB. PDFs can contain up to 10,000 pages. USB jobs are capped at 256 MB. Password-protected, malformed, or inaccessible documents produce a recoverable error and preserve the previous selection. Images are downsampled to a maximum 2048-pixel decoded side to bound memory. PDF 100% scaling uses PDF points; image controls offer Fit and Fill.

Android 8.0+ (API 26). Targets API 36. No Internet, broad storage, location, camera, or photo-library permissions. Imported documents stay in private cache; cloud backup is disabled. See [privacy policy](PRIVACY.md).

## Build

Install JDK 21, Android SDK platform 36 and build-tools 36.0.0. Set `ANDROID_HOME` or add `sdk.dir` to an untracked `local.properties`.

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleDebug
./gradlew :app:assembleRelease :app:bundleRelease
```

The second command produces an unsigned release APK and AAB if no signing environment is configured. Signing uses `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS` (default `upload`), and `KEY_PASSWORD`. No private keys or passwords are stored in the repo. Google Play requires a signed AAB and Play App Signing, rather than the testing APK.

See [audit](AUDIT.md), [architecture](ARCHITECTURE.md), [testing](TESTING.md), and [release runbook](RELEASE.md). GitHub Actions builds, tests, lints, and retains APK/AAB artifacts for each change.
