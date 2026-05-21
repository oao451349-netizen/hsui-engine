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
import android.widget.ImageButton
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.sqrt

class FloatingButtonService : Service() {

    companion object {
        @Volatile
        var isRunning = false
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        showFloatingButton()
    }

    private fun showFloatingButton() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val button = ImageButton(this).apply {
            setBackgroundColor(Color.argb(200, 255, 80, 80))
            setImageDrawable(null)
            setPadding(20, 20, 20, 20)
            contentDescription = "Уворот"
        }

        // Draw "!" text on button
        val label = android.widget.TextView(this).apply {
            text = "⚡"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val container = android.widget.FrameLayout(this).apply {
            val size = (80 * resources.displayMetrics.density).toInt()
            layoutParams = android.widget.FrameLayout.LayoutParams(size, size)
            background = resources.getDrawable(android.R.drawable.btn_default, null)
            setBackgroundColor(Color.argb(210, 220, 50, 50))
            addView(label, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }

        val params = WindowManager.LayoutParams(
            (80 * resources.displayMetrics.density).toInt(),
            (80 * resources.displayMetrics.density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 300
        }

        container.setOnTouchListener(object : View.OnTouchListener {
            private var isDrag = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isDrag = false
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        if (sqrt(dx * dx + dy * dy) > 10f) isDrag = true
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager?.updateViewLayout(container, params)
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDrag) {
                            performDodge()
                        }
                    }
                }
                return true
            }
        })

        floatingView = container
        windowManager?.addView(container, params)
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
        val offset = dm.heightPixels / 4

        // Dodge upward by default
        svc.dispatchAvoidanceSwipe(cx, cy, cx, cy - offset)
    }

    override fun onDestroy() {
        isRunning = false
        floatingView?.let { windowManager?.removeView(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
