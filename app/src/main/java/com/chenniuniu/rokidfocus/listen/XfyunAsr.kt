package com.chenniuniu.rokidfocus.listen

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.chenniuniu.rokidfocus.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * iFlytek realtime LLM ASR. role_type=2 splits speakers.
 * featureIds (enrolled voice) maps the wearer when present.
 */
class XfyunAsr(
    private val featureIds: String = "",
    private val roleType: Int = 0,
    private val onText: (text: String, definite: Boolean, speaker: Int) -> Unit,
    private val onFail: (String) -> Unit,
    private val onReady: () -> Unit = {},
) {
    private val http = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()
    private var ws: WebSocket? = null
    private var sessionId: String? = null
    @Volatile private var closed = false
    private var triedMinor = false
    private val ready = AtomicBoolean(false)
    private val chunks = ArrayDeque<ByteArray>()
    private var chunkOff = 0
    private var pumpThread: HandlerThread? = null
    private var pump: Handler? = null

    fun connect() {
        closed = false
        ready.set(false)
        chunks.clear()
        chunkOff = 0
        sessionId = null
        val extra = mutableMapOf(
            "audio_encode" to "pcm_s16le",
            "lang" to if (triedMinor) "autodialect" else "autominor",
            "samplerate" to "16000",
            "eng_vad_mdn" to "1",
        )
        // Blind role separation holds intermediate results back until it can name
        // the speaker, which kills word-by-word captions. We label by mic source
        // instead, so only turn it on when the caller asks for it.
        if (roleType != 0) extra["role_type"] = roleType.toString()
        if (roleType == 2 && featureIds.isNotBlank()) {
            extra["feature_ids"] = featureIds
            extra["eng_spk_match"] = "1"
        }
        val q = XfyunSign.asrQuery(
            BuildConfig.XFYUN_APP_ID,
            BuildConfig.XFYUN_API_KEY,
            BuildConfig.XFYUN_API_SECRET,
            extra,
        )
        val url = "$HOST?$q"
        val req = Request.Builder().url(url).build()
        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "ws open")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMsg(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                ready.set(false)
                val code = response?.code ?: 0
                Log.w(TAG, "ws fail $code ${t.message}")
                if (!closed && !triedMinor && (code == 35020 || code == 401 || code == 403 || t.message?.contains("35020") == true)) {
                    Log.i(TAG, "autominor not enabled, fallback autodialect (中英+方言)")
                    triedMinor = true
                    pump?.removeCallbacks(pumpRun)
                    pumpThread?.quitSafely()
                    pump = null
                    pumpThread = null
                    ws = null
                    connect()
                    return
                }
                if (!closed) onFail(t.message ?: "xfyun")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                ready.set(false)
                if (!closed) onFail("closed $code")
            }
        })
        val th = HandlerThread("xfyun-pcm")
        th.start()
        pumpThread = th
        val h = Handler(th.looper)
        pump = h
        h.post(pumpRun)
    }

    fun sendPcm(pcm: ByteArray, last: Boolean = false) {
        if (closed) return
        if (pcm.isNotEmpty()) {
            synchronized(chunks) {
                chunks.addLast(pcm.copyOf())
                var queued = chunks.sumOf { it.size } - chunkOff
                while (queued > 16000 * 2 * 3 && chunks.size > 1) {
                    val drop = chunks.removeFirst()
                    queued -= drop.size - chunkOff
                    chunkOff = 0
                }
            }
        }
        if (last) close()
    }

    fun close() {
        if (closed) return
        closed = true
        pump?.removeCallbacks(pumpRun)
        pumpThread?.quitSafely()
        pump = null
        pumpThread = null
        val sid = sessionId
        val end = if (sid.isNullOrBlank()) {
            """{"end":true}"""
        } else {
            """{"end":true,"sessionId":"$sid"}"""
        }
        runCatching { ws?.send(end) }
        ws?.close(1000, "stop")
        ws = null
        synchronized(chunks) {
            chunks.clear()
            chunkOff = 0
        }
    }

    private val pumpRun = object : Runnable {
        override fun run() {
            if (closed) return
            if (ready.get() && !closed) {
                var frame = take(FRAME)
                if (frame.isEmpty()) frame = ByteArray(FRAME)
                ws?.send(frame.toByteString())
            }
            pump?.postDelayed(this, INTERVAL_MS)
        }
    }

    private fun take(n: Int): ByteArray {
        val out = ByteArray(n)
        var i = 0
        synchronized(chunks) {
            while (i < n && chunks.isNotEmpty()) {
                val c = chunks.first()
                val avail = c.size - chunkOff
                val copy = minOf(avail, n - i)
                System.arraycopy(c, chunkOff, out, i, copy)
                i += copy
                chunkOff += copy
                if (chunkOff >= c.size) {
                    chunks.removeFirst()
                    chunkOff = 0
                }
            }
        }
        return if (i == n) out else if (i == 0) ByteArray(0) else out.copyOf(i)
    }

    private fun handleMsg(raw: String) {
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val msgType = o.optString("msg_type")
        val data = o.opt("data")
        val dataObj: JSONObject? = when (data) {
            is JSONObject -> data
            is String -> runCatching { JSONObject(data) }.getOrNull()
            else -> o.optJSONObject("data")
        }
        if (msgType == "action") {
            val act = dataObj?.optString("action").orEmpty()
            if (act == "started" || dataObj?.optString("sessionId")?.isNotBlank() == true) {
                sessionId = dataObj?.optString("sessionId").orEmpty()
                if (ready.compareAndSet(false, true)) {
                    Log.i(TAG, "started sid=$sessionId")
                    onReady()
                }
            } else if (act == "error") {
                onFail(dataObj?.optString("desc").orEmpty().ifBlank { "xfyun" }.take(40))
            }
            return
        }
        if (msgType == "error") {
            onFail(o.optString("desc").ifBlank { o.optString("code") }.take(40))
            return
        }
        val payload = dataObj ?: return
        if (payload.has("normal") && !payload.optBoolean("normal")) {
            onFail(payload.optString("desc").ifBlank { "xfyun" }.take(40))
            return
        }
        val st = findSt(payload)
        if (st == null) {
            Log.i(TAG, "no st keys=${keysOf(payload)}")
            return
        }
        val type = st.optString("type").ifBlank { st.optInt("type", 1).toString() }
        val definite = type == "0"
        val (text, rl) = readWords(st)
        if (text.isNotBlank()) {
            Log.i(
                TAG,
                "asr type=$type seg=${payload.optString("seg_id")} ls=${payload.optString("ls")} " +
                    "rl=$rl n=${text.length} $text",
            )
            onText(text, definite, rl)
        } else {
            Log.i(TAG, "asr type=$type empty seg=${payload.optString("seg_id")}")
        }
    }

    /**
     * iFlytek may key the transcript by language (`cn`, `en`, …). Prefer the
     * source-language nodes, then fall back to any object that carries `st`.
     */
    private fun findSt(payload: JSONObject): JSONObject? {
        for (key in listOf("cn", "en", "ko", "ja")) {
            payload.optJSONObject(key)?.optJSONObject("st")?.let { return it }
        }
        val keys = payload.keys()
        while (keys.hasNext()) {
            val o = payload.optJSONObject(keys.next()) ?: continue
            o.optJSONObject("st")?.let { return it }
        }
        return null
    }

    private fun keysOf(payload: JSONObject): List<String> {
        val out = mutableListOf<String>()
        val keys = payload.keys()
        while (keys.hasNext()) out.add(keys.next())
        return out
    }

    private fun readWords(st: JSONObject): Pair<String, Int> {
        val rt = st.optJSONArray("rt") ?: return "" to 0
        val sb = StringBuilder()
        var rl = 0
        for (i in 0 until rt.length()) {
            val ws = rt.optJSONObject(i)?.optJSONArray("ws") ?: continue
            for (j in 0 until ws.length()) {
                val cw = ws.optJSONObject(j)?.optJSONArray("cw") ?: continue
                for (k in 0 until cw.length()) {
                    val w = cw.optJSONObject(k) ?: continue
                    val piece = w.optString("w")
                    if (piece.isNotEmpty()) sb.append(piece)
                    val r = w.optString("rl").ifBlank { w.optInt("rl", 0).toString() }.toIntOrNull() ?: 0
                    if (r > 0) rl = r
                }
            }
        }
        return sb.toString() to rl
    }

    companion object {
        private const val TAG = "XfyunAsr"
        private const val HOST = "wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1"
        private const val FRAME = 1280
        private const val INTERVAL_MS = 40L
    }
}
