package com.example

import android.app.Application

class LbpOtgApplication : Application() {
    companion object {
        lateinit var instance: LbpOtgApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
