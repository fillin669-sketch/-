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

/** Модель корабля и её базовые характеристики. */
class ShipModel(
    val name: String,
    val role: String,
    val description: String,
    val price: Int,
    val cargo: Int,
    val speed: Double,
    val hull: Int,
    val cannonSlots: Int,
    val wages: Int,
    val armor: Double,       // доля урона, которую гасит корпус
    val maneuver: Double,    // бонус к шансу уйти от погони
    val upgradeBase: Int,    // цена первого уровня улучшения
    val size: Float,         // размер на карте
    val masts: Int,
)

val SHIPS = listOf(
    ShipModel("Шхуна", "Стартовый корабль",
        "Маленькая и вёрткая. Мало места в трюме и почти нет пушек.",
        price = 2_000, cargo = 30, speed = 1.0, hull = 100, cannonSlots = 2, wages = 8,
        armor = 0.0, maneuver = 0.10, upgradeBase = 500, size = 0.85f, masts = 2),
    ShipModel("Каравелла", "Быстрый торговец",
        "Самая быстрая из купеческих: короткие рейсы и лёгкий уход от пиратов.",
        price = 4_500, cargo = 45, speed = 1.3, hull = 110, cannonSlots = 3, wages = 11,
        armor = 0.0, maneuver = 0.15, upgradeBase = 700, size = 0.95f, masts = 3),
    ShipModel("Бриг", "Универсал",
        "Крепкий и надёжный: неплохой трюм, есть где поставить пушки.",
        price = 6_000, cargo = 55, speed = 1.1, hull = 150, cannonSlots = 6, wages = 14,
        armor = 0.10, maneuver = 0.05, upgradeBase = 900, size = 1.05f, masts = 2),
    ShipModel("Флейт", "Грузовоз",
        "Огромный трюм за небольшие деньги, но медленный и плохо вооружён.",
        price = 11_000, cargo = 95, speed = 0.9, hull = 160, cannonSlots = 4, wages = 18,
        armor = 0.10, maneuver = 0.0, upgradeBase = 1_300, size = 1.15f, masts = 3),
    ShipModel("Фрегат", "Военный корабль",
        "Быстрый, бронированный, с множеством пушек. Пираты его боятся.",
        price = 20_000, cargo = 70, speed = 1.25, hull = 230, cannonSlots = 12, wages = 26,
        armor = 0.25, maneuver = 0.05, upgradeBase = 2_000, size = 1.25f, masts = 3),
    ShipModel("Галеон", "Плавучая крепость",
        "Гигантский трюм и толстые борта. Неповоротлив, команда обходится дорого.",
        price = 38_000, cargo = 150, speed = 0.95, hull = 300, cannonSlots = 14, wages = 34,
        armor = 0.30, maneuver = -0.10, upgradeBase = 3_000, size = 1.4f, masts = 4),
)

const val MAX_UPGRADE = 3

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
    var shipModel = 0
    var holdLevel = 0
    var sailLevel = 0
    var armorLevel = 0
    var upgradeSpent = 0     // сколько вложено в улучшения текущего корабля
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
    val model get() = SHIPS[shipModel]
    val capacity get() = (model.cargo * (1 + 0.2 * holdLevel)).roundToInt()
    val speed get() = model.speed * (1 + 0.1 * sailLevel)
    val maxHull get() = (model.hull * (1 + 0.15 * armorLevel)).roundToInt()
    val used get() = cargo.sum()
    val free get() = capacity - used
    val wages get() = model.wages + 2 * cannons
    val boosted get() = day < boostUntil
    val effectiveSpeed get() = speed * (if (boosted) 1.5 else 1.0)

    private fun upgradeCost(level: Int): Int? = if (level >= MAX_UPGRADE) null else model.upgradeBase shl level
    val holdUpgradeCost get() = upgradeCost(holdLevel)
    val sailUpgradeCost get() = upgradeCost(sailLevel)
    val armorUpgradeCost get() = upgradeCost(armorLevel)
    val cannonCost: Int? get() = if (cannons >= model.cannonSlots) null else 400 + 150 * cannons
    val repairCost get() = (maxHull - hull) * 5

    fun upgradeHold() = spend(holdUpgradeCost) { upgradeSpent += holdUpgradeCost!!; holdLevel++ }
    fun upgradeSails() = spend(sailUpgradeCost) { upgradeSpent += sailUpgradeCost!!; sailLevel++ }
    fun upgradeArmor(): Boolean {
        val before = maxHull
        return spend(armorUpgradeCost) {
            upgradeSpent += armorUpgradeCost!!
            armorLevel++
            hull += maxHull - before
        }
    }
    fun buyCannon() = spend(cannonCost) { cannons++ }
    fun repair() = if (hull < maxHull) spend(repairCost) { hull = maxHull } else false

    /** Сколько дадут за текущий корабль со всеми улучшениями и пушками. */
    fun tradeInValue() = model.price / 2 + upgradeSpent / 2 + cannons * 200

    /** Доплата за новый корабль (может быть отрицательной: тогда вы получаете деньги). */
    fun shipCost(m: Int) = SHIPS[m].price - tradeInValue()

    /** Почему нельзя купить корабль, или null, если можно. */
    fun cantBuyShip(m: Int): String? = when {
        m == shipModel -> "Это ваш корабль"
        used > SHIPS[m].cargo -> "Груз не поместится в трюм (${SHIPS[m].cargo} мест). Сначала продайте часть товара"
        shipCost(m) > gold -> "Не хватает золота"
        else -> null
    }

    fun buyShip(m: Int): Boolean {
        if (cantBuyShip(m) != null) return false
        gold -= shipCost(m)
        shipModel = m
        holdLevel = 0
        sailLevel = 0
        armorLevel = 0
        upgradeSpent = 0
        cannons = 0
        hull = maxHull
        return true
    }

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
    fun netWorth() = gold + cargoValue() + (if (sunk) 0 else tradeInValue())

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

    /** Урон в процентах «стандартного» корпуса; большие корабли и броня держат лучше. */
    private fun damage(percent: Int): Int {
        val amount = (percent / 100.0 * (100 + (maxHull - 100) * 0.5) * (1 - model.armor)).roundToInt().coerceAtLeast(1)
        hull -= amount
        if (hull <= 0) {
            hull = 0
            sunk = true
            over = true
            cargo.fill(0)
            paid.fill(0.0)
        }
        return amount
    }

    fun applyStorm(): String {
        val carried = GOODS.indices.filter { cargo[it] > 0 }
        val dmg = damage(10 + rnd.nextInt(21))
        var msg = "Шторм потрепал корабль: корпус −$dmg."
        if (carried.isNotEmpty() && !sunk) {
            val g = carried.random(rnd)
            val lost = (cargo[g] * (0.2 + rnd.nextDouble() * 0.2)).roundToInt().coerceAtLeast(1)
            removeCargo(g, lost)
            msg += "\nЗа борт смыло ${GOODS[g].emoji} ${GOODS[g].name}: $lost шт."
        }
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

    /** Пираты со временем становятся сильнее. */
    val pirateStrength get() = 3 + day / 45
    val fightChance get() = (0.15 + 0.8 * cannons / (cannons + pirateStrength).toDouble() + model.armor * 0.3).coerceAtMost(0.95)
    val fleeChance get() = (0.3 + 0.6 * (effectiveSpeed - 1.0) + model.maneuver).coerceIn(0.05, 0.9)
    val pirateToll get() = (gold * 15 / 100).coerceAtLeast(50).coerceAtMost(gold)

    private fun pirateLoss(cargoShare: Double): String {
        val goldLost = gold / 5
        gold -= goldLost
        var lostUnits = 0
        for (g in GOODS.indices) {
            val l = (cargo[g] * cargoShare).roundToInt()
            lostUnits += l
            removeCargo(g, l)
        }
        val dmg = damage(20 + rnd.nextInt(16))
        return "Пираты взяли корабль на абордаж.\nПотеряно: $goldLost 💰, $lostUnits ед. товара, корпус −$dmg."
    }

    fun fightPirates(): String {
        return if (rnd.nextDouble() < fightChance) {
            val loot = 150 + rnd.nextInt(250) + day * 3
            gold += loot
            piratesBeaten++
            val dmg = damage(5 + rnd.nextInt(11))
            "Победа! Пираты бежали, бросив добычу.\n+$loot 💰, корпус −$dmg."
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
            w < 3_000 -> "Юнга"
            w < 12_000 -> "Матрос-торговец"
            w < 35_000 -> "Купец"
            w < 90_000 -> "Торговый магнат"
            else -> "Легенда морей"
        }
    }
}
