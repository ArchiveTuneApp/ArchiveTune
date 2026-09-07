package moe.rukamori.archivetune.privacy

import javax.inject.Inject

class ResetListeningStatsUseCase @Inject constructor(
    private val repository: ListeningStatsRepository,
) {
    suspend operator fun invoke() = repository.reset()
}
