package com.example.raster

import android.graphics.Bitmap
import com.example.core.model.DitherAlgorithm
import com.example.core.model.PrintContentMode

object DitherEngine {

    /**
     * Converts an Android Bitmap into a packed 1-bit per pixel RasterPage.
     * 1 = Black toner (laser), 0 = White paper.
     */
    fun convertToRaster(
        bitmap: Bitmap,
        dpi: Int,
        algorithm: DitherAlgorithm,
        contentMode: PrintContentMode,
        contrastBoost: Float = 1.15f,
        brightnessOffset: Float = 0.0f
    ): RasterPage {
        val width = bitmap.width
        val height = bitmap.height
        val bytesPerRow = (width + 7) / 8
        val outData = ByteArray(bytesPerRow * height)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Convert to 2D float or int grayscale buffer [0..255]
        val gray = FloatArray(width * height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = (p shr 24) and 0xFF
            if (a < 128) {
                // Transparent is treated as white paper (255)
                gray[i] = 255f
            } else {
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                // Standard Rec. 601 luma
                var luma = 0.299f * r + 0.587f * g + 0.114f * b
                
                // Contrast and brightness adjustment
                if (contrastBoost != 1.0f) {
                    luma = ((luma - 128f) * contrastBoost) + 128f
                }
                if (brightnessOffset != 0.0f) {
                    luma += (brightnessOffset * 255f)
                }
                gray[i] = luma.coerceIn(0f, 255f)
            }
        }

        when {
            contentMode == PrintContentMode.DOCUMENT_TEXT || algorithm == DitherAlgorithm.THRESHOLD -> {
                // High contrast document threshold
                val threshold = 160f // Biased slightly towards crisp dark text
                for (y in 0 until height) {
                    val rowOffset = y * bytesPerRow
                    val srcOffset = y * width
                    for (x in 0 until width) {
                        val isBlack = gray[srcOffset + x] < threshold
                        if (isBlack) {
                            val byteIdx = rowOffset + (x shr 3)
                            val bitIdx = 7 - (x and 7)
                            outData[byteIdx] = (outData[byteIdx].toInt() or (1 shl bitIdx)).toByte()
                        }
                    }
                }
            }

            algorithm == DitherAlgorithm.ATKINSON -> {
                // Atkinson dithering distributes 1/8 error to 6 neighbors:
                // (x+1, y), (x+2, y), (x-1, y+1), (x, y+1), (x+1, y+1), (x, y+2)
                for (y in 0 until height) {
                    val rowOffset = y * bytesPerRow
                    for (x in 0 until width) {
                        val idx = y * width + x
                        val oldVal = gray[idx]
                        val newVal = if (oldVal < 128f) 0f else 255f
                        val isBlack = newVal == 0f

                        if (isBlack) {
                            val byteIdx = rowOffset + (x shr 3)
                            val bitIdx = 7 - (x and 7)
                            outData[byteIdx] = (outData[byteIdx].toInt() or (1 shl bitIdx)).toByte()
                        }

                        val err = (oldVal - newVal) / 8f
                        if (x + 1 < width) gray[idx + 1] += err
                        if (x + 2 < width) gray[idx + 2] += err
                        if (y + 1 < height) {
                            if (x - 1 >= 0) gray[(y + 1) * width + (x - 1)] += err
                            gray[(y + 1) * width + x] += err
                            if (x + 1 < width) gray[(y + 1) * width + (x + 1)] += err
                        }
                        if (y + 2 < height) {
                            gray[(y + 2) * width + x] += err
                        }
                    }
                }
            }

            else -> {
                // Floyd-Steinberg error diffusion with serpentine scanning
                for (y in 0 until height) {
                    val rowOffset = y * bytesPerRow
                    val isEvenRow = (y and 1) == 0

                    if (isEvenRow) {
                        // Left-to-right
                        for (x in 0 until width) {
                            val idx = y * width + x
                            val oldVal = gray[idx]
                            val newVal = if (oldVal < 128f) 0f else 255f
                            val isBlack = newVal == 0f

                            if (isBlack) {
                                val byteIdx = rowOffset + (x shr 3)
                                val bitIdx = 7 - (x and 7)
                                outData[byteIdx] = (outData[byteIdx].toInt() or (1 shl bitIdx)).toByte()
                            }

                            val err = oldVal - newVal
                            if (x + 1 < width) gray[idx + 1] += err * (7f / 16f)
                            if (y + 1 < height) {
                                if (x - 1 >= 0) gray[(y + 1) * width + (x - 1)] += err * (3f / 16f)
                                gray[(y + 1) * width + x] += err * (5f / 16f)
                                if (x + 1 < width) gray[(y + 1) * width + (x + 1)] += err * (1f / 16f)
                            }
                        }
                    } else {
                        // Right-to-left
                        for (x in width - 1 downTo 0) {
                            val idx = y * width + x
                            val oldVal = gray[idx]
                            val newVal = if (oldVal < 128f) 0f else 255f
                            val isBlack = newVal == 0f

                            if (isBlack) {
                                val byteIdx = rowOffset + (x shr 3)
                                val bitIdx = 7 - (x and 7)
                                outData[byteIdx] = (outData[byteIdx].toInt() or (1 shl bitIdx)).toByte()
                            }

                            val err = oldVal - newVal
                            if (x - 1 >= 0) gray[idx - 1] += err * (7f / 16f)
                            if (y + 1 < height) {
                                if (x + 1 < width) gray[(y + 1) * width + (x + 1)] += err * (3f / 16f)
                                gray[(y + 1) * width + x] += err * (5f / 16f)
                                if (x - 1 >= 0) gray[(y + 1) * width + (x - 1)] += err * (1f / 16f)
                            }
                        }
                    }
                }
            }
        }

        return RasterPage(width, height, dpi, outData)
    }
}
