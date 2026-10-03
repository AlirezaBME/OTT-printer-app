package com.example.usb

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UsbTraceEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val message: String,
    val isError: Boolean = false,
    val hexDump: String? = null
) {
    fun toFormattedString(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val timeStr = sdf.format(Date(timestamp))
        val prefix = if (isError) "[ERROR]" else "[INFO]"
        val main = "$timeStr $prefix [$tag] $message"
        return if (hexDump != null) "$main\n  HEX: $hexDump" else main
    }
}

object UsbTraceLogger {
    private const val MAX_LOGS = 5000
    private val deque = ConcurrentLinkedDeque<UsbTraceEvent>()
    
    private val _eventsFlow = MutableStateFlow<List<UsbTraceEvent>>(emptyList())
    val eventsFlow: StateFlow<List<UsbTraceEvent>> = _eventsFlow.asStateFlow()

    fun log(tag: String, message: String, isError: Boolean = false, hexDump: String? = null) {
        val event = UsbTraceEvent(
            tag = tag,
            message = message,
            isError = isError,
            hexDump = hexDump
        )
        deque.addLast(event)
        while (deque.size > MAX_LOGS) {
            deque.pollFirst()
        }
        _eventsFlow.value = deque.toList()
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        val fullMsg = if (throwable != null) "$message (${throwable.message})" else message
        log(tag, fullMsg, isError = true)
    }

    fun getLogs(): List<UsbTraceEvent> = deque.toList()

    fun clear() {
        deque.clear()
        _eventsFlow.value = emptyList()
    }

    fun exportPlainText(): String {
        return deque.joinToString("\n") { it.toFormattedString() }
    }

    fun bytesToHex(bytes: ByteArray, maxBytes: Int = 64): String {
        val count = bytes.size.coerceAtMost(maxBytes)
        val sb = StringBuilder()
        for (i in 0 until count) {
            sb.append(String.format("%02X ", bytes[i]))
        }
        if (bytes.size > maxBytes) {
            sb.append("... (+${bytes.size - maxBytes} bytes)")
        }
        return sb.toString().trim()
    }
}
