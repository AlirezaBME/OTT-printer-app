package com.example.document

import android.graphics.Bitmap

interface DocumentSource : AutoCloseable {
    val title: String
    val totalPages: Int

    suspend fun renderPage(pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap

    suspend fun renderForPrint(pageIndex: Int, settings: com.example.core.model.PrintSettings): Bitmap {
        val source = renderPage(pageIndex, 2048, 2048)
        return try { com.example.raster.PageRenderer.renderPageToCanvas(source, settings) }
        finally { source.recycle() }
    }

    fun duplicate(): DocumentSource

    override fun close() {}
}
