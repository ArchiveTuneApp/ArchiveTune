/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.canvas

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.net.URI
import java.util.Base64

internal class AppleMusicWebToken(private val client: HttpClient) {
    private data class Token(val value: String, val expiresAtSeconds: Long)
    private val mutex = Mutex()
    private var cached: Token? = null
    private val tokenPattern = Regex("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")
    private val scriptPattern = Regex("""<script\b[^>]*\bsrc=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    suspend fun get(rejectedToken: String? = null): String = mutex.withLock {
        cached?.takeIf {
            it.value != rejectedToken && it.expiresAtSeconds > System.currentTimeMillis() / 1_000 + 300
        }?.let { return@withLock it.value }
        val pageUrl = URI("https://music.apple.com/us/new")
        val page = fetch(pageUrl.toString())
        val embedded = findToken(page)
        if (embedded != null) {
            cached = embedded
            return@withLock embedded.value
        }
        val scriptUrls = scriptPattern.findAll(page)
            .map { pageUrl.resolve(it.groupValues[1]) }
            .filter { it.scheme == "https" && it.host == "music.apple.com" && it.path.startsWith("/assets/index") }
            .distinct()
            .take(3)
        for (url in scriptUrls) {
            val token = findToken(fetch(url.toString())) ?: continue
            cached = token
            return@withLock token.value
        }
        throw IOException("Apple Music web catalog token is unavailable")
    }

    private suspend fun fetch(url: String): String {
        CanvasRequestPolicy.check(CanvasSource.APPLE_MUSIC)
        val response = client.get(url) {
            header("User-Agent", "Mozilla/5.0")
            header("Cache-Control", "no-cache")
        }
        if (response.status != HttpStatusCode.OK) throw IOException("Apple Music token request failed: ${response.status.value}")
        return response.bodyAsText()
    }

    private fun findToken(text: String): Token? = tokenPattern.findAll(text).firstNotNullOfOrNull { match ->
        runCatching {
            val value = match.value
            val payload = Json.parseToJsonElement(
                String(Base64.getUrlDecoder().decode(value.substringAfter('.').substringBefore('.')), Charsets.UTF_8),
            ).jsonObject
            if (payload["iss"]?.jsonPrimitive?.contentOrNull != "AMPWebPlay") return@runCatching null
            val expiry = payload["exp"]?.jsonPrimitive?.longOrNull ?: return@runCatching null
            Token(value, expiry).takeIf { expiry > System.currentTimeMillis() / 1_000 + 300 }
        }.getOrNull()
    }
}
