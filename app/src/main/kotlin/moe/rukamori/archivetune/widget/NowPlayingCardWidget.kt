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
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.layout.fillMaxHeight
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

/**
 * Now Playing Card widget — a landscape 4×2 card with a full-height artwork column,
 * bold track metadata, and an expressive pill control group where the primary play/pause
 * action stretches to fill available width inside a unified secondaryContainer pill shell.
 *
 * Hierarchy:
 *   PRIMARY  → play/pause (width-dominant pill, shape morphs on state change)
 *   SECONDARY → prev / next (transparent icon buttons inside the pill)
 *   PASSIVE  → title, artist, progress
 */
class NowPlayingCardWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            NowPlayingCardContent(context)
        }
    }
}

@Composable
private fun NowPlayingCardContent(context: Context) {
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

        Box(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .background(palette.surface)
                    .cornerRadius(28.dp)
                    .padding(12.dp)
                    .clickable(openArchiveTuneAction(context)),
        ) {
            if (size.height < 108.dp) {
                NowPlayingCardBar(state = state, palette = palette, context = context)
            } else {
                NowPlayingCardPanel(
                    state = state,
                    palette = palette,
                    context = context,
                    size = size,
                )
            }
        }
    }
}

// ─── Compact bar layout (single row) ─────────────────────────────────────────

@Composable
private fun NowPlayingCardBar(
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
            targetSize = 46.dp,
            cornerRadius = 14.dp,
            palette = palette,
            modifier = GlanceModifier.size(46.dp),
        )

        Spacer(GlanceModifier.width(10.dp))

        Column(
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = state.title,
                style =
                    TextStyle(
                        color = palette.onSurface,
                        fontSize = 14.sp,
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
            Spacer(GlanceModifier.width(8.dp))
            WidgetControlButton(
                modifier = GlanceModifier.size(46.dp),
                action = playPauseAction(),
                icon = if (state.isPlaying) R.drawable.pause else R.drawable.play,
                contentDescription =
                    context.getString(
                        if (state.isPlaying) R.string.widget_pause else R.string.play,
                    ),
                backgroundColor = palette.primaryContainer,
                contentColor = palette.onPrimaryContainer,
                cornerRadius = if (state.isPlaying) 13.dp else 23.dp,
                iconSize = 22.dp,
            )
        }
    }
}

// ─── Full card layout (artwork column + info/controls column) ─────────────────

@Composable
private fun NowPlayingCardPanel(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
    size: DpSize,
) {
    val artworkSize = (size.height - 24.dp).coerceIn(72.dp, 160.dp)

    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetArtwork(
            artPath = state.artPath,
            context = context,
            contentDescription = context.getString(R.string.album_cover_desc),
            targetSize = artworkSize,
            cornerRadius = 18.dp,
            palette = palette,
            modifier = GlanceModifier.size(artworkSize),
        )

        Spacer(GlanceModifier.width(14.dp))

        Column(
            modifier =
                GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight(),
        ) {
            Text(
                text = state.title,
                style =
                    TextStyle(
                        color = palette.onSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                maxLines = 2,
            )

            Spacer(GlanceModifier.height(3.dp))

            Text(
                text = state.artist,
                style =
                    TextStyle(
                        color = palette.onSurfaceVariant,
                        fontSize = 13.sp,
                    ),
                maxLines = 1,
            )

            Spacer(GlanceModifier.defaultWeight())

            if (state.isAvailable) {
                WidgetExpressiveControlPill(state = state, palette = palette, context = context)
            } else {
                Box(
                    modifier =
                        GlanceModifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .background(palette.secondaryContainer)
                            .cornerRadius(25.dp),
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

            if (state.isAvailable && state.playbackPosition > 0f) {
                Spacer(GlanceModifier.height(8.dp))
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
