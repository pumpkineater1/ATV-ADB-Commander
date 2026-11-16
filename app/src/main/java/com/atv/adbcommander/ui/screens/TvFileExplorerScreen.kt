package com.atv.adbcommander.ui.screens

import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atv.adbcommander.adb.AdbManager
import com.atv.adbcommander.model.TvFileItem
import com.atv.adbcommander.model.TvStorageMount
import com.atv.adbcommander.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvFileExplorerScreen(
    adbManager: AdbManager,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentPath by remember { mutableStateOf("/sdcard") }
    var fileList by remember { mutableStateOf<List<TvFileItem>>(emptyList()) }
    var storageMounts by remember { mutableStateOf<List<TvStorageMount>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf(0) }
    var uploadFileName by remember { mutableStateOf("") }

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var itemToDelete by remember { mutableStateOf<TvFileItem?>(null) }
    var downloadingItem by remember { mutableStateOf<TvFileItem?>(null) }

    val handlePlayOnTv: (TvFileItem) -> Unit = { item ->
        if (isConnected) {
            coroutineScope.launch {
                val (ok, msg) = adbManager.openFileOnTv(item.path)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Connect to TV first", Toast.LENGTH_SHORT).show()
        }
    }

    val loadDirectory = { path: String ->
        if (isConnected) {
            coroutineScope.launch {
                isLoading = true
                try {
                    currentPath = path
                    fileList = adbManager.listFiles(path)
                } finally {
                    isLoading = false
                }
            }
        }
    }

    val refreshMountsAndDirectory = {
        if (isConnected) {
            coroutineScope.launch {
                try {
                    storageMounts = adbManager.fetchStorageMounts()
                } catch (ignored: Exception) {}
                loadDirectory(currentPath)
            }
        }
    }

    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val stream = context.contentResolver.openInputStream(uri)
                    if (stream != null) {
                        var size = 0L
                        var name = "uploaded_file"
                        val cursor = context.contentResolver.query(uri, null, null, null, null)
                        cursor?.use {
                            if (it.moveToFirst()) {
                                val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                val sizeIdx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                                if (nameIdx >= 0) name = it.getString(nameIdx)
                                if (sizeIdx >= 0) size = it.getLong(sizeIdx)
                            }
                        }

                        uploadFileName = name
                        isUploading = true
                        uploadProgress = 0
                        val targetPath = if (currentPath == "/") "/$name" else if (currentPath.endsWith("/")) "$currentPath$name" else "$currentPath/$name"

                        adbManager.pushFile(stream, targetPath, size, name).collect { prog ->
                            uploadProgress = prog.percentage
                            if (prog.isDone) {
                                isUploading = false
                                Toast.makeText(context, "Transferred $name to $currentPath!", Toast.LENGTH_LONG).show()
                                loadDirectory(currentPath)
                            } else if (prog.error != null) {
                                isUploading = false
                                Toast.makeText(context, "Upload failed: ${prog.error}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    isUploading = false
                    Toast.makeText(context, "Upload error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(isConnected) {
        if (isConnected) {
            refreshMountsAndDirectory()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 6.dp)
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
                            text = "Connect to TV to browse filesystem, attached mounts, and send files.",
                            color = AccentAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Attached Mounts & Storage Drives Selector (USB & Internal)
            if (storageMounts.isNotEmpty()) {
                Text(
                    text = "STORAGE MOUNTS & ATTACHED DRIVES",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TellyTealPrimary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    storageMounts.forEach { mount ->
                        val isSelected = currentPath.startsWith(mount.path)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) TellySurfaceContainerHigh else TellySurfaceContainer,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) TellyTealPrimary else TellyOutlineSubtle
                            ),
                            modifier = Modifier.clickable { loadDirectory(mount.path) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (mount.isExternal) Icons.Default.Usb else Icons.Default.Storage,
                                    contentDescription = mount.name,
                                    tint = if (mount.isExternal) TellyTealGlow else TellyOnSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = mount.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) TellyTealGlow else TellyOnSurface
                                    )
                                    if (mount.freeSpace.isNotEmpty()) {
                                        Text(
                                            text = "${mount.freeSpace} free of ${mount.totalSpace}",
                                            fontSize = 9.sp,
                                            color = TellyOnSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // Current Path Header Bar with Upload & New Folder Buttons
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = DarkCard,
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = {
                                if (currentPath != "/" && currentPath.contains("/")) {
                                    val parent = currentPath.substringBeforeLast("/").ifEmpty { "/" }
                                    loadDirectory(parent)
                                }
                            },
                            enabled = currentPath != "/" && currentPath.isNotEmpty() && !isLoading,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Up",
                                tint = if (currentPath != "/") CyberCyan else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = currentPath,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Send / Upload File Here Button
                        IconButton(
                            onClick = { uploadLauncher.launch(arrayOf("*/*")) },
                            enabled = isConnected && !isLoading && !isUploading,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(TellyTealDark)
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = "Send File Here",
                                tint = TellyTealGlow,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = { showNewFolderDialog = true },
                            enabled = isConnected && !isLoading,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceVariant)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = "New Folder",
                                tint = CyberCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = { refreshMountsAndDirectory() },
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
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Jump Subfolder Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Storage" to "/sdcard",
                    "Downloads" to "/sdcard/Download",
                    "Movies" to "/sdcard/Movies",
                    "Pictures" to "/sdcard/Pictures",
                    "Music" to "/sdcard/Music",
                    "Temp" to "/data/local/tmp"
                ).forEach { (label, path) ->
                    val isCur = currentPath == path
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCur) DarkSurfaceVariant else DarkCard,
                        border = BorderStroke(1.dp, if (isCur) CyberCyan.copy(alpha = 0.5f) else BorderSubtle),
                        modifier = Modifier.clickable { loadDirectory(path) }
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isCur) FontWeight.Bold else FontWeight.Medium,
                            color = if (isCur) CyberCyan else TextSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // File and Folder List
            if (fileList.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Directory is empty", color = TextSecondary, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(fileList, key = { it.path }) { item ->
                        TvFileRow(
                            item = item,
                            onClick = {
                                if (item.isDirectory) {
                                    loadDirectory(item.path)
                                } else {
                                    handlePlayOnTv(item)
                                }
                            },
                            onPlayOnTv = {
                                handlePlayOnTv(item)
                            },
                            onDownload = {
                                downloadingItem = item
                                coroutineScope.launch {
                                    try {
                                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                        val localFile = File(downloadsDir, item.name)
                                        val fos = FileOutputStream(localFile)
                                        val ok = adbManager.pullFile(item.path, fos)
                                        if (ok) {
                                            Toast.makeText(context, "Downloaded to ${localFile.name}", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        downloadingItem = null
                                    }
                                }
                            },
                            onDelete = {
                                itemToDelete = item
                            }
                        )
                    }
                }
            }
        }

        // Floating Action Button to Send File to Current Folder
        FloatingActionButton(
            onClick = { uploadLauncher.launch(arrayOf("*/*")) },
            containerColor = TellyTealPrimary,
            contentColor = Color.Black,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Upload,
                contentDescription = "Send File to Current Directory",
                modifier = Modifier.size(24.dp)
            )
        }
    }

    // Upload Progress Modal Dialog
    if (isUploading) {
        AlertDialog(
            onDismissRequest = { /* Disallow cancel mid-transfer */ },
            title = {
                Text(
                    text = "SENDING FILE TO TV",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TellyTealPrimary,
                    letterSpacing = 1.sp
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = uploadFileName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TellyOnSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Destination: $currentPath",
                        fontSize = 11.sp,
                        color = TellyOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { uploadProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = TellyTealPrimary,
                        trackColor = TellySurfaceContainerHigh
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "$uploadProgress%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TellyTealPrimary,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            },
            confirmButton = {},
            containerColor = TellySurfaceContainerHigh
        )
    }

    // New Folder Dialog
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = {
                Text(
                    "CREATE NEW DIRECTORY",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp,
                    color = CyberCyan
                )
            },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    placeholder = { Text("Folder name...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = BorderSubtle
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            val path = "$currentPath/${newFolderName.trim()}"
                            coroutineScope.launch {
                                val res = adbManager.createDirectory(path)
                                Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                                loadDirectory(currentPath)
                                newFolderName = ""
                                showNewFolderDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary
        )
    }

    // Delete Confirmation Dialog
    if (itemToDelete != null) {
        val item = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = {
                Text(
                    "DELETE ${if (item.isDirectory) "DIRECTORY" else "FILE"}?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp,
                    color = CyberRose
                )
            },
            text = {
                Text("Are you sure you want to delete '${item.name}' from TV filesystem?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val res = adbManager.deleteFileOrFolder(item.path)
                            Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                            loadDirectory(currentPath)
                            itemToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberRose),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }
}

@Composable
fun TvFileRow(
    item: TvFileItem,
    onClick: () -> Unit,
    onPlayOnTv: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                val icon = getFileIcon(item)
                val lower = item.name.lowercase()
                val iconTint = if (item.isDirectory) CyberCyan
                else if (lower.endsWith(".apk")) AccentGreen
                else if (listOf(".mp4", ".mkv", ".avi", ".mov", ".webm", ".ts", ".wmv", ".flv", ".m4v").any { lower.endsWith(it) }) AccentAmber
                else if (listOf(".mp3", ".flac", ".wav", ".m4a", ".aac", ".ogg", ".opus", ".wma").any { lower.endsWith(it) }) TellyTealGlow
                else if (listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp").any { lower.endsWith(it) }) Color(0xFF64B5F6)
                else TextSecondary

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = item.name,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = item.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = if (item.isDirectory) "Directory • ${item.date}" else "${item.size} • ${item.date}",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!item.isDirectory) {
                    val lower = item.name.lowercase()
                    val playIcon = when {
                        lower.endsWith(".apk") -> Icons.Default.InstallMobile
                        listOf(".mp4", ".mkv", ".avi", ".mov", ".webm", ".ts", ".wmv", ".flv", ".m4v").any { lower.endsWith(it) } -> Icons.Default.PlayCircle
                        listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp").any { lower.endsWith(it) } -> Icons.Default.Visibility
                        else -> Icons.Default.PlayArrow
                    }
                    val playTint = when {
                        lower.endsWith(".apk") -> Color(0xFF00E676)
                        listOf(".mp4", ".mkv", ".avi", ".mov", ".webm", ".ts", ".wmv", ".flv", ".m4v").any { lower.endsWith(it) } -> Color(0xFFFFB300)
                        listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp").any { lower.endsWith(it) } -> Color(0xFF64B5F6)
                        else -> TellyTealGlow
                    }

                    // Direct One-Click Play on TV Button
                    IconButton(
                        onClick = onPlayOnTv,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = playIcon,
                            contentDescription = "Play on TV",
                            tint = playTint,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download to phone",
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = CyberRose.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun getFileIcon(item: TvFileItem): ImageVector {
    if (item.isDirectory) return Icons.Default.Folder
    val lower = item.name.lowercase()
    return when {
        lower.endsWith(".apk") -> Icons.Default.Android
        listOf(".mp4", ".mkv", ".avi", ".mov", ".webm", ".ts", ".wmv", ".flv", ".m4v").any { lower.endsWith(it) } -> Icons.Default.Movie
        listOf(".mp3", ".flac", ".wav", ".m4a", ".aac", ".ogg", ".opus", ".wma").any { lower.endsWith(it) } -> Icons.Default.MusicNote
        listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp").any { lower.endsWith(it) } -> Icons.Default.Image
        listOf(".zip", ".tar", ".gz", ".rar", ".7z").any { lower.endsWith(it) } -> Icons.Default.FolderZip
        else -> Icons.Default.InsertDriveFile
    }
}
