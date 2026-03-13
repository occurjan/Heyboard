package com.heyboard.teachingassistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

class SpotlightView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var spotX = 0f
    private var spotY = 0f
    private var spotRadius = 200f
    private var minRadius = 80f
    private var maxRadius = 800f

    private val overlayPaint = Paint().apply {
        color = Color.argb(200, 0, 0, 0) // 约80%不透明度的黑色蒙版
    }

    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        isAntiAlias = true
    }

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                spotRadius = (spotRadius * detector.scaleFactor).coerceIn(minRadius, maxRadius)
                invalidate()
                return true
            }
        })

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        spotX = w / 2f
        spotY = h / 2f
        spotRadius = w.coerceAtMost(h) / 4f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 使用离屏缓冲来支持 PorterDuff.Mode.CLEAR
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        // 画半透明黑色蒙版
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        // 在聚光灯位置挖出透明圆形
        canvas.drawCircle(spotX, spotY, spotRadius, clearPaint)
        canvas.restoreToCount(layer)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = event.pointerCount == 1
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    spotX = (spotX + dx).coerceIn(0f, width.toFloat())
                    spotY = (spotY + dy).coerceIn(0f, height.toFloat())
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
