package com.chenniuniu.rokidfocus.speak

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * iFlytek online TTS (streaming) over `wss://tts-api.xfyun.cn/v2/tts`.
 * Same AppID / APIKey / APISecret as the ASR. Returns 16 kHz PCM, played
 * through the phone's media route (speaker or a paired Bluetooth speaker).
 */
class XfyunTts(
    private val appId: String,
    private val apiKey: String,
    private val apiSecret: String,
    private val onLine: (String) -> Unit = {},
) {
    private val http = OkHttpClient.Builder().build()
    private val main = Handler(Looper.getMainLooper())
    private var ws: WebSocket? = null
    private var track: AudioTrack? = null
    private var gen = 0

    fun speak(text: String, voice: String, lang: String) {
        val clean = text.trim()
        if (clean.isEmpty() || appId.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            onLine("tts: no xfyun key")
            return
        }
        stop()
        val myGen = ++gen
        val url = runCatching { signedUrl() }.getOrElse {
            onLine("tts: sign fail")
            return
        }
        val request = Request.Builder().url(url).build()
        val audio = ByteArrayOutputStream()
        ws = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val payload = JSONObject()
                    .put("common", JSONObject().put("app_id", appId))
                    .put(
                        "business",
                        JSONObject()
                            .put("aue", "raw")
                            .put("auf", "audio/L16;rate=16000")
                            .put("vcn", voice.ifBlank { DEFAULT_VOICE })
                            .put("tte", "UTF8")
                            .put("reg", if (lang == "en") "2" else "0")
                            .put("speed", 50)
                            .put("volume", 60)
                            .put("pitch", 50),
                    )
                    .put(
                        "data",
                        JSONObject()
                            .put("status", 2)
                            .put("text", Base64.encodeToString(clean.take(1800).toByteArray(), Base64.NO_WRAP)),
                    )
                webSocket.send(payload.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (myGen != gen) return
                val o = runCatching { JSONObject(text) }.getOrNull() ?: return
                val code = o.optInt("code", -1)
                if (code != 0) {
                    onLine("tts: ${o.optString("message").ifBlank { "code $code" }}")
                    return
                }
                val data = o.optJSONObject("data") ?: return
                data.optString("audio").takeIf { it.isNotBlank() }?.let {
                    runCatching { audio.write(Base64.decode(it, Base64.NO_WRAP)) }
                }
                if (data.optInt("status", 1) == 2) {
                    webSocket.close(1000, "done")
                    val pcm = audio.toByteArray()
                    if (myGen == gen && pcm.isNotEmpty()) play(pcm, myGen)
                    else onLine("tts: empty")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onLine("tts: ${t.message ?: "fail"}")
                Log.w(TAG, "ws ${t.message}")
            }
        })
    }

    fun stop() {
        gen++
        runCatching { ws?.close(1000, "stop") }
        ws = null
        val t = track
        track = null
        runCatching { t?.pause() }
        runCatching { t?.flush() }
        runCatching { t?.release() }
    }

    private fun play(pcm: ByteArray, myGen: Int) {
        runCatching {
            val out = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(16000)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(pcm.size, 8192))
                .build()
            track = out
            out.play()
            out.write(pcm, 0, pcm.size)
            val ms = pcm.size.toLong() * 1000 / (16000 * 2)
            main.postDelayed({
                if (myGen == gen) {
                    runCatching { track?.stop() }
                    runCatching { track?.release() }
                    track = null
                }
            }, ms + 120)
        }.onFailure { onLine("tts: play ${it.message}") }
    }

    private fun signedUrl(): String {
        val host = "tts-api.xfyun.cn"
        val date = rfc1123()
        val requestLine = "GET /v2/tts HTTP/1.1"
        val origin = "host: $host\ndate: $date\n$requestLine"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(apiSecret.toByteArray(), "HmacSHA256"))
        val signature = Base64.encodeToString(mac.doFinal(origin.toByteArray()), Base64.NO_WRAP)
        val authOrigin =
            "api_key=\"$apiKey\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"$signature\""
        val authorization = Base64.encodeToString(authOrigin.toByteArray(), Base64.NO_WRAP)
        return "wss://$host/v2/tts?authorization=${enc(authorization)}&date=${enc(date)}&host=${enc(host)}"
    }

    private fun rfc1123(): String {
        val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("GMT")
        return fmt.format(Date())
    }

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

    fun isConfigured(): Boolean = appId.isNotBlank() && apiKey.isNotBlank() && apiSecret.isNotBlank()

    companion object {
        private const val TAG = "XfyunTts"
        const val DEFAULT_VOICE = "xiaoyan"
    }
}
