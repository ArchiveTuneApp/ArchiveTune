@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.canvas.CanvasHealth
import moe.rukamori.archivetune.canvas.CanvasSource
import moe.rukamori.archivetune.viewmodels.CanvasCacheOption
import moe.rukamori.archivetune.viewmodels.CanvasSettingsAction
import moe.rukamori.archivetune.viewmodels.CanvasSettingsDialog
import moe.rukamori.archivetune.viewmodels.CanvasSettingsState
import moe.rukamori.archivetune.viewmodels.CanvasSettingsUiModel
import moe.rukamori.archivetune.viewmodels.CanvasSettingsViewModel

@Composable
fun CanvasSettings(
    navController: NavController,
    viewModel: CanvasSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onAction = remember(viewModel) { viewModel::onAction }
    val onBack: () -> Unit = remember(navController) {
        {
            navController.navigateUp()
            Unit
        }
    }
    CanvasSettingsContent(state = state, onAction = onAction, onBack = onBack)
}

@Composable
private fun CanvasSettingsContent(
    state: CanvasSettingsState,
    onAction: (CanvasSettingsAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.archivetune_canvas)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.back_button_desc))
                    }
                },
            )
        },
    ) { padding ->
        val topPadding = padding.calculateTopPadding()
        val insets = LocalPlayerAwareWindowInsets.current
        val contentModifier = remember(topPadding, insets) {
            Modifier.padding(top = topPadding)
                .windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
        }
        when (state) {
            CanvasSettingsState.Loading -> Box(contentModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is CanvasSettingsState.Success -> CanvasSettingsBody(state.model, onAction, contentModifier)
            CanvasSettingsState.Empty -> CanvasSettingsFailure(R.string.canvas_settings_load_failed, onAction, contentModifier)
            is CanvasSettingsState.Error -> CanvasSettingsFailure(state.messageRes, onAction, contentModifier)
        }
    }
}

@Composable
private fun CanvasSettingsBody(
    model: CanvasSettingsUiModel,
    onAction: (CanvasSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onEnabled: (Boolean) -> Unit = remember(onAction) { { onAction(CanvasSettingsAction.SetEnabled(it)) } }
    val onWifiOnly: (Boolean) -> Unit = remember(onAction) { { onAction(CanvasSettingsAction.SetWifiOnly(it)) } }
    val onRefresh = remember(onAction) { { onAction(CanvasSettingsAction.RefreshHealth) } }
    val onCacheLimit = remember(onAction) { { onAction(CanvasSettingsAction.ShowCacheLimit) } }
    val onClear = remember(onAction) { { onAction(CanvasSettingsAction.ShowClearCache) } }
    val sources = remember { CanvasSource.entries.toList() }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
            .padding(top = SettingsDimensions.SectionSpacing, bottom = SettingsDimensions.ScreenBottomPadding),
        verticalArrangement = remember { Arrangement.spacedBy(SettingsDimensions.SectionSpacing) },
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            CanvasSwitchRow(
                title = stringResource(R.string.canvas_enable),
                description = stringResource(R.string.archivetune_canvas_desc),
                checked = model.configuration.enabled,
                enabled = !model.busy,
                onCheckedChange = onEnabled,
            )
        }
        CanvasSection(title = stringResource(R.string.canvas_source)) {
            Column(remember { Modifier.selectableGroup() }) {
                sources.forEach { source ->
                    val onSelect = remember(source, onAction) { { onAction(CanvasSettingsAction.SelectSource(source)) } }
                    CanvasChoiceRow(
                        title = stringResource(source.labelResource()),
                        description = if (source == CanvasSource.BOTH) stringResource(R.string.canvas_source_both_desc) else null,
                        selected = model.configuration.source == source,
                        enabled = !model.busy,
                        onClick = onSelect,
                    )
                }
            }
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
            CanvasSwitchRow(
                title = stringResource(R.string.canvas_wifi_only),
                description = stringResource(R.string.canvas_wifi_only_desc),
                checked = model.configuration.wifiOnly,
                enabled = !model.busy,
                onCheckedChange = onWifiOnly,
            )
        }
        CanvasSection(title = stringResource(R.string.canvas_provider_health)) {
            CanvasHealthRow(stringResource(R.string.canvas_better_lyrics), model.health.betterLyrics)
            CanvasHealthRow(stringResource(R.string.canvas_apple_music), model.health.appleMusic)
            TextButton(
                onClick = onRefresh,
                enabled = model.canRefreshHealth && !model.busy,
                modifier = remember { Modifier.align(Alignment.End).padding(horizontal = SettingsDimensions.RowHorizontalPadding) },
            ) { Text(stringResource(R.string.refresh)) }
        }
        Card(remember { Modifier.fillMaxWidth() }) {
            Column(
                modifier = remember { Modifier.padding(SettingsDimensions.RowHorizontalPadding) },
                verticalArrangement = remember { Arrangement.spacedBy(SettingsDimensions.SectionSpacing) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = remember { Arrangement.spacedBy(12.dp) }) {
                    Icon(
                        painterResource(R.drawable.storage),
                        contentDescription = null,
                        modifier = remember { Modifier.size(SettingsDimensions.RowIconInnerSize) },
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(stringResource(R.string.canvas_cache), style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    stringResource(R.string.size_used, model.cacheSize),
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (model.configuration.cacheLimitMb > 0) {
                    val progress = remember(model.cacheProgress) { { model.cacheProgress } }
                    LinearProgressIndicator(progress = progress, modifier = remember { Modifier.fillMaxWidth() })
                }
                Text(
                    stringResource(R.string.canvas_cache_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onCacheLimit, enabled = !model.busy, modifier = remember { Modifier.fillMaxWidth() }) {
                    Text(
                        stringResource(
                            R.string.canvas_cache_limit,
                            when (model.configuration.cacheLimitMb) {
                                0 -> stringResource(R.string.disable)
                                -1 -> stringResource(R.string.unlimited)
                                else -> model.cacheLimit
                            },
                        ),
                    )
                }
                TextButton(
                    onClick = onClear,
                    enabled = !model.busy,
                    modifier = remember { Modifier.align(Alignment.End) },
                ) { Text(stringResource(R.string.clear_canvas_cache)) }
                if (model.busy) LinearProgressIndicator(modifier = remember { Modifier.fillMaxWidth() })
            }
        }
    }
    CanvasSettingsDialogs(model, onAction)
}

@Composable
private fun CanvasSection(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(verticalArrangement = remember { Arrangement.spacedBy(SettingsDimensions.SectionHeaderBottomPadding) }) {
        Text(
            title,
            modifier = remember { Modifier.padding(horizontal = SettingsDimensions.RowHorizontalPadding) },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(modifier = remember { Modifier.fillMaxWidth() }, content = content)
        }
    }
}

@Composable
private fun CanvasSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val rowModifier = remember(checked, enabled, onCheckedChange) {
        Modifier.fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = SettingsDimensions.RowHorizontalPadding, vertical = SettingsDimensions.RowVerticalPadding)
    }
    Row(rowModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = remember { Arrangement.spacedBy(16.dp) }) {
        Column(remember { Modifier.weight(1f) }, verticalArrangement = remember { Arrangement.spacedBy(4.dp) }) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun CanvasChoiceRow(
    title: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    description: String? = null,
) {
    val rowModifier = remember(selected, enabled, onClick) {
        Modifier.fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = SettingsDimensions.RowHorizontalPadding, vertical = SettingsDimensions.RowVerticalPadding)
    }
    Row(rowModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = remember { Arrangement.spacedBy(16.dp) }) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(remember { Modifier.weight(1f) }) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CanvasHealthRow(title: String, health: CanvasHealth) {
    Row(
        modifier = remember { Modifier.fillMaxWidth().padding(SettingsDimensions.RowHorizontalPadding) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = remember { Arrangement.spacedBy(16.dp) },
    ) {
        Text(title, modifier = remember { Modifier.weight(1f) }, style = MaterialTheme.typography.bodyLarge)
        if (health == CanvasHealth.CHECKING) {
            CircularProgressIndicator(modifier = remember { Modifier.size(20.dp) })
        }
        Text(
            stringResource(health.labelResource()),
            modifier = remember { Modifier.weight(1f) },
            style = MaterialTheme.typography.labelLarge,
            color = when (health) {
                CanvasHealth.AVAILABLE -> MaterialTheme.colorScheme.primary
                CanvasHealth.UNAVAILABLE -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun CanvasSettingsDialogs(model: CanvasSettingsUiModel, onAction: (CanvasSettingsAction) -> Unit) {
    val onDismiss = remember(onAction) { { onAction(CanvasSettingsAction.DismissDialog) } }
    val onConfirmClear = remember(onAction) { { onAction(CanvasSettingsAction.ClearCache) } }
    when (model.dialog) {
        CanvasSettingsDialog.CACHE_LIMIT -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.max_cache_size)) },
            text = {
                LazyColumn(modifier = remember { Modifier.heightIn(max = 420.dp).selectableGroup() }) {
                    items(model.cacheOptions.values, key = CanvasCacheOption::limitMb, contentType = { "cache_limit" }) { option ->
                        val onSelect = remember(onAction, option.limitMb) {
                            { onAction(CanvasSettingsAction.SetCacheLimit(option.limitMb)) }
                        }
                        CanvasChoiceRow(
                            title = when (option.limitMb) {
                                0 -> stringResource(R.string.disable)
                                -1 -> stringResource(R.string.unlimited)
                                else -> option.formattedSize
                            },
                            selected = model.configuration.cacheLimitMb == option.limitMb,
                            enabled = !model.busy,
                            onClick = onSelect,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
        CanvasSettingsDialog.CLEAR_CACHE -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.clear_canvas_cache)) },
            text = { Text(stringResource(R.string.clear_canvas_cache_dialog)) },
            confirmButton = { TextButton(onClick = onConfirmClear, enabled = !model.busy) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
        null -> Unit
    }
}

@Composable
private fun CanvasSettingsFailure(@StringRes messageRes: Int, onAction: (CanvasSettingsAction) -> Unit, modifier: Modifier) {
    val onRetry = remember(onAction) { { onAction(CanvasSettingsAction.Retry) } }
    Column(
        modifier = modifier.fillMaxSize().padding(SettingsDimensions.ScreenHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(messageRes))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@StringRes
private fun CanvasSource.labelResource(): Int = when (this) {
    CanvasSource.BETTER_LYRICS -> R.string.canvas_better_lyrics
    CanvasSource.APPLE_MUSIC -> R.string.canvas_apple_music
    CanvasSource.BOTH -> R.string.canvas_source_both
}

@StringRes
private fun CanvasHealth.labelResource(): Int = when (this) {
    CanvasHealth.NOT_CHECKED -> R.string.canvas_health_not_checked
    CanvasHealth.CHECKING -> R.string.canvas_health_checking
    CanvasHealth.AVAILABLE -> R.string.canvas_health_available
    CanvasHealth.UNAVAILABLE -> R.string.canvas_health_unavailable
    CanvasHealth.NOT_SELECTED -> R.string.canvas_health_not_selected
    CanvasHealth.DISABLED -> R.string.canvas_health_disabled
    CanvasHealth.OFFLINE -> R.string.canvas_health_offline
    CanvasHealth.WIFI_REQUIRED -> R.string.canvas_health_wifi_required
    CanvasHealth.LOW_DATA_MODE -> R.string.canvas_health_low_data
}
