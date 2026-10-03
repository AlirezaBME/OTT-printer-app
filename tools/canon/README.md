# Canon reference oracle and raster milestone

This toolkit runs on Linux/x86_64 with Python 3.12+, `gcc`, `dpkg-deb`, Ghostscript and libcups. It downloads Canon's official v5.00 archive, verifies its pinned SHA-256, and extracts it into an external scratch directory. It does not install the driver system-wide or attach to a printer. Canon binaries, ICC profiles and source archives must not be committed or bundled into the Android app.

```sh
python3 tools/canon/reference_driver.py verify-slim --work-dir /tmp/canon-reference
python3 tools/canon/reference_driver.py generate --work-dir /tmp/canon-reference
```

The first command calls **Canon's own `lCaptDecode`** on 60 literal-band fixtures covering zeros, black, alternating pixels, byte values, noise and sparse marks; 1/2-bit depth parameters; short and 621-byte scanlines; and multiple rows. It checks exact decoded bytes, row count and output canaries. The Android project's host test checks the Kotlin codec against those independently accepted fixtures. This avoids asserting only that the new implementation agrees with itself.

`CanonSlimRasterCodec` implements a minimal literal subset of SLIM/HISCOA: zero token `FD`, nonzero literal token `D` followed by a byte, normal band-end token, word padding and byte XOR `43`. It emits no reference-copy commands and expands data by at most approximately 1.5 times. Input bands are capped at 100 KB. The minimal encoder is an independent implementation; no third-party compressor source or proprietary binary is copied into the app. The open CAPT driver's HISCOA implementation was consulted as a protocol reference, and the resulting bytes were verified through the Canon library from the checksum-pinned archive.

The second command renders a test job through Ghostscript's CUPS raster device and Canon's **LBP6030 PPD → `rastertosfp` → `cnrsdrvsfp` → NCAP filter**. `pathmap.c` redirects only Canon's reference paths into the scratch directory. It deliberately disables `cnpkmodulencapr`, selecting the documented no-session fallback from Canon's provided `cnpklib` glue. Output `reference-ncap-pdl.prn` therefore contains **NCAP page-description data only**. Its manifest explicitly marks `cpcaSessionCaptured=false` and `physicalPrintVerified=false`.

This file is not a USB capture from a successful print. Do not send it straight to the printer and claim it is a validated complete job. Do not confuse successful decoder output with correct page placement, printer acceptance or paper output.

## Remaining implementation

1. Derive and test complete NCAP job/media/page/band framing against the reference filter, including 600 DPI, raster depth/polarity, printable area, origin, orientation, paper sizes, copies and band limits.
2. Implement the CPCA job/session state machine and response parsing. Canon's `cnpkmodulencapr` and communication libraries remain binary-only in the inspected packages. Timeouts, cancellation, resource release and job failures must be bounded and tested from real dialogues.
3. Route the main Print action to Canon USB only after a complete backend exists and the selected device's identity matches. Keep Android printing as an explicitly named alternate action.
4. Validate every transport milestone with a physical `04A9:2795` printer, retaining bidirectional USB traces and printed-page evidence.

A successfully printed one-page A4/600-DPI USB capture from the official Canon driver must include enumeration/permission context and the **Bulk OUT and Bulk IN** traffic before, during and after the job. Page-filter `.prn` data alone omits session evidence. On Linux, locate the bus using `lsusb -t`, enable `usbmon`, capture that bus with Wireshark or `tcpdump -i usbmonBUS -s 0 -w canon-test.pcap`, and print one known page through the official queue. Record printer/driver versions and the paper result. Disconnect/reconnect and cancellation require separate captures.
