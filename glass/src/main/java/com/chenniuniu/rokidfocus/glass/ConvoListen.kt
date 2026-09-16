package com.chenniuniu.rokidfocus.glass

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.chenniuniu.rokidfocus.glass.data.GlassStore
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Opt-in glasses mic → Doubao. Silence is not uploaded.
 * Near-field loud = wearer; quieter = other person.
 */
class ConvoListen(
    private val context: Context,
    private val store: GlassStore,
) {
    private val running = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())
    private var ws: SimpleWs? = null
    private var rec: AudioRecord? = null
    private var hideRunnable: Runnable? = null
    private var utterRms = 0.0
    private var utterN = 0
    private var loudEma = 4000.0

    fun start() {
        if (!running.compareAndSet(false, true)) return
        store.update { it.copy(listenLine = "listen…", convoActive = false) }
        val snap = store.snapshot()
        val hosts = listOf(snap.listenHost, "127.0.0.1", "192.168.43.1", "192.168.49.1")
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val port = snap.listenPort.takeIf { it > 0 } ?: 8791
        tryHost(hosts, 0, port)
    }

    private fun tryHost(hosts: List<String>, index: Int, port: Int) {
        if (!running.get()) return
        if (index >= hosts.size) {
            store.update { it.copy(listenLine = "no proxy", convoActive = false) }
            running.set(false)
            return
        }
        val host = hosts[index]
        store.update { it.copy(listenLine = "try $host") }
        val sock = SimpleWs(
            host = host,
            port = port,
            onText = { text -> main.post { onWsText(text) } },
            onOpen = { main.post { onWsOpen() } },
            onFail = { err ->
                Log.w(TAG, "$host $err")
                main.post {
                    if (running.get()) tryHost(hosts, index + 1, port)
                }
            },
        )
        ws = sock
        sock.connect()
    }

    fun stop() {
        running.set(false)
        hideRunnable?.let { main.removeCallbacks(it) }
        runCatching { rec?.stop() }
        runCatching { rec?.release() }
        rec = null
        ws?.sendText("""{"type":"stop"}""")
        ws?.close()
        ws = null
        store.update {
            it.copy(
                convoActive = false,
                listenLine = "off",
                convoLine = "",
                convoDrafts = emptyList(),
                convoWho = "",
            )
        }
    }

    val isOn: Boolean get() = running.get()

    private fun onWsOpen() {
        store.update { it.copy(listenLine = "live") }
        startRecord()
    }

    private fun onWsText(text: String) {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (o.optString("type")) {
            "ready" -> store.update { it.copy(listenLine = "live") }
            "error" -> store.update { it.copy(listenLine = "asr err") }
            "asr" -> {
                val line = o.optString("text").trim()
                if (line.isBlank()) return
                val definite = o.optBoolean("definite")
                val who = classifyWho()
                val remote = mutableListOf<String>()
                o.optJSONArray("drafts")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val s = arr.optString(i).trim()
                        if (s.isNotEmpty()) remote.add(s)
                    }
                }
                showConvo(line, partial = !definite, who = who, remoteDrafts = remote)
                if (definite) {
                    utterRms = 0.0
                    utterN = 0
                }
            }
        }
    }

    private fun classifyWho(): String {
        val avg = if (utterN > 0) utterRms / utterN else 0.0
        val youCut = max(RMS_YOU, loudEma * 0.55)
        return if (avg >= youCut) "you" else "them"
    }

    @SuppressLint("MissingPermission")
    private fun startRecord() {
        val buf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            .coerceAtLeast(CHUNK)
        val recorder = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buf)
        } catch (e: Exception) {
            store.update { it.copy(listenLine = "mic fail") }
            return
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            store.update { it.copy(listenLine = "mic fail") }
            return
        }
        rec = recorder
        recorder.startRecording()
        Thread({
            val pcm = ByteArray(CHUNK)
            var hang = 0
            try {
                while (running.get()) {
                    val n = recorder.read(pcm, 0, pcm.size)
                    if (n <= 0) continue
                    val r = rms(pcm, n)
                    if (r >= RMS_VAD) {
                        hang = HANG_CHUNKS
                        utterRms += r
                        utterN += 1
                        if (r > loudEma) loudEma = loudEma * 0.9 + r * 0.1
                        ws?.sendBinary(pcm.copyOf(n))
                    } else if (hang > 0) {
                        hang -= 1
                        ws?.sendBinary(pcm.copyOf(n))
                    }
                    // silence: do not send — Doubao hours pack bills streamed audio
                }
            } catch (e: Exception) {
                Log.w(TAG, "record ${e.message}")
            }
        }, "convo-mic").start()
    }

    private fun showConvo(line: String, partial: Boolean, who: String, remoteDrafts: List<String> = emptyList()) {
        val drafts = when {
            who != "them" || partial -> emptyList()
            remoteDrafts.size >= 2 -> (remoteDrafts.take(2) + "skip")
            else -> THEM_DRAFTS
        }
        val hideFocus = who == "them"
        store.update {
            it.copy(
                convoActive = hideFocus,
                convoLine = line,
                convoPartial = partial,
                convoWho = who,
                convoDrafts = drafts,
                convoPick = 0,
                listenLine = "live",
            )
        }
        hideRunnable?.let { main.removeCallbacks(it) }
        if (hideFocus) {
            val hide = Runnable {
                store.update { it.copy(convoActive = false, convoDrafts = emptyList()) }
            }
            hideRunnable = hide
            main.postDelayed(hide, HIDE_AFTER_MS)
        }
    }

    private fun rms(pcm: ByteArray, n: Int): Double {
        var s = 0.0
        var i = 0
        while (i + 1 < n) {
            val v = (pcm[i].toInt() and 0xff) or (pcm[i + 1].toInt() shl 8)
            val sample = if (v > 32767) v - 65536 else v
            s += sample.toDouble() * sample
            i += 2
        }
        val count = (n / 2).coerceAtLeast(1)
        return sqrt(s / count)
    }

    companion object {
        private const val TAG = "ConvoListen"
        private const val RATE = 16000
        private const val CHUNK = 3200
        private const val RMS_VAD = 500.0
        private const val RMS_YOU = 2200.0
        private const val HANG_CHUNKS = 4
        private const val HIDE_AFTER_MS = 8000L
        val THEM_DRAFTS = listOf("嗯，然后呢？", "我明白。", "skip")
    }
}
