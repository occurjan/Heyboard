package com.heyboard.teachingassistant.automation

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object SerialCommandExecutor {

    private const val TAG = "SerialCommandExecutor"
    private const val ACTION_USB_PERMISSION = "com.heyboard.teachingassistant.USB_PERMISSION"

    /**
     * Request USB permission for the first available serial device.
     * Calls back on the main thread with true/false.
     */
    fun requestUsbPermissionIfNeeded(context: Context, callback: (granted: Boolean, message: String) -> Unit) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

        if (availableDrivers.isEmpty()) {
            Log.w(TAG, "requestPermission: No USB serial devices found")
            callback(false, "No USB serial device found")
            return
        }

        val device = availableDrivers[0].device
        if (usbManager.hasPermission(device)) {
            Log.i(TAG, "requestPermission: Already have permission for ${device.deviceName}")
            callback(true, "Permission already granted")
            return
        }

        Log.i(TAG, "requestPermission: Requesting permission for ${device.deviceName}")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                try {
                    ctx.unregisterReceiver(this)
                } catch (_: Exception) {}
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                Log.i(TAG, "requestPermission: User responded, granted=$granted")
                callback(granted, if (granted) "Permission granted" else "Permission denied by user")
            }
        }

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        val explicitIntent = Intent(ACTION_USB_PERMISSION).apply {
            setPackage(context.packageName)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val permissionIntent = PendingIntent.getBroadcast(context, 0, explicitIntent, flags)
        usbManager.requestPermission(device, permissionIntent)
    }

    /**
     * Request USB permission synchronously (blocking).
     * Used by background automation tasks (boot, finish class) where no UI callback is available.
     * Returns true if permission was granted within the timeout.
     */
    private fun requestUsbPermissionSync(context: Context, usbManager: UsbManager): Boolean {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) return false

        val device = availableDrivers[0].device
        if (usbManager.hasPermission(device)) return true

        Log.i(TAG, "requestPermissionSync: Requesting USB permission for ${device.deviceName}")

        val latch = CountDownLatch(1)
        val granted = AtomicBoolean(false)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                try {
                    ctx.unregisterReceiver(this)
                } catch (_: Exception) {}
                val result = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                Log.i(TAG, "requestPermissionSync: Permission result=$result")
                granted.set(result)
                latch.countDown()
            }
        }

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        val explicitIntent = Intent(ACTION_USB_PERMISSION).apply {
            setPackage(context.packageName)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val permissionIntent = PendingIntent.getBroadcast(context, 0, explicitIntent, flags)
        usbManager.requestPermission(device, permissionIntent)

        // Wait up to 120 seconds for user to grant permission
        try {
            latch.await(120, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {}

        if (!granted.get()) {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }

        return granted.get()
    }

    fun execute(context: Context, action: ScenarioAction): Result<Unit> {
        val config = action.serialConfig
        Log.i(TAG, "execute: Starting. Data='${config.serialData}', format=${config.dataFormat}, baud=${config.baudRate}")

        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        Log.i(TAG, "execute: Found ${availableDrivers.size} USB serial driver(s)")

        if (availableDrivers.isEmpty()) {
            val allDevices = usbManager.deviceList
            Log.w(TAG, "execute: No serial drivers, but ${allDevices.size} USB device(s) connected:")
            for ((name, dev) in allDevices) {
                Log.w(TAG, "  Device: $name, vendorId=${dev.vendorId}, productId=${dev.productId}")
            }
            return Result.failure(Exception("No USB serial device found (${allDevices.size} USB devices connected)"))
        }

        val driver = availableDrivers[0]
        Log.i(TAG, "execute: Using driver ${driver.javaClass.simpleName}, device=${driver.device.deviceName}, " +
                "vendorId=${driver.device.vendorId}, productId=${driver.device.productId}")

        // Auto-request permission if not granted
        if (!usbManager.hasPermission(driver.device)) {
            Log.i(TAG, "execute: No USB permission, requesting synchronously...")
            val granted = requestUsbPermissionSync(context, usbManager)
            if (!granted) {
                Log.w(TAG, "execute: USB permission not granted after sync request")
                return Result.failure(Exception("USB permission not granted"))
            }
            Log.i(TAG, "execute: USB permission granted via sync request")
        }

        val connection = usbManager.openDevice(driver.device)
            ?: return Result.failure(Exception("Failed to open USB device connection"))

        val port = driver.ports[0]
        return try {
            port.open(connection)
            Log.i(TAG, "execute: Port opened successfully")

            port.setParameters(
                config.baudRate,
                config.dataBits,
                mapStopBits(config.stopBits),
                mapParity(config.parity)
            )
            Log.i(TAG, "execute: Parameters set - baud=${config.baudRate}, dataBits=${config.dataBits}, " +
                    "stopBits=${config.stopBits}, parity=${config.parity}")

            // Flow control
            when (config.flowControl) {
                "RTS/CTS" -> {
                    port.setDTR(true)
                    port.setRTS(true)
                    Log.i(TAG, "execute: RTS/CTS flow control enabled")
                }
                "XON/XOFF" -> {
                    Log.i(TAG, "execute: XON/XOFF flow control (software)")
                }
            }

            val data = parseSerialData(config.serialData, config.dataFormat)
            if (data.isNotEmpty()) {
                port.write(data, 2000)
                Log.i(TAG, "execute: Sent ${data.size} bytes: ${data.joinToString(" ") { "%02X".format(it) }}")
            } else {
                Log.w(TAG, "execute: No data to send (parsed to empty)")
            }

            port.close()
            Log.i(TAG, "execute: Port closed, success")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "execute: Serial command failed", e)
            try { port.close() } catch (_: Exception) {}
            Result.failure(e)
        }
    }

    private fun mapStopBits(stopBits: String): Int = when (stopBits) {
        "1" -> UsbSerialPort.STOPBITS_1
        "1.5" -> UsbSerialPort.STOPBITS_1_5
        "2" -> UsbSerialPort.STOPBITS_2
        else -> UsbSerialPort.STOPBITS_1
    }

    private fun mapParity(parity: String): Int = when (parity) {
        "Odd" -> UsbSerialPort.PARITY_ODD
        "Even" -> UsbSerialPort.PARITY_EVEN
        "Mark" -> UsbSerialPort.PARITY_MARK
        "Space" -> UsbSerialPort.PARITY_SPACE
        else -> UsbSerialPort.PARITY_NONE
    }

    private fun parseSerialData(data: String, format: String): ByteArray {
        if (data.isBlank()) return ByteArray(0)
        return when (format) {
            "HEX" -> {
                val hex = data.replace("\\s".toRegex(), "")
                if (hex.length % 2 != 0) {
                    Log.w(TAG, "parseSerialData: HEX string has odd length: $hex")
                    return ByteArray(0)
                }
                try {
                    hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                } catch (e: Exception) {
                    Log.e(TAG, "parseSerialData: Failed to parse HEX: $hex", e)
                    ByteArray(0)
                }
            }
            else -> data.toByteArray(Charsets.UTF_8)
        }
    }
}
