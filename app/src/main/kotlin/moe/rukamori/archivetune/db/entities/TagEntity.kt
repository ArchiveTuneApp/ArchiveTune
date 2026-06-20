/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.apache.commons.lang3.RandomStringUtils
import java.time.LocalDateTime

@Immutable
@Entity(tableName = "tag")
data class TagEntity(
    @PrimaryKey val id: String = generateTagId(),
    val name: String,
    @ColumnInfo(name = "color", defaultValue = "#FF6B6B")
    val color: String = "#FF6B6B",
    @ColumnInfo(name = "createdAt", defaultValue = "CURRENT_TIMESTAMP")
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    companion object {
        fun generateTagId() = "TAG" + RandomStringUtils.insecure().next(8, true, false)

        val DEFAULT_COLORS =
            listOf(
                "#FF6B6B",
                "#4ECDC4",
                "#45B7D1",
                "#FFA07A",
                "#98D8C8",
                "#F7DC6F",
                "#BB8FCE",
                "#85C1E2",
                "#F8B739",
                "#52B788",
                "#E63946",
                "#457B9D",
                "#F4A261",
                "#2A9D8F",
                "#E76F51",
                "#264653",
                "#E9C46A",
                "#F4A6C1",
                "#8E44AD",
                "#16A085",
                "#C0392B",
                "#D35400",
                "#7D3C98",
                "#1ABC9C",
                "#3498DB",
            )
    }
}

@Immutable
@Entity(
    tableName = "playlist_tag_map",
    primaryKeys = ["playlistId", "tagId"],
)
data class PlaylistTagMap(
    val playlistId: String,
    val tagId: String,
    @ColumnInfo(name = "createdAt", defaultValue = "CURRENT_TIMESTAMP")
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

data class PlaylistWithTags(
    val playlist: Playlist,
    val tags: List<TagEntity>,
)
