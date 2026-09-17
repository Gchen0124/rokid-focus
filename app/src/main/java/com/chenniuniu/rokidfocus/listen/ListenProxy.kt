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

    @Volatile private var beaconOn = false

    fun start() {
        if (server != null) return
        bindIp = localIpv4() ?: "0.0.0.0"
        val s = Server()
        server = s
        s.start()
        onBind("$bindIp:$port")
        Log.i(TAG, "listen proxy $bindIp:$port")
        startBeacon()
    }

    fun stop() {
        beaconOn = false
        runCatching { server?.stop(500) }
        server = null
    }

    private fun startBeacon() {
        beaconOn = true
        Thread({
            val sock = java.net.DatagramSocket()
            sock.broadcast = true
            while (beaconOn) {
                val ip = localIpv4() ?: bindIp
                if (ip != "0.0.0.0") {
                    val payload = "rokid-listen $ip $port"
                    val data = payload.toByteArray()
                    runCatching {
                        sock.send(
                            java.net.DatagramPacket(
                                data,
                                data.size,
                                java.net.InetAddress.getByName("255.255.255.255"),
                                18791,
                            )
                        )
                    }
                }
                Thread.sleep(1500)
            }
            sock.close()
        }, "listen-beacon").start()
    }

    private inner class Server : WebSocketServer(InetSocketAddress(port)) {
        private val sessions = ConcurrentHashMap<WebSocket, XfyunAsr>()

        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
            if (BuildConfig.XFYUN_APP_ID.isBlank()) {
                conn.send(JSONObject().put("type", "error").put("error", "no xfyun").toString())
                return
            }
            val app0 = runCatching { context.applicationContext as com.chenniuniu.rokidfocus.FocusApplication }.getOrNull()
            val asr = XfyunAsr(
                featureIds = app0?.store?.voiceId().orEmpty(),
                onText = { text, definite, speaker ->
                    val who = if (speaker > 1) "them" else if (speaker == 1) "you" else "them"
                    val o = JSONObject().put("type", "asr").put("text", text).put("definite", definite).put("who", who)
                    if (definite) {
                        pool.execute {
                            val app = runCatching { context.applicationContext as com.chenniuniu.rokidfocus.FocusApplication }.getOrNull()
                            app?.convo?.add(who, text)
                            app?.store?.appendConvo(who, text)
                            val result = Drafts.fromConvo(
                                convo = app?.convo?.prompt().orEmpty(),
                                lastThem = text,
                                style = app?.store?.snapshot()?.talkStyle.orEmpty(),
                                apiKey = app?.store?.replyKey().orEmpty(),
                            )
                            app?.store?.setLlmLine(result.status)
                            if (result.replies.isNotEmpty()) {
                                app?.store?.setLastReplies(result.replies)
                                o.put("drafts", JSONArray(result.replies))
                            }
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
