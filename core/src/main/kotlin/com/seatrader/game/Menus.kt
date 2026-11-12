package com.seatrader.game

import com.badlogic.gdx.graphics.Color
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
        stage.clear()
        current = null
        g.state = SeaGame.State.PLAY
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

    private fun priceButton(text: String, gemsPrice: Int, can: Boolean): TextButton {
        val btn = if (can) ui.green(text) else ui.gray(text)
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
        portScroll = 0f
        open(SeaGame.State.PORT) { buildPort() }
    }

    private fun refreshPort() {
        portScroll = scrollRef?.scrollY ?: 0f
        buildPort()
    }

    private fun buildPort() {
        val prevScroll = portScroll
        stage.clear()
        val root = overlay()
        val panel = panelTable()
        panel.add(ui.label("⚓ " + PORTS[portIndex].name, ui.big, align = Align.center)).growX().row()
        panel.add(Table().also { t ->
            t.add(currencyRow())
            t.add(ui.label("  ·  корабль отремонтирован", ui.small, rgb(0xb9d6f2)))
        }).padTop(4f).padBottom(10f).row()

        val tabs = Table()
        listOf("Верфь", "Улучш.", "Флот", "Вид").forEachIndexed { i, name ->
            val b = if (portTab == i) ui.button(name, 0x2f8ff0, 0x1b6fd1, ui.small) else ui.button(name, 0x164266, 0x123a5c, ui.small)
            b.pad(8f, 4f, 8f, 4f)
            b.onClick { portTab = i; portScroll = 0f; buildPort() }
            tabs.add(b).growX().uniformX().height(42f).pad(0f, 3f, 0f, 3f)
        }
        panel.add(tabs).growX().padBottom(10f).row()

        val content = Table().top()
        when (portTab) {
            0 -> shipCards(content)
            1 -> upgradeCards(content)
            2 -> fleetCards(content)
            else -> paintCards(content)
        }
        val scroll = ScrollPane(content, ui.scrollStyle)
        scroll.setScrollingDisabled(true, false)
        scroll.setFadeScrollBars(true)
        scroll.setOverscroll(false, true)
        panel.add(scroll).grow().row()

        val sail = ui.blue("⚓  Выйти в море")
        sail.onClick { g.closePort() }
        panel.add(sail).growX().height(54f).padTop(10f).row()

        root.add(panel).width(min(g.wDp - 16, 520f)).height(g.hDp - g.safeTop - g.safeBottom - 24)
        stage.act(0f)
        root.layout(); panel.layout(); scroll.layout()
        scroll.scrollY = prevScroll
        scroll.updateVisualScroll()
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
        head.add(ui.label("Улучшения действуют только на этот корабль.", ui.small, rgb(0xb9d6f2), wrap = true)).growX().row()
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
            c.add(info).growX()
            val btn: TextButton = if (lvl >= MAX_LEVEL) ui.gray("МАКС").also { it.isDisabled = true } else {
                val cost = upgradeCost(def, lvl)
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
        val cost = upgradeCost(def, u[i])
        if (g.coins < cost) { g.toast("Не хватает монет"); return }
        g.coins -= cost
        u[i]++
        g.rebuildPlayer()
        g.sfx.play("coin")
        g.questEvent(QType.UPGRADE, "", false)
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
            c.add(ui.label("В строю: ${m.name}", ui.mid)).growX().left()
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
        val settings = ui.blue("⚙  Настройки").also { it.onClick { current = { buildSettings() }; buildSettings() } }
        val reset = ui.gray(if (resetArmed) "Нажмите ещё раз для сброса" else "↺  Начать заново").also {
            it.onClick {
                if (!resetArmed) { resetArmed = true; buildPause() } else { resetArmed = false; g.resetProgress() }
            }
        }
        for (bt in listOf(resume, settings, reset)) panel.add(bt).growX().height(54f).padTop(8f).row()
        root.add(panel).width(min(g.wDp - 32, 420f))
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
        val quality = ui.blue(if (g.highQuality) "Графика: высокая" else "Графика: экономная").also {
            it.onClick {
                g.highQuality = !g.highQuality
                g.fx.limit = if (g.highQuality) 900 else 450
                g.save(); buildSettings()
            }
        }
        val tut = ui.blue("Показать обучение снова").also {
            it.onClick { g.tutorial = 0; g.tutorialT = 0f; g.save(); g.toast("Обучение начнётся заново") }
        }
        val back = ui.green("Готово").also { it.onClick { g.save(); current = { buildPause() }; buildPause() } }
        for (bt in listOf(hand, quality, tut, back)) panel.add(bt).growX().height(50f).padTop(8f).row()
        root.add(panel).width(min(g.wDp - 32, 420f))
    }

    // ===================== Гибель =====================
    fun showDeath() = open(SeaGame.State.DEAD) { buildDeath() }

    private fun buildDeath() {
        stage.clear()
        val loss = (g.coins * 0.15f).toInt()
        val root = overlay()
        val panel = panelTable()
        panel.add(ui.label("☠ Корабль потоплен!", ui.big, align = Align.center)).growX().row()
        panel.add(ui.label("Вы потеряли $loss монет. Корабль отбуксируют в порт «${PORTS[g.homePort].name}».",
            ui.small, rgb(0xb9d6f2), wrap = true, align = Align.center)).growX().pad(8f, 0f, 12f, 0f).row()
        val btn = ui.green("⚓  Вернуться в порт")
        btn.onClick {
            g.coins -= loss
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
}
