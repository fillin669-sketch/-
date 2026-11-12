package com.seatrader.game

/** Как выглядит корабль. Цвета — 0xRRGGBB. */
class Look(
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

const val WORLD = 1000f // половина размера мира

class PortDef(val name: String, val x: Float, val z: Float, val r: Float)

val PORTS = listOf(
    PortDef("Гавань Ветров", 0f, 0f, 52f),
    PortDef("Коралловая бухта", 620f, -430f, 48f),
    PortDef("Форт Чайки", -640f, 300f, 55f),
    PortDef("Туманный порт", 430f, 640f, 50f),
    PortDef("Скала Ворона", -420f, -650f, 50f),
)

enum class QType { KILL, RAM, COLLECT, REACH, UPGRADE, BUY }

class Quest(
    val type: QType, val text: String, val n: Int = 1, val enemy: String = "",
    val port: Int = -1, val coins: Int = 0, val gems: Int = 0,
)

private val QUESTS = listOf(
    Quest(QType.KILL, "Потопи пиратский шлюп", 1, "sloop", coins = 100),
    Quest(QType.RAM, "Протарань пиратский шлюп", 1, "sloop", coins = 150),
    Quest(QType.COLLECT, "Собери 300 монет", 300, gems = 3),
    Quest(QType.REACH, "Доплыви до порта «Форт Чайки»", port = 2, coins = 200),
    Quest(QType.UPGRADE, "Улучши корабль в порту", coins = 150),
    Quest(QType.KILL, "Потопи 3 пиратские бригантины", 3, "brig", coins = 300, gems = 3),
    Quest(QType.BUY, "Купи новый корабль на верфи", gems = 10),
    Quest(QType.RAM, "Протарань пиратскую бригантину", 1, "brig", coins = 400),
    Quest(QType.REACH, "Доплыви до «Коралловой бухты»", port = 1, coins = 300),
    Quest(QType.KILL, "Потопи корсарский фрегат", 1, "frigate", coins = 600, gems = 5),
    Quest(QType.COLLECT, "Собери 1 500 монет", 1500, gems = 8),
    Quest(QType.KILL, "Потопи Чёрный галеон", 1, "galleon", coins = 1500, gems = 20),
)

fun questAt(i: Int): Quest {
    if (i < QUESTS.size) return QUESTS[i]
    val k = i - QUESTS.size
    val lvl = k / 4 + 1
    return when (k % 4) {
        0 -> Quest(QType.KILL, "Потопи ${3 + lvl} пиратских бригантин", 3 + lvl, "brig", coins = 400 * lvl, gems = 3)
        1 -> Quest(QType.RAM, "Протарань корсарский фрегат", 1, "frigate", coins = 700 * lvl, gems = 4)
        2 -> Quest(QType.KILL, "Потопи ${1 + lvl} корсарских фрегата", 1 + lvl, "frigate", coins = 800 * lvl, gems = 6)
        else -> Quest(QType.KILL, "Потопи Чёрный галеон", 1, "galleon", coins = 2000 * lvl, gems = 20)
    }
}

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

fun waveHeight(x: Float, z: Float, t: Float): Float =
    (Math.sin(x * 0.08 + t * 1.1) * 0.35 + Math.sin(z * 0.11 + t * 0.9) * 0.3 +
        Math.sin((x + z) * 0.05 + t * 0.7) * 0.4).toFloat()
