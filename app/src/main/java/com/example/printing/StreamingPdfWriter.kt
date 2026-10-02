package com.example.printing

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Locale

/** Incremental PDF output: keep one page bitmap/JPEG in memory, not an entire PdfDocument. */
class StreamingPdfWriter(private val output: OutputStream, private val pageCount: Int,
                         private val maxBytes: Long = 256L * 1024 * 1024) {
    private var count = 0L
    private var pagesWritten = 0
    private val offsets = LongArray(3 + pageCount * 3)
    init {
        require(pageCount in 1..10000) { "Export at most 10,000 pages at a time." }
        ascii("%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n")
        objectStart(1)
        ascii("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
    }
    private fun bytes(data: ByteArray) {
        require(count + data.size <= maxBytes) { "PDF output exceeds the 256 MB limit. Select fewer pages or copies." }
        output.write(data)
        count += data.size
    }
    private fun ascii(text: String) = bytes(text.toByteArray(Charsets.ISO_8859_1))
    private fun objectStart(id: Int) { offsets[id] = count; ascii("$id 0 obj\n") }
    fun writePage(bitmap: Bitmap, widthPoints: Int, heightPoints: Int) {
        check(pagesWritten < pageCount)
        val page = 3 + pagesWritten * 3
        val image = page + 1
        val content = page + 2
        objectStart(page)
        ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $widthPoints $heightPoints] /Resources << /XObject << /Im0 $image 0 R >> >> /Contents $content 0 R >>\nendobj\n")
        val jpeg = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, jpeg)) { "Cannot encode PDF page" }
        objectStart(image)
        ascii("<< /Type /XObject /Subtype /Image /Width ${bitmap.width} /Height ${bitmap.height} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size()} >>\nstream\n")
        bytes(jpeg.toByteArray())
        ascii("\nendstream\nendobj\n")
        val commands = "q\n$widthPoints 0 0 $heightPoints 0 0 cm\n/Im0 Do\nQ\n"
        objectStart(content)
        ascii("<< /Length ${commands.length} >>\nstream\n${commands}endstream\nendobj\n")
        pagesWritten++
    }
    fun finish() {
        check(pagesWritten == pageCount) { "Incomplete PDF" }
        objectStart(2)
        ascii("<< /Type /Pages /Count $pageCount /Kids [")
        repeat(pageCount) { ascii("${3 + it * 3} 0 R ") }
        ascii("] >>\nendobj\n")
        val xref = count
        ascii("xref\n0 ${offsets.size}\n0000000000 65535 f \n")
        for (id in 1 until offsets.size) ascii(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[id]))
        ascii("trailer\n<< /Size ${offsets.size} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        output.flush()
    }
}
