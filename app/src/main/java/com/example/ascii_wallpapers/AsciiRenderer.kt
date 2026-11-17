package com.example.ascii_wallpapers

import android.graphics.Canvas
import android.graphics.Paint

object AsciiRenderer {
    private val shades = charArrayOf('.', ',', '-', '~', ':', ';', '=', '!', '*', '#', '$', '@')

    // Плотность: 1.0 — базовый размер символа, больше — символов больше и они мельче.
    private const val MIN_DENSITY = 0.5f
    private const val MAX_DENSITY = 3f

    /**
     * Подготавливает параметры сетки.
     *
     * Плотность уменьшает размер символа (cellWidth/cellHeight), поэтому колонок и строк
     * становится больше, а сами символы — меньше. [projectionScale] компенсирует уменьшение
     * ячейки, чтобы фигура на экране оставалась того же размера.
     */
    private class Grid(
        val cols: Int,
        val rows: Int,
        val cellWidth: Float,
        val cellHeight: Float,
        val projectionScale: Float
    )

    private fun prepareGrid(canvas: Canvas, paint: Paint, density: Float): Grid? {
        val d = density.coerceIn(MIN_DENSITY, MAX_DENSITY)
        val baseTextSize = paint.textSize

        // Плотнее сетка -> мельче символ
        paint.textSize = baseTextSize / d

        val cellWidth = paint.measureText("M")
        val cellHeight = paint.textSize
        val cols = (canvas.width / cellWidth).toInt()
        val rows = (canvas.height / cellHeight).toInt()

        if (cols <= 0 || rows <= 0) {
            paint.textSize = baseTextSize
            return null
        }
        return Grid(cols, rows, cellWidth, cellHeight, d)
    }

    // --- 1. ТОР (Бесконечная лента) ---
    fun renderTorus(canvas: Canvas, paint: Paint, angleA: Double, angleB: Double, density: Float) {
        val baseTextSize = paint.textSize
        val grid = prepareGrid(canvas, paint, density) ?: return
        try {
            val cols = grid.cols
            val rows = grid.rows
            val d = grid.projectionScale

            val zBuffer = FloatArray(cols * rows) { 0f }
            val chars = Array(rows) { CharArray(cols) { ' ' } }

            val R = 2.0
            val r = 1.0
            val K2 = 5.0

            // Шаг выборки поверхности уменьшается вместе с ячейкой,
            // иначе на плотной сетке появляются "дырки".
            val thetaStep = 0.07 / d
            val phiStep = 0.02 / d

            var theta = 0.0
            while (theta < 2 * Math.PI) {
                var phi = 0.0
                while (phi < 2 * Math.PI) {
                    val cosTheta = Math.cos(theta)
                    val sinTheta = Math.sin(theta)
                    val cosPhi = Math.cos(phi)
                    val sinPhi = Math.sin(phi)

                    val circleX = R + r * cosTheta
                    val circleY = r * sinTheta

                    val x = circleX * cosPhi
                    val y = circleY
                    val z = circleX * sinPhi

                    val y1 = y * Math.cos(angleA) - z * Math.sin(angleA)
                    val z1 = y * Math.sin(angleA) + z * Math.cos(angleA)
                    val x1 = x * Math.cos(angleB) - z1 * Math.sin(angleB)
                    val z2 = x * Math.sin(angleB) + z1 * Math.cos(angleB)

                    val ooz = 1.0 / (z2 + K2)
                    val xp = (cols / 2 + 25 * ooz * x1 * d).toInt()
                    val yp = (rows / 2 - 12 * ooz * y1 * d).toInt()

                    val L = cosTheta * cosPhi * Math.sin(angleA) - cosTheta * sinPhi * Math.cos(angleA) * Math.sin(angleB) - sinTheta * Math.cos(angleA) * Math.cos(angleB)
                    val luminance = (L + 1) / 2
                    val shadeIndex = (luminance * (shades.size - 1)).toInt().coerceIn(0, shades.size - 1)

                    if (xp in 0 until cols && yp in 0 until rows) {
                        val idx = xp + yp * cols
                        if (ooz > zBuffer[idx]) {
                            zBuffer[idx] = ooz.toFloat()
                            chars[yp][xp] = shades[shadeIndex]
                        }
                    }
                    phi += phiStep
                }
                theta += thetaStep
            }
            drawChars(canvas, paint, chars, cols, rows, grid.cellWidth, grid.cellHeight)
        } finally {
            paint.textSize = baseTextSize
        }
    }

    // --- 2. СФЕРА ---
    fun renderSphere(canvas: Canvas, paint: Paint, angleA: Double, angleB: Double, density: Float) {
        val baseTextSize = paint.textSize
        val grid = prepareGrid(canvas, paint, density) ?: return
        try {
            val cols = grid.cols
            val rows = grid.rows
            val d = grid.projectionScale

            val zBuffer = FloatArray(cols * rows) { 0f }
            val chars = Array(rows) { CharArray(cols) { ' ' } }

            val R = 2.0
            val K2 = 5.0

            val thetaStep = 0.05 / d
            val phiStep = 0.05 / d

            var theta = 0.0
            while (theta < Math.PI) {
                var phi = 0.0
                while (phi < 2 * Math.PI) {
                    val x = R * Math.sin(theta) * Math.cos(phi)
                    val y = R * Math.cos(theta)
                    val z = R * Math.sin(theta) * Math.sin(phi)

                    val y1 = y * Math.cos(angleA) - z * Math.sin(angleA)
                    val z1 = y * Math.sin(angleA) + z * Math.cos(angleA)
                    val x1 = x * Math.cos(angleB) - z1 * Math.sin(angleB)
                    val z2 = x * Math.sin(angleB) + z1 * Math.cos(angleB)

                    val ooz = 1.0 / (z2 + K2)
                    val xp = (cols / 2 + 25 * ooz * x1 * d).toInt()
                    val yp = (rows / 2 - 12 * ooz * y1 * d).toInt()

                    val L = (x * Math.sin(angleB) + y * Math.cos(angleA) * Math.cos(angleB) + z * Math.sin(angleA) * Math.cos(angleB))
                    val luminance = (L + 1) / 2
                    val shadeIndex = (luminance * (shades.size - 1)).toInt().coerceIn(0, shades.size - 1)

                    if (xp in 0 until cols && yp in 0 until rows) {
                        val idx = xp + yp * cols
                        if (ooz > zBuffer[idx]) {
                            zBuffer[idx] = ooz.toFloat()
                            chars[yp][xp] = shades[shadeIndex]
                        }
                    }
                    phi += phiStep
                }
                theta += thetaStep
            }
            drawChars(canvas, paint, chars, cols, rows, grid.cellWidth, grid.cellHeight)
        } finally {
            paint.textSize = baseTextSize
        }
    }

    // --- 3. КУБ ---
    fun renderCube(canvas: Canvas, paint: Paint, angleA: Double, angleB: Double, density: Float) {
        val baseTextSize = paint.textSize
        val grid = prepareGrid(canvas, paint, density) ?: return
        try {
            val cols = grid.cols
            val rows = grid.rows
            val d = grid.projectionScale

            val zBuffer = FloatArray(cols * rows) { 0f }
            val chars = Array(rows) { CharArray(cols) { ' ' } }

            val K2 = 5.0
            val S = 1.5

            val vertices = listOf(
                Triple(-S, -S, -S), Triple(S, -S, -S), Triple(S, S, -S), Triple(-S, S, -S),
                Triple(-S, -S, S), Triple(S, -S, S), Triple(S, S, S), Triple(-S, S, S)
            )
            val edges = listOf(
                Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 0),
                Pair(4, 5), Pair(5, 6), Pair(6, 7), Pair(7, 4),
                Pair(0, 4), Pair(1, 5), Pair(2, 6), Pair(3, 7)
            )

            val steps = (40 * d).toInt().coerceAtLeast(40)

            for (edge in edges) {
                val p1 = vertices[edge.first]
                val p2 = vertices[edge.second]
                for (i in 0..steps) {
                    val t = i.toDouble() / steps
                    var x = p1.first + (p2.first - p1.first) * t
                    var y = p1.second + (p2.second - p1.second) * t
                    var z = p1.third + (p2.third - p1.third) * t

                    val y1 = y * Math.cos(angleA) - z * Math.sin(angleA)
                    val z1 = y * Math.sin(angleA) + z * Math.cos(angleA)
                    val x1 = x * Math.cos(angleB) - z1 * Math.sin(angleB)
                    val z2 = x * Math.sin(angleB) + z1 * Math.cos(angleB)

                    val ooz = 1.0 / (z2 + K2)
                    val xp = (cols / 2 + 25 * ooz * x1 * d).toInt()
                    val yp = (rows / 2 - 12 * ooz * y1 * d).toInt()

                    if (xp in 0 until cols && yp in 0 until rows) {
                        val idx = xp + yp * cols
                        if (ooz > zBuffer[idx]) {
                            zBuffer[idx] = ooz.toFloat()
                            chars[yp][xp] = '#'
                        }
                    }
                }
            }
            drawChars(canvas, paint, chars, cols, rows, grid.cellWidth, grid.cellHeight)
        } finally {
            paint.textSize = baseTextSize
        }
    }

    // --- Вспомогательная функция для отрисовки ---
    private fun drawChars(
        canvas: Canvas, paint: Paint, chars: Array<CharArray>,
        cols: Int, rows: Int, cellWidth: Float, cellHeight: Float
    ) {
        // Центрируем символ по вертикали внутри ячейки, чтобы положение фигуры
        // не зависело от плотности сетки.
        val baselineOffset = cellHeight * 0.8f
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                if (chars[y][x] != ' ') {
                    canvas.drawText(
                        chars[y][x].toString(),
                        x * cellWidth,
                        y * cellHeight + baselineOffset,
                        paint
                    )
                }
            }
        }
    }
}
