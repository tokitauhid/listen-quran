package com.listenquran.ui.components

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.listenquran.BuildConfig
import com.listenquran.update.AppUpdateState
import com.listenquran.update.UpdateReleaseInfo

@Composable
fun HomeUpdateBanner(
    release: UpdateReleaseInfo,
    onViewUpdate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onViewUpdate,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Update available · v${release.versionName}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Tap to view release notes and install",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledTonalButton(
                onClick = onViewUpdate,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Update")
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Dismiss update",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateBottomSheet(
    state: AppUpdateState,
    onDownload: (UpdateReleaseInfo) -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onRetryCheck: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    val release = when (state) {
        is AppUpdateState.UpdateAvailable -> state.release
        is AppUpdateState.Downloading -> state.release
        is AppUpdateState.Verifying -> state.release
        is AppUpdateState.NeedsInstallPermission -> state.release
        is AppUpdateState.ReadyToInstall -> state.release
        is AppUpdateState.SignatureConflict -> state.release
        is AppUpdateState.Error -> state.release
        AppUpdateState.Idle, AppUpdateState.Checking, is AppUpdateState.UpToDate -> null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = release?.title ?: "App Updates",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    val sub = if (release != null) {
                        val sizeStr = if (release.sizeBytes > 0L) {
                            " · ${Formatter.formatShortFileSize(context, release.sizeBytes)}"
                        } else ""
                        "v${BuildConfig.VERSION_NAME} → v${release.versionName}$sizeStr"
                    } else {
                        "Current version: v${BuildConfig.VERSION_NAME}"
                    }
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (release != null && release.changelog.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                ) {
                    Text(
                        text = cleanMarkdownForDisplay(release.changelog),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp),
                    )
                }
            }

            when (state) {
                AppUpdateState.Checking -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "Checking GitHub Releases…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is AppUpdateState.UpToDate -> {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "You are on the latest version (v${BuildConfig.VERSION_NAME}).",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Close")
                    }
                }

                is AppUpdateState.UpdateAvailable -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = if (!state.release.sha256Digest.isNullOrBlank()) {
                                "Verified via GitHub SHA-256 & app signing key before install."
                            } else {
                                "Verified via app signing key before install."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = { onOpenBrowser(state.release.htmlUrl) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.OpenInBrowser, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("GitHub")
                        }
                        Button(
                            onClick = { onDownload(state.release) },
                            modifier = Modifier.weight(1.4f),
                        ) {
                            Icon(Icons.Outlined.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Download & Install")
                        }
                    }
                }

                is AppUpdateState.Downloading -> {
                    val dlStr = Formatter.formatShortFileSize(context, state.bytesDownloaded)
                    val totStr = if (state.totalBytes > 0L) {
                        Formatter.formatShortFileSize(context, state.totalBytes)
                    } else "…"
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (state.totalBytes > 0L) {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Text(
                            text = "Downloading ${state.release.assetName} · $dlStr / $totStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = onCancelDownload,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Cancel Download")
                    }
                }

                is AppUpdateState.Verifying -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "Verifying APK checksum and signature…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is AppUpdateState.NeedsInstallPermission -> {
                    Text(
                        text = "Allow Listen Quran to install app updates in Android Settings to continue.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            Text("Later")
                        }
                        Button(onClick = onRequestPermission, modifier = Modifier.weight(1.4f)) {
                            Text("Allow & Install")
                        }
                    }
                }

                is AppUpdateState.ReadyToInstall -> {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "APK verified. Your playlists and Khatam progress stay preserved.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Button(
                        onClick = onInstall,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Install v${state.release.versionName}")
                    }
                }

                is AppUpdateState.SignatureConflict -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text(
                                text = "This release uses a different signing certificate than your installed build. Export a backup in Settings before manually reinstalling from GitHub.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { onOpenBrowser(state.release.htmlUrl) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.OpenInBrowser, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open Release on GitHub")
                    }
                }

                is AppUpdateState.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (state.release != null) {
                            TextButton(
                                onClick = { onOpenBrowser(state.release.htmlUrl) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Open GitHub")
                            }
                        }
                        Button(
                            onClick = {
                                if (state.release != null) onDownload(state.release) else onRetryCheck()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Retry")
                        }
                    }
                }

                AppUpdateState.Idle -> Unit
            }
        }
    }
}

private fun cleanMarkdownForDisplay(raw: String): String {
    return raw
        .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
        .replace("**", "")
        .replace("---", "")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}
