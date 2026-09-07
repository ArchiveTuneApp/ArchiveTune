/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.canvas.CanvasPlaybackRequest
import moe.rukamori.archivetune.canvas.CanvasPlaybackUseCase
import moe.rukamori.archivetune.canvas.CanvasVideo
import timber.log.Timber
import javax.inject.Inject

@Immutable
sealed interface CanvasPlaybackState {
    data object Loading : CanvasPlaybackState
    data class Success(val request: CanvasPlaybackRequest, val video: CanvasVideo) : CanvasPlaybackState
    data object Empty : CanvasPlaybackState
    data class Error(val messageRes: Int) : CanvasPlaybackState
}

@HiltViewModel
class CanvasPlaybackViewModel @Inject constructor(
    private val useCase: CanvasPlaybackUseCase,
) : ViewModel() {
    private val request = MutableStateFlow<CanvasPlaybackRequest?>(null)
    private val mutableState = MutableStateFlow<CanvasPlaybackState>(CanvasPlaybackState.Empty)
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(request, useCase.policy, useCase.revision, useCase.spotifyConnected) { request, policy, _, _ -> request to policy }
                .collectLatest { (request, policy) ->
                    if (request == null || !policy.ready || !policy.configuration.enabled) {
                        mutableState.value = CanvasPlaybackState.Empty
                        return@collectLatest
                    }
                    mutableState.value = CanvasPlaybackState.Loading
                    try {
                        val video = useCase.load(request, policy)
                        mutableState.value = if (video == null) {
                            CanvasPlaybackState.Empty
                        } else {
                            CanvasPlaybackState.Success(request, video)
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Timber.w(error, "Canvas artwork resolution failed")
                        mutableState.value = CanvasPlaybackState.Error(R.string.canvas_refetch_failed)
                    }
                }
        }
    }

    fun setRequest(value: CanvasPlaybackRequest?) {
        request.value = value
    }
}
