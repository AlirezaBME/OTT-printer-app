package com.example.driver

import com.example.core.model.PaperSize
import com.example.core.model.PrintSettings
import com.example.raster.RasterPage
import java.io.ByteArrayOutputStream

/**
 * Standard PCL Laser raster driver for testing and compatibility.
 */
class RawPclEncoder : PrinterLanguageEncoder {
    override val driverId: String = "raw_pcl"
    override val displayName: String = "Standard Laser PCL"

    override fun encodeJobStart(settings: PrintSettings, totalPages: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val init = StringBuilder()
            .append("\u001b%-12345X@PJL\r\n")
            .append("@PJL SET RESOLUTION = ${settings.quality.dpi}\r\n")
            .append("@PJL ENTER LANGUAGE = PCL\r\n")
            .append("\u001bE") // PCL Reset
            .append("\u001b&l${settings.paperSize.pclCode}A") // Paper size
            .append("\u001b&l0O") // Portrait
            .append("\u001b*t${settings.quality.dpi}R") // Graphics resolution
            .append("\u001b*r1A") // Start raster at top left
            .toString()
        out.write(init.toByteArray(Charsets.US_ASCII))
        return out.toByteArray()
    }

    override fun encodePage(
        rasterPage: RasterPage,
        pageNumber: Int,
        totalPages: Int,
        settings: PrintSettings
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val bytesPerRow = rasterPage.bytesPerRow

        // Transfer raster row by row: ESC * b <count> W <data>
        val rowBuffer = ByteArray(bytesPerRow)
        for (y in 0 until rasterPage.height) {
            System.arraycopy(rasterPage.data, y * bytesPerRow, rowBuffer, 0, bytesPerRow)
            val rowHeader = "\u001b*b${bytesPerRow}W".toByteArray(Charsets.US_ASCII)
            out.write(rowHeader)
            out.write(rowBuffer)
        }

        // End raster graphics: ESC * r B
        out.write("\u001b*rB".toByteArray(Charsets.US_ASCII))
        // Form feed: 0x0C
        out.write(byteArrayOf(0x0C))

        return out.toByteArray()
    }

    override fun encodeJobEnd(): ByteArray {
        val out = ByteArrayOutputStream()
        val trailer = "\u001bE\u001b%-12345X@PJL EOJ\r\n\u001b%-12345X"
        out.write(trailer.toByteArray(Charsets.US_ASCII))
        return out.toByteArray()
    }
}
