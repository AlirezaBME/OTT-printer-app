package com.example.driver.canon

import java.io.ByteArrayOutputStream

/**
 * Encodes validated literal and row-bounded copy subsets of Canon SLIM/HISCOA.
 * Independently validated through Canon v5.00's lCaptDecode; see tools/canon/README.md.
 * CanonNcapEncoder adds the NCAP band framing and CPCA print-stream envelope.
 */
object CanonSlimRasterCodec {
    const val MAX_BAND_BYTES = 100 * 1024

    /**
     * LBP6030's SLIM parameters select a default copy distance of THREE bytes,
     * unlike CARPS's last-byte mode. Only emit copies after three equal bytes in
     * this row. No dictionary, mode toggles or prior-row references are needed.
     * See REPOSITORY_REUSE.md and the independent native decoder fixture tests.
     */
    fun encodeCompressedBand(packedRaster: ByteArray, bytesPerRow: Int, lastBand: Boolean = false): ByteArray {
        require(packedRaster.isNotEmpty() && packedRaster.size <= MAX_BAND_BYTES)
        require(bytesPerRow > 0 && bytesPerRow <= packedRaster.size && packedRaster.size % bytesPerRow == 0) {
            "SLIM raster must contain complete rows"
        }
        val writer = BitWriter()
        for (row in packedRaster.indices step bytesPerRow) {
            val rowEnd = row + bytesPerRow
            var position = row
            while (position < rowEnd) {
                val value = packedRaster[position].toInt() and 0xff
                writer.literal(value)
                position++
                if (position - row < 3 || packedRaster[position - 2] != packedRaster[position - 1] ||
                    packedRaster[position - 3] != packedRaster[position - 1]) continue
                var end = position
                while (end < rowEnd && packedRaster[end] == packedRaster[position - 1]) end++
                var remaining = end - position
                // A three-byte copy is already cheaper than three zero tokens.
                // Short runs stay literal, preserving the bounded expansion.
                if (remaining < 3) continue
                while (remaining > 0) {
                    val count = minOf(remaining, 127 * 128 + 127)
                    if (count >= 128) {
                        writer.put(0xfc, 8)
                        writer.number(count / 128)
                    }
                    writer.put(0xe, 4)
                    writer.number(count % 128)
                    position += count
                    remaining -= count
                }
            }
        }
        return writer.finish(lastBand)
    }

    fun encodeBand(packedRaster: ByteArray, lastBand: Boolean = false): ByteArray {
        require(packedRaster.isNotEmpty() && packedRaster.size <= MAX_BAND_BYTES) {
            "SLIM band must contain 1..$MAX_BAND_BYTES packed raster bytes"
        }
        val writer = BitWriter()
        for (byte in packedRaster) {
            val unsigned = byte.toInt() and 0xff
            writer.literal(unsigned)
        }
        return writer.finish(lastBand)
    }

    private fun BitWriter.finish(lastBand: Boolean): ByteArray {
        // Canon's page filter uses FE/00 for intermediate bands and FE/01 for
        // the final band. Raster decoding alone accepts both; firmware also needs
        // the page-end control. Always add a full padding word when word-aligned,
        // matching Canon's decoder's lookahead and its reference encoder's padding.
        put(0xfe, 8)
        put(if (lastBand) 1 else 0, 2)
        val padding = 32 - bitCount % 32
        repeat(padding) { put(1, 1) }
        return bytes()
    }

    private class BitWriter {
        private val output = ByteArrayOutputStream()
        private var current = 0
        private var used = 0
        var bitCount = 0
            private set

        fun literal(value: Int) {
            if (value == 0) put(0xfd, 8) else put(0xd00 or value, 12)
        }

        fun number(value: Int) {
            require(value in 0..127)
            when (value) {
                0 -> put(0x3f, 6)
                1 -> put(0, 2)
                2 -> put(3, 3)
                3 -> put(2, 3)
                else -> {
                    val bits = 31 - Integer.numberOfLeadingZeros(value)
                    put((1 shl (bits - 1)) - 1, bits - 1)
                    put(0, 1)
                    put(value.inv() and ((1 shl bits) - 1), bits)
                }
            }
        }

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
