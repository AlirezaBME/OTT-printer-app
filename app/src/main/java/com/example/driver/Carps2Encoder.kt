package com.example.driver

import com.example.core.model.PaperSize
import com.example.core.model.PrintOrientation
import com.example.core.model.PrintSettings
import com.example.raster.CcittG4Encoder
import com.example.raster.RasterPage
import java.io.ByteArrayOutputStream

/**
 * Canon Advanced Raster Printing System 2 (CARPS2) Encoder.
 * Target family: Canon LBP6030 / LBP6040 / LBP6018L.
 */
class Carps2Encoder(
    private val g4Encoder: CcittG4Encoder = CcittG4Encoder()
) : PrinterLanguageEncoder {

    override val driverId: String = "carps2"
    override val displayName: String = "Canon CARPS2"

    companion object {
        // ESC character
        private const val ESC: Byte = 0x1B
        // Form Feed
        private const val FF: Byte = 0x0C
    }

    override fun encodeJobStart(settings: PrintSettings, totalPages: Int): ByteArray {
        val out = ByteArrayOutputStream()

        // 1. Initial Device Synchronization
        // ESC @ (Printer Reset)
        out.write(byteArrayOf(ESC, 0x40))

        // 2. CARPS2 Mode Entry sequence
        // ESC [ K: Switch into Canon CARPS2 command mode
        out.write(byteArrayOf(ESC, 0x5B, 0x4B))

        // 3. Media and Job Configuration Packet
        // Command syntax: ESC [ <params> ; <cmd>
        val paperCode = settings.paperSize.carpsCode.toInt()
        val copies = settings.copies.coerceIn(1, 99)
        val dpi = settings.quality.dpi

        // Setup Resolution and Paper packet
        val initCmd = String.format(
            "\u001b[0;%d;%d;%dz",
            dpi,
            paperCode,
            copies
        ).toByteArray(Charsets.US_ASCII)
        out.write(initCmd)

        return out.toByteArray()
    }

    override fun encodePage(
        rasterPage: RasterPage,
        pageNumber: Int,
        totalPages: Int,
        settings: PrintSettings
    ): ByteArray {
        val out = ByteArrayOutputStream()

        // Page Header: dimensions (width, height in dots) and page number
        val pageHeader = String.format(
            "\u001b[%d;%d;%dp",
            pageNumber,
            rasterPage.width,
            rasterPage.height
        ).toByteArray(Charsets.US_ASCII)
        out.write(pageHeader)

        // Compress monochrome raster page using CCITT Group 4 (T.6)
        val compressedG4Data = g4Encoder.encode(rasterPage)

        // Write raster data in chunked packets
        // Packet Header: ESC [ <length> r followed by binary compressed bytes
        val chunkSize = 4096
        var offset = 0
        while (offset < compressedG4Data.size) {
            val chunkLen = (compressedG4Data.size - offset).coerceAtMost(chunkSize)
            val chunkHeader = String.format("\u001b[%dr", chunkLen).toByteArray(Charsets.US_ASCII)
            out.write(chunkHeader)
            out.write(compressedG4Data, offset, chunkLen)
            offset += chunkLen
        }

        // Page Trailer: Form Feed / Eject
        out.write(byteArrayOf(FF))

        return out.toByteArray()
    }

    override fun encodeJobEnd(): ByteArray {
        val out = ByteArrayOutputStream()
        // Reset printer and release CARPS2 mode
        out.write(byteArrayOf(ESC, 0x5B, 0x4B)) // Exit CARPS
        out.write(byteArrayOf(ESC, 0x40))       // Reset
        return out.toByteArray()
    }
}
