package com.example

import android.content.Intent
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.MainScreen
import com.example.ui.MainViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIncomingIntent(intent)

        setContent {
            MyApplicationTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action

        when (action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                viewModel.refreshUsbDevices()
            }

            Intent.ACTION_SEND -> {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                val type = intent.type ?: ""
                if (uri != null) {
                    if (type.contains("pdf", ignoreCase = true)) {
                        viewModel.loadPdfUri(uri)
                    } else if (type.startsWith("image/")) {
                        viewModel.loadImageUri(uri)
                    }
                }
            }

            Intent.ACTION_VIEW -> {
                val data = intent.data
                val type = intent.type ?: ""
                if (data != null) {
                    if (type.contains("pdf", ignoreCase = true) || data.toString().endsWith(".pdf", ignoreCase = true)) {
                        viewModel.loadPdfUri(data)
                    } else {
                        viewModel.loadImageUri(data)
                    }
                }
            }
        }
    }
}
