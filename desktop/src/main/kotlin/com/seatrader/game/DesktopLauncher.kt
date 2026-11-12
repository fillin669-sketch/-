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
        setWindowedMode(412, 870)
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

    private fun step() {
        frame++
        if (base < 0) {
            if (g.state == SeaGame.State.LOADING) return
            base = frame
            t0 = System.currentTimeMillis()
            log.append("loaded at frame $frame state=${g.state}\n")
        }
        val f = frame - base
        when (f) {
            2 -> shot("00_daily")
            4 -> { g.menus.close() }
            20 -> { shot("01_start"); info("start") }
            30 -> { touch("down", 206f, 600f); touch("drag", 206f, 690f) }
            190 -> { shot("02_sailing"); info("sailing") }
            191 -> touch("up", 206f, 690f)
            200 -> {
                val p = g.player!!
                p.x = 250f; p.z = 260f; p.inPort = false; p.vx = 0f; p.vz = 0f
                clearArea()
                g.spawnEnemy("sloop", 290f, 240f).ai = AiState.ATTACK
                g.spawnEnemy("brig", 220f, 300f).ai = AiState.ATTACK
                g.useAbility(0)
                touch("down", 200f, 600f); touch("drag", 260f, 560f)
            }
            262 -> { shot("03_battle"); info("battle") }
            420 -> { shot("04_battle2"); info("battle2") }
            421 -> { touch("up", 260f, 560f); g.useAbility(1); g.useAbility(2) }
            500 -> { info("after abilities") }
            510 -> {
                clearArea()
                val p = g.player!!
                g.spawnSerpent(p.x + 30f, p.z - 40f)
                touch("down", 206f, 600f); touch("drag", 150f, 600f)
            }
            620 -> { shot("05_serpent"); info("serpent") }
            621 -> touch("up", 150f, 600f)
            630 -> {
                g.serpent = null
                g.stormTarget = 1f; g.stormLevel = 1f
                touch("down", 206f, 600f); touch("drag", 206f, 520f)
            }
            700 -> { shot("06_storm"); info("storm") }
            701 -> touch("up", 206f, 520f)
            710 -> {
                g.stormTarget = 0f; g.stormLevel = 0f
                g.dayTime = 0.75f
                val port = g.ports[0]
                val p = g.player!!
                p.x = port.rx + 30f; p.z = port.rz + 40f; p.inPort = false
            }
            760 -> { shot("07_night"); info("night") }
            770 -> { g.dayTime = 0.45f }
            790 -> { shot("08_dusk") }
            800 -> { g.dayTime = 0.28f; g.coins = 20000; g.gems = 50; g.openPort(0) }
            815 -> shot("09_port_ships")
            820 -> { clickTab(2) }
            835 -> shot("10_port_fleet")
            840 -> { clickTab(3) }
            855 -> shot("11_port_paint")
            860 -> { g.allies += "merc_brig"; g.allies += "merc_sloop"; g.sailIdx = 1; g.rebuildPlayer(); g.closePort() }
            870 -> { val p = g.player!!; p.inPort = false; touch("down", 206f, 600f); touch("drag", 206f, 690f) }
            990 -> { shot("12_fleet_sailing"); info("fleet"); touch("up", 206f, 690f) }
            1000 -> { g.openPause() }
            1010 -> shot("13_pause")
            1020 -> { g.menus.relayout() }
            1030 -> {
                g.menus.close()
                g.damage(g.player!!, 99999f, null, false)
            }
            1180 -> { shot("14_death"); info("death") }
            1190 -> {
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
