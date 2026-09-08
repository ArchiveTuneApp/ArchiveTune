package moe.rukamori.archivetune.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.backup.drive.DriveBackupEvent
import moe.rukamori.archivetune.backup.drive.DriveBackupFrequency
import moe.rukamori.archivetune.backup.drive.DriveBackupScreenState
import moe.rukamori.archivetune.backup.drive.DriveBackupUiData
import moe.rukamori.archivetune.backup.drive.DriveBackupViewModel
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference

@Composable
fun GoogleDriveBackupSection(
    enabled: Boolean,
    onRestoreReady: (Uri) -> Unit,
) {
    val viewModel: DriveBackupViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val restoreReady by rememberUpdatedState(onRestoreReady)
    val accountLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onAccountResult(it.resultCode, it.data)
    }
    val authorizationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.onAuthorizationResult(it.resultCode, it.data)
    }
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is DriveBackupEvent.ChooseAccount -> try {
                        accountLauncher.launch(event.intent)
                    } catch (_: android.content.ActivityNotFoundException) {
                        viewModel.onLaunchFailed()
                    }
                    is DriveBackupEvent.Authorize -> try {
                        authorizationLauncher.launch(IntentSenderRequest.Builder(event.intent).build())
                    } catch (_: android.content.IntentSender.SendIntentException) {
                        viewModel.onLaunchFailed()
                    }
                    is DriveBackupEvent.Restore -> restoreReady(event.uri)
                }
            }
        }
    }
    val actions = remember(viewModel) {
        DriveBackupActions(
            chooseAccount = viewModel::chooseAccount,
            disconnect = viewModel::disconnect,
            backup = viewModel::backup,
            restore = viewModel::restore,
            refresh = viewModel::refresh,
            cancel = viewModel::cancel,
            showFrequency = viewModel::showFrequencyPicker,
            dismissFrequency = viewModel::dismissFrequencyPicker,
            selectFrequency = viewModel::selectFrequency,
            setWifiOnly = viewModel::setWifiOnly,
        )
    }
    GoogleDriveBackupContent(state, enabled, actions)
}

@Immutable
private data class DriveBackupActions(
    val chooseAccount: () -> Unit,
    val disconnect: () -> Unit,
    val backup: () -> Unit,
    val restore: () -> Unit,
    val refresh: () -> Unit,
    val cancel: () -> Unit,
    val showFrequency: () -> Unit,
    val dismissFrequency: () -> Unit,
    val selectFrequency: (DriveBackupFrequency) -> Unit,
    val setWifiOnly: (Boolean) -> Unit,
)

@Composable
private fun GoogleDriveBackupContent(
    state: DriveBackupScreenState,
    enabled: Boolean,
    actions: DriveBackupActions,
) {
    val defaults = remember { DriveBackupUiData() }
    val data = when (state) {
        is DriveBackupScreenState.Success -> state.data
        is DriveBackupScreenState.Error -> state.data
        DriveBackupScreenState.Empty, DriveBackupScreenState.Loading -> defaults
    }
    val canAct = enabled && !data.busy && state != DriveBackupScreenState.Loading
    val connected = data.account != null
    val progressModifier = remember { Modifier.fillMaxWidth() }
    PreferenceGroup(title = stringResource(R.string.drive_backup_title)) {
        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.drive_backup_account)) },
                description = data.account ?: stringResource(R.string.drive_backup_choose_account),
                icon = { Icon(painterResource(R.drawable.account), contentDescription = null) },
                onClick = actions.chooseAccount,
                isEnabled = canAct,
            )
        }
        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.drive_backup_last_backup)) },
                description = if (data.backupDate != null && data.backupSize != null) {
                    stringResource(R.string.drive_backup_details, data.backupDate, data.backupSize)
                } else {
                    stringResource(R.string.drive_backup_no_backup)
                },
                icon = { Icon(painterResource(R.drawable.backup), contentDescription = null) },
                content = {
                    Text(stringResource(R.string.drive_backup_contents), style = MaterialTheme.typography.bodySmall)
                },
            )
        }
        if (state is DriveBackupScreenState.Error) {
            item {
                PreferenceEntry(
                    title = { Text(stringResource(state.failure.messageRes), color = MaterialTheme.colorScheme.error) },
                    description = stringResource(R.string.retry),
                    onClick = actions.refresh,
                    isEnabled = canAct,
                )
            }
        }
        if (data.busy || state == DriveBackupScreenState.Loading) {
            item {
                PreferenceEntry(
                    title = { Text(stringResource(data.statusRes ?: R.string.drive_backup_loading)) },
                    content = {
                        val progress = data.progress
                        if (progress == null) {
                            LinearProgressIndicator(modifier = progressModifier)
                        } else {
                            val progressProvider = remember(progress) { { progress / 100f } }
                            LinearProgressIndicator(progress = progressProvider, modifier = progressModifier)
                        }
                    },
                    trailingContent = {
                        if (data.busy) {
                            TextButton(onClick = actions.cancel) { Text(stringResource(android.R.string.cancel)) }
                        }
                    },
                )
            }
        }
        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.drive_backup_now)) },
                icon = { Icon(painterResource(R.drawable.backup), contentDescription = null) },
                onClick = actions.backup,
                isEnabled = canAct && connected,
            )
        }
        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.drive_backup_restore)) },
                description = stringResource(R.string.drive_backup_restore_description),
                icon = { Icon(painterResource(R.drawable.restore), contentDescription = null) },
                onClick = actions.restore,
                isEnabled = canAct && connected && data.hasBackup,
            )
        }
        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.scheduled_backup_frequency)) },
                description = stringResource(data.frequency.labelRes),
                icon = { Icon(painterResource(R.drawable.calendar_today), contentDescription = null) },
                onClick = actions.showFrequency,
                isEnabled = canAct && connected,
            )
        }
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.drive_backup_wifi_only)) },
                description = stringResource(R.string.drive_backup_wifi_description),
                checked = data.wifiOnly,
                onCheckedChange = actions.setWifiOnly,
                isEnabled = canAct && connected,
            )
        }
        if (connected) {
            item {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.drive_backup_disconnect)) },
                    description = stringResource(R.string.drive_backup_disconnect_description),
                    onClick = actions.disconnect,
                    isEnabled = canAct,
                )
            }
        }
    }
    if (data.showFrequencyPicker) {
        DefaultDialog(
            title = { Text(stringResource(R.string.scheduled_backup_frequency)) },
            onDismiss = actions.dismissFrequency,
            buttons = {
                TextButton(onClick = actions.dismissFrequency) { Text(stringResource(android.R.string.cancel)) }
            },
        ) {
            Column {
                DriveBackupFrequency.entries.forEach { frequency ->
                    val onClick = remember(actions, frequency) { { actions.selectFrequency(frequency) } }
                    PreferenceEntry(
                        title = { Text(stringResource(frequency.labelRes)) },
                        icon = { RadioButton(selected = frequency == data.frequency, onClick = null) },
                        onClick = onClick,
                    )
                }
            }
        }
    }
}
