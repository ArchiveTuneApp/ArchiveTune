/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.booleanPreferencesKey
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import moe.rukamori.archivetune.constants.MobileThumbnailQualityKey
import moe.rukamori.archivetune.constants.ThumbnailQuality
import moe.rukamori.archivetune.constants.WifiThumbnailQualityKey
import moe.rukamori.archivetune.ui.utils.videoIdToYouTubeThumbnails
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
internal fun rememberOfflineArtworkImageRequest(
    imageUrl: String?,
    videoId: String? = null,
): ImageRequest? {
    val context = LocalContext.current
    val (forceCpuRendering) =
        rememberPreference(
            key = booleanPreferencesKey("force_cpu_rendering"),
            defaultValue = true,
        )
    val (wifiQuality) =
        rememberEnumPreference(
            WifiThumbnailQualityKey,
            defaultValue = ThumbnailQuality.MAX,
        )
    val (mobileQuality) =
        rememberEnumPreference(
            MobileThumbnailQualityKey,
            defaultValue = ThumbnailQuality.HQ,
        )
    return remember(context, imageUrl, videoId, forceCpuRendering, wifiQuality, mobileQuality) {
        if (videoId != null) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val isWifi = cm?.activeNetwork?.let { network ->
                cm.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            } ?: false
            val quality = if (isWifi) wifiQuality else mobileQuality
            val ytUrls = videoIdToYouTubeThumbnails(videoId, quality)
            val primaryUrl = ytUrls.getOrNull(0) ?: return@remember null

            ImageRequest
                .Builder(context)
                .data(primaryUrl)
                .memoryCacheKey(primaryUrl)
                .diskCacheKey(primaryUrl)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .apply { if (forceCpuRendering && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) allowHardware(false) }
                .build()
        } else {
            imageUrl
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let { url ->
                    ImageRequest
                        .Builder(context)
                        .data(url)
                        .memoryCacheKey(url)
                        .diskCacheKey(url)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .apply { if (forceCpuRendering && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) allowHardware(false) }
                        .build()
                }
        }
    }
}
