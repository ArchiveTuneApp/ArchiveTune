/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import java.time.LocalDateTime

@Composable
fun ImportPlaylistDialog(
    isVisible: Boolean,
    onGetSong: suspend () -> List<String>,
    playlistTitle: String,
    browseId: String? = null,
    snackbarHostState: SnackbarHostState? = null,
    onDismiss: () -> Unit,
) {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var currentPlaylistName by remember(playlistTitle) { mutableStateOf(playlistTitle) }
    var songIds by remember { mutableStateOf<List<String>?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var existingPlaylistId by remember { mutableStateOf<String?>(null) }
    var isProcessingDuplicate by remember { mutableStateOf(false) }

    fun showMessage(message: String) {
        coroutineScope.launch {
            if (snackbarHostState != null) {
                snackbarHostState.showSnackbar(message)
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun resetState() {
        songIds = null
        isImporting = false
        showDuplicateDialog = false
        existingPlaylistId = null
        isProcessingDuplicate = false
    }

    if (isVisible) {
        TextFieldDialog(
            icon = { Icon(painter = painterResource(R.drawable.add), contentDescription = null) },
            title = { Text(text = stringResource(R.string.import_playlist)) },
            initialTextFieldValue = TextFieldValue(text = playlistTitle),
            autoFocus = false,
            onDismiss = {
                resetState()
                onDismiss()
            },
            extraContent = {
                if (isImporting) {
                    CircularWavyProgressIndicator()
                }
            },
            onDone = { finalName ->
                currentPlaylistName = finalName
                isImporting = true

                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val ids = onGetSong()
                        songIds = ids

                        if (ids.isEmpty()) {
                            showMessage(context.getString(R.string.import_failed))
                            withContext(Dispatchers.Main) {
                                resetState()
                                onDismiss()
                            }
                            return@launch
                        }

                        if (browseId != null) {
                            val existing = database.playlistByBrowseId(browseId).firstOrNull()
                            if (existing != null) {
                                if (existing.playlist.bookmarkedAt == null) {
                                    database.query {
                                        update(
                                            existing.playlist.copy(
                                                bookmarkedAt = LocalDateTime.now(),
                                                lastUpdateTime = LocalDateTime.now(),
                                            ),
                                        )
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    existingPlaylistId = existing.playlist.id
                                    isImporting = false
                                    showDuplicateDialog = true
                                }
                                return@launch
                            }
                        }

                        val newPlaylist =
                            PlaylistEntity(
                                name = finalName,
                                browseId = browseId,
                                isEditable = browseId == null,
                                bookmarkedAt = LocalDateTime.now(),
                            )
                        database.query { insert(newPlaylist) }

                        val playlist = database.playlist(newPlaylist.id).firstOrNull()
                        if (playlist != null) {
                            database.addSongToPlaylist(playlist, ids)
                        }

                        showMessage(context.getString(R.string.playlist_synced))
                        withContext(Dispatchers.Main) {
                            resetState()
                            onDismiss()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showMessage(context.getString(R.string.import_failed) + ": ${e.message ?: "Unknown error"}")
                        withContext(Dispatchers.Main) {
                            resetState()
                            onDismiss()
                        }
                    }
                }
            },
        )
    }

    if (showDuplicateDialog && existingPlaylistId != null) {
        DefaultDialog(
            onDismiss = {
                if (!isProcessingDuplicate) {
                    resetState()
                }
            },
            title = { Text(text = stringResource(R.string.import_playlist)) },
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = stringResource(R.string.already_in_playlist))
                    if (isProcessingDuplicate) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularWavyProgressIndicator()
                    }
                }
            },
            buttons = {
                TextButton(
                    enabled = !isProcessingDuplicate,
                    onClick = {
                        resetState()
                        onDismiss()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(text = stringResource(android.R.string.cancel)) }

                TextButton(
                    enabled = !isProcessingDuplicate,
                    onClick = {
                        isProcessingDuplicate = true
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val ids = songIds ?: onGetSong()
                                if (ids.isEmpty()) {
                                    showMessage(context.getString(R.string.import_failed))
                                    withContext(Dispatchers.Main) {
                                        resetState()
                                        onDismiss()
                                    }
                                    return@launch
                                }

                                val playlist = database.playlist(existingPlaylistId!!).firstOrNull()
                                if (playlist != null) {
                                    if (playlist.playlist.bookmarkedAt == null) {
                                        database.query {
                                            update(
                                                playlist.playlist.copy(
                                                    bookmarkedAt = LocalDateTime.now(),
                                                    lastUpdateTime = LocalDateTime.now(),
                                                ),
                                            )
                                        }
                                    }
                                    val existingSongIds =
                                        database
                                            .playlistSongs(playlist.id)
                                            .firstOrNull()
                                            ?.map { it.song.id }
                                            ?.toSet() ?: emptySet()
                                    val newSongIds = ids.filterNot { it in existingSongIds }

                                    if (newSongIds.isEmpty()) {
                                        showMessage(context.getString(R.string.playlist_synced))
                                    } else {
                                        database.transaction {
                                            var position = playlist.songCount
                                            newSongIds.forEach { songId ->
                                                insert(
                                                    PlaylistSongMap(
                                                        songId = songId,
                                                        playlistId = playlist.id,
                                                        position = position++,
                                                    ),
                                                )
                                            }
                                        }
                                        showMessage(context.getString(R.string.playlist_synced))
                                    }
                                } else {
                                    showMessage(context.getString(R.string.import_failed))
                                }

                                withContext(Dispatchers.Main) {
                                    resetState()
                                    onDismiss()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                showMessage(context.getString(R.string.import_failed) + ": ${e.message ?: "Unknown error"}")
                                withContext(Dispatchers.Main) {
                                    resetState()
                                    onDismiss()
                                }
                            }
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(text = stringResource(R.string.update_button)) }

                TextButton(
                    enabled = !isProcessingDuplicate,
                    onClick = {
                        isProcessingDuplicate = true
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val ids = songIds ?: onGetSong()
                                if (ids.isEmpty()) {
                                    showMessage(context.getString(R.string.import_failed))
                                    withContext(Dispatchers.Main) {
                                        resetState()
                                        onDismiss()
                                    }
                                    return@launch
                                }

                                val newPlaylist =
                                    PlaylistEntity(
                                        name = currentPlaylistName,
                                        browseId = null,
                                        bookmarkedAt = LocalDateTime.now(),
                                    )
                                database.query { insert(newPlaylist) }

                                val playlist = database.playlist(newPlaylist.id).firstOrNull()
                                if (playlist != null) {
                                    database.addSongToPlaylist(playlist, ids)
                                    showMessage(context.getString(R.string.playlist_synced))
                                } else {
                                    showMessage(context.getString(R.string.import_failed))
                                }

                                withContext(Dispatchers.Main) {
                                    resetState()
                                    onDismiss()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                showMessage(context.getString(R.string.import_failed) + ": ${e.message ?: "Unknown error"}")
                                withContext(Dispatchers.Main) {
                                    resetState()
                                    onDismiss()
                                }
                            }
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(text = stringResource(R.string.import_playlist)) }
            },
        )
    }
}
