/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.canvas

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class CanvasPlaybackRequest(
    val mediaId: String,
    val title: String,
    val artist: String,
    val storefront: String,
    val requireVertical: Boolean,
)

class CanvasPlaybackUseCase @Inject constructor(
    private val repository: CanvasArtworkRepository,
) {
    val policy = CanvasNetworkAccess.policy
    val revision = repository.revision
    val spotifyConnected = repository.spotifyConnected

    suspend fun load(request: CanvasPlaybackRequest, policy: CanvasPolicy): CanvasVideo? {
        if (!policy.ready || !policy.configuration.enabled) return null
        return repository.resolve(request, policy)?.let { artwork ->
            CanvasVideo(
                source = artwork.source ?: return null,
                static = artwork.static,
                animated = artwork.animated,
                videoUrl = artwork.videoUrl,
                animatedVertical = artwork.animatedVertical,
                videoUrlVertical = artwork.videoUrlVertical,
            )
        }
    }

    suspend fun refresh(request: CanvasPlaybackRequest): Boolean = coroutineScope {
        val current = policy.value
        if (!current.networkAllowed) return@coroutineScope false
        val work = async { repository.resolve(request, current, forceRefresh = true) != null }
        val watcher = launch {
            policy.first { it != current }
            work.cancel()
        }
        try {
            work.await()
        } finally {
            watcher.cancel()
        }
    }
}
