/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.musicrecognition

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.window.core.layout.WindowSizeClass
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.musicrecognition.MusicRecognitionAutoStartRequestKey
import moe.rukamori.archivetune.musicrecognition.MusicRecognitionRoute
import moe.rukamori.archivetune.shazamkit.Shazam
import moe.rukamori.archivetune.shazamkit.ShazamSignatureGenerator
import moe.rukamori.archivetune.shazamkit.models.RecognitionResult
import moe.rukamori.archivetune.ui.screens.search.onlineSearchResultRoute
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.utils.dataStore
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicRecognitionScreen(navController: NavHostController) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<MusicRecognitionState>(MusicRecognitionState.Ready) }
    var recognitionJob by remember { mutableStateOf<Job?>(null) }
    var showHistorySheet by rememberSaveable { mutableStateOf(false) }
    val historySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val historyItems by remember(context) {
        context.dataStore.data.map { preferences ->
            decodeRecognitionHistory(preferences[MusicRecognitionHistoryJsonKey])
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val historyCollection = remember(historyItems) { RecognitionHistoryCollection(historyItems) }
    val backStackEntry = remember(navController) { navController.getBackStackEntry(MusicRecognitionRoute) }
    val autoStartRequestId by backStackEntry.savedStateHandle
        .getStateFlow(MusicRecognitionAutoStartRequestKey, 0L)
        .collectAsStateWithLifecycle()

    val strings =
        remember {
            MusicRecognitionStrings(
                signatureFailed = context.getString(R.string.music_recognition_signature_failed),
                noMatchFallback = context.getString(R.string.music_recognition_no_match),
                recognitionFailedFallback = context.getString(R.string.music_recognition_recognition_failed),
            )
        }

    fun handleRecognitionState(nextState: MusicRecognitionState) {
        state = nextState
        if (nextState is MusicRecognitionState.Success) {
            scope.launch(Dispatchers.IO) {
                insertRecognitionHistory(context, nextState.result)
            }
        }
    }

    val requestPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchRecognition(
                    scope = scope,
                    strings = strings,
                    onState = ::handleRecognitionState,
                    onHaptic = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                    onReplaceJob = {
                        recognitionJob?.cancel()
                        recognitionJob = it
                    },
                )
            } else {
                state = MusicRecognitionState.PermissionRequired
            }
        }

    fun cancelRecognition() {
        recognitionJob?.cancel()
        recognitionJob = null
        state = MusicRecognitionState.Ready
    }

    fun startOrRequestPermission() {
        val permission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (permission) {
            launchRecognition(
                scope = scope,
                strings = strings,
                onState = ::handleRecognitionState,
                onHaptic = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                onReplaceJob = {
                    recognitionJob?.cancel()
                    recognitionJob = it
                },
            )
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose { recognitionJob?.cancel() }
    }

    LaunchedEffect(autoStartRequestId) {
        if (autoStartRequestId == 0L) return@LaunchedEffect
        backStackEntry.savedStateHandle[MusicRecognitionAutoStartRequestKey] = 0L
        startOrRequestPermission()
    }

    val scrollBehavior = appBarScrollBehavior()
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val useWideContent = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.music_recognition)) },
                navigationIcon = {
                    Surface(
                        modifier =
                            Modifier
                                .padding(horizontal = 8.dp)
                                .size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = navController::navigateUp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = stringResource(R.string.back_button_desc),
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                actions = {
                    IconButton(onClick = { showHistorySheet = true }) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.history),
                                    contentDescription = stringResource(R.string.music_recognition_history),
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = Color.Transparent,
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { padding ->
        val primary = MaterialTheme.colorScheme.primary
        val tertiary = MaterialTheme.colorScheme.tertiary
        val gradient =
            remember(primary, tertiary) {
                listOf(
                    primary.copy(alpha = 0.35f),
                    tertiary.copy(alpha = 0.18f),
                    Color.Transparent,
                )
            }
        val backgroundBrush =
            remember(gradient) {
                Brush.radialGradient(
                    colors = gradient,
                    center = Offset(0.58f, 0.28f),
                    radius = 1100f,
                )
            }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(backgroundBrush)
                    .padding(padding)
                    .statusBarsPadding(),
        ) {
            val scrollState = rememberScrollState()
            val contentWidthModifier =
                if (useWideContent) {
                    Modifier.widthIn(max = 720.dp)
                } else {
                    Modifier
                }

            Column(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .then(contentWidthModifier)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.98f))
                            .togetherWith(fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 1.02f))
                    },
                    label = "stateContent",
                ) { target ->
                    when (target) {
                        is MusicRecognitionState.Success -> {
                            ResultFirstSheet(
                                result = target.result,
                                isWide = useWideContent,
                                onSearch = {
                                    val query = "${target.result.title} ${target.result.artist}".trim()
                                    navController.navigate(onlineSearchResultRoute(query))
                                },
                                onListenAgain = { startOrRequestPermission() },
                            )
                        }

                        else -> {
                            RecognitionListenPane(
                                state = target,
                                onStart = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    startOrRequestPermission()
                                },
                                onCancel = ::cancelRecognition,
                                onRequestPermission = {
                                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showHistorySheet) {
        RecognitionHistoryBottomSheet(
            history = historyCollection,
            sheetState = historySheetState,
            isWide = useWideContent,
            onDismiss = { showHistorySheet = false },
            onSearch = { query ->
                showHistorySheet = false
                navController.navigate(onlineSearchResultRoute(query))
            },
        )
    }
}

@Composable
private fun RecognitionHistoryBottomSheet(
    history: RecognitionHistoryCollection,
    sheetState: SheetState,
    isWide: Boolean,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = remember(query) { query.trim() }
    val filteredItems by remember(history, normalizedQuery) {
        derivedStateOf { history.items.filter { it.matches(normalizedQuery) } }
    }

    LaunchedEffect(history) {
        if (history.items.isEmpty()) query = ""
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.music_recognition_history),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = stringResource(R.string.close),
                    )
                }
            }

            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = {},
                        expanded = false,
                        onExpandedChange = {},
                        placeholder = { Text(stringResource(R.string.music_recognition_history_search)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.search),
                                contentDescription = null,
                            )
                        },
                        trailingIcon =
                            if (query.isNotBlank()) {
                                {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(
                                            painter = painterResource(R.drawable.close),
                                            contentDescription = stringResource(R.string.clear),
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                    )
                },
                expanded = false,
                onExpandedChange = {},
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp),
            ) {}

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when {
                    history.items.isEmpty() -> {
                        item(key = "empty_history", contentType = "empty_history") {
                            RecognitionHistoryEmptyState(
                                iconRes = R.drawable.history,
                                title = stringResource(R.string.music_recognition_history_empty_title),
                                body = stringResource(R.string.music_recognition_history_empty_body),
                            )
                        }
                    }

                    filteredItems.isEmpty() -> {
                        item(key = "empty_search", contentType = "empty_search") {
                            RecognitionHistoryEmptyState(
                                iconRes = R.drawable.search_off,
                                title = stringResource(R.string.music_recognition_history_no_results_title),
                                body = stringResource(R.string.music_recognition_history_no_results_body),
                            )
                        }
                    }

                    else -> {
                        items(
                            items = filteredItems,
                            key = { it.stableKey },
                            contentType = { "recognition_history_item" },
                        ) { item ->
                            RecognitionHistoryCard(
                                item = item,
                                isWide = isWide,
                                onSearch = { onSearch(item.searchQuery) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecognitionHistoryEmptyState(
    iconRes: Int,
    title: String,
    body: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecognitionHistoryCard(
    item: RecognitionHistoryItem,
    isWide: Boolean,
    onSearch: () -> Unit,
) {
    val context = LocalContext.current
    val cover = item.coverArtHqUrl ?: item.coverArtUrl
    val metadata = remember(item.album, item.genre, item.releaseDate) { buildHistoryMetadata(item) }
    val recognizedAt =
        remember(item.recognizedAtEpochMillis) {
            DateFormat
                .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(item.recognizedAtEpochMillis))
        }
    val coverSize = if (isWide) 76.dp else 68.dp

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CoverArt(
                coverUrl = cover,
                modifier = Modifier.size(coverSize),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = if (isWide) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (metadata.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = metadata,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.calendar_today),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        text = stringResource(R.string.music_recognition_history_recognized_at, recognizedAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!item.shazamUrl.isNullOrBlank()) {
                    RecognitionHistoryActionIcon(
                        iconRes = R.drawable.link,
                        contentDescription = stringResource(R.string.music_recognition_open_shazam),
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(item.shazamUrl)),
                            )
                        },
                    )
                }
                RecognitionHistoryActionIcon(
                    iconRes = R.drawable.search,
                    contentDescription = stringResource(R.string.search),
                    onClick = onSearch,
                )
            }
        }
    }
}

@Composable
private fun RecognitionHistoryActionIcon(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = contentDescription,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun RecognitionListenPane(
    state: MusicRecognitionState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    val isListening = state is MusicRecognitionState.Listening
    val isProcessing = state is MusicRecognitionState.Processing
    val isBusy = isListening || isProcessing

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IdleHeader()

        Spacer(modifier = Modifier.height(28.dp))

        ListeningOrb(
            modifier = Modifier.size(260.dp),
            isActive = isListening,
            isProcessing = isProcessing,
            onClick = onStart,
        )

        Spacer(modifier = Modifier.height(26.dp))

        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn(tween(180)).togetherWith(fadeOut(tween(120))) },
            label = "statusContent",
        ) { target ->
            when (target) {
                MusicRecognitionState.Ready -> {
                    StatusPill(
                        label = stringResource(R.string.music_recognition_tap_to_listen),
                        iconRes = R.drawable.mic,
                    )
                }

                MusicRecognitionState.Listening -> {
                    StatusPill(
                        label = stringResource(R.string.music_recognition_listening),
                        iconRes = R.drawable.listening,
                    )
                }

                MusicRecognitionState.Processing -> {
                    StatusPill(
                        label = stringResource(R.string.music_recognition_processing),
                        iconRes = R.drawable.cached,
                    )
                }

                MusicRecognitionState.PermissionRequired -> {
                    PermissionCard(onAllow = onRequestPermission)
                }

                is MusicRecognitionState.NoMatch -> {
                    FailureCard(
                        title = stringResource(R.string.music_recognition_no_match),
                        message = target.message,
                        actionLabel = stringResource(R.string.music_recognition_listen_again),
                        onAction = onStart,
                    )
                }

                is MusicRecognitionState.Error -> {
                    FailureCard(
                        title = stringResource(R.string.music_recognition_error),
                        message = target.message,
                        actionLabel = stringResource(R.string.music_recognition_listen_again),
                        onAction = onStart,
                    )
                }

                is MusicRecognitionState.Success -> {
                    Unit
                }
            }
        }

        AnimatedVisibility(
            visible = isBusy,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(120)),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                LoadingIndicator(modifier = Modifier.size(36.dp))
            }
        }

        AnimatedVisibility(
            visible = isListening,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(120)),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier =
                    Modifier
                        .padding(top = 16.dp)
                        .heightIn(min = 48.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

private sealed interface MusicRecognitionState {
    data object Ready : MusicRecognitionState

    data object Listening : MusicRecognitionState

    data object Processing : MusicRecognitionState

    data object PermissionRequired : MusicRecognitionState

    data class Success(
        val result: RecognitionResult,
    ) : MusicRecognitionState

    data class NoMatch(
        val message: String,
    ) : MusicRecognitionState

    data class Error(
        val message: String,
    ) : MusicRecognitionState
}

@Immutable
private data class MusicRecognitionStrings(
    val signatureFailed: String,
    val noMatchFallback: String,
    val recognitionFailedFallback: String,
)

@Serializable
@Immutable
private data class RecognitionHistoryItem(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val coverArtUrl: String?,
    val coverArtHqUrl: String?,
    val genre: String?,
    val releaseDate: String?,
    val shazamUrl: String?,
    val isrc: String?,
    val recognizedAtEpochMillis: Long,
) {
    val stableKey: String
        get() = recognitionIdentity(trackId, title, artist, isrc)

    val searchQuery: String
        get() = "$title $artist".trim()
}

@Immutable
private data class RecognitionHistoryCollection(
    val items: List<RecognitionHistoryItem>,
)

private val MusicRecognitionHistoryJsonKey = stringPreferencesKey("musicRecognitionHistoryJson")
private const val MusicRecognitionHistoryLimit = 50

private val MusicRecognitionHistoryJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

private fun decodeRecognitionHistory(raw: String?): List<RecognitionHistoryItem> =
    raw
        ?.takeIf { it.isNotBlank() }
        ?.let {
            runCatching {
                MusicRecognitionHistoryJson.decodeFromString<List<RecognitionHistoryItem>>(it)
            }.getOrDefault(emptyList())
        }
        ?: emptyList()

private suspend fun insertRecognitionHistory(
    context: Context,
    result: RecognitionResult,
) {
    val entry = result.toRecognitionHistoryItem(System.currentTimeMillis())
    context.dataStore.edit { preferences ->
        val current = decodeRecognitionHistory(preferences[MusicRecognitionHistoryJsonKey])
        val next =
            buildList {
                add(entry)
                addAll(current.filterNot { it.stableKey == entry.stableKey })
            }.take(MusicRecognitionHistoryLimit)
        preferences[MusicRecognitionHistoryJsonKey] = MusicRecognitionHistoryJson.encodeToString(next)
    }
}

private fun RecognitionResult.toRecognitionHistoryItem(recognizedAtEpochMillis: Long): RecognitionHistoryItem =
    RecognitionHistoryItem(
        trackId = trackId,
        title = title,
        artist = artist,
        album = album,
        coverArtUrl = coverArtUrl,
        coverArtHqUrl = coverArtHqUrl,
        genre = genre,
        releaseDate = releaseDate,
        shazamUrl = shazamUrl,
        isrc = isrc,
        recognizedAtEpochMillis = recognizedAtEpochMillis,
    )

private fun recognitionIdentity(
    trackId: String,
    title: String,
    artist: String,
    isrc: String?,
): String =
    trackId.takeIf { it.isNotBlank() }
        ?: listOf(title, artist, isrc.orEmpty())
            .joinToString("|") { it.trim().lowercase() }

private fun RecognitionHistoryItem.matches(query: String): Boolean {
    if (query.isBlank()) return true
    return listOf(title, artist, album, genre, releaseDate, isrc)
        .filterNotNull()
        .any { it.contains(query, ignoreCase = true) }
}

private fun launchRecognition(
    scope: kotlinx.coroutines.CoroutineScope,
    strings: MusicRecognitionStrings,
    onState: (MusicRecognitionState) -> Unit,
    onHaptic: () -> Unit,
    onReplaceJob: (Job) -> Unit,
) {
    onReplaceJob(
        scope.launch {
            runRecognitionFlow(
                strings = strings,
                onState = onState,
                onHaptic = onHaptic,
            )
        },
    )
}

private suspend fun runRecognitionFlow(
    strings: MusicRecognitionStrings,
    onState: (MusicRecognitionState) -> Unit,
    onHaptic: () -> Unit,
) {
    onHaptic()
    onState(MusicRecognitionState.Listening)

    val samples =
        withContext(Dispatchers.IO) {
            recordMicPcm16Mono(
                sampleRateHz = 16000,
                recordMs = 4200L,
            ).first
        }

    onState(MusicRecognitionState.Processing)

    val signature =
        withContext(Dispatchers.Default) {
            ShazamSignatureGenerator()
                .apply {
                    feedPcm16Mono(samples)
                }.nextSignatureOrNull()
        }

    if (signature == null) {
        onState(MusicRecognitionState.Error(strings.signatureFailed))
        return
    }

    val result =
        withContext(Dispatchers.IO) {
            Shazam.recognize(signature.uri, signature.sampleDurationMs)
        }

    result.fold(
        onSuccess = { onState(MusicRecognitionState.Success(it)) },
        onFailure = { e ->
            val msg = e.message?.trim().orEmpty()
            when {
                msg.contains("no match", ignoreCase = true) || msg.contains("404") -> {
                    onState(MusicRecognitionState.NoMatch(msg.ifEmpty { strings.noMatchFallback }))
                }

                else -> {
                    onState(MusicRecognitionState.Error(msg.ifEmpty { strings.recognitionFailedFallback }))
                }
            }
        },
    )
}

private fun buildMetadata(result: RecognitionResult): String {
    val pieces =
        listOfNotNull(
            result.album?.takeIf { it.isNotBlank() },
            result.genre?.takeIf { it.isNotBlank() },
            result.releaseDate?.takeIf { it.isNotBlank() },
        )
    return pieces.joinToString(" • ")
}

private fun buildHistoryMetadata(item: RecognitionHistoryItem): String {
    val pieces =
        listOfNotNull(
            item.album?.takeIf { it.isNotBlank() },
            item.genre?.takeIf { it.isNotBlank() },
            item.releaseDate?.takeIf { it.isNotBlank() },
        )
    return pieces.joinToString(" - ")
}

private suspend fun recordMicPcm16Mono(
    sampleRateHz: Int,
    recordMs: Long,
): Pair<ShortArray, Int> =
    withContext(Dispatchers.IO) {
        val channel = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioRecord.getMinBufferSize(sampleRateHz, channel, encoding).coerceAtLeast(4096)
        val record =
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRateHz,
                channel,
                encoding,
                minBuffer,
            )

        val totalSamples = ((recordMs / 1000.0) * sampleRateHz).toInt().coerceAtLeast(sampleRateHz)
        val output = ShortArray(totalSamples)
        val buffer = ShortArray(minBuffer / 2)

        try {
            record.startRecording()

            var written = 0
            while (written < output.size && isActive) {
                val read = record.read(buffer, 0, minOf(buffer.size, output.size - written))
                if (read > 0) {
                    System.arraycopy(buffer, 0, output, written, read)
                    written += read
                }
            }

            if (written <= 0) {
                ShortArray(0) to sampleRateHz
            } else {
                output.copyOf(written) to sampleRateHz
            }
        } finally {
            runCatching { record.stop() }
            runCatching { record.release() }
        }
    }

@Composable
private fun IdleHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.music_recognition),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.music_recognition_tap_to_listen),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultFirstSheet(
    result: RecognitionResult,
    isWide: Boolean,
    onSearch: () -> Unit,
    onListenAgain: () -> Unit,
) {
    val context = LocalContext.current
    val cover = result.coverArtHqUrl ?: result.coverArtUrl
    val metadata = remember(result.album, result.genre, result.releaseDate) { buildMetadata(result) }
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val heroColors =
        remember(primary, tertiary, surfaceContainerHigh) {
            listOf(
                primary.copy(alpha = 0.34f),
                tertiary.copy(alpha = 0.22f),
                surfaceContainerHigh,
            )
        }
    val heroBrush = remember(heroColors) { Brush.verticalGradient(colors = heroColors) }
    val coverSize = if (isWide) 136.dp else 118.dp

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors =
                CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
        ) {
            Column {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(heroBrush)
                            .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CoverArt(
                        coverUrl = cover,
                        modifier = Modifier.size(coverSize),
                    )

                    Column(modifier = Modifier.weight(1f, fill = true)) {
                        Text(
                            text = result.title,
                            style = if (isWide) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = result.artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        if (metadata.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            StatusPill(label = metadata, iconRes = R.drawable.info)
                        }
                    }
                }

                Column(modifier = Modifier.padding(18.dp)) {
                    FlowChips(
                        album = result.album,
                        genre = result.genre,
                        releaseDate = result.releaseDate,
                        isrc = result.isrc,
                    )

                    if (!result.shazamUrl.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(result.shazamUrl)),
                                )
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.link),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(R.string.music_recognition_open_shazam))
                        }
                    }

                    ResultDetailSections(result = result)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        SuccessActions(
            onSearch = onSearch,
            onListenAgain = onListenAgain,
        )
    }
}

@Composable
private fun ResultDetailSections(result: RecognitionResult) {
    val lyrics =
        remember(result.lyrics) {
            result.lyrics
                ?.takeIf { it.isNotEmpty() }
                ?.take(6)
                ?.joinToString("\n")
        }
    val label = remember(result.label) { result.label?.takeIf { it.isNotBlank() } }

    if (!lyrics.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(16.dp))
        ResultInfoBlock(
            iconRes = R.drawable.lyrics,
            title = stringResource(R.string.music_recognition_lyrics_preview),
            body = lyrics,
            maxLines = 6,
        )
    }

    if (label != null) {
        Spacer(modifier = Modifier.height(12.dp))
        ResultInfoBlock(
            iconRes = R.drawable.info,
            title = label,
            body = label,
            maxLines = 1,
            showTitleBody = false,
        )
    }
}

@Composable
private fun ResultInfoBlock(
    iconRes: Int,
    title: String,
    body: String,
    maxLines: Int,
    showTitleBody: Boolean = true,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (showTitleBody) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ListeningOrb(
    modifier: Modifier,
    isActive: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit,
) {
    val ringProgress: Float
    val ringProgress2: Float
    if (isActive) {
        val infinite = rememberInfiniteTransition(label = "orbPulse")
        val animatedRingProgress by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = tween(1700, easing = LinearEasing)),
            label = "ring1",
        )
        val animatedRingProgress2 by infinite.animateFloat(
            initialValue = 0.25f,
            targetValue = 1.25f,
            animationSpec = infiniteRepeatable(animation = tween(2100, easing = LinearEasing)),
            label = "ring2",
        )
        ringProgress = animatedRingProgress
        ringProgress2 = animatedRingProgress2
    } else {
        ringProgress = 0f
        ringProgress2 = 0f
    }

    val orbScale by animateFloatAsState(
        targetValue = if (isActive) 1.03f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "orbScale",
    )

    val baseColor =
        if (isProcessing) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh

    val container =
        remember(baseColor, surfaceContainerHigh) {
            Brush.radialGradient(
                colors =
                    listOf(
                        baseColor.copy(alpha = 0.42f),
                        baseColor.copy(alpha = 0.16f),
                        surfaceContainerHigh.copy(alpha = 0.9f),
                    ),
            )
        }
    val density = LocalDensity.current
    val ringStrokeWidth = remember(density) { with(density) { 10.dp.toPx() } }
    val ringStroke = remember(ringStrokeWidth) { Stroke(width = ringStrokeWidth, cap = StrokeCap.Round) }

    Box(
        modifier =
            modifier
                .scale(orbScale)
                .clip(CircleShape)
                .background(container)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            if (isActive) {
                drawRing(center, r, ringProgress, baseColor, ringStroke)
                drawRing(center, r, ringProgress2, baseColor.copy(alpha = 0.85f), ringStroke)
            }

            val glow = baseColor.copy(alpha = if (isActive) 0.22f else 0.12f)
            drawCircle(glow, radius = r * 0.88f, center = center)
            drawCircle(Color.Black.copy(alpha = 0.06f), radius = r * 0.78f, center = center)
        }

        val icon =
            when {
                isProcessing -> R.drawable.cached
                isActive -> R.drawable.listening
                else -> R.drawable.mic
            }

        val iconAlpha by animateFloatAsState(
            targetValue = if (isProcessing) 0.9f else 1f,
            animationSpec = tween(180),
            label = "iconAlpha",
        )

        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(54.dp).alpha(iconAlpha),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRing(
    center: Offset,
    baseRadius: Float,
    progress: Float,
    color: Color,
    stroke: Stroke,
) {
    val p = progress.coerceIn(0f, 1f)
    val radius = baseRadius * (0.62f + 0.55f * p)
    val alpha = (1f - p) * 0.55f
    drawCircle(
        color = color.copy(alpha = alpha),
        radius = radius,
        center = center,
        style = stroke,
    )
}

@Composable
private fun StatusPill(
    label: String,
    iconRes: Int,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PermissionCard(onAllow: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.music_recognition_permission_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.music_recognition_permission_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onAllow,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.music_recognition_permission_action))
            }
        }
    }
}

@Composable
private fun FailureCard(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (message.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            FilledTonalButton(onClick = onAction, shapes = ButtonDefaults.shapes()) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun SuccessActions(
    onSearch: () -> Unit,
    onListenAgain: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        ToggleButton(
            checked = false,
            onCheckedChange = { onListenAgain() },
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            colors =
                ToggleButtonDefaults.toggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
        ) {
            Icon(
                painter = painterResource(R.drawable.replay),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.music_recognition_listen_again),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
        }

        ToggleButton(
            checked = false,
            onCheckedChange = { onSearch() },
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            colors =
                ToggleButtonDefaults.toggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
        ) {
            Icon(
                painter = painterResource(R.drawable.search),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.search),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun CoverArt(
    coverUrl: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(context)
                        .data(coverUrl)
                        .allowHardware(false)
                        .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FlowChips(
    album: String?,
    genre: String?,
    releaseDate: String?,
    isrc: String?,
) {
    val items =
        remember(album, genre, releaseDate, isrc) {
            buildList {
                album?.takeIf { it.isNotBlank() }?.let { add(ChipData(R.drawable.album, it)) }
                genre?.takeIf { it.isNotBlank() }?.let { add(ChipData(R.drawable.info, it)) }
                releaseDate?.takeIf { it.isNotBlank() }?.let { add(ChipData(R.drawable.calendar_today, it)) }
                isrc?.takeIf { it.isNotBlank() }?.let { add(ChipData(R.drawable.link, it)) }
            }
        }

    if (items.isEmpty()) return

    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { chip ->
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        text = chip.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(chip.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                colors =
                    AssistChipDefaults.assistChipColors(
                        containerColor = containerColor,
                        labelColor = labelColor,
                        leadingIconContentColor = labelColor,
                    ),
                border = null,
            )
        }
    }
}

@Immutable
private data class ChipData(
    val iconRes: Int,
    val label: String,
)
