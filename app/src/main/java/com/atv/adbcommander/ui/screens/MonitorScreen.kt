package com.atv.adbcommander.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atv.adbcommander.adb.AdbManager
import com.atv.adbcommander.model.*
import com.atv.adbcommander.ui.components.TellyStatusChip
import com.atv.adbcommander.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class TvAppFilter(val title: String) {
    USER("User"),
    SYSTEM("System"),
    DISABLED("Disabled"),
    ALL("All")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    adbManager: AdbManager,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var cpuInfo by remember { mutableStateOf(CpuInfo()) }
    var memoryInfo by remember { mutableStateOf(MemoryInfo()) }
    var storageInfo by remember { mutableStateOf(StorageInfo()) }
    val installedApps by adbManager.installedApps.collectAsState()
    val isAppsLoading by adbManager.isLoadingApps.collectAsState()
    var runningProcesses by remember { mutableStateOf<List<RunningProcessInfo>>(emptyList()) }
    var processSortByMem by remember { mutableStateOf(true) }

    var isLoadingStats by remember { mutableStateOf(false) }
    var isAutoRefresh by remember { mutableStateOf(false) }
    var selectedSection by remember { mutableIntStateOf(1) } // 0 = Performance & Processes, 1 = TV Apps, 2 = Backup

    var appSearchQuery by remember { mutableStateOf("") }
    var selectedAppFilter by remember { mutableStateOf(TvAppFilter.USER) }
    var appToUninstall by remember { mutableStateOf<TvAppInfo?>(null) }
    var appToDisable by remember { mutableStateOf<TvAppInfo?>(null) }
    var selectedAppForDetails by remember { mutableStateOf<TvAppInfo?>(null) }
    val netUsageMap by adbManager.perAppNetworkState.collectAsState()
    var showHistoryDialog by remember { mutableStateOf(false) }
    var appHistory by remember { mutableStateOf<List<AppHistoryRecord>>(emptyList()) }
    var ackBootloop by remember { mutableStateOf(false) }
    var recommendations by remember { mutableStateOf<List<DebloatRecommendation>>(emptyList()) }
    var isDisablingRecs by remember { mutableStateOf(false) }
    var isRestoringAll by remember { mutableStateOf(false) }

    val refreshProcesses = {
        if (isConnected) {
            coroutineScope.launch {
                runningProcesses = adbManager.fetchRunningProcesses()
            }
        }
    }

    val refreshStats = {
        if (isConnected) {
            coroutineScope.launch {
                isLoadingStats = true
                try {
                    cpuInfo = adbManager.fetchCpuInfo()
                    memoryInfo = adbManager.fetchMemoryInfo()
                    storageInfo = adbManager.fetchStorageInfo()
                    runningProcesses = adbManager.fetchRunningProcesses()
                    adbManager.fetchNetworkTelemetry()
                } finally {
                    isLoadingStats = false
                }
            }
        }
    }

    val refreshApps = {
        if (isConnected) {
            coroutineScope.launch {
                adbManager.refreshInstalledApps()
                recommendations = adbManager.getModelDebloatRecommendations()
                adbManager.fetchNetworkTelemetry()
            }
        }
    }

    // Initial load on connect
    LaunchedEffect(isConnected) {
        if (isConnected) {
            refreshStats()
            if (installedApps.isEmpty()) {
                refreshApps()
            }
            refreshProcesses()
        }
    }

    // Auto-refresh loop
    LaunchedEffect(isAutoRefresh, isConnected) {
        if (isAutoRefresh && isConnected) {
            while (isActive) {
                delay(3000)
                if (selectedSection == 0) {
                    cpuInfo = adbManager.fetchCpuInfo()
                    memoryInfo = adbManager.fetchMemoryInfo()
                    runningProcesses = adbManager.fetchRunningProcesses()
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        if (!isConnected) {
            val savedList = remember(isConnected) { adbManager.getSavedDevices() }
            val targetDevice = savedList.firstOrNull()
            val targetIp = targetDevice?.ip ?: "Android TV"
            val targetPort = targetDevice?.port ?: 5555

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clickable {
                        coroutineScope.launch {
                            adbManager.connect(targetIp, targetPort)
                        }
                    },
                color = AccentAmber.copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
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
                            text = "TV is Offline · Tap to connect ($targetIp)",
                            color = AccentAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                adbManager.connect(targetIp, targetPort)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Connect", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        }

        // Section Tabs & Telemetry Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = DarkCard,
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Row(
                    modifier = Modifier.padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sections = listOf(
                        Triple(0, "Stats", Icons.Default.Speed),
                        Triple(1, "Apps", Icons.Default.Apps),
                        Triple(2, "Backup", Icons.Default.Backup)
                    )

                    sections.forEach { (index, title, icon) ->
                        val isSelected = selectedSection == index
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) DarkSurfaceVariant else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)) else null,
                            modifier = Modifier.clickable {
                                selectedSection = index
                                if (index == 1 && installedApps.isEmpty()) refreshApps()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) CyberCyan else TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = title,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) TextPrimary else TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Refresh & Auto-Refresh Controls (Always visible across all tabs)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isAutoRefresh) CyberCyan.copy(alpha = 0.18f) else DarkSurfaceVariant)
                        .clickable {
                            isAutoRefresh = !isAutoRefresh
                            if (isAutoRefresh) {
                                Toast.makeText(context, "Live auto-refresh enabled (every 3s)", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Auto-refresh paused", Toast.LENGTH_SHORT).show()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isAutoRefresh) Icons.Default.Sync else Icons.Default.SyncDisabled,
                        contentDescription = "Auto Refresh",
                        tint = if (isAutoRefresh) CyberCyan else TextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                        .clickable {
                            if (selectedSection == 0) refreshStats() else refreshApps()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingStats || (selectedSection == 1 && isAppsLoading)) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = CyberCyan
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = CyberCyan,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }

        // TAB 1: System Performance (CPU, RAM, Storage)
        if (selectedSection == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // CPU Card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = DarkCard,
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(DarkSurfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Memory,
                                            contentDescription = "CPU",
                                            tint = CyberCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "CPU TELEMETRY",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            letterSpacing = 1.sp,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "SYSTEM ENGINE CORE",
                                            fontSize = 9.sp,
                                            color = TextMuted,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = DarkSurfaceVariant,
                                    border = BorderStroke(1.dp, if (cpuInfo.totalUsagePercent > 80) CyberRose else CyberCyan)
                                ) {
                                    Text(
                                        text = "${cpuInfo.totalUsagePercent}%",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (cpuInfo.totalUsagePercent > 80) CyberRose
                                        else if (cpuInfo.totalUsagePercent > 50) AccentAmber
                                        else AccentGreen,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            LinearProgressIndicator(
                                progress = { (cpuInfo.totalUsagePercent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = if (cpuInfo.totalUsagePercent > 80) CyberRose else CyberCyan,
                                trackColor = DarkSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "USER: ${cpuInfo.userPercent}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "SYSTEM: ${cpuInfo.systemPercent}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "IDLE: ${cpuInfo.idlePercent}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGreen
                                )
                            }

                            if (cpuInfo.topProcesses.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "TOP CONSUMING THREADS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted,
                                    letterSpacing = 1.2.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                cpuInfo.topProcesses.forEach { proc ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = proc.name.take(30),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = proc.cpuPercent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CyberCyan
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // RAM Memory Card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = DarkCard,
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(DarkSurfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Storage,
                                            contentDescription = "RAM",
                                            tint = PrimaryBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "RAM ALLOCATION",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            letterSpacing = 1.sp,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "DYNAMIC PHYSICAL MEMORY",
                                            fontSize = 9.sp,
                                            color = TextMuted,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = DarkSurfaceVariant,
                                    border = BorderStroke(1.dp, if (memoryInfo.usedPercent > 85) CyberRose else PrimaryBlue)
                                ) {
                                    Text(
                                        text = "${memoryInfo.usedPercent}%",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (memoryInfo.usedPercent > 85) CyberRose else PrimaryBlue,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            LinearProgressIndicator(
                                progress = { (memoryInfo.usedPercent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = PrimaryBlue,
                                trackColor = DarkSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("USED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                                    Text("${memoryInfo.usedMb} MB", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                }
                                Column {
                                    Text("FREE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                                    Text("${memoryInfo.freeMb} MB", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                                }
                                Column {
                                    Text("CACHED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                                    Text("${memoryInfo.cachedMb} MB", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                                }
                                Column {
                                    Text("TOTAL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                                    Text("${memoryInfo.totalMb} MB", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                                }
                            }
                        }
                    }
                }

                // Internal Storage Card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = DarkCard,
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(DarkSurfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = "Storage",
                                            tint = AccentAmber,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "INTERNAL STORAGE (/sdcard)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        letterSpacing = 1.sp,
                                        color = TextPrimary
                                    )
                                }
                                Text(
                                    text = storageInfo.usePercent,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentAmber
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Used: ${storageInfo.used} / ${storageInfo.total}",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "Free: ${storageInfo.available}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGreen
                                )
                            }
                        }
                    }
                }

                // Running Background Apps & Top Consumers Card
                item {
                    RunningProcessesCard(
                        processes = if (processSortByMem) runningProcesses.sortedByDescending { it.memoryMb } else runningProcesses.sortedByDescending { it.cpuPercent },
                        sortByMemory = processSortByMem,
                        onToggleSort = { processSortByMem = !processSortByMem },
                        onRefresh = refreshProcesses,
                        onKillProcess = { proc ->
                            coroutineScope.launch {
                                val res = adbManager.forceStopApp(proc.packageName)
                                Toast.makeText(context, "Stopped ${proc.name}", Toast.LENGTH_SHORT).show()
                                refreshProcesses()
                                refreshStats()
                            }
                        }
                    )
                }
            }
        }

        // TAB 2: TV Installed Apps Manager
        if (selectedSection == 1) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Tellyscope Model-Aware Debloat Recommendation Banner
                val matchingRecs = recommendations.filter { rec ->
                    installedApps.any { it.packageName == rec.packageName && it.isEnabled }
                }

                if (matchingRecs.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "TELLYSCOPE DEBLOAT PROFILE",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                        color = TextPrimary
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = AccentGreen.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, AccentGreen)
                                ) {
                                    Text(
                                        "${matchingRecs.size} SAFE BLOAT FOUND",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentGreen
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Tested and safe to disable for your specific TV model to eliminate background telemetry and recover RAM.",
                                fontSize = 10.sp,
                                color = TextMuted
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            isDisablingRecs = true
                                            var count = 0
                                            for (rec in matchingRecs) {
                                                adbManager.disableApp(rec.packageName)
                                                count++
                                            }
                                            isDisablingRecs = false
                                            Toast.makeText(context, "Disabled $count recommended bloat apps!", Toast.LENGTH_LONG).show()
                                            refreshApps()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    enabled = isConnected && !isDisablingRecs,
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isDisablingRecs) "Disabling..." else "Disable Recommended", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            isRestoringAll = true
                                            val (count, msg) = adbManager.restoreAllDisabledApps()
                                            isRestoringAll = false
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            refreshApps()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, BorderSubtle),
                                    enabled = isConnected && !isRestoringAll,
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Restore, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isRestoringAll) "Restoring..." else "Restore All", fontSize = 11.sp, color = TextPrimary)
                                }
                            }
                        }
                    }
                }

                // Search Field & Debloat History Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = appSearchQuery,
                        onValueChange = { appSearchQuery = it },
                        placeholder = { Text("Search installed TV packages...", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (appSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { appSearchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted)
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // TV Play Store Direct Open Button
                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .clickable {
                                coroutineScope.launch {
                                    val ok = adbManager.openPlayStoreOnTv()
                                    Toast.makeText(context, if (ok) "Opening TV Play Store..." else "Failed to open TV Play Store", Toast.LENGTH_SHORT).show()
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = DarkCard,
                        border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.45f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ShoppingBag,
                                contentDescription = "TV Play Store",
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .clickable {
                                appHistory = adbManager.getAppHistory()
                                showHistoryDialog = true
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = DarkCard,
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            BadgedBox(badge = {
                                val count = adbManager.getAppHistory().size
                                if (count > 0) {
                                    Badge(containerColor = CyberCyan) {
                                        Text("$count", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                    }
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "Debloat History",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // App Category Filter Chips: User, System, Disabled, All
                val userCount = installedApps.count { !it.isSystemApp }
                val systemCount = installedApps.count { it.isSystemApp }
                val disabledCount = installedApps.count { !it.isEnabled }
                val allCount = installedApps.size

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val filterPills = listOf(
                        Pair(TvAppFilter.USER, "User ($userCount)"),
                        Pair(TvAppFilter.SYSTEM, "System ($systemCount)"),
                        Pair(TvAppFilter.DISABLED, "Disabled ($disabledCount)"),
                        Pair(TvAppFilter.ALL, "All ($allCount)")
                    )

                    filterPills.forEach { (filter, label) ->
                        val isSelected = selectedAppFilter == filter
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) DarkSurfaceVariant else DarkSurface,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) (if (filter == TvAppFilter.DISABLED) AccentAmber else CyberCyan) else BorderSubtle
                            ),
                            modifier = Modifier
                                .weight(if (filter == TvAppFilter.ALL) 0.8f else 1f)
                                .clickable { selectedAppFilter = filter }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) (if (filter == TvAppFilter.DISABLED) AccentAmber else CyberCyan) else TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val filteredApps = remember(installedApps, selectedAppFilter, appSearchQuery) {
                    installedApps.filter { app ->
                        val matchesCategory = when (selectedAppFilter) {
                            TvAppFilter.USER -> !app.isSystemApp
                            TvAppFilter.SYSTEM -> app.isSystemApp
                            TvAppFilter.DISABLED -> !app.isEnabled
                            TvAppFilter.ALL -> true
                        }
                        matchesCategory && (appSearchQuery.isEmpty() ||
                                app.packageName.contains(appSearchQuery, ignoreCase = true) ||
                                app.appName.contains(appSearchQuery, ignoreCase = true))
                    }
                }

                if (filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (installedApps.isEmpty()) "Querying packages from TV via ADB..."
                            else if (selectedAppFilter == TvAppFilter.DISABLED) "No disabled packages found"
                            else "No matching apps found",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            AppItemCard(
                                app = app,
                                netUsage = netUsageMap[app.packageName],
                                onClick = { selectedAppForDetails = app },
                                onLaunch = {
                                    coroutineScope.launch {
                                        adbManager.launchApp(app.packageName)
                                        Toast.makeText(context, "Launched ${if (app.appName.isNotBlank()) app.appName else app.packageName}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onOpenTvStore = {
                                    coroutineScope.launch {
                                        adbManager.openPlayStoreOnTv(app.packageName)
                                        Toast.makeText(context, "Opening TV Play Store...", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onForceStop = {
                                    coroutineScope.launch {
                                        adbManager.forceStopApp(app.packageName)
                                        Toast.makeText(context, "Force stopped", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onDisable = {
                                    appToDisable = app
                                },
                                onEnable = {
                                    coroutineScope.launch {
                                        val res = adbManager.enableApp(app.packageName)
                                        Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                                        adbManager.refreshInstalledApps()
                                    }
                                },
                                onUninstall = {
                                    appToUninstall = app
                                }
                            )
                        }
                    }
                }
            }
        }

        // TAB 3: Full TV Backup & Restore
        if (selectedSection == 2) {
            TvBackupScreen(
                adbManager = adbManager,
                isConnected = isConnected
            )
        }
    }

    // Disable App Confirmation Dialog with Bootloop & Brick Protection
    if (appToDisable != null) {
        val app = appToDisable!!
        val isCoreBlocked = app.packageName == "android" || app.packageName == "com.android.systemui"

        AlertDialog(
            onDismissRequest = {
                appToDisable = null
                ackBootloop = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (icon, tint, title) = when (app.safetyLevel) {
                        AppSafetyLevel.CRITICAL -> Triple(Icons.Default.Dangerous, CyberRose, "CRITICAL: Bootloop Warning!")
                        AppSafetyLevel.CAUTION -> Triple(Icons.Default.Warning, AccentAmber, "Caution: Essential Component")
                        AppSafetyLevel.SAFE -> Triple(Icons.Default.Security, AccentGreen, "Safe to Debloat")
                    }
                    Icon(imageVector = icon, contentDescription = null, tint = tint)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(title, fontWeight = FontWeight.Bold, color = tint, fontSize = 15.sp)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Package: ${app.packageName}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = app.safetyDescription.ifEmpty {
                            "Disabling '${app.packageName}' will stop it from running in background and free memory."
                        },
                        fontSize = 12.sp,
                        color = if (app.safetyLevel == AppSafetyLevel.CRITICAL) CyberRose else TextSecondary,
                        lineHeight = 17.sp
                    )

                    if (isCoreBlocked) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = CyberRose.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CyberRose.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "🚫 PROHIBITED: Disabling core Android OS or System UI is strictly blocked by ADB Commander to prevent permanent device bricking.",
                                color = CyberRose,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else if (app.safetyLevel == AppSafetyLevel.CRITICAL) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = ackBootloop,
                                onCheckedChange = { ackBootloop = it },
                                colors = CheckboxDefaults.colors(checkedColor = CyberRose)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "I understand this may cause bootloop or system crash",
                                fontSize = 11.sp,
                                color = CyberRose,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                val canProceed = !isCoreBlocked && (app.safetyLevel != AppSafetyLevel.CRITICAL || ackBootloop)

                Button(
                    onClick = {
                        coroutineScope.launch {
                            val res = adbManager.disableApp(app.packageName)
                            Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                            adbManager.refreshInstalledApps()
                            appToDisable = null
                            ackBootloop = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (app.safetyLevel == AppSafetyLevel.CRITICAL) CyberRose else AccentAmber
                    ),
                    shape = RoundedCornerShape(10.dp),
                    enabled = canProceed
                ) {
                    Text(if (isCoreBlocked) "Blocked" else "Disable App")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    appToDisable = null
                    ackBootloop = false
                }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary
        )
    }

    // Debloat & State Change Audit History Dialog
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.History, contentDescription = null, tint = CyberCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "APP AUDIT HISTORY",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 1.sp,
                            color = CyberCyan
                        )
                    }

                    if (appHistory.isNotEmpty()) {
                        TextButton(onClick = {
                            adbManager.clearAppHistory()
                            appHistory = emptyList()
                        }) {
                            Text("Clear", fontSize = 11.sp, color = CyberRose)
                        }
                    }
                }
            },
            text = {
                if (appHistory.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No recent app freeze or unfreeze activity", color = TextSecondary, fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(appHistory, key = { it.id }) { record ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = DarkSurfaceVariant,
                                border = BorderStroke(1.dp, BorderSubtle)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val (badgeText, badgeColor) = when (record.action) {
                                                AppActionType.DISABLED -> Pair("DISABLED", AccentAmber)
                                                AppActionType.ENABLED -> Pair("ENABLED", AccentGreen)
                                                AppActionType.UNINSTALLED -> Pair("UNINSTALLED", CyberRose)
                                            }
                                            Text(
                                                text = badgeText,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = badgeColor,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(badgeColor.copy(alpha = 0.15f))
                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = record.appName,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = record.packageName,
                                            fontSize = 10.sp,
                                            color = TextSecondary,
                                            maxLines = 1
                                        )
                                    }

                                    // 1-Tap Undo button
                                    if (record.action != AppActionType.UNINSTALLED) {
                                        OutlinedButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    if (record.action == AppActionType.DISABLED) {
                                                        adbManager.enableApp(record.packageName)
                                                        Toast.makeText(context, "Undone: Re-enabled ${record.appName}", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        adbManager.disableApp(record.packageName)
                                                        Toast.makeText(context, "Undone: Disabled ${record.appName}", Toast.LENGTH_SHORT).show()
                                                    }
                                                    adbManager.refreshInstalledApps()
                                                    appHistory = adbManager.getAppHistory()
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text("Undo", fontSize = 11.sp, color = CyberCyan)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) {
                    Text("Close", color = PrimaryBlue)
                }
            },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary
        )
    }

    // Uninstall Confirmation Dialog
    if (appToUninstall != null) {
        val app = appToUninstall!!
        AlertDialog(
            onDismissRequest = { appToUninstall = null },
            title = { Text("Uninstall App?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to uninstall '${app.packageName}' from Android TV?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val res = adbManager.uninstallApp(app.packageName)
                            Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                            adbManager.refreshInstalledApps()
                            appToUninstall = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberRose),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Uninstall")
                }
            },
            dismissButton = {
                TextButton(onClick = { appToUninstall = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    if (selectedAppForDetails != null) {
        AppDetailsBottomSheet(
            app = selectedAppForDetails!!,
            adbManager = adbManager,
            netUsage = netUsageMap[selectedAppForDetails!!.packageName],
            onDismiss = { selectedAppForDetails = null },
            onRefresh = { refreshApps() }
        )
    }
}

object BitmapMemoryCache {
    private val cache = androidx.collection.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(150)

    fun get(key: String): androidx.compose.ui.graphics.ImageBitmap? = cache[key]

    fun put(key: String, bitmap: androidx.compose.ui.graphics.ImageBitmap) {
        cache.put(key, bitmap)
    }
}

@Composable
fun AppIconView(
    app: TvAppInfo,
    size: androidx.compose.ui.unit.Dp = 42.dp,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(app.packageName, app.iconBytes != null, app.iconBytes?.size) {
        if (app.iconBytes == null) null
        else {
            BitmapMemoryCache.get(app.packageName) ?: run {
                try {
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(app.iconBytes, 0, app.iconBytes.size)?.asImageBitmap()
                    if (bmp != null) BitmapMemoryCache.put(app.packageName, bmp)
                    bmp
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = app.appName,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.28f)),
            contentScale = ContentScale.Crop
        )
        return
    }

    val bg = Color(app.brandColorHex)
    val icon = when (app.category.lowercase()) {
        "streaming", "media", "video" -> Icons.Default.Tv
        "home launcher", "launcher" -> Icons.Default.Home
        "core system", "system", "os" -> Icons.Default.Memory
        "app store", "store" -> Icons.Default.ShoppingBag
        "web browser", "browser" -> Icons.Default.Language
        "music & audio", "music", "audio" -> Icons.Default.MusicNote
        "ad & telemetry", "telemetry" -> Icons.Default.BugReport
        else -> null
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.55f)
            )
        } else {
            val text = if (app.appName.isNotBlank()) app.appName.take(2).uppercase() else app.packageName.takeLast(2).uppercase()
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.36f).sp
                ),
                color = Color.White
            )
        }
    }
}

@Composable
fun AppItemCard(
    app: TvAppInfo,
    netUsage: AppNetworkUsage? = null,
    onClick: () -> Unit,
    onLaunch: () -> Unit,
    onOpenTvStore: () -> Unit,
    onForceStop: () -> Unit,
    onDisable: () -> Unit,
    onEnable: () -> Unit,
    onUninstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = TellySurfaceContainer,
        border = BorderStroke(1.dp, TellyOutlineSubtle)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconView(app = app, size = 44.dp)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (app.appName.isNotBlank()) app.appName else app.packageName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = TellyOnSurface,
                    maxLines = 1
                )

                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = TellyOnSurfaceVariant,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    // System vs User Badge
                    Text(
                        text = if (app.isSystemApp) "SYSTEM" else "USER",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (app.isSystemApp) TellyOnSurfaceVariant else TellyTealGlow,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (app.isSystemApp) TellySurfaceContainerHigh else TellyTealDark.copy(alpha = 0.5f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Enabled vs Disabled Badge
                    Text(
                        text = if (app.isEnabled) "ACTIVE" else "DISABLED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (app.isEnabled) TellyTealPrimary else TellyError,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (app.isEnabled) TellyTealDark.copy(alpha = 0.3f) else TellyError.copy(alpha = 0.2f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )

                    // Individual App Data Usage (Downloaded & Uploaded)
                    if (netUsage != null && (netUsage.rxBytes > 0 || netUsage.txBytes > 0)) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "↓ ${netUsage.rxFormatted} · ↑ ${netUsage.txFormatted}",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = TellySuccess,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(TellySuccessContainer)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    if (app.isSystemApp) {
                        Spacer(modifier = Modifier.width(6.dp))
                        val (sLabel, sColor) = when (app.safetyLevel) {
                            AppSafetyLevel.CRITICAL -> Pair("⛔ CRITICAL", TellyError)
                            AppSafetyLevel.CAUTION -> Pair("⚡ CAUTION", Color(0xFFFFB74D))
                            AppSafetyLevel.SAFE -> Pair("🛡️ SAFE DEBLOAT", TellyTealPrimary)
                        }
                        Text(
                            text = sLabel,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = sColor,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(sColor.copy(alpha = 0.15f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Quick Action Buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenTvStore,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingBag,
                        contentDescription = "TV Play Store",
                        tint = TellyOnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (app.isEnabled) {
                    IconButton(onClick = onLaunch, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Launch",
                            tint = TellyTealPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    IconButton(onClick = onEnable, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Enable",
                            tint = TellyTealPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailsBottomSheet(
    app: TvAppInfo,
    adbManager: AdbManager,
    netUsage: AppNetworkUsage? = null,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val entry = remember(app.packageName) { com.atv.adbcommander.util.AppCatalog.getEntry(app.packageName) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TellyDarkBackground,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TellyOutlineSubtle) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header Row: App Icon, Name, Package, Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconView(app = app, size = 56.dp)

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (app.appName.isNotBlank()) app.appName else entry.friendlyName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = TellyOnSurface
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(app.packageName))
                            Toast.makeText(context, "Package copied!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = TellyOnSurfaceVariant,
                            maxLines = 1
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = TellyTealPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Badges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TellyStatusChip(
                    text = if (app.isSystemApp) "System App" else "User Installed",
                    icon = if (app.isSystemApp) Icons.Default.Memory else Icons.Default.Apps,
                    color = if (app.isSystemApp) TellyOnSurfaceVariant else TellyTealGlow,
                    containerColor = if (app.isSystemApp) TellySurfaceContainerHigh else TellyTealDark
                )

                TellyStatusChip(
                    text = if (app.isEnabled) "Active" else "Disabled",
                    icon = if (app.isEnabled) Icons.Default.CheckCircle else Icons.Default.Block,
                    color = if (app.isEnabled) TellyTealPrimary else TellyError,
                    containerColor = if (app.isEnabled) TellyTealDark.copy(alpha = 0.4f) else TellyError.copy(alpha = 0.2f)
                )

                TellyStatusChip(
                    text = entry.category,
                    icon = Icons.Default.Category,
                    color = TellyOnSurface,
                    containerColor = TellySurfaceContainerHigh
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ==========================================
            // "What is the use of this app?" Card
            // ==========================================
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = TellySurfaceContainer,
                border = BorderStroke(1.dp, TellyOutlineSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = TellyTealPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "What is the use of this app?",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = TellyOnSurface
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = entry.purpose,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TellyOnSurfaceVariant,
                        lineHeight = 20.sp
                    )

                    Spacer(Modifier.height(10.dp))

                    // Safety Note Box
                    val (bannerBg, bannerFg, bannerIcon) = when (entry.safetyLevel) {
                        AppSafetyLevel.CRITICAL -> Triple(TellyError.copy(alpha = 0.15f), TellyError, Icons.Default.Dangerous)
                        AppSafetyLevel.CAUTION -> Triple(Color(0xFFFFB74D).copy(alpha = 0.15f), Color(0xFFFFB74D), Icons.Default.Warning)
                        AppSafetyLevel.SAFE -> Triple(TellyTealDark.copy(alpha = 0.3f), TellyTealGlow, Icons.Default.Security)
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = bannerBg,
                        border = BorderStroke(1.dp, bannerFg.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = bannerIcon, contentDescription = null, tint = bannerFg, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = entry.safetyNote,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = bannerFg
                            )
                        }
                    }
                }
            }

            // Network Data Usage Card (Individual App Downloaded & Uploaded)
            if (netUsage != null && (netUsage.rxBytes > 0 || netUsage.txBytes > 0)) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TellySurfaceContainer,
                    border = BorderStroke(1.dp, TellyOutlineSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DataUsage,
                                contentDescription = null,
                                tint = TellyOnSurface,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Network Data Consumption",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TellyOnSurface
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Downloaded
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = TellySurfaceContainerHigh
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "DOWNLOADED",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        ),
                                        color = TellyOnSurfaceVariant
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "↓ ${netUsage.rxFormatted}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TellySuccess
                                    )
                                }
                            }

                            // Uploaded
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = TellySurfaceContainerHigh
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "UPLOADED",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        ),
                                        color = TellyOnSurfaceVariant
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "↑ ${netUsage.txFormatted}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TellyOnSurface
                                    )
                                }
                            }

                            // Total
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = TellySurfaceContainerHigh
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "TOTAL",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        ),
                                        color = TellyOnSurfaceVariant
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = netUsage.totalFormatted,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TellyOnSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons
            Text(
                text = "App Controls & Actions",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = TellyOnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Row 1: Launch & Stop
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            adbManager.launchApp(app.packageName)
                            Toast.makeText(context, "Launched ${if (app.appName.isNotBlank()) app.appName else app.packageName}", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = TellyTealDark, contentColor = TellyTealGlow),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Launch", fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = {
                        coroutineScope.launch {
                            adbManager.forceStopApp(app.packageName)
                            Toast.makeText(context, "Force stopped", Toast.LENGTH_SHORT).show()
                            onRefresh()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = TellySurfaceContainerHigh, contentColor = TellyOnSurface),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Force Stop")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Freeze / Unfreeze & Clear Cache/Data
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (app.isEnabled) {
                    FilledTonalButton(
                        onClick = {
                            if (app.packageName == "android" || app.packageName == "com.android.systemui") {
                                Toast.makeText(context, "⛔ BLOCKED: Core system app cannot be disabled!", Toast.LENGTH_LONG).show()
                            } else {
                                coroutineScope.launch {
                                    val res = adbManager.disableApp(app.packageName)
                                    Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                                    onRefresh()
                                    onDismiss()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = TellySurfaceContainerHigh, contentColor = Color(0xFFFFB74D)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Disable")
                    }
                } else {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val res = adbManager.enableApp(app.packageName)
                                Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                                onRefresh()
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TellyTealDark, contentColor = TellyTealGlow),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Re-Enable")
                    }
                }

                FilledTonalButton(
                    onClick = {
                        coroutineScope.launch {
                            val res = adbManager.clearApp(app.packageName)
                            Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                            onRefresh()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = TellySurfaceContainerHigh, contentColor = TellyOnSurface),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Clear Data")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 3: Uninstall (if user) & TV Settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!app.isSystemApp) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val res = adbManager.uninstallApp(app.packageName)
                                Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                                onRefresh()
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TellyError, contentColor = Color.Black),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Uninstall", fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            adbManager.openAppDetailsOnTv(app.packageName)
                            Toast.makeText(context, "Opened on TV settings", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, TellyOutlineSubtle),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TellyOnSurface)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("TV Settings")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Open on TV Play Store (Only TV Store, Not Mobile!)
            Button(
                onClick = {
                    coroutineScope.launch {
                        adbManager.openPlayStoreOnTv(app.packageName)
                        Toast.makeText(context, "Opening TV Play Store...", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TellySurfaceContainerHigh,
                    contentColor = TellyOnSurface
                ),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, TellyOutlineSubtle)
            ) {
                Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open on TV Play Store", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RunningProcessesCard(
    processes: List<RunningProcessInfo>,
    sortByMemory: Boolean,
    onToggleSort: () -> Unit,
    onRefresh: () -> Unit,
    onKillProcess: (RunningProcessInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
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
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = "Processes",
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "LIVE ACTIVE TASKS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                        color = TextPrimary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = DarkSurfaceVariant,
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.clickable { onToggleSort() }
                    ) {
                        Text(
                            text = if (sortByMemory) "SORT: RAM" else "SORT: CPU",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Live background processes consuming memory & CPU on your TV:",
                fontSize = 11.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (processes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No active background processes detected", color = TextMuted, fontSize = 12.sp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    processes.take(15).forEach { proc ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = DarkSurfaceVariant,
                            border = BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = proc.name,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (proc.isSystemApp) "SYS" else "APP",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (proc.isSystemApp) TextMuted else CyberCyan,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(if (proc.isSystemApp) DarkCard else CyberCyan.copy(alpha = 0.15f))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                    Text(
                                        text = "${proc.packageName} • PID ${proc.pid}",
                                        fontSize = 10.sp,
                                        color = TextMuted,
                                        maxLines = 1
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        val memColor = if (proc.memoryMb > 300) CyberRose else if (proc.memoryMb > 150) AccentAmber else AccentGreen
                                        Text(
                                            text = String.format("%.1f MB", proc.memoryMb),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = memColor
                                        )
                                        if (proc.cpuPercent > 0.1f) {
                                            Text(
                                                text = String.format("%.1f%% CPU", proc.cpuPercent),
                                                fontSize = 10.sp,
                                                color = CyberCyan
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    IconButton(
                                        onClick = { onKillProcess(proc) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = "Stop App",
                                            tint = AccentAmber,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
