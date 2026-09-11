/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.DisableScreenshotKey
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.PauseListenHistoryKey
import moe.rukamori.archivetune.constants.PauseSearchHistoryKey
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.ResetListeningStatsState
import moe.rukamori.archivetune.viewmodels.ResetListeningStatsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettings(
    navController: NavController,
    resetStatsViewModel: ResetListeningStatsViewModel = hiltViewModel(),
) {
    val resetStatsState by resetStatsViewModel.state.collectAsStateWithLifecycle()
    val onRequestStatsReset = remember(resetStatsViewModel) { resetStatsViewModel::requestReset }
    val onDismissStatsReset = remember(resetStatsViewModel) { resetStatsViewModel::dismissDialog }
    val onConfirmStatsReset = remember(resetStatsViewModel) { resetStatsViewModel::confirmReset }

    val database = LocalDatabase.current
    val (pauseListenHistory, onPauseListenHistoryChange) =
        rememberPreference(
            key = PauseListenHistoryKey,
            defaultValue = false,
        )
    val (pauseSearchHistory, onPauseSearchHistoryChange) =
        rememberPreference(
            key = PauseSearchHistoryKey,
            defaultValue = false,
        )
    val (disableScreenshot, onDisableScreenshotChange) =
        rememberPreference(
            key = DisableScreenshotKey,
            defaultValue = false,
        )
    val (enableHapticFeedback, onEnableHapticFeedbackChange) =
        rememberPreference(
            key = EnableHapticFeedbackKey,
            defaultValue = true,
        )

    var showClearListenHistoryDialog by remember {
        mutableStateOf(false)
    }

    if (showClearListenHistoryDialog) {
        DefaultDialog(
            onDismiss = { showClearListenHistoryDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.clear_listen_history_confirm),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = { showClearListenHistoryDialog = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showClearListenHistoryDialog = false
                        database.query {
                            clearListenHistory()
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    var showClearSearchHistoryDialog by remember {
        mutableStateOf(false)
    }

    if (showClearSearchHistoryDialog) {
        DefaultDialog(
            onDismiss = { showClearSearchHistoryDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.clear_search_history_confirm),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = { showClearSearchHistoryDialog = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showClearSearchHistoryDialog = false
                        database.query {
                            clearSearchHistory()
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_behavior_title)) },
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
            )
        },
    ) { innerPadding ->
        val topPadding = innerPadding.calculateTopPadding()

        Column(
            Modifier
                .padding(top = topPadding)
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(bottom = SettingsDimensions.ScreenBottomPadding),
        ) {
            PreferenceGroup(title = stringResource(R.string.listen_history)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.pause_listen_history)) },
                        icon = { Icon(painterResource(R.drawable.history), null) },
                        checked = pauseListenHistory,
                        onCheckedChange = onPauseListenHistoryChange,
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.clear_listen_history)) },
                        icon = { Icon(painterResource(R.drawable.delete_history), null) },
                        onClick = { showClearListenHistoryDialog = true },
                    )
                }
                item {
                    ResetListeningStatsPreference(
                        state = resetStatsState,
                        onRequestReset = onRequestStatsReset,
                        onDismiss = onDismissStatsReset,
                        onConfirm = onConfirmStatsReset,
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.search_history)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.pause_search_history)) },
                        icon = { Icon(painterResource(R.drawable.search_off), null) },
                        checked = pauseSearchHistory,
                        onCheckedChange = onPauseSearchHistoryChange,
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.clear_search_history)) },
                        icon = { Icon(painterResource(R.drawable.clear_all), null) },
                        onClick = { showClearSearchHistoryDialog = true },
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.misc)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.haptics)) },
                        description = stringResource(R.string.haptics_desc),
                        icon = { Icon(painterResource(R.drawable.vibration), null) },
                        checked = enableHapticFeedback,
                        onCheckedChange = onEnableHapticFeedbackChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.disable_screenshot)) },
                        description = stringResource(R.string.disable_screenshot_desc),
                        icon = { Icon(painterResource(R.drawable.screenshot), null) },
                        checked = disableScreenshot,
                        onCheckedChange = onDisableScreenshotChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResetListeningStatsPreference(
    state: ResetListeningStatsState,
    onRequestReset: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val isResetting = state == ResetListeningStatsState.Loading
    val showConfirmation = (state as? ResetListeningStatsState.Success)?.showConfirmation == true
    val description = when {
        isResetting -> stringResource(R.string.reset_listening_stats_progress)
        state is ResetListeningStatsState.Success && !state.showConfirmation ->
            stringResource(R.string.reset_listening_stats_success)
        else -> null
    }

    PreferenceEntry(
        title = { Text(stringResource(R.string.reset_listening_stats)) },
        description = description,
        icon = { Icon(painterResource(R.drawable.delete_history), contentDescription = null) },
        isEnabled = !isResetting,
        onClick = onRequestReset,
    )

    if (showConfirmation || isResetting || state is ResetListeningStatsState.Error) {
        DefaultDialog(
            onDismiss = onDismiss,
            title = { Text(stringResource(R.string.reset_listening_stats)) },
            content = {
                Text(
                    text = stringResource(
                        when {
                            isResetting -> R.string.reset_listening_stats_progress
                            state is ResetListeningStatsState.Error -> state.messageRes
                            else -> R.string.reset_listening_stats_confirm
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            buttons = {
                if (state is ResetListeningStatsState.Error) {
                    TextButton(
                        onClick = onDismiss,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                } else {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isResetting,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    TextButton(
                        onClick = onConfirm,
                        enabled = !isResetting,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.reset))
                    }
                }
            },
        )
    }
}
