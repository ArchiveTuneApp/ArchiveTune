/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import moe.rukamori.archivetune.R

class PlaybackCapsuleWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            PlaybackCapsuleContent(context)
        }
    }
}

@Composable
private fun PlaybackCapsuleContent(context: Context) {
    val prefs = currentState<Preferences>()
    val state = prefs.toWidgetPlaybackState(context)

    GlanceTheme(
        colors =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                GlanceTheme.colors
            } else {
                ArchiveTuneWidgetColors.providers
            },
    ) {
        val palette = rememberWidgetPalette(state.dominantColor)
        val size = LocalSize.current
        val compact = size.width < 292.dp || size.height < 82.dp

        Box(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .background(palette.surface)
                    .cornerRadius(30.dp)
                    .padding(if (compact) 6.dp else 8.dp)
                    .clickable(openArchiveTuneAction(context)),
        ) {
            PlaybackCapsuleLayout(
                state = state,
                palette = palette,
                context = context,
                compact = compact,
            )
        }
    }
}

@Composable
private fun PlaybackCapsuleLayout(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
    compact: Boolean,
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            modifier =
                GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val artworkSize = if (compact) 50.dp else 58.dp
            WidgetArtwork(
                artPath = state.artPath,
                context = context,
                contentDescription = context.getString(R.string.album_cover_desc),
                targetSize = artworkSize,
                cornerRadius = if (compact) 17.dp else 20.dp,
                palette = palette,
                modifier = GlanceModifier.size(artworkSize),
            )

            Spacer(GlanceModifier.width(if (compact) 10.dp else 12.dp))

            Column(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Text(
                    text = state.title,
                    style =
                        TextStyle(
                            color = palette.onSurface,
                            fontSize = if (compact) 14.sp else 16.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = state.artist,
                    style =
                        TextStyle(
                            color = palette.onSurfaceVariant,
                            fontSize = if (compact) 12.sp else 13.sp,
                        ),
                    maxLines = 1,
                )
            }

            if (state.isAvailable) {
                Spacer(GlanceModifier.width(if (compact) 8.dp else 10.dp))
                WidgetControlButton(
                    modifier = GlanceModifier.size(if (compact) 50.dp else 56.dp),
                    action = playPauseAction(),
                    icon = if (state.isPlaying) R.drawable.pause else R.drawable.play,
                    contentDescription =
                        context.getString(
                            if (state.isPlaying) R.string.widget_pause else R.string.play,
                        ),
                    backgroundColor = palette.primaryContainer,
                    contentColor = palette.onPrimaryContainer,
                    cornerRadius = if (state.isPlaying) 15.dp else 28.dp,
                    iconSize = if (compact) 24.dp else 28.dp,
                )
            }
        }

        if (state.isAvailable && state.playbackPosition > 0f) {
            LinearProgressIndicator(
                progress = state.playbackPosition,
                color = palette.progress,
                backgroundColor = palette.progressTrack,
                modifier =
                    GlanceModifier
                        .fillMaxWidth()
                        .height(if (compact) 5.dp else 6.dp)
                        .cornerRadius(3.dp),
            )
        }
    }
}
