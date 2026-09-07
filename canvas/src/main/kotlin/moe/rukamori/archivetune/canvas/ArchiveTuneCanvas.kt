/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.canvas

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.canvas.models.matchesSongIdentity
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object ArchiveTuneCanvas {
    private const val BASE_URL = "https://artwork.boidu.dev/"
    private const val CACHE_TTL_MS = 60_000L
    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout) {
                connectTimeoutMillis = 12_000
                requestTimeoutMillis = 18_000
                socketTimeoutMillis = 18_000
            }
            expectSuccess = false
        }
    }

    private data class CacheEntry(val artwork: CanvasArtwork, val expiresAtMs: Long)
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    suspend fun getBySongArtist(
        song: String,
        artist: String,
        storefront: String = "us",
        forceRefresh: Boolean = false,
        source: CanvasSource = CanvasSource.ALL,
        requireVertical: Boolean = false,
    ): CanvasArtwork? {
        fun CanvasArtwork.matches(): Boolean =
            matchesSongIdentity(song, artist) &&
                !(if (requireVertical) preferredVerticalAnimationUrl else preferredAnimationUrl).isNullOrBlank()
        if (source.accepts(CanvasSource.BETTER_LYRICS)) {
            fetch(mapOf("s" to song, "a" to artist, "storefront" to storefront), forceRefresh)
                ?.takeIf { it.matches() }
                ?.let { return it }
        }
        return if (source.accepts(CanvasSource.APPLE_MUSIC)) {
            AppleMusicProvider.getBySongArtist(song, artist, null, storefront, forceRefresh)
                ?.takeIf { it.matches() }
                ?.copy(source = CanvasSource.APPLE_MUSIC)
        } else {
            null
        }
    }

    suspend fun getByAlbumId(
        albumId: String,
        source: CanvasSource = CanvasSource.ALL,
    ): CanvasArtwork? {
        if (source.accepts(CanvasSource.BETTER_LYRICS)) {
            fetch(mapOf("id" to albumId))?.let { return it }
        }
        return if (source.accepts(CanvasSource.APPLE_MUSIC)) {
            AppleMusicProvider.getByAlbumId(albumId)?.copy(source = CanvasSource.APPLE_MUSIC)
        } else {
            null
        }
    }

    suspend fun getByAlbumUrl(
        url: String,
        source: CanvasSource = CanvasSource.ALL,
    ): CanvasArtwork? {
        if (source.accepts(CanvasSource.BETTER_LYRICS)) {
            fetch(mapOf("url" to url))?.let { return it }
        }
        if (!source.accepts(CanvasSource.APPLE_MUSIC)) return null
        val parsed = runCatching { java.net.URI(url) }.getOrNull() ?: return null
        if (parsed.host != "music.apple.com") return null
        val parts = parsed.path.trim('/').split('/')
        val albumId = parts.lastOrNull()?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: return null
        val storefront = parts.firstOrNull()?.takeIf { it.length == 2 } ?: return null
        return AppleMusicProvider.getByAlbumId(albumId, storefront)?.copy(source = CanvasSource.APPLE_MUSIC)
    }

    private suspend fun fetch(
        parameters: Map<String, String>,
        forceRefresh: Boolean = false,
    ): CanvasArtwork? {
        CanvasRequestPolicy.check(CanvasSource.BETTER_LYRICS)
        val key = parameters.entries.joinToString("|") { "${it.key}=${it.value.trim().lowercase(Locale.ROOT)}" }
        if (forceRefresh) cache.remove(key)
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it.artwork }
        return try {
            val response = client.get(BASE_URL) {
                parameters.forEach { (key, value) -> parameter(key, value) }
                if (forceRefresh) header(HttpHeaders.CacheControl, "no-cache")
            }
            CanvasRequestPolicy.check(CanvasSource.BETTER_LYRICS)
            if (response.status != HttpStatusCode.OK) return null
            val artwork = response.body<CanvasArtwork>().copy(source = CanvasSource.BETTER_LYRICS)
            if (cache.size >= 128) cache.clear()
            cache[key] = CacheEntry(artwork, System.currentTimeMillis() + CACHE_TTL_MS)
            artwork
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            System.err.println("BetterLyrics canvas request failed: ${error.javaClass.simpleName}")
            null
        }
    }

    suspend fun isHealthy(): Boolean {
        CanvasRequestPolicy.check(CanvasSource.BETTER_LYRICS)
        return try {
            client.get("${BASE_URL}health") {
                header(HttpHeaders.CacheControl, "no-cache")
            }.status == HttpStatusCode.OK
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            System.err.println("BetterLyrics canvas health failed: ${error.javaClass.simpleName}")
            false
        }
    }
}
