/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.LastFmProvider
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.InfoLabel
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.LastFmLoginDialogUiModel
import moe.rukamori.archivetune.viewmodels.LastFmServiceEditorUiModel
import moe.rukamori.archivetune.viewmodels.LastFmSettingsScreenState
import moe.rukamori.archivetune.viewmodels.LastFmSettingsUiModel
import moe.rukamori.archivetune.viewmodels.LastFmSettingsViewModel
import moe.rukamori.archivetune.viewmodels.LastFmTimingEditorUiModel
import moe.rukamori.archivetune.viewmodels.LastFmTimingSetting
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LastFMSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: LastFmSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LastFmSettingsContent(
        state = state,
        onOpenServiceEditor = viewModel::openServiceEditor,
        onDismissServiceEditor = viewModel::dismissServiceEditor,
        onServiceProviderChange = viewModel::updateServiceProvider,
        onCustomEndpointChange = viewModel::updateCustomEndpoint,
        onApiKeyOverrideChange = viewModel::updateApiKeyOverride,
        onSecretOverrideChange = viewModel::updateSecretOverride,
        onSaveServiceEditor = viewModel::saveServiceEditor,
        onOpenLoginDialog = viewModel::openLoginDialog,
        onDismissLoginDialog = viewModel::dismissLoginDialog,
        onLoginUsernameChange = viewModel::updateLoginUsername,
        onLoginPasswordChange = viewModel::updateLoginPassword,
        onLogin = viewModel::login,
        onLogout = viewModel::logout,
        onScrobblingChange = viewModel::setScrobblingEnabled,
        onNowPlayingChange = viewModel::setNowPlayingEnabled,
        onOpenTimingEditor = viewModel::openTimingEditor,
        onDismissTimingEditor = viewModel::dismissTimingEditor,
        onTimingMinTrackDurationChange = viewModel::updateTimingMinTrackDuration,
        onTimingDelayPercentChange = viewModel::updateTimingDelayPercent,
        onTimingDelaySecondsChange = viewModel::updateTimingDelaySeconds,
        onSaveTimingEditor = viewModel::saveTimingEditor,
    )

    TopAppBar(
        title = { Text(stringResource(R.string.lastfm_integration)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun LastFmSettingsContent(
    state: LastFmSettingsScreenState,
    onOpenServiceEditor: () -> Unit,
    onDismissServiceEditor: () -> Unit,
    onServiceProviderChange: (LastFmProvider) -> Unit,
    onCustomEndpointChange: (String) -> Unit,
    onApiKeyOverrideChange: (String) -> Unit,
    onSecretOverrideChange: (String) -> Unit,
    onSaveServiceEditor: () -> Unit,
    onOpenLoginDialog: () -> Unit,
    onDismissLoginDialog: () -> Unit,
    onLoginUsernameChange: (String) -> Unit,
    onLoginPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onScrobblingChange: (Boolean) -> Unit,
    onNowPlayingChange: (Boolean) -> Unit,
    onOpenTimingEditor: (LastFmTimingSetting) -> Unit,
    onDismissTimingEditor: () -> Unit,
    onTimingMinTrackDurationChange: (Int) -> Unit,
    onTimingDelayPercentChange: (Float) -> Unit,
    onTimingDelaySecondsChange: (Int) -> Unit,
    onSaveTimingEditor: () -> Unit,
) {
    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
            .padding(bottom = SettingsDimensions.ScreenBottomPadding),
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top),
            ),
        )

        when (state) {
            LastFmSettingsScreenState.Loading -> {
                LastFmSettingsLoading()
            }

            LastFmSettingsScreenState.Empty -> {
                Unit
            }

            is LastFmSettingsScreenState.Error -> {
                LastFmSettingsError(state.messageResId)
            }

            is LastFmSettingsScreenState.Success -> {
                LastFmSettingsSuccess(
                    model = state.model,
                    onOpenServiceEditor = onOpenServiceEditor,
                    onOpenLoginDialog = onOpenLoginDialog,
                    onLogout = onLogout,
                    onScrobblingChange = onScrobblingChange,
                    onNowPlayingChange = onNowPlayingChange,
                    onOpenTimingEditor = onOpenTimingEditor,
                )
            }
        }
    }

    if (state is LastFmSettingsScreenState.Success) {
        val model = state.model
        LastFmLoginDialog(
            model = model,
            dialog = model.loginDialog,
            onDismiss = onDismissLoginDialog,
            onUsernameChange = onLoginUsernameChange,
            onPasswordChange = onLoginPasswordChange,
            onLogin = onLogin,
        )
        LastFmServiceEditorDialog(
            editor = model.serviceEditor,
            onDismiss = onDismissServiceEditor,
            onProviderChange = onServiceProviderChange,
            onCustomEndpointChange = onCustomEndpointChange,
            onApiKeyOverrideChange = onApiKeyOverrideChange,
            onSecretOverrideChange = onSecretOverrideChange,
            onSave = onSaveServiceEditor,
        )
        LastFmTimingEditorDialog(
            editor = model.timingEditor,
            onDismiss = onDismissTimingEditor,
            onMinTrackDurationChange = onTimingMinTrackDurationChange,
            onDelayPercentChange = onTimingDelayPercentChange,
            onDelaySecondsChange = onTimingDelaySecondsChange,
            onSave = onSaveTimingEditor,
        )
    }
}

@Composable
private fun LastFmSettingsLoading() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularWavyProgressIndicator(modifier = Modifier.size(28.dp))
        Text(
            text = stringResource(R.string.loading),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun LastFmSettingsError(
    @StringRes messageResId: Int,
) {
    Text(
        text = stringResource(messageResId),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(24.dp),
    )
}

@Composable
private fun LastFmSettingsSuccess(
    model: LastFmSettingsUiModel,
    onOpenServiceEditor: () -> Unit,
    onOpenLoginDialog: () -> Unit,
    onLogout: () -> Unit,
    onScrobblingChange: (Boolean) -> Unit,
    onNowPlayingChange: (Boolean) -> Unit,
    onOpenTimingEditor: (LastFmTimingSetting) -> Unit,
) {
    val providerName = stringResource(model.provider.titleResId())
    val endpointDescription =
        if (model.endpointValid) {
            model.resolvedEndpoint
        } else {
            stringResource(R.string.lastfm_endpoint_invalid)
        }

    PreferenceGroup(title = stringResource(R.string.lastfm_service)) {
        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.lastfm_service_provider)) },
                description = "$providerName\n$endpointDescription",
                icon = { Icon(painterResource(R.drawable.token), null) },
                onClick = onOpenServiceEditor,
            )
        }

        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.lastfm_api_credentials)) },
                description =
                    if (model.apiKeyOverride.isBlank() && model.secretOverride.isBlank()) {
                        stringResource(R.string.lastfm_api_credentials_default)
                    } else {
                        stringResource(R.string.lastfm_api_credentials_custom)
                    },
                icon = { Icon(painterResource(R.drawable.token), null) },
                onClick = onOpenServiceEditor,
            )
        }
    }

    PreferenceGroup(title = stringResource(R.string.account)) {
        item {
            PreferenceEntry(
                title = {
                    Text(
                        text = if (model.isLoggedIn) model.username else stringResource(R.string.not_logged_in),
                        modifier = Modifier.alpha(if (model.isLoggedIn) 1f else 0.5f),
                    )
                },
                description = null,
                icon = { Icon(painterResource(R.drawable.token), null) },
                trailingContent = {
                    if (model.isLoggedIn) {
                        OutlinedButton(onClick = onLogout, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(R.string.action_logout))
                        }
                    } else {
                        OutlinedButton(
                            onClick = onOpenLoginDialog,
                            enabled = model.canLogin,
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(R.string.action_login))
                        }
                    }
                },
            )
        }
    }

    PreferenceGroup(title = stringResource(R.string.options)) {
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.enable_scrobbling)) },
                checked = model.scrobblingEnabled,
                onCheckedChange = onScrobblingChange,
                isEnabled = model.canEnableScrobbling,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.lastfm_now_playing)) },
                checked = model.nowPlayingEnabled,
                onCheckedChange = onNowPlayingChange,
                isEnabled = model.canEnableScrobbling && model.scrobblingEnabled,
            )
        }
    }

    PreferenceGroup(title = stringResource(R.string.scrobbling_configuration)) {
        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.scrobble_min_track_duration)) },
                description = stringResource(R.string.duration_seconds_short, model.minTrackDurationSeconds),
                onClick = { onOpenTimingEditor(LastFmTimingSetting.MIN_TRACK_DURATION) },
            )
        }

        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.scrobble_delay_percent)) },
                description =
                    stringResource(
                        R.string.percent_format,
                        (model.scrobbleDelayPercent * 100).roundToInt(),
                    ),
                onClick = { onOpenTimingEditor(LastFmTimingSetting.DELAY_PERCENT) },
            )
        }

        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.scrobble_delay_minutes)) },
                description = stringResource(R.string.duration_seconds_short, model.scrobbleDelaySeconds),
                onClick = { onOpenTimingEditor(LastFmTimingSetting.DELAY_SECONDS) },
            )
        }
    }
}

@Composable
private fun LastFmLoginDialog(
    model: LastFmSettingsUiModel,
    dialog: LastFmLoginDialogUiModel,
    onDismiss: () -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
) {
    if (!dialog.visible) return

    AlertDialog(
        onDismissRequest = {
            if (!dialog.isLoggingIn) onDismiss()
        },
        title = { Text(stringResource(R.string.login)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dialog.username,
                    onValueChange = onUsernameChange,
                    label = { Text(stringResource(R.string.username)) },
                    singleLine = true,
                    enabled = !dialog.isLoggingIn,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dialog.password,
                    onValueChange = onPasswordChange,
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !dialog.isLoggingIn,
                    modifier = Modifier.fillMaxWidth(),
                )

                dialog.errorMessageResId?.let { messageResId ->
                    Text(
                        text = stringResource(messageResId),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                if (dialog.isLoggingIn) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(
                            text = stringResource(R.string.logging_in),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onLogin,
                enabled =
                    !dialog.isLoggingIn &&
                        model.canLogin &&
                        dialog.username.isNotBlank() &&
                        dialog.password.isNotBlank(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.login))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !dialog.isLoggingIn,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun LastFmServiceEditorDialog(
    editor: LastFmServiceEditorUiModel,
    onDismiss: () -> Unit,
    onProviderChange: (LastFmProvider) -> Unit,
    onCustomEndpointChange: (String) -> Unit,
    onApiKeyOverrideChange: (String) -> Unit,
    onSecretOverrideChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    if (!editor.visible) return

    AlertDialog(
        onDismissRequest = {
            if (!editor.isSaving) onDismiss()
        },
        title = { Text(stringResource(R.string.lastfm_service)) },
        text = {
            Column(
                modifier =
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.lastfm_service_provider),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val providers = remember { LastFmProvider.entries.toList() }
                    providers.forEach { provider ->
                        FilterChip(
                            selected = editor.provider == provider,
                            onClick = { onProviderChange(provider) },
                            enabled = !editor.isSaving,
                            label = { Text(stringResource(provider.titleResId())) },
                        )
                    }
                }

                if (editor.showCustomEndpoint) {
                    OutlinedTextField(
                        value = editor.customEndpoint,
                        onValueChange = onCustomEndpointChange,
                        label = { Text(stringResource(R.string.lastfm_custom_endpoint)) },
                        singleLine = true,
                        isError = editor.errorMessageResId == R.string.lastfm_endpoint_invalid,
                        enabled = !editor.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (editor.showApiCredentials) {
                    OutlinedTextField(
                        value = editor.apiKeyOverride,
                        onValueChange = onApiKeyOverrideChange,
                        label = { Text(stringResource(R.string.lastfm_api_key_override)) },
                        singleLine = true,
                        enabled = !editor.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editor.secretOverride,
                        onValueChange = onSecretOverrideChange,
                        label = { Text(stringResource(R.string.lastfm_secret_override)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !editor.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    InfoLabel(text = stringResource(R.string.lastfm_api_credentials_hint))
                }

                editor.errorMessageResId?.let { messageResId ->
                    Text(
                        text = stringResource(messageResId),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = !editor.isSaving,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !editor.isSaving,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun LastFmTimingEditorDialog(
    editor: LastFmTimingEditorUiModel,
    onDismiss: () -> Unit,
    onMinTrackDurationChange: (Int) -> Unit,
    onDelayPercentChange: (Float) -> Unit,
    onDelaySecondsChange: (Int) -> Unit,
    onSave: () -> Unit,
) {
    val setting = editor.setting ?: return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(setting.titleResId())) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp),
            ) {
                when (setting) {
                    LastFmTimingSetting.MIN_TRACK_DURATION -> {
                        Text(
                            text = stringResource(R.string.duration_seconds_short, editor.minTrackDurationSeconds),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                        Slider(
                            value = editor.minTrackDurationSeconds.toFloat(),
                            onValueChange = { onMinTrackDurationChange(it.toInt()) },
                            valueRange = 10f..60f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    LastFmTimingSetting.DELAY_PERCENT -> {
                        Text(
                            text =
                                stringResource(
                                    R.string.percent_format,
                                    (editor.scrobbleDelayPercent * 100).roundToInt(),
                                ),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                        Slider(
                            value = editor.scrobbleDelayPercent,
                            onValueChange = onDelayPercentChange,
                            valueRange = 0.3f..0.95f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    LastFmTimingSetting.DELAY_SECONDS -> {
                        Text(
                            text = stringResource(R.string.duration_seconds_short, editor.scrobbleDelaySeconds),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                        Slider(
                            value = editor.scrobbleDelaySeconds.toFloat(),
                            onValueChange = { onDelaySecondsChange(it.toInt()) },
                            valueRange = 30f..360f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@StringRes
private fun LastFmProvider.titleResId(): Int =
    when (this) {
        LastFmProvider.LASTFM -> R.string.lastfm_provider_lastfm
        LastFmProvider.LIBREFM -> R.string.lastfm_provider_librefm
        LastFmProvider.CUSTOM -> R.string.lastfm_provider_custom
    }

@StringRes
private fun LastFmTimingSetting.titleResId(): Int =
    when (this) {
        LastFmTimingSetting.MIN_TRACK_DURATION -> R.string.scrobble_min_track_duration
        LastFmTimingSetting.DELAY_PERCENT -> R.string.scrobble_delay_percent
        LastFmTimingSetting.DELAY_SECONDS -> R.string.scrobble_delay_minutes
    }
