/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.extensions.isSyncEnabled
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.utils.rememberPreference
import java.time.LocalDateTime
import java.util.logging.Logger

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    initialTextFieldValue: String? = null,
    allowSyncing: Boolean = true,
) {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    var syncedPlaylist by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isSignedIn = innerTubeCookie.isNotEmpty()

    TextFieldDialog(
        icon = { Icon(painter = painterResource(R.drawable.playlist_add), contentDescription = null) },
        title = { Text(text = stringResource(R.string.create_playlist)) },
        placeholder = { Text(text = stringResource(R.string.playlist_name)) },
        isInputValid = { it.trim().isNotEmpty() },
        initialTextFieldValue = TextFieldValue(initialTextFieldValue ?: ""),
        onDismiss = onDismiss,
        onDone = { playlistName ->
            coroutineScope.launch(Dispatchers.IO) {
                val browseId =
                    if (syncedPlaylist && isSignedIn) {
                        YouTube.createPlaylist(playlistName).getOrNull()
                    } else if (syncedPlaylist) {
                        Logger.getLogger("CreatePlaylistDialog").warning("Not signed in")
                        return@launch
                    } else {
                        null
                    }

                database.withTransaction {
                    insert(
                        PlaylistEntity(
                            name = playlistName,
                            browseId = browseId,
                            bookmarkedAt = LocalDateTime.now(),
                            isEditable = true,
                        ),
                    )
                }
            }
        },
        extraContent = {
            if (allowSyncing) {
                val isYtmSyncEnabled = context.isSyncEnabled()
                val syncDescription =
                    when {
                        !isSignedIn -> stringResource(R.string.not_logged_in_youtube)
                        !isYtmSyncEnabled -> stringResource(R.string.sync_disabled)
                        else -> stringResource(R.string.allows_for_sync_witch_youtube)
                    }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.large,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.sync),
                            contentDescription = null,
                            tint =
                                if (syncedPlaylist) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = stringResource(R.string.sync_playlist),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = syncDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = syncedPlaylist,
                            onCheckedChange = {
                                if (syncedPlaylist) {
                                    syncedPlaylist = false
                                    return@Switch
                                }
                                if (!isSignedIn) {
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.not_logged_in_youtube),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    return@Switch
                                }
                                if (!isYtmSyncEnabled) {
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.sync_disabled),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    return@Switch
                                }
                                syncedPlaylist = true
                            },
                        )
                    }
                }
            }
        },
    )
}
