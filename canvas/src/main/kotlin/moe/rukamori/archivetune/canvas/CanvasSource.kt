/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.canvas

import kotlinx.serialization.Serializable

@Serializable
enum class CanvasSource {
    BETTER_LYRICS,
    APPLE_MUSIC,
    BOTH;

    fun accepts(provider: CanvasSource?): Boolean =
        provider != null && provider != BOTH && (this == BOTH || this == provider)
}
