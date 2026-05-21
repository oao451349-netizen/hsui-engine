package com.example.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import kotlin.math.hypot

class TouchSimulationService : AccessibilityService() {

    companion object {
        /** Minimum stroke duration allowed by Android API (API 24+). */
        const val MIN_STROKE_DURATION_MS = 1L

        /** Coordinate update step synced with 120 Hz Vsync (~8 ms). Set 16L for 60 Hz. */
        const val COORD_UPDATE_STEP_MS = 8L
    }

    // ── Avoidance gesture ─────────────────────────────────────────────────────

    /**
     * Dispatches a high-priority single-stroke swipe away from the detected
     * disturbance source. Uses the minimum allowed stroke duration (1 ms) for
     * the lowest possible input latency.
     */
    fun dispatchAvoidanceSwipe(fromX: Int, fromY: Int, toX: Int, toY: Int) {
        val path = Path().apply {
            moveTo(fromX.toFloat(), fromY.toFloat())
            lineTo(toX.toFloat(), toY.toFloat())
        }

        val stroke = GestureDescription.StrokeDescription(
            path,
            0L,
            MIN_STROKE_DURATION_MS,
            false
        )

        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                // Ready to resume task chain
            }
            override fun onCancelled(gestureDescription: GestureDescription) {
                // Competing gesture cancelled this one — log if needed
            }
        }, null)
    }

    // ── Vsync-synced multi-point swipe ────────────────────────────────────────

    /**
     * Dispatches a smooth swipe across [points], with each coordinate step
     * timed to the screen refresh period (default 8 ms = 120 Hz).
     *
     * Use for normal task-chain gestures where smoothness matters.
     */
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

    // ── Tap helper ────────────────────────────────────────────────────────────

    /**
     * Dispatches a minimal tap at (x, y).
     */
    fun dispatchTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, MIN_STROKE_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    // ── AccessibilityService callbacks ────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}
}
