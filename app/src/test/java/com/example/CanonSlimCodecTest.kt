package com.example

import com.example.driver.canon.CanonSlimRasterCodec
import org.junit.Assert.*
import org.junit.Test

class CanonSlimCodecTest {
    @Test fun compressedBandsMatchFixturesAcceptedByCanonDecoder() {
        val resource = javaClass.classLoader!!.getResourceAsStream("canon/slim-copy-vectors.tsv.gz")!!
        var checked = 0
        java.util.zip.GZIPInputStream(resource).bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
                val fields = line.split('\t')
                val raw = hex(fields[4])
                val encoded = CanonSlimRasterCodec.encodeCompressedBand(raw, fields[1].toInt(), fields[6] == "1")
                assertArrayEquals(fields[0], hex(fields[5]), encoded)
                assertTrue(fields[0], encoded.size <= CanonSlimRasterCodec.encodeBand(raw, fields[6] == "1").size)
                assertEquals(0, encoded.size % 4)
                checked++
            }
        }
        assertEquals("Missing independent copy/count fixtures", 112, checked)
    }

    @Test fun blankA4BandUsesBoundedCompressionAndRejectsIncompleteRows() {
        val raw = ByteArray(1248 * 32)
        assertEquals(220, CanonSlimRasterCodec.encodeCompressedBand(raw, 1248).size)
        for (stride in listOf(0, -1, raw.size + 1, 1247)) {
            try { CanonSlimRasterCodec.encodeCompressedBand(raw, stride); fail("Invalid stride accepted") }
            catch (_: IllegalArgumentException) {}
        }
        for (rawInvalid in listOf(ByteArray(0), ByteArray(CanonSlimRasterCodec.MAX_BAND_BYTES + 1))) {
            try { CanonSlimRasterCodec.encodeCompressedBand(rawInvalid, 1); fail("Invalid band accepted") }
            catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun literalBandsMatchGoldenVectorsAcceptedByCanonDecoder() {
        val resource = javaClass.classLoader!!.getResourceAsStream("canon/slim-literal-vectors.tsv")!!
        var checked = 0
        resource.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
                val fields = line.split('\t')
                val raw = hex(fields[4])
                val encoded = CanonSlimRasterCodec.encodeBand(raw)
                assertArrayEquals(fields[0], hex(fields[5]), encoded)
                assertEquals(0, encoded.size % 4)
                checked++
            }
        }
        assertTrue("Missing reference oracle fixtures", checked >= 50)
    }

    @Test fun invalidAndOversizedBandsAreRejectedBeforeEncoding() {
        for (input in listOf(ByteArray(0), ByteArray(CanonSlimRasterCodec.MAX_BAND_BYTES + 1))) {
            try { CanonSlimRasterCodec.encodeBand(input); fail("Accepted invalid band") }
            catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun maximumBandHasBoundedExpansion() {
        val result = CanonSlimRasterCodec.encodeBand(ByteArray(CanonSlimRasterCodec.MAX_BAND_BYTES) { 0xff.toByte() })
        assertTrue(result.size <= CanonSlimRasterCodec.MAX_BAND_BYTES * 3 / 2 + 8)
        for (type in listOf(com.example.core.model.DriverType.CARPS2)) {
            try { com.example.driver.DriverRegistry.getEncoder(type); fail("Partial codec enabled as a USB driver") }
            catch (_: IllegalArgumentException) {}
        }
    }

    private fun hex(text: String): ByteArray = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
