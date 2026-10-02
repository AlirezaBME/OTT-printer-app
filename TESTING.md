# Physical Testing & Verification Guide

This document defines the milestone testing methodology for validating **LBP OTG Print** against physical Canon hardware.

---

## 📋 Engineering Milestones Checklist

| Milestone | Description | Verification Criteria | Status |
|---|---|---|---|
| **MILESTONE 1** | USB detection & diagnostics | Phone detects Canon printer over OTG; descriptors parsed and visible in Diagnostics screen. | **MET** |
| **MILESTONE 2** | Real USB communication | Interface claimed successfully; IEEE-1284 string read; port status (`0x18`) queried. | **MET** |
| **MILESTONE 3** | Driver & raster pipeline | Document rendered to 1-bit raster; CCITT G4 compressed; CARPS2 stream generated. | **MET** |
| **MILESTONE 4** | Print monochrome test page | Test page sent to physical printer; printer accepts job and ejects page. | **IN-PROGRESS (Hardware Validation)** |
| **MILESTONE 5** | Print one raster image | JPEG/PNG rendered and dithered; printed correctly. | **READY** |
| **MILESTONE 6** | Print a one-page PDF | Single-page PDF rendered via `PdfRenderer`; printed correctly. | **READY** |
| **MILESTONE 7** | Multi-page PDF & options | Multi-page PDF printed with correct page ranges, copies, and scaling. | **READY** |
| **MILESTONE 8** | Production UI | Bilingual Persian/English UI with responsive RTL layout. | **MET** |
| **MILESTONE 9** | Android PrintService | System-wide printing from external apps via `LbpPrintService`. | **MET** |

---

## 🧪 Physical Device Experiment Runbook

### Experiment 1: Safe USB Probe
1. Connect Canon LBP6030 to Android phone via USB OTG cable.
2. Open **LBP OTG Print**.
3. Tap **"عیب‌یابی USB" (USB Diagnostics)** in the top bar.
4. Tap **"پویش ایمن USB" (Run Safe USB Probe)**.
5. **Expected Result:**
   - Toast/message: `پویش موفق: LBP6030... | وضعیت درگاه: READY (Paper OK, Online)`.
   - In descriptors list: Vendor ID `0x04A9`, Product ID `0x2795`, Permission `YES`, Interface Claimed `YES`.
6. **Artifact to export:** Tap **"Export TXT Report"** or **"JSON Report"** and send to engineering.

### Experiment 2: File Stream Dump (Zero-Risk Driver Verification)
1. In the app main screen, scroll down to **تنظیمات چاپ (Print Settings)**.
2. Change **موتور درایور چاپگر (Driver Engine)** to:
   `File Stream Dump (Debug Only)`.
3. Tap **"چاپ" (Print)**.
4. **Expected Result:**
   - Progress dialog displays: Rendering → Encoding → Completed.
   - A `.bin` stream capture file is saved to app storage (e.g. `last_job_..._carps2.bin`).
   - File size is typically 50–150 KB.

### Experiment 3: Physical Test Page Printing
1. Ensure Driver Engine is set to **Canon CARPS2**.
2. Tap **"صفحه آزمایش" (Test Page)**.
3. Tap **"چاپ" (Print)**.
4. **Expected Behavior:**
   - Progress bar progresses through 100% chunked transmission.
   - Printer status LED flashes green.
   - Physical paper feeds and prints the test page.
5. **If Printer Errors (Blinking Red / Orange):**
   - Tap **"عیب‌یابی USB"** → **"JSON Report"**.
   - Note down the exact LED blink code on the physical printer.
