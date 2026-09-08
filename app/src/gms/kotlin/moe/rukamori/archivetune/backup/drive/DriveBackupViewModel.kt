package moe.rukamori.archivetune.backup.drive

import android.accounts.AccountManager
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.utils.reportException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

sealed interface DriveBackupEvent {
    data class ChooseAccount(val intent: Intent) : DriveBackupEvent
    data class Authorize(val intent: PendingIntent) : DriveBackupEvent
    data class Restore(val uri: Uri) : DriveBackupEvent
}

@HiltViewModel
class DriveBackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val useCases: DriveBackupUseCases,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private data class Interaction(
        val busy: Boolean = false,
        val statusRes: Int? = null,
        val progress: Int? = null,
        val failure: DriveBackupFailure? = null,
        val showFrequencyPicker: Boolean = false,
        val ignoredFailureBefore: Long = 0,
    )

    private val interaction = MutableStateFlow(Interaction())
    private val eventChannel = Channel<DriveBackupEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var actionJob: Job? = null

    val state: StateFlow<DriveBackupScreenState> = combine(useCases.observeSettings(), useCases.observeWork(), interaction) { settings, work, interaction ->
        val activeWork = work.firstOrNull(DriveBackupScheduler::isActive)
        val workFailure = settings.lastFailure.takeIf { settings.failureAt > interaction.ignoredFailureBefore }
        val backup = settings.backup
        val data = DriveBackupUiData(
            account = settings.account,
            frequency = settings.frequency,
            wifiOnly = settings.wifiOnly,
            hasBackup = backup != null,
            backupDate = backup?.let {
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it.modifiedAt))
            },
            backupSize = backup?.let { Formatter.formatShortFileSize(context, it.size) },
            busy = interaction.busy || activeWork != null,
            statusRes = interaction.statusRes ?: activeWork?.let {
                if (it.state == WorkInfo.State.RUNNING) {
                    it.progress.getInt(DriveBackupScheduler.STATUS, R.string.drive_backup_preparing)
                } else {
                    R.string.drive_backup_waiting
                }
            },
            progress = interaction.progress,
            showFrequencyPicker = interaction.showFrequencyPicker,
        )
        val failure = interaction.failure ?: workFailure
        when {
            failure != null -> DriveBackupScreenState.Error(failure, data)
            settings.account == null && !data.busy -> DriveBackupScreenState.Empty
            else -> DriveBackupScreenState.Success(data)
        }
    }.retryWhen { exception, _ ->
        if (exception is CancellationException) throw exception
        reportException(exception)
        emit(DriveBackupScreenState.Error(DriveBackupFailure.STORAGE, DriveBackupUiData()))
        delay(5_000)
        true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DriveBackupScreenState.Loading)

    init {
        if (savedStateHandle.get<String>(PENDING_ACCOUNT) == null && savedStateHandle.get<Boolean>(PICKING_ACCOUNT) != true) {
            refresh()
        }
    }

    fun chooseAccount() {
        if (isBusy() || savedStateHandle.get<Boolean>(PICKING_ACCOUNT) == true) return
        runAction {
            val intent = useCases.accountPicker()
            savedStateHandle[PICKING_ACCOUNT] = true
            eventChannel.send(DriveBackupEvent.ChooseAccount(intent))
        }
    }

    fun onAccountResult(resultCode: Int, intent: Intent?) {
        savedStateHandle[PICKING_ACCOUNT] = false
        if (resultCode != Activity.RESULT_OK) return
        val account = intent?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.takeIf(String::isNotBlank)
        val type = intent?.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE)
        if (account == null || type != "com.google") {
            onLaunchFailed()
            return
        }
        savedStateHandle[PENDING_ACCOUNT] = account
        runAction {
            val result = useCases.authorize(account)
            if (result.hasResolution()) {
                val resolution = result.pendingIntent ?: throw DriveBackupException(DriveBackupFailure.AUTHORIZATION)
                eventChannel.send(DriveBackupEvent.Authorize(resolution))
            } else {
                useCases.connect(account, result)
                savedStateHandle.remove<String>(PENDING_ACCOUNT)
            }
        }
    }

    fun onAuthorizationResult(resultCode: Int, intent: Intent?) {
        val account = savedStateHandle.remove<String>(PENDING_ACCOUNT) ?: return
        if (resultCode != Activity.RESULT_OK) return
        runAction { useCases.connect(account, useCases.authorizationResult(intent)) }
    }

    fun onLaunchFailed() {
        savedStateHandle[PICKING_ACCOUNT] = false
        savedStateHandle.remove<String>(PENDING_ACCOUNT)
        interaction.update { it.copy(failure = DriveBackupFailure.AUTHORIZATION) }
    }

    fun refresh() = runAction { useCases.refresh() }
    fun disconnect() {
        if (!isBusy()) runAction { useCases.disconnect() }
    }

    fun backup() {
        if (!isBusy()) runAction { useCases.backupNow() }
    }

    fun restore() {
        if (isBusy()) return
        runAction(R.string.drive_backup_downloading) {
            val uri = useCases.download { progress -> interaction.update { it.copy(progress = progress) } }
            eventChannel.send(DriveBackupEvent.Restore(uri))
        }
    }

    fun cancel() {
        val currentJob = actionJob
        currentJob?.cancel()
        viewModelScope.launch {
            currentJob?.join()
            runAction { useCases.cancelBackup() }
        }
    }

    fun showFrequencyPicker() {
        if (!isBusy()) interaction.update { it.copy(showFrequencyPicker = true) }
    }

    fun dismissFrequencyPicker() {
        interaction.update { it.copy(showFrequencyPicker = false) }
    }

    fun selectFrequency(frequency: DriveBackupFrequency) {
        dismissFrequencyPicker()
        if (!isBusy()) runAction { useCases.updateSchedule(frequency = frequency) }
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        if (!isBusy()) runAction { useCases.updateSchedule(wifiOnly = wifiOnly) }
    }

    private fun isBusy(): Boolean = interaction.value.busy || when (val current = state.value) {
        is DriveBackupScreenState.Success -> current.data.busy
        is DriveBackupScreenState.Error -> current.data.busy
        DriveBackupScreenState.Loading -> true
        DriveBackupScreenState.Empty -> false
    }

    private fun runAction(statusRes: Int? = null, action: suspend () -> Unit) {
        if (actionJob?.isActive == true) return
        interaction.update {
            it.copy(busy = true, statusRes = statusRes, progress = null, failure = null, ignoredFailureBefore = System.currentTimeMillis())
        }
        actionJob = viewModelScope.launch {
            try {
                action()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                reportException(exception)
                savedStateHandle[PICKING_ACCOUNT] = false
                savedStateHandle.remove<String>(PENDING_ACCOUNT)
                val failure = (exception as? DriveBackupException)?.failure ?: DriveBackupFailure.UNKNOWN
                interaction.update { it.copy(failure = failure) }
            } finally {
                interaction.update { it.copy(busy = false, statusRes = null, progress = null) }
            }
        }
    }

    private companion object {
        const val PENDING_ACCOUNT = "drive_pending_account"
        const val PICKING_ACCOUNT = "drive_picking_account"
    }
}
