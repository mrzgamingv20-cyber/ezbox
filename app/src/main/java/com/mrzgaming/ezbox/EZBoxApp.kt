package com.mrzgaming.ezbox

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner

class EZBoxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(applicationContext))
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver(applicationContext))
    }
}
