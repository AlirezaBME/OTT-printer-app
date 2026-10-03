# Canon interoperability reference tooling

rc4 adds full-page finalization checks against the official filter stream. Intermediate bands use continuation byte `01` and SLIM end control `FE/00`; the final band uses `00` and `FE/01`. Both controls are checked in every Android-generated page, in addition to decoding every raster pixel. rc3's pixel-only oracle did not catch its incorrect final-band controls.

These tools run Canon's checksum-pinned official v5.00 Linux/x86_64 driver as an offline oracle. They do not open a printer. Canon programs are downloaded/extracted into a separate scratch directory and are not distributed with the app or repository. Requires Linux x86_64, Python 3.12+, `dpkg-deb`, GCC, Ghostscript and libcups.

```sh
python3 tools/canon/reference_driver.py verify-slim --work-dir /tmp/canon-reference
python3 tools/canon/reference_driver.py generate --work-dir /tmp/canon-reference
python3 tools/canon/reference_driver.py verify-protocol --work-dir /tmp/canon-reference
python3 tools/canon/reference_driver.py verify-mlp --work-dir /tmp/canon-reference
CANON_JOB_OUTPUT=/tmp/canon-android-jobs ./gradlew :app:testDebugUnitTest
python3 tools/canon/reference_driver.py verify-jobs --work-dir /tmp/canon-reference --jobs-dir /tmp/canon-android-jobs
```

`verify-slim` decodes 172 independent SLIM fixtures (60 literal and 112 row-bounded copy/count/prefix fixtures) with Canon's native decoder. It verifies every byte, row counts, bounded decoder consumption and output canaries.

`generate` uses Ghostscript CUPS raster and the LBP6030 PPD/filter chain to generate NCAP PDL. `pathmap.c` redirects only Canon paths into scratch and disables the session module, so this output is page data only and must not be mistaken for a full USB job.

`verify-protocol` runs the actual `cnpkmodulencapr` with `session_recorder.c` replacing the Info initialization and all printer I/O. The recorder captures JobStart2, default settings, binder/document setup, PDL data and termination **before transport**. It compares 24 fixed messages with stored reference fixtures; host UUID/time metadata vary. Separately, `pdl_recorder.c` lets native NCAP framing functions write into a bounded recorder. Eleven job/media/page/band/end outputs match the framing fixtures. No Canon implementation source is copied into these helpers.

`verify-jobs` parses complete `.prn` jobs emitted by the Android Kotlin encoder tests. It validates the CPCA headers/lengths, reconstructs NCAP data, parses each band and decodes through native `lCaptDecode`. An independently constructed expected bitmap checks all pixels, paper dimensions, rotation, 300→600 DPI expansion, white row padding and last-band height. Twelve media/orientation/resolution jobs contain 2,332 bands. This is substantially stronger than testing encoder output with its own decoder.

Results are written as JSON manifests and explicitly carry `physicalPrintVerified=false`. GitHub Actions builds the Android fixtures, downloads them in the oracle job, and repeats all comparisons. Test fixture streams contain generated patterns only, not user documents. Neither proprietary Canon binaries nor release signing credentials are included in uploaded results.

`verify-mlp` retains real Info initialization and proves that the LBP6030 PPD selects `multi_usb_ncap` and the USB MLP plugin. It invokes the actual native initialization, channel open/close, packet serializer and receive-credit methods with fake I/O. Nineteen vectors cover three socket pairs, native service-name requests/parsing, packet lengths and credit restoration. Service lookup runs on control channel 0; synthetic oracle names do not establish actual device names. This closes a gap in the older recorder: its replacement of Info initialization concealed the USB layer.

The Android backend wraps the observed inner CPCA stream in Canon USB MLP packets and requires initialization, data and close replies. Interactive CPCA status RPCs are a separate API. The backend does not invent a Bind exchange or treat generic bulk writes as proof of paper output. See [driver findings](../../DRIVER_RESEARCH.md) for physical acceptance criteria.

The rc7 copy fixtures are stored as deterministic gzip TSV (mtime zero). They
use LBP6030 parameters, depth 2, and a separate last-band column. Kotlin tests
compare emitted bytes against these fixtures; native `lCaptDecode` compares
the decoded source directly. Three identical preceding bytes are required
for the default copy distance of three; row boundaries are never crossed.
See [repository/license decisions](../../REPOSITORY_REUSE.md).
