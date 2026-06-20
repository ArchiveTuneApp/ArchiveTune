/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.unison

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.unison.models.UnisonEntry
import moe.rukamori.archivetune.unison.models.UnisonResponse
import moe.rukamori.archivetune.unison.models.UnisonSearchResponse

object Unison {
    private const val API_BASE_URL = "https://unison.boidu.dev/"
    private const val MAX_SEARCH_RESULTS = 5

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
                url(API_BASE_URL)
            }

            expectSuccess = false
        }
    }

    var logger: ((String) -> Unit)? = null

    private suspend fun fetchEntry(
        videoId: String?,
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int,
    ): UnisonEntry? {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()

        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return null

        if (!videoId.isNullOrBlank()) {
            logger?.invoke("Fetching Unison lyrics by videoId: $videoId")
            val byId = fetchByVideoId(videoId)
            if (byId != null) return byId
            logger?.invoke("No match by videoId, falling back to metadata search")
        }

        return fetchByMetadata(cleanTitle, cleanArtist, album?.trim(), durationSeconds)
    }

    private suspend fun fetchByVideoId(videoId: String): UnisonEntry? {
        return try {
            val response =
                client.get("lyrics") {
                    parameter("v", videoId)
                }
            logger?.invoke("Unison videoId response status: ${response.status}")
            if (!response.status.isSuccess()) return null
            val body = response.bodyAsText()
            logger?.invoke("Unison videoId raw response: $body")
            val parsed = runCatching { jsonFormat.decodeFromString<UnisonResponse>(body) }.getOrNull()
            parsed?.takeIf { it.success }?.data?.takeIf { it.lyrics.isNotBlank() }
        } catch (e: Exception) {
            logger?.invoke("Unison videoId fetch error: ${e.message}")
            null
        }
    }

    private suspend fun fetchByMetadata(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int,
    ): UnisonEntry? {
        logger?.invoke("Fetching Unison lyrics by metadata: title=$title, artist=$artist, album=$album, duration=$durationSeconds")
        return try {
            val response =
                client.get("lyrics") {
                    parameter("song", title)
                    parameter("artist", artist)
                    if (!album.isNullOrBlank()) parameter("album", album)
                    if (durationSeconds > 0) parameter("duration", durationSeconds)
                }
            logger?.invoke("Unison metadata response status: ${response.status}")
            if (!response.status.isSuccess()) return null
            val body = response.bodyAsText()
            logger?.invoke("Unison metadata raw response: $body")
            val parsed = runCatching { jsonFormat.decodeFromString<UnisonResponse>(body) }.getOrNull()
            parsed?.takeIf { it.success }?.data?.takeIf { it.lyrics.isNotBlank() }
        } catch (e: Exception) {
            logger?.invoke("Unison metadata fetch error: ${e.message}")
            null
        }
    }

    private suspend fun searchEntries(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int,
    ): List<UnisonEntry> {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return emptyList()

        logger?.invoke("Searching Unison lyrics: title=$cleanTitle, artist=$cleanArtist")
        return try {
            val response =
                client.get("lyrics/search") {
                    parameter("song", cleanTitle)
                    parameter("artist", cleanArtist)
                    if (!album.isNullOrBlank()) parameter("album", album.trim())
                    if (durationSeconds > 0) parameter("duration", durationSeconds)
                }
            logger?.invoke("Unison search response status: ${response.status}")
            if (!response.status.isSuccess()) return emptyList()
            val body = response.bodyAsText()
            logger?.invoke("Unison search raw response: ${body.take(200)}")
            val parsed = runCatching { jsonFormat.decodeFromString<UnisonSearchResponse>(body) }.getOrNull()
            parsed?.takeIf { it.success }?.data?.filter { it.lyrics.isNotBlank() } ?: emptyList()
        } catch (e: Exception) {
            logger?.invoke("Unison search fetch error: ${e.message}")
            emptyList()
        }
    }

    suspend fun getLyrics(
        videoId: String? = null,
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Int = -1,
    ): Result<String> =
        runCatching {
            require(title.isNotBlank() && artist.isNotBlank()) { "Song title and artist are required" }
            val entry =
                fetchEntry(videoId, title, artist, album, durationSeconds)
                    ?: throw IllegalStateException("Lyrics unavailable")
            logger?.invoke("Unison got lyrics: format=${entry.format}, syncType=${entry.syncType}, length=${entry.lyrics.length}")
            entry.lyrics
        }

    suspend fun getAllLyrics(
        videoId: String? = null,
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Int = -1,
        callback: (String) -> Unit,
    ) {
        val results = searchEntries(title, artist, album, durationSeconds)
        var count = 0
        for (entry in results) {
            currentCoroutineContext().ensureActive()
            if (count >= MAX_SEARCH_RESULTS) break
            logger?.invoke("Unison search result ${count + 1}: format=${entry.format}, syncType=${entry.syncType}")
            callback(entry.lyrics)
            count++
        }
        if (count == 0) {
            currentCoroutineContext().ensureActive()
            val single = fetchEntry(videoId, title, artist, album, durationSeconds)
            if (single != null) {
                logger?.invoke("Unison getAllLyrics fallback to single fetch: format=${single.format}")
                callback(single.lyrics)
            }
        }
    }
}
