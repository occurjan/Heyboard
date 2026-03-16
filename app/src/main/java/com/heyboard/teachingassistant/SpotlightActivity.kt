package com.heyboard.teachingassistant

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class SpotlightActivity : AppCompatActivity() {

    private lateinit var seekOpacity: SeekBar
    private lateinit var seekSize: SeekBar
    private lateinit var tvOpacityValue: TextView
    private lateinit var tvSizeValue: TextView
    private lateinit var btnStartSpotlight: MaterialButton
    private lateinit var btnMinimize: MaterialButton

    companion object {
        const val OVERLAY_PERMISSION_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_spotlight)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        seekOpacity = findViewById(R.id.seekOpacity)
        seekSize = findViewById(R.id.seekSize)
        tvOpacityValue = findViewById(R.id.tvOpacityValue)
        tvSizeValue = findViewById(R.id.tvSizeValue)
        btnStartSpotlight = findViewById(R.id.btnStartSpotlight)
        btnMinimize = findViewById(R.id.btnMinimize)

        seekOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvOpacityValue.text = "${progress}%"
                if (fromUser && SpotlightService.isRunning) {
                    SpotlightService.updateOpacity(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvSizeValue.text = "${progress}%"
                if (fromUser && SpotlightService.isRunning) {
                    SpotlightService.updateRadius(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnStartSpotlight.setOnClickListener {
            if (SpotlightService.isRunning) {
                stopSpotlightService()
            } else {
                startSpotlightService()
            }
        }

        btnMinimize.setOnClickListener {
            finishAffinity()
        }

        updateButtonState()
    }

    override fun onResume() {
        super.onResume()
        updateButtonState()
    }

    private fun updateButtonState() {
        if (SpotlightService.isRunning) {
            btnStartSpotlight.text = getString(R.string.stop_spotlight)
            btnStartSpotlight.backgroundTintList =
                ColorStateList.valueOf(0xFFE53935.toInt())
        } else {
            btnStartSpotlight.text = getString(R.string.start_spotlight)
            btnStartSpotlight.backgroundTintList =
                ColorStateList.valueOf(0xFF007AFF.toInt())
        }
    }

    private fun stopSpotlightService() {
        val intent = Intent(this, SpotlightService::class.java).apply {
            action = SpotlightService.ACTION_STOP
        }
        startService(intent)
        btnStartSpotlight.postDelayed({ updateButtonState() }, 200)
    }

    private fun startSpotlightService() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.grant_overlay_permission, Toast.LENGTH_SHORT).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
            return
        }

        val intent = Intent(this, SpotlightService::class.java).apply {
            putExtra("opacity", seekOpacity.progress)
            putExtra("size", seekSize.progress)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        btnStartSpotlight.postDelayed({ updateButtonState() }, 300)
    }

    @Deprecated("Use ActivityResult API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
