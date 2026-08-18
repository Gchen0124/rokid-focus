package com.chenniuniu.rokidfocus.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object TaskSync {

    fun pull(baseUrl: String): List<FocusTask> {
        val conn = open(baseUrl, "GET")
        return try {
            val body = conn.inputStream.bufferedReader().readText()
            val arr = JSONObject(body).optJSONArray("tasks")
            FocusTask.fromJson(arr?.toString() ?: "[]")
        } finally {
            conn.disconnect()
        }
    }

    fun push(baseUrl: String, tasks: List<FocusTask>) {
        val conn = open(baseUrl, "PUT")
        try {
            val raw = """{"tasks":${FocusTask.toJson(tasks)}}"""
            conn.outputStream.use { it.write(raw.toByteArray(Charsets.UTF_8)) }
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    private fun open(baseUrl: String, method: String): HttpURLConnection {
        val url = URL(baseUrl.trimEnd('/') + "/tasks")
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 4000
            readTimeout = 4000
            setRequestProperty("Content-Type", "application/json")
            doInput = true
            if (method == "PUT") doOutput = true
        }
    }
}
