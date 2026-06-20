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

class MusicWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            MusicWidgetContent(context)
        }
    }
}

@Composable
private fun MusicWidgetContent(context: Context) {
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
        when (size.toMusicWidgetLayout()) {
            MusicWidgetLayout.Compact -> {
                MusicWidgetBar(
                    state = state,
                    palette = palette,
                    context = context,
                    showFullControls = false,
                    showProgress = false,
                )
            }

            MusicWidgetLayout.Bar -> {
                MusicWidgetBar(
                    state = state,
                    palette = palette,
                    context = context,
                    showFullControls = true,
                    showProgress = state.isAvailable && state.playbackPosition > 0f && size.height >= 78.dp,
                )
            }

            MusicWidgetLayout.Panel -> {
                MusicWidgetPanel(
                    state = state,
                    palette = palette,
                    context = context,
                )
            }
        }
    }
}

@Composable
private fun MusicWidgetBar(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
    showFullControls: Boolean,
    showProgress: Boolean,
) {
    Box(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(palette.surface)
                .cornerRadius(28.dp)
                .padding(6.dp)
                .clickable(openArchiveTuneAction(context)),
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(
                modifier =
                    GlanceModifier
                        .fillMaxWidth()
                        .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WidgetArtwork(
                    artPath = state.artPath,
                    context = context,
                    contentDescription = context.getString(R.string.album_cover_desc),
                    targetSize = 48.dp,
                    cornerRadius = 16.dp,
                    palette = palette,
                    modifier = GlanceModifier.size(48.dp),
                )

                Spacer(GlanceModifier.width(10.dp))

                MusicWidgetTrackText(
                    state = state,
                    palette = palette,
                    titleSize = 14,
                    artistSize = 12,
                    modifier = GlanceModifier.defaultWeight(),
                )

                if (state.isAvailable) {
                    Spacer(GlanceModifier.width(8.dp))
                    MusicWidgetControls(
                        state = state,
                        palette = palette,
                        context = context,
                        showPreviousNext = showFullControls,
                        modifier = GlanceModifier,
                        buttonModifier = GlanceModifier.size(48.dp),
                    )
                }
            }

            if (showProgress) {
                Spacer(GlanceModifier.height(6.dp))
                MusicWidgetProgress(
                    progress = state.playbackPosition,
                    palette = palette,
                )
            }
        }
    }
}

@Composable
private fun MusicWidgetPanel(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
) {
    Box(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(palette.surface)
                .cornerRadius(28.dp)
                .padding(16.dp)
                .clickable(openArchiveTuneAction(context)),
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
                    targetSize = 72.dp,
                    cornerRadius = 18.dp,
                    palette = palette,
                    modifier = GlanceModifier.size(72.dp),
                )

                Spacer(GlanceModifier.width(14.dp))

                MusicWidgetTrackText(
                    state = state,
                    palette = palette,
                    titleSize = 16,
                    artistSize = 13,
                    modifier = GlanceModifier.defaultWeight(),
                )
            }

            Spacer(GlanceModifier.defaultWeight())

            if (state.isAvailable) {
                WidgetExpressiveControlPill(
                    state = state,
                    palette = palette,
                    context = context,
                )
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
                Spacer(GlanceModifier.height(10.dp))
                MusicWidgetProgress(
                    progress = state.playbackPosition,
                    palette = palette,
                )
            }
        }
    }
}

@Composable
private fun MusicWidgetTrackText(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    titleSize: Int,
    artistSize: Int,
    modifier: GlanceModifier,
) {
    Column(
        modifier = modifier,
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = state.title,
            style =
                TextStyle(
                    color = palette.onSurface,
                    fontSize = titleSize.sp,
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
                    fontSize = artistSize.sp,
                ),
            maxLines = 1,
        )
    }
}

@Composable
private fun MusicWidgetControls(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
    showPreviousNext: Boolean,
    modifier: GlanceModifier,
    buttonModifier: GlanceModifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showPreviousNext) {
            WidgetControlButton(
                modifier = buttonModifier,
                action = skipPreviousAction(),
                icon = R.drawable.skip_previous,
                contentDescription = context.getString(R.string.widget_previous),
                backgroundColor = palette.secondaryContainer,
                contentColor = palette.onSecondaryContainer,
                cornerRadius = 22.dp,
            )
            Spacer(GlanceModifier.width(6.dp))
        }

        WidgetControlButton(
            modifier = buttonModifier,
            action = playPauseAction(),
            icon = if (state.isPlaying) R.drawable.pause else R.drawable.play,
            contentDescription =
                context.getString(
                    if (state.isPlaying) R.string.widget_pause else R.string.play,
                ),
            backgroundColor = palette.primaryContainer,
            contentColor = palette.onPrimaryContainer,
            cornerRadius = if (state.isPlaying) 13.dp else 24.dp,
            iconSize = 24.dp,
        )

        if (showPreviousNext) {
            Spacer(GlanceModifier.width(6.dp))
            WidgetControlButton(
                modifier = buttonModifier,
                action = skipNextAction(),
                icon = R.drawable.skip_next,
                contentDescription = context.getString(R.string.next),
                backgroundColor = palette.secondaryContainer,
                contentColor = palette.onSecondaryContainer,
                cornerRadius = 22.dp,
            )
        }
    }
}

@Composable
private fun MusicWidgetProgress(
    progress: Float,
    palette: WidgetPalette,
) {
    LinearProgressIndicator(
        progress = progress,
        color = palette.progress,
        backgroundColor = palette.progressTrack,
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .height(6.dp)
                .cornerRadius(3.dp),
    )
}

private enum class MusicWidgetLayout {
    Compact,
    Bar,
    Panel,
}

private fun DpSize.toMusicWidgetLayout(): MusicWidgetLayout =
    when {
        height >= 128.dp -> MusicWidgetLayout.Panel
        width < 300.dp -> MusicWidgetLayout.Compact
        else -> MusicWidgetLayout.Bar
    }
