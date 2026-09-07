/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.podcast.LoadPodcastContinuationUseCase
import moe.rukamori.archivetune.podcast.LoadPodcastUseCase
import moe.rukamori.archivetune.podcast.PodcastAction
import moe.rukamori.archivetune.podcast.PodcastEvent
import moe.rukamori.archivetune.podcast.PodcastPlaybackRequest
import moe.rukamori.archivetune.podcast.PodcastScreenState
import javax.inject.Inject

@HiltViewModel
class PodcastViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val loadPodcast: LoadPodcastUseCase,
        private val loadPodcastContinuation: LoadPodcastContinuationUseCase,
    ) : ViewModel() {
        private val browseId = Uri.decode(savedStateHandle.get<String>("browseId").orEmpty()).trim()
        private val _screenState = MutableStateFlow<PodcastScreenState>(PodcastScreenState.Loading)
        val screenState = _screenState.asStateFlow()
        private val eventChannel = Channel<PodcastEvent>(Channel.BUFFERED)
        val events = eventChannel.receiveAsFlow()
        private var continuation: String? = null
        private var loadJob: Job? = null
        private var paginationJob: Job? = null

        init {
            load()
        }

        fun onAction(action: PodcastAction) {
            when (action) {
                PodcastAction.Retry -> load()
                PodcastAction.LoadMore -> loadMore()
                PodcastAction.PlayAll -> playAll()
                is PodcastAction.PlayEpisode -> playEpisode(action.episodeId)
            }
        }

        private fun load() {
            paginationJob?.cancel()
            loadJob?.cancel()
            _screenState.value = PodcastScreenState.Loading
            loadJob =
                viewModelScope.launch {
                    loadPodcast(browseId)
                        .onSuccess { result ->
                            continuation = result.continuation
                            _screenState.value =
                                if (result.uiState.episodes.isEmpty()) {
                                    PodcastScreenState.Empty
                                } else {
                                    PodcastScreenState.Success(result.uiState)
                                }
                        }.onFailure {
                            continuation = null
                            _screenState.value = PodcastScreenState.Error(R.string.error_unknown)
                        }
                }
        }

        private fun loadMore() {
            if (paginationJob?.isActive == true) return
            val current = _screenState.value as? PodcastScreenState.Success ?: return
            val nextContinuation = continuation?.takeIf(String::isNotBlank) ?: return
            _screenState.value = current.copy(uiState = current.uiState.copy(isLoadingMore = true))
            paginationJob =
                viewModelScope.launch {
                    try {
                        loadPodcastContinuation(
                            continuation = nextContinuation,
                            podcastTitle = current.uiState.title,
                            podcastBrowseId = current.uiState.browseId,
                        ).onSuccess { result ->
                            val latest = _screenState.value as? PodcastScreenState.Success ?: return@onSuccess
                            val existingIds = latest.uiState.episodes.mapTo(HashSet()) { it.id }
                            val appended = result.episodes.filterNot { it.id in existingIds }
                            continuation = result.continuation
                            _screenState.value =
                                latest.copy(
                                    uiState =
                                        latest.uiState.copy(
                                            episodes = ImmutableList.copyOf(latest.uiState.episodes + appended),
                                            canLoadMore = !result.continuation.isNullOrBlank(),
                                        ),
                                )
                        }.onFailure {
                            eventChannel.send(PodcastEvent.ShowMessage(R.string.error_unknown))
                        }
                    } finally {
                        val latest = _screenState.value as? PodcastScreenState.Success
                        if (latest != null) {
                            _screenState.value = latest.copy(uiState = latest.uiState.copy(isLoadingMore = false))
                        }
                    }
                }
        }

        private fun playEpisode(episodeId: String) {
            val current = (_screenState.value as? PodcastScreenState.Success)?.uiState ?: return
            val startIndex = current.episodes.indexOfFirst { it.id == episodeId }
            if (startIndex < 0) return
            eventChannel.trySend(
                PodcastEvent.Play(
                    PodcastPlaybackRequest(
                        title = current.title,
                        items = ImmutableList.copyOf(current.episodes.map { it.playbackMetadata }),
                        startIndex = startIndex,
                    ),
                ),
            )
        }

        private fun playAll() {
            val current = (_screenState.value as? PodcastScreenState.Success)?.uiState ?: return
            if (current.episodes.isEmpty()) return
            eventChannel.trySend(
                PodcastEvent.Play(
                    PodcastPlaybackRequest(
                        title = current.title,
                        items = ImmutableList.copyOf(current.episodes.map { it.playbackMetadata }),
                        startIndex = 0,
                    ),
                ),
            )
        }
    }
