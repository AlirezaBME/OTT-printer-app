package com.example.raster

import java.io.ByteArrayOutputStream

/**
 * Pure Kotlin CCITT Group 4 (ITU-T T.6) 2D monochrome raster encoder.
 * Used by Canon CARPS / CARPS2 and TIFF-G4 printers.
 */
class CcittG4Encoder {

    private class BitWriter(val outputStream: ByteArrayOutputStream) {
        private var currentByte = 0
        private var bitCount = 0

        fun writeBits(value: Int, count: Int) {
            for (i in count - 1 downTo 0) {
                val bit = (value shr i) and 1
                currentByte = (currentByte shl 1) or bit
                bitCount++
                if (bitCount == 8) {
                    outputStream.write(currentByte)
                    currentByte = 0
                    bitCount = 0
                }
            }
        }

        fun flush() {
            if (bitCount > 0) {
                currentByte = currentByte shl (8 - bitCount)
                outputStream.write(currentByte)
                currentByte = 0
                bitCount = 0
            }
        }
    }

    fun encode(rasterPage: RasterPage): ByteArray {
        val outStream = ByteArrayOutputStream()
        val writer = BitWriter(outStream)

        val width = rasterPage.width
        val height = rasterPage.height

        var refLine = BooleanArray(width) // initially all white (false)
        val codingLine = BooleanArray(width)

        for (y in 0 until height) {
            // Read coding line pixels (true = black, false = white)
            for (x in 0 until width) {
                codingLine[x] = rasterPage.getPixel(x, y)
            }

            encodeLine(codingLine, refLine, width, writer)

            // Current coding line becomes reference line for next line
            System.arraycopy(codingLine, 0, refLine, 0, width)
        }

        // EOFB (End of Facsimile Block): two EOL sequences
        writer.writeBits(0x001, 12)
        writer.writeBits(0x001, 12)
        writer.flush()

        return outStream.toByteArray()
    }

    private fun encodeLine(
        coding: BooleanArray,
        ref: BooleanArray,
        width: Int,
        writer: BitWriter
    ) {
        var a0 = -1
        var a0Color = false // initially imaginary white

        while (a0 < width) {
            val b1 = findNextChangingElement(ref, a0, width, !a0Color)
            val b2 = if (b1 < width) findNextChangingElement(ref, b1, width, a0Color) else width
            val a1 = findNextChangingElement(coding, a0, width, !a0Color)

            if (b2 < a1) {
                // Pass mode
                writer.writeBits(0x1, 4) // "0001"
                a0 = b2
            } else {
                val diff = a1 - b1
                if (diff in -3..3) {
                    // Vertical mode
                    when (diff) {
                        0 -> writer.writeBits(0x1, 1)        // V0: "1"
                        1 -> writer.writeBits(0x3, 3)        // VR1: "011"
                        2 -> writer.writeBits(0x3, 6)        // VR2: "000011"
                        3 -> writer.writeBits(0x3, 7)        // VR3: "0000011"
                        -1 -> writer.writeBits(0x2, 3)       // VL1: "010"
                        -2 -> writer.writeBits(0x2, 6)       // VL2: "000010"
                        -3 -> writer.writeBits(0x2, 7)       // VL3: "0000010"
                    }
                    a0 = a1
                    a0Color = if (a0 in 0 until width) coding[a0] else !a0Color
                } else {
                    // Horizontal mode
                    val a2 = if (a1 < width) findNextChangingElement(coding, a1, width, a0Color) else width
                    writer.writeBits(0x1, 3) // H: "001"

                    val run1 = a1 - (if (a0 < 0) 0 else a0)
                    val run2 = a2 - a1

                    writeHuffmanRun(writer, run1, a0Color)
                    writeHuffmanRun(writer, run2, !a0Color)

                    a0 = a2
                    a0Color = if (a0 in 0 until width) coding[a0] else a0Color
                }
            }
        }
    }

    private fun findNextChangingElement(
        line: BooleanArray,
        startPos: Int,
        width: Int,
        expectedColor: Boolean
    ): Int {
        var x = (startPos + 1).coerceAtLeast(0)
        while (x < width) {
            if (line[x] == expectedColor) return x
            x++
        }
        return width
    }

    private fun writeHuffmanRun(writer: BitWriter, length: Int, isBlack: Boolean) {
        var remaining = length

        // Make-up codes (multiples of 64 up to 2560)
        while (remaining >= 64) {
            val makeUp = if (remaining >= 2560) 2560 else (remaining / 64) * 64
            val code = if (isBlack) getBlackMakeUp(makeUp) else getWhiteMakeUp(makeUp)
            if (code != null) {
                writer.writeBits(code.first, code.second)
            }
            remaining -= makeUp
        }

        // Terminating code (0..63)
        val term = if (isBlack) getBlackTerminating(remaining) else getWhiteTerminating(remaining)
        writer.writeBits(term.first, term.second)
    }

    // Standard Modified Huffman tables (ITU-T T.4 / T.6)
    private fun getWhiteTerminating(len: Int): Pair<Int, Int> {
        val table = WHITE_TERMINATING
        return if (len in table.indices) table[len] else Pair(0, 0)
    }

    private fun getBlackTerminating(len: Int): Pair<Int, Int> {
        val table = BLACK_TERMINATING
        return if (len in table.indices) table[len] else Pair(0, 0)
    }

    private fun getWhiteMakeUp(len: Int): Pair<Int, Int>? {
        val idx = (len / 64) - 1
        return if (idx in WHITE_MAKEUP.indices) WHITE_MAKEUP[idx] else null
    }

    private fun getBlackMakeUp(len: Int): Pair<Int, Int>? {
        val idx = (len / 64) - 1
        return if (idx in BLACK_MAKEUP.indices) BLACK_MAKEUP[idx] else null
    }

    companion object {
        // Table values: Pair(codeValue, bitLength)
        private val WHITE_TERMINATING = arrayOf(
            Pair(0x35, 8), Pair(0x7, 6), Pair(0x7, 4), Pair(0x8, 4), Pair(0xB, 4), Pair(0xC, 4), Pair(0xE, 4), Pair(0xF, 4),
            Pair(0x13, 5), Pair(0x14, 5), Pair(0x7, 5), Pair(0x8, 5), Pair(0x8, 6), Pair(0x3, 6), Pair(0x34, 6), Pair(0x35, 6),
            Pair(0x2A, 6), Pair(0x2B, 6), Pair(0x27, 7), Pair(0xC, 7), Pair(0x8, 7), Pair(0x17, 7), Pair(0x3, 7), Pair(0x4, 7),
            Pair(0x28, 7), Pair(0x2B, 7), Pair(0x13, 7), Pair(0x24, 7), Pair(0x18, 7), Pair(0x2, 8), Pair(0x3, 8), Pair(0x1A, 8),
            Pair(0x1B, 8), Pair(0x12, 8), Pair(0x13, 8), Pair(0x14, 8), Pair(0x15, 8), Pair(0x16, 8), Pair(0x17, 8), Pair(0x28, 8),
            Pair(0x29, 8), Pair(0x2A, 8), Pair(0x2B, 8), Pair(0x2C, 8), Pair(0x2D, 8), Pair(0x4, 8), Pair(0x5, 8), Pair(0xA, 8),
            Pair(0xB, 8), Pair(0x52, 8), Pair(0x53, 8), Pair(0x54, 8), Pair(0x55, 8), Pair(0x24, 8), Pair(0x25, 8), Pair(0x58, 8),
            Pair(0x59, 8), Pair(0x5A, 8), Pair(0x5B, 8), Pair(0x4A, 8), Pair(0x4B, 8), Pair(0x32, 8), Pair(0x33, 8), Pair(0x34, 8)
        )

        private val BLACK_TERMINATING = arrayOf(
            Pair(0x37, 10), Pair(0x2, 3), Pair(0x3, 2), Pair(0x2, 2), Pair(0x3, 3), Pair(0x3, 4), Pair(0x2, 4), Pair(0x3, 5),
            Pair(0x5, 6), Pair(0x4, 6), Pair(0x4, 7), Pair(0x5, 7), Pair(0x7, 7), Pair(0x4, 8), Pair(0x7, 8), Pair(0x18, 9),
            Pair(0x17, 10), Pair(0x18, 10), Pair(0x8, 10), Pair(0x67, 11), Pair(0x68, 11), Pair(0x6C, 11), Pair(0x37, 11), Pair(0x28, 11),
            Pair(0x17, 11), Pair(0x18, 11), Pair(0xCA, 12), Pair(0xCB, 12), Pair(0xCC, 12), Pair(0xCD, 12), Pair(0x68, 12), Pair(0x69, 12),
            Pair(0x6A, 12), Pair(0x6B, 12), Pair(0xD2, 12), Pair(0xD3, 12), Pair(0xD4, 12), Pair(0xD5, 12), Pair(0xD6, 12), Pair(0xD7, 12),
            Pair(0x6C, 12), Pair(0x6D, 12), Pair(0xDA, 12), Pair(0xDB, 12), Pair(0x54, 12), Pair(0x55, 12), Pair(0x56, 12), Pair(0x57, 12),
            Pair(0x64, 12), Pair(0x65, 12), Pair(0x52, 12), Pair(0x53, 12), Pair(0x24, 12), Pair(0x37, 12), Pair(0x38, 12), Pair(0x27, 12),
            Pair(0x28, 12), Pair(0x58, 12), Pair(0x59, 12), Pair(0x2B, 12), Pair(0x2C, 12), Pair(0x5A, 12), Pair(0x66, 12), Pair(0x67, 12)
        )

        private val WHITE_MAKEUP = arrayOf(
            Pair(0x1B, 5),   // 64
            Pair(0x12, 5),   // 128
            Pair(0x17, 6),   // 192
            Pair(0x37, 7),   // 256
            Pair(0x36, 8),   // 320
            Pair(0x37, 8),   // 384
            Pair(0x64, 8),   // 448
            Pair(0x65, 8),   // 512
            Pair(0x68, 8),   // 576
            Pair(0x67, 8),   // 640
            Pair(0xCC, 9),   // 704
            Pair(0xCD, 9),   // 768
            Pair(0xD2, 9),   // 832
            Pair(0xD3, 9),   // 896
            Pair(0xD4, 9),   // 960
            Pair(0xD5, 9),   // 1024
            Pair(0xD6, 9),   // 1088
            Pair(0xD7, 9),   // 1152
            Pair(0xD8, 9),   // 1216
            Pair(0xD9, 9),   // 1280
            Pair(0xDA, 9),   // 1344
            Pair(0xDB, 9),   // 1408
            Pair(0x98, 9),   // 1472
            Pair(0x99, 9),   // 1536
            Pair(0x9A, 9),   // 1600
            Pair(0x18, 6),   // 1664
            Pair(0x9B, 9),   // 1728
            Pair(0x8, 11),   // 1792
            Pair(0xC, 11),   // 1856
            Pair(0xD, 11),   // 1920
            Pair(0x12, 12),  // 1984
            Pair(0x13, 12),  // 2048
            Pair(0x14, 12),  // 2112
            Pair(0x15, 12),  // 2176
            Pair(0x16, 12),  // 2240
            Pair(0x17, 12),  // 2304
            Pair(0x1C, 12),  // 2368
            Pair(0x1D, 12),  // 2432
            Pair(0x1E, 12),  // 2496
            Pair(0x1F, 12)   // 2560
        )

        private val BLACK_MAKEUP = arrayOf(
            Pair(0x0F, 10),  // 64
            Pair(0xC8, 12),  // 128
            Pair(0xC9, 12),  // 192
            Pair(0x5B, 12),  // 256
            Pair(0x33, 12),  // 320
            Pair(0x34, 12),  // 384
            Pair(0x35, 12),  // 448
            Pair(0x6C, 13),  // 512
            Pair(0x6D, 13),  // 576
            Pair(0x4A, 13),  // 640
            Pair(0x4B, 13),  // 704
            Pair(0x4C, 13),  // 768
            Pair(0x4D, 13),  // 832
            Pair(0x72, 13),  // 896
            Pair(0x73, 13),  // 960
            Pair(0x74, 13),  // 1024
            Pair(0x75, 13),  // 1088
            Pair(0x76, 13),  // 1152
            Pair(0x77, 13),  // 1216
            Pair(0x52, 13),  // 1280
            Pair(0x53, 13),  // 1344
            Pair(0x54, 13),  // 1408
            Pair(0x55, 13),  // 1472
            Pair(0x5A, 13),  // 1536
            Pair(0x5B, 13),  // 1600
            Pair(0x64, 13),  // 1664
            Pair(0x65, 13),  // 1728
            Pair(0x8, 11),   // 1792
            Pair(0xC, 11),   // 1856
            Pair(0xD, 11),   // 1920
            Pair(0x12, 12),  // 1984
            Pair(0x13, 12),  // 2048
            Pair(0x14, 12),  // 2112
            Pair(0x15, 12),  // 2176
            Pair(0x16, 12),  // 2240
            Pair(0x17, 12),  // 2304
            Pair(0x1C, 12),  // 2368
            Pair(0x1D, 12),  // 2432
            Pair(0x1E, 12),  // 2496
            Pair(0x1F, 12)   // 2560
        )
    }
}
