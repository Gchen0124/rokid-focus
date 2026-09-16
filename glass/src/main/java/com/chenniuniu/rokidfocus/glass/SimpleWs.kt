package com.chenniuniu.rokidfocus.glass

import android.util.Log
import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.Base64
import kotlin.concurrent.thread

/** Minimal masked WebSocket client. No OkHttp. ws://host:port only. */
class SimpleWs(
    private val host: String,
    private val port: Int,
    private val path: String = "/",
    private val onText: (String) -> Unit,
    private val onOpen: () -> Unit,
    private val onFail: (String) -> Unit,
) {
    @Volatile private var socket: Socket? = null
    @Volatile var open: Boolean = false
        private set
    private var out: OutputStream? = null

    fun connect() {
        thread(name = "convo-ws") {
            try {
                val s = Socket()
                s.connect(InetSocketAddress(host, port), 5000)
                s.tcpNoDelay = true
                socket = s
                val key = Base64.getEncoder().encodeToString(ByteArray(16).also { SecureRandom().nextBytes(it) })
                val req = buildString {
                    append("GET $path HTTP/1.1\r\n")
                    append("Host: $host:$port\r\n")
                    append("Upgrade: websocket\r\n")
                    append("Connection: Upgrade\r\n")
                    append("Sec-WebSocket-Key: $key\r\n")
                    append("Sec-WebSocket-Version: 13\r\n\r\n")
                }
                s.getOutputStream().write(req.toByteArray())
                s.getOutputStream().flush()
                val ins = BufferedInputStream(s.getInputStream())
                val header = readHttpHeader(ins)
                if (!header.contains("101")) {
                    onFail("handshake $header")
                    s.close()
                    return@thread
                }
                out = s.getOutputStream()
                open = true
                onOpen()
                while (open) {
                    val frame = readFrame(ins) ?: break
                    if (frame.first == 1) onText(String(frame.second, Charsets.UTF_8))
                    if (frame.first == 8) break
                }
            } catch (e: Exception) {
                Log.w(TAG, "ws ${e.message}")
                onFail(e.message ?: "ws")
            } finally {
                open = false
                runCatching { socket?.close() }
            }
        }
    }

    fun sendBinary(data: ByteArray) {
        val o = out ?: return
        synchronized(this) { runCatching { o.write(frame(0x2, data)); o.flush() } }
    }

    fun sendText(text: String) {
        val o = out ?: return
        synchronized(this) { runCatching { o.write(frame(0x1, text.toByteArray())); o.flush() } }
    }

    fun close() {
        open = false
        runCatching { out?.write(frame(0x8, ByteArray(0))) }
        runCatching { socket?.close() }
    }

    private fun frame(opcode: Int, payload: ByteArray): ByteArray {
        val mask = ByteArray(4).also { SecureRandom().nextBytes(it) }
        val masked = ByteArray(payload.size) { i -> (payload[i].toInt() xor mask[i % 4].toInt()).toByte() }
        val header = ArrayList<Byte>(14)
        header.add((0x80 or opcode).toByte())
        val len = payload.size
        when {
            len < 126 -> header.add((0x80 or len).toByte())
            len <= 0xffff -> {
                header.add((0x80 or 126).toByte())
                header.add((len shr 8).toByte())
                header.add((len and 0xff).toByte())
            }
            else -> {
                header.add((0x80 or 127).toByte())
                repeat(4) { header.add(0) }
                header.add((len shr 24).toByte())
                header.add((len shr 16).toByte())
                header.add((len shr 8).toByte())
                header.add((len and 0xff).toByte())
            }
        }
        header.addAll(mask.toList())
        return (header.toByteArray() + masked)
    }

    private fun readHttpHeader(ins: BufferedInputStream): String {
        val buf = StringBuilder()
        while (true) {
            val line = buildString {
                while (true) {
                    val c = ins.read()
                    if (c < 0) return@buildString
                    if (c == '\n'.code) break
                    if (c != '\r'.code) append(c.toChar())
                }
            }
            if (line.isEmpty()) break
            buf.append(line).append('\n')
        }
        return buf.toString()
    }

    private fun readFrame(ins: BufferedInputStream): Pair<Int, ByteArray>? {
        val b0 = ins.read()
        if (b0 < 0) return null
        val opcode = b0 and 0x0f
        val b1 = ins.read()
        if (b1 < 0) return null
        var len = b1 and 0x7f
        if (len == 126) {
            val hi = ins.read(); val lo = ins.read()
            len = (hi shl 8) or lo
        } else if (len == 127) {
            repeat(4) { ins.read() }
            len = 0
            repeat(4) { len = (len shl 8) or ins.read() }
        }
        val payload = ByteArray(len)
        var off = 0
        while (off < len) {
            val n = ins.read(payload, off, len - off)
            if (n < 0) break
            off += n
        }
        if (opcode == 9) { // ping -> pong
            sendPong(payload)
            return readFrame(ins)
        }
        return opcode to payload
    }

    private fun sendPong(payload: ByteArray) {
        val o = out ?: return
        synchronized(this) { runCatching { o.write(frame(0xA, payload)); o.flush() } }
    }

    companion object {
        private const val TAG = "SimpleWs"
    }
}
