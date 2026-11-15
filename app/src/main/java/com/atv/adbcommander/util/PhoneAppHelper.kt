package com.atv.adbcommander.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.atv.adbcommander.model.PhoneInstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PhoneAppHelper {

    suspend fun getInstalledUserApps(context: Context): List<PhoneInstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val apps = mutableListOf<PhoneInstalledApp>()

        try {
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in installed) {
                // Filter out system apps unless updated
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

                if (!isSystem || isUpdatedSystem) {
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    val apkPath = appInfo.sourceDir
                    val file = File(apkPath)
                    val size = if (file.exists()) file.length() else 0L

                    apps.add(
                        PhoneInstalledApp(
                            appName = appName,
                            packageName = appInfo.packageName,
                            apkPath = apkPath,
                            sizeBytes = size
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        apps.sortedBy { it.appName.lowercase() }
    }
}
