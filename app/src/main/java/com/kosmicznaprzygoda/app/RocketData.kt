package com.kosmicznaprzygoda.app

data class Rocket(
    val id: Int,
    val name: String,
    val imageName: String,
    val price: Int,
    val fuelCapacity: Float,
    val thrust: Float,
    val maxSpeed: Float,
    val maxAltitude: Float,
    val cargo: Int,
    val researchRequired: Int,
    val description: String
)

object RocketDatabase {

    val rockets = listOf(
        Rocket(1, "PIONIER", "rocket_pionier", 0, 100f, 1.0f, 120f, 50f, 2, 0,
            "Pierwsza rakieta programu kosmicznego."),

        Rocket(2, "MAŁY BADACZ", "rocket_maly_badacz", 2500, 130f, 1.15f, 145f, 70f, 3, 0,
            "Lekka rakieta do pierwszych misji."),

        Rocket(3, "EXPLORER I", "rocket_explorer_1", 5000, 160f, 1.3f, 175f, 90f, 4, 5,
            "Pierwsza rakieta dalekiego zasięgu."),

        Rocket(4, "ORBITER", "rocket_orbiter", 8500, 190f, 1.45f, 210f, 115f, 5, 10,
            "Rakieta zdolna do osiągania wysokiej orbity."),

        Rocket(5, "HERMES", "rocket_hermes", 13000, 225f, 1.6f, 250f, 140f, 6, 15,
            "Szybka jednostka ekspedycyjna."),

        Rocket(6, "GALAKTYK", "rocket_galaktyk", 18000, 260f, 1.8f, 290f, 165f, 7, 20,
            "Rakieta do pierwszych wypraw międzyplanetarnych."),

        Rocket(7, "ATLAS", "rocket_atlas", 24000, 300f, 2.0f, 330f, 195f, 9, 25,
            "Ciężka rakieta transportowa."),

        Rocket(8, "SHADOW", "rocket_shadow", 30000, 340f, 2.2f, 370f, 225f, 10, 30,
            "Zaawansowana rakieta zwiadowcza."),

        Rocket(9, "NOVA", "rocket_nova", 38000, 380f, 2.4f, 420f, 260f, 12, 35,
            "Nowa generacja jednostek kolonizacyjnych."),

        Rocket(10, "KOSMOS 1", "rocket_kosmos_1", 47000, 430f, 2.6f, 470f, 300f, 14, 40,
            "Przełomowa rakieta programu Kosmiczna Przygoda."),

        Rocket(11, "TITAN", "rocket_titan", 58000, 480f, 2.85f, 520f, 345f, 17, 45,
            "Potężna jednostka transportowa."),

        Rocket(12, "VULCAN", "rocket_vulcan", 70000, 540f, 3.1f, 575f, 395f, 20, 50,
            "Ciężka rakieta ekspedycyjna."),

        Rocket(13, "POSEJDON", "rocket_posejdon", 85000, 600f, 3.4f, 630f, 450f, 23, 55,
            "Jednostka do ekstremalnych misji."),

        Rocket(14, "ARES", "rocket_ares", 100000, 670f, 3.7f, 690f, 510f, 26, 60,
            "Rakieta klasy kolonizacyjnej."),

        Rocket(15, "NEMEZIS", "rocket_nemezis", 120000, 750f, 4.0f, 760f, 575f, 30, 65,
            "Eksperymentalna rakieta dalekiego zasięgu."),

        Rocket(16, "FENIKS", "rocket_feniks", 145000, 840f, 4.35f, 835f, 650f, 34, 70,
            "Nowoczesna jednostka wielokrotnego wykorzystania."),

        Rocket(17, "AURORA", "rocket_aurora", 175000, 940f, 4.7f, 920f, 730f, 38, 75,
            "Zaawansowany system napędowy."),

        Rocket(18, "ECLIPSE", "rocket_eclipse", 210000, 1050f, 5.1f, 1010f, 820f, 43, 80,
            "Jedna z najpotężniejszych rakiet programu."),

        Rocket(19, "GAJA", "rocket_gaja", 250000, 1180f, 5.5f, 1110f, 920f, 48, 90,
            "Rakieta stworzona dla wielkich kolonii."),

        Rocket(20, "STELLARIS", "rocket_stellaris", 300000, 1350f, 6.0f, 1250f, 1050f, 55, 100,
            "Najbardziej zaawansowana rakieta w programie.")
    )

    fun getRocket(id: Int): Rocket {
        return rockets.firstOrNull { it.id == id } ?: rockets.first()
    }
}
