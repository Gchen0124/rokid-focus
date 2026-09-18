package com.chenniuniu.rokidfocus.listen

import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Dual-mic mixer.
 *
 * Source A (glasses mic) = the wearer, near-field. Source B (phone mic) = the
 * other person, near the phone you hold toward them. Both are 16 kHz mono
 * s16le and are summed into ONE stream for a single iFlytek session.
 *
 * The two mics have independent clocks, so output is paced at exactly
 * 40 ms / 1280 bytes: each tick drains whatever both rings have, pads a
 * shortfall with silence, and drops the oldest samples when a ring backs up
 * past [MAX_BACKLOG_BYTES]. That keeps latency and drift bounded without a
 * real resampler.
 */
class PcmMixer(private val onFrame: (ByteArray) -> Unit) {

    private val glasses = PcmRing(CAPACITY_BYTES)
    private val phone = PcmRing(CAPACITY_BYTES)
    private val running = AtomicBoolean(false)
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    fun pushGlasses(pcm: ByteArray) = glasses.write(pcm, 0, pcm.size)

    fun pushPhone(pcm: ByteArray) = phone.write(pcm, 0, pcm.size)

    fun start() {
        if (!running.compareAndSet(false, true)) return
        glasses.clear()
        phone.clear()
        val th = HandlerThread("pcm-mix")
        th.start()
        thread = th
        handler = Handler(th.looper).also { it.post(tick) }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        handler?.removeCallbacks(tick)
        thread?.quitSafely()
        handler = null
        thread = null
        glasses.clear()
        phone.clear()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running.get()) return
            val g = ByteArray(FRAME_BYTES)
            val p = ByteArray(FRAME_BYTES)
            val ng = glasses.read(g, FRAME_BYTES)
            val np = phone.read(p, FRAME_BYTES)
            val out = ByteArray(FRAME_BYTES)
            var i = 0
            while (i + 1 < FRAME_BYTES) {
                val gs = if (i + 1 < ng) sample(g, i) else 0
                val ps = if (i + 1 < np) sample(p, i) else 0
                val v = (gs * GLASS_GAIN + ps * PHONE_GAIN).roundToInt().coerceIn(-32768, 32767)
                out[i] = (v and 0xff).toByte()
                out[i + 1] = ((v shr 8) and 0xff).toByte()
                i += 2
            }
            dropBacklog(glasses)
            dropBacklog(phone)
            onFrame(out)
            handler?.postDelayed(this, INTERVAL_MS)
        }
    }

    private fun dropBacklog(ring: PcmRing) {
        val extra = ring.available() - MAX_BACKLOG_BYTES
        if (extra > 0) ring.dropOldest(extra)
    }

    private fun sample(b: ByteArray, i: Int): Int {
        val v = (b[i].toInt() and 0xff) or (b[i + 1].toInt() shl 8)
        return if (v > 32767) v - 65536 else v
    }

    companion object {
        private const val FRAME_BYTES = 1280
        private const val INTERVAL_MS = 40L
        private const val CAPACITY_BYTES = 16000 * 2
        private const val MAX_BACKLOG_BYTES = 16000 / 2
        private const val GLASS_GAIN = 1.0
        private const val PHONE_GAIN = 1.5
    }
}

/** Byte ring buffer. Drops the oldest bytes on overflow so the newest audio wins. */
private class PcmRing(capacity: Int) {
    private val buf = ByteArray(capacity)
    private var head = 0
    private var size = 0
    private val lock = Any()

    fun available(): Int = synchronized(lock) { size }

    fun clear() = synchronized(lock) {
        head = 0
        size = 0
    }

    fun write(src: ByteArray, off: Int, len: Int) = synchronized(lock) {
        if (len <= 0) return
        val cap = buf.size
        var n = len
        if (n >= cap) {
            System.arraycopy(src, off + n - cap, buf, 0, cap)
            head = 0
            size = cap
            return
        }
        if (size + n > cap) {
            val drop = size + n - cap
            head = (head + drop) % cap
            size -= drop
        }
        val tail = cap - head
        if (n <= tail) {
            System.arraycopy(src, off, buf, head, n)
        } else {
            System.arraycopy(src, off, buf, head, tail)
            System.arraycopy(src, off + tail, buf, 0, n - tail)
        }
        head = (head + n) % cap
        size += n
    }

    fun read(dst: ByteArray, len: Int): Int = synchronized(lock) {
        val n = if (len < size) len else size
        if (n <= 0) return 0
        val start = (head - size + buf.size + buf.size) % buf.size
        val tail = buf.size - start
        if (n <= tail) {
            System.arraycopy(buf, start, dst, 0, n)
        } else {
            System.arraycopy(buf, start, dst, 0, tail)
            System.arraycopy(buf, 0, dst, tail, n - tail)
        }
        size -= n
        n
    }

    fun dropOldest(n: Int) = synchronized(lock) {
        val d = if (n < size) n else size
        if (d > 0) size -= d
    }
}
