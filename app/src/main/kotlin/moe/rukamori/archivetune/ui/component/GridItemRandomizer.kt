/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.constants.ThumbnailCornerRadius

@Composable
fun RandomizeGridItem(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dotOffsetMultiplier by animateFloatAsState(
        targetValue = if (isLoading) 0f else 1f,
        animationSpec = tween(durationMillis = 600),
        label = "dotOffset",
    )

    val loadingAlpha by animateFloatAsState(
        targetValue = if (isLoading) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "loadingAlpha",
    )

    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(ThumbnailCornerRadius))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val dotColor = MaterialTheme.colorScheme.onSecondaryContainer
        val dotSize = 14.dp
        val padding = 24.dp

        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset((-padding * dotOffsetMultiplier).roundToPx(), (-padding * dotOffsetMultiplier).roundToPx()) }
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset((padding * dotOffsetMultiplier).roundToPx(), (-padding * dotOffsetMultiplier).roundToPx()) }
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset((-padding * dotOffsetMultiplier).roundToPx(), (padding * dotOffsetMultiplier).roundToPx()) }
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset((padding * dotOffsetMultiplier).roundToPx(), (padding * dotOffsetMultiplier).roundToPx()) }
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor),
        )

        Box(modifier = Modifier.alpha(loadingAlpha)) {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
