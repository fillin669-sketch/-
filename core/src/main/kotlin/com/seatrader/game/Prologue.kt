package com.seatrader.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Align
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Предыстория: сценки на игровом движке. Юнга с брига «Надежда» попадает под залпы
 * адмирала Гарпуна, а капитаны Fin, Mihael и Hope спасают его и дарят шлюп «Чайка».
 */
class Prologue(private val g: SeaGame) {
    private class Shot(
        val who: String, val text: String, val dur: Float, val cut: Boolean,
        val start: () -> Unit, val tick: (Float) -> Unit, val cam: (Float) -> Unit,
    )

    var active = false
        private set
    /** Идёт бой — играет боевая музыка. */
    var battle = false
        private set
    private var first = true
    private var idx = 0
    private var t = 0f
    private var total = 0f
    private var fade = 1f
    private var fadeOut = false
    private var bars = 0f
    private val shots = ArrayList<Shot>()

    // камера
    private val camP = Vector3(); private val camL = Vector3()
    private val wantP = Vector3(); private val wantL = Vector3()
    private var snap = true
    private var follow = 2f

    // актёры
    private var cx = 0f; private var cz = 0f
    private lateinit var merchant: Ship
    private lateinit var harpoon: Ship
    private var boat: Ship? = null
    private var fin: Ship? = null
    private var mihael: Ship? = null
    private var hope: Ship? = null
    private var gull: Ship? = null
    private var rx = 0f; private var rz = 0f; private var ox = 0f; private var oz = 1f

    private val faces = HashMap<String, TextureRegion?>()

    fun start(first: Boolean) {
        this.first = first
        active = true
        idx = -1; total = 0f; fade = 1f; fadeOut = false; bars = 0f; battle = false
        boat = null; fin = null; mihael = null; hope = null; gull = null
        pickLocation()
        buildShots()
        setNight()
        merchant = g.cutShip("«Надежда»", ENEMIES.getValue("merchant").look, Faction.NEUTRAL, cx, cz, H_EAST, 10f, 0)
        harpoon = g.cutShip("«Кровавая Луна»", ENEMIES.getValue("admiral").look, Faction.PIRATE, cx - 200f, cz - 150f, H_EAST, 13.5f, 12)
        next()
    }

    private fun pickLocation() {
        val p = g.player
        // всегда одно и то же место: самая открытая вода из фиксированного набора точек
        val r = Random(77)
        for (margin in floatArrayOf(300f, 260f, 220f, 190f, 150f, 110f)) repeat(220) {
            val x = r.nextFloat() * WORLD * 1.5f - WORLD * 0.75f; val z = r.nextFloat() * WORLD * 1.5f - WORLD * 0.75f
            if (g.isWaterPublic(x, z, margin) && g.isWaterPublic(x - 150f, z - 120f, 40f) && g.isWaterPublic(x + 140f, z - 20f, 40f)) { cx = x; cz = z; return }
        }
        cx = p?.x ?: 0f; cz = p?.z ?: 0f
    }

    private fun setNight() {
        g.dayTime = 0.75f
        g.stormLevel = 0.5f; g.stormTarget = 0.5f
        g.living.fogK = 0.3f
    }

    // ---------- Помощники ----------
    private fun place(s: Ship, x: Float, z: Float, h: Float, v: Float, thr: Float = 0.45f) {
        s.x = x; s.z = z; s.h = h; s.desired = h
        s.vx = sin(h) * v; s.vz = -cos(h) * v; s.angVel = 0f
        s.throttle = thr
    }

    private fun steer(s: Ship, x: Float, z: Float, thr: Float) {
        s.desired = atan2(x - s.x, -(z - s.z))
        s.throttle = thr
    }

    private fun fwdX(s: Ship) = sin(s.h)
    private fun fwdZ(s: Ship) = -cos(s.h)

    /** Залп по цели тем бортом, к которому она ближе. */
    private fun broadside(s: Ship, o: Ship, n: Int) {
        val rxv = cos(s.h); val rzv = sin(s.h)
        val side = if ((o.x - s.x) * rxv + (o.z - s.z) * rzv >= 0) 1f else -1f
        for (i in 0 until n) {
            val off = (if (n == 1) 0f else i.toFloat() / (n - 1) - 0.5f) * s.len * 0.55f
            val sx = s.x + rxv * side * s.width * 0.6f + fwdX(s) * off
            val sz = s.z + rzv * side * s.width * 0.6f + fwdZ(s) * off
            g.launchBall(s, s.faction, sx, s.y + s.deckTop + 0.3f, sz, o.x, o.z, o.vx, o.vz, 10f, Ammo.NORMAL, i * 0.08f + rand(0f, 0.06f), 0.7f)
        }
        s.rollVel -= side * 0.25f * n / (4f * s.stats.mass)
        g.sfx.play("cannon", 1f)
        g.shake = maxOf(g.shake, 0.15f)
    }

    private fun look(px: Float, py: Float, pz: Float, lx: Float, ly: Float, lz: Float) {
        wantP.set(px, py, pz); wantL.set(lx, ly, lz)
    }

    private fun orbit(s: Ship, a: Float, dist: Float, hgt: Float, ly: Float = 4f) =
        look(s.x + sin(a) * dist, hgt, s.z + cos(a) * dist, s.x, ly, s.z)

    // ---------- Сценарий ----------
    private fun buildShots() {
        shots.clear()
        // 0. Ночь, туман, торговый бриг
        shots += Shot("", "Двенадцать лет назад. Северные воды.\nТорговый бриг «Надежда» везёт груз в Гавань Ветров. Юнга на его борту — это вы.", 7f, true,
            { place(merchant, cx, cz, H_EAST, 4.5f) }, { },
            { tt -> orbit(merchant, -0.9f + tt * 0.06f, 46f, 15f) })
        // 1. Из тумана выходит «Кровавая Луна»
        shots += Shot("", "Той ночью из тумана вышел корабль под чёрно-красными парусами. Его звали «Кровавая Луна».", 6.5f, true,
            {
                place(harpoon, merchant.x - 95f, merchant.z - 70f, H_EAST + 0.55f, 7f, 0.6f)
            },
            { steer(harpoon, merchant.x + 10f, merchant.z - 34f, 0.6f) },
            { tt ->
                val h = harpoon
                look(h.x + fwdX(h) * 48f - fwdZ(h) * 18f, 2.6f + tt * 0.25f, h.z + fwdZ(h) * 48f + fwdX(h) * 18f, h.x, 9f, h.z)
            })
        // 2. «Огонь!»
        var fired2 = 0
        shots += Shot("harpoon", "«Кровавая Луна» не оставляет свидетелей. Огонь!", 6f, true,
            {
                fired2 = 0; battle = true
                place(harpoon, merchant.x - 4f, merchant.z - 32f, H_EAST, 5f)
            },
            { tt ->
                steer(harpoon, harpoon.x + 50f, merchant.z - 30f, 0.42f)
                if (tt > 1.6f && fired2 == 0) { fired2 = 1; broadside(harpoon, merchant, 10) }
                if (tt > 3.9f && fired2 == 1) { fired2 = 2; broadside(harpoon, merchant, 10) }
            },
            { _ ->
                val h = harpoon
                look(h.x - 44f, 15f, h.z - 26f, merchant.x + 6f, 3f, (h.z + merchant.z) / 2 + 6f)
            })
        // 3. «Надежда» тонет
        var st3 = 0
        shots += Shot("", "Бриг «Надежда» пошёл ко дну за считаные минуты.", 5.5f, true,
            { st3 = 0 },
            { tt ->
                steer(harpoon, harpoon.x + 50f, merchant.z - 30f, 0.35f)
                if (tt > 0.3f && st3 == 0) { st3 = 1; broadside(harpoon, merchant, 12) }
                if (tt > 1.7f && st3 == 1) {
                    st3 = 2
                    merchant.burnT = 10f; merchant.burnDps = 0f
                    g.cutSink(merchant)
                }
                if (tt > 2.6f && st3 == 2) {
                    st3 = 3
                    boat = g.cutShip("Шлюпка", BOAT_LOOK, Faction.NEUTRAL, merchant.x + 5f, merchant.z + 11f, H_EAST + 0.4f, 3f, 0).also {
                        it.mastHp[0] = 0f; it.throttle = 0f
                    }
                }
            },
            { _ -> look(merchant.x - 14f, 6.5f, merchant.z + 40f, merchant.x, 2.5f, merchant.z) })
        // 4. Юнга в шлюпке
        shots += Shot("", "Юнга уцепился за разбитую шлюпку. А «Кровавая Луна» развернулась, чтобы добить его.", 6f, true,
            {
                val b = boat ?: return@Shot
                place(harpoon, b.x - 12f, b.z - 70f, PI.toFloat(), 4f, 0.3f)
            },
            { _ -> boat?.let { steer(harpoon, it.x, it.z, 0.3f) } },
            { tt ->
                val b = boat ?: merchant
                look(b.x + 4f, 4.2f + tt * 0.1f, b.z + 17f, b.x - 3f, 3.5f + tt * 0.5f, b.z - 24f)
            })
        // 5. Fin
        shots += Shot("fin", "Держись, юнга! Ветер на нашей стороне — я тебя не брошу!", 6f, true,
            {
                val b = boat ?: merchant
                fin = g.cutShip("«Попутный ветер»", FIN_LOOK, Faction.PLAYER, b.x + 120f, b.z - 20f, -PI.toFloat() / 2 - 0.1f, 18f, 3).also {
                    it.boostT = 6f; it.throttle = 1f
                }
                place(harpoon, b.x - 10f, b.z - 52f, PI.toFloat(), 2f, 0.15f)
            },
            { _ ->
                val f = fin ?: return@Shot
                val b = boat ?: merchant
                val d = hypot(f.x - b.x, f.z - b.z)
                steer(f, b.x + 4f, b.z - 14f, if (d < 30f) 0.15f else 1f)
                if (d < 30f) f.boostT = 0f
                steer(harpoon, b.x, b.z, 0.12f)
            },
            { _ ->
                val f = fin ?: return@Shot
                look(f.x + 6f, 7f, f.z + 30f, f.x - 6f, 4f, f.z)
            })
        // 6. Mihael и Hope бьют по флагману
        var fired6 = 0
        shots += Shot("mihael", "Эй, Гарпун! Попробуй-ка моих ядер! Пушки — пли!", 6.5f, true,
            {
                fired6 = 0
                val h = harpoon
                mihael = g.cutShip("«Гром»", MIHAEL_LOOK, Faction.PLAYER, h.x + 40f, h.z + 6f, 0f, 15f, 6)
                hope = g.cutShip("«Путеводная звезда»", HOPE_LOOK, Faction.PLAYER, h.x - 42f, h.z - 10f, PI.toFloat(), 17f, 4)
                mihael?.let { place(it, it.x, it.z, 0f, 3f, 0.25f) }
                hope?.let { place(it, it.x, it.z, PI.toFloat(), 3f, 0.25f) }
                place(h, h.x, h.z, PI.toFloat(), 1.5f, 0.1f)
            },
            { tt ->
                val m = mihael ?: return@Shot
                val hp = hope ?: return@Shot
                if (tt > 1.0f && fired6 == 0) { fired6 = 1; broadside(m, harpoon, 6) }
                if (tt > 2.2f && fired6 == 1) { fired6 = 2; broadside(hp, harpoon, 4); harpoon.burnT = 12f; harpoon.burnDps = 0f }
                if (tt > 3.6f && fired6 == 2) { fired6 = 3; broadside(m, harpoon, 6) }
                if (tt > 4.6f && fired6 == 3) { fired6 = 4; fin?.let { broadside(it, harpoon, 3) } }
            },
            { tt ->
                val h = harpoon
                orbit(h, 0.35f + tt * 0.05f, 92f, 46f, 2f)
            })
        // 7. Гарпун уходит
        shots += Shot("harpoon", "Мы ещё встретимся, щенки! Все Семь Ветров будут моими!", 6f, true,
            { harpoon.boostT = 0f },
            { tt ->
                steer(harpoon, harpoon.x - 30f, harpoon.z - 300f, 1f)
                if (tt in 2.0f..2.1f) mihael?.let { broadside(it, harpoon, 6) }
            },
            { tt ->
                val h = harpoon
                look(h.x + 34f, 11f, h.z + 26f - tt * 2f, h.x, 7f, h.z)
            })
        // 8. Hope подбирает юнгу
        shots += Shot("hope", "Он дышит. Курс на Гавань Ветров — там ему помогут.", 6f, true,
            {
                battle = false
                val b = boat ?: merchant
                hope?.let { place(it, b.x + 15f, b.z + 2f, 0f, 0f, 0f) }
                fin?.let { place(it, b.x - 30f, b.z - 26f, 0.6f, 2f, 0.15f) }
                mihael?.let { place(it, b.x + 40f, b.z - 40f, 0.2f, 2f, 0.15f) }
                g.ships.remove(harpoon)
            },
            { tt -> g.stormTarget = 0.1f; g.stormLevel += (0.15f - g.stormLevel) * min(1f, 0.02f * (tt + 1)) },
            { tt ->
                val b = boat ?: merchant
                val hp = hope ?: b
                look(b.x - 22f, 8f + tt * 0.3f, b.z + 30f, (b.x + hp.x) / 2, 3f, (b.z + hp.z) / 2)
            })
        // 9. Утро в Гавани Ветров
        shots += Shot("", "Утро в Гавани Ветров. Капитаны выходили юнгу, а на прощание подарили ему старый шлюп «Чайка».", 7f, true,
            { dawn() },
            { },
            { tt -> gull?.let { orbit(it, 2.6f + tt * 0.07f, 38f, 12f, 3f) } })
        // 10. Hope о братствах
        shots += Shot("hope", "Нам пора. Меня ждёт торговая гильдия, Fin — мореходы, а Mihael — береговое братство.", 6.5f, false,
            { },
            { },
            { tt -> gull?.let { orbit(it, 2.95f + tt * 0.05f, 62f, 20f, 3f) } })
        // 11. Mihael — клятва
        shots += Shot("mihael", "Найди нас, когда станешь настоящим капитаном. И тогда «Кровавая Луна» ответит за всё.", 7f, true,
            {
                val dirs = floatArrayOf(-0.75f, 0f, 0.75f)
                for ((i, s) in listOfNotNull(fin, hope, mihael).withIndex()) {
                    val a = atan2(ox, -oz) + dirs[i]
                    s.throttle = 0.5f
                    s.desired = a
                }
            },
            { },
            { tt ->
                val c = gull ?: return@Shot
                look(c.x - ox * 36f + oz * 12f, 14f + tt * 0.5f, c.z - oz * 36f - ox * 12f, c.x + ox * 70f, 4f, c.z + oz * 70f)
            })
        // 12. Титр
        shots += Shot("title", "", 4.5f, false, { }, { },
            { tt ->
                val c = gull ?: return@Shot
                look(c.x - ox * 44f + oz * 12f, 20f + tt * 2f, c.z - oz * 44f - ox * 12f, c.x + ox * 90f, 6f, c.z + oz * 90f)
            })
    }

    /** Смена места действия: рассвет у Гавани Ветров. */
    private fun dawn() {
        g.cutClear()
        boat = null
        g.dayTime = 0.02f
        g.stormLevel = 0f; g.stormTarget = 0f
        g.living.fogK = 0f
        val pt = g.ports[0]
        rx = pt.rx; rz = pt.rz
        ox = pt.rx - pt.def.x; oz = pt.rz - pt.def.z
        val l = hypot(ox, oz).coerceAtLeast(0.01f); ox /= l; oz /= l
        val px = -oz; val pz = ox
        val outH = atan2(ox, -oz)
        gull = g.cutShip("«Чайка»", PLAYER_SHIPS[0].look, Faction.PLAYER, rx, rz, outH + 1.4f, 12f, 2).also { place(it, rx, rz, outH + 1.4f, 0f, 0f) }
        fun put(s: Ship?, along: Float, side: Float, dh: Float) {
            s ?: return
            g.ships += s
            s.burnT = 0f; s.boostT = 0f; s.sinkT = 0f
            for (i in s.mastHp.indices) s.mastHp[i] = 1f
            s.brokenMasts = 0; s.holes.clear()
            place(s, rx + ox * along + px * side, rz + oz * along + pz * side, outH + dh, 0f, 0f)
        }
        put(fin, 26f, -26f, -1.2f)
        put(hope, 14f, 24f, 1.3f)
        put(mihael, 46f, 6f, 1.6f)
        // в списке были старые корабли — убедимся, что нет дублей
        val uniq = LinkedHashSet(g.ships); g.ships.clear(); g.ships.addAll(uniq)
        snap = true
    }

    // ---------- Ход сценки ----------
    private fun next() {
        idx++
        if (idx >= shots.size) { finish(); return }
        t = 0f
        val s = shots[idx]
        if (s.cut) snap = true
        s.start()
        follow = if (s.cut) 2.2f else 1.2f
    }

    fun update(dt: Float) {
        if (!active) return
        total += dt
        t += dt
        bars = min(1f, bars + dt * 1.5f)
        val s = shots[idx]
        s.tick(t)
        s.cam(t)
        // смена на рассвет и финал — через затемнение
        val nextFade = idx + 1 < shots.size && (idx + 1 == 9) || idx == shots.size - 1
        fadeOut = nextFade && t > s.dur - 1f
        fade = if (fadeOut) min(1f, fade + dt * 1.1f) else maxOf(0f, fade - dt * 0.9f)
        // кровавая луна над ночным морем
        if (idx < 9) {
            val dx = camL.x - camP.x; val dz = camL.z - camP.z
            val l = hypot(dx, dz).coerceAtLeast(0.01f)
            val mx = camP.x + dx / l * 320f + dz / l * 90f; val mz = camP.z + dz / l * 320f - dx / l * 90f
            g.fx.glow(mx, camP.y + 120f, mz, 46f, moonCol.set(1f, 0.28f, 0.18f, 0.85f))
            g.fx.glow(mx, camP.y + 120f, mz, 110f, moonCol.set(0.9f, 0.15f, 0.1f, 0.25f))
            g.living.fogK = 0.3f
        }
        if (t >= s.dur) next()
    }
    private val moonCol = Color()

    /** Камера сценки; возвращает точку, вокруг которой рисуется мир. */
    fun camera(dt: Float, cam: com.badlogic.gdx.graphics.Camera): Vector3 {
        if (snap) { camP.set(wantP); camL.set(wantL); snap = false }
        val k = 1 - exp(-dt * follow)
        camP.lerp(wantP, k); camL.lerp(wantL, k)
        cam.position.set(camP)
        if (g.shake > 0) {
            g.shake = maxOf(0f, g.shake - dt)
            cam.position.add(rand(-1f, 1f) * g.shake * 2, rand(-1f, 1f) * g.shake, rand(-1f, 1f) * g.shake * 2)
        }
        cam.up.set(0f, 1f, 0f)
        cam.direction.set(0f, 0f, -1f)
        cam.lookAt(camL)
        cam.update()
        return mid.set((camP.x + camL.x) / 2, camP.y, (camP.z + camL.z) / 2)
    }
    private val mid = Vector3()

    private fun finish() {
        if (!active) return
        active = false
        battle = false
        g.cutClear()
        g.endPrologue(first)
    }

    // ---------- Экран ----------
    private fun typed(s: Shot) = min(s.text.length, (t * 42f).toInt())

    private fun skipRect(): FloatArray {
        val w = 118f; val h = 30f
        return floatArrayOf(g.wDp - w - 12f, g.hDp - g.safeTop - h - 10f, w, h)
    }

    fun touch(x: Float, y: Float) {
        if (!active) return
        val r = skipRect()
        if (x > r[0] - 10 && x < r[0] + r[2] + 10 && y > r[1] - 10 && y < r[1] + r[3] + 10) { g.sfx.play("click"); finish(); return }
        val s = shots[idx]
        if (typed(s) < s.text.length) { t = maxOf(t, s.text.length / 42f + 0.01f); return }
        // реплику можно пролистать, но не раньше чем через полсекунды
        if (t > 0.5f) {
            if (idx + 1 == 9 && fade < 0.9f) { t = maxOf(t, s.dur - 1f); return }
            next()
        }
    }

    private val col = Color()
    private fun c(rgb: Int, a: Float = 1f) = col.set(((rgb shr 16) and 255) / 255f, ((rgb shr 8) and 255) / 255f, (rgb and 255) / 255f, a)

    private fun face(id: String): TextureRegion? = faces.getOrPut(id) {
        HEROES.firstOrNull { it.id == id }?.let { try { HeroArt.face(it) } catch (_: Exception) { null } }
    }

    fun draw() {
        if (!active || idx !in shots.indices) return
        val b = g.batch; val ui = g.ui
        val w = g.wDp; val h = g.hDp
        val s = shots[idx]
        // затемнение
        if (fade > 0f) ui.rect(b, 0f, 0f, w, h, c(0x000000, fade))
        // кинорамка
        val barH = min(h * 0.11f, 84f) * bars
        ui.rect(b, 0f, h - barH - g.safeTop * bars, w, barH + g.safeTop, c(0x000000))
        ui.rect(b, 0f, 0f, w, barH, c(0x000000))
        if (s.who == "title") {
            val a = min(1f, t / 1.2f)
            ui.text(b, ui.huge, "Пираты Семи Ветров", 0f, h * 0.56f, Align.center, w, c = c(0xffe082, a))
            ui.text(b, ui.mid, "Ваша история начинается здесь", 0f, h * 0.56f - 52f, Align.center, w, c = c(0xb9d6f2, a))
        }
        // кнопка «Пропустить»
        val r = skipRect()
        ui.pill(b, r[0], r[1], r[2], r[3], c(0x000000, 0.55f))
        ui.text(b, ui.small, "Пропустить ▶", r[0], r[1] + r[3] * 0.5f + 7f, Align.center, r[2], c = c(0xffffff, 0.85f))
        if (s.text.isEmpty()) { b.color = Color.WHITE; return }
        // субтитр с портретом
        val portrait = s.who.isNotEmpty()
        val bw = min(w - 20f, 640f)
        val pad = 12f
        val ph = if (portrait) 58f else 0f
        val tx = (w - bw) / 2 + pad + (if (portrait) ph + 12f else 0f)
        val tw = bw - pad * 2 - (if (portrait) ph + 12f else 0f)
        val shown = s.text.substring(0, typed(s))
        val lay = com.badlogic.gdx.graphics.g2d.GlyphLayout(ui.small, s.text, Color.WHITE, tw, Align.left, true)
        val nameH = if (portrait) 24f else 0f
        val boxH = maxOf(ph + pad * 2, lay.height + nameH + pad * 2 + 6f)
        val bx = (w - bw) / 2; val by = 10f + g.safeBottom
        ui.round(b, bx, by, bw, boxH, c(0x081723, 0.86f))
        val top = by + boxH - pad
        if (portrait) {
            val fy = top - ph
            val (name, color) = when (s.who) {
                "fin" -> "Fin" to 0x64b5f6
                "hope" -> "Hope" to 0xffd54f
                "mihael" -> "Mihael" to 0xff8a65
                else -> "Адмирал Гарпун" to 0xe53935
            }
            ui.rect(b, bx + pad - 2f, fy - 2f, ph + 4f, ph + 4f, c(color))
            val fr = face(s.who)
            if (fr != null) { b.color = Color.WHITE; b.draw(fr, bx + pad, fy, ph, ph) }
            else {
                ui.rect(b, bx + pad, fy, ph, ph, c(0x2a0606))
                ui.text(b, ui.huge, "☠", bx + pad, fy + ph / 2 + 18f, Align.center, ph, c = c(0xff5252))
            }
            ui.text(b, ui.mid, name, tx, top + 2f, c = c(color))
        }
        ui.text(b, ui.small, shown, tx, top - nameH, Align.left, tw, true,
            c = if (portrait) c(0xffffff) else c(0xe0d6c8))
        if (typed(s) >= s.text.length && (total * 2.2f).toInt() % 2 == 0)
            ui.text(b, ui.small, "▼", bx + bw - pad - 14f, by + pad + 12f, c = c(0xffffff, 0.7f))
        b.color = Color.WHITE
    }

    companion object {
        val H_EAST = (PI / 2).toFloat()
        val BOAT_LOOK = Look(7f, 2.8f, 1, 0x6d4c33, 0x5d4037, 0xd7ccc8, 0x5d4037)
        val FIN_LOOK = Look(15f, 5f, 2, 0x6e3f22, 0x1565c0, 0xbbdefb, 0x1565c0)
        val MIHAEL_LOOK = Look(21f, 6.4f, 3, 0x4a2f1f, 0xb23a48, 0xf3ead3, 0xb23a48)
        val HOPE_LOOK = Look(17f, 5.2f, 2, 0x5b3a29, 0xe9c46a, 0xfff8e7, 0xe9c46a)
    }
}
