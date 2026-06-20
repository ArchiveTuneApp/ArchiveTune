/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.utils.completed
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

enum class AccountContentType {
    PLAYLISTS,
    ALBUMS,
    ARTISTS,
}

@HiltViewModel
class AccountViewModel
    @Inject
    constructor() : ViewModel() {
        val playlists = MutableStateFlow<List<PlaylistItem>?>(null)
        val albums = MutableStateFlow<List<AlbumItem>?>(null)
        val artists = MutableStateFlow<List<ArtistItem>?>(null)

        // Selected content type for chips
        val selectedContentType = MutableStateFlow(AccountContentType.PLAYLISTS)

        init {
            viewModelScope.launch {
                YouTube
                    .library("FEmusic_liked_playlists")
                    .completed()
                    .onSuccess {
                        playlists.value =
                            it.items
                                .filterIsInstance<PlaylistItem>()
                                .filterNot { it.id == "SE" }
                    }.onFailure {
                        reportException(it)
                    }
                YouTube
                    .library("FEmusic_liked_albums")
                    .completed()
                    .onSuccess {
                        albums.value = it.items.filterIsInstance<AlbumItem>()
                    }.onFailure {
                        reportException(it)
                    }
                YouTube
                    .library("FEmusic_library_corpus_artists")
                    .completed()
                    .onSuccess {
                        artists.value = it.items.filterIsInstance<ArtistItem>()
                    }.onFailure {
                        reportException(it)
                    }
            }
        }

        fun setSelectedContentType(contentType: AccountContentType) {
            selectedContentType.value = contentType
        }
    }
