package com.seatrader.game

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

const val MAX_DAY = 365
const val START_GOLD = 1000
private const val IMPACT = 0.006      // каждая купленная/проданная единица двигает цену на 0.6%
private const val SELL_RATIO = 0.88   // торговцы скупают дешевле, чем продают

class Good(val name: String, val emoji: String, val base: Int)

val GOODS = listOf(
    Good("Зерно", "🌾", 20),
    Good("Рыба", "🐟", 26),
    Good("Вино", "🍷", 45),
    Good("Ткани", "🧵", 60),
    Good("Железо", "⛓️", 75),
    Good("Специи", "🌶️", 120),
    Good("Шёлк", "🧣", 180),
    Good("Самоцветы", "💎", 340),
)

/** Порт: координаты на карте (0..1) и что здесь дёшево/дорого. */
class Port(val name: String, val x: Float, val y: Float, val cheap: List<Int>, val dear: List<Int>)

val PORTS = listOf(
    Port("Сольвей", 0.13f, 0.17f, cheap = listOf(0, 1), dear = listOf(5, 7)),
    Port("Туманная Гавань", 0.47f, 0.09f, cheap = listOf(1, 4), dear = listOf(2, 0)),
    Port("Каменный Мыс", 0.84f, 0.16f, cheap = listOf(4, 7), dear = listOf(0, 3)),
    Port("Винный Берег", 0.20f, 0.52f, cheap = listOf(2, 0), dear = listOf(4, 6)),
    Port("Коралловый Остров", 0.56f, 0.42f, cheap = listOf(1), dear = listOf(3, 2, 4)),
    Port("Пряная Бухта", 0.88f, 0.56f, cheap = listOf(5, 6), dear = listOf(1, 2)),
    Port("Шёлковый Порт", 0.36f, 0.86f, cheap = listOf(6, 3), dear = listOf(7, 4)),
    Port("Южный Форт", 0.76f, 0.88f, cheap = listOf(3, 5), dear = listOf(0, 1)),
)

sealed class Event {
    object Pirates : Event()
    object Storm : Event()
    object Wreckage : Event()
    object Wind : Event()
}

/** Сколько дней занимает путь длиной в ширину карты при скорости 1. */
const val DAYS_PER_UNIT = 32.0

class Game(private val rnd: Random = Random.Default) {
    var gold = START_GOLD
    var day = 1
    var port = 4
    val cargo = IntArray(GOODS.size)
    val paid = DoubleArray(GOODS.size)   // сколько всего заплачено за товар в трюме
    var holdLevel = 0
    var sailLevel = 0
    var cannons = 0
    var hull = 100
    var piratesBeaten = 0
    var voyages = 0
    var over = false
    var sunk = false

    // Свободное плавание: положение корабля в долях карты.
    var atSea = false
    var shipX = PORTS[4].x
    var shipY = PORTS[4].y + 0.05f
    var heading = -Math.PI.toFloat() / 2
    var dayProgress = 0.0
    var boostUntil = 0
    var eventCooldown = 3

    val price = Array(PORTS.size) { DoubleArray(GOODS.size) }
    val baseEq = Array(PORTS.size) { DoubleArray(GOODS.size) }
    val shock = Array(PORTS.size) { DoubleArray(GOODS.size) { 1.0 } }
    val shockUntil = Array(PORTS.size) { IntArray(GOODS.size) }
    val news = ArrayList<String>()

    init {
        for (p in PORTS.indices) for (g in GOODS.indices) {
            val port = PORTS[p]
            val f = when (g) {
                in port.cheap -> 0.6
                in port.dear -> 1.65
                else -> 1.0
            } * (0.88 + rnd.nextDouble() * 0.24)
            baseEq[p][g] = GOODS[g].base * f
            price[p][g] = baseEq[p][g] * (0.9 + rnd.nextDouble() * 0.2)
        }
    }

    // ---------- Корабль ----------
    val capacity get() = 30 + 15 * holdLevel
    val speed get() = 1.0 + 0.25 * sailLevel
    val used get() = cargo.sum()
    val free get() = capacity - used
    val wages get() = 8 + 3 * cannons
    val boosted get() = day < boostUntil
    val effectiveSpeed get() = speed * (if (boosted) 1.5 else 1.0)

    val holdUpgradeCost: Int? get() = if (holdLevel >= 4) null else 800 shl holdLevel
    val sailUpgradeCost: Int? get() = if (sailLevel >= 4) null else 900 shl sailLevel
    val cannonCost: Int? get() = if (cannons >= 6) null else 500 + 250 * cannons
    val repairCost get() = (100 - hull) * 6

    fun upgradeHold() = spend(holdUpgradeCost) { holdLevel++ }
    fun upgradeSails() = spend(sailUpgradeCost) { sailLevel++ }
    fun buyCannon() = spend(cannonCost) { cannons++ }
    fun repair() = if (hull < 100) spend(repairCost) { hull = 100 } else false

    private inline fun spend(cost: Int?, action: () -> Unit): Boolean {
        if (cost == null || cost > gold) return false
        gold -= cost
        action()
        return true
    }

    // ---------- Рынок ----------
    fun eq(p: Int, g: Int) = baseEq[p][g] * (if (day < shockUntil[p][g]) shock[p][g] else 1.0)

    fun buyPrice(g: Int, p: Int = port) = price[p][g].roundToInt().coerceAtLeast(1)
    fun sellPrice(g: Int, p: Int = port) = (price[p][g] * SELL_RATIO).roundToInt().coerceAtLeast(1)

    fun buyCost(g: Int, q: Int): Int {
        var pr = price[port][g]
        var total = 0.0
        repeat(q) { total += pr; pr *= 1 + IMPACT }
        return total.roundToInt()
    }

    fun sellRevenue(g: Int, q: Int): Int {
        var pr = price[port][g] * SELL_RATIO
        var total = 0.0
        repeat(q) { total += pr; pr *= 1 - IMPACT }
        return total.roundToInt()
    }

    fun maxBuy(g: Int): Int {
        var pr = price[port][g]
        var total = 0.0
        var q = 0
        while (q < free && (total + pr).roundToInt() <= gold) {
            total += pr; pr *= 1 + IMPACT; q++
        }
        return q
    }

    fun avgPaid(g: Int) = if (cargo[g] == 0) 0 else (paid[g] / cargo[g]).roundToInt()

    fun buy(g: Int, q: Int): Boolean {
        if (q <= 0 || q > free) return false
        val cost = buyCost(g, q)
        if (cost > gold) return false
        gold -= cost
        cargo[g] += q
        paid[g] += cost
        repeat(q) { price[port][g] *= 1 + IMPACT }
        return true
    }

    fun sell(g: Int, q: Int): Boolean {
        if (q <= 0 || q > cargo[g]) return false
        gold += sellRevenue(g, q)
        removeCargo(g, q)
        repeat(q) { price[port][g] *= 1 - IMPACT }
        return true
    }

    private fun removeCargo(g: Int, q: Int) {
        if (cargo[g] == 0) return
        paid[g] -= paid[g] / cargo[g] * q
        cargo[g] -= q
        if (cargo[g] == 0) paid[g] = 0.0
    }

    fun cargoValue() = GOODS.indices.sumOf { cargo[it] * sellPrice(it) }
    fun netWorth() = gold + cargoValue()

    // ---------- Время ----------
    private fun gaussian(): Double {
        val u1 = rnd.nextDouble().coerceAtLeast(1e-9)
        val u2 = rnd.nextDouble()
        return sqrt(-2 * ln(u1)) * cos(2 * Math.PI * u2)
    }

    fun tickDay() {
        day++
        gold = (gold - wages).coerceAtLeast(0)
        for (p in PORTS.indices) for (g in GOODS.indices) {
            val e = eq(p, g)
            var pr = price[p][g]
            pr += (e - pr) * 0.08 + e * 0.035 * gaussian()
            price[p][g] = pr.coerceIn(GOODS[g].base * 0.25, GOODS[g].base * 4.0)
        }
        if (rnd.nextDouble() < 0.05) spawnRumor()
        if (day >= MAX_DAY) over = true
    }

    private fun spawnRumor() {
        val p = rnd.nextInt(PORTS.size)
        val g = rnd.nextInt(GOODS.size)
        val up = rnd.nextBoolean()
        shock[p][g] = if (up) 1.8 + rnd.nextDouble() * 0.6 else 0.4 + rnd.nextDouble() * 0.15
        shockUntil[p][g] = day + 15 + rnd.nextInt(16)
        val good = GOODS[g]
        val text = if (up) "${PORTS[p].name}: ${good.emoji} ${good.name.lowercase()} в большом спросе, цены растут!"
        else "${PORTS[p].name}: избыток товара «${good.name}», цены падают"
        news.add(0, "День $day · $text")
        while (news.size > 6) news.removeAt(news.lastIndex)
    }

    // ---------- Плавание ----------
    /** Примерное число дней пути до порта [to] от текущего места корабля; aspect = высота/ширина карты. */
    fun daysTo(to: Int, aspect: Float): Int {
        val dx = (PORTS[to].x - shipX).toDouble()
        val dy = ((PORTS[to].y - shipY) * aspect).toDouble()
        return ceil(sqrt(dx * dx + dy * dy) * DAYS_PER_UNIT / effectiveSpeed).toInt().coerceAtLeast(1)
    }

    fun leavePort() {
        if (atSea) return
        atSea = true
        eventCooldown = 2
    }

    /**
     * Корабль прошёл [units] (в ширинах карты). Отсчитывает дни пути
     * и возвращает событие, если оно случилось.
     */
    fun sail(units: Double): Event? {
        if (over || !atSea) return null
        dayProgress += units * DAYS_PER_UNIT / effectiveSpeed
        while (dayProgress >= 1.0) {
            dayProgress -= 1.0
            tickDay()
            if (over) return null
            if (eventCooldown > 0) {
                eventCooldown--
                continue
            }
            if (rnd.nextDouble() < 0.04) {
                eventCooldown = 4
                val r = rnd.nextDouble()
                return when {
                    r < 0.42 -> Event.Pirates
                    r < 0.70 -> Event.Storm
                    r < 0.86 -> Event.Wreckage
                    else -> Event.Wind
                }
            }
        }
        return null
    }

    fun dock(p: Int) {
        port = p
        atSea = false
        voyages++
        shipX = PORTS[p].x
        shipY = PORTS[p].y + 0.05f
    }

    fun applyWind(): String {
        val d = 4 + rnd.nextInt(5)
        boostUntil = day + d
        return "Ветер наполнил паруса! Следующие $d дн. корабль идёт в полтора раза быстрее."
    }

    private fun damage(amount: Int) {
        hull -= amount
        if (hull <= 0) {
            hull = 0
            sunk = true
            over = true
            cargo.fill(0)
            paid.fill(0.0)
        }
    }

    fun applyStorm(): String {
        val dmg = 10 + rnd.nextInt(21)
        val carried = GOODS.indices.filter { cargo[it] > 0 }
        var msg = "Шторм потрепал корабль: корпус −$dmg%."
        if (carried.isNotEmpty()) {
            val g = carried.random(rnd)
            val lost = (cargo[g] * (0.2 + rnd.nextDouble() * 0.2)).roundToInt().coerceAtLeast(1)
            removeCargo(g, lost)
            msg += "\nЗа борт смыло ${GOODS[g].emoji} ${GOODS[g].name}: $lost шт."
        }
        damage(dmg)
        return msg
    }

    fun applyWreckage(): String {
        if (free <= 0) {
            val found = 50 + rnd.nextInt(151)
            gold += found
            return "Матросы выловили из воды сундук: +$found 💰"
        }
        val g = rnd.nextInt(GOODS.size - 1)  // самоцветы на обломках не попадаются
        val q = (3 + rnd.nextInt(10)).coerceAtMost(free)
        cargo[g] += q
        return "Вы наткнулись на обломки торгового судна и подобрали ${GOODS[g].emoji} ${GOODS[g].name}: $q шт."
    }

    val fightChance get() = (0.2 + 0.13 * cannons).coerceAtMost(0.95)
    val fleeChance get() = (0.3 + 0.6 * (speed - 1.0)).coerceAtMost(0.9)
    val pirateToll get() = (gold * 15 / 100).coerceAtLeast(50).coerceAtMost(gold)

    private fun pirateLoss(cargoShare: Double): String {
        val dmg = 20 + rnd.nextInt(16)
        val goldLost = gold / 5
        gold -= goldLost
        var lostUnits = 0
        for (g in GOODS.indices) {
            val l = (cargo[g] * cargoShare).roundToInt()
            lostUnits += l
            removeCargo(g, l)
        }
        damage(dmg)
        return "Пираты взяли корабль на абордаж.\nПотеряно: $goldLost 💰, $lostUnits ед. товара, корпус −$dmg%."
    }

    fun fightPirates(): String {
        return if (rnd.nextDouble() < fightChance) {
            val loot = 150 + rnd.nextInt(250) + day * 3
            val dmg = 5 + rnd.nextInt(11)
            gold += loot
            piratesBeaten++
            damage(dmg)
            "Победа! Пираты бежали, бросив добычу.\n+$loot 💰, корпус −$dmg%."
        } else {
            "Бой проигран. " + pirateLoss(0.5)
        }
    }

    fun fleePirates(): String {
        return if (rnd.nextDouble() < fleeChance) "Паруса не подвели: вы оторвались от погони!"
        else "Уйти не удалось. " + pirateLoss(0.3)
    }

    fun payPirates(): String {
        val toll = pirateToll
        gold -= toll
        return "Вы откупились: −$toll 💰. Пираты отпустили корабль."
    }

    fun title(): String {
        val w = netWorth()
        return when {
            w < 2_000 -> "Юнга"
            w < 10_000 -> "Матрос-торговец"
            w < 30_000 -> "Купец"
            w < 80_000 -> "Торговый магнат"
            else -> "Легенда морей"
        }
    }
}
