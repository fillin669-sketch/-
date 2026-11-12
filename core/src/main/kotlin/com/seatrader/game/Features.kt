package com.seatrader.game

import com.badlogic.gdx.Gdx
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

class Fortress(val idx: Int, val x: Float, val z: Float, val r: Float, val inst: ModelInstance, val ruins: ModelInstance) {
    val maxHp = 900f
    var hp = maxHp
    var cd = 2f
    var destroyedT = 0f
    var lastHit = -99f
    val alive get() = destroyedT <= 0f
}

class Kraken(var x: Float, var z: Float, val head: ModelInstance, val tentacles: List<List<ModelInstance>>) {
    val maxHp = 1600f
    var hp = maxHp
    var h = 0f
    var depth = 6f
    var diveT = 0f
    var surfaceT = 18f
    var slamIdx = -1
    var slamT = 0f
    var slamX = 0f
    var slamZ = 0f
    var slamCd = 3f
    var grabCd = 6f
    var deadT = 0f
    var lastHit = -99f
    val seg = Array(6) { Array(8) { Vector3() } }
    val dead get() = deadT > 0
    val diving get() = diveT > 0
}

class Mine(var x: Float, var z: Float, val inst: ModelInstance) { var t = 0f }
class Bottle(val song: Int, val x: Float, val z: Float, val inst: ModelInstance) { var t = 0f }

/** Туман войны: какие клетки карты капитан уже видел. */
class Explored(val n: Int = 80) {
    val bits = java.util.BitSet(n * n)
    val cell get() = WORLD * 2 / n
    fun idx(x: Float, z: Float): Int {
        val i = ((x + WORLD) / cell).toInt().coerceIn(0, n - 1)
        val j = ((z + WORLD) / cell).toInt().coerceIn(0, n - 1)
        return j * n + i
    }
    fun seen(x: Float, z: Float) = bits[idx(x, z)]
    fun reveal(x: Float, z: Float, r: Float) {
        val c = cell
        val i0 = ((x - r + WORLD) / c).toInt().coerceIn(0, n - 1); val i1 = ((x + r + WORLD) / c).toInt().coerceIn(0, n - 1)
        val j0 = ((z - r + WORLD) / c).toInt().coerceIn(0, n - 1); val j1 = ((z + r + WORLD) / c).toInt().coerceIn(0, n - 1)
        for (j in j0..j1) for (i in i0..i1) {
            val cx = -WORLD + (i + 0.5f) * c; val cz = -WORLD + (j + 0.5f) * c
            if (hypot(cx - x, cz - z) < r) bits.set(j * n + i)
        }
    }
    fun percent() = bits.cardinality() * 100 / (n * n)
    fun encode(): String = java.util.Base64.getEncoder().encodeToString(bits.toByteArray())
    fun decode(s: String) {
        bits.clear()
        if (s.isEmpty()) return
        try { bits.or(java.util.BitSet.valueOf(java.util.Base64.getDecoder().decode(s))) } catch (_: Exception) { }
    }
}
class Wreck(var x: Float, var z: Float, val inst: ModelInstance) { var searchT = 0f; var bubbleT = 0f }
class ReefRock(val x: Float, val z: Float, val r: Float, val inst: ModelInstance)

/** Новые системы: капитан, торговля, достижения, крепости, кракен, мины, купцы, флотилии, регионы. */
class Systems(private val g: SeaGame) {
    // ---------- Капитан ----------
    var xp = 0
    var level = 1
    var skillPts = 0
    val skills = IntArray(SKILLS.size)
    fun skill(id: String) = skills[SKILLS.indexOfFirst { it.id == id }]

    // ---------- Статистика и достижения ----------
    val stats = HashMap<String, Int>()
    val unlocked = HashSet<String>()

    // ---------- Торговля ----------
    val cargo = IntArray(GOODS.size)
    val cargoCost = FloatArray(GOODS.size)
    private val fluct = Array(PORTS.size) { FloatArray(GOODS.size) { rand(0.88f, 1.12f) } }
    private var fluctT = 240f

    // ---------- Оружие ----------
    val ownedWeapons = HashSet<Int>()
    var autoFire = true
    var vibrate = true
    var shownIntro = -1

    // ---------- Песни, карта ----------
    val shanties = HashSet<Int>().also { it += 0 }
    var shantiesOn = true
    var shantyCur = 0
    var shantyBanner = 0f
    private var lastShanty = -1
    val bottles = ArrayList<Bottle>()
    lateinit var bottleModel: Model
    val explored = Explored()
    private var revealT = 0f
    var waypoint: FloatArray? = null
    private var gullT = 5f
    private var creakT = 8f
    private var farThunderT = 12f
    // абордажные крюки
    var grappleTarget: Ship? = null
    var grappleT = 0f
    private var boardHintShown = false

    // ---------- Мир ----------
    val fortresses = ArrayList<Fortress>()
    val mines = ArrayList<Mine>()
    val wrecks = ArrayList<Wreck>()
    val reefs = ArrayList<ReefRock>()
    var kraken: Kraken? = null
    var protect: Ship? = null
    private var protectCalmT = 0f
    private var merchantT = 5f
    private var flotillaT = 200f
    var region = Region.CENTER
    private val deep = Color(0.11f, 0.42f, 0.70f, 1f)
    private val mid = Color(0.19f, 0.57f, 0.83f, 1f)
    val waterDeep = FloatArray(3)
    val waterMid = FloatArray(3)
    val regionFog = Color(0.56f, 0.77f, 0.91f, 1f)
    var regionFogMix = 0f
    var regionFogScale = 1f
    var boardTarget: Ship? = null

    lateinit var mineModel: Model
    lateinit var wreckModel: Model
    lateinit var reefModel: Model
    lateinit var krakenHeadModel: Model
    lateinit var tentacleModel: Model

    fun initModels(models: MutableList<Model>) {
        mineModel = Models.mine().also { models += it }
        wreckModel = Models.wreck().also { models += it }
        reefModel = Models.reefRock().also { models += it }
        krakenHeadModel = Models.krakenHead().also { models += it }
        tentacleModel = Models.tentacleSeg().also { models += it }
        bottleModel = Models.bottle().also { models += it }
    }

    /** Бутылки с песнями в открытом море. */
    fun placeBottles(isWater: (Float, Float, Float) -> Boolean) {
        val spots = listOf(1 to floatArrayOf(-1150f, -520f), 2 to floatArrayOf(1180f, 560f))
        for ((song, pos) in spots) {
            var x = pos[0]; var z = pos[1]
            var k = 0
            while (!isWater(x, z, 20f) && k++ < 50) { x += rand(-60f, 60f); z += rand(-60f, 60f) }
            if (song !in shanties) bottles += Bottle(song, x, z, ModelInstance(bottleModel))
        }
    }

    // ===================== Сохранение =====================
    fun load(p: Preferences) {
        xp = p.getInteger("xp", 0)
        level = p.getInteger("level", 1)
        skillPts = p.getInteger("skillPts", 0)
        val sk = p.getString("skills", "").split(",").mapNotNull { it.toIntOrNull() }
        for (i in skills.indices) skills[i] = sk.getOrElse(i) { 0 }.coerceIn(0, MAX_SKILL)
        stats.clear()
        for (part in p.getString("stats", "").split(";")) {
            val kv = part.split("="); if (kv.size == 2) kv[1].toIntOrNull()?.let { stats[kv[0]] = it }
        }
        unlocked.clear(); unlocked += p.getString("ach", "").split(",").filter { it.isNotEmpty() }
        val cg = p.getString("cargo", "").split(",").mapNotNull { it.toIntOrNull() }
        val cc = p.getString("cargoCost", "").split(",").mapNotNull { it.toFloatOrNull() }
        for (i in cargo.indices) { cargo[i] = cg.getOrElse(i) { 0 }; cargoCost[i] = cc.getOrElse(i) { 0f } }
        ownedWeapons.clear(); ownedWeapons += p.getString("weapons", "").split(",").mapNotNull { it.toIntOrNull() }
        autoFire = p.getBoolean("autoFire", true)
        vibrate = p.getBoolean("vibrate", true)
        shownIntro = p.getInteger("shownIntro", -1)
        shanties.clear(); shanties += 0
        shanties += p.getString("shanties", "").split(",").mapNotNull { it.toIntOrNull() }.filter { it in SHANTIES.indices }
        shantiesOn = p.getBoolean("shantiesOn", true)
        explored.decode(p.getString("explored", ""))
        val wp = p.getString("waypoint", "").split(",").mapNotNull { it.toFloatOrNull() }
        waypoint = if (wp.size == 2) floatArrayOf(wp[0], wp[1]) else null
    }

    fun save(p: Preferences) {
        p.putInteger("xp", xp)
        p.putInteger("level", level)
        p.putInteger("skillPts", skillPts)
        p.putString("skills", skills.joinToString(","))
        p.putString("stats", stats.entries.joinToString(";") { "${it.key}=${it.value}" })
        p.putString("ach", unlocked.joinToString(","))
        p.putString("cargo", cargo.joinToString(","))
        p.putString("cargoCost", cargoCost.joinToString(","))
        p.putString("weapons", ownedWeapons.joinToString(","))
        p.putBoolean("autoFire", autoFire)
        p.putBoolean("vibrate", vibrate)
        p.putInteger("shownIntro", shownIntro)
        p.putString("shanties", shanties.joinToString(","))
        p.putBoolean("shantiesOn", shantiesOn)
        p.putString("explored", explored.encode())
        p.putString("waypoint", waypoint?.joinToString(",") ?: "")
    }

    // ===================== Капитан =====================
    fun gainXp(n: Int, x: Float, z: Float) {
        if (n <= 0) return
        xp += n
        g.floatText(x, 9f, z, "+$n опыта", rgb(0x81d4fa), g.ui.small)
        var up = false
        while (xp >= xpForLevel(level)) {
            xp -= xpForLevel(level)
            level++
            skillPts++
            up = true
        }
        if (up) {
            g.toast("Новый уровень капитана: $level!\n+1 очко навыка", 2.4f)
            g.sfx.play("quest")
            setStat("level", level)
            g.questEvent(QType.LEVEL, "", false)
        }
    }

    fun buySkill(i: Int): Boolean {
        if (skillPts <= 0 || skills[i] >= MAX_SKILL) return false
        skillPts--
        skills[i]++
        g.rebuildPlayer()
        return true
    }

    // ===================== Статистика =====================
    fun addStat(key: String, n: Int = 1) { stats[key] = (stats[key] ?: 0) + n; checkAch() }
    fun setStat(key: String, v: Int) { stats[key] = max(stats[key] ?: 0, v); checkAch() }

    private fun checkAch() {
        for (a in ACHIEVEMENTS) {
            if (a.id in unlocked) continue
            if ((stats[a.stat] ?: 0) >= a.goal) {
                unlocked += a.id
                g.gems += a.gems
                g.toast("Достижение: ${a.name}!\n+${a.gems} кристаллов", 2.6f)
                g.sfx.play("treasure")
            }
        }
    }

    // ===================== Торговля =====================
    fun capacity() = CARGO[g.shipId] ?: 20
    fun cargoUsed() = cargo.sum()
    private fun basePrice(port: Int, gi: Int) = GOODS[gi].base * PORT_PRICES[port][gi] * fluct[port][gi]
    fun buyPrice(port: Int, gi: Int) = max(1, (basePrice(port, gi) * (1.06f - 0.02f * skill("trader"))).roundToInt())
    fun sellPrice(port: Int, gi: Int) = max(1, (basePrice(port, gi) * (0.94f + 0.02f * skill("trader"))).roundToInt())

    fun buy(port: Int, gi: Int, n: Int): Int {
        var k = 0
        while (k < n && cargoUsed() < capacity() && g.coins >= buyPrice(port, gi)) {
            g.coins -= buyPrice(port, gi)
            cargoCost[gi] += buyPrice(port, gi).toFloat()
            cargo[gi]++
            k++
        }
        return k
    }

    fun sell(port: Int, gi: Int, n: Int): Int {
        val k = min(n, cargo[gi])
        if (k <= 0) return 0
        val avg = cargoCost[gi] / cargo[gi]
        val revenue = sellPrice(port, gi) * k
        g.coins += revenue
        cargoCost[gi] -= avg * k
        cargo[gi] -= k
        if (cargo[gi] == 0) cargoCost[gi] = 0f
        val profit = (revenue - avg * k).roundToInt()
        if (profit > 0) { addStat("profit", profit); gainXp(profit / 20, g.player?.x ?: 0f, g.player?.z ?: 0f) }
        val q = g.quest()
        if (q.type == QType.DELIVER && q.good == gi && q.port == port) g.questEvent(QType.DELIVER, "", false, k)
        return k
    }

    fun addCargo(gi: Int, n: Int): Int {
        val k = min(n, capacity() - cargoUsed())
        if (k > 0) cargo[gi] += k
        return k
    }

    fun loseCargo() { cargo.fill(0); cargoCost.fill(0f) }

    // ===================== Мир: создание =====================
    fun buildFortresses(models: MutableList<Model>, addIsland: (Float, Float, Float, Region) -> Unit) {
        val fm = Models.fortress(16f).also { models += it }
        val rm = Models.ruins(16f).also { models += it }
        for ((i, f) in FORTRESSES.withIndex()) {
            val x = f[0]; val z = f[1]
            addIsland(x, z, 26f, regionAt(x, z))
            fortresses += Fortress(i, x, z, 26f, ModelInstance(fm).also { it.transform.setToTranslation(x, 0f, z) },
                ModelInstance(rm).also { it.transform.setToTranslation(x, 0f, z) })
        }
    }

    fun buildReefsAndWrecks(isWater: (Float, Float, Float) -> Boolean) {
        val rnd = Random(99)
        var tries = 0
        while (reefs.size < 45 && tries++ < 3000) {
            val x = -WORLD + 60 + rnd.nextFloat() * (WORLD * 0.9f)
            val z = (rnd.nextFloat() * 2 - 1) * (WORLD - 60)
            if (regionAt(x, z) != Region.REEF || !isWater(x, z, 20f)) continue
            if (reefs.any { hypot(it.x - x, it.z - z) < 40 }) continue
            val r = 4f + rnd.nextFloat() * 3
            reefs += ReefRock(x, z, r, ModelInstance(reefModel).also {
                it.transform.setToTranslation(x, -1.4f, z).rotate(Vector3.Y, rnd.nextFloat() * 360).scale(r / 4.5f, 1f, r / 4.5f)
            })
        }
        repeat(9) { placeWreck(null, isWater) }
    }

    fun placeWreck(w: Wreck?, isWater: (Float, Float, Float) -> Boolean) {
        for (k in 0 until 200) {
            val x = (Random.nextFloat() * 2 - 1) * (WORLD - 80)
            val z = (Random.nextFloat() * 2 - 1) * (WORLD - 80)
            val reg = regionAt(x, z)
            if (reg == Region.CENTER && Random.nextFloat() < 0.8f) continue
            if (!isWater(x, z, 25f)) continue
            if (w != null) { w.x = x; w.z = z; w.inst.transform.setToTranslation(x, -0.5f, z); return }
            wrecks += Wreck(x, z, ModelInstance(wreckModel).also { it.transform.setToTranslation(x, -0.5f, z).rotate(Vector3.Y, Random.nextFloat() * 360) })
            return
        }
    }

    // ===================== Обновление =====================
    fun update(dt: Float) {
        val p = g.player ?: return
        updateRegion(dt, p)
        updateFortresses(dt, p)
        updateKraken(dt, p)
        updateMines(dt)
        updateReefs(p)
        updateWrecks(dt, p)
        updateMerchants(dt, p)
        updateFlotillas(dt, p)
        updateProtect(dt, p)
        updateBoarding(p, dt)
        updateBottles(dt, p)
        updateAmbience(dt, p)
        revealT -= dt
        if (revealT <= 0) { revealT = 0.3f; explored.reveal(p.x, p.z, 260f) }
        waypoint?.let { w -> if (hypot(w[0] - p.x, w[1] - p.z) < 40) { waypoint = null; g.toast("Вы прибыли к отметке", 1.4f) } }
        fluctT -= dt
        if (fluctT <= 0) {
            fluctT = 240f
            for (row in fluct) for (i in row.indices) row[i] = rand(0.85f, 1.15f)
        }
        // пройденное расстояние
        distAcc += hypot(p.vx, p.vz) * dt
        if (distAcc > 100) { addStat("distance", distAcc.toInt()); distAcc = 0f }
    }

    private var distAcc = 0f

    private fun updateRegion(dt: Float, p: Ship) {
        val r = regionAt(p.x, p.z)
        if (r != region) {
            region = r
            g.toast(r.title, 2f)
            if (r != Region.CENTER) g.questEvent(QType.ZONE, r.name, false)
        }
        val (d, m, fog, fogMix, fogScale) = when (r) {
            Region.ICE -> Five(Color(0.22f, 0.40f, 0.55f, 1f), Color(0.42f, 0.63f, 0.76f, 1f), Color(0.86f, 0.92f, 0.97f, 1f), 0.6f, 0.8f)
            Region.FIRE -> Five(Color(0.15f, 0.25f, 0.33f, 1f), Color(0.28f, 0.38f, 0.44f, 1f), Color(0.62f, 0.45f, 0.38f, 1f), 0.65f, 0.75f)
            Region.REEF -> Five(Color(0.10f, 0.38f, 0.42f, 1f), Color(0.20f, 0.56f, 0.55f, 1f), Color(0.70f, 0.78f, 0.76f, 1f), 0.85f, 0.42f)
            Region.ABYSS -> Five(Color(0.03f, 0.13f, 0.28f, 1f), Color(0.07f, 0.22f, 0.40f, 1f), Color(0.20f, 0.28f, 0.42f, 1f), 0.6f, 0.8f)
            else -> Five(Color(0.11f, 0.42f, 0.70f, 1f), Color(0.19f, 0.57f, 0.83f, 1f), Color(0.56f, 0.77f, 0.91f, 1f), 0f, 1f)
        }
        val k = min(1f, dt * 0.6f)
        deep.lerp(d, k); mid.lerp(m, k)
        regionFog.lerp(fog, k)
        regionFogMix += (fogMix - regionFogMix) * k
        regionFogScale += (fogScale - regionFogScale) * k
        waterDeep[0] = deep.r; waterDeep[1] = deep.g; waterDeep[2] = deep.b
        waterMid[0] = mid.r; waterMid[1] = mid.g; waterMid[2] = mid.b
        // снег и пепел
        if (g.highQuality) {
            val cx = g.camPos.x; val cz = g.camPos.z - 70
            if (r == Region.ICE && Random.nextFloat() < 0.8f) repeat(2) {
                g.fx.spawn(Color(1f, 1f, 1f, 0.9f), cx + rand(-90f, 90f), rand(30f, 70f), cz + rand(-110f, 70f), rand(-2f, 2f), -8f, rand(-1f, 1f), 0.35f, 6f, Fx.Mode.SHRINK)
            }
            if (r == Region.FIRE && Random.nextFloat() < 0.6f) {
                g.fx.spawn(Color(0.25f, 0.22f, 0.2f, 0.8f), cx + rand(-90f, 90f), rand(5f, 40f), cz + rand(-110f, 70f), rand(-1f, 1f), 2f, rand(-1f, 1f), 0.3f, 5f, Fx.Mode.SHRINK)
            }
        }
    }

    private data class Five(val a: Color, val b: Color, val c: Color, val d: Float, val e: Float)

    // ---------- Крепости ----------
    private fun updateFortresses(dt: Float, p: Ship) {
        for (f in fortresses) {
            if (!f.alive) {
                f.destroyedT -= dt
                if (f.destroyedT <= 0) { f.destroyedT = 0f; f.hp = f.maxHp }
                if (Random.nextFloat() < dt * 3) g.fx.spawn(g.fx.DARK, f.x + rand(-8f, 8f), 8f, f.z + rand(-8f, 8f), 0f, 4f, 0f, 2f, 2f, Fx.Mode.PUFF)
                continue
            }
            val tgt = g.ships.filter { it.faction == Faction.PLAYER && !it.sinking && hypot(it.x - f.x, it.z - f.z) < 110 }
                .minByOrNull { hypot(it.x - f.x, it.z - f.z) } ?: continue
            if (tgt === p && p.safe) continue
            g.combatT = max(g.combatT, 3f)
            f.cd -= dt
            if (f.cd <= 0) {
                f.cd = 2.6f
                val a = atan2(tgt.x - f.x, -(tgt.z - f.z))
                for (k in 0 until 4) {
                    val sa = a + (k - 1.5f) * 0.35f
                    val sx = f.x + sin(sa) * f.r * 0.7f; val sz = f.z - cos(sa) * f.r * 0.7f
                    g.launchBall(null, Faction.PIRATE, sx, 10.5f, sz, tgt.x, tgt.z, tgt.vx, tgt.vz, 16f + g.questIdx * 0.5f, Ammo.NORMAL, k * 0.12f, 1.4f)
                }
                g.sfx.play("cannon", g.volumeAt(f.x, f.z))
            }
        }
    }

    fun damageFortress(f: Fortress, dmg: Float) {
        if (!f.alive) return
        f.hp -= dmg
        f.lastHit = g.now
        g.floatText(f.x, 16f, f.z, "-${dmg.roundToInt()}", Color.WHITE, g.ui.mid)
        if (f.hp <= 0) {
            f.destroyedT = 600f
            repeat(4) { g.fx.explosion(f.x + rand(-10f, 10f), 8f, f.z + rand(-10f, 10f), 2f) }
            g.sfx.play("sink")
            g.dropLootPublic(f.x, f.z + f.r + 8, (900 * luckMul()).roundToInt(), 4f)
            gainXp(250, f.x, f.z)
            addStat("forts")
            g.questEvent(QType.FORTRESS, "", false)
            g.toast("Крепость разрушена!", 2.2f)
        }
    }

    /** Попадание ядра (или взрыв) по крепости или кракену. */
    fun hitStatic(x: Float, y: Float, z: Float, dmg: Float, radius: Float = 0f): Boolean {
        for (f in fortresses) {
            if (!f.alive) continue
            if (hypot(x - f.x, z - f.z) < f.r * 0.85f + radius && y < 16f) { g.fx.explosion(x, y, z, 0.6f); damageFortress(f, dmg); return true }
        }
        val k = kraken ?: return false
        if (k.dead || k.diving) return false
        val headY = 3f - k.depth
        if (hypot(x - k.x, z - k.z) < 7f + radius && abs(y - headY) < 7f) { damageKraken(dmg); g.fx.explosion(x, y, z, 0.6f); return true }
        for (t in k.seg) for (s in t) if (abs(x - s.x) < 2.6f + radius && abs(z - s.z) < 2.6f + radius && abs(y - s.y) < 2.8f + radius) {
            damageKraken(dmg * 0.8f); g.fx.explosion(x, y, z, 0.4f); return true
        }
        return false
    }

    // ---------- Кракен ----------
    fun spawnKraken(x: Float, z: Float) {
        val head = ModelInstance(krakenHeadModel)
        val ts = (0 until 6).map { (0 until 8).map { ModelInstance(tentacleModel) } }
        kraken = Kraken(x, z, head, ts)
        g.sfx.play("roar"); g.shake = 0.6f
        g.toast("Кракен поднимается из бездны!", 2.4f)
    }

    private fun damageKraken(dmg: Float) {
        val k = kraken ?: return
        if (k.dead) return
        k.hp -= dmg
        k.lastHit = g.now
        g.combatT = 8f
        g.floatText(k.x, 12f, k.z, "-${dmg.roundToInt()}", Color.WHITE, g.ui.mid)
        if (k.hp <= 0) {
            k.deadT = 0.001f
            repeat(5) { g.fx.explosion(k.x + rand(-8f, 8f), 3f, k.z + rand(-8f, 8f), 2f) }
            g.fx.splash(k.x, k.z, 3f)
            g.sfx.play("roar")
            g.dropLootPublic(k.x, k.z, (3000 * luckMul()).roundToInt(), 15f)
            gainXp(400, k.x, k.z)
            addStat("krakens")
            g.questEvent(QType.KRAKEN, "", false)
            g.toast("Кракен повержен!", 2.6f)
        }
    }

    private var krakenT = 60f

    private fun updateKraken(dt: Float, p: Ship) {
        val k = kraken
        if (k == null) {
            krakenT -= dt
            val q = g.quest()
            if (regionAt(p.x, p.z) == Region.ABYSS && (q.type == QType.KRAKEN || krakenT <= 0) && !p.safe) {
                g.findSpawnPoint(120f, 200f)?.let { spawnKraken(it.first, it.second) }
                krakenT = rand(300f, 500f)
            }
            return
        }
        val now = g.now
        if (k.dead) {
            k.deadT += dt
            k.depth = min(20f, k.deadT * 5)
            if (k.deadT > 4f) { kraken = null; return }
        } else {
            val d = hypot(p.x - k.x, p.z - k.z)
            if (d > 650) { kraken = null; return }
            // ныряние
            if (k.diving) {
                k.diveT -= dt
                k.depth = min(14f, k.depth + dt * 10)
                k.x += (p.x - k.x) * min(1f, dt * 0.6f); k.z += (p.z - 30 - k.z) * min(1f, dt * 0.6f)
                if (k.diveT <= 0) {
                    g.fx.splash(k.x, k.z, 3f); g.sfx.play("roar"); g.shake = max(g.shake, 0.5f)
                    for (s in g.ships) if (s.faction == Faction.PLAYER && hypot(s.x - k.x, s.z - k.z) < 22) g.damage(s, 30f, null, false)
                    k.surfaceT = rand(14f, 20f)
                }
            } else {
                k.depth = max(0f, k.depth - dt * 6)
                k.surfaceT -= dt
                if (k.surfaceT <= 0) { k.diveT = 4f; k.slamIdx = -1 }
                // держится рядом с игроком
                val want = atan2(p.x - k.x, -(p.z - k.z))
                k.h = wrapAngle(k.h + clamp(wrapAngle(want - k.h), -0.8f * dt, 0.8f * dt))
                if (d > 26) { k.x += sin(k.h) * 7f * dt; k.z -= cos(k.h) * 7f * dt }
                // удар щупальцем
                k.slamCd -= dt
                if (k.slamIdx < 0 && k.slamCd <= 0 && d < 70 && !p.safe) {
                    k.slamIdx = Random.nextInt(6); k.slamT = 0f
                    k.slamX = p.x + p.vx * 0.8f; k.slamZ = p.z + p.vz * 0.8f
                    k.slamCd = rand(2.2f, 3.5f)
                }
                if (k.slamIdx >= 0) {
                    k.slamT += dt
                    if (k.slamT >= 1f && k.slamT - dt < 1f) {
                        g.fx.splash(k.slamX, k.slamZ, 2.5f); g.sfx.play("hit"); g.shake = max(g.shake, 0.4f)
                        for (s in g.ships) if (s.faction == Faction.PLAYER && !s.sinking && hypot(s.x - k.slamX, s.z - k.slamZ) < 10) {
                            g.damage(s, 45f, null, false); s.rollVel += 1.2f
                        }
                    }
                    if (k.slamT > 1.6f) k.slamIdx = -1
                }
                // хватка: удерживает и медленно ломает корабль
                k.grabCd -= dt
                if (k.grabCd <= 0 && d < 28 && !p.safe) {
                    k.grabCd = rand(8f, 12f)
                    p.slowT = 3f
                    p.burnT = 0f
                    g.toast("Кракен схватил корабль!\nРывок поможет вырваться", 1.8f)
                }
                if (p.slowT > 0 && d < 30 && Random.nextFloat() < dt * 4) g.damage(p, 6f, null, false, silent = true)
            }
        }
        // щупальца
        for (i in 0 until 6) {
            val a = k.h + i * (PI.toFloat() / 3) + 0.5f
            val bx = k.x + sin(a) * 6f; val bz = k.z - cos(a) * 6f
            for (j in 0 until 8) {
                val sp = k.seg[i][j]
                if (i == k.slamIdx) {
                    val t = j / 7f
                    val raise = if (k.slamT < 0.8f) k.slamT / 0.8f * 20f else max(0f, (1f - k.slamT) * 100f)
                    sp.set(bx + (k.slamX - bx) * t, (sin(t * PI.toFloat()) * raise + 1f) * (1 - t * 0.2f) - k.depth, bz + (k.slamZ - bz) * t)
                } else {
                    val wave = sin(now * 2f + i + j * 0.6f)
                    sp.set(bx + sin(a + wave * 0.3f) * j * 2.6f, wave * 1.5f + j * 0.35f + 0.5f - k.depth, bz - cos(a + wave * 0.3f) * j * 2.6f)
                }
                val s = 1.05f - j * 0.09f
                k.tentacles[i][j].transform.setToTranslation(sp).scale(s, s, s)
            }
        }
        k.head.transform.setToTranslation(k.x, 2.5f + sin(now * 1.5f) * 0.6f - k.depth, k.z).rotate(Vector3.Y, -k.h * MathUtils.radiansToDegrees)
    }

    // ---------- Мины ----------
    fun dropMines(p: Ship) {
        for (i in -1..1) {
            val bx = p.x - sin(p.h) * (p.len * 0.6f + 2) + cos(p.h) * i * 4f
            val bz = p.z + cos(p.h) * (p.len * 0.6f + 2) + sin(p.h) * i * 4f
            mines += Mine(bx, bz, ModelInstance(mineModel))
        }
    }

    private fun updateMines(dt: Float) {
        val it = mines.iterator()
        while (it.hasNext()) {
            val m = it.next()
            m.t += dt
            m.inst.transform.setToTranslation(m.x, waveHeight(m.x, m.z, g.now) + 0.3f, m.z).rotate(Vector3.Y, m.t * 20)
            val hit = g.ships.firstOrNull { it.faction == Faction.PIRATE && !it.sinking && hypot(it.x - m.x, it.z - m.z) < it.radius * 0.6f + 3f }
            if (hit != null || m.t > 60) {
                if (hit != null) {
                    g.fx.explosion(m.x, 2f, m.z, 1.4f); g.fx.splash(m.x, m.z, 1.6f)
                    g.sfx.play("hit", g.volumeAt(m.x, m.z))
                    g.damage(hit, 80f * gunMul(), g.player, false)
                    hit.rollVel += 1.5f
                }
                it.remove()
            }
        }
    }

    // ---------- Подводные скалы ----------
    private fun updateReefs(p: Ship) {
        for (r in reefs) {
            for (s in g.ships) {
                if (s.sinking) continue
                val dx = s.x - r.x; val dz = s.z - r.z; val d = hypot(dx, dz)
                val mn = r.r + s.radius * 0.5f
                if (d < mn && d > 0.01f) {
                    val nx = dx / d; val nz = dz / d
                    s.x += nx * (mn - d); s.z += nz * (mn - d)
                    val vn = s.vx * nx + s.vz * nz
                    if (vn < 0) {
                        s.vx -= 1.4f * vn * nx; s.vz -= 1.4f * vn * nz
                        if (-vn > 3) {
                            g.damage(s, 10f + (-vn) * 1.5f, null, false)
                            g.fx.dust(r.x + nx * r.r, 1f, r.z + nz * r.r)
                            if (s === p) g.toast("Подводные скалы!", 1.2f)
                        }
                    }
                }
            }
        }
    }

    // ---------- Затонувшие корабли ----------
    private fun updateWrecks(dt: Float, p: Ship) {
        for (w in wrecks) {
            val d = hypot(p.x - w.x, p.z - w.z)
            if (d < 260) {
                w.bubbleT -= dt
                if (w.bubbleT <= 0) {
                    w.bubbleT = 0.12f
                    g.fx.spawn(Color(0.9f, 0.97f, 1f, 0.85f), w.x + rand(-4f, 4f), 0.4f, w.z + rand(-4f, 4f), 0f, rand(1f, 3f), 0f, rand(0.25f, 0.5f), 0.7f, Fx.Mode.SHRINK)
                    if (Random.nextFloat() < 0.3f) g.fx.spawn(g.fx.FOAM, w.x + rand(-3f, 3f), 0.6f, w.z + rand(-3f, 3f), 0f, 0f, 0f, 1.2f, 1f, Fx.Mode.FOAM, flat = true)
                }
            }
            if (d < 13 && abs(p.speed) < 4 && !p.sinking) {
                w.searchT += dt
                if (w.searchT >= 1.6f) {
                    w.searchT = 0f
                    val c = (Random.nextInt(300, 700) * luckMul()).roundToInt()
                    val gm = Random.nextInt(1, 4)
                    g.coins += c; g.gems += gm
                    val gi = Random.nextInt(GOODS.size)
                    val got = addCargo(gi, Random.nextInt(3, 9))
                    g.sfx.play("treasure")
                    g.toast("Ныряльщики подняли:\n+$c монет, +$gm кристалла" + (if (got > 0) ", ${GOODS[gi].name} ×$got" else ""), 2.6f)
                    gainXp(60, w.x, w.z)
                    addStat("wrecks")
                    g.questEvent(QType.WRECK, "", false)
                    placeWreck(w) { x, z, m -> g.isWaterPublic(x, z, m) }
                }
            } else w.searchT = 0f
        }
    }

    // ---------- Купцы и рыбаки ----------
    private fun updateMerchants(dt: Float, p: Ship) {
        merchantT -= dt
        if (merchantT <= 0) {
            merchantT = 20f
            val merchants = g.ships.count { it.type == "merchant" }
            if (merchants < 3) {
                val a = Random.nextInt(5); var b = Random.nextInt(5); if (b == a) b = (a + 1) % 5
                val pa = g.ports[a]
                if (hypot(pa.rx - p.x, pa.rz - p.z) < 600) {
                    val s = g.spawnNeutral("merchant", pa.rx, pa.rz + 10f)
                    s.wx = g.ports[b].rx; s.wz = g.ports[b].rz
                }
            }
            if (g.ships.count { it.type == "fisher" } < 4) {
                val isl = g.islands.filter { it.port < 0 && regionAt(it.x, it.z) == Region.CENTER && hypot(it.x - p.x, it.z - p.z) in 150f..500f }.randomOrNull()
                if (isl != null) {
                    val a = rand(0f, 6.28f)
                    val s = g.spawnNeutral("fisher", isl.x + cos(a) * (isl.r + 25), isl.z + sin(a) * (isl.r + 25))
                    s.wx = isl.x; s.wz = isl.z; s.aiT = isl.r + 25
                }
            }
        }
        g.ships.removeAll { (it.type == "merchant" || it.type == "fisher") && hypot(it.x - p.x, it.z - p.z) > 900 }
    }

    /** Поведение мирных судов: купцы идут в порт назначения, рыбаки кружат у острова. */
    fun neutralAI(s: Ship, dt: Float) {
        if (s.sinking) return
        if (s.type == "fisher") {
            val a = atan2(s.x - s.wx, -(s.z - s.wz))
            s.desired = a + PI.toFloat() / 2 * 1.1f
            s.throttle = 0.35f
            return
        }
        val pirate = g.ships.filter { it.faction == Faction.PIRATE && !it.sinking && hypot(it.x - s.x, it.z - s.z) < 90 }.minByOrNull { hypot(it.x - s.x, it.z - s.z) }
        if (pirate != null && s.lastHit > g.now - 8) {
            s.desired = atan2(s.x - pirate.x, -(s.z - pirate.z)); s.throttle = 1f
        } else {
            s.desired = atan2(s.wx - s.x, -(s.wz - s.z)); s.throttle = 0.7f
        }
        if (hypot(s.wx - s.x, s.wz - s.z) < 60) g.ships.remove(s)
    }

    private fun updateProtect(dt: Float, p: Ship) {
        val m = protect
        if (m == null) {
            val attacked = g.ships.firstOrNull { it.type == "merchant" && !it.sinking && it.lastHit > g.now - 2 && hypot(it.x - p.x, it.z - p.z) < 320 }
            if (attacked != null) {
                protect = attacked; protectCalmT = 0f
                g.toast("Пираты атакуют купца!\nЗащитите его", 2.2f)
            }
            // для задания создаём такое событие
            val q = g.quest()
            if (q.type == QType.PROTECT && attacked == null && !p.safe && Random.nextFloat() < dt * 0.1f) {
                g.findSpawnPoint(140f, 200f)?.let { (x, z) ->
                    val s = g.spawnNeutral("merchant", x, z)
                    val port = g.ports.minByOrNull { hypot(it.rx - x, it.rz - z) }!!
                    s.wx = port.rx; s.wz = port.rz
                    repeat(2) { k -> g.spawnEnemy("sloop", x + 40f * (k * 2 - 1), z + 30f).also { e -> e.ai = AiState.ATTACK; e.target = s } }
                    s.lastHit = g.now
                }
            }
            return
        }
        if (m.sinking || !g.ships.contains(m)) {
            if (m.sinking) g.toast("Купец потоплен…", 1.8f)
            protect = null; return
        }
        val near = g.ships.any { it.faction == Faction.PIRATE && !it.sinking && hypot(it.x - m.x, it.z - m.z) < 170 }
        if (!near) {
            protectCalmT += dt
            if (protectCalmT > 2.5f) {
                protect = null
                val c = (350 * luckMul()).roundToInt()
                g.coins += c; g.gems += 1
                g.sfx.play("treasure")
                g.toast("Купец спасён!\n+$c монет, +1 кристалл", 2.4f)
                gainXp(80, m.x, m.z)
                addStat("protects")
                g.questEvent(QType.PROTECT, "", false)
            }
        } else protectCalmT = 0f
    }

    // ---------- Флотилии и адмирал ----------
    private fun updateFlotillas(dt: Float, p: Ship) {
        val q = g.quest()
        val far = hypot(p.x, p.z) > 1000
        flotillaT -= dt
        val hasFlag = g.ships.any { it.type == "flagship" && !it.sinking }
        if (!hasFlag && !p.safe && ((q.type == QType.FLAGSHIP) || (far && flotillaT <= 0))) {
            flotillaT = rand(240f, 400f)
            g.findSpawnPoint(260f, 360f)?.let { (x, z) ->
                val flag = g.spawnEnemy("flagship", x, z)
                val escort = if (hypot(x, z) > 1300) "frigate" else "brig"
                repeat(3) { k -> g.spawnEnemy(escort, x + (k - 1) * 18f, z + 22f).also { it.leader = flag } }
                g.toast("Пиратская флотилия на горизонте!", 2.2f)
            }
        }
        val hasAdmiral = g.ships.any { it.type == "admiral" && !it.sinking }
        if (q.type == QType.ADMIRAL && !hasAdmiral && regionAt(p.x, p.z) == Region.ABYSS && !p.safe) {
            g.findSpawnPoint(260f, 340f)?.let { (x, z) ->
                val adm = g.spawnEnemy("admiral", x, z)
                repeat(2) { k -> g.spawnEnemy("frigate", x + (k * 2 - 1) * 24f, z + 26f).also { it.leader = adm } }
                g.sfx.play("roar")
                g.toast("«Кровавая Луна» адмирала Гарпуна!", 2.6f)
            }
        }
    }

    // ---------- Абордаж ----------
    /** Можно ли взять корабль на абордаж (без учёта расстояния). */
    fun boardable(s: Ship) = s.faction == Faction.PIRATE && !s.sinking && s.type != "admiral" && s.hp <= s.maxHp * 0.5f

    private fun updateBoarding(p: Ship, dt: Float) {
        // крюки уже летят: стягиваем корабли
        val gt = grappleTarget
        if (gt != null) {
            grappleT += dt
            val dx = gt.x - p.x; val dz = gt.z - p.z; val d = hypot(dx, dz).coerceAtLeast(0.1f)
            val want = p.radius + gt.radius + 2f
            if (d > want) {
                val pull = min(18f, (d - want) * 2.5f)
                gt.vx = -dx / d * pull * 0.7f; gt.vz = -dz / d * pull * 0.7f
                p.vx = dx / d * pull * 0.3f; p.vz = dz / d * pull * 0.3f
            } else { gt.vx *= 0.8f; gt.vz *= 0.8f; p.vx *= 0.8f; p.vz *= 0.8f }
            if (gt.sinking || !g.ships.contains(gt)) { grappleTarget = null; return }
            if (grappleT > 1.4f && d < want + 4f || grappleT > 3f) {
                grappleTarget = null
                g.menus.showBoarding(gt)
            }
            return
        }
        boardTarget = null
        if (p.sinking) return
        var best: Ship? = null; var bd = Float.MAX_VALUE
        for (s in g.ships) {
            if (!boardable(s)) continue
            val d = hypot(s.x - p.x, s.z - p.z)
            if (!boardHintShown && d < 160) {
                boardHintShown = true
                g.toast("Враг ослаблен — его можно взять на абордаж!\nПодплывите ближе 30 м и жмите «Абордаж!»", 3.5f)
            }
            if (d > p.radius + s.radius + 30) continue
            if (hypot(s.vx - p.vx, s.vz - p.vz) > 14) continue
            if (d < bd) { bd = d; best = s }
        }
        boardTarget = best
    }

    /** Бросить абордажные крюки: корабли стягиваются, затем начинается схватка. */
    fun throwGrapples(t: Ship) {
        if (grappleTarget != null) return
        grappleTarget = t
        grappleT = 0f
        boardTarget = null
        g.sfx.play("chain")
    }

    /** Сила абордажной команды игрока относительно врага (≈1 — равные силы). */
    fun boardingRatio(t: Ship): Float {
        val p = g.player ?: return 1f
        val mine = p.stats.mass * 10 * (1 + 0.15f * skill("boarding")) * (0.5f + 0.5f * p.hp / p.maxHp)
        val theirs = t.stats.mass * 10 * (0.35f + t.hp / t.maxHp)
        return mine / theirs
    }

    private fun updateBottles(dt: Float, p: Ship) {
        val it = bottles.iterator()
        while (it.hasNext()) {
            val bt = it.next()
            bt.t += dt
            bt.inst.transform.setToTranslation(bt.x, waveHeight(bt.x, bt.z, g.now) + 0.4f + sin(bt.t * 2) * 0.2f, bt.z)
                .rotate(Vector3.Z, 70f + sin(bt.t * 1.3f) * 10f).rotate(Vector3.X, bt.t * 15f)
            if (hypot(bt.x - p.x, bt.z - p.z) < p.radius * 0.6f + 6f && !p.sinking) {
                shanties += bt.song
                shantyCur = bt.song
                lastShanty = -2
                g.sfx.play("treasure")
                g.toast("Бутылка с песней!\nКоманда выучила «${SHANTIES[bt.song].title}»", 3f)
                gainXp(80, bt.x, bt.z)
                it.remove()
                g.save()
            }
        }
    }

    /** Какую песню должна петь команда сейчас (или -1). */
    fun wantedShanty(): Int {
        val p = g.player ?: return -1
        if (!shantiesOn || g.state != SeaGame.State.PLAY) return -1
        if (g.combatT > 0 || g.stormLevel > 0.55f || abs(p.speed) < 4f || p.sinking) return -1
        return shantyCur
    }

    private fun updateAmbience(dt: Float, p: Ship) {
        // песни: по окончании — следующая из выученных
        if (g.sfx.shantyFinished) {
            val list = shanties.sorted()
            shantyCur = list[(list.indexOf(shantyCur) + 1) % list.size]
        }
        if (g.sfx.shantyIdx >= 0 && g.sfx.shantyIdx != lastShanty) { lastShanty = g.sfx.shantyIdx; shantyBanner = 5f }
        if (g.sfx.shantyIdx < 0 && lastShanty >= 0) lastShanty = -1
        if (shantyBanner > 0) shantyBanner -= dt
        // чайки у берега днём
        gullT -= dt
        if (gullT <= 0) {
            gullT = rand(5f, 12f)
            val near = g.islands.minOfOrNull { hypot(it.x - p.x, it.z - p.z) - it.r } ?: 999f
            if (near < 200 && g.sky.day > 0.5f && g.stormLevel < 0.4f) g.sfx.play(if (Random.nextBoolean()) "gull1" else "gull2", clamp(1f - near / 220f, 0.1f, 0.7f), 0.25f)
        }
        // скрип корпуса
        creakT -= dt
        if (creakT <= 0) {
            creakT = rand(7f, 16f)
            if (abs(p.speed) > 3 || g.stormLevel > 0.3f) g.sfx.play("creak", 0.35f + g.stormLevel * 0.4f, 0.3f)
        }
        // далёкий гром перед и во время шторма
        if (g.stormLevel > 0.12f) {
            farThunderT -= dt
            if (farThunderT <= 0) { farThunderT = rand(9f, 20f); g.sfx.play("thunder_far", 0.4f + g.stormLevel * 0.4f, 0.2f) }
        }
    }

    fun luckMul() = 1f + 0.08f * skill("luck")
    fun gunMul() = 1f + 0.06f * skill("gunner")

    // ===================== Отрисовка =====================
    fun render(batch: ModelBatch, env: Environment, lookX: Float, lookZ: Float) {
        for (f in fortresses) if (hypot(f.x - lookX, f.z - lookZ) < 600) batch.render(if (f.alive) f.inst else f.ruins, env)
        for (m in mines) batch.render(m.inst, env)
        val p = g.player
        for (r in reefs) if (p != null && hypot(r.x - p.x, r.z - p.z) < 170) batch.render(r.inst, env)
        for (w in wrecks) if (hypot(w.x - lookX, w.z - lookZ) < 500) batch.render(w.inst, env)
        for (bt in bottles) if (hypot(bt.x - lookX, bt.z - lookZ) < 500) batch.render(bt.inst, env)
        kraken?.let { k ->
            if (hypot(k.x - lookX, k.z - lookZ) < 600) {
                batch.render(k.head, env)
                for (t in k.tentacles) for (s in t) batch.render(s, env)
            }
        }
    }

    fun renderShadows(batch: ModelBatch, lookX: Float, lookZ: Float) {
        for (f in fortresses) if (hypot(f.x - lookX, f.z - lookZ) < 260) batch.render(if (f.alive) f.inst else f.ruins)
        kraken?.let { k -> if (!k.dead && hypot(k.x - lookX, k.z - lookZ) < 260) { batch.render(k.head); for (t in k.tentacles) for (sg in t) batch.render(sg) } }
    }

    private val ringCol = Color(1f, 1f, 1f, 0.5f)
    private val lavaCol = Color(1f, 0.45f, 0.1f, 0.8f)

    fun renderDecals(lookX: Float, lookZ: Float) {
        val p = g.player ?: return
        for (r in reefs) {
            val d = hypot(r.x - p.x, r.z - p.z)
            if (d < 130) g.fx.frame(g.ui.ring, r.x, waveHeight(r.x, r.z, g.now) + 0.5f, r.z, r.r * 2.6f, r.r * 2.6f,
                ringCol.set(1f, 1f, 1f, 0.6f * (1 - d / 130f)), true, 0f)
        }
        for (isl in g.islands) {
            if (isl.glows.isEmpty() || hypot(isl.x - lookX, isl.z - lookZ) > 520) continue
            for (gl in isl.glows) g.fx.glow(gl.x, gl.y, gl.z, 9f + sin(g.now * 3) * 1.5f, lavaCol)
        }
        // пробоины на кораблях
        for (s in g.ships) {
            if (s.sinking || s.holes.isEmpty() || hypot(s.x - lookX, s.z - lookZ) > 400) continue
            val fx = sin(s.h); val fz = -cos(s.h); val rx = cos(s.h); val rz = sin(s.h)
            for (h in s.holes) {
                g.fx.frame(g.ui.softDisc, s.x + fx * h.x + rx * h.z, s.y + h.y, s.z + fz * h.x + rz * h.z, 1.5f, 1.5f, holeCol, false)
            }
        }
    }

    private val holeCol = Color(0.08f, 0.05f, 0.03f, 0.9f)
    private val ropeCol = Color(0.35f, 0.24f, 0.12f, 1f)
    private val sparkCol = Color(1f, 0.9f, 0.4f, 0.8f)

    fun renderExtraDecals() {
        // абордажные канаты
        val p = g.player
        val t = grappleTarget
        if (p != null && t != null) {
            for (k in 0 until 3) {
                val ox = (k - 1) * p.len * 0.25f
                val ax = p.x + sin(p.h) * ox; val az = p.z - cos(p.h) * ox
                val bx = t.x + sin(t.h) * ox; val bz = t.z - cos(t.h) * ox
                val prog = min(1f, grappleT / 0.5f)
                for (i in 0..24) {
                    val f = i / 24f * prog
                    val x = ax + (bx - ax) * f; val z = az + (bz - az) * f
                    val y = p.y + p.deckTop + 2f + sin(f * PI.toFloat()) * 4f * (1 - min(1f, grappleT / 1.4f))
                    g.fx.frame(g.ui.softDisc, x, y, z, 0.9f, 0.9f, ropeCol)
                }
            }
        }
        for (bt in bottles) {
            if (p != null && hypot(bt.x - p.x, bt.z - p.z) < 300) {
                val pulse = 0.5f + 0.5f * sin(g.now * 4 + bt.song)
                g.fx.glow(bt.x, 2f, bt.z, 4f + pulse * 2f, sparkCol.set(1f, 0.9f, 0.4f, 0.4f + 0.4f * pulse))
            }
        }
    }

    fun vibrate(ms: Int) {
        if (vibrate) try { Gdx.input.vibrate(ms) } catch (_: Exception) { }
    }
}
