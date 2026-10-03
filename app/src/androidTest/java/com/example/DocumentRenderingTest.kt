package com.example

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.*
import com.example.document.PdfDocumentSource
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File

@RunWith(AndroidJUnit4::class)
class DocumentRenderingTest {
    private fun fixture(): File {
        val app = ApplicationProvider.getApplicationContext<LbpOtgApplication>()
        val file = File.createTempFile("fixture_", ".pdf", app.cacheDir)
        val pdf = PdfDocument()
        try {
            for (index in 0..1) {
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(200, 100, index).create())
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawRect(0f, 0f, 200f, 100f, Paint().apply { color = Color.BLACK })
                pdf.finishPage(page)
            }
            file.outputStream().use { pdf.writeTo(it) }
        } finally { pdf.close() }
        return file
    }
    @Test fun pdfSnapshotSurvivesOriginalRemovalAndConcurrentPreviews() = runBlocking<Unit> {
        val app = ApplicationProvider.getApplicationContext<LbpOtgApplication>()
        val file = fixture()
        val source = PdfDocumentSource(app, Uri.fromFile(file))
        file.delete()
        assertEquals(2, source.totalPages)
        coroutineScope {
            (0..1).map { index -> async(Dispatchers.IO) {
                val bitmap = source.renderPage(index, 400, 400)
                assertEquals(400, bitmap.width)
                assertEquals(200, bitmap.height)
                bitmap.recycle()
            } }.awaitAll()
        }
        source.close()
        source.close()
    }
    @Test fun autoLandscapeAndPhysicalActualSizeAreRenderedCorrectly() = runBlocking<Unit> {
        val app = ApplicationProvider.getApplicationContext<LbpOtgApplication>()
        val file = fixture()
        val source = PdfDocumentSource(app, Uri.fromFile(file))
        try {
            val auto = source.renderForPrint(0, PrintSettings(paperSize = PaperSize.A5))
            assertTrue(auto.width > auto.height)
            assertEquals(Color.WHITE, auto.getPixel(0, 0))
            assertEquals(Color.BLACK, auto.getPixel(auto.width / 2, auto.height / 2))
            auto.recycle()
            val actual = source.renderForPrint(0, PrintSettings(paperSize = PaperSize.A5, orientation = PrintOrientation.PORTRAIT, scaling = PrintScaling.ACTUAL_SIZE))
            assertTrue(actual.height > actual.width)
            assertEquals(Color.WHITE, actual.getPixel(actual.width / 2, 10))
            assertEquals(Color.BLACK, actual.getPixel(actual.width / 2, actual.height / 2))
            actual.recycle()
        } finally { source.close(); file.delete() }
    }
    @Test fun duplicateRemainsOpenAfterUiSourceCloses() = runBlocking<Unit> {
        val app = ApplicationProvider.getApplicationContext<LbpOtgApplication>()
        val file = fixture()
        val source = PdfDocumentSource(app, Uri.fromFile(file))
        val duplicate = source.duplicate()
        source.close()
        val preview = duplicate.renderPage(0, 400, 400)
        assertEquals(400, preview.width)
        preview.recycle(); duplicate.close(); file.delete()
    }
    @Test fun malformedPdfDoesNotLeaveASnapshot() {
        val app = ApplicationProvider.getApplicationContext<LbpOtgApplication>()
        val file = File.createTempFile("invalid_", ".pdf", app.cacheDir).apply { writeText("not a PDF") }
        val before = app.cacheDir.listFiles()!!.filter { it.name.startsWith("document_") }.map { it.name }.toSet()
        try { PdfDocumentSource(app, Uri.fromFile(file)); fail("Accepted a malformed PDF") } catch (_: Exception) {}
        val after = app.cacheDir.listFiles()!!.filter { it.name.startsWith("document_") }.map { it.name }.toSet()
        assertEquals(before, after)
        file.delete()
    }
    @Test fun streamingPdfCanBeOpenedAndRendersMultiplePages() = runBlocking<Unit> {
        val app = ApplicationProvider.getApplicationContext<LbpOtgApplication>()
        val file = File.createTempFile("streamed_", ".pdf", app.cacheDir)
        val bitmap = android.graphics.Bitmap.createBitmap(32, 16, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        file.outputStream().use { output ->
            val writer = com.example.printing.StreamingPdfWriter(output, 2)
            writer.writePage(bitmap, 200, 100)
            bitmap.eraseColor(Color.WHITE)
            writer.writePage(bitmap, 200, 100)
            writer.finish()
        }
        bitmap.recycle()
        val source = PdfDocumentSource(app, Uri.fromFile(file))
        assertEquals(2, source.totalPages)
        val black = source.renderPage(0, 400, 400)
        val white = source.renderPage(1, 400, 400)
        assertEquals(Color.BLACK, black.getPixel(100, 100))
        assertEquals(Color.WHITE, white.getPixel(100, 100))
        black.recycle(); white.recycle(); source.close(); file.delete()
    }
    @Test fun pdfOutputLimitFailsBeforeWritingExcessData() {
        val output = java.io.ByteArrayOutputStream()
        try { com.example.printing.StreamingPdfWriter(output, 1, maxBytes = 10); fail("Ignored output limit") }
        catch (_: IllegalArgumentException) {}
        assertTrue(output.size() <= 10)
    }

}
