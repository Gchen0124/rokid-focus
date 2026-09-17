package com.chenniuniu.rokidfocus.listen

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tactful spoken replies from the live convo. DeepSeek on the phone.
 */
object Drafts {
    const val MODEL = "deepseek-flash"
    const val DEFAULT_STYLE = "怪奇实验室 + 外交官 — curious, calm, slightly strange, not corporate, tactful"

    fun fromConvo(
        convo: String,
        lastThem: String,
        style: String,
        apiKey: String,
    ): Result {
        if (apiKey.isBlank()) return Result.missingKey()
        val tone = style.trim().ifBlank { DEFAULT_STYLE }
        val block = convo.trim().ifBlank { "THEY: $lastThem" }
        return runCatching {
            val body = JSONObject()
                .put("model", MODEL)
                .put("thinking", JSONObject().put("type", "disabled"))
                .put("response_format", JSONObject().put("type", "json_object"))
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", SYSTEM))
                        .put(
                            JSONObject().put("role", "user").put(
                                "content",
                                "STYLE:\n$tone\n\n【convo】 full so far:\n$block\n\nLATEST LINE:\n${lastThem.take(400)}\n\nGive NEW options for what the wearer says next, given this whole talk.",
                            ),
                        ),
                )
                .put("max_tokens", 180)
            val conn = URL("https://api.deepseek.com/chat/completions").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 7000
            conn.readTimeout = 9000
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) {
                Log.w(TAG, "http $code ${raw.take(180)}")
                return Result(emptyList(), "llm $code")
            }
            val msg = JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            var content = msg.optString("content")
            if (content.isBlank()) content = msg.optString("reasoning_content")
            val start = content.indexOf('{')
            val end = content.lastIndexOf('}')
            if (start < 0 || end <= start) return Result(emptyList(), "llm parse")
            val arr = JSONObject(content.substring(start, end + 1)).optJSONArray("replies")
                ?: return Result(emptyList(), "llm parse")
            val out = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val s = arr.optString(i).trim().trim('"', '“', '”')
                if (s.isNotEmpty() && !s.equals("skip", true)) out.add(s.take(18))
            }
            while (out.size < 2) out.add("再说一遍？")
            Result(out.take(2) + "skip", "ok")
        }.getOrElse { e ->
            Log.w(TAG, "llm ${e.message}")
            Result(emptyList(), "llm fail")
        }
    }

    data class Result(val replies: List<String>, val status: String) {
        companion object {
            fun missingKey() = Result(emptyList(), "no llm")
        }
    }

    private const val TAG = "Drafts"
    private const val SYSTEM =
        "You are a live conversation helper on AR glasses. " +
            "Use the FULL 【convo】 history. Each new line must produce NEW options that follow the talk so far, not a generic reply to only the last sentence. " +
            "Speakers: YOU = wearer, THEY/S2/S3 = other people. " +
            "Suggest what the wearer should say next. Match the language just used (Chinese, English, Korean, …). " +
            "Follow STYLE. Two real options, then skip: " +
            "1) acknowledge + advance  2) clarify or diplomatically redirect. " +
            "JSON only {\"replies\":[\"..\",\"..\",\"skip\"]}. " +
            "Each option ≤14 Chinese chars or 8 English words. Spoken. Third MUST be skip."
}
