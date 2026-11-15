package com.atv.adbcommander

import android.app.Application
import com.atv.adbcommander.adb.AdbManager

class AdbApp : Application() {
    lateinit var adbManager: AdbManager
        private set

    override fun onCreate() {
        super.onCreate()
        adbManager = AdbManager(this)
    }
}
