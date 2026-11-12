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

class MainActivity : Activity() {

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
    private var traveling = false
    private var dayOverride: Int? = null
    private var mapInfo: TextView? = null
    private var sailButton: Button? = null

    private val dp get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("save", MODE_PRIVATE)
        val saved = prefs.getString("game", null)?.let { Save.fromJson(it) }
        game = saved ?: Game()

        map = MapView(this).apply {
            game = this@MainActivity.game
            onSelect = { renderMapInfo() }
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
            val b = button(label, CARD) { show(s) }
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

        show(Screen.MARKET)
        when {
            game.over -> showEndDialog()
            saved == null -> showIntro()
        }
    }

    override fun onPause() {
        super.onPause()
        if (!traveling) save()
    }

    private fun save() {
        prefs.edit().putString("game", Save.toJson(game)).apply()
    }

    private fun newGame() {
        map.stopVoyage()
        traveling = false
        dayOverride = null
        game = Game()
        map.game = game
        map.selected = -1
        map.interactive = true
        nav.visibility = View.VISIBLE
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
        sailButton = null
        val view = when (s) {
            Screen.MARKET -> marketScreen()
            Screen.SHIPYARD -> shipyardScreen()
            Screen.MAP -> mapScreen()
        }
        content.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
        renderStatus()
    }

    private fun refresh() {
        if (!traveling) show(screen)
    }

    private fun renderStatus() {
        val day = dayOverride ?: game.day
        statusTop.text = "💰 ${game.gold.money()}     📅 День ${day.coerceAtMost(MAX_DAY)} из $MAX_DAY"
        val where = if (traveling) "в море" else PORTS[game.port].name
        statusBottom.text = "📦 ${game.used}/${game.capacity}   🛠 Корпус ${game.hull}%   ⚓ $where"
        statusBottom.setTextColor(if (game.hull < 35) DANGER else MUTED)
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

        col.addView(card(
            "⛵ Ваш корабль",
            "Трюм: ${game.capacity} мест\n" +
                "Скорость: ×${"%.2f".format(Locale.US, game.speed)}\n" +
                "Пушки: ${game.cannons}\n" +
                "Корпус: ${game.hull}%\n" +
                "Жалованье команды: ${game.wages} 💰 в день"
        ), cardParams())

        col.addView(actionCard(
            "🔧 Ремонт",
            if (game.hull >= 100) "Корпус в полном порядке." else "Починить корпус до 100%. Если корпус разрушится, корабль утонет!",
            if (game.hull >= 100) null else game.repairCost
        ) { game.repair() }, cardParams())

        col.addView(actionCard(
            "📦 Больший трюм",
            "+15 мест для товара (сейчас ${game.capacity}).",
            game.holdUpgradeCost
        ) { game.upgradeHold() }, cardParams())

        col.addView(actionCard(
            "🪁 Новые паруса",
            "Плавания на 25% быстрее, от пиратов уходить легче. Шанс уйти от погони: ${pct(game.fleeChance)}.",
            game.sailUpgradeCost
        ) { game.upgradeSails() }, cardParams())

        col.addView(actionCard(
            "💣 Пушка",
            "Выше шанс победить пиратов (сейчас ${pct(game.fightChance)}). Жалованье +3 💰 в день.",
            game.cannonCost
        ) { game.buyCannon() }, cardParams())

        val best = prefs.getInt("best", 0)
        col.addView(card(
            "🏅 Капитанский журнал",
            "Звание: ${game.title()}\n" +
                "Состояние (золото + товар): ${game.netWorth().money()} 💰\n" +
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
        val btn = button("⛵ Отплыть", GOOD_DARK) {
            val to = map.selected
            if (to >= 0 && to != game.port) sail(to)
        }
        val panel = column(padded = false).apply {
            background = rounded(CARD, 14f)
            setPadding(dpi(14), dpi(12), dpi(14), dpi(12))
            addView(info)
            addView(btn, LinearLayout.LayoutParams(MATCH, dpi(48)).apply { topMargin = dpi(10) })
        }
        col.addView(panel, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dpi(10) })
        mapInfo = info
        sailButton = btn
        renderMapInfo()
        return col
    }

    private fun renderMapInfo() {
        val info = mapInfo ?: return
        val btn = sailButton ?: return
        val to = map.selected
        if (traveling) return
        if (to < 0 || to == game.port) {
            info.text = "Вы в порту «${PORTS[game.port].name}».\nНажмите на другой порт на карте, чтобы проложить курс."
            btn.visibility = View.GONE
            return
        }
        val p = PORTS[to]
        val days = game.travelDays(to)
        var s = "Курс: ${p.name}\n" +
            "В пути: $days дн.  ·  Жалованье: ${(days * game.wages).money()} 💰\n" +
            "Обычно дёшево: ${names(p.cheap)}\nОбычно дорого: ${names(p.dear)}"
        if (game.day + days >= MAX_DAY) s += "\n⚠ Торговый сезон закончится в пути!"
        if (game.hull < 35) s += "\n⚠ Корпус сильно повреждён. Загляните на верфь!"
        info.text = s
        btn.visibility = View.VISIBLE
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

    private fun sail(to: Int) {
        save()
        val v = game.planVoyage(to)
        traveling = true
        nav.visibility = View.GONE
        map.interactive = false
        sailButton?.visibility = View.GONE
        val startDay = game.day
        val totalMs = (v.days * 170L).coerceIn(1800L, 5500L)
        val ef = if (v.event != null) v.eventDay.toFloat() / v.days else 1f

        val progress: (Float) -> Unit = { f ->
            dayOverride = startDay + (f * v.days).toInt()
            renderStatus()
            mapInfo?.text = "⛵ Плывём в «${PORTS[v.to].name}»…\nДень пути ${(f * v.days).toInt().coerceAtLeast(1)} из ${v.days}"
        }
        val finish = {
            game.advance(v.days - (if (v.event != null) v.eventDay else 0))
            arrive(v)
        }

        map.animateVoyage(v.from, v.to, 0f, ef, (totalMs * ef).toLong(), progress) {
            val ev = v.event
            if (ev == null) {
                finish()
            } else {
                game.advance(v.eventDay)
                dayOverride = null
                renderStatus()
                handleEvent(ev) {
                    if (game.over) {
                        shipwreck()
                    } else {
                        map.animateVoyage(v.from, v.to, ef, 1f, (totalMs * (1 - ef)).toLong(), progress) { finish() }
                    }
                }
            }
        }
    }

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
            is Event.Wind -> showResult("🌬 Попутный ветер", "Ветер наполнил паруса: путь короче на ${ev.savedDays} дн.!")
        }
    }

    private fun arrive(v: Voyage) {
        game.arrive(v.to)
        map.stopVoyage()
        traveling = false
        dayOverride = null
        map.selected = -1
        map.interactive = true
        nav.visibility = View.VISIBLE
        save()
        show(Screen.MARKET)
        if (game.over) showEndDialog() else toast("Прибыли в порт «${PORTS[v.to].name}»")
    }

    private fun shipwreck() {
        map.stopVoyage()
        traveling = false
        dayOverride = null
        map.interactive = true
        nav.visibility = View.VISIBLE
        save()
        show(Screen.MARKET)
        showEndDialog()
    }

    // ---------------- Диалоги ----------------

    private fun showIntro() {
        dialog().setTitle("⛵ Добро пожаловать, капитан!")
            .setMessage(
                "У вас небольшой парусник, $START_GOLD золотых и $MAX_DAY дней торгового сезона.\n\n" +
                    "• Покупайте товары там, где они дёшевы, и везите туда, где они в цене.\n" +
                    "• На карте видно, что где обычно дёшево и дорого, а слухи в таверне подскажут, где выгоднее сейчас.\n" +
                    "• Каждый день в море стоит денег: команде надо платить.\n" +
                    "• Берегитесь штормов и пиратов. На верфи можно чинить и улучшать корабль.\n\n" +
                    "Цель: к концу сезона стать самым богатым купцом архипелага!"
            )
            .setPositiveButton("Поднять паруса!", null)
            .show()
    }

    private fun showEndDialog() {
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

    private fun actionCard(title: String, body: String, cost: Int?, action: () -> Boolean): View {
        val c = card(title, body)
        val label = if (cost == null) "Недоступно" else "Купить за ${cost.money()} 💰"
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
