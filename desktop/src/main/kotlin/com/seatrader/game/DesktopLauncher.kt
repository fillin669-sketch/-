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
            10 -> { shot("01_journal"); info("journal") }
            12 -> { press("швартовы") }
            20 -> { shot("02_start") }
            25 -> { touch("down", 206f, 600f); touch("drag", 206f, 690f) }
            120 -> { touch("up", 206f, 690f) }
            // ручной прицел
            130 -> {
                tp(250f, 260f)
                val p = g.player!!; p.h = 0f
                val e = g.spawnEnemy("brig", 290f, 260f); e.speed; e.ai = AiState.WANDER
                g.systems.autoFire = false
                val (ax, ay) = g.hud.aimPos()
                touch("down", ax, g.hDp - ay)
            }
            132 -> { val (ax, ay) = g.hud.aimPos(); touch("drag", ax + 44f, g.hDp - ay) }
            150 -> { shot("03_aim") }
            151 -> { val (ax, ay) = g.hud.aimPos(); touch("up", ax + 44f, g.hDp - ay) }
            200 -> { shot("04_manual_fire"); info("manual"); g.systems.autoFire = true }
            // абордаж
            210 -> {
                tp(250f, 260f)
                val p = g.player!!; p.h = 0f
                val e = g.spawnEnemy("sloop", 262f, 260f); e.hp = 10f; e.h = 0f
            }
            230 -> { shot("05_board_button"); log.append("boardTarget=${g.systems.boardTarget?.type}\n") }
            231 -> { g.systems.boardTarget?.let { g.menus.showBoarding(it) } }
            240 -> shot("06_boarding")
            241 -> press("Удар")
            250 -> press("Удар")
            260 -> press("Удар")
            300 -> { shot("07_board_result") }
            301 -> { if (findButton("Захватить") != null) press("Захватить") else if (findButton("Разграбить") != null) press("Разграбить") else press("Продолжить") }
            310 -> info("after board")
            // мортира, ракеты, мины
            320 -> {
                tp(250f, 260f)
                g.systems.ownedWeapons += listOf(3, 4, 5)
                val p = g.player!!; p.h = 0f
                g.spawnEnemy("brig", 250f, 200f).ai = AiState.WANDER
                g.spawnEnemy("sloop", 290f, 230f).ai = AiState.WANDER
                g.useAbility(3); g.useAbility(4); g.useAbility(5)
            }
            345 -> { shot("08_weapons"); info("weapons") }
            // крепость
            400 -> { tp(-1250f, 690f); g.player!!.h = 0f }
            460 -> { shot("09_fortress"); info("fortress") }
            461 -> { g.abilityCd[3] = 0f; g.useAbility(3) }
            // риф
            560 -> { tp(-1400f, -100f) }
            640 -> { shot("10_reef"); info("reef") }
            // лёд
            650 -> { tp(60f, -1350f) }
            730 -> { shot("11_ice") }
            // огонь
            740 -> { tp(1350f, 60f) }
            820 -> { shot("12_fire") }
            // бездна и кракен
            830 -> { tp(40f, 1300f); g.systems.spawnKraken(70f, 1240f) }
            960 -> { shot("13_kraken"); info("kraken") }
            970 -> { g.systems.kraken = null; tp(300f, 300f); val fl = g.spawnEnemy("flagship", 330f, 220f); repeat(3) { k -> g.spawnEnemy("brig", 310f + k * 18f, 245f).leader = fl } }
            1060 -> { shot("14_flotilla"); info("flotilla") }
            1070 -> {
                tp(100f, 200f)
                val m = g.spawnNeutral("merchant", 140f, 150f); m.wx = 0f; m.wz = 0f
                g.spawnEnemy("sloop", 180f, 140f).also { it.ai = AiState.ATTACK; it.target = m }
            }
            1180 -> { shot("15_protect"); info("protect") }
            1190 -> { g.coins = 20000; g.gems = 50; g.openPort(1) }
            1200 -> { press("Рынок") }
            1210 -> shot("16_market")
            1215 -> { press("+5") }
            1220 -> { press("Арсенал") }
            1230 -> shot("17_arsenal")
            1240 -> { g.menus.close(); g.systems.skillPts = 3; g.openPause() }
            1250 -> shot("18_pause")
            1255 -> press("Капитан")
            1265 -> shot("19_captain")
            1270 -> press("Назад")
            1275 -> press("Достижения")
            1285 -> shot("20_achievements")
            1290 -> press("Назад")
            1295 -> press("Карта мира")
            1305 -> shot("21_map")
            1310 -> press("Назад")
            1315 -> press("Настройки")
            1325 -> shot("22_settings")
            1330 -> {
                val code = g.exportSave()
                log.append("save code length=${code.length} import=${g.importSave(code)}\n")
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
