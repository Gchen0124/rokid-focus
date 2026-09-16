package com.chenniuniu.rokidfocus.listen

import android.content.Context
import android.util.Log
import com.chenniuniu.rokidfocus.BuildConfig
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class ListenProxy(private val context: Context, private val onBind: (String) -> Unit) {
    private var server: Server? = null
    private val pool = Executors.newCachedThreadPool()

    val port: Int = 8791
    var bindIp: String = "0.0.0.0"
        private set

    fun start() {
        if (server != null) return
        bindIp = localIpv4() ?: "0.0.0.0"
        val s = Server()
        server = s
        s.start()
        onBind("$bindIp:$port")
        Log.i(TAG, "listen proxy $bindIp:$port")
    }

    fun stop() {
        runCatching { server?.stop(500) }
        server = null
    }

    private inner class Server : WebSocketServer(InetSocketAddress(port)) {
        private val sessions = ConcurrentHashMap<WebSocket, DoubaoAsr>()

        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
            val key = BuildConfig.DOUBAO_API_KEY
            if (key.isBlank()) {
                conn.send(JSONObject().put("type", "error").put("error", "no doubao key").toString())
                return
            }
            val asr = DoubaoAsr(
                apiKey = key,
                onText = { text, definite ->
                    val o = JSONObject().put("type", "asr").put("text", text).put("definite", definite)
                    if (definite) {
                        pool.execute {
                            val drafts = Drafts.fromDeepseek(text, BuildConfig.DEEPSEEK_API_KEY)
                            o.put("drafts", JSONArray(drafts))
                            runCatching { conn.send(o.toString()) }
                        }
                    } else {
                        runCatching { conn.send(o.toString()) }
                    }
                },
                onFail = { err -> runCatching { conn.send(JSONObject().put("type", "error").put("error", err).toString()) } },
            )
            sessions[conn] = asr
            asr.connect()
            conn.send(JSONObject().put("type", "ready").put("via", "phone").toString())
        }

        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
            sessions.remove(conn)?.close()
        }

        override fun onMessage(conn: WebSocket, message: String) {
            if (message.contains("stop")) sessions[conn]?.close()
        }

        override fun onMessage(conn: WebSocket, message: ByteBuffer) {
            val n = message.remaining()
            val pcm = ByteArray(n)
            message.get(pcm)
            sessions[conn]?.sendPcm(pcm, last = false)
        }

        override fun onError(conn: WebSocket?, ex: Exception) {
            Log.w(TAG, "ws ${ex.message}")
        }

        override fun onStart() {}
    }

    companion object {
        private const val TAG = "ListenProxy"

        fun localIpv4(): String? {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (ni in ifaces) {
                if (!ni.isUp || ni.isLoopback) continue
                val addrs = ni.inetAddresses
                while (addrs.hasMoreElements()) {
                    val a = addrs.nextElement()
                    if (a is Inet4Address && !a.isLoopbackAddress) return a.hostAddress
                }
            }
            return null
        }
    }
}
