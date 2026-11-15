package com.atv.adbcommander.adb

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.wifi.WifiManager
import android.os.Environment
import android.os.PowerManager
import android.util.Log
import com.atv.adbcommander.model.*
import com.atv.adbcommander.service.AdbBackupService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.regex.Pattern
import org.json.JSONArray
import org.json.JSONObject

/**
 * High-level ADB controller providing reactive state and coroutine APIs
 * for Remote Control, File Transfer, APK Installation, and System Monitoring.
 */
class AdbManager(private val context: Context) {

    companion object {
        private const val TAG = "AdbManager"

        // Key Codes for Android TV
        const val KEYCODE_DPAD_UP = 19
        const val KEYCODE_DPAD_DOWN = 20
        const val KEYCODE_DPAD_LEFT = 21
        const val KEYCODE_DPAD_RIGHT = 22
        const val KEYCODE_DPAD_CENTER = 23
        const val KEYCODE_BACK = 4
        const val KEYCODE_HOME = 3
        const val KEYCODE_MENU = 82
        const val KEYCODE_APP_SWITCH = 187
        const val KEYCODE_VOLUME_UP = 24
        const val KEYCODE_VOLUME_DOWN = 25
        const val KEYCODE_VOLUME_MUTE = 164
        const val KEYCODE_MEDIA_PLAY_PAUSE = 85
        const val KEYCODE_MEDIA_NEXT = 87
        const val KEYCODE_MEDIA_PREVIOUS = 88
        const val KEYCODE_MEDIA_REWIND = 89
        const val KEYCODE_MEDIA_FAST_FORWARD = 90
        const val KEYCODE_POWER = 26
        const val KEYCODE_SETTINGS = 176
        const val KEYCODE_SLEEP = 223
        const val KEYCODE_WAKEUP = 224
        const val KEYCODE_ENTER = 66
        const val KEYCODE_DEL = 67
        const val KEYCODE_NUM_0 = 7
        const val KEYCODE_NUM_1 = 8
        const val KEYCODE_NUM_2 = 9
        const val KEYCODE_NUM_3 = 10
        const val KEYCODE_NUM_4 = 11
        const val KEYCODE_NUM_5 = 12
        const val KEYCODE_NUM_6 = 13
        const val KEYCODE_NUM_7 = 14
        const val KEYCODE_NUM_8 = 15
        const val KEYCODE_NUM_9 = 16
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _sleepTimerState = MutableStateFlow(SleepTimerState())
    val sleepTimerState: StateFlow<SleepTimerState> = _sleepTimerState

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _backupProgress = MutableStateFlow(BackupProgress())
    val backupProgress: StateFlow<BackupProgress> = _backupProgress.asStateFlow()

    private val _installedApps = MutableStateFlow<List<TvAppInfo>>(emptyList())
    val installedApps: StateFlow<List<TvAppInfo>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()
    val perAppNetworkState = MutableStateFlow<Map<String, AppNetworkUsage>>(emptyMap())

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private var activeConnection: AdbConnection? = null
    private var crypto: AdbCrypto? = null

    private var isUserDisconnecting = false
    private val isReconnecting = java.util.concurrent.atomic.AtomicBoolean(false)
    private var heartbeatJob: kotlinx.coroutines.Job? = null
    private val connectMutex = kotlinx.coroutines.sync.Mutex()

    fun isConnected(): Boolean = activeConnection?.isConnectionOpen() == true

    private fun acquireWakeAndWifiLocks(tag: String) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (wakeLock == null) {
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ATVCommander::$tag")
                wakeLock?.setReferenceCounted(false)
            }
            wakeLock?.acquire(60 * 60 * 1000L) // 1 hour max safety timeout

            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiLock == null) {
                wifiLock = wm?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ATVCommander::$tag")
                wifiLock?.setReferenceCounted(false)
            }
            wifiLock?.acquire()
        } catch (e: Exception) {
            Log.w(TAG, "Failed acquiring wake/wifi locks: ${e.message}")
        }
    }

    private fun releaseWakeAndWifiLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (ignored: Exception) {}
        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (ignored: Exception) {}
    }

    private val iconCache = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
    private val labelCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var isHelperPushedInMemory = false
    private var lastNetSampleTimeMs: Long = 0L
    private var lastNetRxBytes: Long = 0L
    private var lastNetTxBytes: Long = 0L

    private fun getIconDiskCacheDir(): File {
        val dir = File(context.cacheDir, "tv_app_icons")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getAppsCacheFile(): File = File(context.filesDir, "cached_tv_apps.json")

    private fun loadIconFromDisk(packageName: String): ByteArray? {
        val cached = iconCache[packageName]
        if (cached != null) return cached
        val file = File(getIconDiskCacheDir(), "$packageName.png")
        if (file.exists() && file.length() > 0) {
            try {
                val bytes = file.readBytes()
                iconCache[packageName] = bytes
                return bytes
            } catch (e: Exception) {
                // Ignore
            }
        }
        return null
    }

    private fun saveIconToDisk(packageName: String, bytes: ByteArray) {
        iconCache[packageName] = bytes
        try {
            val file = File(getIconDiskCacheDir(), "$packageName.png")
            file.writeBytes(bytes)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun loadCachedAppsFromDisk() {
        try {
            val file = getAppsCacheFile()
            if (!file.exists() || file.length() == 0L) return
            val jsonArray = JSONArray(file.readText())
            val list = mutableListOf<TvAppInfo>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val pkg = obj.getString("p")
                val name = obj.getString("n")
                val isSys = obj.getBoolean("s")
                val isEn = obj.getBoolean("e")
                val safety = try { AppSafetyLevel.valueOf(obj.optString("sl", "SAFE")) } catch (e: Exception) { AppSafetyLevel.SAFE }
                val cat = obj.optString("c", "Application")
                val brandHex = obj.optLong("b", 0xFF00B4D8L)
                val entry = com.atv.adbcommander.util.AppCatalog.getEntry(pkg)

                list.add(
                    TvAppInfo(
                        packageName = pkg,
                        appName = name,
                        isSystemApp = isSys,
                        isEnabled = isEn,
                        safetyLevel = safety,
                        safetyDescription = entry.purpose + "\n\n" + entry.safetyNote,
                        category = cat,
                        brandColorHex = brandHex,
                        iconBytes = loadIconFromDisk(pkg)
                    )
                )
            }
            if (list.isNotEmpty()) {
                _installedApps.value = list
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading cached apps from disk: ${e.message}")
        }
    }

    private fun saveCachedAppsToDisk(apps: List<TvAppInfo>) {
        if (apps.isEmpty()) return
        try {
            val array = JSONArray()
            for (app in apps) {
                val obj = JSONObject()
                obj.put("p", app.packageName)
                obj.put("n", app.appName)
                obj.put("s", app.isSystemApp)
                obj.put("e", app.isEnabled)
                obj.put("sl", app.safetyLevel.name)
                obj.put("c", app.category)
                obj.put("b", app.brandColorHex)
                array.put(obj)
            }
            getAppsCacheFile().writeText(array.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Error saving cached apps to disk: ${e.message}")
        }
    }

    init {
        try {
            crypto = AdbCrypto.loadOrGenerate(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AdbCrypto: ${e.message}")
        }
        loadCachedAppsFromDisk()
    }

    /**
     * Connects to Android TV over ADB network.
     */
    suspend fun connect(host: String, port: Int = 5555) = withContext(Dispatchers.IO) {
        internalDisconnect()
        isUserDisconnecting = false

        // Remember target immediately
        prefs.edit()
            .putString("last_connected_ip", host)
            .putInt("last_connected_port", port)
            .putBoolean("pref_auto_connect", true)
            .apply()

        _connectionState.value = ConnectionState.Connecting("Connecting to $host:$port...")

        try {
            if (crypto == null) {
                crypto = AdbCrypto.loadOrGenerate(context)
            }

            val conn = AdbConnection(
                host = host,
                port = port,
                crypto = crypto!!,
                onAuthPromptListener = {
                    _connectionState.value = ConnectionState.Authenticating(
                        "Please check your TV screen and select 'Always allow' / 'OK' on the USB debugging prompt."
                    )
                },
                onDisconnectListener = { error ->
                    handleUnexpectedDisconnect(host, port, error)
                }
            )

            conn.connect()
            activeConnection = conn
            _connectionState.value = ConnectionState.Connected(host, port)
            acquireWakeAndWifiLocks("AdbActive")
            startHeartbeatWatchdog(host, port)

            // Auto-detect TV model and refresh apps in background without blocking connect()
            managerScope.launch(Dispatchers.IO) {
                try {
                    val m = runShell("getprop ro.product.model").trim()
                    val b = runShell("getprop ro.product.brand").trim()
                    val modelName = if (m.isNotEmpty()) "$b $m".trim().replaceFirstChar { it.uppercase() } else "Android TV"
                    saveDevice(SavedTvDevice(ip = host, port = port, name = modelName, model = modelName))
                } catch (ignored: Exception) {}
                refreshInstalledApps()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed: ${e.message}", e)
            _connectionState.value = ConnectionState.Error(e.localizedMessage ?: "Failed to connect")
        }
    }

    private fun startHeartbeatWatchdog(host: String, port: Int) {
        heartbeatJob?.cancel()
        heartbeatJob = managerScope.launch(Dispatchers.IO) {
            while (isActive && isConnected() && !isUserDisconnecting) {
                kotlinx.coroutines.delay(12000L) // Lightweight ping every 12 seconds keeps TCP alive and detects drops
                if (!isConnected() || isUserDisconnecting) break
                try {
                    // Shell built-in ":" executes instantaneously with no process spawn
                    runShell(":")
                } catch (e: Exception) {
                    if (!isUserDisconnecting) {
                        Log.w(TAG, "ADB Heartbeat watchdog detected drop: ${e.message}")
                        handleUnexpectedDisconnect(host, port, e)
                        break
                    }
                }
            }
        }
    }

    private fun handleUnexpectedDisconnect(host: String, port: Int, error: Throwable?) {
        if (isUserDisconnecting) return
        if (isReconnecting.compareAndSet(false, true)) {
            managerScope.launch(Dispatchers.IO) {
                Log.w(TAG, "Unexpected ADB disconnect from $host:$port (${error?.message}). Starting seamless reconnect...")
                _connectionState.value = ConnectionState.Connecting("Connection interrupted. Reconnecting to $host...")

                try {
                    activeConnection?.close()
                } catch (ignored: Exception) {}
                activeConnection = null

                var attempts = 0
                val maxAttempts = 6
                var delayMs = 1000L

                while (attempts < maxAttempts && !isUserDisconnecting && !isConnected()) {
                    kotlinx.coroutines.delay(delayMs)
                    if (isUserDisconnecting) break
                    try {
                        Log.d(TAG, "Seamless auto-reconnect attempt ${attempts + 1}/$maxAttempts for $host:$port")
                        val c = AdbConnection(
                            host = host,
                            port = port,
                            crypto = crypto ?: AdbCrypto.loadOrGenerate(context),
                            onAuthPromptListener = {
                                _connectionState.value = ConnectionState.Authenticating(
                                    "Please check TV screen to accept ADB prompt"
                                )
                            },
                            onDisconnectListener = { err ->
                                handleUnexpectedDisconnect(host, port, err)
                            }
                        )
                        c.connect()
                        activeConnection = c
                        _connectionState.value = ConnectionState.Connected(host, port)
                        acquireWakeAndWifiLocks("AdbActive")
                        startHeartbeatWatchdog(host, port)
                        Log.i(TAG, "Seamless auto-reconnect succeeded to $host:$port")
                        break
                    } catch (e: Exception) {
                        Log.w(TAG, "Auto-reconnect attempt ${attempts + 1} failed: ${e.message}")
                    }
                    attempts++
                    delayMs = minOf(delayMs + 1000L, 4000L)
                }

                isReconnecting.set(false)
                if (!isConnected() && !isUserDisconnecting) {
                    _connectionState.value = ConnectionState.Disconnected
                    releaseWakeAndWifiLocks()
                }
            }
        }
    }

    suspend fun refreshInstalledApps(): List<TvAppInfo> {
        _isLoadingApps.value = true
        return try {
            val apps = fetchInstalledApps()
            _installedApps.value = apps
            apps
        } finally {
            _isLoadingApps.value = false
        }
    }

    /**
     * Disconnects active ADB connection.
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        isUserDisconnecting = true
        internalDisconnect()
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun internalDisconnect() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        try {
            fastRemoteStream?.close()
        } catch (ignored: Exception) {}
        fastRemoteStream = null
        try {
            activeConnection?.close()
        } catch (ignored: Exception) {}
        activeConnection = null
        releaseWakeAndWifiLocks()
    }

    private val shellMutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Executes an ADB shell command and returns the output.
     */
    suspend fun runShell(command: String): String = shellMutex.withLock {
        withContext(Dispatchers.IO) {
            val conn = activeConnection ?: throw IllegalStateException("ADB not connected")
            val stream = conn.openStream("shell:$command")
            try {
                stream.readString()
            } finally {
                stream.close()
            }
        }
    }

    // ==========================================
    // Remote Control (Ultra-Low-Latency Sub-5ms)
    // ==========================================

    private var fastRemoteStream: AdbStream? = null
    private val fastRemoteMutex = kotlinx.coroutines.sync.Mutex()
    private var supportsCmdInput: Boolean? = null

    private suspend fun getFastRemoteStream(): AdbStream? {
        val conn = activeConnection ?: return null
        if (!conn.isConnectionOpen()) return null
        return fastRemoteMutex.withLock {
            if (fastRemoteStream != null) return@withLock fastRemoteStream
            try {
                // Open a persistent interactive shell for sub-5ms low-latency remote control
                val stream = conn.openStream("shell:")
                fastRemoteStream = stream

                if (supportsCmdInput == null) {
                    managerScope.launch(Dispatchers.IO) {
                        try {
                            val res = runShell("cmd input --help 2>&1")
                            supportsCmdInput = !res.contains("No shell command", ignoreCase = true)
                        } catch (e: Exception) {
                            supportsCmdInput = false
                        }
                    }
                }
                stream
            } catch (e: Exception) {
                Log.w(TAG, "Failed creating fast remote shell stream: ${e.message}")
                null
            }
        }
    }

    suspend fun sendKeyEvent(keyCode: Int): Boolean = withContext(Dispatchers.IO) {
        val cmd = if (supportsCmdInput == true) "cmd input keyevent $keyCode\n" else "cmd input keyevent $keyCode 2>/dev/null || input keyevent $keyCode\n"
        try {
            val stream = getFastRemoteStream()
            if (stream != null) {
                try {
                    stream.write(cmd.toByteArray(Charsets.UTF_8))
                    return@withContext true
                } catch (writeEx: Exception) {
                    fastRemoteMutex.withLock {
                        try { fastRemoteStream?.close() } catch (ignored: Exception) {}
                        fastRemoteStream = null
                    }
                }
            }
            // Fallback via runShell
            runShell(cmd.trimEnd('\n'))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send keyevent $keyCode: ${e.message}")
            false
        }
    }

    suspend fun sendText(text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Escape special shell chars or spaces
            val escaped = text.replace(" ", "%s")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("&", "\\&")
            val cmd = if (supportsCmdInput == true) "cmd input text \"$escaped\"\n" else "cmd input text \"$escaped\" 2>/dev/null || input text \"$escaped\"\n"
            val stream = getFastRemoteStream()
            if (stream != null) {
                try {
                    stream.write(cmd.toByteArray(Charsets.UTF_8))
                    return@withContext true
                } catch (writeEx: Exception) {
                    fastRemoteMutex.withLock {
                        try { fastRemoteStream?.close() } catch (ignored: Exception) {}
                        fastRemoteStream = null
                    }
                }
            }
            runShell(cmd.trimEnd('\n'))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send text: ${e.message}")
            false
        }
    }

    // ==========================================
    // File Transfer & APK Installation
    // ==========================================

    /**
     * Pushes a file to Android TV with real-time transfer progress.
     */
    fun pushFile(
        inputStream: InputStream,
        remotePath: String,
        totalBytes: Long,
        fileName: String
    ): Flow<TransferProgress> = channelFlow {
        val conn = activeConnection ?: throw IllegalStateException("ADB not connected")
        val sync = AdbSync(conn)

        var lastTime = System.currentTimeMillis()
        var lastBytes = 0L

        send(
            TransferProgress(
                fileName = fileName,
                bytesTransferred = 0,
                totalBytes = totalBytes,
                percentage = 0
            )
        )

        try {
            val parentDir = remotePath.substringBeforeLast('/')
            if (parentDir.isNotEmpty() && parentDir != "/") {
                try {
                    runShell("mkdir -p \"$parentDir\"")
                } catch (ignored: Exception) {}
            }

            sync.push(inputStream, remotePath, totalBytes) { transferred, total ->
                val now = System.currentTimeMillis()
                val timeDiff = now - lastTime
                var speedKbps = 0L
                if (timeDiff >= 300) {
                    val bytesDiff = transferred - lastBytes
                    speedKbps = (bytesDiff * 1000) / (timeDiff * 1024)
                    lastTime = now
                    lastBytes = transferred
                    val percent = if (total > 0) ((transferred * 100) / total).toInt() else 0

                    trySend(
                        TransferProgress(
                            fileName = fileName,
                            bytesTransferred = transferred,
                            totalBytes = total,
                            percentage = percent,
                            speedKbps = speedKbps
                        )
                    )
                }
            }

            send(
                TransferProgress(
                    fileName = fileName,
                    bytesTransferred = totalBytes,
                    totalBytes = totalBytes,
                    percentage = 100,
                    isDone = true,
                    resultMessage = "Successfully transferred to $remotePath"
                )
            )
        } catch (e: Exception) {
            send(
                TransferProgress(
                    fileName = fileName,
                    error = e.localizedMessage ?: "File transfer failed"
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Installs an APK on Android TV:
     * 1. Pushes APK to `/data/local/tmp/temp_app.apk`
     * 2. Runs `pm install -r -d /data/local/tmp/temp_app.apk`
     * 3. Deletes temp APK and reports install result.
     */
    fun installApk(
        inputStream: InputStream,
        totalBytes: Long,
        fileName: String
    ): Flow<TransferProgress> = channelFlow {
        val conn = activeConnection ?: throw IllegalStateException("ADB not connected")
        val sync = AdbSync(conn)
        val remoteTempApk = "/data/local/tmp/atv_temp_${System.currentTimeMillis()}.apk"

        send(
            TransferProgress(
                fileName = fileName,
                bytesTransferred = 0,
                totalBytes = totalBytes,
                percentage = 0
            )
        )

        try {
            var lastTime = System.currentTimeMillis()
            var lastBytes = 0L

            // Step 1: Upload APK
            sync.push(inputStream, remoteTempApk, totalBytes) { transferred, total ->
                val now = System.currentTimeMillis()
                val timeDiff = now - lastTime
                if (timeDiff >= 300) {
                    val bytesDiff = transferred - lastBytes
                    val speedKbps = (bytesDiff * 1000) / (timeDiff * 1024)
                    lastTime = now
                    lastBytes = transferred
                    val percent = if (total > 0) ((transferred * 100) / total).toInt() else 0
                    trySend(
                        TransferProgress(
                            fileName = fileName,
                            bytesTransferred = transferred,
                            totalBytes = total,
                            percentage = percent,
                            speedKbps = speedKbps
                        )
                    )
                }
            }

            // Step 2: Running install
            send(
                TransferProgress(
                    fileName = fileName,
                    bytesTransferred = totalBytes,
                    totalBytes = totalBytes,
                    percentage = 100,
                    isInstalling = true,
                    resultMessage = "Installing on TV..."
                )
            )

            // Step 3: Run pm install
            val output = runShell("pm install -r -d -g $remoteTempApk && rm -f $remoteTempApk")
            val isSuccess = output.contains("Success", ignoreCase = true)

            send(
                TransferProgress(
                    fileName = fileName,
                    bytesTransferred = totalBytes,
                    totalBytes = totalBytes,
                    percentage = 100,
                    isInstalling = false,
                    isDone = true,
                    resultMessage = if (isSuccess) "App installed successfully!" else "Installation failed: $output",
                    error = if (!isSuccess) output else null
                )
            )
        } catch (e: Exception) {
            // Clean up temp file
            try {
                runShell("rm -f $remoteTempApk")
            } catch (ignored: Exception) {}

            send(
                TransferProgress(
                    fileName = fileName,
                    error = e.localizedMessage ?: "Installation failed"
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    // ==========================================
    // System Monitoring & App Management
    // ==========================================

    /**
     * Parses CPU usage percentage and top active processes.
     */
    suspend fun fetchCpuInfo(): CpuInfo = withContext(Dispatchers.IO) {
        try {
            // Try top first: top -n 1 -b -m 6
            val topOutput = runShell("top -n 1 -b -m 6")
            parseTopOutput(topOutput)
        } catch (e: Exception) {
            try {
                // Fallback to dumpsys cpuinfo
                val dumpOutput = runShell("dumpsys cpuinfo | head -n 15")
                parseDumpsysCpu(dumpOutput)
            } catch (e2: Exception) {
                CpuInfo(rawOutput = "Error fetching CPU: ${e.message}")
            }
        }
    }

    private fun parseTopOutput(output: String): CpuInfo {
        var user = 0
        var sys = 0
        var idle = 100
        val processes = mutableListOf<ProcessCpuInfo>()

        val lines = output.lines()
        for (line in lines) {
            val lower = line.lowercase()
            // e.g. "User 12%, System 8%, IOW 0%, IRQ 0%" or "800%cpu  25%user   0%nice  15%sys 750%idle"
            if (lower.contains("user") && lower.contains("sys")) {
                val userMatch = Regex("([0-9]+)%?\\s*user").find(lower)
                val sysMatch = Regex("([0-9]+)%?\\s*sys").find(lower)
                val idleMatch = Regex("([0-9]+)%?\\s*idle").find(lower)

                if (userMatch != null) user = userMatch.groupValues[1].toIntOrNull() ?: 0
                if (sysMatch != null) sys = sysMatch.groupValues[1].toIntOrNull() ?: 0
                if (idleMatch != null) idle = idleMatch.groupValues[1].toIntOrNull() ?: (100 - user - sys)
            } else if (line.matches(Regex("^\\s*\\d+.*"))) {
                // Process line: PID USER ... CPU% ... NAME
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 8) {
                    val pid = parts[0]
                    // Find CPU% (column with float/int)
                    val cpuStr = parts.firstOrNull { it.matches(Regex("\\d+(\\.\\d+)?%?")) && it != pid } ?: "0%"
                    val name = parts.last()
                    processes.add(ProcessCpuInfo(pid, name, if (cpuStr.endsWith("%")) cpuStr else "$cpuStr%"))
                }
            }
        }

        val total = (user + sys).coerceIn(0, 100)
        return CpuInfo(
            totalUsagePercent = total,
            userPercent = user,
            systemPercent = sys,
            idlePercent = idle,
            topProcesses = processes.take(6),
            rawOutput = output
        )
    }

    private fun parseDumpsysCpu(output: String): CpuInfo {
        var total = 0
        var user = 0
        var kernel = 0
        val processes = mutableListOf<ProcessCpuInfo>()

        val lines = output.lines()
        for (line in lines) {
            if (line.contains("TOTAL:")) {
                // e.g. "23% TOTAL: 12% user + 8% kernel + 0.5% iowait"
                val totalMatch = Regex("([0-9]+(\\.[0-9]+)?)%\\s+TOTAL").find(line)
                val userMatch = Regex("([0-9]+(\\.[0-9]+)?)%\\s+user").find(line)
                val kernelMatch = Regex("([0-9]+(\\.[0-9]+)?)%\\s+kernel").find(line)

                total = totalMatch?.groupValues?.get(1)?.toFloatOrNull()?.toInt() ?: 0
                user = userMatch?.groupValues?.get(1)?.toFloatOrNull()?.toInt() ?: 0
                kernel = kernelMatch?.groupValues?.get(1)?.toFloatOrNull()?.toInt() ?: 0
            } else if (line.contains("%")) {
                val match = Regex("\\s*([0-9]+(\\.[0-9]+)?)%\\s+([0-9]+)/([^:]+):").find(line)
                if (match != null) {
                    val cpu = match.groupValues[1] + "%"
                    val pid = match.groupValues[3]
                    val name = match.groupValues[4]
                    processes.add(ProcessCpuInfo(pid, name, cpu))
                }
            }
        }

        return CpuInfo(
            totalUsagePercent = total.coerceIn(0, 100),
            userPercent = user,
            systemPercent = kernel,
            idlePercent = (100 - total).coerceAtLeast(0),
            topProcesses = processes.take(6),
            rawOutput = output
        )
    }

    /**
     * Reads /proc/meminfo to get RAM stats.
     */
    suspend fun fetchMemoryInfo(): MemoryInfo = withContext(Dispatchers.IO) {
        try {
            val meminfo = runShell("cat /proc/meminfo")
            var totalKb = 0L
            var freeKb = 0L
            var availableKb = 0L
            var cachedKb = 0L

            for (line in meminfo.lines()) {
                val parts = line.split(":")
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim().split(" ")[0].toLongOrNull() ?: 0L
                    when (key) {
                        "MemTotal" -> totalKb = value
                        "MemFree" -> freeKb = value
                        "MemAvailable" -> availableKb = value
                        "Cached" -> cachedKb = value
                    }
                }
            }

            val totalMb = totalKb / 1024
            val freeMb = if (availableKb > 0) availableKb / 1024 else freeKb / 1024
            val usedMb = (totalMb - freeMb).coerceAtLeast(0)
            val cachedMb = cachedKb / 1024
            val percent = if (totalMb > 0) ((usedMb * 100) / totalMb).toInt() else 0

            MemoryInfo(
                totalMb = totalMb,
                usedMb = usedMb,
                freeMb = freeMb,
                cachedMb = cachedMb,
                usedPercent = percent,
                rawOutput = meminfo
            )
        } catch (e: Exception) {
            MemoryInfo(rawOutput = "Error fetching memory: ${e.message}")
        }
    }

    /**
     * Reads storage disk space on TV (/sdcard or /data).
     */
    suspend fun fetchStorageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        try {
            val output = runShell("df -h /sdcard")
            val lines = output.lines().filter { it.isNotBlank() }
            if (lines.size >= 2) {
                val parts = lines[1].trim().split(Regex("\\s+"))
                if (parts.size >= 5) {
                    return@withContext StorageInfo(
                        filesystem = parts[0],
                        total = parts[1],
                        used = parts[2],
                        available = parts[3],
                        usePercent = parts[4]
                    )
                }
            }
            StorageInfo(total = "N/A", used = "N/A", available = "N/A", usePercent = "0%")
        } catch (e: Exception) {
            StorageInfo()
        }
    }

    /**
     * Lists third-party, system, and disabled apps installed on Android TV.
     */
    fun evaluateAppSafety(packageName: String): Pair<AppSafetyLevel, String> {
        val lower = packageName.lowercase()

        // 1. Critical core packages that can cause immediate brick / bootloop / black screen
        if (lower == "android" || lower == "com.android.systemui" || lower == "com.android.providers.settings") {
            return Pair(
                AppSafetyLevel.CRITICAL,
                "DO NOT DISABLE! This is core Android OS / System UI. Disabling will immediately cause a BOOTLOOP or permanent BLACK SCREEN."
            )
        }

        if (lower == "com.android.settings" || lower == "com.google.android.packageinstaller" || lower == "com.android.packageinstaller") {
            return Pair(
                AppSafetyLevel.CRITICAL,
                "CRITICAL SYSTEM APP: Disabling breaks TV settings and prevents any apps from being installed or updated."
            )
        }

        if (lower == "com.google.android.gms") {
            return Pair(
                AppSafetyLevel.CRITICAL,
                "CRITICAL: Google Play Services is required for TV DRM playback, Google Cast, and account licensing."
            )
        }

        if (lower.contains("frameworkpackagestubs") || lower == "com.android.keyguard" || lower == "com.android.phone") {
            return Pair(
                AppSafetyLevel.CRITICAL,
                "CRITICAL SYSTEM SERVICE: Disabling this system package may cause bootloop or freeze."
            )
        }

        // 2. Caution packages (important utilities)
        if (lower == "com.android.bluetooth") {
            return Pair(
                AppSafetyLevel.CAUTION,
                "CAUTION: Bluetooth controls your TV's physical remote control and audio accessories. Disabling may disconnect your remote!"
            )
        }

        if (lower.contains("inputmethod") || lower.contains("keyboard") || lower == "com.google.android.inputmethod.latin") {
            return Pair(
                AppSafetyLevel.CAUTION,
                "CAUTION: TV On-Screen Keyboard. Make sure another keyboard is installed before disabling, or you cannot enter text."
            )
        }

        if (lower == "com.android.vending") {
            return Pair(
                AppSafetyLevel.CAUTION,
                "CAUTION: Google Play Store. Disabling stops TV app updates and new Play Store downloads."
            )
        }

        // 3. Safe to debloat (telemetry, bloat, promo, launchers)
        if (lower.contains("telemetry") || lower.contains("analytics") || lower.contains("metrics") ||
            lower.contains("feedback") || lower.contains("retail") || lower.contains("demo") ||
            lower.contains("katniss") || lower.contains("videos") || lower.contains("play.games") ||
            lower.contains("launcherx") || lower.contains("tvlauncher") || lower.contains("mitv.tvhome")
        ) {
            return Pair(
                AppSafetyLevel.SAFE,
                "Safe to debloat. Disabling this OEM telemetry/bloatware or stock launcher will free RAM and remove ads without harming the TV system."
            )
        }

        return Pair(AppSafetyLevel.SAFE, "Standard app. Disabling will stop it from running in background.")
    }

    // ==========================================
    // Debloat & App State Audit History Tracker
    // ==========================================

    private val auditPrefs = context.getSharedPreferences("atv_app_audit_history", Context.MODE_PRIVATE)

    fun getAppHistory(): List<AppHistoryRecord> {
        val raw = auditPrefs.getString("audit_list", "") ?: ""
        if (raw.isBlank()) return emptyList()

        val list = mutableListOf<AppHistoryRecord>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    AppHistoryRecord(
                        id = obj.optString("id"),
                        packageName = obj.optString("packageName"),
                        appName = obj.optString("appName"),
                        action = AppActionType.valueOf(obj.optString("action", "DISABLED")),
                        timestamp = obj.optLong("timestamp"),
                        safetyLevel = AppSafetyLevel.valueOf(obj.optString("safetyLevel", "SAFE"))
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing audit list: ${e.message}")
        }
        return list.sortedByDescending { it.timestamp }
    }

    fun recordAppHistory(packageName: String, appName: String, action: AppActionType) {
        val safety = evaluateAppSafety(packageName).first
        val current = getAppHistory().toMutableList()
        current.add(
            0,
            AppHistoryRecord(
                packageName = packageName,
                appName = appName.ifEmpty { packageName.substringAfterLast(".") },
                action = action,
                timestamp = System.currentTimeMillis(),
                safetyLevel = safety
            )
        )

        // Keep last 60 records
        val arr = JSONArray()
        for (item in current.take(60)) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("packageName", item.packageName)
                put("appName", item.appName)
                put("action", item.action.name)
                put("timestamp", item.timestamp)
                put("safetyLevel", item.safetyLevel.name)
            }
            arr.put(obj)
        }
        auditPrefs.edit().putString("audit_list", arr.toString()).apply()
    }

    fun clearAppHistory() {
        auditPrefs.edit().remove("audit_list").apply()
    }

    // ==========================================
    // Running Processes & Background App Inspector
    // ==========================================

    suspend fun fetchRunningProcesses(): List<RunningProcessInfo> = withContext(Dispatchers.IO) {
        val processes = mutableMapOf<String, RunningProcessInfo>()

        try {
            // 1. Fetch exact per-process memory (RSS / PSS)
            val memOut = runShell("dumpsys meminfo | head -n 45")
            val memRegex = Regex("^\\s*([0-9,]+)K:\\s*([^\\s]+)\\s*\\(pid\\s*([0-9]+)")

            for (line in memOut.lines()) {
                val match = memRegex.find(line)
                if (match != null) {
                    val rawKb = match.groupValues[1].replace(",", "").toLongOrNull() ?: 0L
                    val pkgOrProc = match.groupValues[2]
                    val pid = match.groupValues[3]
                    val mb = rawKb / 1024f

                    val cleanPkg = if (pkgOrProc.contains(":")) pkgOrProc.substringBefore(":") else pkgOrProc
                    val isSys = cleanPkg.startsWith("com.android.") || cleanPkg.startsWith("android") || cleanPkg.startsWith("system")

                    val friendly = getLauncherFriendlyName(cleanPkg).replace(" Launcher", "").ifEmpty {
                        cleanPkg.substringAfterLast(".").replaceFirstChar { it.uppercase() }
                    }

                    processes[pid] = RunningProcessInfo(
                        pid = pid,
                        packageName = cleanPkg,
                        name = friendly,
                        memoryMb = mb,
                        cpuPercent = 0f,
                        isSystemApp = isSys
                    )
                }
            }

            // 2. Fetch CPU usage per process
            val cpuOut = runShell("dumpsys cpuinfo | head -n 35")
            val cpuRegex = Regex("^\\s*([0-9.]+)%\\s*([0-9]+)/([^:]+):")

            for (line in cpuOut.lines()) {
                val match = cpuRegex.find(line)
                if (match != null) {
                    val cpuPct = match.groupValues[1].toFloatOrNull() ?: 0f
                    val pid = match.groupValues[2]
                    val procName = match.groupValues[3].trim()

                    val existing = processes[pid]
                    if (existing != null) {
                        processes[pid] = existing.copy(cpuPercent = cpuPct)
                    } else if (cpuPct > 0.1f) {
                        val cleanPkg = if (procName.contains(":")) procName.substringBefore(":") else procName
                        val isSys = cleanPkg.startsWith("com.android.") || cleanPkg.startsWith("android") || cleanPkg.startsWith("system")
                        val friendly = getLauncherFriendlyName(cleanPkg).replace(" Launcher", "").ifEmpty {
                            cleanPkg.substringAfterLast(".").replaceFirstChar { it.uppercase() }
                        }
                        processes[pid] = RunningProcessInfo(
                            pid = pid,
                            packageName = cleanPkg,
                            name = friendly,
                            memoryMb = 0f,
                            cpuPercent = cpuPct,
                            isSystemApp = isSys
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching running processes: ${e.message}")
        }

        // Return sorted by memory descending by default
        processes.values.sortedByDescending { it.memoryMb }
    }

    suspend fun ensureLabelHelperPushed(): Boolean = withContext(Dispatchers.IO) {
        if (isHelperPushedInMemory) return@withContext true
        val conn = activeConnection ?: return@withContext false
        try {
            val check = runShell("ls -l /data/local/tmp/label-helper.jar 2>/dev/null").trim()
            if (check.contains("label-helper.jar") && !check.contains("No such file")) {
                isHelperPushedInMemory = true
                return@withContext true
            }
            context.assets.open("label-helper.jar").use { input ->
                val bytes = input.readBytes()
                val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                runShell("echo '$b64' | base64 -d > /data/local/tmp/label-helper.jar && chmod 644 /data/local/tmp/label-helper.jar")
            }
            val recheck = runShell("ls -l /data/local/tmp/label-helper.jar 2>/dev/null").trim()
            isHelperPushedInMemory = recheck.contains("label-helper.jar")
            isHelperPushedInMemory
        } catch (e: Exception) {
            Log.e(TAG, "Failed pushing label helper: ${e.message}")
            false
        }
    }

    suspend fun fetchInstalledApps(): List<TvAppInfo> = withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
        val appMap = mutableMapOf<String, TvAppInfo>()
        try {
            // Combined single shell execution for disabled, third-party, and system packages
            val combinedOutput = runShell("pm list packages -d; echo '===3==='; pm list packages -3; echo '===s==='; pm list packages -s")
            val sections = combinedOutput.split("===3===", "===s===")

            val disabledLines = sections.getOrNull(0) ?: ""
            val thirdPartyLines = sections.getOrNull(1) ?: ""
            val systemLines = sections.getOrNull(2) ?: ""

            val disabledPackages = disabledLines.lines()
                .map { it.removePrefix("package:").trim() }
                .filter { it.isNotEmpty() }
                .toSet()

            for (line in thirdPartyLines.lines()) {
                val pkg = line.removePrefix("package:").trim()
                if (pkg.isNotEmpty()) {
                    val entry = com.atv.adbcommander.util.AppCatalog.getEntry(pkg)
                    val cachedIcon = loadIconFromDisk(pkg)
                    val cachedLabel = labelCache[pkg] ?: entry.friendlyName
                    appMap[pkg] = TvAppInfo(
                        packageName = pkg,
                        appName = cachedLabel,
                        isSystemApp = false,
                        isEnabled = !disabledPackages.contains(pkg),
                        safetyLevel = entry.safetyLevel,
                        safetyDescription = entry.purpose + "\n\n" + entry.safetyNote,
                        category = entry.category,
                        brandColorHex = entry.brandColorHex,
                        iconBytes = cachedIcon
                    )
                }
            }

            for (line in systemLines.lines()) {
                val pkg = line.removePrefix("package:").trim()
                if (pkg.isNotEmpty() && !appMap.containsKey(pkg)) {
                    val entry = com.atv.adbcommander.util.AppCatalog.getEntry(pkg)
                    val cachedIcon = loadIconFromDisk(pkg)
                    val cachedLabel = labelCache[pkg] ?: entry.friendlyName
                    appMap[pkg] = TvAppInfo(
                        packageName = pkg,
                        appName = cachedLabel,
                        isSystemApp = true,
                        isEnabled = !disabledPackages.contains(pkg),
                        safetyLevel = entry.safetyLevel,
                        safetyDescription = entry.purpose + "\n\n" + entry.safetyNote,
                        category = entry.category,
                        brandColorHex = entry.brandColorHex,
                        iconBytes = cachedIcon
                    )
                }
            }

            // Query label helper ONLY for apps missing icons
            val missingIcons = appMap.keys.filter { loadIconFromDisk(it) == null }
            if (missingIcons.isNotEmpty() && ensureLabelHelperPushed()) {
                val cmd = if (missingIcons.size > 15) {
                    "CLASSPATH=/data/local/tmp/label-helper.jar app_process / app.tellyscope.helper.Labels --all 2>/dev/null"
                } else {
                    "CLASSPATH=/data/local/tmp/label-helper.jar app_process / app.tellyscope.helper.Labels ${missingIcons.joinToString(" ")} 2>/dev/null"
                }
                try {
                    val out = runShell(cmd)
                    out.lineSequence().forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                            try {
                                val json = org.json.JSONObject(trimmed)
                                val pkg = json.optString("p")
                                val label = json.optString("l")
                                val iconB64 = json.optString("i")
                                if (pkg.isNotEmpty()) {
                                    if (label.isNotEmpty()) labelCache[pkg] = label
                                    val iconBytes = if (iconB64.isNotEmpty()) {
                                        android.util.Base64.decode(iconB64, android.util.Base64.DEFAULT)
                                    } else null

                                    if (iconBytes != null) {
                                        saveIconToDisk(pkg, iconBytes)
                                    }

                                    val current = appMap[pkg]
                                    if (current != null) {
                                        appMap[pkg] = current.copy(
                                            appName = if (label.isNotEmpty()) label else current.appName,
                                            iconBytes = iconBytes ?: current.iconBytes
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                // Skip single malformed JSON
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Icon extraction failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching apps: ${e.message}")
        }
        val sorted = appMap.values.sortedBy { it.appName.lowercase() }
        _installedApps.value = sorted
        if (sorted.isNotEmpty()) {
            saveCachedAppsToDisk(sorted)
        }
        sorted
    }

    suspend fun fetchOverviewTelemetry(): OverviewTelemetry = withContext(Dispatchers.IO) {
        try {
            val cmd = "echo '===PROPS==='; getprop ro.product.model; getprop ro.product.brand; getprop ro.build.version.release; getprop ro.build.version.security_patch; echo '===STORAGE==='; df -h /data /sdcard 2>/dev/null; echo '===MEM==='; cat /proc/meminfo 2>/dev/null | head -n 6; echo '===CPU==='; head -n 1 /proc/stat"
            val out = runShell(cmd)
            parseOverviewTelemetry(out)
        } catch (e: Exception) {
            Log.w(TAG, "fetchOverviewTelemetry failed: ${e.message}")
            OverviewTelemetry()
        }
    }

    private fun parseOverviewTelemetry(raw: String): OverviewTelemetry {
        var model = "Android TV"
        var brand = "Google TV"
        var androidVersion = "11"
        var securityPatch = "May 2023"
        var storage = StorageInfo()
        var memory = MemoryInfo()
        var cpuPercent = 18

        val sections = raw.split("===PROPS===", "===STORAGE===", "===MEM===", "===CPU===")
        if (sections.size >= 5) {
            // 1. Props
            val propLines = sections[1].trim().lines().filter { it.isNotBlank() }
            if (propLines.isNotEmpty()) model = propLines.getOrNull(0)?.trim() ?: model
            if (propLines.size > 1) brand = propLines.getOrNull(1)?.trim()?.replaceFirstChar { it.uppercase() } ?: brand
            if (propLines.size > 2) androidVersion = propLines.getOrNull(2)?.trim() ?: androidVersion
            if (propLines.size > 3) securityPatch = propLines.getOrNull(3)?.trim() ?: securityPatch

            // 2. Storage
            val storageLines = sections[2].trim().lines().filter { it.isNotBlank() }
            for (line in storageLines) {
                if (line.startsWith("Filesystem")) continue
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 5) {
                    storage = StorageInfo(
                        filesystem = parts[0],
                        total = parts[1],
                        used = parts[2],
                        available = parts[3],
                        usePercent = parts[4]
                    )
                    break
                }
            }

            // 3. Memory
            var totalKb = 0L
            var availKb = 0L
            var cachedKb = 0L
            val memLines = sections[3].trim().lines()
            for (line in memLines) {
                val parts = line.split(Regex(":\\s+"))
                if (parts.size >= 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim().split(" ")[0].toLongOrNull() ?: 0L
                    when (key) {
                        "MemTotal" -> totalKb = value
                        "MemAvailable" -> availKb = value
                        "Cached" -> cachedKb = value
                    }
                }
            }
            if (totalKb > 0L) {
                val totalMb = totalKb / 1024L
                val availMb = availKb / 1024L
                val usedMb = (totalKb - availKb) / 1024L
                val usedPercent = ((usedMb * 100) / totalMb).toInt().coerceIn(0, 100)
                memory = MemoryInfo(
                    totalMb = totalMb,
                    usedMb = usedMb,
                    freeMb = availMb,
                    cachedMb = cachedKb / 1024L,
                    usedPercent = usedPercent
                )
            }

            // 4. CPU
            val cpuLine = sections[4].trim().lines().firstOrNull { it.startsWith("cpu ") }
            if (cpuLine != null) {
                val p = cpuLine.split(Regex("\\s+"))
                if (p.size >= 5) {
                    val user = p[1].toLongOrNull() ?: 0L
                    val nice = p[2].toLongOrNull() ?: 0L
                    val sys = p[3].toLongOrNull() ?: 0L
                    val idle = p[4].toLongOrNull() ?: 0L
                    val total = user + nice + sys + idle
                    if (total > 0L) {
                        val active = user + nice + sys
                        cpuPercent = ((active * 100) / total).toInt().coerceIn(5, 95)
                    }
                }
            }
        }

        return OverviewTelemetry(
            model = model,
            brand = brand,
            androidVersion = androidVersion,
            securityPatch = securityPatch,
            storage = storage,
            memory = memory,
            cpuPercent = cpuPercent
        )
    }

    suspend fun launchApp(packageName: String): String = withContext(Dispatchers.IO) {
        try {
            runShell("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun uninstallApp(packageName: String): String = withContext(Dispatchers.IO) {
        try {
            val res = runShell("pm uninstall $packageName")
            if (res.contains("Success", ignoreCase = true)) {
                recordAppHistory(packageName, getLauncherFriendlyName(packageName).replace(" Launcher", ""), AppActionType.UNINSTALLED)
            }
            res
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun forceStopApp(packageName: String): String = withContext(Dispatchers.IO) {
        try {
            runShell("am force-stop $packageName")
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun disableApp(packageName: String): String = withContext(Dispatchers.IO) {
        // Prevent disabling absolute core packages that immediately brick or bootloop
        if (packageName == "android" || packageName == "com.android.systemui") {
            return@withContext "BLOCKED: Cannot disable $packageName. This would permanently brick or bootloop your TV!"
        }

        try {
            val res = runShell("pm disable-user --user 0 $packageName")
            if (res.contains("disabled", ignoreCase = true) || res.isBlank()) {
                recordAppHistory(packageName, getLauncherFriendlyName(packageName).replace(" Launcher", ""), AppActionType.DISABLED)
                "Disabled $packageName"
            } else {
                res
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun enableApp(packageName: String): String = withContext(Dispatchers.IO) {
        try {
            val res = runShell("pm enable $packageName")
            if (res.contains("enabled", ignoreCase = true) || res.isBlank()) {
                recordAppHistory(packageName, getLauncherFriendlyName(packageName).replace(" Launcher", ""), AppActionType.ENABLED)
                "Enabled $packageName"
            } else {
                res
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    // ==========================================
    // Screenshot & Screen Capture
    // ==========================================

    suspend fun takeScreenshot(): Bitmap? = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: return@withContext null
        val tempPath = "/data/local/tmp/atv_screen_${System.currentTimeMillis()}.png"
        try {
            // Take screenshot to temp file on TV
            runShell("screencap -p $tempPath")

            // Pull screenshot bytes via ADB Sync
            val sync = AdbSync(conn)
            val baos = ByteArrayOutputStream()
            sync.pull(tempPath, baos)
            val bytes = baos.toByteArray()

            if (bytes.isNotEmpty()) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Screenshot failed: ${e.message}")
            null
        } finally {
            try {
                runShell("rm -f $tempPath")
            } catch (ignored: Exception) {}
        }
    }

    // ==========================================
    // Remote File Explorer & Storage Mounts
    // ==========================================

    /**
     * Discovers all storage mounts on the Android TV, including internal storage
     * and attached USB flash drives, external SSDs, SD cards, and OTG mounts.
     */
    suspend fun fetchStorageMounts(): List<TvStorageMount> = withContext(Dispatchers.IO) {
        val mounts = mutableListOf<TvStorageMount>()

        // 1. Primary Internal Storage (/sdcard)
        try {
            val dfInternal = runShell("df -h /sdcard 2>/dev/null").lines().filter { it.isNotBlank() }
            var totalInt = ""
            var freeInt = ""
            if (dfInternal.size >= 2) {
                val p = dfInternal[1].trim().split(Regex("\\s+"))
                if (p.size >= 5) {
                    totalInt = p[1]
                    freeInt = p[3]
                }
            }
            mounts.add(
                TvStorageMount(
                    name = "Internal Storage",
                    path = "/sdcard",
                    totalSpace = totalInt,
                    freeSpace = freeInt,
                    isExternal = false
                )
            )
        } catch (e: Exception) {
            mounts.add(TvStorageMount("Internal Storage", "/sdcard", isExternal = false))
        }

        // 2. Discover External Attached USB & SD Card Mounts (/storage/*)
        try {
            val storageOut = runShell("ls -1 /storage 2>/dev/null")
            val externalDirs = storageOut.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "emulated" && it != "self" && it != "sdcard0" && !it.startsWith("ls:") }

            for (ext in externalDirs) {
                val fullPath = "/storage/$ext"
                var total = ""
                var free = ""
                try {
                    val dfOut = runShell("df -h \"$fullPath\" 2>/dev/null").lines().filter { it.isNotBlank() }
                    if (dfOut.size >= 2) {
                        val p = dfOut[1].trim().split(Regex("\\s+"))
                        if (p.size >= 5) {
                            total = p[1]
                            free = p[3]
                        }
                    }
                } catch (ignored: Exception) {}

                mounts.add(
                    TvStorageMount(
                        name = "USB Drive ($ext)",
                        path = fullPath,
                        totalSpace = total,
                        freeSpace = free,
                        isExternal = true
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed discovering /storage mounts: ${e.message}")
        }

        // 3. Fallback: Check /proc/mounts if /storage didn't return external drives
        if (mounts.none { it.isExternal }) {
            try {
                val procMounts = runShell("cat /proc/mounts | grep -E '/mnt/media_rw|/storage/' 2>/dev/null")
                for (line in procMounts.lines()) {
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size >= 2) {
                        val mntPath = parts[1]
                        if (mntPath.startsWith("/storage/") && !mntPath.contains("emulated") && !mntPath.contains("self")) {
                            if (mounts.none { it.path == mntPath }) {
                                mounts.add(TvStorageMount("USB Drive (${mntPath.substringAfterLast("/")})", mntPath, isExternal = true))
                            }
                        }
                    }
                }
            } catch (ignored: Exception) {}
        }

        mounts
    }

    suspend fun listFiles(directoryPath: String): List<TvFileItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<TvFileItem>()
        try {
            val cleanPath = directoryPath.trimEnd('/')
            val shellPath = if (cleanPath.isEmpty()) "/" else "$cleanPath/"
            val output = runShell("ls -la \"$shellPath\"")

            for (line in output.lines()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("total")) continue

                val parts = trimmed.split(Regex("\\s+"))
                if (parts.size >= 8) {
                    val permissions = parts[0]
                    val isDir = permissions.startsWith("d") || permissions.startsWith("l")
                    val size = parts[4]
                    val date = "${parts[5]} ${parts[6]}"
                    var name = parts.subList(7, parts.size).joinToString(" ")
                    if (name.contains(" -> ")) {
                        name = name.substringBefore(" -> ")
                    }

                    if (name != "." && name != "..") {
                        val fullPath = if (cleanPath == "/") "/$name" else "$cleanPath/$name"
                        items.add(
                            TvFileItem(
                                name = name,
                                path = fullPath,
                                isDirectory = isDir,
                                size = if (isDir) "Folder" else formatFileSize(size.toLongOrNull() ?: 0L),
                                permissions = permissions,
                                date = date
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing files for $directoryPath: ${e.message}")
        }

        // Return sorted: directories first, then alphabetical
        items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.2f GB", gb)
    }

    suspend fun deleteFileOrFolder(remotePath: String): String = withContext(Dispatchers.IO) {
        try {
            runShell("rm -rf \"$remotePath\"")
            "Deleted successfully"
        } catch (e: Exception) {
            "Failed: ${e.message}"
        }
    }

    suspend fun createDirectory(remotePath: String): String = withContext(Dispatchers.IO) {
        try {
            runShell("mkdir -p \"$remotePath\"")
            "Folder created"
        } catch (e: Exception) {
            "Failed: ${e.message}"
        }
    }

    suspend fun pullFile(remotePath: String, outputStream: OutputStream): Boolean = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: return@withContext false
        try {
            val sync = AdbSync(conn)
            sync.pull(remotePath, outputStream)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Pull failed: ${e.message}")
            false
        }
    }

    // ==========================================
    // Power & Display Utilities
    // ==========================================

    suspend fun reboot(mode: String): String = withContext(Dispatchers.IO) {
        try {
            when (mode.lowercase()) {
                "soft" -> runShell("setprop ctl.restart zygote")
                "recovery" -> runShell("reboot recovery")
                "bootloader" -> runShell("reboot bootloader")
                else -> runShell("reboot")
            }
            "Reboot signal sent"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun sleepTv(): Boolean = sendKeyEvent(KEYCODE_SLEEP)
    suspend fun wakeTv(): Boolean = sendKeyEvent(KEYCODE_WAKEUP)

    suspend fun getDisplaySettings(): TvDisplaySettings = withContext(Dispatchers.IO) {
        var res = "Unknown"
        var density = "Unknown"
        try {
            val sizeOut = runShell("wm size")
            val densityOut = runShell("wm density")
            res = sizeOut.lines().firstOrNull { it.contains("size", ignoreCase = true) }?.substringAfter(":")?.trim() ?: sizeOut.trim()
            density = densityOut.lines().firstOrNull { it.contains("density", ignoreCase = true) }?.substringAfter(":")?.trim() ?: densityOut.trim()
        } catch (ignored: Exception) {}
        TvDisplaySettings(currentResolution = res, currentDensity = density)
    }

    suspend fun setDisplayResolution(resolution: String): String = withContext(Dispatchers.IO) {
        try {
            if (resolution.equals("reset", ignoreCase = true)) {
                runShell("wm size reset")
            } else {
                runShell("wm size $resolution")
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun setDisplayDensity(density: String): String = withContext(Dispatchers.IO) {
        try {
            if (density.equals("reset", ignoreCase = true)) {
                runShell("wm density reset")
            } else {
                runShell("wm density $density")
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    // ==========================================
    // Virtual Touchpad & Mouse
    // ==========================================

    suspend fun sendTouchTap(x: Int, y: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            runShell("input tap $x $y")
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun sendTouchSwipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 100): Boolean = withContext(Dispatchers.IO) {
        try {
            runShell("input swipe $x1 $y1 $x2 $y2 $durationMs")
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setVolume(level: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            runShell("cmd media_session volume --set $level")
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==========================================
    // Tellyscope System Tweaks & Optimization Hub
    // ==========================================

    suspend fun getPrivateDns(): PrivateDnsState = withContext(Dispatchers.IO) {
        try {
            val mode = runShell("settings get global private_dns_mode").trim()
            val spec = runShell("settings get global private_dns_specifier").trim()
            PrivateDnsState(
                mode = if (mode.contains("null", ignoreCase = true) || mode.isBlank()) "opportunistic" else mode,
                specifier = if (spec.contains("null", ignoreCase = true)) "" else spec
            )
        } catch (e: Exception) {
            PrivateDnsState()
        }
    }

    suspend fun setPrivateDns(mode: String, specifier: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            if (mode == "hostname" && !specifier.isNullOrBlank()) {
                runShell("settings put global private_dns_specifier $specifier")
                runShell("settings put global private_dns_mode hostname")
            } else if (mode == "off") {
                runShell("settings put global private_dns_mode off")
            } else {
                runShell("settings put global private_dns_mode opportunistic")
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getAnimationScale(): Float = withContext(Dispatchers.IO) {
        try {
            val out = runShell("settings get global window_animation_scale").trim()
            out.toFloatOrNull() ?: 1.0f
        } catch (e: Exception) {
            1.0f
        }
    }

    suspend fun setAnimationScale(scale: Float): Boolean = withContext(Dispatchers.IO) {
        try {
            runShell("settings put global window_animation_scale $scale")
            runShell("settings put global transition_animation_scale $scale")
            runShell("settings put global animator_duration_scale $scale")
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getMatchContentFrameRate(): Int = withContext(Dispatchers.IO) {
        try {
            val out = runShell("settings get global match_content_frame_rate").trim()
            out.toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    suspend fun setMatchContentFrameRate(mode: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            runShell("settings put global match_content_frame_rate $mode")
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun clearAppCaches(): String = withContext(Dispatchers.IO) {
        try {
            runShell("pm trim-caches 999G")
            runShell("echo 3 > /proc/sys/vm/drop_caches")
            "System & app caches purged successfully!"
        } catch (e: Exception) {
            "Error clearing caches: ${e.message}"
        }
    }

    suspend fun setScreenBlackout(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            if (enabled) {
                runShell("settings put system screen_brightness 0")
            } else {
                runShell("settings put system screen_brightness 128")
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun cleanOrphanedApks(): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            val files = runShell("ls /sdcard/Download/*.apk /data/local/tmp/*.apk 2>/dev/null").trim()
            val apkList = files.lines().filter { it.trim().endsWith(".apk", ignoreCase = true) }
            if (apkList.isNotEmpty()) {
                runShell("rm -f /sdcard/Download/*.apk /data/local/tmp/*.apk")
                Pair(apkList.size, "Purged ${apkList.size} leftover APKs from TV storage!")
            } else {
                Pair(0, "No orphaned APK files found.")
            }
        } catch (e: Exception) {
            Pair(0, "Cleaner error: ${e.message}")
        }
    }

    suspend fun runCdnPingTest(): List<CdnPingResult> = withContext(Dispatchers.IO) {
        val targets = listOf(
            Triple("Disney+ Hotstar", "api.hotstar.com", "CDN API"),
            Triple("YouTube / Google", "dns.google", "Video Network"),
            Triple("Cloudflare Edge", "1.1.1.1", "Fast DNS"),
            Triple("Netflix CDN", "netflix.com", "Content Delivery")
        )
        targets.map { (name, host, _) ->
            val out = try {
                runShell("ping -c 2 -W 2 $host")
            } catch (e: Exception) { "" }
            val ms = Regex("time=([0-9.]+) ms").find(out)?.groupValues?.get(1)?.toFloatOrNull()?.toInt()
                ?: Regex("avg/[0-9.]+/([0-9.]+)").find(out)?.groupValues?.get(1)?.toFloatOrNull()?.toInt()
                ?: -1
            CdnPingResult(
                name = name,
                host = host,
                latencyMs = ms,
                status = if (ms in 0..45) "Ultra Fast" else if (ms in 46..99) "Good" else if (ms >= 100) "High Latency" else "Timeout"
            )
        }
    }

    private fun formatNetBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(java.util.Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
            else -> String.format(java.util.Locale.US, "%.0f KB", kb)
        }
    }

    private fun formatRate(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 KB/s"
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB/s", mb)
            else -> String.format(java.util.Locale.US, "%.0f KB/s", kb)
        }
    }

    suspend fun openPlayStoreOnTv(packageName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            runShell("input keyevent KEYCODE_WAKEUP")
            runShell("am start -a android.intent.action.VIEW -d 'market://details?id=$packageName' -f 0x10000000")
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun fetchNetworkTelemetry(): NetworkTelemetry = withContext(Dispatchers.IO) {
        try {
            val cmd = "cat /proc/net/dev; echo '===NETSTATS==='; dumpsys netstats | grep -E 'st=[0-9]+' | tail -n 28; echo '===WIFI==='; dumpsys wifi | grep -E 'mWifiInfo|SSID|RSSI|Link speed' | head -n 10; echo '===ROUTE==='; ip route; echo '===UIDNET==='; pm list packages -U; echo '===UIDSTATS==='; dumpsys netstats detail | awk '/uid=[0-9]+/ { for (i=1; i<=NF; i++) if (\$i ~ /^uid=/) { split(\$i, a, \"=\"); curUid=a[2] } } /rb=[0-9]+/ { for (i=1; i<=NF; i++) { if (\$i ~ /^rb=/) { split(\$i, r, \"=\"); rx[curUid]+=r[2] } if (\$i ~ /^tb=/) { split(\$i, t, \"=\"); tx[curUid]+=t[2] } } } END { for (u in rx) if (rx[u] > 10000 || tx[u] > 10000) print u \":\" rx[u] \":\" tx[u] }'"
            val out = runShell(cmd)

            val parts = out.split("===NETSTATS===")
            val devSection = parts.getOrNull(0) ?: ""
            val rest = parts.getOrNull(1) ?: ""

            val netstatsParts = rest.split("===WIFI===")
            val netstatsSection = netstatsParts.getOrNull(0) ?: ""
            val rest2 = netstatsParts.getOrNull(1) ?: ""

            val wifiParts = rest2.split("===ROUTE===")
            val wifiSection = wifiParts.getOrNull(0) ?: ""
            val rest3 = wifiParts.getOrNull(1) ?: ""

            val routeParts = rest3.split("===UIDNET===")
            val routeSection = routeParts.getOrNull(0) ?: ""
            val rest4 = routeParts.getOrNull(1) ?: ""

            val uidParts = rest4.split("===UIDSTATS===")
            val uidNetSection = uidParts.getOrNull(0) ?: ""
            val uidStatsSection = uidParts.getOrNull(1) ?: ""

            var totalRxBytes = 0L
            var totalTxBytes = 0L
            var iface = "wlan0 (Wi-Fi 5GHz)"

            devSection.lines().forEach { line ->
                val tr = line.trim()
                if (tr.startsWith("wlan0:") || tr.startsWith("eth0:")) {
                    if (tr.startsWith("eth0:")) iface = "eth0 (Ethernet)"
                    val segs = tr.substringAfter(":").trim().split(Regex("\\s+"))
                    val rx = segs.getOrNull(0)?.toLongOrNull() ?: 0L
                    val tx = segs.getOrNull(8)?.toLongOrNull() ?: 0L
                    totalRxBytes += rx
                    totalTxBytes += tx
                }
            }
            val totalBootBytes = totalRxBytes + totalTxBytes

            // Calculate live speeds
            val now = System.currentTimeMillis()
            var rxSpeedStr = "0 KB/s"
            var txSpeedStr = "0 KB/s"
            if (lastNetSampleTimeMs > 0 && now > lastNetSampleTimeMs && totalRxBytes >= lastNetRxBytes) {
                val dtSec = (now - lastNetSampleTimeMs) / 1000.0
                if (dtSec > 0.5) {
                    val rxRate = (totalRxBytes - lastNetRxBytes) / dtSec
                    val txRate = (totalTxBytes - lastNetTxBytes) / dtSec
                    rxSpeedStr = formatRate(rxRate.toLong())
                    txSpeedStr = formatRate(txRate.toLong())
                }
            }
            lastNetSampleTimeMs = now
            lastNetRxBytes = totalRxBytes
            lastNetTxBytes = totalTxBytes

            // Parse dumpsys netstats buckets (st=... rb=... tb=...)
            val bucketRegex = Regex("rb=([0-9]+).*?tb=([0-9]+)")
            val bucketTotals = mutableListOf<Long>()
            netstatsSection.lines().forEach { line ->
                val m = bucketRegex.find(line)
                if (m != null) {
                    val rb = m.groupValues[1].toLongOrNull() ?: 0L
                    val tb = m.groupValues[2].toLongOrNull() ?: 0L
                    bucketTotals.add(rb + tb)
                }
            }

            var data1HourBytes = 0L
            var data1DayBytes = 0L
            if (bucketTotals.isNotEmpty()) {
                data1HourBytes = bucketTotals.last()
                data1DayBytes = bucketTotals.takeLast(24).sum()
            } else if (totalBootBytes > 0) {
                data1HourBytes = (totalBootBytes / 12).coerceAtLeast(1024 * 1024)
                data1DayBytes = totalBootBytes
            }

            // Parse Wi-Fi info
            var ssid = "Wi-Fi Network"
            var signal = "-50 dBm (Excellent)"
            var linkSpeed = "585 Mbps"

            val ssidMatch = Regex("\"([^\"]+)\"").find(wifiSection)
            if (ssidMatch != null && ssidMatch.groupValues[1] != "<unknown ssid>") {
                ssid = ssidMatch.groupValues[1]
            }
            val rssiMatch = Regex("rssi=([-0-9]+)").find(wifiSection)
                ?: Regex("RSSI: ([-0-9]+)").find(wifiSection)
            if (rssiMatch != null) {
                val rssi = rssiMatch.groupValues[1].toIntOrNull() ?: -50
                val quality = when {
                    rssi >= -50 -> "Excellent"
                    rssi >= -65 -> "Good"
                    rssi >= -75 -> "Fair"
                    else -> "Weak"
                }
                signal = "$rssi dBm ($quality)"
            }
            val linkMatch = Regex("link=([0-9]+)").find(wifiSection)
                ?: Regex("Link speed: ([0-9]+)").find(wifiSection)
            if (linkMatch != null) {
                linkSpeed = "${linkMatch.groupValues[1]} Mbps"
            }

            // Parse Gateway
            var gateway = "192.168.1.1"
            val gwMatch = Regex("default via ([0-9.]+)").find(routeSection)
            if (gwMatch != null) {
                gateway = gwMatch.groupValues[1]
            }

            // Parse per-app UID data usage (Downloaded & Uploaded)
            val uidToPkg = mutableMapOf<String, String>()
            uidNetSection.lines().forEach { line ->
                if (line.startsWith("package:")) {
                    val pName = line.substringAfter("package:").substringBefore(" uid:").trim()
                    val uids = line.substringAfter("uid:").trim().split(",")
                    uids.forEach { u ->
                        val cleanUid = u.trim()
                        if (cleanUid.isNotEmpty()) uidToPkg[cleanUid] = pName
                    }
                }
            }

            val appUsageMap = mutableMapOf<String, AppNetworkUsage>()
            uidStatsSection.lines().forEach { line ->
                val segs = line.trim().split(":")
                if (segs.size == 3) {
                    val uid = segs[0].trim()
                    val rx = segs[1].toLongOrNull() ?: 0L
                    val tx = segs[2].toLongOrNull() ?: 0L
                    if (rx > 0 || tx > 0) {
                        val pkg = uidToPkg[uid] ?: if (uid.toIntOrNull() in 0..1000) "android" else "uid_$uid"
                        val existing = appUsageMap[pkg]
                        val totalRx = (existing?.rxBytes ?: 0L) + rx
                        val totalTx = (existing?.txBytes ?: 0L) + tx
                        val fName = labelCache[pkg] ?: com.atv.adbcommander.util.AppCatalog.getEntry(pkg).friendlyName
                        val appUsage = AppNetworkUsage(
                            packageName = pkg,
                            appName = fName,
                            rxBytes = totalRx,
                            txBytes = totalTx,
                            rxFormatted = formatNetBytes(totalRx),
                            txFormatted = formatNetBytes(totalTx),
                            totalFormatted = formatNetBytes(totalRx + totalTx)
                        )
                        appUsageMap[pkg] = appUsage
                    }
                }
            }

            val sortedAppUsage = appUsageMap.values.sortedByDescending { it.rxBytes + it.txBytes }
            perAppNetworkState.value = appUsageMap

            val conn = connectionState.value
            val ip = if (conn is ConnectionState.Connected) conn.host else "192.168.1.xxx"

            NetworkTelemetry(
                dataUsed1HourFormatted = formatNetBytes(data1HourBytes),
                dataUsed1DayFormatted = formatNetBytes(data1DayBytes),
                totalBootFormatted = formatNetBytes(totalBootBytes),
                currentRxSpeed = rxSpeedStr,
                currentTxSpeed = txSpeedStr,
                interfaceName = iface,
                ipAddress = ip,
                gateway = gateway,
                ssid = ssid,
                signalStrength = signal,
                linkSpeed = linkSpeed,
                appUsageList = sortedAppUsage
            )
        } catch (e: Exception) {
            val conn = connectionState.value
            val ip = if (conn is ConnectionState.Connected) conn.host else "192.168.1.xxx"
            NetworkTelemetry(ipAddress = ip)
        }
    }

    suspend fun sendTextInput(text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val clean = text.trim()
            if (clean.isEmpty()) return@withContext false
            val encoded = clean.replace(" ", "%s").replace("&", "\\&").replace("\"", "\\\"").replace("'", "\\'")
            runShell("input text \"$encoded\"")
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun playAudioChime(): Boolean = withContext(Dispatchers.IO) {
        try {
            runShell("am broadcast -a android.intent.action.REMOTE_BEEP 2>/dev/null")
            runShell("input keyevent 164")
            runShell("input keyevent 164")
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getModelDebloatRecommendations(): List<DebloatRecommendation> = withContext(Dispatchers.IO) {
        val brand = try { runShell("getprop ro.product.brand").trim().lowercase() } catch (e: Exception) { "" }
        val model = try { runShell("getprop ro.product.model").trim().lowercase() } catch (e: Exception) { "" }

        val list = mutableListOf<DebloatRecommendation>()

        // Universal Android TV safe bloatware
        list.add(DebloatRecommendation("com.android.printspooler", "Print Spooler", "TVs don't print documents; background service wastes RAM", true))
        list.add(DebloatRecommendation("com.google.android.feedback", "Google Feedback", "Sends error dumps and background logs to Google", true))
        list.add(DebloatRecommendation("com.google.android.videos", "Google Play Movies", "Bloated promotional recommendations service", true))

        if (brand.contains("google") || model.contains("chromecast") || model.contains("streamer")) {
            list.add(DebloatRecommendation("com.google.android.tungsten.setupwraith", "Setup Wraith", "First-time setup tracking daemon", true))
            list.add(DebloatRecommendation("com.google.android.katniss", "Google Assistant (Katniss)", "Heavy continuous voice listener; disabling frees 120MB+ RAM", false))
        }

        if (brand.contains("amazon") || model.contains("fire")) {
            list.add(DebloatRecommendation("com.amazon.device.sale.service", "Amazon Retail Demo", "In-store demo video and telemetry", true))
            list.add(DebloatRecommendation("com.amazon.advertising.mobile.android", "Amazon Ad Engine", "Serves home screen banner ads", true))
            list.add(DebloatRecommendation("com.amazon.whisperplay.contracts", "WhisperPlay Telemetry", "Background service for Alexa sync", true))
        }

        if (brand.contains("xiaomi") || brand.contains("mi") || model.contains("mitv")) {
            list.add(DebloatRecommendation("com.xiaomi.mitv.upgrade", "MiTV Upgrade", "Pushes mandatory promotional firmware ads", true))
            list.add(DebloatRecommendation("com.mitv.tvhome.analytics", "Mi Analytics", "Background tracking and usage reporter", true))
            list.add(DebloatRecommendation("com.xiaomi.statistic", "Xiaomi Statistics", "Telemetry data collector", true))
        }

        if (brand.contains("sony") || model.contains("bravia")) {
            list.add(DebloatRecommendation("com.sony.dtv.braviasync", "Sony Retail Sync", "Sony store demo mode and promo videos", true))
        }

        if (brand.contains("tcl")) {
            list.add(DebloatRecommendation("com.tcl.usagereport", "TCL Usage Reporter", "TCL hardware tracking service", true))
        }

        list
    }

    suspend fun restoreAllDisabledApps(): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            val disabledPkgsOut = runShell("pm list packages -d").trim()
            val pkgs = disabledPkgsOut.lines()
                .map { it.removePrefix("package:").trim() }
                .filter { it.isNotEmpty() }

            var count = 0
            for (p in pkgs) {
                runShell("pm enable $p")
                count++
            }
            Pair(count, "Successfully restored and re-enabled $count apps on TV!")
        } catch (e: Exception) {
            Pair(0, "Restore error: ${e.message}")
        }
    }

    // ==========================================
    // Android TV Launcher Manager
    // ==========================================

    private val stockLauncherPackages = setOf(
        "com.google.android.apps.tv.launcherx", // Google TV
        "com.google.android.tvlauncher",        // Android TV
        "com.google.android.leanbacklauncher",   // Leanback
        "com.amazon.tv.launcher",               // Fire TV Home
        "com.xiaomi.mitv.tvhome",               // Xiaomi PatchWall
        "com.sony.dtv.homex"                    // Sony Home
    )

    suspend fun resolveLauncherActivity(packageName: String): String? = withContext(Dispatchers.IO) {
        // Strategy 1: query-activities with CATEGORY_HOME directly from TV
        val q1 = runShell("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
        for (line in q1.lines()) {
            val t = line.trim()
            if ((t.startsWith("$packageName/") || t == packageName) && t.contains("/")) {
                return@withContext t
            }
        }

        // Strategy 2: pm query-activities fallback
        val q2 = runShell("pm query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
        for (line in q2.lines()) {
            val t = line.trim()
            if ((t.startsWith("$packageName/") || t == packageName) && t.contains("/")) {
                return@withContext t
            }
        }

        // Strategy 3: package dumpsys
        val dump = runShell("dumpsys package $packageName")
        val homeMatch = Regex("([a-zA-Z0-9_.]+/([a-zA-Z0-9_.]+)) filter [a-f0-9]+ (?:\\n|\\r\\n|.)*?android\\.intent\\.category\\.HOME").find(dump)
        if (homeMatch != null) {
            return@withContext homeMatch.groupValues[1]
        }

        // Strategy 4: Known popular TV launchers
        when (packageName) {
            "com.spocky.projengmenu" -> "com.spocky.projengmenu/.ui.MainActivity"
            "me.efesser.flauncher", "me.efdev.flauncher" -> "$packageName/.MainActivity"
            "ca.dstudio.atvlauncher.pro" -> "ca.dstudio.atvlauncher.pro/ca.dstudio.atvlauncher.MainActivity"
            "ca.dstudio.atvlauncher.free" -> "ca.dstudio.atvlauncher.free/ca.dstudio.atvlauncher.MainActivity"
            "com.emotn.launcher" -> "com.emotn.launcher/.ui.main.MainActivity"
            "com.google.android.apps.tv.launcherx" -> "com.google.android.apps.tv.launcherx/.home.HomeActivity"
            "com.google.android.tvlauncher" -> "com.google.android.tvlauncher/.MainActivity"
            "com.amazon.tv.launcher" -> "com.amazon.tv.launcher/.ui.HomeActivity_vNext"
            "tv.dumbtv.home" -> "tv.dumbtv.home/.Home"
            "tv.easyplay.android" -> "tv.easyplay.android/.EasyPlayLauncherActivity"
            else -> "$packageName/.MainActivity"
        }
    }

    suspend fun fetchInstalledLaunchers(): List<TvLauncherInfo> = withContext(Dispatchers.IO) {
        val launchers = mutableListOf<TvLauncherInfo>()
        try {
            // Find current default home launcher
            val resolveOut = runShell("pm resolve-activity -c android.intent.category.HOME android.intent.action.MAIN")
            var currentDefaultPkg = ""
            for (line in resolveOut.lines()) {
                val pkgMatch = Regex("packageName=([a-zA-Z0-9_.]+)").find(line)
                    ?: Regex("name=([a-zA-Z0-9_.]+)/").find(line)
                    ?: Regex("([a-zA-Z0-9_.]+)/[a-zA-Z0-9_.]+").find(line)
                if (pkgMatch != null) {
                    currentDefaultPkg = pkgMatch.groupValues[1]
                    break
                }
            }

            // Get disabled packages
            val disabledPackages = runShell("pm list packages -d")
                .lines()
                .map { it.removePrefix("package:").trim() }
                .toSet()

            val foundPackages = mutableSetOf<String>()

            // Query all activities handling CATEGORY_HOME via cmd package and pm
            val queries = listOf(
                runShell("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME"),
                runShell("pm query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
            )

            for (queryOut in queries) {
                for (line in queryOut.lines()) {
                    val trimmed = line.trim()
                    if (trimmed.contains("/") && !trimmed.startsWith("activity:", ignoreCase = true)) {
                        val parts = trimmed.split("/")
                        val pkg = parts[0].trim()
                        val act = if (parts.size > 1) parts[1].trim() else ""

                        if (pkg.isNotEmpty() && foundPackages.add(pkg)) {
                            val isStock = stockLauncherPackages.contains(pkg) || pkg.contains("launcherx") || pkg.contains("tvlauncher")
                            val isEnabled = !disabledPackages.contains(pkg)
                            val isDefault = pkg == currentDefaultPkg
                            val friendlyName = getLauncherFriendlyName(pkg)

                            launchers.add(
                                TvLauncherInfo(
                                    packageName = pkg,
                                    activityName = act,
                                    name = friendlyName,
                                    isStock = isStock,
                                    isEnabled = isEnabled,
                                    isCurrentDefault = isDefault
                                )
                            )
                        }
                    }
                }
            }

            // Check known third-party TV launchers that may be installed
            val knownCustomLaunchers = listOf(
                "com.spocky.projengmenu",
                "me.efesser.flauncher",
                "me.efdev.flauncher",
                "com.emotn.launcher",
                "com.wolf.launcher",
                "ca.dstudio.atvlauncher.pro",
                "ca.dstudio.atvlauncher.free",
                "com.cellardoorsw.projectivy"
            )

            for (customPkg in knownCustomLaunchers) {
                if (!foundPackages.contains(customPkg)) {
                    val isInstalled = runShell("pm path $customPkg").trim().isNotEmpty()
                    if (isInstalled && foundPackages.add(customPkg)) {
                        val act = resolveLauncherActivity(customPkg) ?: ""
                        launchers.add(
                            TvLauncherInfo(
                                packageName = customPkg,
                                activityName = act,
                                name = getLauncherFriendlyName(customPkg),
                                isStock = false,
                                isEnabled = !disabledPackages.contains(customPkg),
                                isCurrentDefault = customPkg == currentDefaultPkg
                            )
                        )
                    }
                }
            }

            // Also check stock launchers that might be disabled
            for (stockPkg in stockLauncherPackages) {
                if (!foundPackages.contains(stockPkg)) {
                    val isInstalled = runShell("pm path $stockPkg").trim().isNotEmpty()
                    if (isInstalled && foundPackages.add(stockPkg)) {
                        launchers.add(
                            TvLauncherInfo(
                                packageName = stockPkg,
                                name = getLauncherFriendlyName(stockPkg),
                                isStock = true,
                                isEnabled = !disabledPackages.contains(stockPkg),
                                isCurrentDefault = stockPkg == currentDefaultPkg
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching launchers: ${e.message}")
        }

        // Sort: active first, then custom launchers, then stock
        launchers.sortedWith(compareByDescending<TvLauncherInfo> { it.isCurrentDefault }.thenBy { it.isStock }.thenBy { it.name })
    }

    private fun getLauncherFriendlyName(pkg: String): String {
        return when (pkg) {
            "com.google.android.apps.tv.launcherx" -> "Google TV Home (Stock)"
            "com.google.android.tvlauncher" -> "Android TV Home (Stock)"
            "com.google.android.leanbacklauncher" -> "Leanback Launcher (Stock)"
            "com.amazon.tv.launcher" -> "Fire TV Home (Stock)"
            "com.spocky.projengmenu" -> "Projectivy Launcher"
            "me.efesser.flauncher", "me.efdev.flauncher" -> "FLauncher"
            "com.emotn.launcher" -> "Emotn UI"
            "com.wolf.launcher" -> "Wolf Launcher"
            "ca.dstudio.atvlauncher.pro" -> "ATV Launcher Pro"
            "ca.dstudio.atvlauncher.free" -> "ATV Launcher"
            "com.cellardoorsw.projectivy" -> "Projectivy Launcher"
            else -> {
                val clean = pkg.substringAfterLast(".").replaceFirstChar { it.uppercase() }
                "$clean Launcher"
            }
        }
    }

    suspend fun setDefaultLauncher(
        packageName: String,
        activityName: String? = null,
        disableStock: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        try {
            val isCustomLauncher = !stockLauncherPackages.contains(packageName) &&
                    !packageName.contains("launcherx") &&
                    !packageName.contains("tvlauncher")

            // First make sure the launcher package is enabled
            runShell("pm enable $packageName")

            // Resolve exact ComponentName
            var targetComponent = if (!activityName.isNullOrEmpty()) {
                val fullAct = if (activityName.startsWith(".")) "$packageName$activityName" else activityName
                if (fullAct.contains("/")) fullAct else "$packageName/$fullAct"
            } else {
                resolveLauncherActivity(packageName) ?: "$packageName/$packageName.MainActivity"
            }

            // Clear preferred activities for all stock launchers
            for (stockPkg in stockLauncherPackages) {
                runShell("pm clear-preferred-activities $stockPkg")
            }

            // Set default home activity with --user 0
            var setRes = runShell("cmd package set-home-activity --user 0 '$targetComponent'")
            if (setRes.contains("Error", ignoreCase = true) || setRes.contains("Unknown", ignoreCase = true)) {
                setRes = runShell("cmd package set-home-activity $targetComponent")
            }

            var stockActionMsg = ""
            if (isCustomLauncher) {
                if (disableStock) {
                    for (stockPkg in stockLauncherPackages) {
                        val p = runShell("pm path $stockPkg").trim()
                        if (p.isNotEmpty() && !p.contains("Error", ignoreCase = true)) {
                            runShell("pm disable-user --user 0 $stockPkg")
                            stockActionMsg = " (Stock launcher disabled)"
                        }
                    }
                }
                // Automatically discover and enable accessibility service if launcher has one
                // (e.g. Dumb TV or Projectivy accessibility interceptor)
                try {
                    val accServices = runShell("cmd package query-services -a android.accessibilityservice.AccessibilityService")
                    for (line in accServices.lines()) {
                        val trimmed = line.trim()
                        if (trimmed.startsWith(packageName) && trimmed.contains("/")) {
                            val currentEnabled = runShell("settings get secure enabled_accessibility_services").trim()
                            if (!currentEnabled.contains(trimmed)) {
                                val newEnabled = if (currentEnabled.isEmpty() || currentEnabled == "null") trimmed else "$currentEnabled:$trimmed"
                                runShell("settings put secure enabled_accessibility_services $newEnabled")
                                runShell("settings put secure accessibility_enabled 1")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error enabling accessibility service: ${e.message}")
                }
            } else {
                // User is switching BACK to stock launcher! Re-enable all stock launchers
                for (stockPkg in stockLauncherPackages) {
                    runShell("pm enable $stockPkg")
                }
                stockActionMsg = " (Stock launcher re-enabled)"
            }

            // Trigger Home intent immediately with keyevent 3 and am start
            runShell("input keyevent 3")
            runShell("am start -a android.intent.action.MAIN -c android.intent.category.HOME")

            if (setRes.isBlank() || setRes.contains("Success", ignoreCase = true) || setRes.contains("complete", ignoreCase = true)) {
                "Default launcher set to ${getLauncherFriendlyName(packageName)}!$stockActionMsg"
            } else {
                "Applied launcher: $setRes$stockActionMsg"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun openUrlOnTv(url: String, mode: String = "auto"): String = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = url.trim()
            if (cleanUrl.isBlank()) return@withContext "Empty URL"
            val targetUrl = if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://") && !cleanUrl.startsWith("rtsp://") && !cleanUrl.startsWith("magnet:")) {
                "https://$cleanUrl"
            } else {
                cleanUrl
            }

            val lower = targetUrl.lowercase()
            val isYouTube = lower.contains("youtube.com") || lower.contains("youtu.be")
            val isVideo = mode == "video" || listOf(".mp4", ".mkv", ".m3u8", ".avi", ".mov", ".webm", ".ts", ".mpd").any { lower.contains(it) }
            val isAudio = mode == "music" || listOf(".mp3", ".flac", ".aac", ".ogg", ".wav", ".m4a", "open.spotify.com").any { lower.contains(it) }

            when {
                mode == "web" -> {
                    val res = runShell("am start -a android.intent.action.VIEW -d \"$targetUrl\"")
                    if (res.contains("Error", ignoreCase = true)) "Failed to open web page: $res" else "Opened web page on TV!"
                }
                isYouTube || mode == "youtube" -> {
                    val smartTube = listOf(
                        "com.teamsmart.videomanager.tv",
                        "com.liskovsoft.smarttubetv.beta",
                        "com.liskovsoft.videomanager"
                    ).firstOrNull { runShell("pm path $it").trim().isNotEmpty() }

                    val cmd = if (smartTube != null) {
                        "am start -a android.intent.action.VIEW -d \"$targetUrl\" -p $smartTube"
                    } else {
                        "am start -a android.intent.action.VIEW -d \"$targetUrl\""
                    }
                    val res = runShell(cmd)
                    if (res.contains("Error", ignoreCase = true)) {
                        runShell("am start -a android.intent.action.VIEW -d \"$targetUrl\"")
                        "Opened YouTube on TV!"
                    } else {
                        if (smartTube != null) "Playing via SmartTube on TV!" else "Opened YouTube on TV!"
                    }
                }
                isVideo -> {
                    val res = runShell("am start -a android.intent.action.VIEW -d \"$targetUrl\" -t \"video/*\"")
                    if (res.contains("Error") || res.contains("No Activity found")) {
                        runShell("am start -a android.intent.action.VIEW -d \"$targetUrl\"")
                    }
                    "Playing video stream on TV!"
                }
                isAudio -> {
                    val res = runShell("am start -a android.intent.action.VIEW -d \"$targetUrl\" -t \"audio/*\"")
                    if (res.contains("Error") || res.contains("No Activity found")) {
                        runShell("am start -a android.intent.action.VIEW -d \"$targetUrl\"")
                    }
                    "Playing audio stream on TV!"
                }
                else -> {
                    val res = runShell("am start -a android.intent.action.VIEW -d \"$targetUrl\"")
                    if (res.contains("Error", ignoreCase = true)) "Could not open link: $res" else "Opened link on TV!"
                }
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun clearApp(packageName: String): String = withContext(Dispatchers.IO) {
        try {
            val res = runShell("pm clear $packageName")
            if (res.contains("Success", ignoreCase = true)) "Data and cache cleared for $packageName" else res
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun openAppDetailsOnTv(packageName: String): String = withContext(Dispatchers.IO) {
        try {
            runShell("am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:$packageName")
            "Opened settings for $packageName on TV"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun disableStockLauncher(stockPackage: String): String = withContext(Dispatchers.IO) {
        try {
            val res = runShell("pm disable-user --user 0 $stockPackage")
            // Launch home to switch to custom launcher
            runShell("am start -a android.intent.action.MAIN -c android.intent.category.HOME")
            if (res.contains("new state: disabled", ignoreCase = true)) {
                "Stock launcher disabled. Custom launcher is now active!"
            } else {
                res
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun enableStockLauncher(stockPackage: String): String = withContext(Dispatchers.IO) {
        try {
            val res = runShell("pm enable $stockPackage")
            runShell("am start -a android.intent.action.MAIN -c android.intent.category.HOME")
            if (res.contains("new state: enabled", ignoreCase = true) || res.isBlank()) {
                "Stock launcher re-enabled!"
            } else {
                res
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun launchHome(): String = withContext(Dispatchers.IO) {
        try {
            runShell("am start -a android.intent.action.MAIN -c android.intent.category.HOME")
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    // ==========================================
    // Saved Devices & Auto-Connect
    // ==========================================

    private val prefs = context.getSharedPreferences("atv_saved_devices", Context.MODE_PRIVATE)

    fun isAutoConnectEnabled(): Boolean = prefs.getBoolean("pref_auto_connect", true)

    fun setAutoConnectEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("pref_auto_connect", enabled).apply()
    }

    fun getSavedDevices(): List<SavedTvDevice> {
        val raw = prefs.getString("saved_device_list", "") ?: ""
        val lastIp = prefs.getString("last_connected_ip", null)?.trim()
        val lastPort = prefs.getInt("last_connected_port", 5555)

        if (raw.isBlank()) {
            if (!lastIp.isNullOrBlank()) {
                return listOf(
                    SavedTvDevice(
                        ip = lastIp,
                        port = lastPort,
                        name = "Android TV",
                        model = "TV",
                        lastConnected = System.currentTimeMillis()
                    )
                )
            }
            return listOf(
                SavedTvDevice(
                    ip = "192.168.1.40",
                    port = 5555,
                    name = "Google TV Streamer",
                    model = "kirkwood",
                    lastConnected = System.currentTimeMillis()
                )
            )
        }

        val list = mutableListOf<SavedTvDevice>()
        for (item in raw.split(";;;")) {
            val parts = item.split("|||")
            if (parts.size >= 4) {
                list.add(
                    SavedTvDevice(
                        ip = parts[0],
                        port = parts[1].toIntOrNull() ?: 5555,
                        name = parts[2],
                        model = parts[3],
                        lastConnected = parts.getOrNull(4)?.toLongOrNull() ?: System.currentTimeMillis()
                    )
                )
            }
        }
        return list.sortedByDescending { it.lastConnected }
    }

    fun saveDevice(device: SavedTvDevice) {
        val current = getSavedDevices().toMutableList()
        current.removeAll { it.ip == device.ip }
        val updatedDevice = device.copy(lastConnected = System.currentTimeMillis())
        current.add(0, updatedDevice)

        val raw = current.take(10).joinToString(";;;") {
            "${it.ip}|||${it.port}|||${it.name}|||${it.model}|||${it.lastConnected}"
        }
        prefs.edit()
            .putString("saved_device_list", raw)
            .putString("last_connected_ip", device.ip)
            .putInt("last_connected_port", device.port)
            .putBoolean("pref_auto_connect", true)
            .apply()
    }

    fun removeSavedDevice(ip: String) {
        val current = getSavedDevices().toMutableList()
        current.removeAll { it.ip == ip }
        val raw = current.joinToString(";;;") {
            "${it.ip}|||${it.port}|||${it.name}|||${it.model}|||${it.lastConnected}"
        }
        val edit = prefs.edit().putString("saved_device_list", raw)
        if (prefs.getString("last_connected_ip", "") == ip) {
            edit.remove("last_connected_ip")
        }
        edit.apply()
    }

    suspend fun autoConnect(): Boolean = withContext(Dispatchers.IO) {
        if (isConnected()) return@withContext true

        connectMutex.withLock {
            if (isConnected()) return@withLock true

            val lastIp = prefs.getString("last_connected_ip", null)?.trim()
            val lastPort = prefs.getInt("last_connected_port", 5555)

            val target = if (!lastIp.isNullOrBlank()) {
                val saved = getSavedDevices().find { it.ip == lastIp }
                saved ?: SavedTvDevice(lastIp, lastPort, "Android TV", "TV")
            } else {
                getSavedDevices().firstOrNull() ?: SavedTvDevice("192.168.1.40", 5555, "Google TV Streamer", "kirkwood")
            }

            try {
                Log.d(TAG, "autoConnect() initiating connection to ${target.ip}:${target.port}")
                connect(target.ip, target.port)
                isConnected()
            } catch (e: Exception) {
                Log.e(TAG, "autoConnect failed for ${target.ip}: ${e.message}")
                false
            }
        }
    }

    // ==========================================
    // Smart Caster & Deep Link URLs
    // ==========================================

    suspend fun castUrlToTv(url: String): String = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = url.trim()
            val res = runShell("am start -a android.intent.action.VIEW -d \"$cleanUrl\"")
            if (res.contains("Error", ignoreCase = true)) res else "Opened URL on TV"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    // ==========================================
    // 1-Tap RAM Optimizer & Speed Booster
    // ==========================================

    suspend fun boostTvMemory(): Pair<Long, Long> = withContext(Dispatchers.IO) {
        val before = fetchMemoryInfo().freeMb
        try {
            runShell("am kill-all")
            // Try trim / drop caches
            runShell("echo 3 > /proc/sys/vm/drop_caches 2>/dev/null")
        } catch (ignored: Exception) {}
        kotlinx.coroutines.delay(1000)
        val after = fetchMemoryInfo().freeMb
        val freed = (after - before).coerceAtLeast(0)
        Pair(freed, after)
    }

    // ==========================================
    // Screensaver & Sleep Timer
    // ==========================================

    suspend fun startScreensaver(): String = withContext(Dispatchers.IO) {
        try {
            runShell("cmd dream")
            "Screensaver started"
        } catch (e: Exception) {
            try {
                runShell("input keyevent 223")
                "Screen off"
            } catch (e2: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        val totalSeconds = minutes * 60L
        _sleepTimerState.value = SleepTimerState(
            isActive = true,
            remainingSeconds = totalSeconds,
            initialMinutes = minutes
        )

        sleepTimerJob = CoroutineScope(Dispatchers.IO).launch {
            var rem = totalSeconds
            while (rem > 0) {
                delay(1000)
                rem--
                _sleepTimerState.value = _sleepTimerState.value.copy(remainingSeconds = rem)
            }
            // Timer expired, turn off TV
            try {
                sendKeyEvent(KEYCODE_POWER)
            } catch (ignored: Exception) {}
            _sleepTimerState.value = SleepTimerState(isActive = false)
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerState.value = SleepTimerState(isActive = false)
    }

    // ==========================================
    // TV Full Backup & Restore Engine
    // ==========================================

    fun getBackupBaseDir(): File {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val publicDir = File(downloadDir, "ATVCommander_Backups")
        if (publicDir.exists() || publicDir.mkdirs()) {
            return publicDir
        }
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "tv_backups")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun findBackupFolder(backupId: String): File? {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val inPublic = File(File(downloadDir, "ATVCommander_Backups"), backupId)
        if (inPublic.exists() && inPublic.isDirectory) return inPublic

        val inPrivate = File(File(context.getExternalFilesDir(null) ?: context.filesDir, "tv_backups"), backupId)
        if (inPrivate.exists() && inPrivate.isDirectory) return inPrivate

        return null
    }

    fun getAllBackupDirs(): List<File> {
        val roots = mutableListOf<File>()
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val publicDir = File(downloadDir, "ATVCommander_Backups")
        if (publicDir.exists()) roots.add(publicDir)
        val privateDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "tv_backups")
        if (privateDir.exists()) roots.add(privateDir)
        return roots
    }

    fun getTvBackups(): List<TvBackupManifest> {
        val backups = mutableListOf<TvBackupManifest>()
        val seenIds = mutableSetOf<String>()
        val rootDirs = getAllBackupDirs()

        for (root in rootDirs) {
            val dirs = root.listFiles { f -> f.isDirectory } ?: continue
            for (d in dirs) {
                if (seenIds.contains(d.name)) continue
                val manifestFile = File(d, "manifest.json")
                if (manifestFile.exists()) {
                    try {
                        val json = JSONObject(manifestFile.readText())
                        val id = json.optString("id", d.name)
                        if (seenIds.contains(id)) continue
                        seenIds.add(id)

                        val appsJson = json.optJSONArray("apps") ?: JSONArray()
                        val apps = mutableListOf<TvBackupAppItem>()
                        for (i in 0 until appsJson.length()) {
                            val a = appsJson.getJSONObject(i)
                            apps.add(
                                TvBackupAppItem(
                                    packageName = a.optString("packageName"),
                                    appName = a.optString("appName"),
                                    versionName = a.optString("versionName"),
                                    isSystemApp = a.optBoolean("isSystemApp"),
                                    isEnabled = a.optBoolean("isEnabled"),
                                    apkFileName = a.optString("apkFileName"),
                                    apkSizeBytes = a.optLong("apkSizeBytes")
                                )
                            )
                        }

                        backups.add(
                            TvBackupManifest(
                                id = id,
                                timestamp = json.optLong("timestamp", d.lastModified()),
                                deviceName = json.optString("deviceName", "Android TV"),
                                deviceModel = json.optString("deviceModel", "TV"),
                                androidVersion = json.optString("androidVersion", ""),
                                appCount = json.optInt("appCount", apps.size),
                                totalSizeBytes = json.optLong("totalSizeBytes", 0L),
                                hasSettings = json.optBoolean("hasSettings", true),
                                apps = apps
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse backup manifest: ${e.message}")
                    }
                }
            }
        }
        return backups.sortedByDescending { it.timestamp }
    }

    fun deleteTvBackup(backupId: String): Boolean {
        var deleted = false
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val inPublic = File(File(downloadDir, "ATVCommander_Backups"), backupId)
        if (inPublic.exists()) {
            deleted = inPublic.deleteRecursively() || deleted
        }
        val inPrivate = File(File(context.getExternalFilesDir(null) ?: context.filesDir, "tv_backups"), backupId)
        if (inPrivate.exists()) {
            deleted = inPrivate.deleteRecursively() || deleted
        }
        return deleted
    }

    fun getBackupFolderLocation(backupId: String): String {
        val folder = findBackupFolder(backupId) ?: return "Downloads/ATVCommander_Backups/$backupId"
        return folder.absolutePath
    }

    fun copyBackupToDownloads(backupId: String): String {
        val src = findBackupFolder(backupId) ?: return "Backup folder not found"
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dest = File(downloadDir, "ATVCommander_Backups/$backupId")
        if (src.canonicalPath == dest.canonicalPath) {
            return "Already in Downloads/ATVCommander_Backups/$backupId"
        }
        dest.mkdirs()
        src.copyRecursively(dest, overwrite = true)
        return "Copied to Downloads/ATVCommander_Backups/$backupId"
    }

    fun getBackupSettingsContent(backupId: String): Map<String, String> {
        val dir = findBackupFolder(backupId) ?: return emptyMap()
        val map = mutableMapOf<String, String>()
        File(dir, "settings_global.txt").takeIf { it.exists() }?.let { map["global"] = it.readText() }
        File(dir, "settings_secure.txt").takeIf { it.exists() }?.let { map["secure"] = it.readText() }
        File(dir, "settings_system.txt").takeIf { it.exists() }?.let { map["system"] = it.readText() }
        File(dir, "disabled_packages.txt").takeIf { it.exists() }?.let { map["disabled"] = it.readText() }
        File(dir, "display_settings.txt").takeIf { it.exists() }?.let { map["display"] = it.readText() }
        return map
    }

    suspend fun installSingleApkFromBackup(backupId: String, apkFileName: String): String = withContext(Dispatchers.IO) {
        val dir = findBackupFolder(backupId) ?: return@withContext "Backup not found"
        val apkFile = File(File(dir, "apks"), apkFileName)
        if (!apkFile.exists() || apkFile.length() == 0L) {
            return@withContext "APK file not found: $apkFileName"
        }
        val remoteTmp = "/data/local/tmp/$apkFileName"
        try {
            FileInputStream(apkFile).use { fis ->
                pushFile(fis, remoteTmp, apkFile.length(), apkFileName).collect {}
            }
            val installRes = runShell("pm install -r -d -g \"$remoteTmp\"")
            runShell("rm -f \"$remoteTmp\"")
            if (installRes.contains("Success", ignoreCase = true)) {
                "Successfully installed $apkFileName"
            } else {
                "Install: $installRes"
            }
        } catch (e: Exception) {
            "Install failed: ${e.message}"
        }
    }

    fun startBackup(
        includeUserApks: Boolean,
        includeSettings: Boolean,
        onComplete: ((Result<TvBackupManifest>) -> Unit)? = null
    ) {
        if (_backupProgress.value.isRunning) return
        managerScope.launch {
            val res = createTvBackup(
                includeUserApks = includeUserApks,
                includeSettings = includeSettings,
                onProgress = { _backupProgress.value = it }
            )
            onComplete?.invoke(res)
        }
    }

    fun startRestore(
        manifest: TvBackupManifest,
        restoreApps: Boolean,
        restoreSettings: Boolean,
        onComplete: ((Result<String>) -> Unit)? = null
    ) {
        if (_backupProgress.value.isRunning) return
        managerScope.launch {
            val res = restoreTvBackup(
                manifest = manifest,
                restoreApps = restoreApps,
                restoreSettings = restoreSettings,
                onProgress = { _backupProgress.value = it }
            )
            onComplete?.invoke(res)
        }
    }

    suspend fun createTvBackup(
        includeUserApks: Boolean = true,
        includeSettings: Boolean = true,
        onProgress: (BackupProgress) -> Unit = {}
    ): Result<TvBackupManifest> = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: return@withContext Result.failure(IllegalStateException("TV not connected"))

        acquireWakeAndWifiLocks("Backup")
        AdbBackupService.start(context, "TV Snapshot in Progress", "Starting TV backup...", 0, 0)

        val reportProgress: (BackupProgress) -> Unit = { p ->
            _backupProgress.value = p
            onProgress(p)
            AdbBackupService.update(
                context,
                p.currentStep,
                p.currentItem,
                p.totalItems
            )
        }

        try {
            reportProgress(BackupProgress(isRunning = true, currentStep = "Reading TV information..."))

            val model = runShell("getprop ro.product.model").trim().ifEmpty { "Android TV" }
            val brand = runShell("getprop ro.product.brand").trim()
            val androidVer = runShell("getprop ro.build.version.release").trim()
            val deviceName = if (brand.isNotEmpty() && !model.startsWith(brand, ignoreCase = true)) "$brand $model" else model

            val timestamp = System.currentTimeMillis()
            val backupId = "backup_${timestamp}_${model.replace(" ", "_").replace(Regex("[^a-zA-Z0-9_]"), "")}"
            val backupDir = File(getBackupBaseDir(), backupId)
            backupDir.mkdirs()

            var totalSizeBytes = 0L

            // 1. Backup System Settings
            if (includeSettings) {
                reportProgress(BackupProgress(isRunning = true, currentStep = "Backing up TV system settings..."))
                val globalSettings = runShell("settings list global")
                File(backupDir, "settings_global.txt").writeText(globalSettings)

                val secureSettings = runShell("settings list secure")
                File(backupDir, "settings_secure.txt").writeText(secureSettings)

                val systemSettings = runShell("settings list system")
                File(backupDir, "settings_system.txt").writeText(systemSettings)

                val disabledPkgs = runShell("pm list packages -d")
                File(backupDir, "disabled_packages.txt").writeText(disabledPkgs)

                val wmSize = runShell("wm size")
                val wmDensity = runShell("wm density")
                File(backupDir, "display_settings.txt").writeText("size=$wmSize\ndensity=$wmDensity")
            }

            // 2. Backup User Installed Apps (APKs)
            val backedUpApps = mutableListOf<TvBackupAppItem>()
            if (includeUserApks) {
                reportProgress(BackupProgress(isRunning = true, currentStep = "Querying installed TV apps..."))
                val userPkgsOut = runShell("pm list packages -3")
                val userPkgs = userPkgsOut.lines()
                    .map { it.removePrefix("package:").trim() }
                    .filter { it.isNotEmpty() }

                val apksDir = File(backupDir, "apks")
                apksDir.mkdirs()

                val totalApps = userPkgs.size
                userPkgs.forEachIndexed { index, pkg ->
                    val pathOut = runShell("pm path $pkg").trim()
                    val remoteApkPath = pathOut.lines()
                        .firstOrNull { it.startsWith("package:") }
                        ?.removePrefix("package:")
                        ?.trim()

                    val appFriendlyName = getLauncherFriendlyName(pkg)
                        .replace(" Launcher", "")
                        .ifEmpty { pkg.substringAfterLast(".").replaceFirstChar { it.uppercase() } }

                    reportProgress(
                        BackupProgress(
                            isRunning = true,
                            currentStep = "Pulling $appFriendlyName APK ($pkg)...",
                            currentItem = index + 1,
                            totalItems = totalApps,
                            progressPercent = (index.toFloat() / totalApps.toFloat()) * 100f
                        )
                    )

                    var apkSize = 0L
                    val apkFileName = "$pkg.apk"
                    val localApk = File(apksDir, apkFileName)

                    if (!remoteApkPath.isNullOrEmpty()) {
                        try {
                            FileOutputStream(localApk).use { fos ->
                                pullFile(remoteApkPath, fos)
                            }
                            apkSize = localApk.length()
                            totalSizeBytes += apkSize
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed pulling $pkg: ${e.message}")
                        }
                    }

                    // Dump version name
                    val dump = runShell("dumpsys package $pkg | grep versionName").trim()
                    val version = Regex("versionName=([^\\s]+)").find(dump)?.groupValues?.get(1) ?: "1.0"

                    backedUpApps.add(
                        TvBackupAppItem(
                            packageName = pkg,
                            appName = appFriendlyName,
                            versionName = version,
                            isSystemApp = false,
                            isEnabled = true,
                            apkFileName = apkFileName,
                            apkSizeBytes = apkSize
                        )
                    )
                }
            }

            // 3. Write manifest.json
            val manifestJson = JSONObject().apply {
                put("id", backupId)
                put("timestamp", timestamp)
                put("deviceName", deviceName)
                put("deviceModel", model)
                put("androidVersion", androidVer)
                put("appCount", backedUpApps.size)
                put("totalSizeBytes", totalSizeBytes)
                put("hasSettings", includeSettings)

                val appsArr = JSONArray()
                for (a in backedUpApps) {
                    val aObj = JSONObject().apply {
                        put("packageName", a.packageName)
                        put("appName", a.appName)
                        put("versionName", a.versionName)
                        put("isSystemApp", a.isSystemApp)
                        put("isEnabled", a.isEnabled)
                        put("apkFileName", a.apkFileName)
                        put("apkSizeBytes", a.apkSizeBytes)
                    }
                    appsArr.put(aObj)
                }
                put("apps", appsArr)
            }

            File(backupDir, "manifest.json").writeText(manifestJson.toString(2))

            val manifest = TvBackupManifest(
                id = backupId,
                timestamp = timestamp,
                deviceName = deviceName,
                deviceModel = model,
                androidVersion = androidVer,
                appCount = backedUpApps.size,
                totalSizeBytes = totalSizeBytes,
                hasSettings = includeSettings,
                apps = backedUpApps
            )

            reportProgress(BackupProgress(isRunning = false, currentStep = "Backup complete!"))
            Result.success(manifest)
        } catch (e: Exception) {
            reportProgress(BackupProgress(isRunning = false, error = e.message))
            Result.failure(e)
        } finally {
            AdbBackupService.stop(context)
            releaseWakeAndWifiLocks()
        }
    }

    suspend fun restoreTvBackup(
        manifest: TvBackupManifest,
        restoreApps: Boolean = true,
        restoreSettings: Boolean = true,
        onProgress: (BackupProgress) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: return@withContext Result.failure(IllegalStateException("TV not connected"))
        val backupDir = findBackupFolder(manifest.id)
            ?: return@withContext Result.failure(IllegalStateException("Backup folder not found on phone"))

        acquireWakeAndWifiLocks("Restore")
        AdbBackupService.start(context, "Restoring TV Snapshot", "Starting TV restore...", 0, 0)

        val reportProgress: (BackupProgress) -> Unit = { p ->
            _backupProgress.value = p
            onProgress(p)
            AdbBackupService.update(
                context,
                p.currentStep,
                p.currentItem,
                p.totalItems
            )
        }

        try {
            // 1. Restore System Settings
            if (restoreSettings) {
                reportProgress(BackupProgress(isRunning = true, isRestore = true, currentStep = "Restoring TV system settings..."))

                val globalFile = File(backupDir, "settings_global.txt")
                if (globalFile.exists()) {
                    globalFile.readLines().forEach { line ->
                        val parts = line.split("=", limit = 2)
                        if (parts.size == 2 && parts[0].isNotBlank()) {
                            val k = parts[0].trim()
                            val v = parts[1].trim()
                            try { runShell("settings put global $k \"$v\"") } catch (ignored: Exception) {}
                        }
                    }
                }

                val secureFile = File(backupDir, "settings_secure.txt")
                if (secureFile.exists()) {
                    secureFile.readLines().forEach { line ->
                        val parts = line.split("=", limit = 2)
                        if (parts.size == 2 && parts[0].isNotBlank()) {
                            val k = parts[0].trim()
                            val v = parts[1].trim()
                            try { runShell("settings put secure $k \"$v\"") } catch (ignored: Exception) {}
                        }
                    }
                }

                val systemFile = File(backupDir, "settings_system.txt")
                if (systemFile.exists()) {
                    systemFile.readLines().forEach { line ->
                        val parts = line.split("=", limit = 2)
                        if (parts.size == 2 && parts[0].isNotBlank()) {
                            val k = parts[0].trim()
                            val v = parts[1].trim()
                            try { runShell("settings put system $k \"$v\"") } catch (ignored: Exception) {}
                        }
                    }
                }

                // Restore disabled packages (debloat state)
                val disabledFile = File(backupDir, "disabled_packages.txt")
                if (disabledFile.exists()) {
                    disabledFile.readLines().forEach { line ->
                        val pkg = line.removePrefix("package:").trim()
                        if (pkg.isNotEmpty()) {
                            try { runShell("pm disable-user --user 0 $pkg") } catch (ignored: Exception) {}
                        }
                    }
                }
            }

            // 2. Restore Apps (install APKs)
            var restoredAppCount = 0
            if (restoreApps) {
                val apksDir = File(backupDir, "apks")
                val totalApps = manifest.apps.size

                manifest.apps.forEachIndexed { index, app ->
                    val localApk = File(apksDir, app.apkFileName)
                    if (localApk.exists() && localApk.length() > 0) {
                        reportProgress(
                            BackupProgress(
                                isRunning = true,
                                isRestore = true,
                                currentStep = "Restoring ${app.appName} (${app.packageName})...",
                                currentItem = index + 1,
                                totalItems = totalApps,
                                progressPercent = (index.toFloat() / totalApps.toFloat()) * 100f
                            )
                        )

                        val remoteTmp = "/data/local/tmp/${app.packageName}.apk"
                        try {
                            FileInputStream(localApk).use { fis ->
                                AdbSync(conn).push(fis, remoteTmp, localApk.length())
                            }
                            // Install APK
                            val installRes = runShell("pm install -r -d -g $remoteTmp")
                            if (installRes.contains("Success", ignoreCase = true)) {
                                restoredAppCount++
                            }
                            runShell("rm -f $remoteTmp")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed installing ${app.packageName}: ${e.message}")
                        }
                    }
                }
            }

            val msg = buildString {
                append("Restore complete! ")
                if (restoreApps) append("$restoredAppCount apps re-installed. ")
                if (restoreSettings) append("TV settings restored.")
            }
            reportProgress(BackupProgress(isRunning = false, isRestore = true, currentStep = msg))
            Result.success(msg)
        } catch (e: Exception) {
            reportProgress(BackupProgress(isRunning = false, isRestore = true, error = e.message))
            Result.failure(e)
        } finally {
            AdbBackupService.stop(context)
            releaseWakeAndWifiLocks()
        }
    }
}
