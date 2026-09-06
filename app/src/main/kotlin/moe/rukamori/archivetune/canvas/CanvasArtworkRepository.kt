/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.canvas

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.canvas.models.matchesSongIdentity
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlaybackCache
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CanvasArtworkRepository @Inject constructor() {
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()

    suspend fun resolve(
        request: CanvasPlaybackRequest,
        policy: CanvasPolicy,
        forceRefresh: Boolean = false,
    ): CanvasArtwork? = withContext(Dispatchers.IO) {
        if (request.mediaId.isBlank()) return@withContext null
        val source = policy.configuration.source
        if (!forceRefresh) {
            val cached = CanvasArtworkPlaybackCache.get(request.mediaId, preferCachedOnly = true)
            if (cached != null && source.accepts(cached.source) && cached.matches(request)) {
                return@withContext cached.copy(static = if (policy.networkAllowed) cached.static else null)
            }
        }
        if (!policy.networkAllowed) return@withContext null
        CanvasNetworkAccess.check()
        val songTitle = normalizeCanvasSongTitle(request.title)
        val artistName = normalizeCanvasArtistName(request.artist)
        val candidates = linkedSetOf(
            songTitle to artistName,
            request.title to artistName,
            songTitle to request.artist,
            request.title to request.artist,
        ).filter { (song, artist) -> song.isNotBlank() && artist.isNotBlank() }
        val fetched = candidates.firstNotNullOfOrNull { (song, artist) ->
            ArchiveTuneCanvas.getBySongArtist(
                song = song,
                artist = artist,
                storefront = request.storefront,
                source = source,
                requireVertical = request.requireVertical,
                forceRefresh = forceRefresh,
            )?.takeIf { it.matches(request) }
        } ?: return@withContext null
        CanvasNetworkAccess.check(fetched.source)
        val artwork = if (forceRefresh) {
            CanvasArtworkPlaybackCache.replace(request.mediaId, fetched)
        } else {
            CanvasArtworkPlaybackCache.put(request.mediaId, fetched)
        }
        if (forceRefresh) mutableRevision.update { it + 1 }
        artwork
    }
}

private fun CanvasArtwork.matches(request: CanvasPlaybackRequest): Boolean =
    matchesSongIdentity(request.title, request.artist) &&
        !(if (request.requireVertical) preferredVerticalAnimationUrl else preferredAnimationUrl).isNullOrBlank()

private fun normalizeCanvasSongTitle(raw: String): String {
    val stripped =
        raw
            .replace(Regex("\\s*\\[[^]]*]"), "")
            .replace(
                Regex(
                    "\\s*\\((?:feat\\.?|ft\\.?|featuring|with)\\b[^)]*\\)",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            ).replace(
                Regex(
                    "\\s*\\((?:official\\s*)?(?:music\\s*)?(?:video|mv|lyrics?|audio|visualizer|live|remaster(?:ed)?|version|edit|mix|remix)[^)]*\\)",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            ).replace(
                Regex(
                    "\\s*-\\s*(?:official\\s*)?(?:music\\s*)?(?:video|mv|lyrics?|audio|visualizer|live|remaster(?:ed)?|version|edit|mix|remix)\\b.*$",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            ).replace(Regex("\\s+"), " ")
            .trim()

    return stripped
        .trim('-')
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun normalizeCanvasArtistName(raw: String): String {
    val first =
        raw
            .split(
                Regex(
                    "(?:\\s*,\\s*|\\s*&\\s*|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)",
                    RegexOption.IGNORE_CASE,
                ),
                limit = 2,
            ).firstOrNull()
            .orEmpty()

    return first.replace(Regex("\\s+"), " ").trim()
}
