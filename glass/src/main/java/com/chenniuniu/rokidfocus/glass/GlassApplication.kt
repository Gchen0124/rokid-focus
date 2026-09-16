package com.chenniuniu.rokidfocus.glass

import android.app.Application
import com.chenniuniu.rokidfocus.glass.cxr.FocusBridge
import com.chenniuniu.rokidfocus.glass.data.GlassStore

class GlassApplication : Application() {
    lateinit var store: GlassStore
        private set
    lateinit var bridge: FocusBridge
        private set
    lateinit var runtime: GlassRuntime
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = GlassStore(this)
        bridge = FocusBridge(store)
        bridge.start()
        runtime = GlassRuntime(this)
        runtime.start()
    }

    companion object {
        lateinit var instance: GlassApplication
            private set
    }
}
