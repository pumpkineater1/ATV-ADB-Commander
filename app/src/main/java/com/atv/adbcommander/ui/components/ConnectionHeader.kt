package com.atv.adbcommander.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atv.adbcommander.adb.AdbManager
import com.atv.adbcommander.model.ConnectionState
import com.atv.adbcommander.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionHeader(
    connectionState: ConnectionState,
    adbManager: AdbManager? = null,
    onOpenTerminal: (() -> Unit)? = null,
    onConnect: (String, Int) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("atv_adb_prefs", Context.MODE_PRIVATE) }

    var ipAddress by remember {
        mutableStateOf(prefs.getString("last_ip", "192.168.1.") ?: "192.168.1.")
    }
    var portText by remember {
        mutableStateOf(prefs.getInt("last_port", 5555).toString())
    }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showDiscoveryDialog by remember { mutableStateOf(false) }
    var showPowerToolsDialog by remember { mutableStateOf(false) }
    var showSmartHubDialog by remember { mutableStateOf(false) }
    var autoConnectEnabled by remember { mutableStateOf(adbManager?.isAutoConnectEnabled() ?: true) }
    var savedDevices by remember(connectionState) {
        mutableStateOf(adbManager?.getSavedDevices() ?: emptyList())
    }

    val isConnected = connectionState is ConnectionState.Connected
    val isConnecting = connectionState is ConnectionState.Connecting || connectionState is ConnectionState.Authenticating

    // Pulsing animation for active connection or authenticating
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_trans")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = DarkCard,
        border = BorderStroke(1.dp, if (isConnected) BorderGlow else BorderSubtle),
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Top Row: Brand & Quick Action Circle Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceVariant)
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = "TV",
                            tint = CyberCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "ATV COMMANDER",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // Status Dot
                            val dotColor = when (connectionState) {
                                is ConnectionState.Connected -> AccentGreen
                                is ConnectionState.Connecting -> CyberCyan
                                is ConnectionState.Authenticating -> AccentAmber
                                is ConnectionState.Error -> AccentRed
                                is ConnectionState.Disconnected -> TextMuted
                            }
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .scale(if (isConnected || isConnecting) pulseScale else 1f)
                                    .clip(CircleShape)
                                    .background(dotColor.copy(alpha = if (isConnected || isConnecting) pulseAlpha else 1f))
                            )
                        }
                        Text(
                            text = if (isConnected) "ONLINE • ADB ENGINE ACTIVE" else "WIRELESS ADB PROTOCOL",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isConnected) AccentGreen else TextMuted,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                // Quick HUD Actions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isConnected && onOpenTerminal != null) {
                        IconButton(
                            onClick = onOpenTerminal,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceVariant)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "Terminal",
                                tint = AccentGreen,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    if (isConnected && adbManager != null) {
                        IconButton(
                            onClick = { showSmartHubDialog = true },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceVariant)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Smart Hub",
                                tint = AccentPurple,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        IconButton(
                            onClick = { showPowerToolsDialog = true },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceVariant)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "TV Tools",
                                tint = CyberCyan,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    } else if (!isConnected && !isConnecting) {
                        IconButton(
                            onClick = { showDiscoveryDialog = true },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceVariant)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiFind,
                                contentDescription = "Scan Wi-Fi",
                                tint = CyberCyan,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { showHelpDialog = true },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "Help",
                            tint = TextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // IP & Port Inputs
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("TV IP ADDRESS", fontSize = 10.sp, letterSpacing = 1.sp) },
                    placeholder = { Text("192.168.1.xxx", fontSize = 13.sp) },
                    singleLine = true,
                    enabled = !isConnected && !isConnecting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = if (isConnected) AccentGreen else CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = CyberCyan,
                        unfocusedLabelColor = TextMuted,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    ),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text("PORT", fontSize = 10.sp, letterSpacing = 1.sp) },
                    singleLine = true,
                    enabled = !isConnected && !isConnecting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(96.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = CyberCyan,
                        unfocusedLabelColor = TextMuted,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Connect / Disconnect Row & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Badge Pill
                val (statusColor, statusText) = when (connectionState) {
                    is ConnectionState.Connected -> Pair(AccentGreen, "CONNECTED: ${connectionState.host}")
                    is ConnectionState.Connecting -> Pair(CyberCyan, "CONNECTING...")
                    is ConnectionState.Authenticating -> Pair(AccentAmber, "AUTH POPUP ON TV")
                    is ConnectionState.Error -> Pair(AccentRed, "LINK ERROR")
                    is ConnectionState.Disconnected -> Pair(TextMuted, "DISCONNECTED")
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = DarkSurfaceVariant,
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .scale(if (isConnected || isConnecting) pulseScale else 1f)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = statusColor
                        )
                    }
                }

                // Cyber Action Button
                if (isConnected || isConnecting) {
                    Button(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Disconnect",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Disconnect",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            val port = portText.toIntOrNull() ?: 5555
                            prefs.edit()
                                .putString("last_ip", ipAddress.trim())
                                .putInt("last_port", port)
                                .apply()
                            onConnect(ipAddress.trim(), port)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Connect",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Connect",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Saved TVs & Auto-connect Carousel
            if (savedDevices.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SAVED TVS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.2.sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Auto-link",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = autoConnectEnabled,
                            onCheckedChange = { checked ->
                                autoConnectEnabled = checked
                                adbManager?.setAutoConnectEnabled(checked)
                            },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    savedDevices.forEach { dev ->
                        val isCurrentSelected = ipAddress == dev.ip
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrentSelected) DarkSurfaceVariant else DarkSurface,
                            border = BorderStroke(
                                1.dp,
                                if (isCurrentSelected) CyberCyan.copy(alpha = 0.6f) else BorderSubtle
                            ),
                            modifier = Modifier.clickable {
                                ipAddress = dev.ip
                                portText = dev.port.toString()
                                onConnect(dev.ip, dev.port)
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (isCurrentSelected) CyberCyan else TextSecondary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = if (dev.name.isNotBlank()) dev.name else "Android TV",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrentSelected) TextPrimary else TextSecondary
                                    )
                                    Text(
                                        text = dev.ip,
                                        fontSize = 9.sp,
                                        color = TextMuted
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable {
                                            adbManager?.removeSavedDevice(dev.ip)
                                            savedDevices = adbManager?.getSavedDevices() ?: emptyList()
                                        },
                                    tint = TextMuted
                                )
                            }
                        }
                    }
                }
            }

            // Authentication Banner
            AnimatedVisibility(visible = connectionState is ConnectionState.Authenticating) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    color = AccentAmber.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = AccentAmber,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Approval dialog appeared on TV! Check 'Always allow' and press OK with your TV remote.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = AccentAmber
                        )
                    }
                }
            }

            // Error Banner
            AnimatedVisibility(visible = connectionState is ConnectionState.Error) {
                val errorMsg = (connectionState as? ConnectionState.Error)?.message ?: "Error"
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    color = AccentRed.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = AccentRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errorMsg,
                            fontSize = 12.sp,
                            color = AccentRed
                        )
                    }
                }
            }
        }
    }

    // Help Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Text(
                    "ADB WIRELESS SETUP GUIDE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 1.sp,
                    color = CyberCyan
                )
            },
            text = {
                Column {
                    Text("1. On your Android TV, open Settings -> Device Preferences -> About.", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("2. Scroll to 'Build' and click it 7 times until 'You are now a developer!' appears.", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("3. Go back to Device Preferences -> Developer Options.", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("4. Enable 'USB Debugging' and 'Network Debugging' (or Wireless Debugging).", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("5. Check your TV's Wi-Fi IP address in Settings -> Network & Internet.", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("6. Enter the IP here and press Connect. When the prompt appears on TV, check 'Always allow' and press OK.", fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showHelpDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Got it")
                }
            },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary
        )
    }

    // Wi-Fi Subnet Discovery Dialog
    if (showDiscoveryDialog) {
        TvDiscoveryDialog(
            onDeviceSelected = { ip, port ->
                ipAddress = ip
                portText = port.toString()
                showDiscoveryDialog = false
                onConnect(ip, port)
            },
            onDismiss = { showDiscoveryDialog = false }
        )
    }

    // TV Power & Display Tools Dialog
    if (showPowerToolsDialog && adbManager != null) {
        TvPowerToolsDialog(
            adbManager = adbManager,
            onDismiss = { showPowerToolsDialog = false }
        )
    }

    // TV Smart Hub Dialog
    if (showSmartHubDialog && adbManager != null) {
        TvSmartHubDialog(
            adbManager = adbManager,
            onDismiss = { showSmartHubDialog = false }
        )
    }
}
