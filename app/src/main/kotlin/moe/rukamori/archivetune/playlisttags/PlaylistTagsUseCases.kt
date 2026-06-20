/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playlisttags

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import moe.rukamori.archivetune.repository.PlaylistTagsRepository
import javax.inject.Inject

@Immutable
data class PlaylistTagModel(
    val id: String,
    val name: String,
    val color: String,
)

@Immutable
data class PlaylistTagPlaylistModel(
    val id: String,
    val name: String,
    val songCount: Int,
)

data class PlaylistTagsSnapshot(
    val tags: List<PlaylistTagModel>,
    val selectedTagIds: Set<String>,
    val playlists: List<PlaylistTagPlaylistModel>,
)

class ObservePlaylistTagsUseCase
    @Inject
    constructor(
        private val repository: PlaylistTagsRepository,
    ) {
        operator fun invoke(playlistId: String?): Flow<PlaylistTagsSnapshot> {
            val currentTags =
                playlistId
                    ?.let(repository::observePlaylistTags)
                    ?: flowOf(emptyList())

            return combine(
                repository.observeTags(),
                currentTags,
                repository.observeEditablePlaylists(),
            ) { tags, selectedTags, playlists ->
                PlaylistTagsSnapshot(
                    tags =
                        tags.map { tag ->
                            PlaylistTagModel(
                                id = tag.id,
                                name = tag.name,
                                color = tag.color,
                            )
                        },
                    selectedTagIds = selectedTags.mapTo(LinkedHashSet()) { tag -> tag.id },
                    playlists =
                        playlists
                            .asReversed()
                            .map { playlist ->
                                PlaylistTagPlaylistModel(
                                    id = playlist.id,
                                    name = playlist.playlist.name,
                                    songCount = playlist.songCount,
                                )
                            },
                )
            }.flowOn(Dispatchers.Default)
        }
    }

class CreatePlaylistTagUseCase
    @Inject
    constructor(
        private val repository: PlaylistTagsRepository,
    ) {
        suspend operator fun invoke(
            name: String,
            color: String,
        ) {
            val normalizedName = name.trim()
            if (normalizedName.isEmpty()) return
            repository.createTag(name = normalizedName, color = color)
        }
    }

class UpdatePlaylistTagUseCase
    @Inject
    constructor(
        private val repository: PlaylistTagsRepository,
    ) {
        suspend operator fun invoke(
            tagId: String,
            name: String,
            color: String,
        ) {
            val normalizedName = name.trim()
            if (normalizedName.isEmpty()) return
            repository.updateTag(id = tagId, name = normalizedName, color = color)
        }
    }

class UpdatePlaylistTagColorUseCase
    @Inject
    constructor(
        private val repository: PlaylistTagsRepository,
    ) {
        suspend operator fun invoke(
            tagId: String,
            color: String,
        ) {
            repository.updateTagColor(
                tagId = tagId,
                color = color,
            )
        }
    }

class DeletePlaylistTagUseCase
    @Inject
    constructor(
        private val repository: PlaylistTagsRepository,
    ) {
        suspend operator fun invoke(tagId: String) {
            repository.deleteTag(tagId)
        }
    }

class SavePlaylistTagsUseCase
    @Inject
    constructor(
        private val repository: PlaylistTagsRepository,
    ) {
        suspend operator fun invoke(
            playlistId: String,
            tagIds: Set<String>,
        ) {
            repository.replacePlaylistTags(
                playlistId = playlistId,
                tagIds = tagIds.toList(),
            )
        }
    }

class AddTagsToPlaylistsUseCase
    @Inject
    constructor(
        private val repository: PlaylistTagsRepository,
    ) {
        suspend operator fun invoke(
            playlistIds: Set<String>,
            tagIds: Set<String>,
        ) {
            repository.addTagsToPlaylists(
                playlistIds = playlistIds.toList(),
                tagIds = tagIds.toList(),
            )
        }
    }
