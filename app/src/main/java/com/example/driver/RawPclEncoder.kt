package com.example.driver

import com.example.core.model.PrintSettings
import com.example.raster.RasterPage
import java.io.ByteArrayOutputStream

/** PCL 5 monochrome, uncompressed raster. Only for devices advertising PCL 5. */
class RawPclEncoder : PrinterLanguageEncoder {
    override val driverId = "pcl5"
    override val displayName = "PCL 5 monochrome"
    override fun encodeJobStart(settings: PrintSettings, totalPages: Int) = "\u001bE".toByteArray(Charsets.US_ASCII)

    override fun encodePage(rasterPage: RasterPage, pageNumber: Int, totalPages: Int, settings: PrintSettings): ByteArray {
        val out = ByteArrayOutputStream()
        fun command(text: String) = out.write(text.toByteArray(Charsets.US_ASCII))
        command("\u001b&l${settings.paperSize.pclCode}A\u001b&l${if (rasterPage.width > rasterPage.height) 1 else 0}O")
        command("\u001b&l0E\u001b*p0X\u001b*p0Y\u001b*t${rasterPage.dpi}R")
        command("\u001b*r${rasterPage.width}S\u001b*r${rasterPage.height}T\u001b*b0M\u001b*r1A")
        val header = "\u001b*b${rasterPage.bytesPerRow}W".toByteArray(Charsets.US_ASCII)
        for (y in 0 until rasterPage.height) {
            out.write(header)
            out.write(rasterPage.data, y * rasterPage.bytesPerRow, rasterPage.bytesPerRow)
        }
        command("\u001b*rB\u000c")
        return out.toByteArray()
    }
    override fun encodeJobEnd() = "\u001bE".toByteArray(Charsets.US_ASCII)
}
