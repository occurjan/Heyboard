package com.heyboard.teachingassistant

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class SubtitleActivity : AppCompatActivity() {

    companion object {
        const val OVERLAY_PERMISSION_REQUEST = 2001
        const val AUDIO_PERMISSION_REQUEST = 2002
    }

    private var selectedLanguage = "zh-CN"
    private lateinit var tvCurrentLang: TextView
    private lateinit var btnChinese: MaterialButton
    private lateinit var btnEnglish: MaterialButton
    private lateinit var btnStartSubtitle: MaterialButton
    private lateinit var btnMinimize: MaterialButton

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateButtonState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_subtitle)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        tvCurrentLang = findViewById(R.id.tvCurrentLang)
        btnChinese = findViewById(R.id.btnChinese)
        btnEnglish = findViewById(R.id.btnEnglish)
        btnStartSubtitle = findViewById(R.id.btnStartSubtitle)
        btnMinimize = findViewById(R.id.btnMinimize)

        btnMinimize.setOnClickListener {
            finishAffinity()
        }

        btnChinese.setOnClickListener {
            selectedLanguage = "zh-CN"
            tvCurrentLang.text = getString(R.string.current_lang_zh)
        }

        btnEnglish.setOnClickListener {
            selectedLanguage = "en-US"
            tvCurrentLang.text = getString(R.string.current_lang_en)
        }

        btnStartSubtitle.setOnClickListener {
            if (SubtitleService.isRunning) {
                stopSubtitleService()
            } else {
                checkPermissionsAndStart()
            }
        }

        updateButtonState()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(SubtitleService.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stateReceiver, filter)
        }
        updateButtonState()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(stateReceiver) } catch (_: Exception) {}
    }

    private fun updateButtonState() {
        if (SubtitleService.isRunning) {
            btnStartSubtitle.text = getString(R.string.stop_subtitle)
            btnStartSubtitle.backgroundTintList =
                ColorStateList.valueOf(0xFFE53935.toInt())
            btnMinimize.visibility = android.view.View.VISIBLE
        } else {
            btnStartSubtitle.text = getString(R.string.start_subtitle)
            btnStartSubtitle.backgroundTintList =
                ColorStateList.valueOf(0xFF007AFF.toInt())
            btnMinimize.visibility = android.view.View.GONE
        }
    }

    private fun stopSubtitleService() {
        val intent = Intent(this, SubtitleService::class.java).apply {
            action = SubtitleService.ACTION_STOP
        }
        startService(intent)
        btnStartSubtitle.postDelayed({ updateButtonState() }, 200)
    }

    private fun checkPermissionsAndStart() {
        // 检查悬浮窗权限
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.grant_overlay_permission, Toast.LENGTH_SHORT).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
            return
        }

        // 检查麦克风权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                AUDIO_PERMISSION_REQUEST
            )
            return
        }

        // 检查通知权限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    2003
                )
                return
            }
        }

        startSubtitleService()
    }

    private fun startSubtitleService() {
        val intent = Intent(this, SubtitleService::class.java).apply {
            putExtra("language", selectedLanguage)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, R.string.subtitle_started, Toast.LENGTH_SHORT).show()
        btnStartSubtitle.postDelayed({ updateButtonState() }, 300)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            checkPermissionsAndStart()
        } else {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Use ActivityResult API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST && Settings.canDrawOverlays(this)) {
            checkPermissionsAndStart()
        }
    }
}
