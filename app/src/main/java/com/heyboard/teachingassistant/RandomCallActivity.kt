package com.heyboard.teachingassistant

import android.animation.ValueAnimator
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import kotlin.random.Random

class RandomCallActivity : AppCompatActivity() {

    private lateinit var tvResult: TextView
    private lateinit var etMin: EditText
    private lateinit var etMax: EditText
    private lateinit var btnStart: MaterialButton
    private var isAnimating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_random_call)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        tvResult = findViewById(R.id.tvResult)
        etMin = findViewById(R.id.etMin)
        etMax = findViewById(R.id.etMax)
        btnStart = findViewById(R.id.btnStart)

        btnStart.setOnClickListener { startRandomCall() }
    }

    private fun startRandomCall() {
        if (isAnimating) return

        val minStr = etMin.text.toString().ifEmpty { etMin.hint.toString() }
        val maxStr = etMax.text.toString().ifEmpty { etMax.hint.toString() }
        val min = minStr.toIntOrNull()
        val max = maxStr.toIntOrNull()

        if (min == null || max == null || min > max) {
            Toast.makeText(this, R.string.invalid_range, Toast.LENGTH_SHORT).show()
            return
        }

        isAnimating = true
        btnStart.isEnabled = false

        val finalResult = Random.nextInt(min, max + 1)
        val animator = ValueAnimator.ofInt(0, 20).apply {
            duration = 2000L
            addUpdateListener { animation ->
                val progress = animation.animatedFraction
                if (progress < 0.9f) {
                    tvResult.text = Random.nextInt(min, max + 1).toString()
                } else {
                    tvResult.text = finalResult.toString()
                }
            }
        }

        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                tvResult.text = finalResult.toString()
                tvResult.setTextColor(0xFFFFD740.toInt())
                isAnimating = false
                btnStart.isEnabled = true

                tvResult.animate()
                    .scaleX(1.2f).scaleY(1.2f)
                    .setDuration(150)
                    .withEndAction {
                        tvResult.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(150)
                            .start()
                    }
                    .start()

                tvResult.postDelayed({
                    tvResult.setTextColor(0xFFFFFFFF.toInt())
                }, 3000)
            }
        })

        animator.start()
    }
}
