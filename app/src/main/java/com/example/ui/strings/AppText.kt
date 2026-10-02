package com.example.ui.strings

object AppText {
    fun t(isFa: Boolean, fa: String, en: String): String = if (isFa) fa else en

    fun appName() = "LBP OTG Print"
    fun targetPrinter() = "Canon LBP6030/6040/6018L"
}
