package com.atv.adbcommander.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atv.adbcommander.adb.AdbManager
import com.atv.adbcommander.model.CdnPingResult
import com.atv.adbcommander.model.PrivateDnsState
import com.atv.adbcommander.ui.components.TellyCard
import com.atv.adbcommander.ui.components.TellyTvCookie
import com.atv.adbcommander.ui.components.TvScreenshotDialog
import com.atv.adbcommander.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TweaksScreen(
    adbManager: AdbManager,
    isConnected: Boolean,
    onOpenLaunchers: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var dnsState by remember { mutableStateOf(PrivateDnsState()) }
    var animScale by remember { mutableFloatStateOf(1.0f) }
    var frameRateMode by remember { mutableIntStateOf(0) }
    var isBlackoutActive by remember { mutableStateOf(false) }
    var deviceModel by remember { mutableStateOf("Android TV") }
    var currentLauncher by remember { mutableStateOf("Google TV Home") }

    var isRunningPing by remember { mutableStateOf(false) }
    var pingResults by remember { mutableStateOf<List<CdnPingResult>>(emptyList()) }
    var showScreenshotDialog by remember { mutableStateOf(false) }
    var showDnsDialog by remember { mutableStateOf(false) }
    var customDnsInput by remember { mutableStateOf("dns.adguard-dns.com") }
    var showMenu by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showRebootConfirm by remember { mutableStateOf(false) }
    var showWirelessDialog by remember { mutableStateOf(false) }
    var isStayAwakeActive by remember { mutableStateOf(false) }

    var isPurgingCache by remember { mutableStateOf(false) }
    var isCleaningApks by remember { mutableStateOf(false) }

    val refreshTweaks = {
        if (isConnected) {
            coroutineScope.launch {
                withContext(Dispatchers.IO) {
                    val dns = adbManager.getPrivateDns()
                    val anim = adbManager.getAnimationScale()
                    val fr = adbManager.getMatchContentFrameRate()
                    val m = adbManager.runShell("getprop ro.product.model").trim().ifEmpty { "Android TV" }
                    val launchers = adbManager.fetchInstalledLaunchers()
                    val l = launchers.firstOrNull { it.isCurrentDefault }?.name ?: "Google TV Home"

                    withContext(Dispatchers.Main) {
                        dnsState = dns
                        animScale = anim
                        frameRateMode = fr
                        deviceModel = m
                        currentLauncher = l
                    }
                }
            }
        }
    }

    LaunchedEffect(isConnected) {
        refreshTweaks()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(TellyDarkBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
    ) {
        // Top Header with Overflow 3-Dots Menu
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                // Centered Hero Header
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TellyTvCookie(
                        size = 56.dp,
                        icon = Icons.Default.Tune,
                        tint = TellyTealGlow,
                        containerColor = TellyTealDark
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = deviceModel,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = TellyOnSurface
                    )
                    Text(
                        text = if (isConnected) "Tweaks · Running fine" else "Offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isConnected) TellyTealPrimary else TellyError
                    )
                }

                // 3-Dots Menu Button at Top Right (Exact match to user screenshot)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp)
                ) {
                    Surface(
                        onClick = { showMenu = true },
                        shape = CircleShape,
                        color = TellySurfaceContainerHigh,
                        border = BorderStroke(1.dp, TellyOutlineSubtle),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = TellyOnSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier
                            .background(TellySurfaceContainerHigh)
                            .border(1.dp, TellyOutlineSubtle, RoundedCornerShape(14.dp))
                    ) {
                        // 1. About & Contact
                        DropdownMenuItem(
                            text = { Text("About & Contact", color = TellyOnSurface, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = TellyTealPrimary) },
                            onClick = {
                                showMenu = false
                                showAboutDialog = true
                            }
                        )

                        HorizontalDivider(color = TellyOutlineSubtle.copy(alpha = 0.5f))

                        // 2. Wireless & Bluetooth
                        DropdownMenuItem(
                            text = { Text("Wireless & Bluetooth", color = TellyOnSurface, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = { Icon(Icons.Default.SettingsBluetooth, contentDescription = null, tint = Color(0xFF64B5F6)) },
                            onClick = {
                                showMenu = false
                                showWirelessDialog = true
                            }
                        )

                        // 3. Open TV Developer Settings
                        DropdownMenuItem(
                            text = { Text("TV Developer Options", color = TellyOnSurface) },
                            leadingIcon = { Icon(Icons.Default.DeveloperMode, contentDescription = null, tint = Color(0xFF64B5F6)) },
                            onClick = {
                                showMenu = false
                                coroutineScope.launch {
                                    adbManager.runShell("am start -a com.android.settings.APPLICATION_DEVELOPMENT_SETTINGS || am start -n com.android.tv.settings/.device.apps.specialaccess.SpecialAccessActivity")
                                    Toast.makeText(context, "Opening TV Developer Options...", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        // 3. Take TV Screenshot
                        DropdownMenuItem(
                            text = { Text("Take TV Screenshot", color = TellyOnSurface) },
                            leadingIcon = { Icon(Icons.Default.Screenshot, contentDescription = null, tint = Color(0xFF81C784)) },
                            onClick = {
                                showMenu = false
                                showScreenshotDialog = true
                            }
                        )

                        // 4. Toggle Stay Awake (Keep TV Screen On)
                        DropdownMenuItem(
                            text = { Text(if (isStayAwakeActive) "Disable Stay Awake" else "Keep TV Screen Awake", color = TellyOnSurface) },
                            leadingIcon = { Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFFFFD54F)) },
                            onClick = {
                                showMenu = false
                                isStayAwakeActive = !isStayAwakeActive
                                coroutineScope.launch {
                                    adbManager.runShell(if (isStayAwakeActive) "svc power stayon true" else "svc power stayon false")
                                    Toast.makeText(context, if (isStayAwakeActive) "TV Stay Awake enabled (Screen won't sleep)" else "TV Stay Awake disabled", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        // 5. Clear TV System Logcat Buffer
                        DropdownMenuItem(
                            text = { Text("Flush TV Logcat Buffer", color = TellyOnSurface) },
                            leadingIcon = { Icon(Icons.Default.CleaningServices, contentDescription = null, tint = Color(0xFFFF8A65)) },
                            onClick = {
                                showMenu = false
                                coroutineScope.launch {
                                    adbManager.runShell("logcat -c")
                                    Toast.makeText(context, "TV logcat buffer cleared", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        HorizontalDivider(color = TellyOutlineSubtle.copy(alpha = 0.5f))

                        // 6. Quick Reboot TV
                        DropdownMenuItem(
                            text = { Text("Reboot TV", color = Color(0xFFFF7043), fontWeight = FontWeight.SemiBold) },
                            leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null, tint = Color(0xFFFF7043)) },
                            onClick = {
                                showMenu = false
                                showRebootConfirm = true
                            }
                        )
                    }
                }
            }
        }

        // Card 1: Home Screen Launcher Manager (Exact match to tweaks.png)
        item {
            TellyCard {
                Text(
                    text = "Home screen",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TellyOnSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$currentLauncher opens when you press Home.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TellyOnSurfaceVariant
                )

                Spacer(Modifier.height(14.dp))

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
                            text = currentLauncher.take(1),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TellyTealGlow
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentLauncher,
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
                    onClick = { onOpenLaunchers?.invoke() },
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

        // Card 2: TV Tweaks (Exact match to tweaks.png)
        item {
            TellyCard {
                Text(
                    text = "TV tweaks",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TellyOnSurface
                )

                Spacer(Modifier.height(14.dp))

                // Switch 1: Block ads with Private DNS
                val isDnsOn = dnsState.mode == "hostname"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isConnected) {
                            if (isDnsOn) {
                                coroutineScope.launch {
                                    adbManager.setPrivateDns("opportunistic", "")
                                    dnsState = adbManager.getPrivateDns()
                                    Toast.makeText(context, "Private DNS set to Default", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                showDnsDialog = true
                            }
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Block ads with Private DNS",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = TellyOnSurface
                        )
                        Text(
                            text = if (isDnsOn) "On: ${dnsState.specifier}" else "Off. Turn on to block ads and trackers in every app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDnsOn) TellyTealPrimary else TellyOnSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isDnsOn,
                        onCheckedChange = { on ->
                            if (on) showDnsDialog = true else {
                                coroutineScope.launch {
                                    adbManager.setPrivateDns("opportunistic", "")
                                    dnsState = adbManager.getPrivateDns()
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TellyTealPrimary
                        )
                    )
                }

                HorizontalDivider(color = TellyOutlineSubtle, modifier = Modifier.padding(vertical = 4.dp))

                // Switch 2: Faster animations
                val isFastAnim = animScale <= 0.5f
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isConnected) {
                            val newScale = if (isFastAnim) 1.0f else 0.5f
                            coroutineScope.launch {
                                adbManager.setAnimationScale(newScale)
                                animScale = adbManager.getAnimationScale()
                                Toast.makeText(context, if (newScale == 0.5f) "UI animations set to 0.5× Fast" else "UI animations reset to 1.0×", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Faster animations",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = TellyOnSurface
                        )
                        Text(
                            text = if (isFastAnim) "On: ${animScale}× Fast speed" else "Off. Turn on to make menus feel snappier.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isFastAnim) TellyTealPrimary else TellyOnSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isFastAnim,
                        onCheckedChange = { on ->
                            val newScale = if (on) 0.5f else 1.0f
                            coroutineScope.launch {
                                adbManager.setAnimationScale(newScale)
                                animScale = adbManager.getAnimationScale()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TellyTealPrimary
                        )
                    )
                }

                HorizontalDivider(color = TellyOutlineSubtle, modifier = Modifier.padding(vertical = 4.dp))

                // Action 3: Clear all app caches
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Clear all app caches",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = TellyOnSurface
                        )
                        Text(
                            text = "Frees space by clearing every app's temporary files. App data and sign-ins stay.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TellyOnSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = {
                                coroutineScope.launch {
                                    isPurgingCache = true
                                    val msg = adbManager.clearAppCaches()
                                    isPurgingCache = false
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = isConnected && !isPurgingCache,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = TellySurfaceContainerHigh)
                        ) {
                            Text(if (isPurgingCache) "Clearing..." else "Clear all caches")
                        }
                    }
                }

                HorizontalDivider(color = TellyOutlineSubtle, modifier = Modifier.padding(vertical = 4.dp))

                // Action 4: Clean orphaned APKs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Clean orphaned APKs",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = TellyOnSurface
                        )
                        Text(
                            text = "Removes leftover .apk installer files from /sdcard/Download and /data/local/tmp.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TellyOnSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = {
                                coroutineScope.launch {
                                    isCleaningApks = true
                                    val cleaned = adbManager.cleanOrphanedApks()
                                    isCleaningApks = false
                                    Toast.makeText(context, "Cleaned $cleaned leftover APKs!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = isConnected && !isCleaningApks,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = TellySurfaceContainerHigh)
                        ) {
                            Text(if (isCleaningApks) "Cleaning..." else "Clean orphaned APKs")
                        }
                    }
                }

                HorizontalDivider(color = TellyOutlineSubtle, modifier = Modifier.padding(vertical = 4.dp))

                // Action: Wireless & Bluetooth Devices
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isConnected) { showWirelessDialog = true }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SettingsBluetooth,
                                contentDescription = null,
                                tint = Color(0xFF64B5F6),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Wireless & Bluetooth",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = TellyOnSurface
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Silently view & manage TV Bluetooth devices, headphones, and Wi-Fi networks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TellyOnSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { showWirelessDialog = true },
                            enabled = isConnected,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = TellySurfaceContainerHigh)
                        ) {
                            Icon(Icons.Default.Devices, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Manage Devices")
                        }
                    }
                }

                HorizontalDivider(color = TellyOutlineSubtle, modifier = Modifier.padding(vertical = 4.dp))

                // Action 5: Screenshot & Audio Chime
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Remote hardware diagnostics",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = TellyOnSurface
                        )
                        Text(
                            text = "Capture a high-res remote screenshot or ring an audio chime.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TellyOnSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { showScreenshotDialog = true },
                                enabled = isConnected,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = TellySurfaceContainerHigh)
                            ) {
                                Text("Screenshot")
                            }
                            FilledTonalButton(
                                onClick = {
                                    coroutineScope.launch {
                                        adbManager.playAudioChime()
                                        Toast.makeText(context, "Playing audio chime on TV!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = isConnected,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = TellySurfaceContainerHigh)
                            ) {
                                Text("Play Chime")
                            }
                        }
                    }
                }
            }
        }
    }

    // DNS Selection Dialog (Exact match to DnsDialog in TweaksBlock)
    if (showDnsDialog) {
        AlertDialog(
            onDismissRequest = { showDnsDialog = false },
            title = {
                Text(
                    text = "Block ads with Private DNS",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TellyOnSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Choose an encrypted DNS-over-TLS provider to block ads and telemetry across all apps on your TV.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TellyOnSurfaceVariant
                    )

                    // Presets
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = TellySurfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                customDnsInput = "dns.adguard-dns.com"
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("AdGuard (Recommended)", fontWeight = FontWeight.Bold, color = TellyTealPrimary)
                            Text("dns.adguard-dns.com · Blocks ads & trackers", style = MaterialTheme.typography.bodySmall, color = TellyOnSurfaceVariant)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = TellySurfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                customDnsInput = "1dot1dot1dot1.cloudflare-dns.com"
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Cloudflare 1.1.1.1", fontWeight = FontWeight.Bold, color = TellyOnSurface)
                            Text("1dot1dot1dot1.cloudflare-dns.com · Fastest CDN", style = MaterialTheme.typography.bodySmall, color = TellyOnSurfaceVariant)
                        }
                    }

                    OutlinedTextField(
                        value = customDnsInput,
                        onValueChange = { customDnsInput = it },
                        label = { Text("DoT Hostname") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val host = customDnsInput.trim()
                        if (host.isNotEmpty()) {
                            coroutineScope.launch {
                                adbManager.setPrivateDns("hostname", host)
                                dnsState = adbManager.getPrivateDns()
                                showDnsDialog = false
                                Toast.makeText(context, "Private DNS set to $host", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TellyTealPrimary, contentColor = Color.Black)
                ) {
                    Text("Apply DNS")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDnsDialog = false }) {
                    Text("Cancel", color = TellyOnSurfaceVariant)
                }
            },
            containerColor = TellySurfaceContainer
        )
    }

    if (showScreenshotDialog) {
        TvScreenshotDialog(
            adbManager = adbManager,
            onDismiss = { showScreenshotDialog = false }
        )
    }

    // ==========================================
    // About ATV ADB Commander Dialog (with Contact info)
    // ==========================================
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = TellySurfaceContainerHigh,
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(TellyTealDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = TellyTealGlow,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "ATV ADB Commander",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = TellyOnSurface
                        )
                        Text(
                            text = "v2.5.0 Titanium Edition",
                            fontSize = 11.5.sp,
                            color = TellyTealPrimary
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Next-generation remote management suite for Android TV over wireless ADB. Control, debloat, stream, back up, and monitor your TV in real time with hardware-accelerated telemetry.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TellyOnSurfaceVariant
                    )

                    // Contact & Support Card (Highlighted)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TellyDarkBackground,
                        border = BorderStroke(1.dp, TellyTealPrimary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "CONTACT & SUPPORT",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                color = TellyTealPrimary
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = "Email",
                                    tint = TellyTealGlow,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "message2sekhar@gmail.com",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TellyOnSurface
                                )
                            }

                            Spacer(Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Send Email Action Button
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                data = Uri.parse("mailto:message2sekhar@gmail.com")
                                                putExtra(Intent.EXTRA_SUBJECT, "ATV ADB Commander Feedback & Support")
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "No email app installed", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TellyTealDark),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = null, tint = TellyTealGlow, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Send Email", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TellyTealGlow)
                                }

                                // Copy Email Action Button
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Contact Email", "message2sekhar@gmail.com")
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copied: message2sekhar@gmail.com", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, TellyOutlineSubtle),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TellyOnSurface, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Copy Email", fontSize = 11.sp, color = TellyOnSurface)
                                }
                            }
                        }
                    }

                    // Connected Target TV Info
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = TellyDarkBackground,
                        border = BorderStroke(1.dp, TellyOutlineSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "TARGET TV",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TellyOnSurfaceVariant
                                )
                                Text(
                                    text = deviceModel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TellyOnSurface
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isConnected) TellySuccessContainer else TellyErrorContainer
                            ) {
                                Text(
                                    text = if (isConnected) "CONNECTED" else "OFFLINE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isConnected) TellySuccess else TellyError,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close", color = TellyTealPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // ==========================================
    // Quick Reboot Confirm Dialog
    // ==========================================
    if (showRebootConfirm) {
        AlertDialog(
            onDismissRequest = { showRebootConfirm = false },
            containerColor = TellySurfaceContainerHigh,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Reboot TV?", fontWeight = FontWeight.Bold, color = TellyOnSurface) },
            text = { Text("Are you sure you want to reboot the connected TV now?", color = TellyOnSurfaceVariant) },
            confirmButton = {
                Button(
                    onClick = {
                        showRebootConfirm = false
                        coroutineScope.launch {
                            val res = adbManager.reboot("normal")
                            Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7043))
                ) {
                    Text("Reboot Now", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRebootConfirm = false }) {
                    Text("Cancel", color = TellyOnSurface)
                }
            }
        )
    }

    // ==========================================
    // Wireless & Bluetooth Dialog
    // ==========================================
    if (showWirelessDialog) {
        com.atv.adbcommander.ui.components.WirelessBluetoothDialog(
            adbManager = adbManager,
            onDismiss = { showWirelessDialog = false }
        )
    }
}
