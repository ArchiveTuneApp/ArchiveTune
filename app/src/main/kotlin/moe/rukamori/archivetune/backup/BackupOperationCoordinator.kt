package moe.rukamori.archivetune.backup

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupOperationCoordinator @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> withLock(operation: suspend () -> T): T = mutex.withLock { operation() }
}
