/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

@HiltViewModel
class SpotifyAccountViewModel
    @Inject
    constructor(
        private val repository: SpotifyLibraryRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SpotifyAccountUiState(isLoading = true))
        val uiState: StateFlow<SpotifyAccountUiState> = _uiState.asStateFlow()

        init {
            restoreSession()
        }

        fun restoreSession() {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { repository.restoreSession() }
                    .onSuccess { session ->
                        _uiState.update {
                            it.copy(
                                isAuthenticated = session.isAuthenticated,
                                accountName = session.accountName,
                                accountAvatarUrl = session.accountAvatarUrl,
                                isLoading = false,
                            )
                        }
                        if (session.isAuthenticated) reloadPlaylists()
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        reportException(error)
                        _uiState.update {
                            it.copy(
                                isAuthenticated = false,
                                isLoading = false,
                                errorMessage = error.message,
                            )
                        }
                    }
            }
        }

        fun connectWithCookies(
            spDc: String,
            spKey: String,
        ) {
            if (spDc.isBlank()) return
            viewModelScope.launch(Dispatchers.IO) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                runCatching { repository.connectWithCookies(spDc = spDc, spKey = spKey) }
                    .onSuccess { session ->
                        _uiState.update {
                            it.copy(
                                isAuthenticated = true,
                                accountName = session.accountName,
                                accountAvatarUrl = session.accountAvatarUrl,
                                isLoading = false,
                            )
                        }
                        reloadPlaylists()
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        reportException(error)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message,
                            )
                        }
                    }
            }
        }

        fun reloadPlaylists() {
            if (_uiState.value.isLoading) return
            viewModelScope.launch(Dispatchers.IO) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                val playlists = repository.refreshPlaylists()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        playlistCount = playlists.size,
                        errorMessage = repository.errorMessage.value,
                    )
                }
            }
        }

        fun logout() {
            if (_uiState.value.isLoading) return
            viewModelScope.launch(Dispatchers.IO) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                runCatching { repository.logout() }
                    .onSuccess {
                        _uiState.value = SpotifyAccountUiState()
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        reportException(error)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message,
                            )
                        }
                    }
            }
        }

        fun dismissError() {
            _uiState.update { it.copy(errorMessage = null) }
        }
    }

@Immutable
data class SpotifyAccountUiState(
    val isAuthenticated: Boolean = false,
    val accountName: String = "",
    val accountAvatarUrl: String? = null,
    val playlistCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
