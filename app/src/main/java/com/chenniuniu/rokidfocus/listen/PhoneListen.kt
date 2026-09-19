package com.chenniuniu.rokidfocus.listen

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.chenniuniu.rokidfocus.BuildConfig
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

/**
 * Two-source listen, one iFlytek session per source.
 *
 *   glasses mic → lane "you"   (near-field wearer)
 *   phone   mic → lane "them"  (phone held toward the other person)
 *
 * Source decides the label, so "who" is deterministic instead of a volume
 * guess. Each lane streams iFlytek interim results to the HUD live and only
 * commits + translates on a sentence end (type=0), which is the node where a
 * sentence is sent to DeepSeek.
 */
class PhoneListen(
    private val context: android.content.Context,
    private val memory: ConvoMemory,
    private val seed: () -> List<ConvoTurn> = { emptyList() },
    private val style: () -> String,
    private val nativeLang: () -> String = { "zh" },
    private val llmKey: () -> String,
    private val voiceId: () -> String = { "" },
    private val onEnroll: (ok: Boolean, msg: String) -> Unit = { _, _ -> },
    private val onLog: (List<ConvoTurn>) -> Unit = {},
    private val onTurn: (who: String, text: String) -> Unit = { _, _ -> },
    private val onLive: (who: String, text: String, trans: String) -> Unit = { _, _, _ -> },
    private val onReplies: (List<String>) -> Unit = {},
    private val onTrans: (String) -> Unit = {},
    private val onLlm: (String) -> Unit = {},
    private val sendAsr: (text: String, who: String, trans: String) -> Unit,
    private val sendReact: (drafts: List<String>) -> Unit = {},
    private val sendState: (state: String, msg: String) -> Unit,
    private val onPhoneMic: (String) -> Unit = {},
) {
    private inner class Lane(val who: String, val gate: Double) {
        var asr: XfyunAsr? = null

        @Volatile var open = false
        val para = StringBuilder()
        var gateOpen = false
        var lastGateAt = 0L
        var pcmCount = 0
    }

    private val running = AtomicBoolean(false)
    private val pool = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())
    private val you = Lane("you", GATE_YOU)
    private val them = Lane("them", GATE_THEM)
    private var enrollBuf: java.io.ByteArrayOutputStream? = null
    private val suggestGen = AtomicInteger(0)
    private var pendingThem: String = ""
    private var displayedText: String = ""
    private var noKeySent = false
    private val phoneMic = PhoneMic(context)

    val isOn: Boolean get() = running.get()

    fun start() {
        main.removeCallbacks(noMicWatch)
        main.removeCallbacks(suggestRun)
        closeLane(you)
        closeLane(them)
        running.set(true)
        resetLane(you)
        resetLane(them)
        enrollBuf = null
        noKeySent = false
        displayedText = ""
        suggestGen.incrementAndGet()
        memory.replace(seed())
        sendState("live", "phone")
        startPhoneMic()
        main.postDelayed(noMicWatch, 5000)
        Log.i(TAG, "listen start")
    }

    private fun startPhoneMic() {
        if (!phoneMic.hasPermission()) {
            onPhoneMic("phone mic: no permission")
            return
        }
        val ok = phoneMic.start { pcm -> onPhonePcm(pcm) }
        onPhoneMic(if (ok) "phone mic on" else "phone mic fail")
    }

    fun stop() {
        main.removeCallbacks(noMicWatch)
        main.removeCallbacks(suggestRun)
        suggestGen.incrementAndGet()
        pendingThem = ""
        flushLane(you)
        flushLane(them)
        running.set(false)
        phoneMic.stop()
        closeLane(you)
        closeLane(them)
        onLive("", "", "")
        sendState("off", "")
        Log.i(TAG, "listen stop")
    }

    /** Glasses PCM → "you" lane. */
    fun onPcm(data: ByteArray, sampleRate: Int, channels: Int) {
        if (!running.get()) return
        val rate = if (sampleRate <= 0) 16000 else sampleRate
        val pcm = toMono16k(data, rate, channels)
        if (pcm.isEmpty()) return
        if (you.pcmCount == 0) {
            Log.i(TAG, "first pcm rate=$sampleRate ch=$channels n=${data.size} rms=${rms(pcm, pcm.size).toInt()}")
            main.removeCallbacks(noMicWatch)
            sendState("live", "pcm")
        }
        you.pcmCount++
        enrollBuf?.write(pcm)
        val recorder = enrollBuf
        if (recorder != null && recorder.size() >= ENROLL_BYTES) {
            enrollBuf = null
            val clip = recorder.toByteArray()
            pool.execute {
                val (ok, msg) = XfyunVoicePrint.register(clip)
                main.post { onEnroll(ok, msg) }
            }
        }
        if (!gate(you, rms(pcm, pcm.size))) return
        feed(you, pcm)
    }

    /** Phone PCM → "them" lane. */
    private fun onPhonePcm(pcm: ByteArray) {
        if (!running.get() || pcm.isEmpty()) return
        if (them.pcmCount == 0) main.removeCallbacks(noMicWatch)
        them.pcmCount++
        if (!gate(them, rms(pcm, pcm.size))) return
        feed(them, pcm)
    }

    /**
     * Per-lane voice gate. Both lanes stay live at the same time — the wearer and
     * the other person can overlap. Cross-mic echo is removed later by the
     * you/them dedupe, not by muting a lane here.
     */
    private fun gate(lane: Lane, r: Double): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (r >= lane.gate) {
            lane.gateOpen = true
            lane.lastGateAt = now
        } else if (now - lane.lastGateAt > GATE_HANG_MS) {
            lane.gateOpen = false
        }
        return lane.gateOpen
    }

    private fun feed(lane: Lane, pcm: ByteArray) {
        ensureLane(lane)
        lane.asr?.sendPcm(pcm, last = false)
    }

    private fun resetLane(lane: Lane) {
        lane.para.clear()
        lane.gateOpen = false
        lane.lastGateAt = 0L
        lane.pcmCount = 0
    }

    private fun ensureLane(lane: Lane) {
        if (lane.asr != null) {
            lane.open = true
            return
        }
        if (BuildConfig.XFYUN_APP_ID.isBlank() || BuildConfig.XFYUN_API_KEY.isBlank()) {
            if (!noKeySent) {
                noKeySent = true
                sendState("err", "no xfyun")
            }
            return
        }
        val session = XfyunAsr(
            featureIds = "",
            // The phone may hear several people talking to the wearer, so it needs
            // blind speaker separation. The glasses mic is only the wearer.
            roleType = if (lane === them) ROLE_SEPARATION else 0,
            onText = { text, definite, speaker -> onLaneText(lane, text, definite, speaker) },
            onFail = { err ->
                Log.w(TAG, "xfyun ${lane.who} $err")
                lane.open = false
                lane.asr = null
                if (running.get()) {
                    sendState("live", "rejoin")
                    main.postDelayed({ if (running.get() && lane.asr == null) ensureLane(lane) }, 500)
                }
            },
            onReady = { sendState("live", "xfyun") },
        )
        lane.asr = session
        lane.open = true
        session.connect()
        Log.i(TAG, "xfyun ${lane.who} open")
    }

    private fun closeLane(lane: Lane) {
        lane.open = false
        lane.asr?.close()
        lane.asr = null
    }

    fun beginEnroll() {
        enrollBuf = java.io.ByteArrayOutputStream()
        sendState("live", "enroll 12s")
    }

    private fun onLaneText(lane: Lane, text: String, definite: Boolean, speaker: Int) {
        val line = text.trim()
        if (line.isBlank() || !running.get()) return
        val who = if (lane === you) "you" else if (speaker > 1) "them$speaker" else "them"
        appendClause(lane, line)
        val t = lane.para.toString().trim()
        if (definite) {
            if (t.isNotBlank()) {
                Log.i(TAG, "${lane.who} FINAL ${SystemClock.elapsedRealtime()} $t")
                memory.add(who, t)
                onLog(memory.snapshot())
                onTurn(who, t)
                show(who, t, "")
                if (who != "you") scheduleSuggest(t, who)
                translateTurn(who, t)
            }
            // Sentence committed: start the next one fresh instead of re-sending
            // the whole accumulated paragraph on every iFlytek type=0.
            lane.para.clear()
        } else {
            // Interim: show the sentence growing word by word, no translation yet.
            Log.i(TAG, "${lane.who} part  ${SystemClock.elapsedRealtime()} $t")
            show(who, t, "")
        }
    }

    /** The single line the glasses see. History stays on the phone. */
    private fun show(who: String, text: String, trans: String) {
        if (text.isBlank()) return
        displayedText = text
        onLive(who, text, trans)
        sendAsr(text.takeLast(500), who, trans)
    }

    /** Sentence end → DeepSeek translation, then update the same line on the glasses. */
    private fun translateTurn(who: String, text: String) {
        if (!Lang.needsTrans(text, nativeLang())) return
        val key = llmKey()
        val native = nativeLang()
        pool.execute {
            val out = Drafts.translate(text, native, key)
            if (out.isNotBlank() && running.get()) {
                memory.setTrans(text, out)
                onTrans(out)
                main.post { if (displayedText == text) show(who, text, out) }
            }
        }
    }

    /**
     * Merge an iFlytek segment into the lane's current sentence. Intermediate
     * results may be cumulative (grow from the start) or incremental (only the
     * new tail), so handle both without dropping words.
     */
    private fun appendClause(lane: Lane, text: String) {
        val t = text.trim()
        if (t.isBlank()) return
        val cur = lane.para.toString()
        when {
            cur.isEmpty() -> lane.para.append(t)
            t == cur -> { }
            t.startsWith(cur) -> {
                lane.para.clear()
                lane.para.append(t)
            }
            cur.startsWith(t) || cur.endsWith(t) -> { }
            else -> {
                val last = cur.last()
                val next = t.first()
                val asciiRun = last.isLetterOrDigit() && last.code < 128 &&
                    next.isLetterOrDigit() && next.code < 128
                if (asciiRun || last !in "。？！、，,.!?;； ") lane.para.append(" ")
                lane.para.append(t)
            }
        }
        if (lane.para.length > 2000) lane.para.delete(0, lane.para.length - 1800)
    }

    private fun flushLane(lane: Lane) {
        val t = lane.para.toString().trim()
        if (t.isNotBlank()) {
            memory.add(lane.who, t)
            onLog(memory.snapshot())
            onTurn(lane.who, t)
        }
        lane.para.clear()
    }

    private val noMicWatch = Runnable {
        if (running.get() && you.pcmCount == 0 && them.pcmCount == 0) sendState("err", "no mic")
    }

    private val suggestRun = Runnable {
        val line = pendingThem
        val gen = suggestGen.get()
        if (line.isBlank() || !running.get()) return@Runnable
        val convo = memory.prompt()
        val tone = style()
        val key = llmKey()
        val native = nativeLang()
        val mode = Lang.optionMode(line, native)
        pool.execute {
            val result = Drafts.fromConvo(convo, line, tone, key, optionMode = mode, native = native)
            if (gen != suggestGen.get() || !running.get()) return@execute
            main.post {
                onLlm(result.status)
                if (result.status != "ok") sendState("live", result.status)
                if (result.replies.isNotEmpty()) {
                    onReplies(result.replies)
                    sendReact(result.replies)
                }
            }
        }
    }

    private fun scheduleSuggest(line: String, who: String) {
        pendingThem = line
        suggestGen.incrementAndGet()
        main.removeCallbacks(suggestRun)
        main.postDelayed(suggestRun, SUGGEST_DEBOUNCE_MS)
    }

    companion object {
        private const val TAG = "PhoneListen"
        private const val GATE_YOU = 350.0
        private const val GATE_THEM = 200.0
        private const val ROLE_SEPARATION = 2
        private const val GATE_HANG_MS = 600L
        private const val SUGGEST_DEBOUNCE_MS = 280L
        private const val ENROLL_BYTES = 16000 * 2 * 12

        fun isIdleTimeout(err: String): Boolean {
            val s = err.lowercase()
            return s.contains("timeout") || s.contains("waiting next")
        }

        fun toMono16k(pcm: ByteArray, sampleRate: Int, channels: Int): ByteArray {
            val ch = channels.coerceAtLeast(1)
            val rate = sampleRate.coerceAtLeast(1)
            val srcSamples = pcm.size / 2 / ch
            if (srcSamples <= 0) return ByteArray(0)
            if (rate == 16000 && ch == 1) return pcm
            val outSamples = (srcSamples.toLong() * 16000L / rate).toInt().coerceAtLeast(1)
            val out = ByteArray(outSamples * 2)
            var o = 0
            while (o < outSamples) {
                val src = ((o.toLong() * rate) / 16000L).toInt().coerceIn(0, srcSamples - 1)
                val idx = src * ch * 2
                out[o * 2] = pcm[idx]
                out[o * 2 + 1] = pcm[idx + 1]
                o++
            }
            return out
        }

        fun rms(pcm: ByteArray, n: Int): Double {
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
    }
}
