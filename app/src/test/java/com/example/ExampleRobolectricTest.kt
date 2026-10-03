package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.example.diagnostics.DiagnosticReport
import com.example.usb.Ieee1284Parser
import com.example.usb.UsbDeviceInfo
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("LBP OTG Print", appName)
  }

  @Test
  fun `Canon diagnostics report CID and actual app version identify the included USB driver`() {
    val id = Ieee1284Parser.parseString("MFG:Canon;CMD:LIPSLX,CPCA;CID:CA_UFRIILT_OIP;MDL:LBP6030/6040/6018L;")
    val device = UsbDeviceInfo("canon", 0x04a9, 0x2795, "Canon", "LBP6030", null, null,
      0, 0, 0, 0, emptyList(), true, ieee1284 = id)
    val report = JSONObject(DiagnosticReport.generateJson(device, listOf(device)))
    assertEquals(BuildConfig.VERSION_NAME, report.getString("appVersion"))
    assertEquals(BuildConfig.VERSION_CODE, report.getInt("appVersionCode"))
    val ieee = report.getJSONObject("activeDevice").getJSONObject("ieee1284")
    assertEquals("CA_UFRIILT_OIP", ieee.getString("compatibilityId"))
    assertTrue(ieee.getBoolean("identifiesCanonUfriiLt"))
    assertTrue(ieee.getBoolean("advertisesCpca"))
    assertTrue(ieee.getBoolean("directUsbDriverAvailable"))
    val text = DiagnosticReport.generatePlainText(device, listOf(device))
    assertTrue(text.contains("CID: CA_UFRIILT_OIP"))
    assertTrue(text.contains("NCAP + CPCA over USB MLP flow control"))
  }
}
