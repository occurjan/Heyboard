package com.heyboard.teachingassistant

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.ImageButton
import com.heyboard.teachingassistant.automation.AutomationActivity
import com.heyboard.teachingassistant.automation.HeyboardToolService

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.btnAutomation).setOnClickListener {
            startActivity(Intent(this, AutomationActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btnHome).setOnClickListener {
            finishAffinity()
        }

        // Ensure HeyboardToolService is running (no automation triggered)
        val serviceIntent = Intent(this, HeyboardToolService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        findViewById<CardView>(R.id.cardRandomCall).setOnClickListener {
            startActivity(Intent(this, RandomCallActivity::class.java))
        }
        findViewById<CardView>(R.id.cardTimer).setOnClickListener {
            startActivity(Intent(this, TimerActivity::class.java))
        }
        findViewById<CardView>(R.id.cardSpotlight).setOnClickListener {
            startActivity(Intent(this, SpotlightActivity::class.java))
        }
        findViewById<CardView>(R.id.cardSubtitle).setOnClickListener {
            startActivity(Intent(this, SubtitleActivity::class.java))
        }
    }

}
