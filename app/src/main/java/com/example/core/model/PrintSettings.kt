package com.example.core.model

data class PrintSettings(
    val paperSize: PaperSize = PaperSize.A4,
    val orientation: PrintOrientation = PrintOrientation.AUTO,
    val scaling: PrintScaling = PrintScaling.FIT_PAGE,
    val quality: PrintQuality = PrintQuality.NORMAL_600DPI,
    val contentMode: PrintContentMode = PrintContentMode.DOCUMENT_TEXT,
    val ditherAlgorithm: DitherAlgorithm = DitherAlgorithm.FLOYD_STEINBERG,
    val driverType: DriverType = DriverType.CARPS2,
    val copies: Int = 1,
    val marginMm: Float = 5.0f,
    val pageRangeText: String = "ALL", // "ALL", "1", "1-3,5"
    val contrastBoost: Float = 1.15f,
    val brightnessOffset: Float = 0.0f
) {
    fun parsePageIndices(totalAvailablePages: Int): List<Int> {
        if (totalAvailablePages <= 0) return emptyList()
        val trimmed = pageRangeText.trim().uppercase()
        if (trimmed.isEmpty() || trimmed == "ALL") {
            return (0 until totalAvailablePages).toList()
        }
        val result = mutableSetOf<Int>()
        val parts = trimmed.split(",")
        for (part in parts) {
            val range = part.trim()
            if (range.contains("-")) {
                val bounds = range.split("-")
                val start = (bounds.getOrNull(0)?.toIntOrNull() ?: 1) - 1
                val end = (bounds.getOrNull(1)?.toIntOrNull() ?: totalAvailablePages) - 1
                for (i in start..end) {
                    if (i in 0 until totalAvailablePages) {
                        result.add(i)
                    }
                }
            } else {
                val page = range.toIntOrNull()
                if (page != null) {
                    val index = page - 1
                    if (index in 0 until totalAvailablePages) {
                        result.add(index)
                    }
                }
            }
        }
        return result.sorted()
    }
}
