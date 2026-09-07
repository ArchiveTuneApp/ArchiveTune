/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.sponsorblock

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToLong

class ObserveSponsorBlockSettingsUseCase
    @Inject
    constructor(
        private val repository: SponsorBlockRepository,
    ) {
        operator fun invoke(): Flow<SponsorBlockSettings> = repository.observeSettings()
    }

class SetSponsorBlockEnabledUseCase
    @Inject
    constructor(
        private val repository: SponsorBlockRepository,
    ) {
        suspend operator fun invoke(enabled: Boolean) {
            repository.setEnabled(enabled)
        }
    }

class SetSponsorBlockCategoriesUseCase
    @Inject
    constructor(
        private val repository: SponsorBlockRepository,
    ) {
        suspend operator fun invoke(categories: Set<SponsorBlockCategory>) {
            repository.setCategories(categories)
        }
    }

class SetSponsorBlockApiUrlUseCase
    @Inject
    constructor(
        private val repository: SponsorBlockRepository,
    ) {
        suspend operator fun invoke(apiUrl: String) {
            val normalizedUrl = requireNotNull(normalizeSponsorBlockApiUrl(apiUrl))
            repository.setApiUrl(normalizedUrl)
        }
    }

class ValidateSponsorBlockApiUrlUseCase
    @Inject
    constructor() {
        operator fun invoke(apiUrl: String): Boolean = normalizeSponsorBlockApiUrl(apiUrl) != null
    }

class GetSponsorBlockSegmentsUseCase
    @Inject
    constructor(
        private val repository: SponsorBlockRepository,
    ) {
        suspend operator fun invoke(
            videoId: String,
            settings: SponsorBlockSettings,
        ): List<SponsorBlockSegment> {
            if (
                !YOUTUBE_VIDEO_ID.matches(videoId) ||
                !settings.enabled ||
                settings.categories.isEmpty()
            ) {
                return emptyList()
            }

            val orderedCategories = SponsorBlockCategory.entries.filter(settings.categories::contains)
            val segments =
                repository
                    .fetchSegments(
                        videoId = videoId,
                        categories = orderedCategories,
                        apiUrl = settings.apiUrl,
                    ).mapNotNull { rawSegment -> rawSegment.toSegmentOrNull(settings.categories) }
                    .sortedWith(compareBy(SponsorBlockSegment::startMs, SponsorBlockSegment::endMs))

            if (segments.isEmpty()) return emptyList()

            val mergedSegments = ArrayList<SponsorBlockSegment>(segments.size)
            segments.forEach { segment ->
                val previous = mergedSegments.lastOrNull()
                if (previous != null && segment.startMs <= previous.endMs) {
                    mergedSegments[mergedSegments.lastIndex] =
                        previous.copy(endMs = max(previous.endMs, segment.endMs))
                } else {
                    mergedSegments += segment
                }
            }
            return mergedSegments
        }

        private fun SponsorBlockRawSegment.toSegmentOrNull(
            selectedCategories: Set<SponsorBlockCategory>,
        ): SponsorBlockSegment? {
            if (actionType != SKIP_ACTION_TYPE) return null
            val parsedCategory = SponsorBlockCategory.fromApiValue(category) ?: return null
            if (parsedCategory !in selectedCategories || segment.size < 2) return null

            val startSeconds = segment[0]
            val endSeconds = segment[1]
            if (
                !startSeconds.isFinite() ||
                !endSeconds.isFinite() ||
                startSeconds < 0.0 ||
                endSeconds <= startSeconds
            ) {
                return null
            }
            if (endSeconds > MAX_SUPPORTED_SECONDS) return null

            val startMs = (startSeconds * MILLIS_PER_SECOND).roundToLong().coerceAtLeast(0L)
            val endMs = (endSeconds * MILLIS_PER_SECOND).roundToLong()
            if (endMs - startMs < MIN_SEGMENT_DURATION_MILLIS) return null
            return SponsorBlockSegment(startMs = startMs, endMs = endMs)
        }

        private companion object {
            val YOUTUBE_VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
            const val SKIP_ACTION_TYPE = "skip"
            const val MILLIS_PER_SECOND = 1_000.0
            const val MIN_SEGMENT_DURATION_MILLIS = 100L
            const val MAX_SUPPORTED_SECONDS = Long.MAX_VALUE / 1_000.0
        }
    }
