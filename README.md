# LBP OTG Print

Native Android utility for PDF/image printing over USB OTG, Android Print / Save PDF, and USB diagnostics.

**Release status: protocol debugging candidate; rc3–rc6 produced no paper on the owner's printer.** Version `1.1.0-rc7` adds native-decoder-validated SLIM compression after reviewing the six requested repositories. The prior rc6 features include untouched official Canon PRN import, SHA-256/edge-byte verification, structured MLP/USB binary trace export, device service-name discovery and a bounded post-transfer observation window. The experimental NCAP/CPCA encoder remains separately testable; transport credits never confirm printing. See [the isolation procedure and audit](CANON_ISOLATION.md) and [component/license review](REPOSITORY_REUSE.md).

The target identity is `04A9:2795`, `CID:CA_UFRIILT_OIP`, `CMD:LIPSLX,CPCA`. PCL 5 printers remain supported only when explicitly advertised. Other Canon models and CARPS2 are rejected. The invented original encoders remain removed; no Canon proprietary binaries are bundled.

Canon's checksum-pinned official driver serves as an offline oracle: all twelve generated A4/A5/Letter portrait/landscape jobs decode correctly through Canon's native decoder, comparing every pixel across 2,332 bands. CPCA setup/footer packets and NCAP framing are compared with official driver output. The new transport oracle retains the real Info initialization, verifies selection of Canon's USB MLP plugin and exercises its actual packet serializers and credit handling. This validates software serialization, not paper output. No physical printer is attached to the development workspace. See [driver evidence](DRIVER_RESEARCH.md) and [reference tooling](tools/canon/README.md).

## Use

1. Connect the printer to the Android phone with an OTG cable. Power it on and load paper.
2. Choose a PDF, photo, or the built-in test page. Select paper, orientation, copies and page range. Persian and Arabic digits are accepted.
3. Leave the driver on **Automatic USB driver**. **Print via USB** requests USB permission if needed, probes compatibility/status, renders and submits a Canon or PCL job directly. It does not open Android's print dialog.
4. **Android print / Save PDF** opens Android's print dialog. Other apps can also use this app's USB print service after it is enabled in Android printing settings and USB permission is granted.
5. **Export PCL file** produces a PCL stream without a printer. The main button creates the export; **Share print stream** shares the last completed `.pcl` or Canon `.prn` spool.

Canon Draft mode renders at 300 DPI and doubles pixels into the printer's required 600 DPI format. Standard mode renders at 600 DPI. Both use two-bit black/white raster data, correct paper framing and row-bounded SLIM coding. rc7 adds verified copy-distance-3 tokens: a blank 32-row A4 band uses 220 encoded bytes instead of 39,940 literal bytes. Size depends on page content; incompressible pixels retain the bounded literal fallback. No JBIG or CARPS job grammar is substituted. Copies are rendered in order and counted in the CPCA footer. A job cancelled during transfer resets the partial USB input buffer; pages already accepted may still print. Canon jobs require channel initialization, flow-control replies and channel-close replies. Sessions now observe basic USB port status and passive CPCA responses for 15 seconds before close; job acceptance remains UNKNOWN without verified CPCA status semantics. After failed attempts, power-cycle the printer and reconnect OTG before the next controlled test.

Developer diagnostics provide **Print raw Canon PRN** and opt-in **Capture binary job + USB bytes**. Raw files up to 64 MiB bypass every encoder, and the confirmation displays size, SHA-256 and first/last 64 bytes. Export a session ZIP after each attempt; consult CANON_ISOLATION.md for the official Windows PRN decision test.

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
