/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.TagEntity

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaylistTagChips(
    database: MusicDatabase,
    playlistId: String,
    editable: Boolean = false,
    onTagClick: ((TagEntity) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tags by database.playlistTags(playlistId).collectAsState(initial = emptyList())

    if (tags.isNotEmpty()) {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tags.forEach { tag ->
                TagChip(
                    tag = tag,
                    editable = editable,
                    onClick = { onTagClick?.invoke(tag) },
                    onRemove =
                        if (editable) {
                            { database.transaction { removePlaylistTag(playlistId, tag.id) } }
                        } else {
                            null
                        },
                )
            }
        }
    }
}

@Composable
fun TagChip(
    tag: TagEntity,
    selected: Boolean = false,
    editable: Boolean = false,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val backgroundColor =
        remember(tag.color, selected) {
            if (selected) {
                Color(tag.color.toColorInt()).copy(alpha = 0.35f)
            } else {
                Color(tag.color.toColorInt()).copy(alpha = 0.12f)
            }
        }

    val contentColor =
        remember(tag.color) {
            Color(tag.color.toColorInt())
        }

    val animatedBackgroundColor by animateColorAsState(
        targetValue = backgroundColor,
        label = "tagBackgroundColor",
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = animatedBackgroundColor,
        border =
            BorderStroke(
                width = if (selected) 2.5.dp else 1.5.dp,
                color = contentColor,
            ),
        modifier = modifier,
    ) {
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(onClick = onClick)
                        } else {
                            Modifier
                        },
                    ).padding(
                        start = 8.dp,
                        end = if (editable && onRemove != null) 4.dp else 12.dp,
                        top = 6.dp,
                        bottom = 6.dp,
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(contentColor),
                contentAlignment = Alignment.Center,
            )
            {
                if (selected) {
                    Icon(
                        painter = painterResource(R.drawable.check),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }

            Text(
                text = tag.name,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )

            if (editable && onRemove != null) {
                Spacer(modifier = Modifier.width(2.dp))

                Box(
                    modifier =
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onRemove)
                            .background(contentColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsFilterChips(
    database: MusicDatabase,
    selectedTags: Set<String>,
    onTagToggle: (TagEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val allTags by database.allTags().collectAsState(initial = emptyList())

    if (allTags.isNotEmpty()) {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            allTags.forEach { tag ->
                TagChip(
                    tag = tag,
                    selected = tag.id in selectedTags,
                    onClick = { onTagToggle(tag) },
                )
            }
        }
    }
}
