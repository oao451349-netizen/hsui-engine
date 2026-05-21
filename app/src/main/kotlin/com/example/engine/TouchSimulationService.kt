package com.example.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class TouchSimulationService : AccessibilityService() {

    companion object {
        const val MIN_STROKE_DURATION_MS = 1L
        const val COORD_UPDATE_STEP_MS = 8L

        @Volatile
        var instance: TouchSimulationService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    fun dispatchAvoidanceSwipe(fromX: Int, fromY: Int, toX: Int, toY: Int) {
        val path = Path().apply {
            moveTo(fromX.toFloat(), fromY.toFloat())
            lineTo(toX.toFloat(), toY.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, MIN_STROKE_DURATION_MS, false)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {}
            override fun onCancelled(gestureDescription: GestureDescription) {}
        }, null)
    }

    fun dispatchSyncedSwipe(
        points: List<Pair<Float, Float>>,
        durationPerStep: Long = COORD_UPDATE_STEP_MS
    ) {
        if (points.isEmpty()) return
        val path = Path()
        path.moveTo(points[0].first, points[0].second)
        points.drop(1).forEach { (x, y) -> path.lineTo(x, y) }
        val totalDuration = (points.size.toLong() * durationPerStep)
            .coerceAtLeast(MIN_STROKE_DURATION_MS)
        val stroke = GestureDescription.StrokeDescription(path, 0L, totalDuration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun dispatchTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, MIN_STROKE_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
