package com.atv.adbcommander.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.atv.adbcommander.model.TvLauncherInfo
import com.atv.adbcommander.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun TvLauncherScreen(
    adbManager: AdbManager,
    isConnected: Boolean,
    onNavigateToSideload: () -> Unit = {},
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var launchers by remember { mutableStateOf<List<TvLauncherInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var launcherToDisable by remember { mutableStateOf<TvLauncherInfo?>(null) }
    var showInfoDialog by remember { mutableStateOf(false) }

    fun refreshLaunchers() {
        if (isConnected) {
            coroutineScope.launch {
                isLoading = true
                try {
                    launchers = adbManager.fetchInstalledLaunchers()
                } finally {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(isConnected) {
        if (isConnected) {
            refreshLaunchers()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
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
                        text = "Connect to TV to manage, override, and switch home launchers.",
                        color = AccentAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Header with Refresh & Info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onClose != null) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(TellySurfaceContainer)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TellyOnSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Column {
                    Text(
                        text = "TV LAUNCHER ENGINE",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = TellyOnSurface
                    )
                    Text(
                        text = "Override stock home screen & eliminate TV ads",
                        fontSize = 11.sp,
                        color = TellyOnSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { showInfoDialog = true },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = "Help",
                        tint = TextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = { refreshLaunchers() },
                    enabled = isConnected && !isLoading,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                ) {
                    if (isLoading) {
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

        // Futuristic Tip Banner
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            color = PrimaryBlue.copy(alpha = 0.12f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "On Google TV & Fire TV, tapping 'Set as Default' also disables stock ad-filled launchers so your physical TV remote's Home button opens your new launcher instantly.",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        // Active Default Launcher Card
        val activeLauncher = launchers.firstOrNull { it.isCurrentDefault }
        if (activeLauncher != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                shape = RoundedCornerShape(18.dp),
                color = DarkCard,
                border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f)),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(AccentGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Active Home",
                                tint = AccentGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = activeLauncher.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ACTIVE DEFAULT",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGreen,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(AccentGreen.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = activeLauncher.packageName,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                adbManager.launchHome()
                                Toast.makeText(context, "Opened Home on TV", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Open", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Installed Launchers Section Header
        Text(
            text = "DETECTED HOME LAUNCHERS",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (launchers.isEmpty() && !isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (!isConnected) "Connect to TV to see installed launchers" else "Scanning launchers via ADB...",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(launchers, key = { it.packageName }) { launcher ->
                    LauncherCard(
                        launcher = launcher,
                        onSetDefault = {
                            coroutineScope.launch {
                                val res = adbManager.setDefaultLauncher(launcher.packageName, launcher.activityName)
                                Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                                refreshLaunchers()
                            }
                        },
                        onTestLaunch = {
                            coroutineScope.launch {
                                adbManager.runShell("monkey -p ${launcher.packageName} 1")
                                Toast.makeText(context, "Launched ${launcher.name}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onToggleStock = {
                            if (launcher.isEnabled) {
                                launcherToDisable = launcher
                            } else {
                                coroutineScope.launch {
                                    val res = adbManager.enableStockLauncher(launcher.packageName)
                                    Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                                    refreshLaunchers()
                                }
                            }
                        }
                    )
                }

                // Recommended Launchers Card
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = DarkSurfaceVariant,
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Recommend,
                                    contentDescription = "Recommended",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "POPULAR ZERO-AD LAUNCHERS",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = TextPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "• Projectivy Launcher: Fastest, ultra-smooth, customizable channels with 0 ads\n• FLauncher: Open-source, lightweight Material design\n• Emotn UI: Modern minimalist interface",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedButton(
                                onClick = onNavigateToSideload,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan)
                            ) {
                                Icon(imageVector = Icons.Default.CloudUpload, contentDescription = "Sideload", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sideload Launcher from Phone", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Disable Stock Launcher Confirmation Dialog
    if (launcherToDisable != null) {
        val launcher = launcherToDisable!!
        AlertDialog(
            onDismissRequest = { launcherToDisable = null },
            title = {
                Text(
                    "DISABLE STOCK TV LAUNCHER?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp,
                    color = AccentAmber
                )
            },
            text = {
                Text(
                    "Disabling '${launcher.name}' makes your installed custom launcher (like Projectivy or FLauncher) the permanent home screen when pressing the HOME button on your TV remote.\n\nYou can re-enable the stock launcher at any time.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 17.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val res = adbManager.disableStockLauncher(launcher.packageName)
                            Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                            refreshLaunchers()
                            launcherToDisable = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Disable Stock Launcher")
                }
            },
            dismissButton = {
                TextButton(onClick = { launcherToDisable = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary
        )
    }

    // Help / Info Dialog
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                Text(
                    "HOW TV LAUNCHER SWITCHING WORKS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp,
                    color = CyberCyan
                )
            },
            text = {
                Column {
                    Text("1. Install your preferred custom launcher (e.g. Projectivy Launcher or FLauncher) using the Sideload tab.", fontSize = 12.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("2. Tap 'Set as Default' next to your custom launcher.", fontSize = 12.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("3. On Android TV / Google TV, to completely bypass stock Google/Fire TV ads when pressing HOME on your remote, tap 'Disable Stock Launcher'.", fontSize = 12.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("4. You can restore the stock launcher anytime with one tap.", fontSize = 12.sp, color = TextSecondary)
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Understood", color = PrimaryBlue)
                }
            },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary
        )
    }
}

@Composable
fun LauncherCard(
    launcher: TvLauncherInfo,
    onSetDefault: () -> Unit,
    onTestLaunch: () -> Unit,
    onToggleStock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (launcher.isCurrentDefault) DarkSurfaceVariant else DarkCard,
        border = BorderStroke(1.dp, if (launcher.isCurrentDefault) CyberCyan.copy(alpha = 0.5f) else BorderSubtle)
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
                            text = launcher.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        if (launcher.isStock) {
                            Text(
                                text = "STOCK",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(DarkSurfaceVariant)
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        } else {
                            Text(
                                text = "CUSTOM",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CyberCyan.copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }

                        if (!launcher.isEnabled) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "DISABLED",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentAmber,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AccentAmber.copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = launcher.packageName,
                        fontSize = 10.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }

                IconButton(
                    onClick = onTestLaunch,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.Launch,
                        contentDescription = "Test Launch",
                        tint = CyberCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!launcher.isCurrentDefault && launcher.isEnabled) {
                    Button(
                        onClick = onSetDefault,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Text("Set as Default", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (launcher.isStock) {
                    OutlinedButton(
                        onClick = onToggleStock,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (launcher.isEnabled) AccentAmber else AccentGreen
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Text(if (launcher.isEnabled) "Disable Stock" else "Enable Stock", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
