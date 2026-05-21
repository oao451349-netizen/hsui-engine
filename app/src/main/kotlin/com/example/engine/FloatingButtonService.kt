package com.example.engine

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.sqrt

class FloatingButtonService : Service() {

    companion object {
        @Volatile
        var isRunning = false
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        showFloatingButton()
    }

    private fun showFloatingButton() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        val sizePx = (72 * dm.density).toInt()

        val container = FrameLayout(this)
        val label = TextView(this).apply {
            text = "⚡"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        container.setBackgroundColor(Color.argb(220, 200, 40, 40))
        container.addView(label, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val params = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20; y = 400
        }

        var initialX = 0; var initialY = 0
        var initialTouchX = 0f; var initialTouchY = 0f
        var isDrag = false

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDrag = false
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (sqrt(dx * dx + dy * dy) > 8f) isDrag = true
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    windowManager?.updateViewLayout(container, params)
                }
                MotionEvent.ACTION_UP -> { if (!isDrag) performDodge() }
            }
            true
        }

        floatingView = container
        try {
            windowManager?.addView(container, params)
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun performDodge() {
        val svc = TouchSimulationService.instance
        if (svc == null) {
            Toast.makeText(this, "Включите спец. возможности!", Toast.LENGTH_SHORT).show()
            return
        }
        val dm = resources.displayMetrics
        val cx = dm.widthPixels / 2
        val cy = dm.heightPixels / 2
        svc.dispatchAvoidanceSwipe(cx, cy, cx, cy - dm.heightPixels / 5)
    }

    override fun onDestroy() {
        isRunning = false
        try { floatingView?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
