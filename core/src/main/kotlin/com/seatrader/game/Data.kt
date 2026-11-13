package com.seatrader.game

/** Как выглядит корабль. Цвета — 0xRRGGBB. */
data class Look(
    val len: Float, val width: Float, val masts: Int,
    val hull: Int, val trim: Int, val sail: Int, val flag: Int,
)

class ShipDef(
    val id: String, val name: String, val cls: String, val desc: String,
    val hp: Int, val speed: Float, val turn: Float, val cannons: Int, val dmg: Float,
    val reload: Float, val range: Float, val mass: Float,
    val price: Int, val gems: Int, val look: Look,
)

val PLAYER_SHIPS = listOf(
    ShipDef("sloop", "Чайка", "Шлюп", "Лёгкий и послушный кораблик для первых вылазок.",
        120, 15f, 1.9f, 2, 10f, 1.8f, 42f, 1.0f, 0, 0,
        Look(12f, 4.2f, 1, 0x7a4a26, 0x2f6db5, 0xf6f0e0, 0x2f6db5)),
    ShipDef("brigantine", "Ласточка", "Бригантина", "Быстрая двухмачтовая. Отлично таранит.",
        200, 16.5f, 1.75f, 3, 12f, 1.7f, 46f, 1.4f, 1200, 0,
        Look(15f, 5f, 2, 0x6e3f22, 0x2a9d8f, 0xf6f0e0, 0x2a9d8f)),
    ShipDef("corvette", "Буревестник", "Корвет", "Самый быстрый корабль. Догонит кого угодно.",
        280, 19f, 1.8f, 4, 14f, 1.5f, 50f, 1.6f, 3500, 0,
        Look(17f, 5.2f, 2, 0x5b3a29, 0xe9c46a, 0xfff8e7, 0xe9c46a)),
    ShipDef("frigate", "Гроза", "Фрегат", "Военный корабль: крепкий корпус и шесть пушек на борт.",
        420, 17f, 1.5f, 6, 16f, 1.5f, 55f, 2.4f, 8000, 10,
        Look(21f, 6.4f, 3, 0x4a2f1f, 0xb23a48, 0xf3ead3, 0xb23a48)),
    ShipDef("galleon", "Левиафан", "Галеон", "Плавучая крепость. Медленный, но выдерживает бурю ядер.",
        700, 14.5f, 1.15f, 9, 20f, 1.6f, 58f, 3.6f, 18000, 40,
        Look(25f, 8f, 3, 0x3d2817, 0xd4a017, 0xf1e4c3, 0xd4a017)),
    ShipDef("dragon", "Морской дракон", "Линкор", "Легенда морей. Двенадцать пушек на борт и таран, как у кита.",
        1000, 16f, 1.05f, 12, 24f, 1.4f, 62f, 5.0f, 40000, 120,
        Look(29f, 8.8f, 4, 0x2b1d14, 0x7b2cbf, 0xe9e3f5, 0x7b2cbf)),
)

class EnemyDef(
    val type: String, val name: String, val hp: Int, val speed: Float, val turn: Float, val cannons: Int,
    val dmg: Float, val reload: Float, val range: Float, val mass: Float,
    val coinsMin: Int, val coinsMax: Int, val gems: Float, val boss: Boolean, val look: Look,
)

val ENEMIES = mapOf(
    "sloop" to EnemyDef("sloop", "Пиратский шлюп", 60, 11f, 1.6f, 2, 6f, 2.4f, 38f, 0.9f, 40, 80, 0f, false,
        Look(11f, 4f, 1, 0x3b2a1e, 0x8b1a1a, 0x3a3a3a, 0x111111)),
    "brig" to EnemyDef("brig", "Пиратская бригантина", 130, 12.5f, 1.45f, 3, 9f, 2.2f, 44f, 1.4f, 90, 150, 0.08f, false,
        Look(15f, 5f, 2, 0x33241a, 0x8b1a1a, 0x2e2e2e, 0x111111)),
    "frigate" to EnemyDef("frigate", "Корсарский фрегат", 280, 13.5f, 1.3f, 5, 13f, 2.0f, 52f, 2.4f, 200, 320, 0.35f, false,
        Look(21f, 6.4f, 3, 0x2a1d15, 0x6a040f, 0x262626, 0x111111)),
    "galleon" to EnemyDef("galleon", "Чёрный галеон", 650, 11.5f, 1.0f, 8, 18f, 1.9f, 58f, 3.8f, 700, 1000, 6f, true,
        Look(26f, 8.2f, 3, 0x1a1210, 0xc9a227, 0x1c1c1c, 0x111111)),
    "flagship" to EnemyDef("flagship", "Флагман флотилии", 480, 13f, 1.25f, 6, 15f, 1.9f, 56f, 2.6f, 500, 800, 3f, true,
        Look(22f, 6.6f, 3, 0x241812, 0xffc107, 0x5d1010, 0x111111)),
    "admiral" to EnemyDef("admiral", "«Кровавая Луна» адмирала Гарпуна", 1600, 13.5f, 1.0f, 12, 22f, 1.6f, 62f, 5.2f, 4000, 5000, 40f, true,
        Look(30f, 9f, 4, 0x140c0a, 0xb71c1c, 0x7f0000, 0x000000)),
    "ghost" to EnemyDef("ghost", "Корабль-призрак", 520, 13f, 1.3f, 6, 16f, 2.0f, 56f, 2.4f, 350, 600, 4f, true,
        Look(21f, 6.4f, 3, 0x2b3a38, 0x5fe3b0, 0xa7d9c9, 0x0b1f1a)),
    "navy" to EnemyDef("navy", "Патруль флота", 340, 13f, 1.3f, 5, 13f, 2.0f, 54f, 2.4f, 150, 260, 0.3f, false,
        Look(20f, 6.2f, 3, 0x1f3a5f, 0xffd54f, 0xf5f5f5, 0x1565c0)),
    "merchant" to EnemyDef("merchant", "Торговое судно", 160, 10f, 1.2f, 0, 0f, 3f, 0f, 1.8f, 0, 0, 0f, false,
        Look(16f, 5.6f, 2, 0x8d6e4a, 0x2e7d32, 0xf5f0dc, 0x2e7d32)),
    "fisher" to EnemyDef("fisher", "Рыбацкая лодка", 40, 6f, 1.6f, 0, 0f, 3f, 0f, 0.6f, 0, 0, 0f, false,
        Look(8f, 3.2f, 1, 0x9c7a54, 0x1e88e5, 0xfaf3e0, 0x1e88e5)),
)

class Stats(
    val hp: Int, val speed: Float, val turn: Float, val cannons: Int, val dmg: Float,
    val reload: Float, val range: Float, val mass: Float,
)

class Upgrade(val id: String, val name: String, val per: Float, val text: String)

val UPGRADES = listOf(
    Upgrade("hull", "Корпус", 0.15f, "прочность"),
    Upgrade("sails", "Паруса", 0.06f, "скорость"),
    Upgrade("guns", "Пушки", 0.15f, "урон"),
    Upgrade("reload", "Заряжание", 0.08f, "скорость стрельбы"),
)
const val MAX_LEVEL = 5

fun upgradeCost(ship: ShipDef, level: Int): Int {
    val base = maxOf(150f, ship.price / 5f)
    return (Math.round(base * Math.pow((level + 1).toDouble(), 1.6) / 10) * 10).toInt()
}

/** up: уровни [корпус, паруса, пушки, заряжание] */
fun shipStats(s: ShipDef, up: IntArray?): Stats {
    val u = up ?: IntArray(4)
    return Stats(
        hp = Math.round(s.hp * (1 + 0.15f * u[0])),
        speed = s.speed * (1 + 0.06f * u[1]),
        turn = s.turn,
        cannons = s.cannons,
        dmg = s.dmg * (1 + 0.15f * u[2]),
        reload = s.reload * (1 - 0.08f * u[3]),
        range = s.range,
        mass = s.mass,
    )
}

fun enemyStats(e: EnemyDef) = Stats(e.hp, e.speed, e.turn, e.cannons, e.dmg, e.reload, e.range, e.mass)

const val WORLD = 1800f // половина размера мира

/** Регионы мира: свой вид островов, цвет воды, туман и враги. */
enum class Region(val title: String) {
    CENTER("Изумрудный архипелаг"), ICE("Ледяное море"), FIRE("Огненные острова"), REEF("Туманный риф"), ABYSS("Бездна")
}

fun regionAt(x: Float, z: Float): Region = when {
    Math.hypot(x.toDouble(), z.toDouble()) < 1050 -> Region.CENTER
    z < -Math.abs(x) * 0.9f -> Region.ICE
    x > Math.abs(z) * 0.9f -> Region.FIRE
    x < -Math.abs(z) * 0.9f -> Region.REEF
    else -> Region.ABYSS
}

class PortDef(val name: String, val x: Float, val z: Float, val r: Float)

val PORTS = listOf(
    PortDef("Гавань Ветров", 0f, 0f, 52f),
    PortDef("Коралловая бухта", 620f, -430f, 48f),
    PortDef("Форт Чайки", -640f, 300f, 55f),
    PortDef("Туманный порт", 430f, 640f, 50f),
    PortDef("Скала Ворона", -420f, -650f, 50f),
    PortDef("Северный причал", 150f, -1450f, 50f),
    PortDef("Вулканий порт", 1450f, 150f, 50f),
    PortDef("Приют контрабандистов", -1450f, -150f, 48f),
    PortDef("Последний маяк", -150f, 1450f, 50f),
)

/** Морские крепости пиратов. */
val FORTRESSES = listOf(
    floatArrayOf(-1250f, 600f), floatArrayOf(1200f, -700f), floatArrayOf(700f, -1300f), floatArrayOf(-700f, 1250f), floatArrayOf(-1150f, -900f),
)

enum class QType {
    KILL, RAM, COLLECT, REACH, UPGRADE, BUY, SERPENT, TREASURE, HIRE, ABILITY,
    BOARD, DELIVER, FORTRESS, KRAKEN, FLAGSHIP, ZONE, WRECK, PROTECT, ADMIRAL, LEVEL,
}

class Quest(
    val type: QType, val text: String, val n: Int = 1, val enemy: String = "",
    val port: Int = -1, val coins: Int = 0, val gems: Int = 0,
    /** Запись в бортовом журнале, которая показывается в начале главы. */
    val intro: String? = null, val chapter: String? = null,
    val good: Int = -1, val region: Region? = null,
)

private val QUESTS = listOf(
    // Глава 1
    Quest(QType.KILL, "Потопи пиратский шлюп", 1, "sloop", coins = 100, chapter = "Глава 1. Новый капитан",
        intro = "Бортовой журнал. Мне доверили «Чайку» — маленький шлюп и горстку матросов. Пираты адмирала Гарпуна грабят наши воды. Пора дать им отпор."),
    Quest(QType.RAM, "Протарань пиратский шлюп", 1, "sloop", coins = 150),
    Quest(QType.COLLECT, "Собери 300 монет", 300, gems = 3),
    Quest(QType.DELIVER, "Доставь 5 бочек рома в «Форт Чайки»", 5, port = 2, good = 1, coins = 250),
    Quest(QType.UPGRADE, "Улучши корабль в порту", coins = 150),
    Quest(QType.ABILITY, "Выстрели огненными ядрами", coins = 150),
    // Глава 2
    Quest(QType.KILL, "Потопи 3 пиратские бригантины", 3, "brig", coins = 300, gems = 3, chapter = "Глава 2. Тень адмирала",
        intro = "Бортовой журнал. Пленный пират проболтался: у адмирала Гарпуна целый флот и четыре тайные базы на краях моря. Нужен корабль покрепче и надёжные люди."),
    Quest(QType.BOARD, "Возьми пиратский корабль на абордаж", coins = 300, gems = 2),
    Quest(QType.TREASURE, "Найди сокровище на острове", coins = 200, gems = 2),
    Quest(QType.BUY, "Купи новый корабль на верфи", gems = 10),
    Quest(QType.HIRE, "Найми корабль сопровождения", coins = 300),
    Quest(QType.PROTECT, "Защити торговое судно от пиратов", coins = 400, gems = 3),
    Quest(QType.LEVEL, "Достигни 5 уровня капитана", 5, coins = 500),
    // Глава 3
    Quest(QType.ZONE, "Доплыви до Туманного рифа (запад)", region = Region.REEF, coins = 400, chapter = "Глава 3. Туманный риф",
        intro = "Бортовой журнал. На западе, в вечном тумане, адмирал прячет контрабанду. Под водой там острые скалы, а на дне лежат корабли тех, кто не смотрел на карту."),
    Quest(QType.WRECK, "Найди затонувший корабль (пузыри на воде)", coins = 500, gems = 3),
    Quest(QType.FORTRESS, "Разрушь пиратскую крепость", coins = 900, gems = 6),
    // Глава 4
    Quest(QType.ZONE, "Доплыви до Огненных островов (восток)", region = Region.FIRE, coins = 400, chapter = "Глава 4. Огненная гора",
        intro = "Бортовой журнал. На востоке курятся вулканы. Там пираты льют пушки и куют цепи. Говорят, по ночам там видят огромного змея."),
    Quest(QType.SERPENT, "Победи морского змея", coins = 1200, gems = 15),
    Quest(QType.FLAGSHIP, "Разгроми пиратскую флотилию (потопи флагман)", coins = 1200, gems = 8),
    // Глава 5
    Quest(QType.ZONE, "Доплыви до Ледяного моря (север)", region = Region.ICE, coins = 500, chapter = "Глава 5. Ледяное море",
        intro = "Бортовой журнал. Северные рыбаки рассказали, что флот адмирала зимует среди айсбергов. Холодно так, что паруса звенят."),
    Quest(QType.KILL, "Потопи 3 корсарских фрегата", 3, "frigate", coins = 1500, gems = 8),
    Quest(QType.DELIVER, "Доставь 10 мешков пороха в «Северный причал»", 10, port = 5, good = 5, coins = 1500),
    // Глава 6
    Quest(QType.ZONE, "Спустись в Бездну (юг)", region = Region.ABYSS, coins = 600, chapter = "Глава 6. Бездна",
        intro = "Бортовой журнал. В южной Бездне обитает кракен. Адмирал кормит его пленниками. Чтобы добраться до логова Гарпуна, придётся пройти мимо чудовища."),
    Quest(QType.KRAKEN, "Победи кракена", coins = 3000, gems = 25),
    Quest(QType.LEVEL, "Достигни 15 уровня капитана", 15, coins = 2000),
    // Финал
    Quest(QType.ADMIRAL, "Потопи «Кровавую Луну» адмирала Гарпуна", coins = 8000, gems = 60, chapter = "Финал. Последняя битва",
        intro = "Бортовой журнал. Мы нашли его. «Кровавая Луна» — двенадцать пушек на борт и лучшая команда пиратов. Сегодня всё решится."),
)

const val STORY_LENGTH = 27

fun questAt(i: Int): Quest {
    if (i < QUESTS.size) return QUESTS[i]
    val k = i - QUESTS.size
    val lvl = k / 8 + 1
    return when (k % 8) {
        0 -> Quest(QType.KILL, "Потопи ${3 + lvl} пиратских бригантин", 3 + lvl, "brig", coins = 400 * lvl, gems = 3,
            chapter = if (k == 0) "Вольное плавание" else null,
            intro = if (k == 0) "Бортовой журнал. Адмирал Гарпун повержен! Но пиратов в море меньше не стало. Вольное плавание продолжается." else null)
        1 -> Quest(QType.TREASURE, "Найди сокровище на острове", coins = 300 * lvl, gems = 3)
        2 -> Quest(QType.BOARD, "Возьми на абордаж 2 корабля", 2, coins = 600 * lvl, gems = 4)
        3 -> Quest(QType.DELIVER, "Доставь 15 рулонов шёлка в «Вулканий порт»", 15, port = 6, good = 4, coins = 1200 * lvl)
        4 -> Quest(QType.FORTRESS, "Разрушь пиратскую крепость", coins = 1000 * lvl, gems = 6)
        5 -> Quest(QType.SERPENT, "Победи морского змея", coins = 1500 * lvl, gems = 15)
        6 -> Quest(QType.FLAGSHIP, "Разгроми пиратскую флотилию", coins = 1500 * lvl, gems = 8)
        else -> Quest(QType.KRAKEN, "Победи кракена", coins = 3000 * lvl, gems = 25)
    }
}

// ---------- Торговля ----------
class Good(val name: String, val base: Int)

val GOODS = listOf(
    Good("Зерно", 12), Good("Ром", 30), Good("Ткани", 45), Good("Специи", 80), Good("Шёлк", 130), Good("Порох", 60),
)

/** Множители цен в портах: < 1 — здесь дёшево (производят), > 1 — дорого (спрос). */
val PORT_PRICES = listOf(
    floatArrayOf(1.0f, 1.0f, 1.0f, 1.1f, 1.2f, 1.0f),
    floatArrayOf(0.7f, 0.6f, 1.3f, 1.2f, 1.4f, 1.1f),
    floatArrayOf(1.3f, 1.4f, 0.7f, 1.3f, 1.1f, 0.8f),
    floatArrayOf(0.6f, 1.2f, 1.2f, 0.7f, 1.3f, 1.4f),
    floatArrayOf(1.4f, 0.8f, 1.1f, 1.5f, 0.7f, 1.2f),
    floatArrayOf(1.8f, 1.6f, 1.5f, 1.4f, 1.3f, 1.7f),
    floatArrayOf(1.5f, 1.3f, 1.6f, 0.6f, 1.6f, 0.6f),
    floatArrayOf(1.2f, 0.7f, 1.0f, 1.1f, 0.6f, 1.3f),
    floatArrayOf(1.6f, 1.5f, 1.7f, 1.6f, 1.5f, 1.2f),
)

/** Вместимость трюма по кораблям. */
val CARGO = mapOf("sloop" to 20, "brigantine" to 35, "corvette" to 30, "frigate" to 45, "galleon" to 90, "dragon" to 70)

// ---------- Капитан ----------
class Skill(val id: String, val name: String, val desc: String)

val SKILLS = listOf(
    Skill("aim", "Меткость", "Ядра ложатся кучнее: -12% разброса"),
    Skill("gunner", "Канонир", "+6% урона пушек"),
    Skill("carpenter", "Плотник", "+6% прочности и быстрее починка"),
    Skill("boatswain", "Боцман", "-5% времени перезарядки"),
    Skill("navigator", "Штурман", "+3% скорости и +5% поворотливости"),
    Skill("boarding", "Абордаж", "+15% силы абордажной команды"),
    Skill("trader", "Торговец", "Цены на 4% выгоднее"),
    Skill("luck", "Удача", "+8% добычи с кораблей и сокровищ"),
)
const val MAX_SKILL = 5

/** Опыт, нужный для перехода с уровня n на n+1. */
fun xpForLevel(n: Int) = (100 * Math.pow(n.toDouble(), 1.4)).toInt()

val XP_REWARD = mapOf("sloop" to 20, "brig" to 40, "frigate" to 80, "galleon" to 200, "flagship" to 150, "admiral" to 1500)

// ---------- Достижения ----------
class Achievement(val id: String, val name: String, val desc: String, val stat: String, val goal: Int, val gems: Int)

val ACHIEVEMENTS = listOf(
    Achievement("bounty3", "Охотник за головами", "Поймать 3 разыскиваемых капитанов", "bounties", 3, 10),
    Achievement("ghost", "Изгоняющий призраков", "Потопить корабль-призрак", "ghosts", 1, 5),
    Achievement("lands10", "Исследователь", "Высадиться на 10 островах", "landings", 10, 6),
    Achievement("treasure", "Кладоискатель", "Найти клад по карте", "treasures", 1, 8),
    Achievement("kills10", "Гроза пиратов", "Потопить 10 пиратов", "kills", 10, 5),
    Achievement("kills100", "Морской волк", "Потопить 100 пиратов", "kills", 100, 25),
    Achievement("rams10", "Таранщик", "Потопить 10 кораблей тараном", "rams", 10, 8),
    Achievement("board5", "Абордажник", "Взять на абордаж 5 кораблей", "boards", 5, 8),
    Achievement("treasure5", "Кладоискатель", "Найти 5 сокровищ", "treasures", 5, 6),
    Achievement("wreck5", "Ныряльщик", "Обыскать 5 затонувших кораблей", "wrecks", 5, 6),
    Achievement("miles", "Бывалый мореход", "Пройти 20 000 м", "distance", 20000, 6),
    Achievement("trade", "Купец", "Заработать торговлей 5 000 монет", "profit", 5000, 8),
    Achievement("level10", "Опытный капитан", "Достичь 10 уровня", "level", 10, 10),
    Achievement("serpent", "Змееборец", "Победить морского змея", "serpents", 1, 10),
    Achievement("kraken", "Укротитель бездны", "Победить кракена", "krakens", 1, 20),
    Achievement("fort", "Штурм крепости", "Разрушить пиратскую крепость", "forts", 1, 10),
    Achievement("fleet", "Командор", "Победить флотилию", "flagships", 1, 10),
    Achievement("protect", "Защитник торговли", "Спасти 3 купца", "protects", 3, 8),
    Achievement("admiral", "Победитель адмирала", "Потопить «Кровавую Луну»", "admirals", 1, 50),
    Achievement("dragon", "Легенда морей", "Купить линкор «Морской дракон»", "dragon", 1, 30),
)

/** 2900 -> "2.9K" */
fun fmt(n: Int): String = when {
    n >= 1_000_000 -> trimZero(n / 1_000_000f) + "M"
    n >= 1_000 -> trimZero(n / 1_000f) + "K"
    else -> n.toString()
}

private fun trimZero(v: Float): String {
    val s = String.format(java.util.Locale.US, "%.1f", Math.floor(v * 10.0) / 10.0)
    return if (s.endsWith(".0")) s.dropLast(2) else s
}

/** Сила волн: 1 — штиль, до ~2.6 — шторм. Общая для физики и шейдера воды. */
@Volatile var waveAmp = 1f

fun waveHeight(x: Float, z: Float, t: Float): Float =
    ((Math.sin(x * 0.08 + t * 1.1) * 0.35 + Math.sin(z * 0.11 + t * 0.9) * 0.3 +
        Math.sin((x + z) * 0.05 + t * 0.7) * 0.4 + Math.sin((x - z) * 0.031 + t * 0.55) * 0.35) * waveAmp).toFloat()

// ---------- Способности ----------
class Ability(val id: String, val name: String, val desc: String, val cooldown: Float, val duration: Float)

class Weapon(val id: String, val name: String, val desc: String, val cooldown: Float, val price: Int, val gems: Int)

val ABILITIES = listOf(
    Ability("fire", "Огненные ядра", "Залпы поджигают врага: урон со временем.", 14f, 7f),
    Ability("chain", "Цепные ядра", "Рвут паруса: враг сильно замедляется.", 16f, 7f),
    Ability("repair", "Ремонт", "Быстро чинит корпус прямо в бою.", 28f, 3f),
    Ability("mortar", "Мортира", "Тяжёлый снаряд по навесной дуге в ближайшего врага, урон по площади.", 10f, 0f),
    Ability("rockets", "Ракеты", "Залп из 6 зажигательных ракет вперёд.", 14f, 0f),
    Ability("mines", "Мины", "Сбрасывает 3 мины за кормой.", 18f, 0f),
)

/** Оружие, которое нужно купить в арсенале (индексы в ABILITIES). */
val WEAPON_PRICES = mapOf(3 to Pair(2500, 0), 4 to Pair(4000, 5), 5 to Pair(3000, 3))

// ---------- Наёмники ----------
class Mercenary(val id: String, val name: String, val price: Int, val enemyLook: String,
                val hp: Int, val speed: Float, val cannons: Int, val dmg: Float)

val MERCS = listOf(
    Mercenary("merc_sloop", "Шлюп-наёмник", 600, "sloop", 110, 16f, 2, 7f),
    Mercenary("merc_brig", "Бригантина-наёмник", 2000, "brig", 220, 16.5f, 3, 10f),
    Mercenary("merc_frigate", "Фрегат-наёмник", 6000, "frigate", 420, 17f, 5, 14f),
)
const val MAX_ALLIES = 2

// ---------- Внешний вид ----------
class Paint(val name: String, val color: Int, val price: Int)

val SAIL_COLORS = listOf(
    Paint("Классика", 0xf6f0e0, 0), Paint("Алые", 0xc62828, 400), Paint("Небесные", 0x64b5f6, 400),
    Paint("Изумрудные", 0x2e7d32, 600), Paint("Золотые", 0xffc107, 900), Paint("Королевские", 0x6a1b9a, 1200),
    Paint("Чёрные", 0x262626, 1500),
)
val FLAG_COLORS = listOf(
    Paint("Свой", -1, 0), Paint("Алый", 0xd32f2f, 200), Paint("Синий", 0x1565c0, 200), Paint("Зелёный", 0x2e7d32, 200),
    Paint("Золотой", 0xffb300, 300), Paint("Белый", 0xfafafa, 300), Paint("Фиолетовый", 0x7b1fa2, 300),
)

// ---------- Ежедневные награды ----------
class DailyReward(val coins: Int, val gems: Int)
val DAILY = listOf(DailyReward(100, 0), DailyReward(200, 0), DailyReward(300, 1), DailyReward(400, 0),
    DailyReward(500, 2), DailyReward(700, 0), DailyReward(1000, 10))

/** Что меняется во внешнем виде корабля на каждом уровне улучшения [корпус, паруса, пушки, заряжание]. */
val UPGRADE_LOOKS = listOf(
    listOf("железные пояса по корпусу", "бронированная корма с заклёпками", "броневые плиты на бортах", "железный таран на носу", "золочёная кромка борта"),
    listOf("вымпел на грот-мачте", "вымпелы на всех мачтах", "верхние паруса (брамсели)", "лисели и второй кливер", "золотой штандарт"),
    listOf("стволы длиннее, с кольцами", "бронзовые пушки на лафетах", "крупный калибр", "стальные пушки и вертлюги", "нижняя батарейная палуба"),
    listOf("ящики с ядрами на палубе", "пирамида ядер", "вторая пирамида ядер", "бочки с порохом", "запас пороха на корме"),
)

// ---------- Офицеры ----------
val ROLES = listOf("Боцман", "Канонир", "Штурман", "Квартирмейстер")

/** look: кожа, волосы, шляпа (0 нет, 1 треуголка, 2 бандана, 3 картуз), цвет шляпы, борода (0..2), повязка на глаз, камзол, серьга. */
class Officer(
    val id: Int, val name: String, val role: Int, val ports: IntArray, val price: Int, val gems: Int,
    val bonusText: String, val story: String, val quips: List<String>, val look: IntArray,
    val dmg: Float = 0f, val reload: Float = 0f, val speed: Float = 0f, val turn: Float = 0f, val range: Float = 0f,
    val hp: Float = 0f, val regen: Float = 0f, val board: Float = 0f, val trade: Float = 0f, val loot: Float = 0f,
)

val OFFICERS = listOf(
    Officer(0, "Гром Бородач", 1, intArrayOf(0, 3), 900, 0, "+15% урона пушек",
        "Бывший пушкарь королевского флота. Оглох на одно ухо, зато по звуку залпа узнаёт калибр.",
        listOf("Ха! Прямо в борт!", "Пушки любят чистоту и порох!", "Ещё залп — и они пойдут ко дну!"),
        intArrayOf(0xe0ac80, 0x5d4037, 1, 0x212121, 2, 0, 0x8b1a1a, 0), dmg = 0.15f),
    Officer(1, "Ядвига Фитиль", 1, intArrayOf(2, 6), 1400, 2, "-15% время перезарядки",
        "Мастер пороховых смесей с Огненных островов. Перезаряжает быстрее, чем вы моргнёте.",
        listOf("Заряжено! Огонь!", "Порох сухой, руки быстрые!", "Ещё немного — и будет жарко."),
        intArrayOf(0xf1c9a5, 0xd84315, 2, 0x2e7d32, 0, 0, 0x4e342e, 1), reload = 0.15f),
    Officer(2, "Мира Сокол", 2, intArrayOf(1, 5), 1000, 0, "+10% скорости и поворота",
        "Выросла на маяке и читает ветер, как открытую книгу. Ни разу не садилась на мель.",
        listOf("Ветер меняется — держим курс!", "Чую шторм за горизонтом.", "Право руля, капитан!"),
        intArrayOf(0xc68e64, 0x212121, 2, 0x1565c0, 0, 0, 0x1e88e5, 1), speed = 0.1f, turn = 0.1f),
    Officer(3, "Старик Ось", 2, intArrayOf(4, 8), 1300, 0, "+12% дальности стрельбы, +4% скорости",
        "Полвека у штурвала. Говорят, он рассчитал курс в Бездну и обратно — дважды.",
        listOf("В мои годы ядра летали дальше!", "Видишь ту звезду? Нам туда.", "Не торопись, море не любит суеты."),
        intArrayOf(0xe8c4a0, 0xeeeeee, 3, 0x37474f, 1, 0, 0x455a64, 0), range = 0.12f, speed = 0.04f),
    Officer(4, "Хэмиш Дубовый", 0, intArrayOf(0, 4), 900, 0, "+15% прочности, починка вне боя",
        "Плотник и боцман. Залатает пробоину киянкой и честным словом.",
        listOf("Залатаем, капитан, не впервой!", "Палуба скрипит — значит, жива.", "Держитесь крепче, ребята!"),
        intArrayOf(0xd9a066, 0xa1887f, 0, 0, 2, 0, 0x6d4c41, 0), hp = 0.15f, regen = 2f),
    Officer(5, "Брат Амвросий", 0, intArrayOf(3, 7), 1600, 0, "+30% силы абордажа, +8% прочности",
        "Бывший монах, ныне гроза абордажных схваток. Молится громко, бьёт ещё громче.",
        listOf("С нами удача и абордажный топор!", "На абордаж, братья!", "Да простит их море."),
        intArrayOf(0xd7a27a, 0x3e2723, 0, 0, 1, 0, 0x5d4037, 0), board = 0.3f, hp = 0.08f),
    Officer(6, "Лис Валентин", 3, intArrayOf(1, 7), 1100, 0, "Цены выгоднее на 8%, +10% добычи",
        "Торгаш с улыбкой честного человека. Знает цену всему, кроме совести.",
        listOf("Купим дёшево — продадим дорого!", "Каждая монета на счету.", "Я слышу звон золота!"),
        intArrayOf(0xf0c8a0, 0xbf6b2a, 1, 0x4a148c, 0, 0, 0x6a1b9a, 1), trade = 0.08f, loot = 0.1f),
    Officer(7, "Мадам Жемчуг", 3, intArrayOf(5, 6, 8), 1800, 3, "+25% добычи, больше кристаллов",
        "Хозяйка ломбардов на трёх островах. Говорят, у неё есть карта каждого клада.",
        listOf("Драгоценности любят тишину.", "Мой нюх на сокровища не подводит.", "Добыча — дело тонкое."),
        intArrayOf(0x8d5a3b, 0x111111, 1, 0xad1457, 0, 1, 0x880e4f, 1), loot = 0.25f),
)

// ---------- Охотники за головами ----------
class Captain(
    val id: Int, val name: String, val type: String, val hpMul: Float, val dmgMul: Float, val speedMul: Float,
    val trait: String, val traitText: String, val bounty: Int, val gems: Int, val story: String,
)

val CAPTAINS = listOf(
    Captain(0, "Джек Сухопутный", "brig", 2.2f, 1.3f, 1.25f, "fast", "очень быстрый", 600, 2,
        "Удирает от любого фрегата. Ограбил три каравана и ни разу не сошёл на берег."),
    Captain(1, "Мэри Пепел", "frigate", 1.8f, 1.2f, 1.05f, "fire", "стреляет огненными ядрами", 1200, 4,
        "Сожгла таможню в Коралловой бухте. Её паруса пахнут дымом."),
    Captain(2, "Барон Цепь", "frigate", 2.0f, 1.15f, 1.0f, "chain", "цепные ядра рвут паруса", 1400, 4,
        "Ловит корабли, как рыбу: сначала лишает парусов, потом забирает всё."),
    Captain(3, "Одноглазый Корт", "brig", 2.2f, 1.25f, 1.1f, "escort", "ходит со свитой из двух шлюпов", 1000, 3,
        "Никогда не воюет один. Его шлюпы налетают, как стая чаек."),
    Captain(4, "Вдова Шторма", "frigate", 2.3f, 1.2f, 1.2f, "rammer", "идёт на таран", 2000, 6,
        "Таранит всё, что движется. Нос её корабля окован сталью."),
    Captain(5, "Железный Ганс", "galleon", 2.6f, 1.25f, 0.95f, "armored", "бронированный корпус", 2600, 8,
        "Его галеон обит железом. Ядра отскакивают, как горох."),
)

// ---------- База игрока ----------
class BuildingDef(val id: String, val name: String, val baseCost: Int, val effects: List<String>, val angle: Float, val dist: Float)

const val BASE_FOUND_COST = 1500
val BUILDINGS = listOf(
    BuildingDef("dock", "Причал", 400, listOf("рыбаки приносят +10 монет/мин", "+20 монет/мин", "+30 монет/мин, швартовка флота"), 90f, 0.92f),
    BuildingDef("store", "Склад", 700, listOf("копит до 2000 монет, +8 мест в трюме", "до 3200 монет, +16 мест", "до 4400 монет, +24 места"), 30f, 0.52f),
    BuildingDef("tavern", "Таверна", 900, listOf("+20 монет/мин, найм любых офицеров", "+40 монет/мин, офицеры дешевле на 10%", "+60 монет/мин, офицеры дешевле на 20%"), 150f, 0.5f),
    BuildingDef("farm", "Плантация", 600, listOf("+45 монет/мин", "+90 монет/мин", "+135 монет/мин"), 220f, 0.5f),
    BuildingDef("lighthouse", "Маяк", 800, listOf("открывает карту вокруг базы", "дальше видно, свет ночью", "открывает огромную область"), 330f, 0.62f),
    BuildingDef("battery", "Батарея", 1100, listOf("пушки стреляют по пиратам у базы", "больше пушек и урона", "мощная крепостная батарея"), 270f, 0.62f),
    BuildingDef("yard", "Верфь", 1200, listOf("улучшения дешевле на 6%", "улучшения дешевле на 12%", "улучшения дешевле на 18%"), 0f, 0.0f),
)
fun buildingCost(i: Int, lvl: Int) = (BUILDINGS[i].baseCost * when (lvl) { 0 -> 1f; 1 -> 2.2f; else -> 4.2f }).toInt()

// ---------- Высадки на острова ----------
class Choice(val text: String, val act: String)
class Episode(val id: String, val title: String, val text: String, val choices: List<Choice>)

val LANDINGS = listOf(
    Episode("camp", "Заброшенный лагерь", "На берегу — потухший костёр, рваная парусина и следы, уходящие в джунгли. Кто-то покинул лагерь в спешке.",
        listOf(Choice("Обыскать лагерь", "camp_search"), Choice("Пойти по следам вглубь острова", "camp_track"))),
    Episode("parrot", "Говорящий попугай", "На ветке сидит облезлый попугай и хрипло повторяет: «Тр-р-ри шага от кр-р-ривой пальмы! Тр-р-ри шага!»",
        listOf(Choice("Копать у кривой пальмы", "parrot_dig"), Choice("Забрать попугая на корабль", "parrot_take"))),
    Episode("hermit", "Отшельник", "Из-за скал выходит заросший старик. Он живёт здесь двадцать лет и просит забрать его с острова.",
        listOf(Choice("Взять на борт", "hermit_take"), Choice("Оставить ему припасы (50 монет)", "hermit_food"), Choice("Уйти", "leave"))),
    Episode("cave", "Пещера контрабандистов", "В пещере у воды — ящики с ромом и бочки пороха. На стене углём нарисован череп: это тайник пиратов.",
        listOf(Choice("Забрать груз", "cave_take"), Choice("Оставить метку уважения", "cave_respect"), Choice("Сообщить флоту", "cave_report"))),
    Episode("idol", "Древний идол", "Посреди поляны стоит каменный идол с рубиновыми глазами. Матросы шепчутся и не хотят подходить.",
        listOf(Choice("Выковырять рубины", "idol_take"), Choice("Оставить подношение (100 монет)", "idol_gift"), Choice("Уйти", "leave"))),
    Episode("spring", "Пресный источник", "Под пальмами бьёт холодный родник. Команда устала после долгого плавания.",
        listOf(Choice("Пополнить бочки и починиться", "spring_repair"), Choice("Устроить привал до вечера", "spring_rest"))),
    Episode("boat", "Обломки шлюпки", "На песке лежит разбитая шлюпка. В ней — промокший сундучок, перевязанный бечёвкой.",
        listOf(Choice("Открыть сундучок", "boat_open"))),
)

val DIVE = Episode("ruins", "Затонувший храм", "Под водой видны колонны древнего храма. Сквозь толщу воды блестит что-то золотое на алтаре.",
    listOf(Choice("Нырнуть к алтарю", "dive_swim"), Choice("Спустить водолазный колокол (200 монет)", "dive_bell"), Choice("Оставить храм в покое", "dive_leave")))

val TREASURE_EP = Episode("x", "Крест на карте", "Три куска карты сложились в одну. Вот она — кривая скала и крест, вырезанный на камне. Матросы берутся за лопаты!",
    listOf(Choice("Копать!", "x_dig")))

// ---------- Капитаны (выбор игрока) ----------
/** [face] — область лица на портрете (доли ширины и высоты: x, y, w, h). */
class Hero(
    val id: String, val name: String, val title: String, val story: String, val perks: List<String>, val face: FloatArray,
    val dmg: Float = 0f, val reload: Float = 0f, val speed: Float = 0f, val turn: Float = 0f, val hp: Float = 0f,
    val loot: Float = 0f, val trade: Float = 0f, val upgrade: Float = 0f, val xp: Float = 0f,
)

val HEROES = listOf(
    Hero("mihael", "Mihael", "Канонир-бунтарь",
        "Вырос на орудийной палубе военного фрегата и сбежал оттуда с попугаем и чужой саблей. Лучше всех знает, куда положить ядро.",
        listOf("+15% урона пушек", "Перезарядка быстрее на 12%", "Прочность корабля +10%"),
        floatArrayOf(0.31f, 0.13f, 0.38f, 0.29f), dmg = 0.15f, reload = 0.12f, hp = 0.10f),
    Hero("fin", "Fin", "Вольный мореход",
        "Ловит ветер раньше, чем тот подует. Обогнал шторм у Скалы Ворона и привёз из Бездны сундук, который никто не мог найти.",
        listOf("Скорость корабля +12%", "Поворот быстрее на 15%", "Добыча с кораблей +15%"),
        floatArrayOf(0.31f, 0.12f, 0.38f, 0.29f), speed = 0.12f, turn = 0.15f, loot = 0.15f),
    Hero("hope", "Hope", "Штурман и торговка",
        "Читает карты, звёзды и людей. Знает цену каждому товару в девяти портах и умеет договориться даже с пиратским адмиралом.",
        listOf("Цены на рынке выгоднее на 8%", "Улучшения корабля дешевле на 12%", "Опыт капитана +25%"),
        floatArrayOf(0.32f, 0.15f, 0.36f, 0.27f), trade = 0.08f, upgrade = 0.12f, xp = 0.25f),
)
