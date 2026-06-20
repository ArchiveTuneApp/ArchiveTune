/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ai

import moe.rukamori.archivetune.lyrics.LyricsUtils
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

data class AiLyricsSegment(
    val id: Int,
    val text: String,
)

sealed interface AiLyricsDocument {
    val formatName: String
    val segments: List<AiLyricsSegment>

    fun rebuild(translations: Map<Int, String>): String
}

object AiLyricsDocumentParser {
    fun parse(rawLyrics: String): AiLyricsDocument =
        if (LyricsUtils.isTtml(rawLyrics)) {
            parseTtml(rawLyrics).getOrElse { parseLineBased(rawLyrics, formatName = "TTML fallback") }
        } else {
            parseLineBased(
                rawLyrics = rawLyrics,
                formatName = if (rawLyrics.lineSequence().any { SyncedLineRegex.containsMatchIn(it) }) "synced LRC" else "plain text",
            )
        }

    private fun parseLineBased(
        rawLyrics: String,
        formatName: String,
    ): AiLyricsDocument {
        val lines = rawLyrics.split('\n')
        val templates = ArrayList<LineTemplate>(lines.size)
        val segments = ArrayList<AiLyricsSegment>()
        lines.forEach { line ->
            val syncedMatch = SyncedLineRegex.matchEntire(line)
            if (syncedMatch != null) {
                val content = syncedMatch.groupValues[3]
                val segmentId =
                    if (content.isBlank()) {
                        null
                    } else {
                        segments.size.also { segments.add(AiLyricsSegment(it, content.trim())) }
                    }
                templates.add(
                    LineTemplate(
                        prefix = syncedMatch.groupValues[1],
                        separator = syncedMatch.groupValues[2],
                        suffix = syncedMatch.groupValues[4],
                        segmentId = segmentId,
                        original = line,
                    ),
                )
            } else {
                val plainMatch = PlainLineRegex.matchEntire(line)
                val content = plainMatch?.groupValues?.get(2).orEmpty()
                val segmentId =
                    if (content.isBlank()) {
                        null
                    } else {
                        segments.size.also { segments.add(AiLyricsSegment(it, content.trim())) }
                    }
                templates.add(
                    LineTemplate(
                        prefix = plainMatch?.groupValues?.get(1).orEmpty(),
                        separator = "",
                        suffix = plainMatch?.groupValues?.get(3).orEmpty(),
                        segmentId = segmentId,
                        original = line,
                    ),
                )
            }
        }
        return LineBasedLyricsDocument(formatName = formatName, templates = templates, segments = segments)
    }

    private fun parseTtml(rawLyrics: String): Result<AiLyricsDocument> =
        runCatching {
            val factory =
                DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = true
                    runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
                    runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                    runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                    runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                }
            val document = factory.newDocumentBuilder().parse(InputSource(StringReader(rawLyrics)))
            val paragraphs = ArrayList<TtmlParagraph>()
            val segments = ArrayList<AiLyricsSegment>()
            val elements = document.getElementsByTagName("*")
            for (index in 0 until elements.length) {
                val element = elements.item(index) as? Element ?: continue
                if (!element.tagName.endsWith("p", ignoreCase = true)) continue
                val text = element.textContent?.trim().orEmpty()
                if (text.isBlank()) continue
                val segmentId = segments.size
                segments.add(AiLyricsSegment(segmentId, text))
                paragraphs.add(TtmlParagraph(element = element, segmentId = segmentId))
            }
            require(segments.isNotEmpty()) { "TTML has no translatable text" }
            TtmlLyricsDocument(
                document = document,
                originalHadDeclaration = rawLyrics.trimStart().startsWith("<?xml", ignoreCase = true),
                paragraphs = paragraphs,
                segments = segments,
            )
        }

    private val SyncedLineRegex = Regex("""^(\s*(?:\[[^\]]+])+)(\s*)(.*?)(\s*)$""")
    private val PlainLineRegex = Regex("""^(\s*)(.*?)(\s*)$""")
}

private data class LineTemplate(
    val prefix: String,
    val separator: String,
    val suffix: String,
    val segmentId: Int?,
    val original: String,
)

private data class LineBasedLyricsDocument(
    override val formatName: String,
    private val templates: List<LineTemplate>,
    override val segments: List<AiLyricsSegment>,
) : AiLyricsDocument {
    override fun rebuild(translations: Map<Int, String>): String =
        templates.joinToString("\n") { template ->
            val id = template.segmentId ?: return@joinToString template.original
            val translated = translations[id]?.trim().orEmpty()
            if (translated.isBlank()) {
                template.original
            } else {
                "${template.prefix}${template.separator}$translated${template.suffix}"
            }
        }
}

private data class TtmlParagraph(
    val element: Element,
    val segmentId: Int,
)

private data class TtmlLyricsDocument(
    private val document: Document,
    private val originalHadDeclaration: Boolean,
    private val paragraphs: List<TtmlParagraph>,
    override val segments: List<AiLyricsSegment>,
) : AiLyricsDocument {
    override val formatName: String = "TTML"

    override fun rebuild(translations: Map<Int, String>): String {
        paragraphs.forEach { paragraph ->
            val translated = translations[paragraph.segmentId]?.trim().orEmpty()
            if (translated.isBlank()) return@forEach
            val element = paragraph.element
            while (element.hasChildNodes()) {
                element.removeChild(element.firstChild)
            }
            element.appendChild(document.createTextNode(translated))
        }
        return transform(document, originalHadDeclaration)
    }

    private fun transform(
        document: Document,
        includeDeclaration: Boolean,
    ): String {
        val transformer =
            TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, if (includeDeclaration) "no" else "yes")
                setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            }
        val writer = StringWriter()
        transformer.transform(DOMSource(document), StreamResult(writer))
        return writer.toString()
    }
}
