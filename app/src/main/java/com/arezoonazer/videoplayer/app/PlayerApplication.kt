package com.arezoonazer.videoplayer.app

import android.app.Application
import android.content.Context
import com.facebook.stetho.Stetho
import com.squareup.leakcanary.LeakCanary
import com.squareup.leakcanary.RefWatcher

class PlayerApplication : Application() {
    private var refWatcher: RefWatcher? = null
    override fun onCreate() {
        super.onCreate()

        // initialize stetho
        Stetho.initializeWithDefaults(this)
        if (LeakCanary.isInAnalyzerProcess(this)) {
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not init your app in this process.
            return
        }
        refWatcher = LeakCanary.install(this)
        // Normal app init code...
    }

    companion object {
        fun getRefWatcher(context: Context): RefWatcher? {
            val application = context.applicationContext as PlayerApplication
            return application.refWatcher
        }
    }
}