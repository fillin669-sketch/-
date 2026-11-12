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
        setTitle("Морские волки")
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
        when (f) {
            2 -> shot("00_daily")
            4 -> { press("Забрать") }
            10 -> { press("швартовы") }
            20 -> { shot("01_start_shadows") }
            25 -> { touch("down", 206f, 600f); touch("drag", 206f, 690f) }
            140 -> { shot("02_sailing"); info("sailing"); log.append("shanty idx=${g.sfx.shantyIdx} want=${g.systems.wantedShanty()}\n") }
            141 -> { touch("up", 206f, 690f) }
            // абордаж: ослабленный пират рядом
            150 -> {
                tp(250f, 260f)
                val p = g.player!!; p.h = 0f
                val e = g.spawnEnemy("sloop", 285f, 260f); e.hp = 25f; e.h = 0f; e.ai = AiState.WANDER
            }
            175 -> { shot("03_board_ready"); log.append("boardTarget=${g.systems.boardTarget?.type}\n") }
            176 -> { val (bx, by) = g.hud.boardPos(); touch("down", bx, g.hDp - by); touch("up", bx, g.hDp - by) }
            190 -> shot("04_grapple")
            260 -> { shot("05_boarding"); log.append("state after grapple=${g.state}\n") }
            261 -> press("Удар")
            270 -> press("Удар")
            280 -> press("Удар")
            320 -> shot("06_board_result")
            321 -> { if (findButton("Захватить") != null) press("Захватить") else if (findButton("Разграбить") != null) press("Разграбить") else press("Продолжить") }
            // бутылка с песней
            330 -> { val bt = g.systems.bottles.firstOrNull(); if (bt != null) tp(bt.x + 4f, bt.z) }
            345 -> { shot("07_bottle"); log.append("shanties=${g.systems.shanties}\n") }
            // ночь и тени
            360 -> { tp(30f, 120f); g.dayTime = 0.24f }
            420 -> shot("08_morning_shadows")
            // карта мира через мини-карту
            430 -> { val (mx, my) = Pair(g.wDp - 12 - 62f, g.hDp - 112f - 70f - 16 - 62f); log.append("minimap hit=${g.hud.minimapHit(mx, my)}\n"); g.menus.showMap() }
            445 -> shot("09_world_map")
            450 -> { g.systems.waypoint = floatArrayOf(400f, -300f); g.menus.close() }
            470 -> shot("10_waypoint")
            480 -> { g.openPause() }
            490 -> shot("11_pause")
            495 -> press("Песни")
            505 -> shot("12_songs")
            510 -> press("Назад")
            515 -> press("Как играть")
            525 -> shot("13_help")
            530 -> press("Назад")
            535 -> { g.menus.close(); g.stormTarget = 1f; g.stormLevel = 1f; tp(-300f, 300f) }
            620 -> { shot("14_storm"); info("storm") }
            630 -> {
                log.append("frames=$frame seconds=${(System.currentTimeMillis() - t0) / 1000.0} explored=${g.systems.explored.percent()}%\n")
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
