package moe.rukamori.archivetune.privacy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import javax.inject.Inject

class ListeningStatsRepository @Inject constructor(
    private val database: MusicDatabase,
) {
    suspend fun reset() = withContext(Dispatchers.IO) {
        database.withTransaction {
            clearListenHistory()
            resetTotalPlayTime()
            clearPlayCounts()
        }
    }
}
