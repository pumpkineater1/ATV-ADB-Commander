package com.atv.adbcommander.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.atv.adbcommander.MainActivity

/**
 * Foreground service that ensures TV backups, restores, and file transfers
 * continue uninterrupted even when the screen turns off or the phone rotates.
 */
class AdbBackupService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var lastTitle: String = "TV Transfer in Progress"

    companion object {
        const val CHANNEL_ID = "atv_transfers_channel"
        const val NOTIFICATION_ID = 8801

        const val ACTION_START = "com.atv.adbcommander.service.START"
        const val ACTION_UPDATE = "com.atv.adbcommander.service.UPDATE"
        const val ACTION_STOP = "com.atv.adbcommander.service.STOP"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_CURRENT = "extra_current"
        const val EXTRA_TOTAL = "extra_total"

        fun start(context: Context, title: String, text: String, current: Int = 0, total: Int = 0) {
            val intent = Intent(context, AdbBackupService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TEXT, text)
                putExtra(EXTRA_CURRENT, current)
                putExtra(EXTRA_TOTAL, total)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Ignore if background start is restricted
            }
        }

        fun update(context: Context, text: String, current: Int = 0, total: Int = 0) {
            val intent = Intent(context, AdbBackupService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TEXT, text)
                putExtra(EXTRA_CURRENT, current)
                putExtra(EXTRA_TOTAL, total)
            }
            try {
                context.startService(intent)
            } catch (ignored: Exception) {}
        }

        fun stop(context: Context) {
            val intent = Intent(context, AdbBackupService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (ignored: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        if (action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: lastTitle
        lastTitle = title
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Processing TV task..."
        val current = intent?.getIntExtra(EXTRA_CURRENT, 0) ?: 0
        val total = intent?.getIntExtra(EXTRA_TOTAL, 0) ?: 0

        val notification = buildNotification(title, text, current, total)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TV Background Transfers",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress during TV backup, restore, and sideloading tasks."
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String, current: Int, total: Int): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (total > 0) {
            builder.setProgress(total, current, false)
            builder.setSubText("$current / $total items")
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun acquireLocks() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (wakeLock == null) {
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ATVCommander::BackupWakeLock")
                wakeLock?.setReferenceCounted(false)
            }
            wakeLock?.acquire(60 * 60 * 1000L) // 1 hour safety limit

            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiLock == null) {
                wifiLock = wm?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ATVCommander::BackupWifiLock")
                wifiLock?.setReferenceCounted(false)
            }
            wifiLock?.acquire()
        } catch (ignored: Exception) {}
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (ignored: Exception) {}
        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (ignored: Exception) {}
    }
}
