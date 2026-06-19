/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.TogetherAllowGuestsToAddTracksKey
import moe.rukamori.archivetune.constants.TogetherAllowGuestsToControlPlaybackKey
import moe.rukamori.archivetune.constants.TogetherDefaultPortKey
import moe.rukamori.archivetune.constants.TogetherDisplayNameKey
import moe.rukamori.archivetune.constants.TogetherLastJoinLinkKey
import moe.rukamori.archivetune.constants.TogetherRequireHostApprovalToJoinKey
import moe.rukamori.archivetune.constants.TogetherWelcomeShownKey
import moe.rukamori.archivetune.together.TogetherLink
import moe.rukamori.archivetune.together.TogetherRole
import moe.rukamori.archivetune.together.TogetherRoomSettings
import moe.rukamori.archivetune.together.TogetherSessionState
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.rememberPreference
import kotlin.math.floor
import moe.rukamori.archivetune.ui.component.IconButton as AtIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicTogetherScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val coroutineScope = rememberCoroutineScope()

    val welcomeShown by remember(context) {
        context.dataStore.data
            .map<Preferences, Boolean?> { preferences ->
                preferences[TogetherWelcomeShownKey] ?: false
            }.distinctUntilChanged()
    }.collectAsState(initial = null)
    var welcomeDismissedThisSession by rememberSaveable { mutableStateOf(false) }
    val showWelcome = welcomeShown == false && !welcomeDismissedThisSession

    if (showWelcome) {
        WelcomeDialog(
            onGotIt = { dontShowAgain ->
                welcomeDismissedThisSession = true
                if (dontShowAgain) {
                    coroutineScope.launch {
                        context.dataStore.edit { preferences ->
                            preferences[TogetherWelcomeShownKey] = true
                        }
                    }
                }
            },
            onDismiss = { welcomeDismissedThisSession = true },
        )
    }

    val (displayName, setDisplayName) =
        rememberPreference(
            TogetherDisplayNameKey,
            defaultValue = Build.MODEL?.takeIf { it.isNotBlank() } ?: context.getString(R.string.app_name),
        )
    val (port, setPort) = rememberPreference(TogetherDefaultPortKey, defaultValue = 42117)
    val (allowAddTracks, setAllowAddTracksRaw) = rememberPreference(TogetherAllowGuestsToAddTracksKey, defaultValue = true)
    val (allowControlPlayback, setAllowControlPlaybackRaw) =
        rememberPreference(
            TogetherAllowGuestsToControlPlaybackKey,
            defaultValue = false,
        )
    val (requireApproval, setRequireApprovalRaw) = rememberPreference(TogetherRequireHostApprovalToJoinKey, defaultValue = false)
    val (lastJoinLink, setLastJoinLink) = rememberPreference(TogetherLastJoinLinkKey, defaultValue = "")

    val sessionStateFlow =
        remember(playerConnection) {
            playerConnection?.service?.togetherSessionState ?: MutableStateFlow(TogetherSessionState.Idle)
        }
    val sessionState by sessionStateFlow.collectAsState()

    val isHosting = sessionState is TogetherSessionState.Hosting || sessionState is TogetherSessionState.HostingOnline
    val isJoining = sessionState is TogetherSessionState.Joining || sessionState is TogetherSessionState.JoiningOnline
    val isHostRole =
        when (val state = sessionState) {
            is TogetherSessionState.Hosting,
            is TogetherSessionState.HostingOnline,
            -> true

            is TogetherSessionState.Joined -> state.role is TogetherRole.Host

            else -> false
        }
    val isCreatingSessionLoading =
        when (val state = sessionState) {
            is TogetherSessionState.Hosting -> state.roomState == null
            is TogetherSessionState.HostingOnline -> state.roomState == null
            else -> false
        }
    val isJoinedAsGuest =
        when (val state = sessionState) {
            is TogetherSessionState.Joined -> state.role is TogetherRole.Guest
            else -> false
        }
    val isWaitingApproval =
        when (val state = sessionState) {
            is TogetherSessionState.Joined -> {
                state.role is TogetherRole.Guest &&
                    state.roomState.participants
                        .firstOrNull { it.id == state.selfParticipantId }
                        ?.isPending == true
            }

            else -> {
                false
            }
        }
    val isJoinedAsAcceptedGuest = isJoinedAsGuest && !isWaitingApproval
    val disableJoinUi = isHostRole || isCreatingSessionLoading || isJoinedAsGuest

    var showNameDialog by rememberSaveable { mutableStateOf(false) }
    var showPortDialog by rememberSaveable { mutableStateOf(false) }
    var showJoinDialog by rememberSaveable { mutableStateOf(false) }

    val hostingOnline = sessionState as? TogetherSessionState.HostingOnline
    val onlineParticipants = hostingOnline?.roomState?.participants.orEmpty()
    var confirmKickParticipantId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmBanParticipantId by rememberSaveable { mutableStateOf<String?>(null) }
    val confirmKickName = onlineParticipants.firstOrNull { it.id == confirmKickParticipantId }?.name
    val confirmBanName = onlineParticipants.firstOrNull { it.id == confirmBanParticipantId }?.name

    LaunchedEffect(disableJoinUi, isJoining, isHosting) {
        if (disableJoinUi || isJoining || isHosting) showJoinDialog = false
    }

    var hostModeOnline by rememberSaveable { mutableStateOf(false) }
    var joinModeOnline by rememberSaveable { mutableStateOf(false) }

    fun pushSettingsToActiveSession(
        addTracks: Boolean = allowAddTracks,
        controlPlayback: Boolean = allowControlPlayback,
        approval: Boolean = requireApproval,
    ) {
        if (isHosting) {
            playerConnection?.service?.updateTogetherSettings(
                TogetherRoomSettings(
                    allowGuestsToAddTracks = addTracks,
                    allowGuestsToControlPlayback = controlPlayback,
                    requireHostApprovalToJoin = approval,
                ),
            )
        }
    }

    val setAllowAddTracks: (Boolean) -> Unit = { value ->
        setAllowAddTracksRaw(value)
        pushSettingsToActiveSession(addTracks = value)
    }
    val setAllowControlPlayback: (Boolean) -> Unit = { value ->
        setAllowControlPlaybackRaw(value)
        pushSettingsToActiveSession(controlPlayback = value)
    }
    val setRequireApproval: (Boolean) -> Unit = { value ->
        setRequireApprovalRaw(value)
        pushSettingsToActiveSession(approval = value)
    }

    if (showNameDialog) {
        TextFieldDialog(
            title = { Text(text = stringResource(R.string.together_display_name)) },
            placeholder = { Text(text = stringResource(R.string.together_display_name_placeholder)) },
            isInputValid = { it.trim().isNotBlank() },
            onDone = { setDisplayName(it.trim()) },
            onDismiss = { showNameDialog = false },
        )
    }

    if (showPortDialog) {
        TextFieldDialog(
            title = { Text(text = stringResource(R.string.together_port)) },
            placeholder = { Text(text = "42117") },
            isInputValid = { it.trim().toIntOrNull() in 1..65535 },
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
            onDone = { setPort(it.trim().toInt()) },
            onDismiss = { showPortDialog = false },
        )
    }

    var joinInput by rememberSaveable { mutableStateOf(lastJoinLink) }
    val canJoin =
        remember(joinInput, joinModeOnline) {
            if (joinModeOnline) joinInput.trim().isNotBlank() else TogetherLink.decode(joinInput) != null
        }

    if (showJoinDialog) {
        val placeholder =
            if (joinModeOnline) {
                stringResource(R.string.together_join_code_hint)
            } else {
                stringResource(R.string.together_join_link_hint)
            }
        TextFieldDialog(
            title = { Text(text = stringResource(R.string.join_session)) },
            placeholder = { Text(text = placeholder) },
            singleLine = false,
            maxLines = 8,
            isInputValid = { if (joinModeOnline) it.trim().isNotBlank() else TogetherLink.decode(it) != null },
            onDone = { raw ->
                val trimmed = raw.trim()
                joinInput = trimmed
                setLastJoinLink(trimmed)
                if (joinModeOnline) {
                    playerConnection?.service?.joinTogetherOnline(trimmed, displayName)
                } else {
                    playerConnection?.service?.joinTogether(trimmed, displayName)
                }
            },
            onDismiss = { showJoinDialog = false },
        )
    }

    if (confirmKickParticipantId != null) {
        AlertDialog(
            onDismissRequest = { confirmKickParticipantId = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(text = stringResource(R.string.together_kick)) },
            text = {
                Text(
                    text = stringResource(R.string.together_kick_confirm, confirmKickName ?: stringResource(R.string.unknown)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pid = confirmKickParticipantId ?: return@Button
                        confirmKickParticipantId = null
                        playerConnection?.service?.kickTogetherParticipant(pid)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(R.string.together_kick), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmKickParticipantId = null },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(R.string.dismiss))
                }
            },
        )
    }

    if (confirmBanParticipantId != null) {
        AlertDialog(
            onDismissRequest = { confirmBanParticipantId = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(text = stringResource(R.string.together_ban)) },
            text = {
                Text(
                    text = stringResource(R.string.together_ban_confirm, confirmBanName ?: stringResource(R.string.unknown)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pid = confirmBanParticipantId ?: return@Button
                        confirmBanParticipantId = null
                        playerConnection?.service?.banTogetherParticipant(pid)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(R.string.together_ban), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmBanParticipantId = null },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(R.string.dismiss))
                }
            },
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.music_together)) },
            navigationIcon = {
                AtIconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                ) {
                    Icon(painterResource(R.drawable.arrow_back), null)
                }
            },
            scrollBehavior = scrollBehavior,
        )

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = SettingsDimensions.ScreenBottomPadding),
        ) {
            StatusCard(
                state = sessionState,
                onCopyText = { labelRes, value ->
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(
                        android.content.ClipData.newPlainText(context.getString(labelRes), value),
                    )
                    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                },
                onShareLink = { link ->
                    val share =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, link)
                        }
                    context.startActivity(Intent.createChooser(share, null))
                },
                onLeave = { playerConnection?.service?.leaveTogether() },
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 4.dp, bottom = 12.dp),
            )

            if (hostingOnline?.roomState != null && isHostRole) {
                OnlineParticipantsCard(
                    participants = hostingOnline.roomState.participants,
                    hostApprovalEnabled = hostingOnline.settings.requireHostApprovalToJoin,
                    onApprove = { participantId, approved ->
                        playerConnection?.service?.approveTogetherParticipant(participantId, approved)
                    },
                    onKick = { participantId ->
                        confirmKickParticipantId = participantId
                    },
                    onBan = { participantId ->
                        confirmBanParticipantId = participantId
                    },
                    modifier =
                        Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp),
                )
            }

            if (!isJoinedAsGuest) {
                HostSectionCard(
                    hostModeOnline = hostModeOnline,
                    onHostModeChange = { hostModeOnline = it },
                    displayName = displayName,
                    port = port,
                    allowAddTracks = allowAddTracks,
                    allowControlPlayback = allowControlPlayback,
                    requireApproval = requireApproval,
                    onShowNameDialog = { showNameDialog = true },
                    onShowPortDialog = { showPortDialog = true },
                    onAllowAddTracksChange = setAllowAddTracks,
                    onAllowControlPlaybackChange = setAllowControlPlayback,
                    onRequireApprovalChange = setRequireApproval,
                    isStartEnabled = !isCreatingSessionLoading && !isJoining && !isHosting && sessionState !is TogetherSessionState.Joined,
                    isLoading = isCreatingSessionLoading,
                    onStartSession = {
                        val settings =
                            TogetherRoomSettings(
                                allowGuestsToAddTracks = allowAddTracks,
                                allowGuestsToControlPlayback = allowControlPlayback,
                                requireHostApprovalToJoin = requireApproval,
                            )
                        if (hostModeOnline) {
                            playerConnection?.service?.startTogetherOnlineHost(
                                displayName = displayName,
                                settings = settings,
                            )
                        } else {
                            playerConnection?.service?.startTogetherHost(
                                port = port,
                                displayName = displayName,
                                settings = settings,
                            )
                        }
                    },
                    modifier =
                        Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp),
                )
            }

            JoinSectionCard(
                joinModeOnline = joinModeOnline,
                onJoinModeChange = { joinModeOnline = it },
                joinInput = joinInput,
                canJoin = canJoin,
                disableJoinUi = disableJoinUi,
                isJoined = isJoinedAsAcceptedGuest,
                isWaitingApproval = isWaitingApproval,
                isJoining = isJoining,
                onShowJoinDialog = { showJoinDialog = true },
                onJoin = {
                    val trimmed = joinInput.trim()
                    setLastJoinLink(trimmed)
                    if (joinModeOnline) {
                        playerConnection?.service?.joinTogetherOnline(trimmed, displayName)
                    } else {
                        playerConnection?.service?.joinTogether(trimmed, displayName)
                    }
                },
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun OnlineParticipantsCard(
    participants: List<moe.rukamori.archivetune.together.TogetherParticipant>,
    hostApprovalEnabled: Boolean,
    onApprove: (participantId: String, approved: Boolean) -> Unit,
    onKick: (participantId: String) -> Unit,
    onBan: (participantId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    colors =
                                        listOf(
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
                                        ),
                                ),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.list),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.together_participants),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.together_connected_count, participants.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            participants.forEachIndexed { index, participant ->
                key(participant.id) {
                    if (index != 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 76.dp, end = 18.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        )
                    }

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        val accent =
                            if (participant.isHost) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        Box(
                            modifier =
                                Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(accent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(if (participant.isHost) R.drawable.fire else R.drawable.person),
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = participant.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val subtitle =
                                when {
                                    participant.isHost -> stringResource(R.string.together_role_host)
                                    participant.isPending && hostApprovalEnabled -> stringResource(R.string.together_pending_approval)
                                    else -> stringResource(R.string.together_role_guest)
                                }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        if (!participant.isHost) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (participant.isPending && hostApprovalEnabled) {
                                    AtIconButton(
                                        onClick = { onApprove(participant.id, true) },
                                        onLongClick = {},
                                    ) {
                                        Icon(painterResource(R.drawable.check), null)
                                    }
                                    AtIconButton(
                                        onClick = { onApprove(participant.id, false) },
                                        onLongClick = {},
                                    ) {
                                        Icon(painterResource(R.drawable.close), null)
                                    }
                                }

                                AtIconButton(
                                    onClick = { onKick(participant.id) },
                                    onLongClick = {},
                                ) {
                                    Icon(painterResource(R.drawable.kick), null)
                                }
                                AtIconButton(
                                    onClick = { onBan(participant.id) },
                                    onLongClick = {},
                                ) {
                                    Icon(painterResource(R.drawable.block), null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeDialog(
    onGotIt: (dontShowAgain: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var dontShowAgain by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    colors =
                                        listOf(
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        ),
                                ),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.fire),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.together_welcome_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.together_welcome_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        InstructionRow(
                            icon = R.drawable.fire,
                            accentColor = MaterialTheme.colorScheme.primary,
                            title = stringResource(R.string.together_welcome_host_title),
                            body = stringResource(R.string.together_welcome_host_body),
                        )
                        InstructionRow(
                            icon = R.drawable.link,
                            accentColor = MaterialTheme.colorScheme.tertiary,
                            title = stringResource(R.string.together_welcome_join_title),
                            body = stringResource(R.string.together_welcome_join_body),
                        )
                        InstructionRow(
                            icon = R.drawable.lock,
                            accentColor = MaterialTheme.colorScheme.secondary,
                            title = stringResource(R.string.together_welcome_permissions_title),
                            body = stringResource(R.string.together_welcome_permissions_body),
                        )
                    }
                }
                val cbStrokeWidthPx = with(LocalDensity.current) { floor(CheckboxDefaults.StrokeWidth.toPx()) }
                val cbCheckmarkStroke =
                    remember(cbStrokeWidthPx) {
                        Stroke(width = cbStrokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    }
                val cbOutlineStroke = remember(cbStrokeWidthPx) { Stroke(width = cbStrokeWidthPx) }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .toggleable(
                                value = dontShowAgain,
                                role = Role.Checkbox,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onValueChange = { dontShowAgain = it },
                            ).padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = null,
                        checkmarkStroke = cbCheckmarkStroke,
                        outlineStroke = cbOutlineStroke,
                    )
                    Text(
                        text = stringResource(R.string.together_dont_show_again),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onGotIt(dontShowAgain) },
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.got_it),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}

@Composable
private fun InstructionRow(
    icon: Int,
    accentColor: androidx.compose.ui.graphics.Color,
    title: String,
    body: String,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HostSectionCard(
    hostModeOnline: Boolean,
    onHostModeChange: (Boolean) -> Unit,
    displayName: String,
    port: Int,
    allowAddTracks: Boolean,
    allowControlPlayback: Boolean,
    requireApproval: Boolean,
    onShowNameDialog: () -> Unit,
    onShowPortDialog: () -> Unit,
    onAllowAddTracksChange: (Boolean) -> Unit,
    onAllowControlPlaybackChange: (Boolean) -> Unit,
    onRequireApprovalChange: (Boolean) -> Unit,
    isStartEnabled: Boolean,
    isLoading: Boolean,
    onStartSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    colors =
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                        ),
                                ),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.fire),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.together_host_section),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.together_display_name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SingleChoiceSegmentedButtonRow(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 4.dp),
            ) {
                SegmentedButton(
                    selected = !hostModeOnline,
                    onClick = { onHostModeChange(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {},
                ) {
                    Text(text = stringResource(R.string.together_lan))
                }
                SegmentedButton(
                    selected = hostModeOnline,
                    onClick = { onHostModeChange(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {},
                ) {
                    Text(text = stringResource(R.string.together_online))
                }
            }

            SettingsItemRow(
                icon = R.drawable.person,
                title = stringResource(R.string.together_display_name),
                subtitle = displayName,
                onClick = onShowNameDialog,
            )

            HorizontalDivider(
                modifier = Modifier.padding(start = 76.dp, end = 18.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            )

            AnimatedVisibility(
                visible = !hostModeOnline,
                enter = fadeIn(tween(200)) + expandVertically(tween(250)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(200)),
            ) {
                Column {
                    SettingsItemRow(
                        icon = R.drawable.link,
                        title = stringResource(R.string.together_port),
                        subtitle = port.toString(),
                        onClick = onShowPortDialog,
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 76.dp, end = 18.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    )
                }
            }

            ToggleRow(
                icon = R.drawable.playlist_add,
                title = stringResource(R.string.together_allow_guests_add),
                checked = allowAddTracks,
                onCheckedChange = onAllowAddTracksChange,
            )

            HorizontalDivider(
                modifier = Modifier.padding(start = 76.dp, end = 18.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            )

            ToggleRow(
                icon = R.drawable.play,
                title = stringResource(R.string.together_allow_guests_control),
                checked = allowControlPlayback,
                onCheckedChange = onAllowControlPlaybackChange,
            )

            HorizontalDivider(
                modifier = Modifier.padding(start = 76.dp, end = 18.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            )

            ToggleRow(
                icon = R.drawable.lock,
                title = stringResource(R.string.together_require_approval),
                checked = requireApproval,
                onCheckedChange = onRequireApprovalChange,
            )

            Spacer(Modifier.height(8.dp))

            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.97f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "host_btn_scale",
            )

            Button(
                enabled = isStartEnabled,
                onClick = onStartSession,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 10.dp)
                        .scale(scale),
                interactionSource = interactionSource,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                shapes = ButtonDefaults.shapes(),
            ) {
                if (isLoading) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.loading),
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.fire),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.start_session),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JoinSectionCard(
    joinModeOnline: Boolean,
    onJoinModeChange: (Boolean) -> Unit,
    joinInput: String,
    canJoin: Boolean,
    disableJoinUi: Boolean,
    isJoined: Boolean,
    isWaitingApproval: Boolean,
    isJoining: Boolean,
    onShowJoinDialog: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    colors =
                                        listOf(
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
                                        ),
                                ),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.multi_user),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.together_join_section),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.join_session),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SingleChoiceSegmentedButtonRow(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 4.dp),
            ) {
                SegmentedButton(
                    selected = !joinModeOnline,
                    enabled = !disableJoinUi,
                    onClick = { onJoinModeChange(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {},
                ) {
                    Text(text = stringResource(R.string.together_join_link))
                }
                SegmentedButton(
                    selected = joinModeOnline,
                    enabled = !disableJoinUi,
                    onClick = { onJoinModeChange(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {},
                ) {
                    Text(text = stringResource(R.string.together_join_code))
                }
            }

            val hint =
                if (joinModeOnline) {
                    stringResource(R.string.together_join_code_hint)
                } else {
                    stringResource(R.string.together_join_link_hint)
                }

            SettingsItemRow(
                icon = R.drawable.input,
                title = stringResource(R.string.join_session),
                subtitle = joinInput.trim().ifBlank { hint },
                subtitleMaxLines = 2,
                onClick = if (!disableJoinUi && !isJoining && !isJoined && !isWaitingApproval) onShowJoinDialog else null,
            )

            Spacer(Modifier.height(8.dp))

            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.97f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "join_btn_scale",
            )

            FilledTonalButton(
                enabled = canJoin && !disableJoinUi && !isJoining && !isJoined && !isWaitingApproval,
                onClick = onJoin,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 10.dp)
                        .scale(scale),
                interactionSource = interactionSource,
                shapes = ButtonDefaults.shapes(),
            ) {
                if (isJoining) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.connecting),
                        fontWeight = FontWeight.SemiBold,
                    )
                } else if (isWaitingApproval) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.together_waiting_approval),
                        fontWeight = FontWeight.SemiBold,
                    )
                } else if (isJoined) {
                    Icon(
                        painter = painterResource(R.drawable.check),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.joined),
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.join),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.join),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsItemRow(
    icon: Int,
    title: String,
    subtitle: String,
    subtitleMaxLines: Int = 1,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.7f else 1f,
        animationSpec = tween(100),
        label = "item_alpha",
    )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ).graphicsLayer { this.alpha = alpha }
                .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = subtitleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (onClick != null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                painter = painterResource(R.drawable.navigate_next),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ToggleRow(
    icon: Int,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(Modifier.width(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            thumbContent = {
                Icon(
                    painter =
                        painterResource(
                            id = if (checked) R.drawable.check else R.drawable.close,
                        ),
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            },
        )
    }
}

@Composable
private fun StatusCard(
    state: TogetherSessionState,
    onCopyText: (Int, String) -> Unit,
    onShareLink: (String) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActive = state !is TogetherSessionState.Idle
    val isError = state is TogetherSessionState.Error
    val isWaitingApproval =
        when (state) {
            is TogetherSessionState.Joined -> {
                state.role is TogetherRole.Guest &&
                    state.roomState.participants
                        .firstOrNull { it.id == state.selfParticipantId }
                        ?.isPending == true
            }

            else -> {
                false
            }
        }
    val statusColor =
        when {
            isError -> MaterialTheme.colorScheme.error
            isActive -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors =
                                if (isActive && !isError) {
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        MaterialTheme.colorScheme.surfaceContainerLow,
                                    )
                                } else if (isError) {
                                    listOf(
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                                        MaterialTheme.colorScheme.surfaceContainerLow,
                                    )
                                } else {
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceContainerLow,
                                        MaterialTheme.colorScheme.surfaceContainerLow,
                                    )
                                },
                        ),
                    ).padding(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors =
                                            listOf(
                                                statusColor.copy(alpha = 0.18f),
                                                statusColor.copy(alpha = 0.08f),
                                            ),
                                    ),
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (isError) R.drawable.error else R.drawable.fire,
                                ),
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.together_status),
                                style = MaterialTheme.typography.labelLarge,
                                color = statusColor,
                            )
                            if (isActive && !isError) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                        }
                        Text(
                            text =
                                when (state) {
                                    TogetherSessionState.Idle -> {
                                        stringResource(R.string.together_idle)
                                    }

                                    is TogetherSessionState.Hosting -> {
                                        stringResource(R.string.together_hosting)
                                    }

                                    is TogetherSessionState.HostingOnline -> {
                                        stringResource(R.string.together_hosting)
                                    }

                                    is TogetherSessionState.Joining -> {
                                        stringResource(R.string.together_joining)
                                    }

                                    is TogetherSessionState.JoiningOnline -> {
                                        stringResource(R.string.together_joining)
                                    }

                                    is TogetherSessionState.Joined -> {
                                        if (isWaitingApproval) {
                                            stringResource(R.string.together_waiting_approval)
                                        } else {
                                            stringResource(R.string.together_connected)
                                        }
                                    }

                                    is TogetherSessionState.Error -> {
                                        if (state.message == stringResource(R.string.together_host_left_session)) {
                                            state.message
                                        } else {
                                            stringResource(R.string.together_error_state)
                                        }
                                    }
                                },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (isActive) {
                        FilledTonalButton(
                            onClick = onLeave,
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.leave),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.leave),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                when (state) {
                    is TogetherSessionState.Hosting -> {
                        SessionInfoCard(
                            label = stringResource(R.string.session_link),
                            value = state.joinLink,
                            maxLines = 3,
                            onCopy = { onCopyText(R.string.session_link, state.joinLink) },
                            onShare = { onShareLink(state.joinLink) },
                        )
                    }

                    is TogetherSessionState.HostingOnline -> {
                        SessionInfoCard(
                            label = stringResource(R.string.session_code),
                            value = state.code,
                            maxLines = 2,
                            onCopy = { onCopyText(R.string.session_code, state.code) },
                            onShare = { onShareLink(state.code) },
                        )
                    }

                    is TogetherSessionState.Joined -> {
                        if (!isWaitingApproval) {
                            ParticipantsCard(participants = state.roomState.participants.map { it.name })
                        }
                    }

                    is TogetherSessionState.Error -> {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }

                    else -> {
                        Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionInfoCard(
    label: String,
    value: String,
    maxLines: Int,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = onCopy,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.copy),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(text = stringResource(R.string.copy_link))
                }
                TextButton(
                    onClick = onShare,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.share),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(text = stringResource(R.string.share))
                }
            }
        }
    }
}

@Composable
private fun ParticipantsCard(participants: List<String>) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.person),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.participants),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = participants.joinToString(separator = " · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
