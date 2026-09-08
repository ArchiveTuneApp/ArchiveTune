package moe.rukamori.archivetune.backup.drive

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.guava.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveBackupScheduler @Inject constructor(@ApplicationContext context: Context) {
    private val workManager = WorkManager.getInstance(context)

    fun observe() = workManager.getWorkInfosByTagFlow(TAG)

    suspend fun schedule(settings: DriveBackupSettings) {
        val account = settings.account
        if (account == null || settings.frequency == DriveBackupFrequency.MANUAL) {
            workManager.cancelUniqueWork(PERIODIC_NAME).result.await()
            return
        }
        val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(settings.frequency.days, TimeUnit.DAYS)
            .setInitialDelay(settings.frequency.days, TimeUnit.DAYS)
            .setConstraints(constraints(settings))
            .setInputData(workDataOf(ACCOUNT to account))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, request).result.await()
    }

    suspend fun backupNow(settings: DriveBackupSettings) {
        val account = settings.account ?: throw DriveBackupException(DriveBackupFailure.AUTHORIZATION)
        val request = OneTimeWorkRequestBuilder<DriveBackupWorker>()
            .setConstraints(constraints(settings))
            .setInputData(workDataOf(ACCOUNT to account, MANUAL to true))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .addTag(MANUAL)
            .build()
        workManager.enqueueUniqueWork(MANUAL_NAME, ExistingWorkPolicy.KEEP, request).result.await()
    }

    suspend fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG).result.await()
    }

    private fun constraints(settings: DriveBackupSettings) = Constraints.Builder()
        .setRequiredNetworkType(if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresStorageNotLow(true)
        .build()

    companion object {
        const val TAG = "google-drive-backup"
        const val ACCOUNT = "account"
        const val MANUAL = "manual-drive-backup"
        const val STATUS = "status"
        const val ERROR = "error"
        const val UPDATED_AT = "updated_at"
        private const val MANUAL_NAME = "google-drive-backup-now"
        private const val PERIODIC_NAME = "google-drive-backup-periodic"

        fun isActive(work: WorkInfo): Boolean = work.state == WorkInfo.State.RUNNING ||
            (MANUAL in work.tags && !work.state.isFinished)
    }
}
