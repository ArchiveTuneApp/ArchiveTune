/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.betterlyrics

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.betterlyrics.models.TTMLResponse

object BetterLyrics {
    private const val API_BASE_URL = "https://lyrics-api.boidu.dev/"
    private const val TTML_LYRICS_PATH = "getLyrics"
    private const val KUGOU_LYRICS_PATH = "kugou/getLyrics"
    private val jsonFormat by lazy {
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(jsonFormat)
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 20000
                connectTimeoutMillis = 15000
                socketTimeoutMillis = 20000
            }

            defaultRequest {
                url("https://lyrics-api.boidu.dev/")
            }

            // Don't throw on non-2xx responses, handle them gracefully
            expectSuccess = false
        }
    }

    var logger: ((String) -> Unit)? = null

    private suspend fun fetchLyrics(
        artist: String,
        title: String,
        album: String?,
        durationSeconds: Int,
    ): String? {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        val cleanAlbum = album?.trim().orEmpty()

        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return null

        val endpoints = listOf(TTML_LYRICS_PATH, KUGOU_LYRICS_PATH)

        for (endpoint in endpoints) {
            fetchLyricsFromEndpoint(
                endpoint = endpoint,
                title = cleanTitle,
                artist = cleanArtist,
                album = cleanAlbum,
                durationSeconds = durationSeconds,
            )?.let { lyrics ->
                return lyrics
            }
        }

        return null
    }

    private suspend fun fetchLyricsFromEndpoint(
        endpoint: String,
        title: String,
        artist: String,
        album: String,
        durationSeconds: Int,
    ): String? {
        logger?.invoke(buildRequestLog(endpoint, title, artist, album, durationSeconds))

        return try {
            val response: HttpResponse =
                client.get(endpoint) {
                    parameter("s", title)
                    parameter("a", artist)
                    if (album.isNotBlank()) parameter("al", album)
                    if (durationSeconds > 0) parameter("d", durationSeconds)
                }

            logger?.invoke("$endpoint response status: ${response.status}")

            val responseText = response.bodyAsText()
            if (!response.status.isSuccess()) {
                logger?.invoke("$endpoint request failed with status: ${response.status}")
                return null
            }

            val lyrics =
                try {
                    decodeLyrics(responseText)
                } catch (e: Exception) {
                    logger?.invoke("$endpoint parse error: ${e.message}")
                    ""
                }

            logger?.invoke("$endpoint lyrics length: ${lyrics.length}")
            lyrics.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            logger?.invoke("$endpoint error fetching lyrics: ${e.stackTraceToString()}")
            null
        }
    }

    private fun decodeLyrics(responseText: String): String {
        val trimmed = responseText.trim()
        if (trimmed.startsWith("<")) return trimmed
        return jsonFormat.decodeFromString<TTMLResponse>(responseText).ttml
    }

    private fun buildRequestLog(
        endpoint: String,
        title: String,
        artist: String,
        album: String,
        durationSeconds: Int,
    ): String =
        buildString {
            append("Sending request to ")
            append(API_BASE_URL)
            append(endpoint)
            append(" (s=")
            append(title)
            append(", a=")
            append(artist)
            if (album.isNotBlank()) {
                append(", al=")
                append(album)
            }
            if (durationSeconds > 0) {
                append(", d=")
                append(durationSeconds)
            }
            append(")")
        }

    suspend fun getLyrics(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Int = -1,
    ) = runCatching {
        require(title.isNotBlank() && artist.isNotBlank()) { "Song title and artist are required" }
        val ttml =
            fetchLyrics(
                artist = artist,
                title = title,
                album = album,
                durationSeconds = durationSeconds,
            )
                ?: throw IllegalStateException("Lyrics unavailable")
        ttml
    }

    suspend fun getAllLyrics(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Int = -1,
        callback: (String) -> Unit,
    ) {
        val result =
            getLyrics(
                title = title,
                artist = artist,
                album = album,
                durationSeconds = durationSeconds,
            )
        result.onSuccess { ttml ->
            callback(ttml)
        }
    }
}
