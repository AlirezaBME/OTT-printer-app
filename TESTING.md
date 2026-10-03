# Verification

## Automated checks

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:bundleRelease
./gradlew :app:connectedDebugAndroidTest
```

Host tests cover strict/Persian page ranges, settings validation, explicit PCL 5 detection, rejection of invented CARPS2 and unknown Canon identities, locale-independent IEEE-1284 parsing, raster packing, per-page PCL framing, printer status, copies, cancellation, overlapping jobs, lock release, offline file export, transparency/dithering and margin clipping. Raster/lifecycle tests run on simulated APIs 28 and 36.

The reported Canon identity is covered explicitly. Tests validate exact model/language selection, CPCA messages against the official module, native NCAP frames, packet length boundaries, incomplete jobs and raster/media validation. Simulated USB tests exercise the actual job manager: Automatic routes Canon without PCL, transmits the exact `.prn` spool, expands copies and emits impression counters, blocks paper-out before rendering, and resets a cancelled partial submission while releasing the USB lock. The portable SLIM codec retains its 60 independently decoded literal vectors and adds 112 compressed copy/count/prefix fixtures.

CI repeats the official-driver oracles, including twelve complete Android-generated A4/A5/Letter portrait/landscape jobs, 2,332 bands, and comparisons of every decoded pixel. See tools/canon/README.md. Software verification does not establish physical printer acceptance.

Native PDF tests run on an Android emulator/device: PDF snapshots, concurrent previews, original-file removal, physical-size/landscape rendering, duplicate ownership, malformed-file cleanup, bounded incremental PDF export and actual exported-page rendering. Robolectric's native PDF stubs do not establish native PdfRenderer correctness, so these tests belong in androidTest.

Compose device tests exercise launch/preview, language toggling, direct USB action reporting missing printer, diagnostics, offline PCL generation and terminal dialog dismissal. GitHub Actions runs the host checks and API 35 device tests and saves reports.

See VERIFICATION.md for the results of this specific candidate, including any test environment limitations. Passing software tests does not establish physical print compatibility.

## Physical acceptance (required before production claims)

Use real API 26/28/35/36 phones with USB OTG, the reported Canon LBP6030 `04A9:2795`, and representative PCL 5 printers. rc4 fixes page-finalization controls missed by rc3. The oracle now checks both final controls against the complete official filter stream; a two-page regression verifies each page independently.

| Scenario | Required evidence |
| --- | --- |
| Attach and permission grant/deny/reconnect | Correct device selection, no crashes, request cannot be spoofed or overwritten |
| Safe probe | Correct IEEE-1284 string and status; no transmitted document bytes |
| Canon/PCL test page, text PDF, image | Correct physical page content, media, margins and orientation |
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

## rc6 isolation milestone

Before changing the encoder again, run the untouched official Windows Canon PRN procedure in [CANON_ISOLATION.md](CANON_ISOLATION.md). Record physical paper output independently from transport replies, with binary capture enabled and the exact imported/transmitted SHA-256. If it prints, compare the generated job language; if it fails, compare MLP/channel/CPCA/USB lifecycle against a successful official-driver USBPcap exchange. Current owner reports establish failure for rc3–rc6. rc7 compression compatibility is offline evidence only.

Host coverage includes raw spool/transmitted/export byte equality, parser/frame maximums, credit classification, endpoint edge sizes 511/512/513/8186/8192/8193, snapshot mutation, ambiguous write prefix capture, observation timeout/port error, cancellation and ownership cleanup. A stored A5 source-pattern SHA-256 detects encoder drift. Device coverage includes diagnostics raw PRN controls and binary capture opt-in. Neither the native oracle nor the emulator substitutes for an official PRN hardware test.

## rc7 compression regression coverage

The gzip-compressed fixture files are byte-identical in host test resources
and offline oracle tooling. Cases include counts 0–5, powers of two and nearby
counts, 127/128/129, long prefix split boundaries, all-white/black and 0x43 runs,
changing row colors, sparse data and random bytes at multiple bit phases. A
maximum 100-KiB band verifies bounded output. Every fixture has native decoder
row-count, pixel, consumption and output-canary checks. Kotlin output must
match each fixture exactly and never exceed the retained literal fallback.
The blank A4 band regression checks 220 encoded bytes.
