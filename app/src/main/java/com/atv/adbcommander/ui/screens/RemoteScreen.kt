package com.atv.adbcommander.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atv.adbcommander.adb.AdbManager
import com.atv.adbcommander.model.ConnectionState
import com.atv.adbcommander.ui.components.TellyTvCookie
import com.atv.adbcommander.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    adbManager: AdbManager,
    isConnected: Boolean,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    var textInput by remember { mutableStateOf("") }
    var showKeyboardBar by remember { mutableStateOf(false) }
    var showUrlCasterDialog by remember { mutableStateOf(false) }
    var deviceModel by remember { mutableStateOf("Android TV") }
    var deviceBrand by remember { mutableStateOf("Google TV") }

    val performHaptic = {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(25)
            }
        } catch (ignored: Exception) {}
    }

    fun sendKey(keyCode: Int) {
        performHaptic()
        coroutineScope.launch(Dispatchers.IO) {
            adbManager.sendKeyEvent(keyCode)
        }
    }

    LaunchedEffect(isConnected) {
        if (isConnected) {
            val dev = adbManager.getSavedDevices().firstOrNull()
            if (dev != null && dev.model.isNotEmpty()) {
                deviceModel = dev.model
                deviceBrand = dev.name.substringBefore(" ")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TellyDarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // ==========================================
        // 1. Top Header (Exact match to remote.png)
        // ==========================================
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag Handle Bar (pill at top)
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TellySurfaceVariant)
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TellyTvCookie(
                        size = 44.dp,
                        icon = Icons.Default.Tv,
                        tint = TellyTealGlow,
                        containerColor = TellyTealDark
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = deviceModel,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TellyOnSurface
                        )
                        Text(
                            text = if (isConnected) "$deviceBrand · Running fine" else "Offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isConnected) TellyTealPrimary else TellyError
                        )
                    }
                }

                if (onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TellyOnSurface
                        )
                    }
                } else {
                    IconButton(onClick = { sendKey(26) /* Power */ }) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Power",
                            tint = TellyError
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ==========================================
        // 2. Center Sleek D-Pad (Exact match to remote.png)
        // ==========================================
        Box(
            modifier = Modifier
                .size(260.dp)
                .clip(CircleShape)
                .background(TellySurfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            // Directional Arrow: UP (19)
            IconButton(
                onClick = { sendKey(19) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .size(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Up",
                    tint = TellyOnSurface,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Directional Arrow: DOWN (20)
            IconButton(
                onClick = { sendKey(20) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .size(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Down",
                    tint = TellyOnSurface,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Directional Arrow: LEFT (21)
            IconButton(
                onClick = { sendKey(21) },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .size(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowLeft,
                    contentDescription = "Left",
                    tint = TellyOnSurface,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Directional Arrow: RIGHT (22)
            IconButton(
                onClick = { sendKey(22) },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .size(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = "Right",
                    tint = TellyOnSurface,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Center OK / SELECT Button (23) - Solid Teal Disc
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(TellyTealDark)
                    .clickable { sendKey(23) },
                contentAlignment = Alignment.Center
            ) {
                // Subtle glowing ring or center dot
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(TellyTealGlow.copy(alpha = 0.4f))
                )
            }
        }

        Spacer(Modifier.height(30.dp))

        // ==========================================
        // 3. Navigation Row (Back, Home, Menu) (Exact match)
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // BACK Button (4)
            RemoteCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = { sendKey(4) }
            )

            // HOME Button (3) - slightly larger
            RemoteCircleButton(
                icon = Icons.Default.Home,
                contentDescription = "Home",
                size = 64.dp,
                onClick = { sendKey(3) }
            )

            // MENU Button (82)
            RemoteCircleButton(
                icon = Icons.Default.Menu,
                contentDescription = "Menu",
                onClick = { sendKey(82) }
            )
        }

        Spacer(Modifier.height(20.dp))

        // ==========================================
        // 4. Volume & Keyboard Row (Exact match to remote.png)
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // MUTE Button (164)
            RemoteCircleButton(
                icon = Icons.Default.VolumeMute,
                contentDescription = "Mute",
                onClick = { sendKey(164) }
            )

            // Volume Connected Pill (Vol - | Vol +)
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = TellySurfaceContainerHigh,
                modifier = Modifier.height(56.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    IconButton(
                        onClick = { sendKey(25) /* Volume Down */ },
                        modifier = Modifier.size(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeDown,
                            contentDescription = "Volume Down",
                            tint = TellyOnSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(TellyOutlineSubtle)
                    )

                    IconButton(
                        onClick = { sendKey(24) /* Volume Up */ },
                        modifier = Modifier.size(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Volume Up",
                            tint = TellyOnSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Keyboard Typing Toggle
            RemoteCircleButton(
                icon = Icons.Default.Keyboard,
                contentDescription = "Keyboard",
                containerColor = if (showKeyboardBar) TellyTealDark else TellySurfaceContainerHigh,
                iconTint = if (showKeyboardBar) TellyTealGlow else TellyOnSurface,
                onClick = { showKeyboardBar = !showKeyboardBar }
            )

            // Cast / Open Media & Web URL on TV
            RemoteCircleButton(
                icon = Icons.Default.Cast,
                contentDescription = "Cast URL",
                containerColor = if (showUrlCasterDialog) TellyTealDark else TellySurfaceContainerHigh,
                iconTint = if (showUrlCasterDialog) TellyTealGlow else TellyOnSurface,
                onClick = { showUrlCasterDialog = true }
            )
        }

        // ==========================================
        // 5. Expandable Phone Keyboard Typing Input Bar
        // ==========================================
        AnimatedVisibility(
            visible = showKeyboardBar,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Type text to send to TV...", color = TellyOnSurfaceVariant) },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (textInput.isNotEmpty()) {
                                    val textToSend = textInput
                                    coroutineScope.launch {
                                        val ok = adbManager.sendTextInput(textToSend)
                                        if (ok) {
                                            textInput = ""
                                            Toast.makeText(context, "Sent to TV", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = TellyTealPrimary
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (textInput.isNotEmpty()) {
                                val textToSend = textInput
                                coroutineScope.launch {
                                    val ok = adbManager.sendTextInput(textToSend)
                                    if (ok) {
                                        textInput = ""
                                        Toast.makeText(context, "Sent to TV", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    ),
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = TellySurfaceContainer,
                        unfocusedContainerColor = TellySurfaceContainer,
                        focusedBorderColor = TellyTealPrimary,
                        unfocusedBorderColor = TellyOutlineSubtle,
                        focusedTextColor = TellyOnSurface,
                        unfocusedTextColor = TellyOnSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ==========================================
        // 6. Streaming Shortcuts Row (Exact match to remote.png)
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StreamingAppPill("Play Store", Color(0xFF00E676), Color.Black) {
                coroutineScope.launch {
                    val ok = adbManager.openPlayStoreOnTv()
                    Toast.makeText(context, if (ok) "Opening TV Play Store..." else "Failed to open Play Store", Toast.LENGTH_SHORT).show()
                }
            }
            StreamingAppPill("YouTube", Color(0xFFFF0000), Color.White) {
                coroutineScope.launch { adbManager.launchApp("com.google.android.youtube.tv") }
            }
            StreamingAppPill("NETFLIX", Color(0xFFE50914), Color.White) {
                coroutineScope.launch { adbManager.launchApp("com.netflix.ninja") }
            }
            StreamingAppPill("Hotstar", Color(0xFF0C2053), Color(0xFF13E782)) {
                coroutineScope.launch { adbManager.launchApp("in.startv.hotstar") }
            }
            StreamingAppPill("Prime", Color(0xFF00A8E1), Color.White) {
                coroutineScope.launch { adbManager.launchApp("com.amazon.amazonvideo.livingroom") }
            }
            StreamingAppPill("Jellyfin", Color(0xFF9047FF), Color.White) {
                coroutineScope.launch { adbManager.launchApp("org.jellyfin.androidtv") }
            }
            StreamingAppPill("SmartTube", Color(0xFF0F766E), Color.White) {
                coroutineScope.launch { adbManager.launchApp("com.teamsmart.videomanager.tv") }
            }
            StreamingAppPill("Spotify", Color(0xFF1DB954), Color.Black) {
                coroutineScope.launch { adbManager.launchApp("com.spotify.tv.android") }
            }
            StreamingAppPill("Kodi", Color(0xFF17B2E7), Color.White) {
                coroutineScope.launch { adbManager.launchApp("org.xbmc.kodi") }
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showUrlCasterDialog) {
        com.atv.adbcommander.ui.components.UniversalUrlCastDialog(
            adbManager = adbManager,
            onDismiss = { showUrlCasterDialog = false }
        )
    }
}

@Composable
private fun RemoteCircleButton(
    icon: ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp = 56.dp,
    containerColor: Color = TellySurfaceContainerHigh,
    iconTint: Color = TellyOnSurface,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(size * 0.44f)
        )
    }
}

@Composable
private fun StreamingAppPill(
    name: String,
    accentColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = TellySurfaceContainerHigh,
        border = BorderStroke(1.dp, TellyOutlineSubtle),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
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
