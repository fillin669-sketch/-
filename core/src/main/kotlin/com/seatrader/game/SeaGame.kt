package com.seatrader.game

import com.badlogic.gdx.Application
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.Align
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
import com.badlogic.gdx.scenes.scene2d.Stage
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
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

const val GRAVITY = 30f
const val BALL_SPEED = 62f

class SeaGame : ApplicationAdapter() {
    enum class State { LOADING, PLAY, PORT, PAUSE, DEAD, DIALOG, CUTSCENE }

    // ---------- Графика ----------
    lateinit var ui: Ui
    lateinit var sfx: Sfx
    private lateinit var water: Water
    lateinit var fx: Fx
    lateinit var cam: PerspectiveCamera
    private lateinit var modelBatch: ModelBatch
    lateinit var batch: SpriteBatch
    val hudCam = OrthographicCamera()
    lateinit var stage: Stage
    lateinit var hud: Hud
    lateinit var menus: Menus
    val systems = Systems(this)
    val living = Living(this)
    val portView = PortView(this)
    val extras = Extras(this)
    val guilds = Guilds(this)
    val chestModelPublic get() = chestModel
    val chestReady get() = ::chestModel.isInitialized
    /** Свой рисунок флага, узор и второй цвет парусов (индекс палитры, -1 — нет). */
    var flagPix = ""
    var sailPat = 0
    var sail2Idx = -1
    var hullIdx = 0
    val ownedHulls = HashSet<Int>()
    var trimIdx = -1
    var flagShape = 0
    var flagSize = 0
    var patScale = 1
    /** Игрок стоит в круге у причала: показываем кнопку «В гавань» (номер порта, -2 — своя база). */
    var dockAt = -1
    private val env = Environment()
    private lateinit var sunLight: com.badlogic.gdx.graphics.g3d.environment.DirectionalShadowLight
    private lateinit var shadowBatch: ModelBatch
    private val shadowCenter = Vector3()
    private val fillLight = DirectionalLight()
    private val seaBounce = DirectionalLight()
    private lateinit var post: PostFx
    private val clouds = ArrayList<FloatArray>() // x, z, размер, поворот, вариант
    private val ambient = ColorAttribute(ColorAttribute.AmbientLight, 0.6f, 0.6f, 0.6f, 1f)
    private val fogAttr = ColorAttribute(ColorAttribute.Fog, 0.5f, 0.7f, 0.9f, 1f)
    val sky = Sky()
    private val models = ArrayList<Model>()
    private val shipModels = HashMap<String, Model>()
    private lateinit var chestModel: Model
    private lateinit var gemModel: Model
    private lateinit var treasureModel: Model
    private lateinit var serpentHeadModel: Model
    private lateinit var serpentBodyModel: Model
    private lateinit var portRingModel: Model
    var scale = 1f
    var wDp = 400f
    var hDp = 800f
    var safeTop = 0f
    var safeBottom = 0f
    var loadProgress = 0f
    private var loadStep = 0

    var debugShadow = false
    var debugNoShadow = false

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
    val allies = ArrayList<String>()
    var sailIdx = 0
    var flagIdx = 0
    val ownedSails = HashSet<Int>()
    val ownedFlags = HashSet<Int>()
    var tutorial = 0
    var lastDaily = -1L
    var streak = 0
    var joySens = 85f
    var leftHanded = false
    /** Уровень графики: 0 — низкая, 1 — средняя, 2 — высокая, 3 — ультра. */
    var gfx = 3
    val highQuality get() = gfx >= 2
    private lateinit var detailBatch: ModelBatch
    private lateinit var plainBatch: ModelBatch
    var heroId = ""
    fun hero(): Hero? = HEROES.firstOrNull { it.id == heroId && guilds.heroUnlocked(it.id) }

    // ---------- Мир ----------
    var state = State.LOADING
    val islands = ArrayList<Island>()
    val ports = ArrayList<Port>()
    val ships = ArrayList<Ship>()
    val prologue = Prologue(this)
    /** Тесты на ПК пропускают предысторию. */
    var skipPrologue = false
    /** Тест защиты от вылетов: бросить ошибку в кадре. */
    var debugThrow = false
    var player: Ship? = null
    val balls = ArrayList<Ball>()
    val loot = ArrayList<Loot>()
    val floats = ArrayList<FloatText>()
    var treasure: Treasure? = null
    private var treasureTimer = 5f
    var serpent: Serpent? = null
    private var serpentTimer = 120f
    private val gulls = ArrayList<Gull>()
    private var nextId = 1
    var now = 0f
    var shake = 0f
    var flash = 0f
    var lightning = 0f
    var toastText = ""
    var toastT = 0f
    var questPop = 0f
    private var spawnTimer = 0f
    private var saveTimer = 0f
    var camSnap = true
    val camPos = Vector3()
    private val camOff = Vector3(0f, 118f, 78f)
    var deathTimer = 0f
    var combatT = 0f

    // Погода и время суток
    var dayTime = 0.28f
    var stormLevel = 0f
    var stormTarget = 0f
    var stormTimer = rand(150f, 260f)
    private var lightningTimer = 5f
    var windA = rand(-PI.toFloat(), PI.toFloat())
    var windS = 1f

    // Способности: перезарядка и активные боеприпасы
    val abilityCd = FloatArray(ABILITIES.size)
    var ammo = Ammo.NORMAL
    var ammoT = 0f
    var tutorialT = 0f
    private var startX = 0f
    private var startZ = 0f

    // Ручной прицел
    var aimActive = false
    var aimPointer = -1
    var aimX = 0f
    var aimZ = 0f
    var aimDx = 0f
    var aimDy = 0f

    // ---------- Ввод ----------
    var joyActive = false
    private var joyPointer = -1
    var joyOx = 0f
    var joyOy = 0f
    var joyJx = 0f
    var joyJy = 0f
    var joyMag = 0f
    private val keys = HashSet<Int>()

    // =====================================================================
    override fun create() {
        val density = Gdx.graphics.density
        scale = System.getProperty("sea.scale")?.toFloatOrNull()
            ?: if (Gdx.app.type == Application.ApplicationType.Desktop) 1f else density
        scale = min(scale, Gdx.graphics.width / 360f).coerceAtLeast(0.75f)

        ui = Ui(scale)
        batch = SpriteBatch()
        stage = Stage(ScreenViewport().also { it.unitsPerPixel = 1f / scale })
        hud = Hud(this)
        menus = Menus(this)
        prefs = Gdx.app.getPreferences("seawolves")
        try {
            val f = Gdx.files.local("crash.txt")
            if (f.exists()) { lastCrash = f.readString("UTF-8"); f.delete() }
        } catch (_: Throwable) { }
        try { load() } catch (e: Throwable) {
            // повреждённое сохранение не должно ломать запуск
            Gdx.app.error("SeaGame", "load", e)
            lastCrash = "Сохранение не прочиталось: " + describe(e)
        }
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        Gdx.input.inputProcessor = InputMultiplexer(stage, input)
    }

    private fun glInt(e: Int): Int {
        val buf = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(16)
        Gdx.gl.glGetIntegerv(e, buf)
        return buf.get(0)
    }

    /** Наибольший размер буфера, который точно поддерживает видеочип (текстура и буфер глубины). */
    private fun maxTex(): Int = minOf(glInt(GL20.GL_MAX_TEXTURE_SIZE), glInt(GL20.GL_MAX_RENDERBUFFER_SIZE)).coerceAtLeast(1024)

    /** Видеочип телефона (для настроек и диагностики). */
    var gpuName = ""

    /** Стартовый уровень графики по видеочипу: старые и слабые — ниже, флагманы — ультра. */
    private fun detectGfx(): Int {
        if (Gdx.app.type != Application.ApplicationType.Android) return 3
        val r = gpuName.lowercase()
        val low = listOf("sgx", "mali-4", "mali-t6", "mali-t7", "adreno (tm) 3", "adreno (tm) 4", "adreno (tm) 50", "ge8", "mali-g31",
            "vivante", "videocore", "powervr rogue g6", "powervr rogue g")
        val mid = listOf("mali-t8", "mali-g51", "mali-g52", "mali-g57", "mali-g68", "adreno (tm) 51", "adreno (tm) 52", "adreno (tm) 53",
            "adreno (tm) 54", "adreno (tm) 60", "adreno (tm) 61", "adreno (tm) 62", "gm9", "bxm", "img bxm")
        val ultra = listOf("mali-g77", "mali-g78", "mali-g710", "mali-g715", "mali-g720", "mali-g610", "mali-g615", "immortalis", "xclipse",
            "adreno (tm) 65", "adreno (tm) 66", "adreno (tm) 68", "adreno (tm) 69", "adreno (tm) 7", "adreno (tm) 8")
        var lvl = when {
            low.any { r.contains(it) } -> 0
            mid.any { r.contains(it) } -> 1
            ultra.any { r.contains(it) } -> 3
            else -> 2
        }
        if (maxTex() < 4096) lvl = minOf(lvl, 1)
        return lvl
    }

    /** Карта теней не создалась (мало видеопамяти) — играем без теней. */
    private var shadowFail = false
    private var reflFail = false

    @Suppress("DEPRECATION")
    private fun makeSunLight(size: Int): com.badlogic.gdx.graphics.g3d.environment.DirectionalShadowLight {
        var sz = size
        while (true) {
            try { return com.badlogic.gdx.graphics.g3d.environment.DirectionalShadowLight(sz, sz, 300f, 300f, 1f, 320f) }
            catch (e: Exception) {
                Gdx.app.error("Shadows", "size $sz: ${e.message}")
                if (sz <= 512) { shadowFail = true; return com.badlogic.gdx.graphics.g3d.environment.DirectionalShadowLight(64, 64, 300f, 300f, 1f, 320f) }
                sz /= 2
            }
        }
    }

    private val wildCount = 60
    private val totalSteps = 1 + PORTS.size + wildCount + 2

    /** Мир строится по кусочку за кадр, пока показывается экран загрузки. */
    private fun loadNext() {
        when {
            loadStep == 0 -> {
                gpuName = try { Gdx.gl.glGetString(GL20.GL_RENDERER) ?: "" } catch (_: Exception) { "" }
                if (!prefs.contains("gfx")) { gfx = detectGfx(); autoGfx = true }
                sfx = Sfx()
                sfx.sfxVolume = prefs.getFloat("sfxVol", 0.9f)
                sfx.musicVolume = prefs.getFloat("musicVol", 0.6f)
                water = Water()
                cam = PerspectiveCamera(52f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
                cam.near = 1f
                cam.far = 700f
                resize(Gdx.graphics.width, Gdx.graphics.height)
                detailBatch = Shaders.batch(true)
                plainBatch = Shaders.batch(false)
                modelBatch = if (gfx >= 1) detailBatch else plainBatch
                fx = Fx(cam, ui.softDisc, ui.white, ui.streak, ui.gull)
                sunLight = makeSunLight(shadowSize())
                // теневой проход со смещением глубины (убирает полосы «теневых акне»)
                val dsCfg = com.badlogic.gdx.graphics.g3d.shaders.DepthShader.Config()
                val vs = com.badlogic.gdx.graphics.g3d.shaders.DepthShader.getDefaultVertexShader()
                val end = vs.lastIndexOf('}')
                dsCfg.vertexShader = vs.substring(0, end) + "\n    gl_Position.z += 0.004 * gl_Position.w;\n}\n"
                shadowBatch = ModelBatch(com.badlogic.gdx.graphics.g3d.utils.DepthShaderProvider(dsCfg))
                env.set(ambient)
                env.add(sunLight)
                env.shadowMap = sunLight
                env.add(fillLight)
                env.add(seaBounce)
                post = PostFx().also { it.scale = scaleSteps()[scaleSteps().size - 1]; it.allocScale = it.scale; it.bloomOn = gfx >= 1 }
                scaleIdx = scaleSteps().size - 1
                val cr = Random(5)
                repeat(14) { clouds += floatArrayOf(cr.nextFloat() * 900 - 450, cr.nextFloat() * 900 - 450, 70f + cr.nextFloat() * 90, cr.nextFloat() * 360, cr.nextInt(2).toFloat()) }
                env.set(fogAttr)
                chestModel = Models.chest().also { models += it }
                gemModel = Models.gem().also { models += it }
                treasureModel = Models.treasure().also { models += it }
                serpentHeadModel = Models.serpentHead().also { models += it }
                serpentBodyModel = Models.serpentBody().also { models += it }
                portRingModel = Models.portRing().also { models += it }
                systems.initModels(models)
            }
            loadStep <= PORTS.size -> buildPort(loadStep - 1)
            loadStep == PORTS.size + 1 -> { systems.buildFortresses(models) { x, z, r, reg -> addIsland(x, z, r, reg, 777) }; living.buildBase(models); portView.build(models) }
            loadStep <= PORTS.size + 1 + wildCount -> repeat(1) { buildWildIsland() }
            else -> {
                systems.buildReefsAndWrecks { x, z, m -> isWater(x, z, m) }
                systems.placeBottles { x, z, m -> isWater(x, z, m) }
                living.buildWorld { x, z, m -> isWater(x, z, m) }
                extras.afterWorldBuilt()
                fx.limit = intArrayOf(250, 450, 700, 900)[gfx]
                // заранее строим модели всех врагов и наёмников — без подтормаживаний при их появлении
                for (d in ENEMIES.values) shipModel(d.look)
                spawnPlayer()
                startX = player!!.x; startZ = player!!.z
                repeat(4) { i ->
                    findSpawnPoint(230f, 380f)?.let { spawnEnemy(if (i < 3) "sloop" else "brig", it.first, it.second) }
                }
                spawnGulls()
                state = State.PLAY
                hud.disposeSplash()
                lastCrash?.let { t -> lastCrash = null; menus.showError(t) ; return@let }
                if (state == State.DIALOG) Unit
                else if (guilds.guild < 0) { if (skipPrologue) menus.showGuildSelect() else startPrologue(true) } else { checkDaily(); checkJournal() }
            }
        }
        loadStep++
        loadProgress = min(1f, loadStep.toFloat() / totalSteps)
    }

    private fun buildPort(i: Int) {
        val p = PORTS[i]
        val lights = ArrayList<Vector3>()
        val m = Models.island(p.r, 100 + i, true, i == 0 || i >= 5, lights, regionAt(p.x, p.z)).also { models += it }
        val f = Models.foam(p.r).also { models += it }
        islands += Island(p.x, p.z, p.r * 1.06f, i, ModelInstance(m).also { it.transform.setToTranslation(p.x, 0f, p.z) },
            ModelInstance(f).also { it.transform.setToTranslation(p.x, 0.6f, p.z) }, lights.map { it.add(p.x, 0f, p.z) })
        ports += Port(p, i, p.x, p.z + p.r * 1.17f + 14f, ModelInstance(portRingModel))
    }

    private val wildRnd = Random(4242)
    private var wildTries = 0

    private fun addIsland(x: Float, z: Float, r: Float, reg: Region, seed: Int) {
        val lights = ArrayList<Vector3>()
        val glows = ArrayList<Vector3>()
        val m = Models.island(r, seed, false, false, lights, reg, glows).also { models += it }
        val f = Models.foam(r).also { models += it }
        islands += Island(x, z, r * 1.06f, -1, ModelInstance(m).also { it.transform.setToTranslation(x, 0f, z) },
            ModelInstance(f).also { it.transform.setToTranslation(x, 0.6f, z) }, emptyList(), glows.map { it.add(x, 0f, z) })
    }

    private fun buildWildIsland() {
        while (wildTries < 3000) {
            wildTries++
            val x = (wildRnd.nextFloat() * 2 - 1) * (WORLD - 90)
            val z = (wildRnd.nextFloat() * 2 - 1) * (WORLD - 90)
            val reg = regionAt(x, z)
            val r = when (reg) { Region.ICE -> 14 + wildRnd.nextFloat() * 30; Region.REEF -> 12 + wildRnd.nextFloat() * 26; else -> 16 + wildRnd.nextFloat() * 40 }
            if (islands.any { hypot(it.x - x, it.z - z) < it.r + r + 80 }) continue
            addIsland(x, z, r, reg, 1000 + wildTries)
            return
        }
    }

    override fun resize(width: Int, height: Int) {
        wDp = width / scale
        hDp = height / scale
        hudCam.setToOrtho(false, wDp, hDp)
        stage.viewport.update(width, height, true)
        safeTop = Gdx.graphics.safeInsetTop / scale
        safeBottom = Gdx.graphics.safeInsetBottom / scale
        if (::cam.isInitialized) {
            cam.viewportWidth = width.toFloat()
            cam.viewportHeight = height.toFloat()
            cam.fieldOfView = if (width.toFloat() / height < 0.75f) 54f else 44f
            cam.update()
        }
        menus.relayout()
    }

    override fun pause() {
        if (state == State.LOADING) return
        save()
        if (state == State.PLAY) openPause()
        if (::sfx.isInitialized) sfx.pauseAll()
    }

    override fun resume() {
        if (::sfx.isInitialized) sfx.resumeAll()
    }

    // ================= Сохранение =================
    fun ensureUp(id: String) = up.getOrPut(id) { IntArray(4) }

    private fun load(prefs: Preferences = this.prefs) {
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
        allies.clear()
        allies += prefs.getString("allies", "").split(",").filter { s -> MERCS.any { it.id == s } }.take(MAX_ALLIES)
        sailIdx = prefs.getInteger("sail", 0).coerceIn(0, SAIL_COLORS.size - 1)
        flagIdx = prefs.getInteger("flag", 0).coerceIn(0, FLAG_COLORS.size - 1)
        ownedSails.clear(); ownedSails += 0
        ownedSails += prefs.getString("ownedSails", "").split(",").mapNotNull { it.toIntOrNull() }
        ownedFlags.clear(); ownedFlags += 0
        ownedFlags += prefs.getString("ownedFlags", "").split(",").mapNotNull { it.toIntOrNull() }
        tutorial = prefs.getInteger("tutorial", 0)
        lastDaily = prefs.getLong("lastDaily", -1L)
        streak = prefs.getInteger("streak", 0)
        joySens = prefs.getFloat("joySens", 85f)
        leftHanded = prefs.getBoolean("leftHanded", false)
        autoGfx = prefs.getBoolean("autoGfx", true)
        gfx = if (prefs.contains("gfx")) prefs.getInteger("gfx", 3).coerceIn(0, 3) else if (prefs.getBoolean("hq", true)) 3 else 0
        heroId = prefs.getString("hero", "")
        systems.load(prefs)
        living.load(prefs)
        guilds.load(prefs)
        extras.load(prefs)
        flagPix = prefs.getString("flagPix", "").let { if (it.length == 48) upscaleFlag(it) else it }.takeIf { it.length == FLAG_W * FLAG_H } ?: ""
        hullIdx = prefs.getInteger("hullPaint", 0).coerceIn(0, HULL_PAINTS.size - 1)
        ownedHulls.clear(); ownedHulls += 0
        ownedHulls += prefs.getString("ownedHulls", "").split(",").mapNotNull { it.toIntOrNull() }
        trimIdx = prefs.getInteger("trim", -1).coerceIn(-1, FLAG_PALETTE.size - 1)
        flagShape = prefs.getInteger("flagShape", 0).coerceIn(0, FLAG_SHAPES.size - 1)
        flagSize = prefs.getInteger("flagSize", 0).coerceIn(0, FLAG_SIZES.size - 1)
        patScale = prefs.getInteger("patScale", 1).coerceIn(0, PAT_SCALES.size - 1)
        sailPat = prefs.getInteger("sailPat", 0).coerceIn(0, SAIL_PATTERNS.size - 1)
        sail2Idx = prefs.getInteger("sail2", -1).coerceIn(-1, FLAG_PALETTE.size - 1)
    }

    fun save(prefs: Preferences = this.prefs) {
        prefs.putInteger("coins", coins)
        prefs.putInteger("gems", gems)
        prefs.putString("owned", owned.joinToString(","))
        prefs.putString("ship", shipId)
        prefs.putString("up", up.entries.joinToString(";") { (k, v) -> k + ":" + v.joinToString(".") })
        prefs.putInteger("quest", questIdx)
        prefs.putInteger("qprog", qprog)
        prefs.putInteger("port", homePort)
        prefs.putInteger("kills", kills)
        prefs.putString("allies", allies.joinToString(","))
        prefs.putInteger("sail", sailIdx)
        prefs.putInteger("flag", flagIdx)
        prefs.putString("ownedSails", ownedSails.joinToString(","))
        prefs.putString("ownedFlags", ownedFlags.joinToString(","))
        prefs.putInteger("tutorial", tutorial)
        prefs.putLong("lastDaily", lastDaily)
        prefs.putInteger("streak", streak)
        prefs.putFloat("joySens", joySens)
        prefs.putBoolean("leftHanded", leftHanded)
        prefs.putInteger("gfx", gfx)
        prefs.putBoolean("autoGfx", autoGfx)
        prefs.putString("hero", heroId)
        systems.save(prefs)
        living.save(prefs)
        extras.save(prefs)
        guilds.save(prefs)
        prefs.putString("flagPix", flagPix)
        prefs.putInteger("sailPat", sailPat)
        prefs.putInteger("sail2", sail2Idx)
        prefs.putInteger("hullPaint", hullIdx)
        prefs.putString("ownedHulls", ownedHulls.joinToString(","))
        prefs.putInteger("trim", trimIdx)
        prefs.putInteger("flagShape", flagShape)
        prefs.putInteger("flagSize", flagSize)
        prefs.putInteger("patScale", patScale)
        if (::sfx.isInitialized) {
            prefs.putFloat("sfxVol", sfx.sfxVolume)
            prefs.putFloat("musicVol", sfx.musicVolume)
        }
        prefs.flush()
    }

    fun exportSave(): String = MapPrefs().also { save(it) }.encode()

    fun importSave(code: String): Boolean {
        val mp = MapPrefs.decode(code) ?: return false
        load(mp)
        save()
        for (s in ArrayList(ships)) removeShip(s)
        loot.clear(); balls.clear()
        player = null
        spawnPlayer()
        return true
    }

    fun resetProgress() {
        prefs.clear(); prefs.flush()
        load()
        for (s in ArrayList(ships)) removeShip(s)
        loot.clear()
        balls.clear()
        serpent = null
        player = null
        spawnPlayer()
        menus.close()
    }

    /** Запись в бортовом журнале в начале новой главы сюжета. */
    fun checkJournal() {
        val q = quest()
        if (q.intro != null && systems.shownIntro < questIdx && state == State.PLAY) {
            systems.shownIntro = questIdx
            save()
            menus.showJournal(q)
        }
    }

    /** Номер текущего дня по местному времени (без java.time — работает на старых Android). */
    private fun localDay(): Long {
        val now = System.currentTimeMillis()
        return (now + java.util.TimeZone.getDefault().getOffset(now)) / 86_400_000L
    }

    fun checkDaily() {
        val today = localDay()
        if (lastDaily == today) return
        streak = if (lastDaily == today - 1) streak + 1 else 1
        menus.showDaily((streak - 1) % DAILY.size)
    }

    fun claimDaily(idx: Int) {
        val r = DAILY[idx]
        coins += r.coins
        gems += r.gems
        lastDaily = localDay()
        sfx.play("treasure")
        save()
    }

    // ================= Корабли =================
    fun playerLook(): Look {
        val base = playerDef().look
        val flag = FLAG_COLORS[flagIdx].color.let { if (it < 0) base.flag else it }
        val hull = HULL_PAINTS[hullIdx].color
        return base.copy(sail = if (sailIdx == 0) base.sail else SAIL_COLORS[sailIdx].color, flag = flag,
            sailPat = sailPat, sail2 = if (sail2Idx >= 0) FLAG_PALETTE[sail2Idx] else -1, flagPix = flagPix,
            hull = if (hull >= 0) hull else base.hull, mast = if (hull >= 0) base.hull else -1,
            trim = if (trimIdx >= 0) FLAG_PALETTE[trimIdx] else base.trim,
            flagShape = flagShape, flagSize = flagSize, patScale = patScale)
    }

    fun shipModel(look: Look, up: IntArray? = null) = shipModels.getOrPut(look.toString() + "|" + (up?.joinToString(",") ?: "")) { Models.ship(look, up).also { models += it } }

    private fun createShip(name: String, stats: Stats, faction: Faction, look: Look, x: Float, z: Float, h: Float, boss: Boolean, up: IntArray? = null): Ship {
        val s = Ship(nextId++, name, stats, faction, look, x, z, h, ModelInstance(shipModel(look, up)), boss)
        ships += s
        return s
    }

    fun removeShip(s: Ship) { ships.remove(s) }

    fun playerDef() = PLAYER_SHIPS.firstOrNull { it.id == shipId } ?: PLAYER_SHIPS[0]

    /** Характеристики корабля игрока с учётом улучшений и навыков капитана. */
    fun playerStats(): Stats {
        val b = living.applyStats(shipStats(playerDef(), ensureUp(shipId)))
        val sk = systems
        val ex = extras
        return Stats(
            hp = Math.round(b.hp * (1 + 0.06f * sk.skill("carpenter")) * guilds.shipStatMul()),
            speed = b.speed * (1 + 0.03f * sk.skill("navigator") + ex.artSpeed() + guilds.speedBonus()),
            turn = b.turn * (1 + 0.05f * sk.skill("navigator")),
            cannons = b.cannons,
            dmg = b.dmg * sk.gunMul() * (1 + ex.artDmg()) * guilds.shipStatMul(),
            reload = b.reload * (1 - 0.05f * sk.skill("boatswain")) * ex.reloadMul() * (1 - ex.artReload()),
            range = b.range * (1 + ex.artRange()),
            mass = b.mass,
        )
    }

    fun spawnPlayer() {
        val p = ports[homePort]
        player?.let { removeShip(it) }
        val def = playerDef()
        player = createShip(def.name, playerStats(), Faction.PLAYER, playerLook(), p.rx, p.rz + 4f, PI.toFloat(), false, ensureUp(def.id))
            .also { it.inPort = true }
        camSnap = true
        spawnAllies()
    }

    fun rebuildPlayer() {
        val old = player ?: return
        val atSea = state == State.PLAY && !old.inPort
        removeShip(old)
        val def = playerDef()
        player = createShip(def.name, playerStats(), Faction.PLAYER, playerLook(), old.x, old.z, old.h, false, ensureUp(def.id)).also {
            it.inPort = old.inPort || !atSea
            // в море корабль сохраняет повреждения и ход
            if (atSea) { it.hp = it.maxHp * (old.hp / old.maxHp).coerceIn(0.05f, 1f); it.vx = old.vx; it.vz = old.vz; it.boostCd = old.boostCd }
        }
    }

    /** Пересобрать корабль в море (новый уровень корабля), не трогая состояние. */
    fun rebuildPlayerKeepState() = rebuildPlayer()

    /** Корабли сопровождения появляются рядом с игроком. */
    fun spawnAllies() {
        ships.removeAll { it.ally }
        val p = player ?: return
        for ((i, id) in allies.withIndex()) {
            val m = MERCS.first { it.id == id }
            val e = ENEMIES.getValue(m.enemyLook)
            val look = e.look.copy(sail = 0xf3ead3, flag = 0x1565c0, trim = 0x1565c0)
            val st = Stats(m.hp, m.speed, 1.6f, m.cannons, m.dmg, 2.0f, 46f, e.mass)
            val side = if (i == 0) -1f else 1f
            createShip(m.name, st, Faction.PLAYER, look, p.x + side * 16f, p.z + 22f, p.h, false).also {
                it.ally = true; it.allyIdx = i; it.mercId = id; it.ai = AiState.FOLLOW
            }
        }
    }

    fun spawnEnemy(type: String, x: Float, z: Float, name: String? = null, boss: Boolean? = null): Ship {
        val d = ENEMIES.getValue(type)
        return createShip(name ?: d.name, enemyStats(d), Faction.PIRATE, d.look, x, z, rand(-PI.toFloat(), PI.toFloat()), boss ?: d.boss).also {
            it.type = type; it.coinsMin = d.coinsMin; it.coinsMax = d.coinsMax; it.gems = d.gems
            it.homeX = x; it.homeZ = z
            it.leash = if (it.boss || boss == true) 450f else 300f
        }
    }

    fun spawnNeutral(type: String, x: Float, z: Float): Ship {
        val d = ENEMIES.getValue(type)
        return createShip(d.name, enemyStats(d), Faction.NEUTRAL, d.look, x, z, rand(-PI.toFloat(), PI.toFloat()), false).also { it.type = type }
    }

    fun isWaterPublic(x: Float, z: Float, margin: Float) = isWater(x, z, margin)
    fun dropLootPublic(x: Float, z: Float, c: Int, g: Float) = dropLoot(x, z, c, g)

    private fun isWater(x: Float, z: Float, margin: Float): Boolean {
        if (abs(x) > WORLD - 30 || abs(z) > WORLD - 30) return false
        return islands.none { hypot(it.x - x, it.z - z) < it.r + margin }
    }

    fun findSpawnPoint(minD: Float, maxD: Float): Pair<Float, Float>? {
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
        val zone = min(1f, hypot(x, z) / 1000f)
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
            if (state == State.CUTSCENE) { prologue.touch(x, y); return true }
            if (state != State.PLAY) return false
            if (hud.pauseHit(x, y)) { sfx.play("click"); openPause(); return true }
            if (dockAt != -1 && hud.dockHit(x, y)) { sfx.play("click"); enterDock(); return true }
            run { val (bx, by) = hud.boostPos(); if (hypot(x - bx, y - by) < 30) { tryBoost(); return true } }
            run {
                val (fx, fy) = hud.autoPos()
                if (hypot(x - fx, y - fy) < 26) {
                    systems.autoFire = !systems.autoFire
                    sfx.play("click")
                    toast(if (systems.autoFire) "Автоогонь включён" else "Автоогонь выключен — стреляйте прицелом", 1.4f)
                    save(); return true
                }
            }
            for (i in hud.visibleAbilities()) {
                val (bx, by) = hud.abilityPos(i)
                if (hypot(x - bx, y - by) < 28) { useAbility(i); return true }
            }
            val (ax, ay) = hud.aimPos()
            if (!aimActive && hypot(x - ax, y - ay) < 36) {
                aimActive = true; aimPointer = pointer; aimDx = 0f; aimDy = 0f
                updateAim(); return true
            }
            if (systems.boardTarget != null) {
                val (bx, by) = hud.boardPos()
                if (abs(x - bx) < 95 && abs(y - by) < 30) { systems.boardTarget?.let { systems.throwGrapples(it) }; return true }
            }
            if (extras.touch(x, y)) return true
            if (living.touch(x, y)) return true
            if (hud.minimapHit(x, y)) { sfx.play("click"); joyActive = false; menus.showMap(); return true }
            if (!joyActive) {
                joyActive = true; joyPointer = pointer
                joyOx = x; joyOy = y; joyMag = 0f
            }
            return true
        }

        override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
            if (aimActive && pointer == aimPointer) {
                val (ax, ay) = hud.aimPos()
                aimDx = screenX / scale - ax; aimDy = (hDp - screenY / scale) - ay
                updateAim(); return true
            }
            if (!joyActive || pointer != joyPointer) return false
            val dx = screenX / scale - joyOx
            val dy = (hDp - screenY / scale) - joyOy
            val d = hypot(dx, dy)
            joyMag = min(1f, d / joySens)
            if (d > 0) { joyJx = dx / d; joyJy = dy / d }
            return true
        }

        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (aimActive && pointer == aimPointer) { aimActive = false; aimPointer = -1; manualFire() }
            if (pointer == joyPointer) { joyActive = false; joyMag = 0f; joyPointer = -1 }
            return true
        }

        override fun keyDown(keycode: Int): Boolean {
            keys += keycode
            when (keycode) {
                Input.Keys.BACK, Input.Keys.ESCAPE -> onBack()
                Input.Keys.SPACE -> tryBoost()
                Input.Keys.NUM_1 -> useAbility(0)
                Input.Keys.NUM_2 -> useAbility(1)
                Input.Keys.NUM_3 -> useAbility(2)
                Input.Keys.NUM_4 -> useAbility(3)
                Input.Keys.NUM_5 -> useAbility(4)
                Input.Keys.NUM_6 -> useAbility(5)
                Input.Keys.B -> systems.boardTarget?.let { systems.throwGrapples(it) }
                Input.Keys.M -> if (state == State.PLAY) menus.showMap()
            }
            return true
        }

        override fun keyUp(keycode: Int): Boolean { keys -= keycode; return true }
    }

    private fun onBack() {
        when (state) {
            State.PORT -> closePort()
            State.PAUSE, State.DIALOG -> menus.close()
            State.PLAY -> openPause()
            else -> {}
        }
    }

    fun tryBoost() {
        val p = player ?: return
        if (state != State.PLAY || p.sinking || p.boostCd > 0) return
        p.boostT = 2.2f * (if (extras.hasArt(2)) 1.6f else 1f)
        p.boostCd = 7f
        sfx.play("boost")
        if (tutorial == 2) advanceTutorial()
    }

    fun useAbility(i: Int) {
        val p = player ?: return
        if (state != State.PLAY || p.sinking || abilityCd[i] > 0) return
        val a = ABILITIES[i]
        abilityCd[i] = a.cooldown * extras.abilityCdMul()
        when (a.id) {
            "fire" -> { ammo = Ammo.FIRE; ammoT = a.duration; sfx.play("fire"); toast("Огненные ядра заряжены!"); questEvent(QType.ABILITY, "", false) }
            "chain" -> { ammo = Ammo.CHAIN; ammoT = a.duration; sfx.play("chain"); toast("Цепные ядра заряжены!") }
            "repair" -> {
                p.repairT = a.duration; sfx.play("repair")
                // ремонт восстанавливает мачты
                if (p.brokenMasts > 0) { p.brokenMasts = 0; for (i in p.mastHp.indices) p.mastHp[i] = p.maxHp * 0.18f; for (n in p.sails) n.scale.set(1f, 1f, 1f) }
                p.holes.clear()
            }
            "mortar" -> fireMortar(p)
            "rockets" -> fireRockets(p)
            "mines" -> { systems.dropMines(p); sfx.play("splash") }
        }
        // особые ядра сразу перезаряжают пушки
        if (a.id == "fire" || a.id == "chain") { p.reload[0] = min(p.reload[0], 0.2f); p.reload[1] = min(p.reload[1], 0.2f) }
        if (tutorial == 3) advanceTutorial()
    }

    private fun nearestFoe(p: Ship, maxD: Float, cone: Boolean = false): Ship? =
        ships.filter { it.faction == Faction.PIRATE && !it.sinking && hypot(it.x - p.x, it.z - p.z) < maxD &&
            (!cone || ((it.x - p.x) * sin(p.h) - (it.z - p.z) * cos(p.h)) / hypot(it.x - p.x, it.z - p.z).coerceAtLeast(0.1f) > 0.7f) }
            .minByOrNull { hypot(it.x - p.x, it.z - p.z) }

    private fun fireMortar(p: Ship) {
        val t = nearestFoe(p, 110f)
        val (tx, tz) = when {
            t != null -> (t.x + t.vx * 2f) to (t.z + t.vz * 2f)
            else -> {
                val f = systems.fortresses.filter { it.alive }.minByOrNull { hypot(it.x - p.x, it.z - p.z) }
                if (f != null && hypot(f.x - p.x, f.z - p.z) < 130) f.x to f.z
                else (p.x + sin(p.h) * 60) to (p.z - cos(p.h) * 60)
            }
        }
        val tt = 2f
        val sy = p.y + p.deckTop + 1.5f
        balls += Ball(p, Faction.PLAYER, p.x, sy, p.z, (tx - p.x) / tt, (0f - sy + 0.5f * GRAVITY * tt * tt) / tt, (tz - p.z) / tt,
            70f * systems.gunMul(), Ammo.MORTAR, 0f)
        sfx.play("cannon"); shake = max(shake, 0.3f)
        p.rollVel += 0.3f
    }

    private fun fireRockets(p: Ship) {
        val t = nearestFoe(p, 130f, cone = true)
        for (i in 0 until 6) {
            val spread = (i - 2.5f) * 0.07f
            var dirX = sin(p.h + spread); var dirZ = -cos(p.h + spread)
            if (t != null) {
                val dx = t.x - p.x; val dz = t.z - p.z; val d = hypot(dx, dz)
                dirX = dx / d + spread * cos(p.h); dirZ = dz / d + spread * sin(p.h)
                val l = hypot(dirX, dirZ); dirX /= l; dirZ /= l
            }
            val sx = p.x + sin(p.h) * p.len * 0.45f; val sz = p.z - cos(p.h) * p.len * 0.45f
            balls += Ball(p, Faction.PLAYER, sx, p.y + p.deckTop + 1f, sz, dirX * 85f, 3f, dirZ * 85f, 18f * systems.gunMul(), Ammo.ROCKET, i * 0.08f)
        }
        sfx.play("fire")
    }

    /** Точка прицела: смещение пальца от кнопки прицела переводится в смещение по морю. */
    private fun updateAim() {
        val p = player ?: return
        var dx = aimDx * 0.9f; var dz = -aimDy * 0.9f
        val d = hypot(dx, dz)
        val range = p.stats.range
        if (d > range) { dx *= range / d; dz *= range / d }
        aimX = p.x + dx; aimZ = p.z + dz
    }

    private fun manualFire() {
        val p = player ?: return
        if (p.sinking || state != State.PLAY) return
        updateAim()
        val dx = aimX - p.x; val dz = aimZ - p.z; val d = hypot(dx, dz)
        if (d < 8) return
        val side = (dx * cos(p.h) + dz * sin(p.h)) / d
        if (abs(side) < 0.45f) { toast("Цель не по борту!", 1f); return }
        val si = if (side < 0) 0 else 1
        if (p.reload[si] > 0) { toast("Пушки заряжаются…", 1f); return }
        fireBroadside(p, si, aimX, aimZ, 0f, 0f, ammo, manual = true)
    }

    private fun controlPlayer() {
        val p = player ?: return
        if (p.sinking) return
        var jx = 0f; var jy = 0f; var mag = 0f
        if (joyActive && joyMag > 0.12f) { jx = joyJx; jy = -joyJy; mag = joyMag }
        val kx = (if (Input.Keys.D in keys || Input.Keys.RIGHT in keys) 1 else 0) - (if (Input.Keys.A in keys || Input.Keys.LEFT in keys) 1 else 0)
        val ky = (if (Input.Keys.S in keys || Input.Keys.DOWN in keys) 1 else 0) - (if (Input.Keys.W in keys || Input.Keys.UP in keys) 1 else 0)
        if (kx != 0 || ky != 0) { val d = hypot(kx.toFloat(), ky.toFloat()); jx = kx / d; jy = ky / d; mag = 1f }
        if (mag > 0) { p.desired = atan2(jx, -jy); p.throttle = mag } else p.throttle = 0f
    }

    // ================= Физика кораблей =================
    /** Насколько ветер помогает: по ветру ~1.15, против ветра ~0.7. */
    fun windFactor(h: Float) = (0.7f + 0.45f * (0.5f + 0.5f * cos(h - windA)) * min(1.25f, windS)) * (1f - 0.35f * living.calmK)

    private fun updateShip(s: Ship, dt: Float) {
        val st = s.stats
        if (s.boostT > 0) s.boostT -= dt
        if (s.boostCd > 0) s.boostCd -= dt
        if (s.slowT > 0) s.slowT -= dt
        if (s.burnT > 0 && !s.sinking) {
            s.burnT -= dt
            damage(s, s.burnDps * dt, null, false, silent = true)
            if (Random.nextFloat() < dt * 12) fx.spawn(fx.FIRE, s.x + rand(-2f, 2f), s.y + s.deckTop + 1, s.z + rand(-2f, 2f), 0f, rand(2f, 4f), 0f, rand(0.6f, 1.1f), 0.5f, Fx.Mode.PUFF)
        }
        if (s.repairT > 0 && !s.sinking) {
            s.repairT -= dt
            s.hp = min(s.maxHp, s.hp + s.maxHp * 0.12f * dt)
            if (Random.nextFloat() < dt * 8) fx.spawn(Color(0.5f, 1f, 0.6f, 0.9f), s.x + rand(-3f, 3f), s.y + s.deckTop + 2, s.z + rand(-3f, 3f), 0f, 4f, 0f, 0.6f, 0.8f)
        }

        val fxv = sin(s.h); val fzv = -cos(s.h); val rxv = cos(s.h); val rzv = sin(s.h)
        var vf = s.vx * fxv + s.vz * fzv
        var vr = s.vx * rxv + s.vz * rzv
        val massK = sqrt(st.mass)
        if (s.sinking) {
            s.sinkT += dt
            vf *= 1 - dt; vr *= 1 - dt; s.angVel *= 1 - dt
        } else {
            val diff = wrapAngle(s.desired - s.h)
            val rudder = 0.3f + 0.7f * min(1f, abs(vf) / (st.speed * 0.5f))
            val targetAng = clamp(diff * 2.2f, -st.turn, st.turn) * rudder
            s.angVel += (targetAng - s.angVel) * min(1f, dt * 2.8f / massK)
            val slow = (if (s.slowT > 0) 0.55f else 1f) * max(0.55f, 1f - 0.15f * s.brokenMasts)
            val maxSp = st.speed * windFactor(s.h) * slow * (if (s.boostT > 0) 1.7f else 1f) * (if (s.catchUp) 1.3f else 1f)
            val target = maxSp * s.throttle * (if (abs(diff) > 1.8f) 0.6f else 1f)
            val accel = if (target > vf) (if (s.boostT > 0) 3f else 1.1f / massK) else 0.45f
            vf += (target - vf) * min(1f, dt * accel)
        }
        // киль гасит боковой снос
        vr *= exp(-2.4f * dt)
        s.vx = fxv * vf + rxv * vr
        s.vz = fzv * vf + rzv * vr
        s.h = wrapAngle(s.h + s.angVel * dt)
        s.x += s.vx * dt
        s.z += s.vz * dt

        val lim = WORLD - 20
        if (abs(s.x) > lim) { s.x = Math.signum(s.x) * lim; s.vx *= -0.3f }
        if (abs(s.z) > lim) { s.z = Math.signum(s.z) * lim; s.vz *= -0.3f }

        // Острова: выталкивание и отскок
        for (isl in islands) {
            val dx = s.x - isl.x; val dz = s.z - isl.z
            val d = hypot(dx, dz)
            val mn = isl.r + s.radius * 0.55f
            if (d < mn && d > 0.01f) {
                val nx = dx / d; val nz = dz / d
                s.x += nx * (mn - d); s.z += nz * (mn - d)
                val vn = s.vx * nx + s.vz * nz
                if (vn < 0) {
                    s.vx -= 1.3f * vn * nx; s.vz -= 1.3f * vn * nz
                    if (s === player && -vn > 7 && !s.sinking) {
                        damage(s, (-vn - 7) * 3f, null, false)
                        fx.dust(s.x - nx * s.radius, 2f, s.z - nz * s.radius)
                    }
                }
            }
        }

        if (!s.sinking && now - s.lastHit > 6 && s.hp < s.maxHp) s.hp = min(s.maxHp, s.hp + s.maxHp * 0.02f * dt)

        // Качка: высота волн под носом, кормой и бортами
        val half = s.len * 0.45f; val wd = s.width * 0.5f
        val hBow = waveHeight(s.x + fxv * half, s.z + fzv * half, now)
        val hStern = waveHeight(s.x - fxv * half, s.z - fzv * half, now)
        val hRight = waveHeight(s.x + rxv * wd, s.z + rzv * wd, now)
        val hLeft = waveHeight(s.x - rxv * wd, s.z - rzv * wd, now)
        val waveY = (hBow + hStern + hRight + hLeft) * 0.25f - 0.1f
        val wavePitch = atan2(hBow - hStern, s.len * 0.9f) * 0.8f
        val waveRoll = atan2(hRight - hLeft, s.width) * 0.6f
        // крен в повороте и от ветра
        val windSide = sin(windA - s.h) * 0.05f * min(1f, abs(vf) / 8f)
        val heel = -s.angVel * vf * 0.012f + windSide
        s.rollVel += ((heel - s.roll) * 9f - s.rollVel * 3.5f) * dt
        s.roll += s.rollVel * dt
        s.y = waveY - (if (s.sinking) s.sinkT * s.sinkT * 1.6f else 0f)
        val roll = s.roll + waveRoll + (if (s.sinking) min(0.9f, s.sinkT * 0.35f) else 0f)
        s.pitch = wavePitch + (if (s.sinking) min(0.35f, s.sinkT * 0.12f) else 0f)
        s.inst.transform.setToTranslation(s.x, s.y, s.z)
            .rotate(Vector3.Y, -s.h * MathUtils.radiansToDegrees)
            .rotate(Vector3.X, s.pitch * MathUtils.radiansToDegrees)
            .rotate(Vector3.Z, roll * MathUtils.radiansToDegrees)

        // Паруса надуваются по ветру и от скорости
        if (s.sails.isNotEmpty()) {
            val fill = (0.5f + 0.5f * cos(s.h - windA)) * min(1f, abs(vf) / (st.speed * 0.4f) + 0.25f)
            for ((i, node) in s.sails.withIndex()) {
                if (i / 2 < s.mastHp.size && s.mastHp[i / 2] <= 0) { node.scale.set(0.01f, 0.01f, 0.01f); continue }
                val flutter = sin(now * 7f + i * 1.7f + s.id) * 0.06f * (1.2f - fill)
                node.scale.set(1f, 1f, clamp(0.25f + 0.85f * fill + flutter, 0.15f, 1.15f))
            }
            s.inst.calculateTransforms()
        }

        // Пенный след и брызги у носа
        s.wakeT -= dt
        val spd = abs(vf)
        if (spd > 3 && s.wakeT <= 0 && !s.sinking) {
            s.wakeT = 0.06f
            val bx = s.x - fxv * s.len * 0.48f
            val bz = s.z - fzv * s.len * 0.48f
            fx.spawn(fx.FOAM, bx + rand(-0.6f, 0.6f), 0.7f, bz + rand(-0.6f, 0.6f), 0f, 0f, 0f, s.width * 0.32f, 1.6f, Fx.Mode.FOAM, flat = true)
            val px = s.x + fxv * s.len * 0.45f
            val pz = s.z + fzv * s.len * 0.45f
            fx.spawn(fx.FOAM, px + rand(-1f, 1f), 0.7f, pz + rand(-1f, 1f), 0f, 0f, 0f, s.width * 0.2f, 0.8f, Fx.Mode.FOAM, flat = true)
        }
        s.sprayT -= dt
        if (spd > 10 && s.sprayT <= 0 && !s.sinking && highQuality) {
            s.sprayT = 0.09f
            val px = s.x + fxv * s.len * 0.5f
            val pz = s.z + fzv * s.len * 0.5f
            for (sd in intArrayOf(-1, 1)) {
                fx.spawn(fx.SPLASH, px + rxv * sd * 1.2f, s.y + 0.8f, pz + rzv * sd * 1.2f,
                    rxv * sd * rand(3f, 6f) + fxv * spd * 0.5f, rand(3f, 6f), rzv * sd * rand(3f, 6f) + fzv * spd * 0.5f, rand(0.25f, 0.45f), 0.5f, Fx.Mode.SHRINK, 22f)
            }
        }
        // Дым от повреждений и пожар при потоплении
        s.smokeT -= dt
        if (s.smokeT <= 0 && (s.hp < s.maxHp * 0.35f || s.sinking)) {
            s.smokeT = if (s.sinking) 0.05f else 0.18f
            val y = s.y + s.width * 0.6f
            fx.spawn(fx.DARK, s.x + rand(-2f, 2f), y, s.z + rand(-2f, 2f), rand(-0.5f, 0.5f), rand(3f, 5f), rand(-0.5f, 0.5f), rand(1f, 1.8f), 1.4f, Fx.Mode.PUFF)
            if (s.sinking || Random.nextFloat() < 0.4f) {
                fx.spawn(fx.FIRE, s.x + rand(-2f, 2f), y, s.z + rand(-2f, 2f), 0f, rand(2f, 4f), 0f, rand(0.6f, 1.1f), 0.45f, Fx.Mode.PUFF)
            }
        }
    }

    /** Столкновения кораблей: упругий импульс с учётом масс, урон при таране. */
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
                val ma = a.stats.mass; val mb = b.stats.mass
                val ov = mn - d
                a.x -= nx * ov * (mb / (ma + mb)); a.z -= nz * ov * (mb / (ma + mb))
                b.x += nx * ov * (ma / (ma + mb)); b.z += nz * ov * (ma / (ma + mb))
                val rel = (a.vx - b.vx) * nx + (a.vz - b.vz) * nz
                if (rel <= 0) continue
                val jImp = 1.25f * rel / (1 / ma + 1 / mb)
                a.vx -= jImp / ma * nx; a.vz -= jImp / ma * nz
                b.vx += jImp / mb * nx; b.vz += jImp / mb * nz
                // закрутка от удара не по центру
                val fax = sin(a.h); val faz = -cos(a.h); val fbx = sin(b.h); val fbz = -cos(b.h)
                b.angVel += (fax * nz - faz * nx) * rel * 0.02f / mb
                a.angVel -= (fbx * nz - fbz * nx) * rel * 0.02f / ma
                if (rel < 3) continue
                if ((a.ramT[b.id] ?: 0f) > now) continue
                a.ramT[b.id] = now + 0.8f
                if (a.faction == b.faction || a.faction == Faction.NEUTRAL || b.faction == Faction.NEUTRAL) continue
                val base = rel * 3.2f
                val ramA = fax * nx + faz * nz > 0.5f
                val ramB = -(fbx * nx + fbz * nz) > 0.5f
                if (ramA) damage(b, base * ma * (if (a.boostT > 0) 2f else 1f) / sqrt(mb), a, true)
                if (ramB) damage(a, base * mb * (if (b.boostT > 0) 2f else 1f) / sqrt(ma), b, true)
                if (ramA) damage(a, base * 0.2f, null, false)
                if (ramB) damage(b, base * 0.2f, null, false)
                if (!ramA && !ramB) { damage(a, base * 0.35f, b, true); damage(b, base * 0.35f, a, true) }
                fx.explosion((a.x + b.x) / 2, 2f, (a.z + b.z) / 2, 0.7f)
                fx.splash((a.x + b.x) / 2, (a.z + b.z) / 2, 1.3f)
                sfx.play("hit", volumeAt(a.x, a.z))
                if (a === player || b === player) { shake = max(shake, 0.5f); systems.vibrate(80) }
                a.rollVel += rel * 0.04f; b.rollVel -= rel * 0.04f
            }
        }
    }

    // ================= Бой: баллистика ядер =================
    fun volumeAt(x: Float, z: Float): Float {
        val p = player ?: return 0.5f
        return clamp(1 - hypot(p.x - x, p.z - z) / 260f, 0.05f, 1f)
    }

    private fun fireBroadside(s: Ship, sideIdx: Int, tx0: Float, tz0: Float, tvx: Float, tvz: Float, kind: Ammo, manual: Boolean = false) {
        val st = s.stats
        val side = if (sideIdx == 0) -1f else 1f
        s.reload[sideIdx] = st.reload
        s.lastFire = now
        val fxv = sin(s.h); val fzv = -cos(s.h)
        val rx = cos(s.h) * side; val rz = sin(s.h) * side
        val n = st.cannons
        val muzzleY = s.y + s.deckTop + 0.3f
        var acc = if (s.faction == Faction.PLAYER) 1f else 1.6f
        if (s === player) acc *= (1 - 0.12f * systems.skill("aim")) * (if (manual) 0.6f else 1f)
        val dmgMul = if (manual) 1.25f else 1f
        for (i in 0 until n) {
            val off = (if (n == 1) 0f else i.toFloat() / (n - 1) - 0.5f) * s.len * 0.55f
            val sx = s.x + rx * s.width * 0.6f + fxv * off
            val sz = s.z + rz * s.width * 0.6f + fzv * off
            val d = hypot(tx0 - sx, tz0 - sz)
            val tt = clamp(d / BALL_SPEED, 0.3f, 1.5f)
            val spread = (1.2f + d * 0.035f) * acc
            val tx = tx0 + tvx * tt + rand(-spread, spread)
            val tz = tz0 + tvz * tt + rand(-spread, spread)
            val ty = 1.2f + rand(-0.4f, 1.2f)
            val vx = (tx - sx) / tt
            val vz = (tz - sz) / tt
            val vy = (ty - muzzleY + 0.5f * GRAVITY * tt * tt) / tt
            balls += Ball(s, s.faction, sx, muzzleY, sz, vx, vy, vz, st.dmg * dmgMul * rand(0.85f, 1.15f), kind, i * 0.07f + rand(0f, 0.05f))
        }
        // отдача: корабль кренится и чуть сдвигается от залпа
        s.rollVel -= side * 0.25f * n / (4f * s.stats.mass)
        s.vx -= rx * 0.4f * n / s.stats.mass
        s.vz -= rz * 0.4f * n / s.stats.mass
        sfx.play(if (kind == Ammo.FIRE) "fire" else "cannon", volumeAt(s.x, s.z))
        if (s === player && tutorial == 1) advanceTutorial()
    }

    /** Одиночный выстрел из точки (крепости и т. п.). */
    fun launchBall(owner: Ship?, faction: Faction, sx: Float, sy: Float, sz: Float, tx0: Float, tz0: Float, tvx: Float, tvz: Float,
                   dmg: Float, kind: Ammo, delay: Float, spreadMul: Float) {
        val d = hypot(tx0 - sx, tz0 - sz)
        val tt = clamp(d / BALL_SPEED, 0.3f, 1.8f)
        val spread = (1.2f + d * 0.035f) * spreadMul
        val tx = tx0 + tvx * tt + rand(-spread, spread)
        val tz = tz0 + tvz * tt + rand(-spread, spread)
        val ty = 1.5f
        balls += Ball(owner, faction, sx, sy, sz, (tx - sx) / tt, (ty - sy + 0.5f * GRAVITY * tt * tt) / tt, (tz - sz) / tt, dmg, kind, delay)
    }

    private fun updateFiring(s: Ship, dt: Float) {
        s.reload[0] -= dt; s.reload[1] -= dt
        if (s.sinking) return
        val p = player
        if (s.faction == Faction.NEUTRAL && s.type != "navy") return
        if (s.surrendered) return
        val pirateMe = guilds.isPirate
        if (s === p && !systems.autoFire) return
        val mode = when {
            s.faction == Faction.PLAYER || s.faction == Faction.NEUTRAL -> 1
            s.ai == AiState.ATTACK -> 2
            else -> 0
        }
        val sp = serpent
        for (si in 0..1) {
            if (s.reload[si] > 0) continue
            val side = if (si == 0) -1f else 1f
            val rx = cos(s.h) * side; val rz = sin(s.h) * side
            var best: Ship? = null; var bd = Float.MAX_VALUE
            if (mode != 0) for (o in ships) {
                if (o.sinking) continue
                // по кораблю, который берём на абордаж, не стреляем
                if (o === systems.grappleTarget || o.surrendered) continue
                // автоогонь не добивает ослабленного врага вплотную — даём взять его на абордаж
                if (s === p && systems.boardable(o) && hypot(o.x - s.x, o.z - s.z) < 45f) continue
                // своим автоогнём игрок-пират бьёт и спровоцированных мирных; спровоцированный патруль бьёт игрока
                if (mode == 1 && o.faction != Faction.PIRATE && !(s === p && pirateMe && o.provoked && o.faction == Faction.NEUTRAL) &&
                    !(s.faction == Faction.NEUTRAL && s.provoked && o === p)) continue
                if (mode == 2 && !((o.faction == Faction.PLAYER || o.type == "merchant" || (o.type == "navy" && o.faction == Faction.NEUTRAL)) && !(o === p && p.safe))) continue
                val dx = o.x - s.x; val dz = o.z - s.z; val d = hypot(dx, dz)
                if (d > s.stats.range || d < 2) continue
                if ((dx * rx + dz * rz) / d < 0.42f) continue
                if (d < bd) { bd = d; best = o }
            }
            val kind = when { s === p -> ammo; s.trait == "fire" -> Ammo.FIRE; s.trait == "chain" -> Ammo.CHAIN; else -> Ammo.NORMAL }
            if (best != null) { fireBroadside(s, si, best.x, best.z, best.vx, best.vz, kind); continue }
            // по змею стреляют игрок и союзники
            if (s.faction == Faction.PLAYER && sp != null && !sp.dead && !sp.diving) {
                val dx = sp.x - s.x; val dz = sp.z - s.z; val d = hypot(dx, dz)
                if (d < s.stats.range && d > 2 && (dx * rx + dz * rz) / d > 0.42f) {
                    fireBroadside(s, si, sp.x, sp.z, sin(sp.h) * 12f, -cos(sp.h) * 12f, kind)
                }
            }
        }
    }

    private fun updateBalls(dt: Float) {
        val it = balls.iterator()
        while (it.hasNext()) {
            val b = it.next()
            if (b.delay > 0) { b.delay -= dt; continue }
            if (!b.fired) {
                b.fired = true
                val l = hypot(b.vx, b.vz).coerceAtLeast(0.01f)
                fx.muzzle(b.x, b.y, b.z, b.vx / l, b.vz / l)
            }
            var remove = false
            // два подшага для быстрых ядер
            for (sub in 0..1) {
                val h = dt / 2
                b.vy -= (if (b.ammo == Ammo.ROCKET) 5f else GRAVITY) * h
                b.x += b.vx * h; b.y += b.vy * h; b.z += b.vz * h
                b.age += h
                if (checkBallHit(b)) { if (b.ammo == Ammo.MORTAR) mortarBlast(b); remove = true; break }
                if (b.faction != Faction.NEUTRAL && systems.hitStatic(b.x, b.y, b.z, b.dmg * (if (b.faction == Faction.PLAYER) 1f else 0f)) && b.faction == Faction.PLAYER) {
                    if (b.ammo == Ammo.MORTAR) mortarBlast(b); remove = true; break
                }
                val wy = waveHeight(b.x, b.z, now)
                if (b.ammo == Ammo.MORTAR && b.y <= wy + 0.5f) { mortarBlast(b); remove = true; break }
                if (b.y <= wy) {
                    val hs = hypot(b.vx, b.vz)
                    if (!b.bounced && -b.vy < hs * 0.3f && hs > 35f) {
                        // рикошет от воды
                        b.bounced = true
                        b.y = wy + 0.05f
                        b.vy = -b.vy * 0.5f + 2f
                        b.vx *= 0.65f; b.vz *= 0.65f
                        fx.splash(b.x, b.z, 0.5f)
                    } else {
                        fx.splash(b.x, b.z)
                        if (Random.nextFloat() < 0.3f) sfx.play("splash", volumeAt(b.x, b.z))
                        remove = true; break
                    }
                }
                val isl = islands.firstOrNull { i -> b.y < 7f && hypot(b.x - i.x, b.z - i.z) < i.r * 0.97f }
                if (isl != null) { if (b.ammo == Ammo.MORTAR) mortarBlast(b) else fx.dust(b.x, max(b.y, 2f), b.z); remove = true; break }
            }
            if (b.age > 5f) remove = true
            if (remove) { it.remove(); continue }
            b.trailT -= dt
            if (b.trailT <= 0) {
                b.trailT = if (highQuality) 0.03f else 0.07f
                when (b.ammo) {
                    Ammo.FIRE, Ammo.ROCKET -> fx.spawn(fx.FIRE, b.x, b.y, b.z, 0f, 1f, 0f, 0.5f, 0.35f, Fx.Mode.PUFF)
                    Ammo.MORTAR -> fx.spawn(fx.DARK, b.x, b.y, b.z, 0f, 0.5f, 0f, 0.7f, 0.8f, Fx.Mode.PUFF)
                    else -> fx.spawn(fx.TRAIL, b.x, b.y, b.z, 0f, 0.5f, 0f, 0.35f, 0.5f, Fx.Mode.PUFF)
                }
            }
        }
    }

    private val sailHitColor = Color(0.95f, 0.93f, 0.85f, 1f)

    /** Взрыв мортирного снаряда: урон по площади. */
    private fun mortarBlast(b: Ball) {
        fx.explosion(b.x, max(1f, b.y), b.z, 2f)
        fx.splash(b.x, b.z, 2f)
        sfx.play("hit", volumeAt(b.x, b.z)); shake = max(shake, 0.3f)
        for (s in ships) {
            if (s.faction != Faction.PIRATE || s.sinking) continue
            val d = hypot(s.x - b.x, s.z - b.z)
            if (d < 10 + s.radius * 0.5f) damage(s, b.dmg * (1 - d / 20f), b.owner, false)
        }
        systems.hitStatic(b.x, max(b.y, 3f), b.z, b.dmg, 6f)
        serpent?.let { sp -> if (!sp.dead && !sp.diving && hypot(sp.x - b.x, sp.z - b.z) < 12) serpentDamage(b.dmg) }
    }

    /** Попадание ядра в корпус или паруса чужого корабля (в системе координат корабля). */
    private fun checkBallHit(b: Ball): Boolean {
        for (s in ships) {
            if (s.faction == b.faction || s.sinking) continue
            // мирные суда: попадают только ядра игрока-пирата; мирные стреляют по игроку, только если он напал
            if (s.faction == Faction.NEUTRAL && b.faction == Faction.PLAYER && !(guilds.isPirate && (b.owner === player || s.provoked))) continue
            if (s.faction == Faction.PLAYER && b.faction == Faction.NEUTRAL && b.owner?.provoked != true) continue
            val dx = b.x - s.x; val dz = b.z - s.z
            if (dx * dx + dz * dz > (s.len * 0.6f) * (s.len * 0.6f)) continue
            val fxv = sin(s.h); val fzv = -cos(s.h)
            val lf = dx * fxv + dz * fzv
            val lr = dx * cos(s.h) + dz * sin(s.h)
            if (abs(lf) > s.len * 0.52f) continue
            val half = Models.halfWidthAt(s.len, s.width, -lf)
            val ly = b.y - s.y
            val hull = abs(lr) < half + 0.4f && ly < s.deckTop + 1.3f && ly > -1.5f
            val sail = !hull && abs(lr) < s.width * 0.95f && ly > s.deckTop + 1f && ly < s.deckTop + s.mastH
            if (!hull && !sail) continue
            val mul = when {
                hull && b.ammo == Ammo.CHAIN -> 0.6f
                hull -> 1f
                b.ammo == Ammo.CHAIN -> 1f
                else -> 0.35f
            }
            if (hull && s.holes.size < 8 && Random.nextFloat() < 0.5f) s.holes += Vector3(lf, clamp(ly, -0.3f, s.deckTop - 0.3f), if (lr > 0) half + 0.1f else -half - 0.1f)
            if (sail && s.mastHp.isNotEmpty()) {
                val mi = (0 until s.mastHp.size).minByOrNull { abs(Models.mastZ(s.look, it) + lf) } ?: 0
                if (s.mastHp[mi] > 0) {
                    s.mastHp[mi] -= b.dmg * (if (b.ammo == Ammo.CHAIN) 3f else 1f)
                    if (s.mastHp[mi] <= 0) {
                        s.brokenMasts++
                        for (k in 0..1) s.sails.getOrNull(mi * 2 + k)?.scale?.set(0.01f, 0.01f, 0.01f)
                        repeat(8) { fx.spawn(fx.WOOD, b.x, b.y, b.z, rand(-6f, 6f), rand(2f, 8f), rand(-6f, 6f), rand(0.3f, 0.6f), 1.4f, Fx.Mode.SHRINK, 20f, sq = true) }
                        if (s === player) toast("Мачта сбита!", 1.2f) else floatText(s.x, s.y + 10, s.z, "Мачта сбита!", rgb(0xffc93c), ui.mid)
                    }
                }
            }
            if (hull) fx.explosion(b.x, b.y, b.z, 0.6f) else repeat(5) {
                fx.spawn(sailHitColor, b.x, b.y, b.z, rand(-5f, 5f), rand(0f, 4f), rand(-5f, 5f), rand(0.25f, 0.5f), 1f, Fx.Mode.SHRINK, 8f, sq = true)
            }
            damage(s, b.dmg * mul, b.owner, false)
            when (b.ammo) {
                Ammo.FIRE, Ammo.ROCKET -> { s.burnT = 5f; s.burnDps = max(s.burnDps, b.dmg * 0.45f) }
                Ammo.CHAIN -> s.slowT = 6f
                else -> {}
            }
            // удар толкает корабль
            val k = 0.05f / s.stats.mass
            s.vx += b.vx * k; s.vz += b.vz * k
            s.rollVel += (if (lr > 0) -1 else 1) * 0.15f / s.stats.mass
            if (Random.nextFloat() < 0.4f) sfx.play("hit", volumeAt(b.x, b.z) * 0.7f)
            return true
        }
        val sp = serpent
        if (sp != null && b.faction == Faction.PLAYER && !sp.dead && !sp.diving) {
            for (seg in sp.segPos) {
                if (abs(b.x - seg.x) < 4f && abs(b.z - seg.z) < 4f && abs(b.y - seg.y) < 4.2f) {
                    fx.explosion(b.x, b.y, b.z, 0.5f)
                    serpentDamage(b.dmg * (if (b.ammo == Ammo.FIRE) 1.3f else 1f) * extras.monsterMul())
                    return true
                }
            }
        }
        return false
    }

    private val fireBallColor = Color(1f, 0.6f, 0.15f, 1f)

    private fun drawBalls() {
        for (b in balls) {
            if (b.delay > 0) continue
            if (b.ammo == Ammo.FIRE || b.ammo == Ammo.ROCKET) fx.glow(b.x, b.y, b.z, 2.2f, fireBallColor)
            if (b.ammo == Ammo.MORTAR) fx.frame(ui.softDisc, b.x, b.y, b.z, 2.2f, 2.2f, Color.BLACK) else fx.ball(b.x, b.y, b.z)
            if (b.ammo == Ammo.CHAIN) fx.ball(b.x + 0.9f, b.y, b.z + 0.9f)
        }
    }

    fun damage(s: Ship, amount: Float, src: Ship?, ram: Boolean, silent: Boolean = false) {
        if (s.sinking || state == State.CUTSCENE) return
        var amount = if (s.trait == "armored") amount * 0.6f else amount
        // корабль на крюках не тонет до абордажной схватки (горение, ядра в полёте)
        if (s === systems.grappleTarget && s.hp - amount < 1f) { s.hp = 1f; return }
        if (s === player) amount *= (1f - extras.armor())
        if (s === player && amount > s.maxHp * 0.12f) extras.addMorale(-2f)
        if (s.surrendered && src === player && !s.sinking) {
            s.surrendered = false
            living.addRep(2, -3); extras.addMorale(-3f)
            toast("Вы стреляете по сдавшимся! Пираты будут биться до конца", 2f)
        }
        s.hp -= amount
        if (s.faction == Faction.PIRATE && s.hp > 0 && (src === player || src?.ally == true)) extras.maybeSurrender(s)
        if (s.faction == Faction.NEUTRAL && src === player && !s.provoked) {
            s.provoked = true
            if (s.type == "navy") { living.addRep(1, -8); toast("Вы атаковали патруль флота!", 1.8f) }
            else { living.addRep(0, -3); living.addRep(1, -2); living.addRep(2, 1) }
        }
        s.lastHit = now
        if (s.faction == Faction.PLAYER || src?.faction == Faction.PLAYER) combatT = 8f
        if (!silent) {
            val a = max(1, amount.roundToInt())
            floatText(s.x, s.y + 4 + s.width * 0.5f, s.z, "-$a", if (s === player) rgb(0xff8a80) else Color.WHITE, ui.mid)
        }
        if (s === player && !silent) { flash = 0.25f; shake = max(shake, 0.25f); systems.vibrate(30) }
        if (s.hp <= 0) { s.hp = 0f; sink(s, src, ram) }
    }

    private fun sink(s: Ship, src: Ship?, ram: Boolean) {
        s.sinkT = 0.001f
        living.onSink(s, src)
        fx.explosion(s.x, 3f, s.z, 1.5f)
        sfx.play("sink", volumeAt(s.x, s.z))
        if (s.faction == Faction.PIRATE) {
            dropLoot(s.x, s.z, (Random.nextInt(s.coinsMin, s.coinsMax + 1) * systems.luckMul() * living.lootMul()).roundToInt(), s.gems * (1f + living.gemBonus()))
            if (src == null || src.faction == Faction.PLAYER) {
                extras.addMorale(3f)
                guilds.addShipXp(XP_REWARD[s.type] ?: 30)
                if (extras.hasArt(9) && Random.nextFloat() < 0.2f) { gems++; floatText(s.x, 12f, s.z, "+1 кристалл", rgb(0xce93d8), ui.small) }
                kills++
                systems.addStat("kills")
                systems.gainXp(XP_REWARD[s.type] ?: 30, s.x, s.z)
                questEvent(QType.KILL, s.type, ram && src === player)
                if (ram && src === player) { toast("Таран!"); systems.addStat("rams") }
                when (s.type) {
                    "flagship" -> { systems.addStat("flagships"); questEvent(QType.FLAGSHIP, "", false); toast("Флагман потоплен!", 2f) }
                    "admiral" -> {
                        systems.addStat("admirals"); questEvent(QType.ADMIRAL, "", false)
                        toast("ПОБЕДА!\nАдмирал Гарпун повержен!", 3.5f)
                    }
                }
            }
        } else if (s.faction == Faction.NEUTRAL) {
            if (systems.protect === s) toast("Купец потоплен…", 1.6f)
            if (src === player) {
                kills++
                systems.addStat("kills")
                guilds.addShipXp(25)
                // добыча с мирного судна
                val c = when (s.type) { "merchant" -> Random.nextInt(180, 360); "navy" -> Random.nextInt(200, 320); else -> Random.nextInt(30, 70) }
                dropLoot(s.x, s.z, (c * systems.luckMul() * living.lootMul()).roundToInt(), if (s.type == "navy") 0.6f else 0.2f)
                if (s.type == "merchant") {
                    val gi = Random.nextInt(GOODS.size)
                    val got = systems.addCargo(gi, Random.nextInt(3, 9))
                    if (got > 0) floatText(s.x, 10f, s.z, "+$got ${GOODS[gi].name}", rgb(0xffe082), ui.small)
                }
            }
        } else if (s.ally) {
            extras.addMorale(-8f)
            allies.remove(s.mercId)
            toast("${s.name} потоплен!")
            save()
        } else {
            deathTimer = 2f
            extras.onPlayerDeath(s)
            systems.vibrate(300)
        }
    }

    // ================= Добыча и сокровища =================
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
            l.inst.transform.setToTranslation(l.x, waveHeight(l.x, l.z, now) + 0.9f + sin(l.t * 3) * 0.3f, l.z)
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

    private fun updateTreasure(dt: Float) {
        val p = player ?: return
        val t = treasure
        if (t == null) {
            treasureTimer -= dt
            if (treasureTimer <= 0) {
                val wild = islands.filter { it.port < 0 && hypot(it.x - p.x, it.z - p.z) in 150f..800f }
                if (wild.isNotEmpty()) {
                    val isl = wild.random()
                    val a = rand(0f, (2 * PI).toFloat())
                    val x = isl.x + cos(a) * isl.r * 0.88f
                    val z = isl.z + sin(a) * isl.r * 0.88f
                    treasure = Treasure(isl, x, z, ModelInstance(treasureModel).also {
                        it.transform.setToTranslation(x, 1.2f, z).rotate(Vector3.Y, -a * MathUtils.radiansToDegrees)
                    })
                    toast("На карте отмечено сокровище!")
                } else treasureTimer = 5f
            }
            return
        }
        if (!p.sinking && hypot(p.x - t.x, p.z - t.z) < p.radius * 0.55f + 14f) {
            val c = (Random.nextInt(250, 600 + questIdx * 20) * systems.luckMul()).roundToInt()
            systems.addStat("treasures")
            systems.gainXp(50, t.x, t.z)
            val g = Random.nextInt(1, 4)
            coins += c; gems += g
            sfx.play("treasure")
            toast("Сокровище!\n+$c монет  +$g кристалла", 2.4f)
            treasure = null
            treasureTimer = rand(60f, 110f)
            questEvent(QType.TREASURE, "", false)
            save()
        }
    }

    // ================= Морской змей =================
    fun spawnSerpent(x: Float, z: Float) {
        val head = ModelInstance(serpentHeadModel)
        val body = (0 until 12).map { ModelInstance(serpentBodyModel) }
        serpent = Serpent(x, z, head, body)
        sfx.play("roar")
        toast("Морской змей!", 2f)
    }

    private fun serpentDamage(amount: Float) {
        val sp = serpent ?: return
        if (sp.dead) return
        sp.hp -= amount
        sp.lastHit = now
        combatT = 8f
        floatText(sp.x, 6f, sp.z, "-${amount.roundToInt()}", Color.WHITE, ui.mid)
        if (sp.hp <= 0) {
            sp.deadT = 0.001f
            extras.grantArtifact(extras.monsterArts, 0.6f)
            fx.explosion(sp.x, 2f, sp.z, 2f)
            fx.splash(sp.x, sp.z, 2.5f)
            sfx.play("roar")
            dropLoot(sp.x, sp.z, 1500, 8f)
            questEvent(QType.SERPENT, "", false)
            systems.addStat("serpents")
            systems.gainXp(300, sp.x, sp.z)
            toast("Морской змей повержен!", 2.4f)
            serpentTimer = rand(240f, 400f)
        }
    }

    private fun updateSerpent(dt: Float) {
        val p = player ?: return
        val sp = serpent
        if (sp == null) {
            serpentTimer -= dt
            val q = quest()
            val zone = hypot(p.x, p.z) / WORLD
            if ((q.type == QType.SERPENT || (serpentTimer <= 0 && zone > 0.45f)) && !p.safe) {
                findSpawnPoint(200f, 300f)?.let { spawnSerpent(it.first, it.second) }
                serpentTimer = rand(240f, 400f)
            }
            return
        }
        if (sp.dead) {
            sp.deadT += dt
            sp.depth = min(12f, sp.deadT * 4)
            if (sp.deadT > 3.5f) { serpent = null; return }
        } else {
            sp.stateT -= dt
            sp.biteCd -= dt
            val dx = p.x - sp.x; val dz = p.z - sp.z; val d = hypot(dx, dz)
            if (d > 700) { serpent = null; return }
            if (sp.diving) {
                sp.depth = min(9f, sp.depth + dt * 8)
                if (sp.stateT <= 0) {
                    sp.diving = false; sp.stateT = rand(10f, 15f)
                    fx.splash(sp.x, sp.z, 2.2f); sfx.play("roar", volumeAt(sp.x, sp.z))
                }
            } else {
                sp.depth = max(0f, sp.depth - dt * 8)
                if (sp.stateT <= 0) { sp.diving = true; sp.stateT = 4f; fx.splash(sp.x, sp.z, 1.8f) }
            }
            val home = hypot(sp.x - sp.homeX, sp.z - sp.homeZ)
            if (home > 380f) sp.returning = true else if (home < 120f) sp.returning = false
            val target = when {
                sp.returning -> atan2(sp.homeX - sp.x, -(sp.homeZ - sp.z))
                p.safe || p.sinking -> atan2(sp.x - p.x, -(sp.z - p.z))
                else -> atan2(dx, -dz)
            }
            val diff = wrapAngle(target - sp.h)
            sp.h = wrapAngle(sp.h + clamp(diff, -1.6f * dt, 1.6f * dt))
            val speed = if (sp.diving) 20f else 13f
            sp.x += sin(sp.h) * speed * dt
            sp.z -= cos(sp.h) * speed * dt
            for (isl in islands) {
                val ix = sp.x - isl.x; val iz = sp.z - isl.z; val id = hypot(ix, iz)
                if (id < isl.r + 4) { sp.x = isl.x + ix / id * (isl.r + 4); sp.z = isl.z + iz / id * (isl.r + 4) }
            }
            // укус
            if (!sp.diving && d < p.radius + 8 && sp.biteCd <= 0 && !p.sinking && !p.safe) {
                sp.biteCd = 1.6f
                damage(p, 28f + questIdx, null, false)
                shake = max(shake, 0.6f)
                fx.splash(p.x, p.z, 1.5f)
                sfx.play("roar", 0.6f)
            }
            if (!sp.diving && Random.nextFloat() < dt * 6) fx.spawn(fx.FOAM, sp.x, 0.7f, sp.z, 0f, 0f, 0f, 2.5f, 1.2f, Fx.Mode.FOAM, flat = true)
        }
        // тело тянется по следу головы
        sp.trail.add(0, Vector3(sp.x, 0f, sp.z))
        while (sp.trail.size > 160) sp.trail.removeAt(sp.trail.size - 1)
        var acc = 0f
        var ti = 0
        for (i in sp.segPos.indices) {
            val want = (i + 1) * 4.8f
            while (ti < sp.trail.size - 1 && acc < want) {
                acc += sp.trail[ti].dst(sp.trail[ti + 1]); ti++
            }
            val tp = sp.trail[min(ti, sp.trail.size - 1)]
            val wave = sin(now * 3f - i * 0.7f) * 1.2f
            sp.segPos[i].set(tp.x, wave * 1.4f + 0.8f - sp.depth * (1f + i * 0.04f), tp.z)
        }
        val hy = 3.4f + sin(now * 3f) * 0.8f - sp.depth
        sp.head.transform.setToTranslation(sp.x, hy, sp.z).rotate(Vector3.Y, -sp.h * MathUtils.radiansToDegrees).rotate(Vector3.X, 12f).scale(1.5f, 1.5f, 1.5f)
        for ((i, inst) in sp.body.withIndex()) {
            val s = (1.05f - i * 0.055f) * 1.5f
            inst.transform.setToTranslation(sp.segPos[i]).scale(s, s, s)
        }
    }

    // ================= ИИ =================
    private fun updatePirateAI(s: Ship, dt: Float) {
        if (s.sinking) return
        val p = player ?: return
        s.aiT -= dt
        if (s.surrendered) { s.throttle = 0.12f; s.ai = AiState.WANDER; s.target = null; return }
        // возврат после брошенной погони
        if (s.giveUpT > 0) {
            s.giveUpT -= dt
            s.ai = AiState.WANDER; s.target = null
            val dh = hypot(s.homeX - s.x, s.homeZ - s.z)
            if (dh > 40) { s.desired = atan2(s.homeX - s.x, -(s.homeZ - s.z)); s.throttle = 0.7f } else wander(s)
            avoidPortsAndIslands(s, true)
            return
        }
        if (s.ai == AiState.ATTACK && hypot(s.x - s.homeX, s.z - s.homeZ) > s.leash) {
            s.giveUpT = 14f
            if (s.target === p) floatText(s.x, s.y + 10f, s.z, "Отстал", rgb(0xb0bec5), ui.small)
            s.ai = AiState.WANDER; s.target = null
            return
        }
        val aggro = if (s.hunter) 3000f else (if (s.boss) 280f else 175f) * (1f - 0.55f * living.fogK)
        val friendly = (living.pirateFriendly && !s.boss && !s.hunter && s.type != "navy" || s.ghost && extras.hasArt(1)) && s.lastHit < now - 15
        // цель: ближайший из игрока и его союзников
        var tgt: Ship? = null; var td = Float.MAX_VALUE
        for (o in ships) {
            if (o.sinking) continue
            if (o.faction != Faction.PLAYER && o.type != "merchant" && !(o.type == "navy" && o.faction == Faction.NEUTRAL)) continue
            if (o === p && p.safe) continue
            if (friendly && o.faction == Faction.PLAYER) continue
            val d = hypot(o.x - s.x, o.z - s.z) * (if (o.type == "merchant") 0.8f else 1f)
            if (d < td) { td = d; tgt = o }
        }
        if (tgt != null && (td < aggro || (s.lastHit > now - 10 && td < aggro * 2))) { s.ai = AiState.ATTACK; s.target = tgt }
        else if (s.ai == AiState.ATTACK && (tgt == null || td > aggro * 1.8f)) s.ai = AiState.WANDER
        val t = s.target
        val lead = s.leader
        if (s.ai == AiState.ATTACK && t != null && !t.sinking && s.trait == "rammer" && hypot(t.x - s.x, t.z - s.z) < 120) {
            s.desired = atan2(t.x - s.x, -(t.z - s.z)); s.throttle = 1f
        } else if (s.ai == AiState.ATTACK && t != null && !t.sinking) attackMove(s, t, dt)
        else if (lead != null && !lead.sinking && ships.contains(lead)) {
            // строй флотилии: держатся за флагманом
            val off = (s.id % 3 - 1) * 20f
            val tx = lead.x - sin(lead.h) * 26 + cos(lead.h) * off
            val tz = lead.z + cos(lead.h) * 26 + sin(lead.h) * off
            val d = hypot(tx - s.x, tz - s.z)
            s.desired = if (d > 6) atan2(tx - s.x, -(tz - s.z)) else lead.h
            s.throttle = clamp(d / 30f, 0.3f, 1f)
            if (lead.ai == AiState.ATTACK) { s.ai = AiState.ATTACK; s.target = lead.target }
        } else wander(s)
        avoidPortsAndIslands(s, true)
    }

    fun attackMove(s: Ship, t: Ship, dt: Float) {
        val dx = t.x - s.x; val dz = t.z - s.z; val d = hypot(dx, dz)
        val toT = atan2(dx, -dz)
        val r = s.stats.range * 0.7f
        if (d > r * 1.3f) { s.desired = toT; s.throttle = 1f }
        else { s.desired = toT + s.side * (PI / 2).toFloat() * (if (d < r * 0.6f) 1.3f else 1f); s.throttle = 0.8f }
        if (Random.nextFloat() < dt * 0.05f) s.side = -s.side
    }

    /** Есть ли остров на отрезке от (x0, z0) до (x1, z1). */
    fun pathBlocked(x0: Float, z0: Float, x1: Float, z1: Float, margin: Float): Boolean {
        val dx = x1 - x0; val dz = z1 - z0
        val len2 = dx * dx + dz * dz
        for (isl in islands) {
            var t = if (len2 > 0) ((isl.x - x0) * dx + (isl.z - z0) * dz) / len2 else 0f
            t = clamp(t, 0f, 1f)
            if (hypot(x0 + dx * t - isl.x, z0 + dz * t - isl.z) < isl.r + margin) return true
        }
        return false
    }

    private fun wander(s: Ship) {
        if (s.aiT <= 0 || hypot(s.wx - s.x, s.wz - s.z) < 25) {
            s.aiT = rand(10f, 18f)
            // точка на чистой воде, к которой можно проплыть без острова на пути
            var best = false
            repeat(14) {
                if (best) return@repeat
                val tx = clamp(s.x + rand(-260f, 260f), -WORLD + 80, WORLD - 80)
                val tz = clamp(s.z + rand(-260f, 260f), -WORLD + 80, WORLD - 80)
                if (isWater(tx, tz, 30f) && !pathBlocked(s.x, s.z, tx, tz, s.radius + 6f)) { s.wx = tx; s.wz = tz; best = true }
            }
            if (!best) { s.wx = clamp(s.x + rand(-200f, 200f), -WORLD + 80, WORLD - 80); s.wz = clamp(s.z + rand(-200f, 200f), -WORLD + 80, WORLD - 80) }
        }
        s.desired = atan2(s.wx - s.x, -(s.wz - s.z))
        s.throttle = 0.5f
    }

    /**
     * Обход островов: к желаемому курсу добавляется отталкивание от берега
     * и скольжение вдоль него в сторону цели. Если корабль застрял — отходит от берега.
     */
    fun steerAround(s: Ship, dt: Float) {
        // застревание
        if (s.throttle > 0.3f && abs(s.speed) < 1.2f) s.stuckT += dt else s.stuckT = max(0f, s.stuckT - dt * 2)
        if (s.stuckT > 2.2f) {
            s.stuckT = 0f
            val isl = islands.minByOrNull { hypot(it.x - s.x, it.z - s.z) - it.r }
            if (isl != null) {
                val away = atan2(s.x - isl.x, -(s.z - isl.z))
                s.escA = away + rand(-0.5f, 0.5f)
                s.escapeT = 3.5f
                s.wx = clamp(s.x + sin(s.escA) * 160f, -WORLD + 80, WORLD - 80)
                s.wz = clamp(s.z - cos(s.escA) * 160f, -WORLD + 80, WORLD - 80)
                s.aiT = 10f
            }
        }
        if (s.escapeT > 0) {
            s.escapeT -= dt
            s.desired = s.escA
            s.throttle = max(s.throttle, 0.8f)
            return
        }
        val dx0 = sin(s.desired); val dz0 = -cos(s.desired)
        var ax = dx0; var az = dz0
        val look = 50f + abs(s.speed) * 2.5f
        for (isl in islands) {
            val ox = s.x - isl.x; val oz = s.z - isl.z
            val d = hypot(ox, oz)
            val clear = isl.r + s.radius + 6f
            val range = clear + look
            if (d > range || d < 0.01f) continue
            val nx = ox / d; val nz = oz / d
            val toward = -(nx * dx0 + nz * dz0)
            if (toward < -0.25f && d > clear + 4f) continue
            if (s.type == "fisher" && d > clear + 4f) continue
            val w = clamp((range - d) / (range - clear), 0f, 1.6f)
            ax += nx * w * 1.5f; az += nz * w * 1.5f
            val tx = -nz; val tz = nx
            val side = if (tx * dx0 + tz * dz0 >= 0) 1f else -1f
            ax += tx * side * w * 1.3f; az += tz * side * w * 1.3f
        }
        // край мира
        val edge = WORLD - 70f
        if (s.x > edge) ax -= (s.x - edge) / 40f; if (s.x < -edge) ax += (-edge - s.x) / 40f
        if (s.z > edge) az -= (s.z - edge) / 40f; if (s.z < -edge) az += (-edge - s.z) / 40f
        s.desired = atan2(ax, -az)
    }

    private fun avoidPortsAndIslands(s: Ship, avoidPorts: Boolean) {
        if (avoidPorts) for (port in ports) {
            if (hypot(s.x - port.def.x, s.z - port.def.z) < port.def.r + 120) {
                s.desired = atan2(s.x - port.def.x, -(s.z - port.def.z))
                s.ai = AiState.WANDER
                s.aiT = 0f
            }
        }
        steerAround(s, Gdx.graphics.deltaTime.coerceAtMost(0.05f))
    }

    // След корабля игрока: союзники идут точно по нему кильватерной колонной
    private val trail = ArrayDeque<FloatArray>()

    private fun recordTrail() {
        val p = player ?: return
        val last = trail.lastOrNull()
        if (last != null && hypot(p.x - last[0], p.z - last[1]) > 60f) trail.clear()
        val l2 = trail.lastOrNull()
        if (l2 == null || hypot(p.x - l2[0], p.z - l2[1]) > 2f) {
            trail.addLast(floatArrayOf(p.x, p.z, p.h))
            if (trail.size > 450) trail.removeFirst()
        }
    }

    /** Точка на следе игрока на расстоянии [dist] позади (x, z, курс) или null, если след короче. */
    private fun trailPoint(dist: Float): FloatArray? {
        val p = player ?: return null
        var acc = 0f
        var px = p.x; var pz = p.z
        for (i in trail.indices.reversed()) {
            val q = trail[i]
            val seg = hypot(q[0] - px, q[1] - pz)
            if (acc + seg >= dist) {
                val k = if (seg > 0) (dist - acc) / seg else 0f
                return floatArrayOf(px + (q[0] - px) * k, pz + (q[1] - pz) * k, q[2])
            }
            acc += seg; px = q[0]; pz = q[1]
        }
        return null
    }

    /** Союзники слушаются приказов: строй (колонна за флагманом), атака, отход. */
    private fun updateAllyAI(s: Ship, dt: Float) {
        if (s.sinking) return
        val p = player ?: return
        val order = living.order
        s.catchUp = false
        if (order == 1) {
            var foe: Ship? = null; var fd = Float.MAX_VALUE
            for (o in ships) {
                if (o.faction != Faction.PIRATE || o.sinking) continue
                val d = hypot(o.x - p.x, o.z - p.z)
                if (d < 320 && d < fd) { fd = d; foe = o }
            }
            if (foe != null && hypot(s.x - p.x, s.z - p.z) < 450) {
                s.ai = AiState.ATTACK
                attackMove(s, foe, dt)
                avoidPortsAndIslands(s, false)
                return
            }
        }
        s.ai = AiState.FOLLOW
        // место в колонне: каждый следующий — дальше по следу флагмана
        val spacing = if (order == 2) 18f else 26f
        var rank = 0
        for (o in ships) { if (o === s) break; if (o.ally && !o.sinking) rank++ }
        val dist = p.len * 0.5f + 14f + rank * spacing + s.len * 0.5f
        val tp = trailPoint(dist)
        val tx: Float; val tz: Float
        if (tp != null) { tx = tp[0]; tz = tp[1] }
        else { tx = p.x - sin(p.h) * dist; tz = p.z + cos(p.h) * dist }
        val d = hypot(tx - s.x, tz - s.z)
        if (d > 7) {
            s.desired = atan2(tx - s.x, -(tz - s.z))
            s.throttle = clamp(d / 12f, 0.3f, 1f)
        } else {
            s.desired = tp?.get(2) ?: p.h
            s.throttle = clamp(abs(p.speed) / max(1f, s.stats.speed) + (d - 3f) / 30f, 0f, 1f)
        }
        // не налетать на флагман
        if (hypot(p.x - s.x, p.z - s.z) < p.radius + s.radius + 4f) s.throttle *= 0.3f
        s.catchUp = d > 12f
        if (d > 400) { s.x = tx; s.z = tz; s.vx = p.vx; s.vz = p.vz; s.h = tp?.get(2) ?: p.h }
        if (d > 25f) steerAround(s, dt)
    }

    // ================= Задания =================
    fun quest() = questAt(questIdx)

    fun questEvent(kind: QType, enemy: String, ram: Boolean, amount: Int = 1, port: Int = -1) {
        val q = quest()
        var inc = 0
        when (kind) {
            QType.KILL -> {
                if (q.type == QType.KILL && q.enemy == enemy) inc = 1
                if (q.type == QType.RAM && q.enemy == enemy && ram) inc = 1
            }
            QType.COLLECT -> if (q.type == QType.COLLECT) inc = amount
            QType.REACH -> if (q.type == QType.REACH && q.port == port) inc = 1
            QType.ZONE -> if (q.type == QType.ZONE && q.region?.name == enemy) inc = 1
            QType.DELIVER -> if (q.type == QType.DELIVER) inc = amount
            QType.LEVEL -> if (q.type == QType.LEVEL && systems.level >= q.n) inc = q.n
            else -> if (q.type == kind) inc = 1
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
            systems.gainXp(100 + questIdx * 5, player?.x ?: 0f, player?.z ?: 0f)
            // новое задание может уже быть выполнено (уровень, регион)
            val nq = quest()
            if (nq.type == QType.LEVEL && systems.level >= nq.n) { qprog = 0; questEvent(QType.LEVEL, "", false) }
            if (nq.type == QType.ZONE && systems.region == nq.region) questEvent(QType.ZONE, nq.region.name, false)
            checkJournal()
        }
        save()
    }

    fun questTarget(): Pair<Float, Float>? {
        val q = quest()
        val p = player ?: return null
        return when (q.type) {
            QType.KILL, QType.RAM -> ships.filter { it.faction == Faction.PIRATE && !it.sinking && it.type == q.enemy }
                .minByOrNull { hypot(it.x - p.x, it.z - p.z) }?.let { it.x to it.z }
            QType.REACH -> ports[q.port].let { it.rx to it.rz }
            QType.UPGRADE, QType.BUY, QType.HIRE -> ports.minByOrNull { hypot(it.rx - p.x, it.rz - p.z) }!!.let { it.rx to it.rz }
            QType.TREASURE -> treasure?.let { it.x to it.z }
            QType.SERPENT -> serpent?.takeIf { !it.dead }?.let { it.x to it.z }
            QType.ABILITY -> ships.filter { it.faction == Faction.PIRATE && !it.sinking }.minByOrNull { hypot(it.x - p.x, it.z - p.z) }?.let { it.x to it.z }
            QType.BOARD -> ships.filter { it.faction == Faction.PIRATE && !it.sinking && it.type != "admiral" }.minByOrNull { hypot(it.x - p.x, it.z - p.z) }?.let { it.x to it.z }
            QType.DELIVER -> ports[q.port].let { it.rx to it.rz }
            QType.FORTRESS -> systems.fortresses.filter { it.alive }.minByOrNull { hypot(it.x - p.x, it.z - p.z) }?.let { it.x to it.z }
            QType.KRAKEN -> systems.kraken?.takeIf { !it.dead }?.let { it.x to it.z } ?: (0f to 1450f)
            QType.FLAGSHIP -> ships.firstOrNull { it.type == "flagship" && !it.sinking }?.let { it.x to it.z }
            QType.ZONE -> when (q.region) { Region.ICE -> 0f to -1400f; Region.FIRE -> 1400f to 0f; Region.REEF -> -1400f to 0f; Region.ABYSS -> 0f to 1400f; else -> null }
            QType.WRECK -> systems.wrecks.minByOrNull { hypot(it.x - p.x, it.z - p.z) }?.let { it.x to it.z }
            QType.PROTECT -> (systems.protect ?: ships.filter { it.type == "merchant" && !it.sinking }.minByOrNull { hypot(it.x - p.x, it.z - p.z) })?.let { it.x to it.z }
            QType.ADMIRAL -> ships.firstOrNull { it.type == "admiral" && !it.sinking }?.let { it.x to it.z } ?: (0f to 1450f)
            QType.LEVEL -> null
            QType.COLLECT -> loot.filter { hypot(it.x - p.x, it.z - p.z) < 500 }.minByOrNull { hypot(it.x - p.x, it.z - p.z) }?.let { it.x to it.z }
                ?: ships.filter { it.faction == Faction.PIRATE && !it.sinking }.minByOrNull { hypot(it.x - p.x, it.z - p.z) }?.let { it.x to it.z }
        }
    }

    // ================= Обучение =================
    val tutorialTexts = listOf(
        "Коснитесь экрана и тяните палец: корабль поплывёт туда",
        "Пушки стреляют сами, когда враг сбоку. Подплывите к пирату!",
        "Кнопка с парусами — рывок. Протараньте врага на скорости!",
        "Кнопки сбоку — способности: огненные и цепные ядра, ремонт",
        "Мини-карта: жёлтая точка — цель, красные — пираты, ✕ — сокровище",
        "Заплывите в белый круг у порта и нажмите «⚓ В гавань»",
    )

    fun advanceTutorial() {
        if (tutorial >= tutorialTexts.size) return
        tutorial++
        tutorialT = 0f
        save()
    }

    private fun updateTutorial(dt: Float) {
        if (tutorial >= tutorialTexts.size) return
        tutorialT += dt
        val p = player ?: return
        when (tutorial) {
            0 -> if (hypot(p.x - startX, p.z - startZ) > 30) advanceTutorial()
            3 -> if (tutorialT > 14) advanceTutorial()
            4 -> if (tutorialT > 8) advanceTutorial()
        }
    }

    // ================= Появление пиратов =================
    private fun manageSpawns(dt: Float) {
        spawnTimer -= dt
        val p = player ?: return
        if (spawnTimer > 0) return
        spawnTimer = 1f
        ships.removeAll { it.faction == Faction.PIRATE && !it.hunter && hypot(it.x - p.x, it.z - p.z) > 650 }
        val pirates = ships.filter { it.faction == Faction.PIRATE && !it.sinking && !it.hunter && !it.ghost && it.type != "navy" }
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
        dockAt = -1
        for (port in ports) {
            if (hypot(p.x - port.def.x, p.z - port.def.z) < port.def.r + 110) p.safe = true
            val d = hypot(p.x - port.rx, p.z - port.rz)
            if (!p.sinking && d < 14) dockAt = port.i
            if (p.inPort && port.i == homePort && d > 18) p.inPort = false
        }
    }

    /** Кнопка «В гавань»: заход в порт или на свою базу. */
    fun enterDock() {
        val d = dockAt
        dockAt = -1
        if (d >= 0) openPort(d) else if (d == -2) living.enterBase()
    }

    fun openPort(i: Int) {
        val p = player ?: return
        joyActive = false
        p.vx = 0f; p.vz = 0f; p.angVel = 0f
        p.inPort = true
        p.hp = p.maxHp
        p.burnT = 0f
        homePort = i
        extras.addMorale(5f)
        questEvent(QType.REACH, "", false, port = i)
        if (tutorial == 5) advanceTutorial()
        save()
        portView.enter(i)
        menus.showPort(i)
    }

    fun closePort() {
        menus.close()
        portView.leave()
        val p = player
        if (p != null) for (s in ships) if (s.ally && hypot(s.x - p.x, s.z - p.z) > 200) { s.x = p.x; s.z = p.z + 25 }
        if (ships.count { it.ally } != allies.size) spawnAllies()
        save()
    }

    fun openPause() {
        if (state != State.PLAY) return
        joyActive = false
        save()
        menus.showPause()
    }

    // ================= Погода и время суток =================
    private fun updateWorld(dt: Float) {
        // сутки: 6 минут, ночь проходит быстрее
        val sunH = sin(dayTime * 2 * PI).toFloat()
        dayTime = (dayTime + dt / 360f * (if (sunH < 0) 1.8f else 1f)) % 1f
        // шторм
        stormTimer -= dt
        if (stormTimer <= 0) {
            if (stormTarget > 0) { stormTarget = 0f; stormTimer = rand(180f, 320f) * (if (extras.hasArt(3)) 0.55f else 1f) }
            else { stormTarget = 1f; stormTimer = rand(60f, 100f); toast("Надвигается шторм!", 2f) }
        }
        stormLevel += (stormTarget - stormLevel) * min(1f, dt * 0.12f)
        waveAmp = 1f + stormLevel * 1.6f
        windA = wrapAngle(windA + sin(now * 0.05f) * 0.01f * dt + (if (stormLevel > 0.3f) 0.02f * dt else 0f))
        windS = (1f + stormLevel * 0.6f) * (1f - 0.85f * living.calmK)
        if (stormLevel > 0.4f) {
            lightningTimer -= dt
            if (lightningTimer <= 0) {
                lightningTimer = rand(4f, 9f)
                lightning = 0.25f
                sfx.play("thunder", 0.7f * stormLevel)
            }
            fx.rain(camPos.x, camPos.z - 70, ((if (highQuality) 9 else 4) * stormLevel).roundToInt())
        }
        if (lightning > 0) lightning -= dt
    }

    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
    private val cSun = Color(); private val cMoon = Color(0.35f, 0.42f, 0.65f, 1f); private val cLight = Color()
    private val cFog = Color(); private val cFogDay = Color()

    /** Свет, туман и цвет неба по времени суток и погоде. */
    private fun applyLighting() {
        val ang = dayTime * 2 * PI
        val sunH = sin(ang).toFloat()
        val day = MathUtils.clamp((sunH + 0.15f) / 0.4f, 0f, 1f)
        val dusk = MathUtils.clamp(1f - abs(sunH - 0.08f) / 0.3f, 0f, 1f)
        val storm = stormLevel
        sky.day = day * (1 - storm * 0.35f)
        sky.storm = storm
        // солнце не поднимается выше ~40°, чтобы тени были длинными и заметными сверху
        val sx = cos(ang).toFloat() * 0.85f + 0.25f
        val sy = clamp(if (sunH > -0.05f) sunH else -sunH, 0.32f, 0.62f)
        val sz = 0.55f
        val sl = sqrt(sx * sx + sy * sy + sz * sz)
        sky.sunDir[0] = sx / sl; sky.sunDir[1] = sy / sl; sky.sunDir[2] = sz / sl
        cSun.set(1f, 0.95f, 0.85f, 1f).lerp(1f, 0.55f, 0.3f, 1f, dusk)
        cLight.set(cMoon).lerp(cSun, day)
        sky.sunCol[0] = cLight.r; sky.sunCol[1] = cLight.g; sky.sunCol[2] = cLight.b
        cFogDay.set(0.56f, 0.77f, 0.91f, 1f).lerp(0.95f, 0.66f, 0.5f, 1f, dusk * 0.7f)
        cFog.set(0.05f, 0.09f, 0.18f, 1f).lerp(cFogDay, day).lerp(0.36f, 0.42f, 0.48f, 1f, storm * 0.75f)
        cFog.lerp(systems.regionFog.r * (0.25f + 0.75f * day), systems.regionFog.g * (0.25f + 0.75f * day), systems.regionFog.b * (0.3f + 0.7f * day), 1f, systems.regionFogMix)
        cFog.lerp(0.62f + 0.2f * day, 0.66f + 0.2f * day, 0.7f + 0.2f * day, 1f, living.fogK * 0.85f)
        if (lightning > 0) cFog.lerp(Color.WHITE, 0.5f)
        sky.fog[0] = cFog.r; sky.fog[1] = cFog.g; sky.fog[2] = cFog.b
        sky.fogNear = mix(mix(300f, 170f, storm) * systems.regionFogScale, 105f, living.fogK)
        sky.fogFar = mix(mix(560f, 360f, storm) * (0.5f + 0.5f * systems.regionFogScale), 250f, living.fogK)
        for (i in 0..2) { sky.deep[i] = systems.waterDeep[i]; sky.mid[i] = systems.waterMid[i] }
        fogAttr.color.set(cFog.r, cFog.g, cFog.b, 1f)
        val amb = mix(0.24f, 0.6f, day) * (1 - storm * 0.3f) + (if (lightning > 0) 0.5f else 0f)
        ambient.color.set(amb * (0.85f + 0.15f * day), amb * (0.9f + 0.1f * day), amb * (1.15f - 0.05f * day), 1f)
        val k = (0.35f + 0.65f * day) * (1 - storm * 0.45f)
        sunLight.set(cLight.r * 0.75f * k, cLight.g * 0.72f * k, cLight.b * 0.65f * k, -sky.sunDir[0], -sky.sunDir[1], -sky.sunDir[2])
        // свет неба сверху и отражённый от моря снизу
        fillLight.set(0.14f * day + 0.04f, 0.17f * day + 0.05f, 0.24f * day + 0.08f, 0.3f, -1f, -0.2f)
        seaBounce.set(0.03f * day, 0.09f * day, 0.11f * day, -0.2f, 1f, 0.1f)
        // цветокоррекция по времени суток и погоде
        if (::post.isInitialized) {
            post.warmth = dusk * 1.2f + 0.15f * day
            post.saturation = 1.14f - storm * 0.35f - (1 - day) * 0.15f
            post.contrast = 1.07f + day * 0.03f
            post.exposure = 1.0f + (1 - day) * 0.12f
            post.bloom = 0.45f + (1 - day) * 0.35f
            post.vignette = 0.32f + storm * 0.2f
        }
    }

    private fun spawnGulls() {
        gulls.clear()
        for (port in ports.take(3)) repeat(3) {
            gulls += Gull(port.def.x, port.def.z, rand(30f, 70f), rand(0f, 6.28f), rand(18f, 30f), rand(0.3f, 0.6f) * (if (Random.nextBoolean()) 1 else -1), rand(0f, 6f))
        }
        repeat(4) { gulls += Gull(0f, 0f, rand(20f, 45f), rand(0f, 6.28f), rand(20f, 32f), rand(0.35f, 0.6f), rand(0f, 6f)) }
    }

    // ================= Предыстория =================
    private val cutBackup = ArrayList<Ship>()
    private val cutSaved = FloatArray(4)

    /** Запустить сценки предыстории ([first] — новая игра: после неё выбор фракции). */
    fun startPrologue(first: Boolean, splash: Boolean = false) {
        stage.clear()
        joyActive = false; aimActive = false
        cutBackup.clear(); cutBackup.addAll(ships)
        ships.clear(); balls.clear()
        cutSaved[0] = dayTime; cutSaved[1] = stormLevel; cutSaved[2] = stormTarget; cutSaved[3] = living.fogK
        state = State.CUTSCENE
        if (splash) prologue.startSplash() else prologue.start(first)
    }

    fun endPrologue(first: Boolean) {
        ships.clear(); ships.addAll(cutBackup); cutBackup.clear()
        balls.clear()
        dayTime = cutSaved[0]; stormLevel = cutSaved[1]; stormTarget = cutSaved[2]; living.fogK = cutSaved[3]
        waveAmp = 1f + stormLevel * 1.6f
        lightning = 0f; shake = 0f; flash = 0f; combatT = 0f
        camSnap = true
        state = State.PLAY
        if (first) menus.showGuildSelect() else checkJournal()
    }

    /** Корабль-актёр: не тонет от ядер, тонет только по сценарию. */
    fun cutShip(name: String, look: Look, faction: Faction, x: Float, z: Float, h: Float, speed: Float, cannons: Int): Ship {
        val st = Stats(99999, speed, 1.4f, cannons, 10f, 2f, 60f, max(0.6f, look.len * look.len / 170f))
        return createShip(name, st, faction, look, x, z, h, false)
    }

    fun cutSink(s: Ship) {
        s.sinkT = 0.001f
        fx.explosion(s.x, 3f, s.z, 1.5f)
        sfx.play("sink")
        shake = max(shake, 0.4f)
    }

    fun cutClear() { ships.clear(); balls.clear() }

    private fun cutsceneUpdate(dt: Float) {
        now += dt
        prologue.update(dt)
        if (state != State.CUTSCENE) return
        for (s in ships) updateShip(s, dt)
        shipCollisions()
        updateBalls(dt)
        ships.removeAll { it.sinkT > 4.5f }
        waveAmp = 1f + stormLevel * 1.6f
        windS = 1f + stormLevel * 0.6f
        if (stormLevel > 0.4f) {
            lightningTimer -= dt
            if (lightningTimer <= 0) {
                lightningTimer = rand(4f, 8f)
                lightning = 0.25f
                sfx.play("thunder", 0.7f * stormLevel)
            }
            fx.rain(camPos.x, camPos.z - 70, ((if (highQuality) 9 else 4) * stormLevel).roundToInt())
        }
        if (lightning > 0) lightning -= dt
    }

    // ================= Обновление =================
    private fun update(dt: Float) {
        now += dt
        controlPlayer()
        recordTrail()
        shipsTmp.clear(); shipsTmp.addAll(ships)
        for (s in shipsTmp) when {
            s.faction == Faction.PIRATE -> updatePirateAI(s, dt)
            s.faction == Faction.NEUTRAL -> systems.neutralAI(s, dt)
            s.ally -> updateAllyAI(s, dt)
        }
        for (s in ships) updateShip(s, dt)
        shipCollisions()
        for (s in ships) updateFiring(s, dt)
        updateBalls(dt)
        updateLoot(dt)
        updateTreasure(dt)
        updateSerpent(dt)
        manageSpawns(dt)
        updatePorts()
        updateWorld(dt)
        updateTutorial(dt)
        systems.update(dt)
        living.update(dt)
        extras.update(dt)
        guilds.update(dt)
        if (aimActive) updateAim()
        for (i in abilityCd.indices) if (abilityCd[i] > 0) abilityCd[i] -= dt
        if (ammoT > 0) { ammoT -= dt; if (ammoT <= 0) ammo = Ammo.NORMAL }
        if (combatT > 0) combatT -= dt
        ships.removeAll { it !== player && it.sinkT > 3.5f }
        // чайки кружат над портами; часть летает за игроком
        val p = player
        for ((i, g) in gulls.withIndex()) {
            g.a += g.speed * dt
            if (i >= gulls.size - 4 && p != null) { g.cx += (p.x - g.cx) * dt * 0.3f; g.cz += (p.z - 30 - g.cz) * dt * 0.3f }
        }
        if (deathTimer > 0) {
            deathTimer -= dt
            if (deathTimer <= 0) menus.showDeath()
        }
    }

    private fun updateCamera(dt: Float) {
        val p = player ?: return
        if (state == State.CUTSCENE) {
            val m = prologue.camera(dt, cam)
            camPos.set(m.x, m.y, m.z + 80f)
            return
        }
        if (portView.active) {
            portView.update(dt)
            portView.applyCamera(cam)
            camPos.set(cam.position)
            return
        }
        val k = (p.len / 12f).pow(0.45f) * (if (wDp / hDp > 1) 0.8f else 1f)
        val tx = p.x + p.vx * 0.6f
        val tz = p.z + p.vz * 0.6f
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

    // Динамическое разрешение и автоснижение графики на слабых телефонах
    private var fpsAcc = 0f
    private var fpsFrames = 0
    private var scaleIdx = 0
    private var slowChecks = 0
    private var maxFpsSeen = 0f
    private fun scaleSteps(): FloatArray = when (gfx) {
        // нижний порог разрешения выше: картинка на слабых телефонах не «мылится»
        0 -> floatArrayOf(0.62f, 0.72f, 0.82f)
        1 -> floatArrayOf(0.7f, 0.8f, 0.9f, 1f)
        2 -> floatArrayOf(0.7f, 0.85f, 1f)
        else -> floatArrayOf(0.85f, 1f, 1.12f, 1.25f)
    }
    private fun shadowSize() = when (gfx) { 0, 1 -> 1024; 2 -> 2048; else -> min(4096, maxTex()) }

    private fun adaptQuality(dt: Float) {
        if (!::post.isInitialized || state == State.LOADING) return
        fpsAcc += dt; fpsFrames++
        if (fpsAcc < 2.5f) return
        val fps = fpsFrames / fpsAcc
        fpsAcc = 0f; fpsFrames = 0
        val steps = scaleSteps()
        maxFpsSeen = max(maxFpsSeen, fps)
        // телефон с ограничением 30 кадров (энергосбережение) — не путать с нехваткой мощности
        val capped = maxFpsSeen < 34f
        val lowT = if (capped) 24f else 40f
        val highT = if (capped) 29f else 56f
        if (fps < lowT && scaleIdx > 0) scaleIdx--
        else if (fps > highT && scaleIdx < steps.size - 1) scaleIdx++
        // телефон не справляется даже на минимальном разрешении — снижаем уровень графики
        // графику снижаем, только если телефон стабильно не тянет (не из-за одного подтормаживания)
        val lowFps = if (capped) 20f else if (gfx == 1) 25f else 30f
        if (fps < lowFps && scaleIdx == 0 && gfx > 0 && autoGfx) {
            if (++slowChecks >= 3) { slowChecks = 0; applyGfx(gfx - 1); toast("Графика снижена для плавной игры", 2f); return }
        } else slowChecks = 0
        post.scale = steps[scaleIdx.coerceAtMost(steps.size - 1)]
    }

    /** Автоматически снижать графику (выключается, если игрок выбрал уровень сам). */
    var autoGfx = true

    /** Сменить уровень графики на лету. */
    fun applyGfx(level: Int) {
        gfx = level.coerceIn(0, 3)
        if (!::post.isInitialized) return
        modelBatch = if (gfx >= 1) detailBatch else plainBatch
        post.bloomOn = gfx >= 1
        scaleIdx = scaleSteps().size - 1
        post.scale = scaleSteps()[scaleIdx]
        post.allocScale = post.scale
        fx.limit = intArrayOf(250, 450, 700, 900)[gfx]
        val sm = shadowSize()
        if (sunLight.depthMap.texture.width != sm) {
            env.remove(sunLight)
            sunLight.dispose()
            sunLight = makeSunLight(sm)
            env.add(sunLight)
            env.shadowMap = sunLight
        }
        save()
    }

    private val shipsTmp = ArrayList<Ship>()
    private val nearIslands = ArrayList<FloatArray>()
    private var nearT = 0f
    /** Виден ли шар (x, y, z, r) камере — не рисуем то, что за кадром. */
    fun visible(x: Float, y: Float, z: Float, r: Float) = cam.frustum.sphereInFrustum(x, y, z, r)
    private var reflFbo: com.badlogic.gdx.graphics.glutils.FrameBuffer? = null
    private val reflCam by lazy { PerspectiveCamera(52f, 1f, 1f) }
    private val mirror = com.badlogic.gdx.math.Matrix4().scl(1f, -1f, 1f)

    /** Зеркальное отражение сцены в воде: та же камера, мир отражён по высоте. */
    private fun renderReflection(lookX: Float, lookZ: Float): com.badlogic.gdx.graphics.Texture? = try { renderReflection0(lookX, lookZ) } catch (e: Exception) {
        Gdx.app.error("Reflection", e.message ?: ""); reflFail = true; null
    }

    private fun renderReflection0(lookX: Float, lookZ: Float): com.badlogic.gdx.graphics.Texture {
        val rs = if (gfx >= 3) 0.5f else 0.38f
        val rw = (Gdx.graphics.backBufferWidth * rs).toInt().coerceAtLeast(64)
        val rh = (Gdx.graphics.backBufferHeight * rs).toInt().coerceAtLeast(64)
        var f = reflFbo
        if (f == null || f.width != rw || f.height != rh) {
            f?.dispose()
            f = com.badlogic.gdx.graphics.glutils.FrameBuffer(com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888, rw, rh, true).also {
                it.colorBufferTexture.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.Linear, com.badlogic.gdx.graphics.Texture.TextureFilter.Linear)
            }
            reflFbo = f
        }
        val rc = reflCam
        rc.fieldOfView = cam.fieldOfView
        rc.viewportWidth = cam.viewportWidth; rc.viewportHeight = cam.viewportHeight
        rc.near = cam.near; rc.far = cam.far
        rc.position.set(cam.position); rc.direction.set(cam.direction); rc.up.set(cam.up)
        rc.update()
        rc.view.mul(mirror)
        rc.combined.set(rc.projection).mul(rc.view)
        f.begin()
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        modelBatch.begin(rc)
        // отражение видно, только если отражённый (под водой) объект попадает в кадр
        for (isl in islands) if (hypot(isl.x - lookX, isl.z - lookZ) < 420 + isl.r && visible(isl.x, -8f, isl.z, isl.r * 1.3f + 20f)) modelBatch.render(isl.inst, env)
        for (s in ships) if (!s.sinking && hypot(s.x - lookX, s.z - lookZ) < 380 && visible(s.x, -10f, s.z, s.len + 14f)) modelBatch.render(s.inst, env)
        portView.render(modelBatch, env, lookX, lookZ)
        living.render(modelBatch, env, lookX, lookZ)
        modelBatch.end()
        f.end()
        return f.colorBufferTexture
    }
    private val glowCol = Color()

    // ---------- Защита от вылетов ----------
    private var errText = ""
    private var errT = 0f
    private var errCount = 0
    /** Причина прошлого вылета (с телефона) — покажем после загрузки. */
    private var lastCrash: String? = null

    private fun describe(e: Throwable): String {
        val frames = e.stackTrace.filter { it.className.startsWith("com.seatrader") }.take(4)
            .joinToString("\n") { "  ${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
        val cause = e.cause?.let { "\nПричина: ${it.javaClass.simpleName}: ${it.message?.take(120)}" } ?: ""
        return "${e.javaClass.simpleName}: ${e.message?.take(160) ?: ""}\n$frames$cause"
    }

    override fun render() {
        try { render0() } catch (e: Throwable) {
            Gdx.app.error("SeaGame", "render", e)
            errCount++
            errText = describe(e); errT = 12f
            try { if (batch.isDrawing) batch.end() } catch (_: Throwable) { }
            try { modelBatch.end() } catch (_: Throwable) { }
            try { com.badlogic.gdx.graphics.glutils.FrameBuffer.unbind() } catch (_: Throwable) { }
            try { if (errCount <= 3 && state != State.LOADING) prefs.putString("lastError", "v${'$'}errCount ${'$'}errText").flush() } catch (_: Throwable) { }
            // ошибка в сценке — просто заканчиваем её
            if (state == State.CUTSCENE) try { cutClear(); endPrologue(guilds.guild < 0) } catch (_: Throwable) { state = State.PLAY }
        }
        if (errT > 0f) try {
            errT -= min(0.05f, Gdx.graphics.deltaTime)
            Gdx.gl.glViewport(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
            batch.projectionMatrix = hudCam.combined
            batch.begin()
            ui.rect(batch, 0f, hDp - safeTop - 150f, wDp, 150f, Color(0f, 0f, 0f, 0.75f))
            ui.text(batch, ui.small, "Ошибка в игре (пришлите скриншот):\n$errText", 8f, hDp - safeTop - 8f, Align.left, wDp - 16f, true, Color(1f, 0.6f, 0.5f, 1f))
            batch.end()
        } catch (_: Throwable) { try { if (batch.isDrawing) batch.end() } catch (_: Throwable) { } }
    }

    private fun render0() {
        val dt = min(0.05f, Gdx.graphics.deltaTime)
        if (state == State.LOADING) {
            loadNext()
            Gdx.gl.glClearColor(0.05f, 0.15f, 0.25f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
            batch.projectionMatrix = hudCam.combined
            batch.begin(); hud.drawLoading(); batch.end()
            afterRender?.invoke()
            return
        }
        if (state == State.PLAY) update(dt) else if (state == State.CUTSCENE) cutsceneUpdate(dt) else now += dt * 0.3f
        adaptQuality(dt)
        fx.time = now
        fx.update(dt)
        updateCamera(dt)
        applyLighting()
        val ps = player
        val windy = min(1f, (if (ps != null) abs(ps.speed) / 22f else 0f) + stormLevel * 0.8f)
        val cut = state == State.CUTSCENE
        sfx.update(dt, (combatT > 0 && state == State.PLAY) || (cut && prologue.battle), stormLevel, windy, systems.wantedShanty(), state != State.PLAY && !cut)
        saveTimer += dt
        if (saveTimer > 15 && !cut) { saveTimer = 0f; save() }

        Gdx.gl.glClearColor(sky.fog[0], sky.fog[1], sky.fog[2], 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT or
            (if (Gdx.graphics.bufferFormat.coverageSampling) GL20.GL_COVERAGE_BUFFER_BIT_NV else 0))

        val lookX = camPos.x; val lookZ = camPos.z - 80
        // Карта теней от солнца (или луны)
        shadowCenter.set(lookX, 0f, lookZ + 30)
        // тени есть на всех уровнях графики: на слабых телефонах — карта поменьше и ближний радиус
        if (!shadowFail) {
        val sr = if (gfx == 0) 0.7f else 1f
        sunLight.begin(shadowCenter, sunLight.direction)
        shadowBatch.begin(sunLight.camera)
        for (isl in islands) if (hypot(isl.x - lookX, isl.z - lookZ) < 220 * sr + isl.r) shadowBatch.render(isl.inst)
        for (s in ships) if (hypot(s.x - lookX, s.z - lookZ) < 230 * sr) shadowBatch.render(s.inst)
        systems.renderShadows(shadowBatch, lookX, lookZ)
        living.renderShadows(shadowBatch, lookX, lookZ)
        portView.renderShadows(shadowBatch, lookX, lookZ)
        shadowBatch.end()
        sunLight.end()
        }
        Gdx.gl.glViewport(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        val usePost = post.prepare()
        val reflTex = if (gfx >= 2 && !reflFail) renderReflection(lookX, lookZ) else null
        if (usePost) post.begin()
        Gdx.gl.glClearColor(sky.fog[0], sky.fog[1], sky.fog[2], 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        nearT -= dt
        if (nearT <= 0f || nearIslands.isEmpty()) {
            nearT = 0.25f
            nearIslands.clear()
            islands.sortedBy { hypot(it.x - lookX, it.z - lookZ) - it.r }.take(12).forEach { nearIslands += floatArrayOf(it.x, it.z, it.r) }
        }
        val shadows = !debugNoShadow && !shadowFail
        env.shadowMap = if (shadows) sunLight else null
        water.render(cam, camPos.x, camPos.z - 60, now, sky, nearIslands, if (shadows) sunLight.depthMap.texture else null, sunLight.projViewTrans,
            reflTex, if (usePost) post.sceneW else Gdx.graphics.backBufferWidth, if (usePost) post.sceneH else Gdx.graphics.backBufferHeight, gfx >= 2)

        modelBatch.begin(cam)
        for ((i, isl) in islands.withIndex()) {
            if (hypot(isl.x - lookX, isl.z - lookZ) > 560 + isl.r || !visible(isl.x, 6f, isl.z, isl.r * 1.3f + 20f)) continue
            modelBatch.render(isl.inst, env)
            (isl.foam.materials[0].get(BlendingAttribute.Type) as BlendingAttribute).opacity = (0.22f + sin(now * 1.6f + i) * 0.1f) * (0.5f + 0.5f * sky.day)
            modelBatch.render(isl.foam, env)
        }
        for (port in ports) {
            if (hypot(port.rx - lookX, port.rz - lookZ) > 560) continue
            val s = 1 + sin(now * 3) * 0.05f
            port.ring.transform.setToTranslation(port.rx, waveHeight(port.rx, port.rz, now) + 0.7f, port.rz).scale(s, 1f, s)
            (port.ring.materials[0].get(BlendingAttribute.Type) as BlendingAttribute).opacity = 0.55f + sin(now * 3) * 0.2f
            modelBatch.render(port.ring, env)
        }
        for (s in ships) if (hypot(s.x - lookX, s.z - lookZ) < 600 && visible(s.x, 8f, s.z, s.len + 12f)) modelBatch.render(s.inst, env)
        for (l in loot) if (hypot(l.x - lookX, l.z - lookZ) < 600) modelBatch.render(l.inst, env)
        treasure?.let { if (hypot(it.x - lookX, it.z - lookZ) < 600) modelBatch.render(it.inst, env) }
        systems.render(modelBatch, env, lookX, lookZ)
        living.render(modelBatch, env, lookX, lookZ)
        portView.render(modelBatch, env, lookX, lookZ)
        extras.render(modelBatch, env)
        serpent?.let { sp ->
            if (hypot(sp.x - lookX, sp.z - lookZ) < 600) {
                modelBatch.render(sp.head, env)
                for (b in sp.body) modelBatch.render(b, env)
            }
        }
        modelBatch.end()

        // Тени, огни, чайки, ядра, частицы
        val night = 1f - sky.day
        for (s in ships) {
            if (s.sinking || hypot(s.x - lookX, s.z - lookZ) > 500) continue
            fx.shadow(s.x, s.z, s.width * 1.4f, s.len * 1.05f, -s.h * MathUtils.radiansToDegrees, 0.12f)
            if (night > 0.2f) {
                val lx = s.x - sin(s.h) * s.len * 0.44f; val lz = s.z + cos(s.h) * s.len * 0.44f
                fx.glow(lx, s.y + s.deckTop + s.width * 0.62f * 0.72f + 1.2f, lz, 4f, glowCol.set(1f, 0.8f, 0.4f, night * 0.85f))
            }
        }
        if (night > 0.15f) {
            for (isl in islands) {
                if (isl.lights.isEmpty() || hypot(isl.x - lookX, isl.z - lookZ) > 520) continue
                for ((i, l) in isl.lights.withIndex()) {
                    val big = l.y > 15
                    val flick = 0.85f + 0.15f * sin(now * 7 + i)
                    fx.glow(l.x, l.y, l.z, if (big) 12f + sin(now * 2) * 2 else 4.5f, glowCol.set(1f, 0.82f, 0.45f, night * 0.9f * flick))
                }
            }
        }
        if (night > 0.15f) portView.lanterns({ x, y, z -> fx.glow(x, y, z, 4f, glowCol.set(1f, 0.82f, 0.45f, night * 0.85f)) }, lookX, lookZ)
        treasure?.let { t ->
            val pulse = 0.5f + 0.5f * sin(now * 4)
            fx.glow(t.x, 4f, t.z, 7f + pulse * 3, glowCol.set(1f, 0.85f, 0.3f, 0.35f + 0.3f * pulse))
        }
        if (highQuality) for (g in gulls) {
            val gx = g.cx + cos(g.a) * g.r; val gz = g.cz + sin(g.a) * g.r
            if (hypot(gx - lookX, gz - lookZ) < 300) fx.gull(gx, g.h + sin(now + g.phase) * 2, gz, 0.5f + 0.5f * sin(now * 9 + g.phase))
        }
        systems.renderDecals(lookX, lookZ)
        living.renderDecals(lookX, lookZ)
        extras.renderDecals()
        systems.renderExtraDecals()
        if (aimActive) {
            val p = player
            if (p != null) {
                fx.frame(ui.ring, aimX, waveHeight(aimX, aimZ, now) + 0.6f, aimZ, 8f, 8f, glowCol.set(1f, 0.85f, 0.3f, 0.95f), true, 0f)
                for (k in 1..8) {
                    val t = k / 9f
                    val x = p.x + (aimX - p.x) * t; val z = p.z + (aimZ - p.z) * t
                    fx.glow(x, waveHeight(x, z, now) + 1f + sin(t * PI.toFloat()) * 6f, z, 0.9f, glowCol.set(1f, 0.85f, 0.3f, 0.8f))
                }
            }
        }
        drawBalls()
        drawClouds(lookX, lookZ)
        fx.render()
        if (usePost) post.end()

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        batch.projectionMatrix = hudCam.combined
        batch.begin()
        hud.draw(dt)
        batch.end()

        if (debugShadow) {
            batch.begin()
            batch.color = Color.WHITE
            batch.draw(sunLight.depthMap.texture as com.badlogic.gdx.graphics.Texture, 0f, 0f, 220f, 220f)
            batch.end()
        }
        stage.act(dt)
        stage.draw()
        afterRender?.invoke()
    }

    private val cloudCol = Color()

    /** Облака проплывают между камерой и морем по ветру. */
    private fun drawClouds(lookX: Float, lookZ: Float) {
        val dt = min(0.05f, Gdx.graphics.deltaTime)
        val wx = sin(windA) * 6f * windS; val wz = -cos(windA) * 6f * windS
        val a = (0.24f + stormLevel * 0.12f) * (0.4f + 0.6f * sky.day)
        val shade = 0.7f + 0.3f * sky.day - stormLevel * 0.35f
        cloudCol.set(shade * 1.0f + 0.05f, shade * 1.0f + 0.05f, shade * 1.05f + 0.06f, a)
        for (c in clouds) {
            c[0] += wx * dt; c[1] += wz * dt
            // облако уплыло далеко — переносим на другую сторону
            if (c[0] - lookX > 480) c[0] -= 960; if (c[0] - lookX < -480) c[0] += 960
            if (c[1] - lookZ > 480) c[1] -= 960; if (c[1] - lookZ < -480) c[1] += 960
            if (hypot(c[0] - lookX, c[1] - lookZ) > 420) continue
            fx.frame(if (c[4] < 0.5f) ui.cloud else ui.cloud2, c[0], 62f + c[2] * 0.08f, c[1], c[2] * 1.6f, c[2], cloudCol, true, c[3])
        }
    }

    // ================= Вспомогательное =================
    private val tmp = Vector3()
    fun toScreen(x: Float, y: Float, z: Float): Vector3? {
        tmp.set(x, y, z)
        cam.project(tmp)
        if (tmp.z > 1f || tmp.z < 0f) return null
        tmp.x /= scale; tmp.y /= scale
        return tmp
    }

    fun floatText(x: Float, y: Float, z: Float, text: String, c: Color, font: BitmapFont) {
        val s = toScreen(x, y, z) ?: return
        if (s.x < -50 || s.x > wDp + 50 || s.y < -50 || s.y > hDp + 50) return
        floats += FloatText(text, s.x + rand(-12f, 12f), s.y, c, font)
    }

    fun toast(text: String, seconds: Float = 1.6f) {
        toastText = text; toastT = seconds
    }

    override fun dispose() {
        if (state != State.LOADING) save()
        models.forEach { it.dispose() }
        if (::water.isInitialized) water.dispose()
        if (::fx.isInitialized) fx.dispose()
        if (::post.isInitialized) post.dispose()
        if (::sfx.isInitialized) sfx.dispose()
        ui.dispose()
        batch.dispose()
        if (::modelBatch.isInitialized) modelBatch.dispose()
        stage.dispose()
    }
}
