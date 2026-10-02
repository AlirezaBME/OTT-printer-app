package com.example.printing

import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.*
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.example.LbpOtgApplication
import com.example.core.model.*
import com.example.document.DocumentSource
import kotlinx.coroutines.*

/** Owns a separate document snapshot, so changing the app's selection cannot close it. */
class DocumentPrintAdapter(private val app: LbpOtgApplication, private val source: DocumentSource,
                           private val settings: PrintSettings) : PrintDocumentAdapter() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val indices = settings.parsePageIndices(source.totalPages).let { pages ->
        List(settings.copies) { pages }.flatten()
    }
    private var attributes: PrintAttributes? = null
    private var writer: Job? = null

    override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes,
                          cancellationSignal: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?) {
        if (cancellationSignal.isCanceled) { callback.onLayoutCancelled(); return }
        if (newAttributes.mediaSize?.id !in setOf(null, PrintAttributes.MediaSize.ISO_A4.id, PrintAttributes.MediaSize.ISO_A5.id, PrintAttributes.MediaSize.NA_LETTER.id)) {
            callback.onLayoutFailed("Choose A4, A5 or Letter paper.")
            return
        }
        if (indices.size > 10000) { callback.onLayoutFailed("Export at most 10,000 pages including copies."); return }
        attributes = newAttributes
        callback.onLayoutFinished(PrintDocumentInfo.Builder(source.title)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(indices.size).build(), oldAttributes != newAttributes)
    }

    override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor,
                         cancellationSignal: CancellationSignal, callback: WriteResultCallback) {
        writer?.cancel()
        writer = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val written = withContext(Dispatchers.IO) {
                    val media = attributes?.mediaSize ?: PrintAttributes.MediaSize.ISO_A4
                    val paper = when (media.id) {
                        PrintAttributes.MediaSize.ISO_A5.id -> PaperSize.A5
                        PrintAttributes.MediaSize.NA_LETTER.id -> PaperSize.LETTER
                        else -> PaperSize.A4
                    }
                    val layout = settings.copy(paperSize = paper, copies = 1, quality = PrintQuality.DRAFT_300DPI,
                        orientation = if (media.isPortrait) PrintOrientation.PORTRAIT else PrintOrientation.LANDSCAPE)
                    val widthPoints = (media.widthMils * 72f / 1000).toInt()
                    val heightPoints = (media.heightMils * 72f / 1000).toInt()
                    val selected = indices.indices.filter { index -> pages.any { index in it.start..it.end } }
                    ParcelFileDescriptor.AutoCloseOutputStream(destination).use { output ->
                        val pdf = StreamingPdfWriter(output, selected.size)
                        for (index in selected) {
                            ensureActive()
                            if (cancellationSignal.isCanceled) throw CancellationException()
                            val bitmap = source.renderForPrint(indices[index], layout)
                            try { pdf.writePage(bitmap, widthPoints, heightPoints) }
                            finally { bitmap.recycle() }
                        }
                        ensureActive()
                        pdf.finish()
                    }
                    selected.map { PageRange(it, it) }.toTypedArray()
                }
                if (cancellationSignal.isCanceled) callback.onWriteCancelled() else callback.onWriteFinished(written)
            } catch (e: CancellationException) { callback.onWriteCancelled() }
            catch (e: Exception) { callback.onWriteFailed(e.message ?: "Cannot render document") }
        }
        cancellationSignal.setOnCancelListener { writer?.cancel() }
    }
    override fun onFinish() {
        val task = writer
        scope.cancel()
        app.applicationScope.launch(Dispatchers.IO) { task?.join(); source.close() }
    }
}
