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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import moe.rukamori.archivetune.R

class AlbumArtWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            AlbumArtWidgetContent(context)
        }
    }
}

@Composable
private fun AlbumArtWidgetContent(context: Context) {
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
        val outerPadding = if (minSide < 112.dp) 4.dp else 8.dp
        val artworkCorner = if (minSide < 112.dp) 22.dp else 24.dp

        Box(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .background(palette.surface)
                    .cornerRadius(28.dp)
                    .padding(outerPadding)
                    .clickable(openArchiveTuneAction(context)),
        ) {
            WidgetArtwork(
                artPath = state.artPath,
                context = context,
                contentDescription = context.getString(R.string.album_cover_desc),
                targetSize = minSide,
                cornerRadius = artworkCorner,
                palette = palette,
                modifier = GlanceModifier.fillMaxSize(),
            )

            if (state.isAvailable) {
                AlbumArtControls(
                    state = state,
                    palette = palette,
                    context = context,
                    minSide = minSide,
                )
            } else if (minSide >= 136.dp) {
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier =
                            GlanceModifier
                                .padding(bottom = 8.dp)
                                .height(44.dp)
                                .background(palette.secondaryContainer)
                                .cornerRadius(22.dp)
                                .padding(start = 16.dp, end = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = context.getString(R.string.widget_tap_to_open),
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
            }
        }
    }
}

@Composable
private fun AlbumArtControls(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
    minSide: Dp,
) {
    val showFullControls = minSide >= 176.dp
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (showFullControls) {
            Row(
                modifier =
                    GlanceModifier
                        .padding(bottom = 8.dp)
                        .background(palette.secondaryContainer)
                        .cornerRadius(25.dp)
                        .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WidgetControlButton(
                    modifier = GlanceModifier.size(44.dp),
                    action = skipPreviousAction(),
                    icon = R.drawable.skip_previous,
                    contentDescription = context.getString(R.string.widget_previous),
                    backgroundColor = ColorProvider(Color.Transparent),
                    contentColor = palette.onSecondaryContainer,
                    cornerRadius = 22.dp,
                )
                Spacer(GlanceModifier.width(2.dp))
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
                    cornerRadius = if (state.isPlaying) 13.dp else 28.dp,
                    iconSize = 28.dp,
                )
                Spacer(GlanceModifier.width(2.dp))
                WidgetControlButton(
                    modifier = GlanceModifier.size(44.dp),
                    action = skipNextAction(),
                    icon = R.drawable.skip_next,
                    contentDescription = context.getString(R.string.next),
                    backgroundColor = ColorProvider(Color.Transparent),
                    contentColor = palette.onSecondaryContainer,
                    cornerRadius = 22.dp,
                )
            }
        } else {
            WidgetControlButton(
                modifier =
                    GlanceModifier
                        .padding(bottom = 8.dp)
                        .size(52.dp),
                action = playPauseAction(),
                icon = if (state.isPlaying) R.drawable.pause else R.drawable.play,
                contentDescription =
                    context.getString(
                        if (state.isPlaying) R.string.widget_pause else R.string.play,
                    ),
                backgroundColor = palette.primaryContainer,
                contentColor = palette.onPrimaryContainer,
                cornerRadius = if (state.isPlaying) 13.dp else 26.dp,
                iconSize = 26.dp,
            )
        }
    }
}
