package com.example.document

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.example.usb.UsbDeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TestPageDocumentSource(
    private val deviceInfo: UsbDeviceInfo? = null
) : DocumentSource {

    override val title: String = "Canon_LBP_TestPage.pdf"
    override val totalPages: Int = 1

    override suspend fun renderPage(pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap = withContext(Dispatchers.Default) {
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 28f
        }

        val scale = targetWidth / 2480f // Baseline scale relative to A4 300DPI
        val margin = 80f * scale

        // 1. Alignment Border & Corner Crosshairs
        val pageRect = RectF(margin, margin, targetWidth - margin, targetHeight - margin)
        paint.strokeWidth = 3f * scale
        canvas.drawRect(pageRect, paint)

        // Corner Crosshairs
        fun drawCrosshair(cx: Float, cy: Float) {
            val r = 30f * scale
            canvas.drawLine(cx - r, cy, cx + r, cy, paint)
            canvas.drawLine(cx, cy - r, cx, cy + r, paint)
            canvas.drawCircle(cx, cy, r * 0.6f, paint)
        }
        drawCrosshair(margin, margin)
        drawCrosshair(targetWidth - margin, margin)
        drawCrosshair(margin, targetHeight - margin)
        drawCrosshair(targetWidth - margin, targetHeight - margin)

        // 2. Title Header
        textPaint.isFakeBoldText = true
        textPaint.textSize = 48f * scale
        val title = "Canon LBP6030 / LBP6040 / LBP6018L"
        val subtitle = "USB OTG DIRECT PRINT ENGINE — DIAGNOSTIC TEST PAGE"
        var y = margin + (80f * scale)
        canvas.drawText(title, margin + (40f * scale), y, textPaint)

        y += 45f * scale
        textPaint.textSize = 24f * scale
        textPaint.isFakeBoldText = false
        canvas.drawText(subtitle, margin + (40f * scale), y, textPaint)

        // Divider
        y += 30f * scale
        canvas.drawLine(margin + (40f * scale), y, targetWidth - margin - (40f * scale), y, paint)

        // 3. Device Identification Details
        y += 50f * scale
        textPaint.textSize = 22f * scale
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val timestamp = sdf.format(Date())

        val dev = deviceInfo
        val vid = dev?.vendorIdHex ?: "0x04A9"
        val pid = dev?.productIdHex ?: "0x2795"
        val mfg = dev?.manufacturerName ?: dev?.ieee1284?.manufacturer ?: "Canon,Inc."
        val prod = dev?.productName ?: dev?.ieee1284?.model ?: "LBP6030/6030B/6018L"
        val cmd = dev?.ieee1284?.commandSet?.joinToString(", ") ?: "CARPS2, UFRII LT"

        canvas.drawText("• Hardware Identification: $mfg $prod", margin + (50f * scale), y, textPaint)
        y += 35f * scale
        canvas.drawText("• USB Vendor ID: $vid | Product ID: $pid | Serial: ${dev?.serialNumber ?: "N/A"}", margin + (50f * scale), y, textPaint)
        y += 35f * scale
        canvas.drawText("• IEEE-1284 Command Set: $cmd", margin + (50f * scale), y, textPaint)
        y += 35f * scale
        canvas.drawText("• Render Resolution: ${targetWidth}x$targetHeight px | Generated: $timestamp", margin + (50f * scale), y, textPaint)

        // Divider
        y += 40f * scale
        canvas.drawLine(margin + (40f * scale), y, targetWidth - margin - (40f * scale), y, paint)

        // 4. Font Typography Scaling Verification
        y += 50f * scale
        textPaint.textSize = 28f * scale
        textPaint.isFakeBoldText = true
        canvas.drawText("Font Sharpness & Legibility Test:", margin + (50f * scale), y, textPaint)

        val fontSizes = listOf(14f, 18f, 24f, 32f, 42f)
        for (fs in fontSizes) {
            y += (fs * 1.5f * scale)
            textPaint.textSize = fs * scale
            textPaint.isFakeBoldText = false
            canvas.drawText(
                "${fs.toInt()}pt: The quick brown fox jumps over the lazy dog (0123456789 - آزمون چاپ)",
                margin + (50f * scale),
                y,
                textPaint
            )
        }

        // Divider
        y += 40f * scale
        canvas.drawLine(margin + (40f * scale), y, targetWidth - margin - (40f * scale), y, paint)

        // 5. Grayscale & Dithering Ramp
        y += 50f * scale
        textPaint.textSize = 28f * scale
        textPaint.isFakeBoldText = true
        canvas.drawText("Halftone & Dither Density Ramp (0% to 100%):", margin + (50f * scale), y, textPaint)

        y += 30f * scale
        val rampW = (targetWidth - (margin * 2) - (100f * scale))
        val rampH = 60f * scale
        val rampX = margin + (50f * scale)
        val rampPaint = Paint()

        val steps = 10
        val stepW = rampW / steps
        for (s in 0 until steps) {
            val grayVal = (255 - (s * 255 / (steps - 1))).toInt()
            rampPaint.color = Color.rgb(grayVal, grayVal, grayVal)
            canvas.drawRect(rampX + (s * stepW), y, rampX + ((s + 1) * stepW), y + rampH, rampPaint)
            // Label
            textPaint.textSize = 18f * scale
            textPaint.isFakeBoldText = false
            canvas.drawText("${s * 10}%", rampX + (s * stepW) + 5f, y + rampH + (25f * scale), textPaint)
        }
        paint.strokeWidth = 1.5f * scale
        canvas.drawRect(rampX, y, rampX + rampW, y + rampH, paint)

        // 6. Hairline Resolution & Geometry Test
        y += rampH + (70f * scale)
        textPaint.textSize = 28f * scale
        textPaint.isFakeBoldText = true
        canvas.drawText("Micro-Hairline & Grid Precision Test:", margin + (50f * scale), y, textPaint)

        y += 30f * scale
        paint.strokeWidth = 1f
        val gridW = 200f * scale
        val gridH = 120f * scale
        val gridX = margin + (50f * scale)
        // Vertical hairlines
        for (gx in 0..10) {
            val xpos = gridX + (gx * (gridW / 10f))
            canvas.drawLine(xpos, y, xpos, y + gridH, paint)
        }
        // Horizontal hairlines
        for (gy in 0..6) {
            val ypos = y + (gy * (gridH / 6f))
            canvas.drawLine(gridX, ypos, gridX + gridW, ypos, paint)
        }

        // Concentric resolution circles
        val circleCx = gridX + gridW + (150f * scale)
        val circleCy = y + (gridH / 2f)
        for (r in 1..5) {
            canvas.drawCircle(circleCx, circleCy, r * 15f * scale, paint)
        }

        // Footer note
        val footerY = targetHeight - margin - (25f * scale)
        textPaint.textSize = 18f * scale
        textPaint.isFakeBoldText = false
        canvas.drawText(
            "LBP OTG Print • Offline Direct Hardware Driver Engine • No Cloud Required",
            margin + (50f * scale),
            footerY,
            textPaint
        )

        bitmap
    }

    override fun close() {}
}
