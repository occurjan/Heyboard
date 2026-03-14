package com.heyboard.teachingassistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.app.NotificationCompat

class SubtitleService : Service() {

    companion object {
        const val CHANNEL_ID = "subtitle_channel"
        const val NOTIFICATION_ID = 2002
        const val ACTION_STOP = "com.heyboard.teachingassistant.STOP_SUBTITLE"
        const val ACTION_STATE_CHANGED = "com.heyboard.teachingassistant.SUBTITLE_STATE_CHANGED"
        private const val TAG = "SubtitleService"
        var isRunning = false
            private set
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var currentLanguage = "zh-CN"
    private val handler = Handler(Looper.getMainLooper())
    private var tvSubtitle: TextView? = null
    private var isDestroyed = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        currentLanguage = intent?.getStringExtra("language") ?: "zh-CN"

        // 必须最先调用 startForeground，否则 Android 会在 5 秒内杀死服务
        showNotification()

        try {
            showFloatingWindow()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show floating window", e)
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startListening()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognizer", e)
            tvSubtitle?.text = "语音识别启动失败"
        }

        return START_NOT_STICKY
    }

    private fun showFloatingWindow() {
        if (floatingView != null) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_subtitle, null)
        tvSubtitle = floatingView?.findViewById(R.id.tvSubtitle)

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 80
        }

        // 语言切换
        floatingView?.findViewById<ImageButton>(R.id.btnLanguage)?.setOnClickListener {
            currentLanguage = if (currentLanguage == "zh-CN") "en-US" else "zh-CN"
            val langName = if (currentLanguage == "zh-CN") "中文" else "English"
            tvSubtitle?.text = "已切换到: $langName"
            restartListening()
        }

        // 关闭按钮
        floatingView?.findViewById<ImageButton>(R.id.btnCloseSubtitle)?.setOnClickListener {
            stopSelf()
        }

        // 拖动支持
        setupDrag(floatingView!!, params)

        windowManager?.addView(floatingView, params)
    }

    private fun setupDrag(view: View, params: WindowManager.LayoutParams) {
        var lastX = 0f
        var lastY = 0f
        var initialX = 0
        var initialY = 0
        var isDragging = false

        val dragArea = view.findViewById<TextView>(R.id.tvSubtitle)
        dragArea.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX
                    lastY = event.rawY
                    initialX = params.x
                    initialY = params.y
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastX
                    val dy = event.rawY - lastY
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) isDragging = true
                    if (isDragging) {
                        params.x = initialX + dx.toInt()
                        params.y = initialY - dy.toInt()
                        try {
                            windowManager?.updateViewLayout(view, params)
                        } catch (_: Exception) {}
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "SpeechRecognizer not available on this device")
            tvSubtitle?.text = "语音识别不可用（需要 Google 应用）"
            return
        }

        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create SpeechRecognizer", e)
            tvSubtitle?.text = "语音识别创建失败"
            return
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (!isDestroyed) {
                    tvSubtitle?.text = "正在聆听..."
                }
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                if (isDestroyed) return
                Log.w(TAG, "SpeechRecognizer error: $error")

                // 所有可恢复错误都重试
                when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        tvSubtitle?.text = "缺少麦克风权限"
                    }
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        // 没听到内容，直接重启
                        scheduleRestart(200)
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        // 识别器忙，稍等再试
                        scheduleRestart(1000)
                    }
                    else -> {
                        // 网络错误、客户端错误等，延迟重试
                        scheduleRestart(800)
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                if (isDestroyed) return
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    tvSubtitle?.text = matches[0]
                }
                scheduleRestart(200)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (isDestroyed) return
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    tvSubtitle?.text = matches[0]
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        performListening()
    }

    private fun scheduleRestart(delayMs: Long) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (!isDestroyed) {
                performListening()
            }
        }, delayMs)
    }

    private fun performListening() {
        if (isDestroyed) return

        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            speechRecognizer?.startListening(recognizerIntent)
        } catch (e: Exception) {
            Log.e(TAG, "startListening failed", e)
            // 尝试重建识别器
            if (!isDestroyed) {
                handler.postDelayed({
                    if (!isDestroyed) startListening()
                }, 2000)
            }
        }
    }

    private fun restartListening() {
        handler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
        performListening()
    }

    private fun showNotification() {
        val stopIntent = Intent(this, SubtitleService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("实时字幕运行中")
            .setContentText("正在进行语音转写")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止字幕", stopPending)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.subtitle_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        isRunning = false
        sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(packageName))
        isDestroyed = true
        handler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        try {
            floatingView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
        floatingView = null
        tvSubtitle = null
        super.onDestroy()
    }
}
