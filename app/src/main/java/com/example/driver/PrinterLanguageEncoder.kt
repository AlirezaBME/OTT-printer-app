package com.example.driver

import com.example.core.model.PrintSettings
import com.example.raster.RasterPage

interface PrinterLanguageEncoder {
    val driverId: String
    val displayName: String

    fun encodeJobStart(settings: PrintSettings, totalPages: Int): ByteArray

    fun encodePage(
        rasterPage: RasterPage,
        pageNumber: Int,
        totalPages: Int,
        settings: PrintSettings
    ): ByteArray

    fun encodeJobEnd(): ByteArray
}
