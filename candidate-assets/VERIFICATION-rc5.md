# Canon direct USB candidate verification — 2026-10-03

Candidate 1.1.0-rc5 (version code 6); minimum SDK 26, target SDK 36; application ID com.aistudio.lbpotgprint.kxvlzp.

rc3 and rc4 produced no paper on the owner's LBP6030 despite accepted USB writes. rc5 adds the missing USB MLP transport below CPCA: initialization, three host/printer channel pairs, negotiated packet sizes, separate header/payload USB transfers, data replies and channel-close acknowledgements. It retains rc4's correct page-final controls. This remains a hardware testing candidate.

## Source and package provenance

GitHub PR #5: https://github.com/AlirezaBME/OTT-printer-app/pull/5

CI source commit: `0f586f4c5a26e2c2b098bacbff392e77aefee154`.
Merged main commit: `3bf1b22370069eaffd2da144832053f8598e5c61`.
Verified Git tree: `f9497b18948d373cfd8f985954aea326fd22146d`; the merged tree is identical.

CI run: https://github.com/AlirezaBME/OTT-printer-app/actions/runs/37119734524

All three jobs passed. APK and AAB originate from this exact CI run. The release APK uses the same candidate testing signing identity as rc1–rc4. The unsigned AAB needs the owner's upload identity before Play upload. No Canon binaries or signing credentials are bundled in the source, APK or toolkit.

## Verification

- 45 host tests passed, with zero failures, errors or skips. Coverage includes negotiated packet sizes, fragmented replies, unchanged CPCA spool bytes, exact USB transfer boundaries, missing/rejected initialization and data acknowledgements, bad sockets, rejected close, ambiguous writes, cancellation/reset and USB ownership cleanup.
- 9 Android API 35 emulator tests passed, with zero failures, errors or skips.
- Release lint passed with zero errors. Debug APK, release APK and release AAB builds passed.
- 16 transport vectors checked through Canon v5.00's actual USB MLP library. Real Info initialization was retained to prove multi_usb_ncap and USB MLP plugin selection; native methods verify initialization, all three channel-open/close pairs, six data sizes, separate header/payload writes and one-credit receive semantics.
- 60 SLIM vectors decoded exactly through Canon's native decoder, with row counts and output canaries checked.
- 24 fixed CPCA messages and 11 NCAP framing fixtures match official driver output.
- 12 complete Android jobs passed the native decoder: every pixel across 2,332 bands compared with independently constructed expectations. Final-band continuation and SLIM termination controls are checked in every page.
- The non-debuggable APK passes signature verification and 16 KB ZIP alignment. All four native libraries have ELF LOAD alignment >=16 KB. Google bundletool validates the AAB.

APK SHA-256: `34d0f20fdb985e185bd47557ab49ec3d96d6c02735e27bd06467a1561621e609`

AAB SHA-256: `82531688ca764bfc7c9c33609547568bb13406136bb0685899375aa2527cf6cf`

## Physical and release limits

No printer is attached to this workspace. These checks establish software interoperability with Canon's official library; they do not establish physical output. MLP replies acknowledge transport, not paper. The owner must power-cycle after rc3/rc4's invalid raw streams and reconnect OTG before the first rc5 test. Diagnostics include initialization, negotiated channels, packet/reply counts and any failure stage.

Writes and reads have finite deadlines; ambiguous submissions are not retried. Cancellation resets partial jobs and releases USB ownership. Literal raster coding produces larger jobs than Canon's optimized compressor; physical throughput and repeated-job acceptance still need hardware verification.

This prerelease uses a testing signing identity. No Play Console credentials or owner upload key are available, and no Google Play publication occurred. Production acceptance remains blocked by physical print verification and the owner's Play release setup.
