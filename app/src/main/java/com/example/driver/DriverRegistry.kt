package com.example.driver

import com.example.core.model.DriverType

object DriverRegistry {
    private val carps2Encoder = Carps2Encoder()
    private val ufriiLtEncoder = UfriiLtEncoder()
    private val rawPclEncoder = RawPclEncoder()

    fun getEncoder(driverType: DriverType): PrinterLanguageEncoder {
        return when (driverType) {
            DriverType.CARPS2 -> carps2Encoder
            DriverType.UFRII_LT -> ufriiLtEncoder
            DriverType.RAW_PCL -> rawPclEncoder
            DriverType.FILE_STREAM_DUMP -> carps2Encoder
        }
    }
}
