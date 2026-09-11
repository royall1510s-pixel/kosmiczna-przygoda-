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
        private var planetPage = 0

        private var fuel = 100f
        private var fuelTankBonus = 0
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

        // Surowce kolonii i produkcja pasywna.
        private var iron = 0f
        private var water = 0f
        private var ice = 0f
        private var crystal = 0f
        private var food = 0f
        private var energy = 50f
        private var colonyProductionTime = 0f
        private var productionFlash = 0f

        // V24: przemysł kolonii — osobne budynki wydobywcze i produkcyjne.
        private var ironMineLevel = 0
        private var iceExtractorLevel = 0
        private var crystalLabLevel = 0
        private var foodFarmLevel = 0
        private var factoryLevel = 0
        private var colonyPopulation = 0
        private var engineers = 0
        private var pilots = 0
        private var scientists = 0
        private var colonistsSent = 0
        private var colonistMissionPlanet = -1

        // V26: sieć kolonii i transport między światami.
        private val outpostLevels = IntArray(20)
        private val outpostPopulation = IntArray(20)
        private var transportTarget = -1
        private var transportTime = 0f
        private var transportCargo = 0f
        private var networkMessage = "Brak aktywnego transportu."

        // V27: gospodarka kolonii i handel.
        private var cargoTarget = -1
        private var cargoTime = 0f
        private var cargoResource = ""
        private var cargoAmount = 0f
        private var cargoReward = 0
        private var tradeMessage = "Brak aktywnego kontraktu."
        private var tradeLevel = 0
        private var tradeCompleted = 0

        private fun moonBaseLevel(): Int =
            habitatLevel + powerLevel + researchBaseLevel + storageLevel

        private fun isPlanetUnlocked(index: Int): Boolean {
            return when (index) {
                0 -> true
                1 -> landedOn.contains(0) || totalLaunches >= 1
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
                12 -> landedOn.contains(11)
                13 -> landedOn.contains(12)
                14 -> landedOn.contains(13)
                15 -> landedOn.contains(14)
                16 -> landedOn.contains(15)
                17 -> landedOn.contains(16)
                18 -> landedOn.contains(17)
                19 -> landedOn.contains(18)
                else -> false
            }
        }

        private fun fuelCapacity(): Float = 100f + fuelTankBonus * 25f + fuelTech * 5f

        private fun recommendedRange(): Float {
            return 900f + (rocketLevel - 1) * 950f + navigationTech * 450f + fuelTankBonus * 450f
        }

        private fun isRiskyDestination(): Boolean =
            planets[selectedPlanet].targetAltitude > recommendedRange()

        private fun buyFuelPack() {
            val cost = 350 + fuelTankBonus * 250
            if (fuelTankBonus >= 4) return
            if (credits >= cost) {
                credits -= cost
                fuelTankBonus += 1
                saveState()
            }
        }

        private var landingStart = 0L
        private val landingDuration = 12000L
        private var landingVelocity = 0f
        private var landingFuelStart = 0f

        // Wizualna faza zbliżania do celu. Nie zmienia fizyki lotu —
        // odpowiada tylko za kamerę/perspektywę i skalowanie planety.
        private var approachPulse = 0f

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
            Planet("Księżyc", 50f, 5000, Color.rgb(200,200,200), android.R.drawable.ic_menu_gallery),
            Planet("Mars", 400f, 12000, Color.rgb(210,90,60), android.R.drawable.ic_menu_gallery),
            Planet("Wenus", 600f, 20000, Color.rgb(230,190,120), android.R.drawable.ic_menu_gallery),
            Planet("Jowisz", 1200f, 40000, Color.rgb(200,150,90), android.R.drawable.ic_menu_gallery),
            Planet("Saturn", 2000f, 80000, Color.rgb(180,190,210), android.R.drawable.ic_menu_gallery),
            Planet("Uran", 3200f, 150000, Color.rgb(140,220,235), android.R.drawable.ic_menu_gallery),
            Planet("Neptun", 4500f, 250000, Color.rgb(70,90,220), android.R.drawable.ic_menu_gallery),
            Planet("Aurelia", 6000f, 320000, Color.rgb(40,120,90), android.R.drawable.ic_menu_gallery),
            Planet("Ignara", 7800f, 420000, Color.rgb(120,45,25), android.R.drawable.ic_menu_gallery),
            Planet("Cryonis", 9800f, 540000, Color.rgb(170,220,235), android.R.drawable.ic_menu_gallery),
            Planet("Nectaris", 12000f, 700000, Color.rgb(150,90,200), android.R.drawable.ic_menu_gallery),
            Planet("Obsidia", 15000f, 900000, Color.rgb(35,30,40), android.R.drawable.ic_menu_gallery),
            Planet("Merkury", 240f, 10000, Color.rgb(150,145,135), android.R.drawable.ic_menu_gallery),
            Planet("Ziemia", 850f, 30000, Color.rgb(60,130,220), android.R.drawable.ic_menu_gallery),
            Planet("Verdantis", 18000f, 1100000, Color.rgb(50,170,90), android.R.drawable.ic_menu_gallery),
            Planet("Pyron", 22000f, 1350000, Color.rgb(210,70,35), android.R.drawable.ic_menu_gallery),
            Planet("Crystalon", 27000f, 1700000, Color.rgb(150,220,255), android.R.drawable.ic_menu_gallery),
            Planet("Nyx", 33000f, 2200000, Color.rgb(55,35,95), android.R.drawable.ic_menu_gallery),
            Planet("Ragnar", 38000f, 2600000, Color.rgb(100,105,115), android.R.drawable.ic_menu_gallery),
            Planet("Helios", 46000f, 3200000, Color.rgb(120,95,75), android.R.drawable.ic_menu_gallery)
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
            val safeLevel = level.coerceIn(1, 20)
            val rocket = RocketDatabase.getRocket(safeLevel)
            return drawableId(rocket.imageName, android.R.drawable.ic_menu_gallery)
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
                    11 -> "planet_obsidia"
                    12 -> "planet_mercury"
                    13 -> "planet_earth"
                    14 -> "planet_verdantis"
                    15 -> "planet_pyron"
                    16 -> "planet_crystalon"
                    17 -> "planet_nyx"
                    18 -> "asteroid_ragnar"
                    else -> "asteroid_helios"
                },
                fallback
            )

        private fun baseResId(planetIndex: Int): Int =
            when (planetIndex) {
                0 -> drawableId("moon_base", android.R.drawable.ic_menu_gallery)
                1 -> drawableId("mars_base", android.R.drawable.ic_menu_gallery)
                else -> drawableId("orbital_station", android.R.drawable.ic_menu_gallery)
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
                .putFloat("iron", iron)
                .putFloat("water", water)
                .putFloat("ice", ice)
                .putFloat("crystal", crystal)
                .putFloat("food", food)
                .putFloat("energy", energy)
                .putInt("ironMineLevel", ironMineLevel)
                .putInt("iceExtractorLevel", iceExtractorLevel)
                .putInt("crystalLabLevel", crystalLabLevel)
                .putInt("foodFarmLevel", foodFarmLevel)
                .putInt("factoryLevel", factoryLevel)
                .putInt("colonyPopulation", colonyPopulation)
                .putInt("engineers", engineers)
                .putInt("pilots", pilots)
                .putInt("scientists", scientists)
                .putInt("colonistsSent", colonistsSent)
                .putInt("colonistMissionPlanet", colonistMissionPlanet)
                .putString("outpostLevels", outpostLevels.joinToString(","))
                .putString("outpostPopulation", outpostPopulation.joinToString(","))
                .putInt("transportTarget", transportTarget)
                .putFloat("transportTime", transportTime)
                .putFloat("transportCargo", transportCargo)
                .putInt("cargoTarget", cargoTarget)
                .putFloat("cargoTime", cargoTime)
                .putString("cargoResource", cargoResource)
                .putFloat("cargoAmount", cargoAmount)
                .putInt("cargoReward", cargoReward)
                .putString("tradeMessage", tradeMessage)
                .putInt("tradeLevel", tradeLevel)
                .putInt("tradeCompleted", tradeCompleted)
                .putInt("fuelTankBonus", fuelTankBonus)
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

        private fun loadIntArray(key: String, target: IntArray) {
            val raw = prefs.getString(key, null) ?: return
            raw.split(",").forEachIndexed { i, value ->
                if (i < target.size) target[i] = value.toIntOrNull()?.coerceAtLeast(0) ?: 0
            }
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
            fuelTankBonus = prefs.getInt("fuelTankBonus", 0).coerceIn(0, 4)
            ironMineLevel = prefs.getInt("ironMineLevel", 0).coerceIn(0, 3)
            iceExtractorLevel = prefs.getInt("iceExtractorLevel", 0).coerceIn(0, 3)
            crystalLabLevel = prefs.getInt("crystalLabLevel", 0).coerceIn(0, 3)
            foodFarmLevel = prefs.getInt("foodFarmLevel", 0).coerceIn(0, 3)
            factoryLevel = prefs.getInt("factoryLevel", 0).coerceIn(0, 3)
            colonyPopulation = prefs.getInt("colonyPopulation", 0).coerceAtLeast(0)
            engineers = prefs.getInt("engineers", 0).coerceAtLeast(0)
            pilots = prefs.getInt("pilots", 0).coerceAtLeast(0)
            scientists = prefs.getInt("scientists", 0).coerceAtLeast(0)
            colonistsSent = prefs.getInt("colonistsSent", 0).coerceAtLeast(0)
            colonistMissionPlanet = prefs.getInt("colonistMissionPlanet", -1)
            loadIntArray("outpostLevels", outpostLevels)
            loadIntArray("outpostPopulation", outpostPopulation)
            transportTarget = prefs.getInt("transportTarget", -1).coerceIn(-1, planets.size - 1)
            transportTime = prefs.getFloat("transportTime", 0f).coerceAtLeast(0f)
            transportCargo = prefs.getFloat("transportCargo", 0f).coerceAtLeast(0f)
            cargoTarget = prefs.getInt("cargoTarget", -1).coerceIn(-1, planets.size - 1)
            cargoTime = prefs.getFloat("cargoTime", 0f).coerceAtLeast(0f)
            cargoResource = prefs.getString("cargoResource", "") ?: ""
            cargoAmount = prefs.getFloat("cargoAmount", 0f).coerceAtLeast(0f)
            cargoReward = prefs.getInt("cargoReward", 0).coerceAtLeast(0)
            tradeMessage = prefs.getString("tradeMessage", "Brak aktywnego kontraktu.") ?: "Brak aktywnego kontraktu."
            tradeLevel = prefs.getInt("tradeLevel", 0).coerceAtLeast(0)
            tradeCompleted = prefs.getInt("tradeCompleted", 0).coerceAtLeast(0)

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

            val roleTotal = engineers + pilots + scientists
            if (roleTotal > colonyPopulation) {
                scientists = (scientists - (roleTotal - colonyPopulation)).coerceAtLeast(0)
            }

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
                    android.R.drawable.ic_menu_gallery
                )

            colonistsBmp =
                BitmapFactory.decodeResource(
                    resources,
                    drawableId("colonists", android.R.drawable.ic_menu_gallery)
                )

            towerBmp =
                BitmapFactory.decodeResource(
                    resources,
                    android.R.drawable.ic_menu_gallery
                )

            smokeBmp =
                BitmapFactory.decodeResource(
                    resources,
                    android.R.drawable.ic_menu_gallery
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

            fuel = fuelCapacity()
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

        private fun updateColonyProduction(dt: Float) {
            if (landedOn.isEmpty()) return
            colonyProductionTime += dt
            productionFlash = max(0f, productionFlash - dt)

            // Generator zasila kolonię; wyższy poziom pozwala utrzymać większą produkcję.
            val power = 0.35f + powerLevel * 0.22f
            val researchBonus = 1f + researchBaseLevel * 0.08f
            val storageLimit = 100f + storageLevel * 75f + cargoTech * 25f
            val crewBonus = if (peopleUnlocked) 1f + habitatLevel * 0.06f + min(colonyPopulation, 12) * 0.008f else 1f
            val outpostCount = outpostLevels.count { it > 0 }
            val networkBonus = 1f + outpostCount * 0.035f

            if (energy > 0f) {
                iron = min(storageLimit, iron + (0.35f + ironMineLevel * 0.42f) * power * dt * researchBonus * networkBonus)
                water = min(storageLimit, water + (0.22f + powerLevel * 0.05f) * power * dt * crewBonus * networkBonus)
                ice = min(storageLimit, ice + (0.06f + iceExtractorLevel * 0.28f) * power * dt * networkBonus)
                crystal = min(storageLimit, crystal + (0.02f + crystalLabLevel * 0.11f) * power * dt * researchBonus * networkBonus)
                food = min(storageLimit, food + (0.05f + foodFarmLevel * 0.16f) * power * dt * crewBonus * networkBonus)
                if (factoryLevel > 0 && iron >= 2f && ice >= 1f) {
                    val factoryCycles = min(iron / 2f, ice).coerceAtMost(0.5f * factoryLevel * dt)
                    iron -= factoryCycles * 2f
                    ice -= factoryCycles
                    researchPoints += floor(factoryCycles * 2f).toInt()
                }
                energy = min(100f, energy + (0.7f + powerLevel * 0.35f) * dt)
            } else {
                energy = min(100f, energy + 0.2f * dt)
            }

            // Załoga zużywa trochę żywności i wody.
            if (peopleUnlocked && selectedCrew > 0 && colonyProductionTime >= 10f) {
                val cycles = floor(colonyProductionTime / 10f)
                colonyProductionTime -= cycles * 10f
                water = max(0f, water - cycles * selectedCrew * 0.4f)
                food = max(0f, food - cycles * selectedCrew * 0.25f)
            }

            if (productionFlash <= 0f) {
                productionFlash = 1f
                saveState()
            }
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
            updateColonyProduction(dt)
            updateTransport(dt)
            updateCargoTransport(dt)

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
                "colonists" -> colonists(c)
                "base" -> base(c)
                "resources" -> resources(c)
                "industry" -> industry(c)
                "network" -> network(c)
                "trade" -> trade(c)
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
                // Początkowa prędkość opadania. Gracz musi teraz
                // rzeczywiście wyhamować rakietę przed kontaktem.
                landingVelocity = 135f
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
                112f * scale

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
                112f * scale

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

        private fun drawPlanetVisual(
            c: Canvas,
            index: Int,
            cx: Float,
            cy: Float,
            radius: Float,
            alpha: Int = 255
        ) {
            val target = planets[index]
            val bmp = planetBmps.getOrNull(index)

            p.alpha = alpha.coerceIn(0, 255)

            if (bmp != null) {
                c.drawBitmap(
                    bmp,
                    null,
                    RectF(
                        cx - radius,
                        cy - radius,
                        cx + radius,
                        cy + radius
                    ),
                    p
                )
            } else {
                // Awaryjny proceduralny wygląd planety. To nie jest już
                // płaskie białe kółko: ma światło, cień i kratery.
                val lightX = cx - radius * 0.32f
                val lightY = cy - radius * 0.38f

                p.shader = RadialGradient(
                    lightX,
                    lightY,
                    radius * 1.08f,
                    intArrayOf(
                        Color.rgb(
                            min(255, Color.red(target.color) + 65),
                            min(255, Color.green(target.color) + 65),
                            min(255, Color.blue(target.color) + 65)
                        ),
                        target.color,
                        Color.rgb(
                            max(0, Color.red(target.color) - 45),
                            max(0, Color.green(target.color) - 45),
                            max(0, Color.blue(target.color) - 45)
                        )
                    ),
                    floatArrayOf(0f, 0.62f, 1f),
                    Shader.TileMode.CLAMP
                )
                c.drawCircle(cx, cy, radius, p)
                p.shader = null

                // Stałe kratery — dzięki temu obraz nie "miga" między klatkami.
                val craters = arrayOf(
                    floatArrayOf(-0.38f, -0.12f, 0.10f),
                    floatArrayOf(-0.10f, -0.42f, 0.07f),
                    floatArrayOf(0.23f, -0.28f, 0.13f),
                    floatArrayOf(0.39f, 0.08f, 0.08f),
                    floatArrayOf(-0.24f, 0.28f, 0.14f),
                    floatArrayOf(0.08f, 0.38f, 0.06f)
                )

                for (crater in craters) {
                    val x = cx + crater[0] * radius
                    val y = cy + crater[1] * radius
                    val r = crater[2] * radius

                    p.color = Color.argb(
                        (alpha * 0.28f).toInt().coerceIn(0, 255),
                        20, 20, 24
                    )
                    c.drawCircle(x, y, r, p)

                    p.style = Paint.Style.STROKE
                    p.strokeWidth = max(1.5f, radius * 0.018f)
                    p.color = Color.argb(
                        (alpha * 0.22f).toInt().coerceIn(0, 255),
                        245, 245, 245
                    )
                    c.drawCircle(x - r * 0.12f, y - r * 0.10f, r * 0.78f, p)
                    p.style = Paint.Style.FILL
                }
            }

            p.alpha = 255
        }

        private fun drawApproachRocket(
            c: Canvas,
            rocketX: Float,
            rocketY: Float,
            scale: Float,
            braking: Boolean
        ) {
            if (braking) {
                drawFlame(
                    c,
                    rocketX,
                    rocketNozzleY(rocketY, scale) - 12f,
                    scale * 1.05f
                )
            }
            drawRocket(c, rocketX, rocketY, scale)
        }

        private fun drawLandingGround(
            c: Canvas,
            index: Int,
            progress: Float
        ) {
            val target = planets[index]
            val horizon = height * (0.78f - progress * 0.16f)
            val groundTop = horizon

            p.shader = LinearGradient(
                0f,
                groundTop,
                0f,
                height.toFloat(),
                Color.rgb(
                    max(8, Color.red(target.color) / 2),
                    max(8, Color.green(target.color) / 2),
                    max(8, Color.blue(target.color) / 2)
                ),
                Color.rgb(8, 8, 14),
                Shader.TileMode.CLAMP
            )
            c.drawRect(0f, groundTop, width.toFloat(), height.toFloat(), p)
            p.shader = null

            // Duże, miękkie formy terenu — szczególnie dobrze wyglądają na Księżycu.
            p.color = Color.argb(95, 255, 255, 255)
            for (i in 0..7) {
                val x = (i / 7f) * width
                val hill = 22f + ((i * 17) % 28)
                c.drawCircle(
                    x,
                    groundTop + 10f,
                    hill,
                    p
                )
            }

            p.color = Color.argb(85, 0, 0, 0)
            for (i in 0..10) {
                val x = (i * 83 % max(1, width.toInt())).toFloat()
                val y = groundTop + 34f + (i * 31 % 120)
                c.drawCircle(x, y, 8f + (i % 4) * 5f, p)
            }
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
                    android.R.drawable.ic_menu_gallery

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
            val cx = width / 2f
            val towerTop = rocketY - 150f
            val towerBottom = rocketY + 155f
            val left = cx - 122f
            val right = cx + 122f

            // Konstrukcja wieży: cztery nogi + poprzeczki + światła ostrzegawcze.
            p.style = Paint.Style.STROKE
            p.strokeWidth = 9f
            p.strokeCap = Paint.Cap.SQUARE
            p.color = Color.rgb(105, 116, 132)
            c.drawLine(left, towerTop, left - 16f, towerBottom, p)
            c.drawLine(right, towerTop, right + 16f, towerBottom, p)
            p.strokeWidth = 5f
            for (i in 0..4) {
                val y = towerTop + (towerBottom - towerTop) * i / 4f
                c.drawLine(left - (i * 4f), y, right + (i * 4f), y, p)
            }
            // Ramiona trzymające rakietę przed odczepieniem.
            if (prog < 0.02f) {
                p.strokeWidth = 7f
                p.color = Color.rgb(165, 174, 188)
                c.drawLine(left, rocketY - 10f, cx - 48f, rocketY - 10f, p)
                c.drawLine(right, rocketY - 10f, cx + 48f, rocketY - 10f, p)
            }
            p.style = Paint.Style.FILL

            // Czerwone lampki ostrzegawcze.
            val pulse = ((sin(System.currentTimeMillis() / 220.0) + 1.0) * 0.5).toFloat()
            p.color = Color.argb((130 + pulse * 120).toInt(), 255, 55, 55)
            c.drawCircle(left - 3f, towerTop + 8f, 6f, p)
            c.drawCircle(right + 3f, towerTop + 8f, 6f, p)

            // Platforma startowa i światła.
            p.color = Color.rgb(48, 54, 64)
            c.drawRoundRect(cx - 170f, towerBottom - 4f, cx + 170f, towerBottom + 25f, 8f, 8f, p)
            p.color = Color.rgb(255, 190, 70)
            for (i in -3..3) c.drawCircle(cx + i * 42f, towerBottom + 11f, 4f, p)
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
            val cx = width / 2f
            val cy = height * .98f
            val r = width * .86f

            // Poświata atmosfery.
            p.shader = RadialGradient(cx, cy, r * 1.02f,
                intArrayOf(Color.argb(90, 70, 190, 255), Color.TRANSPARENT),
                floatArrayOf(.82f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r * 1.03f, p)
            p.shader = null

            // Ziemia z delikatnym gradientem.
            p.shader = RadialGradient(cx - r * .28f, cy - r * .52f, r * 1.25f,
                Color.rgb(48, 135, 205), Color.rgb(5, 34, 78), Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, p)
            p.shader = null

            // Kontynenty.
            p.color = Color.rgb(38, 120, 76)
            val land = Path()
            land.moveTo(cx-r*.75f, cy-r*.40f); land.lineTo(cx-r*.47f, cy-r*.56f)
            land.lineTo(cx-r*.20f, cy-r*.47f); land.lineTo(cx-r*.32f, cy-r*.17f)
            land.lineTo(cx-r*.55f, cy-r*.12f); land.close()
            c.drawPath(land,p)
            val land2 = Path()
            land2.moveTo(cx+r*.06f, cy-r*.48f); land2.lineTo(cx+r*.42f, cy-r*.38f)
            land2.lineTo(cx+r*.67f, cy-r*.12f); land2.lineTo(cx+r*.42f, cy+r*.04f)
            land2.lineTo(cx+r*.15f, cy-r*.08f); land2.close()
            c.drawPath(land2,p)

            // Chmury.
            p.color = Color.argb(70,255,255,255)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 16f
            c.drawArc(RectF(cx-r*.92f,cy-r*.80f,cx+r*.92f,cy+r*.92f),205f,65f,false,p)
            c.drawArc(RectF(cx-r*.88f,cy-r*.70f,cx+r*.88f,cy+r*.92f),315f,48f,false,p)
            p.style = Paint.Style.FILL

            // Platforma startowa.
            val nozzle = rocketNozzleY(rocketY, 1.5f)
            p.color = Color.rgb(38,44,54)
            c.drawRoundRect(cx-145f+shakeX,nozzle+20f,cx+145f+shakeX,nozzle+48f,10f,10f,p)
            p.color = Color.rgb(92,102,118)
            c.drawRect(cx-118f+shakeX,nozzle+10f,cx-102f+shakeX,nozzle+30f,p)
            c.drawRect(cx+102f+shakeX,nozzle+10f,cx+118f+shakeX,nozzle+30f,p)

            // Lokalna mgiełka nad horyzontem.
            p.shader = LinearGradient(0f, height*.55f, 0f, height*.82f,
                Color.TRANSPARENT, Color.argb(100,120,210,255), Shader.TileMode.CLAMP)
            c.drawRect(0f,height*.55f,width.toFloat(),height*.82f,p)
            p.shader = null

            text(c,"ZIEMIA • CENTRUM STARTOWE",24f,height-32f,15f,true,Color.rgb(190,235,255))
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
                button(
                    c,
                    "KOLONIŚCI",
                    width - 210f,
                    374f,
                    180f,
                    52f
                ) {
                    screen = "colonists"
                }
            }

            if (peopleUnlocked) {
                button(
                    c,
                    "SIEĆ KOLONII",
                    width - 210f,
                    438f,
                    180f,
                    52f
                ) {
                    screen = "network"
                }
            }

            button(
                c,
                "RAKIETY",
                width - 210f,
                if (peopleUnlocked) 502f else 310f,
                180f,
                52f
            ) {
                screen = "rockets"
            }

            button(
                c,
                "PLANETY",
                width - 210f,
                if (peopleUnlocked) 566f else 374f,
                180f,
                52f
            ) {
                screen = "planets"
            }

            button(
                c,
                "ULEPSZENIA",
                width - 210f,
                if (peopleUnlocked) 630f else 438f,
                180f,
                52f
            ) {
                screen = "upgrade"
            }

            button(
                c,
                "BADANIA",
                width - 210f,
                if (peopleUnlocked) 694f else 502f,
                180f,
                52f
            ) {
                screen = "research"
            }

            button(
                c,
                "MISJE",
                width - 210f,
                if (peopleUnlocked) 758f else 566f,
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


        private fun drawBaseWorld(c: Canvas, planetIndex: Int) {
            val cx = width / 2f
            val horizon = 218f
            val bmp = planetBmps.getOrNull(planetIndex)

            // Niebo i poświata świata.
            p.shader = LinearGradient(
                0f, 80f, 0f, horizon,
                Color.rgb(4, 10, 24),
                Color.rgb(20, 34, 52),
                Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 78f, width.toFloat(), horizon, p)
            p.shader = null

            // Horyzont planety.
            p.color = Color.rgb(48, 48, 54)
            c.drawRect(0f, horizon, width.toFloat(), height.toFloat(), p)

            p.color = Color.rgb(76, 76, 82)
            for (i in 0..7) {
                val x = (i * 137f + planetIndex * 53f) % (width + 80f) - 40f
                val y = horizon + 35f + ((i * 47 + planetIndex * 19) % 210)
                val r = 10f + ((i * 7 + planetIndex) % 18)
                c.drawCircle(x, y, r, p)
            }

            // Duży świat na niebie jako wizualny identyfikator kolonii.
            if (bmp != null) {
                p.alpha = 225
                c.drawBitmap(
                    bmp, null,
                    RectF(width - 170f, 92f, width - 34f, 228f),
                    p
                )
                p.alpha = 255
            }

            // Droga do bazy.
            p.color = Color.rgb(92, 92, 98)
            val road = Path()
            road.moveTo(width * .38f, height.toFloat())
            road.lineTo(width * .47f, horizon + 15f)
            road.lineTo(width * .53f, horizon + 15f)
            road.lineTo(width * .63f, height.toFloat())
            road.close()
            c.drawPath(road, p)

            // Moduł główny kolonii.
            val bx = cx
            val by = horizon + 76f
            p.color = Color.rgb(36, 46, 58)
            c.drawRoundRect(bx - 82f, by - 45f, bx + 82f, by + 38f, 14f, 14f, p)
            p.color = Color.rgb(72, 94, 112)
            c.drawRect(bx - 62f, by - 30f, bx + 62f, by + 18f, p)

            // Kopuła.
            p.color = Color.rgb(72, 190, 225)
            c.drawOval(RectF(bx - 36f, by - 61f, bx + 36f, by - 5f), p)
            p.color = Color.argb(120, 210, 245, 255)
            c.drawOval(RectF(bx - 28f, by - 53f, bx + 28f, by - 13f), p)

            // Panele słoneczne.
            p.color = Color.rgb(28, 58, 82)
            c.drawRect(bx - 145f, by - 8f, bx - 88f, by + 22f, p)
            c.drawRect(bx + 88f, by - 8f, bx + 145f, by + 22f, p)
            p.color = Color.rgb(95, 145, 175)
            for (x in listOf(bx - 126f, bx - 107f, bx + 107f, bx + 126f)) {
                c.drawRect(x, by - 6f, x + 2f, by + 20f, p)
            }

            // Łączniki i światła.
            p.color = Color.rgb(180, 220, 235)
            c.drawCircle(bx - 92f, by + 6f, 5f, p)
            c.drawCircle(bx + 92f, by + 6f, 5f, p)
        }

        private fun base(c: Canvas) {
            val basePlanet = if (landedOn.contains(selectedPlanet)) selectedPlanet else 0
            val world = planets[basePlanet]

            backButton(c, "BAZA")
            creditsBadge(c)

            text(c, "BAZA • ${world.name.uppercase()}", 24f, 48f, 24f, true, Color.WHITE)
            text(c, "KOLONIA POZIOM ${moonBaseLevel()} / 12", 24f, 73f, 13f, true, Color.rgb(90, 220, 255))

            drawBaseWorld(c, basePlanet)

            p.color = Color.argb(210, 5, 12, 24)
            c.drawRoundRect(18f, 242f, width - 18f, height - 22f, 20f, 20f, p)

            text(c, "CENTRUM KOLONII", 34f, 274f, 19f, true, Color.WHITE)
            text(c, "Rozwijaj moduły, aby zwiększać możliwości osady.", 34f, 297f, 13f, false, Color.LTGRAY)

            data class BaseModule(
                val name: String,
                val level: Int,
                val icon: String,
                val bonus: String,
                val setLevel: (Int) -> Unit
            )

            val modules = listOf(
                BaseModule("MODUŁ MIESZKALNY", habitatLevel, "⌂", "Załoga +${habitatLevel * 2}%") { habitatLevel = it },
                BaseModule("GENERATOR ENERGII", powerLevel, "⚡", "Energia +${powerLevel * 3}%") { powerLevel = it },
                BaseModule("LABORATORIUM", researchBaseLevel, "✦", "Badania +${researchBaseLevel * 5}%") { researchBaseLevel = it },
                BaseModule("MAGAZYN", storageLevel, "▣", "Ładunek +${storageLevel * 10}%") { storageLevel = it }
            )

            modules.forEachIndexed { index, module ->
                val y = 312f + index * 86f
                val cost = 350 * (module.level + 1) * (1 + moonBaseLevel() / 6)

                p.color = Color.argb(145, 12, 25, 42)
                c.drawRoundRect(28f, y, width - 28f, y + 72f, 14f, 14f, p)

                text(c, module.icon, 42f, y + 40f, 25f, true, Color.rgb(100, 220, 255))
                text(c, module.name, 78f, y + 25f, 14f, true, Color.WHITE)
                text(c, "Poziom ${module.level}/3  •  ${module.bonus}", 78f, y + 48f, 12f, false, Color.LTGRAY)

                if (module.level < 3) {
                    button(
                        c,
                        "${cost} CR",
                        width - 126f, y + 12f, 84f, 48f,
                        enabled = credits >= cost
                    ) {
                        if (credits >= cost && module.level < 3) {
                            credits -= cost
                            module.setLevel(module.level + 1)
                            saveState()
                        }
                    }
                } else {
                    text(c, "MAX", width - 88f, y + 42f, 14f, true, Color.rgb(100, 230, 140))
                }
            }

            button(
                c,
                "ZARZĄDZAJ KOLONISTAMI",
                width / 2f - 150f,
                height - 166f,
                300f,
                42f
            ) {
                screen = "colonists"
            }

            button(
                c,
                "PRZEMYSŁ / WYDOBYCIE",
                width / 2f - 150f,
                height - 118f,
                300f,
                42f
            ) {
                screen = "industry"
            }

            button(
                c,
                "SIEĆ KOLONII",
                width / 2f - 150f,
                height - 214f,
                300f,
                42f
            ) {
                screen = "network"
            }

            button(
                c,
                "HANDEL I CARGO",
                width / 2f - 150f,
                height - 262f,
                300f,
                42f
            ) {
                screen = "trade"
            }

            button(
                c,
                "SUROWCE I PRODUKCJA",
                width / 2f - 150f,
                height - 70f,
                300f,
                42f
            ) {
                screen = "resources"
            }

            text(
                c,
                "ENERGIA ${powerLevel * 25 + 50}%   •   BADANIA +${researchBaseLevel * 5}%   •   MAGAZYN +${storageLevel * 10}%",
                30f, height - 28f, 11f, true, Color.LTGRAY
            )
        }

        private fun industry(c: Canvas) {
            backButton(c, "PRZEMYSŁ")
            creditsBadge(c)

            text(c, "PRZEMYSŁ KOLONII", 24f, 112f, 25f, true, Color.WHITE)
            text(c, "Wydobywaj surowce i zamieniaj je w materiały.", 24f, 138f, 13f, false, Color.LTGRAY)
            text(c, "ZAŁOGA: $colonyPopulation   •   FABRYKA: $factoryLevel/3", 24f, 162f, 13f, true, Color.rgb(100,220,255))

            data class Plant(
                val name: String,
                val level: Int,
                val icon: String,
                val detail: String,
                val set: (Int) -> Unit
            )

            val plants = listOf(
                Plant("KOPALNIA ŻELAZA", ironMineLevel, "Fe", "wydobycie żelaza", { ironMineLevel = it }),
                Plant("EKSTRAKTOR LODU", iceExtractorLevel, "H₂O", "lód i woda", { iceExtractorLevel = it }),
                Plant("LABORATORIUM KRYSZTAŁÓW", crystalLabLevel, "✦", "kryształy", { crystalLabLevel = it }),
                Plant("FARMA HYDROPONICZNA", foodFarmLevel, "+", "żywność", { foodFarmLevel = it }),
                Plant("FABRYKA", factoryLevel, "⚙", "materiały + badania", { factoryLevel = it })
            )

            plants.forEachIndexed { i, plant ->
                val y = 188f + i * 78f
                val cost = 600 * (plant.level + 1)
                p.color = Color.argb(170, 10, 25, 42)
                c.drawRoundRect(22f, y, width - 22f, y + 64f, 14f, 14f, p)
                text(c, plant.icon, 38f, y + 39f, 21f, true, Color.rgb(100,220,255))
                text(c, plant.name, 78f, y + 25f, 14f, true, Color.WHITE)
                text(c, "Poziom ${plant.level}/3  •  ${plant.detail}", 78f, y + 47f, 12f, false, Color.LTGRAY)
                if (plant.level < 3) {
                    button(c, "$cost CR", width - 118f, y + 10f, 82f, 44f, enabled = credits >= cost) {
                        if (credits >= cost && plant.level < 3) {
                            credits -= cost
                            plant.set(plant.level + 1)
                            saveState()
                        }
                    }
                } else {
                    text(c, "MAX", width - 88f, y + 39f, 13f, true, Color.rgb(100,230,140))
                }
            }

            button(c, "← BAZA", width / 2f - 100f, height - 58f, 200f, 40f) {
                screen = "base"
            }
        }

        private fun updateTransport(dt: Float) {
            if (transportTarget < 0) return
            transportTime += dt
            if (transportTime >= transportDuration(transportTarget)) {
                val target = transportTarget
                outpostLevels[target] = max(1, outpostLevels[target])
                outpostPopulation[target] += 2
                networkMessage = "TRANSPORT DOTARŁ: ${planets[target].name} • +2 kolonistów"
                transportTarget = -1
                transportTime = 0f
                transportCargo = 0f
                colonistsSent = max(0, colonistsSent - 2)
                saveState()
            }
        }

        private fun transportDuration(target: Int): Float =
            18f + planets[target].targetAltitude / 550f

        private fun sendColonyTransport() {
            val target = selectedPlanet
            if (!peopleUnlocked || target == 0 || target !in planets.indices) return
            if (!isPlanetUnlocked(target)) return
            if (transportTarget >= 0) return
            if (outpostLevels[target] >= 3) return
            val free = (colonyPopulation - colonistsSent).coerceAtLeast(0)
            if (free < 2 || pilots <= 0 || engineers <= 0) return
            val ironCost = 12f + outpostLevels[target] * 8f
            val iceCost = 6f + outpostLevels[target] * 4f
            val energyCost = 10f
            if (iron < ironCost || ice < iceCost || energy < energyCost) return
            iron -= ironCost
            ice -= iceCost
            energy -= energyCost
            colonistsSent += 2
            transportTarget = target
            transportTime = 0f
            transportCargo = ironCost + iceCost
            networkMessage = "TRANSPORT WYSTARTOWAŁ DO ${planets[target].name.uppercase()}"
            saveState()
        }

        private fun upgradeOutpost() {
            val target = selectedPlanet
            if (target <= 0 || target !in planets.indices || outpostLevels[target] <= 0) return
            val level = outpostLevels[target]
            if (level >= 3) return
            val ironCost = 25f * level
            val crystalCost = 8f * level
            if (iron < ironCost || crystal < crystalCost) return
            iron -= ironCost
            crystal -= crystalCost
            outpostLevels[target] = level + 1
            networkMessage = "PLACÓWKA ${planets[target].name} ROZBUDOWANA DO POZIOMU ${level + 1}"
            saveState()
        }

        private fun network(c: Canvas) {
            backButton(c, "SIEĆ KOLONII")
            creditsBadge(c)
            text(c, "SIEĆ KOLONII", 24f, 48f, 25f, true, Color.WHITE)
            text(c, "Połącz Ziemię z odległymi placówkami.", 24f, 74f, 13f, false, Color.LTGRAY)

            val target = planets[selectedPlanet]
            val level = if (selectedPlanet in outpostLevels.indices) outpostLevels[selectedPlanet] else 0
            val residents = if (selectedPlanet in outpostPopulation.indices) outpostPopulation[selectedPlanet] else 0
            val built = outpostLevels.count { it > 0 }

            p.color = Color.argb(190, 8, 22, 40)
            c.drawRoundRect(20f, 92f, width - 20f, 176f, 18f, 18f, p)
            text(c, "PLACÓWKI: $built / 19", 36f, 122f, 16f, true, Color.rgb(100,220,255))
            text(c, "TRANSPORT: ${if (transportTarget >= 0) planets[transportTarget].name else "BRAK"}", 36f, 148f, 13f, false, Color.LTGRAY)
            text(c, networkMessage, 36f, 168f, 11f, false, Color.rgb(100,230,150))

            text(c, "CEL: ${target.name.uppercase()}", 24f, 212f, 22f, true, Color.WHITE)
            text(c, "POZIOM PLACÓWKI: $level / 3   •   MIESZKAŃCY: $residents", 24f, 238f, 13f, false, Color.LTGRAY)

            // Schematyczna mapa połączeń.
            val mapTop = 258f
            p.color = Color.argb(130, 12, 34, 55)
            c.drawRoundRect(20f, mapTop, width - 20f, mapTop + 180f, 18f, 18f, p)
            val cx = width / 2f
            val cy = mapTop + 92f
            p.color = Color.rgb(70, 210, 245)
            c.drawCircle(cx, cy, 25f, p)
            text(c, "Z", cx - 8f, cy + 7f, 15f, true, Color.WHITE)
            for (i in 0 until 8) {
                val a = i * PI.toFloat() / 4f
                val x = cx + cos(a) * 118f
                val y = cy + sin(a) * 62f
                p.color = if (outpostLevels.getOrNull(i + 1)?.let { it > 0 } == true) Color.rgb(100, 230, 150) else Color.DKGRAY
                c.drawLine(cx, cy, x, y, p)
                c.drawCircle(x, y, 11f, p)
            }

            button(c, "‹", 24f, 462f, 58f, 46f) {
                selectedPlanet = if (selectedPlanet <= 0) planets.lastIndex else selectedPlanet - 1
            }
            button(c, "›", width - 82f, 462f, 58f, 46f) {
                selectedPlanet = (selectedPlanet + 1) % planets.size
            }
            text(c, "ŚWIAT ${selectedPlanet + 1} / ${planets.size}", width / 2f - 60f, 491f, 13f, true, Color.LTGRAY)

            button(c, "HANDEL I CARGO", 24f, 520f, width - 48f, 50f) {
                screen = "trade"
            }

            val transportReady = selectedPlanet > 0 && isPlanetUnlocked(selectedPlanet) &&
                    transportTarget < 0 && outpostLevels[selectedPlanet] < 3 &&
                    colonyPopulation - colonistsSent >= 2 && pilots > 0 && engineers > 0 &&
                    iron >= 12f + outpostLevels[selectedPlanet] * 8f &&
                    ice >= 6f + outpostLevels[selectedPlanet] * 4f && energy >= 10f
            button(c, "🚀 WYŚLIJ TRANSPORT", 24f, 578f, width - 48f, 54f, enabled = transportReady) {
                sendColonyTransport()
            }

            val upgradeReady = level in 1..2 && iron >= 25f * level && crystal >= 8f * level
            button(c, "⬆ ROZBUDUJ PLACÓWKĘ", 24f, 638f, width - 48f, 50f, enabled = upgradeReady) {
                upgradeOutpost()
            }

            if (transportTarget >= 0) {
                val duration = transportDuration(transportTarget)
                val progress = (transportTime / duration).coerceIn(0f, 1f)
                text(c, "LOT TRANSPORTOWY  ${"%.0f".format(progress * 100f)}%", 24f, height - 52f, 13f, true, Color.rgb(100,220,255))
                p.color = Color.DKGRAY
                c.drawRoundRect(24f, height - 40f, width - 24f, height - 24f, 8f, 8f, p)
                p.color = Color.rgb(80, 210, 245)
                c.drawRoundRect(24f, height - 40f, 24f + (width - 48f) * progress, height - 24f, 8f, 8f, p)
            }
        }

        private fun resourceValue(resource: String): Float = when (resource) {
            "ŻELAZO" -> 32f + tradeLevel * 4f
            "WODA" -> 46f + tradeLevel * 5f
            "LÓD" -> 58f + tradeLevel * 6f
            "KRYSZTAŁY" -> 120f + tradeLevel * 12f
            "ŻYWNOŚĆ" -> 40f + tradeLevel * 4f
            "ENERGIA" -> 26f + tradeLevel * 3f
            else -> 0f
        }

        private fun currentResource(resource: String): Float = when (resource) {
            "ŻELAZO" -> iron
            "WODA" -> water
            "LÓD" -> ice
            "KRYSZTAŁY" -> crystal
            "ŻYWNOŚĆ" -> food
            "ENERGIA" -> energy
            else -> 0f
        }

        private fun changeResource(resource: String, delta: Float) {
            when (resource) {
                "ŻELAZO" -> iron = max(0f, iron + delta)
                "WODA" -> water = max(0f, water + delta)
                "LÓD" -> ice = max(0f, ice + delta)
                "KRYSZTAŁY" -> crystal = max(0f, crystal + delta)
                "ŻYWNOŚĆ" -> food = max(0f, food + delta)
                "ENERGIA" -> energy = max(0f, energy + delta)
            }
        }

        private fun cargoDuration(target: Int): Float =
            14f + planets[target].targetAltitude / 700f

        private fun sendCargo(resource: String) {
            val target = selectedPlanet
            if (cargoTarget >= 0 || target <= 0 || target !in planets.indices) return
            if (!isPlanetUnlocked(target) || outpostLevels[target] <= 0) return
            val amount = when (resource) {
                "ŻELAZO", "WODA", "LÓD", "ŻYWNOŚĆ" -> 10f
                "KRYSZTAŁY" -> 5f
                "ENERGIA" -> 15f
                else -> return
            }
            if (currentResource(resource) < amount) return
            val reward = (amount * resourceValue(resource) * (1f + outpostLevels[target] * 0.12f)).toInt()
            changeResource(resource, -amount)
            cargoTarget = target
            cargoTime = 0f
            cargoResource = resource
            cargoAmount = amount
            cargoReward = reward
            tradeMessage = "STATEK CARGO WYSTARTOWAŁ DO ${planets[target].name.uppercase()}"
            saveState()
        }

        private fun updateCargoTransport(dt: Float) {
            if (cargoTarget < 0) return
            cargoTime += dt
            if (cargoTime >= cargoDuration(cargoTarget)) {
                credits += cargoReward
                tradeCompleted += 1
                tradeLevel = min(10, tradeCompleted / 3)
                val targetName = planets[cargoTarget].name
                tradeMessage = "DOSTAWA DOTARŁA: $targetName • +$cargoReward CR"
                cargoTarget = -1
                cargoTime = 0f
                cargoResource = ""
                cargoAmount = 0f
                cargoReward = 0
                saveState()
            }
        }

        private fun trade(c: Canvas) {
            backButton(c, "HANDEL")
            creditsBadge(c)
            text(c, "GOSPODARKA KOLONII", 24f, 48f, 24f, true, Color.WHITE)
            text(c, "Wysyłaj nadwyżki surowców do placówek.", 24f, 74f, 13f, false, Color.LTGRAY)
            text(c, "POZIOM HANDLU: $tradeLevel   •   DOSTAWY: $tradeCompleted", 24f, 100f, 13f, true, Color.rgb(100,220,255))

            val target = planets[selectedPlanet]
            text(c, "CEL: ${target.name.uppercase()}", 24f, 132f, 19f, true, Color.WHITE)
            text(c, if (outpostLevels[selectedPlanet] > 0) "PLACÓWKA POZIOM ${outpostLevels[selectedPlanet]}" else "BRAK PLACÓWKI — najpierw wyślij kolonistów", 24f, 156f, 12f, false, Color.LTGRAY)

            val resources = listOf("ŻELAZO", "WODA", "LÓD", "KRYSZTAŁY", "ŻYWNOŚĆ", "ENERGIA")
            resources.forEachIndexed { i, r ->
                val col = i % 2
                val row = i / 2
                val x = if (col == 0) 24f else width / 2f + 6f
                val y = 178f + row * 82f
                p.color = Color.argb(175, 10, 28, 46)
                c.drawRoundRect(x, y, x + width / 2f - 36f, y + 68f, 13f, 13f, p)
                text(c, r, x + 12f, y + 22f, 12f, true, Color.LTGRAY)
                text(c, "${"%.1f".format(currentResource(r))} • ${"%.0f".format(resourceValue(r))} CR/u", x + 12f, y + 43f, 10f, false, Color.WHITE)
                val enabled = cargoTarget < 0 && selectedPlanet > 0 && outpostLevels[selectedPlanet] > 0 && currentResource(r) >= if (r == "KRYSZTAŁY") 5f else if (r == "ENERGIA") 15f else 10f
                button(c, "WYŚLIJ", x + width / 2f - 116f, y + 12f, 68f, 40f, enabled = enabled) { sendCargo(r) }
            }

            p.color = Color.argb(190, 7, 18, 32)
            c.drawRoundRect(24f, 432f, width - 24f, 520f, 14f, 14f, p)
            text(c, tradeMessage, 38f, 458f, 12f, true, Color.rgb(100,230,150))
            if (cargoTarget >= 0) {
                val progress = (cargoTime / cargoDuration(cargoTarget)).coerceIn(0f, 1f)
                text(c, "CARGO: $cargoResource ${"%.0f".format(cargoAmount)} • ${"%.0f".format(progress * 100f)}%", 38f, 482f, 11f, false, Color.LTGRAY)
                p.color = Color.DKGRAY
                c.drawRoundRect(38f, 492f, width - 38f, 505f, 6f, 6f, p)
                p.color = Color.rgb(80,210,245)
                c.drawRoundRect(38f, 492f, 38f + (width - 76f) * progress, 505f, 6f, 6f, p)
            }

            text(c, "KONTRAKTY: wyższy poziom handlu zwiększa ceny skupu.", 24f, 550f, 11f, false, Color.LTGRAY)
            button(c, "WRÓĆ DO BAZY", width / 2f - 125f, height - 66f, 250f, 44f) { screen = "base" }
        }

        private fun resources(c: Canvas) {
            backButton(c, "SUROWCE")
            creditsBadge(c)

            text(c, "SUROWCE • ${planets[selectedPlanet].name.uppercase()}", 24f, 48f, 23f, true, Color.WHITE)
            text(c, "Automatyczna produkcja działa po założeniu kolonii.", 24f, 74f, 13f, false, Color.LTGRAY)

            val rows = listOf(
                "ŻELAZO" to iron,
                "WODA" to water,
                "LÓD" to ice,
                "KRYSZTAŁY" to crystal,
                "ŻYWNOŚĆ" to food,
                "ENERGIA" to energy
            )
            rows.forEachIndexed { i, row ->
                val col = i % 2
                val line = i / 2
                val x = if (col == 0) 24f else width / 2f + 6f
                val y = 112f + line * 94f
                p.color = Color.argb(175, 12, 28, 46)
                c.drawRoundRect(x, y, x + width / 2f - 36f, y + 76f, 14f, 14f, p)
                text(c, row.first, x + 16f, y + 27f, 13f, true, Color.LTGRAY)
                text(c, "${"%.1f".format(row.second)}", x + 16f, y + 58f, 25f, true, if (row.first == "ENERGIA") Color.rgb(100,220,255) else Color.WHITE)
            }

            p.color = Color.argb(205, 5, 12, 24)
            c.drawRoundRect(24f, 410f, width - 24f, 585f, 18f, 18f, p)
            text(c, "PRODUKCJA", 42f, 444f, 18f, true, Color.WHITE)
            text(c, "⚙ Żelazo     +${"%.2f".format(0.55f + powerLevel * 0.22f)} / s", 42f, 476f, 14f, false, Color.LTGRAY)
            text(c, "💧 Woda       +${"%.2f".format(0.32f + powerLevel * 0.12f)} / s", 42f, 502f, 14f, false, Color.LTGRAY)
            text(c, "❄ Lód         +${"%.2f".format(0.18f + powerLevel * 0.07f)} / s", 42f, 528f, 14f, false, Color.LTGRAY)
            text(c, "✦ Kryształy   +${"%.2f".format(0.08f + researchBaseLevel * 0.01f)} / s", 42f, 554f, 14f, false, Color.LTGRAY)

            button(c, "WRÓĆ DO BAZY", width / 2f - 130f, height - 78f, 260f, 48f) {
                screen = "base"
            }
        }

        private fun colonistCapacity(): Int = 2 + habitatLevel * 4

        private fun recruitColonist() {
            if (!peopleUnlocked || colonyPopulation >= colonistCapacity()) return
            val cost = 900 + colonyPopulation * 250
            if (credits < cost || food < 2f || water < 2f) return
            credits -= cost
            food -= 2f
            water -= 2f
            colonyPopulation += 1
            engineers = min(engineers + 1, colonyPopulation)
            saveState()
        }

        private fun assignEngineer() {
            if (engineers + pilots + scientists >= colonyPopulation || engineers >= colonyPopulation) return
            engineers += 1
            saveState()
        }

        private fun assignPilot() {
            if (engineers + pilots + scientists >= colonyPopulation || pilots >= colonyPopulation) return
            pilots += 1
            saveState()
        }

        private fun assignScientist() {
            if (engineers + pilots + scientists >= colonyPopulation || scientists >= colonyPopulation) return
            scientists += 1
            saveState()
        }

        private fun sendColonists() {
            if (!peopleUnlocked || colonistMissionPlanet >= 0 || selectedPlanet == 0) return
            val free = (colonyPopulation - colonistsSent).coerceAtLeast(0)
            if (free <= 0 || !isPlanetUnlocked(selectedPlanet)) return
            if (pilots <= 0 || engineers <= 0) return
            val count = min(2, free)
            colonistsSent += count
            colonistMissionPlanet = selectedPlanet
            saveState()
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

        private fun isRiskyForRocket(index: Int): Boolean =
            planets[index].targetAltitude > recommendedRange()

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
            text(c, "ZBIORNIK: ${fuelCapacity().toInt()}%   ZASIĘG ZALECANY: ${recommendedRange().toInt()} km", 24f, 174f, 12f, true, Color.rgb(120,220,255))

            button(
                c,
                if (fuelTankBonus >= 4) "ZBIORNIK MAX" else "+25% PALIWA  •  ${350 + fuelTankBonus * 250} CR",
                width - 190f,
                115f,
                166f,
                42f,
                enabled = fuelTankBonus < 4 && credits >= 350 + fuelTankBonus * 250
            ) { buyFuelPack() }

            if (isRiskyDestination()) {
                text(c, "⚠ RYZYKOWNY LOT — ten cel przekracza zalecany zasięg", 24f, height - 150f, 12f, true, Color.rgb(255,180,80))
            }

            val cols = 2
            val cardW = (width - 54f) / 2f
            val cardH = 88f
            val pageSize = 6
            val pageCount = (planets.size + pageSize - 1) / pageSize
            planetPage = planetPage.coerceIn(0, pageCount - 1)
            val pageStart = planetPage * pageSize
            val pageEnd = min(planets.size, pageStart + pageSize)

            planets.forEachIndexed { i, planet ->
                if (i !in pageStart until pageEnd) return@forEachIndexed
                val local = i - pageStart
                val col = local % cols
                val row = local / cols
                val x = 18f + col * (cardW + 18f)
                val y = 195f + row * 96f

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
                        unlocked && i == 1 && isRiskyForRocket(i) -> "RYZYKOWNA — DOKUP PALIWO"
                        unlocked -> "DOSTĘPNA"
                        i == 1 -> "POWRÓT NA KSIĘŻYC / START"
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

            button(
                c,
                "‹",
                18f,
                height - 118f,
                70f,
                44f,
                enabled = planetPage > 0
            ) {
                planetPage = max(0, planetPage - 1)
            }

            text(
                c,
                "STRONA ${planetPage + 1} / $pageCount",
                width / 2f - 55f,
                height - 91f,
                13f,
                true,
                Color.LTGRAY
            )

            button(
                c,
                "›",
                width - 88f,
                height - 118f,
                70f,
                44f,
                enabled = planetPage < pageCount - 1
            ) {
                planetPage = min(pageCount - 1, planetPage + 1)
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

        private fun colonists(c: Canvas) {
            backButton(c, "KOLONIŚCI")
            creditsBadge(c)

            text(c, "CENTRUM KOLONISTÓW", 24f, 112f, 25f, true, Color.WHITE)
            text(c, "Zarządzaj mieszkańcami, specjalizacją i ekspedycjami.", 24f, 138f, 13f, false, Color.LTGRAY)

            val capacity = colonistCapacity()
            text(c, "POPULACJA  $colonyPopulation / $capacity", 24f, 176f, 19f, true, Color.rgb(90,220,255))
            text(c, "Inżynierowie: $engineers   Piloci: $pilots   Naukowcy: $scientists", 24f, 202f, 13f, false, Color.LTGRAY)
            text(c, "Na ekspedycji: $colonistsSent", 24f, 224f, 13f, false, Color.LTGRAY)

            val recruitCost = 900 + colonyPopulation * 250
            button(c, "REKRUTUJ • $recruitCost CR", 24f, 246f, width - 48f, 52f,
                enabled = colonyPopulation < capacity && credits >= recruitCost && food >= 2f && water >= 2f) {
                recruitColonist()
            }

            text(c, "SPECJALIZACJA", 24f, 336f, 18f, true, Color.WHITE)
            button(c, "+ INŻYNIER", 24f, 354f, width / 2f - 32f, 48f, enabled = engineers + pilots + scientists < colonyPopulation) { assignEngineer() }
            button(c, "+ PILOT", width / 2f + 8f, 354f, width / 2f - 32f, 48f, enabled = engineers + pilots + scientists < colonyPopulation) { assignPilot() }
            button(c, "+ NAUKOWIEC", 24f, 410f, width - 48f, 48f, enabled = engineers + pilots + scientists < colonyPopulation) { assignScientist() }

            text(c, "EKSPEDYCJA KOLONIJNA", 24f, 490f, 18f, true, Color.WHITE)
            val target = planets[selectedPlanet]
            text(c, "CEL: ${target.name.uppercase()}", 24f, 518f, 16f, true, Color.rgb(100,220,255))
            if (colonistMissionPlanet >= 0) {
                text(c, "EKSPEDYCJA W DRODZE: ${planets[colonistMissionPlanet].name}", 24f, 544f, 13f, false, Color.rgb(100,230,150))
            } else {
                text(c, "Wymaga: minimum 1 pilota + 1 inżyniera.", 24f, 544f, 13f, false, Color.LTGRAY)
            }
            button(c, "WYŚLIJ 2 KOLONISTÓW", 24f, 566f, width - 48f, 58f,
                enabled = colonistMissionPlanet < 0 && selectedPlanet != 0 && isPlanetUnlocked(selectedPlanet) && pilots > 0 && engineers > 0 && colonistsSent < colonyPopulation) {
                sendColonists()
            }

            text(c, "Koloniści zwiększą produkcję i umożliwią tworzenie placówek na dalszych światach.", 24f, height - 34f, 11f, false, Color.GRAY)
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
                11 -> "CZARNE BURZE OBSIDII"
                12 -> "PROMIENIOWANIE SŁONECZNE"
                13 -> "ATMOSFERA ZIEMSKA"
                14 -> "BURZE BIOLOGICZNE"
                15 -> "SUPERWULKANY"
                16 -> "KRYszTAŁOWE WYŁADOWANIA"
                17 -> "CIEMNA MATERIA"
                18 -> "PAS METALI"
                else -> "NIESTABILNA ORBITA"
            }

            /*
             * KINOWE PODEJŚCIE I LĄDOWANIE.
             * Tutaj kamera przechodzi z widoku kosmicznego do powierzchni.
             */
            if (phase == "landing" || phase == "done") {

                val rawT =
                    min(
                        1f,
                        (
                            System.currentTimeMillis() - landingStart
                        ).toFloat() / landingDuration
                    )

                val t =
                    if (phase == "done") 1f else rawT

                val ease =
                    t * t * (3f - 2f * t)

                // Planetarna tarcza rośnie aż wychodzi poza ekran,
                // dzięki czemu nie ma efektu "przeskoku".
                val surfaceRadius =
                    width * (0.38f + ease * 0.62f)

                val surfaceY =
                    height * (0.92f - ease * 0.10f)

                drawPlanetVisual(
                    c,
                    selectedPlanet,
                    width / 2f,
                    surfaceY,
                    surfaceRadius
                )

                // Przy końcu zbliżenia pokazujemy powierzchnię zamiast
                // dalszego kosmicznego tła.
                if (ease > 0.48f) {
                    drawLandingGround(c, selectedPlanet, (ease - 0.48f) / 0.52f)
                }

                // Rakieta faktycznie zbliża się do powierzchni.
                val rocketApproach =
                    if (phase == "done") 1f else ease

                val rocketY =
                    height * (0.30f + rocketApproach * 0.39f)

                val rocketScale =
                    0.82f + rocketApproach * 0.32f

                val braking =
                    phase == "landing" &&
                            (landingVelocity > 115f || rawT < 0.72f)

                if (phase == "landing") {
                    drawApproachRocket(
                        c,
                        width / 2f,
                        rocketY,
                        rocketScale,
                        braking && thrust
                    )
                }

                if (phase == "landing") {
                    val remaining = max(0f, altitude)
                    val descent = abs(landingVelocity)

                    text(c, "PODEJŚCIE DO ${target.name.uppercase()}", 24f, 44f, 21f, true, Color.WHITE)
                    text(c, "Wysokość: ${"%.1f".format(remaining)} km", 24f, 76f, 17f, false, Color.LTGRAY)
                    text(
                        c,
                        "Prędkość pionowa: ${"%.0f".format(descent)} m/s",
                        24f,
                        101f,
                        17f,
                        false,
                        if (descent <= 115f) Color.rgb(100,220,120) else Color.rgb(255,100,100)
                    )
                    text(
                        c,
                        if (descent > 115f) "HAMOWANIE — ZMNIEJSZ PRĘDKOŚĆ"
                        else "PODEJŚCIE STABILNE — KONTYNUUJ HAMOWANIE",
                        24f,
                        126f,
                        15f,
                        true,
                        if (descent > 115f) Color.rgb(255,110,90) else Color.rgb(110,230,150)
                    )
                    text(
                        c,
                        "PALIWO ${"%.0f".format(fuel)}%   TEMP ${temperature.toInt()}°C   KADŁUB ${integrity.toInt()}%",
                        24f,
                        151f,
                        15f,
                        true,
                        Color.WHITE
                    )

                    val barLeft = 24f
                    val barRight = width - 24f
                    val barTop = 172f
                    val barBottom = 188f

                    p.color = Color.argb(100, 255, 255, 255)
                    c.drawRoundRect(barLeft, barTop, barRight, barBottom, 8f, 8f, p)

                    val safeRight =
                        barLeft +
                                (barRight - barLeft) *
                                (115f / 180f)

                    p.color = Color.argb(170, 80, 220, 110)
                    c.drawRoundRect(
                        barLeft,
                        barTop,
                        safeRight,
                        barBottom,
                        8f,
                        8f,
                        p
                    )

                    val markerX =
                        barLeft +
                                (barRight - barLeft) *
                                (landingVelocity / 180f).coerceIn(0f, 1f)

                    p.color = Color.WHITE
                    c.drawRect(
                        markerX - 3f,
                        barTop - 5f,
                        markerX + 3f,
                        barBottom + 5f,
                        p
                    )

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

                } else {
                    // Po kontakcie rakieta zostaje na powierzchni.
                    text(
                        c,
                        "LĄDOWANIE ZAKOŃCZONE",
                        width / 2f - 150f,
                        height * .66f,
                        20f,
                        true,
                        Color.rgb(120, 240, 170)
                    )

                    drawRocket(
                        c,
                        width / 2f,
                        height * .64f,
                        1.15f
                    )

                    drawBaseBitmap(
                        c,
                        width / 2f,
                        height * .88f,
                        width * .48f
                    )

                    if (hasGroundColony(selectedPlanet)) {
                        drawColonists(
                            c,
                            width * .28f,
                            height * .88f,
                            width * .24f
                        )
                    }

                    val msg =
                        if (selectedPlanet == 0) {
                            "LĄDOWANIE NA KSIĘŻYCU — KOLONIŚCI ODBLOKOWANI"
                        } else {
                            "KOLONIA ZAŁOŻONA NA ${target.name.uppercase()}"
                        }

                    p.color = Color.argb(205, 5, 10, 20)
                    c.drawRoundRect(
                        width * .04f,
                        height * .72f,
                        width * .96f,
                        height * .96f,
                        20f,
                        20f,
                        p
                    )

                    p.textSize = 20f
                    text(
                        c,
                        msg,
                        width / 2f - p.measureText(msg) / 2f,
                        height * .79f,
                        20f,
                        true,
                        Color.WHITE
                    )

                    if (selectedPlanet == 0 && peopleUnlocked) {
                        text(
                            c,
                            "TRANSPORT LUDZI: ODBLOKOWANY",
                            width / 2f - 125f,
                            height * .835f,
                            17f,
                            true,
                            Color.rgb(100, 220, 140)
                        )
                    } else {
                        text(
                            c,
                            "Nagroda: +${target.reward} CR  •  +${2 + selectedPlanet} NAUKI",
                            width / 2f - 90f,
                            height * .835f,
                            18f,
                            false,
                            Color.rgb(255, 210, 90)
                        )
                    }

                    if (landedOn.contains(selectedPlanet)) {
                        button(
                            c,
                            "OTWÓRZ BAZĘ • ${target.name.uppercase()}",
                            width / 2f - 150f,
                            height * .855f,
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
                        height * .925f,
                        300f,
                        50f
                    ) {
                        screen = "menu"
                    }
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

            p.color = Color.argb(175, 4, 13, 28)
            c.drawRoundRect(16f, 18f, width - 16f, 112f, 18f, 18f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f
            p.color = Color.argb(150, 80, 180, 240)
            c.drawRoundRect(16f, 18f, width - 16f, 112f, 18f, 18f, p)
            p.style = Paint.Style.FILL

            text(c, "MISJA: ${target.name.uppercase()}", 30f, 48f, 19f, true, Color.WHITE)
            text(c, "CEL ${target.targetAltitude.toInt()} km", 30f, 75f, 14f, true, Color.rgb(130,220,255))
            text(c, "RAKIETA ${rocketLevel}/20", width - 150f, 48f, 14f, true, Color.rgb(255,215,110))
            text(c, "ZAŁOGA $selectedCrew", width - 150f, 75f, 14f, true, Color.LTGRAY)

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
                "TEMP  ${"%.0f".format(temperature)}°C",
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
                "INTEGRALNOŚĆ  ${"%.0f".format(integrity)}%",
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
             * PERSPEKTYWA LOTU DO CELU.
             * Planeta jest widoczna od początku, a przy zbliżaniu
             * gwałtowniej rośnie, żeby gracz naprawdę czuł odległość.
             */
            val approach = prog.coerceIn(0f, 1f)
            val zoom = (approach * approach * (3f - 2f * approach))
            approachPulse += 0.04f * (1.4f + approach * 2.2f)

            val planetY =
                235f +
                        (1f - approach) * 210f -
                        zoom * 28f

            val planetR =
                24f +
                        zoom * min(width * 0.22f, 150f)

            drawPlanetVisual(
                c,
                selectedPlanet,
                width / 2f,
                planetY,
                planetR
            )

            // Delikatny efekt "prędkości" gwiazd w kierunku celu.
            if (approach > 0.08f && phase == "flight") {
                p.color = Color.argb(
                    (45 + zoom * 70f).toInt().coerceIn(0, 120),
                    180, 220, 255
                )
                for (i in 0 until 16) {
                    val sx = ((i * 137 + 41) % 1000) / 1000f * width
                    val sy = ((i * 83 + 17) % 1000) / 1000f * height
                    val len = 5f + zoom * 32f
                    c.drawLine(sx, sy, sx, sy + len, p)
                }
            }

            // WEJŚCIE W ATMOSFERĘ — końcówka lotu dostaje własną scenę:
            // poświata, turbulencje i smugi wokół rakiety.
            val atmosphere =
                ((approach - 0.68f) / 0.32f).coerceIn(0f, 1f)

            if (atmosphere > 0f && phase == "flight") {
                val pulse =
                    (0.55f + 0.45f *
                            kotlin.math.sin(approachPulse * 5.5f))
                        .coerceIn(0f, 1f)

                val shake =
                    (atmosphere * (2.5f + pulse * 5f))

                // Ciepła poświata za rakietą.
                p.color = Color.argb(
                    (45 + atmosphere * 105).toInt().coerceIn(0, 170),
                    255, 105, 45
                )
                c.drawCircle(
                    width / 2f,
                    height * 0.67f,
                    18f + atmosphere * 30f + pulse * 8f,
                    p
                )

                // Smugi wejścia w atmosferę.
                p.strokeWidth = 3f + atmosphere * 4f
                for (i in 0 until 11) {
                    val side = if (i % 2 == 0) -1f else 1f
                    val spread = 18f + i * 7f
                    val sx = width / 2f + side * spread
                    val sy = height * 0.53f + i * 20f
                    val ex = width / 2f + side * (spread + 12f + atmosphere * 22f)
                    val ey = sy + 28f + atmosphere * 45f
                    c.drawLine(sx, sy, ex, ey, p)
                }

                // Delikatne drganie kamery przy wejściu.
                c.save()
                c.translate(
                    (kotlin.math.sin(approachPulse * 8f) * shake).toFloat(),
                    (kotlin.math.cos(approachPulse * 10f) * shake * 0.45f).toFloat()
                )

                drawRocket(
                    c,
                    width / 2f,
                    height * 0.62f,
                    0.95f + atmosphere * 0.12f
                )
                c.restore()

                p.strokeWidth = 1f

                val entryColor =
                    if (atmosphere > 0.72f)
                        Color.rgb(255, 125, 70)
                    else
                        Color.rgb(255, 205, 110)

                text(
                    c,
                    if (atmosphere > 0.72f)
                        "WEJŚCIE W ATMOSFERĘ — HAMUJ"
                    else
                        "ZBLIŻENIE DO ATMOSFERY",
                    width / 2f - 155f,
                    height * 0.42f,
                    17f,
                    true,
                    entryColor
                )

                text(
                    c,
                    "TURBULENCJE ${((atmosphere * 100f).toInt())}%",
                    width / 2f - 82f,
                    height * 0.46f,
                    13f,
                    false,
                    Color.LTGRAY
                )
            }

            text(
                c,
                "${target.name} • pozostało ${
                    max(
                        0f,
                        target.targetAltitude - altitude
                    ).toInt()
                } km",
                width / 2f - 100f,
                planetY + planetR + 30f,
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
                        1.05f
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
                1.05f
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
