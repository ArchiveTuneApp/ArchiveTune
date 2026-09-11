/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.sponsorblock

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI

const val DEFAULT_SPONSOR_BLOCK_API_URL = "https://sponsor.ajay.app"

enum class SponsorBlockCategory(
    val apiValue: String,
) {
    MUSIC_OFF_TOPIC("music_offtopic"),
    SPONSOR("sponsor"),
    INTRO("intro"),
    OUTRO("outro"),
    SELF_PROMOTION("selfpromo"),
    PREVIEW("preview"),
    FILLER("filler"),
    INTERACTION("interaction"),
    HOOK("hook"),
    ;

    companion object {
        fun fromApiValue(value: String): SponsorBlockCategory? = entries.firstOrNull { it.apiValue == value }
    }
}

data class SponsorBlockSettings(
    val enabled: Boolean,
    val categories: Set<SponsorBlockCategory>,
    val apiUrl: String,
) {
    companion object {
        val Default =
            SponsorBlockSettings(
                enabled = true,
                categories = SponsorBlockCategory.entries.toSet(),
                apiUrl = DEFAULT_SPONSOR_BLOCK_API_URL,
            )
    }
}

data class SponsorBlockSegment(
    val startMs: Long,
    val endMs: Long,
)

internal data class SponsorBlockRawSegment(
    val segment: List<Double>,
    val category: String,
    val actionType: String,
)

@Serializable
internal data class SponsorBlockVideoResponse(
    @SerialName("videoID") val videoId: String,
    val segments: List<SponsorBlockSegmentResponse> = emptyList(),
)

@Serializable
internal data class SponsorBlockSegmentResponse(
    val segment: List<Double> = emptyList(),
    val category: String = "",
    val actionType: String = "",
)

internal fun normalizeSponsorBlockApiUrl(value: String): String? {
    val candidate = value.trim().ifEmpty { DEFAULT_SPONSOR_BLOCK_API_URL }
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    if (uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null) return null
    return candidate.trimEnd('/')
}
