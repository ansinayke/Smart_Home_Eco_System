package com.smarthome.iot

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class SmartHomeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
    }
}
