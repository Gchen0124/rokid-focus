package com.chenniuniu.rokidfocus.listen

object Lang {
    fun detect(text: String): String {
        val t = text.trim()
        if (t.isEmpty()) return "zh"
        var hangul = 0
        var kana = 0
        var cjk = 0
        var latin = 0
        for (ch in t) {
            val c = ch.code
            when {
                c in 0xAC00..0xD7AF -> hangul++
                c in 0x3040..0x30FF -> kana++
                c in 0x4E00..0x9FFF -> cjk++
                ch.isLetter() && c < 128 -> latin++
            }
        }
        val n = hangul + kana + cjk + latin
        if (n == 0) return "zh"
        return when {
            hangul * 4 >= n -> "ko"
            kana * 4 >= n -> "ja"
            cjk * 2 >= n -> "zh"
            latin * 2 >= n -> "en"
            hangul > 0 -> "ko"
            kana > 0 -> "ja"
            cjk >= latin -> "zh"
            else -> "en"
        }
    }

    fun needsTrans(spoken: String, native: String): Boolean {
        val s = detect(spoken)
        if (s == native) return false
        if (s == "zh") return false
        return true
    }

    /** zh | en | bilingual | native */
    fun optionMode(spoken: String, native: String): String {
        val s = detect(spoken)
        return when {
            s == "zh" -> "zh"
            s == "en" && native == "en" -> "en"
            s == "en" -> "bilingual"
            else -> "native"
        }
    }

    fun nativeName(code: String): String = when (code) {
        "en" -> "English"
        "zh" -> "Simplified Chinese"
        else -> "Simplified Chinese"
    }
}
