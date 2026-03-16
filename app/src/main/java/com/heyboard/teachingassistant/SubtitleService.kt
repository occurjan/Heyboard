package com.heyboard.teachingassistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

class SubtitleService : Service(), RecognitionListener {

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
    private var currentLanguage = "zh-CN"
    private val handler = Handler(Looper.getMainLooper())
    private var tvSubtitle: TextView? = null
    private var isDestroyed = false

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var popupWindow: android.widget.PopupWindow? = null

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

        showNotification()

        try {
            showFloatingWindow()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show floating window", e)
            stopSelf()
            return START_NOT_STICKY
        }

        // Initialize Vosk in background thread
        Thread {
            initVosk()
        }.start()

        return START_NOT_STICKY
    }

    private fun initVosk() {
        try {
            val modelManager = VoskModelManager(this)
            handler.post { tvSubtitle?.text = getString(R.string.loading_model) }

            model = modelManager.loadModel(currentLanguage)
            if (model == null) {
                handler.post { tvSubtitle?.text = getString(R.string.model_load_failed) }
                return
            }

            val rec = Recognizer(model, 16000.0f)
            speechService = SpeechService(rec, 16000.0f)
            speechService?.startListening(this)

            handler.post { tvSubtitle?.text = getString(R.string.listening) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init Vosk", e)
            handler.post { tvSubtitle?.text = getString(R.string.speech_start_failed) }
        }
    }

    // RecognitionListener callbacks
    override fun onPartialResult(hypothesis: String?) {
        if (isDestroyed || hypothesis == null) return
        val text = parseVoskResult(hypothesis, partial = true)
        if (text.isNotEmpty()) {
            handler.post { tvSubtitle?.text = text }
        }
    }

    override fun onResult(hypothesis: String?) {
        if (isDestroyed || hypothesis == null) return
        val text = parseVoskResult(hypothesis, partial = false)
        if (text.isNotEmpty()) {
            handler.post { tvSubtitle?.text = text }
        }
    }

    override fun onFinalResult(hypothesis: String?) {
        if (isDestroyed || hypothesis == null) return
        val text = parseVoskResult(hypothesis, partial = false)
        if (text.isNotEmpty()) {
            handler.post { tvSubtitle?.text = text }
        }
    }

    override fun onError(exception: Exception?) {
        Log.e(TAG, "Vosk error", exception)
        if (!isDestroyed) {
            handler.post {
                tvSubtitle?.text = getString(R.string.speech_start_failed)
            }
        }
    }

    override fun onTimeout() {
        if (!isDestroyed) {
            handler.post { tvSubtitle?.text = getString(R.string.listening) }
        }
    }

    private fun parseVoskResult(json: String, partial: Boolean): String {
        return try {
            val obj = JSONObject(json)
            if (partial) {
                obj.optString("partial", "")
            } else {
                obj.optString("text", "")
            }
        } catch (e: Exception) {
            ""
        }
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

        // Language switch - show popup menu
        floatingView?.findViewById<ImageButton>(R.id.btnLanguage)?.setOnClickListener { btn ->
            showLanguagePopup(btn)
        }

        // Close button
        floatingView?.findViewById<ImageButton>(R.id.btnCloseSubtitle)?.setOnClickListener {
            stopSelf()
        }

        // Drag support
        setupDrag(floatingView!!, params)

        windowManager?.addView(floatingView, params)
    }

    private fun showLanguagePopup(anchor: View) {
        val dp = resources.displayMetrics.density

        val langPopup = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.lang_popup_bg)
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
        }

        val languages = arrayOf(
            "\uD83C\uDDE8\uD83C\uDDF3  中文普通话" to "zh-CN",
            "\uD83C\uDDFA\uD83C\uDDF8  English" to "en-US"
        )

        for ((index, pair) in languages.withIndex()) {
            val (label, code) = pair
            val isSelected = code == currentLanguage

            val itemLayout = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.lang_item_ripple)
                setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    popupWindow?.dismiss()
                    popupWindow = null
                    if (code != currentLanguage) {
                        switchToLanguage(code, label.substringAfter("  "))
                    }
                }
            }

            val tv = TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(if (isSelected) 0xFF007AFF.toInt() else 0xFF333333.toInt())
                typeface = if (isSelected) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            itemLayout.addView(tv)

            if (isSelected) {
                val check = TextView(this).apply {
                    text = "✓"
                    textSize = 16f
                    setTextColor(0xFF007AFF.toInt())
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setPadding((8 * dp).toInt(), 0, 0, 0)
                }
                itemLayout.addView(check)
            }

            langPopup.addView(itemLayout)

            if (index < languages.size - 1) {
                val divider = View(this).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).apply {
                        setMargins((12 * dp).toInt(), (2 * dp).toInt(), (12 * dp).toInt(), (2 * dp).toInt())
                    }
                    setBackgroundColor(0x15000000)
                }
                langPopup.addView(divider)
            }
        }

        val popupWidth = (220 * dp).toInt()
        langPopup.measure(
            View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupHeight = langPopup.measuredHeight

        popupWindow = android.widget.PopupWindow(
            langPopup,
            popupWidth,
            popupHeight,
            true
        ).apply {
            elevation = 24f
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0))
            isOutsideTouchable = true
            showAsDropDown(anchor, anchor.width - popupWidth, -(popupHeight + anchor.height + (4 * dp).toInt()))
        }
    }

    private fun switchToLanguage(code: String, label: String) {
        currentLanguage = code
        tvSubtitle?.text = getString(R.string.switched_to, label)

        try {
            speechService?.stop()
            speechService?.shutdown()
        } catch (_: Exception) {}

        Thread {
            try {
                model?.close()
            } catch (_: Exception) {}
            initVosk()
        }.start()
    }

    private fun setupDrag(view: View, params: WindowManager.LayoutParams) {
        var lastX = 0f
        var lastY = 0f
        var initialX = 0
        var initialY = 0
        var isDragging = false

        view.setOnTouchListener { _, event ->
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
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isDragging = true
                    if (isDragging) {
                        params.x = initialX + dx.toInt()
                        params.y = initialY - dy.toInt()
                        try {
                            windowManager?.updateViewLayout(view, params)
                        } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // Not a drag, pass click to child views
                        false
                    } else {
                        true
                    }
                }
                else -> false
            }
        }
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
            .setContentTitle(getString(R.string.subtitle_running))
            .setContentText(getString(R.string.subtitle_transcribing))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.stop_subtitle_action), stopPending)
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
        try { popupWindow?.dismiss() } catch (_: Exception) {}
        popupWindow = null
        try {
            speechService?.stop()
            speechService?.shutdown()
        } catch (_: Exception) {}
        speechService = null
        try {
            model?.close()
        } catch (_: Exception) {}
        model = null
        try {
            floatingView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
        floatingView = null
        tvSubtitle = null
        super.onDestroy()
    }
}
