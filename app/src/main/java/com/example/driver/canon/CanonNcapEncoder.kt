package com.example.driver.canon

import com.example.core.model.PaperSize
import com.example.core.model.PrintSettings
import com.example.driver.PrinterLanguageEncoder
import com.example.raster.RasterPage
import com.example.driver.canon.CanonCpca.be16
import com.example.driver.canon.CanonCpca.hex
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/** Independent LBP6030/6040/6018L NCAP serializer, carried in a CPCA print stream.
 * The printer consumes 600 DPI, two-bit black pixels (0=white, 3=black). Draft input
 * is doubled; landscape is rotated onto portrait-fed media. Each independently
 * compressed band is small enough for a complete CPCA packet and bounded memory.
 */
class CanonNcapEncoder : PrinterLanguageEncoder {
    override val driverId = "canon_ncap_lbp6030"
    override val displayName = "Experimental Canon NCAP/CPCA"
    private var pages = 0
    private var encodedPages = 0
    override fun encodeJobStart(settings: PrintSettings, totalPages: Int): ByteArray {
        settings.validate()
        require(totalPages in 1..990000)
        pages = totalPages; encodedPages = 0
        return CanonCpca.start() + CanonCpca.data(hex("01c18510001089c200d8840258dd80c8f0840800"))
    }

    override fun encodePage(rasterPage: RasterPage, pageNumber: Int, totalPages: Int, settings: PrintSettings): ByteArray {
        require(pages == totalPages && pageNumber == encodedPages + 1 && pageNumber <= pages) { "Invalid Canon page sequence" }
        val page = encodeNcapPage(rasterPage, settings)
        encodedPages++
        return CanonCpca.data(page)
    }

    override fun encodeJobEnd(): ByteArray {
        check(pages > 0 && encodedPages == pages) { "Canon job is incomplete" }
        val end = CanonCpca.data(byteArrayOf(0x11)) + CanonCpca.end(pages)
        pages = 0; encodedPages = 0
        return end
    }

    internal fun encodeNcapPage(raster: RasterPage, settings: PrintSettings): ByteArray {
        require(raster.dpi == 300 || raster.dpi == 600) { "Canon supports 300 or 600 DPI source pages" }
        val scale = 600 / raster.dpi
        val landscape = raster.width > raster.height
        val sourceWidth = if (landscape) raster.height else raster.width
        val sourceHeight = if (landscape) raster.width else raster.height
        val mediaWidth = settings.paperSize.getPixelWidth(600)
        val mediaHeight = settings.paperSize.getPixelHeight(600)
        require(kotlin.math.abs(sourceWidth * scale - mediaWidth) <= 4 &&
            kotlin.math.abs(sourceHeight * scale - mediaHeight) <= 4) { "Canon raster must match the selected paper size" }
        // The official filter rounds the row stride to 32 bytes (128 two-bit pixels).
        val width = (mediaWidth + 127) / 128 * 128
        val height = (settings.paperSize.heightMm / 25.4 * 600).roundToInt()
        val stride = width / 4
        // Compressed text/white bands are small; do not reserve a raw full page.
        val out = ByteArrayOutputStream(64 * 1024)
        val paper = when (settings.paperSize) { PaperSize.A4 -> 0; PaperSize.A5 -> 0x0b; PaperSize.LETTER -> 2 }
        out.write(hex("02c3")); out.write(paper); out.write(hex("c500c60051f20003e785"))
        out.be16(width); out.be16(height); out.write(hex("de8000c800caa10000cb0061e68002e500"))
        var y = 0
        while (y < height) {
            val rows = minOf(32, height - y)
            val raw = ByteArray(stride * rows)
            for (row in 0 until rows) {
                val sy = (y + row) / scale
                if (sy >= sourceHeight) continue
                for (x in 0 until minOf(sourceWidth * scale, mediaWidth)) {
                    val sx = x / scale
                    val black = if (landscape) raster.getPixel(sy, raster.height - 1 - sx) else raster.getPixel(sx, sy)
                    if (black) {
                        val index = row * stride + x / 4
                        raw[index] = (raw[index].toInt() or (3 shl (6 - 2 * (x % 4)))).toByte()
                    }
                }
            }
            val lastBand = y + rows == height
            out.write(band(width, rows, y, CanonSlimRasterCodec.encodeCompressedBand(raw, stride, lastBand), lastBand))
            y += rows
        }
        out.write(hex("1312"))
        return out.toByteArray()
    }

    internal fun band(width: Int, rows: Int, y: Int, compressed: ByteArray, lastBand: Boolean = false): ByteArray {
        // SLIM wrapper: eight parameters, continuation flag (zero on final band), LE32(stream length+4),
        // the encoded band, and the NCAP terminator. Length excludes the NCAP header.
        val length = compressed.size + 14
        require(length <= 65535 && rows in 1..256 && width in 1..65535 && y in 0..65535)
        return ByteArrayOutputStream(length + 23).apply {
            write(hex("62e385")); be16(width); be16(rows); write(hex("e8a50000")); be16(y)
            write(hex("e103d784")); be16(length); write(0x9d); be16(length)
            write(hex("0309060100005000")); write(if (lastBand) 0 else 1)
            val size = compressed.size + 4
            repeat(4) { write(size ushr (8 * it)) }
            write(compressed); write(0x80)
        }.toByteArray()
    }
}
