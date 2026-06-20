/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.TagEntity
import moe.rukamori.archivetune.playlisttags.AddTagsToPlaylistsUseCase
import moe.rukamori.archivetune.playlisttags.CreatePlaylistTagUseCase
import moe.rukamori.archivetune.playlisttags.DeletePlaylistTagUseCase
import moe.rukamori.archivetune.playlisttags.ObservePlaylistTagsUseCase
import moe.rukamori.archivetune.playlisttags.PlaylistTagModel
import moe.rukamori.archivetune.playlisttags.PlaylistTagPlaylistModel
import moe.rukamori.archivetune.playlisttags.SavePlaylistTagsUseCase
import moe.rukamori.archivetune.playlisttags.UpdatePlaylistTagColorUseCase
import moe.rukamori.archivetune.playlisttags.UpdatePlaylistTagUseCase
import javax.inject.Inject

sealed interface PlaylistTagsScreenState {
    data object Loading : PlaylistTagsScreenState

    @Immutable
    data class Success(
        val tags: ImmutableList<PlaylistTagUiModel>,
        val selectedTagIds: ImmutableSet<String>,
        val playlists: ImmutableList<PlaylistTagPlaylistUiModel>,
        val selectedBulkTagIds: ImmutableSet<String>,
        val selectedBulkPlaylistIds: ImmutableSet<String>,
        val isBulkAssignVisible: Boolean,
    ) : PlaylistTagsScreenState

    data object Empty : PlaylistTagsScreenState

    @Immutable
    data class Error(
        @StringRes val messageResId: Int,
    ) : PlaylistTagsScreenState
}

@Immutable
data class PlaylistTagUiModel(
    val id: String,
    val name: String,
    val color: String,
)

@Immutable
data class PlaylistTagPlaylistUiModel(
    val id: String,
    val name: String,
    val songCount: Int,
)

sealed interface PlaylistTagEditorState {
    data object Hidden : PlaylistTagEditorState

    @Immutable
    data class Visible(
        val tagId: String?,
        val name: String,
        val color: String,
        val canSave: Boolean,
    ) : PlaylistTagEditorState
}

sealed interface PlaylistTagColorPickerState {
    data object Hidden : PlaylistTagColorPickerState

    @Immutable
    data class Visible(
        val selectedColor: String,
        val tagId: String?,
        val isEditorTarget: Boolean,
        val colors: ImmutableList<String>,
    ) : PlaylistTagColorPickerState
}

private data class PlaylistTagsControls(
    val selectedTagIds: Set<String>?,
    val selectedBulkTagIds: Set<String>,
    val selectedBulkPlaylistIds: Set<String>,
    val isBulkAssignVisible: Boolean,
    @StringRes val operationErrorResId: Int?,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistTagsViewModel
    @Inject
    constructor(
        private val observePlaylistTags: ObservePlaylistTagsUseCase,
        private val createPlaylistTag: CreatePlaylistTagUseCase,
        private val updatePlaylistTag: UpdatePlaylistTagUseCase,
        private val updatePlaylistTagColor: UpdatePlaylistTagColorUseCase,
        private val deletePlaylistTag: DeletePlaylistTagUseCase,
        private val savePlaylistTags: SavePlaylistTagsUseCase,
        private val addTagsToPlaylists: AddTagsToPlaylistsUseCase,
    ) : ViewModel() {
        private val playlistId = MutableStateFlow<String?>(null)
        private val selectedTagIds = MutableStateFlow<Set<String>?>(null)
        private val selectedBulkTagIds = MutableStateFlow<Set<String>>(emptySet())
        private val selectedBulkPlaylistIds = MutableStateFlow<Set<String>>(emptySet())
        private val isBulkAssignVisible = MutableStateFlow(false)
        private val isManagementVisible = MutableStateFlow(false)
        private val operationError = MutableStateFlow<Int?>(null)
        private val editor = MutableStateFlow<PlaylistTagEditorState>(PlaylistTagEditorState.Hidden)
        private val colorPicker = MutableStateFlow<PlaylistTagColorPickerState>(PlaylistTagColorPickerState.Hidden)
        private var writeJob: Job? = null

        private val controls =
            combine(
                selectedTagIds,
                selectedBulkTagIds,
                selectedBulkPlaylistIds,
                isBulkAssignVisible,
                operationError,
            ) { selectedTagIds, selectedBulkTagIds, selectedBulkPlaylistIds, isBulkAssignVisible, operationError ->
                PlaylistTagsControls(
                    selectedTagIds = selectedTagIds,
                    selectedBulkTagIds = selectedBulkTagIds,
                    selectedBulkPlaylistIds = selectedBulkPlaylistIds,
                    isBulkAssignVisible = isBulkAssignVisible,
                    operationErrorResId = operationError,
                )
            }

        val screenState: StateFlow<PlaylistTagsScreenState> =
            playlistId
                .flatMapLatest { currentPlaylistId -> observePlaylistTags(currentPlaylistId) }
                .combine(controls) { snapshot, controls ->
                    controls.operationErrorResId?.let { messageResId ->
                        return@combine PlaylistTagsScreenState.Error(messageResId)
                    }

                    val tags = snapshot.tags.mapTagsToUiModels()
                    val validTagIds = snapshot.tags.mapTo(LinkedHashSet()) { tag -> tag.id }
                    val validPlaylistIds = snapshot.playlists.mapTo(LinkedHashSet()) { playlist -> playlist.id }
                    val selected =
                        controls.selectedTagIds
                            ?.sanitize(validTagIds)
                            ?: snapshot.selectedTagIds.sanitize(validTagIds)

                    val success =
                        PlaylistTagsScreenState.Success(
                            tags = ImmutableList.copyOf(tags),
                            selectedTagIds = ImmutableSet.copyOf(selected),
                            playlists = ImmutableList.copyOf(snapshot.playlists.mapPlaylistsToUiModels()),
                            selectedBulkTagIds = ImmutableSet.copyOf(controls.selectedBulkTagIds.sanitize(validTagIds)),
                            selectedBulkPlaylistIds =
                                ImmutableSet.copyOf(
                                    controls.selectedBulkPlaylistIds.sanitize(validPlaylistIds),
                                ),
                            isBulkAssignVisible = controls.isBulkAssignVisible,
                        )

                    if (tags.isEmpty()) {
                        PlaylistTagsScreenState.Empty
                    } else {
                        success
                    }
                }.catch { emit(PlaylistTagsScreenState.Error(R.string.error_unknown)) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = PlaylistTagsScreenState.Loading,
                )

        val editorState: StateFlow<PlaylistTagEditorState> = editor
        val colorPickerState: StateFlow<PlaylistTagColorPickerState> = colorPicker
        val managementVisible: StateFlow<Boolean> = isManagementVisible

        fun setPlaylistId(id: String?) {
            if (playlistId.value == id) return
            playlistId.value = id
            selectedTagIds.value = null
            selectedBulkTagIds.value = emptySet()
            selectedBulkPlaylistIds.value = id?.let(::setOf).orEmpty()
            isBulkAssignVisible.value = false
            isManagementVisible.value = false
        }

        fun toggleTagSelection(tagId: String) {
            val current = (screenState.value as? PlaylistTagsScreenState.Success)?.selectedTagIds?.toSet().orEmpty()
            selectedTagIds.value = current.toggle(tagId)
        }

        fun openBulkAssign() {
            val state = screenState.value as? PlaylistTagsScreenState.Success ?: return
            selectedBulkTagIds.value = state.selectedTagIds
            selectedBulkPlaylistIds.value = playlistId.value?.let(::setOf).orEmpty()
            isBulkAssignVisible.value = true
        }

        fun dismissBulkAssign() {
            isBulkAssignVisible.value = false
        }

        fun openManagement() {
            isManagementVisible.value = true
        }

        fun dismissManagement() {
            isManagementVisible.value = false
        }

        fun toggleBulkTag(tagId: String) {
            selectedBulkTagIds.update { current -> current.toggle(tagId) }
        }

        fun toggleBulkPlaylist(playlistId: String) {
            selectedBulkPlaylistIds.update { current -> current.toggle(playlistId) }
        }

        fun saveSelectedPlaylistTags(onSaved: () -> Unit) {
            val currentPlaylistId = playlistId.value ?: return
            val state = screenState.value as? PlaylistTagsScreenState.Success ?: return
            launchWrite {
                savePlaylistTags(
                    playlistId = currentPlaylistId,
                    tagIds = state.selectedTagIds,
                )
                selectedTagIds.value = null
                onSaved()
            }
        }

        fun saveBulkAssignments(onSaved: () -> Unit) {
            val state = screenState.value as? PlaylistTagsScreenState.Success ?: return
            if (state.selectedBulkPlaylistIds.isEmpty() || state.selectedBulkTagIds.isEmpty()) return
            launchWrite {
                addTagsToPlaylists(
                    playlistIds = state.selectedBulkPlaylistIds,
                    tagIds = state.selectedBulkTagIds,
                )
                isBulkAssignVisible.value = false
                onSaved()
            }
        }

        fun openCreateEditor() {
            editor.value =
                PlaylistTagEditorState.Visible(
                    tagId = null,
                    name = "",
                    color = TagEntity.DEFAULT_COLORS.first(),
                    canSave = false,
                )
        }

        fun openEditEditor(tagId: String) {
            val tag = currentTags().firstOrNull { currentTag -> currentTag.id == tagId } ?: return
            editor.value =
                PlaylistTagEditorState.Visible(
                    tagId = tag.id,
                    name = tag.name,
                    color = tag.color,
                    canSave = tag.name.isNotBlank(),
                )
        }

        fun updateEditorName(name: String) {
            editor.update { current ->
                when (current) {
                    PlaylistTagEditorState.Hidden -> {
                        current
                    }

                    is PlaylistTagEditorState.Visible -> {
                        current.copy(
                            name = name,
                            canSave = name.trim().isNotEmpty(),
                        )
                    }
                }
            }
        }

        fun dismissEditor() {
            editor.value = PlaylistTagEditorState.Hidden
        }

        fun saveEditor() {
            val current = editor.value as? PlaylistTagEditorState.Visible ?: return
            if (!current.canSave) return

            launchWrite {
                if (current.tagId == null) {
                    createPlaylistTag(name = current.name, color = current.color)
                } else {
                    updatePlaylistTag(
                        tagId = current.tagId,
                        name = current.name,
                        color = current.color,
                    )
                }
                editor.value = PlaylistTagEditorState.Hidden
            }
        }

        fun deleteTag(tagId: String) {
            launchWrite {
                deletePlaylistTag(tagId = tagId)
            }
        }

        fun openEditorColorPicker() {
            val current = editor.value as? PlaylistTagEditorState.Visible ?: return
            colorPicker.value =
                PlaylistTagColorPickerState.Visible(
                    selectedColor = current.color,
                    tagId = current.tagId,
                    isEditorTarget = true,
                    colors = ImmutableList.copyOf(TagEntity.DEFAULT_COLORS),
                )
        }

        fun openTagColorPicker(tagId: String) {
            val tag = currentTags().firstOrNull { currentTag -> currentTag.id == tagId } ?: return
            colorPicker.value =
                PlaylistTagColorPickerState.Visible(
                    selectedColor = tag.color,
                    tagId = tag.id,
                    isEditorTarget = false,
                    colors = ImmutableList.copyOf(TagEntity.DEFAULT_COLORS),
                )
        }

        fun selectColor(color: String) {
            val current = colorPicker.value as? PlaylistTagColorPickerState.Visible ?: return
            if (current.isEditorTarget) {
                editor.update { editorState ->
                    when (editorState) {
                        PlaylistTagEditorState.Hidden -> editorState
                        is PlaylistTagEditorState.Visible -> editorState.copy(color = color)
                    }
                }
                colorPicker.value = PlaylistTagColorPickerState.Hidden
                return
            }

            val tagId = current.tagId ?: return
            launchWrite {
                updatePlaylistTagColor(
                    tagId = tagId,
                    color = color,
                )
                colorPicker.value = PlaylistTagColorPickerState.Hidden
            }
        }

        fun dismissColorPicker() {
            colorPicker.value = PlaylistTagColorPickerState.Hidden
        }

        private fun launchWrite(block: suspend () -> Unit) {
            if (writeJob?.isActive == true) return
            writeJob =
                viewModelScope.launch {
                    try {
                        operationError.value = null
                        block()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        operationError.value = R.string.error_unknown
                    }
                }
        }

        private fun currentTags(): List<PlaylistTagUiModel> =
            when (val state = screenState.value) {
                is PlaylistTagsScreenState.Success -> state.tags

                PlaylistTagsScreenState.Empty,
                is PlaylistTagsScreenState.Error,
                PlaylistTagsScreenState.Loading,
                -> emptyList()
            }

        private fun List<PlaylistTagModel>.mapTagsToUiModels(): List<PlaylistTagUiModel> =
            map { tag ->
                PlaylistTagUiModel(
                    id = tag.id,
                    name = tag.name,
                    color = tag.color,
                )
            }

        private fun List<PlaylistTagPlaylistModel>.mapPlaylistsToUiModels(): List<PlaylistTagPlaylistUiModel> =
            map { playlist ->
                PlaylistTagPlaylistUiModel(
                    id = playlist.id,
                    name = playlist.name,
                    songCount = playlist.songCount,
                )
            }

        private fun Set<String>.toggle(value: String): Set<String> =
            if (value in this) {
                this - value
            } else {
                this + value
            }

        private fun Set<String>.sanitize(validIds: Set<String>): Set<String> = filterTo(LinkedHashSet()) { id -> id in validIds }
    }
