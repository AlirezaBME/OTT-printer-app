# Production audit — 2026-10-02

## Assessment

The starting repository is a native Kotlin/Compose Android app, not merely a web UI. Its fundamental defect is an invented printing protocol. The CARPS2 encoder emitted ESC/ASCII framing and the UFRII encoder emitted invented ESC-U packets. The referenced `ondrej-zary/carps-cups` code uses binary CARPS framing (`CD CA 10...`) and does not substantiate CARPS2 support for the target LBP family. `henrya/carps2lbp-drv-arch` packages Canon's UFRII LT driver; a Linux package is not an Android driver. Successful USB writes cannot validate a printer language.

No physical Canon printer or USB-capable Android device is attached to this workspace. No Play Console identity, existing upload key, or store configuration is supplied. Therefore a claim that Canon printing is production-ready or that Google Play publication succeeded would be false.

## Work completed

| Defect | Repair |
| --- | --- |
| Fabricated CARPS2/UFRII commands, unsafe universal PCL fallback | Removed Canon encoders; reject unavailable driver selections; require explicit IEEE-1284 PCL 5 support before USB writes |
| “Completed” after arbitrary two-second paper-ejection delay | Removed delay and claims of confirmed paper output; distinguish file export and transport delivery |
| Build needs missing keystores, wrapper jar/scripts and unused plugin inputs | Restored Gradle wrapper; standard debug signing; conditional release signing; removed unused Firebase/secrets/KSP/Room scaffolding |
| Replacing a job cancels it while the old coroutine can close the new connection | Reject overlapping jobs and use one application-wide USB mutex across UI, print service and diagnostics |
| Unbounded encoded document and full-page pixel/gray arrays | Disk spool capped at 256 MB; dithering uses three scanlines; direct PDF raster rendering avoids a second paper bitmap |
| Unsafe page-range parsing silently ignores typos or loops over enormous ranges | Strict bounded validation, normalized Persian/Arabic digits, explicit errors |
| PDF renderer and imports opened on main thread; stale preview/document races | Background imports; renderer locking; serialized document replacement; keep previous document on failure; separate snapshots for system printing |
| Streams and temporary documents leak on failure/cancellation | Bounded imports, explicit snapshot ownership, finally cleanup and cancellation/join before close |
| USB fallback claims arbitrary bulk-out interfaces | Printer-class protocol 1/2 only; validate OUT endpoint and alternate settings; correct GET_DEVICE_ID interface/alternate-setting index |
| USB transfer ignores coroutine cancellation and rejects valid short writes | Check cancellation between bounded transfers; resume positive partial writes without resending bytes |
| Permission callback is overwritten by another request | Per-device request matching, coalesced callbacks, immutable scoped PendingIntent, detach cleanup |
| Print service advertises a phantom Canon printer and calls framework methods on IO threads | Advertise confirmed PCL printers; collect discovery state; main-thread callbacks; sequential jobs; honor media, resolution, orientation and copies |
| Declared SEND_MULTIPLE is unimplemented | Remove unsupported intent filter; accept single content URIs only |
| Diagnostics sharing from Application crashes without NEW_TASK | Chooser includes NEW_TASK and URI grants/ClipData |
| FileProvider exposes whole cache and files directories | Expose only the exports directory; disable backup |
| Dialog dismiss cancels but never clears completed state | Explicit terminal-state dismissal |
| Generic print action encourages incompatible Canon sends | Primary Android print/save flow, clear compatibility explanation, separate direct USB action |
| Dark-mode muted text and portrait-only preview | Theme-aware muted text and aspect-correct preview |

## Release gates

1. Canon-targeted release: complete and independently validate the Canon UFRII LT / NCAP backend, including CPCA sessions, identified by the supplied `CA_UFRIILT_OIP` report. The decoder-validated SLIM raster component is one milestone, not a working driver. A transport or generic PCL encoder cannot substitute for it; see DRIVER_RESEARCH.md.
2. Physical acceptance: real Android OTG devices and printers, correct test page/PDF/photo output, multiple copies/pages, paper-out, disconnect, denied permission, cancel and reconnect. Software tests do not replace this.
3. Play publication: owner-controlled stable upload key, Play App Signing enrollment, Console access, verified developer account, listing assets/content rating/data-safety declarations and any required closed testing. No account-specific declarations have been fabricated.
4. Confirm supported printer models and marketing claims from physical evidence. The candidate intentionally does not advertise Canon print compatibility.

The signed testing APK uses a separate candidate certificate. It must not establish the production signing identity of an existing Play app.
