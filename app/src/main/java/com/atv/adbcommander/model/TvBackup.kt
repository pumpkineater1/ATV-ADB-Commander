package com.atv.adbcommander.model

data class TvBackupManifest(
    val id: String,
    val timestamp: Long,
    val deviceName: String,
    val deviceModel: String,
    val androidVersion: String,
    val appCount: Int,
    val totalSizeBytes: Long,
    val hasSettings: Boolean,
    val apps: List<TvBackupAppItem>
)

data class TvBackupAppItem(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
    val apkFileName: String,
    val apkSizeBytes: Long
)

data class BackupProgress(
    val isRunning: Boolean = false,
    val isRestore: Boolean = false,
    val currentStep: String = "",
    val currentItem: Int = 0,
    val totalItems: Int = 0,
    val progressPercent: Float = 0f,
    val error: String? = null
)
