/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.TagEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistTagsRepository
    @Inject
    constructor(
        private val database: MusicDatabase,
    ) {
        fun observeTags(): Flow<List<TagEntity>> =
            database
                .allTags()
                .flowOn(Dispatchers.IO)

        fun observePlaylistTags(playlistId: String): Flow<List<TagEntity>> =
            database
                .playlistTags(playlistId)
                .flowOn(Dispatchers.IO)

        fun observeEditablePlaylists(): Flow<List<Playlist>> =
            database
                .editablePlaylistsByCreateDateAsc()
                .flowOn(Dispatchers.IO)

        suspend fun createTag(
            name: String,
            color: String,
        ) = withContext(Dispatchers.IO) {
            database.withTransaction {
                insert(TagEntity(name = name, color = color))
            }
        }

        suspend fun updateTag(
            id: String,
            name: String,
            color: String,
        ) = withContext(Dispatchers.IO) {
            val currentTag = database.tag(id).first() ?: return@withContext
            database.withTransaction {
                update(currentTag.copy(name = name, color = color))
            }
        }

        suspend fun updateTagColor(
            tagId: String,
            color: String,
        ) = withContext(Dispatchers.IO) {
            val currentTag = database.tag(tagId).first() ?: return@withContext
            database.withTransaction {
                update(currentTag.copy(color = color))
            }
        }

        suspend fun deleteTag(tagId: String) =
            withContext(Dispatchers.IO) {
                val currentTag = database.tag(tagId).first() ?: return@withContext
                database.withTransaction {
                    deleteTag(currentTag)
                }
            }

        suspend fun replacePlaylistTags(
            playlistId: String,
            tagIds: List<String>,
        ) = withContext(Dispatchers.IO) {
            database.withTransaction {
                removeAllPlaylistTags(playlistId)
                tagIds.forEach { tagId ->
                    addTagToPlaylist(playlistId, tagId)
                }
            }
        }

        suspend fun addTagsToPlaylists(
            playlistIds: List<String>,
            tagIds: List<String>,
        ) = withContext(Dispatchers.IO) {
            database.withTransaction {
                addTagsToPlaylists(
                    playlistIds = playlistIds,
                    tagIds = tagIds,
                )
            }
        }
    }
