/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.sponsorblock

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SponsorBlockPlaybackController
    @Inject
    constructor(
        private val observeSettings: ObserveSponsorBlockSettingsUseCase,
        private val getSegments: GetSponsorBlockSegmentsUseCase,
    ) {
        private val currentMediaId = MutableStateFlow<String?>(null)
        private var attachedPlayer: Player? = null
        private var observationJob: Job? = null

        private val playerListener =
            object : Player.Listener {
                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    currentMediaId.value = mediaItem.normalizedMediaId()
                }

                override fun onTimelineChanged(
                    timeline: androidx.media3.common.Timeline,
                    reason: Int,
                ) {
                    currentMediaId.value = attachedPlayer?.currentMediaItem.normalizedMediaId()
                }
            }

        fun attach(
            player: Player,
            scope: CoroutineScope,
        ) {
            if (attachedPlayer === player && observationJob?.isActive == true) return
            detach()

            attachedPlayer = player
            currentMediaId.value = player.currentMediaItem.normalizedMediaId()
            player.addListener(playerListener)
            observationJob =
                scope.launch {
                    try {
                        combine(
                            observeSettings(),
                            currentMediaId,
                        ) { settings, mediaId -> settings to mediaId }
                            .collectLatest { (settings, mediaId) ->
                                val activeMediaId = mediaId ?: return@collectLatest
                                if (!settings.enabled || settings.categories.isEmpty()) return@collectLatest

                                val segments =
                                    try {
                                        getSegments(activeMediaId, settings)
                                    } catch (cancellation: CancellationException) {
                                        throw cancellation
                                    } catch (throwable: Throwable) {
                                        Timber
                                            .tag(TAG)
                                            .w(throwable, "SponsorBlock segment lookup failed")
                                        return@collectLatest
                                    }
                                if (segments.isEmpty() || currentMediaId.value != activeMediaId) {
                                    return@collectLatest
                                }

                                monitorPlayback(
                                    player = player,
                                    mediaId = activeMediaId,
                                    segments = segments,
                                )
                            }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (throwable: Throwable) {
                        Timber.tag(TAG).w(throwable, "SponsorBlock settings observation failed")
                    }
                }
        }

        fun detach() {
            observationJob?.cancel()
            observationJob = null
            attachedPlayer?.removeListener(playerListener)
            attachedPlayer = null
            currentMediaId.value = null
        }

        private suspend fun monitorPlayback(
            player: Player,
            mediaId: String,
            segments: List<SponsorBlockSegment>,
        ) {
            var lastSkippedEndMs: Long? = null
            while (
                currentCoroutineContext().isActive &&
                attachedPlayer === player &&
                player.currentMediaItem.normalizedMediaId() == mediaId
            ) {
                if (!player.isPlaying || player.playbackState != Player.STATE_READY) {
                    delay(INACTIVE_POLL_INTERVAL_MILLIS)
                    continue
                }

                val positionMs = player.currentPosition.coerceAtLeast(0L)
                val activeSegment =
                    segments.firstOrNull { segment ->
                        positionMs >= segment.startMs &&
                            positionMs < segment.endMs - END_TOLERANCE_MILLIS
                    }
                if (activeSegment == null) {
                    lastSkippedEndMs = null
                } else if (lastSkippedEndMs != activeSegment.endMs) {
                    lastSkippedEndMs = activeSegment.endMs
                    val knownDuration =
                        player.duration.takeIf { duration ->
                            duration != C.TIME_UNSET && duration > 0L
                        }
                    val targetPositionMs =
                        knownDuration?.let { duration -> activeSegment.endMs.coerceAtMost(duration) }
                            ?: activeSegment.endMs
                    if (targetPositionMs - positionMs >= MIN_SEEK_DISTANCE_MILLIS) {
                        player.seekTo(targetPositionMs)
                    }
                }
                delay(ACTIVE_POLL_INTERVAL_MILLIS)
            }
        }

        private fun MediaItem?.normalizedMediaId(): String? =
            this
                ?.mediaId
                ?.trim()
                ?.takeIf(String::isNotEmpty)

        private companion object {
            const val TAG = "SponsorBlock"
            const val ACTIVE_POLL_INTERVAL_MILLIS = 200L
            const val INACTIVE_POLL_INTERVAL_MILLIS = 750L
            const val END_TOLERANCE_MILLIS = 50L
            const val MIN_SEEK_DISTANCE_MILLIS = 100L
        }
    }
