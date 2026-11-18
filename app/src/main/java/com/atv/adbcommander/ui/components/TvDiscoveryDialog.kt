package com.atv.adbcommander.ui.components

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atv.adbcommander.adb.AdbManager
import com.atv.adbcommander.discovery.TvDiscoveryScanner
import com.atv.adbcommander.model.DiscoveredDevice
import com.atv.adbcommander.model.SavedTvDevice
import com.atv.adbcommander.ui.theme.*
import com.atv.adbcommander.util.NetworkUtils
import kotlinx.coroutines.launch

@Composable
fun TvDiscoveryDialog(
    onDeviceSelected: (String, Int) -> Unit,
    onDismiss: () -> Unit,
    adbManager: AdbManager? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scanner = remember { TvDiscoveryScanner(context) }
    val coroutineScope = rememberCoroutineScope()

    val discoveredDevices = remember { mutableStateListOf<DiscoveredDevice>() }
    var savedDevices by remember { mutableStateOf(adbManager?.getSavedDevices() ?: emptyList()) }
    var isScanning by remember { mutableStateOf(true) }

    // Direct Manual IP Connection Input (Supports IPv4 and IPv6)
    var manualIpInput by remember { mutableStateOf("") }
    var manualPortInput by remember { mutableStateOf("5555") }

    fun startScan() {
        discoveredDevices.clear()
        isScanning = true
        coroutineScope.launch {
            try {
                scanner.scanSubnet(port = 5555).collect { device ->
                    if (discoveredDevices.none { it.ip == device.ip }) {
                        discoveredDevices.add(device)
                    }
                }
            } finally {
                isScanning = false
            }
        }
    }

    LaunchedEffect(Unit) {
        startScan()
    }

    fun submitManualConnection() {
        val raw = manualIpInput.trim()
        if (raw.isEmpty()) {
            Toast.makeText(context, "Please enter an IPv4 or IPv6 address", Toast.LENGTH_SHORT).show()
            return
        }
        val defaultPort = manualPortInput.trim().toIntOrNull() ?: 5555
        val (parsedHost, parsedPort) = NetworkUtils.parseHostAndPort(raw, defaultPort)
        if (parsedHost.isEmpty()) {
            Toast.makeText(context, "Invalid address format", Toast.LENGTH_SHORT).show()
            return
        }
        onDeviceSelected(parsedHost, parsedPort)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
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
                            .background(CyberCyan.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiFind,
                            contentDescription = "Scan",
                            tint = CyberCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "CONNECT TO ANDROID TV",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp,
                            color = TextPrimary
                        )
                        Text(
                            "IPv4 & IPv6 RADAR • AUTO & MANUAL",
                            fontSize = 9.sp,
                            color = TextMuted,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = CyberCyan
                    )
                } else {
                    IconButton(
                        onClick = { startScan() },
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Rescan",
                            tint = CyberCyan,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section 1: Manual IPv4 / IPv6 Connection Card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = DarkSurfaceVariant,
                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Dns,
                                        contentDescription = null,
                                        tint = CyberCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Direct IP / Host Connect",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = TextPrimary
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = PrimaryBlue.copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.4f))
                                    ) {
                                        Text("IPv4", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = CyberCyan.copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f))
                                    ) {
                                        Text("IPv6", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = CyberCyan, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            // IP Address Input with Paste button
                            OutlinedTextField(
                                value = manualIpInput,
                                onValueChange = { manualIpInput = it },
                                placeholder = {
                                    Text("192.168.1.37 or 2401:... or fe80::...", fontSize = 11.sp, color = TextMuted)
                                },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            val clip = clipboardManager.getText()?.text?.trim()
                                            if (!clip.isNullOrEmpty()) {
                                                manualIpInput = clip
                                                Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = CyberCyan, modifier = Modifier.size(16.dp))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyberCyan,
                                    unfocusedBorderColor = BorderSubtle,
                                    focusedContainerColor = DarkSurface,
                                    unfocusedContainerColor = DarkSurface,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Ascii,
                                    imeAction = ImeAction.Next
                                )
                            )

                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = manualPortInput,
                                    onValueChange = { manualPortInput = it },
                                    label = { Text("Port", fontSize = 10.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.35f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CyberCyan,
                                        unfocusedBorderColor = BorderSubtle,
                                        focusedContainerColor = DarkSurface,
                                        unfocusedContainerColor = DarkSurface,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(onDone = { submitManualConnection() })
                                )

                                Button(
                                    onClick = { submitManualConnection() },
                                    modifier = Modifier
                                        .weight(0.65f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Connect", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Section 2: Discovered Radar Targets
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Discovered TV Targets (${discoveredDevices.size})",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        if (isScanning) {
                            Text("Scanning IPv4 + IPv6...", fontSize = 10.sp, color = CyberCyan)
                        }
                    }
                }

                if (discoveredDevices.isEmpty() && isScanning) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = DarkSurfaceVariant,
                            border = BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(color = CyberCyan, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text("Radar probing IPv6 neighbors & IPv4 subnet...", fontSize = 11.sp, color = TextMuted)
                                }
                            }
                        }
                    }
                } else if (discoveredDevices.isEmpty() && !isScanning) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = DarkSurfaceVariant,
                            border = BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Text(
                                "No targets responded on port 5555. Use the Direct IP field above to connect directly.",
                                fontSize = 11.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                } else {
                    items(discoveredDevices) { device ->
                        val isIpv6 = device.isIpv6
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDeviceSelected(device.ip, device.port)
                                    onDismiss()
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = DarkSurfaceVariant,
                            border = BorderStroke(1.dp, if (isIpv6) CyberCyan.copy(alpha = 0.5f) else BorderSubtle)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(if (isIpv6) CyberCyan.copy(alpha = 0.2f) else PrimaryBlue.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tv,
                                            contentDescription = "TV",
                                            tint = if (isIpv6) CyberCyan else PrimaryBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = device.name,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (isIpv6) CyberCyan.copy(alpha = 0.2f) else PrimaryBlue.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    if (isIpv6) "IPv6" else "IPv4",
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isIpv6) CyberCyan else PrimaryBlue,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = device.formattedAddress,
                                            fontSize = 10.5.sp,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        onDeviceSelected(device.ip, device.port)
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isIpv6) Color(0xFF00B0FF) else PrimaryBlue),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Connect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Section 3: Saved & Recent Devices
                if (savedDevices.isNotEmpty()) {
                    item {
                        Text(
                            "Recent & Saved Devices",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    items(savedDevices) { device ->
                        val isIpv6 = device.isIpv6
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDeviceSelected(device.ip, device.port)
                                    onDismiss()
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = DarkCard,
                            border = BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                device.name,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (isIpv6) CyberCyan.copy(alpha = 0.2f) else DarkSurfaceVariant
                                            ) {
                                                Text(
                                                    if (isIpv6) "IPv6" else "IPv4",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isIpv6) CyberCyan else TextSecondary,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            device.formattedAddress,
                                            fontSize = 10.5.sp,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Button(
                                        onClick = {
                                            onDeviceSelected(device.ip, device.port)
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("Link", fontSize = 11.sp, color = TextPrimary)
                                    }

                                    if (adbManager != null) {
                                        IconButton(
                                            onClick = {
                                                adbManager.removeSavedDevice(device.ip)
                                                savedDevices = adbManager.getSavedDevices()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove",
                                                tint = TextMuted,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        },
        containerColor = DarkSurface,
        titleContentColor = TextPrimary
    )
}
