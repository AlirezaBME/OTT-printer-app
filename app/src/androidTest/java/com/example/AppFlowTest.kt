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
        compose.onNodeWithTag("android_print_button").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("print_button").performClick()
        compose.onNodeWithText("Connect a printer with a USB OTG cable.").assertExists()
        compose.onNodeWithTag("language_toggle_button").performClick()
        compose.onNodeWithTag("language_toggle_button").performClick()
        compose.onNodeWithTag("test_page_button").performScrollTo().performClick()
        compose.onNodeWithTag("preview_image").assertExists()
        compose.onNodeWithTag("appbar_diagnostics_button").performClick()
    }
    @Test fun offlinePclExportFinishesAndDismissesWithoutAPrinter() {
        compose.waitUntil(30000) { compose.onAllNodesWithTag("preview_image").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Automatic USB driver").performScrollTo().performClick()
        compose.onNodeWithText("Export PCL file").performClick()
        compose.onNodeWithTag("print_button").assertIsEnabled().performClick()
        compose.waitUntil(180000) { compose.onAllNodesWithTag("dismiss_job_dialog_button").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("dismiss_job_dialog_button").performClick()
        compose.onNodeWithTag("dismiss_job_dialog_button").assertDoesNotExist()
        compose.onNodeWithTag("android_print_button").assertIsEnabled()
    }

}
