package com.example.usb

data class Ieee1284DeviceId(
    val rawString: String,
    val manufacturer: String = "",
    val model: String = "",
    val commandSet: List<String> = emptyList(),
    val deviceClass: String = "",
    val description: String = "",
    val rawKeyValues: Map<String, String> = emptyMap()
) {
    val supportsPcl5: Boolean
        get() = commandSet.any { it.uppercase(java.util.Locale.ROOT) in setOf("PCL", "PCL5", "PCL5E", "PCL5C", "PCL 5", "PCL 5E", "PCL 5C") }

    val supportsCarps2: Boolean
        get() = commandSet.any { it.contains("CARPS", ignoreCase = true) }
        
    val supportsUfrii: Boolean
        get() = commandSet.any { it.contains("UFR", ignoreCase = true) }
}

object Ieee1284Parser {
    /**
     * Parses an IEEE 1284 Device ID raw response.
     * The first 2 bytes from USB control transfer (0xA1, Request 0) are a big-endian length.
     */
    fun parse(rawBytes: ByteArray): Ieee1284DeviceId {
        if (rawBytes.size < 2) {
            return Ieee1284DeviceId(rawString = "")
        }
        val length = ((rawBytes[0].toInt() and 0xFF) shl 8) or (rawBytes[1].toInt() and 0xFF)
        // A truncated command set must never be mistaken for PCL (for example PCLXL cut after PCL).
        if (length !in 2..rawBytes.size) return Ieee1284DeviceId(rawString = "")
        val stringBytes = rawBytes.copyOfRange(2, length)
        val rawStr = String(stringBytes, Charsets.US_ASCII).trim()
        return parseString(rawStr)
    }

    fun parseString(rawString: String): Ieee1284DeviceId {
        val map = mutableMapOf<String, String>()
        val pairs = rawString.split(";")
        for (pair in pairs) {
            val trimmed = pair.trim()
            if (trimmed.isEmpty()) continue
            val colonIdx = trimmed.indexOf(':')
            if (colonIdx > 0) {
                val key = trimmed.substring(0, colonIdx).trim().uppercase(java.util.Locale.ROOT)
                val value = trimmed.substring(colonIdx + 1).trim()
                map[key] = value
            }
        }

        val mfg = map["MFG"] ?: map["MANUFACTURER"] ?: ""
        val mdl = map["MDL"] ?: map["MODEL"] ?: ""
        val cmdStr = map["CMD"] ?: map["COMMAND SET"] ?: ""
        val cls = map["CLS"] ?: map["CLASS"] ?: ""
        val des = map["DES"] ?: map["DESCRIPTION"] ?: ""

        val cmdList = cmdStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        return Ieee1284DeviceId(
            rawString = rawString,
            manufacturer = mfg,
            model = mdl,
            commandSet = cmdList,
            deviceClass = cls,
            description = des,
            rawKeyValues = map
        )
    }
}
