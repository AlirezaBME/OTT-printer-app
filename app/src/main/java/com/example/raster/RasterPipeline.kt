package com.example.raster

import android.graphics.Bitmap
import com.example.core.model.PrintSettings

class RasterPipeline {

    fun processBitmapToRaster(
        sourceBitmap: Bitmap,
        settings: PrintSettings
    ): RasterPage {
        // Step 1: Render onto target paper canvas
        val paperBitmap = PageRenderer.renderPageToCanvas(sourceBitmap, settings)

        // Step 2: Dither to 1-bit monochrome raster
        try {
        return DitherEngine.convertToRaster(
            bitmap = paperBitmap,
            dpi = settings.quality.dpi,
            algorithm = settings.ditherAlgorithm,
            contentMode = settings.contentMode,
            contrastBoost = settings.contrastBoost,
            brightnessOffset = settings.brightnessOffset
        )

        } finally {
            if (paperBitmap != sourceBitmap) paperBitmap.recycle()
        }
    }
}
