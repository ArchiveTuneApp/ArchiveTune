package moe.rukamori.archivetune.backup.drive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.utils.reportException

class DriveBackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val account = inputData.getString(DriveBackupScheduler.ACCOUNT) ?: return Result.failure()
        val useCases = EntryPointAccessors.fromApplication(applicationContext, DriveBackupEntryPoint::class.java).useCases()
        return try {
            setForeground(getForegroundInfo())
            useCases.upload(account) { status ->
                setProgress(workDataOf(DriveBackupScheduler.STATUS to status))
            }
            Result.success(workDataOf(DriveBackupScheduler.UPDATED_AT to System.currentTimeMillis()))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            reportException(exception)
            val failure = (exception as? DriveBackupException)?.failure ?: DriveBackupFailure.UNKNOWN
            try {
                useCases.recordFailure(account, failure)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (storageException: Exception) {
                reportException(storageException)
            }
            if (failure == DriveBackupFailure.NETWORK && runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure(
                    workDataOf(
                        DriveBackupScheduler.ERROR to failure.name,
                        DriveBackupScheduler.UPDATED_AT to System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.drive_backup_title), NotificationManager.IMPORTANCE_LOW),
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.backup)
            .setContentTitle(applicationContext.getString(R.string.drive_backup_title))
            .setContentText(applicationContext.getString(R.string.drive_backup_uploading))
            .setOngoing(true)
            .setSilent(true)
            .setProgress(0, 0, true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        const val CHANNEL_ID = "google_drive_backup"
        const val NOTIFICATION_ID = 2108
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DriveBackupEntryPoint {
    fun useCases(): DriveBackupUseCases
}
