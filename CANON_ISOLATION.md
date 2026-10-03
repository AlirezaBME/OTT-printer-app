# Canon PRN isolation harness — rc6

The owner's rc3, rc4 **and rc5** attempts produced no paper. rc5 received one six-byte MLP reply per packet, but that is transport flow control, not printer-job acceptance. No physical printer, successful official Windows PRN, or official-driver USB capture is available in this workspace. Hardware printing remains unverified.

## Untouched official PRN test

1. On Windows, use Canon's official LBP6030 driver and Print to file to generate a known test page. Keep the original `.prn` bytes. A PDF saved with another printer driver is not this test.
2. Copy that file to the phone. Connect the Canon via OTG, grant USB permission, and open the wrench / USB diagnostics screen.
3. Enable **Capture binary job + USB bytes** before the attempt. Choose **Print raw Canon PRN** and select the `.prn` or `.bin`. The document picker also allows other MIME types because providers often classify PRNs inconsistently.
4. Review size, SHA-256, first 64 bytes and last 64 bytes. Press **Send via USB**. This is an explicit raw-file operation independent of document paper/copies/quality settings.
5. Wait for the bounded 15-second observation window. **Transport complete — print unconfirmed** is not success. Record whether a physical page emerges, and export **session trace ZIP**.

Import creates an owned, read-only snapshot in private cache, capped at 64 MiB. Before transmission, `RawPrnPrintSource` verifies the snapshot and its copied spool. `ProtocolCapture` records the spool's hash, and `CanonMlpSession` hashes the exact concatenation of submitted MLP data bodies. The hashes must match. There is no renderer, encoder, compression, prefix, CPCA job wrapper, newline conversion or document transformation on this path. Only the established transport/session framing is added.

If the official PRN prints, freeze this working transport and compare its opaque job bytes with the generated stream. If it fails, inspect transport/channel/control behavior first and obtain a successful official-driver USB exchange with USBPcap/Wireshark. A failed Windows PRN test alone does not identify the missing command: it might rely on driver-side control traffic outside the saved job. Preserve both USB directions and actual write boundaries in the capture.

## Existing 8,895,959-byte generated payload

The path is MainScreen → MainViewModel.startPrint → PrintJobManager → DocumentSource.renderForPrint → PageRenderer / DitherEngine → CanonNcapEncoder / CanonCpca → CanonPrintJobTransport → CanonMlpSession → UsbPrinterTransport. The raw path enters the manager with `RawPrnPrintSource` and bypasses rendering and encoder selection entirely.

Draft input is rendered at 300 DPI, then doubled to **600 DPI**. Standard input starts at 600 DPI. A4 output is approximately 4992 padded pixels × 7016 rows at **two bits per pixel**, white=0 and black=3: 8,755,968 packed raster bytes before coding and envelopes. The raster uses 128-pixel row alignment and at most 32 rows per band. The literal SLIM subset emits 8 bits for a zero raster byte and 12 bits for a nonzero byte, plus band/page/job overhead. This explains a roughly 8.9 MB mostly-white test page; it is not evidence of an 8-bit 300 DPI stream.

The stream starts with `CD CA 10 00` CPCA envelopes, big-endian command/length fields, sequence zero and user ID `FFFF0000`. JobStart2/default settings, binder/document setup and 600 DPI environment precede command `001A` PDL chunks. Their first payload byte is logical PDL channel 1. NCAP job grammar begins `01 C1 85 10 00 10 89 ...`, followed by media/page/band framing, two-bit raster and literal SLIM/HISCOA coding. Compressed band length is little endian inside NCAP; outer CPCA length is big endian. Intermediate bands have continuation `01` and SLIM end FE/00; each final band uses `00` and FE/01. Page/document and CPCA impression/binder/job termination follow. Native function fixtures verify the observed length fields and ordering; no invented checksum is added.

The original guessed encoder was removed previously. This candidate has real native-decoder/framing evidence, but **its complete job has not been accepted by the physical printer**. It is labelled Experimental NCAP/CPCA. No encoder bytes were randomly changed in rc6. A fixed A5 raster fixture hash detects accidental format changes, and the independent oracle still compares every pixel in twelve generated jobs. Software format checks cannot prove that firmware accepts all lifecycle/setup choices.

## Protocol assumptions and evidence

| Item | Classification | Evidence / limitation |
| --- | --- | --- |
| VID/PID, CID, CMD, OUT 01 / IN 82, packet size 512, READY 18 | SUPPORTED BY TRACE | Owner's Android hardware report; recorded again in every probed session. |
| Channel 1 / printer socket 10 carries jobWrite bytes | VERIFIED against native reference | Canon v5.00 `libcomm_usbmlportr` jobWrite calls WriteChannel(1). Actual physical acceptance still unknown. |
| Channels 2/20 and 3/30 are CPCA transport contexts 1/2 | VERIFIED against native reference | caioRead/caioWrite dispatch; neither is declared a verified job-status service. |
| Device service names | UNVERIFIED until returned by device | Native GetServiceName command `0A socket` on control channel 0; response `8A status socket name`. Device-returned names are recorded, not invented. Native oracle checks all three queries. |
| `01 10 00 06 01 00` is an empty channel-1 transport reply | VERIFIED parser + SUPPORTED BY TRACE | Six-byte length, credit field 1, no CPCA body. Native RecvSub restores one flow token per matching packet even with wire credit field zero. It cannot mean PRINT_ACCEPTED. |
| Header and body in separate USB writes | VERIFIED against native reference | Native SendSub2 makes a short six-byte header WritePort then a payload WritePort. This boundary is preserved. |
| Legal smaller MLP body prevents a packet-aligned final USB write | LIKELY transport-compatible; hardware unverified | Negotiated maximum bounds are preserved, and concatenated job bytes are identical. If a body would be 512-aligned, one byte moves into the next frame. No speculative ZLP is emitted. |
| CPCA acceptance / processing / completion query meanings | UNVERIFIED | No status opcode is fabricated. A bounded passive assembler records CPCA-shaped responses and unknown channel data, without promoting them to job state. |
| 15-second observation | Diagnostic policy, not a Canon completion rule | Session stays claimed/open while documented GET_PORT_STATUS is polled and IN replies are read with <=1s waits. Timeout is confirmation unknown, not failed physical printing or success. |

The native oracle's service names are synthetic callback values, clearly marked `actualDeviceService=UNVERIFIED`. They prove request/parser interoperability with the native method, not what the attached printer will return. The queried service names are informational; routing is supported by the native job writer and does not silently switch channels based on arbitrary name text.

## State and cleanup

The manager serializes USB operations with the existing application-wide mutex. Capture starts before probing, including failed attempts. Device identity and claimed interface metadata update after the existing probe. The sequence is probing/claim → MLP initialization/open/service discovery → source verification → send → TRANSFER_COMPLETE → bounded observation → CONFIRMATION_TIMEOUT → acknowledged channel close → interface release. Cancellation or explicit protocol/port error resets a submitted session and releases ownership without retrying an ambiguous transfer.

A BASIC READY byte does not imply a CPCA job was accepted or completed. `TransferComplete` reports acceptance, processing and physical printing UNKNOWN. `Completed` is used only for offline file export. The Android PrintService blocks an unconfirmed submitted job instead of calling `PrintJob.complete()`. The primary/raw paths remain direct USB, without Android's print dialog or network dependencies.

## Capture contents

Metadata is always recorded. Optional binary capture is opt-in, capped at a 64 MiB job, and may contain private document bytes. Files stay in private app cache until explicitly shared.

- `session.json`: schema, source type/size/hash/edge bytes, full raw IEEE-1284 ID, USB descriptors/claim state, channel mapping, negotiated packet sizes, state/terminal result, timestamps and monotonic timings. Packet events include direction, socket IDs, header hex, command or data classification, lengths, credits/flags, segmentation and response preview. USB events record requested and known accepted byte counts, elapsed time, alignment and no-ZLP policy. Large payload bodies are absent from ordinary logs.
- `usb-out.bin` / `usb-in.bin`: concatenated bytes known accepted/read by Android, including MLP control/framing. **They are application-level captures, not USBPcap bus captures.** JSON events retain segmentation. A failed Android transfer may have delivered unreported bytes; the error event marks this ambiguity, and failed writes are never retried.
- `print-job.bin`: exact opaque job spool, including raw PRN or generated CPCA stream.
- `encoder-output.bin`: generated source only; identical to `print-job.bin`. It is absent for raw PRN.

Metadata is bounded at 100,000 events; exceeding it stops the attempt with an explicit error rather than silently dropping capture records. Export runs off the UI thread. Changing/importing raw files is disabled during printing; cancellation during import deletes its incomplete snapshot.

## Validation matrix

| Test | Current result | Interpretation |
| --- | --- | --- |
| Device ID and port status | YES in supplied hardware trace | Control path and basic readiness work. |
| MLP open and data replies | YES in supplied hardware trace | Negotiation/flow control partially work. |
| Native service query/frame/credit fixtures | PASS offline | Native library interoperability, not actual device service names. |
| Raw official Canon Windows PRN physically prints | UNKNOWN | Requires the official file and attached hardware; the isolation path is now implemented. |
| Generated job physically prints | NO in rc3–rc5 reports | Failure remains unexplained at the firmware/job layer. |
| CPCA accepted / processing / completion | UNKNOWN | Passive bytes captured; validated status command meanings absent. |
| Raw bytes, edge sizes, timeout, cancellation, cleanup, trace export | Automated tests | Do not substitute these checks for Test A. |

GitHub prerelease includes a signed testing APK, unsigned AAB and reproducible test/oracle reports. Physical acceptance and owner Play signing/Console setup remain required for production publication.
