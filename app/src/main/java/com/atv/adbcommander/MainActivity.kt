package com.atv.adbcommander

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.atv.adbcommander.model.ConnectionState
import com.atv.adbcommander.ui.components.TvDiscoveryDialog
import com.atv.adbcommander.ui.screens.*
import com.atv.adbcommander.ui.theme.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.launch

/**
 * Tellyscope 4-Tab Navigation destinations + Center Elevated Remote FAB
 */
enum class MainTab(val title: String, val icon: ImageVector) {
    OVERVIEW("Overview", Icons.Default.Home),
    APPS("Apps", Icons.Default.Apps),
    FILES("Files", Icons.Default.Folder),
    TWEAKS("Tweaks", Icons.Default.Tune)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        val adbManager = (application as AdbApp).adbManager

        setContent {
            ATVADBCommanderTheme {
                val connectionState by adbManager.connectionState.collectAsState()
                val isConnected = connectionState is ConnectionState.Connected
                val backupProgress by adbManager.backupProgress.collectAsState()
                val coroutineScope = rememberCoroutineScope()

                val tabs = remember { MainTab.values() }
                val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
                val currentTab = tabs[pagerState.currentPage]

                // Full-screen flows / sheets
                var showRemoteSheet by remember { mutableStateOf(false) }
                var showAdvancedScreen by remember { mutableStateOf(false) }
                var showShellScreen by remember { mutableStateOf(false) }
                var showBackupScreen by remember { mutableStateOf(false) }
                var showLauncherScreen by remember { mutableStateOf(false) }
                var showConnectDialog by remember { mutableStateOf(false) }

                // Keep screen awake while a backup or restore is actively in progress
                DisposableEffect(backupProgress.isRunning) {
                    if (backupProgress.isRunning) {
                        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    onDispose {
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                // Automatically connect to the last connected TV on launch and whenever returning to foreground
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            if (!adbManager.isConnected()) {
                                coroutineScope.launch {
                                    adbManager.autoConnect()
                                }
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                // Hardware back handler for modals/overlays
                BackHandler(enabled = showRemoteSheet || showAdvancedScreen || showShellScreen || showBackupScreen || showLauncherScreen) {
                    when {
                        showRemoteSheet -> showRemoteSheet = false
                        showLauncherScreen -> showLauncherScreen = false
                        showShellScreen -> showShellScreen = false
                        showAdvancedScreen -> showAdvancedScreen = false
                        showBackupScreen -> showBackupScreen = false
                    }
                }

                // Hardware back handler for Pager: return to Overview tab before exiting app
                BackHandler(enabled = pagerState.currentPage != 0 && !showRemoteSheet && !showAdvancedScreen && !showShellScreen && !showBackupScreen && !showLauncherScreen) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(0)
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        containerColor = TellyDarkBackground,
                        topBar = {
                            // Minimalist Top Bar with pulsing status indicator
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse_top")
                            val pulseAlpha by infiniteTransition.animateFloat(
                                initialValue = 0.4f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                                ),
                                label = "dot_pulse"
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Title / Brand
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { showConnectDialog = true }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background((if (isConnected) TellySuccess else TellyError).copy(alpha = if (isConnected) pulseAlpha else 1f))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (isConnected) "ATV ADB Commander" else "Offline · Tap to Connect",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TellyOnSurface
                                    )
                                }

                                // Right Action: Clean Power/Disconnect/Connect icon
                                IconButton(
                                    onClick = {
                                        if (isConnected) {
                                            coroutineScope.launch { adbManager.disconnect() }
                                        } else {
                                            showConnectDialog = true
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isConnected) Icons.Default.PowerSettingsNew else Icons.Default.Add,
                                        contentDescription = if (isConnected) "Disconnect" else "Connect",
                                        tint = if (isConnected) TellyOnSurfaceVariant else TellyTealPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        bottomBar = {
                            // Exact Tellyscope Bottom Navigation Bar with Center Elevated Remote FAB
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(26.dp),
                                    color = TellySurfaceContainer.copy(alpha = 0.95f),
                                    border = BorderStroke(1.dp, TellyOutlineSubtle),
                                    shadowElevation = 12.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceAround,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Tab 1: Overview
                                        BottomNavTabItem(
                                            tab = MainTab.OVERVIEW,
                                            isSelected = currentTab == MainTab.OVERVIEW,
                                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(MainTab.OVERVIEW.ordinal) } }
                                        )

                                        // Tab 2: Apps
                                        BottomNavTabItem(
                                            tab = MainTab.APPS,
                                            isSelected = currentTab == MainTab.APPS,
                                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(MainTab.APPS.ordinal) } }
                                        )

                                        // Center Elevated Floating Remote FAB (Exact match to Tellyscope remote button)
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(CircleShape)
                                                .background(TellyTealDark)
                                                .clickable { showRemoteSheet = true },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SettingsRemote,
                                                contentDescription = "Remote",
                                                tint = TellyTealGlow,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }

                                        // Tab 3: Files
                                        BottomNavTabItem(
                                            tab = MainTab.FILES,
                                            isSelected = currentTab == MainTab.FILES,
                                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(MainTab.FILES.ordinal) } }
                                        )

                                        // Tab 4: Tweaks
                                        BottomNavTabItem(
                                            tab = MainTab.TWEAKS,
                                            isSelected = currentTab == MainTab.TWEAKS,
                                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(MainTab.TWEAKS.ordinal) } }
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                when (tabs[page]) {
                                    MainTab.OVERVIEW -> OverviewScreen(
                                        adbManager = adbManager,
                                        onNavigateToApps = { coroutineScope.launch { pagerState.animateScrollToPage(MainTab.APPS.ordinal) } },
                                        onNavigateToFiles = { coroutineScope.launch { pagerState.animateScrollToPage(MainTab.FILES.ordinal) } },
                                        onNavigateToTweaks = { coroutineScope.launch { pagerState.animateScrollToPage(MainTab.TWEAKS.ordinal) } },
                                        onOpenRemote = { showRemoteSheet = true },
                                        onOpenAdvanced = { showAdvancedScreen = true },
                                        onOpenShell = { showShellScreen = true },
                                        onOpenBackup = { showBackupScreen = true },
                                        onAddDevice = { showConnectDialog = true },
                                        onOpenLaunchers = { showLauncherScreen = true }
                                    )
                                    MainTab.APPS -> MonitorScreen(
                                        adbManager = adbManager,
                                        isConnected = isConnected
                                    )
                                    MainTab.FILES -> TvFileExplorerScreen(
                                        adbManager = adbManager,
                                        isConnected = isConnected
                                    )
                                    MainTab.TWEAKS -> TweaksScreen(
                                        adbManager = adbManager,
                                        isConnected = isConnected,
                                        onOpenLaunchers = { showLauncherScreen = true }
                                    )
                                }
                            }
                        }
                    }

                    // Full Remote Screen Overlay
                    AnimatedVisibility(
                        visible = showRemoteSheet,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = TellyDarkBackground
                        ) {
                            RemoteScreen(
                                adbManager = adbManager,
                                isConnected = isConnected,
                                onClose = { showRemoteSheet = false }
                            )
                        }
                    }

                    // Advanced Monitor Screen Overlay
                    AnimatedVisibility(
                        visible = showAdvancedScreen,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = TellyDarkBackground
                        ) {
                            AdvancedScreen(
                                adbManager = adbManager,
                                onBack = { showAdvancedScreen = false },
                                onOpenShell = {
                                    showAdvancedScreen = false
                                    showShellScreen = true
                                }
                            )
                        }
                    }

                    // Backup & Restore Screen Overlay
                    AnimatedVisibility(
                        visible = showBackupScreen,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = TellyDarkBackground
                        ) {
                            TvBackupScreen(
                                adbManager = adbManager,
                                isConnected = isConnected,
                                onBack = { showBackupScreen = false }
                            )
                        }
                    }

                    // TV Launcher Engine Screen Overlay
                    AnimatedVisibility(
                        visible = showLauncherScreen,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = TellyDarkBackground
                        ) {
                            TvLauncherScreen(
                                adbManager = adbManager,
                                isConnected = isConnected,
                                onClose = { showLauncherScreen = false }
                            )
                        }
                    }

                    // Shell Screen Modal Dialog
                    if (showShellScreen) {
                        Dialog(
                            onDismissRequest = { showShellScreen = false },
                            properties = DialogProperties(usePlatformDefaultWidth = false)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                shape = RoundedCornerShape(22.dp),
                                color = TellyDarkBackground,
                                border = BorderStroke(1.dp, TellyTealPrimary.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(TellySurfaceContainerHigh)
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(TellyTealPrimary)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                "ADB SHELL CONSOLE // TERMINAL",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = TellyOnSurface
                                            )
                                        }

                                        IconButton(onClick = { showShellScreen = false }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Close",
                                                tint = TellyOnSurface
                                            )
                                        }
                                    }

                                    ShellScreen(
                                        adbManager = adbManager,
                                        isConnected = isConnected,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // TV Connect & Discovery Dialog
                    if (showConnectDialog) {
                        TvDiscoveryDialog(
                            onDeviceSelected = { ip, port ->
                                coroutineScope.launch {
                                    adbManager.connect(ip, port)
                                }
                                showConnectDialog = false
                            },
                            onDismiss = { showConnectDialog = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNavTabItem(
    tab: MainTab,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tab_scale"
    )

    Column(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .background(if (isSelected) TellySurfaceContainerHigh else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.title,
            tint = if (isSelected) TellyTealPrimary else TellyOnSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = tab.title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 10.sp
            ),
            color = if (isSelected) TellyOnSurface else TellyOnSurfaceVariant
        )
    }
}
