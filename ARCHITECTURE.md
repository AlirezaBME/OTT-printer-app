# LBP OTG Print — Architecture & Design

This document details the software architecture, data flow pipelines, memory management strategies, and concurrency patterns used in **LBP OTG Print**.

---

## 🏛️ Layered Modular Architecture

The application strictly separates document ingestion, rasterization, driver encoding, and hardware transport into isolated layers:

```
[ PDF / Image Document / Test Page ]
                 ↓
      [ DocumentSource Interface ]
                 ↓
         [ PageRenderer ]
  (Scaling, Margins, Orientation, Canvas)
                 ↓
       [ DitherEngine ]
  (Floyd-Steinberg, Atkinson, Threshold)
                 ↓
         [ RasterPage ]
  (1-bit Monochrome Bitmask: 8 pixels/byte)
                 ↓
   [ PrinterLanguageEncoder ]
  (Carps2Encoder / UfriiLtEncoder / RawPclEncoder)
                 ↓
       [ Encoded Job Stream ]
                 ↓
       [ UsbTransport ]
  (Safe Chunked Bulk OUT, Error Verification)
                 ↓
   [ Physical Canon Laser Printer ]
```

---

## 📦 Package Breakdown

### 1. `com.example.core.model`
- **`PaperSize`**: Standard paper formats (`A4`, `A5`, `LETTER`) with exact metric dimensions, pixel calculations at target DPI, and native CARPS/PCL protocol media codes.
- **`PrintSettings`**: Immutable configuration state container (Paper, Orientation, Scaling, Quality, Content Mode, Dithering Algorithm, Driver Engine, Copies, Page Ranges).
- **`PrintJob` & `PrintJobState`**: Sealed class state machine tracking job execution:
  - `Idle` → `Preparing` → `Rendering` → `Encoding` → `WaitingForPrinter` → `Sending` → `DataSent` → `Finishing` → `Completed` / `Failed` / `Cancelled`.

### 2. `com.example.usb`
- **`UsbTransport`**: Hardware abstraction interface decoupling the printer driver from Android's USB classes (`android.hardware.usb`).
- **`UsbPrinterTransport`**: Real physical implementation using `UsbManager` and `UsbDeviceConnection`. Enforces:
  - Safe chunked bulk transfers (16 KB chunks).
  - Explicit return-value checking on every `bulkTransfer` call.
  - Active detection of short writes and timeouts.
  - Standard USB Printer Class 1.1 control requests:
    - `GET_DEVICE_ID` (Request 0, Type 0xA1) for reading IEEE-1284 string.
    - `GET_PORT_STATUS` (Request 1, Type 0xA1) for real-time paper and error status.
    - `SOFT_RESET` (Request 2, Type 0x21).
- **`FakeUsbTransport`**: Complete mock transport for unit testing and CI environments without hardware.
- **`UsbDescriptorReader`**: Comprehensive USB descriptor parser extracting interfaces, endpoint addresses, directions, transfer types, and packet sizes.
- **`Ieee1284Parser`**: Key-value parser for printer IEEE-1284 descriptor responses.
- **`UsbTraceLogger`**: High-performance circular buffer logging all transport transactions, timings, and error states.

### 3. `com.example.document`
- **`DocumentSource`**: Unified page-by-page rendering interface.
- **`PdfDocumentSource`**: Native Android `PdfRenderer` wrapper. Renders pages individually to bounded bitmaps, avoiding loading multi-page documents simultaneously into RAM.
- **`ImageDocumentSource`**: Memory-bounded bitmap decoder utilizing `inJustDecodeBounds` and `inSampleSize` downsampling. Handles EXIF orientation tags.
- **`TestPageDocumentSource`**: Vector-drawn high-resolution diagnostic test pattern.

### 4. `com.example.raster`
- **`RasterPage`**: Packed 1-bit per pixel byte array representation (1 = black toner dot, 0 = white paper background, MSB-first bit order).
- **`DitherEngine`**: Converts 8-bit grayscale pixels to 1-bit monochrome using Floyd-Steinberg error diffusion with serpentine scanning or Atkinson dithering.
- **`CcittG4Encoder`**: Pure Kotlin implementation of CCITT Group 4 (ITU-T T.6) 2D fax compression standard. This is the exact compression standard utilized by Canon CARPS / CARPS2 and CUPS filters for monochrome laser printing.

### 5. `com.example.driver`
- **`PrinterLanguageEncoder`**: Driver engine interface.
- **`Carps2Encoder`**: Canon Advanced Raster Printing System 2 encoder. Outputs Canon mode entry sequences (`\x1b[K`), paper configuration packets, G4-compressed raster chunks (`\x1b[...r`), and form feeds (`0x0C`).
- **`UfriiLtEncoder`**: Canon UFRII LT command encoder with universal PJL job encapsulation.
- **`RawPclEncoder`**: Standard PCL laser raster fallback.

### 6. `com.example.jobs`
- **`PrintJobManager`**: Sequential job queue executor. Guarantees that only one print job accesses the USB bulk transport at a time.
- Handles coroutine lifecycle cancellation and safe socket closing.

### 7. `com.example.service`
- **`LbpPrintService`**: Official Android `PrintService` integration. Exposes the USB printer to Android's native print framework so any third-party app can print to the Canon LBP6030 directly.

---

## ⚡ Concurrency & Memory Model

- **Background Dispatchers:** All rasterization, dithering, and driver encoding execute on `Dispatchers.Default`. All USB hardware communication executes on `Dispatchers.IO`. The Android UI thread is never blocked.
- **Memory Bounded:** At no point is an entire document decoded into uncompressed 32-bit ARGB bitmaps. Each page is rendered, dithered to a 1-bit bitmask (which occupies only ~4.3 MB for a full 600 DPI A4 page: 4960 × 7016 / 8 = 4,349,920 bytes), compressed via CCITT Group 4 (typically reducing to 50–200 KB), and the intermediate bitmaps are promptly recycled.
