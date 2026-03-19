package com.heyboard.teachingassistant.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.heyboard.teachingassistant.R

class HeyboardToolService : Service() {

    companion object {
        private const val TAG = "HeyboardToolService"
        const val ACTION_BOOT_COMPLETED = "com.heyboard.teachingassistant.ACTION_BOOT_COMPLETED"
        const val ACTION_FINISH_CLASS = "com.heyboard.teachingassistant.ACTION_FINISH_CLASS"
        private const val CHANNEL_ID = "heyboard_tool_service"
        private const val NOTIFICATION_ID = 3
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        Log.i(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_BOOT_COMPLETED -> {
                Log.i(TAG, "Boot completed trigger received")
                AutomationExecutor.executeOnStart(this)
            }
            ACTION_FINISH_CLASS -> {
                Log.i(TAG, "Finish class trigger received")
                AutomationExecutor.executeOnClose(this)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "Service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.tool_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.tool_service_description)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.tool_service_channel_name))
            .setContentText(getString(R.string.tool_service_running))
            .setSmallIcon(R.drawable.ic_automation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
