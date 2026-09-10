package com.kosmicznaprzygoda.app

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.graphics.drawable.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import kotlin.math.*

class MainActivity : Activity() {

    private lateinit var gameView: SpaceGameView
    private val prefs by lazy {
        getSharedPreferences(
            "kosmiczna_przygoda",
            Context.MODE_PRIVATE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        gameView = SpaceGameView(this)
        setContentView(gameView)
    }

    override fun onBackPressed() {
        if (!gameView.goBack()) {
            super.onBackPressed()
        }
    }

    private inner class SpaceGameView(
        context: Context
    ) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG)

        private val handler =
            Handler(Looper.getMainLooper())

        private enum class Screen {
            MENU,
            ROCKETS,
            PLANETS,
            UPGRADE,
            RESEARCH,
            MISSIONS,
            FLIGHT,
            LANDING,
            RESULT
        }

        private var screen = Screen.MENU

        private var credits =
            prefs.getInt("credits", 1250)

        private var research =
            prefs.getInt("research", 0)

        private var selectedRocket =
            prefs.getInt("selectedRocket", 1)

        private var selectedPlanet =
            prefs.getString(
                "selectedPlanet",
                "Księżyc"
            ) ?: "Księżyc"

        private var moonLanded =
            prefs.getBoolean(
                "moonLanded",
                false
            )

        private var passengersUnlocked =
            prefs.getBoolean(
                "passengersUnlocked",
                false
            )

        // ==========================
        // PARAMETRY LOTU
        // ==========================

        private var fuel = 100f
        private var speed = 0f
        private var altitude = 0f
        private var thrust = 0f
        private var temperature = 22f
        private var integrity = 100f

        private var verticalSpeed = 0f

        // ==========================
        // SEKWENCJA STARTOWA
        // ==========================

        private var countdown = 3.0f
        private var ignition = false
        private var rocketReleased = false
        private var launchAnimation = 0f
        private var shakeTime = 0f
        private var smokeTime = 0f

        private var engineHeld = false
        private var flightActive = false

        // ==========================
        // LĄDOWANIE
        // ==========================

        private var landingActive = false
        private var landingAltitude = 1000f
        private var landingSpeed = 80f

        // ==========================
        // GWIAZDY
        // ==========================

        private data class Star(
            val x: Float,
            val y: Float,
            val radius: Float,
            val alpha: Int
        )

        private val stars =
            ArrayList<Star>()

        // ==========================
        // RAKIETY
        // ==========================

        private val rocketNames = listOf(
            "Explorer I",
            "Explorer II",
            "Falcon Junior",
            "Nova",
            "Nova X",
            "Titan",
            "Titan II",
            "Orion",
            "Orion Plus",
            "Phoenix",
            "Phoenix X",
            "Atlas",
            "Atlas Heavy",
            "Voyager",
            "Voyager II",
            "Star Ranger",
            "Star Ranger X",
            "Ares",
            "Ares Heavy",
            "Kosmiczna Bestia"
        )

        private val rocketImages = listOf(
            "rocket_lvl1",
            "rocket_lvl2",
            "rocket_lvl3",
            "rocket_lvl4",
            "rocket_lvl5",
            "rocket_lvl6",
            "rocket_lvl1",
            "rocket_lvl2",
            "rocket_lvl3",
            "rocket_lvl4",
            "rocket_lvl5",
            "rocket_lvl6",
            "rocket_lvl1",
            "rocket_lvl2",
            "rocket_lvl3",
            "rocket_lvl4",
            "rocket_lvl5",
            "rocket_lvl6",
            "rocket_lvl5",
            "rocket_lvl6"
        )

        private val rocketPrices = listOf(
            0,
            500,
            900,
            1400,
            2200,
            3200,
            4500,
            6000,
            7800,
            10000,
            12500,
            15000,
            18000,
            22000,
            27000,
            33000,
            40000,
            48000,
            58000,
            70000
        )

        private val rocketFuel = listOf(
            100,
            110,
            120,
            135,
            150,
            165,
            180,
            195,
            210,
            230,
            250,
            275,
            300,
            325,
            350,
            380,
            410,
            440,
            470,
            500
        )

        private val rocketThrust = listOf(
            45f,
            50f,
            55f,
            60f,
            66f,
            72f,
            78f,
            84f,
            90f,
            97f,
            104f,
            112f,
            120f,
            128f,
            137f,
            146f,
            156f,
            166f,
            177f,
            190f
        )

        // ==========================
        // PLANETY
        // ==========================

        private val planets = listOf(
            "Księżyc",
            "Mars",
            "Wenus",
            "Jowisz",
            "Saturn",
            "Uran",
            "Neptun",
            "Aurelia",
            "Ignara",
            "Cryonis",
            "Nectaris",
            "Obsidia"
        )

        private val planetImages = listOf(
            "planeta_ksiezyc",
            "planeta_mars",
            "planeta_wenus",
            "planeta_jowisz",
            "planeta_saturn",
            "planeta_uran",
            "planeta_neptun",
            "planeta_aurelia",
            "planeta_ignara",
            "planeta_cryonis",
            "planeta_nectaris",
            "planeta_obsidia"
        )

        // ==========================
        // INICJALIZACJA
        // ==========================

        init {

            textPaint.typeface =
                Typeface.create(
                    "sans-serif",
                    Typeface.BOLD
                )

            for (i in 0 until 180) {

                stars.add(
                    Star(
                        Math.random()
                            .toFloat(),
                        Math.random()
                            .toFloat(),
                        0.5f +
                            Math.random()
                                .toFloat() * 2f,
                        100 +
                            (Math.random() * 155)
                                .toInt()
                    )
                )
            }
        }

        // ==========================
        // RYSOWANIE
        // ==========================

        override fun onDraw(
            canvas: Canvas
        ) {

            super.onDraw(canvas)

            drawSpaceBackground(canvas)

            when (screen) {

                Screen.MENU ->
                    drawMenu(canvas)

                Screen.ROCKETS ->
                    drawRockets(canvas)

                Screen.PLANETS ->
                    drawPlanets(canvas)

                Screen.UPGRADE ->
                    drawUpgrade(canvas)

                Screen.RESEARCH ->
                    drawResearch(canvas)

                Screen.MISSIONS ->
                    drawMissions(canvas)

                Screen.FLIGHT ->
                    drawFlight(canvas)

                Screen.LANDING ->
                    drawLanding(canvas)

                Screen.RESULT ->
                    drawResult(canvas)
            }
        }

        // ==========================
        // TŁO KOSMICZNE
        // ==========================

        private fun drawSpaceBackground(
            canvas: Canvas
        ) {

            val gradient =
                LinearGradient(
                    0f,
                    0f,
                    0f,
                    height.toFloat(),
                    Color.rgb(4, 13, 30),
                    Color.rgb(1, 2, 8),
                    Shader.TileMode.CLAMP
                )

            paint.shader = gradient

            canvas.drawRect(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                paint
            )

            paint.shader = null

            for (star in stars) {

                paint.color =
                    Color.argb(
                        star.alpha,
                        255,
                        255,
                        255
                    )

                canvas.drawCircle(
                    star.x * width,
                    star.y * height,
                    star.radius,
                    paint
                )
            }
        }

        // ==========================
        // TYTUŁ
        // ==========================

        private fun drawTitle(
            canvas: Canvas,
            title: String,
            subtitle: String = ""
        ) {

            textPaint.textAlign =
                Paint.Align.CENTER

            textPaint.textSize = 28f

            textPaint.color =
                Color.rgb(
                    80,
                    220,
                    255
                )

            canvas.drawText(
                title,
                width / 2f,
                48f,
                textPaint
            )

            if (subtitle.isNotEmpty()) {

                textPaint.textSize = 14f

                textPaint.color =
                    Color.rgb(
                        170,
                        190,
                        205
                    )

                canvas.drawText(
                    subtitle,
                    width / 2f,
                    72f,
                    textPaint
                )
            }
        }

        // ==========================
        // PRZYCISK
        // ==========================

        private fun drawButton(
            canvas: Canvas,
            rect: RectF,
            label: String,
            enabled: Boolean = true
        ) {

            paint.color =
                if (enabled) {
                    Color.rgb(
                        7,
                        40,
                        60
                    )
                } else {
                    Color.rgb(
                        35,
                        37,
                        42
                    )
                }

            canvas.drawRoundRect(
                rect,
                18f,
                18f,
                paint
            )

            paint.style =
                Paint.Style.STROKE

            paint.strokeWidth = 2f

            paint.color =
                if (enabled) {
                    Color.rgb(
                        50,
                        190,
                        225
                    )
                } else {
                    Color.rgb(
                        75,
                        80,
                        85
                    )
                }

            canvas.drawRoundRect(
                rect,
                18f,
                18f,
                paint
            )

            paint.style =
                Paint.Style.FILL

            textPaint.textAlign =
                Paint.Align.CENTER

            textPaint.textSize = 18f

            textPaint.color =
                if (enabled)
                    Color.WHITE
                else
                    Color.rgb(
                        130,
                        135,
                        140
                    )

            canvas.drawText(
                label,
                rect.centerX(),
                rect.centerY() + 6f,
                textPaint
            )
        }

        // ==========================
        // MENU
        // ==========================

        private fun drawMenu(
            canvas: Canvas
        ) {

            drawTitle(
                canvas,
                "KOSMICZNA PRZYGODA",
                "ODKRYWAJ • BUDUJ • KOLONIZUJ"
            )

            textPaint.textAlign =
                Paint.Align.CENTER

            textPaint.textSize = 16f
            textPaint.color = Color.WHITE

            canvas.drawText(
                "🚀 ${rocketNames[selectedRocket - 1]}",
                width / 2f,
                112f,
                textPaint
            )

            textPaint.textSize = 14f
            textPaint.color =
                Color.rgb(
                    190,
                    210,
                    220
                )

            canvas.drawText(
                "💰 $credits    🔬 $research",
                width / 2f,
                138f,
                textPaint
            )

            drawRocket(
                canvas,
                width / 2f,
                250f,
                0.72f,
                rocketImages[
                    selectedRocket - 1
                ]
            )

            drawButton(
                canvas,
                RectF(
                    30f,
                    380f,
                    width - 30f,
                    442f
                ),
                "🚀 ROZPOCZNIJ MISJĘ"
            )

            drawButton(
                canvas,
                RectF(
                    30f,
                    455f,
                    width - 30f,
                    517f
                ),
                "🚀 WYBIERZ RAKIETĘ"
            )

            drawButton(
                canvas,
                RectF(
                    30f,
                    530f,
                    width - 30f,
                    592f
                ),
                "🌍 PLANETY"
            )

            drawButton(
                canvas,
                RectF(
                    30f,
                    605f,
                    width - 30f,
                    667f
                ),
                "🔬 BADANIA"
            )

            drawButton(
                canvas,
                RectF(
                    30f,
                    680f,
                    width - 30f,
                    742f
                ),
                "🎯 MISJE"
            )

            textPaint.textSize = 12f
            textPaint.color =
                Color.rgb(
                    120,
                    155,
                    175
                )

            canvas.drawText(
                if (moonLanded)
                    "🌙 KSIĘŻYC ZDOBYTY"
                else
                    "🌍 PIERWSZY CEL: KSIĘŻYC",
                width / 2f,
                height - 18f,
                textPaint
            )
        }

        // ==========================
        // RAKIETA
        // ==========================

        private fun drawRocket(
            canvas: Canvas,
            x: Float,
            y: Float,
            scale: Float,
            imageName: String
        ) {

            val resourceId =
                resources.getIdentifier(
                    imageName,
                    "drawable",
                    context.packageName
                )

            if (resourceId != 0) {

                val bitmap =
                    BitmapFactory.decodeResource(
                        resources,
                        resourceId
                    )

                if (bitmap != null) {

                    val targetHeight =
                        210f * scale

                    val ratio =
                        targetHeight /
                            bitmap.height

                    val targetWidth =
                        bitmap.width *
                            ratio

                    val rect =
                        RectF(
                            x -
                                targetWidth / 2f,
                            y -
                                targetHeight / 2f,
                            x +
                                targetWidth / 2f,
                            y +
                                targetHeight / 2f
                        )

                    canvas.drawBitmap(
                        bitmap,
                        null,
                        rect,
                        paint
                    )

                    return
                }
            }

            // Awaryjne rysowanie rakiety

            paint.color =
                Color.rgb(
                    205,
                    210,
                    215
                )

            canvas.drawRoundRect(
                RectF(
                    x - 25f * scale,
                    y - 70f * scale,
                    x + 25f * scale,
                    y + 65f * scale
                ),
                20f * scale,
                20f * scale,
                paint
            )

            val nose =
                Path()

            nose.moveTo(
                x - 25f * scale,
                y - 65f * scale
            )

            nose.lineTo(
                x,
                y - 110f * scale
            )

            nose.lineTo(
                x + 25f * scale,
                y - 65f * scale
            )

            nose.close()

            paint.color =
                Color.rgb(
                    225,
                    55,
                    55
                )

            canvas.drawPath(
                nose,
                paint
            )

            paint.color =
                Color.rgb(
                    55,
                    145,
                    220
                )

            canvas.drawCircle(
                x,
                y - 18f * scale,
                12f * scale,
                paint
            )
        }
