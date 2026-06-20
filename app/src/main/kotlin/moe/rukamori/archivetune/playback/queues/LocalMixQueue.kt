/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.queues

import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.MediaMetadata

class LocalMixQueue(
    private val database: MusicDatabase,
    private val playlistId: String,
    private val maxMixSize: Int = 500,
) : Queue {
    override val preloadItem: MediaMetadata? = null

    override suspend fun getInitialStatus(): Queue.Status =
        withContext(Dispatchers.IO) {
            val playlistSongEntities = database.playlistSongs(playlistId).first()
            val playlistSongIds = playlistSongEntities.map { it.map.songId }

            val relatedSongs =
                playlistSongIds.flatMap { songId ->
                    database.relatedSongs(songId)
                }
            val uniqueRelated = relatedSongs.filter { song -> song.id !in playlistSongIds }.distinctBy { it.id }
            val finalMix = uniqueRelated.take(maxMixSize)

            if (finalMix.isNotEmpty()) {
                return@withContext Queue.Status(
                    title = "Mix from Playlist",
                    items = finalMix.map { it.toMediaItem() },
                    mediaItemIndex = 0,
                )
            }

            val seedSongId =
                playlistSongIds.firstOrNull()
                    ?: return@withContext Queue.Status(title = "Mix from Playlist", items = emptyList(), mediaItemIndex = 0)

            val nextResult = YouTube.next(WatchEndpoint(videoId = seedSongId)).getOrNull()
            val fromNext =
                nextResult
                    ?.items
                    ?.map { it.toMediaItem() }
                    ?.filter { it.mediaId !in playlistSongIds }
                    .orEmpty()

            val fromRelated =
                nextResult
                    ?.relatedEndpoint
                    ?.let { endpoint ->
                        YouTube
                            .related(endpoint)
                            .getOrNull()
                            ?.songs
                            ?.map { it.toMediaItem() }
                            ?.filter { it.mediaId !in playlistSongIds }
                    }.orEmpty()

            val onlineMix =
                (fromNext + fromRelated)
                    .distinctBy { it.mediaId }
                    .take(maxMixSize)

            Queue.Status(
                title = "Mix from Playlist",
                items = onlineMix,
                mediaItemIndex = 0,
            )
        }

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage(): List<MediaItem> = emptyList()
}
