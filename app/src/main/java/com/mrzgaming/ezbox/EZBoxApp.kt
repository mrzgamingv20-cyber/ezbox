package com.mrzgaming.ezbox

import android.app.Application

class EZBoxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(applicationContext))
    }
}
