package com.chenniuniu.rokidfocus.agent

import org.json.JSONArray
import org.json.JSONObject
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * A personal agent backend. Two shapes exist today: an OpenAI-compatible Hermes
 * Gateway and the Rizon (灵珠) bridge. Both stream text; images may arrive as
 * markdown links inside the text.
 */
interface AgentClient {
    fun ask(
        history: List<AgentMessage>,
        prompt: String,
        images: List<String>,
        onDelta: (String) -> Unit,
        onImage: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    )

    fun cancel()
}

/** No-endpoint backend so the Agent tab is testable before keys exist. */
class MockAgentClient : AgentClient {
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile private var cancelled = false

    override fun ask(
        history: List<AgentMessage>,
        prompt: String,
        images: List<String>,
        onDelta: (String) -> Unit,
        onImage: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ) {
        cancelled = false
        executor.execute {
            val reply = "收到：「${prompt.take(40)}」。这是 mock 回复，用来验证 Agent tab 的流式文本与图片渲染（现在是 mock 后端，填上 Hermes URL/key 就会走真链路）。"
            for (ch in reply) {
                if (cancelled) return@execute
                onDelta(ch.toString())
                runCatching { Thread.sleep(22) }
            }
            if (cancelled) return@execute
            onImage("https://picsum.photos/seed/rokid-focus/900/600")
            onDone()
        }
    }

    override fun cancel() {
        cancelled = true
    }
}

/** Direct Hermes Gateway: POST /v1/chat/completions, OpenAI-compatible SSE. */
class HermesDirectClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) : AgentClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile private var call: Call? = null

    override fun ask(
        history: List<AgentMessage>,
        prompt: String,
        images: List<String>,
        onDelta: (String) -> Unit,
        onImage: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val messages = JSONArray()
        history.takeLast(20).forEach { m ->
            if (m.text.isNotBlank()) {
                messages.put(
                    JSONObject()
                        .put("role", if (m.fromUser) "user" else "assistant")
                        .put("content", m.text),
                )
            }
        }
        val content: Any = if (images.isEmpty()) {
            prompt
        } else {
            JSONArray().apply {
                put(JSONObject().put("type", "text").put("text", prompt))
                images.forEach { url ->
                    put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", url)))
                }
            }
        }
        messages.put(JSONObject().put("role", "user").put("content", content))
        val payload = JSONObject()
            .put("model", model.ifBlank { "hermes-agent" })
            .put("stream", true)
            .put("messages", messages)

        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()

        executor.execute {
            val acc = StringBuilder()
            val seen = HashSet<String>()
            try {
                val active = http.newCall(request)
                call = active
                active.execute().use { response ->
                    val body = response.body
                    if (!response.isSuccessful || body == null) {
                        onError("http ${response.code}")
                        return@execute
                    }
                    val source = body.source()
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        val delta = runCatching {
                            JSONObject(data).getJSONArray("choices").getJSONObject(0)
                                .getJSONObject("delta").optString("content")
                        }.getOrNull().orEmpty()
                        if (delta.isEmpty()) continue
                        acc.append(delta)
                        onDelta(delta)
                        IMAGE_RE.findAll(acc).forEach { m ->
                            val url = m.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: return@forEach
                            if (seen.add(url)) onImage(url)
                        }
                    }
                }
                onDone()
            } catch (e: Exception) {
                onError(e.message ?: "hermes fail")
            } finally {
                call = null
            }
        }
    }

    override fun cancel() {
        runCatching { call?.cancel() }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val IMAGE_RE = Regex(
            """!\[[^\]]*\]\((https?://[^)\s]+)\)|(https?://\S+\.(?:png|jpe?g|gif|webp))""",
            RegexOption.IGNORE_CASE,
        )
    }
}
