/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.utils

import moe.rukamori.archivetune.constants.ThumbnailQuality

private const val PlayerArtworkHighResPx = 1080

private val wHPathRegex = Regex("w\\d+-h\\d+")
private val wHParamRegex = Regex("=w(\\d+)-h(\\d+)")
private val sParamRegex = Regex("=s(\\d+)")
private val brokenSAppendRegex = Regex("-s\\d+")

fun String.resize(
    width: Int? = null,
    height: Int? = null,
): String {
    if (width == null && height == null) return this

    val isGoogleCdn = contains("googleusercontent.com") || contains("ggpht.com")
    val isYtimg = contains("i.ytimg.com")

    if (isGoogleCdn) {
        val w = width ?: height!!
        val h = height ?: width!!

        if (wHPathRegex.containsMatchIn(this)) {
            return replace(wHPathRegex, "w$w-h$h")
        }

        wHParamRegex.find(this)?.let {
            return "${split("=w")[0]}=w$w-h$h-p-l90-rj"
        }

        sParamRegex.find(this)?.let { match ->
            val before = substring(0, match.range.first)
            val after = substring(match.range.last + 1)
            return "$before=s${maxOf(w, h)}${after.replace(brokenSAppendRegex, "")}"
        }

        return this
    }

    if (isYtimg) {
        return this
    }

    return this
}

fun String.highRes(): String = resize(PlayerArtworkHighResPx, PlayerArtworkHighResPx)

private val ytQualityChain = { videoId: String ->
    listOf(
        "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
        "https://i.ytimg.com/vi/$videoId/mqdefault.jpg",
        "https://i.ytimg.com/vi/$videoId/default.jpg",
    )
}

/**
 * Returns YouTube video thumbnail URLs for the given quality.
 * MAX quality prepends maxresdefault.jpg (best resolution but may not exist for all videos),
 * then falls through hqdefault → mqdefault → default.
 * Other qualities start directly at their level: HQ→hqdefault, MQ→mqdefault, DEFAULT→default.
 */
fun videoIdToYouTubeThumbnails(
    videoId: String,
    quality: ThumbnailQuality = ThumbnailQuality.HQ,
): List<String> {
    val chain = ytQualityChain(videoId)
    return when (quality) {
        ThumbnailQuality.MAX -> listOf("https://i.ytimg.com/vi/$videoId/maxresdefault.jpg") + chain
        ThumbnailQuality.HQ -> chain
        ThumbnailQuality.MQ -> chain.drop(1)
        ThumbnailQuality.DEFAULT -> chain.drop(2)
    }
}
