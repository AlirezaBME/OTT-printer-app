# LBP OTG Print

Native Android utility for PDF/image printing over USB OTG, Android Print / Save PDF, and USB diagnostics.

**Release status: Canon printing backend implemented and software-verified; physical printer acceptance pending.** Version `1.1.0-rc4` corrects the final-band continuation and SLIM page-end controls omitted by rc3's Canon UFRII LT / NCAP + CPCA backend for `04A9:2795`, with `CID:CA_UFRIILT_OIP` and `CMD:LIPSLX,CPCA`. It also supports printers explicitly advertising PCL 5. Other Canon models and CARPS2 are rejected. The invented original encoders remain removed; no Canon proprietary binaries are bundled.

Canon's checksum-pinned official driver serves as an offline oracle: all twelve generated A4/A5/Letter portrait/landscape jobs decode correctly through Canon's native decoder, comparing every pixel across 2,332 bands. CPCA setup/footer packets and NCAP framing are compared with official driver output. This validates software serialization, not paper output. No physical printer is attached to the development workspace. See [driver evidence](DRIVER_RESEARCH.md) and [reference tooling](tools/canon/README.md).

## Use

1. Connect the printer to the Android phone with an OTG cable. Power it on and load paper.
2. Choose a PDF, photo, or the built-in test page. Select paper, orientation, copies and page range. Persian and Arabic digits are accepted.
3. Leave the driver on **Automatic USB driver**. **Print via USB** requests USB permission if needed, probes compatibility/status, renders and submits a Canon or PCL job directly. It does not open Android's print dialog.
4. **Android print / Save PDF** opens Android's print dialog. Other apps can also use this app's USB print service after it is enabled in Android printing settings and USB permission is granted.
5. **Export PCL file** produces a PCL stream without a printer. The main button creates the export; **Share print stream** shares the last completed `.pcl` or Canon `.prn` spool.

Canon Draft mode renders at 300 DPI and doubles pixels into the printer's required 600 DPI format. Standard mode renders at 600 DPI. Both use two-bit black/white raster data, correct paper framing and a bounded literal SLIM encoding. This first compatible encoder favors correctness over compression: an A4 draft page is about 9–13 MB. Copies are rendered in order and counted in the CPCA footer. A job cancelled during transfer resets the partial USB input buffer; pages already accepted may still print. “Data sent” confirms transport delivery; inspect the paper result.

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
