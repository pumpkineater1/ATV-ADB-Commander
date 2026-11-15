package com.atv.adbcommander.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
        // Top Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
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
}
