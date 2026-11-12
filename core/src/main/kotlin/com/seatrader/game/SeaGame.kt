package com.seatrader.game

import com.badlogic.gdx.Application
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ScreenViewport
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private fun rand(a: Float, b: Float) = a + Random.nextFloat() * (b - a)
private fun clamp(v: Float, a: Float, b: Float) = max(a, min(b, v))
private fun wrapAngle(x: Float): Float {
    var a = x
    while (a > PI) a -= (2 * PI).toFloat()
    while (a < -PI) a += (2 * PI).toFloat()
    return a
}

enum class Faction { PLAYER, PIRATE }
enum class AiState { WANDER, ATTACK }

class Ship(
    val id: Int, val name: String, var stats: Stats, val faction: Faction, val look: Look,
    var x: Float, var z: Float, var h: Float, val inst: ModelInstance, val boss: Boolean,
) {
    var type = ""
    var coinsMin = 0; var coinsMax = 0; var gems = 0f
    var desired = h
    var speed = 0f
    var throttle = 0f
    var hp = stats.hp.toFloat()
    var maxHp = stats.hp.toFloat()
    val len = look.len
    val width = look.width
    val radius = look.len * 0.36f
    val reload = floatArrayOf(rand(0.3f, 1.2f), rand(0.3f, 1.2f)) // [левый, правый]
    var sinkT = 0f
    var boostT = 0f
    var boostCd = 0f
    var lastHit = -99f
    var wakeT = 0f
    var smokeT = 0f
    val ramT = HashMap<Int, Float>()
    var ai = AiState.WANDER
    var wx = x; var wz = z; var aiT = 0f
    var side = if (Random.nextBoolean()) -1 else 1
    var safe = false
    var inPort = false
    val sinking get() = sinkT > 0
}

class Island(val x: Float, val z: Float, val r: Float, val port: Int, val inst: ModelInstance, val foam: ModelInstance)
class Port(val def: PortDef, val i: Int, val rx: Float, val rz: Float, val ring: ModelInstance)
class Projectile(
    val owner: Ship, val faction: Faction, val sx: Float, val sz: Float, val tx: Float, val tz: Float,
    val dx: Float, val dz: Float, var t: Float, val tt: Float, val arc: Float, val dmg: Float,
) { var fired = false }
class Loot(val gem: Boolean, val value: Int, var x: Float, var z: Float, val inst: ModelInstance, var t: Float) { var age = 0f }
class FloatText(val text: String, val x: Float, var y: Float, val color: Color, val font: BitmapFont) { var age = 0f }

class SeaGame : ApplicationAdapter() {
    enum class State { PLAY, PORT, PAUSE, DEAD }

    // ---------- Графика ----------
    lateinit var ui: Ui
    private lateinit var sfx: Sfx
    private lateinit var water: Water
    private lateinit var fx: Fx
    lateinit var cam: PerspectiveCamera
    private lateinit var modelBatch: ModelBatch
    private lateinit var batch: SpriteBatch
    private val hudCam = OrthographicCamera()
    lateinit var stage: Stage
    private val env = Environment()
    private val fogColor = rgb(0x8fc5e8)
    private val models = ArrayList<Model>()
    private val shipModels = HashMap<Look, Model>()
    private lateinit var chestModel: Model
    private lateinit var gemModel: Model
    var scale = 1f
    var wDp = 400f
    var hDp = 800f
    private var safeTop = 0f
    private var safeBottom = 0f

    /** Для автоматических проверок на компьютере. */
    var afterRender: (() -> Unit)? = null

    // ---------- Сохранение ----------
    private lateinit var prefs: Preferences
    var coins = 150
    var gems = 0
    val owned = ArrayList<String>()
    var shipId = "sloop"
    val up = HashMap<String, IntArray>()
    var questIdx = 0
    var qprog = 0
    var homePort = 0
    var kills = 0

    // ---------- Мир ----------
    var state = State.PLAY
    val islands = ArrayList<Island>()
    val ports = ArrayList<Port>()
    val ships = ArrayList<Ship>()
    var player: Ship? = null
    private val projectiles = ArrayList<Projectile>()
    val loot = ArrayList<Loot>()
    private val floats = ArrayList<FloatText>()
    private var nextId = 1
    var now = 0f
    private var shake = 0f
    private var flash = 0f
    private var toastText = ""
    private var toastT = 0f
    private var questPop = 0f
    private var spawnTimer = 0f
    private var saveTimer = 0f
    private var camSnap = true
    private val camPos = Vector3()
    private val camOff = Vector3(0f, 118f, 78f)

    // ---------- Ввод ----------
    private var joyActive = false
    private var joyPointer = -1
    private var joyOx = 0f
    private var joyOy = 0f
    private var joyJx = 0f
    private var joyJy = 0f
    private var joyMag = 0f
    private val joyR = 85f
    private val keys = HashSet<Int>()

    // =====================================================================
    override fun create() {
        val density = Gdx.graphics.density
        scale = System.getProperty("sea.scale")?.toFloatOrNull()
            ?: if (Gdx.app.type == Application.ApplicationType.Desktop) 1f else density
        scale = min(scale, Gdx.graphics.width / 360f).coerceAtLeast(0.75f)

        ui = Ui(scale)
        sfx = Sfx()
        water = Water()
        cam = PerspectiveCamera(52f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        cam.near = 1f
        cam.far = 700f
        modelBatch = ModelBatch()
        batch = SpriteBatch()
        fx = Fx(cam, ui.softDisc, ui.white)
        stage = Stage(ScreenViewport().also { it.unitsPerPixel = 1f / scale })

        env.set(ColorAttribute(ColorAttribute.AmbientLight, 0.58f, 0.63f, 0.7f, 1f))
        env.add(DirectionalLight().set(0.62f, 0.58f, 0.5f, -0.45f, -1f, -0.35f))
        env.set(ColorAttribute(ColorAttribute.Fog, fogColor.r, fogColor.g, fogColor.b, 1f))

        chestModel = Models.chest().also { models += it }
        gemModel = Models.gem().also { models += it }
        buildWorld()

        prefs = Gdx.app.getPreferences("seawolves")
        load()
        sfx.enabled = prefs.getBoolean("sound", true)

        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        Gdx.input.inputProcessor = InputMultiplexer(stage, input)

        spawnPlayer()
        repeat(4) { i ->
            findSpawnPoint(230f, 380f)?.let { spawnEnemy(if (i < 3) "sloop" else "brig", it.first, it.second) }
        }
    }

    private fun buildWorld() {
        val portModel = Models.portRing().also { models += it }
        PORTS.forEachIndexed { i, p ->
            val m = Models.island(p.r, 100 + i, true, i == 0).also { models += it }
            val f = Models.foam(p.r).also { models += it }
            islands += Island(p.x, p.z, p.r * 1.06f, i, ModelInstance(m).also { it.transform.setToTranslation(p.x, 0f, p.z) },
                ModelInstance(f).also { it.transform.setToTranslation(p.x, 0.6f, p.z) })
            val rx = p.x
            val rz = p.z + p.r * 1.17f + 14f
            ports += Port(p, i, rx, rz, ModelInstance(portModel))
        }
        val wr = Random(4242)
        var tries = 0
        while (islands.size < PORTS.size + 18 && tries < 800) {
            tries++
            val x = (wr.nextFloat() * 2 - 1) * (WORLD - 90)
            val z = (wr.nextFloat() * 2 - 1) * (WORLD - 90)
            val r = 16 + wr.nextFloat() * 40
            if (islands.any { hypot(it.x - x, it.z - z) < it.r + r + 80 }) continue
            val m = Models.island(r, 1000 + tries, false, false).also { models += it }
            val f = Models.foam(r).also { models += it }
            islands += Island(x, z, r * 1.06f, -1, ModelInstance(m).also { it.transform.setToTranslation(x, 0f, z) },
                ModelInstance(f).also { it.transform.setToTranslation(x, 0.6f, z) })
        }
    }

    override fun resize(width: Int, height: Int) {
        cam.viewportWidth = width.toFloat()
        cam.viewportHeight = height.toFloat()
        cam.fieldOfView = if (width.toFloat() / height < 0.75f) 54f else 44f
        cam.update()
        wDp = width / scale
        hDp = height / scale
        hudCam.setToOrtho(false, wDp, hDp)
        stage.viewport.update(width, height, true)
        safeTop = Gdx.graphics.safeInsetTop / scale
        safeBottom = Gdx.graphics.safeInsetBottom / scale
        when (state) {
            State.PORT -> showPort()
            State.PAUSE -> showPause()
            State.DEAD -> showDeath()
            else -> {}
        }
    }

    override fun pause() {
        save()
        if (state == State.PLAY) openPause()
    }

    // ================= Сохранение =================
    private fun ensureUp(id: String) = up.getOrPut(id) { IntArray(4) }

    private fun load() {
        coins = prefs.getInteger("coins", 150)
        gems = prefs.getInteger("gems", 0)
        owned.clear()
        owned += prefs.getString("owned", "sloop").split(",").filter { s -> PLAYER_SHIPS.any { it.id == s } }
        if (owned.isEmpty()) owned += "sloop"
        shipId = prefs.getString("ship", "sloop").takeIf { it in owned } ?: owned[0]
        up.clear()
        for (part in prefs.getString("up", "").split(";")) {
            val kv = part.split(":")
            if (kv.size == 2) up[kv[0]] = kv[1].split(".").map { it.toIntOrNull() ?: 0 }.let { l -> IntArray(4) { i -> l.getOrElse(i) { 0 }.coerceIn(0, MAX_LEVEL) } }
        }
        ensureUp(shipId)
        questIdx = prefs.getInteger("quest", 0)
        qprog = prefs.getInteger("qprog", 0)
        homePort = prefs.getInteger("port", 0).coerceIn(0, PORTS.size - 1)
        kills = prefs.getInteger("kills", 0)
    }

    fun save() {
        prefs.putInteger("coins", coins)
        prefs.putInteger("gems", gems)
        prefs.putString("owned", owned.joinToString(","))
        prefs.putString("ship", shipId)
        prefs.putString("up", up.entries.joinToString(";") { (k, v) -> k + ":" + v.joinToString(".") })
        prefs.putInteger("quest", questIdx)
        prefs.putInteger("qprog", qprog)
        prefs.putInteger("port", homePort)
        prefs.putInteger("kills", kills)
        prefs.putBoolean("sound", sfx.enabled)
        prefs.flush()
    }

    private fun resetProgress() {
        prefs.clear(); prefs.flush()
        load()
        for (s in ArrayList(ships)) removeShip(s)
        loot.clear()
        projectiles.clear()
        player = null
        spawnPlayer()
        closeMenus()
    }

    // ================= Корабли =================
    private fun shipModel(look: Look) = shipModels.getOrPut(look) { Models.ship(look).also { models += it } }

    private fun createShip(name: String, stats: Stats, faction: Faction, look: Look, x: Float, z: Float, h: Float, boss: Boolean): Ship {
        val s = Ship(nextId++, name, stats, faction, look, x, z, h, ModelInstance(shipModel(look)), boss)
        ships += s
        return s
    }

    fun removeShip(s: Ship) { ships.remove(s) }

    private fun playerDef() = PLAYER_SHIPS.firstOrNull { it.id == shipId } ?: PLAYER_SHIPS[0]

    fun spawnPlayer() {
        val p = ports[homePort]
        player?.let { removeShip(it) }
        val def = playerDef()
        player = createShip(def.name, shipStats(def, ensureUp(def.id)), Faction.PLAYER, def.look, p.rx, p.rz + 4f, PI.toFloat(), false)
            .also { it.inPort = true }
        camSnap = true
    }

    private fun rebuildPlayer() {
        val old = player ?: return
        removeShip(old)
        val def = playerDef()
        player = createShip(def.name, shipStats(def, ensureUp(def.id)), Faction.PLAYER, def.look, old.x, old.z, old.h, false)
            .also { it.inPort = true }
    }

    fun spawnEnemy(type: String, x: Float, z: Float): Ship {
        val d = ENEMIES.getValue(type)
        return createShip(d.name, enemyStats(d), Faction.PIRATE, d.look, x, z, rand(-PI.toFloat(), PI.toFloat()), d.boss).also {
            it.type = type; it.coinsMin = d.coinsMin; it.coinsMax = d.coinsMax; it.gems = d.gems
        }
    }

    private fun isWater(x: Float, z: Float, margin: Float): Boolean {
        if (abs(x) > WORLD - 30 || abs(z) > WORLD - 30) return false
        return islands.none { hypot(it.x - x, it.z - z) < it.r + margin }
    }

    private fun findSpawnPoint(minD: Float, maxD: Float): Pair<Float, Float>? {
        val p = player ?: return null
        repeat(40) {
            val a = rand(0f, (2 * PI).toFloat())
            val d = rand(minD, maxD)
            val x = p.x + sin(a) * d
            val z = p.z + cos(a) * d
            if (!isWater(x, z, 25f)) return@repeat
            if (ports.any { hypot(it.def.x - x, it.def.z - z) < it.def.r + 160 }) return@repeat
            return x to z
        }
        return null
    }

    private fun pickEnemyType(x: Float, z: Float): String {
        val zone = hypot(x, z) / WORLD
        val w = linkedMapOf(
            "sloop" to max(0.15f, 1 - zone * 1.3f),
            "brig" to if (zone < 0.2f) 0.15f else 0.7f,
            "frigate" to if (zone > 0.4f) (zone - 0.3f) * 1.6f else 0f,
            "galleon" to if (zone > 0.8f) 0.06f else 0f,
        )
        var r = Random.nextFloat() * w.values.sum()
        for ((k, v) in w) { r -= v; if (r <= 0) return k }
        return "sloop"
    }

    // ================= Управление =================
    val input = object : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            val x = screenX / scale
            val y = hDp - screenY / scale
            if (state != State.PLAY) return false
            if (x < 90 && y > hDp - safeTop - 88) { sfx.play("click"); openPause(); return true }
            val bx = 58f; val by = hDp - safeTop - 140f
            if (hypot(x - bx, y - by) < 48) { tryBoost(); return true }
            if (!joyActive) {
                joyActive = true; joyPointer = pointer
                joyOx = x; joyOy = y; joyMag = 0f
            }
            return true
        }

        override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
            if (!joyActive || pointer != joyPointer) return false
            val dx = screenX / scale - joyOx
            val dy = (hDp - screenY / scale) - joyOy
            val d = hypot(dx, dy)
            joyMag = min(1f, d / joyR)
            if (d > 0) { joyJx = dx / d; joyJy = dy / d }
            return true
        }

        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (pointer == joyPointer) { joyActive = false; joyMag = 0f; joyPointer = -1 }
            return true
        }

        override fun keyDown(keycode: Int): Boolean {
            keys += keycode
            when (keycode) {
                Input.Keys.BACK, Input.Keys.ESCAPE -> onBack()
                Input.Keys.SPACE -> tryBoost()
            }
            return true
        }

        override fun keyUp(keycode: Int): Boolean { keys -= keycode; return true }
    }

    private fun onBack() {
        when (state) {
            State.PORT -> closePort()
            State.PAUSE -> closeMenus()
            State.PLAY -> openPause()
            State.DEAD -> {}
        }
    }

    fun tryBoost() {
        val p = player ?: return
        if (state != State.PLAY || p.sinking || p.boostCd > 0) return
        p.boostT = 2.2f
        p.boostCd = 7f
        sfx.play("boost")
    }

    private fun controlPlayer() {
        val p = player ?: return
        if (p.sinking) return
        var jx = 0f; var jy = 0f; var mag = 0f
        if (joyActive && joyMag > 0.12f) { jx = joyJx; jy = -joyJy; mag = joyMag }
        val kx = (if (Input.Keys.D in keys || Input.Keys.RIGHT in keys) 1 else 0) - (if (Input.Keys.A in keys || Input.Keys.LEFT in keys) 1 else 0)
        val ky = (if (Input.Keys.S in keys || Input.Keys.DOWN in keys) 1 else 0) - (if (Input.Keys.W in keys || Input.Keys.UP in keys) 1 else 0)
        if (kx != 0 || ky != 0) { val d = hypot(kx.toFloat(), ky.toFloat()); jx = kx / d; jy = ky / d; mag = 1f }
        // jx, jy — направление на экране (вниз = +z мира)
        if (mag > 0) { p.desired = atan2(jx, -jy); p.throttle = mag } else p.throttle = 0f
    }

    // ================= Физика =================
    private fun updateShip(s: Ship, dt: Float) {
        val st = s.stats
        if (s.boostT > 0) s.boostT -= dt
        if (s.boostCd > 0) s.boostCd -= dt
        if (s.sinking) {
            s.sinkT += dt
            s.speed *= 1 - dt
        } else {
            val diff = wrapAngle(s.desired - s.h)
            val turnRate = st.turn * (0.45f + 0.55f * min(1f, abs(s.speed) / (st.speed * 0.6f)))
            s.h = wrapAngle(s.h + clamp(diff, -turnRate * dt, turnRate * dt))
            val maxSp = st.speed * (if (s.boostT > 0) 1.7f else 1f)
            val target = maxSp * s.throttle * (if (abs(diff) > 1.8f) 0.55f else 1f)
            val accel = if (target > s.speed) (if (s.boostT > 0) 3f else 0.9f) else 0.7f
            s.speed += (target - s.speed) * min(1f, dt * accel)
        }
        s.x += sin(s.h) * s.speed * dt
        s.z += -cos(s.h) * s.speed * dt

        val lim = WORLD - 20
        if (abs(s.x) > lim) { s.x = sign(s.x) * lim; s.speed *= 0.9f }
        if (abs(s.z) > lim) { s.z = sign(s.z) * lim; s.speed *= 0.9f }

        for (isl in islands) {
            val dx = s.x - isl.x; val dz = s.z - isl.z
            val d = hypot(dx, dz)
            val mn = isl.r + s.radius * 0.55f
            if (d < mn && d > 0.01f) {
                val k = (mn - d) / d
                s.x += dx * k; s.z += dz * k
                if (s === player && s.speed > 9 && !s.sinking) damage(s, s.speed * 0.6f, null, false)
                s.speed *= 0.6f
            }
        }

        if (!s.sinking && now - s.lastHit > 6 && s.hp < s.maxHp) s.hp = min(s.maxHp, s.hp + s.maxHp * 0.02f * dt)

        // Качка и крен
        val bob = waveHeight(s.x, s.z, now) * 0.55f - (if (s.sinking) s.sinkT * s.sinkT * 1.6f else 0f)
        val lean = clamp(wrapAngle(s.desired - s.h), -1f, 1f) * min(1f, s.speed / 10f) * 0.09f
        val roll = sin(now * 1.3f + s.id) * 0.045f - lean + (if (s.sinking) min(0.9f, s.sinkT * 0.35f) else 0f)
        val pitch = sin(now * 1.05f + s.id * 2) * 0.03f + (if (s.sinking) min(0.35f, s.sinkT * 0.12f) else 0f)
        s.inst.transform.setToTranslation(s.x, bob, s.z)
            .rotate(Vector3.Y, -s.h * MathUtils.radiansToDegrees)
            .rotate(Vector3.X, pitch * MathUtils.radiansToDegrees)
            .rotate(Vector3.Z, roll * MathUtils.radiansToDegrees)

        // Пенный след
        s.wakeT -= dt
        if (s.speed > 3 && s.wakeT <= 0 && !s.sinking) {
            s.wakeT = 0.06f
            val bx = s.x - sin(s.h) * s.len * 0.48f
            val bz = s.z + cos(s.h) * s.len * 0.48f
            fx.spawn(fx.FOAM, bx + rand(-0.6f, 0.6f), 0.7f, bz + rand(-0.6f, 0.6f), 0f, 0f, 0f, s.width * 0.32f, 1.4f, Fx.Mode.FOAM, flat = true)
            if (Random.nextBoolean()) {
                val fx2 = s.x + sin(s.h) * s.len * 0.45f
                val fz2 = s.z - cos(s.h) * s.len * 0.45f
                fx.spawn(fx.FOAM, fx2 + rand(-1f, 1f), 0.7f, fz2 + rand(-1f, 1f), 0f, 0f, 0f, s.width * 0.18f, 0.7f, Fx.Mode.FOAM, flat = true)
            }
        }
        // Дым и огонь
        s.smokeT -= dt
        if (s.smokeT <= 0 && (s.hp < s.maxHp * 0.35f || s.sinking)) {
            s.smokeT = if (s.sinking) 0.05f else 0.18f
            val y = bob + s.width * 0.6f
            fx.spawn(fx.DARK, s.x + rand(-2f, 2f), y, s.z + rand(-2f, 2f), rand(-0.5f, 0.5f), rand(3f, 5f), rand(-0.5f, 0.5f), rand(1f, 1.8f), 1.4f, Fx.Mode.PUFF)
            if (s.sinking || Random.nextFloat() < 0.4f) {
                fx.spawn(fx.FIRE, s.x + rand(-2f, 2f), y, s.z + rand(-2f, 2f), 0f, rand(2f, 4f), 0f, rand(0.6f, 1.1f), 0.45f, Fx.Mode.PUFF)
            }
        }
    }

    private fun shipCollisions() {
        for (i in ships.indices) {
            val a = ships[i]
            if (a.sinking) continue
            for (j in i + 1 until ships.size) {
                val b = ships[j]
                if (b.sinking) continue
                val dx = b.x - a.x; val dz = b.z - a.z
                val d = hypot(dx, dz)
                val mn = a.radius + b.radius
                if (d >= mn || d < 0.01f) continue
                val nx = dx / d; val nz = dz / d
                val ma = a.stats.mass; val mb = b.stats.mass; val total = ma + mb
                val ov = mn - d
                a.x -= nx * ov * (mb / total); a.z -= nz * ov * (mb / total)
                b.x += nx * ov * (ma / total); b.z += nz * ov * (ma / total)

                val fax = sin(a.h); val faz = -cos(a.h); val fbx = sin(b.h); val fbz = -cos(b.h)
                val rel = (fax * a.speed - fbx * b.speed) * nx + (faz * a.speed - fbz * b.speed) * nz
                if (rel < 3) continue
                if ((a.ramT[b.id] ?: 0f) > now) continue
                a.ramT[b.id] = now + 0.8f
                if (a.faction == b.faction) { a.speed *= 0.5f; b.speed *= 0.7f; continue }
                val base = rel * 3.2f
                val ramA = fax * nx + faz * nz > 0.5f
                val ramB = -(fbx * nx + fbz * nz) > 0.5f
                if (ramA) damage(b, base * ma * (if (a.boostT > 0) 2f else 1f) / sqrt(mb), a, true)
                if (ramB) damage(a, base * mb * (if (b.boostT > 0) 2f else 1f) / sqrt(ma), b, true)
                if (ramA) damage(a, base * 0.2f, null, false)
                if (ramB) damage(b, base * 0.2f, null, false)
                if (!ramA && !ramB) { damage(a, base * 0.35f, b, true); damage(b, base * 0.35f, a, true) }
                fx.explosion((a.x + b.x) / 2, 2f, (a.z + b.z) / 2, 0.7f)
                sfx.play("hit", volumeAt(a.x, a.z))
                if (a === player || b === player) shake = max(shake, 0.5f)
                a.speed *= 0.35f
                b.speed = b.speed * 0.5f + rel * 0.2f * (ma / total)
            }
        }
    }

    // ================= Бой =================
    private fun volumeAt(x: Float, z: Float): Float {
        val p = player ?: return 0.5f
        return clamp(1 - hypot(p.x - x, p.z - z) / 260f, 0.05f, 1f)
    }

    private fun fireBroadside(s: Ship, sideIdx: Int, target: Ship) {
        val st = s.stats
        val side = if (sideIdx == 0) -1f else 1f
        s.reload[sideIdx] = st.reload
        val fxv = sin(s.h); val fzv = -cos(s.h)
        val rx = cos(s.h) * side; val rz = sin(s.h) * side
        val n = st.cannons
        for (i in 0 until n) {
            val off = (if (n == 1) 0f else i.toFloat() / (n - 1) - 0.5f) * s.len * 0.55f
            val sx = s.x + rx * s.width * 0.6f + fxv * off
            val sz = s.z + rz * s.width * 0.6f + fzv * off
            val d = hypot(target.x - sx, target.z - sz)
            val tt = clamp(d / 55f, 0.35f, 1.3f)
            val spread = (if (s.faction == Faction.PLAYER) 1.5f else 2.5f) + d * 0.045f
            val tx = target.x + sin(target.h) * target.speed * tt + rand(-spread, spread)
            val tz = target.z - cos(target.h) * target.speed * tt + rand(-spread, spread)
            projectiles += Projectile(s, s.faction, sx, sz, tx, tz, rx, rz, -(i * 0.07f + rand(0f, 0.05f)), tt, 2 + tt * 7, st.dmg * rand(0.85f, 1.15f))
        }
        sfx.play("cannon", volumeAt(s.x, s.z))
    }

    private fun updateFiring(s: Ship, dt: Float) {
        s.reload[0] -= dt; s.reload[1] -= dt
        if (s.sinking) return
        val p = player
        val foes: List<Ship> = if (s.faction == Faction.PLAYER) ships.filter { it.faction == Faction.PIRATE && !it.sinking }
        else if (p != null && !p.sinking && !p.safe && s.ai == AiState.ATTACK) listOf(p) else emptyList()
        for (si in 0..1) {
            if (s.reload[si] > 0) continue
            val side = if (si == 0) -1f else 1f
            val rx = cos(s.h) * side; val rz = sin(s.h) * side
            var best: Ship? = null; var bd = Float.MAX_VALUE
            for (o in foes) {
                val dx = o.x - s.x; val dz = o.z - s.z; val d = hypot(dx, dz)
                if (d > s.stats.range || d < 2) continue
                if ((dx * rx + dz * rz) / d < 0.42f) continue
                if (d < bd) { bd = d; best = o }
            }
            best?.let { fireBroadside(s, si, it) }
        }
    }

    private fun updateProjectiles(dt: Float) {
        val it = projectiles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.t += dt
            if (p.t < 0) continue
            if (!p.fired) { p.fired = true; fx.muzzle(p.sx, p.sz, p.dx, p.dz) }
            val k = min(1f, p.t / p.tt)
            if (k < 1) continue
            it.remove()
            val hit = ships.firstOrNull { s -> s.faction != p.faction && !s.sinking && hypot(s.x - p.tx, s.z - p.tz) < s.radius + 1.3f }
            if (hit != null) {
                fx.explosion(p.tx, 2f, p.tz, 0.6f)
                damage(hit, p.dmg, p.owner, false)
                if (Random.nextFloat() < 0.4f) sfx.play("hit", volumeAt(p.tx, p.tz) * 0.7f)
            } else {
                fx.splash(p.tx, p.tz)
                if (Random.nextFloat() < 0.3f) sfx.play("splash", volumeAt(p.tx, p.tz))
            }
        }
    }

    private fun drawProjectiles() {
        for (p in projectiles) {
            if (p.t < 0) continue
            val k = min(1f, p.t / p.tt)
            fx.ball(p.sx + (p.tx - p.sx) * k, 2.4f * (1 - k) + 4 * p.arc * k * (1 - k), p.sz + (p.tz - p.sz) * k)
        }
    }

    fun damage(s: Ship, amount: Float, src: Ship?, ram: Boolean) {
        if (s.sinking) return
        val a = max(1, amount.roundToInt())
        s.hp -= a
        s.lastHit = now
        floatText(s.x, 4 + s.width * 0.5f, s.z, "-$a", if (s === player) rgb(0xff8a80) else Color.WHITE, ui.mid)
        if (s === player) { flash = 0.25f; shake = max(shake, 0.25f) }
        if (s.hp <= 0) { s.hp = 0f; sink(s, src, ram) }
    }

    private fun sink(s: Ship, src: Ship?, ram: Boolean) {
        s.sinkT = 0.001f
        fx.explosion(s.x, 3f, s.z, 1.5f)
        sfx.play("sink", volumeAt(s.x, s.z))
        if (s.faction == Faction.PIRATE) {
            dropLoot(s.x, s.z, Random.nextInt(s.coinsMin, s.coinsMax + 1), s.gems)
            if (src === player) {
                kills++
                questEvent(QType.KILL, s.type, ram)
                if (ram) toast("Таран!")
            }
        } else {
            deathTimer = 2f
        }
    }

    private var deathTimer = 0f

    // ================= Добыча =================
    private fun dropLoot(x: Float, z: Float, c: Int, g: Float) {
        val n = clamp(kotlin.math.ceil(c / 70f), 1f, 7f).toInt()
        repeat(n) { loot += Loot(false, c / n, x + rand(-7f, 7f), z + rand(-7f, 7f), ModelInstance(chestModel), rand(0f, 6f)) }
        val gemsN = if (g >= 1) g.roundToInt() else if (Random.nextFloat() < g) 1 else 0
        repeat(gemsN) { loot += Loot(true, 1, x + rand(-6f, 6f), z + rand(-6f, 6f), ModelInstance(gemModel), rand(0f, 6f)) }
    }

    private fun updateLoot(dt: Float) {
        val p = player
        val it = loot.iterator()
        while (it.hasNext()) {
            val l = it.next()
            l.t += dt; l.age += dt
            if (p != null && !p.sinking) {
                val dx = p.x - l.x; val dz = p.z - l.z; val d = hypot(dx, dz)
                if (d < 24) {
                    val sp = 30 + (24 - d) * 3
                    l.x += dx / d * sp * dt; l.z += dz / d * sp * dt
                }
                if (d < p.radius * 0.6f + 2) { collect(l); it.remove(); continue }
            }
            if (l.age > 120) { it.remove(); continue }
            l.inst.transform.setToTranslation(l.x, waveHeight(l.x, l.z, now) * 0.6f + 0.9f + sin(l.t * 3) * 0.3f, l.z)
                .rotate(Vector3.Y, l.t * 90f)
        }
    }

    private fun collect(l: Loot) {
        if (!l.gem) {
            coins += l.value
            floatText(l.x, 4f, l.z, "+${l.value}", rgb(0xffc93c), ui.big)
            sfx.play("coin")
            questEvent(QType.COLLECT, "", false, l.value)
        } else {
            gems += 1
            floatText(l.x, 4f, l.z, "+1", rgb(0xff9cff), ui.big)
            sfx.play("gem")
        }
    }

    // ================= ИИ пиратов =================
    private fun updateAI(s: Ship, dt: Float) {
        if (s.sinking) return
        val p = player ?: return
        s.aiT -= dt
        val canAttack = !p.sinking && !p.safe
        val dx = p.x - s.x; val dz = p.z - s.z; val d = hypot(dx, dz)
        val aggro = if (s.boss) 280f else 175f
        if (canAttack && (d < aggro || (s.lastHit > now - 10 && d < aggro * 2))) s.ai = AiState.ATTACK
        else if (s.ai == AiState.ATTACK && (!canAttack || d > aggro * 1.8f)) s.ai = AiState.WANDER

        if (s.ai == AiState.ATTACK) {
            val toP = atan2(dx, -dz)
            val r = s.stats.range * 0.7f
            if (d > r * 1.3f) { s.desired = toP; s.throttle = 1f }
            else { s.desired = toP + s.side * (PI / 2).toFloat() * (if (d < r * 0.6f) 1.3f else 1f); s.throttle = 0.8f }
            if (Random.nextFloat() < dt * 0.05f) s.side = -s.side
        } else {
            if (s.aiT <= 0 || hypot(s.wx - s.x, s.wz - s.z) < 25) {
                s.aiT = rand(8f, 16f)
                s.wx = clamp(s.x + rand(-250f, 250f), -WORLD + 80, WORLD - 80)
                s.wz = clamp(s.z + rand(-250f, 250f), -WORLD + 80, WORLD - 80)
            }
            s.desired = atan2(s.wx - s.x, -(s.wz - s.z))
            s.throttle = 0.45f
        }
        for (port in ports) {
            if (hypot(s.x - port.def.x, s.z - port.def.z) < port.def.r + 120) {
                s.desired = atan2(s.x - port.def.x, -(s.z - port.def.z))
                s.ai = AiState.WANDER
                s.aiT = 0f
            }
        }
        val fxv = sin(s.h); val fzv = -cos(s.h)
        for (isl in islands) {
            val lx = s.x + fxv * 32; val lz = s.z + fzv * 32
            if (hypot(lx - isl.x, lz - isl.z) < isl.r + 14) {
                val cross = fxv * (isl.z - s.z) - fzv * (isl.x - s.x)
                s.desired = s.h + (if (cross > 0) 1f else -1f) * 1.2f
                break
            }
        }
    }

    // ================= Задания =================
    fun quest() = questAt(questIdx)

    private fun questEvent(kind: QType, enemy: String, ram: Boolean, amount: Int = 1, port: Int = -1) {
        val q = quest()
        var inc = 0
        when (kind) {
            QType.KILL -> {
                if (q.type == QType.KILL && q.enemy == enemy) inc = 1
                if (q.type == QType.RAM && q.enemy == enemy && ram) inc = 1
            }
            QType.COLLECT -> if (q.type == QType.COLLECT) inc = amount
            QType.REACH -> if (q.type == QType.REACH && q.port == port) inc = 1
            QType.UPGRADE -> if (q.type == QType.UPGRADE) inc = 1
            QType.BUY -> if (q.type == QType.BUY) inc = 1
            QType.RAM -> {}
        }
        if (inc == 0) return
        qprog += inc
        if (qprog >= q.n) {
            coins += q.coins
            gems += q.gems
            questIdx++
            qprog = 0
            val parts = ArrayList<String>()
            if (q.coins > 0) parts += "+${q.coins} монет"
            if (q.gems > 0) parts += "+${q.gems} кристаллов"
            toast("Задание выполнено!\n" + parts.joinToString("   "), 2.6f)
            sfx.play("quest")
            questPop = 0.6f
        }
        save()
    }

    private fun questTarget(): Pair<Float, Float>? {
        val q = quest()
        val p = player ?: return null
        return when (q.type) {
            QType.KILL, QType.RAM -> ships.filter { it.faction == Faction.PIRATE && !it.sinking && it.type == q.enemy }
                .minByOrNull { hypot(it.x - p.x, it.z - p.z) }?.let { it.x to it.z }
            QType.REACH -> ports[q.port].let { it.rx to it.rz }
            QType.UPGRADE, QType.BUY -> ports.minByOrNull { hypot(it.rx - p.x, it.rz - p.z) }!!.let { it.rx to it.rz }
            QType.COLLECT -> loot.filter { hypot(it.x - p.x, it.z - p.z) < 500 }.minByOrNull { hypot(it.x - p.x, it.z - p.z) }?.let { it.x to it.z }
                ?: ships.filter { it.faction == Faction.PIRATE && !it.sinking }.minByOrNull { hypot(it.x - p.x, it.z - p.z) }?.let { it.x to it.z }
        }
    }

    private fun questGlyph(q: Quest) = when (q.type) {
        QType.KILL -> "☠"; QType.RAM -> "⚔"; QType.COLLECT -> ""; QType.REACH -> "⚓"; QType.UPGRADE -> "⚙"; QType.BUY -> "★"
    }

    // ================= Появление пиратов =================
    private fun manageSpawns(dt: Float) {
        spawnTimer -= dt
        val p = player ?: return
        if (spawnTimer > 0) return
        spawnTimer = 1f
        ships.removeAll { it.faction == Faction.PIRATE && hypot(it.x - p.x, it.z - p.z) > 650 }
        val pirates = ships.filter { it.faction == Faction.PIRATE && !it.sinking }
        val zone = hypot(p.x, p.z) / WORLD
        val want = 5 + (zone * 3).roundToInt()
        if (pirates.size < want) findSpawnPoint(240f, 400f)?.let { spawnEnemy(pickEnemyType(it.first, it.second), it.first, it.second) }
        val q = quest()
        if ((q.type == QType.KILL || q.type == QType.RAM) && pirates.none { it.type == q.enemy }) {
            findSpawnPoint(260f, 380f)?.let { spawnEnemy(q.enemy, it.first, it.second) }
        }
    }

    // ================= Порты =================
    private fun updatePorts() {
        val p = player ?: return
        p.safe = false
        for (port in ports) {
            if (hypot(p.x - port.def.x, p.z - port.def.z) < port.def.r + 110) p.safe = true
            val d = hypot(p.x - port.rx, p.z - port.rz)
            if (state == State.PLAY && !p.sinking && !p.inPort && d < 9) openPort(port.i)
            if (p.inPort && port.i == homePort && d > 18) p.inPort = false
        }
    }

    private var portIndex = 0
    private var portTab = 0

    fun openPort(i: Int) {
        val p = player ?: return
        state = State.PORT
        portIndex = i
        joyActive = false
        p.speed = 0f
        p.inPort = true
        p.hp = p.maxHp
        homePort = i
        questEvent(QType.REACH, "", false, port = i)
        save()
        showPort()
    }

    private fun closePort() {
        closeMenus()
        save()
    }

    private fun closeMenus() {
        stage.clear()
        state = State.PLAY
    }

    // ================= Обновление =================
    private fun update(dt: Float) {
        now += dt
        controlPlayer()
        for (s in ships) if (s.faction == Faction.PIRATE) updateAI(s, dt)
        for (s in ships) updateShip(s, dt)
        shipCollisions()
        for (s in ships) updateFiring(s, dt)
        updateProjectiles(dt)
        updateLoot(dt)
        manageSpawns(dt)
        updatePorts()
        ships.removeAll { it !== player && it.sinkT > 3.5f }
        if (deathTimer > 0) {
            deathTimer -= dt
            if (deathTimer <= 0) { state = State.DEAD; showDeath() }
        }
    }

    private fun updateCamera(dt: Float) {
        val p = player ?: return
        val k = (p.len / 12f).pow(0.45f) * (if (wDp / hDp > 1) 0.8f else 1f)
        val tx = p.x + sin(p.h) * p.speed * 0.6f
        val tz = p.z - cos(p.h) * p.speed * 0.6f
        val target = Vector3(tx + camOff.x * k, camOff.y * k, tz + camOff.z * k)
        if (camSnap) { camPos.set(target); camSnap = false }
        camPos.lerp(target, 1 - exp(-dt * 3.5f))
        cam.position.set(camPos)
        if (shake > 0) {
            shake = max(0f, shake - dt)
            cam.position.add(rand(-1f, 1f) * shake * 3, 0f, rand(-1f, 1f) * shake * 3)
        }
        cam.up.set(0f, 1f, 0f)
        cam.direction.set(0f, 0f, -1f)
        cam.lookAt(camPos.x - camOff.x * k, 0f, camPos.z - camOff.z * k - 8)
        cam.update()
    }

    override fun render() {
        val dt = min(0.05f, Gdx.graphics.deltaTime)
        if (state == State.PLAY) update(dt) else now += dt * 0.3f
        fx.time = now
        fx.update(dt)
        updateCamera(dt)
        saveTimer += dt
        if (saveTimer > 5) { saveTimer = 0f; save() }

        Gdx.gl.glClearColor(fogColor.r, fogColor.g, fogColor.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT or
            (if (Gdx.graphics.bufferFormat.coverageSampling) GL20.GL_COVERAGE_BUFFER_BIT_NV else 0))

        val fc = floatArrayOf(fogColor.r, fogColor.g, fogColor.b)
        water.render(cam, camPos.x, camPos.z - 60, now, fc, 300f, 560f)

        val lookX = camPos.x; val lookZ = camPos.z - 80
        modelBatch.begin(cam)
        for ((i, isl) in islands.withIndex()) {
            if (hypot(isl.x - lookX, isl.z - lookZ) > 560 + isl.r) continue
            modelBatch.render(isl.inst, env)
            (isl.foam.materials[0].get(BlendingAttribute.Type) as BlendingAttribute).opacity = 0.25f + sin(now * 1.6f + i) * 0.12f
            modelBatch.render(isl.foam, env)
        }
        for (port in ports) {
            if (hypot(port.rx - lookX, port.rz - lookZ) > 560) continue
            val s = 1 + sin(now * 3) * 0.05f
            port.ring.transform.setToTranslation(port.rx, waveHeight(port.rx, port.rz, now) * 0.55f + 0.9f, port.rz).scale(s, 1f, s)
            (port.ring.materials[0].get(BlendingAttribute.Type) as BlendingAttribute).opacity = 0.55f + sin(now * 3) * 0.2f
            modelBatch.render(port.ring, env)
        }
        for (s in ships) if (hypot(s.x - lookX, s.z - lookZ) < 600) modelBatch.render(s.inst, env)
        for (l in loot) if (hypot(l.x - lookX, l.z - lookZ) < 600) modelBatch.render(l.inst, env)
        modelBatch.end()

        drawProjectiles()
        fx.render()

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        batch.projectionMatrix = hudCam.combined
        batch.begin()
        drawHud(dt)
        batch.end()

        stage.act(dt)
        stage.draw()
        afterRender?.invoke()
    }

    // ================= HUD =================
    private val tmp = Vector3()
    private fun toScreen(x: Float, y: Float, z: Float): Vector3? {
        tmp.set(x, y, z)
        cam.project(tmp)
        if (tmp.z > 1f || tmp.z < 0f) return null
        tmp.x /= scale; tmp.y /= scale
        return tmp
    }

    private fun floatText(x: Float, y: Float, z: Float, text: String, c: Color, font: BitmapFont) {
        val s = toScreen(x, y, z) ?: return
        if (s.x < -50 || s.x > wDp + 50 || s.y < -50 || s.y > hDp + 50) return
        floats += FloatText(text, s.x + rand(-12f, 12f), s.y, c, font)
    }

    fun toast(text: String, seconds: Float = 1.6f) {
        toastText = text; toastT = seconds
    }

    private val cTmp = Color()
    private fun c(hex: Int, a: Float = 1f): Color = cTmp.set(rgb(hex, a))

    private fun drawHud(dt: Float) {
        val b = batch
        val p = player
        val top = hDp - safeTop

        // Красная вспышка при попадании (под интерфейсом)
        if (flash > 0) {
            flash -= dt
            b.color = Color(1f, 1f, 1f, min(1f, flash / 0.25f))
            b.draw(ui.vignette, 0f, 0f, wDp, hDp)
        }

        // HP-полоски
        for (s in ships) {
            if (s.sinking) continue
            if (!(s === p || s.hp < s.maxHp || s.ai == AiState.ATTACK)) continue
            val sc = toScreen(s.x, 6 + s.len * 0.75f, s.z) ?: continue
            if (sc.x < -60 || sc.x > wDp + 60 || sc.y < -20 || sc.y > hDp + 20) continue
            val w = if (s.boss) 110f else 64f
            val hh = if (s.boss) 12f else 9f
            val fill = when { s === p -> 0x3ddc84; s.boss -> 0xb388ff; else -> 0xff5a5f }
            ui.pill(b, sc.x - w / 2 - 1, sc.y - 1, w + 2, hh + 2, c(0x000000, 0.6f))
            ui.rect(b, sc.x - w / 2 + 1, sc.y + 1, (w - 2) * (s.hp / s.maxHp), hh - 2, c(fill))
        }

        // Подписи портов
        for (port in ports) {
            if (p == null || hypot(port.rx - p.x, port.rz - p.z) > 300) continue
            val sc = toScreen(port.rx, 8f, port.rz) ?: continue
            if (sc.x < -100 || sc.x > wDp + 100 || sc.y < 0 || sc.y > hDp) continue
            ui.text(b, ui.small, "⚓ " + port.def.name, sc.x - 100, sc.y + 14, Align.center, 200f)
        }

        // Всплывающие числа
        val fit = floats.iterator()
        while (fit.hasNext()) {
            val f = fit.next()
            f.age += dt
            if (f.age > 1f) { fit.remove(); continue }
            val col = Color(f.color).also { it.a = 1 - f.age }
            ui.text(b, f.font, f.text, f.x - 60, f.y + f.age * 60, Align.center, 120f, c = col)
        }

        // Указатель цели задания
        val target = if (state == State.PLAY) questTarget() else null
        if (target != null && p != null && !p.sinking) {
            val d = hypot(target.first - p.x, target.second - p.z)
            val ps = toScreen(p.x, 0f, p.z)?.let { Vector3(it) }
            val ts = toScreen(target.first, 0f, target.second)?.let { Vector3(it) }
            if (d > 35 && ps != null) {
                var ax = 0f; var ay = 1f
                if (ts != null) { ax = ts.x - ps.x; ay = ts.y - ps.y } else {
                    // Цель позади камеры: направление по миру
                    ax = target.first - p.x; ay = -(target.second - p.z)
                }
                val al = hypot(ax, ay).coerceAtLeast(0.001f)
                val r = 70 + p.len * 1.5f
                val ang = atan2(ay, ax) * MathUtils.radiansToDegrees - 90f
                ui.icon(b, ui.triangle, ps.x + ax / al * r, ps.y + ay / al * r, 26f, 26f, ang, c(0xffc93c))
            }
            // Внизу: значок и расстояние
            val by = 18 + safeBottom
            ui.text(b, ui.big, "${d.roundToInt()} м", 0f, by + 26, Align.center, wDp)
            ui.icon(b, ui.disc, wDp / 2, by + 52, 40f, 40f, 0f, c(0x3c4650, 0.75f))
            val q = quest()
            if (q.type == QType.COLLECT) ui.icon(b, ui.coin, wDp / 2, by + 52, 24f, 24f)
            else ui.text(b, ui.big, questGlyph(q), wDp / 2 - 30, by + 64, Align.center, 60f)
        }

        // Джойстик
        if (joyActive && state == State.PLAY) {
            ui.icon(b, ui.disc, joyOx, joyOy, 186f, 186f, 0f, c(0x3ddc84, 0.06f))
            ui.icon(b, ui.ring, joyOx, joyOy, 192f, 192f, 0f, c(0x3ddc84, 0.95f))
            val kx = joyOx + joyJx * joyMag * joyR
            val ky = joyOy + joyJy * joyMag * joyR
            ui.icon(b, ui.disc, kx, ky, 56f, 56f, 0f, c(0xffffff, 0.35f))
            if (joyMag > 0.12f) {
                val ang = atan2(joyJy, joyJx) * MathUtils.radiansToDegrees - 90f
                ui.icon(b, ui.triangle, joyOx + joyJx * 118, joyOy + joyJy * 118, 52f, 60f, ang, c(0x3ddc84, 0.9f))
            }
        }

        // Пауза
        for (i in 0..1) {
            val x = 24f + i * 28
            ui.round(b, x - 3, top - 82, 24f, 66f, c(0x1b2a3a))
            ui.round(b, x, top - 78, 18f, 60f, c(0xffffff))
        }

        // Рывок
        val bx = 58f; val by2 = top - 140f
        val active = p != null && p.boostT > 0
        ui.icon(b, ui.disc, bx, by2, 88f, 88f, 0f, c(0xffffff, 0.12f))
        ui.icon(b, ui.ring, bx, by2, 92f, 92f, 0f, if (active) c(0x3ddc84) else c(0xffffff, 0.6f))
        ui.icon(b, ui.sails, bx, by2 + 2, 66f, 66f, 0f, c(0xffffff, 0.92f))
        if (p != null && p.boostCd > 0) ui.pie(b, bx, by2, 42f, p.boostCd / 7f, c(0x0a1e32, 0.6f))

        // Монеты и кристаллы
        for ((i, pair) in listOf(fmt(coins) to ui.coin, fmt(gems) to ui.gem).withIndex()) {
            val y = top - 16 - 44 - i * 50
            val tw = ui.measure(ui.big, pair.first)
            val w = max(118f, tw + 62)
            ui.round(b, wDp - 12 - w, y, w, 42f, c(0x0a141e, 0.55f))
            ui.text(b, ui.big, pair.first, wDp - 12 - w, y + 32, Align.right, w - 50)
            ui.icon(b, pair.second, wDp - 36, y + 21, 32f, if (i == 0) 32f else 28f)
        }

        // Баннер задания
        val q = quest()
        val qx = 116f
        val qw = min(wDp - 128f, 440f)
        val textW = qw - 58
        val lay = com.badlogic.gdx.graphics.g2d.GlyphLayout(ui.mid, q.text, Color.WHITE, textW, Align.center, true)
        val qh = lay.height + 34
        val qy = top - 112 - qh
        val pop = if (questPop > 0) { questPop -= dt; 1 + sin(questPop / 0.6f * PI.toFloat()) * 0.08f } else 1f
        val cx = qx + qw / 2; val cy = qy + qh / 2
        val sw = qw * pop; val sh = qh * pop
        ui.round(b, cx - sw / 2, cy - sh / 2 - 3, sw, sh, c(0x0d3f7a))
        ui.round(b, cx - sw / 2, cy - sh / 2, sw, sh, c(0x2f8ff0))
        if (q.type == QType.COLLECT) ui.icon(b, ui.coin, cx - sw / 2 + 26, cy, 28f, 28f)
        else ui.text(b, ui.big, questGlyph(q), cx - sw / 2 + 4, cy + 14, Align.center, 44f)
        ui.mid.color = Color.WHITE
        ui.mid.draw(b, lay, cx - sw / 2 + 50, cy + sh / 2 - 7)
        val barW = sw - 64
        ui.pill(b, cx - sw / 2 + 50, cy - sh / 2 + 6, barW, 11f, c(0x0d2f57))
        val prog = min(1f, qprog.toFloat() / q.n)
        if (prog > 0) ui.pill(b, cx - sw / 2 + 50, cy - sh / 2 + 6, max(11f, barW * prog), 11f, c(0xf5b700))

        // Сообщение по центру
        if (toastT > 0) {
            toastT -= dt
            val a = min(1f, toastT * 3)
            ui.text(b, ui.huge, toastText, 10f, hDp * 0.6f, Align.center, wDp - 20, true, Color(1f, 1f, 1f, a))
        }
        b.color = Color.WHITE
    }

    // ================= Меню (Scene2D) =================
    private fun Actor.onClick(f: () -> Unit) {
        addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) { sfx.play("click"); f() }
        })
    }

    private fun overlay(): Table = Table().also {
        it.setFillParent(true)
        it.background = ui.flat(0x05141f, 0.72f)
        it.touchable = Touchable.enabled
        stage.addActor(it)
    }

    private fun panelTable() = Table().also { it.background = ui.panel(0x10304d); it.pad(14f) }

    private fun currencyRow(): Table = Table().also {
        it.add(Image(ui.coin)).size(20f).padRight(4f)
        it.add(ui.label(fmt(coins), ui.mid)).padRight(14f)
        it.add(Image(ui.gem)).size(22f, 19f).padRight(4f)
        it.add(ui.label(fmt(gems), ui.mid))
    }

    private class Bar(val ui: Ui, val frac: Float) : Actor() {
        override fun draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: Float) {
            ui.pill(batch, x, y + height / 2 - 4, width, 8f, rgb(0x0b2238))
            if (frac > 0) ui.pill(batch, x, y + height / 2 - 4, max(8f, width * frac.coerceIn(0f, 1f)), 8f, rgb(0x3ddc84))
            batch.color = Color.WHITE
        }
    }

    private class Pips(val ui: Ui, val level: Int) : Actor() {
        override fun draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: Float) {
            for (i in 0 until MAX_LEVEL) ui.pill(batch, x + i * 26, y + height / 2 - 4, 22f, 8f, if (i < level) rgb(0xffc93c) else rgb(0x0b2238))
            batch.color = Color.WHITE
        }
    }

    private fun statRow(t: Table, name: String, v: Float, max: Float, shown: String) {
        val r = Table()
        r.add(ui.label(name, ui.small, rgb(0xcfe3f7))).width(96f).left()
        r.add(Bar(ui, v / max)).growX().height(14f).padRight(8f)
        r.add(ui.label(shown, ui.small, align = Align.right)).width(52f).right()
        t.add(r).growX().padTop(2f).row()
    }

    private fun priceButton(text: String, price: Int, gemsPrice: Int, can: Boolean): TextButton {
        val btn = if (can) ui.green(text) else ui.gray(text)
        btn.add(Image(ui.coin)).size(20f).padLeft(6f)
        if (gemsPrice > 0) {
            btn.add(ui.label(" + $gemsPrice", ui.mid))
            btn.add(Image(ui.gem)).size(22f, 19f).padLeft(4f)
        }
        return btn
    }

    private var portScroll = 0f

    fun showPort() {
        val prevScroll = portScroll
        stage.clear()
        val root = overlay()
        val panel = panelTable()
        panel.add(ui.label("⚓ " + PORTS[portIndex].name, ui.big, align = Align.center)).growX().row()
        panel.add(Table().also { t ->
            t.add(currencyRow())
            t.add(ui.label("  ·  корабль отремонтирован", ui.small, rgb(0xb9d6f2)))
        }).padTop(4f).padBottom(10f).row()

        val tabs = Table()
        listOf("Верфь", "Улучшения").forEachIndexed { i, name ->
            val b = if (portTab == i) ui.blue(name) else ui.button(name, 0x164266, 0x123a5c)
            b.onClick { portTab = i; portScroll = 0f; showPort() }
            tabs.add(b).growX().height(46f).pad(0f, 4f, 0f, 4f)
        }
        panel.add(tabs).growX().padBottom(10f).row()

        val content = Table().top()
        if (portTab == 0) shipCards(content) else upgradeCards(content)
        val scroll = ScrollPane(content, ui.scrollStyle)
        scroll.setScrollingDisabled(true, false)
        scroll.setFadeScrollBars(true)
        scroll.setOverscroll(false, true)
        panel.add(scroll).grow().row()

        val sail = ui.blue("⚓  Выйти в море")
        sail.onClick { closePort() }
        panel.add(sail).growX().height(54f).padTop(10f).row()

        root.add(panel).width(min(wDp - 16, 520f)).height(hDp - safeTop - safeBottom - 24)
        stage.act(0f)
        root.layout(); panel.layout(); scroll.layout()
        scroll.scrollY = prevScroll
        scroll.updateVisualScroll()
        scrollRef = scroll
    }

    private var scrollRef: ScrollPane? = null

    private fun card(): Table = Table().also { it.background = ui.panel(0x164266); it.pad(12f) }

    private fun shipCards(content: Table) {
        for (sdef in PLAYER_SHIPS) {
            val isOwned = sdef.id in owned
            val current = sdef.id == shipId
            val st = shipStats(sdef, if (isOwned) ensureUp(sdef.id) else null)
            val c = card()
            if (current) c.background = ui.panel(0x1d5a46)
            c.add(ui.label(sdef.cls.uppercase(), ui.small, rgb(0xffc93c))).left().row()
            c.add(ui.label(sdef.name, ui.big)).left().row()
            c.add(ui.label(sdef.desc, ui.small, rgb(0xb9d6f2), wrap = true)).growX().padBottom(6f).row()
            statRow(c, "Прочность", st.hp.toFloat(), 1000f, st.hp.toString())
            statRow(c, "Скорость", st.speed, 19f, String.format(java.util.Locale.US, "%.1f", st.speed))
            statRow(c, "Пушки", st.cannons.toFloat(), 12f, "${st.cannons}×2")
            statRow(c, "Урон", st.dmg, 24f, st.dmg.roundToInt().toString())
            statRow(c, "Таран", sdef.mass, 5f, String.format(java.util.Locale.US, "%.1f", sdef.mass))
            val btn: TextButton = when {
                current -> ui.gray("✓ Ваш корабль").also { it.isDisabled = true }
                isOwned -> ui.blue("Выбрать").also { it.onClick { shipId = sdef.id; rebuildPlayer(); save(); refreshPort() } }
                else -> priceButton("Купить ${fmt(sdef.price)}", sdef.price, sdef.gems, coins >= sdef.price && gems >= sdef.gems)
                    .also { it.onClick { buyShip(sdef) } }
            }
            c.add(btn).growX().height(50f).padTop(8f).row()
            content.add(c).growX().padBottom(10f).row()
        }
    }

    private fun upgradeCards(content: Table) {
        val def = playerDef()
        val u = ensureUp(def.id)
        val head = card()
        head.add(ui.label(def.cls.uppercase(), ui.small, rgb(0xffc93c))).left().row()
        head.add(ui.label(def.name, ui.big)).left().row()
        head.add(ui.label("Улучшения действуют только на этот корабль.", ui.small, rgb(0xb9d6f2), wrap = true)).growX().row()
        content.add(head).growX().padBottom(10f).row()
        val glyphs = listOf("❤", "⬆", "⚔", "⚙")
        for ((i, upg) in UPGRADES.withIndex()) {
            val lvl = u[i]
            val c = card()
            c.add(ui.label(glyphs[i], ui.huge, rgb(0xffc93c))).width(40f).padRight(8f)
            val info = Table()
            info.add(ui.label(upg.name, ui.big)).left().row()
            info.add(Pips(ui, lvl)).width(130f).height(14f).left().padTop(4f).padBottom(4f).row()
            info.add(ui.label("+${(upg.per * 100).roundToInt()}% ${upg.text} за уровень", ui.small, rgb(0xb9d6f2), wrap = true)).growX().row()
            c.add(info).growX()
            val btn: TextButton = if (lvl >= MAX_LEVEL) ui.gray("МАКС").also { it.isDisabled = true } else {
                val cost = upgradeCost(def, lvl)
                priceButton(fmt(cost), cost, 0, coins >= cost).also { it.onClick { buyUpgrade(i) } }
            }
            c.add(btn).width(116f).height(50f).padLeft(8f)
            content.add(c).growX().padBottom(10f).row()
        }
    }

    private fun refreshPort() {
        portScroll = scrollRef?.scrollY ?: 0f
        showPort()
    }

    private fun buyShip(def: ShipDef) {
        if (coins < def.price) { toast("Не хватает монет"); return }
        if (gems < def.gems) { toast("Не хватает кристаллов"); return }
        coins -= def.price
        gems -= def.gems
        owned += def.id
        ensureUp(def.id)
        shipId = def.id
        rebuildPlayer()
        sfx.play("quest")
        toast("Новый корабль: ${def.name}!")
        questEvent(QType.BUY, "", false)
        save()
        refreshPort()
    }

    private fun buyUpgrade(i: Int) {
        val def = playerDef()
        val u = ensureUp(def.id)
        if (u[i] >= MAX_LEVEL) return
        val cost = upgradeCost(def, u[i])
        if (coins < cost) { toast("Не хватает монет"); return }
        coins -= cost
        u[i]++
        rebuildPlayer()
        sfx.play("coin")
        questEvent(QType.UPGRADE, "", false)
        save()
        refreshPort()
    }

    fun openPause() {
        if (state != State.PLAY) return
        state = State.PAUSE
        joyActive = false
        save()
        showPause()
    }

    private var resetArmed = 0f

    private fun showPause() {
        stage.clear()
        val root = overlay()
        val panel = panelTable()
        panel.add(ui.label("Пауза", ui.huge, align = Align.center)).growX().row()
        panel.add(ui.label("Потоплено пиратов: $kills · Заданий выполнено: $questIdx", ui.small, rgb(0xb9d6f2), wrap = true, align = Align.center))
            .growX().padTop(6f).padBottom(12f).row()
        val resume = ui.green("▶  Продолжить").also { it.onClick { closeMenus() } }
        val sound = ui.blue(if (sfx.enabled) "Звук: вкл" else "Звук: выкл").also {
            it.onClick { sfx.enabled = !sfx.enabled; save(); showPause() }
        }
        val reset = ui.gray(if (resetArmed > 0) "Нажмите ещё раз для сброса" else "↺  Начать заново").also {
            it.onClick {
                if (resetArmed <= 0) { resetArmed = 1f; showPause() } else { resetArmed = 0f; resetProgress() }
            }
        }
        for (bt in listOf(resume, sound, reset)) panel.add(bt).growX().height(54f).padTop(8f).row()
        root.add(panel).width(min(wDp - 32, 420f))
    }

    private fun showDeath() {
        stage.clear()
        val loss = (coins * 0.15f).toInt()
        val root = overlay()
        val panel = panelTable()
        panel.add(ui.label("☠ Корабль потоплен!", ui.big, align = Align.center)).growX().row()
        panel.add(ui.label("Вы потеряли $loss монет. Корабль отбуксируют в порт «${PORTS[homePort].name}».",
            ui.small, rgb(0xb9d6f2), wrap = true, align = Align.center)).growX().pad(8f, 0f, 12f, 0f).row()
        val btn = ui.green("⚓  Вернуться в порт")
        btn.onClick {
            coins -= loss
            spawnPlayer()
            val p = player!!
            ships.removeAll { it.faction == Faction.PIRATE && hypot(it.x - p.x, it.z - p.z) < 280 }
            closeMenus()
            save()
        }
        panel.add(btn).growX().height(54f).row()
        root.add(panel).width(min(wDp - 32, 420f))
    }

    override fun dispose() {
        save()
        models.forEach { it.dispose() }
        water.dispose()
        fx.dispose()
        sfx.dispose()
        ui.dispose()
        batch.dispose()
        modelBatch.dispose()
        stage.dispose()
    }
}
