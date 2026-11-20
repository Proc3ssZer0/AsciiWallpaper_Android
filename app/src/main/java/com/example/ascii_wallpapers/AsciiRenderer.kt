package com.example.ascii_wallpapers

import android.graphics.Canvas
import android.graphics.Paint
import java.util.Arrays

object AsciiRenderer {
    private val shades = charArrayOf('.', ',', '-', '~', ':', ';', '=', '!', '*', '#', '$', '@')

    // Плотность: 1.0 — базовый размер символа, больше — символов больше и они мельче.
    private const val MIN_DENSITY = 0.5f
    private const val MAX_DENSITY = 3f

    // --- Переиспользуемые буферы (не выделяем память каждый кадр) ---
    private var charBuffer = CharArray(0)
    private var depthBuffer = FloatArray(0)
    private var runBuffer = CharArray(256)

    // Кэш cos/sin для колец тора и параллелей сферы
    private var thetaCos = DoubleArray(0)
    private var thetaSin = DoubleArray(0)
    private var phiCos = DoubleArray(0)
    private var phiSin = DoubleArray(0)

    // Предповёрнутые вершины куба (заполняются в renderCube)
    private val cubeRotX = DoubleArray(8)
    private val cubeRotY = DoubleArray(8)
    private val cubeRotZ = DoubleArray(8)

    private val cubeVertices = listOf(
        Triple(-1.5, -1.5, -1.5), Triple(1.5, -1.5, -1.5),
        Triple(1.5, 1.5, -1.5), Triple(-1.5, 1.5, -1.5),
        Triple(-1.5, -1.5, 1.5), Triple(1.5, -1.5, 1.5),
        Triple(1.5, 1.5, 1.5), Triple(-1.5, 1.5, 1.5)
    )
    private val cubeEdges = listOf(
        Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 0),
        Pair(4, 5), Pair(5, 6), Pair(6, 7), Pair(7, 4),
        Pair(0, 4), Pair(1, 5), Pair(2, 6), Pair(3, 7)
    )

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

    /** Готовит буфер символов: расширяет при необходимости и заполняет пробелами. */
    private fun prepareChars(cols: Int, rows: Int) {
        val size = cols * rows
        if (charBuffer.size < size) charBuffer = CharArray(size)
        Arrays.fill(charBuffer, 0, size, ' ')
    }

    /** Готовит z-буфер (только для 3D-фигур). */
    private fun prepareDepth(cols: Int, rows: Int) {
        val size = cols * rows
        if (depthBuffer.size < size) depthBuffer = FloatArray(size)
        Arrays.fill(depthBuffer, 0, size, 0f)
    }

    private fun ensureTrig(thetaCount: Int, phiCount: Int) {
        if (thetaCos.size < thetaCount) {
            thetaCos = DoubleArray(thetaCount)
            thetaSin = DoubleArray(thetaCount)
        }
        if (phiCos.size < phiCount) {
            phiCos = DoubleArray(phiCount)
            phiSin = DoubleArray(phiCount)
        }
    }

    private fun fillTrig(cosArr: DoubleArray, sinArr: DoubleArray, count: Int, step: Double) {
        var angle = 0.0
        for (i in 0 until count) {
            cosArr[i] = Math.cos(angle)
            sinArr[i] = Math.sin(angle)
            angle += step
        }
    }

    // --- 1. ТОР (Бесконечная лента) ---
    fun renderTorus(canvas: Canvas, paint: Paint, angleA: Double, angleB: Double, density: Float) {
        val baseTextSize = paint.textSize
        val grid = prepareGrid(canvas, paint, density) ?: return
        try {
            val cols = grid.cols
            val rows = grid.rows
            val d = grid.projectionScale
            prepareChars(cols, rows)
            prepareDepth(cols, rows)
            val chars = charBuffer
            val zBuffer = depthBuffer

            val R = 2.0
            val r = 1.0
            val K2 = 5.0

            // Шаг выборки поверхности уменьшается вместе с ячейкой,
            // иначе на плотной сетке появляются "дырки".
            val thetaStep = 0.07 / d
            val phiStep = 0.02 / d
            val thetaCount = Math.ceil(2 * Math.PI / thetaStep).toInt()
            val phiCount = Math.ceil(2 * Math.PI / phiStep).toInt()
            ensureTrig(thetaCount, phiCount)
            fillTrig(thetaCos, thetaSin, thetaCount, thetaStep)
            fillTrig(phiCos, phiSin, phiCount, phiStep)

            val cosA = Math.cos(angleA)
            val sinA = Math.sin(angleA)
            val cosB = Math.cos(angleB)
            val sinB = Math.sin(angleB)

            val halfCols = cols / 2.0
            val halfRows = rows / 2.0
            val shadesMax = shades.size - 1

            for (ti in 0 until thetaCount) {
                val cosTheta = thetaCos[ti]
                val sinTheta = thetaSin[ti]

                val circleX = R + r * cosTheta
                val circleY = r * sinTheta

                for (pi in 0 until phiCount) {
                    val cosPhi = phiCos[pi]
                    val sinPhi = phiSin[pi]

                    val x = circleX * cosPhi
                    val y = circleY
                    val z = circleX * sinPhi

                    val y1 = y * cosA - z * sinA
                    val z1 = y * sinA + z * cosA
                    val x1 = x * cosB - z1 * sinB
                    val z2 = x * sinB + z1 * cosB

                    val ooz = 1.0 / (z2 + K2)
                    val xp = (halfCols + 25 * ooz * x1 * d).toInt()
                    val yp = (halfRows - 12 * ooz * y1 * d).toInt()

                    if (xp in 0 until cols && yp in 0 until rows) {
                        val idx = yp * cols + xp
                        if (ooz > zBuffer[idx]) {
                            zBuffer[idx] = ooz.toFloat()
                            // Освещение считаем только для видимых точек
                            val L = cosTheta * cosPhi * sinA - cosTheta * sinPhi * cosA * sinB - sinTheta * cosA * cosB
                            val luminance = (L + 1) / 2
                            val shadeIndex = (luminance * shadesMax).toInt().coerceIn(0, shadesMax)
                            chars[idx] = shades[shadeIndex]
                        }
                    }
                }
            }
            drawChars(canvas, paint, cols, rows, grid.cellWidth, grid.cellHeight)
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
            prepareChars(cols, rows)
            prepareDepth(cols, rows)
            val chars = charBuffer
            val zBuffer = depthBuffer

            val R = 2.0
            val K2 = 5.0

            val thetaStep = 0.05 / d
            val phiStep = 0.05 / d
            val thetaCount = Math.ceil(Math.PI / thetaStep).toInt()
            val phiCount = Math.ceil(2 * Math.PI / phiStep).toInt()
            ensureTrig(thetaCount, phiCount)
            fillTrig(thetaCos, thetaSin, thetaCount, thetaStep)
            fillTrig(phiCos, phiSin, phiCount, phiStep)

            val cosA = Math.cos(angleA)
            val sinA = Math.sin(angleA)
            val cosB = Math.cos(angleB)
            val sinB = Math.sin(angleB)

            val halfCols = cols / 2.0
            val halfRows = rows / 2.0
            val shadesMax = shades.size - 1

            for (ti in 0 until thetaCount) {
                val sinTheta = thetaSin[ti]
                val cosTheta = thetaCos[ti]
                val rx = R * sinTheta

                for (pi in 0 until phiCount) {
                    val cosPhi = phiCos[pi]
                    val sinPhi = phiSin[pi]

                    val x = rx * cosPhi
                    val y = R * cosTheta
                    val z = rx * sinPhi

                    val y1 = y * cosA - z * sinA
                    val z1 = y * sinA + z * cosA
                    val x1 = x * cosB - z1 * sinB
                    val z2 = x * sinB + z1 * cosB

                    val ooz = 1.0 / (z2 + K2)
                    val xp = (halfCols + 25 * ooz * x1 * d).toInt()
                    val yp = (halfRows - 12 * ooz * y1 * d).toInt()

                    if (xp in 0 until cols && yp in 0 until rows) {
                        val idx = yp * cols + xp
                        if (ooz > zBuffer[idx]) {
                            zBuffer[idx] = ooz.toFloat()
                            // Освещение считаем только для видимых точек
                            val L = x * sinB + y * cosA * cosB + z * sinA * cosB
                            val luminance = (L + 1) / 2
                            val shadeIndex = (luminance * shadesMax).toInt().coerceIn(0, shadesMax)
                            chars[idx] = shades[shadeIndex]
                        }
                    }
                }
            }
            drawChars(canvas, paint, cols, rows, grid.cellWidth, grid.cellHeight)
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
            prepareChars(cols, rows)
            prepareDepth(cols, rows)
            val chars = charBuffer
            val zBuffer = depthBuffer

            val K2 = 5.0

            val cosA = Math.cos(angleA)
            val sinA = Math.sin(angleA)
            val cosB = Math.cos(angleB)
            val sinB = Math.sin(angleB)

            // Поворачиваем 8 вершин один раз, дальше интерполируем уже повёрнутые точки
            for (i in cubeVertices.indices) {
                val v = cubeVertices[i]
                val x = v.first
                val y = v.second
                val z = v.third

                val y1 = y * cosA - z * sinA
                val z1 = y * sinA + z * cosA
                cubeRotY[i] = y1
                cubeRotX[i] = x * cosB - z1 * sinB
                cubeRotZ[i] = x * sinB + z1 * cosB
            }

            val halfCols = cols / 2.0
            val halfRows = rows / 2.0
            val steps = (40 * d).toInt().coerceAtLeast(40)

            for (edge in cubeEdges) {
                val i1 = edge.first
                val i2 = edge.second
                val x1s = cubeRotX[i1]
                val y1s = cubeRotY[i1]
                val z2s = cubeRotZ[i1]
                val x1e = cubeRotX[i2]
                val y1e = cubeRotY[i2]
                val z2e = cubeRotZ[i2]
                val dx = x1e - x1s
                val dy = y1e - y1s
                val dz = z2e - z2s

                for (i in 0..steps) {
                    val t = i.toDouble() / steps
                    val x1 = x1s + dx * t
                    val y1 = y1s + dy * t
                    val z2 = z2s + dz * t

                    val ooz = 1.0 / (z2 + K2)
                    val xp = (halfCols + 25 * ooz * x1 * d).toInt()
                    val yp = (halfRows - 12 * ooz * y1 * d).toInt()

                    if (xp in 0 until cols && yp in 0 until rows) {
                        val idx = yp * cols + xp
                        if (ooz > zBuffer[idx]) {
                            zBuffer[idx] = ooz.toFloat()
                            chars[idx] = '#'
                        }
                    }
                }
            }
            drawChars(canvas, paint, cols, rows, grid.cellWidth, grid.cellHeight)
        } finally {
            paint.textSize = baseTextSize
        }
    }

    // --- 4. ДНК (плоская спираль, «течёт» вниз и перекручивается) ---
    @Suppress("UNUSED_PARAMETER")
    fun renderDna(canvas: Canvas, paint: Paint, angleA: Double, angleB: Double, density: Float) {
        val baseTextSize = paint.textSize
        val grid = prepareGrid(canvas, paint, density) ?: return
        try {
            val cols = grid.cols
            val rows = grid.rows
            val cellWidth = grid.cellWidth
            val cellHeight = grid.cellHeight
            prepareChars(cols, rows)
            val chars = charBuffer

            val w = canvas.width.toDouble()
            val h = canvas.height.toDouble()
            val scale = minOf(w, h)

            // Размеры в пикселях, чтобы фигура не зависела от плотности сетки
            val amplitude = scale * 0.34     // полуширина ленты (крупные витки)
            val wavelength = scale * 0.34    // длина одного витка вдоль оси
            val tilt = Math.toRadians(25.0)  // наклон оси спирали

            val axisX = -Math.sin(tilt)
            val axisY = Math.cos(tilt)
            val perpX = -axisY
            val perpY = axisX

            val centerX = w / 2
            val centerY = h / 2
            val halfLen = Math.hypot(w, h)

            val k = 2 * Math.PI / wavelength
            // Бегущая волна перекручивания: локально меняет шаг спирали,
            // из-за чего соседние участки ДНК закручиваются друг относительно друга
            val twistLength = scale * 0.85
            val kTwist = 2 * Math.PI / twistLength
            val wobble = 1.1
            val scroll = angleB * 2.0
            val twistTime = angleB * 1.6

            fun phaseAt(s: Double): Double =
                k * s - scroll + wobble * Math.sin(kTwist * s - twistTime)

            val strandStep = (minOf(cellWidth, cellHeight) * 0.5f).coerceAtLeast(1f).toDouble()
            val rungStep = (cellWidth * 1.5f).coerceAtLeast(1f).toDouble()
            val rungSpacing = wavelength / 12.0

            fun plot(x: Double, y: Double, ch: Char) {
                val xp = (x / cellWidth).toInt()
                val yp = (y / cellHeight).toInt()
                if (xp in 0 until cols && yp in 0 until rows) {
                    val idx = yp * cols + xp
                    // Нить важнее перекладины
                    if (chars[idx] == ' ' || ch == '#') chars[idx] = ch
                }
            }

            // Перекладины между нитями
            var s = -halfLen
            while (s < halfLen) {
                val u = amplitude * Math.cos(phaseAt(s))
                val steps = Math.max(1, Math.ceil(2 * Math.abs(u) / rungStep).toInt())
                for (j in 0..steps) {
                    val uu = -u + 2 * u * j / steps
                    plot(
                        centerX + s * axisX + uu * perpX,
                        centerY + s * axisY + uu * perpY,
                        ':'
                    )
                }
                s += rungSpacing
            }

            // Две нити спирали
            s = -halfLen
            while (s < halfLen) {
                val u = amplitude * Math.cos(phaseAt(s))
                plot(centerX + s * axisX + u * perpX, centerY + s * axisY + u * perpY, '#')
                plot(centerX + s * axisX - u * perpX, centerY + s * axisY - u * perpY, '#')
                s += strandStep
            }

            drawChars(canvas, paint, cols, rows, cellWidth, cellHeight)
        } finally {
            paint.textSize = baseTextSize
        }
    }

    // --- Вспомогательная функция для отрисовки ---
    private fun drawChars(
        canvas: Canvas, paint: Paint,
        cols: Int, rows: Int, cellWidth: Float, cellHeight: Float
    ) {
        val chars = charBuffer
        // Центрируем символ по вертикали внутри ячейки, чтобы положение фигуры
        // не зависело от плотности сетки.
        val baselineOffset = cellHeight * 0.8f

        for (y in 0 until rows) {
            val rowStart = y * cols
            val baseline = y * cellHeight + baselineOffset
            var x = 0
            while (x < cols) {
                if (chars[rowStart + x] == ' ') {
                    x++
                    continue
                }
                // Собираем непрерывный фрагмент строки и рисуем его одним вызовом:
                // так мы не создаём String на каждый символ и делаем меньше draw-вызовов
                var runLength = 0
                while (x + runLength < cols && chars[rowStart + x + runLength] != ' ') {
                    runLength++
                }
                if (runLength > runBuffer.size) runBuffer = CharArray(runLength)
                System.arraycopy(chars, rowStart + x, runBuffer, 0, runLength)
                canvas.drawText(runBuffer, 0, runLength, x * cellWidth, baseline, paint)
                x += runLength
            }
        }
    }
}
