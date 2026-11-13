package com.seatrader.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Меню на Scene2D: порт, пауза, настройки, гибель, ежедневная награда. */
class Menus(private val g: SeaGame) {
    private val ui get() = g.ui
    private val stage get() = g.stage
    private var current: (() -> Unit)? = null
    private var portIndex = 0
    private var portTab = 0
    private var portScroll = 0f
    private var scrollRef: ScrollPane? = null
    private var resetArmed = false
    private var dailyIdx = 0

    fun close() {
        baseOpen = false
        stage.clear()
        current = null
        g.state = SeaGame.State.PLAY
        g.checkJournal()
    }

    fun relayout() { current?.invoke() }

    private fun open(state: SeaGame.State, builder: () -> Unit) {
        g.state = state
        current = builder
        builder()
    }

    private fun Actor.onClick(f: () -> Unit) {
        addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) { g.sfx.play("click"); f() }
        })
    }

    private fun overlay(): Table = Table().also {
        it.setFillParent(true)
        it.background = ui.flat(0x05141f, 0.72f)
        it.touchable = Touchable.enabled
        stage.addActor(it)
    }

    private fun panelTable() = Table().also { it.background = ui.panel(0x10304d); it.pad(14f) }
    private fun card(color: Int = 0x164266): Table = Table().also { it.background = ui.panel(color); it.pad(12f) }

    private fun currencyRow(): Table = Table().also {
        it.add(Image(ui.coin)).size(20f).padRight(4f)
        it.add(ui.label(fmt(g.coins), ui.mid)).padRight(14f)
        it.add(Image(ui.gem)).size(22f, 19f).padRight(4f)
        it.add(ui.label(fmt(g.gems), ui.mid))
    }

    private class Bar(val ui: Ui, val frac: Float, val color: Int = 0x3ddc84) : Actor() {
        override fun draw(batch: Batch, parentAlpha: Float) {
            ui.pill(batch, x, y + height / 2 - 4, width, 8f, rgb(0x0b2238))
            if (frac > 0) ui.pill(batch, x, y + height / 2 - 4, max(8f, width * frac.coerceIn(0f, 1f)), 8f, rgb(color))
            batch.color = Color.WHITE
        }
    }

    private class Pips(val ui: Ui, val level: Int) : Actor() {
        override fun draw(batch: Batch, parentAlpha: Float) {
            for (i in 0 until MAX_LEVEL) ui.pill(batch, x + i * 26, y + height / 2 - 4, 22f, 8f, if (i < level) rgb(0xffc93c) else rgb(0x0b2238))
            batch.color = Color.WHITE
        }
    }

    private class Swatch(val ui: Ui, val color: Int, val selected: Boolean) : Actor() {
        override fun draw(batch: Batch, parentAlpha: Float) {
            if (selected) ui.round(batch, x - 3, y - 3, width + 6, height + 6, rgb(0xffc93c))
            ui.round(batch, x, y, width, height, rgb(color))
            batch.color = Color.WHITE
        }
    }

    private fun statRow(t: Table, name: String, v: Float, max: Float, shown: String) {
        val r = Table()
        r.add(ui.label(name, ui.small, rgb(0xcfe3f7))).width(96f).left()
        r.add(Bar(ui, v / max)).growX().height(14f).padRight(8f)
        r.add(ui.label(shown, ui.small, align = Align.right)).width(52f).right()
        t.add(r).growX().padTop(2f).row()
    }

    /** Кнопка во всю ширину: длинный текст переносится, а не раздвигает окно на узких экранах. */
    private fun TextButton.wrapping(): TextButton {
        label.setWrap(true); label.setAlignment(Align.center); labelCell.minWidth(0f).growX()
        return this
    }

    private fun priceButton(text: String, gemsPrice: Int, can: Boolean): TextButton {
        val btn = if (can) ui.green(text) else ui.gray(text)
        // на узких экранах длинная надпись переносится, а не раздвигает окно
        btn.label.setWrap(true)
        btn.label.setAlignment(Align.center)
        btn.labelCell.minWidth(0f).growX()
        btn.add(Image(ui.coin)).size(20f).padLeft(6f)
        if (gemsPrice > 0) {
            btn.add(ui.label(" + $gemsPrice", ui.mid))
            btn.add(Image(ui.gem)).size(22f, 19f).padLeft(4f)
        }
        return btn
    }

    // ===================== Порт =====================
    fun showPort(i: Int) {
        portIndex = i
        sheetCollapsed = false
        baseOpen = false
        portScroll = 0f
        open(SeaGame.State.PORT) { buildPort() }
    }

    private fun refreshPort() {
        portScroll = scrollRef?.scrollY ?: 0f
        buildPort()
    }

    private var sheetCollapsed = false
    private val portChips = listOf("Верфь", "Улучшения", "Арсенал", "Рынок", "Флот", "Таверна", "Вид")
    private val portPlaces = listOf("Верфь: корабли", "Верфь: улучшения", "Арсенал", "Рыночная площадь", "Причал флота", "Таверна «Семь ветров»", "Парусная мастерская")

    private fun buildPort() {
        val prevScroll = portScroll
        g.portView.select(portTab)
        val content = Table().top()
        when (portTab) {
            0 -> shipCards(content)
            1 -> upgradeCards(content)
            2 -> arsenalCards(content)
            3 -> marketCards(content)
            4 -> fleetCards(content)
            5 -> tavernCards(content, portIndex)
            else -> paintCards(content)
        }
        sceneLayout("⚓ " + PORTS[portIndex].name, portChips, portTab, portPlaces[portTab], { i -> portTab = i; portScroll = 0f; buildPort() },
            content, prevScroll) { g.closePort() }
    }

    /**
     * Меню поверх живой сцены порта: сверху — название и места острова,
     * снизу (или справа) — панель выбранного места. По пустому месту можно провести пальцем — камера облетит остров.
     */
    private fun sceneLayout(title: String, chips: List<String>, sel: Int, place: String, onChip: (Int) -> Unit,
                            content: Table, prevScroll: Float, onExit: () -> Unit) {
        stage.clear()
        val root = Table()
        root.setFillParent(true)
        root.touchable = Touchable.enabled
        stage.addActor(root)
        root.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.DragListener() {
            override fun drag(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float, pointer: Int) {
                if (event?.target === root) g.portView.orbit -= deltaX * 0.006f
            }
        })
        val landscape = g.wDp > g.hDp
        // верхняя плашка
        val top = Table().also { it.background = ui.panel(0x0b2238, 0.82f); it.pad(8f, 10f, 8f, 10f) }
        val head = Table()
        val tl = ui.label(title, ui.big)
        tl.setEllipsis(true)
        head.add(tl).left().growX().minWidth(0f)
        head.add(currencyRow()).right().padLeft(8f)
        top.add(head).growX().row()
        val tabs = Table()
        chips.forEachIndexed { i, name ->
            val b = if (sel == i) ui.button(name, 0x2f8ff0, 0x1b6fd1, ui.small) else ui.button(name, 0x164266, 0x123a5c, ui.small)
            b.pad(5f, 2f, 5f, 2f)
            b.label.setWrap(false)
            // узкий экран: длинное название вкладки чуть мельче
            val avail = (if (landscape) min(g.wDp * 0.5f, 540f) else g.wDp - 12f) / (if (i < 3 || chips.size <= 4) 3f else 4f) - 10f
            val tw = ui.measure(ui.small, name)
            // масштаб задаётся абсолютно, поэтому учитываем базовый масштаб шрифта (1/плотность экрана)
            if (tw > avail - 26f) b.label.setFontScale(ui.small.data.scaleX * ((avail - 26f) / tw).coerceAtLeast(0.66f))
            b.onClick { sheetCollapsed = false; onChip(i) }
            tabs.add(b).growX().uniformX().minWidth(0f).height(34f).pad(2f)
            if (i == 2 && chips.size > 4) tabs.row()
        }
        top.add(tabs).growX().padTop(6f).row()
        // панель места
        val sheet = Table().also { it.background = ui.panel(0x10304d, 0.94f); it.pad(10f) }
        val bar = Table()
        bar.add(ui.label(place, ui.mid, rgb(0xffc93c))).left().growX()
        val toggle = ui.button(if (sheetCollapsed) "▲" else "▼", 0x164266, 0x123a5c, ui.mid)
        toggle.onClick { sheetCollapsed = !sheetCollapsed; relayout() }
        bar.add(toggle).size(44f, 36f)
        sheet.add(bar).growX().padBottom(if (sheetCollapsed) 0f else 6f).row()
        var scroll: ScrollPane? = null
        if (!sheetCollapsed) {
            // содержимое строго по ширине панели: на узких экранах ничего не вылезает за края
            val contentW = (if (landscape) min(g.wDp * 0.5f, 540f) else g.wDp - 12f) - 20f
            for (cell in content.cells) cell.width(contentW).minWidth(0f)
            val sc = ScrollPane(content, ui.scrollStyle)
            sc.setScrollingDisabled(true, false)
            sc.setFadeScrollBars(true)
            sc.setOverscroll(false, true)
            sheet.add(sc).grow().row()
            scroll = sc
        }
        val sail = ui.blue("⚓  Выйти в море")
        sail.onClick { onExit() }
        sheet.add(sail).growX().height(50f).padTop(8f).row()
        val avail = g.hDp - g.safeTop - g.safeBottom
        if (landscape) {
            val col = Table()
            col.add(top).growX().padBottom(6f).row()
            col.add(sheet).grow().row()
            root.add().expand().fill()
            root.add(col).width(min(g.wDp * 0.5f, 540f)).growY().pad(g.safeTop + 6f, 0f, g.safeBottom + 6f, 6f)
        } else {
            root.top()
            root.add(top).growX().pad(g.safeTop + 6f, 6f, 0f, 6f).row()
            root.add().expand().fill().row()
            val h = if (sheetCollapsed) 112f else avail * 0.5f
            root.add(sheet).growX().height(h).pad(0f, 6f, g.safeBottom + 6f, 6f).row()
        }
        stage.act(0f)
        root.layout(); top.layout(); sheet.layout()
        scroll?.let { it.layout(); it.scrollY = prevScroll; it.updateVisualScroll() }
        scrollRef = scroll
    }

    private fun shipCards(content: Table) {
        for (sdef in PLAYER_SHIPS) {
            val isOwned = sdef.id in g.owned
            val current = sdef.id == g.shipId
            val st = shipStats(sdef, if (isOwned) g.ensureUp(sdef.id) else null)
            val c = card(if (current) 0x1d5a46 else 0x164266)
            c.add(ui.label(sdef.cls.uppercase(), ui.small, rgb(0xffc93c))).left().row()
            c.add(ui.label(sdef.name, ui.big)).left().row()
            c.add(ui.label(sdef.desc, ui.small, rgb(0xb9d6f2), wrap = true)).growX().padBottom(6f).row()
            statRow(c, "Прочность", st.hp.toFloat(), 1000f, st.hp.toString())
            statRow(c, "Скорость", st.speed, 19f, String.format(java.util.Locale.US, "%.1f", st.speed))
            statRow(c, "Пушки", st.cannons.toFloat(), 12f, "${st.cannons}×2")
            statRow(c, "Урон", st.dmg, 24f, st.dmg.roundToInt().toString())
            statRow(c, "Таран", sdef.mass, 5f, String.format(java.util.Locale.US, "%.1f", sdef.mass))
            if (isOwned) {
                val xp = g.guilds.shipXp[sdef.id] ?: 0
                val lvl = shipLevelFor(xp)
                val nextXp = SHIP_LEVEL_XP.getOrNull(lvl)
                c.add(ui.label("Уровень корабля $lvl из $SHIP_MAX_LEVEL" + if (nextXp != null) " · опыт $xp / $nextXp" else " · максимум", ui.small, rgb(0xffe082))).left().padTop(4f).row()
                if (nextXp != null) c.add(Bar(ui, (xp - SHIP_LEVEL_XP[lvl - 1]).toFloat() / (nextXp - SHIP_LEVEL_XP[lvl - 1]), 0xffc93c)).growX().height(8f).padTop(2f).row()
            }
            val btn: TextButton = when {
                current -> ui.gray("✓ Ваш корабль").also { it.isDisabled = true }
                isOwned -> ui.blue("Выбрать").also { it.onClick { g.shipId = sdef.id; g.rebuildPlayer(); g.save(); refreshPort() } }
                else -> priceButton("Купить ${fmt(sdef.price)}", sdef.gems, g.coins >= sdef.price && g.gems >= sdef.gems)
                    .also { it.onClick { buyShip(sdef) } }
            }
            c.add(btn).growX().height(50f).padTop(8f).row()
            content.add(c).growX().padBottom(10f).row()
        }
    }

    private fun buyShip(def: ShipDef) {
        if (g.coins < def.price) { g.toast("Не хватает монет"); return }
        if (g.gems < def.gems) { g.toast("Не хватает кристаллов"); return }
        g.coins -= def.price
        g.gems -= def.gems
        g.owned += def.id
        g.ensureUp(def.id)
        g.shipId = def.id
        g.rebuildPlayer()
        g.sfx.play("quest")
        g.toast("Новый корабль: ${def.name}!")
        g.questEvent(QType.BUY, "", false)
        g.save()
        refreshPort()
    }

    private fun upgradeCards(content: Table) {
        val def = g.playerDef()
        val u = g.ensureUp(def.id)
        val head = card()
        head.add(ui.label(def.cls.uppercase(), ui.small, rgb(0xffc93c))).left().row()
        head.add(ui.label(def.name, ui.big)).left().row()
        head.add(ui.label("Корабль стоит в доке — улучшения сразу видны на нём. Проведите пальцем по сцене, чтобы облететь его.", ui.small, rgb(0xb9d6f2), wrap = true)).growX().row()
        head.add(ui.label("Уровень корабля ${g.guilds.shipLevel()} из $SHIP_MAX_LEVEL: высокие улучшения требуют опытного корабля (бои и плавания дают опыт).", ui.small, rgb(0xffe082), wrap = true)).growX().padTop(4f).row()
        val partNames = listOf("Корпус", "Паруса", "Пушки", "Заряжание")
        val seen = (0..3).filter { u[it] > 0 }.map { k -> "${partNames[k]}: " + UPGRADE_LOOKS[k][u[k] - 1] }
        if (seen.isNotEmpty()) head.add(ui.label("Видно на корабле:\n" + seen.joinToString("\n"), ui.small, rgb(0xffe082), wrap = true)).growX().padTop(6f).row()
        if (g.living.upgradeMul() < 1f) head.add(ui.label("Скидка верфи вашей базы: ${((1 - g.living.upgradeMul()) * 100).roundToInt()}%", ui.small, rgb(0x3ddc84))).left().padTop(4f).row()
        content.add(head).growX().padBottom(10f).row()
        val glyphs = listOf("❤", "⬆", "⚔", "⚙")
        for ((i, upg) in UPGRADES.withIndex()) {
            val lvl = u[i]
            val c = card()
            c.add(ui.label(glyphs[i], ui.huge, rgb(0xffc93c))).width(40f).padRight(8f)
            val info = Table()
            info.add(ui.label(upg.name, ui.big)).left().row()
            info.add(Pips(ui, lvl)).width(130f).height(14f).left().padTop(4f).padBottom(4f).row()
            info.add(ui.label("+${(upg.per * 100).roundToInt()}% ${upg.text} за уровень", ui.small, rgb(0xb9d6f2), wrap = true)).growX().row()
            if (lvl < MAX_LEVEL) info.add(ui.label("Вид: " + UPGRADE_LOOKS[i][lvl], ui.small, rgb(0xffe082), wrap = true)).growX().row()
            c.add(info).growX()
            val needShip = shipLevelForUpgrade(lvl + 1)
            val btn: TextButton = if (lvl >= MAX_LEVEL) ui.gray("МАКС").also { it.isDisabled = true }
            else if (g.guilds.shipLevel() < needShip) ui.gray("Ур. корабля $needShip").wrapping().also { it.isDisabled = true }
            else {
                val cost = (upgradeCost(def, lvl) * g.living.upgradeMul()).roundToInt()
                priceButton(fmt(cost), 0, g.coins >= cost).also { it.onClick { buyUpgrade(i) } }
            }
            c.add(btn).width(116f).height(50f).padLeft(8f)
            content.add(c).growX().padBottom(10f).row()
        }
    }

    private fun buyUpgrade(i: Int) {
        val def = g.playerDef()
        val u = g.ensureUp(def.id)
        if (u[i] >= MAX_LEVEL) return
        if (g.guilds.shipLevel() < shipLevelForUpgrade(u[i] + 1)) { g.toast("Нужен уровень корабля ${shipLevelForUpgrade(u[i] + 1)}: плавайте и сражайтесь"); return }
        val cost = (upgradeCost(def, u[i]) * g.living.upgradeMul()).roundToInt()
        if (g.coins < cost) { g.toast("Не хватает монет"); return }
        g.coins -= cost
        u[i]++
        g.rebuildPlayer()
        g.sfx.play("coin")
        g.questEvent(QType.UPGRADE, "", false)
        g.toast("На корабле: " + UPGRADE_LOOKS[i][u[i] - 1], 2f)
        g.save()
        refreshPort()
    }

    private fun fleetCards(content: Table) {
        val head = card()
        head.add(ui.label("Флот сопровождения", ui.big)).left().row()
        head.add(ui.label("Наёмники плывут рядом с вами и сами вступают в бой. Можно нанять до $MAX_ALLIES кораблей. Потопленный наёмник уходит из флота.",
            ui.small, rgb(0xb9d6f2), wrap = true)).growX().row()
        content.add(head).growX().padBottom(10f).row()
        for ((i, id) in g.allies.withIndex()) {
            val m = MERCS.first { it.id == id }
            val c = card(0x1d5a46)
            c.add(ui.label("В строю: ${m.name}", ui.mid, wrap = true)).growX().left()
            val fire = ui.button("Уволить", 0x8a3b3b, 0x6d2c2c, ui.small)
            fire.onClick {
                g.allies.removeAt(i)
                g.spawnAllies()
                g.save()
                refreshPort()
            }
            c.add(fire).height(42f).padLeft(8f)
            content.add(c).growX().padBottom(10f).row()
        }
        for (m in MERCS) {
            val c = card()
            c.add(ui.label(m.name, ui.big)).left().row()
            statRow(c, "Прочность", m.hp.toFloat(), 500f, m.hp.toString())
            statRow(c, "Скорость", m.speed, 19f, String.format(java.util.Locale.US, "%.1f", m.speed))
            statRow(c, "Пушки", m.cannons.toFloat(), 12f, "${m.cannons}×2")
            val full = g.allies.size >= MAX_ALLIES
            val btn = if (full) ui.gray("Флот полон").also { it.isDisabled = true }
            else priceButton("Нанять за ${fmt(m.price)}", 0, g.coins >= m.price).also {
                it.onClick {
                    if (g.coins < m.price) { g.toast("Не хватает монет"); return@onClick }
                    g.coins -= m.price
                    g.allies += m.id
                    g.spawnAllies()
                    g.sfx.play("quest")
                    g.questEvent(QType.HIRE, "", false)
                    g.save()
                    refreshPort()
                }
            }
            c.add(btn).growX().height(50f).padTop(8f).row()
            content.add(c).growX().padBottom(10f).row()
        }
    }

    private fun paintCards(content: Table) {
        val ed = card(0x2b1d12)
        ed.add(ui.label("Свой флаг и узор парусов", ui.big)).left().row()
        ed.add(ui.label("Нарисуйте флаг пальцем и выберите узор парусов — всё сразу видно на корабле в доке.", ui.small, rgb(0xd7ccc8), wrap = true)).growX().row()
        val row = Table()
        row.add(ui.blue("✎ Флаг").also { it.onClick { open(SeaGame.State.PORT) { buildFlagEditor() } } }).growX().height(46f).padRight(6f)
        row.add(ui.blue("✎ Паруса").also { it.onClick { open(SeaGame.State.PORT) { buildSailEditor() } } }).growX().height(46f)
        ed.add(row).growX().padTop(8f).row()
        content.add(ed).growX().padBottom(10f).row()
        for ((title, list, owned, selected) in listOf(
            Quad("Цвет парусов", SAIL_COLORS, g.ownedSails, g.sailIdx),
            Quad("Цвет флага", FLAG_COLORS, g.ownedFlags, g.flagIdx),
        )) {
            val c = card()
            c.add(ui.label(title, ui.big)).left().colspan(2).row()
            for ((i, paint) in list.withIndex()) {
                val row = Table()
                val color = if (paint.color < 0) g.playerDef().look.flag else if (i == 0 && list === SAIL_COLORS) g.playerDef().look.sail else paint.color
                row.add(Swatch(ui, color, i == selected)).size(34f).padRight(10f)
                row.add(ui.label(paint.name, ui.mid)).growX().left()
                val isSails = list === SAIL_COLORS
                val btn: TextButton = when {
                    i == selected -> ui.gray("✓").also { it.isDisabled = true }
                    i in owned -> ui.blue("Выбрать").also { it.onClick { applyPaint(isSails, i) } }
                    else -> priceButton(fmt(paint.price), 0, g.coins >= paint.price).also {
                        it.onClick {
                            if (g.coins < paint.price) { g.toast("Не хватает монет"); return@onClick }
                            g.coins -= paint.price
                            owned += i
                            applyPaint(isSails, i)
                            g.sfx.play("coin")
                        }
                    }
                }
                row.add(btn).width(116f).height(44f)
                c.add(row).growX().padTop(6f).row()
            }
            content.add(c).growX().padBottom(10f).row()
        }
    }

    private data class Quad(val title: String, val list: List<Paint>, val owned: MutableSet<Int>, val selected: Int)

    private fun applyPaint(sails: Boolean, i: Int) {
        if (sails) g.sailIdx = i else g.flagIdx = i
        g.rebuildPlayer()
        g.save()
        refreshPort()
    }

    // ===================== Пауза и настройки =====================
    fun showPause() {
        resetArmed = false
        open(SeaGame.State.PAUSE) { buildPause() }
    }

    private fun buildPause() {
        stage.clear()
        val root = overlay()
        val panel = panelTable()
        panel.add(ui.label("Пауза", ui.huge, align = Align.center)).growX().row()
        panel.add(ui.label("Потоплено пиратов: ${g.kills} · Заданий выполнено: ${g.questIdx}", ui.small, rgb(0xb9d6f2), wrap = true, align = Align.center))
            .growX().padTop(6f).padBottom(12f).row()
        val resume = ui.green("▶  Продолжить").also { it.onClick { close() } }
        val captain = ui.blue("★  Капитан" + if (g.systems.skillPts > 0) " (+${g.systems.skillPts})" else "").also { it.onClick { current = { buildCaptain() }; buildCaptain() } }
        val ach = ui.blue("✓  Достижения").also { it.onClick { current = { buildAchievements() }; buildAchievements() } }
        val map = ui.blue("⚓  Карта мира").also { it.onClick { mapFromPause = true; current = { buildMap() }; buildMap() } }
        val story = ui.blue("☠  Предыстория").also { it.onClick { close(); g.startPrologue(false) } }
        val songs = ui.blue("♪  Песни команды").also { it.onClick { current = { buildSongs() }; buildSongs() } }
        val help = ui.blue("?  Как играть").also { it.onClick { current = { buildHelp() }; buildHelp() } }
        val settings = ui.blue("⚙  Настройки").also { it.onClick { current = { buildSettings() }; buildSettings() } }
        val reset = ui.gray(if (resetArmed) "Нажмите ещё раз для сброса" else "↺  Начать заново").also {
            it.onClick {
                if (!resetArmed) { resetArmed = true; buildPause() } else { resetArmed = false; g.resetProgress() }
            }
        }
        val list = Table()
        for (bt in listOf(resume, captain, ach, map, story, songs, help, settings, reset)) list.add(bt).growX().height(46f).padTop(6f).row()
        fitPanel(root, panel, list, 420f)
    }

    private fun sliderRow(panel: Table, name: String, min: Float, max: Float, value: Float, onChange: (Float) -> Unit) {
        val label = ui.label(name, ui.mid)
        panel.add(label).left().padTop(10f).row()
        val s = Slider(min, max, (max - min) / 100f, false, ui.slider())
        s.value = value
        s.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) { onChange(s.value) }
        })
        panel.add(s).growX().height(36f).row()
    }

    private fun buildSettings() {
        stage.clear()
        val root = overlay()
        val panel = panelTable()
        panel.add(ui.label("Настройки", ui.huge, align = Align.center)).growX().padBottom(6f).row()
        sliderRow(panel, "Звуки", 0f, 1f, g.sfx.sfxVolume) { g.sfx.sfxVolume = it }
        sliderRow(panel, "Музыка", 0f, 1f, g.sfx.musicVolume) { g.sfx.musicVolume = it }
        sliderRow(panel, "Чувствительность джойстика", 0f, 1f, 1f - (g.joySens - 55f) / 70f) { g.joySens = 55f + (1f - it) * 70f }
        val hand = ui.blue(if (g.leftHanded) "Способности: слева" else "Способности: справа").also {
            it.onClick { g.leftHanded = !g.leftHanded; g.save(); buildSettings() }
        }
        val gfxNames = listOf("Низкая", "Средняя", "Высокая", "Ультра")
        val quality = ui.blue("Графика: ${gfxNames[g.gfx]}" + if (g.autoGfx) " (авто)" else "").also {
            it.onClick {
                // по кругу: ультра → высокая → средняя → низкая → авто (ультра)
                if (!g.autoGfx && g.gfx == 0) { g.autoGfx = true; g.applyGfx(3) }
                else { g.autoGfx = false; g.applyGfx(if (g.gfx == 0) 3 else g.gfx - 1) }
                buildSettings()
            }
        }
        val gfxHint = ui.label(when (g.gfx) {
            0 -> "Без теней и отражений, простая вода — для слабых телефонов"
            1 -> "Тени, без отражений, упрощённая вода"
            2 -> "Тени, отражения, подробная вода"
            else -> "Всё на максимум: тени 4096, отражения, суперсэмплинг"
        } + if (g.autoGfx) ". Авто: при тормозах графика снизится сама" else "", ui.small, rgb(0xb9d6f2), wrap = true)
        val auto = ui.blue(if (g.systems.autoFire) "Автострельба: вкл" else "Автострельба: выкл (только прицел)").also {
            it.onClick { g.systems.autoFire = !g.systems.autoFire; g.save(); buildSettings() }
        }
        val vib = ui.blue(if (g.systems.vibrate) "Вибрация: вкл" else "Вибрация: выкл").also {
            it.onClick { g.systems.vibrate = !g.systems.vibrate; g.save(); buildSettings() }
        }
        val saveBtn = ui.blue("Сохранение и перенос").also { it.onClick { current = { buildSaveMenu() }; buildSaveMenu() } }
        val tut = ui.blue("Показать обучение снова").also {
            it.onClick { g.tutorial = 0; g.tutorialT = 0f; g.save(); g.toast("Обучение начнётся заново") }
        }
        val back = ui.green("Готово").also { it.onClick { g.save(); current = { buildPause() }; buildPause() } }
        val list = Table()
        for (bt in listOf(hand, quality)) list.add(bt).growX().height(44f).padTop(6f).row()
        list.add(gfxHint).growX().padTop(2f).row()
        for (bt in listOf(auto, vib, saveBtn, tut, back)) list.add(bt).growX().height(44f).padTop(6f).row()
        fitPanel(root, panel, list, 420f)
    }

    /** Кладёт список кнопок в панель; если по высоте не помещается (альбомная ориентация) — с прокруткой. */
    private fun fitPanel(root: Table, panel: Table, list: Table, maxW: Float) {
        val landscape = g.wDp > g.hDp
        if (landscape) {
            val scroll = ScrollPane(list, ui.scrollStyle).also { it.setScrollingDisabled(true, false); it.setOverscroll(false, true) }
            panel.add(scroll).grow().row()
            root.add(panel).width(min(g.wDp - 32, maxW)).height(g.hDp - g.safeTop - g.safeBottom - 16)
        } else {
            panel.add(list).growX().row()
            root.add(panel).width(min(g.wDp - 32, maxW))
        }
    }

    // ===================== Гибель =====================
    fun showDeath() = open(SeaGame.State.DEAD) { buildDeath() }

    private fun buildDeath() {
        stage.clear()
        val root = overlay()
        val panel = panelTable()
        panel.add(ui.label("☠ Корабль потоплен!", ui.big, align = Align.center)).growX().row()
        val lostTxt = if (g.extras.lost != null) "Груз и ${g.extras.lostCoins()} монет остались на месте крушения — отметка на карте. Успейте их забрать: если погибнете снова, груз пропадёт.\n" else ""
        panel.add(ui.label(lostTxt + "Корабль отбуксируют в порт «${PORTS[g.homePort].name}».",
            ui.small, rgb(0xb9d6f2), wrap = true, align = Align.center)).growX().pad(8f, 0f, 12f, 0f).row()
        val btn = ui.green("⚓  Вернуться в порт")
        btn.onClick {
            g.spawnPlayer()
            val p = g.player!!
            g.ships.removeAll { it.faction == Faction.PIRATE && hypot(it.x - p.x, it.z - p.z) < 280 }
            close()
            g.save()
        }
        panel.add(btn).growX().height(54f).row()
        root.add(panel).width(min(g.wDp - 32, 420f))
    }

    // ===================== Ежедневная награда =====================
    fun showDaily(idx: Int) {
        dailyIdx = idx
        open(SeaGame.State.DIALOG) { buildDaily() }
    }

    private fun buildDaily() {
        stage.clear()
        val root = overlay()
        val panel = panelTable()
        panel.add(ui.label("Ежедневная награда", ui.big, align = Align.center)).growX().row()
        panel.add(ui.label("Заходите каждый день: награда растёт!", ui.small, rgb(0xb9d6f2), align = Align.center)).growX().padBottom(10f).row()
        val grid = Table()
        for ((i, r) in DAILY.withIndex()) {
            val c = card(if (i == dailyIdx) 0x2f8ff0 else if (i < dailyIdx) 0x1d5a46 else 0x164266)
            c.pad(6f)
            c.add(ui.label("День ${i + 1}", ui.small)).row()
            c.add(Image(if (r.gems > 0 && i == DAILY.lastIndex) ui.gem else ui.coin)).size(26f).pad(4f).row()
            c.add(ui.label(fmt(r.coins), ui.small)).row()
            if (r.gems > 0) c.add(ui.label("+${r.gems}", ui.small, rgb(0xff9cff))).row()
            if (i < dailyIdx) c.add(ui.label("✓", ui.mid, rgb(0x3ddc84))).row()
            grid.add(c).width(70f).height(118f).pad(3f)
            if (i == 3) grid.row()
        }
        panel.add(grid).row()
        val btn = ui.green("Забрать")
        btn.onClick { g.claimDaily(dailyIdx); close() }
        panel.add(btn).growX().height(54f).padTop(10f).row()
        root.add(panel).width(min(g.wDp - 16, 420f))
    }

    // ===================== Арсенал =====================
    private val weaponIcons get() = listOf(ui.mortarIcon, ui.rocketIcon, ui.mineIcon)

    private fun arsenalCards(content: Table) {
        val head = card()
        head.add(ui.label("Арсенал", ui.big)).left().row()
        head.add(ui.label("Дополнительное оружие появляется кнопкой рядом со способностями.", ui.small, rgb(0xb9d6f2), wrap = true)).growX().row()
        content.add(head).growX().padBottom(10f).row()
        for ((k, entry) in WEAPON_PRICES.entries.withIndex()) {
            val i = entry.key
            val (price, gemsPrice) = entry.value
            val a = ABILITIES[i]
            val c = card(if (i in g.systems.ownedWeapons) 0x1d5a46 else 0x164266)
            c.add(Image(weaponIcons[k])).size(48f).padRight(10f)
            val info = Table()
            info.add(ui.label(a.name, ui.big)).left().row()
            info.add(ui.label(a.desc, ui.small, rgb(0xb9d6f2), wrap = true)).growX().row()
            info.add(ui.label("Перезарядка ${a.cooldown.toInt()} с", ui.small, rgb(0xffc93c))).left().row()
            c.add(info).growX().row()
            val btn = if (i in g.systems.ownedWeapons) ui.gray("✓ Куплено").also { it.isDisabled = true }
            else priceButton("Купить ${fmt(price)}", gemsPrice, g.coins >= price && g.gems >= gemsPrice).also {
                it.onClick {
                    if (g.coins < price || g.gems < gemsPrice) { g.toast("Не хватает средств"); return@onClick }
                    g.coins -= price; g.gems -= gemsPrice
                    g.systems.ownedWeapons += i
                    g.sfx.play("quest"); g.save(); refreshPort()
                }
            }
            c.add(btn).colspan(2).growX().height(48f).padTop(8f).row()
            content.add(c).growX().padBottom(10f).row()
        }
    }

    // ===================== Рынок =====================
    private fun marketCards(content: Table) {
        val sy = g.systems
        val head = card()
        head.add(ui.label("Рыночная площадь", ui.big)).left().row()
        head.add(ui.label("Трюм: ${sy.cargoUsed()} из ${sy.capacity()}. Покупайте там, где дёшево, и продавайте там, где дорого.",
            ui.small, rgb(0xb9d6f2), wrap = true)).growX().row()
        val q = g.quest()
        if (q.type == QType.DELIVER) head.add(ui.label("Заказ: ${q.text} (${g.qprog}/${q.n})", ui.small, rgb(0xffc93c), wrap = true)).growX().padTop(4f).row()
        content.add(head).growX().padBottom(10f).row()
        contractCard(content, portIndex)
        for ((gi, good) in GOODS.withIndex()) {
            val buy = sy.buyPrice(portIndex, gi)
            val sell = sy.sellPrice(portIndex, gi)
            val mult = PORT_PRICES[portIndex][gi]
            val tag = when { mult < 0.85f -> "дёшево" to 0x3ddc84; mult > 1.25f -> "дорого" to 0xff8a65; else -> "" to 0xffffff }
            val c = card(0x2b1d12)
            c.add(Stall(ui, gi, buy, sell, tag.first, if (tag.second == 0x3ddc84) 0x81c784 else 0xffab91, sy.cargo[gi])).growX().height(150f).row()
            if (sy.cargo[gi] > 0) c.add(ui.label("куплено в среднем по ${(sy.cargoCost[gi] / sy.cargo[gi]).roundToInt()}", ui.small, rgb(0xd7ccc8))).left().padTop(2f).row()
            val row = Table()
            val b1 = ui.button("+1", 0x2fbf6e, 0x22a95c, ui.small).also { it.onClick { if (sy.buy(portIndex, gi, 1) == 0) g.toast("Нет места или монет"); g.save(); refreshPort() } }
            val b5 = ui.button("+5", 0x2fbf6e, 0x22a95c, ui.small).also { it.onClick { if (sy.buy(portIndex, gi, 5) == 0) g.toast("Нет места или монет"); g.save(); refreshPort() } }
            val s1 = ui.button("-1", 0xb9761e, 0x99611a, ui.small).also { it.onClick { sy.sell(portIndex, gi, 1); g.sfx.play("coin"); g.save(); refreshPort() } }
            val sa = ui.button("Продать всё", 0xb9761e, 0x99611a, ui.small).also { it.onClick { if (sy.sell(portIndex, gi, sy.cargo[gi]) > 0) g.sfx.play("coin"); g.save(); refreshPort() } }
            for (bt in listOf(b1, b5, s1, sa)) row.add(bt).growX().height(40f).pad(2f)
            c.add(row).growX().padTop(6f).row()
            content.add(c).growX().padBottom(8f).row()
        }
    }

    // ===================== Таверна =====================
    private fun tavernCards(content: Table, port: Int) {
        val lv = g.living
        guildCard(content)
        // Репутация
        val rc = card()
        rc.add(ui.label("Репутация", ui.big)).left().row()
        for (i in 0..2) {
            val r = Table()
            r.add(ui.label(lv.repNames[i], ui.small, rgb(0xcfe3f7))).width(84f).left()
            r.add(RepBar(ui, lv.rep[i])).growX().minWidth(36f).height(16f).padRight(6f)
            r.add(ui.label(lv.repLabel(lv.rep[i]), ui.small, rgb(if (lv.rep[i] >= 20) 0x3ddc84 else if (lv.rep[i] <= -20) 0xff8a80 else 0xffffff), align = Align.right)).width(100f).right()
            rc.add(r).growX().padTop(3f).row()
        }
        rc.add(ui.label("Торговля поднимает доверие купцов. Охота на пиратов радует флот и злит пиратов. Товары в Приюте контрабандистов — дружба с пиратами.",
            ui.small, rgb(0xb9d6f2), wrap = true)).growX().padTop(6f).row()
        rc.add(priceButton("Откуп пиратам 300", 0, g.coins >= 300).also { it.onClick { lv.tribute(); refreshAny() } }).growX().height(46f).padTop(6f).row()
        content.add(rc).growX().padBottom(10f).row()
        val mc = card(0x3e2a1a)
        mc.add(ui.label("Дух команды: ${g.extras.morale.roundToInt()} — ${g.extras.moraleLabel()}", ui.mid, rgb(g.extras.moraleColor()), wrap = true)).growX().row()
        mc.add(Bar(ui, g.extras.morale / 100f, g.extras.moraleColor())).growX().height(14f).padTop(4f).row()
        mc.add(ui.label("Победы, добыча, песни и порт поднимают дух; поражения, шторма и долгое плавание без добычи — опускают. Бодрая команда быстрее заряжает пушки и сильнее в абордаже, а при упадке может взбунтоваться.",
            ui.small, rgb(0xd7ccc8), wrap = true)).growX().padTop(4f).row()
        mc.add(priceButton("Ром для команды 80", 0, g.coins >= 80).also {
            it.onClick { if (g.coins < 80) g.toast("Не хватает монет") else { g.coins -= 80; g.extras.addMorale(15f); g.sfx.play("coin"); g.save(); refreshAny() } }
        }).growX().height(46f).padTop(6f).row()
        content.add(mc).growX().padBottom(10f).row()
        // Команда
        val cc = card(0x1d3b2e)
        cc.add(ui.label("Офицеры на борту", ui.big)).left().row()
        for (role in 0..3) {
            val r = Table()
            val id = lv.crew[role]
            if (id >= 0) {
                val o = OFFICERS[id]
                r.add(PortraitActor(ui, o)).size(48f).padRight(8f)
                val t = Table()
                t.add(ui.label(ROLES[role] + ": " + o.name, ui.small, rgb(0xffc93c))).left().row()
                t.add(ui.label(o.bonusText, ui.small, rgb(0xc8e6c9), wrap = true)).growX().row()
                r.add(t).growX()
                r.add(ui.button("Списать", 0x6d4c41, 0x5d4037, ui.small).also { it.onClick { lv.dismiss(role); refreshAny() } }).width(92f).height(38f)
            } else {
                r.add(ui.label(ROLES[role] + ": — место свободно —", ui.small, rgb(0x90a4ae))).left().growX()
            }
            cc.add(r).growX().padTop(5f).row()
        }
        content.add(cc).growX().padBottom(10f).row()
        // Офицеры для найма
        val here = OFFICERS.filter { lv.officerAvailable(it, port) && lv.crew[it.role] != it.id }
        if (here.isEmpty()) content.add(card().also { it.add(ui.label("Здесь сейчас нет свободных офицеров. Загляните в другие порты.", ui.small, rgb(0xb9d6f2), wrap = true)).growX() }).growX().padBottom(10f).row()
        for (o in here) {
            val c = card()
            val top = Table()
            top.add(PortraitActor(ui, o)).size(76f).padRight(10f)
            val t = Table()
            t.add(ui.label(ROLES[o.role].uppercase(), ui.small, rgb(0xffc93c))).left().row()
            t.add(ui.label(o.name, ui.big)).left().row()
            t.add(ui.label(o.bonusText, ui.small, rgb(0x3ddc84), wrap = true)).growX().row()
            top.add(t).growX()
            c.add(top).growX().row()
            c.add(ui.label(o.story, ui.small, rgb(0xb9d6f2), wrap = true)).growX().padTop(6f).row()
            val cur = lv.crew[o.role]
            if (cur >= 0) c.add(ui.label("Заменит: ${OFFICERS[cur].name}", ui.small, rgb(0xffab91))).left().padTop(2f).row()
            val price = lv.officerPrice(o)
            val btn = if (o.id in lv.hired) ui.blue("Назначить: ${ROLES[o.role].lowercase()}").wrapping()
                else priceButton("Нанять ${fmt(price)}", o.gems, g.coins >= price && g.gems >= o.gems)
            btn.onClick { if (lv.hire(o)) refreshAny() }
            c.add(btn).growX().height(48f).padTop(8f).row()
            content.add(c).growX().padBottom(10f).row()
        }
        // Доска розыска
        val bc = card(0x3e2a1a)
        bc.add(ui.label("☠ Разыскиваются", ui.big, rgb(0xffe0b2))).left().row()
        bc.add(ui.label("Примите заказ — капитан сам выйдет на охоту за вами. Награду выплачивают сразу после победы; флот доплачивает тем, кого уважает.",
            ui.small, rgb(0xffe0b2), wrap = true)).growX().padBottom(6f).row()
        for (cpt in CAPTAINS) {
            val r = Table().also { it.background = ui.panel(0x4e3420); it.pad(8f) }
            val done = cpt.id in lv.defeated
            r.add(ui.label(cpt.name + if (done) "  ✓ пойман" else "", ui.mid, rgb(if (done) 0x9e9e9e else 0xffffff))).left().row()
            if (!done) {
                r.add(ui.label(ENEMIES.getValue(cpt.type).name + " · " + cpt.traitText, ui.small, rgb(0xffc93c), wrap = true)).growX().row()
                r.add(ui.label(cpt.story, ui.small, rgb(0xd7ccc8), wrap = true)).growX().row()
                r.add(ui.label("Награда: ${fmt(cpt.bounty)} монет и ${cpt.gems} кристаллов", ui.small, rgb(0x3ddc84))).left().padTop(2f).row()
                val b = when {
                    lv.activeBounty == cpt.id -> ui.gray("Охота идёт…").wrapping().also { it.isDisabled = true }
                    lv.activeBounty >= 0 -> ui.gray("Сначала текущий заказ").wrapping().also { it.isDisabled = true }
                    else -> ui.button("Взять заказ", 0xb71c1c, 0x8e1515, ui.mid).wrapping().also { it.onClick { lv.acceptBounty(cpt.id); refreshAny() } }
                }
                r.add(b).growX().height(42f).padTop(6f).row()
            }
            bc.add(r).growX().padTop(6f).row()
        }
        content.add(bc).growX().padBottom(10f).row()
    }

    private fun refreshAny() { if (g.state == SeaGame.State.PORT && baseOpen) buildBaseMenu() else refreshPort() }

    // ===================== База игрока =====================
    private var baseOpen = false
    private var baseTab = 0

    fun showBase() {
        baseOpen = true
        sheetCollapsed = false
        baseTab = 0
        portScroll = 0f
        open(SeaGame.State.PORT) { buildBaseMenu() }
    }

    private fun buildBaseMenu() {
        val prevScroll = scrollRef?.scrollY ?: 0f
        val lv = g.living
        val content = Table().top()
        if (!lv.baseFounded) {
            val c = card()
            c.add(ui.label("Ничейный остров", ui.big)).left().row()
            c.add(ui.label("Тихий остров недалеко от Гавани Ветров. Здесь можно основать собственную базу: причал, склад, таверну, плантацию, маяк, батарею и верфь. Постройки приносят доход, даже когда вы в плавании (и немного — пока игра закрыта).",
                ui.small, rgb(0xb9d6f2), wrap = true)).growX().padTop(4f).row()
            c.add(priceButton("Основать базу ${fmt(BASE_FOUND_COST)}", 0, g.coins >= BASE_FOUND_COST).also { it.onClick { lv.foundBase(); buildBaseMenu() } }).growX().height(52f).padTop(10f).row()
            content.add(c).growX().row()
        } else {
            if (baseTab == 1) tavernCards(content, Living.BASE_PORT) else {
                val bank = card(0x1d5a46)
                bank.add(ui.label("Казна базы: ${lv.bank.toInt()} из ${lv.bankCap()}", ui.mid, wrap = true)).growX().left().row()
                bank.add(ui.label("Доход: ${lv.incomePerMin()} монет в минуту. Склад увеличивает казну. Корабль здесь чинится бесплатно.", ui.small, rgb(0xc8e6c9), wrap = true)).growX().row()
                bank.add(ui.green("Забрать ${lv.bank.toInt()}").also { it.onClick { lv.collectBank(); buildBaseMenu() } }).growX().height(48f).padTop(8f).row()
                content.add(bank).growX().padBottom(10f).row()
                for ((i, bd) in BUILDINGS.withIndex()) {
                    val lvl = lv.blev[i]
                    val c = card()
                    val top = Table()
                    top.add(ui.label(bd.name, ui.big)).left()
                    top.add().growX()
                    top.add(ui.label(if (lvl == 0) "не построено" else "ур. $lvl из 3", ui.small, rgb(if (lvl > 0) 0xffc93c else 0x90a4ae))).right()
                    c.add(top).growX().row()
                    if (lvl > 0) c.add(ui.label("Сейчас: " + bd.effects[lvl - 1], ui.small, rgb(0xc8e6c9), wrap = true)).growX().row()
                    if (lvl < 3) {
                        c.add(ui.label((if (lvl == 0) "Даст: " else "Дальше: ") + bd.effects[lvl], ui.small, rgb(0xb9d6f2), wrap = true)).growX().row()
                        val cost = buildingCost(i, lvl)
                        c.add(priceButton((if (lvl == 0) "Построить " else "Улучшить ") + fmt(cost), 0, g.coins >= cost).also { it.onClick { lv.upgradeBuilding(i); buildBaseMenu() } }).growX().height(46f).padTop(6f).row()
                    }
                    content.add(c).growX().padBottom(8f).row()
                }
            }
        }
        g.portView.select(baseTab)
        val chips = if (lv.baseFounded) listOf("Постройки", "Таверна") else listOf("Остров")
        sceneLayout("⚑ Остров Надежды", chips, if (lv.baseFounded) baseTab else 0, if (!lv.baseFounded) "Ничейный остров" else if (baseTab == 1) "Таверна базы" else "Постройки базы",
            { i -> if (i == 1 && lv.blev[2] == 0) g.toast("Сначала постройте таверну") else { baseTab = i; buildBaseMenu() } },
            content, prevScroll) { g.closePort() }
    }

    // ===================== Эпизоды: высадки, ныряние =====================
    fun showEpisode(ep: Episode, resolver: (String) -> String) = open(SeaGame.State.DIALOG) { buildEpisode(ep, resolver, null) }

    private fun buildEpisode(ep: Episode, resolver: (String) -> String, result: String?) {
        stage.clear()
        val root = overlay()
        val panel = Table().also { it.background = ui.panel(0x3e2a1a); it.pad(18f) }
        panel.add(ui.label(ep.title, ui.big, rgb(0xffe0b2), wrap = true, align = Align.center)).growX().row()
        if (result == null) {
            panel.add(ui.label(ep.text, ui.mid, rgb(0xfff3e0), wrap = true)).growX().pad(12f, 0f, 12f, 0f).row()
            for (ch in ep.choices) {
                val b = ui.button(ch.text, 0x8d6e4a, 0x6d4c2f, ui.mid)
                b.label.setWrap(true)
                b.onClick {
                    val res = resolver(ch.act)
                    current = { buildEpisode(ep, resolver, res) }
                    buildEpisode(ep, resolver, res)
                }
                panel.add(b).growX().minHeight(48f).padTop(6f).row()
            }
        } else {
            panel.add(ui.label(result, ui.mid, rgb(0xfff3e0), wrap = true)).growX().pad(12f, 0f, 12f, 0f).row()
            panel.add(ui.green("⚓  Вернуться на корабль").also { it.onClick { close() } }).growX().height(52f).row()
        }
        root.add(panel).width(min(g.wDp - 24, 460f))
    }

    // ===================== Белый флаг, бунт, сдача пиратов =====================
    private fun simpleDialog(title: String, text: String, options: List<Pair<String, () -> String?>>) {
        stage.clear()
        val root = overlay()
        val panel = Table().also { it.background = ui.panel(0x3e2a1a); it.pad(18f) }
        panel.add(ui.label(title, ui.big, rgb(0xffe0b2), wrap = true, align = Align.center)).growX().row()
        panel.add(ui.label(text, ui.mid, rgb(0xfff3e0), wrap = true)).growX().pad(10f, 0f, 10f, 0f).row()
        for ((label, fn) in options) {
            val b = ui.button(label, 0x8d6e4a, 0x6d4c2f, ui.mid).wrapping()
            b.onClick {
                val res = fn()
                if (res == null) close() else simpleDialog(title, res, listOf<Pair<String, () -> String?>>("Продолжить" to { null }))
            }
            panel.add(b).growX().minHeight(48f).padTop(6f).row()
        }
        root.add(panel).width(min(g.wDp - 24, 460f))
    }

    /** Причина прошлого вылета: игрок может прислать скриншот. */
    fun showError(text: String) = open(SeaGame.State.DIALOG) {
        stage.clear()
        val root = overlay()
        val panel = Table().also { it.background = ui.panel(0x3e2a1a); it.pad(16f) }
        panel.add(ui.label("Игра закрылась с ошибкой", ui.big, rgb(0xffe0b2), wrap = true, align = Align.center)).growX().row()
        panel.add(ui.label("Пришлите, пожалуйста, скриншот этого окна — по нему ошибку можно исправить.", ui.small, rgb(0xfff3e0), wrap = true)).growX().padTop(6f).row()
        val lines = text.lines().filter { it.isNotBlank() }
        val head = lines.take(2) + lines.filter { it.contains("seatrader") || it.startsWith("Caused") || it.contains("Exception") || it.contains("Error") }.distinct().take(12)
        val body = ui.label(head.distinct().joinToString("\n") { it.trim().take(140) }, ui.small, rgb(0xffab91), wrap = true)
        panel.add(ScrollPane(body)).growX().height(min(g.hDp * 0.5f, 340f)).padTop(8f).row()
        val ok = ui.button("Продолжить", 0x8d6e4a, 0x6d4c2f, ui.mid).also { it.onClick { close(); if (g.guilds.guild < 0) g.startPrologue(true) } }
        panel.add(ok).growX().minHeight(48f).padTop(8f).row()
        root.add(panel).width(min(g.wDp - 24, 520f))
    }

    fun showWhiteFlag() = open(SeaGame.State.DIALOG) {
        val ex = g.extras
        val refuse = ex.refusesRansom()
        if (refuse != null) simpleDialog("⚐ Белый флаг", refuse, listOf<Pair<String, () -> String?>>("Биться до конца" to { null }))
        else simpleDialog("⚐ Белый флаг", "Корабль едва держится. Можно предложить выкуп или спустить флаг и отдать груз — пираты уйдут.", listOf<Pair<String, () -> String?>>(
            "Откупиться: ${ex.ransomPrice()} монет" to { ex.payOff(false); null },
            "Сдаться: отдать весь груз и 40% монет" to { ex.payOff(true); null },
            "Продолжить бой" to { null },
        ))
    }

    fun showMutiny() = open(SeaGame.State.DIALOG) {
        val ex = g.extras
        simpleDialog("Ропот на борту", "Команда собралась у грот-мачты. Матросы недовольны: мало добычи и много опасностей. Нужно действовать, пока ропот не перерос в бунт.", listOf<Pair<String, () -> String?>>(
            "Выкатить бочонок рома (100 монет)" to { ex.resolveMutiny(0) },
            "Раздать долю (10% казны)" to { ex.resolveMutiny(1) },
            ("Усмирить силой" + if (g.living.crew[0] >= 0) " (поможет боцман)" else "") to { ex.resolveMutiny(2) },
        ))
    }

    fun showSurrender(s: Ship) = open(SeaGame.State.DIALOG) {
        val ex = g.extras
        val canTake = g.allies.size < MAX_ALLIES && s.type in listOf("sloop", "brig", "frigate")
        simpleDialog("⚐ Пираты сдаются", "${s.name} спустил флаг. Капитан пиратов ждёт вашего решения.", listOf<Pair<String, () -> String?>>(
            "Отпустить (уважение пиратов)" to { ex.acceptSurrender(s, 0) },
            "Забрать выкуп" to { ex.acceptSurrender(s, 1) },
            (if (canTake) "Забрать корабль во флот" else "Забрать корабль (во флоте нет места — выкуп)") to { ex.acceptSurrender(s, 2) },
        ))
    }

    // ===================== Редактор флага и парусов =====================
    private var editPix = CharArray(FLAG_W * FLAG_H) { '.' }
    private var brush = 1

    private inner class FlagGrid : Actor() {
        init {
            addListener(object : com.badlogic.gdx.scenes.scene2d.InputListener() {
                override fun touchDown(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean { paint(x, y); return true }
                override fun touchDragged(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float, pointer: Int) { paint(x, y) }
            })
        }
        fun paint(x: Float, y: Float) {
            val cx = (x / width * FLAG_W).toInt(); val cy = ((height - y) / height * FLAG_H).toInt()
            if (cx in 0 until FLAG_W && cy in 0 until FLAG_H) editPix[cy * FLAG_W + cx] = flagPixChar(brush)
        }
        override fun draw(batch: Batch, parentAlpha: Float) {
            val base = g.playerLook().flag
            val cw = width / FLAG_W; val ch = height / FLAG_H
            ui.rect(batch, x - 3, y - 3, width + 6, height + 6, rgb(0x3e2723))
            for (fy in 0 until FLAG_H) for (fx in 0 until FLAG_W) {
                val c = flagPixColor(editPix[fy * FLAG_W + fx]).let { if (it < 0) base else it }
                ui.rect(batch, x + fx * cw + 1, y + height - (fy + 1) * ch + 1, cw - 2, ch - 2, rgb(c))
            }
            batch.color = Color.WHITE
        }
    }

    private fun buildFlagEditor() {
        stage.clear()
        if (g.flagPix.length == FLAG_W * FLAG_H) editPix = g.flagPix.toCharArray() else if (editPix.all { it == '.' }) editPix = CharArray(FLAG_W * FLAG_H) { '.' }
        val root = overlay()
        val panel = panelTable()
        panel.add(ui.label("✎ Свой флаг", ui.big, align = Align.center)).growX().row()
        panel.add(ui.label("Проведите пальцем по клеткам. Ластик — цвет основы флага (его меняют во вкладке «Вид»).", ui.small, rgb(0xb9d6f2), wrap = true, align = Align.center)).growX().padBottom(6f).row()
        val gw = min(g.wDp - 60f, 320f)
        panel.add(FlagGrid()).size(gw, gw * FLAG_H / FLAG_W).padBottom(8f).row()
        val pal = Table()
        for (i in -1 until FLAG_PALETTE.size) {
            val sw = if (i < 0) ui.button("✕", 0x37474f, 0x263238, ui.small) else ui.button(" ", FLAG_PALETTE[i], FLAG_PALETTE[i], ui.small)
            if (i == brush) sw.label.setText(if (i < 0) "✕" else "✓")
            sw.onClick { brush = i; buildFlagEditor0() }
            pal.add(sw).size(min(36f, (g.wDp - 60f) / 7f), 34f).pad(2f)
            if (i == 5) pal.row()
        }
        panel.add(pal).padBottom(6f).row()
        val tpl = Table()
        for ((k, t) in FLAG_TEMPLATES.withIndex()) {
            tpl.add(ui.button(t.first, 0x164266, 0x123a5c, ui.small).also { it.onClick { editPix = t.second.joinToString("").toCharArray(); buildFlagEditor0() } }).growX().height(34f).pad(2f)
            if (k == 2) tpl.row()
        }
        panel.add(tpl).growX().padBottom(6f).row()
        val row = Table()
        row.add(ui.gray("Очистить").also { it.onClick { editPix = CharArray(FLAG_W * FLAG_H) { '.' }; buildFlagEditor0() } }).growX().height(46f).padRight(6f)
        row.add(ui.green("Поднять флаг").also {
            it.onClick {
                g.flagPix = if (editPix.all { c -> c == '.' }) "" else String(editPix)
                g.rebuildPlayer(); g.save(); g.toast("Новый флаг поднят!", 1.5f)
                portTab = 6; g.portView.select(6); current = { buildPort() }; buildPort()
            }
        }).growX().height(46f)
        panel.add(row).growX().row()
        panel.add(ui.blue("Назад").also { it.onClick { current = { buildPort() }; buildPort() } }).growX().height(42f).padTop(6f).row()
        root.add(panel).width(min(g.wDp - 16, 460f))
    }

    /** Перерисовать редактор, сохранив текущие клетки. */
    private fun buildFlagEditor0() { val keep = editPix; val fp = g.flagPix; g.flagPix = String(keep); buildFlagEditor(); g.flagPix = fp; editPix = keep }

    private var editPat = 0
    private var editSail2 = -1

    private inner class SailPreview : Actor() {
        override fun draw(batch: Batch, parentAlpha: Float) {
            val look = g.playerLook()
            val c1 = look.sail
            val second = if (editSail2 >= 0) FLAG_PALETTE[editSail2] else look.flag
            val nx = 24; val ny = 16
            val cw = width / nx; val ch = height / ny
            ui.rect(batch, x + width / 2 - 2, y - 10, 4f, height + 20, rgb(0x5d4037))
            for (j in 0 until ny) for (i in 0 until nx) {
                val u = (i + 0.5f) / nx; val v = (j + 0.5f) / ny
                val bow = kotlin.math.sin(u * Math.PI).toFloat() * 6f
                ui.rect(batch, x + i * cw, y + j * ch - bow, cw + 0.5f, ch + 0.5f, rgb(sailPatColor(editPat, u, v, c1, second, g.flagPix)))
            }
            ui.rect(batch, x - 6, y + height - 2, width + 12, 5f, rgb(0x5d4037))
            batch.color = Color.WHITE
        }
    }

    private fun buildSailEditor() {
        stage.clear()
        val root = overlay()
        val panel = panelTable()
        panel.add(ui.label("✎ Узор парусов", ui.big, align = Align.center)).growX().row()
        val pw = min(g.wDp - 80f, 260f)
        panel.add(SailPreview()).size(pw, pw * 0.7f).pad(10f, 0f, 14f, 0f).row()
        val pats = Table()
        for ((i, name) in SAIL_PATTERNS.withIndex()) {
            val b = if (i == editPat) ui.button(name, 0x2f8ff0, 0x1b6fd1, ui.small) else ui.button(name, 0x164266, 0x123a5c, ui.small)
            b.onClick { editPat = i; buildSailEditor() }
            pats.add(b).growX().uniformX().minWidth(0f).height(34f).pad(2f)
            if (i % 3 == 2) pats.row()
        }
        panel.add(pats).growX().padBottom(6f).row()
        panel.add(ui.label("Второй цвет узора (✕ — цвет флага):", ui.small, rgb(0xb9d6f2))).left().row()
        val pal = Table()
        for (i in -1 until FLAG_PALETTE.size) {
            val sw = if (i < 0) ui.button(if (editSail2 < 0) "✓" else "✕", 0x37474f, 0x263238, ui.small) else ui.button(if (i == editSail2) "✓" else " ", FLAG_PALETTE[i], FLAG_PALETTE[i], ui.small)
            sw.onClick { editSail2 = i; buildSailEditor() }
            pal.add(sw).size(min(36f, (g.wDp - 60f) / 7f), 34f).pad(2f)
            if (i == 5) pal.row()
        }
        panel.add(pal).padBottom(8f).row()
        panel.add(ui.green("Поставить паруса").also {
            it.onClick {
                g.sailPat = editPat; g.sail2Idx = editSail2
                g.rebuildPlayer(); g.save(); g.toast("Новые паруса подняты!", 1.5f)
                portTab = 6; g.portView.select(6); current = { buildPort() }; buildPort()
            }
        }).growX().height(48f).row()
        panel.add(ui.blue("Назад").also { it.onClick { current = { buildPort() }; buildPort() } }).growX().height(42f).padTop(6f).row()
        root.add(panel).width(min(g.wDp - 16, 460f))
    }

    // ===================== Фракции =====================
    fun showGuildSelect(change: Boolean = false) = open(SeaGame.State.DIALOG) { buildGuildSelect(change) }

    private fun buildGuildSelect(change: Boolean) {
        stage.clear()
        val gs = g.guilds
        val root = overlay()
        val panel = panelTable()
        panel.add(ui.label(if (change) "Сменить фракцию" else "Выберите фракцию", ui.big, align = Align.center)).growX().row()
        panel.add(ui.label(if (change) "Смена стоит 2000 монет. Прогресс по всем путям сохраняется, меняются только бонусы ранга."
            else "Фракция даёт бонусы за ранги. Ранг растёт от ваших дел; на 3-м ранге каждой фракции открывается свой капитан — прогресс по всем путям идёт всегда.",
            ui.small, rgb(0xb9d6f2), wrap = true, align = Align.center)).growX().padBottom(8f).row()
        val content = Table().top()
        for ((i, gd) in GUILDS.withIndex()) {
            val c = card(if (gs.guild == i) 0x1d5a46 else 0x164266)
            val top = Table()
            top.add(ui.label(gd.emblem, ui.huge, rgb(gd.color))).width(48f).padRight(8f)
            val t = Table()
            t.add(ui.label(gd.name, ui.big)).left().row()
            t.add(ui.label(gd.desc, ui.small, rgb(0xb9d6f2), wrap = true)).growX().row()
            top.add(t).growX()
            c.add(top).growX().row()
            val hero = HEROES.first { it.id == gd.hero }
            val how = when (gd.metric) { "contracts" -> "выполнить ${gd.thresholds[gd.heroRank - 1]} контрактов гильдии"; "explored" -> "открыть ${gd.thresholds[gd.heroRank - 1]}% карты"; else -> "потопить ${gd.thresholds[gd.heroRank - 1]} кораблей" }
            c.add(ui.label("Капитан ${hero.name}: $how", ui.small, rgb(0xffc93c), wrap = true)).growX().padTop(4f).row()
            c.add(ui.label("Бонус фракции — ${gd.perRank}", ui.small, rgb(0x3ddc84), wrap = true)).growX().row()
            val b = if (gs.guild == i) ui.gray("✓ Ваша фракция").also { it.isDisabled = true }
                else ui.green(if (change) "Перейти (2000)" else "Вступить").wrapping().also {
                    it.onClick {
                        gs.join(i, !change)
                        if (!change) { close(); g.checkDaily() } else { close() }
                    }
                }
            c.add(b).growX().height(48f).padTop(8f).row()
            content.add(c).growX().padBottom(10f).row()
        }
        val scroll = ScrollPane(content, ui.scrollStyle).also { it.setScrollingDisabled(true, false); it.setOverscroll(false, true) }
        panel.add(scroll).grow().row()
        if (change) panel.add(ui.blue("Назад").also { it.onClick { close() } }).growX().height(46f).padTop(8f).row()
        root.add(panel).width(min(g.wDp - 16, 480f)).height(g.hDp - g.safeTop - g.safeBottom - 24)
    }

    private fun guildCard(content: Table) {
        val gs = g.guilds
        val c = card(0x1d3b2e)
        c.add(ui.label(if (gs.guild >= 0) "${GUILDS[gs.guild].emblem} ${GUILDS[gs.guild].name} · ${gs.rankTitle(gs.guild)}" else "Фракция не выбрана", ui.mid, wrap = true)).growX().row()
        for ((i, gd) in GUILDS.withIndex()) {
            val r = Table()
            val next = gs.nextThreshold(i)
            val v = gs.metric(gd)
            r.add(ui.label(gd.emblem, ui.mid, rgb(gd.color))).width(26f)
            val t = Table()
            t.add(ui.label("${gd.name}: ${gs.rankTitle(i)}", ui.small, rgb(0xe3f2fd))).left().row()
            val unit = when (gd.metric) { "contracts" -> "контр."; "explored" -> "% карты"; else -> "потоплено" }
            t.add(ui.label(if (next != null) "$v / $next $unit" else "$v $unit · высший ранг", ui.small, rgb(0x90a4ae))).left().row()
            r.add(t).growX()
            val h = HEROES.first { it.id == gd.hero }
            r.add(ui.label(if (gs.heroUnlocked(h.id)) "${h.name} ✓" else "${h.name} 🔒".replace("🔒", "—"), ui.small, rgb(if (gs.heroUnlocked(h.id)) 0x3ddc84 else 0x90a4ae))).width(80f).right()
            c.add(r).growX().padTop(4f).row()
            if (next != null) c.add(Bar(ui, v.toFloat() / next, gd.color)).growX().height(8f).padTop(2f).row()
        }
        c.add(ui.blue(if (gs.guild >= 0) "Сменить фракцию" else "Выбрать фракцию").also { it.onClick { showGuildSelect(gs.guild >= 0) } }).growX().height(44f).padTop(8f).row()
        content.add(c).growX().padBottom(10f).row()
    }

    private fun contractCard(content: Table, port: Int) {
        val gs = g.guilds
        val c = card(0x2b1d12)
        c.add(ui.label("⚖ Контракт торговой гильдии", ui.mid, rgb(0xffe082))).left().row()
        val active = gs.contract
        if (active != null) {
            val left = active.timeLeft.toInt()
            c.add(ui.label("Доставить ${active.amount} × ${GOODS[active.good].name} в «${PORTS[active.port].name}». Доставлено ${active.delivered}. Осталось ${left / 60}:${"%02d".format(left % 60)}. Награда ${fmt(active.reward)}.",
                ui.small, rgb(0xfff3e0), wrap = true)).growX().row()
            if (active.port == port) c.add(ui.label("Это порт назначения — продайте товар на рынке.", ui.small, rgb(0x3ddc84), wrap = true)).growX().padTop(2f).row()
        } else {
            val o = gs.offerFor(port)
            c.add(ui.label("Доставить ${o.amount} × ${GOODS[o.good].name} в «${PORTS[o.port].name}» за ${(o.timeLeft / 60).toInt()} мин. Награда: ${fmt(o.reward)} монет и ранг гильдии.",
                ui.small, rgb(0xfff3e0), wrap = true)).growX().row()
            c.add(ui.green("Взять контракт").also { it.onClick { gs.accept(o); refreshAny() } }).growX().height(44f).padTop(6f).row()
        }
        c.add(ui.label("Выполнено контрактов: ${gs.contractsDone} · ранг: ${gs.rankTitle(0)}", ui.small, rgb(0x90a4ae))).left().padTop(4f).row()
        content.add(c).growX().padBottom(10f).row()
    }

    // ===================== Бортовой журнал =====================
    fun showJournal(q: Quest) = open(SeaGame.State.DIALOG) { buildJournal(q) }

    private fun buildJournal(q: Quest) {
        stage.clear()
        val root = overlay()
        val panel = Table().also { it.background = ui.panel(0x3e2a1a); it.pad(18f) }
        panel.add(ui.label(q.chapter ?: "Бортовой журнал", ui.big, rgb(0xffe0b2), wrap = true, align = Align.center)).growX().row()
        panel.add(ui.label(q.intro ?: "", ui.mid, rgb(0xfff3e0), wrap = true)).growX().pad(12f, 0f, 12f, 0f).row()
        panel.add(ui.label("Задание: ${q.text}", ui.small, rgb(0xffc93c), wrap = true)).growX().padBottom(10f).row()
        val btn = ui.green("⚓  Отдать швартовы")
        btn.onClick { close() }
        panel.add(btn).growX().height(52f).row()
        root.add(panel).width(min(g.wDp - 24, 440f))
    }

    // ===================== Абордаж =====================
    private class BoardBar(val ui: Ui, val zone: Float) : Actor() {
        var pos = 0f
        var dir = 1f
        var speed = 0.9f
        var frozen = false
        override fun act(delta: Float) {
            super.act(delta)
            if (frozen) return
            pos += dir * speed * delta
            if (pos > 1f) { pos = 1f; dir = -1f }
            if (pos < 0f) { pos = 0f; dir = 1f }
        }
        override fun draw(batch: Batch, parentAlpha: Float) {
            ui.round(batch, x, y, width, height, rgb(0x0b2238))
            ui.round(batch, x + width * (0.5f - zone / 2), y + 3, width * zone, height - 6, rgb(0x2e7d32))
            ui.round(batch, x + width * pos - 5, y - 6, 10f, height + 12, rgb(0xffc93c))
            batch.color = Color.WHITE
        }
    }

    fun showBoarding(target: Ship) = open(SeaGame.State.DIALOG) { buildBoarding(target) }

    private val tactics = listOf("Натиск", "Залп мушкетов", "Оборона")
    private val tacticHints = listOf("Пираты строятся для рукопашной…", "Пираты заряжают мушкеты…", "Пираты прячутся за бочками…")

    /**
     * Абордажная схватка: 3 раунда, в каждом выбираете тактику.
     * Натиск бьёт оборону, оборона гасит натиск? — нет: натиск сильнее залпа, залп сильнее обороны, оборона сильнее натиска.
     * Перед раундом видно, что затевают пираты (не всегда верно).
     */
    private fun buildBoarding(target: Ship) {
        val ratio = g.systems.boardingRatio(target)
        val enemyStart = when (target.type) { "brig" -> 24f; "frigate", "navy" -> 30f; "galleon", "flagship", "ghost" -> 40f; else -> 18f }
        val mineStart = (enemyStart * ratio).coerceIn(5f, 80f)
        var mine = mineStart; var theirs = enemyStart
        var round = 0
        var log = "Крюки впились в борт — команда готова к броску!"
        var enemyNext = kotlin.random.Random.nextInt(3)
        var hint = if (kotlin.random.Random.nextFloat() < 0.75f) enemyNext else kotlin.random.Random.nextInt(3)
        var volleyBonus = 1f
        fun build() {
            stage.clear()
            val root = overlay()
            val panel = panelTable()
            panel.add(ui.label("⚔ Абордаж", ui.big, align = Align.center)).growX().row()
            panel.add(ui.label(target.name, ui.mid, rgb(0xffcdd2), align = Align.center)).growX().padBottom(6f).row()
            fun crewRow(name: String, v: Float, max: Float, col: Int) {
                val r = Table()
                r.add(ui.label(name, ui.small, rgb(0xcfe3f7))).width(92f).left()
                r.add(Bar(ui, v / max, col)).growX().height(16f).padRight(6f)
                r.add(ui.label("${v.roundToInt()}", ui.small, align = Align.right)).width(36f).right()
                panel.add(r).growX().padTop(2f).row()
            }
            crewRow("Ваши бойцы", mine, max(mineStart, enemyStart), 0x3ddc84)
            crewRow("Пираты", theirs, max(mineStart, enemyStart), 0xff5a5f)
            panel.add(ui.label("Раунд ${min(round + 1, 3)} из 3 · дух команды: ${g.extras.moraleLabel()}", ui.small, rgb(0xffc93c), align = Align.center)).growX().padTop(6f).row()
            panel.add(ui.label(log, ui.small, rgb(0xfff3e0), wrap = true, align = Align.center)).growX().pad(6f, 0f, 6f, 0f).row()
            panel.add(ui.label(tacticHints[hint], ui.mid, rgb(0xffab91), wrap = true, align = Align.center)).growX().padBottom(8f).row()
            panel.add(ui.label("Натиск сильнее залпа · залп сильнее обороны · оборона сильнее натиска", ui.small, rgb(0x90a4ae), wrap = true, align = Align.center)).growX().padBottom(8f).row()
            val cols = intArrayOf(0xd32f2f, 0x6d4c41, 0x1565c0)
            for (t in 0..2) {
                val btn = ui.button(tactics[t], cols[t], cols[t], ui.mid)
                btn.onClick {
                    val e = enemyNext
                    // матрица: натиск > залп > оборона > натиск
                    val win = (t == 0 && e == 1) || (t == 1 && e == 2) || (t == 2 && e == 0)
                    val lose = (e == 0 && t == 1) || (e == 1 && t == 2) || (e == 2 && t == 0)
                    val r = kotlin.random.Random
                    var dealt = when (t) { 0 -> mine * r.nextFloat().let { 0.35f + it * 0.2f } * volleyBonus; 1 -> theirs * (0.18f + r.nextFloat() * 0.1f); else -> mine * (0.12f + r.nextFloat() * 0.08f) }
                    var taken = when (e) { 0 -> theirs * (0.3f + r.nextFloat() * 0.15f); 1 -> mine * (0.12f + r.nextFloat() * 0.08f); else -> theirs * (0.08f + r.nextFloat() * 0.06f) }
                    if (t == 2) taken *= 0.55f
                    if (win) dealt *= 1.6f
                    if (lose) taken *= 1.6f
                    volleyBonus = if (t == 1) 1.25f else 1f
                    theirs = max(0f, theirs - dealt); mine = max(0f, mine - taken)
                    g.sfx.play(if (win) "hit" else if (lose) "splash" else "cannon")
                    g.systems.vibrate(if (win) 60 else 25)
                    log = "Вы: ${tactics[t].lowercase()}, пираты: ${tactics[e].lowercase()}. " +
                        (if (win) "Удачный ход! " else if (lose) "Пираты переиграли вас! " else "") +
                        "Врагов выбыло ${dealt.roundToInt()}, наших — ${taken.roundToInt()}."
                    round++
                    val over = round >= 3 || theirs <= 0.5f || mine <= 0.5f
                    if (!over) {
                        enemyNext = r.nextInt(3)
                        hint = if (r.nextFloat() < 0.75f) enemyNext else r.nextInt(3)
                    }
                    build()
                    if (over) {
                        // итог — после перерисовки (stage.clear() снял бы отложенное действие)
                        val won = theirs <= 0.5f || (mine > 0.5f && mine / mineStart > theirs / enemyStart)
                        current = { boardingResult(target, won) }
                        stage.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.delay(0.7f, com.badlogic.gdx.scenes.scene2d.actions.Actions.run { boardingResult(target, won) }))
                    }
                }
                if (round >= 3 || theirs <= 0.5f || mine <= 0.5f) btn.isDisabled = true
                panel.add(btn).growX().height(50f).padTop(4f).row()
            }
            root.add(panel).width(min(g.wDp - 24, 440f))
        }
        current = { build() }
        build()
    }

    private fun boardingResult(target: Ship, win: Boolean) {
        stage.clear()
        val root = overlay()
        val panel = panelTable()
        val p = g.player
        if (!win) {
            g.extras.addMorale(-8f)
            if (p != null) p.hp = max(1f, p.hp - p.maxHp * 0.25f)
            target.hp = min(target.maxHp, target.hp + target.maxHp * 0.15f)
            if (p != null) { target.vx += (target.x - p.x) * 0.5f; target.vz += (target.z - p.z) * 0.5f }
            panel.add(ui.label("Абордаж отбит!", ui.big, align = Align.center)).growX().row()
            panel.add(ui.label("Команда отступила с потерями. Корабль повреждён.", ui.small, rgb(0xb9d6f2), wrap = true, align = Align.center)).growX().pad(8f, 0f, 12f, 0f).row()
            val ok = ui.blue("Продолжить бой").also { it.onClick { close() } }
            panel.add(ok).growX().height(52f).row()
        } else {
            g.systems.addStat("boards")
            g.extras.addMorale(6f)
            g.guilds.addShipXp(50)
            g.systems.gainXp(70, target.x, target.z)
            g.questEvent(QType.BOARD, "", false)
            panel.add(ui.label("Корабль взят!", ui.big, align = Align.center)).growX().row()
            val mercId = when (target.type) { "sloop" -> "merc_sloop"; "brig" -> "merc_brig"; "frigate" -> "merc_frigate"; else -> null }
            val canCapture = mercId != null && g.allies.size < MAX_ALLIES
            panel.add(ui.label(if (canCapture) "Можно забрать корабль во флот или разграбить трюм." else "Разграбьте трюм: во флоте нет места или корабль слишком велик.",
                ui.small, rgb(0xb9d6f2), wrap = true, align = Align.center)).growX().pad(8f, 0f, 12f, 0f).row()
            if (canCapture) {
                val cap = ui.blue("Захватить во флот").also {
                    it.onClick {
                        g.ships.remove(target)
                        g.allies += mercId!!
                        g.spawnAllies()
                        g.toast("${target.name} теперь в вашем флоте!", 2f)
                        g.save(); close()
                    }
                }
                panel.add(cap).growX().height(52f).padBottom(8f).row()
            }
            val loot = ui.green("Разграбить").also {
                it.onClick {
                    val coins = (target.coinsMax * 1.5f * g.systems.luckMul()).roundToInt()
                    g.coins += coins
                    val gi = kotlin.random.Random.nextInt(GOODS.size)
                    val got = g.systems.addCargo(gi, kotlin.random.Random.nextInt(4, 12))
                    if (kotlin.random.Random.nextFloat() < 0.4f) g.gems += 1
                    target.sinkT = 0.001f
                    g.sfx.play("treasure")
                    g.toast("+$coins монет" + if (got > 0) "\n${GOODS[gi].name} ×$got" else "", 2f)
                    g.save(); close()
                }
            }
            panel.add(loot).growX().height(52f).row()
            val release = ui.blue("Отпустить команду").also {
                it.onClick {
                    g.ships.remove(target); g.living.addRep(2, 4); g.extras.addMorale(2f)
                    g.toast("Пираты отпущены. В тавернах заговорят о вашем благородстве.", 2.2f); g.save(); close()
                }
            }
            panel.add(release).growX().height(46f).padTop(8f).row()
            val sinkIt = ui.gray("Потопить корабль").also {
                it.onClick {
                    target.sinkT = 0.001f; g.living.addRep(1, 2); g.living.addRep(2, -2)
                    g.systems.gainXp(40, target.x, target.z); g.sfx.play("sink"); g.save(); close()
                }
            }
            panel.add(sinkIt).growX().height(46f).padTop(6f).row()
        }
        root.add(panel).width(min(g.wDp - 24, 420f))
    }

    // ===================== Капитан =====================
    private fun buildCaptain() {
        stage.clear()
        val sy = g.systems
        val root = overlay()
        val panel = panelTable()
        panel.add(ui.label("Капитан · уровень ${sy.level}", ui.big, align = Align.center)).growX().row()
        panel.add(Bar(ui, sy.xp / xpForLevel(sy.level).toFloat(), 0x81d4fa)).growX().height(16f).pad(6f, 0f, 2f, 0f).row()
        panel.add(ui.label("Опыт ${sy.xp} / ${xpForLevel(sy.level)} · очков навыков: ${sy.skillPts}", ui.small, rgb(0xb9d6f2), align = Align.center)).growX().padBottom(8f).row()
        val content = Table().top()
        g.hero()?.let { h ->
            val hc = card(0x1d3b2e)
            hc.add(Image(HeroArt.full(h)).also { it.setScaling(com.badlogic.gdx.utils.Scaling.fit) }).size(84f, 112f).padRight(10f)
            val t = Table()
            t.add(ui.label(h.name, ui.big)).left().row()
            t.add(ui.label(h.title, ui.small, rgb(0xffc93c))).left().row()
            for (pk in h.perks) t.add(ui.label("✓ $pk", ui.small, rgb(0x3ddc84), wrap = true)).growX().left().row()
            hc.add(t).growX().row()
            hc.add(ui.blue("Сменить капитана").also { it.onClick { current = { buildHeroSelect(false) }; buildHeroSelect(false) } }).colspan(2).growX().height(44f).padTop(6f).row()
            content.add(hc).growX().padBottom(10f).row()
        }
        if (g.hero() == null) {
            val hc = card(0x1d3b2e)
            hc.add(ui.label("Безымянный капитан", ui.big)).left().row()
            hc.add(ui.label("Капитан-герой ещё не выбран. Поднимайте ранги фракций — откроются Hope, Fin и Mihael.", ui.small, rgb(0xb9d6f2), wrap = true)).growX().row()
            hc.add(ui.blue("Капитаны").also { it.onClick { current = { buildHeroSelect(false) }; buildHeroSelect(false) } }).growX().height(44f).padTop(6f).row()
            content.add(hc).growX().padBottom(10f).row()
        }
        val ex = g.extras
        val ac = card(0x2b1d12)
        ac.add(ui.label("Артефакты · найдено ${ex.owned.size} из ${ARTIFACTS.size} · надето ${ex.equipped.size}/$ARTIFACT_SLOTS", ui.mid, rgb(0xffe082), wrap = true)).growX().row()
        for (a in ARTIFACTS) {
            val r = Table().also { it.background = ui.panel(if (a.id in ex.equipped) 0x1d5a46 else 0x3e2a1a); it.pad(8f) }
            val t = Table()
            if (a.id in ex.owned) {
                t.add(ui.label(a.name, ui.mid)).left().row()
                t.add(ui.label(a.desc, ui.small, rgb(0xd7ccc8), wrap = true)).growX().row()
                r.add(t).growX()
                val b = if (a.id in ex.equipped) ui.button("Снять", 0x6d4c41, 0x5d4037, ui.small) else ui.green("Надеть")
                b.onClick { if (ex.toggleEquip(a.id)) buildCaptain() }
                r.add(b).width(92f).height(40f).padLeft(6f)
            } else {
                t.add(ui.label("??? Неизвестный артефакт", ui.mid, rgb(0x90a4ae))).left().row()
                t.add(ui.label("Где искать: ${a.source}", ui.small, rgb(0xa1887f), wrap = true)).growX().row()
                r.add(t).growX()
            }
            ac.add(r).growX().padTop(5f).row()
        }
        content.add(ac).growX().padBottom(10f).row()
        for ((i, sk) in SKILLS.withIndex()) {
            val c = card()
            val info = Table()
            info.add(ui.label(sk.name, ui.mid)).left().row()
            info.add(Pips(ui, sy.skills[i])).width(130f).height(12f).left().pad(3f, 0f, 3f, 0f).row()
            info.add(ui.label(sk.desc, ui.small, rgb(0xb9d6f2), wrap = true)).growX().row()
            c.add(info).growX()
            val can = sy.skillPts > 0 && sy.skills[i] < MAX_SKILL
            val btn = if (can) ui.green("+") else ui.gray(if (sy.skills[i] >= MAX_SKILL) "МАКС" else "+")
            btn.isDisabled = !can
            btn.onClick { if (sy.buySkill(i)) { g.sfx.play("coin"); g.save(); buildCaptain() } }
            c.add(btn).width(72f).height(46f).padLeft(8f)
            content.add(c).growX().padBottom(8f).row()
        }
        // по ширине панели: длинные описания артефактов не раздвигают окно
        val cw = min(g.wDp - 16, 460f) - 34f
        for (cell in content.cells) cell.width(cw).minWidth(0f)
        val scroll = ScrollPane(content, ui.scrollStyle).also { it.setScrollingDisabled(true, false); it.setOverscroll(false, true) }
        panel.add(scroll).grow().row()
        val back = ui.blue("Назад").also { it.onClick { current = { buildPause() }; buildPause() } }
        panel.add(back).growX().height(50f).padTop(8f).row()
        root.add(panel).width(min(g.wDp - 16, 460f)).height(g.hDp - g.safeTop - g.safeBottom - 24)
    }

    // ===================== Выбор капитана =====================
    fun showHeroSelect(first: Boolean) = open(SeaGame.State.DIALOG) { buildHeroSelect(first) }

    private fun buildHeroSelect(first: Boolean) {
        stage.clear()
        val root = overlay()
        val panel = panelTable()
        panel.add(ui.label(if (first) "Выберите капитана" else "Сменить капитана", ui.big, align = Align.center)).growX().row()
        panel.add(ui.label("Капитаны открываются за ранги фракций: Hope — торговая гильдия, Fin — братство мореходов, Mihael — береговое братство.", ui.small, rgb(0xb9d6f2), wrap = true, align = Align.center)).growX().padBottom(8f).row()
        val content = Table().top()
        for (h in HEROES) {
            val isCur = h.id == g.heroId
            val c = card(if (isCur) 0x1d5a46 else 0x164266)
            val img = Image(HeroArt.full(h))
            img.setScaling(com.badlogic.gdx.utils.Scaling.fit)
            c.add(img).size(120f, 160f).padRight(10f).top()
            val t = Table()
            t.add(ui.label(h.name, ui.big)).left().row()
            t.add(ui.label(h.title.uppercase(), ui.small, rgb(0xffc93c))).left().padBottom(4f).row()
            for (pk in h.perks) t.add(ui.label("✓ $pk", ui.small, rgb(0x3ddc84), wrap = true)).growX().left().row()
            t.add(ui.label(h.story, ui.small, rgb(0xb9d6f2), wrap = true)).growX().padTop(4f).row()
            c.add(t).growX().top().row()
            val gd = GUILDS.firstOrNull { it.hero == h.id }
            val open = g.guilds.heroUnlocked(h.id)
            if (!open) img.color = Color(0.25f, 0.25f, 0.3f, 1f)
            val btn = if (!open && gd != null) ui.gray("Закрыт: ${gd.name}, ранг «${gd.ranks[gd.heroRank - 1]}»").wrapping().also { it.isDisabled = true }
                else if (isCur && !first) ui.gray("✓ Ваш капитан").also { it.isDisabled = true }
                else ui.green("Выбрать: ${h.name}").also {
                    it.onClick {
                        g.heroId = h.id
                        if (g.player != null) g.rebuildPlayer()
                        g.sfx.play("quest")
                        g.save()
                        if (first) { close(); g.checkDaily() }
                        else { g.toast("Ваш капитан — ${h.name}", 1.6f); current = { buildCaptain() }; buildCaptain() }
                    }
                }
            c.add(btn).colspan(2).growX().height(48f).padTop(8f).row()
            content.add(c).growX().padBottom(10f).row()
        }
        val scroll = ScrollPane(content, ui.scrollStyle).also { it.setScrollingDisabled(true, false); it.setOverscroll(false, true) }
        panel.add(scroll).grow().row()
        if (!first) panel.add(ui.blue("Назад").also { it.onClick { current = { buildCaptain() }; buildCaptain() } }).growX().height(48f).padTop(8f).row()
        root.add(panel).width(min(g.wDp - 16, 480f)).height(g.hDp - g.safeTop - g.safeBottom - 24)
    }

    // ===================== Достижения =====================
    private fun buildAchievements() {
        stage.clear()
        val sy = g.systems
        val root = overlay()
        val panel = panelTable()
        panel.add(ui.label("Достижения ${sy.unlocked.size}/${ACHIEVEMENTS.size}", ui.big, align = Align.center)).growX().row()
        val st = sy.stats
        panel.add(ui.label("Потоплено: ${st["kills"] ?: 0} · тараном: ${st["rams"] ?: 0} · абордажей: ${st["boards"] ?: 0}\nПройдено: ${st["distance"] ?: 0} м · прибыль торговли: ${st["profit"] ?: 0}",
            ui.small, rgb(0xb9d6f2), wrap = true, align = Align.center)).growX().pad(6f, 0f, 8f, 0f).row()
        val content = Table().top()
        for (a in ACHIEVEMENTS) {
            val done = a.id in sy.unlocked
            val c = card(if (done) 0x1d5a46 else 0x164266)
            c.add(ui.label((if (done) "✓ " else "") + a.name, ui.mid, if (done) rgb(0x3ddc84) else Color.WHITE)).left().row()
            c.add(ui.label(a.desc + "  ·  +${a.gems}", ui.small, rgb(0xb9d6f2))).left().row()
            val v = min(st[a.stat] ?: 0, a.goal)
            c.add(Bar(ui, v / a.goal.toFloat(), if (done) 0x3ddc84 else 0xffc93c)).growX().height(12f).padTop(4f).row()
            content.add(c).growX().padBottom(8f).row()
        }
        val scroll = ScrollPane(content, ui.scrollStyle).also { it.setScrollingDisabled(true, false); it.setOverscroll(false, true) }
        panel.add(scroll).grow().row()
        val back = ui.blue("Назад").also { it.onClick { current = { buildPause() }; buildPause() } }
        panel.add(back).growX().height(50f).padTop(8f).row()
        root.add(panel).width(min(g.wDp - 16, 460f)).height(g.hDp - g.safeTop - g.safeBottom - 24)
    }

    // ===================== Карта мира =====================
    /** Интерактивная карта: перетаскивание, масштаб, туман войны, выбор объектов и путевая точка. */
    private inner class WorldMap : Actor() {
        var zoom = 1f
        var cx = 0f
        var cz = 0f
        var selX = Float.NaN
        var selZ = Float.NaN
        var onSelect: ((String, Boolean) -> Unit)? = null

        init {
            g.player?.let { cx = it.x * 0.5f; cz = it.z * 0.5f }
            addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener() {
                var startZoom = 1f
                override fun pan(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float, deltaX: Float, deltaY: Float) {
                    val k = scaleK()
                    cx -= deltaX / k; cz += deltaY / k
                    clampView()
                }
                override fun touchDown(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float, pointer: Int, button: Int) { startZoom = zoom }
                override fun zoom(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, initialDistance: Float, distance: Float) {
                    zoom = (startZoom * distance / initialDistance).coerceIn(1f, 6f); clampView()
                }
                override fun tap(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float, count: Int, button: Int) {
                    if (count >= 2) { zoom = 1f; cx = 0f; cz = 0f; return }
                    select(x, y)
                }
            })
            addListener(object : com.badlogic.gdx.scenes.scene2d.InputListener() {
                override fun scrolled(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float, amountX: Float, amountY: Float): Boolean {
                    zoom = (zoom * (if (amountY > 0) 0.85f else 1.18f)).coerceIn(1f, 6f); clampView(); return true
                }
            })
        }

        fun scaleK() = min(width, height) / (WORLD * 2) * zoom
        private fun clampView() {
            val lim = WORLD * (1 - 1 / zoom)
            cx = cx.coerceIn(-lim, lim); cz = cz.coerceIn(-lim, lim)
        }
        fun sx(wx: Float) = x + width / 2 + (wx - cx) * scaleK()
        fun sy(wz: Float) = y + height / 2 - (wz - cz) * scaleK()

        private fun select(lx: Float, ly: Float) {
            val k = scaleK()
            val wx = cx + (lx - width / 2) / k
            val wz = cz - (ly - height / 2) / k
            val ex = g.systems.explored
            data class F(val x: Float, val z: Float, val text: String)
            val feats = ArrayList<F>()
            for (pt in g.ports) feats += F(pt.rx, pt.rz, "Порт «${pt.def.name}»: ремонт, верфь, рынок")
            for (f in g.systems.fortresses) if (ex.seen(f.x, f.z)) feats += F(f.x, f.z, if (f.alive) "Пиратская крепость: стреляет по всем, кто рядом" else "Руины крепости (отстроят позже)")
            for (w in g.systems.wrecks) if (ex.seen(w.x, w.z)) feats += F(w.x, w.z, "Затонувший корабль: остановитесь у пузырей")
            for (bt in g.systems.bottles) if (ex.seen(bt.x, bt.z)) feats += F(bt.x, bt.z, "Бутылка с песней для команды")
            g.treasure?.let { feats += F(it.x, it.z, "Сокровище на острове") }
            val near = feats.minByOrNull { hypot(it.x - wx, it.z - wz) }
            selX = wx; selZ = wz
            var text = "Точка на карте. Можно проложить курс."
            if (near != null && hypot(near.x - wx, near.z - wz) * k < 26) { selX = near.x; selZ = near.z; text = near.text }
            if (!ex.seen(selX, selZ) && near == null) text = "Неизведанные воды. Можно проложить курс."
            val reg = regionAt(selX, selZ).title
            onSelect?.invoke("$text\n$reg · ${hypot(selX - (g.player?.x ?: 0f), selZ - (g.player?.z ?: 0f)).roundToInt()} м от вас", true)
        }

        override fun draw(batch: Batch, parentAlpha: Float) {
            val ui = g.ui
            batch.flush()
            if (!clipBegin()) return
            ui.rect(batch, x, y, width, height, rgb(0xe8d5a8))
            val k = scaleK()
            val n = 36
            val cs = WORLD * 2 / n
            for (gx in 0 until n) for (gz in 0 until n) {
                val wx = -WORLD + (gx + 0.5f) * cs; val wz = -WORLD + (gz + 0.5f) * cs
                val px = sx(wx); val py = sy(wz)
                if (px < x - cs * k || px > x + width + cs * k || py < y - cs * k || py > y + height + cs * k) continue
                val col = when (regionAt(wx, wz)) { Region.ICE -> 0x8fb6cf; Region.FIRE -> 0x5f7280; Region.REEF -> 0x4aa39b; Region.ABYSS -> 0x23507e; else -> 0x4f9fd6 }
                ui.rect(batch, px - cs * k / 2, py - cs * k / 2, cs * k + 1, cs * k + 1, rgb(col))
            }
            val ex = g.systems.explored
            for (isl in g.islands) {
                if (!ex.seen(isl.x, isl.z) && isl.port < 0) continue
                val r = max(3f, isl.r * k * 2.1f)
                val reg = regionAt(isl.x, isl.z)
                ui.icon(batch, ui.disc, sx(isl.x), sy(isl.z), r, r, 0f, rgb(when (reg) { Region.ICE -> 0xf4fafd; Region.FIRE -> 0x4e4038; else -> 0xe6d3a3 }))
                ui.icon(batch, ui.disc, sx(isl.x), sy(isl.z), r * 0.72f, r * 0.72f, 0f, rgb(when (reg) { Region.ICE -> 0xdbe9f3; Region.FIRE -> 0x2b2523; else -> 0x6fa84a }))
            }
            // туман войны
            val fn = ex.n
            val fc = WORLD * 2 / fn
            for (j in 0 until fn) for (i in 0 until fn) {
                if (ex.bits[j * fn + i]) continue
                val wx = -WORLD + (i + 0.5f) * fc; val wz = -WORLD + (j + 0.5f) * fc
                val px = sx(wx); val py = sy(wz)
                val sz = fc * k * 2.3f
                if (px < x - sz || px > x + width + sz || py < y - sz || py > y + height + sz) continue
                ui.icon(batch, ui.softDisc, px, py, sz * 1.2f, sz * 1.2f, 0f, rgb(0x3b3127, 0.8f))
            }
            for (f in g.systems.fortresses) if (ex.seen(f.x, f.z)) ui.icon(batch, ui.squareIcon, sx(f.x), sy(f.z), 10f, 10f, 0f, rgb(if (f.alive) 0xd84315 else 0x757575))
            for (w in g.systems.wrecks) if (ex.seen(w.x, w.z)) ui.icon(batch, ui.disc, sx(w.x), sy(w.z), 8f, 8f, 0f, rgb(0xb3e5fc))
            for (bt in g.systems.bottles) if (ex.seen(bt.x, bt.z)) ui.text(batch, ui.mid, "♪", sx(bt.x) - 10, sy(bt.z) + 8, Align.center, 20f, c = rgb(0xffe082))
            g.treasure?.let { ui.icon(batch, ui.xmark, sx(it.x), sy(it.z), 14f, 14f) }
            g.extras.lost?.let { l -> ui.icon(batch, ui.coin, sx(l.x), sy(l.z), 20f, 20f); ui.text(batch, ui.small, "ваш груз", sx(l.x) - 40, sy(l.z) - 12, Align.center, 80f, c = rgb(0xffe082)) }
            val lv = g.living
            ui.text(batch, ui.mid, "⚑", sx(lv.baseX) - 12, sy(lv.baseZ) + 9, Align.center, 24f, c = rgb(if (lv.baseFounded) 0x2e7d32 else 0x5d4037))
            for (r in lv.ruins) if (ex.seen(r.x, r.z) && r.idx !in lv.dived) ui.text(batch, ui.small, "⌂", sx(r.x) - 10, sy(r.z) + 7, Align.center, 20f, c = rgb(0xffc93c))
            for (w in lv.whirls) if (ex.seen(w.x, w.z)) ui.icon(batch, ui.ring, sx(w.x), sy(w.z), 16f, 16f, 0f, rgb(0x0d47a1))
            if (lv.mapTarget >= 0) g.islands.getOrNull(lv.mapTarget)?.let { ui.icon(batch, ui.xmark, sx(it.x), sy(it.z), 20f, 20f, 0f, rgb(0xd32f2f)) }
            for (pt in g.ports) {
                ui.text(batch, ui.mid, "⚓", sx(pt.rx) - 12, sy(pt.rz) + 9, Align.center, 24f, c = rgb(0x3e2723))
                if (zoom > 1.6f || ex.seen(pt.rx, pt.rz)) ui.text(batch, ui.small, pt.def.name, sx(pt.rx) - 80, sy(pt.rz) - 8, Align.center, 160f, c = rgb(0x3e2723))
            }
            g.questTarget()?.let {
                val pulse = 1f + sin(g.now * 5) * 0.2f
                ui.icon(batch, ui.disc, sx(it.first), sy(it.second), 14f * pulse, 14f * pulse, 0f, rgb(0xffc93c))
            }
            g.systems.waypoint?.let { ui.icon(batch, ui.disc, sx(it[0]), sy(it[1]), 12f, 12f, 0f, rgb(0x00acc1)) }
            if (!selX.isNaN()) ui.icon(batch, ui.ring, sx(selX), sy(selZ), 30f, 30f, 0f, rgb(0xd32f2f))
            g.player?.let { p -> ui.icon(batch, ui.arrowIcon, sx(p.x), sy(p.z), 16f, 20f, -p.h * MathUtils.radiansToDegrees, rgb(0xffffff)) }
            // роза ветров
            val rx = x + width - 34; val ry = y + 40
            ui.icon(batch, ui.disc, rx, ry, 50f, 50f, 0f, rgb(0x3e2723, 0.6f))
            ui.text(batch, ui.small, "С", rx - 10, ry + 22, Align.center, 20f, c = rgb(0xffcdd2))
            ui.text(batch, ui.small, "Ю", rx - 10, ry - 12, Align.center, 20f)
            ui.text(batch, ui.small, "З", rx - 30, ry + 5, Align.center, 20f)
            ui.text(batch, ui.small, "В", rx + 10, ry + 5, Align.center, 20f)
            ui.text(batch, ui.small, "Исследовано ${ex.percent()}%", x + 6, y + height - 6, Align.left, 200f, c = rgb(0x3e2723))
            batch.flush()
            clipEnd()
            ui.icon(batch, ui.white, x, y, 0f, 0f)
            batch.color = Color.WHITE
        }
    }

    private var mapFromPause = false
    fun showMap() { mapFromPause = false; open(SeaGame.State.DIALOG) { buildMap() } }

    private fun buildMap() {
        stage.clear()
        val root = overlay()
        val panel = panelTable()
        panel.pad(10f)
        panel.add(ui.label("Карта мира", ui.big, align = Align.center)).growX().row()
        panel.add(ui.label("Тяните — двигать, щипок или колесо — масштаб, касание — выбрать место", ui.small, rgb(0xb9d6f2), wrap = true, align = Align.center)).growX().padBottom(6f).row()
        val landscape = g.wDp > g.hDp
        val side = if (landscape) min(g.hDp - g.safeTop - g.safeBottom - 120, g.wDp - 300) else min(g.wDp - 40, g.hDp - g.safeTop - g.safeBottom - 300)
        val map = WorldMap()
        val info = ui.label("Выберите место на карте", ui.small, wrap = true)
        val route = ui.green("Проложить курс сюда")
        route.isDisabled = true
        map.onSelect = { text, can -> info.setText(text); route.isDisabled = !can }
        route.onClick {
            if (!map.selX.isNaN()) {
                g.systems.waypoint = floatArrayOf(map.selX, map.selZ)
                g.toast("Курс проложен", 1.2f); g.save(); close()
            }
        }
        val clear = ui.gray("Убрать отметку").also { it.onClick { g.systems.waypoint = null; g.save(); info.setText("Отметка убрана") } }
        val back = ui.blue("Назад").also { it.onClick { if (mapFromPause) { current = { buildPause() }; buildPause() } else close() } }
        val legend = Table()
        fun leg(icon: com.badlogic.gdx.graphics.g2d.TextureRegion?, glyph: String?, col: Int, text: String) {
            if (icon != null) legend.add(Image(icon).also { it.color = rgb(col) }).size(12f).padRight(3f)
            else legend.add(ui.label(glyph ?: "", ui.small, rgb(col))).padRight(3f)
            legend.add(ui.label(text, ui.small, rgb(0xcfd8dc))).padRight(9f)
        }
        leg(null, "⚓", 0xffffff, "порт"); leg(ui.squareIcon, null, 0xd84315, "крепость"); leg(ui.disc, null, 0xffc93c, "цель")
        legend.row()
        leg(ui.disc, null, 0x00acc1, "курс"); leg(ui.disc, null, 0xb3e5fc, "клад под водой"); leg(null, "♪", 0xffe082, "песня")
        val side2 = Table()
        side2.add(info).growX().padBottom(6f).row()
        side2.add(route).growX().height(44f).padBottom(4f).row()
        side2.add(clear).growX().height(40f).padBottom(4f).row()
        side2.add(back).growX().height(44f).padBottom(6f).row()
        side2.add(legend).left().row()
        if (landscape) {
            val row = Table()
            row.add(map).size(side).padRight(10f)
            row.add(side2).width(260f).top()
            panel.add(row).row()
        } else {
            panel.add(map).size(side).padBottom(8f).row()
            panel.add(side2).growX().row()
        }
        root.add(panel)
    }

    // ===================== Песни и справка =====================
    private fun buildSongs() {
        stage.clear()
        val sy = g.systems
        val root = overlay()
        val panel = panelTable()
        panel.add(ui.label("Песни команды", ui.big, align = Align.center)).growX().row()
        panel.add(ui.label("Пока корабль идёт по морю без боя, матросы поют. Новые песни лежат в бутылках в открытом море (значок ♪ на карте).",
            ui.small, rgb(0xb9d6f2), wrap = true)).growX().pad(6f, 0f, 8f, 0f).row()
        val list = Table()
        for ((i, sh) in SHANTIES.withIndex()) {
            val c = card(if (i == sy.shantyCur && i in sy.shanties) 0x1d5a46 else 0x164266)
            if (i in sy.shanties) {
                val info = Table()
                info.add(ui.label("♪ " + sh.title, ui.mid)).left().row()
                info.add(ui.label(sh.desc, ui.small, rgb(0xb9d6f2), wrap = true)).growX().row()
                c.add(info).growX()
                val pick = if (i == sy.shantyCur) ui.gray("Поют").also { it.isDisabled = true } else ui.blue("Петь").also { it.onClick { sy.shantyCur = i; g.save(); buildSongs() } }
                c.add(pick).width(100f).height(44f)
            } else {
                c.add(ui.label("??? — найдите бутылку с этой песней", ui.small, rgb(0x90a4ae))).growX().left()
            }
            list.add(c).growX().padBottom(8f).row()
        }
        val toggle = ui.blue(if (sy.shantiesOn) "Пение команды: вкл" else "Пение команды: выкл").also { it.onClick { sy.shantiesOn = !sy.shantiesOn; g.save(); buildSongs() } }
        list.add(toggle).growX().height(46f).padTop(4f).row()
        list.add(ui.blue("Назад").also { it.onClick { current = { buildPause() }; buildPause() } }).growX().height(46f).padTop(6f).row()
        fitPanel(root, panel, list, 440f)
    }

    private fun buildHelp() {
        stage.clear()
        val root = overlay()
        val panel = panelTable()
        panel.add(ui.label("Как играть", ui.big, align = Align.center)).growX().padBottom(6f).row()
        val content = Table().top()
        val sections = listOf(
            "Управление" to "Коснитесь экрана в любом месте и тяните палец: корабль поплывёт туда. Кнопка с парусами слева вверху — рывок. По ветру (стрелка у мини-карты) корабль идёт быстрее.",
            "Бой" to "Пушки стреляют сами, когда враг сбоку от корабля. Кнопка с прицелом: зажмите и тяните к цели, отпустите — прицельный залп (сильнее на 25%). Кнопки справа — особые ядра, ремонт и купленное оружие.",
            "Абордаж" to "1) Повредите пирата до половины здоровья: над ним появятся красные скрещённые сабли.\n2) Подплывите ближе 30 м — внизу загорится кнопка «Абордаж!».\n3) Нажмите её: команда бросит крюки и стянет корабли.\n4) В схватке жмите «Удар!», когда жёлтый маркер в зелёной зоне. 2 удачных удара из 3 — победа: корабль можно забрать во флот или разграбить.",
            "Карта" to "Нажмите на мини-карту, чтобы открыть карту мира. Тёмные облака — места, где вы ещё не были. Коснитесь места и выберите «Проложить курс» — на экране появится голубая стрелка.",
            "Порты" to "Заплывите в белый круг у причала. В порту: ремонт, новые корабли, улучшения, арсенал, рынок, наёмники и покраска.",
            "Торговля" to "На рынке покупайте товар там, где он «дёшево», и продавайте там, где «дорого». Трюм зависит от корабля.",
            "Песни" to "Пока вы плывёте без боя, команда поёт шанти. Новые песни — в бутылках в море (♪ на карте).",
            "Таверна и офицеры" to "В таверне порта нанимайте офицеров: боцман, канонир, штурман и квартирмейстер дают бонусы и комментируют события. Там же доска «Разыскиваются» — охота за капитанами с наградой.",
            "Репутация" to "Торговцы, флот и пираты помнят ваши дела. Торговля поднимает доверие купцов (цены лучше). Охота на пиратов радует флот, но злит пиратов — за вашей головой придут охотники. Уважение пиратов: мелкие шайки не нападут первыми.",
            "Своя база" to "Остров Надежды рядом с Гаванью Ветров (⚑ на карте). Заплывите в круг у острова, основайте базу и стройте: плантация и таверна приносят доход, склад копит деньги и расширяет трюм, батарея защищает, верфь удешевляет улучшения.",
            "Флот" to "Если есть наёмники, слева появляются кнопки приказов: ⚓ колонна (корабли идут точно по вашему следу и стреляют бортами на ходу), ⚔ атака (флот бьёт врагов в большом радиусе), ↺ отход (плотная колонна).",
            "Высадки и клады" to "Остановитесь у дикого острова — появится кнопка «Высадиться». На островах — тайники, засады и куски карты. Три куска дают карту сокровищ. У колонн затонувших храмов можно нырнуть.",
            "Абордаж" to "Ослабьте пирата до половины прочности, подойдите вдоль его борта с той же скоростью — появится кнопка «Абордаж!». В схватке 3 раунда: натиск сильнее залпа мушкетов, залп сильнее обороны, оборона сильнее натиска. Перед раундом видно, что затевают пираты (не всегда верно). После победы корабль можно взять во флот, разграбить, отпустить или потопить.",
            "Пиратская фракция" to "Игрок Берегового братства может нападать на любые суда: купцов, рыбаков и патрули флота. Автоогонь бьёт только пиратов — мирное судно атакуйте прицельным залпом, после первого попадания по нему стреляет и автоогонь. Купцы удирают, патрули отвечают огнём. Добыча богаче, но торговцы и флот это запомнят.",
            "Белый флаг" to "Если корабль почти потоплен, появится кнопка «Белый флаг»: откупитесь монетами или сдайтесь, отдав груз. Охотники за головами и призраки выкуп не берут. Почти потопленные пираты тоже иногда сдаются — подплывите и примите сдачу.",
            "Потерянный груз" to "После гибели груз и часть монет остаются на месте крушения (отметка на карте). Обломки стерегут падальщики. Если погибнете снова, не подобрав их, груз пропадёт.",
            "Дух команды" to "Победы, добыча, песни и порт поднимают дух, поражения и долгое плавание без добычи — опускают. Бодрая команда быстрее заряжает пушки и сильнее в абордаже. Ром в таверне поднимает дух. Если дух упадёт совсем низко — начнётся ропот.",
            "Артефакты" to "12 редких предметов: в затонувших храмах, кладах по карте, у капитанов с наградой и у морских чудовищ. Носить можно 3 — меню «Капитан».",
            "Свой флаг" to "Во вкладке «Вид» порта: нарисуйте флаг пальцем по клеткам и выберите узор парусов, например эмблему с флага.",
            "Погода" to "Туман прячет вас от пиратов, штиль замедляет, водовороты затягивают (уходите полным ходом), а в Ледяном море дрейфуют айсберги. Ночью бродят корабли-призраки.",
        )
        for ((t, d) in sections) {
            val c = card()
            c.add(ui.label(t, ui.mid, rgb(0xffc93c))).left().row()
            c.add(ui.label(d, ui.small, Color.WHITE, wrap = true)).growX().padTop(4f).row()
            content.add(c).growX().padBottom(8f).row()
        }
        val scroll = ScrollPane(content, ui.scrollStyle).also { it.setScrollingDisabled(true, false); it.setOverscroll(false, true) }
        panel.add(scroll).grow().row()
        panel.add(ui.blue("Назад").also { it.onClick { current = { buildPause() }; buildPause() } }).growX().height(48f).padTop(8f).row()
        root.add(panel).width(min(g.wDp - 16, 480f)).height(g.hDp - g.safeTop - g.safeBottom - 24)
    }

    // ===================== Сохранение =====================
    private fun buildSaveMenu() {
        stage.clear()
        val root = overlay()
        val panel = panelTable()
        panel.add(ui.label("Сохранение", ui.big, align = Align.center)).growX().row()
        panel.add(ui.label("Прогресс сохраняется автоматически, а Android делает резервную копию в аккаунт Google. Чтобы перенести игру на другой телефон, скопируйте код сохранения и вставьте его там.",
            ui.small, rgb(0xb9d6f2), wrap = true)).growX().pad(6f, 0f, 10f, 0f).row()
        val exp = ui.blue("Скопировать код сохранения").also {
            it.onClick { Gdx.app.clipboard.contents = g.exportSave(); g.toast("Код скопирован в буфер обмена", 1.8f) }
        }
        val imp = ui.green("Загрузить код из буфера").also {
            it.onClick {
                val ok = g.importSave(Gdx.app.clipboard.contents ?: "")
                g.toast(if (ok) "Сохранение загружено!" else "Неверный код сохранения", 1.8f)
                if (ok) close()
            }
        }
        val back = ui.gray("Назад").also { it.onClick { current = { buildSettings() }; buildSettings() } }
        for (b in listOf(exp, imp, back)) panel.add(b).growX().height(50f).padTop(8f).row()
        root.add(panel).width(min(g.wDp - 32, 420f))
    }
}
