package com.atv.adbcommander.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.atv.adbcommander.model.TransferProgress
import com.atv.adbcommander.ui.theme.*

@Composable
fun AppInstallProgressDialog(
    progress: TransferProgress,
    onDismiss: () -> Unit,
    onMinimize: () -> Unit = {},
    onLaunchOnTv: ((packageName: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Smooth continuous animation for progress bar
    val animatedProgressFraction by animateFloatAsState(
        targetValue = progress.progressFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 400, easing = LinearOutSlowInEasing),
        label = "continuousProgress"
    )

    // Pulsing glow effect during active TV OS install
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val isSuccess = progress.isDone && progress.error == null
    val isFailed = progress.error != null
    val isActive = !progress.isDone && !isFailed

    val accentColor by animateColorAsState(
        targetValue = when {
            isFailed -> TellyError
            isSuccess -> TellySuccess
            progress.isInstalling -> Color(0xFF64B5F6)
            else -> Color(0xFF00E5FF)
        },
        label = "accentColor"
    )

    Dialog(
        onDismissRequest = {
            if (progress.isDone || isFailed) onDismiss() else onMinimize()
        },
        properties = DialogProperties(
            dismissOnBackPress = progress.isDone || isFailed,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(26.dp),
            color = TellyDarkBackground,
            border = BorderStroke(1.5.dp, accentColor.copy(alpha = if (isActive) pulseAlpha else 0.8f)),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Row with Status Tag & Minimize Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accentColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = when {
                                isFailed -> "INSTALL FAILED"
                                isSuccess -> "INSTALL COMPLETED"
                                progress.isInstalling -> "INSTALLING ON TV"
                                else -> "STREAMING TO TV"
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = accentColor,
                            letterSpacing = 1.sp
                        )
                    }

                    if (isActive) {
                        IconButton(
                            onClick = onMinimize,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloseFullscreen,
                                contentDescription = "Minimize",
                                tint = TellyOnSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TellyOnSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // App Hero Icon with Status Indicator
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.25f),
                                    TellySurfaceContainerHigh
                                )
                            )
                        )
                        .border(2.dp, accentColor.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isFailed -> Icons.Default.ErrorOutline
                            isSuccess -> Icons.Default.CheckCircle
                            progress.isInstalling -> Icons.Default.SystemUpdate
                            else -> Icons.Default.CloudUpload
                        },
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                // App Title & Package Details
                Text(
                    text = progress.appName ?: progress.fileName.removeSuffix(".apk"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TellyOnSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!progress.packageName.isNullOrBlank()) {
                    Text(
                        text = progress.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = TellyOnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(18.dp))

                // Continuous Percentage Display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = if (isFailed) "Error" else "${progress.percentage}%",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor
                    )

                    if (!isFailed && !isSuccess && progress.speedKbps > 0) {
                        Text(
                            text = formatInstallSpeed(progress.speedKbps),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = TellyOnSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // CONTINUOUS PROGRESS BAR
                LinearProgressIndicator(
                    progress = { animatedProgressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = accentColor,
                    trackColor = TellySurfaceContainerHighest
                )

                Spacer(Modifier.height(10.dp))

                // Current Stage Description
                Text(
                    text = when {
                        isFailed -> progress.error ?: "Installation encountered an error"
                        isSuccess -> "Package installed and verified on Android TV!"
                        progress.stageText.isNotEmpty() -> progress.stageText
                        progress.isInstalling -> "Installing on Android TV OS..."
                        else -> "Streaming file to TV storage..."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFailed) TellyError else TellyOnSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                // Transferred Bytes Stats (while uploading)
                if (progress.totalBytes > 0 && !progress.isDone) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${formatFileSize(progress.bytesTransferred)} of ${formatFileSize(progress.totalBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TellyOnSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Bottom Action Buttons
                if (isSuccess) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!progress.packageName.isNullOrBlank() && onLaunchOnTv != null) {
                            Button(
                                onClick = {
                                    onLaunchOnTv(progress.packageName)
                                    Toast.makeText(context, "Launching on TV...", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TellySuccess)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Open on TV", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TellySurfaceContainerHigh)
                        ) {
                            Text("Done", color = TellyOnSurface, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else if (isFailed) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!progress.error.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(progress.error))
                                    Toast.makeText(context, "Error message copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, TellyOutlineSubtle)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy Error", fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TellySurfaceContainerHigh)
                        ) {
                            Text("Dismiss", color = TellyOnSurface)
                        }
                    }
                } else {
                    // While installing: Minimize button
                    OutlinedButton(
                        onClick = onMinimize,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, TellyOutlineSubtle)
                    ) {
                        Icon(Icons.Default.CloseFullscreen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Minimize (Run in Background)", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * Sleek, floating banner displayed at the bottom when an installation is active but minimized.
 */
@Composable
fun MinimizedInstallBanner(
    progress: TransferProgress,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onExpand),
        shape = RoundedCornerShape(16.dp),
        color = TellySurfaceContainerHigh,
        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f)),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                CircularProgressIndicator(
                    progress = { progress.progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier.size(24.dp),
                    color = if (progress.isDone) TellySuccess else Color(0xFF00E5FF),
                    trackColor = TellySurfaceContainerLowest,
                    strokeWidth = 3.dp
                )

                Spacer(Modifier.width(12.dp))

                Column {
                    Text(
                        text = if (progress.isDone) "✓ ${progress.appName ?: progress.fileName} Installed"
                        else "Installing ${progress.appName ?: progress.fileName} (${progress.percentage}%)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = TellyOnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = progress.stageText.ifEmpty { if (progress.isInstalling) "Writing to Android TV..." else "Streaming..." },
                        style = MaterialTheme.typography.labelSmall,
                        color = TellyOnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.OpenInFull,
                contentDescription = "Expand",
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024f
    val mb = kb / 1024f
    val gb = mb / 1024f
    return when {
        gb >= 1f -> String.format(java.util.Locale.US, "%.2f GB", gb)
        mb >= 1f -> String.format(java.util.Locale.US, "%.1f MB", mb)
        kb >= 1f -> String.format(java.util.Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}

private fun formatInstallSpeed(kbps: Long): String {
    return when {
        kbps >= 1024 -> String.format(java.util.Locale.US, "%.1f MB/s", kbps / 1024f)
        kbps > 0 -> "$kbps KB/s"
        else -> ""
    }
}
