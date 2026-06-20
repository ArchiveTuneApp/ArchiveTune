/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
@Immutable
data class NewsItem(
    @SerialName("id") val id: String = "",
    @SerialName("Title") val title: String,
    @SerialName("Description") val description: String = "",
    @SerialName("ImageURL")
    @Serializable(with = NewsImageUrlsSerializer::class)
    val imageUrls: List<String> = emptyList(),
    @SerialName("Important") val important: Boolean = false,
    @SerialName("Author") val author: String,
    @SerialName("Date") val timestamp: Long = 0L,
) {
    val stableKey: String
        get() = id.ifEmpty { "$timestamp|$author|$title" }
}

object NewsImageUrlsSerializer : KSerializer<List<String>> {
    private val delegate = ListSerializer(String.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<String> {
        val jsonDecoder = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)

        return when (val element = jsonDecoder.decodeJsonElement()) {
            JsonNull -> {
                emptyList()
            }

            is JsonArray -> {
                element.mapNotNull { item ->
                    (item as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                }
            }

            is JsonPrimitive -> {
                element.contentOrNull
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(::listOf)
                    ?: emptyList()
            }

            else -> {
                emptyList()
            }
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: List<String>,
    ) {
        delegate.serialize(encoder, value)
    }
}
