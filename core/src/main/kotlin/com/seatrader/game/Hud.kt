package com.seatrader.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Align
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Всё, что рисуется поверх 3D-сцены во время игры. Координаты — dp, y вверх. */
class Hud(private val g: SeaGame) {
    private val ui get() = g.ui
    private val cTmp = Color()
    private fun c(hex: Int, a: Float = 1f): Color = cTmp.set(rgb(hex, a))
    private val layout = GlyphLayout()

    /** Центр кнопки способности i. */
    fun abilityPos(i: Int): Pair<Float, Float> {
        val x = if (g.leftHanded) 46f else g.wDp - 46f
        val y = g.safeBottom + 250f + i * 84f
        return x to y
    }

    private fun minimapCenter(): Pair<Float, Float> {
        val r = 62f
        val x = g.wDp - 12 - r
        val y = g.hDp - g.safeTop - 112 - questHeight - 16 - r
        return x to y
    }

    private var questHeight = 60f

    fun drawLoading() {
        val b = g.batch
        val w = g.wDp; val h = g.hDp
        ui.text(b, ui.huge, "Морские волки", 0f, h * 0.58f, Align.center, w)
        ui.text(b, ui.small, "Поднимаем паруса…", 0f, h * 0.58f - 44, Align.center, w, c = rgb(0x9cc3e6))
        val bw = min(260f, w - 60)
        ui.pill(b, w / 2 - bw / 2, h * 0.45f, bw, 12f, c(0x0b2238))
        ui.pill(b, w / 2 - bw / 2, h * 0.45f, max(12f, bw * g.loadProgress), 12f, c(0x3ddc84))
        ui.icon(b, ui.sails, w / 2, h * 0.7f, 110f, 110f, 0f, c(0xffffff, 0.9f))
        b.color = Color.WHITE
    }

    fun draw(dt: Float) {
        val b = g.batch
        val p = g.player
        val top = g.hDp - g.safeTop
        val wDp = g.wDp; val hDp = g.hDp

        // Лёгкое затемнение краёв для глубины
        b.color = Color.WHITE
        b.draw(ui.dark, 0f, 0f, wDp, hDp)

        // Красная вспышка при попадании и молния
        if (g.flash > 0) {
            g.flash -= dt
            b.color = Color(1f, 1f, 1f, min(1f, g.flash / 0.25f))
            b.draw(ui.vignette, 0f, 0f, wDp, hDp)
        }
        if (g.lightning > 0) ui.rect(b, 0f, 0f, wDp, hDp, c(0xffffff, g.lightning * 1.6f))

        // HP-полоски кораблей
        for (s in g.ships) {
            if (s.sinking) continue
            if (!(s === p || s.ally || s.hp < s.maxHp || s.ai == AiState.ATTACK)) continue
            val sc = g.toScreen(s.x, s.y + 6 + s.len * 0.75f, s.z) ?: continue
            if (sc.x < -60 || sc.x > wDp + 60 || sc.y < -20 || sc.y > hDp + 20) continue
            val w = if (s.boss) 110f else 64f
            val hh = if (s.boss) 12f else 9f
            val fill = when { s === p -> 0x3ddc84; s.ally -> 0x4fc3f7; s.boss -> 0xb388ff; else -> 0xff5a5f }
            ui.pill(b, sc.x - w / 2 - 1, sc.y - 1, w + 2, hh + 2, c(0x000000, 0.6f))
            ui.rect(b, sc.x - w / 2 + 1, sc.y + 1, (w - 2) * (s.hp / s.maxHp), hh - 2, c(fill))
            if (s.burnT > 0) ui.icon(b, ui.fireIcon, sc.x + w / 2 + 10, sc.y + hh / 2, 16f, 16f)
            if (s.slowT > 0) ui.icon(b, ui.chainIcon, sc.x - w / 2 - 10, sc.y + hh / 2, 16f, 16f)
        }
        // Полоса здоровья змея
        g.serpent?.let { sp ->
            if (!sp.dead) {
                val sc = g.toScreen(sp.x, 10f, sp.z)
                if (sc != null && sc.x > -60 && sc.x < wDp + 60 && sc.y > 0 && sc.y < hDp) {
                    ui.pill(b, sc.x - 61, sc.y - 1, 122f, 14f, c(0x000000, 0.6f))
                    ui.rect(b, sc.x - 59, sc.y + 1, 118f * (sp.hp / sp.maxHp), 10f, c(0x26a69a))
                    ui.text(b, ui.small, "Морской змей", sc.x - 80, sc.y + 30, Align.center, 160f)
                }
            }
        }

        // Подписи портов
        for (port in g.ports) {
            if (p == null || hypot(port.rx - p.x, port.rz - p.z) > 300) continue
            val sc = g.toScreen(port.rx, 8f, port.rz) ?: continue
            if (sc.x < -100 || sc.x > wDp + 100 || sc.y < 0 || sc.y > hDp) continue
            ui.text(b, ui.small, "⚓ " + port.def.name, sc.x - 100, sc.y + 14, Align.center, 200f)
        }

        // Всплывающие числа
        val fit = g.floats.iterator()
        while (fit.hasNext()) {
            val f = fit.next()
            f.age += dt
            if (f.age > 1f) { fit.remove(); continue }
            val col = Color(f.color).also { it.a = 1 - f.age }
            ui.text(b, f.font, f.text, f.x - 60, f.y + f.age * 60, Align.center, 120f, c = col)
        }

        val q = g.quest()
        val target = if (g.state == SeaGame.State.PLAY) g.questTarget() else null
        // Стрелка к цели задания и расстояние
        if (target != null && p != null && !p.sinking) {
            val d = hypot(target.first - p.x, target.second - p.z)
            val ps = g.toScreen(p.x, 0f, p.z)?.let { Vector3(it) }
            val ts = g.toScreen(target.first, 0f, target.second)?.let { Vector3(it) }
            if (d > 35 && ps != null) {
                var ax: Float; var ay: Float
                if (ts != null) { ax = ts.x - ps.x; ay = ts.y - ps.y } else { ax = target.first - p.x; ay = -(target.second - p.z) }
                val al = hypot(ax, ay).coerceAtLeast(0.001f)
                val r = 70 + p.len * 1.5f
                val ang = atan2(ay, ax) * MathUtils.radiansToDegrees - 90f
                ui.icon(b, ui.triangle, ps.x + ax / al * r, ps.y + ay / al * r, 26f, 26f, ang, c(0xffc93c))
            }
            val by = 18 + g.safeBottom
            ui.text(b, ui.big, "${d.roundToInt()} м", 0f, by + 26, Align.center, wDp)
            ui.icon(b, ui.disc, wDp / 2, by + 52, 40f, 40f, 0f, c(0x3c4650, 0.75f))
            questIcon(q, wDp / 2, by + 52, 24f)
        }

        // Джойстик
        if (g.joyActive && g.state == SeaGame.State.PLAY) {
            val jr = g.joySens
            ui.icon(b, ui.disc, g.joyOx, g.joyOy, jr * 2.2f, jr * 2.2f, 0f, c(0x3ddc84, 0.06f))
            ui.icon(b, ui.ring, g.joyOx, g.joyOy, jr * 2.26f, jr * 2.26f, 0f, c(0x3ddc84, 0.95f))
            val kx = g.joyOx + g.joyJx * g.joyMag * jr
            val ky = g.joyOy + g.joyJy * g.joyMag * jr
            ui.icon(b, ui.disc, kx, ky, 56f, 56f, 0f, c(0xffffff, 0.35f))
            if (g.joyMag > 0.12f) {
                val ang = atan2(g.joyJy, g.joyJx) * MathUtils.radiansToDegrees - 90f
                ui.icon(b, ui.triangle, g.joyOx + g.joyJx * (jr + 33), g.joyOy + g.joyJy * (jr + 33), 52f, 60f, ang, c(0x3ddc84, 0.9f))
            }
        }

        // Пауза
        for (i in 0..1) {
            val x = 24f + i * 28
            ui.round(b, x - 3, top - 82, 24f, 66f, c(0x1b2a3a))
            ui.round(b, x, top - 78, 18f, 60f, c(0xffffff))
        }

        // Рывок
        val bx = 58f; val by2 = top - 140f
        val active = p != null && p.boostT > 0
        ui.icon(b, ui.disc, bx, by2, 88f, 88f, 0f, c(0xffffff, 0.12f))
        ui.icon(b, ui.ring, bx, by2, 92f, 92f, 0f, if (active) c(0x3ddc84) else c(0xffffff, 0.6f))
        ui.icon(b, ui.sails, bx, by2 + 2, 66f, 66f, 0f, c(0xffffff, 0.92f))
        if (p != null && p.boostCd > 0) ui.pie(b, bx, by2, 42f, p.boostCd / 7f, c(0x0a1e32, 0.6f))

        // Способности
        val icons = listOf(ui.fireIcon, ui.chainIcon, ui.repairIcon)
        for (i in ABILITIES.indices) {
            val (ax, ay) = abilityPos(i)
            val cd = g.abilityCd[i]
            val on = (i == 0 && g.ammo == Ammo.FIRE) || (i == 1 && g.ammo == Ammo.CHAIN) || (i == 2 && p != null && p.repairT > 0)
            ui.icon(b, ui.disc, ax, ay, 68f, 68f, 0f, c(0x0a1e32, 0.55f))
            ui.icon(b, ui.ring, ax, ay, 72f, 72f, 0f, if (on) c(0xffc93c) else c(0xffffff, 0.55f))
            ui.icon(b, icons[i], ax, ay, 44f, 44f, 0f, if (cd > 0) c(0xffffff, 0.45f) else Color.WHITE)
            if (cd > 0) {
                ui.pie(b, ax, ay, 32f, cd / ABILITIES[i].cooldown, c(0x0a1e32, 0.55f))
                ui.text(b, ui.mid, "${kotlin.math.ceil(cd).toInt()}", ax - 30, ay + 9, Align.center, 60f)
            }
            if (on && i < 2) ui.text(b, ui.small, "${kotlin.math.ceil(g.ammoT).toInt()} с", ax - 30, ay - 40, Align.center, 60f, c = rgb(0xffc93c))
        }

        // Монеты и кристаллы
        for ((i, pair) in listOf(fmt(g.coins) to ui.coin, fmt(g.gems) to ui.gem).withIndex()) {
            val y = top - 16 - 44 - i * 50
            val tw = ui.measure(ui.big, pair.first)
            val w = max(118f, tw + 62)
            ui.round(b, wDp - 12 - w, y, w, 42f, c(0x0a141e, 0.55f))
            ui.text(b, ui.big, pair.first, wDp - 12 - w, y + 32, Align.right, w - 50)
            ui.icon(b, pair.second, wDp - 36, y + 21, 32f, if (i == 0) 32f else 28f)
        }

        // Баннер задания
        val qx = 116f
        val qw = min(wDp - 128f, 440f)
        val textW = qw - 58
        layout.setText(ui.mid, q.text, Color.WHITE, textW, Align.center, true)
        val qh = layout.height + 34
        questHeight = qh
        val qy = top - 112 - qh
        val pop = if (g.questPop > 0) { g.questPop -= dt; 1 + sin(g.questPop / 0.6f * PI.toFloat()) * 0.08f } else 1f
        val cx = qx + qw / 2; val cy = qy + qh / 2
        val sw = qw * pop; val sh = qh * pop
        ui.round(b, cx - sw / 2, cy - sh / 2 - 3, sw, sh, c(0x0d3f7a))
        ui.round(b, cx - sw / 2, cy - sh / 2, sw, sh, c(0x2f8ff0))
        questIcon(q, cx - sw / 2 + 26, cy, 28f)
        ui.mid.color = Color.WHITE
        ui.mid.draw(b, layout, cx - sw / 2 + 50, cy + sh / 2 - 7)
        val barW = sw - 64
        ui.pill(b, cx - sw / 2 + 50, cy - sh / 2 + 6, barW, 11f, c(0x0d2f57))
        val prog = min(1f, g.qprog.toFloat() / q.n)
        if (prog > 0) ui.pill(b, cx - sw / 2 + 50, cy - sh / 2 + 6, max(11f, barW * prog), 11f, c(0xf5b700))

        drawMinimap()

        // Обучение
        if (g.tutorial < g.tutorialTexts.size && g.state == SeaGame.State.PLAY) {
            val text = g.tutorialTexts[g.tutorial]
            val tw = min(wDp - 40, 360f)
            layout.setText(ui.mid, text, Color.WHITE, tw - 70, Align.left, true)
            val th = layout.height + 28
            val tx = wDp / 2 - tw / 2
            val ty = 110 + g.safeBottom
            ui.round(b, tx, ty - 3, tw, th, c(0x0a141e, 0.5f))
            ui.round(b, tx, ty, tw, th, c(0x1b3a57, 0.92f))
            ui.mid.draw(b, layout, tx + 58, ty + th - 14)
            val bob = sin(g.now * 5) * 5
            ui.icon(b, ui.hand, tx + 30, ty + th / 2 + bob, 36f, 36f, 0f, c(0xffffff))
        }

        // Сообщение по центру
        if (g.toastT > 0) {
            g.toastT -= dt
            val a = min(1f, g.toastT * 3)
            ui.text(b, ui.huge, g.toastText, 10f, hDp * 0.62f, Align.center, wDp - 20, true, Color(1f, 1f, 1f, a))
        }
        b.color = Color.WHITE
    }

    private fun questIcon(q: Quest, x: Float, y: Float, size: Float) {
        val b = g.batch
        when (q.type) {
            QType.COLLECT -> ui.icon(b, ui.coin, x, y, size, size)
            QType.TREASURE -> ui.icon(b, ui.xmark, x, y, size, size)
            QType.ABILITY -> ui.icon(b, ui.fireIcon, x, y, size * 1.2f, size * 1.2f)
            else -> {
                val glyph = when (q.type) {
                    QType.KILL -> "☠"; QType.RAM -> "⚔"; QType.REACH -> "⚓"; QType.UPGRADE -> "⚙"; QType.BUY -> "★"
                    QType.HIRE -> "⚓"; QType.SERPENT -> "☠"; else -> "★"
                }
                ui.text(b, ui.big, glyph, x - 30, y + 13, Align.center, 60f)
            }
        }
    }

    /** Мини-карта: север вверху, радиус обзора 420 м. */
    private fun drawMinimap() {
        val b = g.batch
        val p = g.player ?: return
        val (cx, cy) = minimapCenter()
        val r = 62f
        val range = 420f
        ui.icon(b, ui.disc, cx, cy, r * 2 + 6, r * 2 + 6, 0f, c(0x0a141e, 0.55f))
        ui.icon(b, ui.disc, cx, cy, r * 2, r * 2, 0f, c(0x1f6fb0, 0.85f))
        fun pt(wx: Float, wz: Float, clampEdge: Boolean = false): Pair<Float, Float>? {
            var dx = (wx - p.x) / range * r
            var dy = -(wz - p.z) / range * r
            val d = hypot(dx, dy)
            if (d > r - 4) {
                if (!clampEdge) return null
                dx *= (r - 6) / d; dy *= (r - 6) / d
            }
            return (cx + dx) to (cy + dy)
        }
        for (isl in g.islands) {
            val s = isl.r / range * r * 2
            pt(isl.x, isl.z)?.let { (x, y) -> ui.icon(b, ui.disc, x, y, max(4f, s), max(4f, s), 0f, if (isl.port >= 0) c(0xe6d3a3) else c(0x7cb342)) }
        }
        for (port in g.ports) pt(port.rx, port.rz)?.let { (x, y) -> ui.icon(b, ui.disc, x, y, 7f, 7f, 0f, c(0xffffff)) }
        for (s in g.ships) {
            if (s === p || s.sinking) continue
            val col = if (s.ally) c(0x4fc3f7) else if (s.boss) c(0xb388ff) else c(0xff5a5f)
            pt(s.x, s.z)?.let { (x, y) -> ui.icon(b, ui.disc, x, y, if (s.boss) 9f else 6f, if (s.boss) 9f else 6f, 0f, col) }
        }
        g.serpent?.let { sp -> if (!sp.dead) pt(sp.x, sp.z)?.let { (x, y) -> ui.icon(b, ui.disc, x, y, 10f, 10f, 0f, c(0x26a69a)) } }
        g.treasure?.let { t -> pt(t.x, t.z, true)?.let { (x, y) -> ui.icon(b, ui.xmark, x, y, 14f, 14f) } }
        g.questTarget()?.let { t -> pt(t.first, t.second, true)?.let { (x, y) -> ui.icon(b, ui.disc, x, y, 9f, 9f, 0f, c(0xffc93c)) } }
        // игрок — стрелка по курсу
        ui.icon(b, ui.arrowIcon, cx, cy, 12f, 14f, -p.h * MathUtils.radiansToDegrees, c(0xffffff))
        ui.icon(b, ui.ring, cx, cy, r * 2 + 6, r * 2 + 6, 0f, c(0xffffff, 0.7f))
        // ветер: стрелка на ободке, куда дует
        val wx = cx + sin(g.windA) * (r + 12)
        val wy = cy + cos(g.windA) * (r + 12)
        ui.icon(b, ui.arrowIcon, wx, wy, 14f, 16f, -g.windA * MathUtils.radiansToDegrees, c(0xb3e5fc))
        val tags = ArrayList<String>()
        tags += "ветер"
        if (g.stormLevel > 0.3f) tags += "шторм"
        if (1f - g.sky.day > 0.5f) tags += "ночь"
        ui.text(b, ui.small, tags.joinToString(" · "), cx - 80, cy - r - 8, Align.center, 160f, c = rgb(0xb3e5fc))
    }
}
