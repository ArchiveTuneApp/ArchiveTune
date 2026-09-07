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
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.canvas.models.matchesSongIdentity
import java.util.Locale

object TidalCanvasProvider {
    private const val SEARCH_URL = "https://api.tidal.com/v1/search"
    private const val EMBED_TOKEN = "vNVdglQOjFJJGG2U"
    private val coverIdPattern = Regex("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
    private val client by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    addInterceptor { CanvasRequestPolicy.intercept(it, CanvasSource.TIDAL) }
                }
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout) {
                connectTimeoutMillis = 12_000
                requestTimeoutMillis = 18_000
                socketTimeoutMillis = 18_000
            }
            expectSuccess = true
        }
    }

    suspend fun getBySongArtist(song: String, artist: String, storefront: String): CanvasArtwork? {
        val results = search("$artist $song", storefront, 10)
        for (track in results.tracks.items) {
            val album = track.album ?: continue
            val video = coverUrl(album.videoCover, "videos", "1280x1280.mp4") ?: continue
            val artwork = CanvasArtwork(
                source = CanvasSource.TIDAL,
                name = track.title,
                artist = track.artists.joinToString(", ") { it.name },
                albumName = album.title,
                static = coverUrl(album.cover, "images", "640x640.jpg"),
                videoUrl = video,
            )
            if (artwork.matchesSongIdentity(song, artist)) return artwork
        }
        return null
    }

    suspend fun isHealthy(): Boolean = search("music", "US", 1).tracks.items.isNotEmpty()

    private suspend fun search(query: String, storefront: String, limit: Int): SearchResponse {
        CanvasRequestPolicy.check(CanvasSource.TIDAL)
        val response = client.get(SEARCH_URL) {
            header("X-Tidal-Token", EMBED_TOKEN)
            parameter("query", query)
            parameter("types", "TRACKS")
            parameter("countryCode", storefront.uppercase(Locale.ROOT))
            parameter("limit", limit)
        }.body<SearchResponse>()
        CanvasRequestPolicy.check(CanvasSource.TIDAL)
        return response
    }

    private fun coverUrl(id: String?, kind: String, file: String): String? =
        id?.takeIf(coverIdPattern::matches)?.let {
            "https://resources.tidal.com/$kind/${it.replace('-', '/')}/$file"
        }

    @Serializable
    private data class SearchResponse(val tracks: TrackPage)

    @Serializable
    private data class TrackPage(val items: List<Track> = emptyList())

    @Serializable
    private data class Track(
        val title: String,
        val artists: List<Artist> = emptyList(),
        val album: Album? = null,
    )

    @Serializable
    private data class Artist(val name: String)

    @Serializable
    private data class Album(
        val title: String? = null,
        val cover: String? = null,
        val videoCover: String? = null,
    )
}
