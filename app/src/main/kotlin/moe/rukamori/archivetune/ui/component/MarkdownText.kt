/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.network.OkHttpNetworkSchemeHandler
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.simple.ext.SimpleExtPlugin
import moe.rukamori.archivetune.R

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val context = LocalContext.current
    val colorArgb = color.toArgb()
    val fontSize = style.fontSize

    val typeface =
        remember(context) {
            ResourcesCompat.getFont(context, R.font.poppins)
        }

    val markwon =
        remember(context) {
            Markwon
                .builder(context)
                .usePlugin(
                    ImagesPlugin.create { plugin ->
                        plugin.addSchemeHandler(OkHttpNetworkSchemeHandler.create())
                    },
                ).usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(context))
                .usePlugin(TaskListPlugin.create(context))
                .usePlugin(HtmlPlugin.create())
                .usePlugin(LinkifyPlugin.create())
                .usePlugin(SimpleExtPlugin.create())
                .build()
        }

    val spanned =
        remember(markdown, markwon) {
            markwon.toMarkdown(markdown)
        }

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { textView ->
            textView.setTextColor(colorArgb)
            if (fontSize.isSp) {
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.value)
            }
            typeface?.let { textView.typeface = it }
            markwon.setParsedMarkdown(textView, spanned)
        },
        modifier = modifier,
    )
}
