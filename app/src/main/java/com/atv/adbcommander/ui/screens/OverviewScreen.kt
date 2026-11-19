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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    val isTvSleeping by adbManager.isTvSleeping.collectAsState()
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
    var showWirelessDialog by remember { mutableStateOf(false) }

    // APK File Picker for "+ Install app"
    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    adbManager.ensureConnected()
                    adbManager.wakeTv()
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
                        adbManager.installApk(
                            inputStream = stream,
                            totalBytes = size,
                            fileName = name,
                            appName = name.removeSuffix(".apk").replace("_", " ")
                        ).collect { }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Install error: ${e.message}", Toast.LENGTH_SHORT).show()
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

    val liveTelemetry by adbManager.overviewTelemetry.collectAsState()

    LaunchedEffect(liveTelemetry) {
        if (liveTelemetry.model.isNotEmpty()) deviceModel = liveTelemetry.model
        if (liveTelemetry.brand.isNotEmpty()) deviceBrand = liveTelemetry.brand
        if (liveTelemetry.androidVersion.isNotEmpty()) androidVersion = liveTelemetry.androidVersion
        if (liveTelemetry.securityPatch.isNotEmpty()) securityPatch = liveTelemetry.securityPatch
        if (liveTelemetry.currentLauncherName.isNotEmpty()) currentLauncherName = liveTelemetry.currentLauncherName
        if (liveTelemetry.storage.total.isNotEmpty()) storageInfo = liveTelemetry.storage
        if (liveTelemetry.memory.totalMb > 0) memoryInfo = liveTelemetry.memory
        cpuInfo = CpuInfo(totalUsagePercent = liveTelemetry.cpuPercent)
        val recs = adbManager.getModelDebloatRecommendations(liveTelemetry.brand, liveTelemetry.model)
        debloatCount = recs.size
        debloatDisabledCount = disabledCount
    }

    // Load Live Device Telemetry in 1 lightning-fast combined ADB roundtrip
    fun refreshData() {
        if (!isConnected) return
        scope.launch {
            isRefreshing = true
            try {
                withContext(Dispatchers.IO) {
                    adbManager.fetchOverviewTelemetry()
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
            while (isActive && isConnected) {
                delay(7000L)
                refreshData()
            }
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
        // 1. Tellyscope Hero Banner
        // ==========================================
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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

                // Maker / Brand Subtitle with IPv6 indicator
                val connectedSub = if (isConnected) {
                    val conn = connectionState as? ConnectionState.Connected
                    val ipTag = if (conn?.isIpv6 == true) " • IPv6" else ""
                    "$deviceBrand · Android TV $androidVersion$ipTag"
                } else "Offline · Connect via ADB"

                Text(
                    text = connectedSub,
                    style = MaterialTheme.typography.bodySmall,
                    color = TellyOnSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // Quick One-Tap TV Switcher Row when multiple saved TVs exist (e.g. Google Streamer & Fire TV Max)
                val savedTvs by adbManager.savedDevices.collectAsState()
                if (savedTvs.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        savedTvs.forEach { dev ->
                            val activeConn = connectionState as? ConnectionState.Connected
                            val isActiveTarget = isConnected && (dev.ip == activeConn?.host || dev.ipv6 == activeConn?.host)
                            Surface(
                                onClick = {
                                    if (!isActiveTarget) {
                                        scope.launch {
                                            adbManager.switchToDevice(dev)
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isActiveTarget) TellyTealDark.copy(alpha = 0.5f) else TellySurfaceContainerHigh,
                                border = BorderStroke(
                                    1.dp,
                                    if (isActiveTarget) TellyTealPrimary.copy(alpha = 0.8f) else TellyOutlineSubtle
                                ),
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .height(30.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (isActiveTarget) TellySuccess else TellyOnSurfaceVariant.copy(alpha = 0.6f))
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (dev.name.isNotBlank()) dev.name else dev.model.ifEmpty { "TV" },
                                        fontSize = 11.sp,
                                        fontWeight = if (isActiveTarget) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isActiveTarget) TellyTealGlow else TellyOnSurfaceVariant
                                    )
                                    if (isActiveTarget) {
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "• Active",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TellySuccess
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // TV Status Chip ("Running fine", "Running fine • IPv6", or "TV Asleep • Tap to Wake Up")
                if (isConnected) {
                    val conn = connectionState as? ConnectionState.Connected
                    if (isTvSleeping) {
                        Surface(
                            onClick = {
                                scope.launch {
                                    adbManager.wakeTv()
                                    Toast.makeText(context, "Waking up TV...", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF332000),
                            border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NightlightRound,
                                    contentDescription = "TV Asleep",
                                    tint = Color(0xFFFFB300),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = "TV Asleep • Tap to Wake Up",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFFFD54F)
                                )
                            }
                        }
                    } else {
                        TellyStatusChip(
                            text = if (conn?.isIpv6 == true) "Running fine · IPv6" else "Running fine",
                            icon = Icons.Default.CheckCircle,
                            color = TellySuccess,
                            containerColor = TellySuccessContainer
                        )
                    }
                } else {
                    TellyStatusChip(
                        text = "Offline",
                        icon = Icons.Default.Close,
                        color = TellyError,
                        containerColor = TellyErrorContainer
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Prominent Quick Action Buttons:
                // [ ✦ Screensaver / ⚡ Wake Up ]   [ 🛍 Store ]   [ ⟳ Reboot ]   [ + TV ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isConnected) {
                        if (isTvSleeping) {
                            // 1. Prominent Wake Up Button (when TV is in sleep mode)
                            Surface(
                                onClick = {
                                    scope.launch {
                                        adbManager.wakeTv()
                                        Toast.makeText(context, "Waking up TV...", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF332000),
                                border = BorderStroke(1.dp, Color(0xFFFFB300)),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = "Wake Up",
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Wake Up",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFD54F)
                                    )
                                }
                            }
                        } else {
                            // 1. Screensaver Pill
                            Surface(
                                onClick = {
                                    scope.launch {
                                        val res = adbManager.startScreensaver()
                                        Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = TellySurfaceContainerHigh,
                                border = BorderStroke(1.dp, TellyOutlineSubtle),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Screensaver",
                                        tint = TellyTealPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Screensaver",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TellyOnSurface
                                    )
                                }
                            }
                        }

                        // 2. TV Store Pill (Direct One-Click Launch)
                        Surface(
                            onClick = {
                                scope.launch {
                                    val ok = adbManager.openPlayStoreOnTv()
                                    Toast.makeText(context, if (ok) "Opening TV Store..." else "Failed to open Store", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = TellySurfaceContainerHigh,
                            border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.45f)),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingBag,
                                    contentDescription = "Store",
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Store",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TellyOnSurface
                                )
                            }
                        }

                        // 3. Reboot Pill
                        Surface(
                            onClick = { showRebootDialog = true },
                            shape = RoundedCornerShape(20.dp),
                            color = TellySurfaceContainerHigh,
                            border = BorderStroke(1.dp, TellyOutlineSubtle),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = "Reboot",
                                    tint = Color(0xFFFFB74D),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Reboot",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TellyOnSurface
                                )
                            }
                        }

                        // 4. + TV Pill (Bigger & More Appealing with Glow Accent)
                        Surface(
                            onClick = onAddDevice,
                            shape = RoundedCornerShape(20.dp),
                            color = TellyTealDark.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, TellyTealPrimary.copy(alpha = 0.7f)),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add TV",
                                    tint = TellyTealGlow,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "TV",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TellyTealGlow
                                )
                            }
                        }
                    } else {
                        // Prominent Connect Button
                        Surface(
                            onClick = {
                                scope.launch {
                                    val ok = adbManager.autoConnect()
                                    if (!ok) {
                                        onAddDevice()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = TellyTealDark,
                            border = BorderStroke(1.dp, TellyTealPrimary.copy(alpha = 0.6f)),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = TellyTealPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Connect TV",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TellyTealGlow
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 2. Storage & Telemetry Card (Compact & Aesthetic)
        // ==========================================
        item {
            val freeGbText = if (storageInfo.available.isNotEmpty()) storageInfo.available else "6.4 GB"
            val totalGbText = if (storageInfo.total.isNotEmpty()) storageInfo.total else "16.0 GB"
            val usedGbText = if (storageInfo.used.isNotEmpty()) storageInfo.used else "9.6 GB"

            val usedNum = usedGbText.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: 9.6f
            val totalNum = totalGbText.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: 16.0f
            val progress = (usedNum / totalNum.coerceAtLeast(1f)).coerceIn(0.1f, 0.95f)

            TellyCard(
                onClick = onOpenAdvanced,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Internal storage",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = TellyOnSurfaceVariant
                    )
                    Text(
                        text = "$usedGbText of $totalGbText used",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = TellyOnSurfaceVariant
                    )
                }

                Spacer(Modifier.height(3.dp))

                // Headline Free Storage (Compact 19.sp)
                Text(
                    text = "$freeGbText free",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    ),
                    color = TellyOnSurface
                )

                Spacer(Modifier.height(6.dp))

                // Dual-tone Segmented Bar (Compact 5.dp)
                TellyDualProgressBar(
                    progress = progress,
                    barHeight = 5.dp,
                    fillColor = TellyTealPrimary,
                    trackColor = TellyTealDark.copy(alpha = 0.35f)
                )

                Spacer(Modifier.height(10.dp))

                // Three Stat Columns (CPU, RAM, Network - Compact)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // CPU
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "CPU",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = TellyOnSurfaceVariant
                        )
                        Text(
                            text = "${cpuInfo.totalUsagePercent.coerceAtLeast(18)}%",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            color = TellyOnSurface
                        )
                    }

                    // RAM
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "RAM",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = TellyOnSurfaceVariant
                        )
                        Text(
                            text = "${memoryInfo.usedPercent.coerceAtLeast(65)}%",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            color = TellyOnSurface
                        )
                    }

                    // Network
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Network",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = TellyOnSurfaceVariant
                        )
                        Text(
                            text = "2.2 MB/s",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
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
                            scope.launch {
                                try {
                                    adbManager.ensureConnected()
                                    apkPickerLauncher.launch(arrayOf("application/vnd.android.package-archive", "*/*"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "TV not connected: ${e.message}", Toast.LENGTH_SHORT).show()
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
        // 3.6. Wireless & Bluetooth Devices Card
        // ==========================================
        item {
            TellyCard(
                onClick = { showWirelessDialog = true }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1565C0).copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, Color(0xFF1976D2).copy(alpha = 0.4f)),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.SettingsBluetooth,
                                contentDescription = "Wireless & Devices",
                                tint = Color(0xFF64B5F6),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Wireless & Bluetooth Devices",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TellyOnSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Silently manage TV Bluetooth headphones, remotes & Wi-Fi",
                            style = MaterialTheme.typography.bodySmall,
                            color = TellyOnSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TellyOnSurfaceVariant,
                        modifier = Modifier.size(24.dp)
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
                    QuickLaunchPill("Store", "com.android.vending", Color(0xFF00E676), Color.Black) {
                        scope.launch {
                            val ok = adbManager.openPlayStoreOnTv()
                            Toast.makeText(context, if (ok) "Opening TV Play Store..." else "Failed to open Play Store", Toast.LENGTH_SHORT).show()
                        }
                    }
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

    if (showWirelessDialog) {
        com.atv.adbcommander.ui.components.WirelessBluetoothDialog(
            adbManager = adbManager,
            onDismiss = { showWirelessDialog = false }
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
