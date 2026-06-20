/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

private const val YOUTUBE_MUSIC_PACKAGE = "com.google.android.apps.youtube.music"
private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
private const val YOUTUBE_MUSIC_HOME_URL = "https://music.youtube.com"

fun Context.openYouTubeMusicUrl(targetUrl: String): Boolean {
    val uri =
        targetUrl
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let(Uri::parse)
            ?: Uri.parse(YOUTUBE_MUSIC_HOME_URL)

    val baseIntent =
        Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    val externalResolvedIntent =
        packageManager
            .queryIntentActivities(baseIntent, PackageManager.MATCH_DEFAULT_ONLY)
            .asSequence()
            .mapNotNull { it.activityInfo }
            .firstOrNull { it.packageName != packageName }
            ?.let { activityInfo ->
                Intent(baseIntent).setClassName(activityInfo.packageName, activityInfo.name)
            }

    return sequenceOf(
        Intent(baseIntent).setPackage(YOUTUBE_MUSIC_PACKAGE),
        Intent(baseIntent).setPackage(YOUTUBE_PACKAGE),
        externalResolvedIntent,
    ).filterNotNull().any(::tryStartActivity)
}

private fun Context.tryStartActivity(intent: Intent): Boolean =
    try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
