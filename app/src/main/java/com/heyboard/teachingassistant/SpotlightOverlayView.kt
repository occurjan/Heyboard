package com.heyboard.teachingassistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.View

/**
 * 纯绘制蒙版视图，不处理触摸。
 * 触摸由 SpotlightService 的控制窗口处理。
 */
class SpotlightOverlayView(
    context: Context,
    maskAlpha: Int = 200
) : View(context) {

    private var circleX = 0f
    private var circleY = 0f
    private var circleRadius = 200f

    private val overlayPaint = Paint().apply {
        color = Color.argb(maskAlpha, 0, 0, 0)
    }

    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        isAntiAlias = true
    }

    fun updateOpacity(alpha: Int) {
        overlayPaint.color = Color.argb(alpha, 0, 0, 0)
        invalidate()
    }

    fun updateCircle(x: Float, y: Float, radius: Float) {
        circleX = x
        circleY = y
        circleRadius = radius
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        canvas.drawCircle(circleX, circleY, circleRadius, clearPaint)
        canvas.restoreToCount(layer)
    }
}
