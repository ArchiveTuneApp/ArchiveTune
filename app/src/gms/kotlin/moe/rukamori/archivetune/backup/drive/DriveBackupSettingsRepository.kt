package moe.rukamori.archivetune.backup.drive

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.driveBackupDataStore by preferencesDataStore(name = "google_drive_backup")

@Singleton
class DriveBackupSettingsRepository @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.driveBackupDataStore

    fun observe(): Flow<DriveBackupSettings> = store.data.map { preferences ->
        val id = preferences[BACKUP_ID]
        DriveBackupSettings(
            account = preferences[ACCOUNT],
            frequency = DriveBackupFrequency.entries.firstOrNull { it.name == preferences[FREQUENCY] }
                ?: DriveBackupFrequency.MANUAL,
            wifiOnly = preferences[WIFI_ONLY] ?: true,
            backup = id?.let {
                DriveBackupMetadata(it, preferences[MODIFIED_AT] ?: 0, preferences[SIZE] ?: 0, preferences[CHECKSUM].orEmpty())
            },
            lastFailure = DriveBackupFailure.entries.firstOrNull { it.name == preferences[FAILURE] },
            failureAt = preferences[FAILURE_AT] ?: 0,
        )
    }

    suspend fun get(): DriveBackupSettings = observe().first()

    suspend fun setAccount(account: String?) {
        store.edit { preferences ->
            if (preferences[ACCOUNT] != account) {
                preferences.clear()
                if (account != null) preferences[ACCOUNT] = account
            }
        }
    }

    suspend fun setSchedule(frequency: DriveBackupFrequency, wifiOnly: Boolean) {
        store.edit {
            it[FREQUENCY] = frequency.name
            it[WIFI_ONLY] = wifiOnly
        }
    }

    suspend fun setBackup(account: String, backup: DriveBackupMetadata?) {
        store.edit {
            if (it[ACCOUNT] != account) return@edit
            it.remove(FAILURE)
            it.remove(FAILURE_AT)
            if (backup == null) {
                it.remove(BACKUP_ID)
                it.remove(MODIFIED_AT)
                it.remove(SIZE)
                it.remove(CHECKSUM)
            } else {
                it[BACKUP_ID] = backup.id
                it[MODIFIED_AT] = backup.modifiedAt
                it[SIZE] = backup.size
                it[CHECKSUM] = backup.checksum
            }
        }
    }

    suspend fun setFailure(account: String, failure: DriveBackupFailure) {
        store.edit {
            if (it[ACCOUNT] != account) return@edit
            it[FAILURE] = failure.name
            it[FAILURE_AT] = System.currentTimeMillis()
        }
    }

    private companion object {
        val ACCOUNT = stringPreferencesKey("account")
        val FREQUENCY = stringPreferencesKey("frequency")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val BACKUP_ID = stringPreferencesKey("backup_id")
        val MODIFIED_AT = longPreferencesKey("modified_at")
        val SIZE = longPreferencesKey("size")
        val CHECKSUM = stringPreferencesKey("checksum")
        val FAILURE = stringPreferencesKey("failure")
        val FAILURE_AT = longPreferencesKey("failure_at")
    }
}
