package com.seatrader.game

import com.badlogic.gdx.Gdx
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
        if (System.getenv("SEA_LANDSCAPE") != null) setWindowedMode(870, 412) else setWindowedMode(412, 870)
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

    fun install() { g.afterRender = { step() } }

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
        if (System.getenv("SEA_AI") != null) { aiTest(f); return }
        if (System.getenv("SEA_ALLY") != null) { allyTest(f); return }
        if (System.getenv("SEA_LEASH") != null) { leashTest(f); return }
        when (f) {
            2 -> shot("00_hero")
            3 -> press("Выбрать: Hope")
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
            2 -> press("Выбрать: Fin")
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
            2 -> press("Выбрать: Fin")
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
            2 -> press("Выбрать: Fin")
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
