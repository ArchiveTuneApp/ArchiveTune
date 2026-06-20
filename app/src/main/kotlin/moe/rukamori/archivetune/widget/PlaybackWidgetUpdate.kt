/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.widget

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.playback.MusicService
import java.util.concurrent.TimeUnit

internal suspend fun requestPlaybackWidgetUpdate(context: Context) {
    try {
        val token =
            SessionToken(
                context,
                ComponentName(context, MusicService::class.java),
            )
        val future = MediaController.Builder(context, token).buildAsync()
        val controller =
            withContext(Dispatchers.IO) {
                future.get(2, TimeUnit.SECONDS)
            }

        try {
            val serviceIntent = android.content.Intent(context, MusicService::class.java)
            context.bindService(
                serviceIntent,
                object : android.content.ServiceConnection {
                    override fun onServiceConnected(
                        name: ComponentName?,
                        binder: android.os.IBinder?,
                    ) {
                        val service = (binder as? MusicService.MusicBinder)?.service
                        service?.updateWidget()
                        runCatching { context.unbindService(this) }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) = Unit
                },
                Context.BIND_AUTO_CREATE,
            )
        } finally {
            controller.release()
        }
    } catch (_: Exception) {
    }
}
