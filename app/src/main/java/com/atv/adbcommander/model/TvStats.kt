package com.atv.adbcommander.model

data class ProcessCpuInfo(
    val pid: String,
    val name: String,
    val cpuPercent: String
)

data class CpuInfo(
    val totalUsagePercent: Int = 0,
    val userPercent: Int = 0,
    val systemPercent: Int = 0,
    val idlePercent: Int = 100,
    val topProcesses: List<ProcessCpuInfo> = emptyList(),
    val rawOutput: String = ""
)

data class MemoryInfo(
    val totalMb: Long = 0,
    val usedMb: Long = 0,
    val freeMb: Long = 0,
    val cachedMb: Long = 0,
    val usedPercent: Int = 0,
    val rawOutput: String = ""
)

data class StorageInfo(
    val filesystem: String = "",
    val total: String = "",
    val used: String = "",
    val available: String = "",
    val usePercent: String = ""
)

data class RunningProcessInfo(
    val pid: String,
    val packageName: String,
    val name: String,
    val memoryMb: Float = 0f,
    val cpuPercent: Float = 0f,
    val isSystemApp: Boolean = false
)

enum class AppSafetyLevel {
    SAFE,       // Safe to disable (bloatware, telemetry, analytics, demos)
    CAUTION,    // Caution (keyboards, play store, bluetooth remote)
    CRITICAL    // CRITICAL: Core OS system component (bootloop / brick risk)
}

enum class AppActionType {
    DISABLED,
    ENABLED,
    UNINSTALLED
}

data class AppHistoryRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val packageName: String,
    val appName: String,
    val action: AppActionType,
    val timestamp: Long = System.currentTimeMillis(),
    val safetyLevel: AppSafetyLevel = AppSafetyLevel.SAFE
)

data class TvAppInfo(
    val packageName: String,
    val appName: String = "",
    val isSystemApp: Boolean = false,
    val isEnabled: Boolean = true,
    val safetyLevel: AppSafetyLevel = AppSafetyLevel.SAFE,
    val safetyDescription: String = "",
    val category: String = "App",
    val versionName: String = "",
    val apkPath: String = "",
    val brandColorHex: Long = 0xFF004F50,
    val iconBytes: ByteArray? = null
)

data class TransferProgress(
    val fileName: String = "",
    val bytesTransferred: Long = 0,
    val totalBytes: Long = 0,
    val percentage: Int = 0,
    val speedKbps: Long = 0,
    val isInstalling: Boolean = false,
    val isDone: Boolean = false,
    val resultMessage: String? = null,
    val error: String? = null
)

data class DiscoveredDevice(
    val ip: String,
    val port: Int = 5555,
    val name: String = "Android TV",
    val isAdbOpen: Boolean = true
)

data class PhoneInstalledApp(
    val appName: String,
    val packageName: String,
    val apkPath: String,
    val sizeBytes: Long
)

data class TvFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: String = "",
    val permissions: String = "",
    val date: String = ""
)

data class TvStorageMount(
    val name: String,
    val path: String,
    val totalSpace: String = "",
    val freeSpace: String = "",
    val isExternal: Boolean = false
)

data class TvDisplaySettings(
    val currentResolution: String = "Auto",
    val currentDensity: String = "Auto"
)

data class TvLauncherInfo(
    val packageName: String,
    val activityName: String = "",
    val name: String,
    val isStock: Boolean = false,
    val isEnabled: Boolean = true,
    val isCurrentDefault: Boolean = false
)

data class SavedTvDevice(
    val ip: String,
    val port: Int = 5555,
    val name: String = "Android TV",
    val model: String = "",
    val lastConnected: Long = System.currentTimeMillis()
)

data class SleepTimerState(
    val isActive: Boolean = false,
    val remainingSeconds: Long = 0L,
    val initialMinutes: Int = 0
)

data class PrivateDnsState(
    val mode: String = "opportunistic", // "hostname", "opportunistic", "off"
    val specifier: String = ""
)

data class CdnPingResult(
    val name: String,
    val host: String,
    val latencyMs: Int,
    val status: String
)

data class DebloatRecommendation(
    val packageName: String,
    val appName: String,
    val reason: String,
    val isSafe: Boolean = true
)

data class TvTweaksState(
    val dns: PrivateDnsState = PrivateDnsState(),
    val animationScale: Float = 1.0f,
    val matchFrameRate: Int = 0,
    val isBlackoutActive: Boolean = false
)

data class OverviewTelemetry(
    val model: String = "Android TV",
    val brand: String = "Google TV",
    val androidVersion: String = "11",
    val securityPatch: String = "May 2023",
    val storage: StorageInfo = StorageInfo(),
    val memory: MemoryInfo = MemoryInfo(),
    val cpuPercent: Int = 18
)

data class NetworkTelemetry(
    val dataUsed1HourFormatted: String = "0 MB",
    val dataUsed1DayFormatted: String = "0 MB",
    val totalBootFormatted: String = "0 MB",
    val currentRxSpeed: String = "0 KB/s",
    val currentTxSpeed: String = "0 KB/s",
    val interfaceName: String = "wlan0 (Wi-Fi)",
    val ipAddress: String = "192.168.1.xxx",
    val gateway: String = "192.168.1.1",
    val ssid: String = "Wi-Fi",
    val signalStrength: String = "-54 dBm (Good)",
    val linkSpeed: String = "866 Mbps"
)
