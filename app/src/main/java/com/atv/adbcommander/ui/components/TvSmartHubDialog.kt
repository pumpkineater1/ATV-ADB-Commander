package com.atv.adbcommander.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.atv.adbcommander.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun TvSmartHubDialog(
    adbManager: AdbManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboard = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    var castUrlText by remember { mutableStateOf("") }
    var isBoosting by remember { mutableStateOf(false) }
    var boostResult by remember { mutableStateOf<String?>(null) }

    val sleepState by adbManager.sleepTimerState.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AccentPurple.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Smart Hub",
                        tint = AccentPurple,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "TV SMART HUB & UTILITIES",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp,
                        color = TextPrimary
                    )
                    Text(
                        "HIGH-TECH TV ACCELERATION & CONTROLS",
                        fontSize = 9.sp,
                        color = TextMuted,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Feature 1: 1-Tap TV Speed Booster
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = DarkSurfaceVariant,
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.RocketLaunch, contentDescription = "Boost", tint = AccentGreen, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("1-Tap TV RAM Booster", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary)
                            }

                            Button(
                                onClick = {
                                    isBoosting = true
                                    boostResult = null
                                    coroutineScope.launch {
                                        val res = adbManager.boostTvMemory()
                                        boostResult = "Freed ${res.first} MB • Now ${res.second} MB free"
                                        isBoosting = false
                                    }
                                },
                                enabled = !isBoosting,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                if (isBoosting) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                                } else {
                                    Text("Boost TV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (boostResult != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(boostResult!!, fontSize = 11.sp, color = AccentGreen, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Feature 2: Smart URL & Video Caster
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = DarkSurfaceVariant,
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Cast, contentDescription = "Cast", tint = CyberCyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open URL / Video on TV", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = castUrlText,
                            onValueChange = { castUrlText = it },
                            placeholder = { Text("https://youtube.com/watch?v=... or any web link", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                        if (clipText.isNotEmpty()) castUrlText = clipText
                                    }
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = CyberCyan, modifier = Modifier.size(18.dp))
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = BorderSubtle,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    if (castUrlText.isNotBlank()) {
                                        coroutineScope.launch {
                                            val res = adbManager.castUrlToTv(castUrlText)
                                            Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = castUrlText.isNotBlank(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Play on TV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Feature 3: Sleep Timer
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = DarkSurfaceVariant,
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bedtime, contentDescription = "Sleep", tint = AccentAmber, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("TV Sleep Timer", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary)
                            }

                            if (sleepState.isActive) {
                                val mins = sleepState.remainingSeconds / 60
                                val secs = sleepState.remainingSeconds % 60
                                Text(
                                    text = String.format("%02d:%02d", mins, secs),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentAmber
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (sleepState.isActive) {
                            Button(
                                onClick = { adbManager.cancelSleepTimer() },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberRose),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cancel Sleep Timer", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(15, 30, 45, 60).forEach { mins ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = DarkCard,
                                        border = BorderStroke(1.dp, BorderSubtle),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                adbManager.startSleepTimer(mins)
                                                Toast.makeText(context, "TV turns off in $mins min", Toast.LENGTH_SHORT).show()
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                                            Text("${mins}m", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentAmber)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Feature 4: Phone Clipboard to TV & Screensaver
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                            if (clipText.isNotEmpty()) {
                                coroutineScope.launch {
                                    adbManager.sendText(clipText)
                                    Toast.makeText(context, "Clipboard dispatched to TV", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Phone clipboard is empty", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentPasteGo, contentDescription = "Clip", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Send Clip", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                val res = adbManager.startScreensaver()
                                Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.NightlightRound, contentDescription = "Screensaver", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Screensaver", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = CyberCyan)
            }
        },
        containerColor = DarkSurface,
        titleContentColor = TextPrimary
    )
}
