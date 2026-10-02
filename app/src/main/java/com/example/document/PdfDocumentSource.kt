package com.example.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfDocumentSource(
    private val context: Context,
    val uri: Uri,
    override val title: String = "Document.pdf"
) : DocumentSource {

    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var tempFile: File? = null

    init {
        try {
            // First try opening directly via ContentResolver
            fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            val pfd = fileDescriptor
            if (pfd != null) {
                pdfRenderer = PdfRenderer(pfd)
            }
        } catch (_: Exception) {
            // If openFileDescriptor is not seekable (e.g. from stream), copy to cache file
            try {
                val cache = File(context.cacheDir, "temp_print_${System.currentTimeMillis()}.pdf")
                tempFile = cache
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(cache).use { output ->
                        input.copyTo(output)
                    }
                }
                fileDescriptor = ParcelFileDescriptor.open(cache, ParcelFileDescriptor.MODE_READ_ONLY)
                pdfRenderer = PdfRenderer(fileDescriptor!!)
            } catch (e2: Exception) {
                close()
                throw e2
            }
        }
    }

    override val totalPages: Int
        get() = pdfRenderer?.pageCount ?: 0

    override suspend fun renderPage(pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap = withContext(Dispatchers.IO) {
        val renderer = pdfRenderer ?: throw IllegalStateException("PdfRenderer not initialized")
        if (pageIndex !in 0 until renderer.pageCount) {
            throw IllegalArgumentException("Page index $pageIndex out of range (0..${renderer.pageCount - 1})")
        }

        val page = renderer.openPage(pageIndex)
        try {
            val pageW = page.width.toFloat()
            val pageH = page.height.toFloat()

            // Calculate scaled dimensions to preserve aspect ratio
            val scale = (targetWidth / pageW).coerceAtMost(targetHeight / pageH)
            val renderW = (pageW * scale).toInt().coerceAtLeast(1)
            val renderH = (pageH * scale).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            bitmap
        } finally {
            page.close()
        }
    }

    override fun close() {
        try {
            pdfRenderer?.close()
        } catch (_: Exception) {}
        try {
            fileDescriptor?.close()
        } catch (_: Exception) {}
        try {
            tempFile?.delete()
        } catch (_: Exception) {}
        pdfRenderer = null
        fileDescriptor = null
        tempFile = null
    }
}
