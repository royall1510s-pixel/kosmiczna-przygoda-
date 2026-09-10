package com.kosmicznaprzygoda.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

class FlightHud {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
    }

    private val barBack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(25, 30, 42)
    }

    private val barFill = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        fuel: Float,
        thrust: Float,
        speed: Float,
        altitude: Float,
        temperature: Float,
        integrity: Float,
        verticalSpeed: Float,
        target: String
    ) {

        val left = 24f
        val top = 28f
        val panelW = min(520f, width * 0.72f)
        val rowH = 62f

        val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(185, 3, 8, 18)
        }

        canvas.drawRoundRect(
            RectF(
                left,
                top,
                left + panelW,
                top + rowH * 6f + 34f
            ),
            22f,
            22f,
            panel
        )

        drawBar(
            canvas,
            left + 18f,
            top + 18f,
            panelW - 36f,
            30f,
            "PALIWO",
            fuel,
            100f
        )

        drawBar(
            canvas,
            left + 18f,
            top + 18f + rowH,
            panelW - 36f,
            30f,
            "CIĄG",
            thrust,
            100f
        )

        drawValue(
            canvas,
            "PRĘDKOŚĆ",
            "${speed.toInt()} km/s",
            left + 18f,
            top + 18f + rowH * 2f
        )

        drawValue(
            canvas,
            "WYSOKOŚĆ",
            "${altitude.toInt()} km",
            left + 18f,
            top + 18f + rowH * 3f
        )

        drawBar(
            canvas,
            left + 18f,
            top + 18f + rowH * 4f,
            panelW - 36f,
            30f,
            "TEMP. OSŁONY",
            temperature,
            100f
        )

        drawBar(
            canvas,
            left + 18f,
            top + 18f + rowH * 5f,
            panelW - 36f,
            30f,
            "STAN RAKIETY",
            integrity,
            100f
        )

        val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        canvas.drawText(
            "CEL: $target",
            left,
            top + rowH * 6f + 82f,
            targetPaint
        )

        val warning = when {
            verticalSpeed < -25f -> "HAMUJ!"
            verticalSpeed < -10f -> "UWAGA: OPADANIE"
            else -> "TRAKTORIA OK"
        }

        val warningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (verticalSpeed < -10f)
                Color.rgb(255, 190, 70)
            else
                Color.rgb(120, 240, 170)

            textSize = 30f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        canvas.drawText(
            warning,
            left,
            top + rowH * 6f + 122f,
            warningPaint
        )

        when {
            fuel <= 15f ->
                drawAlert(canvas, width, height, "NISKI POZIOM PALIWA")

            temperature >= 90f ->
                drawAlert(canvas, width, height, "PRZEGRZANIE OSŁONY")

            integrity <= 20f ->
                drawAlert(canvas, width, height, "KRYTYCZNE USZKODZENIA")
        }
    }

    private fun drawBar(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        label: String,
        value: Float,
        maxValue: Float
    ) {

        val ratio = min(
            1f,
            max(0f, value / maxValue)
        )

        canvas.drawRoundRect(
            RectF(x, y, x + w, y + h),
            12f,
            12f,
            barBack
        )

        barFill.color = when {
            ratio <= 0.2f -> Color.rgb(235, 75, 75)
            ratio <= 0.4f -> Color.rgb(255, 190, 70)
            else -> Color.rgb(80, 210, 150)
        }

        canvas.drawRoundRect(
            RectF(
                x,
                y,
                x + w * ratio,
                y + h
            ),
            12f,
            12f,
            barFill
        )

        canvas.drawText(
            "$label  ${value.toInt()}%",
            x + 12f,
            y + h - 5f,
            smallPaint
        )
    }

    private fun drawValue(
        canvas: Canvas,
        label: String,
        value: String,
        x: Float,
        y: Float
    ) {

        canvas.drawText(
            label,
            x,
            y,
            smallPaint
        )

        canvas.drawText(
            value,
            x + 250f,
            y,
            textPaint
        )
    }

    private fun drawAlert(
        canvas: Canvas,
        width: Float,
        height: Float,
        message: String
    ) {

        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(
                220,
                120,
                15,
                20
            )
        }

        val boxW = min(
            width - 48f,
            700f
        )

        val boxH = 68f

        val left = (width - boxW) / 2f
        val top = height - boxH - 32f

        canvas.drawRoundRect(
            RectF(
                left,
                top,
                left + boxW,
                top + boxH
            ),
            20f,
            20f,
            p
        )

        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText(
            message,
            width / 2f,
            top + 44f,
            tp
        )
    }
}
