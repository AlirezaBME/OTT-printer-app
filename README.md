# LBP OTG Print

**LBP OTG Print** is a specialized native Android application designed for **direct, offline printing to Canon laser printers** over a physical USB OTG cable.

It operates entirely on-device without Wi-Fi, without a computer, without cloud services, and without requiring any Internet connection.

---

## 🎯 Target Printer Hardware

This application is built for the **Canon LBP6030 / LBP6040 / LBP6018L** family:

| Parameter | Observed Hardware Value |
|---|---|
| **Target Models** | Canon LBP6030, LBP6030B, LBP6030w, LBP6040, LBP6018L |
| **Vendor ID (VID)** | `0x04A9` (1193 decimal) |
| **Product ID (PID)** | `0x2795` (10133 decimal) |
| **Manufacturer String** | `Canon,Inc.` |
| **Product String** | `LBP6030/6030B/6018L` |
| **USB Class** | Class 7 (Printers), Subclass 1, Protocol 1 or 2 |
| **Printer Languages** | CARPS2, UFRII LT |

*(The app also supports related models in the family such as LBP6000, LBP6020, LBP6200, LBP6230, and any USB Printer Class 7 device via universal driver fallback).*

---

## 🚀 Key Features

1. **Hardware USB OTG Direct Transport:**
   - Enumerates physical USB descriptors.
   - Dynamic USB permission negotiation.
   - Safe chunked bulk transfer (16 KB packets) with timeout management and write verification.
   - Standard USB Printer Class control transfers: IEEE-1284 Device ID reading and real-time port status (Paper Out, Online, Hardware Error).

2. **Genuine Driver & Raster Pipeline:**
   - **CARPS2 Driver Engine:** Generates genuine Canon CARPS2 commands with CCITT Group 4 (ITU-T T.6) 2D fax compressed raster streams.
   - **UFRII LT Driver Engine:** Generates PJL encapsulated Canon UFRII LT command structures.
   - **Raw PCL Engine:** Fallback laser driver.
   - **File Stream Dump Mode:** Generates and exports raw `.bin` printer streams to disk for inspection and byte comparison without physical hardware.

3. **High-Precision Image & Document Processing:**
   - Direct PDF rendering via Android `PdfRenderer` (page-by-page, memory-safe, no OOM).
   - Image rendering (JPEG, PNG, WEBP) with EXIF orientation correction.
   - Multi-algorithm dithering:
     - **Floyd-Steinberg Error Diffusion** (serpentine scanning).
     - **Atkinson Dithering** (optimized for crisp halftone laser reproduction).
     - **High-contrast Threshold** for crisp document text.
   - Built-in alignment test page featuring corner crosshairs, font legibility ladder (6pt to 42pt), and 0–100% halftone ramp.

4. **Production UI:**
   - Bilingual: Full Persian (Farsi) with true RTL layout + English (LTR) toggle.
   - Clean, professional utility aesthetics (no AI gradients, no floating glassmorphism).
   - System PrintService integration (`LbpPrintService`) enabling other Android apps to print via the standard Android "Print" dialog.
   - Share Intent support (`ACTION_SEND`, `ACTION_VIEW`).

---

## 🛠️ Physical Device Quick Start Guide

### What You Need:
1. Android phone running Android 8.0+ (API 26+) with USB Host / OTG support.
2. USB OTG adapter (USB-C or Micro-USB to USB-A Female).
3. Standard USB Type-A to Type-B printer cable.
4. Physical Canon LBP6030 / LBP6040 / LBP6018L printer turned on with paper loaded in tray.

### Step-by-Step Procedure:
1. **Connect Cable:** Plug the USB OTG adapter into your phone and connect the printer cable to your Canon printer.
2. **Turn On Printer:** Power on the Canon printer and wait for its green ready LED to turn solid.
3. **Open App:** Launch **LBP OTG Print**.
4. **Grant USB Permission:** When prompted by the system dialog ("Allow LBP OTG Print to access LBP6030?"), tap **OK / Always allow**.
5. **Check Printer Card:** The status card will show:
   ```
   Canon LBP6030/6040/6018L
   ● متصل از طریق USB OTG
   VID: 0x04A9 | PID: 0x2795 | کاغذ: آماده
   ```
6. **Print Test Page:**
   - Tap **"صفحه آزمایش" (Test Page)**.
   - Tap the primary blue button **"چاپ" (Print)** at the bottom.
   - Observe progress: Preparing → Rendering → Encoding (CARPS2) → Transmitting → Paper Ejection.
7. **Print Your Own PDF or Photo:**
   - Tap **"انتخاب PDF"** or **"انتخاب عکس"**.
   - Configure paper (A4 / Letter), orientation, and copies.
   - Tap **"چاپ"**.

---

## 📦 Building the APK

### Debug APK:
```bash
gradle :app:assembleDebug
```
The resulting APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

### Release APK:
```bash
gradle :app:assembleRelease
```
The resulting APK will be located at:
`app/build/outputs/apk/release/app-release-unsigned.apk`
