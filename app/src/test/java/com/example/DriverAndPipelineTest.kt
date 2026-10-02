package com.example

import com.example.core.model.DitherAlgorithm
import com.example.core.model.PaperSize
import com.example.core.model.PrintContentMode
import com.example.core.model.PrintOrientation
import com.example.core.model.PrintQuality
import com.example.core.model.PrintSettings
import com.example.diagnostics.DiagnosticReport
import com.example.driver.Carps2Encoder
import com.example.driver.RawPclEncoder
import com.example.driver.UfriiLtEncoder
import com.example.raster.CcittG4Encoder
import com.example.raster.RasterPage
import com.example.usb.FakeUsbTransport
import com.example.usb.Ieee1284Parser
import com.example.usb.UsbDeviceInfo
import com.example.usb.UsbPrinterPortStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DriverAndPipelineTest {

    @Test
    fun testPaperDimensionsAndCalculations() {
        val a4 = PaperSize.A4
        // A4 metric: 210 x 297 mm
        assertEquals(210f, a4.widthMm, 0.01f)
        assertEquals(297f, a4.heightMm, 0.01f)

        // At 300 DPI
        assertEquals(2480, a4.getPixelWidth(300))
        assertEquals(3507, a4.getPixelHeight(300))

        // At 600 DPI
        assertEquals(4960, a4.getPixelWidth(600))
        assertEquals(7015, a4.getPixelHeight(600))

        // Printable area with 5mm margin at 600 DPI: (200mm / 25.4) * 600 = ~4724 px
        val printableW = a4.getPrintablePixelWidth(600, 5f)
        assertTrue(printableW in 4720..4730)
    }

    @Test
    fun testPageRangeParsing() {
        val settings = PrintSettings(pageRangeText = "ALL")
        assertEquals(listOf(0, 1, 2, 3), settings.parsePageIndices(4))

        val singlePage = PrintSettings(pageRangeText = "2")
        assertEquals(listOf(1), singlePage.parsePageIndices(5))

        val range = PrintSettings(pageRangeText = "1-3, 5")
        assertEquals(listOf(0, 1, 2, 4), range.parsePageIndices(5))

        // Handles out of bounds gracefully
        val outOfBounds = PrintSettings(pageRangeText = "8-10")
        assertEquals(emptyList<Int>(), outOfBounds.parsePageIndices(5))
    }

    @Test
    fun testRasterPageBitOperations() {
        val width = 16
        val height = 4
        val bytesPerRow = (width + 7) / 8 // 2 bytes
        val data = ByteArray(bytesPerRow * height)
        val raster = RasterPage(width, height, 600, data)

        assertFalse(raster.getPixel(0, 0))
        assertFalse(raster.getPixel(7, 0))

        // Set pixel (0, 0) to black (bit 7 of byte 0)
        raster.setPixel(0, 0, true)
        assertTrue(raster.getPixel(0, 0))
        assertEquals(0x80.toByte(), data[0])

        // Set pixel (1, 0) to black (bit 6 of byte 0)
        raster.setPixel(1, 0, true)
        assertTrue(raster.getPixel(1, 0))
        assertEquals(0xC0.toByte(), data[0])

        // Reset pixel (0, 0) to white
        raster.setPixel(0, 0, false)
        assertFalse(raster.getPixel(0, 0))
        assertEquals(0x40.toByte(), data[0])
    }

    @Test
    fun testCcittG4CompressionProducesValidStream() {
        val encoder = CcittG4Encoder()
        val width = 64
        val height = 16
        val data = ByteArray((width / 8) * height)
        val raster = RasterPage(width, height, 300, data)

        // Draw a test black horizontal line on row 4
        for (x in 0 until width) {
            raster.setPixel(x, 4, true)
        }

        val compressed = encoder.encode(raster)
        assertTrue("Compressed stream should not be empty", compressed.isNotEmpty())
        // G4 stream should terminate with EOFB code
        assertTrue(compressed.size >= 3)
    }

    @Test
    fun testCarps2EncoderPacketFraming() {
        val carps = Carps2Encoder()
        val settings = PrintSettings(
            paperSize = PaperSize.A4,
            quality = PrintQuality.NORMAL_600DPI,
            copies = 1
        )

        val jobStart = carps.encodeJobStart(settings, 1)
        // Must start with ESC @ (0x1B 0x40) and CARPS entry \x1b[K (0x1B 0x5B 0x4B)
        assertEquals(0x1B.toByte(), jobStart[0])
        assertEquals(0x40.toByte(), jobStart[1])
        assertEquals(0x1B.toByte(), jobStart[2])
        assertEquals(0x5B.toByte(), jobStart[3])
        assertEquals(0x4B.toByte(), jobStart[4])

        // Verify page encoding
        val raster = RasterPage(32, 8, 600, ByteArray(4 * 8))
        val pageBytes = carps.encodePage(raster, 1, 1, settings)
        assertTrue("Page bytes must be generated", pageBytes.isNotEmpty())
        // Ends with form feed (0x0C)
        assertEquals(0x0C.toByte(), pageBytes[pageBytes.size - 1])

        val jobEnd = carps.encodeJobEnd()
        assertTrue("Job end must have release commands", jobEnd.isNotEmpty())
    }

    @Test
    fun testUfriiLtAndPclEncoders() {
        val ufrii = UfriiLtEncoder()
        val settings = PrintSettings()
        val ufriiStart = String(ufrii.encodeJobStart(settings, 1), Charsets.US_ASCII)
        assertTrue(ufriiStart.contains("@PJL ENTER LANGUAGE = UFRII"))

        val pcl = RawPclEncoder()
        val pclStart = String(pcl.encodeJobStart(settings, 1), Charsets.US_ASCII)
        assertTrue(pclStart.contains("@PJL ENTER LANGUAGE = PCL"))
    }

    @Test
    fun testIeee1284DeviceParser() {
        val raw = "MFG:Canon;CMD:CARPS2,UFRII LT;MDL:LBP6030/6030B/6018L;CLS:PRINTER;DES:Canon LBP6030/6030B/6018L;"
        val devId = Ieee1284Parser.parseString(raw)

        assertEquals("Canon", devId.manufacturer)
        assertEquals("LBP6030/6030B/6018L", devId.model)
        assertTrue(devId.commandSet.contains("CARPS2"))
        assertTrue(devId.commandSet.contains("UFRII LT"))
        assertTrue(devId.supportsCarps2)
        assertTrue(devId.supportsUfrii)
    }

    @Test
    fun testUsbPortStatusParsing() {
        // Ready status: Selected=1 (bit 4 = 0x10), NotError=1 (bit 3 = 0x08), PaperEmpty=0 (bit 5 = 0) -> 0x18
        val readyStatus = UsbPrinterPortStatus.fromByte(0x18)
        assertTrue(readyStatus.isReady)
        assertFalse(readyStatus.paperEmpty)
        assertTrue(readyStatus.selected)
        assertTrue(readyStatus.notError)

        // Paper empty status: PaperEmpty=1 (bit 5 = 0x20) -> 0x38
        val paperOut = UsbPrinterPortStatus.fromByte(0x38)
        assertTrue(paperOut.paperEmpty)
        assertFalse(paperOut.isReady)

        // Error status: NotError=0 -> 0x10
        val errorStatus = UsbPrinterPortStatus.fromByte(0x10)
        assertFalse(errorStatus.notError)
        assertFalse(errorStatus.isReady)
    }

    @Test
    fun testFakeUsbTransportChunkedTransfer() = runBlocking {
        val transport = FakeUsbTransport()
        assertFalse(transport.isConnected())

        // Connect
        transport.openMock("Canon LBP6030")
        transport.claimInterface(0)
        assertTrue(transport.isConnected())

        // Test writing 32KB in 8KB chunks
        val testData = ByteArray(32768) { (it % 256).toByte() }
        var progressCalls = 0
        val result = transport.writeBulk(testData, timeoutMs = 5000, chunkSize = 8192) { written, total ->
            progressCalls++
            assertEquals(32768L, total)
        }

        assertTrue(result.isSuccess)
        assertEquals(32768L, result.getOrThrow())
        assertEquals(4, progressCalls)
        assertArrayEquals(testData, transport.getCapturedBytes())

        // Test failure simulation
        transport.simulateFailAtByteOffset = 1000L
        val failResult = transport.writeBulk(testData, timeoutMs = 5000, chunkSize = 4096)
        assertTrue(failResult.isFailure)

        transport.close()
        assertFalse(transport.isConnected())
    }

    @Test
    fun testDiagnosticReportJsonAndTextGeneration() {
        val devInfo = UsbDeviceInfo(
            deviceName = "/dev/bus/usb/001/002",
            vendorId = 0x04A9,
            productId = 0x2795,
            manufacturerName = "Canon,Inc.",
            productName = "LBP6030/6030B/6018L",
            serialNumber = "00000000",
            version = "2.0",
            deviceClass = 0,
            deviceSubclass = 0,
            deviceProtocol = 0,
            interfaceCount = 1,
            interfaces = emptyList(),
            permissionGranted = true,
            interfaceClaimed = true,
            ieee1284 = Ieee1284Parser.parseString("MFG:Canon;MDL:LBP6030;CMD:CARPS2;"),
            portStatus = UsbPrinterPortStatus.fromByte(0x18)
        )

        val json = DiagnosticReport.generateJson(devInfo, listOf(devInfo))
        assertTrue(json.contains("LBP6030"))
        assertTrue(json.contains("0x04A9"))
        assertTrue(json.contains("0x2795"))

        val text = DiagnosticReport.generatePlainText(devInfo, listOf(devInfo))
        assertTrue(text.contains("CANON PRINTER STATUS"))
        assertTrue(text.contains("0x04A9"))
    }
}
