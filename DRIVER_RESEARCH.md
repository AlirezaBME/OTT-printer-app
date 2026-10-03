# Driver findings

The original AI-generated claims of a “genuine CARPS2” and “UFRII LT” implementation were unsupported. The invented encoders have been removed. An experimental independent NCAP/CPCA print-stream backend exists for the reported LBP6030 identity. Software encoding is verified against the official driver; physical output remains unverified. rc3, rc4 and rc5 were tested by the owner and produced no paper. See [rc6 isolation audit](CANON_ISOLATION.md).

References inspected on 2026-10-02:

- https://github.com/ondrej-zary/carps-cups — a CARPS CUPS implementation using binary framing; its source does not validate the original app's ESC/ASCII CARPS2 packets or LBP6030 support.
- https://github.com/henrya/carps2lbp-drv-arch — packaging for Canon's Linux UFRII LT driver, including the LBP6030/6040/6018L family. Packaging a Linux driver does not supply an Android-compatible backend.
- USB Printer Class specification — printer enumeration, GET_DEVICE_ID and GET_PORT_STATUS establish transport/identity/status only.
- HP PCL 5 raster commands — the app implements uncompressed monochrome raster transfer. PCL XL/PCL 6-only printers cannot accept this PCL 5 stream and are rejected unless they separately advertise PCL 5.

## Physical identity supplied on 2026-10-03

The user reports a working Android OTG connection to `04A9:2795`, printer-class interface with Bulk OUT `01`, Bulk IN `82`, permission granted and port status ready. Its IEEE-1284 report contains `CID:CA_UFRIILT_OIP` and `CMD:LIPSLX,CPCA`, with no PCL 5 advertisement. This narrows the missing backend to Canon UFRII LT / NCAP and CPCA session handling. It does not justify a CARPS2 implementation or a PCL fallback. The report is user-supplied evidence; no physical printer is attached to the development workspace.

Diagnostics now expose CID explicitly, recognize the UFRII LT compatibility identifier even when CMD does not spell UFRII, and report the actual APK version. Driver selection requires the exact Canon VID/PID plus CID and both command languages. Detection or a generic Canon model string alone does not enable the backend. PCL remains restricted to an explicit PCL 5 advertisement.

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
                                       → cnpkmodulencapr → CPCA communication → USB MLP → USB
```

The supplied source includes the raster-to-process glue, `cnpklib` process/output orchestration and JBIG wrapper. It does **not** contain the core NCAP page encoder, SLIM compressor, session module or CPCA communication implementation as portable source. Those components are delivered as ELF/static binaries. JBIG's presence does not imply that this specific model's default raster path uses JBIG; the inspected NCAP library imports `lCaptCompEx` from the SLIM library.

The v5.10 ARM64 filter uses `/lib/ld-linux-aarch64.so.1`; its page library requires `libc.so.6` and GLIBC_2.17 symbols. ARM64 CPU compatibility alone does not make these Android/Bionic libraries. The proprietary components are listed under Schedule 1 of Canon's package licence. They have not been bundled, modified or represented as redistributable Android dependencies.

## Verified independent backend — rc3

`CanonSlimRasterCodec` uses a literal subset of SLIM/HISCOA. Its 60 vectors round-trip through the official `lCaptDecode` with byte equality, correct row counts and intact output canaries. The Android APK contains independent Kotlin code, not the Canon libraries.

`CanonNcapEncoder` adds the actual job/media/page/band grammar, model version `1000/1089`, 600-DPI resolution, two-bit depth, 128-pixel row alignment, A4/A5/Letter media codes, band coordinates and termination. Each band contains the eight SLIM parameters from the LBP6030 PPD, continuation flag, little-endian compressed-length field, encoded pixels and NCAP terminator. Input at 300 DPI doubles in both axes; landscape is rotated into portrait-fed media. Row padding remains white. Bands are at most 32 rows and independently decodable.

## Page-finalization correction — rc4

The user's rc3 report on Xiaomi Android 15 confirms successful transmission of a one-page, approximately 8.4 MB Canon job. It does not establish paper output. Further comparison of the complete official filter stream revealed that rc3 incorrectly used intermediate-band controls on the final band: continuation byte `01` and SLIM end token `FE/00`. Canon's official filter uses continuation byte `00` and end token `FE/01` on the final band of every page. Pixel decoder tests alone did not expose this difference.

rc4 emits both observed final controls on each page, including copies and multi-page jobs. A two-page regression checks the controls, and the independent oracle now parses Canon's complete generated reference page and checks intermediate/final controls in every Android-generated job. Physical acceptance still requires the real printer; this correction is not inferred from USB transfer success.

The offline recorder replaces the official module's `Info_Initialize_FilterCalled`, `Info_commJobWrite` and other Info calls before transport. It captures a complete `cnpkmodulencapr` job without touching a printer. Its CPCA envelope is `CD CA 10 00`, sequence zero, stream user ID `FFFF0000`, and zero trailing reserved/user fields. This captures the inner CPCA print stream only. It does not capture the USB MLP transport, which requires replies. `glue_cpcaSendData` → `NCT_CPCA_SendData` → `caioWrite` passes inner job bytes into a selected transport context. `CPCA_Bind` opens local transport contexts; it is not a mandatory wire-level Bind packet to be invented.

`CanonCpca` implements that print-stream mode: JobStart2 attributes, default unauthenticated job settings, binder/document setup, 600-DPI environment, paper-save setting, channel-1 PDL transfer chunks with 16-bit length bounds, impression counters and document/binder/job end. Optional timestamp and host UUID metadata are omitted; fixed generic job/owner names avoid embedding device identifiers or document names. Authentication/accounting defaults and all non-metadata setup/footer bytes match the observed official output. The final byte counts and packet boundaries are independently checked.

The reference harness compares 24 fixed CPCA messages against the official module and 11 framing outputs against native NCAP functions. Twelve complete Android-generated jobs (three media sizes × two orientations × Draft/Standard resolution) decode through the official native SLIM decoder: **2,332 bands, every pixel compared**, including white padding, isolated edge pixels, coordinate placement and doubled/rotated source geometry. These are reproducible software oracles, not a physical USB capture.

The application's primary Print button now invokes the direct job manager. Automatic selection recognizes only Canon `04A9:2795` with `CA_UFRIILT_OIP`, `LIPSLX`, and `CPCA`, or an explicitly advertised PCL 5 language. Android printing remains a separately named action. The print service advertises those supported identities. USB ownership remains serialized; failures/cancellation release locks and reset a partially submitted input stream without automatically retrying ambiguous writes. Canon writes have a finite 15-second timeout to allow printer backpressure.

## Missing USB multichannel transport — rc5

The owner's rc4 trace reports 8,895,951 bytes accepted by raw USB, with no paper output. The previous oracle replaced `Info_Initialize_FilterCalled` and therefore missed the transport selected by real initialization. Keeping actual Info initialization with the LBP6030 PPD changes the device URI from `usb://...` to `multi_usb_ncap://...`. The actual CUPS communicator selects scheme 3, job plugin 3: **libcomm_usbmlportr.so**. Sending the CPCA stream directly to the endpoint bypassed that layer in rc3/rc4.

`verify-mlp` now retains actual Info initialization, stubs external I/O, and invokes the official x86_64 library's InitSub, OpenSub, CloseSub, SendSub2 and RecvSub methods with fake I/O callbacks. It verifies initialization `00 00 00 08 01 00 00 08`, successful reply payload `80 00 08`, and the three host/printer socket pairs `01/10`, `02/20`, `03/30`. The job writer selects channel 1. Channel-open payloads contain opcode 1, the socket pair and six FF bytes; replies negotiate total packet sizes. Headers and payloads are written in separate USB transfers, matching the native WritePort calls. Six-byte headers encode socket pair, big-endian total length, credit byte 1 and flags 0. Job writes use flags 0. Opening gives one transmit credit, and the library's RecvSub restores one credit for each received packet, including empty packets with header credit byte zero. Do not substitute generic IEEE 1284.4 credit semantics for these observed Canon semantics.

`CanonMlpSession` performs that initialization and opens all three channels before rendering. It sends the unchanged rc4 CPCA/NCAP stream inside negotiated, bounded MLP packets and waits for a reply on channel 1 before sending the next packet. It drains asynchronous replies, handles fragmented/coalesced input using whole USB reads, enforces deadlines and size/socket checks, and requires successful channel-close replies. A silent printer fails before page data; an absent data reply stops further transmission. Writes are not retried automatically. Failures and cancellation reset partial jobs and release USB ownership. Diagnostics include channel negotiation and acknowledgement counts. These are printer transport acknowledgements, not evidence of physical paper output.

## Remaining hardware/release acceptance

No successful physical print or bidirectional hardware trace is available in this workspace. Validate the rc6 raw PRN isolation APK on the reported phone/printer: one built-in page, a multi-page PDF, image, each supported media/orientation, copies/ranges, denied permission, paper-out, disconnect, cancellation and reconnect. Compare output placement/density and actual printer acceptance. Physical output cannot be inferred solely from an accepted bulk write, native decoder success or matching packet builders.

For a Linux reference capture, locate the USB bus with `lsusb -t`, enable `usbmon`, capture that bus with Wireshark or `tcpdump -i usbmonBUS -s 0 -w canon-test.pcap`, and print one known page through Canon's official queue. Keep both Bulk OUT and Bulk IN traffic and note the printer/driver version and paper result. The offline recorder manifest explicitly states `recordedBeforeTransport=true` and `physicalPrintVerified=false`.

The APK remains a testing candidate until physical acceptance is established. Play release additionally needs the owner's stable production/upload signing identity and Play Console configuration.
