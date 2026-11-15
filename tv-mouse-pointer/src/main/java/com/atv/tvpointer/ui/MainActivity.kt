package com.atv.tvpointer.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.atv.tvpointer.data.ActivationTrigger
import com.atv.tvpointer.data.PointerPreferences
import com.atv.tvpointer.service.TvPointerAccessibilityService
import com.atv.tvpointer.ui.theme.*

class MainActivity : ComponentActivity() {

    private lateinit var preferences: PointerPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = PointerPreferences(this)

        setContent {
            TVMousePointerTheme {
                MainScreen(
                    preferences = preferences,
                    onOpenAccessibility = { openAccessibilitySettings() },
                    onTogglePointer = {
                        val service = TvPointerAccessibilityService.activeService
                        if (service != null) {
                            service.togglePointer()
                        } else {
                            Toast.makeText(
                                this,
                                "Please enable Accessibility Service first!",
                                Toast.LENGTH_LONG
                            ).show()
                            openAccessibilitySettings()
                        }
                    }
                )
            }
        }
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open settings: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        ?: return false
    if (!am.isEnabled) return false
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: ""
    return enabledServices.contains(context.packageName)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    preferences: PointerPreferences,
    onOpenAccessibility: () -> Unit,
    onTogglePointer: () -> Unit
) {
    val context = LocalContext.current
    var isServiceActive by remember { mutableStateOf(false) }
    var isPointerVisible by remember { mutableStateOf(false) }

    // Live state from service
    val runningState by TvPointerAccessibilityService.serviceRunningFlow.collectAsState()
    val pointerState by TvPointerAccessibilityService.pointerActiveFlow.collectAsState()

    LaunchedEffect(runningState, pointerState) {
        isServiceActive = runningState || isAccessibilityServiceEnabled(context)
        isPointerVisible = pointerState
    }

    // Preferences states
    var selectedSpeed by remember { mutableIntStateOf(preferences.pointerSpeed) }
    var selectedSize by remember { mutableIntStateOf(preferences.pointerSizeDp) }
    var selectedColor by remember { mutableStateOf(preferences.pointerColorHex) }
    var selectedTrigger by remember { mutableStateOf(preferences.activationTrigger) }
    var backExits by remember { mutableStateOf(preferences.backExitsPointer) }
    var autoHideSec by remember { mutableIntStateOf(preferences.autoHideSeconds) }

    // Testing playground counter
    var testClickCount by remember { mutableIntStateOf(0) }
    var testToggleState by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PrimaryCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NearMe,
                                contentDescription = null,
                                tint = PrimaryCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                "TV Mouse Pointer",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                "Virtual on-screen cursor for your TV Remote",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                },
                actions = {
                    // Service Status Badge
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isServiceActive) AccentGreen.copy(alpha = 0.15f) else AccentRed.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isServiceActive) AccentGreen.copy(alpha = 0.4f) else AccentRed.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isServiceActive) AccentGreen else AccentRed)
                            )
                            Text(
                                if (isServiceActive) "Service Ready" else "Service Disabled",
                                color = if (isServiceActive) AccentGreen else AccentRed,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Accessibility Service Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isServiceActive) BorderSubtle else AccentAmber.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isServiceActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isServiceActive) AccentGreen else AccentAmber,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                if (isServiceActive) "Accessibility Service Enabled" else "Action Required: Enable Accessibility",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                if (isServiceActive)
                                    "The service is active and listening for your remote shortcuts."
                                else
                                    "Android TV requires Accessibility Permission so the app can draw the cursor and simulate clicks.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!isServiceActive) {
                            Button(
                                onClick = onOpenAccessibility,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, tint = BgDark)
                                Spacer(Modifier.width(8.dp))
                                Text("Open TV Accessibility Settings", color = BgDark, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = onTogglePointer,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isPointerVisible) AccentAmber else PrimaryCyan
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    if (isPointerVisible) Icons.Default.VisibilityOff else Icons.Default.TouchApp,
                                    contentDescription = null,
                                    tint = BgDark
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (isPointerVisible) "Hide Pointer" else "Test Cursor Now (Toggle)",
                                    color = BgDark,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            OutlinedButton(
                                onClick = onOpenAccessibility,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Accessibility Settings", color = TextPrimary)
                            }
                        }
                    }
                }
            }

            // 2. Interactive Testing Arena
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = PrimaryCyan)
                        Text(
                            "Cursor Testing Playground",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Text(
                        "Test your mouse cursor here! Move the pointer over the buttons and press the OK button on your remote to test clicking.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { testClickCount++ },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighlightDark)
                        ) {
                            Text("Click Target (+1)", color = TextPrimary)
                        }

                        Button(
                            onClick = { testClickCount += 5 },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighlightDark)
                        ) {
                            Text("Click Target (+5)", color = TextPrimary)
                        }

                        OutlinedButton(
                            onClick = { testClickCount = 0 },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Reset", color = TextSecondary)
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SurfaceContainerDark,
                            modifier = Modifier.padding(start = 12.dp)
                        ) {
                            Text(
                                "Total Clicks: $testClickCount",
                                color = PrimaryCyan,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Switch(
                            checked = testToggleState,
                            onCheckedChange = { testToggleState = it }
                        )
                        Text(
                            if (testToggleState) "Interactive Toggle: ACTIVE" else "Interactive Toggle: OFF",
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // 3. Remote Control Guide & Shortcuts
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Gamepad, contentDescription = null, tint = PrimaryCyan)
                        Text(
                            "TV Remote Quick Reference",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        RemoteGuideItem(
                            icon = Icons.Default.TouchApp,
                            title = "Activation Shortcut",
                            desc = selectedTrigger.title,
                            modifier = Modifier.weight(1f)
                        )
                        RemoteGuideItem(
                            icon = Icons.Default.Navigation,
                            title = "D-Pad Arrows (▲ ▼ ◄ ►)",
                            desc = "Glides cursor smoothly. Hold down to accelerate.",
                            modifier = Modifier.weight(1f)
                        )
                        RemoteGuideItem(
                            icon = Icons.Default.RadioButtonChecked,
                            title = "OK / Center Button",
                            desc = "Tap to click. Hold 500ms+ for long-press.",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        RemoteGuideItem(
                            icon = Icons.Default.SwapVert,
                            title = "Menu / Info Button",
                            desc = "Toggles Scroll Mode (D-Pad scrolls page).",
                            modifier = Modifier.weight(1f)
                        )
                        RemoteGuideItem(
                            icon = Icons.Default.FastForward,
                            title = "FF / Rewind / Ch +/-",
                            desc = "Instant scroll page up or down.",
                            modifier = Modifier.weight(1f)
                        )
                        RemoteGuideItem(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            title = "Back Button",
                            desc = "Exits pointer mode immediately.",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 4. Customization & Settings
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = PrimaryCyan)
                        Text(
                            "Pointer Customization & Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    // Activation Trigger
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Remote Activation Shortcut", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ActivationTrigger.values().forEach { trigger ->
                                FilterChip(
                                    selected = selectedTrigger == trigger,
                                    onClick = {
                                        selectedTrigger = trigger
                                        preferences.activationTrigger = trigger
                                    },
                                    label = { Text(trigger.title, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryCyan.copy(alpha = 0.2f),
                                        selectedLabelColor = PrimaryCyan,
                                        labelColor = TextSecondary
                                    )
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = BorderSubtle)

                    // Pointer Speed
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Pointer Speed / Sensitivity", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                            Text("${selectedSpeed}px/frame", style = MaterialTheme.typography.bodyMedium, color = PrimaryCyan)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(8 to "Slow", 16 to "Normal", 24 to "Fast", 36 to "Turbo").forEach { (speed, label) ->
                                Button(
                                    onClick = {
                                        selectedSpeed = speed
                                        preferences.pointerSpeed = speed
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedSpeed == speed) PrimaryCyan else SurfaceHighlightDark
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        label,
                                        color = if (selectedSpeed == speed) BgDark else TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = BorderSubtle)

                    // Pointer Size
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Pointer Size", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                            Text("${selectedSize}dp", style = MaterialTheme.typography.bodyMedium, color = PrimaryCyan)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(28 to "Small", 40 to "Standard", 56 to "Large", 72 to "Extra Large").forEach { (size, label) ->
                                Button(
                                    onClick = {
                                        selectedSize = size
                                        preferences.pointerSizeDp = size
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedSize == size) PrimaryCyan else SurfaceHighlightDark
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        label,
                                        color = if (selectedSize == size) BgDark else TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = BorderSubtle)

                    // Pointer Color
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Pointer Accent Color", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            listOf(
                                "#00E5FF" to "Cyan",
                                "#FFFFFF" to "White",
                                "#00E676" to "Green",
                                "#FFAB00" to "Amber",
                                "#E040FB" to "Purple"
                            ).forEach { (hex, _) ->
                                val color = Color(AndroidColor.parseColor(hex))
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (selectedColor == hex) 3.dp else 1.dp,
                                            color = if (selectedColor == hex) Color.White else BorderSubtle,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            selectedColor = hex
                                            preferences.pointerColorHex = hex
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selectedColor == hex) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = if (hex == "#FFFFFF") BgDark else Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = BorderSubtle)

                    // Auto-hide timeout
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Inactivity Auto-Hide", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                            Text(
                                if (autoHideSec == 0) "Never" else "${autoHideSec}s",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PrimaryCyan
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(10 to "10s", 20 to "20s", 30 to "30s", 0 to "Never").forEach { (sec, label) ->
                                Button(
                                    onClick = {
                                        autoHideSec = sec
                                        preferences.autoHideSeconds = sec
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (autoHideSec == sec) PrimaryCyan else SurfaceHighlightDark
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        label,
                                        color = if (autoHideSec == sec) BgDark else TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = BorderSubtle)

                    // Back button behavior
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Back Key Exits Pointer Mode", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                            Text(
                                "When enabled, pressing Back dismisses cursor without leaving the current TV app",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = backExits,
                            onCheckedChange = {
                                backExits = it
                                preferences.backExitsPointer = it
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun RemoteGuideItem(
    icon: ImageVector,
    title: String,
    desc: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceContainerDark,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}
