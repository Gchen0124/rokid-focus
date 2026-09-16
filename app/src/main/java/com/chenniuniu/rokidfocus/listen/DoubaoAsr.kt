package com.chenniuniu.rokidfocus.listen

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class DoubaoAsr(
    private val apiKey: String,
    private val onText: (text: String, definite: Boolean) -> Unit,
    private val onFail: (String) -> Unit,
) {
    private val http = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private var ws: WebSocket? = null
    private var seq = 2
    private val pending = ArrayDeque<ByteArray>()
    @Volatile private var live = false

    fun connect() {
        val req = Request.Builder()
            .url(URL)
            .header("X-Api-Key", apiKey)
            .header("X-Api-Resource-Id", RESOURCE)
            .header("X-Api-Connect-Id", UUID.randomUUID().toString())
            .build()
        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                seq = 2
                webSocket.send(fullClient(UUID.randomUUID().toString()).toByteString())
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (!live) {
                    live = true
                    seq = 2
                    synchronized(pending) {
                        while (pending.isNotEmpty()) sendPcm(pending.removeFirst(), last = false)
                    }
                }
                val parsed = parse(bytes.toByteArray()) ?: return
                if (parsed.optBoolean("error")) {
                    onFail(parsed.optString("msg"))
                    return
                }
                val result = parsed.optJSONObject("result") ?: parsed
                var text = result.optString("text")
                var definite = false
                val utt = result.optJSONArray("utterances")
                if (utt != null && utt.length() > 0) {
                    val last = utt.optJSONObject(utt.length() - 1)
                    definite = last?.optBoolean("definite") == true
                    if (text.isBlank()) text = last?.optString("text").orEmpty()
                }
                if (text.isNotBlank()) onText(text, definite)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onFail(t.message ?: "doubao")
            }
        })
    }

    fun sendPcm(pcm: ByteArray, last: Boolean = false) {
        if (!live && !last) {
            synchronized(pending) { pending.addLast(pcm) }
            return
        }
        val s = seq++
        ws?.send(audioPacket(pcm, s, last).toByteString())
    }

    fun close() {
        runCatching { sendPcm(ByteArray(0), last = true) }
        ws?.close(1000, "stop")
        ws = null
    }

    companion object {
        private const val URL = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async"
        private const val RESOURCE = "volc.seedasr.sauc.duration"
        private const val CLIENT_FULL = 0b0001
        private const val CLIENT_AUDIO = 0b0010
        private const val SERVER_ERROR = 0b1111
        private const val JSON = 0b0001
        private const val GZIP = 0b0001

        private fun header(type: Int, flags: Int): ByteArray =
            byteArrayOf(
                ((0b0001 shl 4) or 0b0001).toByte(),
                ((type shl 4) or flags).toByte(),
                ((JSON shl 4) or GZIP).toByte(),
                0,
            )

        private fun gzip(data: ByteArray): ByteArray {
            val bos = ByteArrayOutputStream()
            GZIPOutputStream(bos).use { it.write(data) }
            return bos.toByteArray()
        }

        private fun gunzip(data: ByteArray): ByteArray =
            GZIPInputStream(data.inputStream()).readBytes()

        private fun fullClient(reqid: String): ByteArray {
            val json = JSONObject()
                .put("user", JSONObject().put("uid", "rokid-ear"))
                .put(
                    "audio",
                    JSONObject().put("format", "pcm").put("codec", "raw").put("rate", 16000).put("bits", 16).put("channel", 1),
                )
                .put(
                    "request",
                    JSONObject()
                        .put("model_name", "bigmodel")
                        .put("enable_itn", true)
                        .put("enable_punc", true)
                        .put("enable_ddc", true)
                        .put("show_utterances", true)
                        .put("enable_speaker_info", true)
                        .put("ssd_version", "200")
                        .put("result_type", "single")
                        .put("reqid", reqid),
                )
                .toString().toByteArray()
            val gz = gzip(json)
            return header(CLIENT_FULL, 0) + intBE(gz.size) + gz
        }

        private fun audioPacket(pcm: ByteArray, sequence: Int, last: Boolean): ByteArray {
            val flags = if (last) 0b0011 else 0b0001
            val seq = if (last) -sequence else sequence
            val gz = gzip(pcm)
            return header(CLIENT_AUDIO, flags) + intBE(seq) + intBE(gz.size) + gz
        }

        private fun intBE(v: Int): ByteArray =
            ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(v).array()

        private fun parse(frame: ByteArray): JSONObject? {
            if (frame.size < 8) return null
            val type = (frame[1].toInt() shr 4) and 0x0f
            val flags = frame[1].toInt() and 0x0f
            val compression = frame[2].toInt() and 0x0f
            var off = (frame[0].toInt() and 0x0f) * 4
            if (flags and 0x01 != 0) off += 4
            if (type == SERVER_ERROR) {
                val msg = runCatching {
                    val size = ByteBuffer.wrap(frame, off + 4, 4).order(ByteOrder.BIG_ENDIAN).int
                    val raw = frame.copyOfRange(off + 8, off + 8 + size)
                    String(if (compression == GZIP) gunzip(raw) else raw)
                }.getOrDefault("error")
                return JSONObject().put("error", true).put("msg", msg)
            }
            val size = ByteBuffer.wrap(frame, off, 4).order(ByteOrder.BIG_ENDIAN).int
            var raw = frame.copyOfRange(off + 4, (off + 4 + size).coerceAtMost(frame.size))
            if (compression == GZIP && raw.isNotEmpty()) raw = gunzip(raw)
            return runCatching { JSONObject(String(raw)) }.getOrNull()
        }
    }
}
