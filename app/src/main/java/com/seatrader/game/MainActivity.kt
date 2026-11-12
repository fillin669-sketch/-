package com.seatrader.game

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity(), MapView.Listener {

    private enum class Screen { MARKET, SHIPYARD, MAP }

    private lateinit var prefs: SharedPreferences
    private lateinit var game: Game
    private lateinit var statusTop: TextView
    private lateinit var statusBottom: TextView
    private lateinit var content: FrameLayout
    private lateinit var nav: LinearLayout
    private lateinit var map: MapView
    private val navButtons = HashMap<Screen, Button>()

    private var screen = Screen.MARKET
    private var endShown = false
    private var mapInfo: TextView? = null
    private var dockButton: Button? = null

    private val dp get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("save", MODE_PRIVATE)
        val saved = prefs.getString("game", null)?.let { Save.fromJson(it) }
        game = saved ?: Game()

        map = MapView(this).apply {
            game = this@MainActivity.game
            listener = this@MainActivity
        }

        statusTop = text("", 16f, TEXT, bold = true)
        statusBottom = text("", 13.5f, MUTED)
        val statusBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(16), dpi(12), dpi(16), dpi(10))
            setBackgroundColor(BAR)
            addView(statusTop)
            addView(statusBottom)
        }

        content = FrameLayout(this)

        nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(BAR)
            setPadding(dpi(8), dpi(6), dpi(8), dpi(8))
        }
        for ((s, label) in listOf(Screen.MARKET to "🏪 Рынок", Screen.SHIPYARD to "🛠 Верфь", Screen.MAP to "🗺 Карта")) {
            val b = button(label, CARD) {
                if (game.atSea && s != Screen.MAP) toast("Вы в море. Зайдите в порт, чтобы торговать и чинить корабль")
                else show(s)
            }
            navButtons[s] = b
            nav.addView(b, LinearLayout.LayoutParams(0, dpi(48), 1f).apply { setMargins(dpi(4), 0, dpi(4), 0) })
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            fitsSystemWindows = true
            addView(statusBar, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(content, LinearLayout.LayoutParams(MATCH, 0, 1f))
            addView(nav, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        setContentView(root)

        show(if (game.atSea) Screen.MAP else Screen.MARKET)
        when {
            game.over -> showEndDialog()
            saved == null -> showIntro()
        }
    }

    override fun onPause() {
        super.onPause()
        save()
    }

    private fun save() {
        prefs.edit().putString("game", Save.toJson(game)).apply()
    }

    private fun newGame() {
        endShown = false
        game = Game()
        map.game = game
        map.paused = false
        map.selected = -1
        save()
        show(Screen.MARKET)
        showIntro()
    }

    // ---------------- Экраны ----------------

    private fun show(s: Screen) {
        screen = s
        for ((key, b) in navButtons) b.background = rounded(if (key == s) ACCENT_DARK else CARD, 12f)
        content.removeAllViews()
        mapInfo = null
        dockButton = null
        val view = when (s) {
            Screen.MARKET -> marketScreen()
            Screen.SHIPYARD -> shipyardScreen()
            Screen.MAP -> mapScreen()
        }
        content.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
        renderStatus()
    }

    private fun refresh() = show(screen)

    private fun renderStatus() {
        statusTop.text = "💰 ${game.gold.money()}     📅 День ${game.day.coerceAtMost(MAX_DAY)} из $MAX_DAY" +
            (if (game.boosted) "   🌬" else "")
        val where = if (game.atSea) "в море" else PORTS[game.port].name
        statusBottom.text = "⛵ ${game.model.name}  📦 ${game.used}/${game.capacity}  🛡 ${game.hull}/${game.maxHull}  ⚓ $where"
        statusBottom.setTextColor(if (game.hull < game.maxHull * 0.35) DANGER else MUTED)
    }

    private fun marketScreen(): View {
        val col = column()
        val port = PORTS[game.port]
        col.addView(text("⚓ ${port.name}", 22f, TEXT, bold = true))
        col.addView(text("Здесь обычно дёшево: ${names(port.cheap)}\nОбычно дорого: ${names(port.dear)}", 13.5f, MUTED).apply {
            setPadding(0, dpi(2), 0, dpi(8))
        })

        for ((g, good) in GOODS.withIndex()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(CARD, 14f)
                setPadding(dpi(14), dpi(10), dpi(10), dpi(10))
            }
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val buy = game.buyPrice(g)
            val trend = when {
                buy < good.base * 0.8 -> "  ▼ дёшево" to GOOD
                buy > good.base * 1.3 -> "  ▲ дорого" to DANGER
                else -> "" to MUTED
            }
            info.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(text("${good.emoji} ${good.name}", 17f, TEXT, bold = true))
                addView(text(trend.first, 13f, trend.second, bold = true))
            })
            info.addView(text("Купить: $buy  ·  Продать: ${game.sellPrice(g)}", 14f, MUTED))
            if (game.cargo[g] > 0) {
                info.addView(text("В трюме: ${game.cargo[g]} шт., куплено по ~${game.avgPaid(g)}", 13.5f, ACCENT))
            }
            row.addView(info, LinearLayout.LayoutParams(0, WRAP, 1f))

            val btns = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            btns.addView(smallButton("Купить", GOOD_DARK) { showTrade(g, buying = true) })
            btns.addView(smallButton("Продать", ACCENT_DARK) { showTrade(g, buying = false) }.apply {
                (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dpi(6)
            })
            row.addView(btns)
            col.addView(row, cardParams())
        }

        val news = column(padded = false).apply {
            background = rounded(CARD, 14f)
            setPadding(dpi(14), dpi(12), dpi(14), dpi(12))
            addView(text("📰 Слухи в таверне", 16f, TEXT, bold = true))
            if (game.news.isEmpty()) addView(text("Пока ничего не слышно. Слухи о ценах приходят со временем.", 13.5f, MUTED))
            for (n in game.news) addView(text("• $n", 13.5f, MUTED).apply { setPadding(0, dpi(4), 0, 0) })
        }
        col.addView(news, cardParams())
        return scroll(col)
    }

    private fun shipyardScreen(): View {
        val col = column()
        col.addView(text("🛠 Верфь", 22f, TEXT, bold = true))

        val m = game.model
        col.addView(card(
            "⛵ ${m.name} · ${m.role}",
            "${m.description}\n\n" +
                "📦 Трюм: ${game.capacity} мест\n" +
                "⚡ Скорость: ×${"%.2f".format(Locale.US, game.speed)}\n" +
                "🛡 Корпус: ${game.hull}/${game.maxHull}" + (if (m.armor > 0) ", броня гасит ${pct(m.armor)} урона" else "") + "\n" +
                "💣 Пушки: ${game.cannons} из ${m.cannonSlots}\n" +
                "🧭 Манёвренность: ${if (m.maneuver >= 0) "+" else ""}${pct(m.maneuver)} к побегу\n" +
                "👥 Жалованье команды: ${game.wages} 💰 в день\n\n" +
                "Против пиратов (их сила растёт со временем): бой ${pct(game.fightChance)}, побег ${pct(game.fleeChance)}"
        ), cardParams())

        col.addView(actionCard(
            "🔧 Ремонт",
            if (game.hull >= game.maxHull) "Корпус в полном порядке."
            else "Починить корпус до ${game.maxHull}. Если корпус разрушится, корабль утонет!",
            if (game.hull >= game.maxHull) null else game.repairCost,
            buyLabel = "Починить"
        ) { game.repair() }, cardParams())

        col.addView(section("Улучшения: ${m.name}"))
        col.addView(actionCard(
            "📦 Трюм  ${stars(game.holdLevel)}",
            "+20% места для груза. Сейчас ${game.capacity}, станет ${(m.cargo * (1 + 0.2 * (game.holdLevel + 1))).toInt()}.",
            game.holdUpgradeCost
        ) { game.upgradeHold() }, cardParams())
        col.addView(actionCard(
            "🪁 Паруса  ${stars(game.sailLevel)}",
            "+10% к скорости: короче плавания, проще уйти от пиратов.",
            game.sailUpgradeCost
        ) { game.upgradeSails() }, cardParams())
        col.addView(actionCard(
            "🛡 Обшивка  ${stars(game.armorLevel)}",
            "+15% прочности корпуса: штормы и абордажи страшны меньше.",
            game.armorUpgradeCost
        ) { game.upgradeArmor() }, cardParams())
        col.addView(actionCard(
            "💣 Пушки  ${game.cannons}/${m.cannonSlots}",
            if (game.cannons >= m.cannonSlots) "Все орудийные порты заняты. Нужно больше пушек? Нужен корабль побольше."
            else "Ещё одна пушка: выше шанс победить пиратов. Жалованье +2 💰 в день.",
            game.cannonCost
        ) { game.buyCannon() }, cardParams())

        col.addView(section("⚓ Корабли на продажу"))
        col.addView(text("За ваш корабль с улучшениями и пушками дадут ${game.tradeInValue().money()} 💰. Новый корабль приходит без улучшений и пушек.", 13.5f, MUTED))
        for ((i, sm) in SHIPS.withIndex()) {
            if (i == game.shipModel) continue
            col.addView(shipOffer(i, sm), cardParams())
        }

        val best = prefs.getInt("best", 0)
        col.addView(card(
            "🏅 Капитанский журнал",
            "Звание: ${game.title()}\n" +
                "Состояние (золото, товар, корабль): ${game.netWorth().money()} 💰\n" +
                "Рейсов: ${game.voyages}   Побеждено пиратов: ${game.piratesBeaten}" +
                (if (best > 0) "\nРекорд: ${best.money()} 💰" else "")
        ), cardParams())

        col.addView(button("Начать заново", CARD) {
            dialog().setTitle("Начать заново?")
                .setMessage("Текущая игра будет потеряна.")
                .setPositiveButton("Да") { _, _ -> newGame() }
                .setNegativeButton("Нет", null)
                .show()
        }, LinearLayout.LayoutParams(MATCH, dpi(48)).apply { topMargin = dpi(16) })
        return scroll(col)
    }

    private fun mapScreen(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(12), dpi(12), dpi(12), dpi(12))
        }
        (map.parent as? ViewGroup)?.removeView(map)
        val mapFrame = FrameLayout(this).apply {
            background = rounded(CARD, 16f)
            clipToOutline = true
            addView(map, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        col.addView(mapFrame, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val info = text("", 14.5f, TEXT)
        val btn = button("", GOOD_DARK) {
            val p = map.nearPort
            if (p >= 0 && game.atSea) dock(p)
        }
        val panel = column(padded = false).apply {
            background = rounded(CARD, 14f)
            setPadding(dpi(14), dpi(12), dpi(14), dpi(12))
            addView(info)
            addView(btn, LinearLayout.LayoutParams(MATCH, dpi(48)).apply { topMargin = dpi(10) })
        }
        col.addView(panel, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dpi(10) })
        mapInfo = info
        dockButton = btn
        renderMapInfo()
        return col
    }

    private fun renderMapInfo() {
        val info = mapInfo ?: return
        val btn = dockButton ?: return
        val lines = ArrayList<String>()
        if (!game.atSea) {
            lines += "⚓ Вы в порту «${PORTS[game.port].name}»."
            lines += "Коснитесь карты и тяните палец в сторону, куда плыть."
        } else {
            lines += "🌊 Открытое море · жалованье ${game.wages} 💰 в день"
            if (game.boosted) lines += "🌬 Попутный ветер ещё ${game.boostUntil - game.day} дн."
        }
        val to = map.selected
        if (to >= 0 && !(to == game.port && !game.atSea)) {
            val p = PORTS[to]
            lines += "🧭 До «${p.name}» ≈ ${game.daysTo(to, map.aspect)} дн."
            lines += "Обычно дёшево: ${names(p.cheap)}"
            lines += "Обычно дорого: ${names(p.dear)}"
        } else if (to < 0) {
            lines += "Нажмите на порт, чтобы увидеть курс и его товары."
        }
        if (game.hull < game.maxHull * 0.35) lines += "⚠ Корпус сильно повреждён. Зайдите на верфь!"
        if (MAX_DAY - game.day <= 20) lines += "⏳ До конца сезона ${MAX_DAY - game.day} дн."
        info.text = lines.joinToString("\n")

        val near = map.nearPort
        if (game.atSea && near >= 0) {
            btn.text = "⚓ Зайти в порт «${PORTS[near].name}»"
            btn.visibility = View.VISIBLE
        } else {
            btn.visibility = View.GONE
        }
    }

    // ---------- События карты ----------

    override fun onSelect(port: Int) = renderMapInfo()

    override fun onDeparted() {
        renderStatus()
        renderMapInfo()
    }

    override fun onDayPassed() {
        renderStatus()
        renderMapInfo()
        if (game.over) showEndDialog()
    }

    override fun onNearPortChanged(port: Int) = renderMapInfo()

    override fun onEvent(event: Event) {
        handleEvent(event) {
            save()
            if (game.over) showEndDialog() else map.paused = false
        }
    }

    // ---------------- Торговля ----------------

    private fun showTrade(g: Int, buying: Boolean) {
        val good = GOODS[g]
        val max = if (buying) game.maxBuy(g) else game.cargo[g]
        if (max <= 0) {
            toast(
                when {
                    !buying -> "В трюме нет товара «${good.name}»"
                    game.free <= 0 -> "Трюм полон"
                    else -> "Не хватает золота"
                }
            )
            return
        }
        val summary = text("", 16f, TEXT, bold = true)
        val hint = text("", 14f, MUTED)
        val seek = SeekBar(this).apply {
            min = 1
            this.max = max
            progress = max
        }
        fun update() {
            val q = seek.progress
            if (buying) {
                summary.text = "$q шт. за ${game.buyCost(g, q).money()} 💰"
                hint.text = "Можно купить до $max шт. Чем больше берёте, тем дороже каждая следующая единица."
            } else {
                val rev = game.sellRevenue(g, q)
                val profit = rev - game.avgPaid(g) * q
                summary.text = "$q шт. за ${rev.money()} 💰"
                hint.text = if (game.avgPaid(g) == 0) "Товар достался даром!"
                else "Прибыль: ${if (profit >= 0) "+" else ""}${profit.money()} 💰"
                hint.setTextColor(if (profit >= 0) GOOD else DANGER)
            }
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) = update()
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        update()

        val box = column().apply {
            setPadding(dpi(22), dpi(8), dpi(22), 0)
            addView(summary)
            addView(seek, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dpi(14), 0, dpi(10)) })
            addView(hint)
        }
        dialog()
            .setTitle("${good.emoji} ${good.name}: ${if (buying) "покупка" else "продажа"}")
            .setView(box)
            .setPositiveButton(if (buying) "Купить" else "Продать") { _, _ ->
                val ok = if (buying) game.buy(g, seek.progress) else game.sell(g, seek.progress)
                if (!ok) toast("Сделка не состоялась")
                save()
                refresh()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ---------------- Плавание ----------------

    private fun handleEvent(ev: Event, then: () -> Unit) {
        val showResult = { title: String, msg: String ->
            renderStatus()
            dialog().setTitle(title).setMessage(msg).setCancelable(false)
                .setPositiveButton("Дальше") { _, _ -> then() }
                .show()
        }
        when (ev) {
            is Event.Pirates -> {
                dialog().setTitle("🏴‍☠️ Пираты!")
                    .setMessage(
                        "На горизонте пиратский бриг, он идёт на перехват. Что прикажете, капитан?\n\n" +
                            "⚔ Сражаться: шанс победы ${pct(game.fightChance)}\n" +
                            "🏃 Бежать: шанс уйти ${pct(game.fleeChance)}\n" +
                            "💰 Откупиться: ${game.pirateToll.money()} золотых"
                    )
                    .setCancelable(false)
                    .setPositiveButton("Сражаться") { _, _ -> showResult("⚔ Бой", game.fightPirates()) }
                    .setNeutralButton("Бежать") { _, _ -> showResult("🏃 Погоня", game.fleePirates()) }
                    .setNegativeButton("Откупиться") { _, _ -> showResult("💰 Выкуп", game.payPirates()) }
                    .show()
            }
            is Event.Storm -> showResult("🌊 Шторм!", game.applyStorm())
            is Event.Wreckage -> showResult("🪵 Находка", game.applyWreckage())
            is Event.Wind -> showResult("🌬 Попутный ветер", game.applyWind())
        }
    }

    private fun dock(p: Int) {
        game.dock(p)
        map.selected = -1
        map.placeShipAtPort()
        save()
        show(Screen.MARKET)
        toast("Добро пожаловать в «${PORTS[p].name}»!")
    }

    // ---------------- Диалоги ----------------

    private fun showIntro() {
        dialog().setTitle("⛵ Добро пожаловать, капитан!")
            .setMessage(
                "У вас небольшой парусник, $START_GOLD золотых и $MAX_DAY дней торгового сезона.\n\n" +
                    "• Покупайте товары там, где они дёшевы, и везите туда, где они в цене.\n" +
                    "• В море управляйте кораблём джойстиком: коснитесь карты и тяните палец. У порта появится кнопка «Зайти в порт».\n" +
                    "• Нажмите на порт на карте, чтобы узнать, что там дёшево и дорого. Слухи в таверне подскажут, где выгоднее сейчас.\n" +
                    "• Пока вы в море, идут дни, а команде каждый день надо платить.\n" +
                    "• Берегитесь штормов и пиратов. На верфи можно чинить и улучшать корабль.\n\n" +
                    "Цель: к концу сезона стать самым богатым купцом архипелага!"
            )
            .setPositiveButton("Поднять паруса!", null)
            .show()
    }

    private fun showEndDialog() {
        if (endShown) return
        endShown = true
        map.paused = true
        val worth = game.netWorth()
        val best = prefs.getInt("best", 0)
        if (worth > best) prefs.edit().putInt("best", worth).apply()
        val title = if (game.sunk) "⚓ Корабль затонул" else "🏁 Сезон окончен"
        val intro = if (game.sunk) "Корпус не выдержал, и корабль пошёл ко дну. Груз потерян, но золото на берегу уцелело.\n\n"
        else "Торговый сезон подошёл к концу.\n\n"
        dialog().setTitle(title)
            .setMessage(
                intro +
                    "Итоговое состояние: ${worth.money()} 💰\n" +
                    "Звание: ${game.title()}\n" +
                    "Рейсов: ${game.voyages}, побеждено пиратов: ${game.piratesBeaten}\n" +
                    (if (worth > best) "\n🎉 Новый рекорд!" else "Рекорд: ${best.money()} 💰")
            )
            .setCancelable(false)
            .setPositiveButton("Новая игра") { _, _ -> newGame() }
            .show()
    }

    private fun dialog() = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)

    // ---------------- Вспомогательное ----------------

    private fun names(ids: List<Int>) = ids.joinToString(", ") { "${GOODS[it].emoji} ${GOODS[it].name}" }

    private fun pct(x: Double) = "${(x * 100).toInt()}%"

    private fun Int.money() = String.format(Locale.US, "%,d", this).replace(',', ' ')

    private fun dpi(v: Int) = (v * dp).toInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(0f, 1.15f)
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius * dp
    }

    private fun button(label: String, color: Int, onClick: () -> Unit) = Button(this).apply {
        text = label
        textSize = 15f
        isAllCaps = false
        setTextColor(Color.WHITE)
        stateListAnimator = null
        background = rounded(color, 12f)
        setOnClickListener { onClick() }
    }

    private fun smallButton(label: String, color: Int, onClick: () -> Unit) = button(label, color, onClick).apply {
        textSize = 14f
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        setPadding(dpi(14), dpi(8), dpi(14), dpi(8))
        layoutParams = LinearLayout.LayoutParams(dpi(96), WRAP)
    }

    private fun column(padded: Boolean = true) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        if (padded) setPadding(dpi(14), dpi(14), dpi(14), dpi(20))
    }

    private fun scroll(v: View) = ScrollView(this).apply {
        isFillViewport = true
        addView(v)
    }

    private fun cardParams() = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dpi(10) }

    private fun card(title: String, body: String) = column(padded = false).apply {
        background = rounded(CARD, 14f)
        setPadding(dpi(14), dpi(12), dpi(14), dpi(12))
        addView(text(title, 17f, TEXT, bold = true))
        addView(text(body, 14.5f, MUTED).apply { setPadding(0, dpi(4), 0, 0) })
    }

    private fun stars(level: Int) = "★".repeat(level) + "☆".repeat(MAX_UPGRADE - level)

    private fun section(title: String) = text(title, 18f, ACCENT, bold = true).apply {
        setPadding(0, dpi(20), 0, dpi(2))
    }

    private fun shipStats(m: ShipModel) =
        "📦 ${m.cargo}   ⚡ ×${"%.2f".format(Locale.US, m.speed)}   🛡 ${m.hull}   💣 ${m.cannonSlots}\n" +
            "👥 ${m.wages} 💰/день" + (if (m.armor > 0) "   броня ${pct(m.armor)}" else "") +
            "   манёвр ${if (m.maneuver >= 0) "+" else ""}${pct(m.maneuver)}"

    private fun shipOffer(i: Int, m: ShipModel): View {
        val c = card("⛵ ${m.name} · ${m.role}", "${m.description}\n${shipStats(m)}\nЦена: ${m.price.money()} 💰")
        val cost = game.shipCost(i)
        val reason = game.cantBuyShip(i)
        val label = if (cost >= 0) "Купить: доплата ${cost.money()} 💰" else "Обменять: получите ${(-cost).money()} 💰"
        val b = button(label, if (reason == null) GOOD_DARK else DISABLED) {
            if (reason != null) {
                toast(reason)
                return@button
            }
            dialog().setTitle("Купить «${m.name}»?")
                .setMessage(
                    "Ваш корабль «${game.model.name}» уйдёт в зачёт за ${game.tradeInValue().money()} 💰.\n" +
                        (if (cost >= 0) "Доплата: ${cost.money()} 💰." else "Вы получите ${(-cost).money()} 💰.") +
                        "\n\nУлучшения и пушки остаются со старым кораблём, груз переносится."
                )
                .setPositiveButton("Купить") { _, _ ->
                    if (game.buyShip(i)) {
                        save()
                        map.invalidate()
                        refresh()
                        toast("Теперь вы капитан корабля «${m.name}»!")
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
        c.addView(b, LinearLayout.LayoutParams(MATCH, dpi(44)).apply { topMargin = dpi(10) })
        return c
    }

    private fun actionCard(title: String, body: String, cost: Int?, buyLabel: String = "Купить", action: () -> Boolean): View {
        val c = card(title, body)
        val label = if (cost == null) "Недоступно" else "$buyLabel за ${cost.money()} 💰"
        val b = button(label, if (cost != null && cost <= game.gold) GOOD_DARK else DISABLED) {
            if (cost == null) return@button
            if (action()) {
                save()
                refresh()
            } else {
                toast("Не хватает золота")
            }
        }
        if (cost == null) b.isEnabled = false
        c.addView(b, LinearLayout.LayoutParams(MATCH, dpi(44)).apply { topMargin = dpi(10) })
        return c
    }

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        private const val BG = 0xFF0E2236.toInt()
        private const val BAR = 0xFF0A1A2A.toInt()
        private const val CARD = 0xFF173452.toInt()
        private const val TEXT = 0xFFFFFFFF.toInt()
        private const val MUTED = 0xFFA9C1D9.toInt()
        private const val ACCENT = 0xFFF2B84B.toInt()
        private const val ACCENT_DARK = 0xFFB9761E.toInt()
        private const val GOOD = 0xFF7BD88F.toInt()
        private const val GOOD_DARK = 0xFF2E7D4F.toInt()
        private const val DANGER = 0xFFFF7B72.toInt()
        private const val DISABLED = 0xFF3A4E63.toInt()
    }
}
