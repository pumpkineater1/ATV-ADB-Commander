package com.atv.adbcommander.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.atv.adbcommander.adb.AdbManager
import com.atv.adbcommander.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun UniversalUrlCastDialog(
    adbManager: AdbManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var urlInput by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf("auto") } // "auto", "youtube", "video", "music", "web"
    var isCasting by remember { mutableStateOf(false) }

    val modes = listOf(
        Triple("auto", "⚡ Auto", Icons.Default.AutoAwesome),
        Triple("youtube", "📺 YouTube", Icons.Default.SmartDisplay),
        Triple("video", "▶️ Video", Icons.Default.Movie),
        Triple("music", "🎵 Music", Icons.Default.MusicNote),
        Triple("web", "🌐 Webpage", Icons.Default.Language)
    )

    fun sendUrl() {
        val target = urlInput.trim()
        if (target.isEmpty()) {
            Toast.makeText(context, "Please enter or paste a URL first", Toast.LENGTH_SHORT).show()
            return
        }
        coroutineScope.launch {
            isCasting = true
            try {
                val res = adbManager.openUrlOnTv(target, selectedMode)
                Toast.makeText(context, res, Toast.LENGTH_LONG).show()
                onDismiss()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isCasting = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(26.dp),
            color = TellyDarkBackground,
            border = BorderStroke(1.dp, TellyOutlineSubtle)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = TellyTealDark,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Cast,
                                    contentDescription = null,
                                    tint = TellyTealGlow,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Cast / Open URL on TV",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TellyOnSurface
                            )
                            Text(
                                text = "Stream videos, music, YouTube, or web links",
                                style = MaterialTheme.typography.bodySmall,
                                color = TellyOnSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TellyOnSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Mode Selector Chips
                Text(
                    text = "Content Type",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TellyOnSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    modes.forEach { (mode, label, _) ->
                        val isSelected = selectedMode == mode
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedMode = mode },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = TellySurfaceContainer,
                                labelColor = TellyOnSurfaceVariant,
                                selectedContainerColor = TellyTealDark,
                                selectedLabelColor = TellyTealGlow
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = TellyOutlineSubtle,
                                selectedBorderColor = TellyTealPrimary
                            ),
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // URL Input Field
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = {
                        Text(
                            text = "https://... (e.g. video.mp4, music.mp3, youtube.com)",
                            color = TellyOnSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = TellySurfaceContainer,
                        unfocusedContainerColor = TellySurfaceContainer,
                        focusedBorderColor = TellyTealPrimary,
                        unfocusedBorderColor = TellyOutlineSubtle,
                        focusedTextColor = TellyOnSurface,
                        unfocusedTextColor = TellyOnSurface
                    ),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (urlInput.isNotEmpty()) {
                                IconButton(onClick = { urlInput = "" }, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = TellyOnSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text
                                    if (!clip.isNullOrBlank()) {
                                        urlInput = clip.trim()
                                    } else {
                                        Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = TellyTealPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                    },
                    singleLine = true
                )

                Spacer(Modifier.height(14.dp))

                // Quick Samples Row
                Text(
                    text = "Quick Test Links",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TellyOnSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = {
                            urlInput = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                            selectedMode = "video"
                        },
                        label = { Text("Big Buck Bunny (.mp4)", fontSize = 11.sp, color = TellyOnSurface) },
                        shape = RoundedCornerShape(12.dp),
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = TellySurfaceContainer)
                    )

                    SuggestionChip(
                        onClick = {
                            urlInput = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
                            selectedMode = "youtube"
                        },
                        label = { Text("YouTube / SmartTube", fontSize = 11.sp, color = TellyOnSurface) },
                        shape = RoundedCornerShape(12.dp),
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = TellySurfaceContainer)
                    )

                    SuggestionChip(
                        onClick = {
                            urlInput = "https://www.google.com"
                            selectedMode = "web"
                        },
                        label = { Text("Web Browser", fontSize = 11.sp, color = TellyOnSurface) },
                        shape = RoundedCornerShape(12.dp),
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = TellySurfaceContainer)
                    )
                }

                Spacer(Modifier.height(22.dp))

                // Bottom Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TellyOnSurfaceVariant)
                    }

                    Spacer(Modifier.width(10.dp))

                    Button(
                        onClick = { sendUrl() },
                        enabled = !isCasting && urlInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TellyTealDark,
                            contentColor = TellyTealGlow,
                            disabledContainerColor = TellySurfaceContainerHigh,
                            disabledContentColor = TellyOnSurfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        if (isCasting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = TellyTealGlow
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Sending to TV...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Play on TV", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
