package com.chenniuniu.rokidfocus

import android.app.Application
import com.chenniuniu.rokidfocus.data.FocusStore
import com.chenniuniu.rokidfocus.glasses.CxrHudController
import com.chenniuniu.rokidfocus.glasses.GlassesStatus
import com.chenniuniu.rokidfocus.listen.ListenProxy

class FocusApplication : Application() {

    lateinit var store: FocusStore
        private set

    lateinit var glasses: CxrHudController
        private set

    lateinit var listen: ListenProxy
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = FocusStore(this)
        listen = ListenProxy(this) { bind ->
            store.update { it.copy(listenBind = bind, statusLine = "Listen $bind") }
        }
        runCatching { listen.start() }
        glasses = CxrHudController(this) { status, message ->
            store.update {
                it.copy(
                    glasses = status,
                    statusLine = message.ifBlank { status.label },
                )
            }
            if (status == GlassesStatus.Ready || status == GlassesStatus.ViewOpen) {
                pushGlasses()
            }
        }
    }

    fun pushGlasses() {
        glasses.push(store.snapshot())
    }

    companion object {
        lateinit var instance: FocusApplication
            private set
    }
}
