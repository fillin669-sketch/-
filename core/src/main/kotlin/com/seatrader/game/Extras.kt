package com.seatrader.game

import com.badlogic.gdx.Preferences
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.utils.Align
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Груз, оставшийся на месте крушения. */
class LostCargo(val x: Float, val z: Float, val cargo: IntArray, val cost: FloatArray, val coins: Int) {
    var guarded = false
    val inst = ArrayList<ModelInstance>()
}

/**
 * Дух команды, артефакты, потерянный груз после гибели и белый флаг
 * (откуп/сдача игрока, сдача пиратов).
 */
class Extras(private val g: SeaGame) {
    // ===================== Дух команды =====================
    var morale = 60f
    private var moraleT = 0f
    private var sinceLoot = 0f
    var mutinyCd = 120f

    fun addMorale(d: Float, show: Boolean = false) {
        val old = morale
        morale = (morale + d).coerceIn(if (hasArt(6)) 40f else 0f, 100f)
        if (show && morale.roundToInt() != old.roundToInt()) g.player?.let { p ->
            g.floatText(p.x, p.y + 17f, p.z, "${if (d > 0) "+" else ""}${d.roundToInt()} дух команды", rgb(if (d > 0) 0xffe082 else 0xff8a65), g.ui.small)
        }
        if (d > 0) sinceLoot = 0f
    }

    fun moraleLabel() = when { morale < 15 -> "бунтует"; morale < 35 -> "ропщет"; morale < 65 -> "спокойна"; morale < 85 -> "бодра"; else -> "рвётся в бой" }
    fun moraleColor() = when { morale < 15 -> 0xff5252; morale < 35 -> 0xffab40; morale < 65 -> 0xe3f2fd; morale < 85 -> 0xaed581; else -> 0x69f0ae }

    /** Множитель перезарядки от духа: подавленная команда медленнее, воодушевлённая быстрее. */
    fun reloadMul() = when { morale < 25 -> 1.15f; morale > 75 -> 0.92f; else -> 1f }
    /** Сила абордажной команды. */
    fun boardMul() = 0.8f + morale / 250f

    // ===================== Артефакты =====================
    val owned = HashSet<Int>()
    val equipped = ArrayList<Int>()
    fun hasArt(id: Int) = id in equipped
    private fun art(sel: (Artifact) -> Float) = equipped.sumOf { sel(ARTIFACTS[it]).toDouble() }.toFloat()

    fun artDmg(): Float = art { it.dmg } + (if (hasArt(8) && g.stormLevel > 0.4f) 0.15f else 0f)
    fun artReload() = art { it.reload }
    fun artSpeed(): Float = art { it.speed } + (if (hasArt(8) && g.stormLevel > 0.4f) 0.15f else 0f)
    fun artRange() = art { it.range }
    fun armor() = art { it.armor }
    fun artLoot() = art { it.loot }
    fun artXp() = art { it.xp }
    fun monsterMul() = 1f + art { it.monster }
    fun abilityCdMul() = 1f - art { it.abilityCd }

    fun toggleEquip(id: Int): Boolean {
        if (id !in owned) return false
        if (id in equipped) { equipped.remove(id); g.rebuildPlayer(); g.save(); return true }
        if (equipped.size >= ARTIFACT_SLOTS) { g.toast("Можно носить только $ARTIFACT_SLOTS артефакта"); return false }
        equipped += id
        g.rebuildPlayer()
        g.save()
        return true
    }

    /** Выдать артефакт из списка (если ещё не найден). Возвращает текст для сообщения или null. */
    fun grantArtifact(pool: List<Int>, chance: Float = 1f): String? {
        if (Random.nextFloat() > chance) return null
        val left = pool.filter { it !in owned }
        if (left.isEmpty()) return null
        val a = ARTIFACTS[left.random()]
        owned += a.id
        if (equipped.size < ARTIFACT_SLOTS) equipped += a.id
        g.sfx.play("treasure")
        g.toast("Найден артефакт: ${a.name}!", 3f)
        g.systems.addStat("artifacts")
        g.save()
        return "\n\nНайден артефакт «${a.name}»: ${a.desc}"
    }

    val templeArts = listOf(1, 2, 7)
    val treasureArts = listOf(0, 3, 11)
    val bountyArts = listOf(5, 6, 9)
    val monsterArts = listOf(4, 8, 10)

    // ===================== Потерянный груз =====================
    var lost: LostCargo? = null

    /** Игрок погиб: груз и часть монет остаются на месте крушения. Прежний несобранный груз пропадает. */
    fun onPlayerDeath(p: Ship) {
        val coins = (g.coins * 0.15f).toInt()
        g.coins -= coins
        val sy = g.systems
        val had = sy.cargo.sum() > 0 || coins > 0
        if (lost != null) g.toast("Прежний груз пропал навсегда", 2f)
        lost = if (had) LostCargo(p.x, p.z, sy.cargo.copyOf(), sy.cargoCost.copyOf(), coins).also { makeInst(it) } else null
        sy.loseCargo()
        addMorale(-20f)
        g.save()
    }

    fun lostCoins() = lost?.coins ?: 0

    private fun makeInst(l: LostCargo) {
        l.inst.clear()
        repeat(5) { k ->
            val m = if (k % 2 == 0) g.chestModelPublic else g.systems.wreckModel
            l.inst += ModelInstance(m)
        }
    }

    private fun updateLost(dt: Float, p: Ship) {
        val l = lost ?: return
        val d = hypot(l.x - p.x, l.z - p.z)
        // падальщики слетаются на обломки
        if (!l.guarded && d < 260f) {
            l.guarded = true
            repeat(2) { k ->
                val a = k * 3.14f + 0.6f
                g.spawnEnemy("sloop", l.x + kotlin.math.cos(a) * 35f, l.z + kotlin.math.sin(a) * 35f).also { it.homeX = l.x; it.homeZ = l.z; it.leash = 220f }
            }
            g.toast("Обломки вашего корабля стерегут падальщики!", 2.2f)
        }
        if (d < p.radius + 14f && !p.sinking) {
            val sy = g.systems
            var got = 0
            for (i in l.cargo.indices) {
                val k = sy.addCargo(i, l.cargo[i])
                if (k > 0 && l.cargo[i] > 0) sy.cargoCost[i] += l.cost[i] * k / l.cargo[i]
                got += k
            }
            g.coins += l.coins
            lost = null
            g.sfx.play("treasure")
            addMorale(10f, true)
            g.toast("Груз поднят: +${l.coins} монет" + if (got > 0) ", товаров: $got" else "", 2.5f)
            g.save()
        }
    }

    // ===================== Белый флаг =====================
    /** Показывать кнопку «белый флаг»: мало прочности и рядом враги. */
    var flagAvailable = false
        private set

    private fun attackers(p: Ship) = g.ships.filter { it.faction == Faction.PIRATE && !it.sinking && !it.surrendered && it.ai == AiState.ATTACK && hypot(it.x - p.x, it.z - p.z) < 260f }

    fun ransomPrice(): Int {
        val p = g.player ?: return 0
        val att = attackers(p)
        val boss = att.any { it.boss || it.type == "navy" }
        return max(100, (g.coins * (if (boss) 0.45f else 0.25f)).toInt())
    }

    fun refusesRansom(): String? {
        val p = g.player ?: return null
        return attackers(p).firstOrNull { it.bounty >= 0 || it.type == "admiral" || it.ghost }?.let {
            if (it.ghost) "Призраки не торгуются!" else "${it.name.removePrefix("☠ ")} не берёт выкуп — ему нужна ваша голова!"
        }
    }

    /** Откуп или сдача: враги рядом прекращают бой и уходят. */
    fun payOff(surrender: Boolean) {
        val p = g.player ?: return
        if (surrender) {
            g.systems.loseCargo()
            g.coins = (g.coins * 0.6f).toInt()
            addMorale(-15f)
            g.living.addRep(2, 3)
            g.toast("Вы спустили флаг. Пираты забрали груз и ушли.", 2.5f)
        } else {
            val price = ransomPrice()
            if (g.coins < price) { g.toast("Не хватает монет на выкуп"); return }
            g.coins -= price
            addMorale(-6f)
            g.living.addRep(2, 2)
            g.toast("Откуп принят: −$price монет. Пираты уходят.", 2.5f)
        }
        for (s in attackers(p)) { s.giveUpT = 45f; s.ai = AiState.WANDER; s.target = null; s.homeX = s.x + (s.x - p.x) * 3f; s.homeZ = s.z + (s.z - p.z) * 3f }
        g.combatT = 0f
        g.save()
    }

    /** Пират решает сдаться, когда почти потоплен. */
    fun maybeSurrender(s: Ship) {
        if (s.surrendered || s.boss || s.bounty >= 0 || s.ghost || s.type == "navy" || s.surrenderChecked) return
        if (s.hp > s.maxHp * 0.25f) return
        s.surrenderChecked = true
        if (Random.nextFloat() < 0.4f) {
            s.surrendered = true
            s.ai = AiState.WANDER; s.target = null
            g.floatText(s.x, s.y + 12f, s.z, "⚐ Сдаёмся!", Color.WHITE, g.ui.mid)
        }
    }

    /** Сдавшийся корабль рядом — можно принять сдачу. */
    fun surrenderNear(p: Ship): Ship? = g.ships.firstOrNull { it.surrendered && !it.sinking && hypot(it.x - p.x, it.z - p.z) < p.radius + it.radius + 35f }

    fun acceptSurrender(s: Ship, choice: Int): String {
        val text = when (choice) {
            0 -> { g.living.addRep(2, 4); addMorale(4f); g.ships.remove(s); "Вы отпустили пиратов. Слух о вашем благородстве разнесётся по тавернам." }
            1 -> { val c = (s.coinsMax * g.systems.luckMul()).roundToInt(); g.coins += c; addMorale(6f); g.ships.remove(s); "Пираты отдали казну: +$c монет — и ушли налегке." }
            else -> {
                val mercId = when (s.type) { "sloop" -> "merc_sloop"; "brig" -> "merc_brig"; "frigate" -> "merc_frigate"; else -> null }
                if (mercId != null && g.allies.size < MAX_ALLIES) {
                    g.ships.remove(s); g.allies += mercId; g.spawnAllies(); addMorale(8f)
                    "${s.name} теперь в вашем флоте. Пленники отправлены на берег."
                } else { val c = (s.coinsMax * 0.6f).roundToInt(); g.coins += c; g.ships.remove(s); "Во флоте нет места — забрали казну (+$c) и отпустили корабль." }
            }
        }
        g.systems.addStat("surrenders")
        g.systems.gainXp(40, s.x, s.z)
        g.save()
        return text
    }

    // ===================== Обновление =====================
    fun update(dt: Float) {
        val p = g.player ?: return
        updateLost(dt, p)
        flagAvailable = !p.sinking && p.hp < p.maxHp * 0.35f && attackers(p).isNotEmpty()
        // дух команды со временем
        moraleT += dt
        sinceLoot += dt
        if (moraleT >= 10f) {
            moraleT = 0f
            if (g.sfx.shantyIdx >= 0) addMorale(0.6f)
            if (g.stormLevel > 0.5f) addMorale(-0.6f)
            if (sinceLoot > 300f) addMorale(-0.5f)
            if (hasArt(3)) addMorale(-0.3f)
            if (g.living.baseFounded && g.living.blev[2] > 0) addMorale(0.2f)
            if (morale > 60f) addMorale(-0.1f) else if (morale < 55f && sinceLoot < 120f) addMorale(0.1f)
        }
        mutinyCd -= dt
        if (morale < 12f && mutinyCd <= 0f && g.state == SeaGame.State.PLAY && g.combatT <= 0f) {
            mutinyCd = 300f
            g.joyActive = false
            g.menus.showMutiny()
        }
    }

    fun resolveMutiny(choice: Int): String = when (choice) {
        0 -> if (g.coins >= 100) { g.coins -= 100; addMorale(30f); "Бочонок рома выкатили на палубу. Команда поёт и клянётся в верности!" } else { addMorale(5f); "Рома нет — команда ворчит, но ждёт обещаний." }
        1 -> { val c = (g.coins * 0.1f).toInt(); g.coins -= c; addMorale(25f); "Вы раздали команде $c монет из казны. Все довольны." }
        else -> {
            val boatswain = g.living.crew[0] >= 0
            addMorale(if (boatswain) 20f else 12f); g.living.addRep(2, -3)
            if (boatswain) "Боцман быстро навёл порядок. Зачинщики драят палубу." else "Вы усмирили бунтовщиков силой. Порядок восстановлен, но шёпот не утих."
        }
    }.also { g.save() }

    // ===================== Отрисовка =====================
    private val glowCol = Color()
    fun render(batch: ModelBatch, env: Environment) {
        val l = lost ?: return
        for ((k, i) in l.inst.withIndex()) {
            val a = k * 1.3f
            val x = l.x + kotlin.math.cos(a) * (2f + k * 1.6f); val z = l.z + kotlin.math.sin(a) * (2f + k * 1.6f)
            i.transform.setToTranslation(x, waveHeight(x, z, g.now) * 0.6f + 0.2f, z).rotate(0f, 1f, 0f, k * 70f + sin(g.now + k) * 10f)
            batch.render(i, env)
        }
    }

    fun renderDecals() {
        val l = lost
        if (l != null) {
            val pulse = 0.5f + 0.5f * sin(g.now * 3f)
            g.fx.glow(l.x, 3f, l.z, 10f + pulse * 4f, glowCol.set(1f, 0.85f, 0.35f, 0.35f + 0.3f * pulse))
        }
        // белые флаги над сдавшимися
        for (s in g.ships) if (s.surrendered && !s.sinking) {
            val fl = 0.85f + 0.15f * sin(g.now * 6f + s.id)
            g.fx.frame(g.ui.white, s.x, s.y + s.deckTop + s.mastH + 1.5f, s.z, 2.6f * fl, 1.7f, glowCol.set(1f, 1f, 1f, 0.95f))
        }
    }

    // ===================== HUD =====================
    fun flagPos(): Pair<Float, Float> = (g.wDp / 2) to (g.hDp * 0.64f)

    fun drawHud(b: Batch) {
        val ui = g.ui
        if (flagAvailable && g.state == SeaGame.State.PLAY) {
            val (fx, fy) = flagPos()
            val pulse = 1f + sin(g.now * 5) * 0.05f
            ui.round(b, fx - 52 * pulse, fy - 20 * pulse, 104 * pulse, 40 * pulse, rgb(0xeceff1, 0.92f))
            ui.text(b, ui.small, "⚐ Белый флаг", fx - 52, fy + 7, Align.center, 104f, c = rgb(0x263238))
        }
        b.color = Color.WHITE
    }

    fun touch(x: Float, y: Float): Boolean {
        if (!flagAvailable) return false
        val (fx, fy) = flagPos()
        if (kotlin.math.abs(x - fx) < 54 && kotlin.math.abs(y - fy) < 22) { g.sfx.play("click"); g.joyActive = false; g.menus.showWhiteFlag(); return true }
        return false
    }

    // ===================== Сохранение =====================
    fun load(p: Preferences) {
        morale = p.getFloat("morale", 60f)
        owned.clear(); owned += p.getString("arts", "").split(",").mapNotNull { it.toIntOrNull() }.filter { it in ARTIFACTS.indices }
        equipped.clear(); equipped += p.getString("artsOn", "").split(",").mapNotNull { it.toIntOrNull() }.filter { it in owned }.take(ARTIFACT_SLOTS)
        val l = p.getString("lost", "").split(";")
        lost = if (l.size == 5) try {
            LostCargo(l[0].toFloat(), l[1].toFloat(), l[2].split(",").map { it.toInt() }.toIntArray(), l[3].split(",").map { it.toFloat() }.toFloatArray(), l[4].toInt())
        } catch (_: Exception) { null } else null
        lost?.let { if (g.chestReady) makeInst(it) }
    }

    fun save(p: Preferences) {
        p.putFloat("morale", morale)
        p.putString("arts", owned.joinToString(","))
        p.putString("artsOn", equipped.joinToString(","))
        p.putString("lost", lost?.let { "${it.x};${it.z};${it.cargo.joinToString(",")};${it.cost.joinToString(",")};${it.coins}" } ?: "")
    }

    fun afterWorldBuilt() { lost?.let { makeInst(it) } }
}
