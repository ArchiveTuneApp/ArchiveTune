/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.theme.rememberArchiveTuneLyricsFontFamily

@Stable
private data class LyricsShareColors(
    val main: Color,
    val secondary: Color,
    val panel: Color,
    val overlay: Color,
    val fallbackBackground: Color,
)

@Composable
fun rememberAdjustedFontSize(
    text: String,
    maxWidth: Dp,
    maxHeight: Dp,
    density: Density,
    initialFontSize: TextUnit = 20.sp,
    minFontSize: TextUnit = 14.sp,
    style: TextStyle = TextStyle.Default,
    textMeasurer: androidx.compose.ui.text.TextMeasurer? = null,
): TextUnit {
    val measurer = textMeasurer ?: rememberTextMeasurer()

    var calculatedFontSize by remember(text, maxWidth, maxHeight, style, density) {
        val initialSize =
            when {
                text.length < 50 -> initialFontSize
                text.length < 100 -> (initialFontSize.value * 0.8f).sp
                text.length < 200 -> (initialFontSize.value * 0.6f).sp
                else -> (initialFontSize.value * 0.5f).sp
            }
        mutableStateOf(initialSize)
    }

    androidx.compose.runtime.LaunchedEffect(text, maxWidth, maxHeight) {
        val targetWidthPx = with(density) { maxWidth.toPx() * 0.92f }
        val targetHeightPx = with(density) { maxHeight.toPx() * 0.92f }
        if (text.isBlank()) {
            calculatedFontSize = minFontSize
            return@LaunchedEffect
        }

        if (text.length < 20) {
            val largerSize = (initialFontSize.value * 1.1f).sp
            val result = measurer.measure(text = AnnotatedString(text), style = style.copy(fontSize = largerSize))
            if (result.size.width <= targetWidthPx && result.size.height <= targetHeightPx) {
                calculatedFontSize = largerSize
                return@LaunchedEffect
            }
        } else if (text.length < 30) {
            val largerSize = (initialFontSize.value * 0.9f).sp
            val result = measurer.measure(text = AnnotatedString(text), style = style.copy(fontSize = largerSize))
            if (result.size.width <= targetWidthPx && result.size.height <= targetHeightPx) {
                calculatedFontSize = largerSize
                return@LaunchedEffect
            }
        }

        var minSize = minFontSize.value
        var maxSize = initialFontSize.value
        var bestFit = minSize
        var iterations = 0

        while (minSize <= maxSize && iterations < 20) {
            iterations++
            val midSize = (minSize + maxSize) / 2
            val result = measurer.measure(text = AnnotatedString(text), style = style.copy(fontSize = midSize.sp))
            if (result.size.width <= targetWidthPx && result.size.height <= targetHeightPx) {
                bestFit = midSize
                minSize = midSize + 0.5f
            } else {
                maxSize = midSize - 0.5f
            }
        }

        calculatedFontSize = if (bestFit < minFontSize.value) minFontSize else bestFit.sp
    }

    return calculatedFontSize
}

@Composable
fun LyricsImageCard(
    lyricText: String,
    songTitle: String,
    artistName: String,
    coverArtUrl: String?,
    glassStyle: LyricsGlassStyle = LyricsGlassStyle.FrostedDark,
    shareOptions: LyricsShareImageOptions = LyricsShareImageOptions(),
    textColor: Color? = null,
    secondaryTextColor: Color? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lyricsFontFamily = rememberArchiveTuneLyricsFontFamily()
    val colors =
        remember(glassStyle, textColor, secondaryTextColor) {
            LyricsShareColors(
                main = textColor ?: glassStyle.textColor,
                secondary = secondaryTextColor ?: glassStyle.secondaryTextColor,
                panel = glassStyle.surfaceTint.copy(alpha = glassStyle.surfaceAlpha),
                overlay = glassStyle.overlayColor.copy(alpha = glassStyle.overlayAlpha),
                fallbackBackground = glassStyle.surfaceTint.copy(alpha = (glassStyle.surfaceAlpha + 0.18f).coerceIn(0f, 1f)),
            )
        }
    val dimAlpha =
        remember(glassStyle.backgroundDimAlpha, shareOptions.sanitizedDimAmount) {
            (glassStyle.backgroundDimAlpha * shareOptions.sanitizedDimAmount).coerceIn(0f, 0.95f)
        }
    val backgroundBlur = rememberNativeBlurEffect(shareOptions.sanitizedBlurRadius)
    val panelBlur = rememberNativeBlurEffect((shareOptions.sanitizedBlurRadius + 10f).coerceIn(8f, 48f))
    val artworkPainter =
        rememberAsyncImagePainter(
            ImageRequest
                .Builder(context)
                .data(coverArtUrl)
                .crossfade(true)
                .build(),
        )
    val cardShape = MaterialTheme.shapes.large
    val panelShape = MaterialTheme.shapes.large
    val artworkShape = MaterialTheme.shapes.medium

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (coverArtUrl != null) {
                Image(
                    painter = artworkPainter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .nativeBlur(backgroundBlur),
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            if (glassStyle.isDark) Color.Black.copy(alpha = dimAlpha) else Color.White.copy(alpha = dimAlpha * 0.42f),
                        ),
            )

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .clip(panelShape),
                contentAlignment = Alignment.Center,
            ) {
                if (coverArtUrl != null && panelBlur != null) {
                    Image(
                        painter = artworkPainter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .matchParentSize()
                                .nativeBlur(panelBlur),
                    )
                } else {
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .background(colors.fallbackBackground),
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .drawWithContent {
                                drawContent()
                                drawRect(colors.panel)
                                drawRect(colors.overlay)
                            },
                )

                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 22.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (shareOptions.showArtwork) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                painter = artworkPainter,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier =
                                    Modifier
                                        .size(56.dp)
                                        .clip(artworkShape)
                                        .border(1.dp, colors.secondary.copy(alpha = 0.2f), artworkShape),
                            )
                            Spacer(modifier = Modifier.size(14.dp))
                            SongTextBlock(
                                songTitle = songTitle,
                                artistName = artistName,
                                mainTextColor = colors.main,
                                secondaryColor = colors.secondary,
                                centered = false,
                            )
                        }
                    } else {
                        SongTextBlock(
                            songTitle = songTitle,
                            artistName = artistName,
                            mainTextColor = colors.main,
                            secondaryColor = colors.secondary,
                            centered = true,
                        )
                    }

                    BoxWithConstraints(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val textStyle =
                            remember(colors.main, lyricsFontFamily) {
                                TextStyle(
                                    color = colors.main,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    letterSpacing = (-0.01).em,
                                    fontFamily = lyricsFontFamily,
                                )
                            }
                        val textMeasurer = rememberTextMeasurer()
                        val initialSize =
                            remember(lyricText) {
                                when {
                                    lyricText.length < 50 -> 24.sp
                                    lyricText.length < 100 -> 20.sp
                                    lyricText.length < 200 -> 17.sp
                                    lyricText.length < 300 -> 15.sp
                                    else -> 13.sp
                                }
                            }
                        val dynamicFontSize =
                            rememberAdjustedFontSize(
                                text = lyricText,
                                maxWidth = maxWidth - 8.dp,
                                maxHeight = maxHeight - 8.dp,
                                density = density,
                                initialFontSize = initialSize,
                                minFontSize = 11.sp,
                                style = textStyle,
                                textMeasurer = textMeasurer,
                            )

                        androidx.compose.material3.Text(
                            text = lyricText,
                            style =
                                textStyle.copy(
                                    fontSize = dynamicFontSize,
                                    lineHeight = dynamicFontSize.value.sp * 1.35f,
                                ),
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(22.dp)
                                    .clip(MaterialTheme.shapes.extraLarge)
                                    .background(colors.secondary.copy(alpha = 0.9f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.small_icon),
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                colorFilter =
                                    ColorFilter.tint(
                                        if (glassStyle.isDark) Color.Black.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.9f),
                                    ),
                            )
                        }

                        Spacer(modifier = Modifier.size(8.dp))

                        androidx.compose.material3.Text(
                            text = stringResource(R.string.app_name),
                            color = colors.secondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.02.em,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberNativeBlurEffect(radius: Float): RenderEffect? {
    val safeRadius = radius.coerceIn(0f, 48f)
    return remember(safeRadius) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && safeRadius > 0.5f) {
            BlurEffect(
                radiusX = safeRadius,
                radiusY = safeRadius,
                edgeTreatment = TileMode.Clamp,
            ).takeIf(RenderEffect::isSupported)
        } else {
            null
        }
    }
}

private fun Modifier.nativeBlur(effect: RenderEffect?): Modifier =
    if (effect == null) {
        this
    } else {
        graphicsLayer {
            renderEffect = effect
        }
    }

@Composable
private fun SongTextBlock(
    songTitle: String,
    artistName: String,
    mainTextColor: Color,
    secondaryColor: Color,
    centered: Boolean,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.material3.Text(
            text = songTitle,
            color = mainTextColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(letterSpacing = (-0.02).em),
        )
        Spacer(modifier = Modifier.height(2.dp))
        androidx.compose.material3.Text(
            text = artistName,
            color = secondaryColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 18.dp),
        )
    }
}
