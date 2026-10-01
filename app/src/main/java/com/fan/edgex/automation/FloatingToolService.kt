package com.fan.edgex.automation

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.fan.edgex.config.requestHookActionExecution

/** Draggable floating system-action palette and pass-through screen dim filter. */
class FloatingToolService : Service() {
    private var windowManager: WindowManager? = null
    private val attachedViews = mutableListOf<View>()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            runCatching {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
            stopSelf()
            return START_NOT_STICKY
        }
        clearViews()
        when (intent?.getStringExtra("mode")) {
            "filter" -> showFilter()
            else -> showTools()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        clearViews()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showTools() {
        val wm = windowManager ?: return
        val density = resources.displayMetrics.density
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((6 * density).toInt(), (4 * density).toInt(), (6 * density).toInt(), (4 * density).toInt())
            setBackgroundColor(0xE6202124.toInt())
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (16 * density).toInt()
            y = (160 * density).toInt()
        }

        addButton(bar, "≡") { }
        addButton(bar, "Back") { requestHookActionExecution("back") }
        addButton(bar, "Home") { requestHookActionExecution("home") }
        addButton(bar, "Recent") { requestHookActionExecution("recents") }
        addButton(bar, "×") { stopSelf() }

        var startX = 0
        var startY = 0
        var downX = 0f
        var downY = 0f
        bar.getChildAt(0)?.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    downX = event.rawX
                    downY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - downX).toInt()
                    params.y = startY + (event.rawY - downY).toInt()
                    runCatching { wm.updateViewLayout(bar, params) }
                    true
                }
                else -> true
            }
        }

        wm.addView(bar, params)
        attachedViews += bar
    }

    private fun showFilter() {
        val wm = windowManager ?: return
        val density = resources.displayMetrics.density
        val filter = View(this).apply { setBackgroundColor(0x66000000) }
        val filterParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        wm.addView(filter, filterParams)
        attachedViews += filter

        val close = TextView(this).apply {
            text = "×"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC202124.toInt())
            setOnClickListener { stopSelf() }
        }
        val closeParams = WindowManager.LayoutParams(
            (48 * density).toInt(),
            (48 * density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (16 * density).toInt()
            y = (80 * density).toInt()
        }
        wm.addView(close, closeParams)
        attachedViews += close
    }

    private fun addButton(parent: LinearLayout, label: String, onClick: () -> Unit) {
        val density = resources.displayMetrics.density
        parent.addView(TextView(this).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
            setOnClickListener { onClick() }
        })
    }

    private fun clearViews() {
        val wm = windowManager ?: return
        attachedViews.toList().forEach { runCatching { wm.removeView(it) } }
        attachedViews.clear()
    }
}
