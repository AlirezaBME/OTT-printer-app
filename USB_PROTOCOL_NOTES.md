# USB transport contract

- Discover a USB Printer Class 7 interface with protocol 1/2 and bulk OUT. Protocol 3 (IEEE-1284.4) is not supported.
- Request per-device Android USB permission using a package-scoped immutable PendingIntent. Match the returned device and verify permission through UsbManager.
- One application-wide mutex owns each open/claim/probe/send/close cycle. No diagnostic probe interrupts an active print job.
- GET_DEVICE_ID: bmRequestType 0xA1, request 0, configuration index 0, wIndex `(interfaceId << 8) | alternateSetting`. The response starts with a big-endian length including two length bytes.
- GET_PORT_STATUS: bmRequestType 0xA1, request 1, wValue 0, wIndex interfaceId. Bit 3 is NotError, bit 4 Selected, bit 5 PaperEmpty.
- USB writes use at most 16 KB, a finite three-second transfer timeout and a cancellation check between chunks. Positive partial writes advance by the actual byte count. Zero/negative writes fail; never automatically resend an ambiguous failed transfer.
- Direct output requires CMD to explicitly contain PCL/PCL5/PCL5e/PCL5c (including spaced spellings). Unknown IDs, CARPS2, UFRII LT and PCL XL-only devices are rejected before transmitting.
- The Printer Class port status is not a per-job physical completion acknowledgement. Never infer paper ejection from successful transfer or an elapsed delay.

The supplied Canon `04A9:2795` identity (`CID:CA_UFRIILT_OIP`, `CMD:LIPSLX,CPCA`) requires a UFRII LT / NCAP backend with CPCA session handling. Its valid USB endpoints and ready Printer Class status establish transport readiness only. The research SLIM raster codec and an NCAP PDL file generated without a session cannot replace CPCA setup, responses and teardown. Canon output remains blocked until a complete backend is independently validated; see tools/canon/README.md.
