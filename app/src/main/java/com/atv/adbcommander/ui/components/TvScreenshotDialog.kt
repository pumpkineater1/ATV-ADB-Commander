package com.atv.adbcommander.ui.components

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atv.adbcommander.adb.AdbManager
import com.atv.adbcommander.ui.theme.*
import kotlinx.coroutines.launch
import java.io.OutputStream

@Composable
fun TvScreenshotDialog(
    adbManager: AdbManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var screenshotBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun capture() {
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            try {
                val bmp = adbManager.takeScreenshot()
                if (bmp != null) {
                    screenshotBitmap = bmp
                } else {
                    errorMessage = "Failed to capture TV screen frame"
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Capture error"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        capture()
    }

    fun saveToGallery(bmp: Bitmap) {
        try {
            val filename = "ATV_Screenshot_${System.currentTimeMillis()}.png"
            var fos: OutputStream? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ATVCommander")
                }
                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    fos = resolver.openOutputStream(imageUri)
                }
            } else {
                @Suppress("DEPRECATION")
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString()
                val image = java.io.File(imagesDir, filename)
                fos = java.io.FileOutputStream(image)
            }

            fos?.use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                Toast.makeText(context, "Saved to Pictures/ATVCommander", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
                            imageVector = Icons.Default.Screenshot,
                            contentDescription = "Screenshot",
                            tint = CyberCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "LIVE TV SCREENSHOT",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 1.sp,
                            color = TextPrimary
                        )
                        Text(
                            "DIRECT FRAMEBUFFER CAPTURE",
                            fontSize = 9.sp,
                            color = TextMuted,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                IconButton(
                    onClick = { capture() },
                    enabled = !isLoading,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isLoading) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = DarkSurfaceVariant,
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = CyberCyan)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Capturing framebuffer via ADB...", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                    }
                } else if (errorMessage != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = DarkSurfaceVariant,
                        border = BorderStroke(1.dp, CyberRose.copy(alpha = 0.5f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(errorMessage ?: "Error", color = CyberRose, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (screenshotBitmap != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, BorderGlow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Image(
                            bitmap = screenshotBitmap!!.asImageBitmap(),
                            contentDescription = "TV Screen",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (screenshotBitmap != null) {
                    Button(
                        onClick = { saveToGallery(screenshotBitmap!!) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.SaveAlt, contentDescription = "Save", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save Image", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                TextButton(onClick = onDismiss) {
                    Text("Close", color = TextSecondary)
                }
            }
        },
        containerColor = DarkSurface,
        titleContentColor = TextPrimary
    )
}
