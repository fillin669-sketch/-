package com.seatrader.game

import com.badlogic.gdx.Gdx
import kotlin.math.abs
import kotlin.math.max
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.ScreenUtils

/** Запуск на компьютере (для разработки и автоматических проверок). */
fun main() {
    val game = SeaGame()
    val test = System.getenv("SEA_TEST")
    if (test != null) TestScript(game, test).install()
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Пираты Семи Ветров")
        val sz = System.getenv("SEA_SIZE")?.split("x")?.mapNotNull { it.toIntOrNull() }
        if (sz != null && sz.size == 2) setWindowedMode(sz[0], sz[1])
        else if (System.getenv("SEA_LANDSCAPE") != null) setWindowedMode(870, 412) else setWindowedMode(412, 870)
        setForegroundFPS(60)
        setBackBufferConfig(8, 8, 8, 8, 24, 0, 4)
        if (test != null) setPreferencesConfig(test + "/prefs", com.badlogic.gdx.Files.FileType.Absolute)
    }
    Lwjgl3Application(game, config)
}

/** Сценарий проверки: двигает корабль, устраивает бой, открывает порт и делает скриншоты. */
class TestScript(private val g: SeaGame, private val out: String) {
    private var frame = 0
    private var t0 = 0L
    private val log = StringBuilder()

    fun install() { g.skipPrologue = System.getenv("SEA_PROLOGUE") == null; g.afterRender = { step() } }

    private fun shot(name: String) {
        val w = Gdx.graphics.backBufferWidth; val h = Gdx.graphics.backBufferHeight
        val px = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true)
        val pm = Pixmap(w, h, Pixmap.Format.RGBA8888)
        BufferUtils.copy(px, 0, pm.pixels, px.size)
        PixmapIO.writePNG(Gdx.files.absolute("$out/$name.png"), pm)
        pm.dispose()
        log.append("shot $name\n")
    }

    private fun touch(kind: String, x: Float, y: Float) {
        val sx = (x * g.scale).toInt(); val sy = (y * g.scale).toInt()
        when (kind) {
            "down" -> g.input.touchDown(sx, sy, 0, 0)
            "drag" -> g.input.touchDragged(sx, sy, 0)
            "up" -> g.input.touchUp(sx, sy, 0, 0)
        }
    }

    private fun info(tag: String) {
        val p = g.player!!
        log.append("$tag state=${g.state} p=(${p.x.toInt()},${p.z.toInt()}) speed=${"%.1f".format(p.speed)} hp=${p.hp.toInt()}/${p.maxHp.toInt()} coins=${g.coins} gems=${g.gems} quest=${g.questIdx} kills=${g.kills} balls=${g.balls.size} allies=${g.ships.count { it.ally }} fps=${Gdx.graphics.framesPerSecond}\n")
        for (s in g.ships) if (s.faction == Faction.PIRATE && Math.hypot((s.x - p.x).toDouble(), (s.z - p.z).toDouble()) < 150)
            log.append("   pirate ${s.type} hp=${s.hp.toInt()} d=${Math.hypot((s.x - p.x).toDouble(), (s.z - p.z).toDouble()).toInt()} ai=${s.ai} burn=${s.burnT > 0} slow=${s.slowT > 0}\n")
        g.serpent?.let { log.append("   serpent hp=${it.hp.toInt()} diving=${it.diving}\n") }
    }

    private var base = -1

    private fun clearArea() {
        for (s in ArrayList(g.ships)) if (s.faction == Faction.PIRATE) g.removeShip(s)
    }

    private fun findButton(prefix: String): com.badlogic.gdx.scenes.scene2d.ui.TextButton? {
        fun find(a: com.badlogic.gdx.scenes.scene2d.Actor): com.badlogic.gdx.scenes.scene2d.ui.TextButton? {
            if (a is com.badlogic.gdx.scenes.scene2d.ui.TextButton && a.text.toString().contains(prefix)) return a
            if (a is com.badlogic.gdx.scenes.scene2d.Group) for (c in a.children) find(c)?.let { return it }
            return null
        }
        return find(g.stage.root)
    }

    private fun press(prefix: String) {
        val b = findButton(prefix) ?: run { log.append("button '$prefix' not found\n"); return }
        b.toggle()
    }

    private fun tp(x: Float, z: Float) {
        val p = g.player!!
        p.x = x; p.z = z; p.vx = 0f; p.vz = 0f; p.inPort = false
        g.camSnap = true
        clearArea()
    }

    private val landscape = System.getenv("SEA_LANDSCAPE") != null

    private fun step() {
        frame++
        if (base < 0) {
            if (g.state == SeaGame.State.LOADING) return
            base = frame
            t0 = System.currentTimeMillis()
            log.append("loaded at frame $frame state=${g.state}\n")
        }
        val f = frame - base
        if (System.getenv("SEA_PROLOGUE") != null) { prologueTest(f); return }
        if (System.getenv("SEA_BETA") != null) { betaTest(f); return }
        if (System.getenv("SEA_FEAT") != null) { featTest(f); return }
        if (System.getenv("SEA_BOARD") != null) {
            if (f == 3) press("Вступить"); if (f == 5) press("Забрать"); if (f == 8) g.menus.close()
            if (f == 12) { val p = g.player!!; g.menus.showBoarding(g.spawnEnemy("sloop", p.x + 30f, p.z)) }
            if (f == 20) shot("board"); if (f == 21) { repeat(3) { press("Удар") } }
            if (f == 40) shot("board_result")
            if (f == 41) { Gdx.files.absolute("$out/log.txt").writeString(log.toString(), false); Gdx.app.exit() }
            return
        }
        if (System.getenv("SEA_DUMP") != null) { if (f == 3) { press("Вступить"); } ; if (f == 5) press("Забрать"); if (f == 8) g.menus.close(); if (f == 20) { g.autoGfx = false; g.applyGfx(0) }; if (f == 40) g.openPort(0); if (f == 60) { g.menus.close(); g.closePort(); g.applyGfx(3) }; if (f == 80) g.openPort(0); if (f == 120) { dumpShaders(); Gdx.app.exit() }; return }
        if (landscape) {
            when (f) {
                3 -> { g.menus.close() }
                6 -> { g.menus.close() }
                30 -> { shot("L1_play"); touch("down", 300f, 300f); touch("drag", 360f, 260f) }
                120 -> { shot("L2_sail"); touch("up", 360f, 260f); g.coins = 9000; g.openPort(0) }
                140 -> shot("L3_port")
                150 -> { g.menus.close(); g.openPause() }
                165 -> shot("L4_pause")
                170 -> { Gdx.files.absolute("$out/log.txt").writeString(log.toString(), false); Gdx.app.exit() }
            }
            return
        }
        val lv = g.living
        if (f == 1) System.getenv("SEA_GFX")?.toIntOrNull()?.let { g.autoGfx = false; g.applyGfx(it) }
        if (System.getenv("SEA_AI") != null) { aiTest(f); return }
        if (System.getenv("SEA_ALLY") != null) { allyTest(f); return }
        if (System.getenv("SEA_LEASH") != null) { leashTest(f); return }
        if (System.getenv("SEA_BETA") != null) { betaTest(f); return }
        when (f) {
            2 -> shot("00_hero")
            3 -> press("Вступить")
            5 -> shot("00_daily")
            6 -> { press("Забрать") }
            10 -> { press("швартовы") }
            11 -> { g.openPause() }
            12 -> press("Капитан")
            15 -> shot("00b_captain")
            16 -> press("Сменить")
            18 -> shot("00c_change")
            19 -> { g.menus.close() }
            20 -> { g.coins = 60000; g.gems = 60; val u = g.ensureUp("sloop"); u[0] = 2; u[2] = 2; g.allies.clear(); g.allies += MERCS[0].id; g.allies += MERCS[1].id; g.spawnAllies(); g.rebuildPlayer(); g.openPort(0) }
            25 -> press("Улучшения")
            90 -> shot("01_upgrades")
            91 -> { g.ensureUp("sloop").fill(5); g.rebuildPlayer(); press("Улучшения") }
            120 -> shot("02_upgrades_max")
            121 -> press("Рынок")
            180 -> shot("03_market")
            181 -> press("Таверна")
            240 -> shot("04_tavern")
            241 -> press("Арсенал")
            300 -> shot("05_arsenal")
            301 -> press("Флот")
            360 -> shot("06_fleet")
            361 -> press("Вид")
            420 -> shot("07_paint")
            421 -> press("Верфь")
            480 -> shot("08_ships")
            481 -> press("▼")
            500 -> { g.portView.orbit = 1.2f }
            540 -> shot("09_collapsed_orbit")
            541 -> { press("Выйти в море"); g.systems.skillPts = 2; g.systems.cargo[0] = 10 }
            575 -> { g.systems.shantyBanner = 4f }
            580 -> { shot("10_left_port"); info("left"); g.debugNoShadow = true }
            584 -> { shot("10b_noshadow"); g.debugNoShadow = false }
            590 -> tp(lv.ringX, lv.ringZ)
            650 -> shot("11_base_new")
            651 -> press("Основать")
            655 -> { lv.blev.fill(3); lv.refreshBuildings(); lv.bank = 900f; press("Постройки") }
            720 -> shot("12_base_menu")
            721 -> press("Выйти в море")
            760 -> { shot("13_left_base"); info("leftbase") }
            765 -> { g.dayTime = 0.82f; g.openPort(0) }
            766 -> press("Таверна")
            830 -> shot("14_night_tavern")
            831 -> press("Верфь")
            890 -> shot("15_night_yard")
            891 -> { g.dayTime = 0.3f; press("Таверна") }
            950 -> shot("16_day_tavern")
            960 -> {
                log.append("frames=$frame seconds=${(System.currentTimeMillis() - t0) / 1000.0}\n")
                Gdx.files.absolute("$out/log.txt").writeString(log.toString(), false)
                Gdx.app.exit()
            }
        }
    }
    private val track = HashMap<Ship, FloatArray>()
    private var stuckSum = 0; private var samples = 0
    /** Проверка навигации ботов: застревают ли у островов. */
    private fun aiTest(f: Int) {
        when (f) {
            2 -> press("Вступить")
            4 -> press("Забрать")
            8 -> g.menus.close()
            10 -> {
                val p = g.player!!
                // место среди диких островов
                val isl = g.islands.filter { it.port == -1 && Math.hypot(it.x.toDouble(), it.z.toDouble()) in 300.0..900.0 }.take(6)
                for (s in ArrayList(g.ships)) if (s !== p) g.ships.remove(s)
                var k = 0
                for (i in isl) for (a in 0 until 4) {
                    val ang = a * 1.57f + 0.4f
                    val x = i.x + kotlin.math.cos(ang) * (i.r + 18f); val z = i.z + kotlin.math.sin(ang) * (i.r + 18f)
                    val s = if (k % 3 == 2) g.spawnNeutral("merchant", x, z).also { it.wx = i.x * -1f; it.wz = i.z * -1f } else g.spawnEnemy("brig", x, z)
                    s.h = Math.atan2((i.x - x).toDouble(), (-(i.z - z)).toDouble()).toFloat() // носом к острову
                    s.desired = s.h
                    k++
                }
                p.x = 0f; p.z = 60f; p.inPort = true
                log.append("spawned $k ships\n")
            }
            in 11..1400 -> {
                g.player!!.let { it.x = 0f; it.z = 60f; it.vx = 0f; it.vz = 0f }
                if (f % 60 == 0) {
                    var stuck = 0; var inside = 0
                    for (s in g.ships) {
                        if (s === g.player || s.sinking) continue
                        val prev = track[s]
                        if (prev != null && Math.hypot((s.x - prev[0]).toDouble(), (s.z - prev[1]).toDouble()) < 6.0) {
                            stuck++
                            val ni = g.islands.minByOrNull { Math.hypot((it.x - s.x).toDouble(), (it.z - s.z).toDouble()) - it.r }!!
                            log.append("   stuck ${s.type} ${s.faction} ai=${s.ai} thr=${"%.2f".format(s.throttle)} spd=${"%.1f".format(s.speed)} island=${(Math.hypot((ni.x - s.x).toDouble(), (ni.z - s.z).toDouble()) - ni.r).toInt()} pos=(${s.x.toInt()},${s.z.toInt()}) esc=${s.escapeT}\n")
                        }
                        if (g.islands.any { Math.hypot((it.x - s.x).toDouble(), (it.z - s.z).toDouble()) < it.r }) inside++
                        track[s] = floatArrayOf(s.x, s.z)
                    }
                    if (f > 300) { stuckSum += stuck; samples++ }
                    log.append("t=${f / 30}s ships=${g.ships.size - 1} stuck(<6m/2s)=$stuck inside=$inside\n")
                }
            }
            1401 -> {
                log.append("avg stuck after 10s = ${stuckSum.toFloat() / samples}\n")
                Gdx.files.absolute("$out/log.txt").writeString(log.toString(), false)
                Gdx.app.exit()
            }
        }
    }

    private fun allyTest(f: Int) {
        val p = g.player ?: return
        when (f) {
            2 -> press("Вступить")
            4 -> press("Забрать")
            8 -> { g.menus.close(); for (s in ArrayList(g.ships)) if (s !== p) g.ships.remove(s); p.x = 300f; p.z = 300f; p.inPort = false
                g.allies.clear(); g.allies += MERCS[0].id; g.allies += MERCS[1].id; g.spawnAllies() }
            in 10..1300 -> {
                for (s in ArrayList(g.ships)) if (s.faction == Faction.PIRATE) g.ships.remove(s)
                // игрок рулит змейкой
                g.joyActive = true; g.joyMag = 1f
                val a = f * 0.004f
                g.joyJx = kotlin.math.sin(a * 3f); g.joyJy = kotlin.math.cos(a * 2f)
                if (f % 90 == 0) {
                    val al = g.ships.filter { it.ally }
                    log.append("t=${f / 30}s speed=${"%.1f".format(p.speed)} " + al.joinToString { "d=${Math.hypot((it.x - p.x).toDouble(), (it.z - p.z).toDouble()).toInt()} spd=${"%.1f".format(it.speed)}" } + "\n")
                }
                if (f == 600) shot("ally1")
                if (f == 1200) shot("ally2")
            }
            1301 -> { Gdx.files.absolute("$out/log.txt").writeString(log.toString(), false); Gdx.app.exit() }
        }
    }

    private var chaser: Ship? = null
    private fun leashTest(f: Int) {
        val p = g.player ?: return
        when (f) {
            2 -> press("Вступить")
            4 -> press("Забрать")
            8 -> { g.menus.close(); for (s in ArrayList(g.ships)) if (s !== p) g.ships.remove(s)
                p.x = 400f; p.z = 400f; p.inPort = false; p.h = 0f
                chaser = g.spawnEnemy("brig", 400f, 480f) }
            in 10..1500 -> {
                g.joyActive = true; g.joyMag = 1f; g.joyJx = 0.3f; g.joyJy = 1f
                p.hp = p.maxHp
                val c = chaser ?: return
                if (f % 60 == 0) log.append("t=${f / 30}s dist=${Math.hypot((c.x - p.x).toDouble(), (c.z - p.z).toDouble()).toInt()} fromHome=${Math.hypot((c.x - c.homeX).toDouble(), (c.z - c.homeZ).toDouble()).toInt()} ai=${c.ai} giveUp=${"%.1f".format(c.giveUpT)} fps=${Gdx.graphics.framesPerSecond}\n")
            }
            1501 -> { Gdx.files.absolute("$out/log.txt").writeString(log.toString(), false); Gdx.app.exit() }
        }
    }

    // ===================== Бета-тест: сценарии + «обезьяна» + проверки =====================
    private val plan = ArrayList<Pair<Int, () -> Unit>>()
    private var planIdx = 0
    private var waitUntil = 0
    private var lastNano = 0L
    private val issues = LinkedHashMap<String, Int>()
    private var stepName = ""
    private var softFrames = 0
    private var minFps = 999
    private val rnd = java.util.Random(77)
    private var spikes = 0
    private fun issue(t: String) { val k = "[$stepName] $t"; issues[k] = (issues[k] ?: 0) + 1 }
    private fun step(name: String, wait: Int, a: () -> Unit) { plan += wait to { stepName = name; a() } }

    private fun betaChecks(f: Int) {
        val now = System.nanoTime()
        if (lastNano != 0L && f > 20) {
            val ms = (now - lastNano) / 1_000_000
            if (ms > 400) { spikes++; issue("долгий кадр ${ms / 100 * 100}+ мс") }
        }
        lastNano = now
        val p = g.player ?: run { issue("нет корабля игрока"); return }
        // в сценарных шагах игрок не должен случайно погибнуть (кроме проверки гибели и «обезьяны»)
        if (!stepName.startsWith("гибель") && stepName != "обезьяна" && !p.sinking && !stepName.startsWith("абордаж")) p.hp = max(p.hp, p.maxHp * 0.6f)
        if (p.x.isNaN() || p.z.isNaN() || p.hp.isNaN()) issue("NaN у игрока")
        if (abs(p.x) > WORLD + 60 || abs(p.z) > WORLD + 60) issue("игрок за краем мира")
        if (g.coins < 0) issue("монеты < 0"); if (g.gems < 0) issue("кристаллы < 0")
        if (g.ships.size > 70) issue("слишком много кораблей ${g.ships.size / 10 * 10}+")
        if (g.balls.size > 500) issue("слишком много ядер")
        if (g.loot.size > 250) issue("слишком много добычи")
        for (s in g.ships) if (s.x.isNaN() || s.hp.isNaN()) { issue("NaN у корабля ${s.type}"); break }
        val actors = g.stage.root.children.size
        if (g.state != SeaGame.State.PLAY && g.state != SeaGame.State.LOADING && actors == 0) { if (++softFrames == 30) issue("зависание: состояние ${g.state} без окна") } else softFrames = 0
        if (g.state == SeaGame.State.PLAY && actors > 0) issue("в игре осталось окно поверх экрана")
        val fps = Gdx.graphics.framesPerSecond
        if (f > 200 && fps in 1 until minFps) minFps = fps
    }

    private fun spawnSide(type: String, d: Float): Ship {
        val p = g.player!!
        val rx = kotlin.math.cos(p.h); val rz = kotlin.math.sin(p.h)
        return g.spawnEnemy(type, p.x + rx * d, p.z + rz * d).also { it.h = p.h; it.ai = AiState.WANDER }
    }

    private fun buildPlan() {
        val lv = g.living
        step("старт", 3) { press("Вступить") }
        step("старт", 3) { press("Забрать") }
        step("старт", 3) { if (findButton("швартовы") != null) press("швартовы") }
        step("старт", 20) { g.menus.close(); g.coins = 200000; g.gems = 2000; tp(250f, -250f); g.player!!.h = 0f }
        // бой
        var e1: Ship? = null
        step("бой", 360) { g.systems.autoFire = true; e1 = spawnSide("sloop", 40f) }
        step("бой", 1) { val e = e1!!; if (e.hp >= e.maxHp && !e.sinking) issue("автоогонь не попал по пирату за 12 с"); log.append("бой: hp врага ${e.hp.toInt()}/${e.maxHp.toInt()} kills=${g.kills}\n") }
        // способности и оружие
        step("способности", 30) { tp(300f, -300f); g.systems.ownedWeapons += listOf(3, 4, 5); spawnSide("brig", 45f) }
        for (i in 0..5) step("способность $i", 40) { g.abilityCd[i] = 0f; g.useAbility(i) }
        // абордаж
        var e2: Ship? = null
        step("абордаж", 40) { tp(350f, -350f); g.player!!.h = 0f; e2 = spawnSide("sloop", 30f).also { it.hp = it.maxHp * 0.3f } }
        step("абордаж", 90) {
            val t = g.systems.boardTarget ?: g.systems.alignHint
            if (t == null) issue("нет ни кнопки абордажа, ни подсказки рядом с ослабленным пиратом") else g.systems.throwGrapples(t)
        }
        step("абордаж", 110) { }
        step("абордаж", 10) { if (g.state != SeaGame.State.DIALOG) issue("абордаж не начался: ${g.state} цель hp=${e2?.hp} тонет=${e2?.sinking} крюки=${g.systems.grappleTarget != null}") }
        for (k in 0..2) step("абордаж", 25) { press(listOf("Натиск", "Залп", "Оборона")[k]) }
        step("абордаж", 30) { if (findButton("Натиск") != null) issue("после 3 раундов схватка не закончилась") }
        step("абордаж", 15) { for (b in listOf("Захватить", "Разграбить", "Продолжить")) if (findButton(b) != null) { press(b); break } }
        step("абордаж", 10) { if (g.state != SeaGame.State.PLAY) { issue("после абордажа не вернулись в игру: ${g.state}"); g.menus.close() } }
        // порт: всё купить и проверить
        step("порт", 30) { g.openPort(0) }
        for (sd in PLAYER_SHIPS.drop(1)) step("верфь", 6) { press("Верфь"); if (findButton("Купить") != null) press("Купить") }
        step("верфь", 6) { log.append("кораблей: ${g.owned.size}\n"); if (g.owned.size < PLAYER_SHIPS.size) issue("куплены не все корабли (${g.owned.size})") }
        step("улучшения", 6) { press("Улучшения") }
        for (k in 0 until 20) step("улучшения", 3) { val b = findButtonIn { it.text.toString().contains("K") || it.text.toString().matches(Regex(".*\\d.*")) && !it.text.toString().contains("Выйти") }; b?.toggle() }
        step("улучшения", 6) { log.append("улучшения ${g.playerDef().id}: ${g.ensureUp(g.shipId).toList()}\n") }
        step("рынок", 6) { press("Рынок") }
        step("рынок", 6) { repeat(4) { press("+5") } }
        step("рынок", 6) { val before = g.coins; press("Продать всё"); if (g.coins < before) issue("продажа уменьшила монеты") }
        step("арсенал", 6) { press("Арсенал") }
        step("арсенал", 6) { repeat(3) { press("Купить") } }
        step("флот", 6) { press("Флот") }
        step("флот", 6) {
            val names = ArrayList<String>()
            fun collect(a: com.badlogic.gdx.scenes.scene2d.Actor) { if (a is com.badlogic.gdx.scenes.scene2d.ui.TextButton) names += a.text.toString(); if (a is com.badlogic.gdx.scenes.scene2d.Group) for (c in a.children) collect(c) }
            collect(g.stage.root); log.append("флот: союзников ${g.allies.size}, кнопки: $names\n")
            repeat(2) { press("Нанять") } }
        step("таверна", 6) { press("Таверна") }
        step("таверна", 6) { repeat(4) { press("Нанять") }; press("Взять заказ") }
        step("таверна", 6) { log.append("офицеры: ${lv.crew.toList()} заказ=${lv.activeBounty}\n") }
        step("вид", 6) { press("Вид") }
        step("вид", 6) { repeat(3) { press("Купить") } }
        step("порт", 30) { press("Выйти в море") }
        step("порт", 5) { if (g.state != SeaGame.State.PLAY) issue("не вышли из порта: ${g.state}") }
        // база
        step("база", 60) { tp(lv.ringX, lv.ringZ) }
        step("база", 5) { if (g.state != SeaGame.State.PORT) issue("база не открылась") ; press("Основать") }
        for (k in 0 until 22) step("база", 3) { if (findButton("Построить") != null) press("Построить") else press("Улучшить") }
        step("база", 5) { log.append("постройки: ${lv.blev.toList()} доход=${lv.incomePerMin()}/мин\n"); press("Таверна") }
        step("база", 5) { press("Постройки"); press("Забрать") }
        step("база", 30) { press("Выйти в море") }
        // высадки: все исходы
        step("высадки", 5) {
            val acts = LANDINGS.flatMap { it.choices.map { c -> c.act } } + DIVE.choices.map { it.act } + "x_dig"
            val wild = g.islands.withIndex().filter { it.value.port == -1 }.map { it.index }
            for ((k, a) in acts.withIndex()) {
                try { val t = lv.resolve(a, wild[k % wild.size]); if (t.isBlank()) issue("пустой текст исхода $a") } catch (e: Exception) { issue("исход $a: $e") }
            }
            log.append("высадки: ${acts.size} исходов, кусков карты=${lv.mapPieces} цель=${lv.mapTarget}\n")
        }
        step("эпизод", 30) { g.menus.showEpisode(LANDINGS[0]) { lv.resolve(it, 0) } }
        step("эпизод", 10) { press("") }
        step("эпизод", 10) { press("Вернуться") }
        // регионы, погода, ночь
        for ((name, xz) in listOf("лёд" to (0f to -1500f), "огонь" to (1500f to 0f), "риф" to (-1500f to 0f), "бездна" to (0f to 1500f))) {
            step("регион $name", 90) { tp(xz.first, xz.second); g.stormTarget = 1f; g.stormLevel = 1f }
        }
        step("ночь", 120) { g.dayTime = 0.8f; g.stormTarget = 0f; g.stormLevel = 0f; tp(-300f, 300f); g.spawnEnemy("ghost", -260f, 260f).ghost = true }
        step("день", 60) { g.dayTime = 0.3f }
        step("водоворот", 120) { val w = lv.whirls.first(); tp(w.x, w.z + 30f) }
        step("айсберг", 90) { val b = lv.bergs.first(); tp(b.x, b.z + 20f) }
        step("туман", 60) { val fb = lv.fogBanks.first(); tp(fb[0], fb[1]) }
        // боссы
        step("кракен", 300) { tp(0f, 1300f); g.systems.spawnKraken(40f, 1250f) }
        step("змей", 300) { tp(-500f, -500f); g.spawnSerpent(-470f, -540f) }
        step("крепость", 300) { val f0 = g.systems.fortresses.first(); tp(f0.x + f0.r + 60f, f0.z) }
        step("охотник", 200) { tp(600f, 600f); lv.acceptBounty(1) }
        // гибель и возрождение
        step("гибель", 120) { if (g.state != SeaGame.State.PLAY) g.menus.close(); tp(200f, 200f); val p = g.player!!; log.append("перед гибелью: state=${g.state} hp=${p.hp} sinking=${p.sinking}\n"); g.damage(p, p.hp + 10f, null, false); log.append("после урона: hp=${p.hp} sinking=${p.sinking} таймер=${g.deathTimer}\n") }
        step("гибель", 5) { if (g.state != SeaGame.State.DEAD) issue("нет экрана гибели: ${g.state} hp=${g.player?.hp} тонет=${g.player?.sinking} таймер=${g.deathTimer} окна=${g.stage.root.children.size}"); press("Вернуться") }
        step("гибель", 30) { if (g.state != SeaGame.State.PLAY || g.player!!.sinking) issue("не возродились: ${g.state}") }
        // сохранение
        step("сохранение", 5) {
            val code = g.exportSave(); val coins = g.coins; val q = g.questIdx; val crew = lv.crew.toList()
            g.coins = 1; if (!g.importSave(code)) issue("код сохранения не принят")
            if (g.coins != coins || g.questIdx != q || lv.crew.toList() != crew) issue("после загрузки кода данные отличаются")
            log.append("код сохранения: ${code.length} символов\n")
        }
        // все меню
        step("меню", 5) { g.openPause() }
        for (b in listOf("Капитан", "Достижения", "Карта мира", "Песни команды", "Как играть", "Настройки")) {
            step("меню $b", 8) { press(b) }
            step("меню $b", 5) { if (findButton("Назад") != null) press("Назад") else if (findButton("Готово") != null) press("Готово") else g.menus.showPause() }
        }
        step("графика", 5) { press("Настройки") }
        for (k in 0 until 5) step("графика", 40) { press("Графика") }
        step("графика", 5) { press("Готово") }
        step("меню", 10) { press("Продолжить") }
        // сюжет: все задания
        step("задания", 5) {
            val keep = g.questIdx
            for (i in 0..STORY_LENGTH + 6) { g.questIdx = i; try { g.quest(); g.questTarget() } catch (e: Exception) { issue("задание $i: $e") } }
            g.questIdx = keep
        }
        // «обезьяна»: случайные касания и кнопки
        step("обезьяна", 1) { tp(150f, 150f) }
        for (k in 0 until 2400) step("обезьяна", 1) { monkey(k) }
    }

    private fun findButtonIn(pred: (com.badlogic.gdx.scenes.scene2d.ui.TextButton) -> Boolean): com.badlogic.gdx.scenes.scene2d.ui.TextButton? {
        fun find(a: com.badlogic.gdx.scenes.scene2d.Actor): com.badlogic.gdx.scenes.scene2d.ui.TextButton? {
            if (a is com.badlogic.gdx.scenes.scene2d.ui.TextButton && !a.isDisabled && pred(a)) return a
            if (a is com.badlogic.gdx.scenes.scene2d.Group) for (c in a.children) find(c)?.let { return it }
            return null
        }
        return find(g.stage.root)
    }

    private var monkeyDown = false
    private fun monkey(k: Int) {
        val w = g.wDp; val h = g.hDp
        if (g.state != SeaGame.State.PLAY) {
            // случайная кнопка меню
            val all = ArrayList<com.badlogic.gdx.scenes.scene2d.ui.TextButton>()
            fun collect(a: com.badlogic.gdx.scenes.scene2d.Actor) {
                if (a is com.badlogic.gdx.scenes.scene2d.ui.TextButton && !a.isDisabled) all += a
                if (a is com.badlogic.gdx.scenes.scene2d.Group) for (c in a.children) collect(c)
            }
            collect(g.stage.root)
            if (k % 7 == 0 && all.isNotEmpty()) all[rnd.nextInt(all.size)].toggle()
            if (k % 120 == 0 && g.state != SeaGame.State.DEAD) g.menus.close()
            return
        }
        when (rnd.nextInt(10)) {
            0, 1, 2 -> { if (!monkeyDown) { touch("down", rnd.nextFloat() * w, rnd.nextFloat() * h); monkeyDown = true } }
            3, 4, 5 -> if (monkeyDown) touch("drag", rnd.nextFloat() * w, rnd.nextFloat() * h)
            6 -> if (monkeyDown) { touch("up", rnd.nextFloat() * w, rnd.nextFloat() * h); monkeyDown = false }
            7 -> if (k % 40 == 0) spawnSide(listOf("sloop", "brig", "frigate")[rnd.nextInt(3)], 60f)
            else -> {}
        }
        if (k % 600 == 0) g.player?.let { it.hp = it.maxHp }
    }

    private fun betaTest(f: Int) {
        if (plan.isEmpty()) buildPlan()
        betaChecks(f)
        if (f >= waitUntil && planIdx < plan.size) {
            val (w, a) = plan[planIdx++]
            try { a() } catch (e: Exception) { issue("исключение: $e"); log.append(e.stackTraceToString().take(800) + "\n") }
            waitUntil = f + w
        } else if (planIdx >= plan.size && f >= waitUntil) {
            log.append("\n=== ИТОГ: шагов ${plan.size}, кадров $f, ${(System.currentTimeMillis() - t0) / 1000} с, мин. FPS $minFps, долгих кадров $spikes ===\n")
            if (issues.isEmpty()) log.append("Проблем не найдено\n") else for ((k, n) in issues) log.append("ПРОБЛЕМА ×$n: $k\n")
            log.append("kills=${g.kills} quest=${g.questIdx} coins=${g.coins} ships=${g.ships.size} level=${g.systems.level} ach=${g.systems.unlocked.size}\n")
            Gdx.files.absolute("$out/log.txt").writeString(log.toString(), false)
            shot("beta_end")
            Gdx.app.exit()
        }
    }

    private fun field(o: Any, name: String): Any? {
        var c: Class<*>? = o.javaClass
        while (c != null) { try { val f = c.getDeclaredField(name); f.isAccessible = true; return f.get(o) } catch (_: NoSuchFieldException) { c = c.superclass } }
        return null
    }

    private fun dumpShaders() {
        val dir = Gdx.files.absolute("$out/shaders"); dir.mkdirs()
        var n = 0
        fun save(name: String, p: com.badlogic.gdx.graphics.glutils.ShaderProgram) {
            Gdx.files.absolute("$out/shaders/$name.vert").writeString(p.vertexShaderSource, false)
            Gdx.files.absolute("$out/shaders/$name.frag").writeString(p.fragmentShaderSource, false)
            n++
        }
        for (bn in listOf("detailBatch", "plainBatch")) {
            val mb = field(g, bn) ?: continue
            val prov = field(mb, "shaderProvider") ?: continue
            val arr = field(prov, "shaders") as? com.badlogic.gdx.utils.Array<*> ?: continue
            for ((i, sh) in arr.withIndex()) (field(sh!!, "program") as? com.badlogic.gdx.graphics.glutils.ShaderProgram)?.let { save("${bn}_$i", it) }
        }
        run {
            val prov = field(field(g, "shadowBatch")!!, "shaderProvider")
            val arr = prov?.let { field(it, "shaders") } as? com.badlogic.gdx.utils.Array<*>
            arr?.forEachIndexed { i, sh -> (field(sh!!, "program") as? com.badlogic.gdx.graphics.glutils.ShaderProgram)?.let { save("depth_$i", it) } }
        }
        (field(field(g, "water")!!, "shader") as? com.badlogic.gdx.graphics.glutils.ShaderProgram)?.let { save("water", it) }
        val post = field(g, "post")!!
        for (k in listOf("bright", "blur", "composite")) (field(post, k) as? com.badlogic.gdx.graphics.glutils.ShaderProgram)?.let { save("post_$k", it) }
        log.append("dumped $n\n"); Gdx.files.absolute("$out/log.txt").writeString(log.toString(), false)
    }

    private var featE: Ship? = null
    private var featN: Ship? = null
    private fun prologueTest(f: Int) {
        // сценка идёт сама; снимки ключевых кадров, в конце — повтор из паузы
        if (f % 60 == 0 && f <= 4800 && g.state == SeaGame.State.CUTSCENE) shot("p%04d".format(f))
        if (f % 60 == 0) log.append("f=$f state=${g.state} ships=${g.ships.size} balls=${g.balls.size} fps=${Gdx.graphics.framesPerSecond}\n")
        if (f > 60 && g.state != SeaGame.State.CUTSCENE && done < 0) { done = f; shot("p_after"); log.append("после сценки: state=${g.state} ships=${g.ships.size}\n") }
        if (done > 0 && f == done + 20) press("Вступить")
        if (done > 0 && f == done + 25) press("Забрать")
        if (done > 0 && f == done + 30) { g.menus.close(); g.openPause() }
        if (done > 0 && f == done + 35) press("Предыстория")
        if (done > 0 && f == done + 120) { shot("p_replay"); val (sx, sy) = g.wDp - 60f to 30f; touch("down", sx, sy) }
        if (done > 0 && f == done + 140) { shot("p_back"); log.append("после пропуска: state=${g.state} ships=${g.ships.size} player=${g.player?.x?.toInt()},${g.player?.z?.toInt()}\n")
            Gdx.files.absolute("$out/log.txt").writeString(log.toString(), false); Gdx.app.exit() }
        if (f > 9000) { Gdx.files.absolute("$out/log.txt").writeString(log.toString(), false); Gdx.app.exit() }
    }
    private var done = -1

    private fun featTest(f: Int) {
        val ex = g.extras
        val p = g.player
        when (f) {
            3 -> press("Вступить"); 5 -> press("Забрать"); 8 -> g.menus.close()
            10 -> { g.coins = 50000; g.gems = 100; g.openPort(0) }
            14 -> press("Вид")
            20 -> press("Флаг")
            30 -> shot("f01_flag_editor")
            31 -> press("Череп")
            36 -> { press("Поднять") }
            40 -> press("Паруса")
            46 -> press("Эмблема")
            52 -> shot("f02_sail_editor")
            53 -> press("Поставить")
            100 -> shot("f03_ship_flag")
            101 -> { g.menus.close(); g.closePort() }
            110 -> { tp(300f, -300f); p!!.h = 0f; featE = spawnSide("brig", 28f).also { it.hp = it.maxHp * 0.4f }; g.systems.throwGrapples(featE!!) }
            200 -> shot("f04_boarding")
            201 -> press("Натиск"); 215 -> press("Залп"); 230 -> press("Оборона")
            260 -> shot("f05_board_result")
            261 -> { for (b in listOf("Отпустить", "Продолжить")) if (findButton(b) != null) { press(b); break }; if (g.state != SeaGame.State.PLAY) g.menus.close() }
            270 -> { tp(300f, -750f); p!!.hp = p.maxHp * 0.2f
                repeat(2) { k -> g.spawnEnemy("brig", p.x + 40f + k * 20f, p.z + 30f).also { it.ai = AiState.ATTACK; it.target = p } } }
            285 -> { log.append("пираты: " + g.ships.filter { it.faction == Faction.PIRATE }.joinToString { "${it.type} d=${Math.hypot((it.x - p!!.x).toDouble(), (it.z - p.z).toDouble()).toInt()} ai=${it.ai} sink=${it.sinking}" } + " state=${g.state} combat=${g.combatT}\n") }
            in 271..299 -> { p!!.hp = p.maxHp * 0.2f; for (s in g.ships) if (s.faction == Faction.PIRATE) { s.ai = AiState.ATTACK; s.target = p } }
            300 -> { shot("f06_white_flag_btn"); log.append("flag=${ex.flagAvailable} price=${ex.ransomPrice()}\n"); val (fx, fy) = ex.flagPos(); touch("down", fx, g.hDp - fy); touch("up", fx, g.hDp - fy) }
            310 -> shot("f07_white_flag_dialog")
            311 -> press("Откупиться")
            330 -> { log.append("после откупа: монеты=${g.coins} атакуют=${g.ships.count { it.faction == Faction.PIRATE && it.ai == AiState.ATTACK }}\n") }
            340 -> { tp(700f, -700f); p!!.hp = p.maxHp; featE = spawnSide("sloop", 30f).also { it.surrendered = true; it.hp = it.maxHp * 0.2f } }
            360 -> { shot("f08_surrender"); log.append("action=${g.living.actionText}\n"); val (ax, ay) = g.living.actionPos(); touch("down", ax, g.hDp - ay); touch("up", ax, g.hDp - ay) }
            370 -> shot("f09_surrender_dialog")
            371 -> press("выкуп")
            380 -> { shot("f10_surrender_result"); press("Продолжить") }
            390 -> { tp(-800f, 500f); g.combatT = 0f; ex.morale = 5f; ex.mutinyCd = 0f }
            410 -> log.append("мораль=${ex.morale} state=${g.state} combat=${g.combatT} cd=${ex.mutinyCd}\n")
            420 -> { shot("f11_mutiny"); press("ром") }
            430 -> { shot("f12_mutiny_result"); press("Продолжить") }
            440 -> { g.systems.cargo[1] = 8; g.systems.cargo[3] = 4; tp(900f, 900f); g.damage(p!!, p.hp + 10f, null, false) }
            520 -> { shot("f13_death"); log.append("lost=${ex.lost != null} coins=${ex.lostCoins()}\n"); press("Вернуться") }
            530 -> { g.menus.showMap() }
            545 -> { shot("f14_map_lost"); g.menus.close() }
            550 -> { val l = ex.lost!!; tp(l.x + 60f, l.z) }
            600 -> { shot("f15_lost_cargo"); log.append("падальщиков: ${g.ships.count { it.faction == Faction.PIRATE }}\n"); val l = ex.lost; if (l != null) tp(l.x, l.z) }
            620 -> { log.append("груз поднят=${ex.lost == null} трюм=${g.systems.cargo.toList()}\n") }
            630 -> { ex.owned.addAll(ARTIFACTS.indices); ex.equipped.clear(); ex.equipped.addAll(listOf(0, 5, 10)); g.rebuildPlayer(); g.openPause() }
            640 -> press("Капитан")
            655 -> shot("f16_artifacts")
            660 -> { g.menus.close(); ex.morale = 82f; g.systems.cargo[0] = 5 }
            700 -> shot("f17_hud_morale")
            720 -> { g.openPort(0) }
            726 -> press("Таверна")
            760 -> shot("f18_guilds")
            761 -> press("Рынок")
            790 -> { shot("f19_contract"); press("Взять контракт") }
            795 -> { log.append("контракт=${g.guilds.contract?.let { "${it.amount} ${GOODS[it.good].name} -> ${PORTS[it.port].name}" }}\n"); val c = g.guilds.contract!!
                g.systems.cargo[c.good] = c.amount; g.guilds.onSell(c.port, c.good, c.amount); log.append("контрактов=${g.guilds.contractsDone}\n") }
            800 -> { g.menus.close(); g.closePort(); g.kills = 60; g.guilds.contractsDone = 5 }
            900 -> { log.append("открыты капитаны: ${g.guilds.unlocked} ранги: ${GUILDS.indices.map { g.guilds.rankTitle(it) }}\n"); g.openPause() }
            905 -> press("Капитан")
            915 -> press("Капитаны")
            930 -> shot("f20_heroes")
            931 -> { g.menus.close(); val pt = g.ports[9]; tp(pt.rx, pt.rz + 40f); g.player!!.h = 0f }
            990 -> { shot("f21_lugan"); g.openPort(9) }
            1040 -> { shot("f22_lugan_port"); log.append("порт: ${PORTS[9].name}\n") }
            1050 -> { g.menus.close(); g.closePort(); g.guilds.join(2, true); tp(-300f, -300f); g.player!!.h = 0f
                featE = g.spawnNeutral("merchant", g.player!!.x + 35f, g.player!!.z).also { it.h = 0f; it.wx = 1500f; it.wz = 1500f }
                g.damage(featE!!, 5f, g.player, false) }
            1060 -> { val n = g.spawnNeutral("navy", g.player!!.x - 60f, g.player!!.z + 20f); g.damage(n, 5f, g.player, false); featN = n }
            1250 -> { shot("f23_pirate_attack"); log.append("купец: hp=${featE?.hp?.toInt()} тонет=${featE?.sinking} спровоцирован=${featE?.provoked}; патруль: ai=${featN?.ai} цель-игрок=${featN?.target === g.player}; kills=${g.kills} репутация=${g.living.rep.toList()}\n") }
            1260 -> { Gdx.files.absolute("$out/log.txt").writeString(log.toString(), false); Gdx.app.exit() }
        }
    }

    /** Нажатие на вкладку порта: ищем кнопку по тексту. */
    private fun clickTab(i: Int) {
        val names = listOf("Верфь", "Улучш.", "Флот", "Вид")
        fun find(a: com.badlogic.gdx.scenes.scene2d.Actor): com.badlogic.gdx.scenes.scene2d.ui.TextButton? {
            if (a is com.badlogic.gdx.scenes.scene2d.ui.TextButton && a.text.toString() == names[i]) return a
            if (a is com.badlogic.gdx.scenes.scene2d.Group) for (c in a.children) find(c)?.let { return it }
            return null
        }
        val b = find(g.stage.root) ?: run { log.append("tab not found\n"); return }
        b.toggle()
    }
}
