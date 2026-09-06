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
