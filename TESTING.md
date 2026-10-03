# Verification

## Automated checks

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:bundleRelease
./gradlew :app:connectedDebugAndroidTest
```

Host tests cover strict/Persian page ranges, settings validation, explicit PCL 5 detection, rejection of unavailable Canon encoders, locale-independent IEEE-1284 parsing, raster packing, per-page PCL framing, printer status, copies, cancellation, overlapping jobs, lock release, offline file export, transparency/dithering and margin clipping. Raster/lifecycle tests run on simulated APIs 28 and 36.

Native PDF tests run on an Android emulator/device: PDF snapshots, concurrent previews, original-file removal, physical-size/landscape rendering, duplicate ownership, malformed-file cleanup, bounded incremental PDF export and actual exported-page rendering. Robolectric's native PDF stubs do not establish native PdfRenderer correctness, so these tests belong in androidTest.

Compose device tests exercise launch/preview, language toggling, disabled USB output without permission, diagnostics, offline PCL generation and terminal dialog dismissal. GitHub Actions runs the host checks and API 35 device tests and saves reports.

See VERIFICATION.md for the results of this specific candidate, including any test environment limitations. Passing software tests does not establish physical print compatibility.

## Physical acceptance (required before production claims)

Use real API 26/28/35/36 phones with USB OTG and representative PCL 5 printers. Canon LBP6030 tests cannot proceed until a real Canon backend exists.

| Scenario | Required evidence |
| --- | --- |
| Attach and permission grant/deny/reconnect | Correct device selection, no crashes, request cannot be spoofed or overwritten |
| Safe probe | Correct IEEE-1284 string and status; no transmitted document bytes |
| PCL test page, text PDF, image | Correct physical page content, media, margins and orientation |
| Multi-page PDF and multiple copies | Exact selected pages and copy count in document order |
| Paper empty, offline or error | Recoverable failure; no false completion |
| Cable removal during a write | Bounded failure and closed connection; reconnect works |
| Cancellation during render/send | Busy state clears, USB lock releases, no automatic duplicate retry; previously sent pages may still print |
| Probe/second app PrintService job during a UI job | No competing opens or closes; recoverable busy state |
| Low-memory device | 300 DPI works; 600 DPI is rejected below 256 MB memory class |
| Rotation, background, external share, malformed/encrypted file | No closed-document races or activity crashes; invalid import preserves prior document |
| Android Save PDF, installed print service and app PrintService | Page subsets/copies, layout/cancel behavior and final output verified |
| Release on 16 KB page-size Android | Startup, imports, exports and printing verified using the optimized APK |

Retain the phone/printer model, OS version, diagnostics report, printed pages/photos and candidate APK SHA-256 for each result. Do not mark milestones as passed without that evidence.
