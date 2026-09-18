package com.chenniuniu.rokidfocus.listen

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.chenniuniu.rokidfocus.BuildConfig
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Glasses PCM → iFlytek realtime LLM ASR (role_type=2). DeepSeek writes replies.
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
    private val sendHist: (List<String>) -> Unit = {},
    private val sendState: (state: String, msg: String) -> Unit,
    private val onPhoneMic: (String) -> Unit = {},
) {
    private val running = AtomicBoolean(false)
    private val pool = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())
    private var asr: XfyunAsr? = null
    private var currentRl = 0
    private var wearerRl = 0
    private var enrollBuf: java.io.ByteArrayOutputStream? = null
    @Volatile private var asrOpen = false
    private var utterRms = 0.0
    private var utterN = 0
    private var loudEma = 4000.0
    private var pcmCount = 0
    private var lastVoiceAt = 0L
    private var lastKeepAt = 0L
    private val suggestGen = AtomicInteger(0)
    private var pendingThem: String = ""
    private var pendingWho: String = "them"
    private var lastPartial: String = ""
    private var lastPartialWho: String = "them"
    private val para = StringBuilder()
    private var paraWho = ""
    private var liveUtt = ""
    private var lastTrans = ""
    private var phonePcmCount = 0
    private var noKeySent = false
    private val phoneMic = PhoneMic(context)
    private val mixer = PcmMixer { frame -> emitMixed(frame) }

    val isOn: Boolean get() = running.get()

    fun start() {
        main.removeCallbacks(noMicWatch)
        main.removeCallbacks(keepAlive)
        main.removeCallbacks(suggestRun)
        closeAsr()
        running.set(true)
        utterRms = 0.0
        utterN = 0
        pcmCount = 0
        asrOpen = false
        pendingThem = ""
        pendingWho = "them"
        lastPartial = ""
        lastPartialWho = "them"
        para.clear()
        paraWho = ""
        liveUtt = ""
        lastTrans = ""
        currentRl = 0
        wearerRl = 0
        enrollBuf = null
        suggestGen.incrementAndGet()
        phonePcmCount = 0
        noKeySent = false
        memory.replace(seed())
        sendState("live", "phone")
        mixer.start()
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
        main.removeCallbacks(keepAlive)
        main.removeCallbacks(suggestRun)
        suggestGen.incrementAndGet()
        pendingThem = ""
        pendingWho = "them"
        running.set(false)
        flushPara(keepListen = false)
        phoneMic.stop()
        mixer.stop()
        closeAsr()
        sendState("off", "")
        Log.i(TAG, "listen stop")
    }

    fun onPcm(data: ByteArray, sampleRate: Int, channels: Int) {
        if (!running.get()) return
        val rate = if (sampleRate <= 0) 16000 else sampleRate
        val pcm = toMono16k(data, rate, channels)
        if (pcm.isEmpty()) return
        if (pcmCount == 0) {
            Log.i(TAG, "first pcm rate=$sampleRate ch=$channels n=${data.size} rms=${rms(pcm, pcm.size).toInt()}")
            main.removeCallbacks(noMicWatch)
            sendState("live", "pcm")
        }
        pcmCount++
        enrollBuf?.write(pcm)
        val rec = enrollBuf
        if (rec != null && rec.size() >= ENROLL_BYTES) {
            enrollBuf = null
            val clip = rec.toByteArray()
            pool.execute {
                val (ok, msg) = XfyunVoicePrint.register(clip)
                main.post { onEnroll(ok, msg) }
            }
        }
        val r = rms(pcm, pcm.size)
        val now = SystemClock.elapsedRealtime()
        if (r >= RMS_VAD) {
            lastVoiceAt = now
            utterRms += r
            utterN += 1
            if (r > loudEma) loudEma = loudEma * 0.9 + r * 0.1
        }
        mixer.pushGlasses(boostFar(pcm, r))
        lastKeepAt = now
    }

    private fun onPhonePcm(pcm: ByteArray) {
        if (!running.get() || pcm.isEmpty()) return
        if (phonePcmCount == 0) {
            phonePcmCount++
            main.removeCallbacks(noMicWatch)
        }
        mixer.pushPhone(pcm)
    }

    private fun emitMixed(frame: ByteArray) {
        if (!running.get()) return
        ensureAsr()
        asr?.sendPcm(frame, last = false)
    }

    private val keepAlive = object : Runnable {
        override fun run() {
            if (!running.get() || !asrOpen) return
            val now = SystemClock.elapsedRealtime()
            lastKeepAt = now
            main.postDelayed(this, KEEP_MS)
        }
    }

    private val noMicWatch = Runnable {
        if (running.get() && pcmCount == 0 && phonePcmCount == 0) sendState("err", "no mic")
    }

    private fun ensureAsr() {
        if (asr != null) {
            asrOpen = true
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
            featureIds = voiceId(),
            onText = { text, definite, speaker -> onAsr(text, definite, speaker) },
            onFail = { err ->
                Log.w(TAG, "xfyun $err")
                asrOpen = false
                asr = null
                main.removeCallbacks(keepAlive)
                if (running.get()) {
                    sendState("live", "rejoin")
                    main.postDelayed({ if (running.get() && asr == null) ensureAsr() }, 500)
                }
            },
            onReady = { sendState("live", "xfyun") },
        )
        asr = session
        asrOpen = true
        lastKeepAt = SystemClock.elapsedRealtime()
        session.connect()
        main.removeCallbacks(keepAlive)
        main.postDelayed(keepAlive, KEEP_MS)
        Log.i(TAG, "xfyun session open")
    }

    fun beginEnroll() {
        enrollBuf = java.io.ByteArrayOutputStream()
        sendState("live", "enroll 12s")
    }

    private fun closeAsr() {
        asrOpen = false
        main.removeCallbacks(keepAlive)
        asr?.close()
        asr = null
    }

    private fun onAsr(text: String, definite: Boolean, speaker: Int = 0) {
        val line = text.trim()
        if (line.isBlank() || !running.get()) return
        if (speaker > 0 && currentRl > 0 && speaker != currentRl) {
            flushPara(keepListen = true)
        }
        if (speaker > 0) currentRl = speaker
        val who = whoFor(currentRl)
        if (paraWho.isNotBlank() && who != paraWho) flushPara(keepListen = true)
        paraWho = who
        lastPartial = line
        lastPartialWho = who
        if (definite) {
            appendClause(line)
            liveUtt = ""
            val t = shownText()
            memory.add(who, t)
            onTurn(who, t)
            if (Lang.needsTrans(t, nativeLang())) {
                val key = llmKey()
                val native = nativeLang()
                pool.execute {
                    val zh = Drafts.translate(t, native, key)
                    if (zh.isNotBlank() && running.get()) {
                        lastTrans = zh
                        onTrans(zh)
                        main.post { pushTranscript() }
                    }
                }
            } else {
                lastTrans = ""
            }
            if (who != "you") scheduleSuggest(t, who)
        } else {
            liveUtt = line
        }
        pushTranscript()
    }

    private fun appendClause(text: String) {
        val t = text.trim()
        if (t.isBlank()) return
        val cur = para.toString()
        when {
            cur.isEmpty() -> para.append(t)
            t.startsWith(cur) -> {
                para.clear()
                para.append(t)
            }
            cur.endsWith(t) -> { }
            cur.contains(t) && t.length < cur.length / 2 -> { }
            else -> {
                val last = cur.last()
                if (last !in "。？！、，,.!?;； ") para.append(" ")
                para.append(t)
            }
        }
        if (para.length > 2000) para.delete(0, para.length - 1800)
    }

    private fun shownText(): String {
        val body = para.toString()
        val live = liveUtt.trim()
        return when {
            live.isBlank() -> body
            body.isBlank() -> live
            live.startsWith(body) -> live
            body.endsWith(live) -> body
            else -> "$body $live".trim()
        }
    }

    private fun pushTranscript() {
        val who = paraWho.ifBlank { lastPartialWho }
        val text = shownText()
        onLive(who, text, lastTrans)
        sendAsr(text.takeLast(500), who, lastTrans)
        sendHist(memory.hudLines(6))
    }

    private fun flushPara(keepListen: Boolean) {
        val t = shownText().trim()
        val who = paraWho.ifBlank { lastPartialWho }
        if (t.isNotBlank()) {
            memory.add(who, t)
            onLog(memory.snapshot())
            onTurn(who, t)
            sendHist(memory.hudLines(6))
            if (who != "you") scheduleSuggest(t, who)
            Log.i(TAG, "flush $who ${t.take(80)}")
        }
        para.clear()
        liveUtt = ""
        paraWho = ""
        if (!keepListen) onLive("", "", "")
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
        pendingWho = who
        suggestGen.incrementAndGet()
        main.removeCallbacks(suggestRun)
        main.postDelayed(suggestRun, SUGGEST_DEBOUNCE_MS)
    }

    private fun whoFor(rl: Int): String {
        val vol = classifyWho()
        if (vol == "you" && rl > 0 && wearerRl == 0) wearerRl = rl
        if (wearerRl > 0 && rl > 0) {
            if (rl == wearerRl) return "you"
            return if (rl == 1) "them" else "them$rl"
        }
        if (vol == "you") return "you"
        return if (rl > 1) "them$rl" else "them"
    }

    private fun boostFar(pcm: ByteArray, rms: Double): ByteArray {
        if (rms < 60.0 || rms >= FAR_TARGET) return pcm
        val gain = (FAR_TARGET / rms).coerceAtMost(FAR_GAIN_MAX)
        val out = ByteArray(pcm.size)
        var i = 0
        while (i + 1 < pcm.size) {
            val v = (pcm[i].toInt() and 0xff) or (pcm[i + 1].toInt() shl 8)
            val sample = if (v > 32767) v - 65536 else v
            val boosted = (sample * gain).toInt().coerceIn(-32767, 32767)
            out[i] = (boosted and 0xff).toByte()
            out[i + 1] = ((boosted shr 8) and 0xff).toByte()
            i += 2
        }
        return out
    }

    private fun classifyWho(): String {
        val avg = if (utterN > 0) utterRms / utterN else 0.0
        val youCut = max(2200.0, loudEma * 0.55)
        return if (avg >= youCut) "you" else "them"
    }

    companion object {
        private const val TAG = "PhoneListen"
        private const val RMS_VAD = 180.0
        private const val RMS_YOU = 3500.0
        private const val KEEP_MS = 250L
        private const val SUGGEST_DEBOUNCE_MS = 280L
        private const val FAR_TARGET = 3200.0
        private const val FAR_GAIN_MAX = 12.0
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
