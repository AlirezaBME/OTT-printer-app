package com.example.usb

data class UsbEndpointInfo(
    val endpointNumber: Int,
    val address: Int,
    val direction: String, // "OUT" or "IN"
    val type: String,      // "BULK", "CONTROL", "INTERRUPT", "ISOCHRONOUS"
    val maxPacketSize: Int,
    val interval: Int
) {
    val isBulkOut: Boolean get() = direction == "OUT" && type == "BULK"
    val isBulkIn: Boolean get() = direction == "IN" && type == "BULK"
}

data class UsbInterfaceInfo(
    val id: Int,
    val alternateSetting: Int,
    val interfaceClass: Int,
    val interfaceSubclass: Int,
    val interfaceProtocol: Int,
    val name: String?,
    val endpoints: List<UsbEndpointInfo>
) {
    val isPrinterClass: Boolean get() = interfaceClass == 7

    val bulkOutEndpoint: UsbEndpointInfo?
        get() = endpoints.firstOrNull { it.isBulkOut }

    val bulkInEndpoint: UsbEndpointInfo?
        get() = endpoints.firstOrNull { it.isBulkIn }
}

data class UsbPrinterPortStatus(
    val rawByte: Byte,
    val paperEmpty: Boolean,
    val selected: Boolean,
    val notError: Boolean
) {
    val isReady: Boolean get() = selected && notError && !paperEmpty

    fun toDisplayString(): String {
        val list = mutableListOf<String>()
        if (paperEmpty) list.add("PAPER EMPTY")
        if (!selected) list.add("OFFLINE")
        if (!notError) list.add("HARDWARE ERROR")
        if (list.isEmpty()) return "READY (Paper OK, Online)"
        return list.joinToString(", ")
    }

    companion object {
        fun fromByte(statusByte: Byte): UsbPrinterPortStatus {
            val unsigned = statusByte.toInt() and 0xFF
            val paperEmpty = (unsigned and 0x20) != 0 // Bit 5
            val selected = (unsigned and 0x10) != 0   // Bit 4
            val notError = (unsigned and 0x08) != 0   // Bit 3
            return UsbPrinterPortStatus(statusByte, paperEmpty, selected, notError)
        }
    }
}

data class UsbDeviceInfo(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val manufacturerName: String?,
    val productName: String?,
    val serialNumber: String?,
    val version: String?,
    val deviceClass: Int,
    val deviceSubclass: Int,
    val deviceProtocol: Int,
    val interfaceCount: Int,
    val interfaces: List<UsbInterfaceInfo>,
    val permissionGranted: Boolean,
    val interfaceClaimed: Boolean = false,
    val ieee1284: Ieee1284DeviceId? = null,
    val portStatus: UsbPrinterPortStatus? = null
) {
    val vendorIdHex: String get() = String.format("0x%04X", vendorId)
    val productIdHex: String get() = String.format("0x%04X", productId)

    val isCanonFamily: Boolean
        get() = vendorId == 0x04A9 || (manufacturerName?.contains("Canon", ignoreCase = true) == true)

    val isLbp6030Family: Boolean
        get() = (vendorId == 0x04A9 && productId == 0x2795) ||
                (productName?.contains("6030", ignoreCase = true) == true) ||
                (productName?.contains("6018", ignoreCase = true) == true) ||
                (productName?.contains("6040", ignoreCase = true) == true) ||
                (ieee1284?.model?.contains("6030", ignoreCase = true) == true)

    val primaryPrinterInterface: UsbInterfaceInfo?
        get() = interfaces.firstOrNull { it.isPrinterClass && it.bulkOutEndpoint != null }
            ?: interfaces.firstOrNull { it.bulkOutEndpoint != null }

    val primaryBulkOut: UsbEndpointInfo?
        get() = primaryPrinterInterface?.bulkOutEndpoint

    val primaryBulkIn: UsbEndpointInfo?
        get() = primaryPrinterInterface?.bulkInEndpoint
}
