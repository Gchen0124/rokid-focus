package com.chenniuniu.rokidfocus

import android.app.Application
import com.chenniuniu.rokidfocus.data.FocusStore
import com.chenniuniu.rokidfocus.glasses.CxrHudController
import com.chenniuniu.rokidfocus.glasses.GlassesStatus
import com.chenniuniu.rokidfocus.listen.ConvoMemory
import com.chenniuniu.rokidfocus.listen.ListenProxy

class FocusApplication : Application() {

    lateinit var store: FocusStore
        private set

    lateinit var glasses: CxrHudController
        private set

    lateinit var listen: ListenProxy
        private set

    lateinit var convo: ConvoMemory
        private set

    /** Set by the ViewModel so other components (e.g. a glasses ring) can speak. */
    var speak: ((String) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = FocusStore(this)
        convo = ConvoMemory()
        convo.replace(store.snapshot().convoTurns)
        listen = ListenProxy(this) { bind ->
            store.update { it.copy(listenBind = bind, statusLine = "Listen $bind") }
        }
        runCatching { listen.start() }
        glasses = CxrHudController(
            this,
            onStatus = { status, message ->
                store.update {
                    it.copy(
                        glasses = status,
                        statusLine = message.ifBlank { status.label },
                    )
                }
                if (status == GlassesStatus.Ready || status == GlassesStatus.ViewOpen) {
                    pushGlasses()
                }
            },
            onListen = { live ->
                store.update { it.copy(listenLive = live) }
            },
            onReactPick = { index ->
                val replies = store.snapshot().convoTurns.lastOrNull { it.replies.isNotEmpty() }?.replies.orEmpty()
                replies.getOrNull(index)?.let { speak?.invoke(it) }
            },
        )
    }

    fun pushGlasses() {
        glasses.push(store.snapshot())
    }

    companion object {
        lateinit var instance: FocusApplication
            private set
    }
}
