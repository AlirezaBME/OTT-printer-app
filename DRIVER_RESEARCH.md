# Driver findings

The original AI-generated claims of a “genuine CARPS2” and “UFRII LT” implementation were unsupported. The invented encoders have been removed. A portable raster-compression milestone now exists, but a complete Canon USB backend remains unavailable.

References inspected on 2026-10-02:

- https://github.com/ondrej-zary/carps-cups — a CARPS CUPS implementation using binary framing; its source does not validate the original app's ESC/ASCII CARPS2 packets or LBP6030 support.
- https://github.com/henrya/carps2lbp-drv-arch — packaging for Canon's Linux UFRII LT driver, including the LBP6030/6040/6018L family. Packaging a Linux driver does not supply an Android-compatible backend.
- USB Printer Class specification — printer enumeration, GET_DEVICE_ID and GET_PORT_STATUS establish transport/identity/status only.
- HP PCL 5 raster commands — the app implements uncompressed monochrome raster transfer. PCL XL/PCL 6-only printers cannot accept this PCL 5 stream and are rejected unless they separately advertise PCL 5.

## Physical identity supplied on 2026-10-03

The user reports a working Android OTG connection to `04A9:2795`, printer-class interface with Bulk OUT `01`, Bulk IN `82`, permission granted and port status ready. Its IEEE-1284 report contains `CID:CA_UFRIILT_OIP` and `CMD:LIPSLX,CPCA`, with no PCL 5 advertisement. This narrows the missing backend to Canon UFRII LT / NCAP and CPCA session handling. It does not justify a CARPS2 implementation or a PCL fallback. The report is user-supplied evidence; no physical printer is attached to the development workspace.

Diagnostics now expose CID explicitly, recognize the UFRII LT compatibility identifier even when CMD does not spell UFRII, and report the actual APK version. Recognition does not mark a driver available or bypass the PCL guard.

## Official packages obtained and inspected

| Package | Canon URL and verified SHA-256 |
| --- | --- |
| v5.00, x86/x86_64 | `https://gdlp01.c-wss.com/gds/0/0100005950/10/linux-UFRIILT-drv-v500-uken-18.tar.gz`; `46888140016bc1096694a0fd6fd3f6ad393970b8153756373a382dc82390f259` |
| v5.10, x86/x86_64/ARM64 | `https://gdlp01.c-wss.com/gds/1/0100005951/11/linux-UFRIILT-drv-v510-us.tar.gz`; `5091d2d7dc58e2b2f0e27c26ea62a0dced69c94d8b55fd0fd4e8f68ab895b4c0` |

The v5.00 SHA-512 also matches the earlier Arch packaging reference. The v5.10 SHA-256 matches the Nix packaging reference at https://github.com/Truenomaxs/canon-ufrii-lt. The archives were obtained directly from Canon; no user upload was necessary.

The LBP6030/6040/6018L PPD selects `rastertosfp`, `CNOEFLibName:ncapfilterr`, `CN_PdlWrapper_PdlPath:libcanonncapr`, `CNPDLType:HB`, 600 DPI, output depth 2 and bidirectional USB communication. The page-encoding chain is:

```text
CUPS raster → rastertosfp → cnrsdrvsfp → libncapfilterr
                                       → libcanonncapr → libcanon_slimsfp
                                       → cnpkmodulencapr → CPCA communication → USB
```

The supplied source includes the raster-to-process glue, `cnpklib` process/output orchestration and JBIG wrapper. It does **not** contain the core NCAP page encoder, SLIM compressor, session module or CPCA communication implementation as portable source. Those components are delivered as ELF/static binaries. JBIG's presence does not imply that this specific model's default raster path uses JBIG; the inspected NCAP library imports `lCaptCompEx` from the SLIM library.

The v5.10 ARM64 filter uses `/lib/ld-linux-aarch64.so.1`; its page library requires `libc.so.6` and GLIBC_2.17 symbols. ARM64 CPU compatibility alone does not make these Android/Bionic libraries. The proprietary components are listed under Schedule 1 of Canon's package licence. They have not been bundled, modified or represented as redistributable Android dependencies.

## Verified encoding milestone

The new [offline oracle](tools/canon/README.md) executes the checksum-pinned official v5.00 driver locally, without opening a printer. It generated a 39,657-byte NCAP PDL stream for the provided test input, SHA-256 `fc0d05454b81f646496baa359e66c3d332cb391d0e3606ceec045aea57971dbf`. This output is page-filter data from the no-session fallback, **not a successful physical print or a complete CPCA USB capture**.

`CanonSlimRasterCodec` is an independent, bounded Kotlin implementation of SLIM/HISCOA's literal subset. Its 60 fixtures are accepted by Canon's actual `lCaptDecode`, with exact reconstructed bytes, correct row counts and intact output canaries. Host tests verify Kotlin output against those fixtures. It handles raster bands only; it does not frame NCAP pages, establish CPCA sessions or confirm paper output. DriverRegistry deliberately leaves Canon unavailable.

No Canon proprietary binaries or third-party driver source were copied into the app. Complete NCAP framing, CPCA session/response handling, and physical acceptance remain outstanding; [the implementation milestones and capture requirements](tools/canon/README.md#remaining-implementation) describe the concrete next work. A real Canon backend remains a release blocker for a product promising direct LBP6030 USB printing. See AUDIT.md and TESTING.md.
