package com.example.core.model

enum class PrintOrientation(val displayName: String) {
    AUTO("Auto"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class PrintScaling(val displayName: String) {
    FIT_PAGE("Fit to Page"),
    FILL_PAGE("Fill Page"),
    ACTUAL_SIZE("Actual Size (100%)")
}

enum class PrintQuality(val displayName: String, val dpi: Int) {
    DRAFT_300DPI("Draft (300 DPI)", 300),
    NORMAL_600DPI("Standard (600 DPI)", 600),
    HIGH_600DPI_FINE("High Definition (600 DPI Fine)", 600)
}

enum class PrintContentMode(val displayName: String) {
    DOCUMENT_TEXT("Document / Text (Crisp)"),
    PHOTO("Photo (Smooth Grayscale)")
}

enum class DitherAlgorithm(val displayName: String) {
    FLOYD_STEINBERG("Floyd-Steinberg Error Diffusion"),
    ATKINSON("Atkinson Dither"),
    THRESHOLD("Direct Threshold")
}

enum class DriverType(val displayName: String, val id: String) {
    CARPS2("Canon CARPS2 (Native LBP6030)", "carps2"),
    UFRII_LT("Canon UFRII LT", "ufrii_lt"),
    RAW_PCL("Standard Laser PCL Raster", "raw_pcl"),
    FILE_STREAM_DUMP("File Stream Dump (Debug Only)", "debug_dump")
}
