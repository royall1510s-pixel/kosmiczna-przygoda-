package com.kosmicznaprzygoda.app

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import kotlin.math.*
import kotlin.random.Random

object Prefs {
    private lateinit var sp: SharedPreferences
    fun init(ctx: Context) { if (!::sp.isInitialized) sp = ctx.getSharedPreferences("game", Context.MODE_PRIVATE) }
    fun getMoney(): Int = sp.getInt("money", 0)
    // Jedyne miejsce dodające pieniądze - wywoływane wyłącznie po ukończonej misji
    fun addMoney(amount: Int) { if (amount > 0) sp.edit().putInt("money", getMoney() + amount).apply() }
    fun spendMoney(amount: Int): Boolean { val m = getMoney(); return if (m >= amount) { sp.edit().putInt("money", m - amount).apply(); true } else false }
    fun getRocketLevel(): Int = sp.getInt("rocketLevel", 1)
    fun upgradeRocket() { sp.edit().putInt("rocketLevel", getRocketLevel() + 1).apply() }
    fun isMoonBaseBuilt(): Boolean = sp.getBoolean("moonBase", false)
    fun setMoonBaseBuilt() { sp.edit().putBoolean("moonBase", true).apply() }
    fun getMaxPlanet(): Int = sp.getInt("maxPlanet", 0)
    fun setMaxPlanet(idx: Int) { if (idx > getMaxPlanet()) sp.edit().putInt("maxPlanet", idx).apply() }
}

object Sound {
    fun beep(freqHz: Double, durMs: Int, volume: Double = 0.3) {
        Thread {
            try {
                val sampleRate = 44100
                val numSamples = durMs * sampleRate / 1000
                val buffer = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val angle = 2.0 * Math.PI * i * freqHz / sampleRate
                    val fade = min(1.0, min(i / 200.0, (numSamples - i) / 200.0))
                    buffer[i] = (sin(angle) * Short.MAX_VALUE * volume * fade).toInt().toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(buffer, 0, buffer.size)
                track.play()
                Thread.sleep(durMs.toLong() + 50)
                track.release()
            } catch (e: Exception) { }
        }.start()
    }
    fun thrust() = beep(120.0, 120, 0.15)
    fun click() = beep(600.0, 60, 0.2)
    fun collect() = beep(880.0, 90, 0.25)
    fun success() = beep(660.0, 140, 0.3)
    fun fail() = beep(160.0, 300, 0.3)
}

enum class Mineral(val label: String, val value: Int, val color: Int) {
    ZELAZO("Żelazo", 20, Color.rgb(160, 160, 170)),
    SREBRO("Srebro", 60, Color.rgb(210, 210, 230)),
    ZLOTO("Złoto", 150, Color.rgb(255, 215, 80))
}

data class Planet(val name: String, val targetAltitude: Float, val minRocketLevel: Int, val reward: Int, val color: Int)

val PLANETS = listOf(
    Planet("Księżyc", 60f, 1, 300, Color.rgb(200, 200, 205)),
    Planet("Mars", 150f, 2, 600, Color.rgb(210, 110, 70)),
    Planet("Wenus", 230f, 3, 900, Color.rgb(230, 190, 120)),
    Planet("Jowisz", 340f, 4, 1400, Color.rgb(220, 170, 120)),
    Planet("Saturn", 430f, 5, 1900, Color.rgb(230, 200, 150)),
    Planet("Uran", 520f, 6, 2500, Color.rgb(150, 210, 220)),
    Planet("Neptun", 610f, 6, 3200, Color.rgb(90, 110, 220))
)

data class Star(val x: Float, val y: Float, val r: Float)
data class Dust(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float)
data class Btn(val rect: RectF, val action: () -> Unit)

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        setContentView(GameView(this))
    }
}

class GameView(ctx: Context) : View(ctx) {

    private var screen = "menu"
    private var selectedPlanetIndex = 0
    private var fuel = 100f
    private var altitude = 0f
    private var speed = 0f
    private var thrust = false
    private var lastFrame = System.currentTimeMillis()
    private var lastThrustSound = 0L
    private var resultText = ""
    private var resultMoney = 0
    private var missionType = "planet"

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stars = List(120) { Star(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 1.6f + 0.5f) }
    private val dust = mutableListOf<Dust>()
    private val buttons = mutableListOf<Btn>()

    private data class FloatingAsteroid(var x: Float, var y: Float, val mineral: Mineral, var collected: Boolean = false)
    private val asteroids = mutableListOf<FloatingAsteroid>()
    private var asteroidTimeLeft = 0f
    private var minedValue = 0

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val now = System.currentTimeMillis()
        val dt = (now - lastFrame) / 1000f
        lastFrame = now
        buttons.clear()
        drawBackground(c)
        when (screen) {
            "menu" -> drawMenu(c)
            "hangar" -> drawHangar(c)
            "planets" -> drawPlanets(c)
            "launch" -> { updateFlight(dt); drawFlight(c) }
            "orbit" -> drawOrbit(c)
            "asteroid" -> { updateAsteroid(dt); drawAsteroid(c) }
            "result" -> drawResult(c)
        }
        postInvalidateDelayed(35)
    }

    private fun drawBackground(c: Canvas) {
        c.drawColor(Color.rgb(3, 6, 16))
        for (s in stars) { p.color = Color.WHITE; p.alpha = (140 + s.r * 60).toInt().coerceIn(0, 255); c.drawCircle(s.x * width, s.y * height, s.r, p) }
        p.alpha = 255
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, bold: Boolean = false, color: Int = Color.WHITE, center: Boolean = false) {
        p.color = color
        p.textSize = size
        p.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        p.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
        c.drawText(s, x, y, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun button(c: Canvas, label: String, x: Float, y: Float, w: Float, h: Float, active: Boolean = true, action: () -> Unit) {
        val rect = RectF(x, y, x + w, y + h)
        p.style = Paint.Style.FILL
        p.color = if (active) Color.rgb(20, 85, 100) else Color.rgb(28, 30, 40)
        c.drawRoundRect(rect, 20f, 20f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = if (active) Color.rgb(90, 220, 240) else Color.DKGRAY
        c.drawRoundRect(rect, 20f, 20f, p)
        p.style = Paint.Style.FILL
        text(c, label, x + w / 2f, y + h * 0.63f, 22f, true, Color.WHITE, center = true)
        if (active) buttons.add(Btn(rect, action))
    }

    private fun drawRocketVector(c: Canvas, cx: Float, cy: Float, scale: Float, showFlame: Boolean) {
        val w = 40f * scale; val h = 100f * scale
        p.style = Paint.Style.FILL
        p.color = Color.rgb(210, 220, 230)
        c.drawRoundRect(RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f), w * 0.4f, w * 0.4f, p)
        p.color = Color.rgb(90, 190, 230)
        c.drawCircle(cx, cy - h * 0.18f, w * 0.28f, p)
        p.color = Color.rgb(200, 70, 60)
        val fin1 = Path(); fin1.moveTo(cx - w / 2f, cy + h * 0.3f); fin1.lineTo(cx - w * 0.9f, cy + h / 2f); fin1.lineTo(cx - w / 2f, cy + h / 2f); fin1.close()
        c.drawPath(fin1, p)
        val fin2 = Path(); fin2.moveTo(cx + w / 2f, cy + h * 0.3f); fin2.lineTo(cx + w * 0.9f, cy + h / 2f); fin2.lineTo(cx + w / 2f, cy + h / 2f); fin2.close()
        c.drawPath(fin2, p)
        if (showFlame) {
            val flameLen = (30f + Random.nextFloat() * 20f) * scale
            p.color = Color.rgb(255, 150 + Random.nextInt(80), 40)
            val flame = Path()
            flame.moveTo(cx - w * 0.28f, cy + h / 2f)
            flame.lineTo(cx, cy + h / 2f + flameLen)
            flame.lineTo(cx + w * 0.28f, cy + h / 2f)
            flame.close()
            c.drawPath(flame, p)
        }
    }

    private fun drawMenu(c: Canvas) {
        text(c, "KOSMICZNA", width / 2f, height * 0.22f, 46f, true, Color.WHITE, center = true)
        text(c, "PRZYGODA", width / 2f, height * 0.28f, 46f, true, Color.rgb(80, 210, 245), center = true)
        text(c, "Saldo: ${Prefs.getMoney()} kredytów", width / 2f, height * 0.34f, 20f, false, Color.rgb(255, 210, 90), center = true)
        drawRocketVector(c, width / 2f, height * 0.5f, 1.6f, false)
        val bw = width * 0.6f; val bx = width / 2f - bw / 2f
        button(c, "START MISJI", bx, height * 0.62f, bw, 64f) { screen = "planets" }
        button(c, "HANGAR", bx, height * 0.70f, bw, 56f) { screen = "hangar" }
        text(c, "Poziom rakiety: ${Prefs.getRocketLevel()}/6", width / 2f, height * 0.80f, 16f, false, Color.LTGRAY, center = true)
    }

    private fun drawHangar(c: Canvas) {
        text(c, "HANGAR", width / 2f, height * 0.12f, 34f, true, Color.rgb(180, 240, 255), center = true)
        button(c, "‹ Wstecz", 24f, 50f, 120f, 48f) { screen = "menu" }
        val lvl = Prefs.getRocketLevel()
        text(c, "Poziom rakiety: $lvl / 6", width / 2f, 170f, 22f, true, Color.WHITE, center = true)
        drawRocketVector(c, width / 2f, 290f, 1.4f, false)
        text(c, "Ciąg silnika: ${100 + (lvl - 1) * 20}%", width / 2f, 400f, 18f, false, Color.WHITE, center = true)
        text(c, "Zbiornik paliwa: ${100 + (lvl - 1) * 15}%", width / 2f, 430f, 18f, false, Color.WHITE, center = true)
        text(c, "Wyższy poziom = dalszy zasięg lotu", width / 2f, 460f, 15f, false, Color.LTGRAY, center = true)
        val cost = lvl * 400
        if (lvl < 6) {
            val canAfford = Prefs.getMoney() >= cost
            button(c, "ULEPSZ za $cost kr.", width / 2f - 140f, 510f, 280f, 60f, canAfford) {
                if (Prefs.spendMoney(cost)) { Prefs.upgradeRocket(); Sound.success() }
            }
        } else {
            text(c, "MAKSYMALNY POZIOM", width / 2f, 540f, 20f, true, Color.rgb(120, 255, 150), center = true)
        }
    }

    private fun drawPlanets(c: Canvas) {
        text(c, "UKŁAD SŁONECZNY", width / 2f, 70f, 26f, true, Color.rgb(180, 240, 255), center = true)
        button(c, "‹ Wstecz", 24f, 20f, 120f, 46f) { screen = "menu" }
        var y = 110f
        val maxReached = Prefs.getMaxPlanet()
        for ((i, planet) in PLANETS.withIndex()) {
            val unlocked = Prefs.getRocketLevel() >= planet.minRocketLevel && i <= maxReached
            button(c, "${planet.name}  •  nagroda ${planet.reward} kr.", 24f, y, width - 48f, 56f, unlocked) {
                selectedPlanetIndex = i; missionType = "planet"; startFlight()
            }
            y += 64f
        }
        val crewAvailable = !Prefs.isMoonBaseBuilt()
        button(c, if (crewAvailable) "ZABIERZ ZAŁOGĘ NA KSIĘŻYC (+800 kr.)" else "BAZA NA KSIĘŻYCU ZBUDOWANA", 24f, y + 10f, width - 48f, 56f, crewAvailable) {
            missionType = "crew"; startFlight()
        }
        y += 74f
        val asteroidUnlocked = maxReached >= 1
        button(c, "PAS ASTEROID – WYDOBYCIE MINERAŁÓW", 24f, y + 10f, width - 48f, 56f, asteroidUnlocked) {
            startAsteroidMission()
        }
    }

    private fun startFlight() {
        fuel = 100f; altitude = 0f; speed = 0f; thrust = false; screen = "launch"
    }

    private fun updateFlight(dt: Float) {
        val lvl = Prefs.getRocketLevel()
        val burn = 9f - (lvl - 1) * 0.8f
        val thrustPower = 380f + (lvl - 1) * 70f
        val maxSpeed = 2000f + (lvl - 1) * 220f
        val now = System.currentTimeMillis()
        if (thrust && fuel > 0f) {
            fuel = max(0f, fuel - burn * dt)
            speed = min(maxSpeed, speed + thrustPower * dt)
            if (now - lastThrustSound > 180) { Sound.thrust(); lastThrustSound = now }
        } else {
            speed = max(0f, speed - 90f * dt)
        }
        altitude += speed * dt / 90f
        if (fuel <= 0f) thrust = false

        if (thrust && altitude < 15f) {
            repeat(2) { dust.add(Dust(width / 2f + Random.nextFloat() * 60f - 30f, height * 0.78f, (Random.nextFloat() - 0.5f) * 90f, -Random.nextFloat() * 40f, 1f)) }
        }
        val it = dust.iterator()
        while (it.hasNext()) {
            val d = it.next()
            d.x += d.vx * dt; d.y += d.vy * dt; d.life -= dt * 0.8f
            if (d.life <= 0f) it.remove()
        }

        val targetAlt = if (missionType == "crew") 60f else PLANETS[selectedPlanetIndex].targetAltitude
        if (altitude >= targetAlt) {
            screen = "orbit"
        } else if (fuel <= 0f && speed <= 0f) {
            finishMission(false, 0)
        }
    }

    private fun drawFlight(c: Canvas) {
        val targetAlt = if (missionType == "crew") 60f else PLANETS[selectedPlanetIndex].targetAltitude
        val destName = if (missionType == "crew") "KSIĘŻYC (ZAŁOGA)" else PLANETS[selectedPlanetIndex].name.uppercase()
        text(c, "LOT DO: $destName", 24f, 50f, 20f, true, Color.WHITE)
        text(c, "Wysokość ${"%.1f".format(altitude)} / ${"%.0f".format(targetAlt)} km", 24f, 84f, 16f, false, Color.WHITE)
        text(c, "Prędkość ${"%.0f".format(speed)} m/s", 24f, 108f, 16f, false, Color.WHITE)
        p.style = Paint.Style.FILL
        p.color = Color.DKGRAY; c.drawRoundRect(width - 180f, 40f, width - 24f, 60f, 8f, 8f, p)
        p.color = if (fuel > 25f) Color.rgb(70, 220, 120) else Color.rgb(230, 80, 60)
        c.drawRoundRect(width - 180f, 40f, width - 180f + (width - 204f) * fuel / 100f, 60f, 8f, 8f, p)
        text(c, "PALIWO", width - 180f, 32f, 14f, false, Color.LTGRAY)

        for (d in dust) { p.color = Color.argb((d.life * 160).toInt().coerceIn(0, 160), 210, 190, 160); c.drawCircle(d.x, d.y, 6f, p) }

        val ry = height * (0.75f - min(0.5f, altitude / targetAlt * 0.5f))
        drawRocketVector(c, width / 2f, ry, 1.5f, thrust && fuel > 0f)

        val er = RectF(width / 2f - 150f, height - 120f, width / 2f + 150f, height - 46f)
        p.style = Paint.Style.FILL
        p.color = if (thrust) Color.rgb(30, 130, 100) else Color.rgb(20, 85, 100)
        c.drawRoundRect(er, 20f, 20f, p)
        text(c, if (thrust) "SILNIK PRACUJE" else "PRZYTRZYMAJ SILNIK", width / 2f, height - 74f, 22f, true, Color.WHITE, center = true)
    }

    private fun drawOrbit(c: Canvas) {
        val destName = if (missionType == "crew") "KSIĘŻYC" else PLANETS[selectedPlanetIndex].name
        text(c, "ORBITA: $destName", width / 2f, 90f, 26f, true, Color.WHITE, center = true)
        text(c, "Okrążasz cel... gotowy do lądowania.", width / 2f, 124f, 16f, false, Color.LTGRAY, center = true)
        val planetColor = if (missionType == "crew") Color.rgb(200, 200, 205) else PLANETS[selectedPlanetIndex].color
        p.style = Paint.Style.FILL
        p.color = planetColor
        c.drawCircle(width / 2f, height * 0.5f, 110f, p)
        drawRocketVector(c, width / 2f + 150f, height * 0.35f, 0.8f, false)
        button(c, "LĄDUJ", width / 2f - 120f, height * 0.75f, 240f, 64f) {
            if (missionType == "crew") {
                Prefs.setMoonBaseBuilt()
                finishMission(true, 800)
            } else {
                Prefs.setMaxPlanet(selectedPlanetIndex + 1)
                finishMission(true, PLANETS[selectedPlanetIndex].reward)
            }
        }
    }

    private fun finishMission(success: Boolean, reward: Int) {
        if (success && reward > 0) { Prefs.addMoney(reward); Sound.success() } else { Sound.fail() }
        resultText = if (success) "MISJA ZAKOŃCZONA SUKCESEM!\nZarobiono: $reward kredytów" else "BRAK PALIWA\nNie udało się dolecieć do celu."
        resultMoney = reward
        screen = "result"
    }

    private fun drawResult(c: Canvas) {
        val lines = resultText.split("\n")
        for ((i, line) in lines.withIndex()) {
            text(c, line, width / 2f, height * 0.4f + i * 36f, 24f, true, if (resultMoney > 0) Color.rgb(120, 255, 150) else Color.rgb(255, 120, 110), center = true)
        }
        button(c, "OK", width / 2f - 100f, height * 0.6f, 200f, 60f) { screen = "planets" }
    }

    private fun startAsteroidMission() {
        asteroids.clear(); minedValue = 0; asteroidTimeLeft = 20f; screen = "asteroid"
    }

    private fun updateAsteroid(dt: Float) {
        asteroidTimeLeft -= dt
        if (Random.nextFloat() < dt * 1.2f && asteroids.size < 8) {
            asteroids.add(FloatingAsteroid(Random.nextFloat() * (width - 80f) + 40f, -40f, Mineral.values().random()))
        }
        val it = asteroids.iterator()
        while (it.hasNext()) {
            val a = it.next()
            if (!a.collected) a.y += 90f * dt
            if (a.y > height + 60f) it.remove()
        }
        if (asteroidTimeLeft <= 0f) finishMission(true, minedValue)
    }

    private fun handleAsteroidTap(x: Float, y: Float) {
        for (a in asteroids) {
            if (!a.collected && hypot((a.x - x).toDouble(), (a.y - y).toDouble()) < 45) {
                a.collected = true; minedValue += a.mineral.value; Sound.collect()
                break
            }
        }
        asteroids.removeAll { it.collected }
    }

    private fun drawAsteroid(c: Canvas) {
        text(c, "PAS ASTEROID", width / 2f, 50f, 24f, true, Color.WHITE, center = true)
        text(c, "Czas: ${max(0, asteroidTimeLeft.toInt())}s   Wartość: $minedValue kr.", width / 2f, 82f, 16f, false, Color.rgb(255, 210, 90), center = true)
        for (a in asteroids) {
            p.style = Paint.Style.FILL
            p.color = a.mineral.color
            c.drawCircle(a.x, a.y, 28f, p)
            text(c, a.mineral.label, a.x, a.y + 45f, 12f, false, Color.WHITE, center = true)
        }
        text(c, "Dotknij asteroidy, aby wydobyć minerał", width / 2f, height - 40f, 14f, false, Color.LTGRAY, center = true)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x; val y = e.y
        if (screen == "launch") {
            val er = RectF(width / 2f - 150f, height - 120f, width / 2f + 150f, height - 46f)
            when (e.action) {
                MotionEvent.ACTION_DOWN -> if (er.contains(x, y)) thrust = true
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> thrust = false
            }
            return true
        }
        if (screen == "asteroid" && e.action == MotionEvent.ACTION_DOWN) {
            handleAsteroidTap(x, y)
            return true
        }
        if (e.action == MotionEvent.ACTION_UP) {
            for (b in buttons) {
                if (b.rect.contains(x, y)) { Sound.click(); b.action(); break }
            }
        }
        return true
    }
}
