package com.example

import com.example.usb.CanonMlpSession
import com.example.usb.UsbTraceLogger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class CanonMlpSessionTest {
    private fun hex(text: String)=text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    @Test fun packetsMatchTheOfficialLibraryWireFixtures() {
        assertArrayEquals(hex("0000000801000008"),CanonMlpSession.INIT)
        for (id in 1..3) {
            val endpoints="0$id${(id*16).toString(16)}"
            assertArrayEquals(hex("0000000f010001${endpoints}ffffffffffff"),CanonMlpSession.frame(0,CanonMlpSession.openRequest(id)))
            assertArrayEquals(hex("00000009010002${endpoints}"),CanonMlpSession.frame(0,byteArrayOf(2,id.toByte(),(id*16).toByte())))
        }
        assertArrayEquals(hex("0110000e0100cdca100000000000"),CanonMlpSession.frame(1,hex("cdca100000000000")))
    }
    @Test fun negotiatesSizeHandlesFragmentedRepliesAndPreservesEveryCpcaByte()=runBlocking {
        val peer=CanonMlpPeer(packetSize=512).apply { readFragment=2 }
        val session=CanonMlpSession(peer)
        val payload=ByteArray(17001) { (it*31).toByte() }
        session.open()
        var acknowledged=0L
        session.transmit(ByteArrayInputStream(payload),payload.size.toLong()) { acknowledged=it }
        session.finish()
        assertEquals(payload.size.toLong(),acknowledged)
        assertArrayEquals(payload,peer.cpca.toByteArray())
        assertEquals(34,peer.wire.count { it[0]==1.toByte() })
        assertTrue(peer.replies.isEmpty())
        assertEquals(1 + 2*(peer.wire.size-1),peer.transfers.size)
        assertTrue(peer.transfers.drop(1).filterIndexed { i,_ -> i%2==0 }.all { it.size==6 })
        assertEquals(3,peer.wire.count { it[0]==0.toByte() && it[6]==2.toByte() })
    }

    @Test fun postJobObservationCapturesAsynchronousChannelPayloadBeforeClose()=runBlocking {
        UsbTraceLogger.clear()
        val peer=CanonMlpPeer()
        val session=CanonMlpSession(peer,postJobObservationMs=60)
        session.open()
        val payload=byteArrayOf(1,2,3,4)
        session.transmit(ByteArrayInputStream(payload),payload.size.toLong()) {}
        byteArrayOf(2,32,0,8,0,0,0x12,0x34).forEach { peer.replies.add(it) }
        session.finish()
        val logs=UsbTraceLogger.getLogs()
        assertTrue(logs.any { it.message.contains("post-job observation") && it.message.contains("channel=2/32") })
        assertTrue(logs.any { it.message.contains("Post-job Canon observation complete") && it.message.contains("payloadFrames=1") })
        UsbTraceLogger.clear()
    }

    @Test fun silentPrinterCannotReceiveRasterData()=runBlocking {
        val peer=CanonMlpPeer().apply { missingReplies=true }
        val result=runCatching { CanonMlpSession(peer).open() }
        assertTrue(result.exceptionOrNull() is IOException)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("initialization"))
        assertEquals(1,peer.wire.size);assertEquals(0,peer.cpca.size())
    }
    @Test fun rejectedChannelFailsBeforeAnyRasterData()=runBlocking {
        val peer=CanonMlpPeer().apply { rejectOpen=true }
        val result=runCatching { CanonMlpSession(peer).open() }
        assertTrue(result.exceptionOrNull()!!.message!!.contains("rejected channel"))
        assertEquals(0,peer.cpca.size())
    }
    @Test fun wrongSocketIsRejected()=runBlocking {
        val peer=CanonMlpPeer().apply { corruptChannel=true }
        val result=runCatching { CanonMlpSession(peer).open() }
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Malformed"))
    }
    @Test fun missingDataAcknowledgementStopsAfterOnePacket()=runBlocking {
        val peer=CanonMlpPeer(packetSize=512)
        val session=CanonMlpSession(peer);session.open();peer.missingReplies=true
        var progress=0L
        val result=runCatching { session.transmit(ByteArrayInputStream(ByteArray(10000)),10000) { progress=it } }
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals(506,peer.cpca.size());assertEquals(0,progress)
        assertEquals(1,peer.wire.count { it[0]==1.toByte() })
    }
    @Test fun rejectedCloseCannotReportSuccessfulSession()=runBlocking {
        val peer=CanonMlpPeer();val session=CanonMlpSession(peer);session.open();peer.rejectClose=true
        assertTrue(runCatching { session.finish() }.exceptionOrNull() is IOException)
    }
    @Test fun ambiguousWriteIsNotRetried()=runBlocking {
        val peer=CanonMlpPeer();val session=CanonMlpSession(peer);session.open();peer.failWrite=true
        assertTrue(runCatching { session.transmit(ByteArrayInputStream(ByteArray(100)),100) {} }.exceptionOrNull() is IOException)
        assertEquals(4,peer.wire.size);assertEquals(0,peer.cpca.size())
    }
}
