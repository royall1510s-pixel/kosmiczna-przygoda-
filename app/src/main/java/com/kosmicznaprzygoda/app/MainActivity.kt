package com.kosmicznaprzygoda.app

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.graphics.*
import android.view.*
import kotlin.math.*

class MainActivity : Activity() {
    private var gameView: SpaceGameView? = null

    private fun goImmersive() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        goImmersive()
        gameView = SpaceGameView()
        setContentView(gameView)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goImmersive()
    }

    override fun onPause() {
        super.onPause()
        gameView?.saveState()
    }

    data class Planet(
        val name: String,
        val targetAltitude: Float,
        val reward: Int,
        val color: Int,
        val drawableRes: Int
    )
    data class MissionDef(
    val name: String,
    val reward: Int,
    val requirement: String,
    val isDone: () -> Boolean
)

data class Puff(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var age: Float,
    var life: Float,
    var scale: Float
)

    inner class SpaceGameView : View(this) {

        private var screen = "menu"

        private var credits = 500
        private var rocketLevel = 1
        private var selectedPlanet = 0

        private var fuel = 100f
        private var altitude = 0f
        private var speed = 0f
        private var verticalSpeed = 0f
        private var temperature = 20f
        private var integrity = 100f
        private var flightTime = 0f
        private var thrust = false

        private var phase = "flight"

        private var totalLaunches = 0

        private val landedOn = mutableSetOf<Int>()
        private val claimedMissions = mutableSetOf<Int>()

        // Koloniści są odblokowani dopiero po pierwszym udanym
        // lądowaniu na Księżycu.
        private var peopleUnlocked = false

        // Załoga: dostępna dopiero po pierwszym udanym lądowaniu na Księżycu.
        private var selectedCrew = 0
        private val crewNames = listOf(
            "Dowódca",
            "Pilot",
            "Inżynier"
        )
        private val crewMass = 82f

        // Baza księżycowa: cztery rozwijalne moduły, każdy do poziomu 3.
        private var habitatLevel = 0
        private var powerLevel = 0
        private var researchBaseLevel = 0
        private var storageLevel = 0

        // Pełna progresja technologiczna.
        private var researchPoints = 0
        private var fuelTech = 0
        private var heatTech = 0
        private var navigationTech = 0
        private var landingTech = 0
        private var cargoTech = 0

        private fun moonBaseLevel(): Int =
            habitatLevel + powerLevel + researchBaseLevel + storageLevel

        private fun isPlanetUnlocked(index: Int): Boolean {
            return when (index) {
                0 -> true
                1 -> moonBaseLevel() >= 4
                2 -> landedOn.contains(1)
                3 -> landedOn.contains(2)
                4 -> landedOn.contains(3)
                5 -> landedOn.contains(4)
                6 -> landedOn.contains(5)
                7 -> landedOn.contains(6)
                8 -> landedOn.contains(7)
                9 -> landedOn.contains(8)
                10 -> landedOn.contains(9)
                11 -> landedOn.contains(10)
                else -> false
            }
        }

        private var landingStart = 0L
        private val landingDuration = 12000L
        private var landingVelocity = 0f
        private var landingFuelStart = 0f

        private var launchStart = 0L

        /*
         * 0 = odliczanie
         * 1 = zapłon
         * 2 = start
         * 3 = właściwy lot
         */
        private var launchState = 0

        private var launchShake = 0f

        private var last = System.currentTimeMillis()

        private val planets = listOf(
            Planet(
                "Księżyc",
                50f,
                5000,
                Color.rgb(200, 200, 200),
                R.drawable.planeta_ksiezyc
            ),
            Planet(
                "Mars",
                400f,
                12000,
                Color.rgb(210, 90, 60),
                R.drawable.planeta_mars
            ),
            Planet(
                "Wenus",
                600f,
                20000,
                Color.rgb(230, 190, 120),
                R.drawable.planeta_wenus
            ),
            Planet(
                "Jowisz",
                1200f,
                40000,
                Color.rgb(200, 150, 90),
                R.drawable.planeta_jowisz
            ),
            Planet(
                "Saturn",
                2000f,
                80000,
                Color.rgb(180, 190, 210),
                R.drawable.planeta_saturn
            ),
            Planet(
                "Uran",
                3200f,
                150000,
                Color.rgb(140, 220, 235),
                R.drawable.planeta_uran
            ),
            Planet(
                "Neptun",
                4500f,
                250000,
                Color.rgb(70, 90, 220),
                R.drawable.planeta_neptun
            ),
            Planet(
                "Aurelia",
                6000f,
                320000,
                Color.rgb(40, 120, 90),
                R.drawable.planeta_aurelia
            ),
            Planet(
                "Ignara",
                7800f,
                420000,
                Color.rgb(120, 45, 25),
                R.drawable.planeta_ignara
            ),
            Planet(
                "Cryonis",
                9800f,
                540000,
                Color.rgb(170, 220, 235),
                R.drawable.planeta_cryonis
            ),
            Planet(
                "Nectaris",
                12000f,
                700000,
                Color.rgb(150, 90, 200),
                R.drawable.planeta_nectaris
            ),
            Planet(
                "Obsidia",
                15000f,
                900000,
                Color.rgb(35, 30, 40),
                R.drawable.planeta_obsidia
            )
        )


        private val missionDefs: List<MissionDef> by lazy {
            listOf(
                MissionDef(
                    "Wynieś satelitę",
                    5000,
                    "Wykonaj pierwszy start rakiety"
                ) {
                    totalLaunches >= 1
                },

                MissionDef(
                    "Okrąż Księżyc",
                    12000,
                    "Wyląduj na Księżycu"
                ) {
                    landedOn.contains(0)
                },

                MissionDef(
                    "Lądowanie na Marsie",
                    25000,
                    "Rozbuduj bazę Księżycową i wyląduj na Marsie"
                ) {
                    landedOn.contains(1)
                },

                MissionDef(
                    "Zbadaj chmury Wenus",
                    22000,
                    "Wyląduj na Wenus"
                ) {
                    landedOn.contains(2)
                },

                MissionDef(
                    "Wydobądź surowce z asteroidy",
                    18000,
                    "Przeleć przez pas asteroid — wykonaj 3 starty"
                ) {
                    totalLaunches >= 3
                },

                MissionDef(
                    "Dolot do Jowisza",
                    45000,
                    "Wyląduj na Jowiszu"
                ) {
                    landedOn.contains(3)
                },

                MissionDef(
                    "Zbuduj stację orbitalną",
                    50000,
                    "Wyląduj na Jowiszu i Saturnie"
                ) {
                    landedOn.contains(3) && landedOn.contains(4)
                },

                MissionDef(
                    "Okrąż pierścienie Saturna",
                    90000,
                    "Wyląduj na Saturnie"
                ) {
                    landedOn.contains(4)
                },

                MissionDef(
                    "Dotrzyj do Urana",
                    150000,
                    "Wyląduj na Uranie"
                ) {
                    landedOn.contains(5)
                },

                MissionDef(
                    "Dotrzyj do Neptuna",
                    250000,
                    "Wyląduj na Neptunie"
                ) {
                    landedOn.contains(6)
                },

                MissionDef(
                    "Odkryj Aurelię",
                    320000,
                    "Wyląduj na Aurelii"
                ) {
                    landedOn.contains(7)
                },

                MissionDef(
                    "Zbadaj wulkany Ignary",
                    420000,
                    "Wyląduj na Ignarze"
                ) {
                    landedOn.contains(8)
                },

                MissionDef(
                    "Rozbij obóz na Cryonis",
                    540000,
                    "Wyląduj na Cryonis"
                ) {
                    landedOn.contains(9)
                },

                MissionDef(
                    "Okrąż pierścienie Nectaris",
                    700000,
                    "Wyląduj na Nectaris"
                ) {
                    landedOn.contains(10)
                },

                MissionDef(
                    "Dotrzyj do Obsidii",
                    900000,
                    "Wyląduj na Obsidii"
                ) {
                    landedOn.contains(11)
                }
            )
        }

        private val stars = Array(90) {
            PointF(
                Math.random().toFloat(),
                Math.random().toFloat()
            )
        }

        private val p = Paint(Paint.ANTI_ALIAS_FLAG)

        private var rocketBmp: Bitmap? = null
        private var baseBmp: Bitmap? = null
        private var asteroidBmp: Bitmap? = null
        private var colonistsBmp: Bitmap? = null
        private var towerBmp: Bitmap? = null
        private var smokeBmp: Bitmap? = null

        private val planetBmps = mutableListOf<Bitmap>()

        private val hitRegions =
            mutableListOf<Pair<RectF, () -> Unit>>()

        private val thrustRegion = RectF()


        private val puffs = mutableListOf<Puff>()

        private fun drawableId(name: String, fallback: Int): Int {
            val id = resources.getIdentifier(name, "drawable", packageName)
            return if (id != 0) id else fallback
        }

        private fun rocketResId(level: Int): Int {
            val rocket = RocketDatabase.getRocket(level)
            val fallback = when (level) {
                1 -> R.drawable.rocket_lvl1
                2 -> R.drawable.rocket_lvl2
                3 -> R.drawable.rocket_lvl3
                4 -> R.drawable.rocket_lvl4
                5 -> R.drawable.rocket_lvl5
                else -> R.drawable.rocket_lvl6
            }
            return drawableId(rocket.imageName, fallback)
        }

        private fun planetResId(index: Int, fallback: Int): Int =
            drawableId(
                when (index) {
                    0 -> "planet_moon"
                    1 -> "planet_mars"
                    2 -> "planet_venus"
                    3 -> "planet_jupiter"
                    4 -> "planet_saturn"
                    5 -> "planet_uran"
                    6 -> "planet_neptune"
                    7 -> "planet_aurelia"
                    8 -> "planet_ignara"
                    9 -> "planet_cryonis"
                    10 -> "planet_nectaris"
                    else -> "planet_obsidia"
                },
                fallback
            )

        private fun baseResId(planetIndex: Int): Int =
            when (planetIndex) {
                0 -> drawableId("moon_base", R.drawable.dome_colony_base)
                1 -> drawableId("mars_base", R.drawable.mars_colony_base)
                else -> drawableId("orbital_station", R.drawable.ring_station_base)
            }

        private val prefs =
            context.getSharedPreferences(
                "kosmiczna_save",
                Context.MODE_PRIVATE
            )

        fun saveState() {
            prefs.edit()
                .putInt("credits", credits)
                .putInt("rocketLevel", rocketLevel)
                .putInt("selectedPlanet", selectedPlanet)
                .putInt("totalLaunches", totalLaunches)
                .putBoolean("peopleUnlocked", peopleUnlocked)
                .putInt("selectedCrew", selectedCrew)
                .putInt("habitatLevel", habitatLevel)
                .putInt("powerLevel", powerLevel)
                .putInt("researchBaseLevel", researchBaseLevel)
                .putInt("storageLevel", storageLevel)
                .putInt("researchPoints", researchPoints)
                .putInt("fuelTech", fuelTech)
                .putInt("heatTech", heatTech)
                .putInt("navigationTech", navigationTech)
                .putInt("landingTech", landingTech)
                .putInt("cargoTech", cargoTech)
                .putStringSet(
                    "landedOn",
                    landedOn.map { it.toString() }.toSet()
                )
                .putStringSet(
                    "claimedMissions",
                    claimedMissions.map { it.toString() }.toSet()
                )
                .apply()
        }

        private fun loadState() {
            credits = prefs.getInt("credits", credits)

            rocketLevel =
                prefs.getInt("rocketLevel", rocketLevel)

            selectedPlanet =
                prefs.getInt(
                    "selectedPlanet",
                    selectedPlanet
                ).coerceIn(
                    0,
                    planets.size - 1
                )

            totalLaunches =
                prefs.getInt(
                    "totalLaunches",
                    totalLaunches
                )

            habitatLevel = prefs.getInt("habitatLevel", habitatLevel).coerceIn(0, 3)
            powerLevel = prefs.getInt("powerLevel", powerLevel).coerceIn(0, 3)
            researchBaseLevel = prefs.getInt("researchBaseLevel", researchBaseLevel).coerceIn(0, 3)
            storageLevel = prefs.getInt("storageLevel", storageLevel).coerceIn(0, 3)
            researchPoints = prefs.getInt("researchPoints", 0)
            fuelTech = prefs.getInt("fuelTech", 0).coerceIn(0, 5)
            heatTech = prefs.getInt("heatTech", 0).coerceIn(0, 5)
            navigationTech = prefs.getInt("navigationTech", 0).coerceIn(0, 5)
            landingTech = prefs.getInt("landingTech", 0).coerceIn(0, 5)
            cargoTech = prefs.getInt("cargoTech", 0).coerceIn(0, 5)

            prefs.getStringSet(
                "landedOn",
                null
            )?.let { set ->
                landedOn.clear()
                landedOn.addAll(
                    set.mapNotNull {
                        it.toIntOrNull()
                    }
                )
            }

            peopleUnlocked =
                prefs.getBoolean("peopleUnlocked", false)

            selectedCrew =
                prefs.getInt("selectedCrew", 0).coerceIn(0, 3)

            prefs.getStringSet(
                "claimedMissions",
                null
            )?.let { set ->
                claimedMissions.clear()
                claimedMissions.addAll(
                    set.mapNotNull {
                        it.toIntOrNull()
                    }
                )
            }
        }

        init {
            isFocusable = true

            loadState()

            rocketBmp =
                BitmapFactory.decodeResource(
                    resources,
                    rocketResId(rocketLevel)
                )

            asteroidBmp =
                BitmapFactory.decodeResource(
                    resources,
                    R.drawable.asteroida
                )

            colonistsBmp =
                BitmapFactory.decodeResource(
                    resources,
                    drawableId("colonists", R.drawable.kolonizatorzy)
                )

            towerBmp =
                BitmapFactory.decodeResource(
                    resources,
                    R.drawable.wieza_startowa
                )

            smokeBmp =
                BitmapFactory.decodeResource(
                    resources,
                    R.drawable.smoke_puff
                )

            for (pl in planets) {
                planetBmps.add(
                    BitmapFactory.decodeResource(
                        resources,
                        planetResId(planets.indexOf(pl), pl.drawableRes)
                    )
                )
            }
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            saveState()
        }

        private fun startFlight() {

            /*
             * KSIĘŻYC jest pierwszym obowiązkowym celem.
             * Dopiero po prawidłowym lądowaniu na Księżycu
             * można wybierać dalsze planety.
             */
            if (!isPlanetUnlocked(selectedPlanet)) {
                selectedPlanet = 0
            }

            val crewCapacity = when {
                !peopleUnlocked -> 0
                rocketLevel >= 5 -> 3
                rocketLevel >= 3 -> 2
                else -> 1
            }
            selectedCrew = selectedCrew.coerceIn(0, crewCapacity)

            fuel = 100f
            altitude = 0f
            speed = 0f
            verticalSpeed = 0f
            temperature = 20f
            integrity = 100f
            flightTime = 0f
            thrust = false

            phase = "flight"
            screen = "game"

            launchStart =
                System.currentTimeMillis()

            launchState = 0
            launchShake = 0f

            puffs.clear()

            totalLaunches += 1

            saveState()
        }

        override fun onDraw(c: Canvas) {
            super.onDraw(c)

            val now =
                System.currentTimeMillis()

            val dt =
                (now - last) / 1000f

            last = now

            if (screen == "game") {
                update(dt)
            }

            hitRegions.clear()

            drawSpace(c)

            when (screen) {
                "menu" -> menu(c)
                "rockets" -> rockets(c)
                "planets" -> planetsScreen(c)
                "upgrade" -> upgrade(c)
                "research" -> research(c)
                "missions" -> missions(c)
                "crew" -> crew(c)
                "base" -> base(c)
                "game" -> game(c)
            }

            postInvalidateDelayed(40)
        }

        private fun update(dt: Float) {

            /*
             * SEKWENCJA STARTOWA
             *
             * 0–3 s    odliczanie
             * 3–4.2 s  zapłon
             * 4.2–5.6  start
             * >5.6     właściwy lot
             */
            if (
                phase == "flight" &&
                launchState < 3
            ) {

                val elapsed =
                    System.currentTimeMillis() -
                            launchStart

                launchState =
                    when {
                        elapsed < 3000L -> 0
                        elapsed < 4200L -> 1
                        elapsed < 5600L -> 2
                        else -> 3
                    }

                launchShake =
                    when (launchState) {
                        1 -> 1.5f
                        2 -> 4f
                        else -> 0f
                    }

                if (launchState < 3) {

                    thrust =
                        launchState >= 1

                    if (
                        launchState >= 1 &&
                        fuel > 0f
                    ) {
                        fuel =
                            max(
                                0f,
                                fuel - 5f * dt
                            )
                    }

                    if (launchState == 2) {

                        speed =
                            min(
                                420f,
                                speed + 260f * dt
                            )

                        altitude +=
                            speed * dt / 100f
                    }

                    if (launchState == 0) {
                        thrust = false
                    }

                    for (s in stars) {
                        s.y += 0.0006f

                        if (s.y > 1f) {
                            s.y -= 1f
                        }
                    }

                    if (
                        launchState >= 1 &&
                        puffs.size < 70
                    ) {

                        val groundY =
                            rocketNozzleY(
                                height * .62f -
                                        altitude * 1.8f,
                                1.5f
                            )

                        repeat(3) {

                            puffs.add(
                                Puff(
                                    x =
                                        width / 2f +
                                                (
                                                    Math.random()
                                                        .toFloat() -
                                                            0.5f
                                                ) * 70f,

                                    y =
                                        groundY + 8f,

                                    vx =
                                        (
                                            Math.random()
                                                .toFloat() -
                                                    0.5f
                                        ) * 45f,

                                    vy =
                                        -(
                                            20f +
                                                    Math.random()
                                                        .toFloat() *
                                                    35f
                                        ),

                                    age = 0f,

                                    life =
                                        1.0f +
                                                Math.random()
                                                    .toFloat() *
                                                0.7f,

                                    scale =
                                        0.55f +
                                                Math.random()
                                                    .toFloat() *
                                                0.9f
                                )
                            )
                        }
                    }

                    val pit =
                        puffs.iterator()

                    while (pit.hasNext()) {

                        val pf = pit.next()

                        pf.age += dt
                        pf.x += pf.vx * dt
                        pf.y += pf.vy * dt
                        pf.vy += 30f * dt

                        if (pf.age >= pf.life) {
                            pit.remove()
                        }
                    }

                    return
                }

                /*
                 * Wieża została zwolniona.
                 * Od tego momentu gracz przejmuje sterowanie.
                 */
                thrust = false
            }

            if (phase != "flight") {

                if (phase == "landing") {

                    // KONTROLOWANE LĄDOWANIE: landingVelocity oznacza
                    // prędkość OPADANIA w m/s. Silnik ją zmniejsza,
                    // grawitacja zwiększa. Wysokość maleje aż do kontaktu.
                    val gravity = 72f

                    if (thrust && fuel > 0f && integrity > 0f) {
                        val crewFactor = 1f + selectedCrew * 0.015f
                        fuel = max(
                            0f,
                            fuel - (4.5f * crewFactor - fuelTech * 0.25f) * dt
                        )
                        landingVelocity = max(
                            0f,
                            landingVelocity - (230f + landingTech * 28f + powerLevel * 8f) * dt
                        )
                        temperature = min(1200f, temperature + 95f * dt)
                    } else {
                        landingVelocity = min(180f, landingVelocity + gravity * dt)
                        temperature = max(20f, temperature - 80f * dt)
                    }

                    if (temperature > 900f) {
                        integrity = max(0f, integrity - (temperature - 900f) * 0.01f * dt)
                    }

                    altitude = max(0f, altitude - landingVelocity * dt / 100f)
                    speed = landingVelocity

                    // Pomyślne lądowanie: kontakt z powierzchnią przy
                    // bezpiecznej prędkości pionowej.
                    if (altitude <= 0f) {
                        altitude = 0f

                        if (abs(landingVelocity) <= 115f && integrity > 0f) {
                            phase = "done"
                            credits += planets[selectedPlanet].reward
                            landedOn.add(selectedPlanet)
                            researchPoints += 2 + selectedPlanet
                            if (selectedPlanet == 0) {
                                peopleUnlocked = true
                                selectedCrew = 0
                            }
                            baseBmp = BitmapFactory.decodeResource(
                                resources,
                                baseResId(selectedPlanet)
                            )
                            saveState()
                        } else {
                            integrity = 0f
                            phase = "failed"
                            thrust = false
                        }
                    } else if (integrity <= 0f || (System.currentTimeMillis() - landingStart) > landingDuration) {
                        phase = "failed"
                        thrust = false
                    }
                }

                return
            }

            flightTime += dt

            for (s in stars) {

                s.y +=
                    speed *
                            dt *
                            0.00025f

                if (s.y > 1f) {
                    s.y -= 1f
                }
            }

            // Fizyka właściwego lotu: ciąg konkuruje z grawitacją,
            // a puszczenie silnika pozwala rakiecie wyhamować.
            val burn =
                max(
                    3.2f,
                    8f -
                            (rocketLevel - 1) * 0.9f -
                            fuelTech * 0.65f
                )

            val thrustPow =
                420f +
                        (rocketLevel - 1) * 70f +
                        navigationTech * 18f +
                        powerLevel * 12f

            val maxSpd =
                2400f +
                        (rocketLevel - 1) * 180f +
                        navigationTech * 120f

            val gravity =
                150f -
                        (rocketLevel - 1) *
                        8f

            if (
                thrust &&
                fuel > 0f &&
                integrity > 0f
            ) {

                val crewFactor = 1f + selectedCrew * 0.0125f
                fuel =
                    max(
                        0f,
                        fuel - burn * crewFactor * dt
                    )

                verticalSpeed =
                    min(
                        maxSpd,
                        verticalSpeed +
                                (thrustPow - gravity) *
                                dt
                    )

                // Temperatura rośnie przy pracującym silniku.
                temperature =
                    min(
                        1200f,
                        temperature +
                                max(
                                    70f,
                                    (170f + rocketLevel * 8f) -
                                            heatTech * 18f -
                                            powerLevel * 5f
                                ) * dt
                    )

            } else {

                verticalSpeed =
                    max(
                        0f,
                        verticalSpeed -
                                gravity * dt
                    )

                // Po wyłączeniu silnika układ się chłodzi.
                temperature =
                    max(
                        20f,
                        temperature -
                                115f * dt
                    )
            }

            // Prędkość HUD korzysta z tej samej wartości co prędkość pionowa.
            speed = verticalSpeed

            // Przegrzanie powyżej 900°C uszkadza rakietę.
            if (temperature > 900f) {
                integrity =
                    max(
                        0f,
                        integrity -
                                (temperature - 900f) *
                                max(0.003f, 0.012f - heatTech * 0.0018f) * dt
                    )
            }

            altitude +=
                verticalSpeed *
                        dt /
                        100f

            val target =
                planets[selectedPlanet]

            /*
             * Pył przy stanowisku startowym.
             */
            val liftoffZoneKm =
                max(
                    4f,
                    target.targetAltitude *
                            0.05f
                )

            if (
                thrust &&
                fuel > 0f &&
                altitude < liftoffZoneKm &&
                puffs.size < 70
            ) {

                val groundY =
                    rocketNozzleY(
                        height * .62f,
                        1.5f
                    )

                repeat(2) {

                    puffs.add(
                        Puff(
                            x =
                                width / 2f +
                                        (
                                            Math.random()
                                                .toFloat() -
                                                0.5f
                                        ) * 50f,

                            y =
                                groundY - 6f,

                            vx =
                                (
                                    Math.random()
                                        .toFloat() -
                                        0.5f
                                ) * 34f,

                            vy =
                                -(
                                    18f +
                                            Math.random()
                                                .toFloat() *
                                            28f
                                ),

                            age = 0f,

                            life =
                                0.9f +
                                        Math.random()
                                            .toFloat() *
                                        0.5f,

                            scale =
                                0.45f +
                                        Math.random()
                                            .toFloat() *
                                        0.85f
                        )
                    )
                }
            }

            val pit =
                puffs.iterator()

            while (pit.hasNext()) {

                val pf = pit.next()

                pf.age += dt
                pf.x += pf.vx * dt
                pf.y += pf.vy * dt
                pf.vy += 30f * dt

                if (pf.age >= pf.life) {
                    pit.remove()
                }
            }

            if (
                altitude >=
                        target.targetAltitude
            ) {

                phase = "landing"
                landingStart = System.currentTimeMillis()
                landingFuelStart = fuel
                // Początkowa prędkość opadania po wejściu w atmosferę celu.
                landingVelocity = 78f
                thrust = false

            } else if (
                integrity <= 0f
            ) {

                phase = "failed"

                thrust = false
            } else if (
                fuel <= 0f &&
                speed <= 0f
            ) {

                phase = "failed"

                thrust = false
            }
        }

        private fun drawSpace(c: Canvas) {

            c.drawColor(
                Color.rgb(
                    2,
                    6,
                    16
                )
            )

            p.color = Color.WHITE

            for (s in stars) {

                c.drawCircle(
                    s.x * width,
                    s.y * height,
                    1.2f,
                    p
                )
            }

            p.shader =
                LinearGradient(
                    0f,
                    height * .65f,
                    0f,
                    height.toFloat(),
                    Color.TRANSPARENT,
                    Color.rgb(
                        3,
                        20,
                        42
                    ),
                    Shader.TileMode.CLAMP
                )

            c.drawRect(
                0f,
                height * .6f,
                width.toFloat(),
                height.toFloat(),
                p
            )

            p.shader = null
        }

        private fun text(
            c: Canvas,
            s: String,
            x: Float,
            y: Float,
            size: Float,
            bold: Boolean = false,
            color: Int = Color.WHITE
        ) {

            p.color = color
            p.textSize = size

            p.typeface =
                Typeface.create(
                    "sans",
                    if (bold)
                        Typeface.BOLD
                    else
                        Typeface.NORMAL
                )

            c.drawText(
                s,
                x,
                y,
                p
            )
        }

        private fun button(
            c: Canvas,
            s: String,
            x: Float,
            y: Float,
            w: Float,
            h: Float,
            enabled: Boolean = true,
            onTap: (() -> Unit)? = null
        ) {

            p.color =
                if (enabled)
                    Color.rgb(
                        18,
                        78,
                        92
                    )
                else
                    Color.rgb(
                        25,
                        28,
                        38
                    )

            c.drawRoundRect(
                x,
                y,
                x + w,
                y + h,
                22f,
                22f,
                p
            )

            p.style =
                Paint.Style.STROKE

            p.strokeWidth = 2f

            p.color =
                if (enabled)
                    Color.rgb(
                        75,
                        210,
                        235
                    )
                else
                    Color.DKGRAY

            c.drawRoundRect(
                x,
                y,
                x + w,
                y + h,
                22f,
                22f,
                p
            )

            p.style =
                Paint.Style.FILL

            text(
                c,
                s,
                x +
                        w / 2 -
                        p.measureText(s) / 2,
                y + h * .64f,
                24f,
                true,
                Color.WHITE
            )

            if (
                onTap != null &&
                enabled
            ) {

                hitRegions.add(
                    RectF(
                        x,
                        y,
                        x + w,
                        y + h
                    ) to onTap
                )
            }
        }

        private fun title(
            c: Canvas,
            s: String
        ) =
            text(
                c,
                s,
                28f,
                54f,
                30f,
                true,
                Color.rgb(
                    180,
                    240,
                    255
                )
            )

        private fun backButton(
            c: Canvas,
            s: String
        ) {

            title(c, s)

            button(
                c,
                "‹",
                18f,
                70f,
                52f,
                44f
            ) {
                screen = "menu"
            }
        }

        private fun creditsBadge(
            c: Canvas
        ) =
            text(
                c,
                "credits: $credits",
                width - 260f,
                54f,
                22f,
                true,
                Color.rgb(
                    255,
                    210,
                    90
                )
            )

        private fun drawRocket(
            c: Canvas,
            x: Float,
            y: Float,
            scale: Float
        ) {

            val bmp =
                rocketBmp ?: return

            val targetW =
                76f * scale

            val targetH =
                targetW *
                        bmp.height /
                        bmp.width

            val top =
                y -
                        targetH *
                        0.543f

            c.drawBitmap(
                bmp,
                null,
                RectF(
                    x -
                            targetW /
                            2f,

                    top,

                    x +
                            targetW /
                            2f,

                    top +
                            targetH
                ),
                p
            )
        }

        private fun rocketNozzleY(
            y: Float,
            scale: Float
        ): Float {

            val bmp =
                rocketBmp ?: return y

            val targetW =
                76f * scale

            val targetH =
                targetW *
                        bmp.height /
                        bmp.width

            val top =
                y -
                        targetH *
                        0.543f

            return top +
                    targetH
        }

        private fun drawFlame(
            c: Canvas,
            cx: Float,
            baseY: Float,
            scale: Float
        ) {

            val t =
                System.currentTimeMillis()

            val flicker =
                (
                    sin(
                        t /
                                55.0
                    ) *
                            0.5 +
                            0.5
                ).toFloat()

            val jitter =
                (
                    sin(
                        t /
                                17.0
                    ) *
                            0.5 +
                            0.5
                ).toFloat()

            val sway =
                (
                    sin(
                        t /
                                90.0
                    ) *
                            3.5
                ).toFloat() *
                        scale

            val len =
                (
                    46f +
                            flicker *
                            24f +
                            jitter *
                            8f
                ) * scale

            val w1 =
                15f * scale

            fun flamePath(
                width: Float,
                length: Float
            ): Path {

                val path =
                    Path()

                path.moveTo(
                    cx,
                    baseY
                )

                path.quadTo(
                    cx -
                            width *
                            1.1f +
                            sway,

                    baseY +
                            length *
                            0.32f,

                    cx -
                            width *
                            0.5f +
                            sway,

                    baseY +
                            length *
                            0.68f
                )

                path.quadTo(
                    cx -
                            width *
                            0.16f +
                            sway,

                    baseY +
                            length *
                            0.9f,

                    cx,
                    baseY +
                            length
                )

                path.quadTo(
                    cx +
                            width *
                            0.16f +
                            sway,

                    baseY +
                            length *
                            0.9f,

                    cx +
                            width *
                            0.5f +
                            sway,

                    baseY +
                            length *
                            0.68f
                )

                path.quadTo(
                    cx +
                            width *
                            1.1f +
                            sway,

                    baseY +
                            length *
                            0.32f,

                    cx,
                    baseY
                )

                path.close()

                return path
            }

            p.color =
                Color.argb(
                    210,
                    255,
                    130,
                    20
                )

            c.drawPath(
                flamePath(
                    w1,
                    len
                ),
                p
            )

            p.color =
                Color.argb(
                    225,
                    255,
                    180,
                    60
                )

            c.drawPath(
                flamePath(
                    w1 * 0.62f,
                    len * 0.72f
                ),
                p
            )

            p.color =
                Color.argb(
                    235,
                    255,
                    235,
                    120
                )

            c.drawPath(
                flamePath(
                    w1 * 0.3f,
                    len * 0.42f
                ),
                p
            )
        }

        private fun drawBaseBitmap(
            c: Canvas,
            cx: Float,
            bottomY: Float,
            targetW: Float
        ) {

            val bmp =
                baseBmp ?: return

            val targetH =
                targetW *
                        bmp.height /
                        bmp.width

            val left =
                cx -
                        targetW /
                        2f

            val top =
                bottomY -
                        targetH

            c.drawBitmap(
                bmp,
                null,
                RectF(
                    left,
                    top,
                    left +
                            targetW,
                    top +
                            targetH
                ),
                p
            )
        }

        private fun hasGroundColony(
            planetIndex: Int
        ): Boolean =
            baseResId(
                planetIndex
            ) !=
                    R.drawable.ring_station_base

        private fun drawColonists(
            c: Canvas,
            cx: Float,
            bottomY: Float,
            targetW: Float
        ) {

            val bmp =
                colonistsBmp ?: return

            val targetH =
                targetW *
                        bmp.height /
                        bmp.width

            val left =
                cx -
                        targetW /
                        2f

            val top =
                bottomY -
                        targetH

            c.drawBitmap(
                bmp,
                null,
                RectF(
                    left,
                    top,
                    left +
                            targetW,
                    top +
                            targetH
                ),
                p
            )
        }

        private fun drawLaunchTower(
            c: Canvas,
            prog: Float,
            rocketY: Float
        ) {

            val bmp =
                towerBmp ?: return

            val fadeZone =
                0.12f

            if (prog >= fadeZone) {
                return
            }

            val alpha =
                (
                    (
                        1f -
                                prog /
                                fadeZone
                    ) *
                            255
                ).toInt()
                    .coerceIn(
                        0,
                        255
                    )

            val targetW =
                width * 0.34f

            val targetH =
                targetW *
                        bmp.height /
                        bmp.width

            val groundY =
                rocketNozzleY(
                    rocketY,
                    1.5f
                ) + 24f

            val left =
                width * 0.08f

            val top =
                groundY -
                        targetH

            p.alpha =
                alpha

            c.drawBitmap(
                bmp,
                null,
                RectF(
                    left,
                    top,
                    left +
                            targetW,
                    top +
                            targetH
                ),
                p
            )

            p.alpha = 255
        }

        private fun drawPuffs(
            c: Canvas
        ) {

            val bmp =
                smokeBmp ?: return

            for (pf in puffs) {

                val t =
                    (
                        pf.age /
                                pf.life
                    ).coerceIn(
                        0f,
                        1f
                    )

                val alpha =
                    (
                        (
                            1f - t
                        ) *
                                150
                    ).toInt()
                        .coerceIn(
                            0,
                            150
                        )

                if (alpha <= 0) {
                    continue
                }

                val s =
                    (
                        34f +
                                t *
                                40f
                    ) *
                            pf.scale

                p.alpha =
                    alpha

                c.drawBitmap(
                    bmp,
                    null,
                    RectF(
                        pf.x -
                                s /
                                2f,

                        pf.y -
                                s /
                                2f,

                        pf.x +
                                s /
                                2f,

                        pf.y +
                                s /
                                2f
                    ),
                    p
                )
            }

            p.alpha = 255
        }

        private fun drawEarthLaunchScene(
            c: Canvas,
            shakeX: Float,
            rocketY: Float
        ) {

            /*
             * ZIEMIA — duży półokrąg.
             * Kamera jest ustawiona nad powierzchnią.
             */

            val earthR =
                width * 0.82f

            val cx =
                width / 2f

            val cy =
                height * 0.93f

            p.color =
                Color.rgb(
                    18,
                    74,
                    125
                )

            c.drawCircle(
                cx,
                cy,
                earthR,
                p
            )

            /*
             * Kontynenty.
             */

            p.color =
                Color.rgb(
                    35,
                    120,
                    75
                )

            c.drawOval(
                RectF(
                    cx -
                            earthR *
                            .82f,

                    cy -
                            earthR *
                            .42f,

                    cx -
                            earthR *
                            .12f,

                    cy +
                            earthR *
                            .10f
                ),
                p
            )

            c.drawOval(
                RectF(
                    cx +
                            earthR *
                            .08f,

                    cy -
                            earthR *
                            .36f,

                    cx +
                            earthR *
                            .72f,

                    cy +
                            earthR *
                            .12f
                ),
                p
            )

            p.color =
                Color.argb(
                    90,
                    255,
                    255,
                    255
                )

            c.drawArc(
                RectF(
                    cx -
                            earthR,

                    cy -
                            earthR,

                    cx +
                            earthR,

                    cy +
                            earthR
                ),
                180f,
                180f,
                false,
                p
            )

            /*
             * Platforma pod rakietą.
             */

            p.color =
                Color.rgb(
                    55,
                    60,
                    70
                )

            val nozzle =
                rocketNozzleY(
                    rocketY,
                    1.5f
                )

            c.drawRect(
                cx -
                        125f +
                        shakeX,

                nozzle + 15f,

                cx +
                        125f +
                        shakeX,

                nozzle + 35f,

                p
            )

            text(
                c,
                "ZIEMIA • STANOWISKO STARTOWE",
                24f,
                height - 32f,
                15f,
                true,
                Color.rgb(
                    180,
                    230,
                    255
                )
            )
        }

        private fun menu(
            c: Canvas
        ) {

            text(
                c,
                "KOSMICZNA",
                28f,
                110f,
                42f,
                true
            )

            text(
                c,
                "PRZYGODA",
                28f,
                152f,
                42f,
                true,
                Color.rgb(
                    70,
                    210,
                    245
                )
            )

            text(
                c,
                "WERSJA 3.00",
                30f,
                180f,
                14f,
                false,
                Color.LTGRAY
            )

            creditsBadge(c)

            button(
                c,
                "GRAJ",
                width - 210f,
                230f,
                180f,
                64f
            ) {
                if (peopleUnlocked) {
                    selectedCrew = selectedCrew.coerceAtMost(3)
                    screen = "crew"
                } else {
                    startFlight()
                }
            }

            if (peopleUnlocked) {
                button(
                    c,
                    "BAZA KSIĘŻYCOWA",
                    width - 210f,
                    310f,
                    180f,
                    52f
                ) {
                    screen = "base"
                }
            }

            button(
                c,
                "RAKIETY",
                width - 210f,
                if (peopleUnlocked) 374f else 310f,
                180f,
                52f
            ) {
                screen = "rockets"
            }

            button(
                c,
                "PLANETY",
                width - 210f,
                if (peopleUnlocked) 438f else 374f,
                180f,
                52f
            ) {
                screen = "planets"
            }

            button(
                c,
                "ULEPSZENIA",
                width - 210f,
                if (peopleUnlocked) 502f else 438f,
                180f,
                52f
            ) {
                screen = "upgrade"
            }

            button(
                c,
                "BADANIA",
                width - 210f,
                if (peopleUnlocked) 566f else 502f,
                180f,
                52f
            ) {
                screen = "research"
            }

            button(
                c,
                "MISJE",
                width - 210f,
                if (peopleUnlocked) 630f else 566f,
                180f,
                52f
            ) {
                screen = "missions"
            }

            text(
                c,
                "EXPLORE  •  RESEARCH  •  UPGRADE  •  COLONIZE",
                28f,
                height - 32f,
                14f,
                false,
                Color.GRAY
            )

            drawRocket(
                c,
                width * .28f,
                height * .52f,
                1.6f
            )
        }


        private fun base(
            c: Canvas
        ) {
            backButton(c, "BAZA KSIĘŻYCOWA")

            text(c, "BAZA KSIĘŻYCOWA", 28f, 145f, 30f, true)
            text(c, "PROGRES: ${moonBaseLevel()} / 12", width - 170f, 145f, 14f, true, Color.rgb(90, 220, 255))
            text(
                c,
                "Rozwijaj osadę po każdym udanym lądowaniu.",
                28f, 178f, 16f, false, Color.LTGRAY
            )
            text(
                c,
                if (moonBaseLevel() >= 4) "MARS: ODBLOKOWANY" else "MARS: rozbuduj bazę do 4 poziomów",
                28f, 198f, 14f, true,
                if (moonBaseLevel() >= 4) Color.rgb(100, 230, 140) else Color.rgb(255, 180, 90)
            )

            data class BaseModule(
                val name: String,
                val level: Int,
                val setLevel: (Int) -> Unit
            )

            val modules = listOf(
                BaseModule("MODUŁ MIESZKALNY", habitatLevel) { habitatLevel = it },
                BaseModule("MODUŁ ENERGETYCZNY", powerLevel) { powerLevel = it },
                BaseModule("LABORATORIUM BADAWCZE", researchBaseLevel) { researchBaseLevel = it },
                BaseModule("MAGAZYN", storageLevel) { storageLevel = it }
            )

            modules.forEachIndexed { index, module ->
                val y = 230f + index * 108f
                val cost = 350 * (module.level + 1) * (1 + moonBaseLevel() / 6)

                p.color = Color.argb(145, 10, 20, 38)
                c.drawRoundRect(
                    20f, y, width - 20f, y + 92f,
                    18f, 18f, p
                )

                text(c, module.name, 38f, y + 29f, 17f, true, Color.WHITE)
                text(
                    c,
                    "Poziom ${module.level} / 3",
                    38f, y + 55f, 15f, false, Color.LTGRAY
                )

                if (module.level < 3) {
                    button(
                        c,
                        "ROZBUDUJ • $cost CR",
                        width - 190f, y + 19f, 160f, 52f,
                        enabled = credits >= cost
                    ) {
                        if (credits >= cost && module.level < 3) {
                            credits -= cost
                            module.setLevel(module.level + 1)
                            saveState()
                        }
                    }
                } else {
                    text(
                        c, "MAX", width - 90f, y + 51f,
                        17f, true, Color.rgb(100, 220, 140)
                    )
                }
            }

            p.color = Color.argb(180, 5, 10, 20)
            c.drawRoundRect(
                20f, height - 130f, width - 20f, height - 25f,
                18f, 18f, p
            )

            text(c, "BONUS BAZY", 38f, height - 98f, 16f, true, Color.WHITE)
            text(
                c,
                "Załoga +${habitatLevel * 2}%   Energia +${powerLevel * 3}%   " +
                        "Badania +${researchBaseLevel * 5}%   Magazyn +${storageLevel * 10}%  Ładunek +${cargoTech * 4}%",
                38f, height - 66f, 13f, false, Color.LTGRAY
            )
        }

        private fun crew(
            c: Canvas
        ) {
            backButton(c, "ZAŁOGA")

            text(
                c,
                "MISJA Z ZAŁOGĄ",
                28f,
                150f,
                30f,
                true
            )

            text(
                c,
                "Pierwszy lot ludzi jest możliwy dopiero",
                28f,
                190f,
                17f,
                false,
                Color.LTGRAY
            )
            text(
                c,
                "po udanym lądowaniu na Księżycu.",
                28f,
                216f,
                17f,
                false,
                Color.LTGRAY
            )

            val capacity = when {
                !peopleUnlocked -> 0
                rocketLevel >= 5 -> 3
                rocketLevel >= 3 -> 2
                else -> 1
            }

            text(
                c,
                "MIEJSCA: $selectedCrew / $capacity",
                28f,
                270f,
                22f,
                true,
                Color.rgb(70, 210, 245)
            )

            val names = crewNames.take(capacity)
            names.forEachIndexed { index, name ->
                val y = 310f + index * 70f
                button(
                    c,
                    if (index < selectedCrew) "✓  $name" else "○  $name",
                    28f,
                    y,
                    width - 56f,
                    54f
                ) {
                    selectedCrew =
                        if (index < selectedCrew) {
                            index
                        } else {
                            (index + 1).coerceAtMost(capacity)
                        }
                    saveState()
                }
            }

            val mass = selectedCrew * crewMass
            text(
                c,
                "Masa załogi: ${"%.0f".format(mass)} kg",
                28f,
                545f,
                18f,
                false,
                Color.LTGRAY
            )

            button(
                c,
                if (selectedCrew > 0) "ROZPOCZNIJ MISJĘ" else "WYBIERZ ZAŁOGĘ",
                28f,
                585f,
                width - 56f,
                64f,
                enabled = selectedCrew > 0
            ) {
                if (selectedCrew > 0) {
                    startFlight()
                }
            }

            button(
                c,
                "MISJA BEZ ZAŁOGI",
                28f,
                660f,
                width - 56f,
                54f
            ) {
                selectedCrew = 0
                startFlight()
            }
        }

        private fun rockets(
            c: Canvas
        ) {

            backButton(
                c,
                "RAKIETY"
            )

            text(
                c,
                "Pioneer I  •  Poziom $rocketLevel/6",
                28f,
                170f,
                28f,
                true
            )

            drawRocket(
                c,
                width / 2f,
                320f,
                1.4f
            )

            text(
                c,
                "Siła ciągu       ${
                    "#".repeat(rocketLevel)
                }${
                    "-".repeat(
                        6 -
                                rocketLevel
                    )
                }",
                28f,
                440f,
                18f,
                false,
                Color.rgb(
                    70,
                    210,
                    245
                )
            )

            text(
                c,
                "Zbiornik paliwa  ${
                    "#".repeat(rocketLevel)
                }${
                    "-".repeat(
                        6 -
                                rocketLevel
                    )
                }",
                28f,
                475f,
                18f,
                false,
                Color.rgb(
                    70,
                    210,
                    245
                )
            )

            text(
                c,
                "Idź do ULEPSZENIA, żeby podnieść poziom",
                28f,
                520f,
                16f,
                false,
                Color.GRAY
            )
        }

        private fun planetsScreen(
            c: Canvas
        ) {
            backButton(c, "PLANETY")
            creditsBadge(c)

            text(c, "MAPA GALAKTYKI", 24f, 126f, 26f, true)
            text(
                c,
                "Każdy świat wymaga poprzedniej kolonizacji.",
                24f, 151f, 14f, false, Color.LTGRAY
            )

            val cols = 2
            val cardW = (width - 54f) / 2f
            val cardH = 88f

            planets.forEachIndexed { i, planet ->
                val col = i % cols
                val row = i / cols
                val x = 18f + col * (cardW + 18f)
                val y = 170f + row * 96f

                if (y > height - 12f) return@forEachIndexed

                val unlocked = isPlanetUnlocked(i)
                val landed = landedOn.contains(i)

                p.color = if (unlocked)
                    Color.argb(155, 12, 28, 48)
                else
                    Color.argb(105, 12, 16, 28)

                c.drawRoundRect(x, y, x + cardW, y + cardH, 16f, 16f, p)

                planetBmps.getOrNull(i)?.let { bmp ->
                    c.drawBitmap(
                        bmp, null,
                        RectF(x + 8f, y + 10f, x + 62f, y + 64f),
                        p
                    )
                }

                text(
                    c,
                    planet.name,
                    x + 72f, y + 28f, 16f, true,
                    if (unlocked) Color.WHITE else Color.GRAY
                )
                text(
                    c,
                    "${planet.targetAltitude.toInt()} km • +${planet.reward}",
                    x + 72f, y + 50f, 12f, false, Color.LTGRAY
                )
                text(
                    c,
                    when {
                        landed -> "KOLONIA ✓"
                        unlocked -> "DOSTĘPNA"
                        i == 1 -> "BAZA KSIĘŻYCOWA 4 LVL"
                        else -> "POPRZEDNI ŚWIAT"
                    },
                    x + 72f, y + 71f, 11f, true,
                    when {
                        landed -> Color.rgb(100, 230, 140)
                        unlocked -> Color.rgb(90, 210, 245)
                        else -> Color.rgb(255, 175, 90)
                    }
                )

                if (unlocked) {
                    hitRegions.add(
                        Pair(RectF(x, y, x + cardW, y + cardH)) {
                            selectedPlanet = i
                            saveState()
                        }
                    )
                }
            }

            val selected = planets[selectedPlanet]
            text(
                c,
                "CEL: ${selected.name.uppercase()}",
                24f, height - 54f, 17f, true, Color.rgb(70, 210, 245)
            )
            text(
                c,
                if (isPlanetUnlocked(selectedPlanet))
                    "Gotowe do startu"
                else
                    "Wybierz odblokowany świat",
                24f, height - 30f, 13f, false, Color.LTGRAY
            )

            button(
                c,
                "START MISJI",
                width - 170f, height - 68f, 146f, 50f,
                enabled = isPlanetUnlocked(selectedPlanet)
            ) {
                if (isPlanetUnlocked(selectedPlanet)) {
                    if (peopleUnlocked) {
                        screen = "crew"
                    } else {
                        startFlight()
                    }
                }
            }
        }

        private fun upgrade(
            c: Canvas
        ) {

            backButton(
                c,
                "ULEPSZENIA"
            )

            creditsBadge(c)

            text(
                c,
                "ROZWÓJ RAKIETY  •  POZIOM $rocketLevel/6",
                28f,
                145f,
                22f,
                true
            )

            drawRocket(
                c,
                width * .78f,
                300f,
                1.2f
            )

            listOf(
                "SILNIKI",
                "ZBIORNIKI",
                "STEROWANIE",
                "OSŁONY"
            ).forEachIndexed {
                    i,
                    n
                ->

                text(
                    c,
                    "$n    Poziom ${
                        min(
                            rocketLevel +
                                    (3 - i) / 4,
                            6
                        )
                    }",
                    28f,
                    180f +
                            i *
                            62f,
                    18f
                )
            }

            val maxed =
                rocketLevel >= 6

            val cost =
                1000 *
                        rocketLevel

            if (maxed) {

                button(
                    c,
                    "MAKSYMALNY POZIOM",
                    width / 2 -
                            130f,
                    470f,
                    260f,
                    58f,
                    false
                )

            } else {

                button(
                    c,
                    "ULEPSZ  ->  $cost credits",
                    width / 2 -
                            150f,
                    470f,
                    300f,
                    58f,
                    credits >= cost
                ) {

                    if (credits >= cost) {

                        credits -= cost

                        rocketLevel += 1

                        rocketBmp =
                            BitmapFactory.decodeResource(
                                resources,
                                rocketResId(
                                    rocketLevel
                                )
                            )

                        saveState()
                    }
                }
            }
        }

        private fun research(
            c: Canvas
        ) {
            backButton(c, "BADANIA")
            creditsBadge(c)

            text(c, "DRZEWKO TECHNOLOGII", 24f, 128f, 25f, true)
            text(
                c,
                "Punkty nauki: $researchPoints",
                24f, 158f, 17f, true, Color.rgb(90, 220, 255)
            )

            data class Tech(
                val name: String,
                val level: Int,
                val desc: String,
                val set: (Int) -> Unit
            )

            val techs = listOf(
                Tech("EKOLOGICZNE PALIWO", fuelTech, "mniejsze zużycie paliwa") { fuelTech = it },
                Tech("OSŁONA TERMICZNA", heatTech, "mniejsze przegrzewanie") { heatTech = it },
                Tech("NAWIGACJA", navigationTech, "większy ciąg i prędkość") { navigationTech = it },
                Tech("STEROWANIE LĄDOWNIKIEM", landingTech, "mocniejsze hamowanie") { landingTech = it },
                Tech("LOGISTYKA ŁADUNKU", cargoTech, "większy bonus kolonii") { cargoTech = it }
            )

            techs.forEachIndexed { i, tech ->
                val y = 185f + i * 92f
                val cost = 2 + tech.level * 2

                p.color = Color.argb(145, 10, 20, 38)
                c.drawRoundRect(18f, y, width - 18f, y + 78f, 16f, 16f, p)

                text(c, tech.name, 32f, y + 25f, 16f, true)
                text(c, "Poziom ${tech.level}/5 • ${tech.desc}", 32f, y + 49f, 13f, false, Color.LTGRAY)

                if (tech.level < 5) {
                    button(
                        c,
                        "+1  $cost NAUKI",
                        width - 145f, y + 14f, 115f, 48f,
                        enabled = researchPoints >= cost
                    ) {
                        if (researchPoints >= cost && tech.level < 5) {
                            researchPoints -= cost
                            tech.set(tech.level + 1)
                            saveState()
                        }
                    }
                } else {
                    text(c, "MAX", width - 80f, y + 45f, 15f, true, Color.rgb(100, 230, 140))
                }
            }
        }

        private fun missions(
            c: Canvas
        ) {

            backButton(
                c,
                "MISJE"
            )

            creditsBadge(c)

            val scroll =
                min(
                    0f,
                    height * .8f -
                            (
                                150f +
                                        missionDefs.size *
                                        82f
                            )
                )

            missionDefs.forEachIndexed {
                    i,
                    m
                ->

                val y =
                    150f +
                            i *
                            82f +
                            scroll

                if (
                    y !in
                    -80f..(
                        height *
                                .82f
                    )
                ) {
                    return@forEachIndexed
                }

                val claimed =
                    claimedMissions
                        .contains(i)

                val done =
                    m.isDone()

                val label =
                    when {

                        claimed ->
                            "${m.name}   •   UKOŃCZONA ✓"

                        done ->
                            "${m.name}   •   ODBIERZ  +${m.reward}"

                        else ->
                            "${m.name}   •   +${m.reward} credits"
                    }

                button(
                    c,
                    label,
                    28f,
                    y,
                    width - 56f,
                    62f,
                    enabled =
                        done &&
                                !claimed
                ) {

                    /*
                     * Nagroda może zostać odebrana tylko raz.
                     */
                    if (
                        m.isDone() &&
                        !claimedMissions.contains(i)
                    ) {

                        credits +=
                            m.reward

                        claimedMissions.add(
                            i
                        )

                        saveState()
                    }
                }

                val sub =
                    if (claimed) {
                        "Nagroda odebrana"
                    } else if (done) {
                        "Warunek spełniony — dotknij, aby odebrać"
                    } else {
                        "Warunek: ${m.requirement}"
                    }

                text(
                    c,
                    sub,
                    40f,
                    y + 76f,
                    14f,
                    false,
                    if (claimed)
                        Color.rgb(
                            70,
                            220,
                            120
                        )
                    else if (done)
                        Color.rgb(
                            255,
                            210,
                            90
                        )
                    else
                        Color.GRAY
                )

                if (
                    m.name.contains(
                        "asteroidy"
                    )
                ) {

                    asteroidBmp
                        ?.let { bmp ->

                            c.drawBitmap(
                                bmp,
                                null,
                                RectF(
                                    width -
                                            100f,
                                    y + 6f,
                                    width -
                                            100f +
                                            50f,
                                    y + 6f +
                                            42f
                                ),
                                p
                            )
                        }
                }
            }
        }

        private fun game(
            c: Canvas
        ) {

            val target =
                planets[selectedPlanet]

            val worldHazard = when (selectedPlanet) {
                0 -> "PYŁ KSIĘŻYCOWY"
                1 -> "BURZE PYŁOWE"
                2 -> "TOKSYCZNA ATMOSFERA"
                3 -> "PROMIENIOWANIE"
                4 -> "PIERŚCIENIE I ZAKŁÓCENIA"
                5 -> "LODY I NISKIE TEMPERATURY"
                6 -> "WIATRY NADSONICZNE"
                7 -> "NIEZNANE ZJAWISKA"
                8 -> "AKTYWNOŚĆ WULKANICZNA"
                9 -> "KRYOGENICZNE BURZE"
                10 -> "PIERŚCIENIE NECTARIS"
                else -> "CZARNE BURZE OBSIDII"
            }

            /*
             * EKRAN LĄDOWANIA / KOLONII
             */

            if (
                phase == "landing" ||
                phase == "done"
            ) {

                val t =
                    min(
                        1f,
                        (
                            System.currentTimeMillis() -
                                    landingStart
                        ).toFloat() /
                                landingDuration
                    )

                val ease =
                    t *
                            t *
                            (
                                3f -
                                        2f *
                                        t
                            )

                val r =
                    90f +
                            ease *
                            (
                                height *
                                        0.75f
                            )

                val cx =
                    width / 2f

                val cy =
                    height / 2f

                p.color =
                    target.color

                c.drawCircle(
                    cx,
                    cy,
                    r,
                    p
                )

                if (phase == "landing") {
                    val remaining = max(0f, altitude)
                    val descent = abs(landingVelocity)
                    text(c, "LĄDOWANIE RĘCZNE", 24f, 44f, 21f, true, Color.WHITE)
                    text(c, "Wysokość: ${"%.1f".format(remaining)} km", 24f, 76f, 17f, false, Color.LTGRAY)
                    text(c, "Prędkość pionowa: ${"%.0f".format(descent)} m/s", 24f, 101f, 17f, false, if (descent <= 115f) Color.rgb(100,220,120) else Color.rgb(255,100,100))
                    text(c, "Cel: ≤ 115 m/s przy kontakcie", 24f, 126f, 15f, false, Color.LTGRAY)
                    text(c, "PALIWO ${"%.0f".format(fuel)}%   TEMP ${temperature.toInt()}°C   KADŁUB ${integrity.toInt()}%", 24f, 151f, 15f, true, Color.WHITE)

                    // Wskaźnik prędkości opadania: zielona strefa = bezpieczna.
                    val barLeft = 24f
                    val barRight = width - 24f
                    val barTop = 172f
                    val barBottom = 188f
                    p.color = Color.argb(100, 255, 255, 255)
                    c.drawRoundRect(barLeft, barTop, barRight, barBottom, 8f, 8f, p)
                    val safeRight = barLeft + (barRight - barLeft) * (115f / 180f)
                    p.color = Color.argb(170, 80, 220, 110)
                    c.drawRoundRect(barLeft, barTop, safeRight, barBottom, 8f, 8f, p)
                    val markerX = barLeft + (barRight - barLeft) * (landingVelocity / 180f).coerceIn(0f, 1f)
                    p.color = Color.WHITE
                    c.drawRect(markerX - 3f, barTop - 5f, markerX + 3f, barBottom + 5f, p)
                    text(c, if (landingVelocity <= 115f) "PRĘDKOŚĆ BEZPIECZNA" else "ZA SZYBKO — HAMUJ", 24f, 211f, 14f, true, if (landingVelocity <= 115f) Color.rgb(100,220,120) else Color.rgb(255,100,100))

                    thrustRegion.set(
                        width / 2f - 160f,
                        height - 205f,
                        width / 2f + 160f,
                        height - 105f
                    )
                    button(
                        c,
                        if (thrust) "HAMOWANIE SILNIKIEM" else "PRZYTRZYMAJ — HAMUJ",
                        thrustRegion.left,
                        thrustRegion.top,
                        thrustRegion.width(),
                        thrustRegion.height(),
                        enabled = fuel > 0f && integrity > 0f
                    )
                }

                if (
                    phase ==
                    "done"
                ) {

                    drawBaseBitmap(
                        c,
                        width / 2f,
                        height * .715f,
                        width * .62f
                    )

                    if (
                        hasGroundColony(
                            selectedPlanet
                        )
                    ) {

                        drawColonists(
                            c,
                            width * .28f,
                            height * .715f,
                            width * .3f
                        )
                    }

                    val msg =
                        if (selectedPlanet == 0) {
                            "LĄDOWANIE NA KSIĘŻYCU — KOLONIŚCI ODBLOKOWANI"
                        } else {
                            "KOLONIA ZAŁOŻONA NA ${
                                target.name.uppercase()
                            }"
                        }

                    p.color =
                        Color.argb(
                            200,
                            5,
                            10,
                            20
                        )

                    c.drawRoundRect(
                        width * .06f,
                        height * .74f,
                        width * .94f,
                        height * .96f,
                        20f,
                        20f,
                        p
                    )

                    p.textSize = 20f

                    text(
                        c,
                        msg,
                        width / 2f -
                                p.measureText(
                                    msg
                                ) / 2f,
                        height * .8f,
                        20f,
                        true,
                        Color.WHITE
                    )

                    if (selectedPlanet == 0 && peopleUnlocked) {
                        text(
                            c,
                            "TRANSPORT LUDZI: ODBLOKOWANY",
                            width / 2f -
                                    125f,
                            height * .84f,
                            17f,
                            true,
                            Color.rgb(
                                100,
                                220,
                                140
                            )
                        )
                    } else {
                        text(
                            c,
                            "Nagroda: +${target.reward} CR  •  +${2 + selectedPlanet} NAUKI",
                            width / 2f -
                                    90f,
                            height * .84f,
                            18f,
                            false,
                            Color.rgb(
                                255,
                                210,
                                90
                            )
                        )
                    }

                    if (selectedPlanet == 0 && peopleUnlocked) {
                        button(
                            c,
                            "BAZA KSIĘŻYCOWA",
                            width / 2f - 150f,
                            height * .80f,
                            300f,
                            46f
                        ) {
                            screen = "base"
                        }
                    }

                    button(
                        c,
                        "WRÓĆ DO MENU",
                        width / 2f - 150f,
                        height * .92f,
                        300f,
                        50f
                    ) {
                        screen = "menu"
                    }

                } else {

                    text(
                        c,
                        "Zbliżanie się do powierzchni...",
                        width / 2f -
                                140f,
                        height * .9f,
                        18f,
                        false,
                        Color.LTGRAY
                    )
                }

                return
            }

            /*
             * NIEUDANEJ MISJI
             */

            if (
                phase ==
                "failed"
            ) {

                text(
                    c,
                    "${target.name.uppercase()} -- CEL ${
                        target.targetAltitude.toInt()
                    } km",
                    24f,
                    48f,
                    20f,
                    true
                )

                p.color =
                    Color.argb(
                        215,
                        5,
                        10,
                        20
                    )

                c.drawRoundRect(
                    width * .1f,
                    height * .38f,
                    width * .9f,
                    height * .6f,
                    24f,
                    24f,
                    p
                )

                val msg =
                    if (integrity <= 0f)
                        "RAKIETA USZKODZONA"
                    else
                        "ZABRAKŁO PALIWA"

                p.textSize = 22f

                text(
                    c,
                    msg,
                    width / 2f -
                            p.measureText(
                                msg
                            ) / 2f,
                    height * .46f,
                    22f,
                    true,
                    Color.rgb(
                        255,
                        210,
                        90
                    )
                )

                button(
                    c,
                    "WRÓĆ DO MENU",
                    width / 2f -
                            150f,
                    height * .5f,
                    300f,
                    58f
                ) {

                    screen = "menu"
                }

                return
            }

            /*
             * HUD LOTU
             */

            val prog =
                min(
                    1f,
                    altitude /
                            target.targetAltitude
                )

            text(
                c,
                "${target.name.uppercase()} -- CEL ${target.targetAltitude.toInt()} km",
                24f, 48f, 20f, true
            )
            text(c, "ZAGROŻENIE: $worldHazard", 24f, 72f, 14f, true, Color.rgb(255, 180, 90))

            text(
                c,
                "Wysokość  ${
                    "%.1f".format(
                        altitude
                    )
                } / ${
                    target.targetAltitude.toInt()
                } km",
                24f,
                88f,
                18f
            )

            text(
                c,
                "Prędkość  ${
                    "%.0f".format(
                        speed
                    )
                } m/s",
                24f,
                114f,
                18f
            )

            text(
                c,
                "PALIWO  ${
                    "%.0f".format(
                        fuel
                    )
                }%",
                width - 150f,
                88f,
                18f,
                true
            )

            p.color =
                Color.DKGRAY

            c.drawRoundRect(
                width - 155f,
                100f,
                width - 25f,
                116f,
                8f,
                8f,
                p
            )

            p.color =
                Color.rgb(
                    70,
                    220,
                    120
                )

            c.drawRoundRect(
                width - 155f,
                100f,
                width -
                        155f +
                        130f *
                        fuel /
                        100f,
                116f,
                8f,
                8f,
                p
            )

            text(
                c,
                "TEMP  ${temperature.toInt()}°C",
                24f,
                172f,
                17f,
                true,
                if (temperature > 900f)
                    Color.rgb(255, 90, 70)
                else if (temperature > 650f)
                    Color.rgb(255, 190, 80)
                else
                    Color.rgb(120, 220, 255)
            )

            text(
                c,
                "INTEGRALNOŚĆ  ${integrity.toInt()}%",
                width - 220f,
                172f,
                17f,
                true,
                if (integrity < 35f)
                    Color.rgb(255, 80, 80)
                else
                    Color.rgb(120, 240, 150)
            )

            text(
                c,
                "V/S  ${verticalSpeed.toInt()} m/s   •   CZAS  ${flightTime.toInt()} s   •   ZAŁOGA $selectedCrew",
                24f,
                198f,
                15f,
                false,
                Color.LTGRAY
            )

            /*
             * Pasek postępu.
             */

            p.color =
                Color.DKGRAY

            c.drawRoundRect(
                24f,
                130f,
                width - 24f,
                146f,
                8f,
                8f,
                p
            )

            p.color =
                Color.rgb(
                    70,
                    210,
                    245
                )

            c.drawRoundRect(
                24f,
                130f,
                24f +
                        (
                            width -
                                    48f
                        ) *
                        prog,
                146f,
                8f,
                8f,
                p
            )

            /*
             * Cel przed rakietą.
             */

            val planetY =
                210f +
                        (
                            1f -
                                    prog
                        ) *
                        260f

            val planetR =
                26f +
                        prog *
                        60f

            val targetBmp =
                planetBmps
                    .getOrNull(
                        selectedPlanet
                    )

            if (
                targetBmp != null
            ) {

                val d =
                    planetR *
                            2f

                c.drawBitmap(
                    targetBmp,
                    null,
                    RectF(
                        width / 2f -
                                d / 2f,
                        planetY -
                                d / 2f,
                        width / 2f +
                                d / 2f,
                        planetY +
                                d / 2f
                    ),
                    p
                )

            } else {

                p.color =
                    target.color

                c.drawCircle(
                    width / 2f,
                    planetY,
                    planetR,
                    p
                )
            }

            text(
                c,
                "${target.name} • pozostało ${
                    max(
                        0f,
                        target.targetAltitude -
                                altitude
                    ).toInt()
                } km",
                width / 2f -
                        100f,
                planetY +
                        planetR +
                        30f,
                14f,
                false,
                Color.LTGRAY
            )

            /*
             * SCENA STARTOWA.
             */

            val launchMode =
                launchState < 3 &&
                        altitude < 8f

            val baseRocketY =
                height * .62f -
                        altitude *
                        1.8f

            val shakeX =
                if (
                    launchMode &&
                    launchState >= 1
                ) {

                    sin(
                        System.currentTimeMillis() /
                                24.0
                    ).toFloat() *
                            launchShake

                } else {
                    0f
                }

            val rocketY =
                baseRocketY

            if (launchMode) {

                drawEarthLaunchScene(
                    c,
                    shakeX,
                    rocketY
                )
            }

            /*
             * Wieża startowa.
             */

            drawLaunchTower(
                c,
                prog,
                rocketY
            )

            /*
             * Dym.
             */

            drawPuffs(c)

            /*
             * Płomień.
             */

            if (
                thrust ||
                (
                    launchMode &&
                            launchState >= 1
                    )
            ) {

                drawFlame(
                    c,
                    width / 2f +
                            shakeX,
                    rocketNozzleY(
                        rocketY,
                        1.5f
                    ) - 14f,
                    1.5f
                )
            }

            /*
             * Rakieta.
             */

            drawRocket(
                c,
                width / 2f +
                        shakeX,
                rocketY,
                1.5f
            )

            /*
             * ODLICZANIE / START.
             */

            if (launchMode) {

                val elapsed =
                    System.currentTimeMillis() -
                            launchStart

                val label =
                    when {

                        elapsed < 1000L ->
                            "3"

                        elapsed < 2000L ->
                            "2"

                        elapsed < 3000L ->
                            "1"

                        elapsed < 4200L ->
                            "ZAPŁON SILNIKA"

                        elapsed < 5600L ->
                            "START!"

                        else ->
                            "ODCZEPIENIE OD WIEŻY"
                    }

                p.color =
                    Color.argb(
                        210,
                        0,
                        0,
                        0
                    )

                c.drawRoundRect(
                    width / 2f -
                            170f,
                    85f,
                    width / 2f +
                            170f,
                    155f,
                    20f,
                    20f,
                    p
                )

                p.textSize =
                    if (
                        label.length <= 2
                    )
                        42f
                    else
                        25f

                text(
                    c,
                    label,
                    width / 2f -
                            p.measureText(
                                label
                            ) / 2f,
                    132f,
                    if (
                        label.length <= 2
                    )
                        42f
                    else
                        25f,
                    true,
                    Color.rgb(
                        255,
                        220,
                        100
                    )
                )

                text(
                    c,
                    when {
                        launchState == 0 ->
                            "Przygotowanie do startu..."

                        launchState == 1 ->
                            "Silniki uruchomione"

                        else ->
                            "Rakieta opuszcza wieżę startową"
                    },
                    24f,
                    height - 220f,
                    18f,
                    true,
                    Color.LTGRAY
                )
            }

            /*
             * Przycisk menu.
             */

            button(
                c,
                "\u2630",
                18f,
                70f,
                52f,
                44f
            ) {

                screen = "menu"
                thrust = false
            }

            /*
             * Sterowanie silnikiem.
             * Podczas automatycznego startu jest zablokowane.
             */

            thrustRegion.set(
                width / 2f - 150f,
                height - 200f,
                width / 2f + 150f,
                height - 110f
            )

            button(
                c,
                if (launchMode) {
                    "START AUTOMATYCZNY"
                } else if (thrust) {
                    "SILNIK - PRACUJE"
                } else {
                    "PRZYTRZYMAJ SILNIK"
                },
                thrustRegion.left,
                thrustRegion.top,
                thrustRegion.width(),
                thrustRegion.height(),
                enabled = !launchMode
            )
        }

        override fun onTouchEvent(
            e: MotionEvent
        ): Boolean {

            val x =
                e.x

            val y =
                e.y

            /*
             * Sterowanie silnikiem podczas lotu i hamowania przy lądowaniu.
             */

            if (
                screen == "game" &&
                (phase == "flight" || phase == "landing") &&
                launchState >= 3
            ) {

                if (
                    thrustRegion.contains(
                        x,
                        y
                    )
                ) {

                    thrust =
                        e.action ==
                                MotionEvent.ACTION_DOWN ||
                                e.action ==
                                MotionEvent.ACTION_MOVE

                    return true

                } else if (
                    e.action ==
                    MotionEvent.ACTION_DOWN
                ) {

                    thrust = false
                }
            }

            /*
             * Przyciski.
             */

            if (
                e.action ==
                MotionEvent.ACTION_UP
            ) {

                for (
                    (rect, action)
                    in hitRegions
                ) {

                    if (
                        rect.contains(
                            x,
                            y
                        )
                    ) {

                        action()
                        invalidate()

                        return true
                    }
                }
            }

            return true
        }
    }
}
