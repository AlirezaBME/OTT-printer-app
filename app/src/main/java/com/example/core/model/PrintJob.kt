package com.example.core.model

sealed class PrintJobState {
    data object Idle : PrintJobState()
    
    data class Preparing(val documentTitle: String) : PrintJobState()
    
    data class Rendering(
        val currentPage: Int,
        val totalPages: Int,
        val message: String = ""
    ) : PrintJobState()
    
    data class Encoding(
        val currentPage: Int,
        val totalPages: Int,
        val driverName: String
    ) : PrintJobState()
    
    data class WaitingForPrinter(
        val message: String
    ) : PrintJobState()
    
    data class Sending(
        val bytesSent: Long,
        val totalBytes: Long,
        val percent: Int
    ) : PrintJobState()
    
    data class DataSent(
        val bytesSent: Long,
        val totalPages: Int
    ) : PrintJobState()
    
    data class Finishing(
        val message: String = "Finalizing printer session…"
    ) : PrintJobState()
    
    data class Completed(
        val totalBytes: Long,
        val pagesPrinted: Int,
        val durationMs: Long
    ) : PrintJobState()
    
    data class Cancelled(
        val reason: String = "Cancelled by user"
    ) : PrintJobState()
    
    data class Failed(
        val reason: String,
        val technicalDetail: String = "",
        val canRetry: Boolean = true
    ) : PrintJobState()
}

data class PrintJob(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val totalPages: Int,
    val pageIndices: List<Int>,
    val settings: PrintSettings,
    val state: PrintJobState = PrintJobState.Idle,
    val createdAt: Long = System.currentTimeMillis()
)
