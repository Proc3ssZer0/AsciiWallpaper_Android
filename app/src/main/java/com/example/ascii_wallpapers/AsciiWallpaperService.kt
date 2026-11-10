package com.example.ascii_wallpapers

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

class AsciiWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = AsciiEngine()

    inner class AsciiEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint().apply {
            color = Color.GREEN
            textSize = 24f
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }

        private var visible = false
        private var shapeType = "torus"
        private var angleA = 0.0
        private var angleB = 0.0
        private var speedMultiplier = 1.0f

        // Настройки
        private val prefs: SharedPreferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this@AsciiWallpaperService)

        // Слушатель изменений настроек (чтобы обои менялись на лету)
        private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            loadSettings()
        }

        private val drawRunnable = object : Runnable {
            override fun run() {
                drawFrame()
                if (visible) handler.postDelayed(this, 33L) // ~30 FPS
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                loadSettings()
                prefs.registerOnSharedPreferenceChangeListener(prefListener)
                handler.post(drawRunnable)
            } else {
                prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(drawRunnable)
        }

        // Чтение настроек из preferences.xml
        private fun loadSettings() {
            // 1. Фигура
            shapeType = prefs.getString("shape", "torus") ?: "torus"

            // 2. Скорость (от 0.1 до 2.0)
            val speedVal = prefs.getInt("speed", 50)
            speedMultiplier = (speedVal / 50f).coerceAtLeast(0.1f)

            // 3. Цвет
            val colorStr = prefs.getString("color", "green")
            paint.color = when (colorStr) {
                "amber" -> Color.parseColor("#FFBF00")
                "white" -> Color.WHITE
                else -> Color.GREEN
            }
            val sizeVal = prefs.getInt("size", 24)
            paint.textSize = sizeVal.toFloat()
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                canvas?.let {
                    it.drawColor(Color.BLACK)

                    // Вызываем нужный рендерер
                    when (shapeType) {
                        "sphere" -> AsciiRenderer.renderSphere(it, paint, angleA, angleB)
                        "cube" -> AsciiRenderer.renderCube(it, paint, angleA, angleB)
                        else -> AsciiRenderer.renderTorus(it, paint, angleA, angleB)
                    }
                }
            } finally {
                canvas?.let { holder.unlockCanvasAndPost(it) }
            }

            // Обновляем углы с учетом скорости
            angleA += 0.05 * speedMultiplier
            angleB += 0.03 * speedMultiplier
        }
    }
}