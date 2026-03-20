package com.heyboard.teachingassistant.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.heyboard.teachingassistant.R
import org.json.JSONArray
import java.io.File

class HeyboardToolService : Service() {

    companion object {
        private const val TAG = "HeyboardToolService"
        const val ACTION_BOOT_COMPLETED = "com.heyboard.teachingassistant.ACTION_BOOT_COMPLETED"
        private const val CHANNEL_ID = "heyboard_tool_service"
        private const val NOTIFICATION_ID = 3
    }

    private var shutdownReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        Log.i(TAG, "Service created")
        registerShutdownReceiver()
        ensureH3CWhitelist()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_BOOT_COMPLETED -> {
                Log.i(TAG, "Boot completed trigger received")
                AutomationExecutor.executeOnStart(this)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "Service destroyed")
        shutdownReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        super.onDestroy()
    }

    private fun getSystemProperty(key: String): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            method.invoke(null, key, "") as String
        } catch (e: Exception) {
            ""
        }
    }

    private fun registerShutdownReceiver() {
        shutdownReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.i(TAG, "Shutdown broadcast received: ${intent.action}")
                // 通过 sys.shutdown.requested 区分关机和重启
                val shutdownReason = getSystemProperty("sys.shutdown.requested")
                Log.i(TAG, "sys.shutdown.requested: '$shutdownReason'")
                // "0" = shutdown, "1" = reboot, "reboot,xxx" = reboot variant
                val isReboot = shutdownReason == "1" || shutdownReason.contains("reboot", ignoreCase = true)
                if (isReboot) {
                    Log.i(TAG, "This is a REBOOT, skipping on_close scenarios")
                    return
                }
                Log.i(TAG, "This is a SHUTDOWN, executing on_close scenarios")
                AutomationExecutor.executeOnClose(context.applicationContext)
            }
        }
        val filter = IntentFilter(Intent.ACTION_SHUTDOWN)
        registerReceiver(shutdownReceiver, filter)
        Log.i(TAG, "Registered dynamic ShutdownReceiver")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Ensure our service and package are registered in H3C system whitelist config files.
     * The app runs as system UID (android:sharedUserId="android.uid.system") so it has
     * write access to /data/h3c/h3cconfig/ which is owned by system:system.
     */
    private fun ensureH3CWhitelist() {
        Thread {
            try {
                val configDir = findH3CConfigDir()
                if (configDir == null) {
                    Log.w(TAG, "H3C config directory not found, skipping whitelist setup")
                    return@Thread
                }
                Log.i(TAG, "H3C config dir: ${configDir.absolutePath}")

                // Add service to service.json (prevents forceStopPackage from killing our service)
                val serviceEntry = "com.heyboard.teachingassistant/.automation.HeyboardToolService"
                ensureJsonArrayEntry(File(configDir, "service.json"), serviceEntry)

                // Remove package from startApp.json if present (startApp launches the main Activity UI,
                // we only need BOOT_COMPLETED + service, not the foreground Activity on boot)
                val packageEntry = "com.heyboard.teachingassistant"
                removeJsonArrayEntry(File(configDir, "startApp.json"), packageEntry)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to update H3C whitelist", e)
            }
        }.start()
    }

    private fun findH3CConfigDir(): File? {
        val baseDir = File("/data/h3c/h3cconfig")
        if (!baseDir.exists() || !baseDir.isDirectory) return null
        // Find the highest version directory
        return baseDir.listFiles()
            ?.filter { it.isDirectory }
            ?.maxByOrNull { it.name }
    }

    private fun ensureJsonArrayEntry(file: File, entry: String) {
        if (!file.exists()) {
            Log.w(TAG, "Config file not found: ${file.absolutePath}, creating it")
            file.writeText(JSONArray().put(entry).toString(4))
            Log.i(TAG, "Created ${file.name} with entry: $entry")
            return
        }

        val content = file.readText()
        val jsonArray = JSONArray(content)

        // Check if entry already exists
        for (i in 0 until jsonArray.length()) {
            if (jsonArray.getString(i) == entry) {
                Log.i(TAG, "${file.name} already contains: $entry")
                return
            }
        }

        // Add entry
        jsonArray.put(entry)
        file.writeText(jsonArray.toString(4))
        Log.i(TAG, "Added to ${file.name}: $entry")
    }

    private fun removeJsonArrayEntry(file: File, entry: String) {
        if (!file.exists()) return
        val content = file.readText()
        val jsonArray = JSONArray(content)
        var found = false
        val newArray = JSONArray()
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getString(i)
            if (item == entry) {
                found = true
            } else {
                newArray.put(item)
            }
        }
        if (found) {
            file.writeText(newArray.toString(4))
            Log.i(TAG, "Removed from ${file.name}: $entry")
        } else {
            Log.i(TAG, "${file.name} does not contain: $entry (no action needed)")
        }
    }

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
