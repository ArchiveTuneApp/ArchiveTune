/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.cast

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class CastViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = CastPlaybackRepositoryLocator.get(application)
    private val observeCastStateUseCase = ObserveCastStateUseCase(repository)
    private val disconnectCastSessionUseCase = DisconnectCastSessionUseCase(repository)
    private val setCastVolumeUseCase = SetCastVolumeUseCase(repository)

    val screenState = observeCastStateUseCase()

    fun disconnect() = disconnectCastSessionUseCase()

    fun setVolume(volume: Float) = setCastVolumeUseCase(volume)
}
