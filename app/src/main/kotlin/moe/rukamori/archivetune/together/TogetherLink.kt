/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.together

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class TogetherJoinInfo(
    val host: String,
    val port: Int,
    val sessionId: String,
    val sessionKey: String,
) {
    fun toWebSocketUrl(): String = "ws://$host:$port/together"

    fun toDeepLink(): String {
        val charset = StandardCharsets.UTF_8.name()
        val q =
            listOf(
                "host" to host,
                "port" to port.toString(),
                "sid" to sessionId,
                "key" to sessionKey,
            ).joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, charset)}=${URLEncoder.encode(v, charset)}"
            }
        return "archivetune://together?$q"
    }
}

object TogetherLink {
    fun encode(joinInfo: TogetherJoinInfo): String = joinInfo.toDeepLink()

    fun decode(raw: String): TogetherJoinInfo? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null

        val asUri = runCatching { URI(trimmed) }.getOrNull()
        if (asUri != null) {
            decodeDeepLink(asUri)?.let { return it }
            decodeWsUrl(asUri)?.let { return it }
        }

        decodeCompact(trimmed)?.let { return it }
        return null
    }

    private fun decodeDeepLink(uri: URI): TogetherJoinInfo? {
        if (!uri.scheme.equals("archivetune", ignoreCase = true)) return null
        val authority = uri.host?.lowercase() ?: uri.authority?.lowercase() ?: return null
        if (authority != "together") return null

        val params = parseQuery(uri.rawQuery)
        val host = params["host"]?.trim().orEmpty()
        val port = params["port"]?.toIntOrNull()
        val sid = params["sid"]?.trim().orEmpty()
        val key = params["key"]?.trim().orEmpty()

        if (host.isBlank() || port == null || sid.isBlank() || key.isBlank()) return null
        if (port !in 1..65535) return null

        return TogetherJoinInfo(host = host, port = port, sessionId = sid, sessionKey = key)
    }

    private fun decodeWsUrl(uri: URI): TogetherJoinInfo? {
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "ws" && scheme != "wss" && scheme != "http" && scheme != "https") return null

        val host = uri.host?.trim().orEmpty()
        val port =
            when {
                uri.port != -1 -> uri.port
                scheme == "wss" || scheme == "https" -> 443
                else -> 80
            }

        val params = parseQuery(uri.rawQuery)
        val sid = params["sid"]?.trim().orEmpty()
        val key = params["key"]?.trim().orEmpty()

        if (host.isBlank() || sid.isBlank() || key.isBlank()) return null
        if (port !in 1..65535) return null

        return TogetherJoinInfo(host = host, port = port, sessionId = sid, sessionKey = key)
    }

    private fun decodeCompact(raw: String): TogetherJoinInfo? {
        val cleaned = raw.replace("\\s+".toRegex(), "")
        val parts = cleaned.split("|")
        if (parts.size != 4) return null
        val host = parts[0].trim()
        val port = parts[1].toIntOrNull() ?: return null
        val sid = parts[2].trim()
        val key = parts[3].trim()
        if (host.isBlank() || sid.isBlank() || key.isBlank()) return null
        if (port !in 1..65535) return null
        return TogetherJoinInfo(host = host, port = port, sessionId = sid, sessionKey = key)
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val charset = StandardCharsets.UTF_8.name()
        return rawQuery
            .split("&")
            .asSequence()
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                val k = URLDecoder.decode(pair.substring(0, idx), charset)
                val v = URLDecoder.decode(pair.substring(idx + 1), charset)
                k to v
            }.toMap()
    }
}
