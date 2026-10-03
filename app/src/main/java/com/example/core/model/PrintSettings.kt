package com.example.core.model

data class PrintSettings(
    val paperSize: PaperSize = PaperSize.A4,
    val orientation: PrintOrientation = PrintOrientation.AUTO,
    val scaling: PrintScaling = PrintScaling.FIT_PAGE,
    val quality: PrintQuality = PrintQuality.DRAFT_300DPI,
    val contentMode: PrintContentMode = PrintContentMode.DOCUMENT_TEXT,
    val ditherAlgorithm: DitherAlgorithm = DitherAlgorithm.FLOYD_STEINBERG,
    val driverType: DriverType = DriverType.AUTO,
    val copies: Int = 1,
    val marginMm: Float = 5.0f,
    val pageRangeText: String = "ALL", // "ALL", "1", "1-3,5"
    val contrastBoost: Float = 1.15f,
    val brightnessOffset: Float = 0.0f
) {
    fun validate() {
        require(copies in 1..99) { "Copies must be between 1 and 99." }
        require(marginMm.isFinite() && marginMm in 0f..30f) { "Margins must be between 0 and 30 mm." }
        require(contrastBoost.isFinite() && contrastBoost in 0.1f..3f) { "Invalid contrast." }
        require(brightnessOffset.isFinite() && brightnessOffset in -1f..1f) { "Invalid brightness." }
    }

    fun parsePageIndices(totalAvailablePages: Int): List<Int> {
        require(totalAvailablePages in 1..10000) { "Document must contain 1 to 10,000 pages." }
        val normalized = pageRangeText.map { char ->
            when {
                char in '۰'..'۹' -> '0' + (char - '۰')
                char in '٠'..'٩' -> '0' + (char - '٠')
                char == '،' -> ','
                else -> char
            }
        }.joinToString("").trim()
        if (normalized.isEmpty() || normalized.equals("ALL", ignoreCase = true)) {
            return (0 until totalAvailablePages).toList()
        }
        require(normalized.length <= 2000) { "Page range is too long." }
        val result = sortedSetOf<Int>()
        for (part in normalized.split(',')) {
            val bounds = part.trim().split('-').map { it.trim().toIntOrNull() }
            require(bounds.size in 1..2 && bounds.all { it != null }) { "Use page ranges such as 1-3,5." }
            val start = bounds.first()!!
            val end = bounds.last()!!
            require(start in 1..totalAvailablePages && end in start..totalAvailablePages) {
                "Page range must be between 1 and $totalAvailablePages, in ascending order."
            }
            for (page in start..end) result.add(page - 1)
        }
        require(result.isNotEmpty()) { "Select at least one page." }
        return result.toList()
    }
}
