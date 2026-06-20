/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import moe.rukamori.archivetune.constants.MyTopFilter
import moe.rukamori.archivetune.db.MusicDatabase
import javax.inject.Inject

@HiltViewModel
class TopPlaylistViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        database: MusicDatabase,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val top = savedStateHandle.get<String>("top")!!

        val topPeriod = MutableStateFlow(MyTopFilter.ALL_TIME)

        @OptIn(ExperimentalCoroutinesApi::class)
        val topSongs =
            topPeriod
                .flatMapLatest { period ->
                    database.mostPlayedSongs(period.toTimeMillis(), top.toInt())
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    }
