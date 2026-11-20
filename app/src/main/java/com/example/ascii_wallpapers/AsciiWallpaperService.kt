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
import androidx.preference.PreferenceManager

class AsciiWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = AsciiEngine()

    inner class AsciiEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint().apply {
            color = Color.GREEN
            textSize = 24f
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
            // Дробные позиции символов: ровная сетка и без дрейфа при пакетной отрисовке
            isSubpixelText = true
        }

        private var visible = false
        private var shapeType = "torus"
        private var angleA = 0.0
        private var angleB = 0.0
        private var speedMultiplier = 1.0f
        private var densityMultiplier = 1.0f // Новая переменная
        private var targetFps = 30
        private var frameDelayMs = 1000L / 30
        private var lastFrameNanos = 0L

        private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this@AsciiWallpaperService)

        private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            loadSettings()
        }

        private val drawRunnable = object : Runnable {
            override fun run() {
                val startNanos = System.nanoTime()
                drawFrame()
                if (visible) {
                    // Вычитаем время отрисовки, чтобы выдержать заданный FPS
                    val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
                    handler.postDelayed(this, (frameDelayMs - elapsedMs).coerceAtLeast(1L))
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                loadSettings()
                lastFrameNanos = 0L
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

        private fun loadSettings() {
            // 1. Фигура
            shapeType = prefs.getString("shape", "torus") ?: "torus"

            // 2. Скорость
            val speedVal = prefs.getInt("speed", 50)
            speedMultiplier = (speedVal / 50f).coerceAtLeast(0.1f)

            // 3. Цвет
            val colorStr = prefs.getString("color", "green")
            paint.color = when (colorStr) {
                "amber" -> Color.parseColor("#FFBF00")
                "white" -> Color.WHITE
                else -> Color.GREEN
            }

            // 4. Размер символов
            val sizeVal = prefs.getInt("size", 24)
            paint.textSize = sizeVal.toFloat()

            // 5. Плотность сетки (10 по умолчанию = 1.0f)
            val densityVal = prefs.getInt("density", 10)
            densityMultiplier = (densityVal / 10f).coerceAtLeast(0.5f)

            // 6. Частота кадров
            val fpsVal = prefs.getInt("fps", 30).coerceIn(10, 120)
            targetFps = fpsVal
            frameDelayMs = (1000L / fpsVal).coerceAtLeast(1L)
        }

        private fun drawFrame() {
            val now = System.nanoTime()
            // Время с прошлого кадра — скорость анимации не зависит от FPS
            val deltaSeconds = if (lastFrameNanos == 0L) {
                1f / targetFps
            } else {
                ((now - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.1f)
            }
            lastFrameNanos = now

            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                canvas?.let {
                    it.drawColor(Color.BLACK)

                    // Передаем densityMultiplier в рендерер
                    when (shapeType) {
                        "sphere" -> AsciiRenderer.renderSphere(it, paint, angleA, angleB, densityMultiplier)
                        "cube" -> AsciiRenderer.renderCube(it, paint, angleA, angleB, densityMultiplier)
                        "dna" -> AsciiRenderer.renderDna(it, paint, angleA, angleB, densityMultiplier)
                        else -> AsciiRenderer.renderTorus(it, paint, angleA, angleB, densityMultiplier)
                    }
                }
            } finally {
                canvas?.let { holder.unlockCanvasAndPost(it) }
            }

            // 0.05 и 0.03 на кадр при 30 FPS -> 1.5 и 0.9 рад/с
            angleA += 1.5 * speedMultiplier * deltaSeconds
            angleB += 0.9 * speedMultiplier * deltaSeconds
        }
    }
}