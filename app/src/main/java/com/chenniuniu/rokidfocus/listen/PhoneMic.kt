package com.chenniuniu.rokidfocus.listen

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Phone microphone — the second source in the dual-mic setup.
 *
 * The glasses mic mostly hears the wearer. Hold the phone toward the other
 * person and this mic becomes the near-field source for what THEY say.
 * Output is 16 kHz mono s16le, fed to its own iFlytek session ("them" lane).
 */
class PhoneMic(private val context: Context) {

    private val running = AtomicBoolean(false)
    private var rec: AudioRecord? = null
    private var thread: Thread? = null

    val isOn: Boolean get() = running.get()

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** Returns false when permission is missing or the mic cannot be opened. */
    fun start(onPcm: (ByteArray) -> Unit): Boolean {
        if (!hasPermission()) return false
        if (!running.compareAndSet(false, true)) return true
        if (!open(onPcm)) {
            running.set(false)
            return false
        }
        return true
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching { rec?.stop() }
        runCatching { thread?.join(400) }
        runCatching { rec?.release() }
        rec = null
        thread = null
    }

    @SuppressLint("MissingPermission")
    private fun open(onPcm: (ByteArray) -> Unit): Boolean {
        val buf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            .coerceAtLeast(CHUNK * 2)
        val recorder = firstWorking(buf) ?: return false
        rec = recorder
        return runCatching {
            recorder.startRecording()
            val t = Thread({
                val pcm = ByteArray(CHUNK)
                while (running.get()) {
                    val n = recorder.read(pcm, 0, pcm.size)
                    if (n > 0) onPcm(pcm.copyOf(n))
                }
            }, "phone-mic")
            thread = t
            t.start()
            true
        }.getOrElse {
            Log.w(TAG, "start ${it.message}")
            runCatching { recorder.release() }
            rec = null
            false
        }
    }

    private fun firstWorking(buf: Int): AudioRecord? {
        for (source in SOURCES) {
            val recorder = runCatching {
                AudioRecord(source, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buf)
            }.getOrNull()
            if (recorder != null && recorder.state == AudioRecord.STATE_INITIALIZED) return recorder
            runCatching { recorder?.release() }
        }
        return null
    }

    companion object {
        private const val TAG = "PhoneMic"
        private const val RATE = 16000
        private const val CHUNK = 1280
        private val SOURCES = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )
    }
}
