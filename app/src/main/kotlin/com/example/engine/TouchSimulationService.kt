package com.example.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class TouchSimulationService : AccessibilityService() {

    companion object {
        const val MIN_STROKE_DURATION_MS = 1L
        const val COORD_UPDATE_STEP_MS = 8L

        @Volatile
        var instance: TouchSimulationService? = null
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var decisionEngine: AutomatedDecisionEngine? = null

    @Volatile
    var isCapturing = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        stopCapture()
        return super.onUnbind(intent)
    }

    fun startCapture(resultCode: Int, data: Intent) {
        if (isCapturing) stopCapture()
        try {
            val projManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projManager.getMediaProjection(resultCode, data)

            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)

            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "HSUI_Capture", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )

            decisionEngine = AutomatedDecisionEngine(this, width, height).also { engine ->
                // Full-screen ROI — tracks bullets from all directions
                engine.setRoi(0, 0, width, height)
                engine.setDeltaThreshold(8.0)
                imageReader?.let { engine.attachImageReader(it) }
            }

            isCapturing = true
        } catch (e: Exception) {
            isCapturing = false
        }
    }

    fun stopCapture() {
        isCapturing = false
        try {
            decisionEngine?.release()
            virtualDisplay?.release()
            imageReader?.close()
            mediaProjection?.stop()
        } catch (_: Exception) {}
        decisionEngine = null
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
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

    fun dispatchSyncedSwipe(points: List<Pair<Float, Float>>, durationPerStep: Long = COORD_UPDATE_STEP_MS) {
        if (points.isEmpty()) return
        val path = Path()
        path.moveTo(points[0].first, points[0].second)
        points.drop(1).forEach { (x, y) -> path.lineTo(x, y) }
        val totalDuration = (points.size.toLong() * durationPerStep).coerceAtLeast(MIN_STROKE_DURATION_MS)
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
