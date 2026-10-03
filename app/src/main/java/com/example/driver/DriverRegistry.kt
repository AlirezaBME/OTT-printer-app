package com.example.driver

import com.example.core.model.DriverType
import com.example.driver.canon.CanonNcapEncoder
import com.example.usb.UsbDeviceInfo

object DriverRegistry {
    fun supportsCanon(info: UsbDeviceInfo): Boolean = info.vendorId == 0x04a9 &&
        info.productId == 0x2795 && info.ieee1284?.let { id ->
            id.identifiesCanonUfriiLt && id.advertisesCpca &&
                id.commandSet.any { it.equals("LIPSLX", ignoreCase = true) }
        } == true

    fun resolve(info: UsbDeviceInfo, requested: DriverType): DriverType {
        if (requested == DriverType.FILE_STREAM_DUMP) return requested
        if (requested == DriverType.AUTO) {
            if (supportsCanon(info)) return DriverType.UFRII_LT
            if (info.ieee1284?.supportsPcl5 == true) return DriverType.RAW_PCL
        }
        if (requested == DriverType.UFRII_LT && supportsCanon(info)) return requested
        if (requested == DriverType.RAW_PCL && info.ieee1284?.supportsPcl5 == true) return requested
        error("No compatible USB driver. This version supports Canon 04A9:2795 with LIPSLX/CPCA and PCL 5 printers. Run the USB probe or use a compatible Android print service.")
    }

    fun getEncoder(driverType: DriverType): PrinterLanguageEncoder = when (driverType) {
        DriverType.RAW_PCL, DriverType.FILE_STREAM_DUMP -> RawPclEncoder()
        DriverType.UFRII_LT -> CanonNcapEncoder()
        DriverType.AUTO -> throw IllegalArgumentException("Automatic driver must be resolved against the connected printer first.")
        DriverType.CARPS2 -> throw IllegalArgumentException("Canon CARPS2 is not supported.")
    }
}
