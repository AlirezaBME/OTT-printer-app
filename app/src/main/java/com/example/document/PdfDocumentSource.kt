package com.example.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.core.model.PrintOrientation
import com.example.core.model.PrintScaling
import com.example.core.model.PrintSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Snapshot provider data once. Renderer access and close are serialized. */
class PdfDocumentSource(private val context: Context, val uri: Uri, override val title: String = "Document.pdf") : DocumentSource {
    private val lock = Any()
    private val snapshot = File.createTempFile("document_", ".pdf", context.cacheDir)
    private var renderer: PdfRenderer? = null
    override val totalPages: Int
    init {
        var descriptor: ParcelFileDescriptor? = null
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("Cannot read this document.")
            input.use { source -> snapshot.outputStream().use { output ->
                val buffer = ByteArray(65536)
                var count = 0L
                while (true) {
                    val size = source.read(buffer)
                    if (size < 0) break
                    count += size
                    require(count <= 64L * 1024 * 1024) { "PDF exceeds the 64 MB limit." }
                    output.write(buffer, 0, size)
                }
            } }
            descriptor = ParcelFileDescriptor.open(snapshot, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(descriptor)
            totalPages = renderer!!.pageCount
            require(totalPages in 1..10000) { "Unsupported PDF page count." }
        } catch (e: Exception) {
            renderer?.close()
            if (renderer == null) descriptor?.close()
            snapshot.delete()
            throw e
        }
    }
    override suspend fun renderPage(pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val pdf = renderer ?: error("Document is closed.")
            pdf.openPage(pageIndex).use { page ->
                val scale = minOf(targetWidth.toFloat() / page.width, targetHeight.toFloat() / page.height)
                val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                try { page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); bitmap }
                catch (e: Exception) { bitmap.recycle(); throw e }
            }
        }
    }
    override suspend fun renderForPrint(pageIndex: Int, settings: PrintSettings): Bitmap = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val pdf = renderer ?: error("Document is closed.")
            pdf.openPage(pageIndex).use { page ->
                val landscape = settings.orientation == PrintOrientation.LANDSCAPE ||
                    (settings.orientation == PrintOrientation.AUTO && page.width > page.height)
                val paperW = settings.paperSize.getPixelWidth(settings.quality.dpi)
                val paperH = settings.paperSize.getPixelHeight(settings.quality.dpi)
                val width = if (landscape) paperH else paperW
                val height = if (landscape) paperW else paperH
                val margin = (settings.marginMm * settings.quality.dpi / 25.4f).toInt()
                val availableW = width - 2 * margin
                val availableH = height - 2 * margin
                val scale = when (settings.scaling) {
                    PrintScaling.FIT_PAGE -> minOf(availableW.toFloat() / page.width, availableH.toFloat() / page.height)
                    PrintScaling.FILL_PAGE -> maxOf(availableW.toFloat() / page.width, availableH.toFloat() / page.height)
                    PrintScaling.ACTUAL_SIZE -> settings.quality.dpi / 72f
                }
                val matrix = Matrix().apply {
                    postScale(scale, scale)
                    postTranslate((width - page.width * scale) / 2f, (height - page.height * scale) / 2f)
                }
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                try {
                    page.render(bitmap, Rect(margin, margin, width - margin, height - margin), matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    bitmap
                } catch (e: Exception) { bitmap.recycle(); throw e }
            }
        }
    }
    override fun duplicate(): DocumentSource = synchronized(lock) {
        check(renderer != null) { "Document is closed" }
        PdfDocumentSource(context, Uri.fromFile(snapshot), title)
    }
    override fun close() = synchronized(lock) {
        renderer?.close()
        renderer = null
        snapshot.delete()
        Unit
    }
}
