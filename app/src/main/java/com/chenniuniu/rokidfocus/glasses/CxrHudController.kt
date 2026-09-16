package com.chenniuniu.rokidfocus.glasses

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.chenniuniu.rokidfocus.data.FocusState
import com.rokid.cxr.link.CXRLink
import com.rokid.cxr.link.callbacks.ICXRLinkCbk
import com.rokid.cxr.link.callbacks.ICustomViewCbk
import com.rokid.cxr.link.utils.CxrDefs
import com.rokid.cxr.link.utils.GlassInfo
import com.rokid.sprite.aiapp.externalapp.auth.AuthResult
import com.rokid.sprite.aiapp.externalapp.auth.AuthorizationHelper
import com.rokid.sprite.aiapp.externalapp.auth.GlassPermission

/**
 * Optional CXR-L path. Timer and chimes do not depend on this.
 *
 * Jumpable: needs Rokid Maven artifact, companion app, and glasses.
 */
class CxrHudController(
    private val appContext: Context,
    private val onStatus: (GlassesStatus, String) -> Unit,
) {
    private var link: CXRLink? = null
    private var token: String = ""
    private var cxrOk = false
    private var btOk = false
    private var viewOpen = false
    private var lastJson: String = ""

    val requestCode: Int = REQUEST_AUTH

    fun isCompanionInstalled(): Boolean {
        val pm = appContext.packageManager
        return COMPANION_PACKAGES.any { pkg ->
            runCatching {
                pm.getPackageInfo(pkg, 0)
                true
            }.getOrDefault(false)
        }
    }

    fun requestAuth(activity: Activity) {
        if (!isCompanionInstalled()) {
            onStatus(GlassesStatus.MissingCompanion, "Install Rokid AI or Hi Rokid, then retry.")
            return
        }
        onStatus(GlassesStatus.Authorizing, "Waiting for companion authorization…")
        val cached = AuthorizationHelper.requestAuthorization(
            activity,
            arrayOf(GlassPermission.MEDIA),
            REQUEST_AUTH,
        )
        if (cached != null) {
            parseAuth(cached.first, cached.second)
        }
    }

    fun parseAuth(resultCode: Int, data: Intent?) {
        when (val result = AuthorizationHelper.parseAuthorizationResult(resultCode, data)) {
            is AuthResult.AuthSuccess -> {
                token = result.token
                if (token.isBlank()) {
                    onStatus(GlassesStatus.Error, "Auth returned an empty token.")
                } else {
                    onStatus(GlassesStatus.Connecting, "Token ok. Connecting…")
                    connect()
                }
            }
            is AuthResult.AuthFail -> onStatus(GlassesStatus.Error, "Authorization failed.")
            is AuthResult.AuthCancel -> onStatus(GlassesStatus.Idle, "Authorization cancelled.")
        }
    }

    fun connect() {
        if (token.isBlank()) {
            onStatus(GlassesStatus.Error, "No token. Tap Connect glasses first.")
            return
        }
        disconnect()
        cxrOk = false
        btOk = false
        viewOpen = false
        val created = CXRLink(appContext).apply {
            configCXRSession(CxrDefs.CXRSession(CxrDefs.CXRSessionType.CUSTOMVIEW))
            setCXRLinkCbk(linkCallback)
            setCXRCustomViewCbk(viewCallback)
        }
        link = created
        created.connect(token)
        onStatus(GlassesStatus.Connecting, "Waiting for CXR + Bluetooth…")
    }

    fun push(state: FocusState) {
        val current = link ?: return
        if (!cxrOk || !btOk) return
        if (!viewOpen) {
            val json = HudLayout.openJson(state)
            lastJson = json
            current.customViewOpen(json)
            return
        }
        val json = HudLayout.updateJson(state)
        if (json == lastJson) return
        lastJson = json
        current.customViewUpdate(json)
    }

    fun disconnect() {
        val current = link
        if (current != null) {
            runCatching {
                if (viewOpen || current.customViewIsOpen()) current.customViewClose()
            }
        }
        link = null
        viewOpen = false
        cxrOk = false
        btOk = false
    }

    private fun maybeReady() {
        if (cxrOk && btOk) {
            onStatus(
                if (viewOpen) GlassesStatus.ViewOpen else GlassesStatus.Ready,
                if (viewOpen) "HUD on glasses." else "Link ready."
            )
        }
    }

    private val linkCallback = object : ICXRLinkCbk {
        override fun onCXRLConnected(connected: Boolean) {
            Log.i(TAG, "onCXRLConnected $connected")
            cxrOk = connected
            if (!connected) {
                viewOpen = false
                onStatus(GlassesStatus.Connecting, "CXR dropped.")
            } else {
                maybeReady()
            }
        }

        override fun onGlassBtConnected(connected: Boolean) {
            Log.i(TAG, "onGlassBtConnected $connected")
            btOk = connected
            if (!connected) {
                viewOpen = false
                onStatus(GlassesStatus.Connecting, "Glasses Bluetooth dropped.")
            } else {
                maybeReady()
            }
        }

        override fun onGlassAiAssistStart() {}
        override fun onGlassAiAssistStop() {}
        override fun onGlassAiInterrupt(interruptWake: Boolean) {}
        override fun onGlassDeviceInfo(deviceInfo: GlassInfo) {}
        override fun onGlassWearingStatus(wearing: Boolean) {}
    }

    private val viewCallback = object : ICustomViewCbk {
        override fun onCustomViewOpened() {
            viewOpen = true
            onStatus(GlassesStatus.ViewOpen, "HUD on glasses.")
        }

        override fun onCustomViewUpdated() {}

        override fun onCustomViewClosed() {
            viewOpen = false
            onStatus(GlassesStatus.Ready, "HUD closed.")
        }

        override fun onCustomViewIconsSent() {}

        override fun onCustomViewError(code: Int, message: String?) {
            viewOpen = false
            onStatus(GlassesStatus.Error, "CustomView error $code ${message.orEmpty()}")
        }
    }

    companion object {
        private const val TAG = "CxrHud"
        const val REQUEST_AUTH = 1001
        private val COMPANION_PACKAGES = listOf(
            "com.rokid.sprite.aiapp",
            "com.rokid.sprite.global.aiapp",
        )
    }
}
