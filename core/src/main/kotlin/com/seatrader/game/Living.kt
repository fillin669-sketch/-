package com.seatrader.game

import com.badlogic.gdx.Preferences
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.utils.Align
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

class Berg(var x: Float, var z: Float, var vx: Float, var vz: Float, val r: Float, val inst: ModelInstance, val spin: Float) { var hitCd = 0f }
class Whirl(val x: Float, val z: Float, val r: Float)
class Ruin(val idx: Int, val x: Float, val z: Float, val inst: ModelInstance)

/**
 * «Живой мир»: репутация, офицеры, охотники за головами, база игрока,
 * приказы флоту, туман, штиль, водовороты, айсберги, высадки, руины и призраки.
 */
class Living(private val g: SeaGame) {
    // ---------- Репутация: торговцы, флот, пираты ----------
    val rep = IntArray(3)
    val repNames = listOf("Торговцы", "Флот", "Пираты")

    // ---------- Офицеры ----------
    val hired = HashSet<Int>()
    val crew = IntArray(4) { -1 }

    // ---------- Охотники за головами ----------
    val defeated = HashSet<Int>()
    var activeBounty = -1
    private var hunter: Ship? = null
    private var huntT = 300f
    private var bountyRespawnT = 0f
    private var navyT = 30f

    // ---------- База ----------
    var baseFounded = false
    val blev = IntArray(BUILDINGS.size)
    var bank = 0f
    private var lastSeen = 0L
    var baseX = 0f; var baseZ = 0f
    val baseR = 34f
    private var baseBuilt = false
    private val buildingInst = ArrayList<ModelInstance>()
    private val buildingModels = HashMap<String, Model>()
    private lateinit var models: MutableList<Model>
    private var ringInst: ModelInstance? = null
    val ringX get() = baseX
    val ringZ get() = baseZ + baseR + 26f
    private var atBase = false
    private var batteryCd = 2f
    private var revealT = 0f

    // ---------- Флот ----------
    var order = 0 // 0 строй, 1 атака, 2 отход
    val orderNames = listOf("Колонна", "Атака", "Отход")

    // ---------- Погода ----------
    val fogBanks = ArrayList<FloatArray>() // x, z, r
    var fogK = 0f
    var calmK = 0f
    private var calmOn = false
    private var calmT = rand(240f, 420f)
    val whirls = ArrayList<Whirl>()
    val bergs = ArrayList<Berg>()
    private var whirlWarn = 0f

    // ---------- Высадки и руины ----------
    val visited = HashSet<Int>()
    val dived = HashSet<Int>()
    var mapPieces = 0
    var mapTarget = -1
    val ruins = ArrayList<Ruin>()

    // ---------- Призраки ----------
    private var ghostT = rand(40f, 90f)

    // ---------- Реплики офицеров ----------
    var sayOfficer = -1
    var sayText = ""
    var sayT = 0f
    private var talkCd = 20f

    // ---------- Кнопка действия ----------
    var actionText: String? = null
    var actionSub: String? = null
    private var actionFn: (() -> Unit)? = null
    private var actionCd = 0f

    // ===================================================================
    // Офицеры и бонусы
    private fun active(): List<Officer> = crew.filter { it >= 0 }.map { OFFICERS[it] }
    private fun off(sel: (Officer) -> Float): Float = active().sumOf { sel(it).toDouble() }.toFloat()

    private val hero get() = g.hero()

    fun applyStats(b: Stats): Stats = Stats(
        hp = (b.hp * (1 + off { it.hp } + (hero?.hp ?: 0f))).roundToInt(),
        speed = b.speed * (1 + off { it.speed } + (hero?.speed ?: 0f)),
        turn = b.turn * (1 + off { it.turn } + (hero?.turn ?: 0f)),
        cannons = b.cannons,
        dmg = b.dmg * (1 + off { it.dmg } + (hero?.dmg ?: 0f)),
        reload = b.reload * (1 - off { it.reload } - (hero?.reload ?: 0f)),
        range = b.range * (1 + off { it.range }),
        mass = b.mass,
    )

    fun buyMul() = 1f - off { it.trade } - (hero?.trade ?: 0f) - clamp(rep[0] / 1000f, -0.1f, 0.1f)
    fun sellMul() = 1f + off { it.trade } + (hero?.trade ?: 0f) + clamp(rep[0] / 1000f, -0.1f, 0.1f)
    fun lootMul() = 1f + off { it.loot } + (hero?.loot ?: 0f)
    fun boardMul() = 1f + off { it.board }
    fun gemBonus() = if (crew.contains(7)) 0.25f else 0f
    fun upgradeMul() = (if (baseFounded) 1f - 0.06f * blev[6] else 1f) * (1f - (hero?.upgrade ?: 0f))
    fun extraCargo() = if (baseFounded) 8 * blev[1] else 0
    fun officerPrice(o: Officer) = (o.price * (1f - (if (baseFounded && blev[2] >= 2) 0.1f * (blev[2] - 1) else 0f)) *
        (if (o.role == 0 || o.role == 1) 1f else 1f) * (1f - clamp(rep[0] / 1000f, 0f, 0.1f))).roundToInt()

    fun officerAvailable(o: Officer, port: Int) = port in o.ports || (port == BASE_PORT && baseFounded && blev[2] >= 1)

    fun hire(o: Officer): Boolean {
        val price = officerPrice(o)
        if (o.id !in hired) {
            if (g.coins < price || g.gems < o.gems) { g.toast("Не хватает средств"); return false }
            g.coins -= price; g.gems -= o.gems
            hired += o.id
        }
        crew[o.role] = o.id
        g.rebuildPlayer()
        say(o.id, o.quips[0])
        g.sfx.play("quest")
        g.save()
        return true
    }

    fun dismiss(role: Int) { crew[role] = -1; g.rebuildPlayer(); g.save() }

    fun say(id: Int, text: String) { sayOfficer = id; sayText = text; sayT = 4f; talkCd = 25f }

    /** Случайная реплика офицера нужной должности. */
    fun quip(role: Int, chance: Float = 1f) {
        val id = crew[role]
        if (id < 0 || talkCd > 0 || Random.nextFloat() > chance) return
        say(id, OFFICERS[id].quips.random())
    }

    // ===================================================================
    // Репутация
    fun addRep(i: Int, d: Int) {
        if (d == 0) return
        val old = rep[i]
        rep[i] = (rep[i] + d).coerceIn(-100, 100)
        if (rep[i] == old) return
        val p = g.player
        if (p != null) g.floatText(p.x, p.y + 14f + i * 2.5f, p.z, "${if (d > 0) "+" else ""}$d ${repNames[i].lowercase()}",
            rgb(if (d > 0) 0xa5d6a7 else 0xef9a9a), g.ui.small)
        val crossed = listOf(-30, 30).firstOrNull { (old < it) != (rep[i] < it) }
        if (crossed != null) g.toast(when {
            i == 2 && rep[2] >= 30 -> "Пираты вас уважают — мелкие шайки не нападут первыми"
            i == 2 && rep[2] < -30 -> "Пираты назначили награду за вашу голову!"
            i == 1 && rep[1] < -30 -> "Флот считает вас преступником — патрули атакуют!"
            i == 1 && rep[1] >= 30 -> "Флот на вашей стороне — патрули помогут в бою"
            i == 0 && rep[0] >= 30 -> "Торговцы доверяют вам — цены лучше"
            else -> "${repNames[i]}: ${repLabel(rep[i])}"
        }, 2.5f)
    }

    fun repLabel(v: Int) = when { v < -60 -> "ненавидят"; v < -20 -> "враждебны"; v < 20 -> "нейтральны"; v < 60 -> "уважают"; else -> "союзники" }
    val pirateFriendly get() = rep[2] >= 30
    val navyHostile get() = rep[1] <= -30

    /** Продажа товаров влияет на репутацию. */
    fun onTrade(port: Int, sold: Int) {
        if (sold <= 0) return
        tradeAcc += sold
        if (tradeAcc >= 10) {
            val k = tradeAcc / 10; tradeAcc %= 10
            if (port == 7) { addRep(2, k); addRep(1, -k) } else addRep(0, k)
        }
    }
    private var tradeAcc = 0

    fun tribute() {
        val price = 300
        if (g.coins < price) { g.toast("Не хватает монет"); return }
        g.coins -= price
        addRep(2, 10); addRep(1, -3)
        g.sfx.play("coin")
        g.save()
    }

    // ===================================================================
    // Гибель кораблей
    fun onSink(s: Ship, src: Ship?) {
        val byPlayer = src == null || src.faction == Faction.PLAYER
        if (s.faction == Faction.PIRATE && byPlayer) {
            if (s.type == "navy") { addRep(1, -6); return }
            if (s.ghost) { g.systems.addStat("ghosts"); g.toast("Призрак развеян!", 2f) }
            if (s.bounty >= 0) {
                val c = CAPTAINS[s.bounty]
                defeated += c.id
                if (activeBounty == c.id) activeBounty = -1
                if (hunter === s) hunter = null
                val reward = (c.bounty * (1f + max(0, rep[1]) / 200f)).roundToInt()
                g.coins += reward; g.gems += c.gems
                g.systems.addStat("bounties")
                g.toast("Награда за ${c.name}: +$reward монет, +${c.gems} кристаллов!", 3f)
                g.sfx.play("treasure")
                addRep(1, 8); addRep(2, -5)
                g.save()
            } else {
                if (Random.nextFloat() < 0.5f) addRep(2, -1)
                if (Random.nextFloat() < 0.5f) addRep(1, 1)
            }
            quip(1, 0.35f)
        } else if (s.faction == Faction.NEUTRAL && src === g.player) {
            if (s.type == "navy") { addRep(1, -15) } else { addRep(0, -10); addRep(1, -8); addRep(2, 4) }
        }
    }

    // ===================================================================
    // Охотники за головами
    fun acceptBounty(id: Int) {
        if (activeBounty >= 0) { g.toast("Сначала разберитесь с текущим заказом"); return }
        activeBounty = id
        bountyRespawnT = 4f
        g.toast("Заказ принят: ${CAPTAINS[id].name}.\nОн сам найдёт вас в море!", 3f)
        g.save()
    }

    private fun spawnCaptain(id: Int) {
        val c = CAPTAINS[id]
        val pt = g.findSpawnPoint(300f, 420f) ?: return
        val s = g.spawnEnemy(c.type, pt.first, pt.second, "☠ " + c.name, true)
        val st = s.stats
        val massMul = when (c.trait) { "rammer" -> 2f; "armored" -> 1.5f; else -> 1f }
        s.stats = Stats((st.hp * c.hpMul).roundToInt(), st.speed * c.speedMul, st.turn * (if (c.trait == "fast") 1.2f else 1f), st.cannons,
            st.dmg * c.dmgMul, st.reload, st.range, st.mass * massMul)
        s.hp = s.stats.hp.toFloat(); s.maxHp = s.hp
        s.bounty = id; s.hunter = true; s.trait = c.trait
        s.leash = 750f
        hunter = s
        if (c.trait == "escort") repeat(2) { k ->
            g.spawnEnemy("sloop", pt.first + (k * 2 - 1) * 20f, pt.second + 18f).also { it.leader = s; it.hunter = true }
        }
        g.toast("☠ ${c.name} вышел на охоту за вами!\n${c.traitText.replaceFirstChar { it.uppercase() }}.", 3.2f)
        g.sfx.play("thunder_far", 0.6f)
        quip(2, 1f)
    }

    private fun updateHunters(dt: Float) {
        val h = hunter
        if (h != null && (!g.ships.contains(h) || h.sinking)) hunter = null
        if (activeBounty >= 0 && hunter == null) {
            bountyRespawnT -= dt
            if (bountyRespawnT <= 0) { bountyRespawnT = 30f; spawnCaptain(activeBounty) }
        }
        huntT -= dt
        if (huntT <= 0) {
            huntT = rand(260f, 420f)
            if (hunter == null && rep[2] <= -30) {
                val left = CAPTAINS.filter { it.id !in defeated }
                if (left.isNotEmpty()) spawnCaptain(left.random().id)
            } else if (navyHostile) {
                repeat(2) { g.findSpawnPoint(280f, 380f)?.let { pt -> g.spawnEnemy("navy", pt.first, pt.second).also { it.hunter = true } } }
                g.toast("Флот отправил за вами погоню!", 2.5f)
            }
        }
        // патрули флота
        navyT -= dt
        val p = g.player ?: return
        if (navyT <= 0) {
            navyT = 40f
            val navy = g.ships.count { it.type == "navy" && !it.sinking }
            val want = when { navyHostile -> 0; rep[1] >= 30 -> 2; else -> 1 }
            if (navy < want && regionAt(p.x, p.z) == Region.CENTER) g.findSpawnPoint(260f, 380f)?.let { pt ->
                g.spawnNeutral("navy", pt.first, pt.second)
            }
        }
        g.ships.removeAll { it.type == "navy" && it.faction == Faction.NEUTRAL && hypot(it.x - p.x, it.z - p.z) > 900 }
    }

    /** Патруль флота охотится на пиратов поблизости, а если дружен с игроком — держится рядом. */
    fun navyAI(s: Ship, dt: Float) {
        if (s.sinking) return
        val p = g.player ?: return
        var foe: Ship? = null; var fd = 200f
        for (o in g.ships) if (o.faction == Faction.PIRATE && !o.sinking) { val d = hypot(o.x - s.x, o.z - s.z); if (d < fd) { fd = d; foe = o } }
        if (foe != null) { s.ai = AiState.ATTACK; s.target = foe; g.attackMove(s, foe, dt); return }
        s.ai = AiState.WANDER
        if (rep[1] >= 30 && hypot(p.x - s.x, p.z - s.z) < 400) {
            val tx = p.x + cos(p.h) * 40f; val tz = p.z + sin(p.h) * 40f
            val d = hypot(tx - s.x, tz - s.z)
            s.desired = if (d > 10) atan2(tx - s.x, -(tz - s.z)) else p.h
            s.throttle = clamp(d / 40f, 0.3f, 1f)
        } else {
            s.aiT -= dt
            if (s.aiT <= 0 || hypot(s.wx - s.x, s.wz - s.z) < 30) {
                s.aiT = rand(10f, 20f)
                s.wx = clamp(s.x + rand(-300f, 300f), -1000f, 1000f); s.wz = clamp(s.z + rand(-300f, 300f), -1000f, 1000f)
            }
            s.desired = atan2(s.wx - s.x, -(s.wz - s.z)); s.throttle = 0.5f
        }
    }

    // ===================================================================
    // База
    fun buildBase(models: MutableList<Model>) {
        this.models = models
        val cands = listOf(300f to 230f, -280f to -230f, 260f to -300f, -320f to 240f, 380f to 60f, -60f to 380f)
        val pick = cands.firstOrNull { (x, z) -> g.islands.none { hypot(it.x - x, it.z - z) < it.r + baseR + 80 } } ?: cands[0]
        baseX = pick.first; baseZ = pick.second
        val m = Models2.baseIsland(baseR).also { models += it }
        val f = Models.foam(baseR).also { models += it }
        g.islands += Island(baseX, baseZ, baseR * 1.06f, BASE_ISLAND, ModelInstance(m).also { it.transform.setToTranslation(baseX, 0f, baseZ) },
            ModelInstance(f).also { it.transform.setToTranslation(baseX, 0.6f, baseZ) }, emptyList())
        ringInst = ModelInstance(Models.portRing().also { models += it })
        baseBuilt = true
        refreshBuildings()
    }

    fun refreshBuildings() {
        if (!baseBuilt) return
        buildingInst.clear()
        if (!baseFounded) return
        for ((i, b) in BUILDINGS.withIndex()) {
            val lvl = blev[i]
            if (lvl <= 0) continue
            val key = b.id + lvl
            val m = buildingModels.getOrPut(key) { Models2.building(b.id, lvl).also { models += it } }
            val inst = ModelInstance(m)
            if (b.id == "dock") inst.transform.setToTranslation(baseX, 0.6f, baseZ + baseR * 0.86f)
            else {
                val a = b.angle * PI.toFloat() / 180f
                inst.transform.setToTranslation(baseX + cos(a) * baseR * b.dist, 3.7f, baseZ - sin(a) * baseR * b.dist)
                    .rotate(0f, 1f, 0f, b.angle - 90f)
            }
            buildingInst += inst
        }
    }

    fun incomePerMin(): Int = if (!baseFounded) 0 else 10 * blev[0] + 20 * blev[2] + 45 * blev[3]
    fun bankCap(): Int = 800 + 1200 * blev[1]

    fun foundBase() {
        if (baseFounded) return
        if (g.coins < BASE_FOUND_COST) { g.toast("Не хватает монет"); return }
        g.coins -= BASE_FOUND_COST
        baseFounded = true
        blev[0] = max(blev[0], 1)
        lastSeen = System.currentTimeMillis()
        refreshBuildings()
        g.sfx.play("quest")
        g.toast("Остров Надежды теперь ваш!", 2.5f)
        g.save()
    }

    fun upgradeBuilding(i: Int) {
        val lvl = blev[i]
        if (lvl >= 3) return
        val cost = buildingCost(i, lvl)
        if (g.coins < cost) { g.toast("Не хватает монет"); return }
        g.coins -= cost
        blev[i]++
        refreshBuildings()
        g.sfx.play("coin")
        g.save()
    }

    fun collectBank() {
        val n = bank.toInt()
        if (n <= 0) return
        g.coins += n; bank -= n
        g.sfx.play("coin")
        g.save()
    }

    private fun updateBase(dt: Float, p: Ship) {
        if (!baseBuilt) return
        if (baseFounded) bank = min(bankCap().toFloat(), bank + incomePerMin() * dt / 60f)
        val dBase = hypot(p.x - baseX, p.z - baseZ)
        if (dBase < baseR + 110) p.safe = true
        val d = hypot(p.x - ringX, p.z - ringZ)
        if (g.state == SeaGame.State.PLAY && !p.sinking && !atBase && d < 9) {
            atBase = true
            p.vx = 0f; p.vz = 0f; p.angVel = 0f
            if (baseFounded) { p.hp = p.maxHp; p.burnT = 0f }
            g.joyActive = false
            g.save()
            g.portView.enterBase()
            g.menus.showBase()
        }
        if (atBase && d > 18) atBase = false
        if (!baseFounded) return
        // маяк открывает карту
        revealT -= dt
        if (revealT <= 0 && blev[4] > 0) { revealT = 5f; g.systems.explored.reveal(baseX, baseZ, 200f + 160f * blev[4]) }
        // батарея
        val bl = blev[5]
        if (bl > 0) {
            batteryCd -= dt
            if (batteryCd <= 0) {
                val range = 140f + 30f * bl
                val foe = g.ships.filter { it.faction == Faction.PIRATE && !it.sinking && hypot(it.x - baseX, it.z - baseZ) < range }
                    .minByOrNull { hypot(it.x - baseX, it.z - baseZ) }
                batteryCd = if (foe != null) 3.6f - 0.6f * bl else 1f
                if (foe != null) {
                    val a = 270f * PI.toFloat() / 180f
                    val bx = baseX + cos(a) * baseR * 0.62f; val bz = baseZ - sin(a) * baseR * 0.62f
                    repeat(bl + 1) { k ->
                        g.launchBall(null, Faction.PLAYER, bx, 7f, bz, foe.x, foe.z, foe.vx, foe.vz, 14f + 6f * bl, Ammo.NORMAL, k * 0.15f, 1.2f)
                    }
                    g.fx.muzzle(bx, 7f, bz, foe.x - bx, foe.z - bz)
                    g.sfx.play("cannon", g.volumeAt(bx, bz))
                }
            }
        }
    }

    // ===================================================================
    // Мир: руины, водовороты, айсберги, туман
    fun buildWorld(isWater: (Float, Float, Float) -> Boolean) {
        val rnd = Random(9090)
        fun spot(margin: Float, ok: (Float, Float) -> Boolean): Pair<Float, Float>? {
            repeat(400) {
                val x = (rnd.nextFloat() * 2 - 1) * (WORLD - 120); val z = (rnd.nextFloat() * 2 - 1) * (WORLD - 120)
                if (ok(x, z) && isWater(x, z, margin) && g.ports.none { hypot(it.def.x - x, it.def.z - z) < 260 }) return x to z
            }
            return null
        }
        var ri = 0
        repeat(6) {
            spot(40f) { x, z -> ruins.none { r -> hypot(r.x - x, r.z - z) < 500 } && hypot(x - baseX, z - baseZ) > 200 }?.let { (x, z) ->
                val m = Models2.sunkenRuins(ri).also { models += it }
                ruins += Ruin(ri++, x, z, ModelInstance(m).also { it.transform.setToTranslation(x, 0f, z).scale(1.8f, 1.8f, 1.8f) })
            }
        }
        repeat(5) {
            spot(80f) { x, z -> regionAt(x, z) != Region.CENTER && whirls.none { w -> hypot(w.x - x, w.z - z) < 600 } }?.let { (x, z) -> whirls += Whirl(x, z, 60f) }
        }
        spot(80f) { x, z -> regionAt(x, z) == Region.CENTER && hypot(x, z) > 600 }?.let { (x, z) -> whirls += Whirl(x, z, 50f) }
        val bergModels = (0..2).map { Models2.iceberg(it).also { m -> models += m } }
        repeat(12) { k ->
            spot(30f) { x, z -> regionAt(x, z) == Region.ICE }?.let { (x, z) ->
                val a = rnd.nextFloat() * 6.28f
                val sc = 0.8f + rnd.nextFloat() * 0.7f
                bergs += Berg(x, z, cos(a) * 1.5f, sin(a) * 1.5f, 8.5f * sc, ModelInstance(bergModels[k % 3]).also { it.transform.setToScaling(sc, sc, sc) }, rnd.nextFloat() * 6f)
            }
        }
        repeat(8) {
            val x = (rnd.nextFloat() * 2 - 1) * (WORLD - 200); val z = (rnd.nextFloat() * 2 - 1) * (WORLD - 200)
            if (hypot(x, z) > 250) fogBanks += floatArrayOf(x, z, 110f + rnd.nextFloat() * 90f)
        }
    }

    private fun updateWeather(dt: Float, p: Ship) {
        // туманные банки дрейфуют по ветру
        val wx = sin(g.windA) * 3f * g.windS; val wz = -cos(g.windA) * 3f * g.windS
        var k = 0f
        for (f in fogBanks) {
            f[0] += wx * dt; f[1] += wz * dt
            if (f[0] > WORLD) f[0] -= WORLD * 2; if (f[0] < -WORLD) f[0] += WORLD * 2
            if (f[1] > WORLD) f[1] -= WORLD * 2; if (f[1] < -WORLD) f[1] += WORLD * 2
            val d = hypot(f[0] - p.x, f[1] - p.z)
            k = max(k, clamp((f[2] - d) / 60f, 0f, 1f))
        }
        val prevFog = fogK
        fogK += (k - fogK) * min(1f, dt * 1.5f)
        if (prevFog < 0.5f && fogK >= 0.5f) { g.toast("Густой туман… Пираты вас почти не видят", 2f); quip(2, 0.6f) }
        // штиль
        calmT -= dt
        if (calmT <= 0) {
            if (calmOn) { calmOn = false; calmT = rand(300f, 520f); g.toast("Подул ветер!", 1.5f) }
            else if (g.stormLevel < 0.1f) { calmOn = true; calmT = rand(40f, 65f); g.toast("Штиль… Паруса обвисли", 2f); quip(2, 0.8f) }
            else calmT = 60f
        }
        calmK += ((if (calmOn) 1f else 0f) - calmK) * min(1f, dt * 0.5f)
        // водовороты
        whirlWarn -= dt
        for (w in whirls) for (s in g.ships) {
            val dx = w.x - s.x; val dz = w.z - s.z; val d = hypot(dx, dz)
            if (d > w.r || d < 0.1f) continue
            val f = 1f - d / w.r
            val pull = 5f + 9f * f
            val tang = 12f * f
            s.vx += (dx / d * pull + dz / d * tang) * dt
            s.vz += (dz / d * pull - dx / d * tang) * dt
            s.angVel += 0.6f * f * dt
            if (d < 12 && !s.sinking) g.damage(s, 8f * dt, null, false, silent = true)
            if (s === p && whirlWarn <= 0) { whirlWarn = 8f; g.toast("Водоворот! Уходите полным ходом!", 2f) }
        }
        // айсберги дрейфуют и бьют корабли
        for (b in bergs) {
            b.x += b.vx * dt; b.z += b.vz * dt
            if (!g.isWaterPublic(b.x + b.vx * 6, b.z + b.vz * 6, b.r + 4)) { b.vx = -b.vx; b.vz = -b.vz }
            b.hitCd -= dt
            for (s in g.ships) {
                val dx = s.x - b.x; val dz = s.z - b.z; val d = hypot(dx, dz)
                val minD = b.r + s.width * 0.6f
                if (d >= minD || d < 0.1f) continue
                val nx = dx / d; val nz = dz / d
                s.x += nx * (minD - d); s.z += nz * (minD - d)
                val vn = s.vx * nx + s.vz * nz
                if (vn < 0) {
                    s.vx -= 1.6f * vn * nx; s.vz -= 1.6f * vn * nz
                    if (-vn > 3 && b.hitCd <= 0) {
                        b.hitCd = 1f
                        g.damage(s, -vn * 3f, null, false)
                        g.fx.splash(s.x + nx * -s.width, s.z + nz * -s.width, 1.2f)
                        g.sfx.play("hit", g.volumeAt(s.x, s.z))
                        if (s === p) g.toast("Айсберг!", 1.2f)
                    }
                }
            }
        }
    }

    // ===================================================================
    // Призраки
    private fun updateGhosts(dt: Float, p: Ship) {
        val night = g.sky.day < 0.25f
        if (!night) {
            for (s in ArrayList(g.ships)) if (s.ghost && !s.sinking) {
                repeat(10) { g.fx.spawn(ghostCol, s.x + rand(-6f, 6f), rand(2f, 12f), s.z + rand(-6f, 6f), 0f, rand(1f, 4f), 0f, rand(1f, 2f), 1.5f, Fx.Mode.PUFF, 0f) }
                g.ships.remove(s)
            }
            return
        }
        ghostT -= dt
        if (ghostT <= 0) {
            ghostT = rand(120f, 220f)
            if (g.ships.none { it.ghost } && !p.safe) g.findSpawnPoint(260f, 360f)?.let { pt ->
                g.spawnEnemy("ghost", pt.first, pt.second).also { it.ghost = true }
                g.toast("Из ночного тумана выходит корабль-призрак…", 2.8f)
                g.sfx.play("thunder_far", 0.5f)
                quip(0, 1f)
            }
        }
    }

    // ===================================================================
    // Высадки
    private fun nearLanding(p: Ship): Int {
        if (abs(p.speed) > 5) return -1
        for ((i, isl) in g.islands.withIndex()) {
            if (isl.port != -1) continue
            val d = hypot(isl.x - p.x, isl.z - p.z)
            if (d > isl.r + 24) continue
            if (g.systems.fortresses.any { hypot(it.x - isl.x, it.z - isl.z) < 80 }) continue
            if (i in visited && i != mapTarget) continue
            return i
        }
        return -1
    }

    private fun nearRuin(p: Ship): Ruin? {
        if (abs(p.speed) > 5) return null
        return ruins.firstOrNull { it.idx !in dived && hypot(it.x - p.x, it.z - p.z) < 26 }
    }

    private fun updateAction(p: Ship) {
        actionText = null; actionSub = null; actionFn = null
        if (p.sinking || g.systems.boardTarget != null || g.systems.grappleTarget != null) return
        val li = nearLanding(p)
        if (li >= 0) {
            if (li == mapTarget) { actionText = "Копать клад!"; actionSub = "крест на карте здесь" }
            else { actionText = "Высадиться"; actionSub = "исследовать остров" }
            actionFn = { landOn(li) }
            return
        }
        val r = nearRuin(p)
        if (r != null) { actionText = "Нырнуть"; actionSub = "затонувший храм"; actionFn = { g.joyActive = false; g.menus.showEpisode(DIVE) { act -> resolve(act, r.idx) } } }
    }

    private fun landOn(i: Int) {
        g.joyActive = false
        val p = g.player ?: return
        p.vx = 0f; p.vz = 0f
        if (i == mapTarget) { g.menus.showEpisode(TREASURE_EP) { act -> resolve(act, i) }; return }
        val ep = LANDINGS[(i * 7 + visited.size) % LANDINGS.size]
        g.menus.showEpisode(ep) { act -> resolve(act, i) }
    }

    private fun mapPiece(): String {
        mapPieces++
        if (mapPieces >= 3) {
            mapPieces = 0
            val p = g.player
            val cands = g.islands.withIndex().filter { (i, isl) -> isl.port == -1 && i !in visited && (p == null || hypot(isl.x - p.x, isl.z - p.z) > 400) &&
                g.systems.fortresses.none { hypot(it.x - isl.x, it.z - isl.z) < 80 } }
            val t = cands.randomOrNull()
            if (t != null) {
                mapTarget = t.index
                g.systems.waypoint = floatArrayOf(t.value.x, t.value.z + t.value.r + 15f)
                return "\n\nТретий кусок карты! Карта сокровищ собрана — крест отмечен на карте мира."
            }
        }
        return "\n\nНайден кусок карты сокровищ ($mapPieces из 3)."
    }

    /** Исход выбора в эпизоде. Возвращает текст результата. */
    fun resolve(act: String, idx: Int): String {
        val p = g.player
        val sy = g.systems
        fun coins(a: Int, b: Int): Int { val n = (Random.nextInt(a, b + 1) * lootMul()).roundToInt(); g.coins += n; return n }
        fun xp(n: Int) { if (p != null) sy.gainXp(n, p.x, p.z) }
        if (act.startsWith("dive")) {
            dived += idx
        } else if (act != "x_dig") {
            visited += idx
            sy.addStat("landings")
        }
        val text = when (act) {
            "camp_search" -> { val n = coins(100, 250); "Под парусиной нашёлся кошель: +$n монет." + (if (Random.nextFloat() < 0.4f) mapPiece() else "") }
            "camp_track" -> if (Random.nextFloat() < 0.5f) { val n = coins(120, 200); "Следы привели к пещере с тайником: +$n монет." + mapPiece() }
                else { p?.let { it.hp = max(1f, it.hp - it.maxHp * 0.25f) }; xp(60); "Засада! Дикари обстреляли команду из луков. Еле ушли к шлюпке.\nКорабль повреждён, но команда набралась опыта (+60)." }
            "parrot_dig" -> if (Random.nextFloat() < 0.6f) { val n = coins(300, 500); g.gems += 1; "Лопата стукнула о дерево — сундук! +$n монет и 1 кристалл." }
                else { xp(30); "Копали до заката — только крабы и песок. Попугай смеялся. (+30 опыта)" }
            "parrot_take" -> { xp(30); addRep(2, 5); "Попугай уселся на плечо капитана. Пираты в тавернах оценят такого спутника!" }
            "hermit_take" -> { addRep(0, 5); "Старик плачет от счастья и отдаёт вам свёрток из промасленной кожи." + mapPiece() }
            "hermit_food" -> { if (g.coins >= 50) { g.coins -= 50; addRep(0, 10); xp(40); "Отшельник благословляет вас. Торговцы узнают о вашей щедрости. (+40 опыта)" } else "У вас нет даже 50 монет…" }
            "cave_take" -> { val a = sy.addCargo(1, 6); val b = sy.addCargo(5, 4); addRep(2, -8); "Команда перетащила в трюм ром ($a) и порох ($b). Пираты этого не забудут…" }
            "cave_respect" -> { addRep(2, 8); addRep(1, -3); "Вы оставили на ящиках знак Семи Ветров. Пираты оценят." }
            "cave_report" -> { val n = coins(180, 240); addRep(1, 10); addRep(2, -10); "Флот щедро платит за наводку: +$n монет." }
            "idol_take" -> { g.gems += 3; if (Random.nextFloat() < 0.5f) { g.stormTarget = 1f; g.stormTimer = 70f; "+3 кристалла! Но небо мгновенно темнеет… Идол гневается — надвигается шторм!" } else "+3 кристалла! Матросы крестятся, но ничего не случилось." }
            "idol_gift" -> { if (g.coins >= 100) { g.coins -= 100; g.gems += 1; xp(80); "Глаза идола вспыхнули. В чаше для подношений — кристалл! (+80 опыта)" } else "Монет на подношение не хватило." }
            "spring_repair" -> { p?.let { it.hp = it.maxHp; it.holes.clear() }; xp(30); "Бочки полны, пробоины заделаны. Корабль как новенький!" }
            "spring_rest" -> { xp(80); g.dayTime = (g.dayTime + 0.15f) % 1f; "Команда отдохнула и травит байки до вечера. (+80 опыта)" }
            "boat_open" -> { val n = coins(40, 80); "В сундучке $n монет и обрывок карты." + mapPiece() }
            "x_dig" -> { val n = coins(1500, 2500); val gm = Random.nextInt(6, 11); g.gems += gm; visited += idx; mapTarget = -1; g.systems.waypoint = null
                sy.addStat("treasures"); g.sfx.play("treasure"); "Сундук, полный дублонов! +$n монет и +$gm кристаллов!" }
            "dive_swim" -> if (Random.nextFloat() < 0.7f) { val n = coins(300, 450); g.gems += 4; "Ныряльщики подняли золотую чашу с алтаря: +$n монет и 4 кристалла!" }
                else { p?.let { it.hp = max(1f, it.hp - it.maxHp * 0.2f) }; g.gems += 1; "Акулы! Матросы еле выбрались, прихватив один кристалл." }
            "dive_bell" -> { if (g.coins >= 200) { g.coins -= 200; val n = coins(550, 700); g.gems += 3; "Колокол спокойно опустился к алтарю: +$n монет и 3 кристалла." } else { dived -= idx; "Колокол стоит 200 монет — не хватает." } }
            "dive_leave" -> { xp(50); "Вы оставили храм в покое. Матросы долго смотрят в воду… (+50 опыта)" }
            else -> { "Вы вернулись на корабль." }
        }
        g.save()
        return text
    }

    // ===================================================================
    fun update(dt: Float) {
        val p = g.player ?: return
        if (sayT > 0) sayT -= dt
        if (talkCd > 0) talkCd -= dt
        val regen = off { it.regen }
        if (regen > 0 && g.combatT <= 0 && !p.sinking && p.hp < p.maxHp) p.hp = min(p.maxHp, p.hp + regen * dt)
        if (p.hp < p.maxHp * 0.3f) quip(0, 0.02f)
        updateHunters(dt)
        updateBase(dt, p)
        updateWeather(dt, p)
        updateGhosts(dt, p)
        actionCd -= dt
        if (actionCd <= 0f) { actionCd = 0.2f; updateAction(p) }
    }

    // ===================================================================
    // Отрисовка
    fun render(batch: ModelBatch, env: Environment, lookX: Float, lookZ: Float) {
        if (baseBuilt && hypot(baseX - lookX, baseZ - lookZ) < 600) {
            for (b in buildingInst) batch.render(b, env)
            ringInst?.let { r ->
                val s = 1 + sin(g.now * 3) * 0.05f
                r.transform.setToTranslation(ringX, waveHeight(ringX, ringZ, g.now) + 0.7f, ringZ).scale(s, 1f, s)
                (r.materials[0].get(BlendingAttribute.Type) as BlendingAttribute).opacity = 0.55f + sin(g.now * 3) * 0.2f
                batch.render(r, env)
            }
        }
        for (b in bergs) if (hypot(b.x - lookX, b.z - lookZ) < 600) {
            val sc = b.r / 8.5f
            b.inst.transform.setToTranslation(b.x, waveHeight(b.x, b.z, g.now) * 0.4f - 0.5f, b.z).rotate(0f, 1f, 0f, (b.spin + g.now * 2f) % 360f).scale(sc, sc, sc)
            batch.render(b.inst, env)
        }
        for (r in ruins) if (hypot(r.x - lookX, r.z - lookZ) < 600) batch.render(r.inst, env)
    }

    fun renderShadows(batch: ModelBatch, lookX: Float, lookZ: Float) {
        if (baseBuilt && hypot(baseX - lookX, baseZ - lookZ) < 260) for (b in buildingInst) batch.render(b)
        for (b in bergs) if (hypot(b.x - lookX, b.z - lookZ) < 260) batch.render(b.inst)
    }

    private val fogCol = Color()
    private val whirlCol = Color()
    private val ghostCol = Color(0.45f, 1f, 0.75f, 0.7f)
    private val tmpCol = Color()

    fun renderDecals(lookX: Float, lookZ: Float) {
        val fx = g.fx
        val day = g.sky.day
        // туманные банки — низкие клубы облаков
        fogCol.set(0.82f + 0.1f * day, 0.85f + 0.1f * day, 0.9f + 0.08f * day, 0.3f * (0.4f + 0.6f * day))
        for ((bi, f) in fogBanks.withIndex()) {
            if (hypot(f[0] - lookX, f[1] - lookZ) > 420 + f[2]) continue
            val n = 14
            for (k in 0 until n) {
                val a = k * 6.283f / n + bi
                val rr = f[2] * (0.35f + 0.6f * ((k * 37 + bi * 11) % 10) / 10f)
                val x = f[0] + cos(a) * rr; val z = f[1] + sin(a) * rr
                val dl = hypot(x - lookX, z - lookZ)
                if (dl > 420) continue
                val near = clamp((dl - 70f) / 140f, 0f, 1f)
                if (near <= 0f) continue
                tmpCol.set(fogCol.r, fogCol.g, fogCol.b, fogCol.a * near)
                fx.frame(if (k % 2 == 0) g.ui.cloud else g.ui.cloud2, x, 8f + (k % 3) * 3f, z, 120f, 60f, tmpCol)
            }
        }
        // водовороты
        for (w in whirls) {
            if (hypot(w.x - lookX, w.z - lookZ) > 500) continue
            val y = waveHeight(w.x, w.z, g.now) * 0.3f + 0.6f
            fx.frame(g.ui.softDisc, w.x, y - 0.1f, w.z, w.r * 0.7f, w.r * 0.7f, whirlCol.set(0.02f, 0.08f, 0.14f, 0.75f), true, 0f)
            for (k in 0 until 5) {
                val rr = w.r * (0.25f + k * 0.19f)
                fx.frame(g.ui.ring, w.x, y + k * 0.02f, w.z, rr * 2, rr * 2, whirlCol.set(1f, 1f, 1f, 0.5f - k * 0.07f), true, (g.now * (140f - k * 22f)) % 360f)
            }
            for (k in 0 until 18) {
                val t = (g.now * 0.35f + k / 18f) % 1f
                val a = t * 9f + k
                val rr = w.r * (1f - t)
                fx.frame(g.ui.softDisc, w.x + cos(a) * rr, y + 0.1f, w.z + sin(a) * rr, 3f, 3f, whirlCol.set(1f, 1f, 1f, 0.55f * t), true, 0f)
            }
        }
        // руины: блики над водой
        for (r in ruins) {
            if (hypot(r.x - lookX, r.z - lookZ) > 400) continue
            val pulse = 0.5f + 0.5f * sin(g.now * 2.5f + r.idx)
            if (r.idx !in dived) fx.glow(r.x, 1.2f, r.z, 7f + pulse * 3f, tmpCol.set(1f, 0.85f, 0.35f, 0.25f + 0.3f * pulse))
        }
        // свечение призраков
        for (s in g.ships) {
            if (!s.ghost || s.sinking || hypot(s.x - lookX, s.z - lookZ) > 400) continue
            val fl = 0.6f + 0.4f * sin(g.now * 5f + s.id)
            for (k in -1..1) {
                val ox = sin(s.h) * k * s.len * 0.3f; val oz = -cos(s.h) * k * s.len * 0.3f
                fx.glow(s.x + ox, s.y + s.deckTop + 4f + abs(k) * 2f, s.z + oz, 9f, tmpCol.set(0.35f, 1f, 0.7f, 0.35f * fl))
            }
            if (Random.nextFloat() < 0.3f) fx.spawn(ghostCol, s.x + rand(-4f, 4f), s.y + rand(2f, 10f), s.z + rand(-4f, 4f), 0f, rand(0.5f, 2f), 0f, rand(0.6f, 1.2f), 1.4f, Fx.Mode.PUFF, 0f)
        }
        // свет маяка базы ночью
        if (baseFounded && blev[4] > 0 && day < 0.6f) {
            val a = 330f * PI.toFloat() / 180f
            val lx = baseX + cos(a) * baseR * 0.62f; val lz = baseZ - sin(a) * baseR * 0.62f
            val h = 3.7f + 10f + blev[4] * 4f + 2.5f
            fx.glow(lx, h, lz, 14f + sin(g.now * 2) * 2f, tmpCol.set(1f, 0.9f, 0.5f, (1 - day) * 0.9f))
        }
    }

    // ===================================================================
    // HUD
    fun fleetVisible() = g.ships.any { it.ally && !it.sinking }
    fun fleetPos(k: Int): Pair<Float, Float> = 28f to (g.hDp * 0.52f - k * 46f)
    fun actionPos(): Pair<Float, Float> = (g.wDp / 2) to (g.safeBottom + 175f)

    fun touch(x: Float, y: Float): Boolean {
        if (fleetVisible()) for (k in 0..2) {
            val (fx, fy) = fleetPos(k)
            if (hypot(x - fx, y - fy) < 21) {
                order = k
                g.sfx.play("click")
                g.toast("Флот: ${orderNames[k].lowercase()}!", 1.2f)
                return true
            }
        }
        val fn = actionFn
        if (fn != null) {
            val (ax, ay) = actionPos()
            if (abs(x - ax) < 95 && abs(y - ay) < 30) { g.sfx.play("click"); fn(); return true }
        }
        return false
    }

    fun drawHud(b: Batch) {
        val ui = g.ui
        if (fleetVisible()) {
            val icons = listOf("⚓", "⚔", "↺")
            for (k in 0..2) {
                val (fx, fy) = fleetPos(k)
                val on = order == k
                ui.icon(b, ui.disc, fx, fy, 38f, 38f, 0f, rgb(if (on) 0x2f8ff0 else 0x0a1e32, if (on) 0.9f else 0.5f))
                ui.icon(b, ui.ring, fx, fy, 41f, 41f, 0f, rgb(if (on) 0xffc93c else 0xffffff, 0.6f))
                ui.text(b, ui.small, icons[k], fx - 19, fy + 8, Align.center, 38f)
                if (on) ui.text(b, ui.small, orderNames[k], fx + 23, fy + 7, Align.left, 100f, c = rgb(0xffe082))
            }
            ui.text(b, ui.small, "Флот", 10f, fleetPos(0).second + 36f, Align.left, 60f, c = rgb(0xb3e5fc))
        }
        actionText?.let { t ->
            val (px, py) = actionPos()
            val pulse = 1f + sin(g.now * 5) * 0.04f
            ui.round(b, px - 95 * pulse, py - 28 * pulse, 190 * pulse, 56 * pulse, rgb(0x0d3b2e))
            ui.round(b, px - 93 * pulse, py - 25 * pulse, 186 * pulse, 50 * pulse, rgb(0x2e9d6a))
            ui.text(b, ui.big, t, px - 90, py + 12, Align.center, 180f)
            actionSub?.let { ui.text(b, ui.small, it, px - 120, py - 34, Align.center, 240f, c = rgb(0xc8e6c9)) }
        }
        if (sayT > 0 && sayOfficer >= 0) {
            val o = OFFICERS[sayOfficer]
            val a = min(1f, sayT * 2f)
            val w = min(g.wDp - 24f, 360f)
            val x = (g.wDp - w) / 2; val y = g.hDp * 0.3f
            ui.round(b, x, y, w, 64f, rgb(0x2b1d12, 0.85f * a))
            Portraits.draw(b, ui, o, x + 6, y + 6, 52f, a)
            ui.text(b, ui.small, o.name + " · " + ROLES[o.role].lowercase(), x + 66, y + 56, Align.left, w - 74, c = rgb(0xffc93c, a))
            ui.text(b, ui.mid, "«$sayText»", x + 66, y + 34, Align.left, w - 74, wrap = true, c = rgb(0xffffff, a))
        }
        if (fogK > 0.5f) ui.text(b, ui.small, "≈ туман: враги вас плохо видят", 0f, g.hDp * 0.3f - 12f, Align.center, g.wDp, c = rgb(0xcfd8dc, fogK))
        else if (calmK > 0.5f) ui.text(b, ui.small, "≈ штиль: ветра нет", 0f, g.hDp * 0.3f - 12f, Align.center, g.wDp, c = rgb(0xcfd8dc, calmK))
        b.color = Color.WHITE
    }

    // ===================================================================
    // Сохранение
    fun load(p: Preferences) {
        val r = p.getString("rep", "").split(",").mapNotNull { it.toIntOrNull() }
        for (i in rep.indices) rep[i] = r.getOrElse(i) { 0 }.coerceIn(-100, 100)
        hired.clear(); hired += p.getString("hired", "").split(",").mapNotNull { it.toIntOrNull() }.filter { it in OFFICERS.indices }
        val c = p.getString("crew", "").split(",").mapNotNull { it.toIntOrNull() }
        for (i in crew.indices) crew[i] = c.getOrElse(i) { -1 }.takeIf { it in hired && OFFICERS[it].role == i } ?: -1
        defeated.clear(); defeated += p.getString("defeated", "").split(",").mapNotNull { it.toIntOrNull() }
        activeBounty = p.getInteger("activeBounty", -1).takeIf { it in CAPTAINS.indices && it !in defeated } ?: -1
        hunter = null
        baseFounded = p.getBoolean("baseFounded", false)
        val bl = p.getString("blev", "").split(",").mapNotNull { it.toIntOrNull() }
        for (i in blev.indices) blev[i] = bl.getOrElse(i) { 0 }.coerceIn(0, 3)
        bank = p.getFloat("bank", 0f)
        lastSeen = p.getLong("lastSeen", 0L)
        if (baseFounded && lastSeen > 0) {
            val mins = ((System.currentTimeMillis() - lastSeen) / 60000f).coerceIn(0f, 8 * 60f)
            bank = min(bankCap().toFloat(), bank + incomePerMin() * mins * 0.5f)
        }
        order = p.getInteger("fleetOrder", 0).coerceIn(0, 2)
        visited.clear(); visited += p.getString("visited", "").split(",").mapNotNull { it.toIntOrNull() }
        dived.clear(); dived += p.getString("dived", "").split(",").mapNotNull { it.toIntOrNull() }
        mapPieces = p.getInteger("mapPieces", 0)
        mapTarget = p.getInteger("mapTarget", -1)
        refreshBuildings()
    }

    fun save(p: Preferences) {
        p.putString("rep", rep.joinToString(","))
        p.putString("hired", hired.joinToString(","))
        p.putString("crew", crew.joinToString(","))
        p.putString("defeated", defeated.joinToString(","))
        p.putInteger("activeBounty", activeBounty)
        p.putBoolean("baseFounded", baseFounded)
        p.putString("blev", blev.joinToString(","))
        p.putFloat("bank", bank)
        lastSeen = System.currentTimeMillis()
        p.putLong("lastSeen", lastSeen)
        p.putInteger("fleetOrder", order)
        p.putString("visited", visited.joinToString(","))
        p.putString("dived", dived.joinToString(","))
        p.putInteger("mapPieces", mapPieces)
        p.putInteger("mapTarget", mapTarget)
    }

    companion object {
        const val BASE_ISLAND = -2
        const val BASE_PORT = -1
    }
}

/** Рисованные портреты офицеров. */
object Portraits {
    private val c = Color()
    private fun col(hex: Int, a: Float) = c.set(rgb(hex, a))

    fun draw(b: Batch, ui: Ui, o: Officer, x: Float, y: Float, s: Float, a: Float = 1f) {
        val L = o.look
        val skin = L[0]; val hair = L[1]; val hat = L[2]; val hatC = L[3]; val beard = L[4]; val patch = L[5]; val coat = L[6]; val ear = L[7]
        val cx = x + s / 2
        ui.round(b, x, y, s, s, col(0x4e3420, a))
        ui.round(b, x + s * 0.04f, y + s * 0.04f, s * 0.92f, s * 0.92f, col(0x7a5a3a, a))
        // плечи и камзол
        ui.icon(b, ui.disc, cx, y + s * 0.1f, s * 0.84f, s * 0.42f, 0f, col(coat, a))
        ui.icon(b, ui.disc, cx, y + s * 0.12f, s * 0.2f, s * 0.3f, 0f, col(0xf5f0dc, a))
        // волосы сзади
        if (hat != 2) ui.icon(b, ui.disc, cx, y + s * 0.55f, s * 0.56f, s * 0.56f, 0f, col(hair, a))
        // лицо
        ui.icon(b, ui.disc, cx, y + s * 0.5f, s * 0.44f, s * 0.52f, 0f, col(skin, a))
        // борода
        if (beard == 1) ui.icon(b, ui.disc, cx, y + s * 0.34f, s * 0.36f, s * 0.22f, 0f, col(hair, a))
        if (beard == 2) ui.icon(b, ui.disc, cx, y + s * 0.32f, s * 0.46f, s * 0.34f, 0f, col(hair, a))
        // глаза
        val ey = y + s * 0.54f
        ui.icon(b, ui.disc, cx - s * 0.09f, ey, s * 0.07f, s * 0.07f, 0f, col(0x1b1b1b, a))
        if (patch == 1) {
            ui.rect(b, x + s * 0.2f, ey + s * 0.02f, s * 0.6f, s * 0.025f, col(0x111111, a))
            ui.icon(b, ui.disc, cx + s * 0.09f, ey, s * 0.13f, s * 0.11f, 0f, col(0x111111, a))
        } else ui.icon(b, ui.disc, cx + s * 0.09f, ey, s * 0.07f, s * 0.07f, 0f, col(0x1b1b1b, a))
        // рот
        ui.rect(b, cx - s * 0.06f, y + s * (if (beard > 0) 0.4f else 0.38f), s * 0.12f, s * 0.025f, col(0x6d2a1a, a))
        if (ear == 1) ui.icon(b, ui.disc, cx + s * 0.22f, y + s * 0.45f, s * 0.06f, s * 0.06f, 0f, col(0xffc93c, a))
        // головной убор
        when (hat) {
            1 -> {
                ui.icon(b, ui.disc, cx, y + s * 0.75f, s * 0.78f, s * 0.2f, 0f, col(hatC, a))
                ui.icon(b, ui.disc, cx, y + s * 0.82f, s * 0.46f, s * 0.24f, 0f, col(hatC, a))
                ui.rect(b, cx - s * 0.3f, y + s * 0.72f, s * 0.6f, s * 0.025f, col(0xd4a017, a))
            }
            2 -> {
                ui.icon(b, ui.disc, cx, y + s * 0.72f, s * 0.5f, s * 0.26f, 0f, col(hatC, a))
                ui.icon(b, ui.disc, cx + s * 0.27f, y + s * 0.66f, s * 0.12f, s * 0.12f, 0f, col(hatC, a))
                ui.rect(b, cx + s * 0.27f, y + s * 0.5f, s * 0.04f, s * 0.14f, col(hatC, a))
                ui.icon(b, ui.disc, cx - s * 0.1f, y + s * 0.74f, s * 0.06f, s * 0.06f, 0f, col(0xffffff, a * 0.8f))
            }
            3 -> {
                ui.icon(b, ui.disc, cx, y + s * 0.74f, s * 0.48f, s * 0.24f, 0f, col(hatC, a))
                ui.rect(b, cx - s * 0.28f, y + s * 0.66f, s * 0.36f, s * 0.05f, col(0x212121, a))
            }
            else -> {}
        }
        b.color = Color.WHITE
    }
}
