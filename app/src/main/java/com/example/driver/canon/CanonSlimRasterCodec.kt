package com.example.driver.canon

import java.io.ByteArrayOutputStream

/**
 * Encodes the literal subset of Canon SLIM/HISCOA raster bands.
 * Independently validated through Canon v5.00's lCaptDecode; see tools/canon/README.md.
 * CanonNcapEncoder adds the NCAP band framing and CPCA print-stream envelope.
 */
object CanonSlimRasterCodec {
    const val MAX_BAND_BYTES = 100 * 1024

    fun encodeBand(packedRaster: ByteArray): ByteArray {
        require(packedRaster.isNotEmpty() && packedRaster.size <= MAX_BAND_BYTES) {
            "SLIM band must contain 1..$MAX_BAND_BYTES packed raster bytes"
        }
        val writer = BitWriter()
        for (byte in packedRaster) {
            val unsigned = byte.toInt() and 0xff
            if (unsigned == 0) writer.put(0xfd, 8)
            else writer.put(0xd00 or unsigned, 12)
        }
        // Normal band end. Always add a full padding word when already word-aligned,
        // matching Canon's decoder's lookahead and its reference encoder's padding.
        writer.put(0xfe, 8)
        writer.put(0, 2)
        val padding = 32 - writer.bitCount % 32
        repeat(padding) { writer.put(1, 1) }
        return writer.bytes()
    }

    private class BitWriter {
        private val output = ByteArrayOutputStream()
        private var current = 0
        private var used = 0
        var bitCount = 0
            private set

        fun put(value: Int, count: Int) {
            for (index in count - 1 downTo 0) {
                current = (current shl 1) or ((value ushr index) and 1)
                used++
                bitCount++
                if (used == 8) {
                    output.write(current xor 0x43)
                    current = 0
                    used = 0
                }
            }
        }

        fun bytes(): ByteArray {
            check(used == 0)
            return output.toByteArray()
        }
    }
}
