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
            20 -> { g.coins = 60000; g.gems = 60; val u = g.ensureUp("sloop"); u[0] = 2; u[2] = 2; g.rebuildPlayer(); g.openPort(0) }
            25 -> press("Улучшения")
            40 -> shot("01_upgrades")
            41 -> { g.ensureUp("sloop").fill(5); g.rebuildPlayer(); press("Рынок") }
            43 -> press("Улучшения")
            60 -> shot("02_upgrades_max")
            61 -> press("Рынок")
            75 -> shot("03_market")
            80 -> press("Таверна")
            95 -> shot("04_tavern")
            96 -> press("Нанять")
            110 -> shot("05_tavern_hired")
            111 -> press("Взять заказ")
            115 -> press("Выйти в море")
            125 -> tp(lv.ringX, lv.ringZ)
            140 -> shot("06_base_new")
            141 -> press("Основать")
            145 -> { lv.blev.fill(3); lv.refreshBuildings(); lv.bank = 900f; press("Постройки") }
            160 -> shot("07_base_menu")
            161 -> press("Выйти в море")
            165 -> { tp(lv.baseX + 20f, lv.baseZ + lv.baseR + 75f); g.player!!.h = -0.4f }
            185 -> { shot("08_base_view"); info("base") }
            200 -> {
                val i = g.islands.indexOfFirst { it.port == -1 && g.systems.fortresses.none { f -> Math.hypot((f.x - it.x).toDouble(), (f.z - it.z).toDouble()) < 80 } }
                val isl = g.islands[i]; tp(isl.x, isl.z + isl.r + 14f); g.player!!.h = 0f
                log.append("landing island $i action=${lv.actionText}\n")
            }
            210 -> { shot("09_landing_button"); log.append("action=${lv.actionText}\n") }
            211 -> { val (ax, ay) = lv.actionPos(); touch("down", ax, g.hDp - ay); touch("up", ax, g.hDp - ay) }
            225 -> shot("10_episode")
            226 -> press("")
            240 -> shot("11_episode_result")
            241 -> press("Вернуться")
            245 -> { val w = lv.whirls.first(); tp(w.x, w.z + 45f) }
            275 -> { shot("12_whirlpool"); info("whirl") }
            280 -> { val fb = lv.fogBanks.first(); tp(fb[0], fb[1]) }
            310 -> { shot("13_fog"); log.append("fogK=${lv.fogK}\n") }
            315 -> { val b = lv.bergs.first(); tp(b.x, b.z + 30f) }
            340 -> { shot("14_iceberg"); info("berg") }
            345 -> { val r = lv.ruins.first(); tp(r.x, r.z + 18f) }
            360 -> { shot("15_ruins"); log.append("ruin action=${lv.actionText}\n") }
            365 -> { g.dayTime = 0.8f; tp(-300f, 300f); val p = g.player!!; g.spawnEnemy("ghost", p.x + 30f, p.z - 60f).ghost = true }
            400 -> shot("16_ghost")
            405 -> { g.dayTime = 0.3f; tp(200f, -200f); g.allies.clear(); g.allies += MERCS[0].id; g.allies += MERCS[1].id; g.spawnAllies() }
            420 -> { val (fx, fy) = lv.fleetPos(1); touch("down", fx, g.hDp - fy); touch("up", fx, g.hDp - fy) }
            425 -> { val id = lv.crew.firstOrNull { it >= 0 } ?: 0; lv.say(id, OFFICERS[id].quips[1]) }
            435 -> { shot("17_fleet_talk"); log.append("order=${lv.order} crew=${lv.crew.toList()} bounty=${lv.activeBounty}\n") }
            440 -> { tp(150f, -250f) }
            520 -> { shot("18_hunter"); info("hunter"); log.append("hunters=${g.ships.filter { it.bounty >= 0 }.map { it.name }}\n") }
            530 -> {
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
