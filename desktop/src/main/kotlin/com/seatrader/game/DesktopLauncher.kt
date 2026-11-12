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
        log.append("$tag state=${g.state} p=(${p.x.toInt()},${p.z.toInt()}) speed=${"%.1f".format(p.speed)} hp=${p.hp.toInt()}/${p.maxHp.toInt()} coins=${g.coins} gems=${g.gems} quest=${g.questIdx} kills=${g.kills} loot=${g.loot.size} fps=${Gdx.graphics.framesPerSecond}\n")
        for (s in g.ships) if (s.faction == Faction.PIRATE && Math.hypot((s.x - p.x).toDouble(), (s.z - p.z).toDouble()) < 150)
            log.append("   pirate ${s.type} hp=${s.hp.toInt()} d=${Math.hypot((s.x - p.x).toDouble(), (s.z - p.z).toDouble()).toInt()} ai=${s.ai}\n")
    }

    private fun step() {
        frame++
        if (frame == 1) t0 = System.currentTimeMillis()
        when (frame) {
            30 -> { shot("01_start"); info("start") }
            40 -> { touch("down", 206f, 600f); touch("drag", 206f, 690f) }
            200 -> { shot("02_sailing"); info("sailing") }
            201 -> touch("up", 206f, 690f)
            210 -> {
                val p = g.player!!
                p.x = 250f; p.z = 260f; p.inPort = false
                for (s in g.ships) if (s.faction == Faction.PIRATE) s.x += 3000f
                g.spawnEnemy("sloop", 290f, 240f).ai = AiState.ATTACK
                g.spawnEnemy("brig", 220f, 300f).ai = AiState.ATTACK
                touch("down", 200f, 600f); touch("drag", 260f, 560f)
            }
            330 -> { shot("03_battle"); info("battle") }
            600 -> { touch("up", 260f, 560f); shot("04_after"); info("after") }
            610 -> {
                val p = g.player!!
                for (s in g.ships) if (s.faction == Faction.PIRATE) s.x += 3000f
                val e = g.spawnEnemy("sloop", p.x, p.z - 45f)
                e.speed = 0f
                p.h = 0f; p.desired = 0f
                touch("down", 206f, 600f); touch("drag", 206f, 520f)
                g.tryBoost()
            }
            640 -> shot("05_ram")
            760 -> { touch("up", 206f, 520f); info("after ram") }
            770 -> { g.coins = 5000; g.openPort(0) }
            790 -> shot("06_port")
            800 -> { g.damage(g.player!!, 1f, null, false) }
            820 -> {
                g.stage.clear(); g.state = SeaGame.State.PLAY
                g.openPause()
            }
            840 -> shot("07_pause")
            850 -> { g.stage.clear(); g.state = SeaGame.State.PLAY; g.damage(g.player!!, 99999f, null, false) }
            1000 -> { shot("08_death"); info("death") }
            1010 -> {
                log.append("frames=$frame seconds=${(System.currentTimeMillis() - t0) / 1000.0}\n")
                Gdx.files.absolute("$out/log.txt").writeString(log.toString(), false)
                Gdx.app.exit()
            }
        }
    }
}
