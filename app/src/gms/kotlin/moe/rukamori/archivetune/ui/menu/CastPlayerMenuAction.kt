/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.cast.CastScreenState
import moe.rukamori.archivetune.cast.CastViewModel
import moe.rukamori.archivetune.ui.component.NewAction

@Composable
fun rememberCastPlayerMenuAction(): NewAction? {
    val viewModel: CastViewModel = viewModel()
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val castState = (screenState as? CastScreenState.Success)?.uiState ?: return null
    if (!castState.isAvailable) return null

    val text = stringResource(R.string.cast)
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    var routeButton by remember { mutableStateOf<MediaRouteButton?>(null) }

    return NewAction(
        icon = {
            AndroidView(
                factory = { viewContext ->
                    MediaRouteButton(viewContext).also { button ->
                        CastButtonFactory.setUpMediaRouteButton(viewContext, button)
                        button.setColorFilter(tint.toArgb())
                        routeButton = button
                    }
                },
                update = { button ->
                    button.setColorFilter(tint.toArgb())
                    routeButton = button
                },
                modifier = Modifier.size(28.dp),
            )
        },
        text = text,
        onClick = { routeButton?.performClick() },
    )
}
