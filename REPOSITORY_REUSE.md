# Requested repository review (2026-10-03)

This review was completed before changing application code. Revisions are pinned
below so upstream updates cannot silently change the conclusions.

| Repository / revision | Useful component and evidence | License / reuse conditions | LBP6030 decision |
| --- | --- | --- | --- |
| [Truenomaxs/canon-ufrii-lt](https://github.com/Truenomaxs/canon-ufrii-lt/tree/d2a8d3735c6abcdb8a89d45b44d77627d87a404d) | `package.nix` pins Canon v5.10; `module.nix` exposes `/usr/lib/Canon/CUPS_SFPR`. README reports LBP6030w USB testing. | MIT covers the Nix packaging. Canon's downloaded executables/libraries have their own proprietary and component-specific terms; the MIT grant does not cover them. | Reuse the package reference and model-selection evidence. No Nix, GLIBC binaries or proprietary Canon libraries bundled in Android. |
| [MopriaAlliance/CUPS-for-Android](https://github.com/MopriaAlliance/CUPS-for-Android/tree/6275e3493490b3126049b2dc17f7ab5de44de3f9) | `Android.mk` builds a CUPS client library including raster stream APIs, backchannel and sidechannel helpers. | Apache-2.0 with retained licenses/notices and modification notices when copying applicable files. Inspect individual vendored files before reuse. | Relevant raster/filter ABI reference, but no Canon codec or Android USB backend in this target. Adding the library alone cannot print this model. Existing Android PDF renderer is retained; no unnecessary CUPS dependency added. |
| [pelya/android-print-plugin-cups](https://github.com/pelya/android-print-plugin-cups/tree/3e2f1dcbeba81c798480f9986951b445b00775a0) | PrintService and PRoot/Debian CUPS process orchestration. README explicitly lists USB forwarding from libusb to Java and 64-bit support as TODOs. | Root LGPLv3; Java headers LGPL-3.0-or-later. Redistribution/linking requires applicable source, notices and user modification/relinking rights. Bundled distro components have separate licenses. | No working OTG bridge to reuse. Its old 32-bit execution/rootfs architecture is not a minimal Android 15 integration. No source copied. |
| [farminos/open-escpos-print-service](https://github.com/farminos/open-escpos-print-service/tree/b5b777171c937dea0a185735844b42d070a89092) | `Utils.kt` selects USB printer-class interfaces and lazily renders PDFs on white; `Drivers.kt` delegates USB to DantSu ESCPOS-ThermalPrinter-Android 3.3.0. | App GPLv3; copying app code into a combined derivative entails GPL obligations. The external transport library needs a separate review; the app's license does not license that dependency. | Architecture comparison only. Existing discovery/rendering already covers these functions. ESC/POS/CPCL encoding excluded. No source or new dependency copied. |
| [elearning-hue/thermal_printer_android_app](https://github.com/elearning-hue/thermal_printer_android_app/tree/40f913fc18bc1401bc3c4ccaf7ab898b1737d532) | `UsbTransport.kt` uses claimInterface and bounded bulk calls; renderer/discovery examples. Its write loop does not reject zero-byte progress. It has no Canon IN/status/MLP path. | No license grant found. Public availability does not authorize copying/distribution. | No code copied. Our existing transport handles partial/zero progress, IN endpoints, permission and ownership; replacing it would remove necessary behavior. |
| [ondrej-zary/carps-cups](https://github.com/ondrej-zary/carps-cups/tree/67fb6a543dcdf3beb459d2928f56acab69c78fdb) | `carps.txt` describes HISCOA bit tokens, variable-length counts, XOR 0x43 and row-bounded compression. CARPS job grammar targets other printer families. | GPLv3 applies to source reuse. This app does not copy/translate/link the C encoder or CARPS job code. Independently implemented protocol behavior is documented and tested against Canon's decoder. | Use only independently verified compression facts. CARPS page headers, CPCA field defaults, status commands and Linux USB quirks are excluded. |

No runtime code from these six repositories is incorporated. The concrete
integration is an independently implemented, row-bounded SLIM copy token in the
existing Kotlin encoder, informed by the documented compression and verified
against Canon's native implementation. This does not change the app's license
or bundle GPL/proprietary components. These are identified license obligations,
not a blanket clearance for all upstream files or patents.

## Why JBIG is not added

The MIT Nix package downloads Canon v5.10 from
`https://gdlp01.c-wss.com/gds/1/0100005951/11/linux-UFRIILT-drv-v510-us.tar.gz`
with SHA-256 `5091d2d7dc58e2b2f0e27c26ea62a0dced69c94d8b55fd0fd4e8f68ab895b4c0`.
Its JBIG dependency is not proof that every supported printer uses JBIG.
`CNRCUPSLBP6030ZNS.ppd` selects `rastertosfp`, `ncapfilterr`, `libcanonncapr`,
600 DPI and `CNOutputDepth: 2`. The reference NCAP encoder calls
`lCaptCompEx` in `libcanon_slimsfp`. The source glue/JBIG wrapper does not
supply the portable core NCAP/SLIM/session implementations.

The practical pipeline remains:

`PDF/image -> existing Android renderer -> packed 2-bit 600-DPI bands -> SLIM -> NCAP -> CPCA job envelope -> Canon MLP -> UsbDeviceConnection`.

Raw official `.prn` jobs bypass rendering and compression completely.

## Evidence and integration scope

* **Verified offline:** LBP6030 PPD/filter choice; existing CPCA/NCAP/MLP oracle
  comparisons; SLIM zero/literal and count/prefix bit syntax; copy-back distance
  **3** with model parameters `3,9,6,1,0,0,80,0`. The CARPS last-byte interpretation
  fails these settings and was rejected before integration.
* **Verified offline:** 576 preliminary native-decoder bands cover constant,
  random, sparse, multiple-row and final/intermediate-band inputs. Output
  canaries and every byte are checked. Committed fixtures and complete generated
  jobs are additionally verified by CI against Canon's decoder.
  All 112 committed copy fixtures also pass the pinned v5.10 decoder. Its
  `include/libcanon_slim.h` describes `bXOffset[2]` in the compression settings;
  the LBP6030 band's first offsets are 3 and 9, consistent with the measured
  default copy-back distance of three.
* **Unverified on hardware:** firmware acceptance, page processing, completion,
  actual service names, CPCA status exchanges and physical printing. Existing
  MLP credit responses establish flow control only.

The encoder copies from three bytes back only after three identical bytes have
been emitted in the same row. Runs stop at each row boundary, split within the
count-prefix limit, and retain the native NCAP final-band controls and padding.
No transport retries, channel assignments, ZLP policy, USB discovery or UI are
changed based on unrelated thermal-printer behavior.

The deciding hardware test remains an untouched Canon-generated PRN through
the existing raw-job path. If that fails, the required comparison is the actual
official-driver USB exchange; compression success alone does not resolve an
unverified device/session handshake.
