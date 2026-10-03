package com.example.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

class ImageDocumentSource(
    private val context: Context,
    inputUri: Uri,
    override val title: String = "Image.png"
) : DocumentSource {

    private val snapshot = java.io.File.createTempFile("image_", ".img", context.cacheDir)
    val uri: Uri = Uri.fromFile(snapshot)
    init {
        try {
            val input = context.contentResolver.openInputStream(inputUri) ?: error("Cannot read this image")
            input.use { source -> snapshot.outputStream().use { output ->
                val buffer = ByteArray(65536)
                var count = 0L
                while (true) {
                    val size = source.read(buffer)
                    if (size < 0) break
                    count += size
                    require(count <= 64L * 1024 * 1024) { "Image exceeds the 64 MB limit." }
                    output.write(buffer, 0, size)
                }
            } }
        } catch (e: Exception) { snapshot.delete(); throw e }
    }
    override val totalPages: Int = 1

    override suspend fun renderPage(pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap = withContext(Dispatchers.IO) {
        require(pageIndex == 0) { "Image has only one page" }
        // Step 1: Decode bounds only
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }

        val origW = boundsOptions.outWidth
        val origH = boundsOptions.outHeight
        if (origW <= 0 || origH <= 0) {
            throw IllegalArgumentException("Could not decode image bounds from $uri")
        }

        // Calculate sample size
        var sampleSize = 1
        while ((origW / sampleSize) > targetWidth.coerceAtMost(2048) || (origH / sampleSize) > targetHeight.coerceAtMost(2048)) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        var decoded: Bitmap? = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw IllegalStateException("Failed to decode image bitmap")

        // Handle EXIF orientation
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
                }
                if (!matrix.isIdentity) {
                    val rotated = Bitmap.createBitmap(
                        decoded!!, 0, 0, decoded.width, decoded.height, matrix, true
                    )
                    decoded.recycle()
                    decoded = rotated
                }
            }
        } catch (_: Exception) {}

        decoded!!
    }

    override fun duplicate(): DocumentSource = ImageDocumentSource(context, uri, title)
    override fun close() { snapshot.delete() }
}
