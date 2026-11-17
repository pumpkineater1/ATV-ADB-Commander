package com.atv.adbcommander.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.atv.adbcommander.model.PhoneInstalledApp
import com.atv.adbcommander.model.TransferProgress
import com.atv.adbcommander.ui.theme.*
import com.atv.adbcommander.util.PhoneAppHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTransferScreen(
    adbManager: AdbManager,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Sideload & Push, 1 = Phone Installed Apps

    // File Push State
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var selectedFileSize by remember { mutableStateOf(0L) }
    var targetFolder by remember { mutableStateOf("/sdcard/Download") }

    // APK Install State
    var selectedApkUri by remember { mutableStateOf<Uri?>(null) }
    var selectedApkName by remember { mutableStateOf("") }
    var selectedApkSize by remember { mutableStateOf(0L) }

    // Active Transfer State
    var transferState by remember { mutableStateOf<TransferProgress?>(null) }
    var isTransferring by remember { mutableStateOf(false) }

    // Phone Apps State
    var phoneApps by remember { mutableStateOf<List<PhoneInstalledApp>>(emptyList()) }
    var phoneAppSearch by remember { mutableStateOf("") }
    var isLoadingPhoneApps by remember { mutableStateOf(false) }

    // File Picker Launcher
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            val meta = getFileMetadata(context, uri)
            selectedFileName = meta.first
            selectedFileSize = meta.second
        }
    }

    // APK Picker Launcher
    val apkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedApkUri = uri
            val meta = getFileMetadata(context, uri)
            selectedApkName = meta.first
            selectedApkSize = meta.second
        }
    }

    LaunchedEffect(activeTab) {
        if (activeTab == 1 && phoneApps.isEmpty()) {
            isLoadingPhoneApps = true
            phoneApps = PhoneAppHelper.getInstalledUserApps(context)
            isLoadingPhoneApps = false
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
                        text = "Connect to TV to send files or sideload APKs wirelessly.",
                        color = AccentAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Active Transfer / Installation Progress Card
        AnimatedVisibility(visible = transferState != null) {
            val progress = transferState
            if (progress != null) {
                val cardBorder = if (progress.error != null) CyberRose
                else if (progress.isDone) AccentGreen
                else CyberCyan

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    color = DarkCard,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, cardBorder.copy(alpha = 0.6f)),
                    shadowElevation = 8.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (progress.isInstalling) "INSTALLING ON TV OS..."
                                else if (progress.isDone) "OPERATION COMPLETED"
                                else if (progress.error != null) "TRANSFER ERROR"
                                else "STREAMING FILE TO TV...",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                letterSpacing = 1.sp,
                                color = if (progress.error != null) CyberRose
                                else if (progress.isDone) AccentGreen
                                else CyberCyan
                            )

                            if (progress.isDone || progress.error != null) {
                                IconButton(
                                    onClick = { transferState = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = progress.fileName,
                            fontSize = 13.sp,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        if (!progress.isDone && progress.error == null) {
                            LinearProgressIndicator(
                                progress = { progress.progressFraction.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = CyberCyan,
                                trackColor = DarkSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${formatSize(progress.bytesTransferred)} / ${formatSize(progress.totalBytes)}",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            if (progress.percentage > 0) {
                                Text(
                                    text = "${progress.percentage}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberCyan
                                )
                            }
                        }

                        if (progress.resultMessage != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = progress.resultMessage,
                                fontSize = 11.sp,
                                color = if (progress.error != null) CyberRose else AccentGreen
                            )
                        }

                        if (progress.error != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = progress.error,
                                fontSize = 11.sp,
                                color = CyberRose
                            )
                        }
                    }
                }
            }
        }

        // Futuristic Feature Mode Tabs: Local Files/APKs vs Phone Apps
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (activeTab == 0) DarkSurfaceVariant else DarkCard,
                border = BorderStroke(1.dp, if (activeTab == 0) CyberCyan.copy(alpha = 0.5f) else BorderSubtle),
                modifier = Modifier
                    .weight(1f)
                    .clickable { activeTab = 0 }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Push",
                        tint = if (activeTab == 0) CyberCyan else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Push & Sideload",
                        fontSize = 11.sp,
                        fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Medium,
                        color = if (activeTab == 0) TextPrimary else TextSecondary
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (activeTab == 1) DarkSurfaceVariant else DarkCard,
                border = BorderStroke(1.dp, if (activeTab == 1) CyberCyan.copy(alpha = 0.5f) else BorderSubtle),
                modifier = Modifier
                    .weight(1f)
                    .clickable { activeTab = 1 }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = "Phone Apps",
                        tint = if (activeTab == 1) CyberCyan else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Phone Apps (${phoneApps.size})",
                        fontSize = 11.sp,
                        fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium,
                        color = if (activeTab == 1) TextPrimary else TextSecondary
                    )
                }
            }
        }

        // TAB 0: Local File Push & Sideload APK Picker
        if (activeTab == 0) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section 1: Install APK to TV
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = DarkCard,
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AccentGreen.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Android,
                                    contentDescription = "Install App",
                                    tint = AccentGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "SIDELOAD APK TO TV",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Direct wireless package installer via ADB",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = { apkPicker.launch("*/*") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen)
                        ) {
                            Icon(imageVector = Icons.Default.FileOpen, contentDescription = "Pick APK", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedApkName.isEmpty()) "Select APK Package" else "Change: $selectedApkName",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (selectedApkName.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Package Size: ${formatSize(selectedApkSize)}",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    val uri = selectedApkUri ?: return@Button
                                    isTransferring = true
                                    coroutineScope.launch {
                                        try {
                                            val stream = context.contentResolver.openInputStream(uri)
                                            if (stream != null) {
                                                adbManager.installApk(
                                                    inputStream = stream,
                                                    totalBytes = selectedApkSize,
                                                    fileName = selectedApkName,
                                                    appName = selectedApkName.removeSuffix(".apk").replace("_", " ")
                                                ).collectLatest { prog ->
                                                    transferState = prog
                                                }
                                            }
                                        } finally {
                                            isTransferring = false
                                        }
                                    }
                                },
                                enabled = isConnected && !isTransferring,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                            ) {
                                Icon(imageVector = Icons.Default.Download, contentDescription = "Install", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Install APK on TV", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Section 2: Send Any File to TV
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = DarkCard,
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CyberCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DriveFolderUpload,
                                    contentDescription = "Send File",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "PUSH FILES TO TV",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Send movies, videos, music, subs, or documents",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = { filePicker.launch("*/*") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan)
                        ) {
                            Icon(imageVector = Icons.Default.AttachFile, contentDescription = "Pick File", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedFileName.isEmpty()) "Select Any File" else "Change: $selectedFileName",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (selectedFileName.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "File Size: ${formatSize(selectedFileSize)}",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = targetFolder,
                                onValueChange = { targetFolder = it },
                                label = { Text("TV TARGET DIRECTORY", fontSize = 10.sp, letterSpacing = 1.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyberCyan,
                                    unfocusedBorderColor = BorderSubtle,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedContainerColor = DarkSurface,
                                    unfocusedContainerColor = DarkSurface
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("/sdcard/Download", "/sdcard/Movies", "/sdcard/Pictures").forEach { dir ->
                                    val isCur = targetFolder == dir
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isCur) DarkSurfaceVariant else DarkSurface,
                                        border = BorderStroke(1.dp, if (isCur) CyberCyan.copy(alpha = 0.5f) else BorderSubtle),
                                        modifier = Modifier.clickable { targetFolder = dir }
                                    ) {
                                        Text(
                                            text = dir.substringAfterLast("/"),
                                            fontSize = 11.sp,
                                            fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCur) CyberCyan else TextSecondary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    val uri = selectedFileUri ?: return@Button
                                    val remotePath = "$targetFolder/$selectedFileName"
                                    isTransferring = true
                                    coroutineScope.launch {
                                        try {
                                            val stream = context.contentResolver.openInputStream(uri)
                                            if (stream != null) {
                                                adbManager.pushFile(stream, remotePath, selectedFileSize, selectedFileName)
                                                    .collectLatest { prog ->
                                                        transferState = prog
                                                    }
                                            }
                                        } finally {
                                            isTransferring = false
                                        }
                                    }
                                },
                                enabled = isConnected && !isTransferring,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Send File to TV Storage", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // TAB 1: Direct Phone App Sideloading
        if (activeTab == 1) {
            Column(modifier = Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = phoneAppSearch,
                    onValueChange = { phoneAppSearch = it },
                    placeholder = { Text("Search apps on your phone...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = CyberCyan, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
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

                Spacer(modifier = Modifier.height(10.dp))

                if (isLoadingPhoneApps) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = CyberCyan)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Scanning phone apps...", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                } else {
                    val filteredApps = phoneApps.filter {
                        phoneAppSearch.isEmpty() ||
                                it.appName.contains(phoneAppSearch, ignoreCase = true) ||
                                it.packageName.contains(phoneAppSearch, ignoreCase = true)
                    }

                    if (filteredApps.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No matching phone apps found", color = TextSecondary, fontSize = 12.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = DarkCard,
                                    border = BorderStroke(1.dp, BorderSubtle)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = app.appName,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = "${app.packageName} • ${formatSize(app.sizeBytes)}",
                                                fontSize = 10.sp,
                                                color = TextSecondary,
                                                maxLines = 1
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                isTransferring = true
                                                coroutineScope.launch {
                                                    try {
                                                        val file = File(app.apkPath)
                                                        if (file.exists()) {
                                                            val stream = FileInputStream(file)
                                                            adbManager.installApk(
                                                                inputStream = stream,
                                                                totalBytes = app.sizeBytes,
                                                                fileName = "${app.appName}.apk",
                                                                packageName = app.packageName,
                                                                appName = app.appName
                                                            ).collectLatest { prog ->
                                                                transferState = prog
                                                            }
                                                        }
                                                    } finally {
                                                        isTransferring = false
                                                    }
                                                }
                                            },
                                            enabled = isConnected && !isTransferring,
                                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            val isThisAppInstalling = isTransferring && transferState?.packageName == app.packageName
                                            if (isThisAppInstalling) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    CircularProgressIndicator(
                                                        progress = { (transferState?.progressFraction ?: 0f).coerceIn(0f, 1f) },
                                                        modifier = Modifier.size(13.dp),
                                                        strokeWidth = 2.dp,
                                                        color = Color.Black
                                                    )
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Text("${transferState?.percentage ?: 0}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                                }
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.CloudUpload,
                                                    contentDescription = "Sideload",
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Sideload", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
    }
}

private fun getFileMetadata(context: Context, uri: Uri): Pair<String, Long> {
    var name = "unknown"
    var size = 0L
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) name = cursor.getString(nameIndex) ?: "file"
                if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
            }
        }
    } catch (e: Exception) {
        name = uri.lastPathSegment ?: "file"
    }
    return Pair(name, size)
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}
