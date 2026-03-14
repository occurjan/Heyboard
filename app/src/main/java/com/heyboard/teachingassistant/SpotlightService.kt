package com.heyboard.teachingassistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class SpotlightService : Service() {

    companion object {
        const val CHANNEL_ID = "spotlight_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_STOP = "com.heyboard.teachingassistant.STOP_SPOTLIGHT"
        private const val MIN_RADIUS = 60f
        private const val MAX_RADIUS = 1000f
        var isRunning = false
            private set
        private var instance: SpotlightService? = null

        fun updateRadius(sizePct: Int) {
            instance?.applyNewRadius(sizePct)
        }

        fun updateOpacity(opacityPct: Int) {
            instance?.maskView?.updateOpacity(opacityPct * 255 / 100)
        }
    }

    private var maskView: SpotlightOverlayView? = null
    private var controlView: View? = null
    private var windowManager: WindowManager? = null
    private var controlParams: WindowManager.LayoutParams? = null
    private var currentRadius = 200f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        instance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val opacity = intent?.getIntExtra("opacity", 80) ?: 80
        val size = intent?.getIntExtra("size", 40) ?: 40
        val maskAlpha = (opacity * 255 / 100)

        showNotification()
        showOverlay(maskAlpha, size)

        return START_NOT_STICKY
    }

    private fun showOverlay(maskAlpha: Int, sizePct: Int) {
        if (maskView != null) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        currentRadius = (minOf(screenWidth, screenHeight) * sizePct / 100f) / 2f

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        // === 窗口1：蒙版层 - 全屏，完全不拦截触摸 ===
        maskView = SpotlightOverlayView(this, maskAlpha)
        val maskParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        windowManager?.addView(maskView, maskParams)

        // 设置初始圆形位置
        val cx = screenWidth / 2f
        val cy = screenHeight / 2f
        maskView?.updateCircle(cx, cy, currentRadius)

        // === 窗口2：控制层 - 与聚光灯圆形同大小，可触摸 ===
        val diameter = (currentRadius * 2).toInt()
        controlView = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
        controlParams = WindowManager.LayoutParams(
            diameter, diameter,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - diameter) / 2
            y = (screenHeight - diameter) / 2
        }

        setupTouchHandling()
        windowManager?.addView(controlView, controlParams)
    }

    private fun setupTouchHandling() {
        val ctrl = controlView ?: return
        val params = controlParams ?: return

        var lastX = 0f
        var lastY = 0f

        val scaleDetector = ScaleGestureDetector(this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val oldRadius = currentRadius
                    currentRadius = (currentRadius * detector.scaleFactor).coerceIn(MIN_RADIUS, MAX_RADIUS)

                    val newDiameter = (currentRadius * 2).toInt()
                    // 保持中心不变地缩放控制窗口
                    val centerX = params.x + ctrl.width / 2
                    val centerY = params.y + ctrl.height / 2
                    params.width = newDiameter
                    params.height = newDiameter
                    params.x = centerX - newDiameter / 2
                    params.y = centerY - newDiameter / 2

                    try {
                        windowManager?.updateViewLayout(ctrl, params)
                    } catch (_: Exception) {}

                    // 同步更新蒙版
                    maskView?.updateCircle(
                        centerX.toFloat(),
                        centerY.toFloat(),
                        currentRadius
                    )
                    return true
                }
            })

        ctrl.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX
                    lastY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                        val dx = event.rawX - lastX
                        val dy = event.rawY - lastY
                        params.x += dx.toInt()
                        params.y += dy.toInt()

                        try {
                            windowManager?.updateViewLayout(ctrl, params)
                        } catch (_: Exception) {}

                        // 同步更新蒙版圆形位置
                        val cx = params.x + ctrl.width / 2f
                        val cy = params.y + ctrl.height / 2f
                        maskView?.updateCircle(cx, cy, currentRadius)

                        lastX = event.rawX
                        lastY = event.rawY
                    }
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> true
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
    }

    private fun showNotification() {
        val stopIntent = Intent(this, SpotlightService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("聚光灯运行中")
            .setContentText("点击关闭聚光灯")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭聚光灯", stopPending)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.spotlight_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun applyNewRadius(sizePct: Int) {
        val ctrl = controlView ?: return
        val params = controlParams ?: return

        val metrics = resources.displayMetrics
        val screenMin = minOf(metrics.widthPixels, metrics.heightPixels)
        val newRadius = (screenMin * sizePct / 100f / 2f).coerceIn(MIN_RADIUS, MAX_RADIUS)
        currentRadius = newRadius

        val newDiameter = (newRadius * 2).toInt()
        val centerX = params.x + ctrl.width / 2
        val centerY = params.y + ctrl.height / 2
        params.width = newDiameter
        params.height = newDiameter
        params.x = centerX - newDiameter / 2
        params.y = centerY - newDiameter / 2

        try {
            windowManager?.updateViewLayout(ctrl, params)
        } catch (_: Exception) {}

        maskView?.updateCircle(centerX.toFloat(), centerY.toFloat(), currentRadius)
    }

    override fun onDestroy() {
        isRunning = false
        instance = null
        super.onDestroy()
        try {
            controlView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
        try {
            maskView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
        controlView = null
        maskView = null
    }
}
