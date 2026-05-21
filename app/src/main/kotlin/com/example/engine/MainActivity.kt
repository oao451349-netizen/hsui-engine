package com.example.engine

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    companion object {
        const val REQUEST_CODE_SCREEN_CAPTURE = 100
        const val REQUEST_CODE_NOTIFICATION = 101
    }

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var hintText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        statusText = findViewById(R.id.statusText)
        startButton = findViewById(R.id.startButton)
        hintText = findViewById(R.id.hintText)

        startButton.setOnClickListener {
            if (ScreenCaptureService.isRunning) {
                stopService(Intent(this, ScreenCaptureService::class.java))
                updateUI(false)
            } else {
                if (TouchSimulationService.instance == null) {
                    Toast.makeText(this,
                        "Сначала включите HSUI Engine в Настройки → Спец. возможности",
                        Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                requestCapture()
            }
        }

        updateUI(ScreenCaptureService.isRunning)
    }

    private fun requestCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_NOTIFICATION)
                return
            }
        }
        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(),
            REQUEST_CODE_SCREEN_CAPTURE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_NOTIFICATION) {
            startActivityForResult(
                mediaProjectionManager.createScreenCaptureIntent(),
                REQUEST_CODE_SCREEN_CAPTURE
            )
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE
            && resultCode == Activity.RESULT_OK
            && data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            updateUI(true)
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI(ScreenCaptureService.isRunning)
        val accessEnabled = TouchSimulationService.instance != null
        hintText.text = if (accessEnabled)
            "Спец. возможности: ✅ включено"
        else
            "⚠️ Включите HSUI Engine в\nНастройки → Спец. возможности"
    }

    private fun updateUI(running: Boolean) {
        if (running) {
            statusText.text = "✅ Движок активен"
            startButton.text = "Остановить"
            startButton.setBackgroundColor(0xFFE53935.toInt())
        } else {
            statusText.text = "⏸ Движок остановлен"
            startButton.text = "Запустить"
            startButton.setBackgroundColor(0xFF43A047.toInt())
        }
    }
}
