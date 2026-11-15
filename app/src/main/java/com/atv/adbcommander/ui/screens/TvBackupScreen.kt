package com.atv.adbcommander.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.atv.adbcommander.adb.AdbManager
import com.atv.adbcommander.model.BackupProgress
import com.atv.adbcommander.model.TvBackupAppItem
import com.atv.adbcommander.model.TvBackupManifest
import com.atv.adbcommander.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvBackupScreen(
    adbManager: AdbManager,
    isConnected: Boolean,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var backups by remember { mutableStateOf<List<TvBackupManifest>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var backupToRestore by remember { mutableStateOf<TvBackupManifest?>(null) }
    var backupToDelete by remember { mutableStateOf<TvBackupManifest?>(null) }
    var backupToInspect by remember { mutableStateOf<TvBackupManifest?>(null) }
    val backupProgress by adbManager.backupProgress.collectAsState()

    fun refreshBackups() {
        backups = adbManager.getTvBackups()
    }

    LaunchedEffect(Unit) {
        refreshBackups()
    }

    LaunchedEffect(backupProgress.isRunning) {
        if (!backupProgress.isRunning) {
            refreshBackups()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        if (onBack != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Backup & Restore",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
            }
        }
        // Connection warning if disconnected
        if (!isConnected) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                color = AccentAmber.copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Not Connected",
                        tint = AccentAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Connect to TV to extract full APK backups or restore saved apps and settings.",
                        color = AccentAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Storage Location Banner
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            shape = RoundedCornerShape(14.dp),
            color = DarkSurfaceVariant,
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FolderSpecial,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "SAVED TO PHONE STORAGE:",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Downloads / ATVCommander_Backups /",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan
                    )
                }
            }
        }

        // Top Action Card: Create Backup
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            shape = RoundedCornerShape(18.dp),
            color = DarkCard,
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PrimaryBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Backup,
                                contentDescription = "Backup",
                                tint = CyberCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "TV SNAPSHOT & RESTORE",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Extract APKs & settings directly to phone",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Button(
                        onClick = { showCreateDialog = true },
                        enabled = isConnected && !backupProgress.isRunning,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Create", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Backup", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Pulls complete APK packages and system preferences from your TV directly into your phone's Downloads directory. Tap any backup below to inspect its apps, settings, or restore.",
                    fontSize = 11.sp,
                    color = TextMuted,
                    lineHeight = 16.sp
                )
            }
        }

        // Section Title: Saved Backups
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SAVED TV SNAPSHOTS (${backups.size})",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.2.sp
            )

            IconButton(
                onClick = { refreshBackups() },
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = CyberCyan,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // Backups List
        if (backups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderZip,
                            contentDescription = "No Backups",
                            tint = TextMuted,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No TV backups stored yet",
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap 'New Backup' above to back up your TV",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(backups, key = { it.id }) { item ->
                    BackupItemCard(
                        manifest = item,
                        isConnected = isConnected,
                        onInspect = { backupToInspect = item },
                        onRestore = { backupToRestore = item },
                        onDelete = { backupToDelete = item }
                    )
                }
            }
        }
    }

    // Backup Inspector Modal Dialog
    if (backupToInspect != null) {
        val manifest = backupToInspect!!
        BackupInspectorDialog(
            manifest = manifest,
            adbManager = adbManager,
            isConnected = isConnected,
            onDismiss = { backupToInspect = null },
            onRestoreFull = {
                backupToInspect = null
                backupToRestore = manifest
            }
        )
    }

    // Create Backup Dialog
    if (showCreateDialog) {
        var includeApks by remember { mutableStateOf(true) }
        var includeSettings by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = CyberCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "CREATE TV SNAPSHOT",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp,
                        color = CyberCyan
                    )
                }
            },
            text = {
                Column {
                    Text(
                        "Select data to pull from your TV and save into Downloads/ATVCommander_Backups/:",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = includeApks,
                            onCheckedChange = { includeApks = it },
                            colors = CheckboxDefaults.colors(checkedColor = CyberCyan)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Backup User Apps (APKs)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Pulls full .apk binaries from TV to phone", fontSize = 10.sp, color = TextMuted)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = includeSettings,
                            onCheckedChange = { includeSettings = it },
                            colors = CheckboxDefaults.colors(checkedColor = CyberCyan)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Backup TV System Preferences", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Global/secure/system settings & debloat list", fontSize = 10.sp, color = TextMuted)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCreateDialog = false
                        adbManager.startBackup(
                            includeUserApks = includeApks,
                            includeSettings = includeSettings,
                            onComplete = { res ->
                                res.onSuccess {
                                    refreshBackups()
                                }
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(10.dp),
                    enabled = includeApks || includeSettings
                ) {
                    Text("Start Backup", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary
        )
    }

    // Restore Backup Dialog
    if (backupToRestore != null) {
        val manifest = backupToRestore!!
        var restoreApks by remember { mutableStateOf(manifest.appCount > 0) }
        var restoreSettings by remember { mutableStateOf(manifest.hasSettings) }

        AlertDialog(
            onDismissRequest = { backupToRestore = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SettingsBackupRestore, contentDescription = null, tint = AccentGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "RESTORE TO TV",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp,
                        color = AccentGreen
                    )
                }
            },
            text = {
                Column {
                    Text(
                        "Restore snapshot from ${manifest.deviceName} to connected TV:",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    if (manifest.appCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = restoreApks,
                                onCheckedChange = { restoreApks = it },
                                colors = CheckboxDefaults.colors(checkedColor = AccentGreen)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Re-install ${manifest.appCount} Apps", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text("Pushes and installs all APKs automatically", fontSize = 10.sp, color = TextMuted)
                            }
                        }
                    }

                    if (manifest.hasSettings) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = restoreSettings,
                                onCheckedChange = { restoreSettings = it },
                                colors = CheckboxDefaults.colors(checkedColor = AccentGreen)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Restore System Settings & Debloat", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text("Reapplies preferences and frozen packages", fontSize = 10.sp, color = TextMuted)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        backupToRestore = null
                        adbManager.startRestore(
                            manifest = manifest,
                            restoreApps = restoreApks,
                            restoreSettings = restoreSettings
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    shape = RoundedCornerShape(10.dp),
                    enabled = restoreApks || restoreSettings
                ) {
                    Text("Start Restore", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { backupToRestore = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary
        )
    }

    // Delete Confirmation Dialog
    if (backupToDelete != null) {
        val manifest = backupToDelete!!
        AlertDialog(
            onDismissRequest = { backupToDelete = null },
            title = {
                Text(
                    "DELETE TV BACKUP?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp,
                    color = CyberRose
                )
            },
            text = {
                Text(
                    "Are you sure you want to delete this backup (${manifest.deviceName})? This will permanently delete the saved APKs and settings from your phone.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        adbManager.deleteTvBackup(manifest.id)
                        backupToDelete = null
                        refreshBackups()
                        Toast.makeText(context, "Backup deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberRose),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { backupToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    // Active Backup / Restore Progress Dialog
    if (backupProgress.isRunning) {
        Dialog(
            onDismissRequest = { /* Prevent dismiss during active operation */ },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                color = DarkSurface,
                border = BorderStroke(1.dp, BorderGlow),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = if (backupProgress.isRestore) AccentGreen else CyberCyan,
                        modifier = Modifier.size(46.dp),
                        strokeWidth = 3.dp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (backupProgress.isRestore) "RESTORING TO TV..." else "CREATING TV SNAPSHOT...",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = backupProgress.currentStep,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 2
                    )

                    if (backupProgress.totalItems > 0) {
                        Spacer(modifier = Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { (backupProgress.currentItem.toFloat() / backupProgress.totalItems.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (backupProgress.isRestore) AccentGreen else CyberCyan,
                            trackColor = DarkSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${backupProgress.currentItem} / ${backupProgress.totalItems} Items Processed",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BackupItemCard(
    manifest: TvBackupManifest,
    isConnected: Boolean,
    onInspect: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateStr = remember(manifest.timestamp) {
        SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(manifest.timestamp))
    }

    val sizeMb = remember(manifest.totalSizeBytes) {
        String.format("%.1f MB", manifest.totalSizeBytes.toDouble() / (1024.0 * 1024.0))
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onInspect() },
        shape = RoundedCornerShape(16.dp),
        color = DarkCard,
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = manifest.deviceName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        if (manifest.androidVersion.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Android ${manifest.androidVersion}",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CyberCyan.copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = dateStr,
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onInspect,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Inspect",
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = CyberRose,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Details Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Apps, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${manifest.appCount} Apps ($sizeMb)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }

                if (manifest.hasSettings) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Settings Included", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onInspect,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Icon(Icons.Default.List, contentDescription = "View", modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Inspect (${manifest.appCount})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onRestore,
                    enabled = isConnected,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Icon(Icons.Default.SettingsBackupRestore, contentDescription = "Restore", modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Restore to TV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun BackupInspectorDialog(
    manifest: TvBackupManifest,
    adbManager: AdbManager,
    isConnected: Boolean,
    onDismiss: () -> Unit,
    onRestoreFull: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Backed up Apps, 1 = Settings, 2 = Info
    var searchQuery by remember { mutableStateOf("") }
    var settingsContent by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var installingAppPackage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(manifest.id) {
        settingsContent = adbManager.getBackupSettingsContent(manifest.id)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            shape = RoundedCornerShape(20.dp),
            color = DarkSurface,
            border = BorderStroke(1.dp, BorderGlow),
            shadowElevation = 20.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkCard),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.FolderZip, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = manifest.deviceName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "BACKUP INSPECTOR • ${manifest.appCount} APKS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                // Sub-tabs (Apps / Settings / Storage)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        0 to "Apps (${manifest.apps.size})",
                        1 to "Settings",
                        2 to "Storage Location"
                    ).forEach { (idx, label) ->
                        val isSel = activeTab == idx
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSel) DarkSurfaceVariant else DarkCard,
                            border = BorderStroke(1.dp, if (isSel) CyberCyan.copy(alpha = 0.5f) else BorderSubtle),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { activeTab = idx }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 7.dp)) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) CyberCyan else TextSecondary
                                )
                            }
                        }
                    }
                }

                // CONTENT TABS
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp)
                ) {
                    // TAB 0: APPS LIST
                    if (activeTab == 0) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search apps in backup...", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyberCyan,
                                    unfocusedBorderColor = BorderSubtle,
                                    focusedContainerColor = DarkCard,
                                    unfocusedContainerColor = DarkCard
                                )
                            )

                            val filteredApps = manifest.apps.filter {
                                searchQuery.isEmpty() ||
                                        it.appName.contains(searchQuery, ignoreCase = true) ||
                                        it.packageName.contains(searchQuery, ignoreCase = true)
                            }

                            if (filteredApps.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No matching apps in this backup", color = TextSecondary, fontSize = 12.sp)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(filteredApps, key = { it.packageName }) { app ->
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            color = DarkCard,
                                            border = BorderStroke(1.dp, BorderSubtle)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(32.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(DarkSurfaceVariant),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Default.Android, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                                                    }
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column {
                                                        Text(
                                                            text = app.appName,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = TextPrimary
                                                        )
                                                        Text(
                                                            text = "${app.packageName} • v${app.versionName}",
                                                            fontSize = 10.sp,
                                                            color = TextSecondary,
                                                            maxLines = 1
                                                        )
                                                        val apkMb = String.format("%.1f MB", app.apkSizeBytes.toDouble() / (1024.0 * 1024.0))
                                                        Text(
                                                            text = "APK Size: $apkMb • File: ${app.apkFileName}",
                                                            fontSize = 9.sp,
                                                            color = TextMuted
                                                        )
                                                    }
                                                }

                                                // 1-Tap install this single app to TV
                                                val isInstallingThis = installingAppPackage == app.packageName
                                                Button(
                                                    onClick = {
                                                        installingAppPackage = app.packageName
                                                        coroutineScope.launch {
                                                            val res = adbManager.installSingleApkFromBackup(manifest.id, app.apkFileName)
                                                            Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                                                            installingAppPackage = null
                                                        }
                                                    },
                                                    enabled = isConnected && installingAppPackage == null,
                                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(30.dp)
                                                ) {
                                                    if (isInstallingThis) {
                                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = Color.White)
                                                    } else {
                                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(13.dp))
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text("Install", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // TAB 1: SETTINGS CONTENT
                    if (activeTab == 1) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = DarkCard,
                                border = BorderStroke(1.dp, BorderSubtle)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("DISPLAY SETTINGS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = settingsContent["display"]?.ifEmpty { "None recorded" } ?: "Loading display preferences...",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextPrimary
                                    )
                                }
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = DarkCard,
                                border = BorderStroke(1.dp, BorderSubtle)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("FROZEN / DEBLOATED PACKAGES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentAmber, letterSpacing = 1.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = settingsContent["disabled"]?.ifEmpty { "No disabled packages recorded" } ?: "Loading disabled packages...",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextSecondary
                                    )
                                }
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = DarkCard,
                                border = BorderStroke(1.dp, BorderSubtle)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("SYSTEM GLOBAL SETTINGS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentGreen, letterSpacing = 1.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = (settingsContent["global"]?.lines()?.take(30)?.joinToString("\n") ?: "None") + "\n...",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }

                    // TAB 2: STORAGE LOCATION & EXPORT
                    if (activeTab == 2) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = DarkCard,
                                border = BorderStroke(1.dp, BorderSubtle)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("FILE SYSTEM PATH", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = adbManager.getBackupFolderLocation(manifest.id),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "All APKs and config files are directly inside this directory. You can open any phone File Manager app and copy them to PC or USB drive.",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    val msg = adbManager.copyBackupToDownloads(manifest.id)
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy to Phone Downloads Folder", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Bottom Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = TextSecondary)
                    }

                    Button(
                        onClick = onRestoreFull,
                        enabled = isConnected,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.SettingsBackupRestore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore Full Backup", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
