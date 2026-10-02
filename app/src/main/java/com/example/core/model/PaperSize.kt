package com.example.core.model

enum class PaperSize(
    val displayName: String,
    val widthMm: Float,
    val heightMm: Float,
    val carpsCode: Byte,
    val pclCode: Int
) {
    A4("A4 (210 × 297 mm)", 210f, 297f, 0x01, 26),
    A5("A5 (148 × 210 mm)", 148f, 210f, 0x04, 25),
    LETTER("Letter (8.5 × 11 in)", 215.9f, 279.4f, 0x02, 2);

    fun getPixelWidth(dpi: Int): Int {
        return ((widthMm / 25.4f) * dpi).toInt()
    }

    fun getPixelHeight(dpi: Int): Int {
        return ((heightMm / 25.4f) * dpi).toInt()
    }

    /**
     * Printable area with margins subtracted.
     * Canon LBP6030 has standard hardware margins ~5mm top/bottom/left/right.
     */
    fun getPrintablePixelWidth(dpi: Int, marginMm: Float = 5f): Int {
        val printableMm = (widthMm - (marginMm * 2f)).coerceAtLeast(10f)
        return ((printableMm / 25.4f) * dpi).toInt()
    }

    fun getPrintablePixelHeight(dpi: Int, marginMm: Float = 5f): Int {
        val printableMm = (heightMm - (marginMm * 2f)).coerceAtLeast(10f)
        return ((printableMm / 25.4f) * dpi).toInt()
    }
}
