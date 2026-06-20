/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.toColorInt
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.viewmodels.PlaylistTagPlaylistUiModel
import moe.rukamori.archivetune.viewmodels.PlaylistTagUiModel
import moe.rukamori.archivetune.viewmodels.PlaylistTagsScreenState
import moe.rukamori.archivetune.viewmodels.PlaylistTagsViewModel

@Composable
fun AssignTagsDialog(
    playlistId: String,
    onDismiss: () -> Unit,
    viewModel: PlaylistTagsViewModel = hiltViewModel(),
) {
    LaunchedEffect(playlistId) {
        viewModel.setPlaylistId(playlistId)
    }

    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val managementVisible by viewModel.managementVisible.collectAsStateWithLifecycle()

    if (managementVisible) {
        TagsManagementDialog(
            onDismiss = viewModel::dismissManagement,
            viewModel = viewModel,
        )
    }

    (state as? PlaylistTagsScreenState.Success)
        ?.takeIf { success -> success.isBulkAssignVisible }
        ?.let { success ->
            AssignTagsToPlaylistsDialog(
                state = success,
                onDismiss = viewModel::dismissBulkAssign,
                onTagToggle = viewModel::toggleBulkTag,
                onPlaylistToggle = viewModel::toggleBulkPlaylist,
                onSave = { viewModel.saveBulkAssignments(onSaved = viewModel::dismissBulkAssign) },
            )
        }

    PlaylistTagsDialogScaffold(onDismiss = onDismiss) {
        AssignTagsContent(
            state = state,
            onManageTags = viewModel::openManagement,
            onAssignToPlaylists = viewModel::openBulkAssign,
            onTagToggle = viewModel::toggleTagSelection,
            onDismiss = onDismiss,
            onSave = { viewModel.saveSelectedPlaylistTags(onSaved = onDismiss) },
        )
    }
}

@Composable
private fun AssignTagsContent(
    state: PlaylistTagsScreenState,
    onManageTags: () -> Unit,
    onAssignToPlaylists: () -> Unit,
    onTagToggle: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        AssignTagsHeader(
            title = stringResource(R.string.assign_tags),
            subtitle =
                (state as? PlaylistTagsScreenState.Success)?.let { success ->
                    pluralStringResource(
                        R.plurals.n_selected,
                        success.selectedTagIds.size,
                        success.selectedTagIds.size,
                    )
                },
        )

        when (state) {
            PlaylistTagsScreenState.Loading -> {
                AssignTagsLoadingContent()
            }

            PlaylistTagsScreenState.Empty -> {
                AssignTagsEmptyContent(onManageTags = onManageTags)
            }

            is PlaylistTagsScreenState.Error -> {
                AssignTagsErrorContent(messageResId = state.messageResId)
            }

            is PlaylistTagsScreenState.Success -> {
                AssignTagsList(
                    tags = state.tags,
                    selectedTagIds = state.selectedTagIds,
                    onTagToggle = onTagToggle,
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = onManageTags,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(R.string.manage_tags))
                }

                TextButton(
                    enabled = state is PlaylistTagsScreenState.Success,
                    onClick = onAssignToPlaylists,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(R.string.assign_tags_to_playlists))
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                Button(
                    enabled = state is PlaylistTagsScreenState.Success,
                    onClick = onSave,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(R.string.save))
                }
            }
        }
    }
}

@Composable
private fun AssignTagsList(
    tags: List<PlaylistTagUiModel>,
    selectedTagIds: Set<String>,
    onTagToggle: (String) -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        itemsIndexed(
            items = tags,
            key = { _, tag -> tag.id },
            contentType = { _, _ -> "assign_tag_row" },
        ) { index, tag ->
            val tagClick = remember(tag.id, onTagToggle) { { onTagToggle(tag.id) } }

            AssignTagRow(
                tag = tag,
                index = index,
                count = tags.size,
                selected = tag.id in selectedTagIds,
                onClick = tagClick,
            )
        }
    }
}

@Composable
private fun AssignTagRow(
    tag: PlaylistTagUiModel,
    index: Int,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        leadingContent = {
            TagColorSwatch(
                color = tag.color,
                selected = selected,
            )
        },
        trailingContent = {
            Icon(
                painter =
                    painterResource(
                        if (selected) R.drawable.check else R.drawable.add,
                    ),
                contentDescription = null,
                tint =
                    if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(24.dp),
            )
        },
    ) {
        Text(
            text = tag.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AssignTagsToPlaylistsDialog(
    state: PlaylistTagsScreenState.Success,
    onDismiss: () -> Unit,
    onTagToggle: (String) -> Unit,
    onPlaylistToggle: (String) -> Unit,
    onSave: () -> Unit,
) {
    PlaylistTagsDialogScaffold(onDismiss = onDismiss) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            AssignTagsHeader(
                title = stringResource(R.string.assign_tags_to_playlists),
                subtitle =
                    pluralStringResource(
                        R.plurals.n_selected,
                        state.selectedBulkPlaylistIds.size,
                        state.selectedBulkPlaylistIds.size,
                    ),
            )

            AssignTagsBulkSection(
                tags = state.tags,
                selectedTagIds = state.selectedBulkTagIds,
                onTagToggle = onTagToggle,
            )

            AssignPlaylistsBulkSection(
                playlists = state.playlists,
                selectedPlaylistIds = state.selectedBulkPlaylistIds,
                onPlaylistToggle = onPlaylistToggle,
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                Button(
                    enabled = state.selectedBulkPlaylistIds.isNotEmpty() && state.selectedBulkTagIds.isNotEmpty(),
                    onClick = onSave,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(R.string.save))
                }
            }
        }
    }
}

@Composable
private fun AssignTagsBulkSection(
    tags: List<PlaylistTagUiModel>,
    selectedTagIds: Set<String>,
    onTagToggle: (String) -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        itemsIndexed(
            items = tags,
            key = { _, tag -> tag.id },
            contentType = { _, _ -> "bulk_tag_row" },
        ) { index, tag ->
            val tagClick = remember(tag.id, onTagToggle) { { onTagToggle(tag.id) } }
            AssignTagRow(
                tag = tag,
                index = index,
                count = tags.size,
                selected = tag.id in selectedTagIds,
                onClick = tagClick,
            )
        }
    }
}

@Composable
private fun AssignPlaylistsBulkSection(
    playlists: List<PlaylistTagPlaylistUiModel>,
    selectedPlaylistIds: Set<String>,
    onPlaylistToggle: (String) -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        itemsIndexed(
            items = playlists,
            key = { _, playlist -> playlist.id },
            contentType = { _, _ -> "bulk_playlist_row" },
        ) { index, playlist ->
            val playlistClick = remember(playlist.id, onPlaylistToggle) { { onPlaylistToggle(playlist.id) } }

            AssignPlaylistRow(
                playlist = playlist,
                index = index,
                count = playlists.size,
                selected = playlist.id in selectedPlaylistIds,
                onClick = playlistClick,
            )
        }
    }
}

@Composable
private fun AssignPlaylistRow(
    playlist: PlaylistTagPlaylistUiModel,
    index: Int,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
        leadingContent = {
            Surface(
                shape = if (selected) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.large,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.queue_music),
                        contentDescription = null,
                        tint =
                            if (selected) {
                                MaterialTheme.colorScheme.onTertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
        supportingContent = {
            Text(
                text =
                    pluralStringResource(
                        R.plurals.n_song,
                        playlist.songCount,
                        playlist.songCount,
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Icon(
                painter =
                    painterResource(
                        if (selected) R.drawable.check else R.drawable.add,
                    ),
                contentDescription = null,
                tint =
                    if (selected) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(24.dp),
            )
        },
    ) {
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AssignTagsHeader(
    title: String,
    subtitle: String?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.style),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AssignTagsLoadingContent() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LoadingIndicator(modifier = Modifier.size(40.dp))
        Text(
            text = stringResource(R.string.loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AssignTagsEmptyContent(onManageTags: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.style),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text = stringResource(R.string.no_tags_available),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = onManageTags,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(R.string.manage_tags))
            }
        }
    }
}

@Composable
private fun AssignTagsErrorContent(messageResId: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.error),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = stringResource(messageResId),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TagColorSwatch(
    color: String,
    selected: Boolean,
) {
    val motionScheme = MaterialTheme.motionScheme
    val swatchColor = rememberTagColor(color)
    val borderColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "assignTagSwatchBorder",
    )

    Surface(
        modifier = Modifier.size(48.dp),
        shape = if (selected) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.large,
        color = swatchColor,
        border = BorderStroke(2.dp, borderColor),
    ) {
        if (selected) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(28.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.check),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistTagsDialogScaffold(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .imePadding()
                    .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight),
                shape = AlertDialogDefaults.shape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = AlertDialogDefaults.TonalElevation,
                content = content,
            )
        }
    }
}

@Composable
private fun rememberTagColor(color: String): Color {
    val fallback = MaterialTheme.colorScheme.primary
    return remember(color, fallback) {
        runCatching { Color(color.toColorInt()) }.getOrDefault(fallback)
    }
}
