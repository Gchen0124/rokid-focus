package com.chenniuniu.rokidfocus.listen

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Drafts {
    fun fallback(): List<String> = listOf("嗯，然后呢？", "我明白。", "skip")

    fun fromDeepseek(text: String, apiKey: String): List<String> {
        if (apiKey.isBlank()) return fallback()
        return runCatching {
            val body = JSONObject()
                .put("model", "deepseek-flash")
                .put("thinking", JSONObject().put("type", "disabled"))
                .put(
                    "messages",
                    JSONArray()
                        .put(
                            JSONObject().put("role", "system").put(
                                "content",
                                "Three very short spoken replies the wearer can say. Match language. Tone: 怪奇实验室 + 外交官. JSON {\"replies\":[\"..\",\"..\",\"skip\"]}. ≤12 Chinese chars. Last is skip.",
                            ),
                        )
                        .put(JSONObject().put("role", "user").put("content", "THEY SAID:\n" + text.take(500))),
                )
                .put("max_tokens", 120)
            val conn = URL("https://api.deepseek.com/chat/completions").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val raw = conn.inputStream.bufferedReader().readText()
            val content = JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            val json = content.substring(content.indexOf('{'), content.lastIndexOf('}') + 1)
            val arr = JSONObject(json).optJSONArray("replies") ?: return fallback()
            val out = mutableListOf<String>()
            for (i in 0 until arr.length()) out.add(arr.optString(i).trim())
            while (out.size < 3) out.add("skip")
            out[2] = "skip"
            out.take(3)
        }.getOrDefault(fallback())
    }
}
