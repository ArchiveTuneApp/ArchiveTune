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

class PlaybackSpotlightWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            PlaybackSpotlightContent(context)
        }
    }
}

@Composable
private fun PlaybackSpotlightContent(context: Context) {
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
        val minSide = if (size.width < size.height) size.width else size.height
        val compact = minSide < 142.dp
        val padding = if (compact) 5.dp else 8.dp

        Box(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .background(palette.surface)
                    .cornerRadius(30.dp)
                    .padding(padding)
                    .clickable(openArchiveTuneAction(context)),
        ) {
            WidgetArtwork(
                artPath = state.artPath,
                context = context,
                contentDescription = context.getString(R.string.album_cover_desc),
                targetSize = minSide,
                cornerRadius = if (compact) 24.dp else 26.dp,
                palette = palette,
                modifier = GlanceModifier.fillMaxSize(),
            )

            PlaybackSpotlightOverlay(
                state = state,
                palette = palette,
                context = context,
                compact = compact,
            )
        }
    }
}

@Composable
private fun PlaybackSpotlightOverlay(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
    compact: Boolean,
) {
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (compact) {
            if (state.isAvailable) {
                WidgetControlButton(
                    modifier =
                        GlanceModifier
                            .padding(bottom = 8.dp)
                            .size(54.dp),
                    action = playPauseAction(),
                    icon = if (state.isPlaying) R.drawable.pause else R.drawable.play,
                    contentDescription =
                        context.getString(
                            if (state.isPlaying) R.string.widget_pause else R.string.play,
                        ),
                    backgroundColor = palette.primaryContainer,
                    contentColor = palette.onPrimaryContainer,
                    cornerRadius = if (state.isPlaying) 15.dp else 27.dp,
                    iconSize = 27.dp,
                )
            } else {
                Box(
                    modifier =
                        GlanceModifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .height(40.dp)
                            .background(palette.secondaryContainer)
                            .cornerRadius(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.title,
                        style =
                            TextStyle(
                                color = palette.onSecondaryContainer,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        maxLines = 1,
                    )
                }
            }
        } else {
            Column(
                modifier =
                    GlanceModifier
                        .fillMaxWidth()
                        .background(palette.secondaryContainer)
                        .cornerRadius(24.dp)
                        .padding(8.dp),
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                        verticalAlignment = Alignment.Vertical.CenterVertically,
                    ) {
                        Text(
                            text = state.title,
                            style =
                                TextStyle(
                                    color = palette.onSecondaryContainer,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            maxLines = 1,
                        )
                        Spacer(GlanceModifier.height(2.dp))
                        Text(
                            text = state.artist,
                            style =
                                TextStyle(
                                    color = palette.onSecondaryContainer,
                                    fontSize = 11.sp,
                                ),
                            maxLines = 1,
                        )
                    }

                    if (state.isAvailable) {
                        Spacer(GlanceModifier.width(8.dp))
                        WidgetControlButton(
                            modifier = GlanceModifier.size(52.dp),
                            action = playPauseAction(),
                            icon = if (state.isPlaying) R.drawable.pause else R.drawable.play,
                            contentDescription =
                                context.getString(
                                    if (state.isPlaying) R.string.widget_pause else R.string.play,
                                ),
                            backgroundColor = palette.primaryContainer,
                            contentColor = palette.onPrimaryContainer,
                            cornerRadius = if (state.isPlaying) 14.dp else 26.dp,
                            iconSize = 26.dp,
                        )
                    }
                }

                if (state.isAvailable && state.playbackPosition > 0f) {
                    Spacer(GlanceModifier.height(7.dp))
                    LinearProgressIndicator(
                        progress = state.playbackPosition,
                        color = palette.progress,
                        backgroundColor = palette.progressTrack,
                        modifier =
                            GlanceModifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .cornerRadius(3.dp),
                    )
                }
            }
        }
    }
}
