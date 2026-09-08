package moe.rukamori.archivetune.backup.drive

import android.content.Intent
import android.net.Uri
import com.google.android.gms.auth.api.identity.AuthorizationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.backup.BackupArchiveCategory
import moe.rukamori.archivetune.backup.CreateBackupUseCase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveBackupUseCases @Inject constructor(
    private val settings: DriveBackupSettingsRepository,
    private val authorization: DriveAuthorizationRepository,
    private val repository: DriveBackupRepository,
    private val createBackup: CreateBackupUseCase,
    private val scheduler: DriveBackupScheduler,
) {
    private val operationMutex = Mutex()

    fun observeSettings() = settings.observe()
    fun observeWork() = scheduler.observe()
    fun accountPicker(): Intent = authorization.accountPicker()
    suspend fun authorize(account: String): AuthorizationResult = authorization.authorize(account)
    fun authorizationResult(intent: Intent?): AuthorizationResult = authorization.completeAuthorization(intent)

    suspend fun connect(account: String, result: AuthorizationResult) = operationMutex.withLock {
        val token = authorization.requireToken(result)
        val backup = repository.latest(token)
        scheduler.cancelAll()
        settings.setAccount(account)
        settings.setBackup(account, backup)
        scheduler.schedule(settings.get())
    }

    suspend fun refresh() = operationMutex.withLock {
        val current = settings.get()
        val account = current.account ?: return@withLock
        val backup = withToken(account) { repository.latest(it) }
        settings.setBackup(account, backup)
        scheduler.schedule(current)
    }

    suspend fun disconnect() = operationMutex.withLock {
        scheduler.cancelAll()
        settings.setAccount(null)
    }

    suspend fun updateSchedule(frequency: DriveBackupFrequency? = null, wifiOnly: Boolean? = null) = operationMutex.withLock {
        val current = settings.get()
        if (current.account == null) throw DriveBackupException(DriveBackupFailure.AUTHORIZATION)
        val updated = current.copy(frequency = frequency ?: current.frequency, wifiOnly = wifiOnly ?: current.wifiOnly)
        settings.setSchedule(updated.frequency, updated.wifiOnly)
        scheduler.schedule(updated)
    }

    suspend fun backupNow() = scheduler.backupNow(settings.get())
    suspend fun cancelBackup() {
        scheduler.cancelAll()
        scheduler.schedule(settings.get())
    }

    suspend fun recordFailure(account: String, failure: DriveBackupFailure) = settings.setFailure(account, failure)

    suspend fun upload(account: String, onStatus: suspend (Int) -> Unit) = operationMutex.withLock {
        if (settings.get().account != account) return@withLock
        withContext(Dispatchers.IO) {
            val archive = repository.createTemporaryArchive()
            try {
                onStatus(R.string.drive_backup_preparing)
                createBackup(Uri.fromFile(archive), BackupArchiveCategory.entries.toSet())
                onStatus(R.string.drive_backup_uploading)
                val backup = withToken(account) { repository.upload(it, archive) }
                settings.setBackup(account, backup)
            } finally {
                withContext(NonCancellable) { repository.deleteTemporaryArchive(archive) }
            }
        }
    }

    suspend fun download(onProgress: (Int) -> Unit): Uri = operationMutex.withLock {
        val account = settings.get().account ?: throw DriveBackupException(DriveBackupFailure.AUTHORIZATION)
        withToken(account) { token ->
            val backup = repository.latest(token) ?: throw DriveBackupException(DriveBackupFailure.MISSING)
            settings.setBackup(account, backup)
            Uri.fromFile(repository.download(token, backup, onProgress))
        }
    }

    private suspend fun <T> withToken(account: String, action: suspend (String) -> T): T {
        val token = authorization.token(account)
        return try {
            action(token)
        } catch (exception: DriveBackupException) {
            if (exception.failure != DriveBackupFailure.AUTHORIZATION) throw exception
            authorization.clearToken(token)
            action(authorization.token(account))
        }
    }
}
