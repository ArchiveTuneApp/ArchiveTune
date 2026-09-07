package moe.rukamori.archivetune.viewmodels

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.privacy.ResetListeningStatsUseCase
import timber.log.Timber
import javax.inject.Inject

@Immutable
sealed interface ResetListeningStatsState {
    data object Loading : ResetListeningStatsState
    data class Success(val showConfirmation: Boolean) : ResetListeningStatsState
    data object Empty : ResetListeningStatsState
    data class Error(@param:StringRes val messageRes: Int) : ResetListeningStatsState
}

@HiltViewModel
class ResetListeningStatsViewModel @Inject constructor(
    private val resetListeningStats: ResetListeningStatsUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ResetListeningStatsState>(ResetListeningStatsState.Empty)
    val state = mutableState.asStateFlow()

    fun requestReset() {
        if (mutableState.value == ResetListeningStatsState.Loading) return
        mutableState.value = ResetListeningStatsState.Success(showConfirmation = true)
    }

    fun dismissDialog() {
        if (mutableState.value == ResetListeningStatsState.Loading) return
        mutableState.value = ResetListeningStatsState.Empty
    }

    fun confirmReset() {
        val currentState = mutableState.value as? ResetListeningStatsState.Success ?: return
        if (!currentState.showConfirmation) return
        mutableState.value = ResetListeningStatsState.Loading
        viewModelScope.launch {
            try {
                resetListeningStats()
                mutableState.value = ResetListeningStatsState.Success(showConfirmation = false)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Timber.e(exception, "Failed to reset listening stats")
                mutableState.value = ResetListeningStatsState.Error(R.string.reset_listening_stats_error)
            }
        }
    }
}
