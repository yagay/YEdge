package com.fan.edgex.automation

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/** Transparent full-screen annotation layer similar to Xposed Edge's screen pen. */
class ScreenPenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )
        setContentView(PenView())
    }

    private inner class PenView : View(this) {
        private val density = resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE53935.toInt()
            strokeWidth = 4f * density
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCC202124.toInt()
            style = Paint.Style.FILL
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14f * density
            textAlign = Paint.Align.CENTER
        }
        private val paths = mutableListOf<Path>()
        private var current: Path? = null

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            paths.forEach { canvas.drawPath(it, paint) }
            current?.let { canvas.drawPath(it, paint) }

            val top = 18f * density
            drawButton(canvas, 18f * density, top, 82f * density, 42f * density, "Close")
            drawButton(canvas, width - 100f * density, top, 82f * density, 42f * density, "Clear")
        }

        private fun drawButton(canvas: Canvas, left: Float, top: Float, width: Float, height: Float, text: String) {
            canvas.drawRoundRect(left, top, left + width, top + height, 14f * density, 14f * density, buttonPaint)
            canvas.drawText(text, left + width / 2f, top + height * 0.66f, textPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val closeRect = event.x in 18f * density..100f * density && event.y in 18f * density..60f * density
            val clearRect = event.x in (width - 100f * density)..(width - 18f * density) && event.y in 18f * density..60f * density
            if (event.actionMasked == MotionEvent.ACTION_UP && closeRect) {
                finish()
                return true
            }
            if (event.actionMasked == MotionEvent.ACTION_UP && clearRect) {
                paths.clear()
                current = null
                invalidate()
                return true
            }
            if (event.y < 72f * density) return true

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    current = Path().apply { moveTo(event.x, event.y) }
                }
                MotionEvent.ACTION_MOVE -> current?.lineTo(event.x, event.y)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    current?.let(paths::add)
                    current = null
                }
            }
            invalidate()
            return true
        }
    }
}
