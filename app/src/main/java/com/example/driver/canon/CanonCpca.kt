package com.example.driver.canon

import java.io.ByteArrayOutputStream

/** CPCA print-stream mode used by Canon's cnpkmodulencapr (no response requested).
 * Request IDs/sequence are zero and the stream's user ID is 0xffff0000. This is
 * different from the interactive CPCA status API; do not add acknowledgement waits.
 * Wire observations and reproducible oracle are documented in tools/canon.
 */
object CanonCpca {
    const val MAX_DATA = 65534 // 16-bit payload length also includes the channel byte.

    fun packet(command: Int, payload: ByteArray): ByteArray {
        require(command in 0..65535 && payload.size <= 65535)
        return ByteArrayOutputStream(payload.size + 20).apply {
            write(hex("cdca1000")); be16(command); be16(0); be16(payload.size)
            write(hex("ffff0000000000000000")); write(payload)
        }.toByteArray()
    }

    fun data(bytes: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        var offset = 0
        while (offset < bytes.size) {
            val count = minOf(MAX_DATA, bytes.size - offset)
            write(packet(0x1a, byteArrayOf(1) + bytes.copyOfRange(offset, offset + count)))
            offset += count
        }
    }.toByteArray()

    fun start(): ByteArray = ByteArrayOutputStream().apply {
        // JobStart2's attribute count, followed by length-delimited attributes.
        // Only the non-authenticated print defaults are needed. Metadata is local
        // and deliberately excludes the device serial number and document contents.
        val attributes = listOf(
            attribute(0xf0, hex("01")), attribute(0x130, hex("030000")),
            attribute(0x0d, hex("04")), attribute(4, string("OTG Print")),
            attribute(0x117, string("OTG Print")), attribute(6, string("Android")),
            attribute(0x0c, hex("32"))
        )
        write(packet(0x6b, ByteArrayOutputStream().apply {
            be16(attributes.size); attributes.forEach { write(it) }
        }.toByteArray()))
        // SetJob: no PIN/account credentials. Matches the official default setup.
        write(packet(0x12, hex("013103") + ByteArray(9) +
            (byteArrayOf(3, 32) + ByteArray(41)).let { it + it } + byteArrayOf(3, 32) + ByteArray(32)))
        write(packet(0x14, ByteArray(4))) // BinderStart
        listOf("08b302", "082b" + string("OTG Print").toHex(), "07d70001",
            "07d91700000000", "07d80f", "084a04", "07da00",
            "08a5fefefffe000000000000000003").forEach { write(packet(0x15, hex(it))) }
        write(packet(0x17, ByteArray(4))) // DocumentStart
        listOf("002e830000", "07d70001", "07e004", "086e010b0000",
            "003a0802580258", "07ed03fe0002000000000000000003").forEach {
            write(packet(0x18, hex(it)))
        }
        write(packet(0x1d, hex("00000000008f00830a50415045522d53415645034f4646"))) // Paper save OFF
    }.toByteArray()

    fun end(pages: Int): ByteArray {
        require(pages in 1..990000)
        val count = ByteArrayOutputStream().apply { be32(pages) }.toByteArray()
        return packet(0x19, ByteArray(0)) + packet(0x15, hex("0113") + count) +
            packet(0x16, ByteArray(0)) + packet(0x12, hex("0113") + count) +
            packet(0x12, hex("07d600000000")) + packet(0x13, byteArrayOf(0))
    }

    private fun attribute(id: Int, value: ByteArray) = ByteArrayOutputStream().apply {
        be16(id); be16(value.size); write(value)
    }.toByteArray()
    private fun string(value: String) = byteArrayOf(3, 0xf2.toByte(), value.length.toByte()) + value.toByteArray(Charsets.US_ASCII)
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it.toInt() and 255) }
    internal fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    internal fun ByteArrayOutputStream.be16(value: Int) { write(value ushr 8); write(value) }
    internal fun ByteArrayOutputStream.be32(value: Int) { be16(value ushr 16); be16(value) }
}
