package com.example

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.*
import com.example.document.DocumentSource
import com.example.jobs.PrintJobManager
import com.example.raster.DitherEngine
import com.example.raster.PageRenderer
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrintLifecycleTest {
    @org.junit.Before fun grantCompatReceiverPermission() {
        val app = ApplicationProvider.getApplicationContext<LbpOtgApplication>()
        org.robolectric.Shadows.shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        runBlocking { app.usbRepository.awaitRefresh() }
    }
    private fun app(): LbpOtgApplication = ApplicationProvider.getApplicationContext()
    private open class Source : DocumentSource {
        override val title = "Fixture"
        override val totalPages = 2
        var rendered = 0
        override suspend fun renderPage(pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap {
            rendered++
            return Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        }
        override suspend fun renderForPrint(pageIndex: Int, settings: PrintSettings) = renderPage(pageIndex, 8, 4)
        override fun duplicate(): DocumentSource = Source()
    }
    @Test fun fileExportHonorsCopiesAndNeverClaimsUsbSuccess() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val source = Source()
        val manager = PrintJobManager(app(), app().usbRepository, scope)
        val done = CompletableDeferred<PrintJobState>()
        assertTrue(manager.startPrintJob(source, PrintSettings(copies = 3, driverType = DriverType.FILE_STREAM_DUMP)) { done.complete(it) })
        val result = withTimeout(20000) { done.await() }
        assertTrue("$result", result is PrintJobState.Completed)
        val completed = result as PrintJobState.Completed
        assertEquals(6, source.rendered)
        assertEquals(6, completed.pagesPrinted)
        val file = manager.lastCapturedStreamFile.value!!
        val bytes = file.readBytes()
        assertEquals(6, bytes.count { it == 12.toByte() })
        assertFalse(manager.isBusy.value)
        manager.dismissJob()
        assertNull(manager.currentJob.value)
        file.delete()
        scope.cancel()
    }
    @Test fun cancellationReleasesLockAndRejectsOverlappingJobs() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val entered = CompletableDeferred<Unit>()
        val source = object : Source() {
            override suspend fun renderForPrint(pageIndex: Int, settings: PrintSettings): Bitmap {
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        val manager = PrintJobManager(app(), app().usbRepository, scope)
        val done = CompletableDeferred<PrintJobState>()
        val settings = PrintSettings(driverType = DriverType.FILE_STREAM_DUMP)
        manager.startPrintJob(source, settings) { done.complete(it) }
        withTimeout(20000) { entered.await() }
        assertFalse(manager.startPrintJob(Source(), settings))
        withTimeout(20000) { manager.cancelAndJoin() }
        assertTrue(withTimeout(20000) { done.await() } is PrintJobState.Cancelled)
        assertFalse(manager.isBusy.value)
        assertFalse(app().usbRepository.operationMutex.isLocked)
        assertNull(manager.lastCapturedStreamFile.value)
        scope.cancel()
    }
    @Test fun missingPrinterFailsBeforeRendering() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = PrintJobManager(app(), app().usbRepository, scope)
        val source = Source()
        val done = CompletableDeferred<PrintJobState>()
        manager.startPrintJob(source, PrintSettings()) { done.complete(it) }
        assertTrue(withTimeout(20000) { done.await() } is PrintJobState.Failed)
        assertEquals(0, source.rendered)
        assertFalse(app().usbRepository.operationMutex.isLocked)
        scope.cancel()
    }
    @Test fun rowDitherTreatsTransparencyAsWhiteAndPreservesPadding() {
        val bitmap = Bitmap.createBitmap(9, 2, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        bitmap.setPixel(0, 0, Color.BLACK)
        bitmap.setPixel(8, 1, Color.BLACK)
        for (algorithm in DitherAlgorithm.entries) {
            val raster = DitherEngine.convertToRaster(bitmap, 300, algorithm, PrintContentMode.PHOTO)
            assertEquals(0x80.toByte(), raster.data[0])
            assertEquals(0x80.toByte(), raster.data[3])
            assertFalse(raster.getPixel(7, 1))
            assertEquals(0, raster.data[1].toInt() and 0x7f)
        }
        bitmap.recycle()
    }
    @Test fun fillClipsInsideThePaperMargins() {
        val source = Bitmap.createBitmap(200, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val output = PageRenderer.renderPageToCanvas(source, PrintSettings(paperSize = PaperSize.A5, scaling = PrintScaling.FILL_PAGE, orientation = PrintOrientation.PORTRAIT))
        assertEquals(Color.WHITE, output.getPixel(0, output.height / 2))
        assertEquals(Color.BLACK, output.getPixel(output.width / 2, output.height / 2))
        output.recycle(); source.recycle()
    }
}
