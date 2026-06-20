/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.paxsenix

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import moe.rukamori.archivetune.paxsenix.models.*
import java.util.Locale
import kotlin.math.abs

object PaxsenixLyrics {
    private const val BASE_URL = "https://lyrics.paxsenix.org/"

    var userAgent: String = "ArchiveTune"
        private set

    fun setUserAgent(
        appName: String,
        versionName: String,
    ) {
        userAgent = "$appName/$versionName"
    }

    // Apple Music AMP API (direct catalog search)
    private const val AMP_BASE_URL = "https://amp-api.music.apple.com"

    private var ampToken: String =
        "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6IldlYlBsYXlLaWQifQ" +
            ".eyJpc3MiOiJBTVBXZWJQbGF5IiwiaWF0IjoxNzc0NDU2MzgyLCJleHAiOjE3ODE3" +
            "MTM5ODIsInJvb3RfaHR0cHNfb3JpZ2luIjpbImFwcGxlLmNvbSJdfQ" +
            ".4n8qYF4qa18sL1E0G9A3qX35cD8wQ-IJcS9Bh8ZT8JV_yLBtVq46B-9-2ZS3EvWHuw3yK9BYFYAhAdTaDm38vQ"

    fun setAmpToken(token: String) {
        ampToken = token
    }

    private val json =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 15000
            }

            defaultRequest {
                url(BASE_URL)
                header(HttpHeaders.UserAgent, userAgent)
                header(HttpHeaders.Accept, "application/json, text/plain, */*")
                header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
            }

            expectSuccess = false
        }
    }

    private fun resolveDurationMs(duration: Int): Long =
        when {
            duration <= 0 -> 0L

            duration > 360000 -> duration.toLong()

            // 1h+ is likely ms (360k ms = 6 min)
            else -> duration * 1000L // Likely seconds
        }

    private fun cleanJsonLyrics(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            runCatching { Json.decodeFromString<String>(trimmed) }.getOrDefault(trimmed)
        } else {
            trimmed
        }
    }

    private val ampUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36"

    /**
     * Searches Apple Music catalog via the AMP API to find a song's catalog ID.
     */
    private suspend fun searchAppleMusicId(
        title: String,
        artist: String,
        durationMs: Long,
    ): String? {
        val query = "$title $artist"
        System.err.println("PaxsenixLyrics: Searching Apple Music catalog for: $query")

        val country = Locale.getDefault().country
        val storefront = if (country.length == 2) country.lowercase(Locale.ROOT) else "us"

        return runCatching {
            val response =
                client.get("$AMP_BASE_URL/v1/catalog/$storefront/search") {
                    header("Authorization", "Bearer $ampToken")
                    header("Origin", "https://music.apple.com")
                    header("Referer", "https://music.apple.com/")
                    header(HttpHeaders.UserAgent, ampUserAgent)
                    parameter("term", query)
                    parameter("types", "songs")
                    parameter("limit", "10")
                }

            if (response.status != HttpStatusCode.OK) {
                System.err.println("PaxsenixLyrics: AMP search failed with status: ${response.status}")
                return@runCatching null
            }

            val root = response.body<JsonObject>()
            val songs =
                root["results"]
                    ?.jsonObject
                    ?.get("songs")
                    ?.jsonObject
                    ?.get("data")
                    ?.jsonArray
                    ?: return@runCatching null

            if (songs.isEmpty()) {
                System.err.println("PaxsenixLyrics: AMP search returned no results")
                return@runCatching null
            }

            data class ScoredSong(
                val id: String,
                val score: Int,
                val name: String,
                val artistName: String,
                val duration: Long,
            )

            val scored =
                songs
                    .mapNotNull { item ->
                        val obj = item.jsonObject ?: return@mapNotNull null
                        val attrs = obj["attributes"]?.jsonObject ?: return@mapNotNull null
                        val songId = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        val name = attrs["name"]?.jsonPrimitive?.contentOrNull ?: ""
                        val artistName = attrs["artistName"]?.jsonPrimitive?.contentOrNull ?: ""
                        val dur = attrs["durationInMillis"]?.jsonPrimitive?.longOrNull ?: 0L

                        var score = 0
                        if (name.equals(title, ignoreCase = true)) {
                            score += 20
                        } else if (name.contains(title, ignoreCase = true) || title.contains(name, ignoreCase = true)) {
                            score += 10
                        }
                        if (artistName.equals(artist, ignoreCase = true)) {
                            score += 15
                        } else if (artistName.contains(artist, ignoreCase = true) || artist.contains(artistName, ignoreCase = true)) {
                            score += 5
                        }
                        if (durationMs > 0 && dur > 0) {
                            val diff = abs(dur - durationMs)
                            if (diff < 3000) {
                                score += 10
                            } else if (diff < 10000) {
                                score += 5
                            }
                        }

                        ScoredSong(songId, score, name, artistName, dur)
                    }.sortedByDescending { it.score }

            val best = scored.firstOrNull() ?: return@runCatching null
            System.err.println("PaxsenixLyrics: Best AMP match: ${best.name} by ${best.artistName} (ID: ${best.id}, Score: ${best.score})")

            if (best.score < 12) {
                System.err.println("PaxsenixLyrics: Rejecting match — score $best.score < 12")
                return@runCatching null
            }

            best.id
        }.onFailure { e ->
            if (e is CancellationException) throw e
            System.err.println("PaxsenixLyrics: AMP search error: ${e.message}")
        }.getOrNull()
    }

    suspend fun getAppleMusicLyrics(
        title: String,
        artist: String,
        durationSeconds: Int,
    ): Result<String> =
        runCatching {
            val durationMs = resolveDurationMs(durationSeconds)
            val songId =
                searchAppleMusicId(title, artist, durationMs)
                    ?: throw IllegalStateException("Apple Music lyrics unavailable")

            val lyricsResponse =
                client.get("apple-music/lyrics") {
                    parameter("id", songId)
                    parameter("ttml", "true")
                }

            System.err.println("PaxsenixLyrics: Apple Music lyrics (TTML) status: ${lyricsResponse.status}")
            if (lyricsResponse.status == HttpStatusCode.OK) {
                try {
                    val rawBody = lyricsResponse.body<String>().trim()

                    if (rawBody.startsWith("<tt") || rawBody.startsWith("<?xml")) {
                        System.err.println("PaxsenixLyrics: SUCCESS from Apple Music (Direct TTML)")
                        return@runCatching rawBody
                    }

                    val data = Json.decodeFromString<JsonObject>(rawBody)
                    val content = data["content"]?.jsonPrimitive?.content
                    if (content != null && (content.contains("<tt") || content.contains("<?xml"))) {
                        System.err.println("PaxsenixLyrics: SUCCESS from Apple Music (JSON-wrapped TTML, Length: ${content.length})")
                        return@runCatching content
                    } else {
                        System.err.println("PaxsenixLyrics: Apple Music TTML content was null or invalid. Type: ${data["type"]}")
                    }
                } catch (e: Exception) {
                    System.err.println("PaxsenixLyrics: Error parsing Apple Music TTML: ${e.message}")
                }
            }

            val jsonResponse =
                client.get("apple-music/lyrics") {
                    parameter("id", songId)
                }
            System.err.println("PaxsenixLyrics: Apple Music lyrics (JSON) status: ${jsonResponse.status}")
            if (jsonResponse.status == HttpStatusCode.OK) {
                val lyricsData = jsonResponse.body<AppleMusicLyricsResponse>()
                if (lyricsData.content.isNotEmpty()) {
                    System.err.println("PaxsenixLyrics: SUCCESS from Apple Music (LRC Fallback)")
                    return@runCatching convertAppleMusicToLrc(lyricsData)
                }
            }

            throw IllegalStateException("Apple Music lyrics unavailable")
        }

    suspend fun getNeteaseLyrics(
        title: String,
        artist: String,
        durationSeconds: Int,
    ): Result<String> =
        runCatching {
            val durationMs = resolveDurationMs(durationSeconds)
            val query = "$title $artist"
            val neteaseSearch =
                client.get("netease/search") {
                    parameter("q", query)
                }

            if (neteaseSearch.status == HttpStatusCode.OK) {
                val searchResponse = neteaseSearch.body<NeteaseSearchResponse>()
                val songs = searchResponse.result?.songs ?: emptyList()

                val bestMatch =
                    if (durationMs > 0) {
                        songs.minByOrNull { abs(it.duration.toLong() - durationMs) }
                    } else {
                        songs.firstOrNull()
                    }

                if (bestMatch != null) {
                    val diff = abs(bestMatch.duration.toLong() - durationMs)
                    System.err.println(
                        "PaxsenixLyrics: Best NetEase match: ${bestMatch.name} (ID: ${bestMatch.id}, Duration: ${bestMatch.duration}, Diff: $diff)",
                    )
                    if (durationMs <= 0 || (diff < 10000)) {
                        val lyricsResponse =
                            client.get("netease/lyrics") {
                                parameter("id", bestMatch.id)
                                parameter("word", "true")
                            }

                        System.err.println("PaxsenixLyrics: NetEase lyrics status: ${lyricsResponse.status}")
                        if (lyricsResponse.status == HttpStatusCode.OK) {
                            val lyricsData = lyricsResponse.body<JsonObject>()

                            // Try to get word-by-word (klyric) first
                            val klyric =
                                lyricsData["klyric"]
                                    ?.jsonObject
                                    ?.get("lyric")
                                    ?.jsonPrimitive
                                    ?.content
                            if (!klyric.isNullOrBlank()) {
                                System.err.println("PaxsenixLyrics: SUCCESS from NetEase (Karaoke)")
                                return@runCatching klyric
                            }

                            // Fallback to normal lyric (lrc)
                            val lrc =
                                lyricsData["lrc"]
                                    ?.jsonObject
                                    ?.get("lyric")
                                    ?.jsonPrimitive
                                    ?.content
                            if (!lrc.isNullOrBlank()) {
                                System.err.println("PaxsenixLyrics: SUCCESS from NetEase (LRC)")
                                return@runCatching lrc
                            }
                        }
                    }
                }
            }
            throw IllegalStateException("NetEase lyrics unavailable")
        }

    suspend fun getSpotifyLyrics(
        title: String,
        artist: String,
        durationSeconds: Int,
    ): Result<String> =
        runCatching {
            val durationMs = resolveDurationMs(durationSeconds)
            val query = "$title $artist"
            val spotifySearch =
                client.get("spotify/search") {
                    parameter("q", query)
                }
            if (spotifySearch.status == HttpStatusCode.OK) {
                val items = spotifySearch.body<List<PaxsenixSearchItem>>()
                val bestMatch =
                    if (durationMs > 0) {
                        items.minByOrNull { abs(it.durationMs - durationMs) }
                    } else {
                        items.firstOrNull()
                    }

                if (bestMatch != null) {
                    val diff = abs(bestMatch.durationMs - durationMs)
                    System.err.println(
                        "PaxsenixLyrics: Best Spotify match: ${bestMatch.name ?: bestMatch.title} (ID: ${bestMatch.realId}, Duration: ${bestMatch.durationMs}, Diff: $diff)",
                    )
                    if (durationMs <= 0 || (diff < 10000)) {
                        val lyricsResponse =
                            client.get("spotify/lyrics") {
                                parameter("id", bestMatch.realId)
                            }
                        System.err.println("PaxsenixLyrics: Spotify lyrics status: ${lyricsResponse.status}")
                        if (lyricsResponse.status == HttpStatusCode.OK) {
                            val data = cleanJsonLyrics(lyricsResponse.body<String>())
                            if (data.isNotBlank()) {
                                System.err.println("PaxsenixLyrics: SUCCESS from Spotify")
                                return@runCatching data
                            }
                        }
                    }
                }
            }
            throw IllegalStateException("Spotify lyrics unavailable")
        }

    suspend fun getYouTubeLyrics(
        title: String,
        artist: String,
        durationSeconds: Int,
    ): Result<String> =
        runCatching {
            val durationMs = resolveDurationMs(durationSeconds)
            val query = "$title $artist"
            System.err.println("PaxsenixLyrics: Requesting YouTube lyrics for: $query (Duration: $durationSeconds)")

            val searchResponse =
                client.get("youtube/search") {
                    parameter("q", query)
                }
            if (searchResponse.status != HttpStatusCode.OK) {
                System.err.println("PaxsenixLyrics: YouTube search failed with status: ${searchResponse.status}")
                throw IllegalStateException("YouTube lyrics unavailable")
            }

            val items = searchResponse.body<List<PaxsenixSearchItem>>()
            val bestMatch =
                if (durationMs > 0) {
                    items.minByOrNull { abs(it.durationMs - durationMs) }
                } else {
                    items.firstOrNull()
                }

            if (bestMatch != null) {
                val diff = abs(bestMatch.durationMs - durationMs)
                System.err.println(
                    "PaxsenixLyrics: Best YouTube match: ${bestMatch.name ?: bestMatch.title} (ID: ${bestMatch.realId}, Duration: ${bestMatch.durationMs}, Diff: $diff)",
                )
                if (durationMs <= 0 || (diff < 10000)) {
                    val lyricsResponse =
                        client.get("youtube/lyrics") {
                            parameter("id", bestMatch.realId)
                        }
                    System.err.println("PaxsenixLyrics: YouTube lyrics status: ${lyricsResponse.status}")
                    if (lyricsResponse.status == HttpStatusCode.OK) {
                        val data = cleanJsonLyrics(lyricsResponse.body<String>())
                        if (data.isNotBlank() && !data.contains("\"error\"") && !data.contains("\"isError\"")) {
                            System.err.println("PaxsenixLyrics: SUCCESS from YouTube")
                            return@runCatching data
                        }
                        System.err.println("PaxsenixLyrics: YouTube returned error: ${data.take(200)}")
                    }
                }
            }
            throw IllegalStateException("YouTube lyrics unavailable")
        }

    suspend fun getMusixmatchLyrics(
        title: String,
        artist: String,
        durationSeconds: Int,
    ): Result<String> =
        runCatching {
            val query = "$title $artist"
            System.err.println("PaxsenixLyrics: Requesting Musixmatch lyrics for: $query (Duration: $durationSeconds)")

            // Try word-by-word first
            val mxmWord =
                client.get("musixmatch/lyrics") {
                    parameter("q", query)
                    parameter("t", title)
                    parameter("a", artist)
                    parameter("d", durationSeconds.toString())
                    parameter("type", "word")
                }
            if (mxmWord.status == HttpStatusCode.OK) {
                val data = cleanJsonLyrics(mxmWord.body<String>())
                if (data.isNotBlank() && !data.contains("\"error\"") && !data.contains("\"isError\"")) {
                    System.err.println("PaxsenixLyrics: SUCCESS from Musixmatch (Word)")
                    return@runCatching data
                }
                System.err.println("PaxsenixLyrics: Musixmatch (Word) returned server error: ${data.take(200)}")
            }

            // Fallback to default
            val mxmLyrics =
                client.get("musixmatch/lyrics") {
                    parameter("q", query)
                    parameter("t", title)
                    parameter("a", artist)
                    parameter("d", durationSeconds.toString())
                }
            System.err.println("PaxsenixLyrics: Musixmatch lyrics status: ${mxmLyrics.status}")
            if (mxmLyrics.status == HttpStatusCode.OK) {
                val data = cleanJsonLyrics(mxmLyrics.body<String>())
                if (data.isNotBlank() && !data.contains("\"error\"") && !data.contains("\"isError\"")) {
                    System.err.println("PaxsenixLyrics: SUCCESS from Musixmatch")
                    return@runCatching data
                }
                System.err.println("PaxsenixLyrics: Musixmatch returned server error: ${data.take(200)}")
            }
            throw IllegalStateException("Musixmatch lyrics unavailable")
        }

    suspend fun getLyrics(
        title: String,
        artist: String,
        durationSeconds: Int,
    ): Result<String> =
        runCatching {
            System.err.println("PaxsenixLyrics: --- Starting search for [$title] by [$artist] ---")

            getAppleMusicLyrics(title, artist, durationSeconds).getOrNull()?.let {
                System.err.println("PaxsenixLyrics: Search FINISHED (Apple Music)")
                return@runCatching it
            }

            getNeteaseLyrics(title, artist, durationSeconds).getOrNull()?.let {
                System.err.println("PaxsenixLyrics: Search FINISHED (NetEase)")
                return@runCatching it
            }

            getSpotifyLyrics(title, artist, durationSeconds).getOrNull()?.let {
                System.err.println("PaxsenixLyrics: Search FINISHED (Spotify)")
                return@runCatching it
            }

            getMusixmatchLyrics(title, artist, durationSeconds).getOrNull()?.let {
                System.err.println("PaxsenixLyrics: Search FINISHED (Musixmatch)")
                return@runCatching it
            }

            System.err.println("PaxsenixLyrics: Search FAILED - No providers found lyrics")
            throw IllegalStateException("Lyrics unavailable from Paxsenix for $title")
        }

    private fun convertAppleMusicToLrc(response: AppleMusicLyricsResponse): String =
        response.content.joinToString("\n") { line ->
            val minutes = line.timestamp / 1000 / 60
            val seconds = (line.timestamp / 1000) % 60
            val hundredths = (line.timestamp % 1000) / 10
            val time = String.format(Locale.US, "[%02d:%02d.%02d]", minutes, seconds, hundredths)
            val text = line.text.joinToString(" ") { it.text.trim() }
            "$time$text"
        }

    suspend fun getAllLyrics(
        title: String,
        artist: String,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        getLyrics(title, artist, duration).onSuccess(callback)
    }

    suspend fun getStats(): Result<PaxsenixStats> =
        runCatching {
            client.get("api/stats").body<PaxsenixStats>()
        }
}
