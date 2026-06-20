/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.LowDataModeKey
import moe.rukamori.archivetune.constants.PreloadQueueLyricsEnabledKey
import moe.rukamori.archivetune.constants.QueueLyricsPreloadCountKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.NetworkConnectivityObserver
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.isLowDataModeActive
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

/**
 * Manages pre-loading of lyrics for upcoming songs in the queue.
 * This improves user experience by having lyrics ready when songs change.
 */
class LyricsPreloadManager
    @Inject
    constructor(
        @ApplicationContext private val context: android.content.Context,
        private val database: MusicDatabase,
        private val networkConnectivity: NetworkConnectivityObserver,
        private val lyricsHelper: LyricsHelper,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var preloadJob: Job? = null

        // Track current queue to detect changes
        private var currentQueueIds: List<String> = emptyList()
        private var currentIndex: Int = -1

        /**
         * Called when the current song changes in the player.
         * Triggers pre-loading of lyrics for the next N songs in the queue.
         *
         * @param currentIndex The index of the currently playing song in the queue
         * @param queue List of MediaMetadata for songs in the queue
         */
        fun onSongChanged(
            currentIndex: Int,
            queue: List<MediaMetadata>,
        ) {
            // Cancel any existing preload job
            preloadJob?.cancel()

            // Check if pre-load is enabled
            scope.launch {
                try {
                    val preferences = context.dataStore.data.first()
                    val isEnabled = preferences[PreloadQueueLyricsEnabledKey] ?: true

                    if (!isEnabled) {
                        Log.d(TAG, "Queue lyrics pre-load is disabled")
                        return@launch
                    }

                    // Check network connectivity
                    val isNetworkAvailable =
                        try {
                            networkConnectivity.isCurrentlyConnected()
                        } catch (e: Exception) {
                            true
                        }

                    if (!isNetworkAvailable) {
                        Log.w(TAG, "Network unavailable, skipping lyrics pre-load")
                        return@launch
                    }

                    if (context.isLowDataModeActive(preferences[LowDataModeKey] ?: true)) {
                        Log.d(TAG, "Low Data Mode active, skipping lyrics pre-load")
                        return@launch
                    }

                    val preloadCount = preferences[QueueLyricsPreloadCountKey] ?: DEFAULT_PRELOAD_COUNT

                    // Get next N songs after current index
                    val nextSongs = getNextSongs(queue, currentIndex, preloadCount)

                    if (nextSongs.isEmpty()) {
                        Log.d(TAG, "No songs to pre-load")
                        return@launch
                    }

                    Log.d(TAG, "Starting pre-load for ${nextSongs.size} songs")
                    preloadLyrics(nextSongs)
                } catch (e: Exception) {
                    reportException(e)
                }
            }
        }

        /**
         * Get the next N songs from the queue after the current index.
         */
        private fun getNextSongs(
            queue: List<MediaMetadata>,
            currentIndex: Int,
            count: Int,
        ): List<MediaMetadata> {
            if (queue.isEmpty() || currentIndex < 0) {
                return emptyList()
            }

            val startIndex = currentIndex + 1
            val endIndex = minOf(startIndex + count, queue.size)

            if (startIndex >= queue.size) {
                return emptyList()
            }

            return queue.subList(startIndex, endIndex)
        }

        /**
         * Pre-load lyrics for the given songs.
         * Uses parallel fetching with limited concurrency.
         */
        private fun preloadLyrics(songs: List<MediaMetadata>) {
            preloadJob =
                scope.launch {
                    try {
                        // Process songs with limited concurrency
                        songs.forEach { song ->
                            // Check if lyrics already exist in database
                            val existingLyrics = database.lyrics(song.id).first()
                            if (existingLyrics != null && existingLyrics.lyrics != LyricsEntity.LYRICS_NOT_FOUND) {
                                Log.d(TAG, "Lyrics already cached for: ${song.title}")
                                return@forEach
                            }

                            // Fetch lyrics for this song
                            try {
                                val lyrics = fetchLyricsForSong(song)
                                if (lyrics != null && lyrics != LyricsEntity.LYRICS_NOT_FOUND) {
                                    database.query {
                                        insertLyricsIfAbsent(
                                            id = song.id,
                                            lyrics = lyrics,
                                        )
                                    }
                                    Log.d(TAG, "Pre-loaded lyrics for: ${song.title}")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to pre-load lyrics for ${song.title}: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }
        }

        /**
         * Fetch lyrics for a single song using the LyricsHelper.
         * This is a simplified version that gets lyrics from enabled providers.
         */
        private suspend fun fetchLyricsForSong(song: MediaMetadata): String? =
            try {
                lyricsHelper.getLyrics(song, preferredProviderOnly = true)
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching lyrics for ${song.title}: ${e.message}")
                null
            }

        /**
         * Cancel any ongoing preload operations.
         */
        fun cancel() {
            preloadJob?.cancel()
            preloadJob = null
        }

        /**
         * Clean up resources when no longer needed.
         */
        fun destroy() {
            cancel()
            scope.cancel()
        }

        companion object {
            private const val TAG = "LyricsPreloadManager"
            private const val DEFAULT_PRELOAD_COUNT = 3
        }
    }
