package com.chenniuniu.rokidfocus.speak

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Speaks a line for the wearer. Audio follows the phone's media route, so a
 * connected Bluetooth speaker (or the phone speaker) becomes the wearer's voice
 * when they would rather not talk.
 */
class TtsSpeaker(context: Context) {

    private var tts: TextToSpeech? = null
    private var pending: Pair<String, String>? = null

    @Volatile
    private var ready = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                runCatching {
                    tts?.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                }
                pending?.let { (text, lang) -> speakNow(text, lang) }
            }
            pending = null
        }
    }

    val isReady: Boolean get() = ready

    fun speak(text: String, lang: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        if (!ready) {
            pending = t to lang
            return
        }
        speakNow(t, lang)
    }

    private fun speakNow(text: String, lang: String) {
        val engine = tts ?: return
        runCatching {
            engine.language = localeFor(lang)
            engine.setSpeechRate(1.0f)
            engine.setPitch(1.0f)
            engine.speak(text.take(3800), TextToSpeech.QUEUE_FLUSH, null, "rokid-${System.currentTimeMillis()}")
        }
    }

    fun stop() {
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
    }

    private fun localeFor(lang: String): Locale = when (lang) {
        "zh" -> Locale.SIMPLIFIED_CHINESE
        "en" -> Locale.US
        "ja" -> Locale.JAPANESE
        "ko" -> Locale.KOREAN
        else -> Locale.getDefault()
    }
}
