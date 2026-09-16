package com.chenniuniu.rokidfocus

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.chenniuniu.rokidfocus.glasses.CxrHudController
import com.chenniuniu.rokidfocus.ui.FocusScreen
import com.chenniuniu.rokidfocus.ui.FocusTheme

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<FocusViewModel>()

    private val notifyPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            FocusTheme {
                FocusScreen(
                    viewModel = viewModel,
                    onConnectGlasses = { viewModel.connectGlasses(this) }
                )
            }
        }
    }

    @Deprecated("Used if companion auth falls back to onActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CxrHudController.REQUEST_AUTH) {
            viewModel.onAuthResult(resultCode, data)
        }
    }
}
