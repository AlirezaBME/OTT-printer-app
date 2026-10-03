package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun launcherLoadsPreviewLanguageToggleAndDiagnostics() {
        compose.waitUntil(20000) { compose.onAllNodesWithTag("preview_image").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("print_button").assertIsEnabled()
        compose.onNodeWithTag("usb_print_button").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("language_toggle_button").performClick()
        compose.onNodeWithTag("language_toggle_button").performClick()
        compose.onNodeWithTag("test_page_button").performScrollTo().performClick()
        compose.onNodeWithTag("preview_image").assertExists()
        compose.onNodeWithTag("appbar_diagnostics_button").performClick()
    }
    @Test fun offlinePclExportFinishesAndDismissesWithoutAPrinter() {
        compose.waitUntil(30000) { compose.onAllNodesWithTag("preview_image").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Standard Laser PCL Raster").performScrollTo().performClick()
        compose.onNodeWithText("Export PCL file").performClick()
        compose.onNodeWithTag("usb_print_button").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(180000) { compose.onAllNodesWithTag("dismiss_job_dialog_button").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("dismiss_job_dialog_button").performClick()
        compose.onNodeWithTag("dismiss_job_dialog_button").assertDoesNotExist()
        compose.onNodeWithTag("usb_print_button").assertIsEnabled()
    }

}
