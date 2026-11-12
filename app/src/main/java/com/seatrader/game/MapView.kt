package com.seatrader.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Region
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Карта архипелага со свободным плаванием.
 * Коснитесь карты в любом месте: там появится джойстик, и корабль поплывёт
 * в сторону, куда тянете палец. Короткое касание порта показывает курс до него.
 */
class MapView(context: Context) : View(context) {

    interface Listener {
        fun onSelect(port: Int)
        fun onDeparted()
        fun onDayPassed()
        fun onNearPortChanged(port: Int)
        fun onEvent(event: Event)
    }

    var game: Game? = null
        set(value) {
            field = value
            trail.clear()
            lastDay = value?.day ?: 0
            nearPort = -1
            ensureShipFree()
            invalidate()
        }
    var listener: Listener? = null
    var selected = -1
        set(value) { field = value; invalidate() }

    /** Пока показан диалог события, корабль стоит. */
    var paused = false
        set(value) {
            field = value
            if (value) releaseJoystick()
        }

    var nearPort = -1
        private set

    private val dp = resources.displayMetrics.density
    private var mapL = 0f
    private var mapT = 0f
    private var mapW = 1f
    private var mapH = 1f
    val aspect get() = mapH / mapW

    private val islands = ArrayList<Path>()
    private val islandRegions = ArrayList<Region>()
    private val waves = ArrayList<PointF>()
    private val trail = ArrayDeque<PointF>()
    private var lastDay = 0

    // Джойстик
    private var joyActive = false
    private var dragging = false
    private var joyCx = 0f
    private var joyCy = 0f
    private var knobX = 0f
    private var knobY = 0f
    private val joyR get() = 56 * dp

    // Игровой цикл
    private var loopRunning = false
    private var lastFrameNs = 0L
    private val frame = object : Runnable {
        override fun run() {
            step()
            if (loopRunning) postOnAnimation(this)
        }
    }

    private val sea = Paint()
    private val sand = fill(0xFFE8D6A0.toInt())
    private val grass = fill(0xFF5E9E54.toInt())
    private val wave = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x55BFE3FF; style = Paint.Style.STROKE; strokeWidth = 1.5f * dp; strokeCap = Paint.Cap.ROUND
    }
    private val route = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF2B84B.toInt(); style = Paint.Style.STROKE; strokeWidth = 2.5f * dp
        pathEffect = DashPathEffect(floatArrayOf(10 * dp, 7 * dp), 0f)
    }
    private val portFill = fill(Color.WHITE)
    private val portHome = fill(0xFFF2B84B.toInt())
    private val portRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF2B84B.toInt(); style = Paint.Style.STROKE; strokeWidth = 3 * dp
    }
    private val nearRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF7BD88F.toInt(); style = Paint.Style.STROKE; strokeWidth = 3 * dp
    }
    private val portEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0E2236.toInt(); style = Paint.Style.STROKE; strokeWidth = 2 * dp
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 12.5f * dp; isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        setShadowLayer(3 * dp, 0f, 0f, 0xFF0E2236.toInt())
    }
    private val hullPaint = fill(0xFF8D5A2B.toInt())
    private val deckPaint = fill(0xFFC08A55.toInt())
    private val sailPaint = fill(0xFFFFFFFF.toInt()).apply { setShadowLayer(2 * dp, 0f, 1 * dp, 0x66000000) }
    private val flagPaint = fill(0xFFE53935.toInt())
    private val wakePaint = fill(Color.WHITE)
    private val joyBase = fill(0x33FFFFFF)
    private val joyEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88FFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 2 * dp
    }
    private val joyKnob = fill(0xCCFFFFFF.toInt())

    private val hullPath = Path()
    private val deckPath = Path()

    private fun fill(c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c; style = Paint.Style.FILL }

    private fun px(x: Float) = mapL + x * mapW
    private fun py(y: Float) = mapT + y * mapH

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val pad = 22 * dp
        mapL = pad; mapT = pad
        mapW = w - 2 * pad; mapH = h - 2 * pad
        sea.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF1D5C8C.toInt(), 0xFF113A5E.toInt(), Shader.TileMode.CLAMP)
        buildScenery(w, h)
        buildShipShape()
        trail.clear()
        ensureShipFree()
    }

    private fun buildScenery(w: Int, h: Int) {
        val rnd = Random(7)
        islands.clear()
        islandRegions.clear()
        waves.clear()
        val unit = min(mapW, mapH)
        // Остров над каждым портом: порт стоит на южном берегу.
        for (p in PORTS) {
            val r = unit * (0.07f + rnd.nextFloat() * 0.03f)
            islands += blob(px(p.x), py(p.y) - r * 1.05f, r, rnd)
        }
        // Мелкие необитаемые островки.
        var tries = 0
        while (islands.size < PORTS.size + 7 && tries < 300) {
            tries++
            val x = 0.05f + rnd.nextFloat() * 0.9f
            val y = 0.05f + rnd.nextFloat() * 0.9f
            if (PORTS.any { hypot(it.x - x, it.y - y) < 0.17f }) continue
            islands += blob(px(x), py(y), unit * (0.02f + rnd.nextFloat() * 0.03f), rnd)
        }
        val clip = Region(0, 0, w, h)
        for (path in islands) islandRegions += Region().apply { setPath(path, clip) }
        repeat(45) { waves += PointF(rnd.nextFloat(), rnd.nextFloat()) }
    }

    private fun blob(cx: Float, cy: Float, r: Float, rnd: Random): Path {
        val n = 12
        val pts = (0 until n).map { i ->
            val a = 2 * PI * i / n
            val rr = r * (0.8f + rnd.nextFloat() * 0.35f)
            PointF(cx + (cos(a) * rr * 1.25f).toFloat(), cy + (sin(a) * rr).toFloat())
        }
        val path = Path()
        val first = mid(pts[n - 1], pts[0])
        path.moveTo(first.x, first.y)
        for (i in 0 until n) {
            val m = mid(pts[i], pts[(i + 1) % n])
            path.quadTo(pts[i].x, pts[i].y, m.x, m.y)
        }
        path.close()
        return path
    }

    private fun mid(a: PointF, b: PointF) = PointF((a.x + b.x) / 2, (a.y + b.y) / 2)

    private fun buildShipShape() {
        val s = dp
        hullPath.reset()
        hullPath.moveTo(17 * s, 0f)
        hullPath.quadTo(7 * s, -8 * s, -12 * s, -6.5f * s)
        hullPath.lineTo(-13 * s, 6.5f * s)
        hullPath.quadTo(7 * s, 8 * s, 17 * s, 0f)
        hullPath.close()
        deckPath.reset()
        deckPath.moveTo(12 * s, 0f)
        deckPath.quadTo(5 * s, -5 * s, -9 * s, -4 * s)
        deckPath.lineTo(-10 * s, 4 * s)
        deckPath.quadTo(5 * s, 5 * s, 12 * s, 0f)
        deckPath.close()
    }

    // ---------------- Столкновения ----------------

    private fun isLand(x: Float, y: Float): Boolean {
        val xi = x.toInt()
        val yi = y.toInt()
        return islandRegions.any { it.contains(xi, yi) }
    }

    private fun blocked(x: Float, y: Float): Boolean {
        if (x < mapL || x > mapL + mapW || y < mapT || y > mapT + mapH) return true
        val r = 5 * dp
        return isLand(x, y) || isLand(x + r, y) || isLand(x - r, y) || isLand(x, y + r) || isLand(x, y - r)
    }

    /** Если корабль оказался на суше (например, у порта), сдвигаем его на ближайшую воду. */
    fun ensureShipFree() {
        val g = game ?: return
        if (islandRegions.isEmpty() || mapW <= 1f) return
        val sx = px(g.shipX)
        val sy = py(g.shipY)
        if (!blocked(sx, sy)) return
        val step = 3 * dp
        for (ring in 1..80) {
            val rad = ring * step
            val n = 8 + ring * 2
            for (k in 0 until n) {
                // Сначала пробуем направления к югу: порты стоят на южных берегах.
                val a = PI / 2 + (if (k % 2 == 0) 1 else -1) * PI * ((k + 1) / 2) / n * 2
                val x = sx + (cos(a) * rad).toFloat()
                val y = sy + (sin(a) * rad).toFloat()
                if (!blocked(x, y)) {
                    g.shipX = (x - mapL) / mapW
                    g.shipY = (y - mapT) / mapH
                    return
                }
            }
        }
    }

    fun placeShipAtPort() {
        trail.clear()
        nearPort = -1
        ensureShipFree()
        invalidate()
    }

    // ---------------- Игровой цикл ----------------

    private fun startLoop() {
        if (loopRunning) return
        loopRunning = true
        lastFrameNs = System.nanoTime()
        postOnAnimation(frame)
    }

    private fun stopLoop() {
        loopRunning = false
        removeCallbacks(frame)
    }

    private fun releaseJoystick() {
        joyActive = false
        dragging = false
        stopLoop()
        invalidate()
    }

    private fun step() {
        val now = System.nanoTime()
        val dt = ((now - lastFrameNs) / 1e9f).coerceAtMost(0.05f)
        lastFrameNs = now
        val g = game
        if (g == null || paused || g.over || !joyActive) {
            stopLoop()
            return
        }
        var vx = (knobX - joyCx) / joyR
        var vy = (knobY - joyCy) / joyR
        val mag = hypot(vx, vy).coerceAtMost(1f)
        if (mag < 0.15f) return
        val len = hypot(vx, vy)
        vx /= len; vy /= len

        if (!g.atSea) {
            g.leavePort()
            listener?.onDeparted()
        }

        g.heading = atan2(vy, vx)
        // Один день пути ≈ 0.4 секунды при полном ходе.
        val pxPerSec = (1.0 / DAYS_PER_UNIT / 0.4 * g.effectiveSpeed).toFloat() * mapW * mag
        val dx = vx * pxPerSec * dt
        val dy = vy * pxPerSec * dt
        val cx = px(g.shipX)
        val cy = py(g.shipY)
        var nx = cx + dx
        var ny = cy + dy
        if (blocked(nx, ny)) {
            // Скользим вдоль берега.
            when {
                !blocked(cx + dx, cy) -> { nx = cx + dx; ny = cy }
                !blocked(cx, cy + dy) -> { nx = cx; ny = cy + dy }
                else -> { nx = cx; ny = cy }
            }
        }
        val moved = hypot(nx - cx, ny - cy)
        if (moved > 0f) {
            g.shipX = (nx - mapL) / mapW
            g.shipY = (ny - mapT) / mapH
            val last = trail.lastOrNull()
            if (last == null || hypot(last.x - nx, last.y - ny) > 5 * dp) {
                trail.addLast(PointF(nx, ny))
                while (trail.size > 28) trail.removeFirst()
            }
        }

        val event = g.sail((moved / mapW).toDouble())
        if (g.day != lastDay) {
            lastDay = g.day
            listener?.onDayPassed()
        }
        updateNearPort()
        if (event != null) {
            paused = true
            listener?.onEvent(event)
        }
        if (g.over) releaseJoystick()
        invalidate()
    }

    private fun updateNearPort() {
        val g = game ?: return
        val sx = px(g.shipX)
        val sy = py(g.shipY)
        var best = -1
        var bestD = 40 * dp
        for ((i, p) in PORTS.withIndex()) {
            val d = hypot(px(p.x) - sx, py(p.y) - sy)
            if (d < bestD) { bestD = d; best = i }
        }
        if (!g.atSea) best = -1
        if (best != nearPort) {
            nearPort = best
            listener?.onNearPortChanged(best)
        }
    }

    // ---------------- Отрисовка ----------------

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), sea)
        for (w in waves) {
            val x = w.x * width
            val y = w.y * height
            canvas.drawLine(x, y, x + 5 * dp, y - 3 * dp, wave)
            canvas.drawLine(x + 5 * dp, y - 3 * dp, x + 10 * dp, y, wave)
        }
        val bounds = RectF()
        for (path in islands) {
            canvas.drawPath(path, sand)
            path.computeBounds(bounds, true)
            canvas.save()
            canvas.scale(0.8f, 0.8f, bounds.centerX(), bounds.centerY())
            canvas.drawPath(path, grass)
            canvas.restore()
        }

        val g = game ?: return
        val sx = px(g.shipX)
        val sy = py(g.shipY)

        if (selected >= 0 && !(selected == g.port && !g.atSea)) {
            canvas.drawLine(sx, sy, px(PORTS[selected].x), py(PORTS[selected].y), route)
        }

        for ((i, p) in PORTS.withIndex()) {
            val x = px(p.x)
            val y = py(p.y)
            val r = 7 * dp
            if (i == selected) canvas.drawCircle(x, y, r + 5 * dp, portRing)
            if (i == nearPort) canvas.drawCircle(x, y, r + 9 * dp, nearRing)
            canvas.drawCircle(x, y, r, if (i == g.port && !g.atSea) portHome else portFill)
            canvas.drawCircle(x, y, r, portEdge)

            val tw = label.measureText(p.name)
            val lx = x.coerceIn(tw / 2 + 4 * dp, width - tw / 2 - 4 * dp)
            canvas.drawText(p.name, lx, y - 12 * dp, label)
        }

        // След за кораблём
        for ((k, pt) in trail.withIndex()) {
            val f = (k + 1).toFloat() / trail.size
            wakePaint.alpha = (110 * f).toInt()
            canvas.drawCircle(pt.x, pt.y, (1.5f + 2.5f * f) * dp, wakePaint)
        }

        drawShip(canvas, sx, sy, g.heading, g.model)

        if (joyActive && dragging) {
            canvas.drawCircle(joyCx, joyCy, joyR, joyBase)
            canvas.drawCircle(joyCx, joyCy, joyR, joyEdge)
            canvas.drawCircle(knobX, knobY, 24 * dp, joyKnob)
        }
    }

    private fun drawShip(canvas: Canvas, x: Float, y: Float, heading: Float, model: ShipModel) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(Math.toDegrees(heading.toDouble()).toFloat())
        canvas.scale(model.size, model.size)
        canvas.drawPath(hullPath, hullPaint)
        canvas.drawPath(deckPath, deckPaint)
        // Паруса (вид сверху: поперечные полосы), по одной на мачту
        val n = model.masts
        for (k in 0 until n) {
            val cx = (5f - 12f * k / (n - 1).coerceAtLeast(1)) * dp
            val half = (if (k == 0 || k == n - 1) 7.5f else 9f) * dp
            canvas.drawRoundRect(cx - 1.8f * dp, -half, cx + 1.8f * dp, half, 2 * dp, 2 * dp, sailPaint)
        }
        canvas.drawRect(-14 * dp, -1.5f * dp, -11 * dp, 1.5f * dp, flagPaint)
        canvas.restore()
    }

    // ---------------- Касания ----------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val g = game ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                joyActive = true
                dragging = false
                joyCx = event.x; joyCy = event.y
                knobX = event.x; knobY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!joyActive) return true
                var dx = event.x - joyCx
                var dy = event.y - joyCy
                val d = hypot(dx, dy)
                if (d > 10 * dp) dragging = true
                if (d > joyR) { dx *= joyR / d; dy *= joyR / d }
                knobX = joyCx + dx
                knobY = joyCy + dy
                if (dragging && !paused && !g.over) startLoop()
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasTap = event.actionMasked == MotionEvent.ACTION_UP && !dragging
                releaseJoystick()
                if (wasTap) handleTap(event.x, event.y)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTap(x: Float, y: Float) {
        var best = -1
        var bestD = 44 * dp
        for ((i, p) in PORTS.withIndex()) {
            val d = hypot(px(p.x) - x, py(p.y) - y)
            if (d < bestD) { bestD = d; best = i }
        }
        if (best >= 0) {
            selected = if (selected == best) -1 else best
            listener?.onSelect(selected)
            performClick()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        releaseJoystick()
        super.onDetachedFromWindow()
    }
}
