# Driver findings

The original AI-generated claims of a “genuine CARPS2” and “UFRII LT” implementation were unsupported. The invented encoders have been removed.

References inspected on 2026-10-02:

- https://github.com/ondrej-zary/carps-cups — a CARPS CUPS implementation using binary framing; its source does not validate the original app's ESC/ASCII CARPS2 packets or LBP6030 support.
- https://github.com/henrya/carps2lbp-drv-arch — packaging for Canon's Linux UFRII LT driver, including the LBP6030/6040/6018L family. Packaging a Linux driver does not supply an Android-compatible backend.
- USB Printer Class specification — printer enumeration, GET_DEVICE_ID and GET_PORT_STATUS establish transport/identity/status only.
- HP PCL 5 raster commands — the app implements uncompressed monochrome raster transfer. PCL XL/PCL 6-only printers cannot accept this PCL 5 stream and are rejected unless they separately advertise PCL 5.

No Canon proprietary binaries or third-party driver source were copied into the app. No physical print capture or tested Canon protocol fixture was available. A real Canon backend remains a release blocker for a product promising direct LBP6030 USB printing. See AUDIT.md and TESTING.md.
