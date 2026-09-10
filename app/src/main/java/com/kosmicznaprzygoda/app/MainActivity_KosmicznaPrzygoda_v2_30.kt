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
    private val prefs by lazy { getSharedPreferences("kosmiczna_przygoda", Context.MODE_PRIVATE) }

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

        private enum class Screen { MENU, ROCKETS, PLANETS, FLIGHT, LANDING, RESULT }

        private var screen = Screen.MENU
        private var selectedRocketId = prefs.getInt("selectedRocket", 1)
        private var credits = prefs.getInt("credits", 1250)
        private var research = prefs.getInt("research", 0)
        private var moonLanded = prefs.getBoolean("moonLanded", false)

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

        private val handler = Handler(Looper.getMainLooper())

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
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
                handler.postDelayed(this, 50L)
            }
        }

        init {
            setBackgroundColor(Color.rgb(2, 5, 14))
            isFocusable = true
        }

        private fun rocket(): Rocket =
            RocketDatabase.getRocket(selectedRocketId)

        private fun unlocked(id: Int): Boolean {
            return id == 1 || prefs.getBoolean("rocket_$id", false)
        }

        private fun save() {
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

        private fun drawBackground(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()

            paint.shader = LinearGradient(
                0f, 0f, 0f, h,
                Color.rgb(3, 12, 28),
                Color.rgb(1, 3, 10),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null

            paint.color = Color.WHITE
            for (s in stars) {
                val x = s.x / 1000f * w
                val y = s.y / 1800f * h
                val r = 1f + ((s.x.toInt() + s.y.toInt()) % 3)
                canvas.drawCircle(x, y, r, paint)
            }
        }

        private fun drawTitle(canvas: Canvas, title: String, subtitle: String? = null) {
            text.color = Color.rgb(80, 220, 255)
            text.textSize = 31f
            text.textAlign = Paint.Align.CENTER
            canvas.drawText(title, width / 2f, 52f, text)

            if (subtitle != null) {
                text.color = Color.rgb(150, 180, 195)
                text.textSize = 17f
                canvas.drawText(subtitle, width / 2f, 80f, text)
            }
        }

        private fun drawButton(
            canvas: Canvas,
            rect: RectF,
            label: String,
            enabled: Boolean = true
        ) {
            paint.color = if (enabled)
                Color.rgb(8, 38, 56)
            else
                Color.rgb(25, 28, 34)

            canvas.drawRoundRect(rect, 18f, 18f, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = if (enabled)
                Color.rgb(35, 175, 215)
            else
                Color.rgb(70, 75, 80)
            canvas.drawRoundRect(rect, 18f, 18f, paint)
            paint.style = Paint.Style.FILL

            text.color = if (enabled) Color.WHITE else Color.rgb(120, 125, 130)
            text.textSize = 20f
            text.textAlign = Paint.Align.CENTER
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
            text.textAlign = Paint.Align.CENTER
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

            val cx = width / 2f
            val cy = 265f

            drawRocket(canvas, cx, cy, 0.72f, rocket().imageName)

            drawButton(canvas, RectF(40f, 390f, width - 40f, 450f), "🚀  ROZPOCZNIJ MISJĘ")
            drawButton(canvas, RectF(40f, 465f, width - 40f, 525f), "🛰  WYBIERZ RAKIETĘ")
            drawButton(canvas, RectF(40f, 540f, width - 40f, 600f), "🌍  PLANETY")
            drawButton(canvas, RectF(40f, 615f, width - 40f, 675f), "🔬  BADANIA")

            text.color = Color.rgb(130, 165, 180)
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
            drawTitle(canvas, "WYBIERZ RAKIETĘ", "20 jednostek programu kosmicznego")

            val cols = 2
            val cardW = (width - 54f) / 2f
            val cardH = 205f
            val startY = 105f
            val gap = 14f

            for (i in RocketDatabase.rockets.indices) {
                val r = RocketDatabase.rockets[i]
                val col = i % cols
                val row = i / cols
                val left = 18f + col * (cardW + gap)
                val top = startY + row * (cardH + gap)
                val rect = RectF(left, top, left + cardW, top + cardH)

                paint.color = if (r.id == selectedRocketId)
                    Color.argb(100, 20, 130, 170)
                else
                    Color.argb(85, 10, 30, 45)
                canvas.drawRoundRect(rect, 18f, 18f, paint)

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                paint.color = if (r.id == selectedRocketId)
                    Color.rgb(75, 225, 255)
                else
                    Color.rgb(25, 90, 115)
                canvas.drawRoundRect(rect, 18f, 18f, paint)
                paint.style = Paint.Style.FILL

                drawRocket(
                    canvas,
                    rect.centerX(),
                    top + 78f,
                    0.34f,
                    r.imageName
                )

                text.textAlign = Paint.Align.CENTER
                text.textSize = 16f
                text.color = Color.WHITE
                canvas.drawText(r.name, rect.centerX(), top + 128f, text)

                text.textSize = 12f
                text.color = Color.rgb(170, 195, 205)
                canvas.drawText(
                    "Paliwo ${r.fuelCapacity.toInt()} • Udźwig ${r.cargo}",
                    rect.centerX(),
                    top + 148f,
                    text
                )

                val available = unlocked(r.id)
                val state = when {
                    r.id == selectedRocketId -> "WYBRANA"
                    available -> "WYBIERZ"
                    research < r.researchRequired -> "BADANIA ${r.researchRequired}"
                    credits < r.price -> "${r.price} KR"
                    else -> "KUP ${r.price} KR"
                }

                text.color = if (available || (research >= r.researchRequired && credits >= r.price))
                    Color.rgb(100, 230, 175)
                else
                    Color.rgb(255, 190, 80)

                canvas.drawText(state, rect.centerX(), top + 178f, text)
            }
        }

        private fun drawPlanets(canvas: Canvas) {
            drawTitle(canvas, "PLANETY", "Najpierw Księżyc — potem ludzie i kolonie")

            val planets = listOf(
                "Księżyc" to true,
                "Mars" to moonLanded,
                "Wenus" to moonLanded,
                "Jowisz" to moonLanded,
                "Saturn" to moonLanded,
                "Uran" to moonLanded,
                "Neptun" to moonLanded,
                "Aurelia" to moonLanded,
                "Ignara" to moonLanded,
                "Cryonis" to moonLanded,
                "Nectaris" to moonLanded,
                "Obsidia" to moonLanded
            )

            val cardH = 74f
            for (i in planets.indices) {
                val (name, available) = planets[i]
                val top = 105f + i * 80f
                drawButton(
                    canvas,
                    RectF(28f, top, width - 28f, top + cardH),
                    if (available) "🌐  $name" else "🔒  $name — ZABLOKOWANA",
                    available
                )
            }
        }

        private fun drawFlight(canvas: Canvas) {
            drawEarthAndTower(canvas)

            val r = rocket()
            val rocketX = width / 2f
            val baseY = height * 0.78f - launchPhase * height * 0.52f

            drawRocket(canvas, rocketX, baseY, 0.62f, r.imageName)

            if (engineHeld) {
                paint.color = Color.argb(190, 255, 160, 50)
                val flameH = 50f + thrust * 0.8f
                canvas.drawOval(
                    RectF(
                        rocketX - 25f,
                        baseY + 45f,
                        rocketX + 25f,
                        baseY + 45f + flameH
                    ),
                    paint
                )
                paint.color = Color.argb(120, 230, 230, 230)
                canvas.drawCircle(rocketX - 42f, baseY + 70f, 15f, paint)
                canvas.drawCircle(rocketX + 38f, baseY + 85f, 11f, paint)
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

            drawButton(
                canvas,
                RectF(24f, height - 90f, width / 2f - 10f, height - 28f),
                if (engineHeld) "🔥 SILNIK" else "🚀 PRZYGOTUJ CIĄG"
            )

            drawButton(
                canvas,
                RectF(width / 2f + 10f, height - 90f, width - 24f, height - 28f),
                "🛬 LĄDOWANIE"
            )
        }

        private fun drawLanding(canvas: Canvas) {
            drawMoonSurface(canvas)

            val r = rocket()
            val x = width / 2f
            val y = height * 0.25f + landingPhase * height * 0.5f

            drawRocket(canvas, x, y, 0.60f, r.imageName)

            val status = when {
                altitude > 300f || speed > 50f -> "CZERWONA • HAMUJ"
                altitude > 100f || speed > 25f -> "ŻÓŁTA • KOREKTA"
                else -> "ZIELONA • TRAJEKTORIA PRAWIDŁOWA"
            }

            text.color = when {
                altitude > 300f || speed > 50f -> Color.rgb(255, 80, 80)
                altitude > 100f || speed > 25f -> Color.rgb(255, 200, 70)
                else -> Color.rgb(90, 240, 170)
            }
            text.textSize = 25f
            text.textAlign = Paint.Align.CENTER
            canvas.drawText(status, width / 2f, 105f, text)

            text.color = Color.WHITE
            text.textSize = 18f
            canvas.drawText(
                "WYSOKOŚĆ ${altitude.toInt()} m",
                width / 2f,
                height - 180f,
                text
            )
            canvas.drawText(
                "PRĘDKOŚĆ OPADANIA ${abs(verticalSpeed).toInt()} m/s",
                width / 2f,
                height - 150f,
                text
            )

            drawButton(
                canvas,
                RectF(25f, height - 125f, width / 2f - 10f, height - 55f),
                "▲ WIĘCEJ CIĄGU"
            )
            drawButton(
                canvas,
                RectF(width / 2f + 10f, height - 125f, width - 25f, height - 55f),
                "▼ HAMUJ"
            )

            drawButton(
                canvas,
                RectF(25f, height - 50f, width - 25f, height - 10f),
                "🛬 WYKONAJ LĄDOWANIE"
            )
        }

        private fun drawResult(canvas: Canvas) {
            drawTitle(
                canvas,
                if (moonLanded) "🌙 LĄDOWANIE UDANE!" else "MISJA ZAKOŃCZONA",
                "KOSMICZNA PRZYGODA"
            )

            val msg = if (moonLanded) {
                "KSIĘŻYC ZOSTAŁ OSIĄGNIĘTY!\n\n" +
                "Baza księżycowa została aktywowana.\n" +
                "Odblokowano transport kolonistów.\n\n" +
                "+500 KREDYTÓW   •   +100 BADAŃ"
            } else {
                "Misja zakończona.\nSpróbuj ponownie i popraw trajektorię."
            }

            text.color = Color.WHITE
            text.textSize = 21f
            text.textAlign = Paint.Align.CENTER

            val lines = msg.split("\n")
            var y = 180f
            for (line in lines) {
                canvas.drawText(line, width / 2f, y, text)
                y += 34f
            }

            drawButton(
                canvas,
                RectF(35f, height - 150f, width - 35f, height - 90f),
                "🚀 POWRÓT DO MENU"
            )
            drawButton(
                canvas,
                RectF(35f, height - 80f, width - 35f, height - 20f),
                "🌙 WRÓĆ DO PLANÓW MISJI"
            )
        }

        private fun drawEarthAndTower(canvas: Canvas) {
            val cx = width / 2f
            val earthY = height * 0.96f
            val radius = width * 0.72f

            paint.color = Color.rgb(20, 100, 170)
            canvas.drawCircle(cx, earthY, radius, paint)

            paint.color = Color.rgb(70, 170, 220)
            canvas.drawCircle(cx, earthY, radius - 14f, paint)

            paint.color = Color.argb(100, 240, 250, 255)
            canvas.drawOval(
                RectF(cx - radius, earthY - radius, cx + radius, earthY + radius * 0.2f),
                paint
            )

            // Wieża startowa — rakieta stoi na szczycie zakrzywionej Ziemi.
            paint.color = Color.rgb(125, 145, 155)
            paint.strokeWidth = 9f
            canvas.drawLine(cx - 70f, height * 0.70f, cx - 70f, earthY - radius + 15f, paint)
            canvas.drawLine(cx + 70f, height * 0.70f, cx + 70f, earthY - radius + 15f, paint)
            canvas.drawLine(cx - 70f, height * 0.70f, cx + 70f, height * 0.70f, paint)
            canvas.drawLine(cx - 70f, height * 0.76f, cx + 70f, height * 0.76f, paint)

            paint.color = Color.rgb(190, 205, 215)
            canvas.drawRect(
                cx - 76f,
                height * 0.67f,
                cx - 64f,
                height * 0.75f,
                paint
            )
        }

        private fun drawMoonSurface(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()

            paint.color = Color.rgb(120, 125, 135)
            canvas.drawCircle(w / 2f, h + 120f, w * 0.95f, paint)

            paint.color = Color.rgb(90, 95, 105)
            for (i in 0 until 12) {
                val x = (i * 97f) % w
                val y = h - 110f - ((i * 43f) % 130f)
                canvas.drawCircle(x, y, 12f + (i % 4) * 5f, paint)
            }

            paint.color = Color.WHITE
            text.color = Color.WHITE
            text.textSize = 27f
            text.textAlign = Paint.Align.CENTER
            canvas.drawText("KSIĘŻYC", w / 2f, 48f, text)
        }

        private fun drawRocket(
            canvas: Canvas,
            x: Float,
            y: Float,
            scale: Float,
            imageName: String
        ) {
            val id = resources.getIdentifier(
                imageName,
                "drawable",
                context.packageName
            )

            if (id != 0) {
                val bitmap = BitmapFactory.decodeResource(resources, id)
                if (bitmap != null) {
                    val maxH = 180f * scale
                    val ratio = maxH / bitmap.height
                    val dw = bitmap.width * ratio
                    val rect = RectF(
                        x - dw / 2f,
                        y - maxH / 2f,
                        x + dw / 2f,
                        y + maxH / 2f
                    )
                    canvas.drawBitmap(bitmap, null, rect, paint)
                    return
                }
            }

            // Awaryjna rakieta rysowana z kodu, gdy PNG nie został jeszcze dodany.
            paint.color = Color.LTGRAY
            val body = RectF(
                x - 24f * scale,
                y - 70f * scale,
                x + 24f * scale,
                y + 60f * scale
            )
            canvas.drawRoundRect(body, 20f, 20f, paint)

            Path nose = Path()
            nose.moveTo(x - 24f * scale, y - 70f * scale)
            nose.lineTo(x, y - 105f * scale)
            nose.lineTo(x + 24f * scale, y - 70f * scale)
            nose.close()
            canvas.drawPath(nose, paint)

            paint.color = Color.rgb(50, 130, 190)
            canvas.drawCircle(x, y - 20f * scale, 11f * scale, paint)

            paint.color = Color.rgb(210, 65, 55)
            val fin = Path()
            fin.moveTo(x - 24f * scale, y + 35f * scale)
            fin.lineTo(x - 52f * scale, y + 65f * scale)
            fin.lineTo(x - 20f * scale, y + 55f * scale)
            fin.close()
            canvas.drawPath(fin, paint)

            val fin2 = Path()
            fin2.moveTo(x + 24f * scale, y + 35f * scale)
            fin2.lineTo(x + 52f * scale, y + 65f * scale)
            fin2.lineTo(x + 20f * scale, y + 55f * scale)
            fin2.close()
            canvas.drawPath(fin2, paint)
        }

        private fun updateFlight() {
            val r = rocket()
            val dt = 0.05f

            val engine = if (engineHeld) 1f else 0f

            thrust += ((engine * 100f) - thrust) * 0.18f
            thrust = thrust.coerceIn(0f, 100f)

            if (launchPhase < 1f) {
                launchPhase += if (engineHeld) 0.012f * r.thrust else 0.002f
                launchPhase = launchPhase.coerceIn(0f, 1f)
            }

            if (engineHeld && fuel > 0f) {
                val acceleration = r.thrust * 42f * dt
                speed += acceleration
                altitude += speed * dt * 0.035f

                fuel -= (0.28f + r.thrust * 0.08f) * dt * 10f
                temperature += (0.8f + r.thrust * 0.18f) * dt * 10f
                integrity -= max(0f, temperature - 92f) * 0.002f
            } else {
                speed *= 0.998f
                altitude += speed * dt * 0.015f
                temperature -= 0.08f
            }

            speed = speed.coerceIn(0f, r.maxSpeed)
            altitude = altitude.coerceIn(0f, r.maxAltitude)
            fuel = fuel.coerceIn(0f, 100f)
            temperature = temperature.coerceIn(20f, 120f)
            integrity = integrity.coerceIn(0f, 100f)

            verticalSpeed = if (engineHeld) speed * 0.08f else -max(1f, speed * 0.015f)

            if (fuel <= 0f || integrity <= 0f || temperature >= 118f) {
                flightRunning = false
                Toast.makeText(context, "Awaria! Misja przerwana.", Toast.LENGTH_SHORT).show()
                screen = Screen.RESULT
            }
        }

        private fun startFlight() {
            val r = rocket()

            fuel = 100f
            thrust = 0f
            speed = 0f
            altitude = 0f
            temperature = 22f
            integrity = 100f
            verticalSpeed = 0f
            launchPhase = 0f
            landingPhase = 0f
            engineHeld = false

            if (selectedPlanet != "Księżyc") {
                Toast.makeText(
                    context,
                    "Najpierw musisz bezpiecznie wylądować na Księżycu.",
                    Toast.LENGTH_SHORT
                ).show()
                selectedPlanet = "Księżyc"
            }

            screen = Screen.FLIGHT
            flightRunning = true
            handler.removeCallbacks(tick)
            handler.post(tick)

            Toast.makeText(
                context,
                "Start: ${r.name}. Cel: Księżyc.",
                Toast.LENGTH_SHORT
            ).show()
        }

        private fun startLanding() {
            flightRunning = false
            handler.removeCallbacks(tick)

            altitude = max(20f, altitude * 1000f)
            speed = speed.coerceAtMost(80f)
            verticalSpeed = -abs(speed * 0.4f)
            landingPhase = 0f

            screen = Screen.LANDING
            invalidate()
        }

        private fun landingAdjust(power: Float) {
            val r = rocket()

            verticalSpeed += power
            altitude += verticalSpeed * 0.08f
            speed = abs(verticalSpeed)

            fuel -= 0.25f
            temperature += abs(power) * 0.03f

            if (power > 0f) {
                integrity += 0.02f
            }

            altitude = altitude.coerceAtLeast(0f)
            fuel = fuel.coerceIn(0f, 100f)
            temperature = temperature.coerceIn(20f, 120f)
            integrity = integrity.coerceIn(0f, 100f)

            landingPhase = (1f - altitude / 1000f).coerceIn(0f, 1f)

            if (fuel <= 0f || integrity <= 0f) {
                screen = Screen.RESULT
                invalidate()
                return
            }

            if (altitude <= 0f) {
                val landingSpeed = abs(verticalSpeed)

                if (landingSpeed <= 18f) {
                    moonLanded = true
                    credits += 500
                    research += 100
                    save()
                    screen = Screen.RESULT
                    Toast.makeText(
                        context,
                        "Lądowanie na Księżycu udane!",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    integrity -= min(90f, landingSpeed * 2.2f)
                    screen = Screen.RESULT
                    Toast.makeText(
                        context,
                        "Za szybkie lądowanie — uszkodzenie rakiety.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            invalidate()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val x = event.x
            val y = event.y

            if (event.action == MotionEvent.ACTION_DOWN) {
                when (screen) {
                    Screen.MENU -> {
                        when {
                            y in 390f..450f -> startFlight()
                            y in 465f..525f -> {
                                screen = Screen.ROCKETS
                                invalidate()
                            }
                            y in 540f..600f -> {
                                screen = Screen.PLANETS
                                invalidate()
                            }
                            y in 615f..675f -> {
                                Toast.makeText(
                                    context,
                                    "Badania: $research pkt. — kolejne technologie odblokujemy w następnej części.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }

                    Screen.ROCKETS -> {
                        if (y >= 105f) {
                            val cols = 2
                            val cardW = (width - 54f) / 2f
                            val cardH = 205f
                            val gap = 14f
                            val col = ((x - 18f) / (cardW + gap)).toInt()
                            val row = ((y - 105f) / (cardH + gap)).toInt()

                            if (col in 0..1 && row >= 0) {
                                val index = row * cols + col
                                if (index in RocketDatabase.rockets.indices) {
                                    selectRocket(index + 1)
                                }
                            }
                        }
                    }

                    Screen.PLANETS -> {
                        if (y >= 105f) {
                            val index = ((y - 105f) / 80f).toInt()
                            val names = listOf(
                                "Księżyc", "Mars", "Wenus", "Jowisz",
                                "Saturn", "Uran", "Neptun", "Aurelia",
                                "Ignara", "Cryonis", "Nectaris", "Obsidia"
                            )
                            if (index in names.indices) {
                                if (index == 0 || moonLanded) {
                                    selectedPlanet = names[index]
                                    Toast.makeText(
                                        context,
                                        "Wybrano: $selectedPlanet",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Najpierw wyląduj na Księżycu.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }

                    Screen.FLIGHT -> {
                        if (y >= height - 110f) {
                            if (x < width / 2f) {
                                engineHeld = true
                            } else {
                                startLanding()
                            }
                        }
                    }

                    Screen.LANDING -> {
                        when {
                            y >= height - 130f && y < height - 60f && x < width / 2f ->
                                landingAdjust(8f)

                            y >= height - 130f && y < height - 60f && x >= width / 2f ->
                                landingAdjust(-8f)

                            y >= height - 60f ->
                                landingAdjust(-4f)
                        }
                    }

                    Screen.RESULT -> {
                        if (y >= height - 160f && y < height - 90f) {
                            screen = Screen.MENU
                            invalidate()
                        } else if (y >= height - 90f) {
                            screen = Screen.PLANETS
                            invalidate()
                        }
                    }
                }
                return true
            }

            if (event.action == MotionEvent.ACTION_UP ||
                event.action == MotionEvent.ACTION_CANCEL) {
                if (screen == Screen.FLIGHT) {
                    engineHeld = false
                }
                return true
            }

            return true
        }

        private fun selectRocket(id: Int) {
            val r = RocketDatabase.getRocket(id)

            if (unlocked(id)) {
                selectedRocketId = id
                save()
                Toast.makeText(
                    context,
                    "Wybrano ${r.name}.",
                    Toast.LENGTH_SHORT
                ).show()
                invalidate()
                return
            }

            if (research < r.researchRequired) {
                Toast.makeText(
                    context,
                    "${r.name}: potrzebujesz ${r.researchRequired} pkt badań.",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            if (credits < r.price) {
                Toast.makeText(
                    context,
                    "Potrzebujesz ${r.price} kredytów.",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            credits -= r.price
            prefs.edit()
                .putBoolean("rocket_$id", true)
                .apply()

            selectedRocketId = id
            save()

            Toast.makeText(
                context,
                "${r.name} odblokowana!",
                Toast.LENGTH_LONG
            ).show()

            invalidate()
        }

        fun goBack(): Boolean {
            return when (screen) {
                Screen.MENU -> false
                Screen.ROCKETS, Screen.PLANETS -> {
                    screen = Screen.MENU
                    invalidate()
                    true
                }
                Screen.FLIGHT, Screen.LANDING, Screen.RESULT -> {
                    flightRunning = false
                    handler.removeCallbacks(tick)
                    screen = Screen.MENU
                    invalidate()
                    true
                }
            }
        }
    }
}
