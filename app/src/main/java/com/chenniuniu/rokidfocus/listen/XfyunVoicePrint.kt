package com.chenniuniu.rokidfocus.listen

import android.util.Base64
import android.util.Log
import com.chenniuniu.rokidfocus.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object XfyunVoicePrint {
    private const val TAG = "XfyunVp"
    private const val REGISTER = "https://office-api-personal-dx.iflyaisol.com/res/feature/v1/register"

    fun register(pcm: ByteArray): Pair<Boolean, String> {
        if (pcm.size < 16000 * 2 * 10) {
            return false to "need 10s audio (${pcm.size / 32000}s)"
        }
        val clip = wavWrap(
            if (pcm.size > 16000 * 2 * 55) pcm.copyOfRange(pcm.size - 16000 * 2 * 55, pcm.size) else pcm,
        )
        val appId = BuildConfig.XFYUN_APP_ID
        val key = BuildConfig.XFYUN_API_KEY
        val secret = BuildConfig.XFYUN_API_SECRET
        val qParams = mapOf(
            "appId" to appId,
            "accessKeyId" to key,
            "dateTime" to XfyunSign.utc(),
            "signatureRandom" to UUID.randomUUID().toString().replace("-", "").take(16),
        )
        val (_, sig) = XfyunSign.signedQuery(secret, qParams)
        val query = qParams.entries.joinToString("&") {
            "${XfyunSign.enc(it.key)}=${XfyunSign.enc(it.value)}"
        }
        val body = JSONObject()
            .put("audio_data", Base64.encodeToString(clip, Base64.NO_WRAP))
            .put("audio_type", "raw") // wav container, pcm 16k/16bit/mono
            .put("uid", "rokid-focus")
            .toString()
        return runCatching {
            val conn = URL("$REGISTER?$query").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("signature", sig)
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.outputStream.use { it.write(body.toByteArray()) }
            val http = conn.responseCode
            val raw = (if (http in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            Log.i(TAG, "register $http ${raw.take(400)}")
            val o = JSONObject(raw)
            val code = o.optString("code").ifBlank { o.optInt("code", -1).toString() }
            if (code != "000000") return@runCatching (false to "err $code ${o.optString("desc")}".trim())
            val data = o.opt("data")
            val inner = when (data) {
                is JSONObject -> data
                is String -> runCatching { JSONObject(data) }.getOrNull()
                else -> null
            } ?: return@runCatching (false to "enroll no data")
            val id = inner.optString("feature_id").ifBlank { inner.optString("featureId") }
            if (id.isNotBlank()) return@runCatching (true to id)
            false to "enroll ${inner.opt("status") ?: inner.toString().take(48)}"
        }.getOrElse { false to "enroll ${it.message?.take(40)}" }
    }

    private fun wavWrap(pcm: ByteArray): ByteArray {
        val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val rate = 16000
        val ch = 1
        val bits = 16
        val byteRate = rate * ch * bits / 8
        header.put("RIFF".toByteArray())
        header.putInt(36 + pcm.size)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(ch.toShort())
        header.putInt(rate)
        header.putInt(byteRate)
        header.putShort((ch * bits / 8).toShort())
        header.putShort(bits.toShort())
        header.put("data".toByteArray())
        header.putInt(pcm.size)
        return header.array() + pcm
    }
}
