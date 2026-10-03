# USB transport contract

- Existing printer discovery, package-scoped USB permission, class-7 interface claiming, endpoint discovery and device-ID parsing are preserved.
- One application-wide mutex owns each open/probe/send/observe/close cycle. Diagnostics cannot interrupt an active job.
- GET_DEVICE_ID: bmRequestType A1, request 0, configuration index 0, wIndex `(interfaceId << 8) | alternateSetting`; big-endian response length includes its two bytes.
- GET_PORT_STATUS: bmRequestType A1, request 1, wValue 0, wIndex interfaceId. Bit 3 NotError, bit 4 Selected, bit 5 PaperEmpty. READY is not job acceptance or page completion.
- Raw PCL is restricted to explicitly advertised PCL 5. Canon experimental NCAP/CPCA and raw Canon PRN use only exact 04A9:2795 + CA_UFRIILT_OIP + LIPSLX/CPCA identity.
- Canon uses the native-reference MLP initialization and channel pairs 1/10, 2/20, 3/30. Job bytes use channel 1; channels 2/3 are CPCA transport contexts. Device service names are queried and recorded separately from native roles.
- Native header and payload are separate USB writes, preserved in the app. Frames obey negotiated bounds. Packet-aligned body lengths are shortened by one byte into the next frame, preserving the opaque job; hardware compatibility of this legal segmentation policy is unverified. No blind ZLP is emitted.
- Reads use whole USB packets and bounded parsing. Data waits for a matching flow token. Canon reads have a finite 10-second deadline and writes a finite 15-second timeout; positive partial writes advance by their actual count. Zero/negative or ambiguous writes fail without automatic retry.
- After transfer, Canon sessions remain open for up to 15 seconds of documented port-status polling and passive reply capture. CPCA acceptance/processing/completion commands are not fabricated. Timeout preserves UNKNOWN printer acceptance and physical output.
- All terminal paths release ownership. Cancellation/failure after submission resets partial input; pages already accepted might still print. No infinite retries.

See [CANON_ISOLATION.md](CANON_ISOLATION.md) for exact byte origin, raw Windows PRN procedure, trace schema, assumption classifications and current hardware limits.
