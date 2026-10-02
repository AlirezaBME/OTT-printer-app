package com.example.driver

import com.example.core.model.DriverType

object DriverRegistry {
    fun getEncoder(driverType: DriverType): PrinterLanguageEncoder = when (driverType) {
        DriverType.RAW_PCL, DriverType.FILE_STREAM_DUMP -> RawPclEncoder()
        else -> throw IllegalArgumentException("Canon CARPS2/UFRII LT is not implemented. Use a compatible Android print service.")
    }
}
