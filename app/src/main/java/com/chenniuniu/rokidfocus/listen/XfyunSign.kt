package com.chenniuniu.rokidfocus.listen

import android.util.Base64
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.TreeMap
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object XfyunSign {
    private val utcFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXX")

    fun utc(): String = OffsetDateTime.now(ZoneOffset.ofHours(8)).format(utcFmt)

    fun enc(s: String): String =
        URLEncoder.encode(s, Charsets.UTF_8.name()).replace("+", "%20")

    fun hmacSha1(secret: String, base: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        return Base64.encodeToString(mac.doFinal(base.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    fun signedQuery(accessKeySecret: String, paramsIn: Map<String, String>): Pair<String, String> {
        val params = TreeMap<String, String>()
        paramsIn.forEach { (k, v) -> if (v.isNotBlank() && k != "signature") params[k] = v }
        val base = params.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        val sig = hmacSha1(accessKeySecret, base)
        params["signature"] = sig
        val query = params.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        return query to sig
    }

    fun asrQuery(
        appId: String,
        accessKeyId: String,
        accessKeySecret: String,
        extra: Map<String, String> = emptyMap(),
    ): String {
        val params = mutableMapOf(
            "accessKeyId" to accessKeyId,
            "appId" to appId,
            "uuid" to UUID.randomUUID().toString().replace("-", ""),
            "utc" to utc(),
        )
        params.putAll(extra)
        return signedQuery(accessKeySecret, params).first
    }
}
