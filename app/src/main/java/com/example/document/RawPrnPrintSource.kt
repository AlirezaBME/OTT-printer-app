package com.example.document

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class PrinterJobFingerprint(val size: Long, val sha256: String, val first64Hex: String, val last64Hex: String)
/** Owned private snapshot. Never rendered, encoded, normalized or prefixed. */
class RawPrnPrintSource private constructor(val name: String, private val file: File, val fingerprint: PrinterJobFingerprint) : AutoCloseable {
    suspend fun copyVerifiedTo(target: File): PrinterJobFingerprint {
        val actual=fingerprint(file)
        check(actual==fingerprint) { "Raw printer job changed since import. Import it again." }
        file.inputStream().use { input -> target.outputStream().use { output ->
            val buffer=ByteArray(16384)
            while(true) {
                currentCoroutineContext().ensureActive()
                val count=input.read(buffer);if(count<0) break
                output.write(buffer,0,count)
            }
        } }
        check(fingerprint(target)==fingerprint) { "Raw printer job snapshot is not byte-identical." }
        return actual
    }
    override fun close() { file.delete() }
    companion object {
        const val MAX_BYTES=64L*1024*1024
        suspend fun import(input: InputStream, directory: File, name: String): RawPrnPrintSource {
            directory.mkdirs()
            val file=File.createTempFile("raw_prn_", ".bin", directory)
            try {
                var count=0L
                file.outputStream().use { output ->
                    val buffer=ByteArray(16384)
                    while(true) {
                        currentCoroutineContext().ensureActive()
                        val size=input.read(buffer);if(size<0) break
                        count+=size;require(count<=MAX_BYTES) { "Raw printer job exceeds 64 MB." }
                        output.write(buffer,0,size)
                    }
                }
                require(count>0) { "Raw printer job is empty." }
                file.setReadOnly()
                return RawPrnPrintSource(name.take(200),file,fingerprint(file))
            } catch(e: Throwable) { file.delete();throw e }
        }
        fun fingerprint(file: File): PrinterJobFingerprint {
            val digest=MessageDigest.getInstance("SHA-256")
            var size=0L;var first=ByteArray(0);var last=ByteArray(0)
            file.inputStream().use { input ->
                val buffer=ByteArray(16384)
                while(true) {
                    val n=input.read(buffer);if(n<0) break
                    val bytes=buffer.copyOf(n);digest.update(bytes);size+=n
                    if(first.size<64) first=(first+bytes).take(64).toByteArray()
                    last=(last+bytes).takeLast(64).toByteArray()
                }
            }
            fun hex(b: ByteArray)=b.joinToString("") { "%02x".format(it.toInt() and 255) }
            return PrinterJobFingerprint(size,hex(digest.digest()),hex(first),hex(last))
        }
    }
}
