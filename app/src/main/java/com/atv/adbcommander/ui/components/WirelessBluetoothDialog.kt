package com.atv.adbcommander.ui.components

import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.atv.adbcommander.adb.AdbManager
import com.atv.adbcommander.model.*
import com.atv.adbcommander.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun WirelessBluetoothDialog(
    adbManager: AdbManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Bluetooth, 1 = Wi-Fi
    var isLoading by remember { mutableStateOf(true) }
    var isScanningWifi by remember { mutableStateOf(false) }

    var bluetoothState by remember { mutableStateOf(TvBluetoothState()) }
    var wifiDetails by remember { mutableStateOf(TvWifiDetails()) }
    var scannedWifiList by remember { mutableStateOf<List<TvScannedWifiNetwork>>(emptyList()) }

    var connectWifiDialogNetwork by remember { mutableStateOf<String?>(null) }
    var connectWifiPassword by remember { mutableStateOf("") }
    var isConnectingWifi by remember { mutableStateOf(false) }

    fun refreshData() {
        scope.launch {
            isLoading = true
            try {
                if (selectedTab == 0) {
                    bluetoothState = adbManager.fetchBluetoothState()
                } else {
                    wifiDetails = adbManager.fetchWifiDetails()
                }
            } catch (ignored: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedTab) {
        refreshData()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = TellyDarkBackground,
            border = BorderStroke(1.dp, TellyOutlineSubtle),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (selectedTab == 0) Color(0xFF1976D2).copy(alpha = 0.2f) else TellyTealPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (selectedTab == 0) Icons.Default.Bluetooth else Icons.Default.Wifi,
                                contentDescription = null,
                                tint = if (selectedTab == 0) Color(0xFF64B5F6) else TellyTealPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Wireless & Devices",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TellyOnSurface
                            )
                            Text(
                                text = "Manage TV Bluetooth & Wi-Fi silently",
                                style = MaterialTheme.typography.bodySmall,
                                color = TellyOnSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TellyOnSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Segmented Tabs: [ ᛒ Bluetooth ]  [ 📶 Wi-Fi ]
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TellySurfaceContainerHigh,
                    border = BorderStroke(1.dp, TellyOutlineSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Tab 0: Bluetooth
                        Surface(
                            onClick = { selectedTab = 0 },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedTab == 0) Color(0xFF1565C0) else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bluetooth,
                                    contentDescription = null,
                                    tint = if (selectedTab == 0) Color.White else TellyOnSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Bluetooth (${bluetoothState.bondedDevices.size})",
                                    fontSize = 12.5.sp,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTab == 0) Color.White else TellyOnSurfaceVariant
                                )
                            }
                        }

                        // Tab 1: Wi-Fi
                        Surface(
                            onClick = { selectedTab = 1 },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedTab == 1) TellyTealPrimary else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = if (selectedTab == 1) Color.Black else TellyOnSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (wifiDetails.currentSsid.isNotEmpty()) wifiDetails.currentSsid.take(12) else "Wi-Fi",
                                    fontSize = 12.5.sp,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTab == 1) Color.Black else TellyOnSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = if (selectedTab == 0) Color(0xFF64B5F6) else TellyTealPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else {
                    // TAB 0: BLUETOOTH VIEW
                    if (selectedTab == 0) {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Master Bluetooth Switch Card
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = TellySurfaceContainerHigh,
                                    border = BorderStroke(1.dp, TellyOutlineSubtle),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "TV Bluetooth",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = TellyOnSurface
                                            )
                                            Text(
                                                text = if (bluetoothState.isEnabled) "Status: Enabled · ${bluetoothState.adapterName}" else "Status: Disabled",
                                                fontSize = 11.5.sp,
                                                color = if (bluetoothState.isEnabled) TellySuccess else TellyError
                                            )
                                        }

                                        Switch(
                                            checked = bluetoothState.isEnabled,
                                            onCheckedChange = { enable ->
                                                scope.launch {
                                                    adbManager.toggleBluetooth(enable)
                                                    bluetoothState = bluetoothState.copy(isEnabled = enable)
                                                    Toast.makeText(context, if (enable) "Bluetooth turned ON" else "Bluetooth turned OFF", Toast.LENGTH_SHORT).show()
                                                    refreshData()
                                                }
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = Color(0xFF1976D2),
                                                uncheckedThumbColor = TellyOnSurfaceVariant,
                                                uncheckedTrackColor = TellySurfaceContainer
                                            )
                                        )
                                    }
                                }
                            }

                            // Paired Devices Header
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp, bottom = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "PAIRED DEVICES (${bluetoothState.bondedDevices.size})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TellyOnSurfaceVariant,
                                        letterSpacing = 0.5.sp
                                    )

                                    Text(
                                        text = "Tap to manage",
                                        fontSize = 10.5.sp,
                                        color = TellyOnSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            if (bluetoothState.bondedDevices.isEmpty()) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = TellySurfaceContainerHigh.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "No paired Bluetooth devices found on TV",
                                            fontSize = 12.sp,
                                            color = TellyOnSurfaceVariant,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                }
                            } else {
                                items(bluetoothState.bondedDevices) { device ->
                                    val icon = when (device.type) {
                                        "Remote" -> Icons.Default.SettingsRemote
                                        "Gamepad" -> Icons.Default.SportsEsports
                                        "Audio" -> Icons.Default.Headphones
                                        else -> Icons.Default.Bluetooth
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = TellySurfaceContainerHigh,
                                        border = BorderStroke(1.dp, if (device.isConnected) Color(0xFF1976D2).copy(alpha = 0.6f) else TellyOutlineSubtle),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(if (device.isConnected) Color(0xFF1565C0).copy(alpha = 0.25f) else TellySurfaceContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = if (device.isConnected) Color(0xFF64B5F6) else TellyOnSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            Spacer(Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = device.name,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 13.5.sp,
                                                        color = TellyOnSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (device.isConnected) {
                                                        Spacer(Modifier.width(6.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(Color(0xFF1976D2).copy(alpha = 0.25f))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = "Connected",
                                                                fontSize = 9.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFF90CAF9)
                                                            )
                                                        }
                                                    }
                                                }

                                                Text(
                                                    text = "${device.type} · ${device.address}",
                                                    fontSize = 11.sp,
                                                    color = TellyOnSurfaceVariant
                                                )
                                            }

                                            // Actions: Connect or Disconnect
                                            if (device.isConnected) {
                                                if (device.type == "Audio") {
                                                    Surface(
                                                        onClick = {
                                                            scope.launch {
                                                                adbManager.disconnectBluetoothAudio()
                                                                Toast.makeText(context, "Audio disconnected silently", Toast.LENGTH_SHORT).show()
                                                                refreshData()
                                                            }
                                                        },
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = TellySurfaceContainer,
                                                        border = BorderStroke(1.dp, TellyOutlineSubtle)
                                                    ) {
                                                        Text(
                                                            text = "Disconnect",
                                                            fontSize = 10.5.sp,
                                                            color = TellyError,
                                                            fontWeight = FontWeight.SemiBold,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            } else {
                                                Surface(
                                                    onClick = {
                                                        scope.launch {
                                                            Toast.makeText(context, "Connecting to ${device.name}...", Toast.LENGTH_SHORT).show()
                                                            adbManager.connectBluetoothDevice(device.address, device.name)
                                                            delay(1500)
                                                            refreshData()
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = Color(0xFF1976D2)
                                                ) {
                                                    Text(
                                                        text = "Connect",
                                                        fontSize = 10.5.sp,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }

                                            Spacer(Modifier.width(4.dp))

                                            // Quick accessory settings icon button on TV (rename / unpair)
                                            IconButton(
                                                onClick = {
                                                    scope.launch {
                                                        adbManager.openBluetoothAccessoryManager(device.address, device.name)
                                                        Toast.makeText(context, "Opening ${device.name} settings on TV...", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Settings,
                                                    contentDescription = "Manage on TV",
                                                    tint = TellyOnSurfaceVariant,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Bottom Actions: [ ⟳ Refresh ]  [ + Pair New ]  [ TV Settings ]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { refreshData() },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, TellyOutlineSubtle),
                                modifier = Modifier.weight(0.9f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("Refresh", fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        adbManager.openPairNewBluetoothDevice()
                                        Toast.makeText(context, "Opening TV pairing assistant...", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                                modifier = Modifier.weight(1.1f)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("Pair New", fontSize = 11.sp, color = Color.White)
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        adbManager.openRemotesAndAccessoriesSettings()
                                        Toast.makeText(context, "Opening TV Remotes & Accessories...", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, TellyOutlineSubtle),
                                modifier = Modifier.weight(1.1f)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("TV Remotes", fontSize = 11.sp)
                            }
                        }
                    }

                    // TAB 1: WI-FI VIEW
                    if (selectedTab == 1) {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Active Wi-Fi Connection Card
                            item {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = TellySurfaceContainerHigh,
                                    border = BorderStroke(1.dp, if (wifiDetails.isConnected) TellyTealPrimary.copy(alpha = 0.5f) else TellyOutlineSubtle),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Wifi,
                                                    contentDescription = null,
                                                    tint = if (wifiDetails.isConnected) TellyTealPrimary else TellyOnSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = if (wifiDetails.isConnected) wifiDetails.currentSsid else "Wi-Fi Disconnected",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = TellyOnSurface
                                                )
                                            }

                                            if (wifiDetails.isConnected) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(TellySuccessContainer)
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    Text(
                                                        text = "Active",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = TellySuccess
                                                    )
                                                }
                                            }
                                        }

                                        if (wifiDetails.isConnected) {
                                            Spacer(Modifier.height(12.dp))
                                            HorizontalDivider(color = TellyOutlineSubtle.copy(alpha = 0.5f))
                                            Spacer(Modifier.height(10.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column {
                                                    Text("IP Address", fontSize = 10.5.sp, color = TellyOnSurfaceVariant)
                                                    Text(wifiDetails.ipAddress.ifEmpty { "192.168.1.xxx" }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TellyOnSurface)
                                                }
                                                Column {
                                                    Text("Signal", fontSize = 10.5.sp, color = TellyOnSurfaceVariant)
                                                    Text("${wifiDetails.rssi} dBm", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TellyTealPrimary)
                                                }
                                                Column {
                                                    Text("Link Speed", fontSize = 10.5.sp, color = TellyOnSurfaceVariant)
                                                    Text(if (wifiDetails.linkSpeedMbps > 0) "${wifiDetails.linkSpeedMbps} Mbps" else "High Speed", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TellyOnSurface)
                                                }
                                                Column {
                                                    Text("Band", fontSize = 10.5.sp, color = TellyOnSurfaceVariant)
                                                    Text(if (wifiDetails.frequencyMhz > 5000) "5 GHz" else "2.4 GHz", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TellyOnSurface)
                                                }
                                            }

                                            if (wifiDetails.ipv6Address.isNotEmpty()) {
                                                Spacer(Modifier.height(8.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                                                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f))
                                                    ) {
                                                        Text("IPv6", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                    }
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        wifiDetails.ipv6Address,
                                                        fontSize = 11.sp,
                                                        color = TellyOnSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Notice Banner about ADB Wi-Fi
                            item {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF332000),
                                    border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Switching to a different Wi-Fi network will change the TV's IP address and briefly disconnect ADB until the TV reconnects.",
                                            fontSize = 10.5.sp,
                                            color = Color(0xFFFFD54F)
                                        )
                                    }
                                }
                            }

                            // Saved Networks Section
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp, bottom = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "SAVED NETWORKS (${wifiDetails.savedNetworks.size})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TellyOnSurfaceVariant,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }

                            items(wifiDetails.savedNetworks) { net ->
                                val isCurrent = net.ssid == wifiDetails.currentSsid

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = TellySurfaceContainerHigh,
                                    border = BorderStroke(1.dp, if (isCurrent) TellyTealPrimary.copy(alpha = 0.4f) else TellyOutlineSubtle),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isCurrent) Icons.Default.Wifi else Icons.Default.WifiLock,
                                                contentDescription = null,
                                                tint = if (isCurrent) TellyTealPrimary else TellyOnSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = net.ssid,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 13.sp,
                                                    color = TellyOnSurface
                                                )
                                                Text(
                                                    text = "${net.security} · ID: ${net.networkId}",
                                                    fontSize = 10.5.sp,
                                                    color = TellyOnSurfaceVariant
                                                )
                                            }
                                        }

                                        if (!isCurrent) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    onClick = {
                                                        scope.launch {
                                                            Toast.makeText(context, "Connecting TV to ${net.ssid}...", Toast.LENGTH_SHORT).show()
                                                            adbManager.connectSavedWifi(net.networkId, net.ssid)
                                                            delay(2500)
                                                            refreshData()
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = TellyTealPrimary.copy(alpha = 0.2f),
                                                    border = BorderStroke(1.dp, TellyTealPrimary.copy(alpha = 0.5f))
                                                ) {
                                                    Text(
                                                        text = "Connect",
                                                        fontSize = 10.5.sp,
                                                        color = TellyTealPrimary,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                                    )
                                                }

                                                Spacer(Modifier.width(4.dp))

                                                IconButton(
                                                    onClick = {
                                                        scope.launch {
                                                            val ok = adbManager.forgetWifi(net.networkId)
                                                            Toast.makeText(context, if (ok) "Forgot ${net.ssid}" else "Failed to forget network", Toast.LENGTH_SHORT).show()
                                                            refreshData()
                                                        }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.DeleteOutline,
                                                        contentDescription = "Forget",
                                                        tint = TellyError,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Nearby Scan Results Section
                            if (scannedWifiList.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "NEARBY NETWORKS (${scannedWifiList.size})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TellyOnSurfaceVariant,
                                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                                    )
                                }

                                items(scannedWifiList) { scanned ->
                                    Surface(
                                        onClick = {
                                            connectWifiDialogNetwork = scanned.ssid
                                            connectWifiPassword = ""
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = TellySurfaceContainerHigh.copy(alpha = 0.6f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Wifi, contentDescription = null, tint = TellyOnSurfaceVariant, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text(scanned.ssid, fontSize = 12.sp, color = TellyOnSurface)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("${scanned.rssi} dBm", fontSize = 11.sp, color = TellyTealPrimary)
                                                Spacer(Modifier.width(8.dp))
                                                Text("Connect", fontSize = 10.sp, color = TellyTealPrimary, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Bottom Actions: [ Scan Nearby ]  [ TV Wi-Fi Setup ]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isScanningWifi = true
                                        Toast.makeText(context, "Scanning nearby Wi-Fi networks...", Toast.LENGTH_SHORT).show()
                                        scannedWifiList = adbManager.scanWifiNetworks()
                                        isScanningWifi = false
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TellyTealPrimary),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isScanningWifi) {
                                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.WifiFind, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(4.dp))
                                Text("Scan Nearby", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        adbManager.openWifiWizard()
                                        Toast.makeText(context, "Opening TV Wi-Fi Setup Wizard...", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, TellyOutlineSubtle),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("TV Wi-Fi Setup", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Connect to Wi-Fi Network with Password Dialog
    if (connectWifiDialogNetwork != null) {
        val targetSsid = connectWifiDialogNetwork!!
        AlertDialog(
            onDismissRequest = { connectWifiDialogNetwork = null },
            title = {
                Text("Connect to $targetSsid", fontWeight = FontWeight.Bold, color = TellyOnSurface, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter Wi-Fi password (or leave empty if it's an open network):",
                        fontSize = 12.sp,
                        color = TellyOnSurfaceVariant
                    )
                    OutlinedTextField(
                        value = connectWifiPassword,
                        onValueChange = { connectWifiPassword = it },
                        label = { Text("Password") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TellyTealPrimary,
                            unfocusedBorderColor = TellyOutlineSubtle,
                            focusedTextColor = TellyOnSurface,
                            unfocusedTextColor = TellyOnSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isConnectingWifi = true
                            Toast.makeText(context, "Connecting TV to $targetSsid...", Toast.LENGTH_SHORT).show()
                            adbManager.connectWifiNetwork(targetSsid, "wpa2", connectWifiPassword)
                            connectWifiDialogNetwork = null
                            delay(2500)
                            refreshData()
                            isConnectingWifi = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TellyTealPrimary)
                ) {
                    Text("Connect", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { connectWifiDialogNetwork = null }) {
                    Text("Cancel", color = TellyOnSurfaceVariant)
                }
            },
            containerColor = TellySurfaceContainerHigh,
            shape = RoundedCornerShape(18.dp)
        )
    }
}
