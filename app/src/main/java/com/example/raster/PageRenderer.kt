package com.example.raster

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import com.example.core.model.PrintOrientation
import com.example.core.model.PrintScaling
import com.example.core.model.PrintSettings

object PageRenderer {

    /**
     * Renders a source Bitmap onto a target paper canvas respecting orientation, margins, and scaling.
     */
    fun renderPageToCanvas(
        sourceBitmap: Bitmap,
        settings: PrintSettings
    ): Bitmap {
        val targetDpi = settings.quality.dpi
        val targetPaper = settings.paperSize

        // Target paper pixel dimensions
        var paperWidth = targetPaper.getPixelWidth(targetDpi)
        var paperHeight = targetPaper.getPixelHeight(targetDpi)

        // Resolve orientation
        val effectiveOrientation = when (settings.orientation) {
            PrintOrientation.AUTO -> {
                if (sourceBitmap.width > sourceBitmap.height) PrintOrientation.LANDSCAPE else PrintOrientation.PORTRAIT
            }
            PrintOrientation.PORTRAIT -> PrintOrientation.PORTRAIT
            PrintOrientation.LANDSCAPE -> PrintOrientation.LANDSCAPE
        }

        if (effectiveOrientation == PrintOrientation.LANDSCAPE) {
            val tmp = paperWidth
            paperWidth = paperHeight
            paperHeight = tmp
        }

        val targetBitmap = Bitmap.createBitmap(paperWidth, paperHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(targetBitmap)
        // Laser printer paper starts white
        canvas.drawColor(Color.WHITE)

        val marginPx = ((settings.marginMm / 25.4f) * targetDpi)
        val printableRect = RectF(
            marginPx,
            marginPx,
            paperWidth - marginPx,
            paperHeight - marginPx
        )

        val srcW = sourceBitmap.width.toFloat()
        val srcH = sourceBitmap.height.toFloat()
        val destW = printableRect.width()
        val destH = printableRect.height()

        val matrix = Matrix()

        when (settings.scaling) {
            PrintScaling.FIT_PAGE -> {
                val scale = (destW / srcW).coerceAtMost(destH / srcH)
                val renderedW = srcW * scale
                val renderedH = srcH * scale
                val dx = printableRect.left + (destW - renderedW) / 2f
                val dy = printableRect.top + (destH - renderedH) / 2f
                matrix.postScale(scale, scale)
                matrix.postTranslate(dx, dy)
            }
            PrintScaling.FILL_PAGE -> {
                val scale = (destW / srcW).coerceAtLeast(destH / srcH)
                val renderedW = srcW * scale
                val renderedH = srcH * scale
                val dx = printableRect.left + (destW - renderedW) / 2f
                val dy = printableRect.top + (destH - renderedH) / 2f
                matrix.postScale(scale, scale)
                matrix.postTranslate(dx, dy)
            }
            PrintScaling.ACTUAL_SIZE -> {
                val dx = printableRect.left + (destW - srcW) / 2f
                val dy = printableRect.top + (destH - srcH) / 2f
                matrix.postTranslate(dx, dy)
            }
        }

        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(sourceBitmap, matrix, paint)

        return targetBitmap
    }
}
