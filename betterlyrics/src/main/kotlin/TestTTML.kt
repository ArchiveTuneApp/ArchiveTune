/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

import moe.rukamori.archivetune.betterlyrics.TTMLParser

fun main(args: Array<String>) {
    val ttml =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <tt xmlns="http://www.w3.org/ns/ttml">
          <body>
            <div>
              <p begin="00:00:01.000" end="00:00:05.000">
                <span begin="00:00:01.000" end="00:00:02.000">mi</span>
                <span begin="00:00:02.000" end="00:00:03.000">ne,</span>
              </p>
            </div>
          </body>
        </tt>
        """.trimIndent()

    val lines = TTMLParser.parseTTML(ttml)
    lines.forEach { line ->
        println("Line: '${line.text}'")
        line.words.forEach { word ->
            println("  Word: '${word.text}' (bg=${word.isBackground}) [${word.startTime} -> ${word.endTime}]")
        }
    }
}
