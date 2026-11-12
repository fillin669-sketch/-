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
        listOf("Верфь", "Улучшения", "Арсенал", "Рынок", "Флот", "Вид").forEachIndexed { i, name ->
            val b = if (portTab == i) ui.button(name, 0x2f8ff0, 0x1b6fd1, ui.small) else ui.button(name, 0x164266, 0x123a5c, ui.small)
            b.pad(6f, 4f, 6f, 4f)
            b.onClick { portTab = i; portScroll = 0f; buildPort() }
            tabs.add(b).growX().uniformX().height(38f).pad(3f, 3f, 3f, 3f)
            if (i == 2) tabs.row()
        }
        panel.add(tabs).growX().padBottom(8f).row()

        val content = Table().top()
        when (portTab) {
            0 -> shipCards(content)
            1 -> upgradeCards(content)
            2 -> arsenalCards(content)
            3 -> marketCards(content)
            4 -> fleetCards(content)
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
        val captain = ui.blue("★  Капитан" + if (g.systems.skillPts > 0) " (+${g.systems.skillPts})" else "").also { it.onClick { current = { buildCaptain() }; buildCaptain() } }
        val ach = ui.blue("✓  Достижения").also { it.onClick { current = { buildAchievements() }; buildAchievements() } }
        val map = ui.blue("⚓  Карта мира").also { it.onClick { current = { buildMap() }; buildMap() } }
        val settings = ui.blue("⚙  Настройки").also { it.onClick { current = { buildSettings() }; buildSettings() } }
        val reset = ui.gray(if (resetArmed) "Нажмите ещё раз для сброса" else "↺  Начать заново").also {
            it.onClick {
                if (!resetArmed) { resetArmed = true; buildPause() } else { resetArmed = false; g.resetProgress() }
            }
        }
        val list = Table()
        for (bt in listOf(resume, captain, ach, map, settings, reset)) list.add(bt).growX().height(50f).padTop(7f).row()
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
        val quality = ui.blue(if (g.highQuality) "Графика: высокая" else "Графика: экономная").also {
            it.onClick {
                g.highQuality = !g.highQuality
                g.fx.limit = if (g.highQuality) 900 else 450
                g.save(); buildSettings()
            }
        }
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
        for (bt in listOf(hand, quality, auto, vib, saveBtn, tut, back)) list.add(bt).growX().height(44f).padTop(6f).row()
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
        head.add(ui.label("Рынок", ui.big)).left().row()
        head.add(ui.label("Трюм: ${sy.cargoUsed()} из ${sy.capacity()}. Покупайте там, где дёшево, и продавайте там, где дорого.",
            ui.small, rgb(0xb9d6f2), wrap = true)).growX().row()
        val q = g.quest()
        if (q.type == QType.DELIVER) head.add(ui.label("Заказ: ${q.text} (${g.qprog}/${q.n})", ui.small, rgb(0xffc93c), wrap = true)).growX().padTop(4f).row()
        content.add(head).growX().padBottom(10f).row()
        for ((gi, good) in GOODS.withIndex()) {
            val buy = sy.buyPrice(portIndex, gi)
            val sell = sy.sellPrice(portIndex, gi)
            val mult = PORT_PRICES[portIndex][gi]
            val tag = when { mult < 0.85f -> "дёшево" to 0x3ddc84; mult > 1.25f -> "дорого" to 0xff8a65; else -> "" to 0xffffff }
            val c = card()
            val top = Table()
            top.add(ui.label(good.name, ui.big)).left()
            if (tag.first.isNotEmpty()) top.add(ui.label("  ${tag.first}", ui.small, rgb(tag.second))).left()
            top.add().growX()
            top.add(ui.label("в трюме: ${sy.cargo[gi]}", ui.small, rgb(0xd7ccc8))).right()
            c.add(top).growX().row()
            val avg = if (sy.cargo[gi] > 0) " · куплено по ${(sy.cargoCost[gi] / sy.cargo[gi]).roundToInt()}" else ""
            c.add(ui.label("Купить: $buy · Продать: $sell$avg", ui.small, rgb(0xb9d6f2))).left().padTop(2f).row()
            val row = Table()
            val b1 = ui.button("+1", 0x2fbf6e, 0x22a95c, ui.small).also { it.onClick { if (sy.buy(portIndex, gi, 1) == 0) g.toast("Нет места или монет"); g.save(); refreshPort() } }
            val b5 = ui.button("+5", 0x2fbf6e, 0x22a95c, ui.small).also { it.onClick { if (sy.buy(portIndex, gi, 5) == 0) g.toast("Нет места или монет"); g.save(); refreshPort() } }
            val s1 = ui.button("Продать 1", 0xb9761e, 0x99611a, ui.small).also { it.onClick { sy.sell(portIndex, gi, 1); g.sfx.play("coin"); g.save(); refreshPort() } }
            val sa = ui.button("Продать всё", 0xb9761e, 0x99611a, ui.small).also { it.onClick { if (sy.sell(portIndex, gi, sy.cargo[gi]) > 0) g.sfx.play("coin"); g.save(); refreshPort() } }
            for (bt in listOf(b1, b5, s1, sa)) row.add(bt).growX().height(40f).pad(2f)
            c.add(row).growX().padTop(6f).row()
            content.add(c).growX().padBottom(8f).row()
        }
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

    private fun buildBoarding(target: Ship) {
        stage.clear()
        val ratio = g.systems.boardingRatio(target)
        val zone = (0.2f * ratio).coerceIn(0.08f, 0.42f)
        val root = overlay()
        val panel = panelTable()
        panel.add(ui.label("Абордаж!", ui.huge, align = Align.center)).growX().row()
        panel.add(ui.label("${target.name}. Ваша команда ${if (ratio >= 1) "сильнее" else "слабее"} противника. Жмите «Удар!», когда маркер в зелёной зоне. Нужно 2 удачных удара из 3.",
            ui.small, rgb(0xb9d6f2), wrap = true, align = Align.center)).growX().pad(6f, 0f, 12f, 0f).row()
        val bar = BoardBar(ui, zone)
        panel.add(bar).growX().height(30f).padBottom(10f).row()
        val status = ui.label("Удар 1 из 3", ui.mid, align = Align.center)
        panel.add(status).growX().padBottom(10f).row()
        var round = 0
        var hits = 0
        val strike = ui.button("⚔  Удар!", 0xd32f2f, 0xb71c1c, ui.big)
        strike.onClick {
            if (round >= 3) return@onClick
            val ok = kotlin.math.abs(bar.pos - 0.5f) < zone / 2
            if (ok) hits++
            round++
            g.sfx.play(if (ok) "hit" else "splash")
            g.systems.vibrate(if (ok) 60 else 20)
            bar.speed *= 1.3f
            status.setText((if (ok) "Попали!" else "Промах!") + "   $hits из $round" + if (round < 3) " · удар ${round + 1} из 3" else "")
            if (round >= 3) {
                bar.frozen = true
                stage.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.delay(0.6f, com.badlogic.gdx.scenes.scene2d.actions.Actions.run { boardingResult(target, hits >= 2) }))
            }
        }
        panel.add(strike).growX().height(64f).row()
        root.add(panel).width(min(g.wDp - 24, 440f))
    }

    private fun boardingResult(target: Ship, win: Boolean) {
        stage.clear()
        val root = overlay()
        val panel = panelTable()
        val p = g.player
        if (!win) {
            if (p != null) p.hp = max(1f, p.hp - p.maxHp * 0.25f)
            target.hp = min(target.maxHp, target.hp + target.maxHp * 0.15f)
            if (p != null) { target.vx += (target.x - p.x) * 0.5f; target.vz += (target.z - p.z) * 0.5f }
            panel.add(ui.label("Абордаж отбит!", ui.big, align = Align.center)).growX().row()
            panel.add(ui.label("Команда отступила с потерями. Корабль повреждён.", ui.small, rgb(0xb9d6f2), wrap = true, align = Align.center)).growX().pad(8f, 0f, 12f, 0f).row()
            val ok = ui.blue("Продолжить бой").also { it.onClick { close() } }
            panel.add(ok).growX().height(52f).row()
        } else {
            g.systems.addStat("boards")
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
        val scroll = ScrollPane(content, ui.scrollStyle).also { it.setScrollingDisabled(true, false); it.setOverscroll(false, true) }
        panel.add(scroll).grow().row()
        val back = ui.blue("Назад").also { it.onClick { current = { buildPause() }; buildPause() } }
        panel.add(back).growX().height(50f).padTop(8f).row()
        root.add(panel).width(min(g.wDp - 16, 460f)).height(g.hDp - g.safeTop - g.safeBottom - 24)
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
    private class WorldMap(val g: SeaGame) : Actor() {
        override fun draw(batch: Batch, parentAlpha: Float) {
            val ui = g.ui
            val cx = x + width / 2; val cy = y + height / 2
            val r = min(width, height) / 2
            val k = r / WORLD
            ui.round(batch, x, y, width, height, rgb(0x1f6fb0))
            // регионы: цветные квадранты по краям и центр
            fun dot(wx: Float, wz: Float, size: Float, col: Int) { ui.icon(batch, ui.disc, cx + wx * k, cy - wz * k, size, size, 0f, rgb(col)) }
            for (gx in -12..12) for (gz in -12..12) {
                val wx = gx * WORLD / 12f; val wz = gz * WORLD / 12f
                val col = when (regionAt(wx, wz)) { Region.ICE -> 0x7fa8c4; Region.FIRE -> 0x4b5e6b; Region.REEF -> 0x2f8f88; Region.ABYSS -> 0x163a63; else -> 0x2a82c4 }
                ui.rect(batch, cx + wx * k - r / 24f, cy - wz * k - r / 24f, r / 12f + 1, r / 12f + 1, rgb(col, 0.9f))
            }
            for (isl in g.islands) dot(isl.x, isl.z, max(3f, isl.r * k * 2), if (isl.port >= 0) 0xe6d3a3 else 0x7cb342)
            for (f in g.systems.fortresses) ui.icon(batch, ui.squareIcon, cx + f.x * k, cy - f.z * k, 8f, 8f, 0f, rgb(if (f.alive) 0xd84315 else 0x757575))
            for (port in g.ports) {
                dot(port.rx, port.rz, 8f, 0xffffff)
                val lx = (cx + port.rx * k - 80).coerceIn(x + 2, x + width - 162)
                ui.text(batch, ui.small, port.def.name, lx, cy - port.rz * k - 6, Align.center, 160f)
            }
            g.questTarget()?.let { dot(it.first, it.second, 10f, 0xffc93c) }
            g.player?.let { p -> ui.icon(batch, ui.arrowIcon, cx + p.x * k, cy - p.z * k, 14f, 16f, -p.h * MathUtils.radiansToDegrees, Color.WHITE) }
            ui.text(batch, ui.mid, "ЛЕДЯНОЕ МОРЕ", cx - 100, y + height - 6, Align.center, 200f, c = rgb(0xe1f5fe))
            ui.text(batch, ui.mid, "БЕЗДНА", cx - 100, y + 24, Align.center, 200f, c = rgb(0xbbdefb))
            ui.text(batch, ui.small, "ТУМАННЫЙ\nРИФ", x + 4, y + height * 0.82f, Align.left, 110f, c = rgb(0xb2dfdb))
            ui.text(batch, ui.small, "ОГНЕННЫЕ\nОСТРОВА", x + width - 114, y + height * 0.82f, Align.right, 110f, c = rgb(0xffccbc))
            batch.color = Color.WHITE
        }
    }

    private fun buildMap() {
        stage.clear()
        val root = overlay()
        val panel = panelTable()
        panel.add(ui.label("Карта мира", ui.big, align = Align.center)).growX().row()
        val side = min(g.wDp - 48, g.hDp - g.safeTop - g.safeBottom - 170)
        panel.add(WorldMap(g)).size(side).pad(8f).row()
        val back = ui.blue("Назад").also { it.onClick { current = { buildPause() }; buildPause() } }
        panel.add(back).growX().height(50f).row()
        root.add(panel)
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
