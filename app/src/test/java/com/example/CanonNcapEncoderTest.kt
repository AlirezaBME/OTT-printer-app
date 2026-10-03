package com.example

import com.example.core.model.*
import com.example.driver.DriverRegistry
import com.example.driver.canon.CanonCpca
import com.example.driver.canon.CanonNcapEncoder
import com.example.raster.RasterPage
import com.example.usb.*
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class CanonNcapEncoderTest {
    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 255) shl 8) or (b[i+1].toInt() and 255)
    private fun packets(bytes: ByteArray): List<Pair<Int, ByteArray>> {
        val result = mutableListOf<Pair<Int, ByteArray>>()
        var offset = 0
        while (offset < bytes.size) {
            assertTrue(bytes.size - offset >= 20)
            assertArrayEquals(hex("cdca1000"), bytes.copyOfRange(offset, offset + 4))
            assertArrayEquals(hex("0000"), bytes.copyOfRange(offset+6, offset+8))
            assertArrayEquals(hex("ffff0000000000000000"), bytes.copyOfRange(offset+10, offset+20))
            val length = u16(bytes, offset+8)
            assertTrue(offset + 20 + length <= bytes.size)
            result.add(u16(bytes, offset+4) to bytes.copyOfRange(offset+20, offset+20+length))
            offset += 20 + length
        }
        return result
    }

    @Test fun setupAndFooterMatchOfficialModuleMessages() {
        val reference = javaClass.classLoader!!.getResourceAsStream("canon/cpca-module-packets.tsv")!!
            .bufferedReader().readLines().filter { !it.startsWith("#") && it.isNotBlank() }
            .map { val f=it.split('\t'); f[0].toInt() to if(f[1]=="-")ByteArray(0) else hex(f[1]) }
        val actual = packets(CanonCpca.start())
        assertEquals(19, actual.size)
        for (i in actual.indices) {
            assertEquals(reference[i].first, actual[i].first)
            // User/title metadata differ intentionally; every other setup byte is exact.
            if (i != 0 && i != 4) assertArrayEquals("CPCA setup packet $i", reference[i].second, actual[i].second)
        }
        val first = actual.first().second
        assertEquals(7, u16(first,0))
        var offset = 2
        repeat(u16(first,0)) { offset += 4 + u16(first,offset+2) }
        assertEquals("JobStart2 attribute boundaries", first.size, offset)
        val footer = packets(CanonCpca.end(1))
        assertEquals(reference.takeLast(6).map { it.first }, footer.map { it.first })
        footer.forEachIndexed { i, p -> assertArrayEquals(reference[reference.size-6+i].second, p.second) }
    }

    @Test fun ncapFramingMatchesNativeCanonFunctions() {
        val frames = javaClass.classLoader!!.getResourceAsStream("canon/ncap-native-frames.tsv")!!
            .bufferedReader().readLines().associate { val f=it.split('\t'); f[0] to hex(f[1]) }
        val encoder = CanonNcapEncoder()
        val start = packets(encoder.encodeJobStart(PrintSettings(),1)).last().second.drop(1).toByteArray()
        assertArrayEquals(frames.getValue("job"), start)
        val band = encoder.band(4992,32,64,byteArrayOf(1,2,3,4))
        // Native transfer function's framing, with the independent SLIM wrapper length.
        val expectedHeader = frames.getValue("band").copyOfRange(0,22)
        expectedHeader[18]=18; expectedHeader[21]=18 // four literal bytes + fourteen wrapper bytes
        assertArrayEquals(expectedHeader, band.copyOfRange(0,22))
        assertArrayEquals(hex("030906010000500001080000000102030480"), band.copyOfRange(22,band.size))
    }

    @Test fun cpcaChunkingPreservesEveryByteAtLengthBoundaries() {
        for (size in listOf(1,65533,65534,65535,131068,131069)) {
            val raw = ByteArray(size) { (it * 73).toByte() }
            val chunks = packets(CanonCpca.data(raw))
            val recovered = ByteArrayOutputStream()
            chunks.forEach { (command,payload) ->
                assertEquals(0x1a,command); assertEquals(1,payload[0].toInt())
                assertTrue(payload.size <= 65535); recovered.write(payload,1,payload.size-1)
            }
            assertArrayEquals(raw,recovered.toByteArray())
        }
    }

    @Test fun finalBandEndsEachPageWithOfficialContinuationAndSlimControls() {
        val settings = PrintSettings(paperSize = PaperSize.A5)
        val width = PaperSize.A5.getPixelWidth(300)
        val height = PaperSize.A5.getPixelHeight(300)
        val raster = RasterPage(width, height, 300, ByteArray((width + 7) / 8 * height))
        val encoder = CanonNcapEncoder()
        encoder.encodeJobStart(settings, 2)
        repeat(2) { page ->
            val pdl = ByteArrayOutputStream().apply {
                packets(encoder.encodePage(raster, page + 1, 2, settings)).forEach { (_, data) -> write(data, 1, data.size - 1) }
            }.toByteArray()
            var offset = 34 // Page-only PDL excludes the twenty-byte job header.
            var y = 0
            val actualHeight = u16(pdl, 15)
            while (y < actualHeight) {
                val rows = u16(pdl, offset + 5)
                val length = u16(pdl, offset + 17)
                val last = y + rows == actualHeight
                assertEquals("Page ${page + 1}, y=$y continuation", if (last) 0 else 1, pdl[offset + 30].toInt())
                val encoded = pdl.copyOfRange(offset + 35, offset + 22 + length - 1)
                val bits = encoded.joinToString("") { ((it.toInt() and 255) xor 0x43).toString(2).padStart(8, '0') }.trimEnd('1')
                assertTrue("Page ${page + 1}, y=$y SLIM end", bits.endsWith(if (last) "111111100" else "1111111000"))
                y += rows
                offset += 22 + length
            }
            assertArrayEquals(hex("1312"), pdl.copyOfRange(offset, pdl.size))
        }
        encoder.encodeJobEnd()
    }

    @Test fun automaticSelectionRequiresExactSupportedIdentity() {
        val id=Ieee1284Parser.parseString("MFG:Canon;MDL:LBP6030;CMD:LIPSLX,CPCA;CID:CA_UFRIILT_OIP;")
        val device=UsbDeviceInfo("canon",0x04a9,0x2795,"Canon","LBP6030",null,null,0,0,0,0,emptyList(),true,ieee1284=id)
        assertEquals(DriverType.UFRII_LT,DriverRegistry.resolve(device,DriverType.AUTO))
        assertEquals(DriverType.UFRII_LT,DriverRegistry.resolve(device,DriverType.UFRII_LT))
        for (other in listOf(device.copy(productId=0x1234),device.copy(vendorId=0x1234),
            device.copy(ieee1284=Ieee1284Parser.parseString("CMD:LIPSLX;CID:CA_UFRIILT_OIP;")),
            device.copy(ieee1284=Ieee1284Parser.parseString("CMD:LIPSLX,CPCA;CID:CA_CARPS;")))) {
            try { DriverRegistry.resolve(other,DriverType.AUTO); fail("Unverified Canon model selected") } catch (_: IllegalStateException) {}
        }
        val pcl=device.copy(ieee1284=Ieee1284Parser.parseString("CMD:PCL5;"))
        assertEquals(DriverType.RAW_PCL,DriverRegistry.resolve(pcl,DriverType.AUTO))
        try { DriverRegistry.resolve(device,DriverType.RAW_PCL); fail("Canon sent PCL") } catch (_: IllegalStateException) {}
    }

    @Test fun completeJobsForAllMediaAndOrientationsAreBoundedAndOracleDecodable() {
        val directory=System.getenv("CANON_JOB_OUTPUT")?.let { File(it).apply { mkdirs() } }
        for (paper in PaperSize.entries) for (landscape in listOf(false,true)) for (dpi in listOf(300,600)) {
            val w=paper.getPixelWidth(dpi); val h=paper.getPixelHeight(dpi)
            val width=if(landscape)h else w; val height=if(landscape)w else h
            val raster=RasterPage(width,height,dpi,ByteArray((width+7)/8*height))
            // Black rectangle + isolated pixels exercise packing, orientation, doubling,
            // row endings, white padding and page margins in Canon's real decoder.
            for (y in 40..69) for(x in 60..89) raster.setPixel(x,y,true)
            raster.setPixel(width-1,height-1,true);raster.setPixel(0,0,true)
            val settings=PrintSettings(paperSize=paper,quality=if(dpi==600)PrintQuality.NORMAL_600DPI else PrintQuality.DRAFT_300DPI)
            val encoder=CanonNcapEncoder()
            val job=encoder.encodeJobStart(settings,1)+encoder.encodePage(raster,1,1,settings)+encoder.encodeJobEnd()
            if (paper == PaperSize.A5 && !landscape && dpi == 300) {
                val sha = java.security.MessageDigest.getInstance("SHA-256").digest(job).joinToString("") { "%02x".format(it.toInt() and 255) }
                assertEquals("Deterministic A5 source fixture", "87d7748ea9d3120400e86af6f85e5e5eba6c7c614a3255f3190dbe2678d01998", sha)
            }
            val pieces=packets(job)
            assertTrue(job.size <= paper.getPixelWidth(600)*paper.getPixelHeight(600)*3/8+200000)
            val pdl=ByteArrayOutputStream()
            pieces.filter { it.first==0x1a }.forEach { pdl.write(it.second,1,it.second.size-1) }
            val raw=pdl.toByteArray()
            assertArrayEquals(hex("131211"),raw.takeLast(3).toByteArray())
            assertEquals(0x13,pieces.last().first)
            directory?.let { d ->
                val name=paper.name+(if(landscape)"-landscape" else "-portrait")+"-$dpi"
                File(d,"$name.prn").writeBytes(job)
                File(d,"$name.raster").writeBytes(raster.data)
                File(d,"$name.tsv").writeText("$width\t${height}\t$dpi\t${paper.widthMm}\t${paper.heightMm}\n")
            }
        }
    }

    @Test fun incompleteJobsAndWrongRasterSizeAreRejected() {
        val encoder=CanonNcapEncoder()
        encoder.encodeJobStart(PrintSettings(),2)
        try { encoder.encodeJobEnd();fail("Incomplete job emitted") } catch (_: IllegalStateException) {}
        val tiny=RasterPage(8,8,300,ByteArray(8))
        try { encoder.encodePage(tiny,1,2,PrintSettings());fail("Paper mismatch accepted") } catch (_: IllegalArgumentException) {}
        try { encoder.encodePage(tiny,2,2,PrintSettings());fail("Page order accepted") } catch (_: IllegalArgumentException) {}
    }
}
