package com.atv.tvpointer

import android.app.Application

class TvPointerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: TvPointerApp
            private set
    }
}
