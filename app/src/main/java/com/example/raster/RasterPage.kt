package com.example.raster

data class RasterPage(
    val width: Int,
    val height: Int,
    val dpi: Int,
    val data: ByteArray // 1 bit per pixel, MSB first, 1 = black, 0 = white
) {
    init {
        require(width in 1..20000 && height in 1..20000 && dpi > 0) { "Invalid raster dimensions" }
        require(data.size.toLong() == ((width.toLong() + 7) / 8) * height) { "Incorrect raster data length" }
    }
    val bytesPerRow: Int = (width + 7) / 8
    val totalBytes: Int = bytesPerRow * height

    fun getPixel(x: Int, y: Int): Boolean {
        if (x !in 0 until width || y !in 0 until height) return false
        val byteIndex = (y * bytesPerRow) + (x shr 3)
        val bitIndex = 7 - (x and 7)
        return ((data[byteIndex].toInt() shr bitIndex) and 1) != 0
    }

    fun setPixel(x: Int, y: Int, isBlack: Boolean) {
        if (x !in 0 until width || y !in 0 until height) return
        val byteIndex = (y * bytesPerRow) + (x shr 3)
        val bitIndex = 7 - (x and 7)
        if (isBlack) {
            data[byteIndex] = (data[byteIndex].toInt() or (1 shl bitIndex)).toByte()
        } else {
            data[byteIndex] = (data[byteIndex].toInt() and (1 shl bitIndex).inv()).toByte()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RasterPage
        if (width != other.width) return false
        if (height != other.height) return false
        if (dpi != other.dpi) return false
        if (!data.contentEquals(other.data)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + dpi
        result = 31 * result + data.contentHashCode()
        return result
    }
}
