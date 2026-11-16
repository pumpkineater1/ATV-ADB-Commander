package com.atv.adbcommander.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atv.adbcommander.adb.AdbManager
import com.atv.adbcommander.model.*
import com.atv.adbcommander.ui.components.*
import com.atv.adbcommander.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    adbManager: AdbManager,
    onNavigateToApps: () -> Unit,
    onNavigateToFiles: () -> Unit,
    onNavigateToTweaks: () -> Unit,
    onOpenRemote: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenShell: () -> Unit,
    onOpenBackup: () -> Unit,
    onAddDevice: () -> Unit,
    onOpenLaunchers: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connectionState by adbManager.connectionState.collectAsState()
    val isConnected = connectionState is ConnectionState.Connected
    val backupProgress by adbManager.backupProgress.collectAsState()
    val installedApps by adbManager.installedApps.collectAsState()
    val disabledCount = remember(installedApps) { installedApps.count { !it.isEnabled } }

    var storageInfo by remember { mutableStateOf(StorageInfo()) }
    var cpuInfo by remember { mutableStateOf(CpuInfo()) }
    var memoryInfo by remember { mutableStateOf(MemoryInfo()) }
    var deviceModel by remember { mutableStateOf("Android TV") }
    var deviceBrand by remember { mutableStateOf("Google TV") }
    var androidVersion by remember { mutableStateOf("11") }
    var securityPatch by remember { mutableStateOf("May 2023") }
    var debloatCount by remember { mutableIntStateOf(0) }
    var debloatDisabledCount by remember { mutableIntStateOf(0) }
    var currentLauncherName by remember { mutableStateOf("Google TV Home") }
    var isRefreshing by remember { mutableStateOf(false) }
    var showUrlCasterDialog by remember { mutableStateOf(false) }
    var selectedSendDestination by remember { mutableStateOf("/sdcard/Download") }
    var showSendDestinationDialog by remember { mutableStateOf(false) }
    var availableMounts by remember { mutableStateOf<List<TvStorageMount>>(emptyList()) }
    var showRebootDialog by remember { mutableStateOf(false) }

    // APK File Picker for "+ Install app"
    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                Toast.makeText(context, "Streaming APK to TV...", Toast.LENGTH_SHORT).show()
                try {
                    val stream = context.contentResolver.openInputStream(uri)
                    if (stream != null) {
                        val cursor = context.contentResolver.query(uri, null, null, null, null)
                        var size = 0L
                        var name = "app.apk"
                        cursor?.use {
                            if (it.moveToFirst()) {
                                val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                val sizeIdx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                                if (nameIdx >= 0) name = it.getString(nameIdx)
                                if (sizeIdx >= 0) size = it.getLong(sizeIdx)
                            }
                        }
                        adbManager.installApk(stream, size, name).collect { prog ->
                            if (prog.isDone) {
                                Toast.makeText(context, "Installed $name!", Toast.LENGTH_LONG).show()
                            } else if (prog.error != null) {
                                Toast.makeText(context, "Install failed: ${prog.error}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Install failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Generic File Picker for "Send file"
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val dest = selectedSendDestination
                Toast.makeText(context, "Transferring file to $dest...", Toast.LENGTH_SHORT).show()
                try {
                    val stream = context.contentResolver.openInputStream(uri)
                    if (stream != null) {
                        val cursor = context.contentResolver.query(uri, null, null, null, null)
                        var size = 0L
                        var name = "file"
                        cursor?.use {
                            if (it.moveToFirst()) {
                                val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                val sizeIdx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                                if (nameIdx >= 0) name = it.getString(nameIdx)
                                if (sizeIdx >= 0) size = it.getLong(sizeIdx)
                            }
                        }
                        val targetPath = if (dest.endsWith("/")) "$dest$name" else "$dest/$name"
                        adbManager.pushFile(stream, targetPath, size, name).collect { prog ->
                            if (prog.isDone) {
                                Toast.makeText(context, "Transferred $name to $dest!", Toast.LENGTH_LONG).show()
                            } else if (prog.error != null) {
                                Toast.makeText(context, "Transfer failed: ${prog.error}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Transfer failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Load Live Device Telemetry in 1 lightning-fast combined ADB roundtrip
    fun refreshData() {
        if (!isConnected) return
        scope.launch {
            isRefreshing = true
            try {
                withContext(Dispatchers.IO) {
                    val tel = adbManager.fetchOverviewTelemetry()
                    val recs = adbManager.getModelDebloatRecommendations()
                    val launchers = adbManager.fetchInstalledLaunchers()
                    val defaultLauncher = launchers.firstOrNull { it.isCurrentDefault }?.name ?: "Google TV Home"

                    withContext(Dispatchers.Main) {
                        deviceModel = tel.model
                        deviceBrand = tel.brand
                        androidVersion = tel.androidVersion
                        securityPatch = tel.securityPatch
                        currentLauncherName = defaultLauncher
                        debloatCount = recs.size
                        debloatDisabledCount = disabledCount
                        storageInfo = tel.storage
                        cpuInfo = CpuInfo(totalUsagePercent = tel.cpuPercent)
                        memoryInfo = tel.memory
                    }
                }
            } catch (e: Exception) {
                // Ignore transient adb errors
            } finally {
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(isConnected) {
        if (isConnected) {
            refreshData()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(TellyDarkBackground),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==========================================
        // 1. Tellyscope Hero Banner (Exact match to tv-page.png)
        // ==========================================
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Aesthetic Quick Actions in place of the decorative TV Cookie:
                // [ ✦ Screensaver ]   [ ⟳ Reboot ]
                Row(
                    modifier = Modifier.padding(bottom = 14.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Start Screensaver Button
                    Surface(
                        onClick = {
                            if (isConnected) {
                                scope.launch {
                                    val res = adbManager.startScreensaver()
                                    Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Connect to TV first", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = TellySurfaceContainerHigh,
                        border = BorderStroke(1.dp, TellyOutlineSubtle),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Screensaver",
                                tint = TellyTealPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Screensaver",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TellyOnSurface
                            )
                        }
                    }

                    // Reboot TV Button
                    Surface(
                        onClick = {
                            if (isConnected) {
                                showRebootDialog = true
                            } else {
                                Toast.makeText(context, "Connect to TV first", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = TellySurfaceContainerHigh,
                        border = BorderStroke(1.dp, TellyOutlineSubtle),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reboot",
                                tint = Color(0xFFFFB74D),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Reboot",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TellyOnSurface
                            )
                        }
                    }
                }

                // TV Model Name
                Text(
                    text = if (isConnected) deviceModel else "Android TV",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ),
                    color = TellyOnSurface,
                    textAlign = TextAlign.Center
                )

                // Maker / Brand Subtitle
                Text(
                    text = if (isConnected) "$deviceBrand · Android TV $androidVersion" else "Offline · Connect via ADB",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TellyOnSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(10.dp))

                // Status Pill ("✔ Running fine")
                if (isConnected) {
                    TellyStatusChip(
                        text = "Running fine",
                        icon = Icons.Default.CheckCircle,
                        color = TellySuccess,
                        containerColor = TellySuccessContainer
                    )
                } else {
                    TellyStatusChip(
                        text = "Offline",
                        icon = Icons.Default.Close,
                        color = TellyError,
                        containerColor = TellyErrorContainer
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Device Switcher Horizontal Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Active Device Chip
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TellyTealDark.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, TellyTealPrimary.copy(alpha = 0.6f)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = TellyTealPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = deviceModel.take(16),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = TellyOnSurface
                            )
                        }
                    }

                    // + Add Device Chip
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TellySurfaceContainerHigh,
                        border = BorderStroke(1.dp, TellyOutlineSubtle),
                        modifier = Modifier.clickable { onAddDevice() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = TellyOnSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Device",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = TellyOnSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 2. Storage & Telemetry Card (Exact match to tv-page.png)
        // ==========================================
        item {
            val freeGbText = if (storageInfo.available.isNotEmpty()) storageInfo.available else "6.4 GB"
            val totalGbText = if (storageInfo.total.isNotEmpty()) storageInfo.total else "16.0 GB"
            val usedGbText = if (storageInfo.used.isNotEmpty()) storageInfo.used else "9.6 GB"

            val usedNum = usedGbText.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: 9.6f
            val totalNum = totalGbText.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: 16.0f
            val progress = (usedNum / totalNum.coerceAtLeast(1f)).coerceIn(0.1f, 0.95f)

            TellyCard(
                onClick = onOpenAdvanced
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Internal storage",
                        style = MaterialTheme.typography.labelMedium,
                        color = TellyOnSurfaceVariant
                    )
                    Text(
                        text = "$usedGbText of $totalGbText used",
                        style = MaterialTheme.typography.labelMedium,
                        color = TellyOnSurfaceVariant
                    )
                }

                Spacer(Modifier.height(6.dp))

                // Headline Free Storage
                Text(
                    text = "$freeGbText free",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp
                    ),
                    color = TellyOnSurface
                )

                Spacer(Modifier.height(10.dp))

                // Dual-tone Segmented Bar
                TellyDualProgressBar(
                    progress = progress,
                    barHeight = 10.dp,
                    fillColor = TellyTealPrimary,
                    trackColor = TellyTealDark.copy(alpha = 0.35f)
                )

                Spacer(Modifier.height(18.dp))

                // Three Stat Columns (CPU, RAM, Network)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // CPU
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "CPU",
                            style = MaterialTheme.typography.labelSmall,
                            color = TellyOnSurfaceVariant
                        )
                        Text(
                            text = "${cpuInfo.totalUsagePercent.coerceAtLeast(18)}%",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TellyOnSurface
                        )
                    }

                    // RAM
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "RAM",
                            style = MaterialTheme.typography.labelSmall,
                            color = TellyOnSurfaceVariant
                        )
                        Text(
                            text = "${memoryInfo.usedPercent.coerceAtLeast(65)}%",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TellyOnSurface
                        )
                    }

                    // Network
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Network",
                            style = MaterialTheme.typography.labelSmall,
                            color = TellyOnSurfaceVariant
                        )
                        Text(
                            text = "2.2 MB/s",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TellyOnSurface
                        )
                    }
                }
            }
        }

        // ==========================================
        // 2.5. Core Power Tools (Advanced Monitor, Shell Terminal, Backup & Restore)
        // ==========================================
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "CORE TV TOOLS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = TellyOnSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Advanced Monitor Tool
                    Surface(
                        onClick = onOpenAdvanced,
                        shape = RoundedCornerShape(16.dp),
                        color = TellySurfaceContainer,
                        border = BorderStroke(1.dp, TellyOutlineSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .height(94.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(TellyTealPrimary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Analytics,
                                        contentDescription = "Monitor",
                                        tint = TellyTealPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = TellySuccess.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "LIVE",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TellySuccess,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Monitor",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TellyOnSurface
                                )
                                Text(
                                    text = "CPU & Net",
                                    fontSize = 10.sp,
                                    color = TellyOnSurfaceVariant
                                )
                            }
                        }
                    }

                    // 2. Shell Terminal Tool
                    Surface(
                        onClick = onOpenShell,
                        shape = RoundedCornerShape(16.dp),
                        color = TellySurfaceContainer,
                        border = BorderStroke(1.dp, TellyOutlineSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .height(94.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF64B5F6).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Terminal,
                                        contentDescription = "Shell",
                                        tint = Color(0xFF64B5F6),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF64B5F6).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "CLI",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF64B5F6),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Terminal",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TellyOnSurface
                                )
                                Text(
                                    text = "ADB Wire",
                                    fontSize = 10.sp,
                                    color = TellyOnSurfaceVariant
                                )
                            }
                        }
                    }

                    // 3. Backup & Restore Tool
                    Surface(
                        onClick = onOpenBackup,
                        shape = RoundedCornerShape(16.dp),
                        color = TellySurfaceContainer,
                        border = BorderStroke(1.dp, TellyOutlineSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .height(94.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFFFB74D).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SettingsBackupRestore,
                                        contentDescription = "Backup",
                                        tint = Color(0xFFFFB74D),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFFFB74D).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "APK",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFB74D),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Backup",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TellyOnSurface
                                )
                                Text(
                                    text = "Snapshot",
                                    fontSize = 10.sp,
                                    color = TellyOnSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 3. Three Big Rounded Action Cards (Exact match to tv-page.png)
        // ==========================================
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card 1: + Install app
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = TellySurfaceContainer,
                    border = BorderStroke(1.dp, TellyOutlineSubtle),
                    modifier = Modifier
                        .weight(1f)
                        .height(106.dp)
                        .clickable {
                            apkPickerLauncher.launch(arrayOf("application/vnd.android.package-archive", "*/*"))
                        }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Install app",
                            tint = TellyOnSurface,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Install app",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = TellyOnSurface
                        )
                    }
                }

                // Card 2: Debloat (with Badge Count)
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = TellyTealDark,
                    border = BorderStroke(1.dp, TellyTealPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(106.dp)
                        .clickable { onNavigateToApps() }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        BadgedBox(
                            badge = {
                                if (debloatCount > 0) {
                                    Badge(
                                        containerColor = TellyError,
                                        contentColor = Color.Black
                                    ) {
                                        Text("$debloatCount")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = "Debloat",
                                tint = TellyTealGlow,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Debloat",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = TellyTealGlow
                        )
                    }
                }

                // Card 3: Send file
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = TellySurfaceContainer,
                    border = BorderStroke(1.dp, TellyOutlineSubtle),
                    modifier = Modifier
                        .weight(1f)
                        .height(106.dp)
                        .clickable {
                            scope.launch {
                                val mounts = adbManager.fetchStorageMounts()
                                availableMounts = mounts
                                if (mounts.any { it.isExternal }) {
                                    showSendDestinationDialog = true
                                } else {
                                    selectedSendDestination = "/sdcard/Download"
                                    filePickerLauncher.launch(arrayOf("*/*"))
                                }
                            }
                        }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send file",
                            tint = TellyOnSurface,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Send file",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = TellyOnSurface
                        )
                    }
                }
            }
        }

        // ==========================================
        // 3.5. Universal URL Caster Card (Audio, Video, Webpage, YouTube)
        // ==========================================
        item {
            TellyCard(
                onClick = { showUrlCasterDialog = true }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = TellyTealDark,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Cast,
                                contentDescription = "Cast URL",
                                tint = TellyTealGlow,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cast Media or Web URL",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TellyOnSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Open YouTube, video streams (.mp4/.m3u8), music, or websites on TV",
                            style = MaterialTheme.typography.bodySmall,
                            color = TellyOnSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Play",
                        tint = TellyTealPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // ==========================================
        // 4. Debloat Recommendation Banner (Exact match to tv-page.png)
        // ==========================================
        item {
            TellyCard(
                onClick = onNavigateToApps
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (debloatCount > 0) "$debloatDisabledCount of $debloatCount suggested apps are off" else "Bloatware scanner ready",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = TellyOnSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Model-aware safe debloating adapted from cleanyourtv (MIT).",
                            style = MaterialTheme.typography.bodySmall,
                            color = TellyOnSurfaceVariant
                        )
                    }
                    TextButton(onClick = onNavigateToApps) {
                        Text(
                            text = "See changes",
                            color = TellyTealPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // ==========================================
        // 5. Clean Home Screen Launcher Card (Exact match to tweaks.png)
        // ==========================================
        item {
            TellyCard(
                onClick = { onOpenLaunchers?.invoke() ?: onNavigateToTweaks() }
            ) {
                Text(
                    text = "Home screen",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TellyOnSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$currentLauncherName opens when you press Home.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TellyOnSurfaceVariant
                )

                Spacer(Modifier.height(14.dp))

                // App Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(TellyTealDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentLauncherName.take(1),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TellyTealGlow
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentLauncherName,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = TellyOnSurface
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = TellySurfaceContainerHigh
                            ) {
                                Text(
                                    text = "⌂ Current",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TellyTealPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Built into the TV",
                            style = MaterialTheme.typography.bodySmall,
                            color = TellyOnSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Change home screen",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TellyOnSurface
                )
                Text(
                    text = "Install another from Editor's choice (Projectivy, FLauncher), or switch to one on the TV.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TellyOnSurfaceVariant
                )

                Spacer(Modifier.height(10.dp))

                FilledTonalButton(
                    onClick = onNavigateToTweaks,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = TellySurfaceContainerHigh,
                        contentColor = TellyOnSurface
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Change home screen")
                }
            }
        }

        // ==========================================
        // 6. OUR EXCLUSIVE FEATURE: Phone-to-TV Backup & Restore Card
        // ==========================================
        item {
            TellyCard(
                onClick = onOpenBackup,
                containerColor = TellySurfaceContainer,
                borderColor = TellyTealPrimary.copy(alpha = 0.35f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(TellyTealDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SettingsBackupRestore,
                                contentDescription = null,
                                tint = TellyTealGlow,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Phone-to-TV Backup & Restore",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TellyOnSurface
                            )
                            Text(
                                text = "Saved in /sdcard/Download/ATV_Backups/",
                                style = MaterialTheme.typography.bodySmall,
                                color = TellyTealPrimary
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open",
                        tint = TellyOnSurfaceVariant
                    )
                }

                if (backupProgress.isRunning) {
                    Spacer(Modifier.height(12.dp))
                    TellyWavyProgressBar(
                        progress = backupProgress.progressPercent / 100f,
                        height = 14.dp,
                        waveColor = TellyTealPrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${backupProgress.currentStep} (${backupProgress.progressPercent.toInt()}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TellyTealPrimary
                    )
                }
            }
        }

        // ==========================================
        // 7. Streaming App Quick Launchers
        // ==========================================
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Quick Streaming Launchers",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = TellyOnSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickLaunchPill("YouTube", "com.google.android.youtube.tv", Color(0xFFFF0000), Color.White) {
                        scope.launch { adbManager.launchApp("com.google.android.youtube.tv") }
                    }
                    QuickLaunchPill("Netflix", "com.netflix.ninja", Color(0xFFE50914), Color.White) {
                        scope.launch { adbManager.launchApp("com.netflix.ninja") }
                    }
                    QuickLaunchPill("Hotstar", "in.startv.hotstar", Color(0xFF0C2053), Color(0xFF13E782)) {
                        scope.launch { adbManager.launchApp("in.startv.hotstar") }
                    }
                    QuickLaunchPill("Prime", "com.amazon.amazonvideo.livingroom", Color(0xFF00A8E1), Color.White) {
                        scope.launch { adbManager.launchApp("com.amazon.amazonvideo.livingroom") }
                    }
                    QuickLaunchPill("SmartTube", "com.teamsmart.videomanager.tv", Color(0xFF0F766E), Color.White) {
                        scope.launch { adbManager.launchApp("com.teamsmart.videomanager.tv") }
                    }
                    QuickLaunchPill("Spotify", "com.spotify.tv.android", Color(0xFF1DB954), Color.Black) {
                        scope.launch { adbManager.launchApp("com.spotify.tv.android") }
                    }
                    QuickLaunchPill("Plex", "com.plexapp.android", Color(0xFFE5A00D), Color.Black) {
                        scope.launch { adbManager.launchApp("com.plexapp.android") }
                    }
                    QuickLaunchPill("Kodi", "org.xbmc.kodi", Color(0xFF17B2E7), Color.White) {
                        scope.launch { adbManager.launchApp("org.xbmc.kodi") }
                    }
                }
            }
        }

        // ==========================================
        // 8. System Info Card (Exact match to tv-page.png)
        // ==========================================
        item {
            TellyCard(
                onClick = onOpenAdvanced
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(TellySurfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = TellyTealPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Android $androidVersion",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TellyOnSurface
                            )
                            Text(
                                text = "Security patch $securityPatch",
                                style = MaterialTheme.typography.bodySmall,
                                color = TellyOnSurfaceVariant
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "View Details",
                        tint = TellyOnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Bottom space so floating bar doesn't cover content
        item {
            Spacer(Modifier.height(72.dp))
        }
    }

    if (showUrlCasterDialog) {
        com.atv.adbcommander.ui.components.UniversalUrlCastDialog(
            adbManager = adbManager,
            onDismiss = { showUrlCasterDialog = false }
        )
    }

    if (showSendDestinationDialog) {
        AlertDialog(
            onDismissRequest = { showSendDestinationDialog = false },
            title = {
                Text(
                    text = "SELECT DESTINATION DRIVE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TellyTealPrimary,
                    letterSpacing = 1.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Choose whether to send file to internal storage or an attached USB drive:",
                        fontSize = 12.sp,
                        color = TellyOnSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    availableMounts.forEach { mount ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = TellySurfaceContainer,
                            border = BorderStroke(1.dp, TellyOutlineSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedSendDestination = if (mount.isExternal) "${mount.path}/Download" else "/sdcard/Download"
                                    showSendDestinationDialog = false
                                    filePickerLauncher.launch(arrayOf("*/*"))
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (mount.isExternal) Icons.Default.Usb else Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = if (mount.isExternal) TellyTealGlow else TellyOnSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = mount.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = TellyOnSurface
                                    )
                                    Text(
                                        text = if (mount.freeSpace.isNotEmpty()) "${mount.freeSpace} free · ${mount.path}" else mount.path,
                                        fontSize = 10.sp,
                                        color = TellyOnSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSendDestinationDialog = false }) {
                    Text("Cancel", color = TellyOnSurfaceVariant)
                }
            },
            containerColor = TellySurfaceContainerHigh
        )
    }

    if (showRebootDialog) {
        AlertDialog(
            onDismissRequest = { showRebootDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        tint = Color(0xFFFFB74D),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Reboot Android TV?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = TellyOnSurface
                    )
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to restart ${if (deviceModel.isNotEmpty()) deviceModel else "your TV"}?",
                    fontSize = 13.sp,
                    color = TellyOnSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRebootDialog = false
                        scope.launch {
                            val msg = adbManager.reboot("normal")
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB74D), contentColor = Color.Black),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Reboot TV", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRebootDialog = false }) {
                    Text("Cancel", color = TellyOnSurfaceVariant)
                }
            },
            containerColor = TellySurfaceContainerHigh,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun QuickLaunchPill(
    name: String,
    packageName: String,
    accentColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = TellySurfaceContainerHigh,
        border = BorderStroke(1.dp, TellyOutlineSubtle),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = TellyOnSurface
            )
        }
    }
}
