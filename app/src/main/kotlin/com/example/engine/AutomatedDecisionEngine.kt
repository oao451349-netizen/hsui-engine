package com.example.engine

import android.graphics.ImageFormat
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.Keep
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import kotlin.math.hypot
import kotlin.math.sqrt

class AutomatedDecisionEngine(
    private val touchService: TouchSimulationService,
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    companion object {
        const val DELTA_THRESHOLD = 15.0
        const val AVOIDANCE_DISTANCE = 200f

        init {
            System.loadLibrary("frame_processor")
        }
    }

    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var currentTaskJob: Job? = null

    private val frameExecutor = Executors.newFixedThreadPool(3) as ThreadPoolExecutor

    private val backgroundThread = HandlerThread("FrameIngestor").also { it.start() }
    private val backgroundHandler = Handler(backgroundThread.looper)

    private var imageReader: ImageReader? = null

    fun attachImageReader(reader: ImageReader) {
        imageReader = reader
        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            frameExecutor.submit {
                try {
                    processFrameNative(image)
                } finally {
                    image.close()
                }
            }
        }, backgroundHandler)
    }

    /**
     * Called from JNI when frame delta >= DELTA_THRESHOLD.
     * sourceX/sourceY — pixel coordinates of the detected change peak.
     */
    @Keep
    fun onCriticalFrameEvent(sourceX: Int, sourceY: Int, delta: Double) {
        currentTaskJob?.cancel()

        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f
        val dx = centerX - sourceX
        val dy = centerY - sourceY
        val magnitude = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)

        val targetX = (centerX + (dx / magnitude) * AVOIDANCE_DISTANCE)
            .toInt().coerceIn(0, screenWidth)
        val targetY = (centerY + (dy / magnitude) * AVOIDANCE_DISTANCE)
            .toInt().coerceIn(0, screenHeight)

        touchService.dispatchAvoidanceSwipe(
            fromX = centerX.toInt(),
            fromY = centerY.toInt(),
            toX = targetX,
            toY = targetY
        )
    }

    fun submitTask(block: suspend () -> Unit) {
        currentTaskJob = engineScope.launch { block() }
    }

    fun release() {
        engineScope.cancel()
        frameExecutor.shutdown()
        backgroundThread.quitSafely()
        imageReader = null
    }

    // ── JNI declarations ──────────────────────────────────────────────────────

    private external fun processFrameNative(image: Any)

    external fun setRoi(x: Int, y: Int, width: Int, height: Int)
    external fun setDeltaThreshold(threshold: Double)
}
