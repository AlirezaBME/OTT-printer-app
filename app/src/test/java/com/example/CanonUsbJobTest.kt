package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.usb.*
import android.os.Parcelable
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.*
import com.example.document.DocumentSource
import com.example.jobs.PrintJobManager
import com.example.raster.PageRenderer
import com.example.usb.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CanonUsbJobTest {
    private class Source : DocumentSource {
        override val title = "Canon USB fixture"
        override val totalPages = 1
        var rendered = 0
        override suspend fun renderPage(pageIndex: Int, targetWidth: Int, targetHeight: Int) =
            Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        override suspend fun renderForPrint(pageIndex: Int, settings: PrintSettings): Bitmap {
            rendered++
            val small = renderPage(pageIndex,8,8)
            return try { PageRenderer.renderPageToCanvas(small,settings) } finally { small.recycle() }
        }
        override fun duplicate(): DocumentSource = Source()
    }
    private fun <T> construct(type: Class<T>, vararg args: Any): T {
        val ctor=type.declaredConstructors.single { it.parameterCount == args.size }
        ctor.isAccessible=true
        @Suppress("UNCHECKED_CAST") return ctor.newInstance(*args) as T
    }
    private suspend fun repository(scope: CoroutineScope, transport: UsbTransport): UsbDeviceRepository {
        val app=ApplicationProvider.getApplicationContext<LbpOtgApplication>()
        shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        app.usbRepository.awaitRefresh()
        val device=construct(UsbDevice::class.java,"/dev/bus/usb/001/001",0x04a9,0x2795,0,0,0,"Canon","LBP6030","1.0","test")
        val printer=construct(UsbInterface::class.java,0,0,"Printer",7,1,2)
        val out=construct(UsbEndpoint::class.java,1,2,64,0)
        val incoming=construct(UsbEndpoint::class.java,0x82,2,64,0)
        UsbInterface::class.java.getMethod("setEndpoints",Array<Parcelable>::class.java).invoke(printer,arrayOf<Parcelable>(out,incoming))
        val config=construct(UsbConfiguration::class.java,1,"Default",0x80,50)
        UsbConfiguration::class.java.getMethod("setInterfaces",Array<Parcelable>::class.java).invoke(config,arrayOf<Parcelable>(printer))
        UsbDevice::class.java.getMethod("setConfigurations",Array<Parcelable>::class.java).invoke(device,arrayOf<Parcelable>(config))
        shadowOf(app.getSystemService(Context.USB_SERVICE) as UsbManager).addOrUpdateUsbDevice(device,true)
        return UsbDeviceRepository(app,scope,transport).also { it.awaitRefresh() }
    }
    private fun settings()=PrintSettings(paperSize=PaperSize.A5,ditherAlgorithm=DitherAlgorithm.THRESHOLD,driverType=DriverType.AUTO)

    @Test fun autoCanonJobTransmitsExactSpoolAndCopiesThroughUsb() = runBlocking {
        val app=ApplicationProvider.getApplicationContext<LbpOtgApplication>()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val transport=CanonMlpPeer()
        val repository=repository(scope,transport)
        val source=Source();val done=CompletableDeferred<PrintJobState>()
        val manager=PrintJobManager(app,repository,scope)
        assertTrue(manager.startPrintJob(source,settings().copy(copies=2)) { done.complete(it) })
        val result=withTimeout(60000) { done.await() }
        assertTrue("$result",result is PrintJobState.Completed)
        assertEquals(2,source.rendered)
        assertEquals(2,(result as PrintJobState.Completed).pagesPrinted)
        val bytes=transport.cpca.toByteArray()
        assertTrue(bytes.size > 1000000)
        assertArrayEquals(byteArrayOf(0xcd.toByte(),0xca.toByte(),0x10,0),bytes.take(4).toByteArray())
        val file=manager.lastCapturedStreamFile.value!!
        assertEquals("prn",file.extension)
        assertArrayEquals(file.readBytes(),bytes)
        // Both footer impression counters report the two host-rendered copies.
        var offset=0;var counts=0
        while(offset<bytes.size) {
            val length=((bytes[offset+8].toInt() and 255) shl 8) or (bytes[offset+9].toInt() and 255)
            if(length==6 && bytes[offset+20]==1.toByte() && bytes[offset+21]==0x13.toByte()) {
                assertArrayEquals(byteArrayOf(0,0,0,2),bytes.copyOfRange(offset+22,offset+26));counts++
            }
            offset+=20+length
        }
        assertEquals(2,counts)
        assertFalse(transport.isConnected());assertFalse(repository.operationMutex.isLocked)
        file.delete();scope.cancel()
    }

    @Test fun missingCanonHandshakeFailsBeforeRenderingAndReleasesUsb() = runBlocking {
        val app=ApplicationProvider.getApplicationContext<LbpOtgApplication>()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val peer=CanonMlpPeer().apply { missingReplies=true }
        val repository=repository(scope,peer)
        val source=Source();val done=CompletableDeferred<PrintJobState>()
        val manager=PrintJobManager(app,repository,scope)
        manager.startPrintJob(source,settings()) { done.complete(it) }
        val result=withTimeout(20000) { done.await() }
        assertTrue(result is PrintJobState.Failed);assertEquals(0,source.rendered)
        assertEquals(1,peer.wire.size);assertEquals(0,peer.cpca.size())
        assertFalse(peer.isConnected());assertFalse(repository.operationMutex.isLocked)
        assertNull(manager.lastCapturedStreamFile.value);scope.cancel()
    }

    @Test fun paperOutStopsCanonBeforeRenderingOrTransmission() = runBlocking {
        val app=ApplicationProvider.getApplicationContext<LbpOtgApplication>()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val transport=FakeUsbTransport(simulatedPortStatus=UsbPrinterPortStatus.fromByte(0x38))
        val repository=repository(scope,transport)
        val source=Source();val done=CompletableDeferred<PrintJobState>()
        val manager=PrintJobManager(app,repository,scope)
        manager.startPrintJob(source,settings()) { done.complete(it) }
        val result=withTimeout(20000) { done.await() }
        assertTrue(result is PrintJobState.Failed);assertEquals(0,source.rendered)
        assertEquals(0,transport.getCapturedBytes().size)
        assertFalse(repository.operationMutex.isLocked);scope.cancel()
    }

    @Test fun cancellationDuringCanonSubmissionResetsPartialPacketAndReleasesUsb() = runBlocking {
        val app=ApplicationProvider.getApplicationContext<LbpOtgApplication>()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val fake=FakeUsbTransport();val entered=CompletableDeferred<Unit>();var resets=0
        val transport=object : UsbTransport by fake {
            override suspend fun writeBulk(data: ByteArray,timeoutMs: Int,chunkSize: Int,onProgress: (Long,Long)->Unit): Result<Long> {
                assertEquals(15000,timeoutMs)
                entered.complete(Unit);awaitCancellation()
            }
            override suspend fun softReset(interfaceIndex: Int): Result<Unit> { resets++;return fake.softReset(interfaceIndex) }
        }
        val repository=repository(scope,transport)
        val manager=PrintJobManager(app,repository,scope);val done=CompletableDeferred<PrintJobState>()
        manager.startPrintJob(Source(),settings()) { done.complete(it) }
        withTimeout(60000) { entered.await() }
        manager.cancelAndJoin()
        assertTrue(done.await() is PrintJobState.Cancelled)
        assertEquals(1,resets);assertFalse(manager.isBusy.value)
        assertFalse(fake.isConnected());assertFalse(repository.operationMutex.isLocked)
        assertNull(manager.lastCapturedStreamFile.value);scope.cancel()
    }
}
