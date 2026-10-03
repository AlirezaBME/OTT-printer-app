package com.example

import com.example.core.model.*
import com.example.driver.DriverRegistry
import com.example.driver.RawPclEncoder
import com.example.raster.RasterPage
import com.example.usb.Ieee1284Parser
import com.example.usb.UsbPrinterPortStatus
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class DriverAndPipelineTest {
    @Test fun pageRangesSupportPersianDigitsAndDeduplicate() {
        assertEquals(listOf(0, 1, 2, 4), PrintSettings(pageRangeText = "۱-۳، ۵,2").parsePageIndices(5))
        assertEquals(listOf(0, 1), PrintSettings(pageRangeText = "all").parsePageIndices(2))
    }
    @Test fun malformedAndHugeRangesFailBeforeRendering() {
        for (range in listOf("0", "8-10", "3-1", "1-2147483647", "junk", "1,", "1-2-3", "-1")) {
            try { PrintSettings(pageRangeText = range).parsePageIndices(5); fail("Accepted $range") }
            catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun invalidSettingsAreRejected() {
        for (settings in listOf(PrintSettings(copies = 0), PrintSettings(copies = 100), PrintSettings(marginMm = Float.NaN), PrintSettings(contrastBoost = Float.POSITIVE_INFINITY))) {
            try { settings.validate(); fail("Accepted invalid settings") } catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun inventedCarpsProtocolRemainsUnavailable() {
        for (type in listOf(DriverType.CARPS2)) {
            try { DriverRegistry.getEncoder(type); fail("Fabricated Canon driver reachable") } catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun pclOnlyCompatibilityRequiresExplicitPcl5Language() {
        assertTrue(Ieee1284Parser.parseString("CMD:PCL,PJL;").supportsPcl5)
        assertTrue(Ieee1284Parser.parseString("CMD:PCL5e;").supportsPcl5)
        for (languages in listOf("CARPS2,UFRII LT", "PCLXL", "PCL6", "POSTSCRIPT", "")) {
            assertFalse(Ieee1284Parser.parseString("CMD:$languages;").supportsPcl5)
        }
    }
    @Test fun physicalCanonIdentityRequiresUfriiLtWithoutEnablingPcl() {
        val parsed = Ieee1284Parser.parseString("MFG:Canon;MDL:LBP6030/6040/6018L;CMD:LIPSLX,CPCA;CID:CA_UFRIILT_OIP;")
        assertEquals("CA_UFRIILT_OIP", parsed.compatibilityId)
        assertTrue(parsed.identifiesCanonUfriiLt)
        assertTrue(parsed.supportsUfrii)
        assertTrue(parsed.advertisesCpca)
        assertFalse(parsed.supportsPcl5)
        assertFalse(parsed.supportsCarps2)
        // LIPSLX/CPCA alone is not a model-specific UFRII LT compatibility identifier.
        assertFalse(Ieee1284Parser.parseString("CMD:LIPSLX,CPCA;").identifiesCanonUfriiLt)
        assertFalse(Ieee1284Parser.parseString("CID:OTHER_UFR;CMD:PCLXL;").supportsPcl5)
    }
    @Test fun lengthPrefixedPrinterIdAndLocaleAreHandled() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            val id = "manufacturer:Canon;model:LBP6030;command set:CARPS2,UFRII LT;"
            val bytes = id.toByteArray(Charsets.US_ASCII)
            val length = bytes.size + 2
            val parsed = Ieee1284Parser.parse(byteArrayOf((length shr 8).toByte(), length.toByte()) + bytes)
            assertEquals("Canon", parsed.manufacturer)
            assertEquals("LBP6030", parsed.model)
            assertTrue(parsed.supportsUfrii)
        } finally { Locale.setDefault(original) }
    }
    @Test fun rasterBitsAndPaddingAreCorrect() {
        val raster = RasterPage(9, 2, 300, ByteArray(4))
        raster.setPixel(0, 0, true)
        raster.setPixel(8, 1, true)
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0, 0, 0x80.toByte()), raster.data)
        assertTrue(raster.getPixel(8, 1))
        assertFalse(raster.getPixel(9, 1))
        try { RasterPage(9, 2, 300, ByteArray(3)); fail("Truncated raster") } catch (_: IllegalArgumentException) {}
    }
    @Test fun everyPclPageStartsRasterAndContainsExactRawRows() {
        val encoder = RawPclEncoder()
        val settings = PrintSettings()
        val raster = RasterPage(8, 2, 300, byteArrayOf(0x81.toByte(), 0x42))
        val page1 = encoder.encodePage(raster, 1, 2, settings)
        val page2 = encoder.encodePage(raster, 2, 2, settings)
        assertArrayEquals(page1, page2)
        val expected = "\u001b*b1W".toByteArray(Charsets.US_ASCII) + byteArrayOf(0x81.toByte()) +
            "\u001b*b1W".toByteArray(Charsets.US_ASCII) + byteArrayOf(0x42)
        val stream = String(page2, Charsets.ISO_8859_1)
        assertTrue(stream.contains("\u001b*r1A"))
        assertTrue(stream.contains("\u001b*b0M"))
        assertTrue(stream.contains(String(expected, Charsets.ISO_8859_1)))
        assertTrue(stream.endsWith("\u001b*rB\u000c"))
    }
    @Test fun portStatusIncludesOfflineHardwareErrorAndPaperOut() {
        assertTrue(UsbPrinterPortStatus.fromByte(0x18).isReady)
        for (status in listOf(0x38, 0x10, 0x08)) assertFalse(UsbPrinterPortStatus.fromByte(status.toByte()).isReady)
    }
    @Test fun truncatedDeviceIdCannotBecomeAFalsePclMatch() {
        val partial = "CMD:PCL".toByteArray(Charsets.US_ASCII)
        val parsed = Ieee1284Parser.parse(byteArrayOf(0, 30) + partial)
        assertFalse(parsed.supportsPcl5)
        assertEquals("", parsed.rawString)
        assertFalse(Ieee1284Parser.parse(byteArrayOf(0, 1, 67)).supportsPcl5)
    }

}
