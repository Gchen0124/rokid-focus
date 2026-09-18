package com.chenniuniu.rokidfocus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.chenniuniu.rokidfocus.glasses.CxrHudController
import com.chenniuniu.rokidfocus.ui.FocusScreen
import com.chenniuniu.rokidfocus.ui.FocusTheme

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<FocusViewModel>()

    private val notifyPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // Granted or not, listen still runs on the glasses mic; the phone mic just joins when allowed.
    private val listenPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.toggleListen() }

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
                    onConnectGlasses = { viewModel.connectGlasses(this) },
                    onListen = { requestListen() },
                )
            }
        }
    }

    private fun requestListen() {
        val live = (application as FocusApplication).store.snapshot().listenLive
        if (live) {
            viewModel.toggleListen()
            return
        }
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.toggleListen() else listenPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    @Deprecated("Used if companion auth falls back to onActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CxrHudController.REQUEST_AUTH) {
            viewModel.onAuthResult(resultCode, data)
        }
    }
}
