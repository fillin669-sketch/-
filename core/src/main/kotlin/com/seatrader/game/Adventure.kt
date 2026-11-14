package com.seatrader.game

import com.badlogic.gdx.Preferences
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Материалы верфи: нужны для высоких уровней улучшений. */
val MATERIALS = listOf("Дерево", "Железо", "Парусина", "Шкуры")
val MATERIAL_PRICE = intArrayOf(40, 60, 50, 90)
val MATERIAL_COLOR = intArrayOf(0xa1887f, 0x90a4ae, 0xfff3e0, 0xbcaaa4)

/** Легендарный корабль: живёт в своём логове на краю карты. */
class Legend(val name: String, val base: String, val lx: Float, val lz: Float, val look: Look, val hp: Int, val dmg: Float,
             val trait: String, val coins: Int, val gems: Int, val story: String)

val LEGENDS = listOf(
    Legend("«Ледяной Призрак»", "ghost", 0f, -1460f, Look(24f, 7.4f, 3, 0x37474f, 0x80deea, 0xe0f7fa, 0x4dd0e1), 1500, 20f, "chain",
        3500, 20, "Корабль-призрак Ледяного моря: цепные ядра рвут паруса."),
    Legend("«Огненный Змей»", "galleon", 1460f, 60f, Look(27f, 8.4f, 3, 0x1a1210, 0xff6f00, 0x3e2723, 0xff3d00), 1800, 22f, "fire",
        4200, 24, "Галеон Огненных островов: бьёт зажигательными ядрами."),
    Legend("«Морская Ведьма»", "frigate", -1460f, -60f, Look(23f, 7f, 3, 0x1b3a2f, 0x69f0ae, 0x2e7d32, 0x00e676), 1400, 19f, "chain",
        3200, 18, "Быстрый фрегат Туманного рифа: нападает из тумана."),
    Legend("«Чёрный Левиафан»", "galleon", 60f, 1460f, Look(31f, 9.6f, 4, 0x0d0d0d, 0x5e35b1, 0x212121, 0x311b92), 2600, 26f, "",
        6000, 32, "Исполин Бездны: самый крепкий корабль морей."),
)

class Creature(val kind: Int, var x: Float, var z: Float, var h: Float, val inst: ModelInstance) {
    var hp = if (kind == 0) 260f else 60f
    val maxHp = hp
    var t = rand(0f, 10f)
    var fleeT = 0f
    var deadT = 0f
    var biteCd = 0f
    val radius get() = if (kind == 0) 7f else 2.6f
}

class Spout(var x: Float, var z: Float, var vx: Float, var vz: Float) { var life = rand(40f, 70f); var t = 0f }

class Caravan(val amount: Int, var timeLeft: Float, val total: Float, val port: Int)

/**
 * Приключения: экипаж, материалы верфи, розыск, легендарные корабли, торговые рейсы,
 * морские звери, смерчи, носовые пушки, захват крепостей и капитаны-союзники в финале.
 */
class Adventure(private val g: SeaGame) {
    // ---------- Экипаж ----------
    var sailors = -1
    fun maxSailors(): Int = when (g.shipId) { "sloop" -> 20; "brigantine" -> 30; "corvette" -> 34; "frigate" -> 50; "galleon" -> 80; "dragon" -> 100; else -> 30 }
    fun crewK() = (sailors.toFloat() / maxSailors()).coerceIn(0f, 1f)
    /** Нехватка людей — пушки заряжаются дольше (до +60% при пустой палубе). */
    fun reloadSlow() = 1f + 0.6f * (1f - crewK())
    fun boardMul() = 0.35f + 0.65f * crewK()
    fun recruitPrice() = 18

    fun fixCrew() { if (sailors < 0) sailors = maxSailors(); sailors = sailors.coerceIn(0, maxSailors()) }

    fun onPlayerHit(amount: Float, p: Ship) {
        fixCrew()
        if (sailors <= 2) return
        if (Random.nextFloat() < amount / p.maxHp * 4f) {
            val n = if (amount > p.maxHp * 0.1f) 2 else 1
            sailors = max(2, sailors - n)
            g.floatText(p.x, p.y + 7, p.z, "-$n матрос" + if (n > 1) "а" else "", rgb(0xffab91), g.ui.small)
        }
    }

    fun onBoardingLoss(frac: Float) {
        fixCrew()
        val n = (sailors * frac.coerceIn(0f, 1f) * 0.45f).roundToInt()
        if (n > 0) { sailors = max(2, sailors - n); g.toast("В абордаже погибло матросов: $n", 2f) }
    }

    fun recruit(n: Int): Int {
        fixCrew()
        val k = min(n, maxSailors() - sailors).coerceAtLeast(0)
        val cost = k * recruitPrice()
        if (k == 0 || g.coins < cost) return 0
        g.coins -= cost; sailors += k
        return k
    }

    // ---------- Материалы ----------
    val mats = IntArray(4)

    /** Что нужно для улучшения части [part] до уровня [next] (1..5): с 3-го уровня — материалы. */
    fun matNeed(part: Int, next: Int): IntArray {
        val n = IntArray(4)
        if (next < 3) return n
        val k = (next - 2) * 2
        when (part) {
            0 -> { n[0] = k; n[3] = k / 2 }
            1 -> { n[2] = k + 1 }
            2 -> { n[1] = k }
            else -> { n[1] = k / 2 + 1; n[0] = k / 2 }
        }
        return n
    }
    fun hasMats(n: IntArray) = (0..3).all { mats[it] >= n[it] }
    fun spendMats(n: IntArray) { for (i in 0..3) mats[i] -= n[i] }
    fun matText(n: IntArray) = (0..3).filter { n[it] > 0 }.joinToString(", ") { "${MATERIALS[it]} ${mats[it]}/${n[it]}" }

    fun buyMat(port: Int, i: Int, n: Int): Boolean {
        val price = matBuyPrice(port, i) * n
        if (g.coins < price) return false
        g.coins -= price; mats[i] += n; return true
    }
    fun matBuyPrice(port: Int, i: Int) = (MATERIAL_PRICE[i] * (0.85f + ((port * 7 + i * 3) % 5) * 0.08f)).roundToInt()
    fun matSellPrice(port: Int, i: Int) = (matBuyPrice(port, i) * 0.55f).roundToInt()

    // ---------- Розыск ----------
    var wanted = 0f
    private var hunterT = 30f
    val stars get() = wanted.toInt().coerceIn(0, 5)
    fun addWanted(v: Float, why: String? = null) {
        val before = stars
        wanted = (wanted + v).coerceIn(0f, 5.4f)
        if (stars > before) g.toast("Розыск: " + "★".repeat(stars) + (why?.let { "\n$it" } ?: ""), 2f)
    }
    fun bribePrice() = 350 * max(1, stars) + stars * stars * 150
    fun bribe(): Boolean {
        if (stars == 0) return false
        val p = bribePrice()
        if (g.coins < p) return false
        g.coins -= p; wanted = 0f
        for (s in g.ships) if (s.hunter && s.type == "navy") s.provoked = false
        return true
    }

    // ---------- Легенды ----------
    val legendsDown = HashSet<Int>()
    private val legendLair = Array(LEGENDS.size) { floatArrayOf(LEGENDS[it].lx, LEGENDS[it].lz) }

    fun lair(i: Int) = legendLair[i]

    // ---------- Торговые рейсы ----------
    val caravans = ArrayList<Caravan>()
    fun maxCaravans() = if (g.guilds.guild >= 0 && GUILDS[g.guilds.guild].id == "merchants") 3 else 2
    val caravanOptions = listOf(Triple(500, 240f, "Ближний рейс"), Triple(2000, 420f, "Дальний рейс"), Triple(5000, 600f, "Заморский рейс"))

    fun sendCaravan(opt: Int, port: Int): Boolean {
        val (amount, time, _) = caravanOptions[opt]
        if (caravans.size >= maxCaravans() || g.coins < amount) return false
        g.coins -= amount
        caravans += Caravan(amount, time, time, port)
        g.save()
        return true
    }

    // ---------- Звери, смерчи, крепости ----------
    val creatures = ArrayList<Creature>()
    val spouts = ArrayList<Spout>()
    val captured = HashSet<Int>()
    private var creatureT = 5f
    private var spoutT = 10f
    private var bowCd = 1f

    private lateinit var whaleModel: Model
    private lateinit var sharkModel: Model
    lateinit var raftModel: Model
    lateinit var crateModel: Model

    fun initModels(models: MutableList<Model>) {
        whaleModel = whale().also { models += it }
        sharkModel = shark().also { models += it }
        raftModel = raft().also { models += it }
        crateModel = crate().also { models += it }
    }

    fun afterWorldBuilt() {
        // логово легенды — ближайшая чистая вода к намеченной точке
        for ((i, l) in LEGENDS.withIndex()) {
            var best = floatArrayOf(l.lx, l.lz); var found = false
            for (r in 0..12) {
                for (k in 0 until 16) {
                    val a = k / 16f * 2 * PI.toFloat()
                    val x = l.lx + sin(a) * r * 25f; val z = l.lz + cos(a) * r * 25f
                    if (g.isWaterPublic(x, z, 60f)) { best = floatArrayOf(x, z); found = true; break }
                }
                if (found) break
            }
            legendLair[i] = best
        }
    }

    fun nearCapturedFort(x: Float, z: Float) = g.systems.fortresses.any { it.idx in captured && hypot(it.x - x, it.z - z) < 450f }

    // ---------- Финал: капитаны приходят на помощь ----------
    private val heroes = ArrayList<Ship>()
    private var heroesLeaveT = 0f

    // ---------- Подсказка к кладу ----------
    private var treasureRef: Any? = null
    var hintX = 0f; var hintZ = 0f
    /** Клад виден точно только вблизи; издали — круг поиска. */
    fun treasureExact(): Boolean {
        val t = g.treasure ?: return false
        val p = g.player ?: return true
        return hypot(t.x - p.x, t.z - p.z) < 130f
    }
    fun treasureHint(): String {
        val t = g.treasure ?: return ""
        val port = g.ports.minByOrNull { hypot(it.rx - t.x, it.rz - t.z) } ?: return "Сокровище где-то в круге"
        val dx = t.x - port.rx; val dz = t.z - port.rz
        val dir = when (((atan2(dx, -dz) / (PI.toFloat() / 4f)).roundToInt() + 8) % 8) {
            0 -> "к северу"; 1 -> "к северо-востоку"; 2 -> "к востоку"; 3 -> "к юго-востоку"
            4 -> "к югу"; 5 -> "к юго-западу"; 6 -> "к западу"; else -> "к северо-западу"
        }
        return "Карта сокровищ: остров $dir от «${port.def.name}». Ищите в круге — вблизи место отметится крестом."
    }

    // =================== Обновление ===================
    fun update(dt: Float) {
        val p = g.player ?: return
        fixCrew()
        // клад: новый — новая точка круга поиска
        val t = g.treasure
        if (t !== treasureRef) {
            treasureRef = t
            if (t != null) { val a = rand(0f, 6.28f); val r = rand(20f, 65f); hintX = t.x + sin(a) * r; hintZ = t.z + cos(a) * r }
        }
        // розыск спадает, пока вы не воюете
        if (g.combatT <= 0f && wanted > 0f) wanted = max(0f, wanted - dt / 150f)
        updateHunters(dt, p)
        updateLegends(p)
        updateCaravans(dt)
        updateCreatures(dt, p)
        updateSpouts(dt, p)
        updateBow(dt, p)
        updateHeroes(dt, p)
    }

    private fun updateHunters(dt: Float, p: Ship) {
        if (stars >= 2 && !p.inPort) {
            hunterT -= dt
            if (hunterT <= 0f) {
                hunterT = 100f - stars * 12f
                val n = if (stars >= 4) 2 else 1
                val have = g.ships.count { it.hunter && it.type == "navy" && !it.sinking }
                if (have < 3) repeat(n) {
                    g.findSpawnPoint(220f, 320f)?.let { (x, z) ->
                        g.spawnNeutral("navy", x, z).also { it.provoked = true; it.hunter = true; it.target = p }
                    }
                }
                g.toast("Охотники флота идут по вашему следу!", 2f)
            }
        }
        // розыск снят — охотники отстают и уходят
        if (stars == 0) g.ships.removeAll { it.hunter && it.type == "navy" && hypot(it.x - p.x, it.z - p.z) > 380f }
    }

    private fun updateLegends(p: Ship) {
        for ((i, l) in LEGENDS.withIndex()) {
            if (i in legendsDown) continue
            val (lx, lz) = legendLair[i].let { it[0] to it[1] }
            if (hypot(p.x - lx, p.z - lz) > 480f) continue
            if (g.ships.any { it.legend == i }) continue
            val d = ENEMIES.getValue(l.base)
            g.spawnEnemy(l.base, lx, lz, l.name, true, l.look).also {
                it.legend = i
                it.stats = Stats(l.hp, d.speed * 1.08f, d.turn, d.cannons + 2, l.dmg, d.reload * 0.9f, d.range + 4f, d.mass * 1.3f)
                it.hp = l.hp.toFloat(); it.maxHp = it.hp
                it.trait = l.trait
                it.leash = 520f
                it.coinsMin = l.coins; it.coinsMax = l.coins + 500; it.gems = l.gems.toFloat()
            }
            g.toast("На горизонте — легендарный ${l.name}!", 2.6f)
            g.sfx.play("thunder", 0.6f)
        }
    }

    fun onLegendSunk(s: Ship) {
        val i = s.legend
        if (i < 0 || i in legendsDown) return
        legendsDown += i
        val art = g.extras.grantArtifact(g.extras.monsterArts + g.extras.bountyArts, 1f)
        g.systems.addStat("legends")
        g.toast("Легенда повержена: ${LEGENDS[i].name}!" + (art ?: ""), 3.5f)
        g.sfx.play("quest")
        g.save()
    }

    private fun updateCaravans(dt: Float) {
        val it = caravans.iterator()
        while (it.hasNext()) {
            val c = it.next()
            c.timeLeft -= dt
            if (c.timeLeft > 0) continue
            it.remove()
            val r = Random.nextFloat()
            val lostRisk = 0.12f - (if (g.allies.isNotEmpty()) 0.04f else 0f)
            if (r < lostRisk) {
                val back = (c.amount * 0.5f).roundToInt()
                g.coins += back
                g.toast("Пираты перехватили караван: спасено $back монет", 2.5f)
            } else {
                val k = 1.35f + Random.nextFloat() * 0.35f + c.total / 2000f
                val back = (c.amount * k * (1f + g.guilds.tradeBonus())).roundToInt()
                g.coins += back
                g.sfx.play("coin")
                g.toast("Караван вернулся из «${PORTS[c.port].name}»: +${back - c.amount} прибыли", 2.5f)
            }
            g.save()
        }
    }

    // ---------- Морские звери ----------
    private fun updateCreatures(dt: Float, p: Ship) {
        creatureT -= dt
        if (creatureT <= 0f) {
            creatureT = 6f
            creatures.removeAll { it.deadT > 4f || hypot(it.x - p.x, it.z - p.z) > 700f }
            val zone = hypot(p.x, p.z) / WORLD
            val whales = creatures.count { it.kind == 0 }; val sharks = creatures.count { it.kind == 1 }
            val sharkCap = if (zone > 0.5f) 4 else 2
            val kind = if (whales < 2 && Random.nextFloat() < 0.5f) 0 else if (sharks < sharkCap) 1 else -1
            if (kind >= 0) g.findSpawnPoint(200f, 340f)?.let { (x, z) ->
                if (g.isWaterPublic(x, z, 30f)) creatures += Creature(kind, x, z, rand(-3f, 3f), ModelInstance(if (kind == 0) whaleModel else sharkModel))
            }
        }
        for (c in creatures) {
            c.t += dt
            if (c.deadT > 0f) {
                c.deadT += dt
                c.inst.transform.setToTranslation(c.x, waveHeight(c.x, c.z, g.now) - c.deadT * 1.5f, c.z).rotate(Vector3.Y, -c.h * MathUtils.radiansToDegrees).rotate(Vector3.Z, 160f)
                continue
            }
            val dx = p.x - c.x; val dz = p.z - c.z; val d = hypot(dx, dz)
            var speed: Float
            if (c.kind == 0) {
                // кит плывёт сам по себе, раненый — уходит
                speed = if (c.fleeT > 0f) 9f else 3.5f
                if (c.fleeT > 0f) { c.fleeT -= dt; c.h = turn(c.h, atan2(-dx, dz), dt * 0.8f) }
                else c.h += sin(c.t * 0.1f) * 0.05f * dt
            } else {
                // акула кружит у корабля и кусает
                speed = if (d < 90f && !p.sinking && !p.inPort) 10f else 4f
                val tx = p.x + cos(c.t * 0.6f) * 9f; val tz = p.z + sin(c.t * 0.6f) * 9f
                if (d < 90f) c.h = turn(c.h, atan2(tx - c.x, -(tz - c.z)), dt * 2.4f) else c.h += sin(c.t * 0.3f) * 0.3f * dt
                c.biteCd -= dt
                if (d < p.radius + 3f && c.biteCd <= 0f && !p.safe) { c.biteCd = 1.6f; g.damage(p, 3f, null, false) }
            }
            val nx = c.x + sin(c.h) * speed * dt; val nz = c.z - cos(c.h) * speed * dt
            if (g.isWaterPublic(nx, nz, c.radius + 4f)) { c.x = nx; c.z = nz } else c.h += PI.toFloat() * 0.5f
            val dive = if (c.kind == 0) (sin(c.t * 0.35f) * 0.35f - 0.15f) else -0.35f
            // пенный след — зверя видно издалека
            if (Random.nextFloat() < dt * (if (c.kind == 0) 10f else 7f)) g.fx.spawn(g.fx.FOAM, c.x - sin(c.h) * c.radius, 0.4f, c.z + cos(c.h) * c.radius, 0f, 0.3f, 0f, if (c.kind == 0) 3f else 1.6f, 1.6f, Fx.Mode.PUFF)
            c.inst.transform.setToTranslation(c.x, waveHeight(c.x, c.z, g.now) + dive, c.z).rotate(Vector3.Y, -c.h * MathUtils.radiansToDegrees)
                .rotate(Vector3.X, if (c.kind == 0) sin(c.t * 0.7f) * 4f else 0f)
            if (c.kind == 1) c.inst.transform.scale(1.7f, 1.7f, 1.7f)
            // фонтан у кита
            if (c.kind == 0 && (c.t % 9f) < dt * 1.1f) repeat(6) { g.fx.spawn(g.fx.SPLASH, c.x + sin(c.h) * 3f, 2f, c.z - cos(c.h) * 3f, rand(-1f, 1f), rand(7f, 10f), rand(-1f, 1f), 0.8f, 1.2f, Fx.Mode.PUFF) }
        }
    }

    private fun turn(h: Float, want: Float, k: Float): Float = wrapAngle(h + clamp(wrapAngle(want - h), -k, k))

    /** Попадание ядра игрока в зверя. */
    fun hitCreature(x: Float, y: Float, z: Float, dmg: Float): Boolean {
        for (c in creatures) {
            if (c.deadT > 0f || y > 4f) continue
            if (hypot(c.x - x, c.z - z) > c.radius) continue
            c.hp -= dmg
            c.fleeT = 20f
            g.fx.spawn(g.fx.SPLASH, x, 1f, z, 0f, 5f, 0f, 1f, 0.6f, Fx.Mode.PUFF)
            g.floatText(c.x, 5f, c.z, "-${dmg.roundToInt()}", Color.WHITE, g.ui.small)
            if (c.hp <= 0f) {
                c.deadT = 0.01f
                val skins = if (c.kind == 0) Random.nextInt(3, 6) else Random.nextInt(1, 3)
                dropMat(c.x, c.z, 3, skins)
                if (c.kind == 0) g.dropLootPublic(c.x, c.z, 250, 0.3f)
                g.systems.addStat("beasts")
                g.toast(if (c.kind == 0) "Кит добыт: шкуры и ворвань" else "Акула добыта", 1.6f)
            }
            return true
        }
        return false
    }

    // ---------- Смерчи ----------
    private fun updateSpouts(dt: Float, p: Ship) {
        spoutT -= dt
        if (g.stormLevel > 0.55f && spoutT <= 0f && spouts.size < 2 && !p.inPort) {
            spoutT = rand(25f, 45f)
            g.findSpawnPoint(160f, 260f)?.let { (x, z) ->
                val a = atan2(p.x - x, -(p.z - z)) + rand(-0.5f, 0.5f)
                spouts += Spout(x, z, sin(a) * 5f, -cos(a) * 5f)
                g.toast("Смерч! Уходите с его пути", 1.8f)
            }
        }
        val it = spouts.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.t += dt; s.life -= dt
            if (s.life <= 0f || g.stormLevel < 0.3f) { it.remove(); continue }
            s.x += s.vx * dt; s.z += s.vz * dt
            s.vx += sin(s.t * 0.7f) * 1.2f * dt; s.vz += cos(s.t * 0.5f) * 1.2f * dt
            // столб брызг
            if (g.visible(s.x, 20f, s.z, 40f)) for (k in 0 until 6) {
                // воронка: внизу узкая, кверху расширяется
                val h = rand(0f, 60f); val a = s.t * 6f + k * 1.05f + h * 0.2f; val r = 2f + h * 0.14f
                g.fx.spawn(if (k % 2 == 0) g.fx.SMOKE else g.fx.SPLASH, s.x + cos(a) * r, h, s.z + sin(a) * r, -sin(a) * 7f, 5f, cos(a) * 7f, 2.6f + h * 0.06f, 1.0f, Fx.Mode.PUFF)
            }
            if (Random.nextFloat() < dt * 12) g.fx.spawn(g.fx.FOAM, s.x + rand(-9f, 9f), 0.5f, s.z + rand(-9f, 9f), 0f, 0.5f, 0f, 4f, 1.5f, Fx.Mode.PUFF)
            if (Random.nextFloat() < dt * 8) g.fx.spawn(g.fx.SPLASH, s.x + rand(-4f, 4f), 1f, s.z + rand(-4f, 4f), rand(-6f, 6f), rand(3f, 8f), rand(-6f, 6f), 1f, 0.8f, Fx.Mode.PUFF)
            for (sh in g.ships) {
                if (sh.sinking) continue
                val d = hypot(sh.x - s.x, sh.z - s.z)
                if (d < 11f) {
                    // крутит и швыряет
                    sh.angVel += 1.6f * dt
                    val nx = (sh.x - s.x) / max(d, 0.1f); val nz = (sh.z - s.z) / max(d, 0.1f)
                    sh.vx += (-nz * 9f + nx * 4f) * dt; sh.vz += (nx * 9f + nz * 4f) * dt
                    if (sh === p) { g.damage(p, 14f * dt, null, false, silent = true); g.shake = max(g.shake, 0.2f) }
                    else g.damage(sh, 14f * dt, null, false, silent = true)
                }
            }
        }
    }

    // ---------- Носовые пушки ----------
    private fun updateBow(dt: Float, p: Ship) {
        bowCd -= dt
        if (bowCd > 0f || p.sinking || p.inPort || !g.systems.autoFire) return
        val fx = sin(p.h); val fz = -cos(p.h)
        var best: Ship? = null; var bd = p.stats.range * 1.15f
        for (o in g.ships) {
            if (o.sinking || o.surrendered || o === g.systems.grappleTarget) continue
            val hostile = o.faction == Faction.PIRATE || (o.faction == Faction.NEUTRAL && o.provoked)
            if (!hostile) continue
            if (g.systems.boardable(o) && hypot(o.x - p.x, o.z - p.z) < 45f) continue
            val dx = o.x - p.x; val dz = o.z - p.z; val d = hypot(dx, dz)
            if (d < 8f || d > bd) continue
            if ((dx * fx + dz * fz) / d < 0.9f) continue
            bd = d; best = o
        }
        val t = best ?: return
        bowCd = 3.2f * reloadSlow()
        val n = if ((g.ensureUp(g.shipId).getOrNull(2) ?: 0) >= 3) 2 else 1
        val bx = p.x + fx * p.len * 0.5f; val bz = p.z + fz * p.len * 0.5f
        for (k in 0 until n) {
            val side = if (n == 1) 0f else (k - 0.5f) * 1.2f
            g.launchBall(p, Faction.PLAYER, bx + cos(p.h) * side, p.y + p.deckTop + 0.6f, bz + sin(p.h) * side, t.x, t.z, t.vx, t.vz,
                p.stats.dmg * 0.75f, Ammo.NORMAL, k * 0.15f, 0.8f)
        }
        g.sfx.play("cannon", 0.7f)
    }

    // ---------- Капитаны в финальной битве ----------
    private fun updateHeroes(dt: Float, p: Ship) {
        val admiral = g.ships.firstOrNull { it.type == "admiral" && !it.sinking }
        if (heroes.isEmpty() && admiral != null && g.quest().type == QType.ADMIRAL && hypot(admiral.x - p.x, admiral.z - p.z) < 260f) {
            val looks = listOf(Prologue.FIN_LOOK to "«Попутный ветер»", Prologue.MIHAEL_LOOK to "«Гром»", Prologue.HOPE_LOOK to "«Путеводная звезда»")
            for ((k, lk) in looks.withIndex()) {
                val a = p.h + PI.toFloat() + (k - 1) * 0.6f
                val x = p.x + sin(a) * 70f; val z = p.z - cos(a) * 70f
                heroes += g.cutShip(lk.second, lk.first, Faction.PLAYER, x, z, p.h, 16f, 5).also { it.heroShip = true; it.stats = Stats(99999, 16f, 1.5f, 5, 14f, 1.8f, 55f, it.stats.mass) }
            }
            heroesLeaveT = 0f
            g.toast("Mihael: Мы с тобой, капитан! Сегодня «Кровавая Луна» ответит за всё!", 3.5f)
            g.sfx.play("quest")
        }
        if (heroes.isEmpty()) return
        if (admiral != null && hypot(admiral.x - p.x, admiral.z - p.z) < 700f) {
            for (h in heroes) { g.attackMove(h, admiral, dt); h.ai = AiState.ATTACK }
        } else {
            if (heroesLeaveT == 0f) g.toast(if (admiral == null) "Hope: Получилось! Моря снова свободны. До встречи, капитан!" else "Fin: Гарпун ушёл… Мы ещё догоним его!", 3f)
            heroesLeaveT += dt
            for (h in heroes) { h.desired = h.h; h.throttle = 1f }
            if (heroesLeaveT > 25f) { g.ships.removeAll { it.heroShip }; heroes.clear(); heroesLeaveT = 0f }
        }
    }

    // ---------- Потопление и добыча ----------
    fun onSink(s: Ship, src: Ship?) {
        val p = g.player
        if (s === p) return
        if (s.legend >= 0) onLegendSunk(s)
        // выжившие в воде — можно подобрать в команду
        if (!s.heroShip && !s.ally && Random.nextFloat() < 0.55f) dropSurvivors(s.x + rand(-6f, 6f), s.z + rand(-6f, 6f), Random.nextInt(2, if (s.len > 18) 7 else 5))
        if (s.faction == Faction.PIRATE && Random.nextFloat() < 0.6f) {
            val k = Random.nextInt(3)
            dropMat(s.x + rand(-5f, 5f), s.z + rand(-5f, 5f), k, Random.nextInt(1, if (s.len > 18) 5 else 3))
        }
        // мирное судно или патруль, потопленные игроком, — розыск
        if (s.faction == Faction.NEUTRAL && src === p) addWanted(if (s.type == "navy") 1.4f else 0.9f, "За потопление мирного судна")
    }

    fun onProvoke(s: Ship) { addWanted(if (s.type == "navy") 1.0f else 0.5f, if (s.type == "navy") "Вы атаковали патруль флота" else null) }

    private fun dropSurvivors(x: Float, z: Float, n: Int) {
        g.loot += Loot(false, n, x, z, ModelInstance(raftModel), rand(0f, 6f)).also { it.kind = 2 }
    }

    fun dropMat(x: Float, z: Float, mat: Int, n: Int) {
        g.loot += Loot(false, n, x, z, ModelInstance(crateModel), rand(0f, 6f)).also { it.kind = 3; it.mat = mat }
    }

    /** Подобрана особая добыча (выжившие или материалы). */
    fun collect(l: Loot) {
        when (l.kind) {
            2 -> {
                fixCrew()
                val add = min(l.value, maxSailors() - sailors)
                if (add > 0) { sailors += add; g.floatText(l.x, 4f, l.z, "+$add в команду", rgb(0x81d4fa), g.ui.mid) }
                else { g.extras.addMorale(1f); g.floatText(l.x, 4f, l.z, "Спасены моряки", rgb(0x81d4fa), g.ui.small) }
                g.sfx.play("click")
            }
            3 -> {
                mats[l.mat] += l.value
                g.floatText(l.x, 4f, l.z, "+${l.value} ${MATERIALS[l.mat]}", rgb(MATERIAL_COLOR[l.mat]), g.ui.mid)
                g.sfx.play("coin")
            }
        }
    }

    fun onPlayerDeath() {
        fixCrew()
        sailors = max(sailors, maxSailors() / 2)
        wanted = max(0f, wanted - 1f)
        g.ships.removeAll { it.heroShip }; heroes.clear()
    }

    // ---------- Рисование ----------
    fun render(batch: ModelBatch, env: Environment, lookX: Float, lookZ: Float) {
        for (c in creatures) if (hypot(c.x - lookX, c.z - lookZ) < 500f && g.visible(c.x, 0f, c.z, 12f)) batch.render(c.inst, env)
    }

    // ---------- Модели ----------
    private fun whale(): Model {
        val gm = Geo()
        gm.color(0x5b6f7c).push().scale(1f, 0.75f, 2.6f).ico(2.6f).pop()
        gm.color(0xcfd8dc).push().at(0f, -1.2f, -1f).scale(1f, 0.4f, 2.1f).ico(2.2f).pop()
        gm.color(0x5b6f7c).push().at(0f, 0.2f, 7.2f).scale(1f, 0.6f, 1.2f).ico(1.3f).pop()
        gm.color(0x455a64).push().at(0f, 0.3f, 8.6f).box(4.6f, 0.3f, 1.4f).pop()
        for (sx in intArrayOf(-1, 1)) gm.color(0x263238).push().at(sx * 2.4f, -0.6f, -1.6f).rotZ(sx * 25f).box(2.2f, 0.25f, 1.2f).pop()
        return gm.build()
    }

    private fun shark(): Model {
        val gm = Geo()
        gm.color(0x1c262b).push().at(0f, 1.4f, 0f).rotX(-14f).scale(0.45f, 1f, 1f).cone(1.1f, 2.8f, 6).pop()
        gm.color(0x455a64).push().at(0f, -0.2f, 0f).scale(0.6f, 0.5f, 2.2f).ico(1.4f).pop()
        gm.color(0x546e7a).push().at(0f, 0.6f, 3.2f).rotX(20f).cone(0.4f, 1.2f, 4).pop()
        return gm.build()
    }

    private fun raft(): Model {
        val gm = Geo()
        gm.color(0x8d6e63).box(3.2f, 0.4f, 2.4f)
        gm.color(0x6d4c41).push().at(0f, 0.25f, 0f).box(3.4f, 0.15f, 0.3f).pop()
        // двое моряков держатся за плот
        for (sx in intArrayOf(-1, 1)) {
            gm.color(0xfff3e0).push().at(sx * 0.8f, 0.8f, 0.2f).box(0.6f, 0.8f, 0.45f).pop()
            gm.color(0xd7a77a).push().at(sx * 0.8f, 1.45f, 0.2f).ico(0.3f).pop()
            gm.color(0xc62828).push().at(sx * 0.8f, 1.7f, 0.2f).box(0.55f, 0.15f, 0.5f).pop()
        }
        return gm.build()
    }

    private fun crate(): Model {
        val gm = Geo()
        gm.color(0x9c6b30).box(1.6f, 1.2f, 1.6f)
        gm.color(0x5d4037)
        for (y in floatArrayOf(-0.45f, 0.45f)) gm.push().at(0f, y, 0f).box(1.7f, 0.15f, 1.7f).pop()
        gm.color(0xe0e0e0).push().at(0f, 0.75f, 0f).box(1.0f, 0.3f, 1.0f).pop()
        return gm.build()
    }

    // ---------- Для тестов ----------
    fun debugCreature(kind: Int, x: Float, z: Float) { creatures += Creature(kind, x, z, 0f, ModelInstance(if (kind == 0) whaleModel else sharkModel)) }
    fun debugSpout(x: Float, z: Float) { spouts += Spout(x, z, 0f, 0f) }
    fun debugHunters() { hunterT = 0f }
    val heroCount get() = heroes.size

    // ---------- Сохранение ----------
    fun save(p: Preferences) {
        p.putInteger("sailors", sailors)
        p.putString("mats", mats.joinToString(","))
        p.putFloat("wanted", wanted)
        p.putString("legendsDown", legendsDown.joinToString(","))
        p.putString("captured", captured.joinToString(","))
        p.putString("caravans", caravans.joinToString(";") { "${it.amount}:${it.timeLeft}:${it.total}:${it.port}" })
    }

    fun load(p: Preferences) {
        sailors = p.getInteger("sailors", -1)
        val m = p.getString("mats", "").split(",").mapNotNull { it.toIntOrNull() }
        for (i in 0..3) mats[i] = m.getOrNull(i)?.coerceAtLeast(0) ?: 0
        wanted = p.getFloat("wanted", 0f).coerceIn(0f, 5.4f)
        legendsDown.clear(); legendsDown += p.getString("legendsDown", "").split(",").mapNotNull { it.toIntOrNull() }
        captured.clear(); captured += p.getString("captured", "").split(",").mapNotNull { it.toIntOrNull() }
        caravans.clear()
        for (part in p.getString("caravans", "").split(";")) {
            val v = part.split(":"); if (v.size != 4) continue
            try { caravans += Caravan(v[0].toInt(), v[1].toFloat(), v[2].toFloat(), v[3].toInt().coerceIn(0, PORTS.size - 1)) } catch (_: Exception) { }
        }
    }

    // ---------- Сюжет: капитаны в журнале ----------
    fun chapterVoice(chapter: String?): Pair<String, String>? {
        val c = chapter ?: return null
        return when {
            c.startsWith("Глава 1") -> "hope" to "Каждый капитан начинает с малого. Потопи пиратский шлюп — и о тебе заговорят в тавернах."
            c.startsWith("Глава 2") -> "mihael" to "Бригантины Гарпуна рыщут у наших берегов. Покажи им, чьи это воды!"
            c.startsWith("Глава 3") -> "fin" to "Туманный риф хранит тайны. Держи курс на запад и не верь туману — я проверял."
            c.startsWith("Глава 4") -> "mihael" to "На Огненных островах куют пушки для «Кровавой Луны». Самое время их испытать — на них же."
            c.startsWith("Глава 5") -> "fin" to "Ледяное море не прощает ошибок. Но след Гарпуна ведёт туда."
            c.startsWith("Глава 6") -> "hope" to "Говорят, в Бездне Гарпун прячет казну. Будь осторожен, юнга — ты нам ещё нужен."
            c.startsWith("Финал") -> "mihael" to "Найди «Кровавую Луну» — а мы придём на помощь, как двенадцать лет назад."
            else -> null
        }
    }
}
