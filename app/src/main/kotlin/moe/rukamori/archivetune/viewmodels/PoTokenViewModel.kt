/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

sealed interface PoTokenState {
    data object Idle : PoTokenState

    data class Success(
        val gvsToken: String,
        val playerToken: String,
        val visitorData: String,
    ) : PoTokenState

    data class Error(
        val message: String,
    ) : PoTokenState
}

@HiltViewModel
class PoTokenViewModel
    @Inject
    constructor() : ViewModel() {
        private val _state = MutableStateFlow<PoTokenState>(PoTokenState.Idle)
        val state: StateFlow<PoTokenState> = _state.asStateFlow()

        fun onTokensExtracted(
            visitorData: String,
            poToken: String,
            playerToken: String,
        ) {
            _state.value =
                PoTokenState.Success(
                    gvsToken = poToken,
                    playerToken = playerToken,
                    visitorData = visitorData,
                )
        }

        fun onExtractionError(message: String) {
            _state.value = PoTokenState.Error(message)
        }

        fun resetState() {
            _state.value = PoTokenState.Idle
        }
    }
