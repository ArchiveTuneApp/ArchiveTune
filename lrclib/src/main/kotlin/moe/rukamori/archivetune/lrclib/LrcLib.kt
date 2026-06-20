/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lrclib

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.lrclib.models.Track
import moe.rukamori.archivetune.lrclib.models.bestMatchingFor
import kotlin.math.abs

object LrcLib {
    private const val MAX_SEARCH_RESULTS = 5
    private const val MAX_DURATION_DELTA_SECONDS = 2

    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                    },
                )
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 15000
            }

            defaultRequest {
                url("https://lrclib.net")
            }

            expectSuccess = true
        }
    }

    private suspend fun queryLyrics(
        artist: String,
        title: String,
        album: String? = null,
    ): List<Track> =
        client
            .get("/api/search") {
                parameter("track_name", title)
                parameter("artist_name", artist)
                if (album != null) parameter("album_name", album)
            }.body<List<Track>>()
            .filter { track -> track.syncedLyrics.isUsableLyrics() || track.plainLyrics.isUsableLyrics() }

    suspend fun getLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ) = runCatching {
        val tracks = queryLyrics(artist, title, album)

        val lyrics =
            when {
                duration == -1 -> tracks.bestMatchingFor(duration, title, artist)?.preferredLyrics()
                else -> tracks.bestMatchingFor(duration)?.preferredLyrics()
            } ?: throw IllegalStateException("Lyrics unavailable")

        lyrics
    }

    suspend fun getAllLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
        callback: (String) -> Unit,
    ) {
        val tracks = queryLyrics(artist, title, album)
        var count = 0
        var emittedPlainLyrics = false

        val sortedTracks =
            when {
                duration == -1 -> {
                    tracks.sortedByDescending { track ->
                        var score = 0.0
                        if (track.syncedLyrics.isUsableLyrics()) score += 1.0
                        if (track.plainLyrics.isUsableLyrics()) score += 0.25

                        val titleSimilarity = calculateStringSimilarity(title, track.trackName)
                        val artistSimilarity = calculateStringSimilarity(artist, track.artistName)
                        score + (titleSimilarity + artistSimilarity) / 2.0
                    }
                }

                else -> {
                    tracks.sortedBy { track -> abs(track.duration.toInt() - duration) }
                }
            }

        sortedTracks.forEach { track ->
            currentCoroutineContext().ensureActive()
            if (count >= MAX_SEARCH_RESULTS) return
            if (!track.matchesDuration(duration)) return@forEach

            track.syncedLyrics?.takeIf(String::isNotBlank)?.let { lyrics ->
                callback(lyrics)
                count++
            }

            if (!emittedPlainLyrics && count < MAX_SEARCH_RESULTS) {
                track.plainLyrics?.takeIf(String::isNotBlank)?.let { lyrics ->
                    callback(lyrics)
                    count++
                    emittedPlainLyrics = true
                }
            }
        }
    }

    private fun Track.matchesDuration(duration: Int): Boolean =
        duration == -1 || abs(this.duration.toInt() - duration) <= MAX_DURATION_DELTA_SECONDS

    private fun Track.preferredLyrics(): String? = syncedLyrics.takeIf { it.isUsableLyrics() } ?: plainLyrics.takeIf { it.isUsableLyrics() }

    private fun String?.isUsableLyrics(): Boolean = !isNullOrBlank()

    private fun calculateStringSimilarity(
        str1: String,
        str2: String,
    ): Double {
        val s1 = str1.trim().lowercase()
        val s2 = str2.trim().lowercase()

        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        return when {
            s1.contains(s2) || s2.contains(s1) -> {
                0.8
            }

            else -> {
                val maxLength = maxOf(s1.length, s2.length)
                val distance = levenshteinDistance(s1, s2)
                1.0 - (distance.toDouble() / maxLength)
            }
        }
    }

    private fun levenshteinDistance(
        str1: String,
        str2: String,
    ): Int {
        val len1 = str1.length
        val len2 = str2.length
        val matrix = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) matrix[i][0] = i
        for (j in 0..len2) matrix[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (str1[i - 1] == str2[j - 1]) 0 else 1
                matrix[i][j] =
                    minOf(
                        matrix[i - 1][j] + 1,
                        matrix[i][j - 1] + 1,
                        matrix[i - 1][j - 1] + cost,
                    )
            }
        }

        return matrix[len1][len2]
    }

    suspend fun lyrics(
        artist: String,
        title: String,
    ) = runCatching {
        queryLyrics(artist = artist, title = title, album = null)
    }

    @JvmInline
    value class Lyrics(
        val text: String,
    ) {
        val sentences
            get() =
                runCatching {
                    buildMap {
                        put(0L, "")
                        text.trim().lines().filter { it.length >= 10 }.forEach {
                            put(
                                it[8].digitToInt() * 10L +
                                    it[7].digitToInt() * 100 +
                                    it[5].digitToInt() * 1000 +
                                    it[4].digitToInt() * 10000 +
                                    it[2].digitToInt() * 60 * 1000 +
                                    it[1].digitToInt() * 600 * 1000,
                                it.substring(10),
                            )
                        }
                    }
                }.getOrNull()
    }
}
