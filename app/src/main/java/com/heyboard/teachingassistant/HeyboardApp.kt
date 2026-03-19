package com.heyboard.teachingassistant

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import com.heyboard.teachingassistant.automation.HeyboardToolService

class HeyboardApp : Application() {

    companion object {
        private const val TAG = "HeyboardApp"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Application created, starting HeyboardToolService")
        val intent = Intent(this, HeyboardToolService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
