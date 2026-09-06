/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.canvas.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import moe.rukamori.archivetune.canvas.CanvasSource

@Serializable
data class CanvasArtwork(
    val source: CanvasSource? = null,
    val name: String? = null,
    val artist: String? = null,
    @SerialName("albumId")
    val albumId: String? = null,
    val albumName: String? = null,
    val static: String? = null,
    val animated: String? = null,
    val animatedVertical: String? = null,
    val videoUrl: String? = null,
    val videoUrlVertical: String? = null,
) {
    val preferredAnimationUrl: String?
        get() = animated ?: videoUrl

    val preferredVerticalAnimationUrl: String?
        get() = animatedVertical ?: videoUrlVertical
}
