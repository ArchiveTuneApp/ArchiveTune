/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.ui.utils.isScrollingUp
import moe.rukamori.archivetune.utils.rememberPreference

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HideOnScrollFAB(
    visible: Boolean = true,
    lazyListState: LazyListState,
    @DrawableRes icon: Int,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    AnimatedVisibility(
        visible = visible && lazyListState.isScrollingUp(),
        enter = slideInVertically(animationSpec = tween(if (animationsDisabled) 0 else 220)) { it },
        exit = slideOutVertically(animationSpec = tween(if (animationsDisabled) 0 else 220)) { it },
        modifier =
            modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            ),
    ) {
        HideOnScrollFabButton(
            icon = icon,
            label = label,
            onClick = onClick,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BoxScope.HideOnScrollFAB(
    visible: Boolean = true,
    lazyListState: LazyListState,
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    HideOnScrollFAB(
        visible = visible,
        lazyListState = lazyListState,
        icon = icon,
        label = label,
        modifier = Modifier.align(Alignment.BottomEnd),
        onClick = onClick,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BoxScope.HideOnScrollFAB(
    visible: Boolean = true,
    lazyListState: LazyGridState,
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    AnimatedVisibility(
        visible = visible && lazyListState.isScrollingUp(),
        enter = slideInVertically(animationSpec = tween(if (animationsDisabled) 0 else 220)) { it },
        exit = slideOutVertically(animationSpec = tween(if (animationsDisabled) 0 else 220)) { it },
        modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current
                        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                ),
    ) {
        HideOnScrollFabButton(
            icon = icon,
            label = label,
            onClick = onClick,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BoxScope.HideOnScrollFAB(
    visible: Boolean = true,
    scrollState: ScrollState,
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    AnimatedVisibility(
        visible = visible && scrollState.isScrollingUp(),
        enter = slideInVertically(animationSpec = tween(if (animationsDisabled) 0 else 220)) { it },
        exit = slideOutVertically(animationSpec = tween(if (animationsDisabled) 0 else 220)) { it },
        modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current
                        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                ),
    ) {
        HideOnScrollFabButton(
            icon = icon,
            label = label,
            onClick = onClick,
        )
    }
}

@Composable
private fun HideOnScrollFabButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)

    ExtendedFloatingActionButton(
        modifier = Modifier.padding(16.dp),
        onClick = {
            if (enableHapticFeedback) {
                view.performHapticFeedback(
                    android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                )
            }
            onClick()
        },
        icon = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
            )
        },
        text = { Text(label) },
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}
