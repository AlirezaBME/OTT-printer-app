package com.example.document

import android.graphics.Bitmap

interface DocumentSource : AutoCloseable {
    val title: String
    val totalPages: Int

    suspend fun renderPage(pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap

    override fun close() {}
}
