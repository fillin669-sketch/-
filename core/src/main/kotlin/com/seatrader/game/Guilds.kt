package com.seatrader.game

import com.badlogic.gdx.Preferences
import kotlin.math.roundToInt
import kotlin.random.Random

/** Контракт торговой гильдии: доставить товар в порт к сроку. */
class Contract(val good: Int, val amount: Int, val port: Int, var timeLeft: Float, val reward: Int) {
    var delivered = 0
}

/**
 * Фракции и ранги, открытие капитанов, контракты гильдии и уровни кораблей.
 * Прогресс копится по всем трём путям; бонусы ранга даёт только своя фракция.
 */
class Guilds(private val g: SeaGame) {
    var guild = -1
    var contractsDone = 0
    val unlocked = HashSet<String>()
    val shipXp = HashMap<String, Int>()

    var contract: Contract? = null
    var offer: Contract? = null
    private var offerPort = -1
    private var checkT = 0f

    fun metric(gd: Guild): Int = when (gd.metric) {
        "contracts" -> contractsDone
        "explored" -> g.systems.explored.percent()
        else -> g.kills
    }

    fun rank(i: Int): Int { val gd = GUILDS[i]; return gd.thresholds.count { metric(gd) >= it }.coerceIn(1, gd.ranks.size) }
    fun myRank() = if (guild >= 0) rank(guild) else 0
    fun rankTitle(i: Int) = GUILDS[i].ranks[rank(i) - 1]
    /** Сколько нужно до следующего ранга (null — высший ранг). */
    fun nextThreshold(i: Int): Int? = GUILDS[i].thresholds.getOrNull(rank(i))

    // бонусы своей фракции (за ранги выше первого)
    private fun bonusRanks(id: String) = if (guild >= 0 && GUILDS[guild].id == id) myRank() - 1 else 0
    fun tradeBonus() = 0.02f * bonusRanks("merchants")
    fun cargoBonus() = 5 * bonusRanks("merchants")
    fun speedBonus() = 0.02f * bonusRanks("navigators")
    fun xpBonus() = 0.04f * bonusRanks("navigators")
    fun lootBonus() = 0.04f * bonusRanks("pirates")
    fun boardBonus() = 0.05f * bonusRanks("pirates")

    fun heroUnlocked(id: String) = id in unlocked
    /** Игрок пиратской фракции может нападать на любые корабли. */
    val isPirate get() = guild >= 0 && GUILDS[guild].id == "pirates"

    fun join(i: Int, first: Boolean) {
        if (!first) {
            if (g.coins < 2000) { g.toast("Сменить фракцию стоит 2000 монет"); return }
            g.coins -= 2000
        }
        guild = i
        if (first) when (GUILDS[i].id) {
            "merchants" -> g.living.addRep(0, 15)
            "navigators" -> g.living.addRep(1, 10)
            else -> { g.living.addRep(2, 20); g.living.addRep(1, -10) }
        }
        g.rebuildPlayer()
        g.toast("Вы вступили: ${GUILDS[i].name}", 2f)
        g.save()
    }

    // ---------- Контракты ----------
    fun offerFor(port: Int): Contract {
        val o = offer
        if (o != null && offerPort == port) return o
        val r = rank(0)
        var target = Random.nextInt(PORTS.size); if (target == port) target = (target + 1) % PORTS.size
        val gi = Random.nextInt(GOODS.size)
        val amount = (5 + r * 3 + Random.nextInt(4)).coerceAtMost(g.systems.capacity())
        val reward = (GOODS[gi].base * amount * (1.4f + r * 0.1f)).roundToInt() + 150
        return Contract(gi, amount, target, 420f + amount * 12f, reward).also { offer = it; offerPort = port }
    }

    fun accept(c: Contract) {
        contract = c
        offer = null
        g.systems.waypoint = floatArrayOf(g.ports[c.port].rx, g.ports[c.port].rz)
        g.toast("Контракт: ${c.amount} × ${GOODS[c.good].name} в «${PORTS[c.port].name}»", 2.5f)
        g.save()
    }

    /** Продажа товара: засчитывается в контракт, если порт и товар совпадают. */
    fun onSell(port: Int, gi: Int, n: Int) {
        val c = contract ?: return
        if (c.port != port || c.good != gi || n <= 0) return
        c.delivered += n
        if (c.delivered >= c.amount) {
            contract = null
            contractsDone++
            g.coins += c.reward
            g.living.addRep(0, 5)
            g.extras.addMorale(4f)
            g.sfx.play("quest")
            g.toast("Контракт выполнен! +${c.reward} монет", 2.5f)
            g.systems.addStat("contracts")
            g.save()
        }
    }

    // ---------- Уровни кораблей ----------
    fun shipLevel(id: String = g.shipId) = shipLevelFor(shipXp[id] ?: 0)

    fun addShipXp(n: Int) {
        if (n <= 0) return
        val id = g.shipId
        val before = shipLevel(id)
        shipXp[id] = (shipXp[id] ?: 0) + n
        val after = shipLevel(id)
        if (after > before) {
            g.rebuildPlayerKeepState()
            g.toast("${g.playerDef().name}: уровень корабля $after!", 2.2f)
            g.sfx.play("quest")
        }
    }

    fun shipStatMul(id: String = g.shipId) = 1f + 0.03f * (shipLevel(id) - 1)

    // ---------- Обновление ----------
    fun update(dt: Float) {
        contract?.let { c ->
            c.timeLeft -= dt
            if (c.timeLeft <= 0f) {
                contract = null
                g.living.addRep(0, -3)
                g.toast("Контракт провален: срок вышел", 2.2f)
                g.save()
            }
        }
        checkT -= dt
        if (checkT <= 0f) {
            checkT = 3f
            for ((i, gd) in GUILDS.withIndex()) {
                if (gd.hero !in unlocked && rank(i) >= gd.heroRank) {
                    unlocked += gd.hero
                    val h = HEROES.first { it.id == gd.hero }
                    g.sfx.play("treasure")
                    g.toast("${gd.name}: ранг «${rankTitle(i)}»!\nКапитан ${h.name} готов к службе — меню «Капитан»", 3.5f)
                    g.save()
                }
            }
        }
    }

    // ---------- Сохранение ----------
    fun load(p: Preferences) {
        guild = p.getInteger("guild", -1).coerceIn(-1, GUILDS.size - 1)
        contractsDone = p.getInteger("contracts", 0)
        unlocked.clear(); unlocked += p.getString("heroesOpen", "").split(",").filter { s -> HEROES.any { it.id == s } }
        // старые сохранения: уже выбранный капитан остаётся доступным
        val cur = p.getString("hero", "")
        if (cur.isNotEmpty() && !p.contains("heroesOpen")) unlocked += cur
        shipXp.clear()
        for (part in p.getString("shipXp", "").split(";")) { val kv = part.split(":"); if (kv.size == 2) kv[1].toIntOrNull()?.let { shipXp[kv[0]] = it } }
        val c = p.getString("contract", "").split(",")
        contract = if (c.size == 6) try {
            Contract(c[0].toInt(), c[1].toInt(), c[2].toInt().coerceIn(0, PORTS.size - 1), c[3].toFloat(), c[4].toInt()).also { it.delivered = c[5].toInt() }
        } catch (_: Exception) { null } else null
    }

    fun save(p: Preferences) {
        p.putInteger("guild", guild)
        p.putInteger("contracts", contractsDone)
        p.putString("heroesOpen", unlocked.joinToString(","))
        p.putString("shipXp", shipXp.entries.joinToString(";") { "${it.key}:${it.value}" })
        p.putString("contract", contract?.let { "${it.good},${it.amount},${it.port},${it.timeLeft},${it.reward},${it.delivered}" } ?: "")
    }
}
