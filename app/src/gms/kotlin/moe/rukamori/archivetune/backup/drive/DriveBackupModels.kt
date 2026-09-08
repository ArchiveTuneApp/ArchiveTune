package moe.rukamori.archivetune.backup.drive

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.R
import java.io.IOException

enum class DriveBackupFrequency(@param:StringRes val labelRes: Int, val days: Long) {
    MANUAL(R.string.drive_backup_manual, 0),
    DAILY(R.string.scheduled_backup_daily, 1),
    WEEKLY(R.string.scheduled_backup_weekly, 7),
    MONTHLY(R.string.scheduled_backup_monthly, 30),
}

@Immutable
data class DriveBackupSettings(
    val account: String? = null,
    val frequency: DriveBackupFrequency = DriveBackupFrequency.MANUAL,
    val wifiOnly: Boolean = true,
    val backup: DriveBackupMetadata? = null,
    val lastFailure: DriveBackupFailure? = null,
    val failureAt: Long = 0,
)

@Immutable
data class DriveBackupMetadata(
    val id: String,
    val modifiedAt: Long,
    val size: Long,
    val checksum: String,
)

enum class DriveBackupFailure(@param:StringRes val messageRes: Int) {
    AUTHORIZATION(R.string.drive_backup_authorization_failed),
    CONFIGURATION(R.string.drive_backup_configuration_error),
    NETWORK(R.string.drive_backup_network_error),
    QUOTA(R.string.drive_backup_quota_error),
    MISSING(R.string.drive_backup_not_found),
    INVALID(R.string.restore_corrupted),
    STORAGE(R.string.drive_backup_storage_error),
    UNKNOWN(R.string.drive_backup_failed),
}

class DriveBackupException(val failure: DriveBackupFailure) : IOException(failure.name)

@Immutable
data class DriveBackupUiData(
    val account: String? = null,
    val frequency: DriveBackupFrequency = DriveBackupFrequency.MANUAL,
    val wifiOnly: Boolean = true,
    val hasBackup: Boolean = false,
    val backupDate: String? = null,
    val backupSize: String? = null,
    val busy: Boolean = false,
    @param:StringRes val statusRes: Int? = null,
    val progress: Int? = null,
    val showFrequencyPicker: Boolean = false,
)

sealed interface DriveBackupScreenState {
    data object Loading : DriveBackupScreenState
    data object Empty : DriveBackupScreenState
    @Immutable data class Success(val data: DriveBackupUiData) : DriveBackupScreenState
    @Immutable data class Error(val failure: DriveBackupFailure, val data: DriveBackupUiData) : DriveBackupScreenState
}
