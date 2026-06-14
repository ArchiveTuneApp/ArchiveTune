/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.utils

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
        if (wHPathRegex.containsMatchIn(this)) {
            val replacement = buildString {
                if (width != null) append("w$width")
                if (width != null && height != null) append("-")
                if (height != null) append("h$height")
            }
            return replace(wHPathRegex, replacement)
        }

        wHParamRegex.find(this)?.let {
            val base = split("=w")[0]
            val param = when {
                width != null && height != null -> "${base}=w$width-h$height-p-l90-rj"
                width != null -> "${base}=w$width-p-l90-rj"
                height != null -> "${base}=h$height-p-l90-rj"
                else -> return this
            }
            return param
        }

        sParamRegex.find(this)?.let { match ->
            val before = substring(0, match.range.first)
            val after = substring(match.range.last + 1)
            val size = when {
                width != null && height != null -> maxOf(width, height)
                width != null -> width
                height != null -> height
                else -> return this
            }
            return "${before}=s$size${after.replace(brokenSAppendRegex, "")}"
        }

        return this
    }

    if (isYtimg) {
        return this
    }

    return this
}

fun String.highRes(): String = resize(width = PlayerArtworkHighResPx)
