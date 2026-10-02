package com.example.driver

import com.example.core.model.PaperSize
import com.example.core.model.PrintSettings
import com.example.raster.CcittG4Encoder
import com.example.raster.RasterPage
import java.io.ByteArrayOutputStream

/**
 * Canon UFRII LT Encoder for Canon LBP laser printers.
 */
class UfriiLtEncoder(
    private val g4Encoder: CcittG4Encoder = CcittG4Encoder()
) : PrinterLanguageEncoder {

    override val driverId: String = "ufrii_lt"
    override val displayName: String = "Canon UFRII LT"

    override fun encodeJobStart(settings: PrintSettings, totalPages: Int): ByteArray {
        val out = ByteArrayOutputStream()

        val paperName = when (settings.paperSize) {
            PaperSize.A4 -> "A4"
            PaperSize.A5 -> "A5"
            PaperSize.LETTER -> "LETTER"
        }

        // Standard Universal PJL Envelope
        val pjlHeader = StringBuilder()
            .append("\u001b%-12345X@PJL\r\n")
            .append("@PJL SET JOBNAME = \"LBP_OTG_PRINT\"\r\n")
            .append("@PJL SET RESOLUTION = ${settings.quality.dpi}\r\n")
            .append("@PJL SET PAPER = $paperName\r\n")
            .append("@PJL SET COPIES = ${settings.copies.coerceIn(1, 99)}\r\n")
            .append("@PJL ENTER LANGUAGE = UFRII\r\n")
            .toString()

        out.write(pjlHeader.toByteArray(Charsets.US_ASCII))
        return out.toByteArray()
    }

    override fun encodePage(
        rasterPage: RasterPage,
        pageNumber: Int,
        totalPages: Int,
        settings: PrintSettings
    ): ByteArray {
        val out = ByteArrayOutputStream()

        // UFRII Page Start command
        val pageStart = byteArrayOf(
            0x1B.toByte(), 0x55.toByte(), 0x01.toByte(), // ESC U 0x01 (Page Start)
            (pageNumber and 0xFF).toByte(),
            ((rasterPage.width shr 8) and 0xFF).toByte(),
            (rasterPage.width and 0xFF).toByte(),
            ((rasterPage.height shr 8) and 0xFF).toByte(),
            (rasterPage.height and 0xFF).toByte()
        )
        out.write(pageStart)

        // Compress raster using CCITT Group 4
        val compressed = g4Encoder.encode(rasterPage)

        // UFRII Raster data block header: ESC U 0x02 <4-byte length>
        val len = compressed.size
        val dataHeader = byteArrayOf(
            0x1B.toByte(), 0x55.toByte(), 0x02.toByte(),
            ((len shr 24) and 0xFF).toByte(),
            ((len shr 16) and 0xFF).toByte(),
            ((len shr 8) and 0xFF).toByte(),
            (len and 0xFF).toByte()
        )
        out.write(dataHeader)
        out.write(compressed)

        // Page End / Eject: ESC U 0x03
        out.write(byteArrayOf(0x1B.toByte(), 0x55.toByte(), 0x03.toByte(), 0x0C.toByte()))

        return out.toByteArray()
    }

    override fun encodeJobEnd(): ByteArray {
        val out = ByteArrayOutputStream()
        // PJL Job Trailer
        val pjlTrailer = "\u001b%-12345X@PJL EOJ\r\n\u001b%-12345X"
        out.write(pjlTrailer.toByteArray(Charsets.US_ASCII))
        return out.toByteArray()
    }
}
