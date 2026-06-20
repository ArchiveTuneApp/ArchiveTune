/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.player

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackException
import moe.rukamori.archivetune.MainActivity
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.utils.openYouTubeMusicUrl

@Composable
fun PlaybackError(
    error: PlaybackException,
    retry: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val fallbackUnknown = stringResource(R.string.error_unknown)
    val fallbackNoInternet = stringResource(R.string.error_no_internet)
    val fallbackTimeout = stringResource(R.string.error_timeout)
    val fallbackNoStream = stringResource(R.string.error_no_stream)
    val fallbackMalformedStream = stringResource(R.string.error_malformed_stream)
    val retryText = stringResource(R.string.retry)
    val copyText = stringResource(R.string.copy)
    val copiedText = stringResource(R.string.copied)
    val openYouTubeMusicText = stringResource(R.string.open_youtube_music)
    val loginText = stringResource(R.string.login)
    val couldNotOpenYouTubeMusicText = stringResource(R.string.could_not_open_youtube_music)
    val errorInfo = remember(error) { error.toPlaybackErrorInfo() }
    val httpCode = errorInfo.httpCode
    val title =
        when (errorInfo.kind) {
            PlaybackErrorKind.LoginRefreshRequired -> stringResource(R.string.playback_login_refresh_required)
            PlaybackErrorKind.ConfirmationRequired -> stringResource(R.string.playback_confirmation_required)
            else -> fallbackUnknown
        }
    val reason =
        when (errorInfo.kind) {
            PlaybackErrorKind.LoginRefreshRequired -> {
                stringResource(R.string.playback_requires_youtube_music_login_refresh)
            }

            PlaybackErrorKind.ConfirmationRequired -> {
                stringResource(R.string.playback_requires_youtube_music_confirmation)
            }

            PlaybackErrorKind.NoInternet -> {
                fallbackNoInternet
            }

            PlaybackErrorKind.Timeout -> {
                fallbackTimeout
            }

            PlaybackErrorKind.NoStream -> {
                fallbackNoStream
            }

            PlaybackErrorKind.MalformedStream -> {
                fallbackMalformedStream
            }

            PlaybackErrorKind.Decoder -> {
                "$fallbackUnknown (code ${error.errorCode})"
            }

            PlaybackErrorKind.Http -> {
                "$fallbackUnknown (HTTP $httpCode)"
            }

            PlaybackErrorKind.Unknown -> {
                error.cause?.message?.takeIf { it.isNotBlank() }
                    ?: error.message?.takeIf { it.isNotBlank() }
                    ?: fallbackUnknown
            }
        }

    val details =
        remember(error, reason, httpCode) {
            buildString {
                appendLine(reason)
                appendLine("Code: ${error.errorCode}")
                if (httpCode != null) appendLine("HTTP: $httpCode")

                val rootMessage = error.message?.trim().orEmpty()
                if (rootMessage.isNotBlank() && rootMessage != reason) {
                    appendLine()
                    appendLine("Message: $rootMessage")
                }

                var t: Throwable? = error.cause
                var depth = 0
                while (t != null && depth < 6) {
                    val name = t.javaClass.simpleName.ifBlank { t.javaClass.name }
                    val msg = t.message?.trim().orEmpty()
                    appendLine()
                    appendLine("Cause: $name${if (msg.isNotBlank()) ": $msg" else ""}")
                    t = t.cause
                    depth++
                }
            }.trim()
        }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.86f),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.info),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.06f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.92f),
                    modifier = Modifier.padding(12.dp),
                    maxLines = 12,
                    overflow = TextOverflow.Clip,
                )
            }

            val recoveryUrl = errorInfo.loginRecoveryUrl
            val recoveryAction = errorInfo.recoveryAction
            if (recoveryUrl != null && recoveryAction != null) {
                Button(
                    onClick = {
                        when (recoveryAction) {
                            PlaybackRecoveryAction.RefreshLogin -> {
                                val deepLink = Uri.parse("archivetune://login?url=${Uri.encode(recoveryUrl)}")
                                val loginIntent =
                                    Intent(Intent.ACTION_VIEW, deepLink, context, MainActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    }
                                runCatching { context.startActivity(loginIntent) }
                            }

                            PlaybackRecoveryAction.OpenYouTubeMusic -> {
                                if (!context.openYouTubeMusicUrl(recoveryUrl)) {
                                    Toast.makeText(context, couldNotOpenYouTubeMusicText, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onErrorContainer,
                            contentColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(
                        text =
                            when (recoveryAction) {
                                PlaybackRecoveryAction.RefreshLogin -> loginText
                                PlaybackRecoveryAction.OpenYouTubeMusic -> openYouTubeMusicText
                            },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = retry,
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = retryText)
                }

                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(details))
                        Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.select_all),
                        contentDescription = null,
                    )
                    androidx.compose.foundation.layout
                        .Spacer(Modifier.width(8.dp))
                    Text(text = copyText)
                }
            }
        }
    }
}
