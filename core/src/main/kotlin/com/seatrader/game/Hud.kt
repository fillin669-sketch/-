package com.seatrader.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Align
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.abs
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
    private val landscape get() = g.wDp > g.hDp

    /** Индексы способностей, которые есть у игрока. */
    fun visibleAbilities(): List<Int> = ABILITIES.indices.filter { it < 3 || it in g.systems.ownedWeapons }

    /** Центр кнопки способности i. */
    fun abilityPos(i: Int): Pair<Float, Float> {
        val vis = visibleAbilities()
        val k = vis.indexOf(i).coerceAtLeast(0)
        val right = !g.leftHanded
        val colX = if (right) g.wDp - 42f else 42f
        val step = 70f
        return if (landscape) {
            // в альбомной ориентации — ряд внизу, левее кнопки прицела
            val x = if (right) g.wDp - 125f - k * 66f else 125f + k * 66f
            x to (g.safeBottom + 42f)
        } else {
            val col = k / 3; val row = k % 3
            val x = if (right) colX + 4 - col * 60f else colX - 4 + col * 60f
            x to (g.safeBottom + 222f + row * 62f)
        }
    }

    fun aimPos(): Pair<Float, Float> {
        val right = !g.leftHanded
        return (if (right) g.wDp - 50f else 50f) to (g.safeBottom + 135f)
    }

    fun boardPos(): Pair<Float, Float> = (g.wDp / 2) to (g.safeBottom + 175f)

    /** Кнопка паузы: маленькая, в левом верхнем углу. */
    fun pausePos(): Pair<Float, Float> = 30f to (g.hDp - g.safeTop - 30f)
    fun pauseHit(x: Float, y: Float): Boolean { val (px, py) = pausePos(); return abs(x - px) < 26 && abs(y - py) < 26 }

    /** Рывок — над способностями справа (в альбомной — слева под паузой). */
    fun boostPos(): Pair<Float, Float> {
        if (landscape) return 34f to (g.hDp - g.safeTop - 150f)
        val right = !g.leftHanded
        val rows = minOf(3, visibleAbilities().size)
        return (if (right) g.wDp - 38f else 38f) to (g.safeBottom + 222f + rows * 62f)
    }

    /** Переключатель автоогня рядом с прицелом. */
    fun autoPos(): Pair<Float, Float> {
        val right = !g.leftHanded
        return if (landscape) (if (right) g.wDp - 50f else 50f) to (g.safeBottom + 215f)
        else (if (right) g.wDp - 122f else 122f) to (g.safeBottom + 100f)
    }

    private val mapR = 50f

    fun minimapHit(x: Float, y: Float): Boolean {
        val (cx, cy) = minimapCenter()
        return hypot(x - cx, y - cy) < mapR + 6f
    }

    private var questHeight = 60f
    private var lineEnd = 130f

    private fun minimapCenter(): Pair<Float, Float> {
        val top = g.hDp - g.safeTop
        return (g.wDp - 10 - mapR) to (top - 10 - mapR)
    }

    fun drawLoading() {
        val b = g.batch
        val w = g.wDp; val h = g.hDp
        ui.text(b, ui.huge, "Пираты Семи Ветров", 0f, h * 0.58f, Align.center, w)
        ui.text(b, ui.small, "Поднимаем паруса…", 0f, h * 0.58f - 44, Align.center, w, c = rgb(0x9cc3e6))
        val bw = min(260f, w - 60)
        ui.pill(b, w / 2 - bw / 2, h * 0.45f, bw, 12f, c(0x0b2238))
        ui.pill(b, w / 2 - bw / 2, h * 0.45f, max(12f, bw * g.loadProgress), 12f, c(0x3ddc84))
        ui.icon(b, ui.sails, w / 2, h * 0.7f, 110f, 110f, 0f, c(0xffffff, 0.9f))
        b.color = Color.WHITE
    }

    private fun bar(x: Float, y: Float, w: Float, h: Float, frac: Float, color: Int) {
        val b = g.batch
        ui.pill(b, x - w / 2 - 1, y - 1, w + 2, h + 2, c(0x000000, 0.6f))
        ui.rect(b, x - w / 2 + 1, y + 1, (w - 2) * frac.coerceIn(0f, 1f), h - 2, c(color))
    }

    fun draw(dt: Float) {
        val b = g.batch
        val p = g.player
        val top = g.hDp - g.safeTop
        val wDp = g.wDp; val hDp = g.hDp

        if (g.portView.active) return
        b.color = Color.WHITE
        b.draw(ui.dark, 0f, 0f, wDp, hDp)
        if (g.flash > 0) {
            g.flash -= dt
            b.color = Color(1f, 1f, 1f, min(1f, g.flash / 0.25f))
            b.draw(ui.vignette, 0f, 0f, wDp, hDp)
        }
        if (g.lightning > 0) ui.rect(b, 0f, 0f, wDp, hDp, c(0xffffff, g.lightning * 1.6f))

        // HP-полоски кораблей
        for (s in g.ships) {
            if (s.sinking) continue
            val neutralShow = s.faction == Faction.NEUTRAL && (s === g.systems.protect || s.hp < s.maxHp)
            if (!(s === p || s.ally || neutralShow || (s.faction == Faction.PIRATE && (s.hp < s.maxHp || s.ai == AiState.ATTACK)))) continue
            val sc = g.toScreen(s.x, s.y + 6 + s.len * 0.75f, s.z) ?: continue
            if (sc.x < -60 || sc.x > wDp + 60 || sc.y < -20 || sc.y > hDp + 20) continue
            val w = if (s.boss) 110f else 64f
            val hh = if (s.boss) 12f else 9f
            val fill = when { s === p -> 0x3ddc84; s.ally -> 0x4fc3f7; s.faction == Faction.NEUTRAL -> 0xfff176; s.boss -> 0xb388ff; else -> 0xff5a5f }
            bar(sc.x, sc.y, w, hh, s.hp / s.maxHp, fill)
            if (s.boss) ui.text(b, ui.small, s.name, sc.x - 120, sc.y + 30, Align.center, 240f, c = rgb(0xffcdd2))
            if (s.burnT > 0) ui.icon(b, ui.fireIcon, sc.x + w / 2 + 10, sc.y + hh / 2, 16f, 16f)
            if (s.slowT > 0) ui.icon(b, ui.chainIcon, sc.x - w / 2 - 10, sc.y + hh / 2, 16f, 16f)
            if (g.systems.boardable(s)) {
                val pulse = 1f + sin(g.now * 6) * 0.12f
                ui.icon(b, ui.disc, sc.x, sc.y + 30, 34f * pulse, 34f * pulse, 0f, c(0xd32f2f, 0.9f))
                ui.icon(b, ui.swordsIcon, sc.x, sc.y + 30, 26f * pulse, 26f * pulse)
            }
            if (s === g.systems.protect) ui.text(b, ui.small, "Защитите купца!", sc.x - 80, sc.y + 30, Align.center, 160f, c = rgb(0xfff176))
        }
        // Боссы и крепости
        g.serpent?.let { sp ->
            if (!sp.dead) g.toScreen(sp.x, 10f, sp.z)?.let { sc ->
                if (sc.x > -60 && sc.x < wDp + 60 && sc.y > 0 && sc.y < hDp) {
                    bar(sc.x, sc.y, 120f, 12f, sp.hp / sp.maxHp, 0x26a69a)
                    ui.text(b, ui.small, "Морской змей", sc.x - 80, sc.y + 30, Align.center, 160f)
                }
            }
        }
        g.systems.kraken?.let { k ->
            if (!k.dead) g.toScreen(k.x, 14f, k.z)?.let { sc ->
                if (sc.x > -60 && sc.x < wDp + 60 && sc.y > 0 && sc.y < hDp) {
                    bar(sc.x, sc.y, 140f, 13f, k.hp / k.maxHp, 0xd81b60)
                    ui.text(b, ui.small, "Кракен", sc.x - 80, sc.y + 30, Align.center, 160f)
                }
            }
        }
        for (f in g.systems.fortresses) {
            if (!f.alive || p == null || hypot(f.x - p.x, f.z - p.z) > 220) continue
            g.toScreen(f.x, 26f, f.z)?.let { sc ->
                if (sc.x > -60 && sc.x < wDp + 60 && sc.y > 0 && sc.y < hDp) {
                    bar(sc.x, sc.y, 110f, 12f, f.hp / f.maxHp, 0xff7043)
                    ui.text(b, ui.small, "Пиратская крепость", sc.x - 100, sc.y + 30, Align.center, 200f)
                }
            }
        }

        // Подписи портов
        for (port in g.ports) {
            if (p == null || hypot(port.rx - p.x, port.rz - p.z) > 300) continue
            val sc = g.toScreen(port.rx, 8f, port.rz) ?: continue
            if (sc.x < -100 || sc.x > wDp + 100 || sc.y < 0 || sc.y > hDp) continue
            ui.text(b, ui.small, "⚓ " + port.def.name, sc.x - 110, sc.y + 14, Align.center, 220f)
        }
        // Затонувший корабль: подсказка
        if (p != null) for (w in g.systems.wrecks) {
            val d = hypot(w.x - p.x, w.z - p.z)
            if (d > 60) continue
            g.toScreen(w.x, 3f, w.z)?.let { sc ->
                val txt = if (w.searchT > 0) "Ныряльщики ищут… ${(w.searchT / 1.6f * 100).roundToInt()}%" else "Затонувший корабль: остановитесь рядом"
                ui.text(b, ui.small, txt, sc.x - 130, sc.y + 20, Align.center, 260f, c = rgb(0xb3e5fc))
            }
        }

        // Всплывающие числа
        val fit = g.floats.iterator()
        while (fit.hasNext()) {
            val f = fit.next()
            f.age += dt
            if (f.age > 1f) { fit.remove(); continue }
            val col = Color(f.color).also { it.a = 1 - f.age }
            ui.text(b, f.font, f.text, f.x - 80, f.y + f.age * 60, Align.center, 160f, c = col)
        }

        val q = g.quest()
        val target = if (g.state == SeaGame.State.PLAY) g.questTarget() else null
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
            val by = 10 + g.safeBottom
            ui.icon(b, ui.disc, wDp / 2 - 30, by + 12, 24f, 24f, 0f, c(0x3c4650, 0.7f))
            questIcon(q, wDp / 2 - 30, by + 12, 14f)
            ui.text(b, ui.mid, "${d.roundToInt()} м", wDp / 2 - 14, by + 22, Align.left, 100f)
        }

        // Путевая точка с карты
        val wp = g.systems.waypoint
        if (wp != null && p != null && !p.sinking && g.state == SeaGame.State.PLAY) {
            val ps = g.toScreen(p.x, 0f, p.z)?.let { Vector3(it) }
            if (ps != null) {
                val ts = g.toScreen(wp[0], 0f, wp[1])
                var ax: Float; var ay: Float
                if (ts != null) { ax = ts.x - ps.x; ay = ts.y - ps.y } else { ax = wp[0] - p.x; ay = -(wp[1] - p.z) }
                val al = hypot(ax, ay).coerceAtLeast(0.001f)
                val r = 95 + p.len * 1.5f
                val ang = atan2(ay, ax) * MathUtils.radiansToDegrees - 90f
                ui.icon(b, ui.triangle, ps.x + ax / al * r, ps.y + ay / al * r, 22f, 22f, ang, c(0x4dd0e1))
                val d = hypot(wp[0] - p.x, wp[1] - p.z)
                ui.text(b, ui.small, "Отметка: ${d.roundToInt()} м", ps.x + ax / al * (r + 26) - 60, ps.y + ay / al * (r + 26) + 6, Align.center, 120f, c = rgb(0x4dd0e1))
            }
        }

        // Песня команды
        if (g.systems.shantyBanner > 0 && g.sfx.shantyIdx >= 0) {
            val a = min(1f, g.systems.shantyBanner)
            val title = SHANTIES[g.sfx.shantyIdx].title
            // строкой ниже уровня и названия региона — ни на что не налезает
            val songY = g.hDp - g.safeTop - 10f - 2 * mapR - 34f
            val st = "♪ «$title» ♪"
            val sw2 = ui.measure(ui.small, st) + 20
            ui.round(b, (wDp - sw2) / 2, songY - 17, sw2, 22f, c(0x0a141e, 0.45f * a))
            ui.text(b, ui.small, st, 0f, songY, Align.center, wDp, c = Color(1f, 0.92f, 0.7f, a))
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

        // Пауза — маленькая
        val (pX, pY) = pausePos()
        ui.round(b, pX - 20, pY - 20, 40f, 40f, c(0x0a141e, 0.5f))
        for (i in 0..1) ui.rect(b, pX - 8 + i * 10, pY - 10, 6f, 20f, c(0xffffff, 0.95f))

        // Рывок
        val (bx, by2) = boostPos()
        val active = p != null && p.boostT > 0
        ui.icon(b, ui.disc, bx, by2, 54f, 54f, 0f, c(0x0a1e32, 0.5f))
        ui.icon(b, ui.ring, bx, by2, 57f, 57f, 0f, if (active) c(0x3ddc84) else c(0xffffff, 0.5f))
        ui.icon(b, ui.sails, bx, by2 + 1, 38f, 38f, 0f, c(0xffffff, 0.92f))
        if (p != null && p.boostCd > 0) ui.pie(b, bx, by2, 25f, p.boostCd / 7f, c(0x0a1e32, 0.6f))

        // Автоогонь
        val (fxp, fyp) = autoPos()
        val auto = g.systems.autoFire
        ui.icon(b, ui.disc, fxp, fyp, 46f, 46f, 0f, c(if (auto) 0x2e7d32 else 0x0a1e32, if (auto) 0.85f else 0.5f))
        ui.icon(b, ui.ring, fxp, fyp, 49f, 49f, 0f, if (auto) c(0x81c784) else c(0xffffff, 0.5f))
        ui.text(b, ui.small, "АВТО", fxp - 30, fyp + 12, Align.center, 60f, c = if (auto) Color.WHITE else rgb(0xb0bec5))
        ui.text(b, ui.small, if (auto) "вкл" else "выкл", fxp - 30, fyp - 2, Align.center, 60f, c = if (auto) rgb(0xc8e6c9) else rgb(0x90a4ae))

        val sy = g.systems

        // Способности
        val icons = listOf(ui.fireIcon, ui.chainIcon, ui.repairIcon, ui.mortarIcon, ui.rocketIcon, ui.mineIcon)
        for (i in visibleAbilities()) {
            val (ax, ay) = abilityPos(i)
            val cd = g.abilityCd[i]
            val on = (i == 0 && g.ammo == Ammo.FIRE) || (i == 1 && g.ammo == Ammo.CHAIN) || (i == 2 && p != null && p.repairT > 0)
            ui.icon(b, ui.disc, ax, ay, 50f, 50f, 0f, c(0x0a1e32, 0.5f))
            ui.icon(b, ui.ring, ax, ay, 53f, 53f, 0f, if (on) c(0xffc93c) else c(0xffffff, 0.5f))
            ui.icon(b, icons[i], ax, ay, 31f, 31f, 0f, if (cd > 0) c(0xffffff, 0.45f) else Color.WHITE)
            if (cd > 0) {
                ui.pie(b, ax, ay, 23f, cd / ABILITIES[i].cooldown, c(0x0a1e32, 0.55f))
                ui.text(b, ui.mid, "${kotlin.math.ceil(cd).toInt()}", ax - 30, ay + 9, Align.center, 60f)
            }
            if (on && i < 2) ui.text(b, ui.small, "${kotlin.math.ceil(g.ammoT).toInt()} с", ax - 30, ay - 36, Align.center, 60f, c = rgb(0xffc93c))
        }
        // Прицел
        val (axx, ayy) = aimPos()
        ui.icon(b, ui.disc, axx, ayy, 74f, 74f, 0f, c(0x0a1e32, if (g.aimActive) 0.75f else 0.5f))
        ui.icon(b, ui.ring, axx, ayy, 78f, 78f, 0f, if (g.aimActive) c(0xffc93c) else c(0xffffff, 0.6f))
        ui.icon(b, ui.aimIcon, axx, ayy, 46f, 46f, 0f, Color.WHITE)
        if (!g.systems.autoFire) ui.text(b, ui.small, "ручной огонь", (axx - 60).coerceIn(4f, wDp - 124), ayy - 44, Align.center, 120f, c = rgb(0xffc93c))
        if (g.aimActive) {
            val reloadL = p?.reload?.get(0) ?: 0f; val reloadR = p?.reload?.get(1) ?: 0f
            ui.text(b, ui.small, "Левый борт: ${if (reloadL > 0) "%.1f с".format(reloadL) else "готов"}   Правый: ${if (reloadR > 0) "%.1f с".format(reloadR) else "готов"}",
                0f, ayy + 70, Align.center, wDp, c = rgb(0xffe082))
        }
        g.living.drawHud(b)
        // Абордаж
        g.systems.boardTarget?.let {
            val (px, py) = boardPos()
            val pulse = 1f + sin(g.now * 6) * 0.06f
            ui.round(b, px - 95 * pulse, py - 28 * pulse, 190 * pulse, 56 * pulse, c(0x7f1d1d))
            ui.round(b, px - 93 * pulse, py - 25 * pulse, 186 * pulse, 50 * pulse, c(0xd32f2f))
            ui.icon(b, ui.swordsIcon, px - 64, py, 36f, 36f)
            ui.text(b, ui.big, "Абордаж!", px - 42, py + 12, Align.left, 150f)
            ui.text(b, ui.small, "нажмите — команда бросит крюки", px - 120, py - 34, Align.center, 240f, c = rgb(0xffcdd2))
        }
        if (g.systems.grappleTarget != null) ui.text(b, ui.big, "Крюки летят — сближаемся…", 0f, hDp * 0.55f, Align.center, wDp, c = rgb(0xffe0b2))

        // Монеты и кристаллы — одна компактная строка рядом с паузой
        val curY = top - 30f
        var cxx = 58f
        for ((i, pair) in listOf(fmt(g.coins) to ui.coin, fmt(g.gems) to ui.gem).withIndex()) {
            val tw = ui.measure(ui.mid, pair.first)
            val w = tw + 36
            ui.round(b, cxx, curY - 15, w, 30f, c(0x0a141e, 0.5f))
            ui.icon(b, pair.second, cxx + 14, curY, 20f, if (i == 0) 20f else 17f)
            ui.text(b, ui.mid, pair.first, cxx + 28, curY + 10, Align.left, tw + 4)
            cxx += w + 6
        }

        // Задание — узкая плашка под валютой
        val qx = 10f
        val qw = (wDp - 2 * mapR - 34f - qx).coerceAtMost(330f)
        val textW = qw - 40
        layout.setText(ui.small, q.text, Color.WHITE, textW, Align.left, true)
        val qh = layout.height + 22
        questHeight = qh
        val qy = top - 54 - qh
        val pop = if (g.questPop > 0) { g.questPop -= dt; 1 + sin(g.questPop / 0.6f * PI.toFloat()) * 0.06f } else 1f
        val sw = qw * pop; val sh = qh * pop
        val qcx = qx + qw / 2; val qcy = qy + qh / 2
        ui.round(b, qcx - sw / 2, qcy - sh / 2, sw, sh, c(if (g.questIdx < STORY_LENGTH) 0x1565c0 else 0x4527a0, 0.72f))
        questIcon(q, qcx - sw / 2 + 17, qcy + 2, 18f)
        ui.small.color = Color.WHITE
        ui.small.draw(b, layout, qcx - sw / 2 + 34, qcy + sh / 2 - 6)
        val barW = sw - 44
        ui.pill(b, qcx - sw / 2 + 34, qcy - sh / 2 + 5, barW, 5f, c(0x0d2f57))
        val prog = min(1f, g.qprog.toFloat() / q.n)
        if (prog > 0) ui.pill(b, qcx - sw / 2 + 34, qcy - sh / 2 + 5, max(5f, barW * prog), 5f, c(0xf5b700))
        // уровень и трюм под заданием
        val lvTxt = "Ур. ${sy.level}" + if (sy.skillPts > 0) "  +${sy.skillPts}" else ""
        val ly = qy - 8
        val hero = g.hero()
        val lx = if (hero != null) 40f else 12f
        if (hero != null) {
            ui.round(b, 10f, ly - 22, 26f, 26f, c(0xd7b98e))
            b.color = Color.WHITE
            b.draw(HeroArt.face(hero), 12f, ly - 20, 22f, 22f)
        }
        val lvW = ui.measure(ui.small, lvTxt)
        ui.text(b, ui.small, lvTxt, lx, ly, Align.left, lvW + 4, c = if (sy.skillPts > 0) rgb(0xffc93c) else rgb(0xe3f2fd))
        val barX = lx + lvW + 8
        ui.pill(b, barX, ly - 9, 50f, 5f, c(0x0b2238, 0.8f))
        ui.pill(b, barX, ly - 9, max(5f, 50f * sy.xp / xpForLevel(sy.level).toFloat()), 5f, c(0x81d4fa))
        lineEnd = barX + 58
        if (sy.cargoUsed() > 0) ui.text(b, ui.small, "Трюм ${sy.cargoUsed()}/${sy.capacity()}", lineEnd, ly, Align.left, 120f, c = rgb(0xd7ccc8))

        drawMinimap()

        // Обучение
        if (g.tutorial < g.tutorialTexts.size && g.state == SeaGame.State.PLAY) {
            val text = g.tutorialTexts[g.tutorial]
            val tw = min(wDp - 175, 300f)
            layout.setText(ui.mid, text, Color.WHITE, tw - 70, Align.left, true)
            val th = layout.height + 28
            val tx = if (g.leftHanded) wDp - tw - 16 else 16f
            val ty = 118 + g.safeBottom
            ui.round(b, tx, ty - 3, tw, th, c(0x0a141e, 0.5f))
            ui.round(b, tx, ty, tw, th, c(0x1b3a57, 0.92f))
            ui.mid.draw(b, layout, tx + 58, ty + th - 14)
            val bob = sin(g.now * 5) * 5
            ui.icon(b, ui.hand, tx + 30, ty + th / 2 + bob, 36f, 36f, 0f, c(0xffffff))
        }

        if (g.toastT > 0) {
            g.toastT -= dt
            val a = min(1f, g.toastT * 3)
            val font = if (g.toastText.length > 30) ui.mid else ui.big
            ui.text(b, font, g.toastText, 20f, hDp * 0.6f, Align.center, wDp - 40, true, Color(1f, 1f, 1f, a))
        }
        b.color = Color.WHITE
    }

    private fun questIcon(q: Quest, x: Float, y: Float, size: Float) {
        val b = g.batch
        val icon: TextureRegion? = when (q.type) {
            QType.COLLECT, QType.DELIVER -> ui.coin
            QType.TREASURE, QType.WRECK -> ui.xmark
            QType.ABILITY -> ui.fireIcon
            QType.BOARD -> ui.swordsIcon
            QType.FORTRESS -> ui.mortarIcon
            else -> null
        }
        if (icon != null) { ui.icon(b, icon, x, y, size * 1.1f, size * 1.1f); return }
        val glyph = when (q.type) {
            QType.KILL, QType.SERPENT, QType.KRAKEN, QType.ADMIRAL, QType.FLAGSHIP -> "☠"
            QType.RAM -> "⚔"; QType.REACH, QType.HIRE, QType.ZONE -> "⚓"; QType.UPGRADE -> "⚙"; QType.LEVEL -> "★"
            QType.PROTECT -> "❤"; else -> "★"
        }
        ui.text(b, ui.big, glyph, x - 30, y + 13, Align.center, 60f)
    }

    /** Мини-карта: север вверху, радиус обзора 420 м. Нажатие открывает карту мира. */
    private fun drawMinimap() {
        val b = g.batch
        val p = g.player ?: return
        val (cx, cy) = minimapCenter()
        val r = mapR
        val range = 420f
        ui.icon(b, ui.disc, cx, cy, r * 2 + 8, r * 2 + 8, 0f, c(0x3e2723, 0.85f))
        val waterCol = when (g.systems.region) { Region.ICE -> 0x5d8fb0; Region.FIRE -> 0x3d5566; Region.REEF -> 0x2f7f7c; Region.ABYSS -> 0x163a63; else -> 0x2a7fc0 }
        ui.icon(b, ui.disc, cx, cy, r * 2, r * 2, 0f, c(waterCol, 0.92f))
        fun pt(wx: Float, wz: Float, clampEdge: Boolean = false): Pair<Float, Float>? {
            var dx = (wx - p.x) / range * r
            var dy = -(wz - p.z) / range * r
            val d = hypot(dx, dy)
            if (d > r - 4) {
                if (!clampEdge) return null
                dx *= (r - 7) / d; dy *= (r - 7) / d
            }
            return (cx + dx) to (cy + dy)
        }
        for (isl in g.islands) {
            val s = isl.r / range * r * 2
            val col = when (regionAt(isl.x, isl.z)) { Region.ICE -> 0xf0f8ff; Region.FIRE -> 0x5d4b42; Region.REEF -> 0xd9cfa8; else -> 0xe6d3a3 }
            pt(isl.x, isl.z)?.let { (x, y) ->
                ui.icon(b, ui.disc, x, y, max(5f, s), max(5f, s), 0f, c(col))
                if (s > 8) ui.icon(b, ui.disc, x, y, s * 0.7f, s * 0.7f, 0f, c(if (regionAt(isl.x, isl.z) == Region.ICE) 0xdbe9f3 else 0x6fa84a))
            }
        }
        for (rk in g.systems.reefs) if (hypot(rk.x - p.x, rk.z - p.z) < 160) pt(rk.x, rk.z)?.let { (x, y) -> ui.icon(b, ui.xmark, x, y, 7f, 7f, 0f, c(0x263238)) }
        for (port in g.ports) pt(port.rx, port.rz)?.let { (x, y) -> ui.text(b, ui.small, "⚓", x - 10, y + 7, Align.center, 20f) }
        for (f in g.systems.fortresses) pt(f.x, f.z)?.let { (x, y) -> ui.icon(b, ui.squareIcon, x, y, 10f, 10f, 0f, if (f.alive) c(0xd84315) else c(0x757575)) }
        for (w in g.systems.wrecks) if (hypot(w.x - p.x, w.z - p.z) < 300) pt(w.x, w.z)?.let { (x, y) -> ui.icon(b, ui.disc, x, y, 7f, 7f, 0f, c(0xb3e5fc)) }
        for (bt in g.systems.bottles) if (hypot(bt.x - p.x, bt.z - p.z) < 500) pt(bt.x, bt.z, true)?.let { (x, y) -> ui.text(b, ui.small, "♪", x - 10, y + 7, Align.center, 20f, c = rgb(0xffe082)) }
        for (s in g.ships) {
            if (s === p || s.sinking) continue
            val col = when { s.ally -> c(0x4fc3f7); s.faction == Faction.NEUTRAL -> c(0xfff176); s.boss -> c(0xb388ff); else -> c(0xff5252) }
            val sz = if (s.boss) 13f else if (s.type == "fisher") 6f else 9f
            pt(s.x, s.z)?.let { (x, y) -> ui.icon(b, ui.arrowIcon, x, y, sz * 0.8f, sz, -s.h * MathUtils.radiansToDegrees, col) }
        }
        g.serpent?.let { sp -> if (!sp.dead) pt(sp.x, sp.z)?.let { (x, y) -> ui.icon(b, ui.disc, x, y, 11f, 11f, 0f, c(0x26a69a)) } }
        g.systems.kraken?.let { k -> if (!k.dead) pt(k.x, k.z)?.let { (x, y) -> ui.icon(b, ui.disc, x, y, 14f, 14f, 0f, c(0xd81b60)) } }
        g.treasure?.let { t -> pt(t.x, t.z, true)?.let { (x, y) -> ui.icon(b, ui.xmark, x, y, 14f, 14f) } }
        g.systems.waypoint?.let { w -> pt(w[0], w[1], true)?.let { (x, y) -> ui.icon(b, ui.disc, x, y, 10f, 10f, 0f, c(0x4dd0e1)) } }
        g.questTarget()?.let { t ->
            val pulse = 1f + sin(g.now * 5) * 0.2f
            pt(t.first, t.second, true)?.let { (x, y) -> ui.icon(b, ui.disc, x, y, 11f * pulse, 11f * pulse, 0f, c(0xffc93c)) }
        }
        // игрок
        ui.icon(b, ui.arrowIcon, cx, cy, 13f, 16f, -p.h * MathUtils.radiansToDegrees, c(0xffffff))
        ui.icon(b, ui.ring, cx, cy, r * 2 + 8, r * 2 + 8, 0f, c(0xd7b98e, 0.95f))
        // север и ветер
        ui.text(b, ui.small, "С", cx - 10, cy + r - 2, Align.center, 20f, c = rgb(0xffcdd2))
        val wx = cx + sin(g.windA) * (r + 2)
        val wy = cy + kotlin.math.cos(g.windA) * (r + 2)
        ui.icon(b, ui.arrowIcon, wx, wy, 11f, 14f, -g.windA * MathUtils.radiansToDegrees, c(0xb3e5fc))
        val tags = ArrayList<String>()
        tags += g.systems.region.title
        if (g.stormLevel > 0.3f) tags += "шторм"
        val tw = 2 * r + 60
        ui.text(b, ui.small, tags.joinToString(" · "), g.wDp - tw - 6, cy - r - 8, Align.right, tw, c = rgb(0xb3e5fc, 0.85f))
    }
}
