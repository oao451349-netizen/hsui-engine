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
        const val REQUEST_CODE_CAPTURE = 100
        const val REQUEST_CODE_NOTIF = 101
        const val REQUEST_CODE_OVERLAY = 102
    }

    private lateinit var projManager: MediaProjectionManager
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var floatButton: Button
    private lateinit var hintText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        statusText = findViewById(R.id.statusText)
        startButton = findViewById(R.id.startButton)
        floatButton = findViewById(R.id.floatButton)
        hintText = findViewById(R.id.hintText)

        startButton.setOnClickListener {
            val svc = TouchSimulationService.instance
            if (svc == null) {
                Toast.makeText(this, "Сначала включите HSUI Engine в Настройки → Спец. возможности", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (svc.isCapturing) {
                svc.stopCapture()
                updateUI()
            } else {
                requestNotifThenCapture()
            }
        }

        floatButton.setOnClickListener {
            if (FloatingButtonService.isRunning) {
                stopService(Intent(this, FloatingButtonService::class.java))
            } else {
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Разрешите отображение поверх других приложений", Toast.LENGTH_LONG).show()
                    startActivityForResult(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                        REQUEST_CODE_OVERLAY
                    )
                } else {
                    startService(Intent(this, FloatingButtonService::class.java))
                    Toast.makeText(this, "Кнопка уворота ⚡ появится поверх игры", Toast.LENGTH_SHORT).show()
                }
            }
            updateUI()
        }
    }

    private fun requestNotifThenCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE_NOTIF)
        } else {
            startActivityForResult(projManager.createScreenCaptureIntent(), REQUEST_CODE_CAPTURE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_NOTIF) {
            startActivityForResult(projManager.createScreenCaptureIntent(), REQUEST_CODE_CAPTURE)
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_CAPTURE -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val svc = TouchSimulationService.instance
                    if (svc == null) {
                        Toast.makeText(this, "Включите спец. возможности!", Toast.LENGTH_LONG).show()
                        return
                    }
                    svc.startCapture(resultCode, data)
                    if (svc.isCapturing) {
                        Toast.makeText(this, "Автоуворот запущен! Заходи в игру.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Не удалось запустить. Попробуй ещё раз.", Toast.LENGTH_LONG).show()
                    }
                    updateUI()
                }
            }
            REQUEST_CODE_OVERLAY -> {
                if (Settings.canDrawOverlays(this)) {
                    startService(Intent(this, FloatingButtonService::class.java))
                    Toast.makeText(this, "Кнопка уворота ⚡ появится поверх игры", Toast.LENGTH_SHORT).show()
                    updateUI()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        val svc = TouchSimulationService.instance
        val capturing = svc?.isCapturing == true
        val accessOn = svc != null

        statusText.text = when {
            capturing -> "✅ Автоуворот активен"
            else -> "⏸ Автоуворот выключен"
        }
        startButton.text = if (capturing) "Остановить автоуворот" else "Запустить автоуворот"
        startButton.setBackgroundColor(if (capturing) 0xFFE53935.toInt() else 0xFF43A047.toInt())

        floatButton.text = if (FloatingButtonService.isRunning) "Убрать кнопку ⚡" else "Кнопка уворота ⚡"
        floatButton.setBackgroundColor(if (FloatingButtonService.isRunning) 0xFFE53935.toInt() else 0xFF1565C0.toInt())

        hintText.text = if (accessOn) "Спец. возможности: ✅ включено"
        else "⚠️ Включите HSUI Engine в\nНастройки → Спец. возможности"
    }
}
