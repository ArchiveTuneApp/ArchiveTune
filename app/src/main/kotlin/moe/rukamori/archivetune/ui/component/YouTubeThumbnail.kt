package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import moe.rukamori.archivetune.R

private val ytVideoIdRegex = Regex("/vi/([^/]+)/")
private val resolvedThumbnailIndex = mutableMapOf<String, Int>()

@Composable
fun YTFallbackImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    widthPx: Int? = null,
    heightPx: Int? = null,
) {
    val context = LocalContext.current
    val videoId = url?.let { ytVideoIdRegex.find(it)?.groupValues?.getOrNull(1) }
    if (videoId != null) {
        val urls = remember(videoId) {
            listOf(
                "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
                "https://i.ytimg.com/vi/$videoId/mqdefault.jpg",
            )
        }
        val cachedIndex = resolvedThumbnailIndex.getOrElse(videoId) { 0 }
        var urlIndex by remember(videoId) { mutableStateOf(cachedIndex) }

        if (urlIndex < urls.size) {
            val request = remember(urls[urlIndex], widthPx, heightPx) {
                val builder = ImageRequest.Builder(context)
                    .data(urls[urlIndex])
                    .allowHardware(true)
                if (widthPx != null && heightPx != null) {
                    builder.size(widthPx, heightPx)
                }
                builder.build()
            }
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = modifier,
                onError = { urlIndex++ },
                onSuccess = { resolvedThumbnailIndex[videoId] = urlIndex },
            )
        } else {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.about_splash),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(48.dp),
                )
            }
        }
    } else {
        val request = remember(url, widthPx, heightPx) {
            val builder = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(true)
            if (widthPx != null && heightPx != null) {
                builder.size(widthPx, heightPx)
            }
            builder.build()
        }
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}
