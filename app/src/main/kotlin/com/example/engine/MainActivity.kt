package com.example.engine

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
        const val REQUEST_CODE_OVERLAY = 102
    }

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var floatButton: Button
    private lateinit var hintText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        statusText = findViewById(R.id.statusText)
        startButton = findViewById(R.id.startButton)
        floatButton = findViewById(R.id.floatButton)
        hintText = findViewById(R.id.hintText)

        startButton.setOnClickListener {
            if (ScreenCaptureService.isRunning) {
                stopService(Intent(this, ScreenCaptureService::class.java))
                updateUI()
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

        floatButton.setOnClickListener {
            if (FloatingButtonService.isRunning) {
                stopService(Intent(this, FloatingButtonService::class.java))
                updateUI()
            } else {
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Разрешите отображение поверх других приложений", Toast.LENGTH_LONG).show()
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivityForResult(intent, REQUEST_CODE_OVERLAY)
                } else {
                    startFloatingButton()
                }
            }
        }
    }

    private fun startFloatingButton() {
        val intent = Intent(this, FloatingButtonService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateUI()
        Toast.makeText(this, "Кнопка уворота появится поверх игры ⚡", Toast.LENGTH_SHORT).show()
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_NOTIFICATION) {
            startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE_SCREEN_CAPTURE)
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_SCREEN_CAPTURE -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val intent = Intent(this, ScreenCaptureService::class.java).apply {
                        putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                        putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    updateUI()
                }
            }
            REQUEST_CODE_OVERLAY -> {
                if (Settings.canDrawOverlays(this)) {
                    startFloatingButton()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        val accessEnabled = TouchSimulationService.instance != null

        if (ScreenCaptureService.isRunning) {
            statusText.text = "✅ Автоуворот активен"
            startButton.text = "Остановить автоуворот"
            startButton.setBackgroundColor(0xFFE53935.toInt())
        } else {
            statusText.text = "⏸ Автоуворот выключен"
            startButton.text = "Запустить автоуворот"
            startButton.setBackgroundColor(0xFF43A047.toInt())
        }

        if (FloatingButtonService.isRunning) {
            floatButton.text = "Убрать кнопку ⚡"
            floatButton.setBackgroundColor(0xFFE53935.toInt())
        } else {
            floatButton.text = "Кнопка уворота ⚡"
            floatButton.setBackgroundColor(0xFF1565C0.toInt())
        }

        hintText.text = if (accessEnabled)
            "Спец. возможности: ✅ включено"
        else
            "⚠️ Включите HSUI Engine в\nНастройки → Спец. возможности"
    }
}
