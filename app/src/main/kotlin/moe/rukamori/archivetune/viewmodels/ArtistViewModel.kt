/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.extensions.filterExplicit
import moe.rukamori.archivetune.extensions.filterExplicitAlbums
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.filterExplicit
import moe.rukamori.archivetune.innertube.pages.ArtistPage
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArtistViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val artistId = savedStateHandle.get<String>("artistId")!!
        var artistPage by mutableStateOf<ArtistPage?>(null)
        val libraryArtist =
            database
                .artist(artistId)
                .stateIn(viewModelScope, SharingStarted.Lazily, null)
        val librarySongs =
            context.dataStore.data
                .map { it[HideExplicitKey] ?: false }
                .distinctUntilChanged()
                .flatMapLatest { hideExplicit ->
                    database.artistSongsByCreateDateAsc(artistId).map { it.filterExplicit(hideExplicit) } // show all
                    // database.artistSongsPreview(artistId).map { it.filterExplicit(hideExplicit) } // only preview
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        val libraryAlbums =
            context.dataStore.data
                .map { it[HideExplicitKey] ?: false }
                .distinctUntilChanged()
                .flatMapLatest { hideExplicit ->
                    database.artistAlbumsPreview(artistId).map { it.filterExplicitAlbums(hideExplicit) }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        init {
            // Load artist page and reload when hide explicit setting changes
            viewModelScope.launch {
                context.dataStore.data
                    .map { it[HideExplicitKey] ?: false }
                    .distinctUntilChanged()
                    .collect {
                        fetchArtistsFromYTM()
                    }
            }
        }

        fun fetchArtistsFromYTM() {
            viewModelScope.launch {
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                YouTube
                    .artist(artistId)
                    .onSuccess { page ->
                        val filteredSections =
                            page.sections
                                .map { section ->
                                    section.copy(items = section.items.filterExplicit(hideExplicit))
                                }

                        artistPage = page.copy(sections = filteredSections)

                        withContext(Dispatchers.IO) {
                            database.artist(artistId).firstOrNull()?.artist?.let { artistEntity ->
                                database.update(artistEntity, page)
                            }
                        }
                    }.onFailure {
                        reportException(it)
                    }
            }
        }
    }
