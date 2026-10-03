package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.diagnostics.ProtocolCapture
import com.example.document.RawPrnPrintSource
import com.example.protocol.*
import com.example.usb.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class CanonIsolationTest {
    private fun dir()=File(ApplicationProvider.getApplicationContext<LbpOtgApplication>().cacheDir,"isolation-${java.util.UUID.randomUUID()}").apply { mkdirs() }
    @Test fun rawJobRemainsIdenticalIncludingZeroesCrLfAndNonCpcaPrefix()=runBlocking {
        val directory=dir();val bytes=ByteArray(8193) { (it*41).toByte() }
        bytes[0]=0x1b;bytes[1]=0x0d;bytes[2]=0x0a
        val raw=RawPrnPrintSource.import(ByteArrayInputStream(bytes),directory,"official.prn")
        val snapshot=File(directory,"snapshot.bin")
        assertEquals(raw.fingerprint,raw.copyVerifiedTo(snapshot))
        assertArrayEquals(bytes,snapshot.readBytes())
        val capture=ProtocolCapture(File(directory,"trace").apply { mkdirs() },true,"RAW_PRN",null)
        capture.source(snapshot,"RAW_PRN")
        val peer=CanonMlpPeer(packetSize=8192)
        val transport=CanonPrintJobTransport(capture.transport(peer,512),capture,512,0)
        transport.open();transport.send(snapshot,0,{}, { _,_-> error("Zero observation policy must not poll") })
        assertArrayEquals(bytes,peer.cpca.toByteArray())
        assertEquals(raw.fingerprint.sha256,capture.summary.getString("transmittedJobSha256"))
        assertFalse(capture.summary.getBoolean("physicalPrintConfirmed"))
        capture.close()
        val zip=File(directory,"session.zip");capture.export(zip)
        ZipFile(zip).use { z ->
            assertArrayEquals(bytes,z.getInputStream(z.getEntry("print-job.bin")).readBytes())
            assertNull(z.getEntry("encoder-output.bin"))
            val usb=z.getInputStream(z.getEntry("usb-out.bin")).readBytes()
            assertArrayEquals(peer.transfers.fold(ByteArray(0)) { all,part -> all+part },usb)
            val session=JSONObject(z.getInputStream(z.getEntry("session.json")).readBytes().toString(Charsets.UTF_8))
            assertEquals("RAW_PRN",session.getString("sourceType"))
            assertEquals("UNKNOWN",session.getString("printerAcceptance"))
            assertEquals("CONFIRMATION_TIMEOUT",session.getString("observationOutcome"))
        }
        raw.close();directory.deleteRecursively();Unit
    }
    @Test fun emptyRawFileIsRejectedWithoutLeakingSnapshot()=runBlocking {
        val directory=dir()
        assertTrue(runCatching { RawPrnPrintSource.import(ByteArrayInputStream(byteArrayOf()),directory,"empty.prn") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(directory.listFiles()!!.isEmpty());directory.deleteRecursively();Unit
    }
    @Test fun segmentationCasesPreserveBytesAndAvoidAlignedPayloadWrites()=runBlocking {
        for(size in listOf(511,512,513,8186,8192,8193)) {
            val peer=CanonMlpPeer(packetSize=8192);val session=CanonMlpSession(peer)
            val bytes=ByteArray(size) { (it*17).toByte() }
            session.open();session.transmit(ByteArrayInputStream(bytes),size.toLong()) {};session.finish()
            assertArrayEquals(bytes,peer.cpca.toByteArray())
            assertTrue("size=$size",peer.wire.filter { it[0]==1.toByte() }.all { (it.size-6)%512!=0 && it.size<=8192 })
        }
    }
    @Test fun parserDistinguishesCreditFromJobAcceptanceAndRejectsLengths() {
        val credit=CanonMlpProtocol.parse(byteArrayOf(1,16,0,6,1,0))
        assertTrue(credit.isFlowControl);assertEquals(1,credit.credit);assertEquals(1,credit.channel)
        for(bytes in listOf(byteArrayOf(1),byteArrayOf(1,32,0,6,1,0),byteArrayOf(1,16,0,7,1,0))) {
            assertTrue(runCatching { CanonMlpProtocol.parse(bytes) }.exceptionOrNull() is CanonProtocolException)
        }
        val maximum=CanonMlpSession.frame(3,ByteArray(65529));assertEquals(65535,maximum.size)
        assertEquals(65529,CanonMlpProtocol.parse(maximum).payload.size)
        assertTrue(runCatching { CanonMlpSession.frame(3,ByteArray(65530)) }.isFailure)
        assertEquals("CPCA",CanonMlpProtocol.serviceName(byteArrayOf(0x8a.toByte(),0,32)+"CPCA".toByteArray(),32))
        assertEquals(16,CanonMlpProtocol.channels.single { it.nativeRole=="PRINT_STREAM" }.printerSocket)
    }
    @Test fun passiveCpcaResponseCannotConfirmPrinting() {
        val directory=dir();val capture=ProtocolCapture(directory,false,"RAW_PRN",null)
        val observer=CanonCpcaSession(capture)
        val packet=com.example.driver.canon.CanonCpca.packet(0x1234,byteArrayOf(1,2,3))
        observer.observe(MlpFrame(2,32,1,0,packet.copyOfRange(0,8)))
        observer.observe(MlpFrame(2,32,1,0,packet.copyOfRange(8,packet.size)))
        assertEquals(1,observer.responseCount);assertFalse(capture.summary.getBoolean("physicalPrintConfirmed"))
        capture.close();directory.deleteRecursively();Unit
    }
    @Test fun observationKeepsSessionOpenUntilTimeoutAndDoesNotCloseChannels()=runBlocking {
        val peer=CanonMlpPeer();var ports=0;var clock=0L
        val transport=object: UsbTransport by peer {
            override suspend fun queryPortStatus(interfaceIndex: Int): Result<UsbPrinterPortStatus> { ports++;return Result.success(UsbPrinterPortStatus.fromByte(0x18)) }
            override suspend fun readBulk(buffer: ByteArray,timeoutMs: Int): Result<Int> {
                if(peer.replies.isEmpty()) { clock+=minOf(timeoutMs,10)*1_000_000L;return Result.success(0) }
                return peer.readBulk(buffer,timeoutMs)
            }
        }
        val session=CanonMlpSession(transport,nanoTime={ clock });session.open()
        session.observePrinter(40,0) { _,_-> }
        assertEquals(40_000_000L,clock)
        assertTrue(ports>0)
        assertEquals(0,peer.wire.count { it[0]==0.toByte() && it[6]==2.toByte() })
        session.finish()
        assertEquals(3,peer.wire.count { it[0]==0.toByte() && it[6]==2.toByte() })
    }
    @Test fun observationPortErrorIsExplicitAndNotCompletion()=runBlocking {
        val peer=CanonMlpPeer()
        val transport=object:UsbTransport by peer { override suspend fun queryPortStatus(interfaceIndex: Int)=Result.success(UsbPrinterPortStatus.fromByte(0x38)) }
        val session=CanonMlpSession(transport);session.open()
        val error=runCatching { session.observePrinter(100,0) { _,_-> } }.exceptionOrNull()
        assertTrue(error is CanonProtocolException);assertEquals("PRINTER_PORT_ERROR",(error as CanonProtocolException).code)
    }
    @Test fun failedUsbWriteCapturesOnlyKnownAcceptedPrefixAndMarksAmbiguity()=runBlocking {
        val directory=dir();val capture=ProtocolCapture(directory,true,"RAW_PRN",null)
        val peer=CanonMlpPeer()
        val transport=object: UsbTransport by peer {
            override suspend fun writeBulk(data: ByteArray,timeoutMs: Int,chunkSize: Int,onProgress: (Long,Long)->Unit): Result<Long> {
                onProgress(3,data.size.toLong())
                return Result.failure(java.io.IOException("ambiguous bulk failure"))
            }
        }
        val bytes=ByteArray(512) { it.toByte() }
        assertTrue(capture.transport(transport,512).writeBulk(bytes).isFailure)
        capture.close()
        assertArrayEquals(bytes.copyOf(3),File(directory,"usb-out.bin").readBytes())
        val events=JSONObject(File(directory,"session.json").readText()).getJSONArray("events")
        val result=events.getJSONObject(events.length()-1)
        assertTrue(result.getBoolean("failedTransferBytesMayBeAmbiguous"))
        assertEquals(3,result.getInt("knownAcceptedBytes"));directory.deleteRecursively();Unit
    }
    @Test fun rawSourceMutationIsRejectedBeforeSpooling()=runBlocking {
        val directory=dir()
        val raw=RawPrnPrintSource.import(byteArrayOf(1,2,3).inputStream(),directory,"official.prn")
        val snapshot=directory.listFiles()!!.single()
        assertTrue(snapshot.setWritable(true));snapshot.writeBytes(byteArrayOf(3,2,1))
        val spool=File(directory,"spool.prn")
        assertTrue(runCatching { raw.copyVerifiedTo(spool) }.exceptionOrNull() is IllegalStateException)
        assertFalse(spool.exists());raw.close();directory.deleteRecursively();Unit
    }

}
