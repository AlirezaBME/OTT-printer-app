# Candidate verification — 2026-10-03

This is the historical **1.1.0-rc1** record. The subsequent **1.1.0-rc2** Canon research/diagnostics candidate has its own [verification report in the release assets](https://github.com/AlirezaBME/OTT-printer-app/releases/download/v1.1.0-rc2/VERIFICATION-rc2.md). Neither candidate implements a complete Canon USB driver.

Version 1.1.0-rc1 (version code 2), application ID `com.aistudio.lbpotgprint.kxvlzp`.

## Reproducible source and results

The APK and AAB were built from Git tree `3d082d0ad060652654441e9ff315a9eb44605993`, published as commit `8284cdd4282298d69d30b2f1652693196c6e7366`. Later verification documentation does not change the application binaries.

[GitHub Actions run 37080591986](https://github.com/AlirezaBME/OTT-printer-app/actions/runs/37080591986) passed both jobs against this exact application source:

| Check | Result |
| --- | --- |
| Host regression tests | 21 passed; zero failures, errors or skips. Lifecycle/raster tests cover APIs 28 and 36. |
| Android instrumentation | 9 passed; zero failures, errors or skips on an API 35 x86_64 emulator. Native PDF rendering/export and Compose flows are included. |
| Release lint | Passed with zero errors. Remaining library/tool update notices are advisory. |
| Build | Debug APK, optimized release APK, Android test APK and release AAB built successfully. |
| APK signature | Valid v2/v3 signature using a candidate testing certificate. |
| APK alignment | `zipalign -c -P 16 -v 4` passed. All included native ELF LOAD segments have at least 16 KB alignment. |
| Bundle validation | Google bundletool validation passed. Published AAB is unsigned. |
| Manifest | Non-debuggable release; minimum SDK 26; target SDK 36; no Internet permission. |

The local release build used JDK 21, Gradle 9.3.1 and Android build tools 36.0.0. CI independently reproduced the host checks and builds, and ran the device suite with hardware-accelerated emulation. Detailed host/lint/device reports and binary validation output accompany the GitHub prerelease.

## Published binary checksums

```text
13d536c0ffb2383a0eff1d6ef6cff634c2b9c93a5d7974b32868bc0bd3951f24  LBP-OTG-Print-1.1.0-rc1.apk
bfbf7eda21d6ec9529d08a1b5497a44001447d06b3ae268ed7116de20997d597  LBP-OTG-Print-1.1.0-rc1-unsigned.aab
```

## Limits of this evidence

This is a **testing release candidate**, not production certification. No physical USB printer or Android OTG phone was available. Real PCL 5 printing, disconnect/reconnect, permission behavior across vendors, low-memory physical devices and 16 KB page-size runtime acceptance remain subject to TESTING.md.

Canon LBP6030/6040/6018L direct printing is unsupported: the fabricated original encoders were removed and no independently validated Canon backend exists. Android system printing requires a compatible installed print service for physical output; Save as PDF can operate independently.

Google Play was not published. The workspace has no owner's Play Console access or established upload-signing identity. RELEASE.md records the signing, account, store and physical acceptance requirements. Passing automated checks does not establish paper output or guarantee an absence of bugs.
