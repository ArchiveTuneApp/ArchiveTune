package moe.rukamori.archivetune.backup.drive

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.AccountPicker
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class DriveAuthorizationRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val client = Identity.getAuthorizationClient(context)

    fun accountPicker(): Intent {
        ensureAvailable()
        return AccountPicker.newChooseAccountIntent(
            AccountPicker.AccountChooserOptions.Builder()
                .setAllowableAccountsTypes(listOf("com.google"))
                .setAlwaysShowAccountPicker(true)
                .build(),
        )
    }

    suspend fun authorize(account: String): AuthorizationResult {
        ensureAvailable()
        return try {
            client.authorize(
                AuthorizationRequest.builder()
                    .setAccount(Account(account, "com.google"))
                    .setRequestedScopes(listOf(Scope(DRIVE_SCOPE)))
                    .build(),
            ).awaitResult()
        } catch (exception: ApiException) {
            throw DriveBackupException(
                if (exception.statusCode == 10) DriveBackupFailure.CONFIGURATION else DriveBackupFailure.AUTHORIZATION,
            )
        }
    }

    fun completeAuthorization(intent: Intent?): AuthorizationResult = try {
        client.getAuthorizationResultFromIntent(intent)
    } catch (exception: ApiException) {
        throw DriveBackupException(
            if (exception.statusCode == 10) DriveBackupFailure.CONFIGURATION else DriveBackupFailure.AUTHORIZATION,
        )
    }

    suspend fun token(account: String): String {
        val result = authorize(account)
        if (result.hasResolution()) throw DriveBackupException(DriveBackupFailure.AUTHORIZATION)
        return requireToken(result)
    }

    fun requireToken(result: AuthorizationResult): String {
        if (DRIVE_SCOPE !in result.grantedScopes) throw DriveBackupException(DriveBackupFailure.AUTHORIZATION)
        return result.accessToken?.takeIf(String::isNotBlank)
            ?: throw DriveBackupException(DriveBackupFailure.AUTHORIZATION)
    }

    suspend fun clearToken(token: String) {
        client.clearToken(ClearTokenRequest.builder().setToken(token).build()).awaitResult()
    }

    private fun ensureAvailable() {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
            throw DriveBackupException(DriveBackupFailure.AUTHORIZATION)
        }
    }

    private companion object {
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
