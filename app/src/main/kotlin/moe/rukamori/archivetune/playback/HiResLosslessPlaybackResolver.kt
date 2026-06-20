/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import moe.rukamori.archivetune.innertube.NewPipeUtils
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.utils.YTPlayerUtils

object HiResLosslessPlaybackResolver {
    data class TrackIdentity(
        val title: String,
        val artists: List<String>,
        val durationSeconds: Int?,
    )

    fun resolve(identity: TrackIdentity): Result<YTPlayerUtils.PlaybackData> =
        NewPipeUtils
            .getHiResLosslessAudioStream(
                NewPipeUtils.ExternalAudioQuery(
                    title = identity.title,
                    artists = identity.artists,
                    durationSeconds = identity.durationSeconds,
                ),
            ).map { stream -> stream.toPlaybackData() }

    private fun NewPipeUtils.ExternalAudioStream.toPlaybackData(): YTPlayerUtils.PlaybackData {
        val resolvedBitrate = maxOf(bitrate, averageBitrate, estimatedBitrate)
        val resolvedItag =
            itag.takeIf { it > 0 }
                ?: when (source) {
                    NewPipeUtils.ExternalAudioService.BANDCAMP -> BANDCAMP_EXTERNAL_ITAG
                    NewPipeUtils.ExternalAudioService.SOUNDCLOUD -> SOUNDCLOUD_EXTERNAL_ITAG
                }

        return YTPlayerUtils.PlaybackData(
            audioConfig = null,
            videoDetails = null,
            playbackTracking = null,
            format =
                PlayerResponse.StreamingData.Format(
                    itag = resolvedItag,
                    url = streamUrl,
                    mimeType = mimeType,
                    bitrate = resolvedBitrate,
                    width = null,
                    height = null,
                    contentLength = null,
                    quality = quality ?: source.name,
                    fps = null,
                    qualityLabel = null,
                    averageBitrate = maxOf(averageBitrate, estimatedBitrate).takeIf { it > 0 },
                    audioQuality = source.name,
                    approxDurationMs = durationSeconds.takeIf { it > 0L }?.times(1000L)?.toString(),
                    audioSampleRate = null,
                    audioChannels = null,
                    loudnessDb = null,
                    lastModified = null,
                    signatureCipher = null,
                    cipher = null,
                ),
            streamUrl = streamUrl,
            streamExpiresInSeconds = EXTERNAL_STREAM_CACHE_SECONDS,
            authFingerprint = EXTERNAL_AUTH_FINGERPRINT,
        )
    }

    const val EXTERNAL_AUTH_FINGERPRINT = "external:hi-res-lossless"

    private const val BANDCAMP_EXTERNAL_ITAG = -7001
    private const val SOUNDCLOUD_EXTERNAL_ITAG = -7002
    private const val EXTERNAL_STREAM_CACHE_SECONDS = 900
}
