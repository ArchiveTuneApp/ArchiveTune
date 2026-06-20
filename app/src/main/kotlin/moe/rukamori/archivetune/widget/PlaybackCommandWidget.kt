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

class PlaybackCommandWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            PlaybackCommandContent(context)
        }
    }
}

@Composable
private fun PlaybackCommandContent(context: Context) {
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
        val compact = size.width < 240.dp || size.height < 124.dp

        Box(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .background(palette.surface)
                    .cornerRadius(30.dp)
                    .padding(if (compact) 10.dp else 14.dp)
                    .clickable(openArchiveTuneAction(context)),
        ) {
            if (compact) {
                PlaybackCommandCompact(state = state, palette = palette, context = context)
            } else {
                PlaybackCommandPanel(state = state, palette = palette, context = context)
            }
        }
    }
}

@Composable
private fun PlaybackCommandCompact(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetArtwork(
            artPath = state.artPath,
            context = context,
            contentDescription = context.getString(R.string.album_cover_desc),
            targetSize = 58.dp,
            cornerRadius = 19.dp,
            palette = palette,
            modifier = GlanceModifier.size(58.dp),
        )

        Spacer(GlanceModifier.width(12.dp))

        Column(
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = state.title,
                style =
                    TextStyle(
                        color = palette.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                maxLines = 1,
            )
            Text(
                text = state.artist,
                style =
                    TextStyle(
                        color = palette.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                maxLines = 1,
            )
        }

        if (state.isAvailable) {
            Spacer(GlanceModifier.width(10.dp))
            WidgetControlButton(
                modifier = GlanceModifier.size(56.dp),
                action = playPauseAction(),
                icon = if (state.isPlaying) R.drawable.pause else R.drawable.play,
                contentDescription =
                    context.getString(
                        if (state.isPlaying) R.string.widget_pause else R.string.play,
                    ),
                backgroundColor = palette.primaryContainer,
                contentColor = palette.onPrimaryContainer,
                cornerRadius = if (state.isPlaying) 16.dp else 28.dp,
                iconSize = 28.dp,
            )
        }
    }
}

@Composable
private fun PlaybackCommandPanel(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetArtwork(
                artPath = state.artPath,
                context = context,
                contentDescription = context.getString(R.string.album_cover_desc),
                targetSize = 64.dp,
                cornerRadius = 20.dp,
                palette = palette,
                modifier = GlanceModifier.size(64.dp),
            )

            Spacer(GlanceModifier.width(12.dp))

            Column(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Text(
                    text = state.title,
                    style =
                        TextStyle(
                            color = palette.onSurface,
                            fontSize = 17.sp,
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
                            fontSize = 13.sp,
                        ),
                    maxLines = 1,
                )
            }
        }

        Spacer(GlanceModifier.defaultWeight())

        if (state.isAvailable) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WidgetControlButton(
                    modifier = GlanceModifier.size(52.dp),
                    action = skipPreviousAction(),
                    icon = R.drawable.skip_previous,
                    contentDescription = context.getString(R.string.widget_previous),
                    backgroundColor = palette.secondaryContainer,
                    contentColor = palette.onSecondaryContainer,
                    cornerRadius = 26.dp,
                )
                Spacer(GlanceModifier.width(8.dp))
                WidgetControlButton(
                    modifier =
                        GlanceModifier
                            .defaultWeight()
                            .height(60.dp),
                    action = playPauseAction(),
                    icon = if (state.isPlaying) R.drawable.pause else R.drawable.play,
                    contentDescription =
                        context.getString(
                            if (state.isPlaying) R.string.widget_pause else R.string.play,
                        ),
                    backgroundColor = palette.primaryContainer,
                    contentColor = palette.onPrimaryContainer,
                    cornerRadius = if (state.isPlaying) 18.dp else 30.dp,
                    iconSize = 32.dp,
                )
                Spacer(GlanceModifier.width(8.dp))
                WidgetControlButton(
                    modifier = GlanceModifier.size(52.dp),
                    action = skipNextAction(),
                    icon = R.drawable.skip_next,
                    contentDescription = context.getString(R.string.next),
                    backgroundColor = palette.secondaryContainer,
                    contentColor = palette.onSecondaryContainer,
                    cornerRadius = 26.dp,
                )
            }

            if (state.playbackPosition > 0f) {
                Spacer(GlanceModifier.height(10.dp))
                LinearProgressIndicator(
                    progress = state.playbackPosition,
                    color = palette.progress,
                    backgroundColor = palette.progressTrack,
                    modifier =
                        GlanceModifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .cornerRadius(3.dp),
                )
            }
        } else {
            Box(
                modifier =
                    GlanceModifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .background(palette.secondaryContainer)
                        .cornerRadius(27.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = context.getString(R.string.widget_tap_to_open),
                    style =
                        TextStyle(
                            color = palette.onSecondaryContainer,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    maxLines = 1,
                )
            }
        }
    }
}
