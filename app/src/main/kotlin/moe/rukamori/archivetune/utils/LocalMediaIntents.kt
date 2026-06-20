/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import java.util.Locale

fun String.isLocalMediaId(): Boolean =
    runCatching {
        when (toUri().scheme?.lowercase(Locale.US)) {
            "content", "file", "android.resource" -> true
            else -> false
        }
    }.getOrDefault(false)

fun shareLocalAudio(
    context: Context,
    mediaId: String,
    mimeType: String? = null,
): Boolean {
    val uri = mediaId.toUri()
    val scheme = uri.scheme?.lowercase(Locale.US)
    if (scheme != "content" && scheme != "android.resource") return false

    val shareIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType?.takeIf(String::isNotBlank) ?: "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(shareIntent, null))
    return true
}
