package com.heyboard.teachingassistant

import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class TimerActivity : AppCompatActivity() {

    private lateinit var setupContainer: LinearLayout
    private lateinit var countdownContainer: LinearLayout
    private lateinit var etActivityName: EditText
    private lateinit var etMinutes: EditText
    private lateinit var etSeconds: EditText
    private lateinit var tvActivityName: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var btnStartTimer: MaterialButton
    private lateinit var btnPause: MaterialButton
    private lateinit var btnReset: MaterialButton

    private var countDownTimer: CountDownTimer? = null
    private var totalMillis: Long = 0
    private var remainingMillis: Long = 0
    private var isPaused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_timer)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        setupContainer = findViewById(R.id.setupContainer)
        countdownContainer = findViewById(R.id.countdownContainer)
        etActivityName = findViewById(R.id.etActivityName)
        etMinutes = findViewById(R.id.etMinutes)
        etSeconds = findViewById(R.id.etSeconds)
        tvActivityName = findViewById(R.id.tvActivityName)
        tvCountdown = findViewById(R.id.tvCountdown)
        btnStartTimer = findViewById(R.id.btnStartTimer)
        btnPause = findViewById(R.id.btnPause)
        btnReset = findViewById(R.id.btnReset)

        btnStartTimer.setOnClickListener { startTimer() }
        btnPause.setOnClickListener { togglePause() }
        btnReset.setOnClickListener { resetTimer() }
    }

    private fun startTimer() {
        val minStr = etMinutes.text.toString().ifEmpty { etMinutes.hint.toString() }
        val secStr = etSeconds.text.toString().ifEmpty { "0" }
        val minutes = minStr.toIntOrNull() ?: 0
        val seconds = secStr.toIntOrNull() ?: 0

        if (minutes == 0 && seconds == 0) {
            Toast.makeText(this, "请设置计时时长", Toast.LENGTH_SHORT).show()
            return
        }

        totalMillis = (minutes * 60L + seconds) * 1000L
        remainingMillis = totalMillis

        val activityName = etActivityName.text.toString().ifEmpty { "课堂活动" }
        tvActivityName.text = activityName

        setupContainer.visibility = View.GONE
        countdownContainer.visibility = View.VISIBLE

        beginCountdown(remainingMillis)
    }

    private fun beginCountdown(millis: Long) {
        isPaused = false
        btnPause.text = "暂停"

        countDownTimer = object : CountDownTimer(millis, 50) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMillis = millisUntilFinished
                updateDisplay(millisUntilFinished)
            }

            override fun onFinish() {
                remainingMillis = 0
                updateDisplay(0)
                tvCountdown.setTextColor(0xFFE53935.toInt())
                tvCountdown.text = "00:00"
                playAlarm()
            }
        }.start()
    }

    private fun updateDisplay(millis: Long) {
        val totalSeconds = (millis + 999) / 1000 // 向上取整
        val min = totalSeconds / 60
        val sec = totalSeconds % 60
        tvCountdown.text = String.format("%02d:%02d", min, sec)

        // 最后10秒变红
        if (totalSeconds <= 10) {
            tvCountdown.setTextColor(0xFFE53935.toInt())
        } else {
            tvCountdown.setTextColor(0xFF1976D2.toInt())
        }
    }

    private fun togglePause() {
        if (isPaused) {
            // 恢复
            beginCountdown(remainingMillis)
        } else {
            // 暂停
            countDownTimer?.cancel()
            isPaused = true
            btnPause.text = "继续"
        }
    }

    private fun resetTimer() {
        countDownTimer?.cancel()
        countdownContainer.visibility = View.GONE
        setupContainer.visibility = View.VISIBLE
        tvCountdown.setTextColor(0xFF1976D2.toInt())
    }

    private fun playAlarm() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(this, uri)
            ringtone.play()
        } catch (_: Exception) {
            // 忽略播放失败
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
