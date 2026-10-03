package com.example.raster

import android.graphics.Bitmap
import com.example.core.model.DitherAlgorithm
import com.example.core.model.PrintContentMode

/** Uses three scanlines, rather than two full-page arrays (280 MB at 600 DPI). */
object DitherEngine {
    fun convertToRaster(bitmap: Bitmap, dpi: Int, algorithm: DitherAlgorithm,
                        contentMode: PrintContentMode, contrastBoost: Float = 1.15f,
                        brightnessOffset: Float = 0f): RasterPage {
        val width = bitmap.width
        val height = bitmap.height
        val stride = (width + 7) / 8
        val data = ByteArray(stride * height)
        val pixels = IntArray(width)
        var current = FloatArray(width)
        var next = FloatArray(width)
        var afterNext = FloatArray(width)
        val threshold = contentMode == PrintContentMode.DOCUMENT_TEXT || algorithm == DitherAlgorithm.THRESHOLD
        for (y in 0 until height) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                val p = pixels[x]
                val alpha = (p ushr 24) / 255f
                val luma = (.299f * ((p ushr 16) and 255) + .587f * ((p ushr 8) and 255) + .114f * (p and 255))
                val whiteComposite = luma * alpha + 255f * (1f - alpha)
                current[x] += ((whiteComposite - 128f) * contrastBoost + 128f + brightnessOffset * 255f).coerceIn(0f, 255f)
            }
            val reverse = algorithm == DitherAlgorithm.FLOYD_STEINBERG && y % 2 == 1
            val xs = if (reverse) width - 1 downTo 0 else 0 until width
            for (x in xs) {
                val old = current[x]
                val black = old < if (threshold) 160f else 128f
                if (black) {
                    val index = y * stride + x / 8
                    data[index] = (data[index].toInt() or (1 shl (7 - x % 8))).toByte()
                }
                if (threshold) continue
                val error = old - if (black) 0f else 255f
                if (algorithm == DitherAlgorithm.ATKINSON) {
                    val e = error / 8f
                    if (x + 1 < width) current[x + 1] += e
                    if (x + 2 < width) current[x + 2] += e
                    if (x > 0) next[x - 1] += e
                    next[x] += e
                    if (x + 1 < width) next[x + 1] += e
                    afterNext[x] += e
                } else {
                    val direction = if (reverse) -1 else 1
                    if (x + direction in 0 until width) current[x + direction] += error * 7f / 16f
                    if (x - direction in 0 until width) next[x - direction] += error * 3f / 16f
                    next[x] += error * 5f / 16f
                    if (x + direction in 0 until width) next[x + direction] += error / 16f
                }
            }
            val used = current
            current = next
            next = afterNext
            afterNext = used
            afterNext.fill(0f)
        }
        return RasterPage(width, height, dpi, data)
    }
}
