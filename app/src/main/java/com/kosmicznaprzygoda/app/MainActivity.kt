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
        getSharedPreferences("kosmiczna_przygoda", Context.MODE_PRIVATE)
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
        if (gameView.goBack()) return
        super.onBackPressed()
    }

    private class SpaceGameView(context: Context) : View(context) {

        private val prefs = context.getSharedPreferences(
            "kosmiczna_przygoda",
            Context.MODE_PRIVATE
        )

        private enum class Screen {
            MENU,
            ROCKETS,
            PLANETS,
            FLIGHT,
            LANDING,
            RESULT
        }

        private var screen = Screen.MENU

        private var selectedRocketId =
            prefs.getInt("selectedRocket", 1)

        private var credits =
            prefs.getInt("credits", 1250)

        private var research =
            prefs.getInt("research", 0)

        private var moonLanded =
            prefs.getBoolean("moonLanded", false)

        private var fuel = 100f
        private var thrust = 0f
        private var speed = 0f
        private var altitude = 0f
        private var temperature = 22f
        private var integrity = 100f
        private var verticalSpeed = 0f

        private var flightRunning = false
        private var launchPhase = 0f
        private var landingPhase = 0f
        private var engineHeld = false

        private var selectedPlanet = "Księżyc"

        private val handler =
            Handler(Looper.getMainLooper())

        private val paint =
            Paint(Paint.ANTI_ALIAS_FLAG)

        private val text =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface =
                    Typeface.create(
                        "sans-serif",
                        Typeface.BOLD
                    )
            }

        private val hud = FlightHud()

        private val stars = Array(120) {
            PointF(
                (Math.random() * 1000).toFloat(),
                (Math.random() * 1800).toFloat()
            )
        }

        private val tick = object : Runnable {

            override fun run() {

                if (!flightRunning) return

                updateFlight()
                invalidate()

                handler.postDelayed(
                    this,
                    50L
                )
            }
        }

        init {
            setBackgroundColor(
                Color.rgb(2, 5, 14)
            )

            isFocusable = true
        }

        private fun rocket(): Rocket =
            RocketDatabase.getRocket(
                selectedRocketId
            )

        private fun unlocked(id: Int): Boolean {

            return id == 1 ||
                prefs.getBoolean(
                    "rocket_$id",
                    false
                )
        }

        private fun save() {

            prefs.edit()
                .putInt(
                    "selectedRocket",
                    selectedRocketId
                )
                .putInt(
                    "credits",
                    credits
                )
                .putInt(
                    "research",
                    research
                )
                .putBoolean(
                    "moonLanded",
                    moonLanded
                )
                .apply()
        }

        override fun onDraw(canvas: Canvas) {

            super.onDraw(canvas)

            drawBackground(canvas)

            when (screen) {

                Screen.MENU ->
                    drawMenu(canvas)

                Screen.ROCKETS ->
                    drawRockets(canvas)

                Screen.PLANETS ->
                    drawPlanets(canvas)

                Screen.FLIGHT ->
                    drawFlight(canvas)

                Screen.LANDING ->
                    drawLanding(canvas)

                Screen.RESULT ->
                    drawResult(canvas)
            }
        }

        private fun drawBackground(canvas: Canvas) {

            val w = width.toFloat()
            val h = height.toFloat()

            paint.shader =
                LinearGradient(
                    0f,
                    0f,
                    0f,
                    h,
                    Color.rgb(3, 12, 28),
                    Color.rgb(1, 3, 10),
                    Shader.TileMode.CLAMP
                )

            canvas.drawRect(
                0f,
                0f,
                w,
                h,
                paint
            )

            paint.shader = null

            paint.color = Color.WHITE

            for (s in stars) {

                val x =
                    s.x / 1000f * w

                val y =
                    s.y / 1800f * h

                val r =
                    1f +
                        ((s.x.toInt() +
                            s.y.toInt()) % 3)

                canvas.drawCircle(
                    x,
                    y,
                    r,
                    paint
                )
            }
        }

        private fun drawTitle(
            canvas: Canvas,
            title: String,
            subtitle: String? = null
        ) {

            text.color =
                Color.rgb(80, 220, 255)

            text.textSize = 31f

            text.textAlign =
                Paint.Align.CENTER

            canvas.drawText(
                title,
                width / 2f,
                52f,
                text
            )

            if (subtitle != null) {

                text.color =
                    Color.rgb(
                        150,
                        180,
                        195
                    )

                text.textSize = 17f

                canvas.drawText(
                    subtitle,
                    width / 2f,
                    80f,
                    text
                )
            }
        }

        private fun drawButton(
            canvas: Canvas,
            rect: RectF,
            label: String,
            enabled: Boolean = true
        ) {

            paint.color =
                if (enabled)
                    Color.rgb(8, 38, 56)
                else
                    Color.rgb(25, 28, 34)

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
                if (enabled)
                    Color.rgb(35, 175, 215)
                else
                    Color.rgb(70, 75, 80)

            canvas.drawRoundRect(
                rect,
                18f,
                18f,
                paint
            )

            paint.style =
                Paint.Style.FILL

            text.color =
                if (enabled)
                    Color.WHITE
                else
                    Color.rgb(120, 125, 130)

            text.textSize = 20f

            text.textAlign =
                Paint.Align.CENTER

            canvas.drawText(
                label,
                rect.centerX(),
                rect.centerY() + 7f,
                text
            )
        }

        private fun drawMenu(canvas: Canvas) {

            drawTitle(
                canvas,
                "KOSMICZNA PRZYGODA",
                "ODKRYWAJ • BUDUJ • LĄDUJ • SIĘGAJ DALEJ"
            )

            text.color = Color.WHITE
            text.textSize = 19f
            text.textAlign =
                Paint.Align.CENTER

            canvas.drawText(
                "RAKIETA: ${rocket().name}",
                width / 2f,
                125f,
                text
            )

            canvas.drawText(
                "KREDYTY: $credits   •   BADANIA: $research",
                width / 2f,
                153f,
                text
            )

            val cx =
                width / 2f

            val cy = 265f

            drawRocket(
                canvas,
                cx,
                cy,
                0.72f,
                rocket().imageName
            )

            drawButton(
                canvas,
                RectF(
                    40f,
                    390f,
                    width - 40f,
                    450f
                ),
                "🚀  ROZPOCZNIJ MISJĘ"
            )

            drawButton(
                canvas,
                RectF(
                    40f,
                    465f,
                    width - 40f,
                    525f
                ),
                "🛰  WYBIERZ RAKIETĘ"
            )

            drawButton(
                canvas,
                RectF(
                    40f,
                    540f,
                    width - 40f,
                    600f
                ),
                "🌍  PLANETY"
            )

            drawButton(
                canvas,
                RectF(
                    40f,
                    615f,
                    width - 40f,
                    675f
                ),
                "🔬  BADANIA"
            )

            text.color =
                Color.rgb(
                    130,
                    165,
                    180
                )

            text.textSize = 15f

            canvas.drawText(
                if (moonLanded)
                    "🌙 BAZA KSIĘŻYCOWA ODBLOKOWANA • KOLONIŚCI DOSTĘPNI"
                else
                    "🌍 PIERWSZY CEL: KSIĘŻYC • NAJPIERW BEZPIECZNE LĄDOWANIE",
                width / 2f,
                height - 28f,
                text
            )
        }

        private fun drawRockets(canvas: Canvas) {

            drawTitle(
                canvas,
                "WYBIERZ RAKIETĘ",
                "20 jednostek programu kosmicznego"
            )

            val cols = 2

            val cardW =
                (width - 54f) / 2f

            val cardH = 205f
            val startY = 105f
            val gap = 14f

            for (i in RocketDatabase.rockets.indices) {

                val r =
                    RocketDatabase.rockets[i]

                val col =
                    i % cols

                val row =
                    i / cols

                val left =
                    18f +
                        col *
                        (cardW + gap)

                val top =
                    startY +
                        row *
                        (cardH + gap)

                val rect =
                    RectF(
                        left,
                        top,
                        left + cardW,
                        top + cardH
                    )

                paint.color =
                    if (r.id == selectedRocketId)
                        Color.argb(
                            100,
                            20,
                            130,
                            170
                        )
                    else
                        Color.argb(
                            85,
                            10,
                             
