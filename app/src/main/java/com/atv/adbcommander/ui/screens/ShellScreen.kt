package com.atv.adbcommander.ui.screens

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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atv.adbcommander.adb.AdbManager
import com.atv.adbcommander.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ShellScreen(
    adbManager: AdbManager,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var commandInput by remember { mutableStateOf("") }
    var outputText by remember { mutableStateOf("root@android-tv:~$ ADB Wire Protocol ready.\nExecute direct shell commands or select telemetry shortcuts:\n") }
    var isExecuting by remember { mutableStateOf(false) }

    val outputScrollState = rememberScrollState()

    val runCmd = { cmd: String ->
        if (isConnected && cmd.isNotBlank()) {
            isExecuting = true
            outputText += "\n$ $cmd\n"
            coroutineScope.launch {
                try {
                    val result = adbManager.runShell(cmd)
                    outputText += result.ifEmpty { "(Execution finished: 0)\n" }
                } catch (e: Exception) {
                    outputText += "Execution fault: ${e.message}\n"
                } finally {
                    isExecuting = false
                    outputScrollState.animateScrollTo(outputScrollState.maxValue)
                }
            }
        }
    }

    val shortcuts = listOf(
        "getprop ro.product.model",
        "dumpsys battery",
        "uptime",
        "netstat -tuln",
        "wm size",
        "wm density",
        "reboot"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        // Shortcuts row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            shortcuts.forEach { shortcut ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkCard,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.clickable {
                        commandInput = shortcut
                        runCmd(shortcut)
                    }
                ) {
                    Text(
                        text = shortcut,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Futuristic Terminal Output Box
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF060910),
            border = BorderStroke(1.dp, BorderGlow.copy(alpha = 0.5f))
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(outputScrollState)
                ) {
                    Text(
                        text = outputText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFFE2E8F0),
                        lineHeight = 17.sp
                    )
                }

                // Clear button
                IconButton(
                    onClick = { outputText = "" },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Command Input Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                placeholder = { Text("Enter adb shell command...", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    val cmd = commandInput.trim()
                    commandInput = ""
                    runCmd(cmd)
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = BorderSubtle,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    val cmd = commandInput.trim()
                    commandInput = ""
                    runCmd(cmd)
                },
                enabled = isConnected && !isExecuting && commandInput.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                modifier = Modifier.height(52.dp)
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Run", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
