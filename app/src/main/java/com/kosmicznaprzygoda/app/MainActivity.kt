package com.kosmicznaprzygoda.app

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {

    private lateinit var gameView: SpaceGameView

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
        if (!gameView.goBack()) super.onBackPressed()
    }

    private class SpaceGameView(context: Context) : View(context) {

        private enum class Screen {
            MENU, ROCKETS, PLANETS, FLIGHT, LANDING, RESULT
        }

        private var screen = Screen.MENU

        private val prefs =
            context.getSharedPreferences(
                "kosmiczna_przygoda",
                Context.MODE_PRIVATE
            )

        private var selectedRocketId =
            prefs.getInt("selectedRocket", 1)

        private var credits =
            prefs.getInt("credits", 1250)

        private var research =
            prefs.getInt("research", 0)

        private var moonLanded =
            prefs.getBoolean("moonLanded", false)

        private var selectedPlanet = "Księżyc"

        // PARAMETRY LOTU
        private var fuel = 100f
        private var thrust = 0f
        private var speed = 0f
        private var altitude = 0f
        private var temperature = 22f
        private var integrity = 100f
        private var verticalSpeed = 0f

        // START
        private var launchCountdown = 0f
        private var launchStarted = false
        private var ignition = false
        private var launchPhase = 0f
        private var rocketShake = 0f
        private var smokeTime = 0f

        private var engineHeld = false
        private var flightRunning = false

        // LĄDOWANIE
        private var landingPhase = 0f

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

        private val stars =
            Array(140) {
                PointF(
                    (Math.random() * 1000).toFloat(),
                    (Math.random() * 2000).toFloat()
                )
            }

        private val loop = object : Runnable {
            override fun run() {
                if (!flightRunning) return

                updateGame()
                invalidate()

                handler.postDelayed(this, 50L)
            }
        }

        init {
            setBackgroundColor(Color.rgb(2, 5, 14))
            isFocusable = true
        }

        private fun rocket(): Rocket =
            RocketDatabase.getRocket(selectedRocketId)

        private fun isRocketUnlocked(id: Int): Boolean {
            return id == 1 ||
                prefs.getBoolean("rocket_$id", false)
        }

        private fun saveGame() {
            prefs.edit()
                .putInt("selectedRocket", selectedRocketId)
                .putInt("credits", credits)
                .putInt("research", research)
                .putBoolean("moonLanded", moonLanded)
                .apply()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            drawBackground(canvas)

            when (screen) {
                Screen.MENU -> drawMenu(canvas)
                Screen.ROCKETS -> drawRockets(canvas)
                Screen.PLANETS -> drawPlanets(canvas)
                Screen.FLIGHT -> drawFlight(canvas)
                Screen.LANDING -> drawLanding(canvas)
                Screen.RESULT -> drawResult(canvas)
            }
        }

        // =========================================================
        // TŁO
        // =========================================================

        private fun drawBackground(canvas: Canvas) {

            paint.shader = LinearGradient(
                0f,
                0f,
                0f,
                height.toFloat(),
                Color.rgb(3, 13, 30),
                Color.rgb(1, 3, 10),
                Shader.TileMode.CLAMP
            )

            canvas.drawRect(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                paint
            )

            paint.shader = null

            paint.color = Color.WHITE

            for (star in stars) {

                val x =
                    star.x / 1000f * width

                val y =
                    star.y / 2000f * height

                canvas.drawCircle(
                    x,
                    y,
                    1f + ((star.x.toInt() + star.y.toInt()) % 2),
                    paint
                )
            }
        }

        private fun drawTitle(
            canvas: Canvas,
            title: String,
            subtitle: String? = null
        ) {

            text.textAlign = Paint.Align.CENTER
            text.textSize = 30f
            text.color = Color.rgb(80, 220, 255)

            canvas.drawText(
                title,
                width / 2f,
                50f,
                text
            )

            if (subtitle != null) {

                text.textSize = 15f
                text.color =
                    Color.rgb(155, 185, 200)

                canvas.drawText(
                    subtitle,
                    width / 2f,
                    76f,
                    text
                )
            }
        }

        private fun button(
            canvas: Canvas,
            rect: RectF,
            label: String,
            enabled: Boolean = true
        ) {

            paint.color =
                if (enabled)
                    Color.rgb(7, 38, 57)
                else
                    Color.rgb(28, 30, 34)

            canvas.drawRoundRect(
                rect,
                18f,
                18f,
                paint
            )

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f

            paint.color =
                if (enabled)
                    Color.rgb(45, 180, 220)
                else
                    Color.rgb(70, 75, 80)

            canvas.drawRoundRect(
                rect,
                18f,
                18f,
                paint
            )

            paint.style = Paint.Style.FILL

            text.textAlign = Paint.Align.CENTER
            text.textSize = 19f
            text.color =
                if (enabled)
                    Color.WHITE
                else
                    Color.rgb(125, 130, 135)

            canvas.drawText(
                label,
                rect.centerX(),
                rect.centerY() + 7f,
                text
            )
        }

        // =========================================================
        // MENU
        // =========================================================

        private fun drawMenu(canvas: Canvas) {

            drawTitle(
                canvas,
                "KOSMICZNA PRZYGODA",
                "ODKRYWAJ • BUDUJ • LĄDUJ"
            )

            text.textAlign = Paint.Align.CENTER
            text.textSize = 18f
            text.color = Color.WHITE

            canvas.drawText(
                "🚀 ${rocket().name}",
                width / 2f,
                118f,
                text
            )

            text.textSize = 15f

            canvas.drawText(
                "💰 $credits   •   🔬 $research",
                width / 2f,
                145f,
                text
            )

            drawRocket(
                canvas,
                width / 2f,
                260f,
                0.72f,
                rocket().imageName
            )

            button(
                canvas,
                RectF(
                    35f,
                    390f,
                    width - 35f,
                    450f
                ),
                "🚀 ROZPOCZNIJ MISJĘ"
            )

            button(
                canvas,
                RectF(
                    35f,
                    465f,
                    width - 35f,
                    525f
                ),
                "🛰 WYBIERZ RAKIETĘ"
            )

            button(
                canvas,
                RectF(
                    35f,
                    540f,
                    width - 35f,
                    600f
                ),
                "🌍 PLANETY"
            )

            button(
                canvas,
                RectF(
                    35f,
                    615f,
                    width - 35f,
                    675f
                ),
                "🔬 BADANIA"
            )

            text.textSize = 13f
            text.color =
                Color.rgb(125, 165, 180)

            canvas.drawText(
                if (moonLanded)
                    "🌙 KSIĘŻYC ZDOBYTY • KOLONIŚCI ODBLOKOWANI"
                else
                    "🌍 PIERWSZY CEL: KSIĘŻYC",
                width / 2f,
                height - 25f,
                text
            )
        }

        // =========================================================
        // RAKIETY
        // =========================================================

        private fun drawRockets(canvas: Canvas) {

            drawTitle(
                canvas,
                "WYBIERZ RAKIETĘ",
                "20 rakiet programu kosmicznego"
            )

            val cardW =
                (width - 54f) / 2f

            val cardH = 190f
            val gap = 14f

            for (i in RocketDatabase.rockets.indices) {

                val r =
                    RocketDatabase.rockets[i]

                val col = i % 2
                val row = i / 2

                val left =
                    18f +
                        col * (cardW + gap)

                val top =
                    95f +
                        row * (cardH + gap)

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
                            110,
                            15,
                            130,
                            175
                        )
                    else
                        Color.argb(
                            80,
                            10,
                            30,
                            45
                        )

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
                    if (r.id == selectedRocketId)
                        Color.rgb(70, 225, 255)
                    else
                        Color.rgb(30, 90, 115)

                canvas.drawRoundRect(
                    rect,
                    18f,
                    18f,
                    paint
                )

                paint.style =
                    Paint.Style.FILL

                drawRocket(
                    canvas,
                    rect.centerX(),
                    top + 65f,
                    0.32f,
                    r.imageName
                )

                text.textAlign =
                    Paint.Align.CENTER

                text.textSize = 15f
                text.color = Color.WHITE

                canvas.drawText(
                    r.name,
                    rect.centerX(),
                    top + 112f,
                    text
                )

                text.textSize = 11f
                text.color =
                    Color.rgb(170, 195, 205)

                canvas.drawText(
                    "Paliwo ${r.fuelCapacity.toInt()} • Udźwig ${r.cargo}",
                    rect.centerX(),
                    top + 132f,
                    text
                )

                val unlocked =
                    isRocketUnlocked(r.id)

                val state =
                    when {
                        r.id == selectedRocketId ->
                            "WYBRANA"

                        unlocked ->
                            "WYBIERZ"

                        research < r.researchRequired ->
                            "🔒 BADANIA ${r.researchRequired}"

                        credits < r.price ->
                            "💰 ${r.price}"

                        else ->
                            "KUP ${r.price}"
                    }

                text.textSize = 12f

                text.color =
                    if (unlocked)
                        Color.rgb(100, 235, 175)
                    else
                        Color.rgb(255, 190, 80)

                canvas.drawText(
                    state,
                    rect.centerX(),
                    top + 163f,
                    text
                )
            }
        }

        // =========================================================
        // PLANETY
        // =========================================================

        private fun drawPlanets(canvas: Canvas) {

            drawTitle(
                canvas,
                "PLANETY",
                "Najpierw musisz zdobyć Księżyc"
            )

            val planets =
                listOf(
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

            for (i in planets.indices) {

                val available =
                    i == 0 || moonLanded

                val top =
                    95f + i * 78f

                button(
                    canvas,
                    RectF(
                        25f,
                        top,
                        width - 25f,
                        top + 60f
                    ),
                    if (available)
                        "🌐 ${planets[i]}"
                    else
                        "🔒 ${planets[i]}",
                    available
                )
            }
        }

        // =========================================================
        // START Z ZIEMI
        // =========================================================

        private fun drawFlight(canvas: Canvas) {

            drawEarthAndLaunchTower(canvas)

            /*
             * Rakieta stoi na czubku Ziemi.
             * Podczas odliczania jest jeszcze przy wieży.
             */

            val baseY =
                if (!launchStarted) {
                    height * 0.70f
                } else {
                    height * 0.70f -
                        launchPhase *
                        height *
                        0.58f
                }

            var rocketX =
                width / 2f

            if (ignition && !launchStarted) {

                rocketX +=
                    sin(smokeTime * 8f) * rocketShake
            }

            drawRocket(
                canvas,
                rocketX,
                baseY,
                0.62f,
                rocket().imageName
            )

            // Płomień
            if (ignition) {

                val flame =
                    45f +
                        thrust * 0.9f

                paint.color =
                    Color.argb(
                        220,
                        255,
                        145,
                        30
                    )

                canvas.drawOval(
                    RectF(
                        rocketX - 23f,
                        baseY + 42f,
                        rocketX + 23f,
                        baseY + 42f + flame
                    ),
                    paint
                )

                paint.color =
                    Color.argb(
                        220,
                        255,
                        235,
                        140
                    )

                canvas.drawOval(
                    RectF(
                        rocketX - 11f,
                        baseY + 43f,
                        rocketX + 11f,
                        baseY + 43f + flame * 0.62f
                    ),
                    paint
                )

                drawSmoke(
                    canvas,
                    rocketX,
                    baseY + 80f
                )
            }

            // Odliczanie
            if (!launchStarted) {

                text.textAlign =
                    Paint.Align.CENTER

                text.textSize = 58f
                text.color = Color.WHITE

                val countdown =
                    if (launchCountdown > 0f)
                        ceil(launchCountdown).toInt()
                    else
                        3

                canvas.drawText(
                    if (countdown > 0)
                        countdown.toString()
                    else
                        "START!",
                    width / 2f,
                    145f,
                    text
                )

                text.textSize = 17f
                text.color =
                    Color.rgb(
                        180,
                        205,
                        215
                    )

                canvas.drawText(
                    when {
                        launchCountdown > 2f ->
                            "SYSTEMY STARTOWE AKTYWNE"

                        launchCountdown > 1f ->
                            "ZAPŁON SILNIKÓW"

                        launchCountdown > 0f ->
                            "ODLICZANIE KOŃCOWE"

                        else ->
                            "START!"
                    },
                    width / 2f,
                    180f,
                    text
                )
            }

            hud.draw(
                canvas = canvas,
                width = width.toFloat(),
                height = height.toFloat(),
                fuel = fuel,
                thrust = thrust,
                speed = speed,
                altitude = altitude,
                temperature = temperature,
                integrity = integrity,
                verticalSpeed = verticalSpeed,
                target = selectedPlanet
            )

            // Przycisk silnika
            button(
                canvas,
                RectF(
                    20f,
                    height - 92f,
                    width / 2f - 10f,
                    height - 25f
                ),
                if (ignition)
                    "🔥 SILNIK"
                else
                    "🔥 ZAPŁON"
            )

            // Lądowanie
            button(
                canvas,
                RectF(
                    width / 2f + 10f,
                    height - 92f,
                    width - 20f,
                    height - 25f
                ),
                "🛬 LĄDOWANIE"
            )
        }

        private fun drawEarthAndLaunchTower(
            canvas: Canvas
        ) {

            val cx =
                width / 2f

            val earthY =
                height * 1.02f

            val radius =
                width * 0.74f

            // Półkole Ziemi
            paint.color =
                Color.rgb(
                    18,
                    88,
                    155
                )

            canvas.drawCircle(
                cx,
                earthY,
                radius,
                paint
            )

            paint.color =
                Color.rgb(
                    35,
                    125,
                    185
                )

            canvas.drawCircle(
                cx,
                earthY,
                radius - 12f,
                paint
            )

            // Kontynenty — dekoracyjne
            paint.color =
                Color.rgb(
                    60,
                    150,
                    95
                )

            canvas.drawOval(
                RectF(
                    cx - 160f,
                    earthY - 210f,
                    cx - 30f,
                    earthY - 110f
                ),
                paint
            )

            canvas.drawOval(
                RectF(
                    cx + 30f,
                    earthY - 240f,
                    cx + 170f,
                    earthY - 130f
                ),
                paint
            )

            // Wieża
            paint.color =
                Color.rgb(
                    130,
                    150,
                    160
                )

            paint.strokeWidth = 8f

            val towerTop =
                height * 0.48f

            val towerBottom =
                height * 0.78f

            canvas.drawLine(
                cx - 70f,
                towerTop,
                cx - 70f,
                towerBottom,
                paint
            )

            canvas.drawLine(
                cx + 70f,
                towerTop,
                cx + 70f,
                towerBottom,
                paint
            )

            // Poprzeczki
            for (i in 0..4) {

                val y =
                    towerTop +
                        i *
                        (
                            (towerBottom - towerTop) /
                                4f
                        )

                canvas.drawLine(
                    cx - 70f,
                    y,
                    cx + 70f,
                    y,
                    paint
                )
            }

            // Ramię mocujące rakietę
            paint.strokeWidth = 10f

            canvas.drawLine(
                cx - 70f,
                height * 0.62f,
                cx - 28f,
                height * 0.62f,
                paint
            )

            canvas.drawLine(
                cx + 70f,
                height * 0.62f,
                cx + 28f,
                height * 0.62f,
                paint
            )

            // Platforma
            paint.color =
                Color.rgb(
                    185,
                    195,
                    205
                )

            canvas.drawRect(
                cx - 90f,
                towerBottom - 8f,
                cx + 90f,
                towerBottom + 5f,
                paint
            )
        }

        private fun drawSmoke(
            canvas: Canvas,
            x: Float,
            y: Float
        ) {

            val t = smokeTime

            paint.color =
                Color.argb(
                    110,
                    210,
                    220,
                    225
                )

            for (i in 0 until 8) {

                val sx =
                    x +
                        sin(
                            t * 2f +
                                i
                        ) *
                        (25f + i * 3f)

                val sy =
                    y +
                        i * 17f

                val r =
                    12f +
                        i * 2.5f

                canvas.drawCircle(
                    sx,
                    sy,
                    r,
                    paint
                )
            }
        }

        // =========================================================
        // LOT
        // =========================================================

        private fun updateGame() {

            val r =
                rocket()

            smokeTime += 0.05f

            // -------------------------
            // ODLICZANIE
            // -------------------------

            if (!launchStarted) {

                launchCountdown -= 0.05f

                if (launchCountdown <= 2.2f) {
                    ignition = true
                }

                if (ignition) {

                    thrust +=
                        (100f - thrust) *
                            0.08f

                    rocketShake =
                        3f

                    fuel -=
                        0.08f
                }

                if (launchCountdown <= 0f) {

                    launchStarted = true

                    launchPhase = 0f

                    Toast.makeText(
                        context,
                        "🚀 START!",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                return
            }

            // -------------------------
            // WŁAŚCIWY START
            // -------------------------

            launchPhase +=
                0.018f *
                    r.thrust

            launchPhase =
                launchPhase.coerceIn(
                    0f,
                    1f
                )

            if (engineHeld || ignition) {

                thrust +=
                    (100f - thrust) *
                        0.12f

                speed +=
                    r.thrust *
                        35f *
                        0.05f

                altitude +=
                    speed *
                        0.05f *
                        0.035f

                fuel -=
                    (
                        0.15f +
                            r.thrust *
                            0.06f
                    )

                temperature +=
                    (
                        0.25f +
                            r.thrust *
                            0.08f
                    )

            } else {

                thrust *= 0.97f

                speed *= 0.997f

                altitude +=
                    speed *
                        0.05f *
                        0.012f

                temperature -=
                    0.08f
            }

            speed =
                speed.coerceIn(
                    0f,
                    r.maxSpeed
                )

            fuel =
                fuel.coerceIn(
                    0f,
                    100f
                )

            altitude =
                altitude.coerceIn(
                    0f,
                    r.maxAltitude
                )

            temperature =
                temperature.coerceIn(
                    20f,
                    120f
                )

            integrity =
                integrity.coerceIn(
                    0f,
                    100f
                )

            verticalSpeed =
                if (engineHeld)
                    speed * 0.08f
                else
                    -speed * 0.01f

            // Awaria
            if (
                fuel <= 0f ||
                temperature >= 118f ||
                integrity <= 0f
            ) {

                flightRunning = false
                screen = Screen.RESULT

                Toast.makeText(
                    context,
                    "⚠️ AWARIA MISJI",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // =========================================================
        // LĄDOWANIE
        // =========================================================

        private fun drawLanding(
            canvas: Canvas
        ) {

            drawMoonSurface(canvas)

            val r =
                rocket()

            val x =
                width / 2f

            val y =
                height * 0.22f +
                    landingPhase *
                    height *
                    0.48f

            drawRocket(
                canvas,
                x,
                y,
                0.60f,
                r.imageName
            )

            text.textAlign =
                Paint.Align.CENTER

            text.textSize = 24f

            text.color =
                when {
                    altitude > 300f ||
                        speed > 50f ->
                        Color.rgb(
                            255,
                            75,
                            75
                        )

                    altitude > 100f ||
                        speed > 25f ->
                        Color.rgb(
                            255,
                            200,
                            60
                        )

                    else ->
                        Color.rgb(
                            90,
                            240,
                            170
                        )
                }

            canvas.drawText(
                when {
                    altitude > 300f ->
                        "⚠️ HAMUJ"

                    altitude > 100f ->
                        "⚠️ KOREKTA TRAJEKTORII"

                    else ->
                        "🟢 TRAJEKTORIA PRAWIDŁOWA"
                },
                width / 2f,
                105f,
                text
            )

            text.textSize = 17f
            text.color = Color.WHITE

            canvas.drawText(
                "Wysokość: ${altitude.toInt()} m",
                width / 2f,
                height - 180f,
                text
            )

            canvas.drawText(
                "Prędkość opadania: ${abs(verticalSpeed).toInt()} m/s",
                width / 2f,
                height - 150f,
                text
            )

            button(
                canvas,
                RectF(
                    20f,
                    height - 125f,
                    width / 2f - 10f,
                    height - 60f
                ),
                "▲ CIĄG"
            )

            button(
                canvas,
                RectF(
                    width / 2f + 10f,
                    height - 125f,
                    width - 20f,
                    height - 60f
                ),
                "▼ HAMUJ"
            )

            button(
                canvas,
                RectF(
                    20f,
                    height - 52f,
                    width - 20f,
                    height - 12f
                ),
                "🛬 WYKONAJ LĄDOWANIE"
            )
        }

        private fun drawMoonSurface(
            canvas: Canvas
        ) {

            paint.color =
                Color.rgb(
                    105,
                    110,
                    120
                )

            canvas.drawCircle(
                width / 2f,
                height + 130f,
                width * 0.95f,
                paint
            )

            paint.color =
                Color.rgb(
                    78,
                    82,
                    92
                )

            for (i in 0 until 14) {

                val x =
                    (i * 93f) %
                        width

                val y =
                    height -
                        100f -
                        (i % 5) * 27f

                canvas.drawCircle(
                    x,
                    y,
                    10f + (i % 4) * 5f,
                    paint
                )
            }

            text.textAlign =
                Paint.Align.CENTER

            text.textSize = 27f
            text.color = Color.WHITE

            canvas.drawText(
                "KSIĘŻYC",
                width / 2f,
                50f,
                text
            )
        }

        private fun performLanding(
            power: Float
        ) {

            verticalSpeed += power

            altitude +=
                verticalSpeed *
                    0.08f

            speed =
                abs(verticalSpeed)

            fuel -= 0.25f

            temperature +=
                abs(power) * 0.03f

            altitude =
                max(
                    0f,
                    altitude
                )

            fuel =
                fuel.coerceIn(
                    0f,
                    100f
                )

            landingPhase =
                (
                    1f -
                        altitude / 1000f
                    ).coerceIn(
                        0f,
                        1f
                    )

            if (altitude <= 0f) {

                val landingSpeed =
                    abs(verticalSpeed)

                flightRunning = false

                if (landingSpeed <= 18f) {

                    moonLanded = true

                    credits += 500
                    research += 100

                    saveGame()

                    screen =
                        Screen.RESULT

                    Toast.makeText(
                        context,
                        "🌙 LĄDOWANIE UDANE!",
                        Toast.LENGTH_LONG
                    ).show()

                } else {

                    screen =
                        Screen.RESULT

                    Toast.makeText(
                        context,
                        "💥 Lądowanie zbyt szybkie!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            invalidate()
        }

        // =========================================================
        // WYNIK
        // =========================================================

        private fun drawResult(
            canvas: Canvas
        ) {

            drawTitle(
                canvas,
                if (moonLanded)
                    "🌙 LĄDOWANIE UDANE"
                else
                    "MISJA ZAKOŃCZONA",
                "KOSMICZNA PRZYGODA"
            )

            text.textAlign =
                Paint.Align.CENTER

            text.textSize = 20f
            text.color = Color.WHITE

            val message =
                if (moonLanded)
                    listOf(
                        "Księżyc został osiągnięty!",
                        "",
                        "+500 kredytów",
                        "+100 punktów badań",
                        "",
                        "Transport kolonistów odblokowany."
                    )
                else
                    listOf(
                        "Misja nie została ukończona.",
                        "",
                        "Spróbuj ponownie."
                    )

            var y = 180f

            for (line in message) {

                canvas.drawText(
                    line,
                    width / 2f,
                    y,
                    text
                )

                y += 34f
            }

            button(
                canvas,
                RectF(
                    25f,
                    height - 145f,
                    width - 25f,
                    height - 85f
                ),
                "🚀 WRÓĆ DO MENU"
            )

            button(
                canvas,
                RectF(
                    25f,
                    height - 75f,
                    width - 25f,
                    height - 20f
                ),
                "🌍 PLANETY"
            )
        }

        // =========================================================
        // RYSOWANIE RAKIETY
        // =========================================================

        private fun drawRocket(
            canvas: Canvas,
            x: Float,
            y: Float,
            scale: Float,
            imageName: String
        ) {

            val id =
                resources.getIdentifier(
                    imageName,
                    "drawable",
                    context.packageName
                )

            if (id != 0) {

                val bitmap =
                    BitmapFactory.decodeResource(
                        resources,
                        id
                    )

                if (bitmap != null) {

                    val targetH =
                        180f * scale

                    val ratio =
                        targetH /
                            bitmap.height

                    val targetW =
                        bitmap.width *
                            ratio

                    val rect =
                        RectF(
                            x - targetW / 2f,
                            y - targetH / 2f,
                            x + targetW / 2f,
                            y + targetH / 2f
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

            // Awaryjny model rakiety,
            // jeżeli grafika nie istnieje.

            paint.color =
                Color.LTGRAY

            canvas.drawRoundRect(
                RectF(
                    x - 24f * scale,
                    y - 70f * scale,
                    x + 24f * scale,
                    y + 60f * scale
                ),
                20f,
                20f,
                paint
            )

            val nose =
                Path()

            nose.moveTo(
                x - 24f * scale,
                y - 70f * scale
            )

            nose.lineTo(
                x,
                y - 108f * scale
            )

            nose.lineTo(
                x + 24f * scale,
                y - 70f * scale
            )

            nose.close()

            canvas.drawPath(
                nose,
                paint
            )

            paint.color =
                Color.rgb(
                    45,
                    125,
                    190
                )

            canvas.drawCircle(
                x,
                y - 20f * scale,
                11f * scale,
                paint
            )

            paint.color =
                Color.rgb(
                    210,
                    65,
                    55
                )

            val leftFin =
                Path()

            leftFin.moveTo(
                x - 22f * scale,
                y + 30f * scale
            )

            leftFin.lineTo(
                x - 52f * scale,
                y + 65f * scale
            )

            leftFin.lineTo(
                x - 18f * scale,
                y + 55f * scale
            )

            leftFin.close()

            canvas.drawPath(
                leftFin,
                paint
            )

            val rightFin =
                Path()

            rightFin.moveTo(
                x + 22f * scale,
                y + 30f * scale
            )

            rightFin.lineTo(
                x + 52f * scale,
                y + 65f * scale
            )

            rightFin.lineTo(
                x + 18f * scale,
                y + 55f * scale
            )

            rightFin.close()

            canvas.drawPath(
                rightFin,
                paint
            )
        }

        // =========================================================
        // DOTYK
        // =========================================================

        override fun onTouchEvent(
            event: MotionEvent
        ): Boolean {

            val x = event.x
            val y = event.y

            if (
                event.action ==
                MotionEvent.ACTION_DOWN
            ) {

                when (screen) {

                    Screen.MENU -> {

                        when {

                            y in 390f..450f ->
                                startMission()

                            y in 465f..525f -> {

                                screen =
                                    Screen.ROCKETS

                                invalidate()
                            }

                            y in 540f..600f -> {

                                screen =
                                    Screen.PLANETS

                                invalidate()
                            }

                            y in 615f..675f -> {

                                Toast.makeText(
                                    context,
                                    "🔬 Badania: $research pkt.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }

                    Screen.ROCKETS -> {

                        val cardW =
                            (width - 54f) / 2f

                        val cardH = 190f
                        val gap = 14f

                        if (y >= 95f) {

                            val col =
                                (
                                    (x - 18f) /
                                        (cardW + gap)
                                    ).toInt()

                            val row =
                                (
                                    (y - 95f) /
                                        (cardH + gap)
                                    ).toInt()

                            val index =
                                row * 2 + col

                            if (
                                col in 0..1 &&
                                index in
                                RocketDatabase.rockets.indices
                            ) {

                                selectRocket(
                                    index + 1
                                )
                            }
                        }
                    }

                    Screen.PLANETS -> {

                        if (y >= 95f) {

                            val index =
                                (
                                    (y - 95f) /
                                        78f
                                    ).toInt()

                            val planets =
                                listOf(
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

                            if (
                                index in planets.indices
                            ) {

                                if (
                                    index == 0 ||
                                    moonLanded
                                ) {

                                    selectedPlanet =
                                        planets[index]

                                    Toast.makeText(
                                        context,
                                        "Wybrano: $selectedPlanet",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                } else {

                                    Toast.makeText(
                                        context,
                                        "🌙 Najpierw wyląduj na Księżycu.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }

                    Screen.FLIGHT -> {

                        if (
                            y >=
                            height - 110f
                        ) {

                            if (
                                x <
                                width / 2f
                            ) {

                                ignition = true
                                engineHeld = true

                            } else {

                                startLanding()
                            }
                        }
                    }

                    Screen.LANDING -> {

                        when {

                            y >= height - 130f &&
                                y < height - 60f &&
                                x < width / 2f ->

                                performLanding(8f)

                            y >= height - 130f &&
                                y < height - 60f &&
                                x >= width / 2f ->

                                performLanding(-8f)

                            y >= height - 60f ->
                                performLanding(-4f)
                        }
                    }

                    Screen.RESULT -> {

                        if (
                            y >=
                            height - 145f &&
                            y <
                            height - 85f
                        ) {

                            screen =
                                Screen.MENU

                            invalidate()

                        } else if (
                            y >=
                            height - 85f
                        ) {

                            screen =
                                Screen.PLANETS

                            invalidate()
                        }
                    }
                }

                return true
            }

            if (
                event.action ==
                    MotionEvent.ACTION_UP ||
                event.action ==
                    MotionEvent.ACTION_CANCEL
            ) {

                if (
                    screen ==
                    Screen.FLIGHT
                ) {
                    engineHeld = false
                }

                return true
            }

            return true
        }

        // =========================================================
        // START MISJI
        // =========================================================

        private fun startMission() {

            if (selectedPlanet != "Księżyc") {

                selectedPlanet =
                    "Księżyc"

                Toast.makeText(
                    context,
                    "🌙 Pierwsza misja prowadzi na Księżyc.",
                    Toast.LENGTH_SHORT
                ).show()
            }

            fuel = 100f
            thrust = 0f
            speed = 0f
            altitude = 0f
            temperature = 22f
            integrity = 100f
            verticalSpeed = 0f

            launchCountdown = 3.0f
            launchStarted = false
            ignition = false
            launchPhase = 0f
            rocketShake = 0f
            smokeTime = 0f
            engineHeld = false

            landingPhase = 0f

            screen =
                Screen.FLIGHT

            flightRunning = true

            handler.removeCallbacks(loop)
            handler.post(loop)

            Toast.makeText(
                context,
                "🚀 Przygotowanie do startu...",
                Toast.LENGTH_SHORT
            ).show()
        }

        private fun startLanding() {

            flightRunning = false

            handler.removeCallbacks(loop)

            altitude =
                max(
                    100f,
                    altitude * 1000f
                )

            verticalSpeed =
                -max(
                    5f,
                    speed * 0.35f
                )

            landingPhase = 0f

            screen =
                Screen.LANDING

            invalidate()
        }

        // =========================================================
        // RAKIETY
        // =========================================================

        private fun selectRocket(id: Int) {

            val r =
                RocketDatabase.getRocket(id)

            if (isRocketUnlocked(id)) {

                selectedRocketId = id
                saveGame()

                Toast.makeText(
                    context,
                    "🚀 Wybrano ${r.name}",
                    Toast.LENGTH_SHORT
                ).show()

                invalidate()

                return
            }

            if (
                research <
                r.researchRequired
            ) {

                Toast.makeText(
                    context,
                    "🔬 Potrzebujesz ${r.researchRequired} pkt badań.",
                    Toast.LENGTH_SHORT
                ).show()

                return
            }

            if (
                credits <
                r.price
            ) {

                Toast.makeText(
                    context,
                    "💰 Potrzebujesz ${r.price} kredytów.",
                    Toast.LENGTH_SHORT
                ).show()

                return
            }

            credits -= r.price

            prefs.edit()
                .putBoolean(
                    "rocket_$id",
                    true
                )
                .apply()

            selectedRocketId = id

            saveGame()

            Toast.makeText(
                context,
                "🚀 ${r.name} odblokowana!",
                Toast.LENGTH_LONG
            ).show()

            invalidate()
        }

        // =========================================================
        // BACK
        // =========================================================

        fun goBack(): Boolean {

            return when (screen) {

                Screen.MENU ->
                    false

                Screen.ROCKETS,
                Screen.PLANETS -> {

                    screen =
                        Screen.MENU

                    invalidate()
                    true
                }

                Screen.FLIGHT,
                Screen.LANDING,
                Screen.RESULT -> {

                    flightRunning = false
                    handler.removeCallbacks(loop)

                    screen =
                        Screen.MENU

                    invalidate()
                    true
                }
            }
        }
    }
}
