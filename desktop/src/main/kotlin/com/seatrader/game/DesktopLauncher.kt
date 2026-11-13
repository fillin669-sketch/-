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
        when (f) {
            2 -> shot("00_daily")
            4 -> { press("Забрать") }
            10 -> { press("швартовы") }
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
            541 -> press("Выйти в море")
            580 -> { shot("10_left_port"); info("left") }
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
