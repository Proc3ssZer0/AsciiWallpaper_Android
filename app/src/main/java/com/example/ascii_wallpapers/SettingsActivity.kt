package com.example.ascii_wallpapers

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val toggleShape = findViewById<MaterialButtonToggleGroup>(R.id.toggleShape)
        val sliderSpeed = findViewById<Slider>(R.id.sliderSpeed)
        val sliderSize = findViewById<Slider>(R.id.sliderSize)
        val sliderDensity = findViewById<Slider>(R.id.sliderDensity)
        val sliderFps = findViewById<Slider>(R.id.sliderFps)
        val btnGreen = findViewById<MaterialButton>(R.id.btnColorGreen)
        val btnAmber = findViewById<MaterialButton>(R.id.btnColorAmber)
        val btnWhite = findViewById<MaterialButton>(R.id.btnColorWhite)

        // 1. Загружаем текущие настройки фигуры
        val currentShape = prefs.getString("shape", "torus")
        when (currentShape) {
            "sphere" -> toggleShape.check(R.id.btnSphere)
            "cube" -> toggleShape.check(R.id.btnCube)
            else -> toggleShape.check(R.id.btnTorus)
        }

        // 2. Загружаем ползунки
        val savedSpeed = prefs.getInt("speed", 50).toFloat().coerceIn(10f, 100f)
        sliderSpeed.value = savedSpeed

        val savedSize = prefs.getInt("size", 24).toFloat().coerceIn(10f, 60f)
        sliderSize.value = savedSize

        val savedDensity = prefs.getInt("density", 10).toFloat().coerceIn(5f, 30f)
        sliderDensity.value = savedDensity

        val savedFps = prefs.getInt("fps", 30).toFloat().coerceIn(10f, 120f)
        // Ползунок идёт с шагом 5, поэтому подгоняем сохранённое значение под шаг
        sliderFps.value = ((savedFps - 10f) / 5f).roundToInt() * 5f + 10f

        // 3. Обработчики изменений
        toggleShape.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val shape = when (checkedId) {
                    R.id.btnSphere -> "sphere"
                    R.id.btnCube -> "cube"
                    else -> "torus"
                }
                prefs.edit().putString("shape", shape).apply()
            }
        }

        sliderSpeed.addOnChangeListener { _, value, _ ->
            prefs.edit().putInt("speed", value.toInt()).apply()
        }

        sliderSize.addOnChangeListener { _, value, _ ->
            prefs.edit().putInt("size", value.toInt()).apply()
        }

        sliderDensity.addOnChangeListener { _, value, _ ->
            prefs.edit().putInt("density", value.toInt()).apply()
        }

        sliderFps.addOnChangeListener { _, value, _ ->
            prefs.edit().putInt("fps", value.toInt()).apply()
        }

        btnGreen.setOnClickListener { prefs.edit().putString("color", "green").apply() }
        btnAmber.setOnClickListener { prefs.edit().putString("color", "amber").apply() }
        btnWhite.setOnClickListener { prefs.edit().putString("color", "white").apply() }
    }
}