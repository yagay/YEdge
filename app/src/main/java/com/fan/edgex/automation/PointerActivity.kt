package com.fan.edgex.automation

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.fan.edgex.config.requestHookActionExecution

/** One-shot touch pointer: choose a screen coordinate, close the overlay, then inject a tap. */
class PointerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )
        setContentView(PointerView())
    }

    private inner class PointerView : View(this) {
        private val density = resources.displayMetrics.density
        private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 2f * density
            style = Paint.Style.STROKE
        }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xAA5B8CFF.toInt()
            style = Paint.Style.FILL
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14f * density
        }
        private val closePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCC202124.toInt()
            style = Paint.Style.FILL
        }

        private var pointerX = resources.displayMetrics.widthPixels / 2f
        private var pointerY = resources.displayMetrics.heightPixels / 2f

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val radius = 24f * density
            canvas.drawCircle(pointerX, pointerY, radius, ringPaint)
            canvas.drawCircle(pointerX, pointerY, radius, crossPaint)
            canvas.drawLine(pointerX - radius * 1.5f, pointerY, pointerX + radius * 1.5f, pointerY, crossPaint)
            canvas.drawLine(pointerX, pointerY - radius * 1.5f, pointerX, pointerY + radius * 1.5f, crossPaint)
            canvas.drawText("Release to tap", 20f * density, 42f * density, textPaint)

            val closeCx = width - 34f * density
            val closeCy = 34f * density
            canvas.drawCircle(closeCx, closeCy, 22f * density, closePaint)
            canvas.drawLine(closeCx - 7f * density, closeCy - 7f * density, closeCx + 7f * density, closeCy + 7f * density, crossPaint)
            canvas.drawLine(closeCx + 7f * density, closeCy - 7f * density, closeCx - 7f * density, closeCy + 7f * density, crossPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            pointerX = event.rawX
            pointerY = event.rawY
            invalidate()
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                val closeX = resources.displayMetrics.widthPixels - 34f * density
                val closeY = 34f * density
                val dx = event.rawX - closeX
                val dy = event.rawY - closeY
                if (dx * dx + dy * dy <= (34f * density) * (34f * density)) {
                    finish()
                    return true
                }
                val x = event.rawX.toInt()
                val y = event.rawY.toInt()
                finish()
                Handler(Looper.getMainLooper()).postDelayed({
                    applicationContext.requestHookActionExecution("shell:true:input tap $x $y")
                }, 180L)
            }
            return true
        }
    }
}
