/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

@Immutable
data class Song
    @JvmOverloads
    constructor(
        @Embedded val song: SongEntity,
        @Relation(
            entity = ArtistEntity::class,
            entityColumn = "id",
            parentColumn = "id",
            associateBy =
                Junction(
                    value = SortedSongArtistMap::class,
                    parentColumn = "songId",
                    entityColumn = "artistId",
                ),
        )
        val artists: List<ArtistEntity>,
        @Relation(
            entity = AlbumEntity::class,
            entityColumn = "id",
            parentColumn = "id",
            associateBy =
                Junction(
                    value = SongAlbumMap::class,
                    parentColumn = "songId",
                    entityColumn = "albumId",
                ),
        )
        val album: AlbumEntity? = null,
        @Relation(
            parentColumn = "id",
            entityColumn = "id",
        )
        val format: FormatEntity? = null,
    ) : LocalItem() {
        override val id: String
            get() = song.id
        override val title: String
            get() = song.title
        override val thumbnailUrl: String?
            get() = song.thumbnailUrl
    }
