package com.seatrader.game

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Карта архипелага. Нажатие на порт выбирает его как цель плавания. */
class MapView(context: Context) : View(context) {

    var game: Game? = null
    var selected = -1
        set(value) { field = value; invalidate() }
    var onSelect: ((Int) -> Unit)? = null
    var interactive = true

    private val dp = resources.displayMetrics.density
    private var mapL = 0f
    private var mapT = 0f
    private var mapW = 1f
    private var mapH = 1f

    // Положение корабля в долях карты; во время плавания меняется анимацией.
    private var ship: PointF? = null
    private var animator: ValueAnimator? = null

    private val islands = ArrayList<Path>()
    private val waves = ArrayList<PointF>()

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
    private val portEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0E2236.toInt(); style = Paint.Style.STROKE; strokeWidth = 2 * dp
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 12.5f * dp; isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        setShadowLayer(3 * dp, 0f, 0f, 0xFF0E2236.toInt())
    }
    private val shipText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30 * dp; textAlign = Paint.Align.CENTER }

    private fun fill(c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c; style = Paint.Style.FILL }

    private fun px(x: Float) = mapL + x * mapW
    private fun py(y: Float) = mapT + y * mapH

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val pad = 22 * dp
        mapL = pad; mapT = pad
        mapW = w - 2 * pad; mapH = h - 2 * pad
        sea.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF1D5C8C.toInt(), 0xFF113A5E.toInt(), Shader.TileMode.CLAMP)
        buildScenery()
    }

    private fun buildScenery() {
        val rnd = Random(7)
        islands.clear()
        waves.clear()
        val unit = min(mapW, mapH)
        // Остров под каждым портом: порт стоит на южном берегу.
        for (p in PORTS) {
            val r = unit * (0.075f + rnd.nextFloat() * 0.03f)
            islands += blob(px(p.x), py(p.y) - r * 0.65f, r, rnd)
        }
        // Мелкие необитаемые островки.
        var tries = 0
        while (islands.size < PORTS.size + 7 && tries < 200) {
            tries++
            val x = 0.05f + rnd.nextFloat() * 0.9f
            val y = 0.05f + rnd.nextFloat() * 0.9f
            if (PORTS.any { hypot(it.x - x, it.y - y) < 0.17f }) continue
            islands += blob(px(x), py(y), unit * (0.02f + rnd.nextFloat() * 0.03f), rnd)
        }
        repeat(45) { waves += PointF(rnd.nextFloat(), rnd.nextFloat()) }
    }

    private fun blob(cx: Float, cy: Float, r: Float, rnd: Random): Path {
        val n = 12
        val pts = (0 until n).map { i ->
            val a = 2 * PI * i / n
            val rr = r * (0.75f + rnd.nextFloat() * 0.45f)
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

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), sea)
        for (w in waves) {
            val x = w.x * width
            val y = w.y * height
            canvas.drawLine(x, y, x + 5 * dp, y - 3 * dp, wave)
            canvas.drawLine(x + 5 * dp, y - 3 * dp, x + 10 * dp, y, wave)
        }
        for (path in islands) {
            canvas.save()
            canvas.drawPath(path, sand)
            canvas.scale(0.8f, 0.8f, pathCenterX(path), pathCenterY(path))
            canvas.drawPath(path, grass)
            canvas.restore()
        }

        val g = game ?: return
        val home = PORTS[g.port]
        if (selected >= 0 && selected != g.port) {
            canvas.drawLine(px(home.x), py(home.y), px(PORTS[selected].x), py(PORTS[selected].y), route)
        }

        for ((i, p) in PORTS.withIndex()) {
            val x = px(p.x)
            val y = py(p.y)
            val r = 7 * dp
            if (i == selected) canvas.drawCircle(x, y, r + 5 * dp, portRing)
            canvas.drawCircle(x, y, r, if (i == g.port && ship == null) portHome else portFill)
            canvas.drawCircle(x, y, r, portEdge)

            val tw = label.measureText(p.name)
            val lx = x.coerceIn(tw / 2 + 4 * dp, width - tw / 2 - 4 * dp)
            val ly = if (p.y > 0.8f) y - 13 * dp else y + 22 * dp
            canvas.drawText(p.name, lx, ly, label)
        }

        val s = ship ?: PointF(home.x, home.y)
        val sx = px(s.x)
        val sy = py(s.y) - 10 * dp
        canvas.drawText("⛵", sx, sy, shipText)
    }

    private val tmpBounds = android.graphics.RectF()
    private fun pathCenterX(p: Path): Float { p.computeBounds(tmpBounds, true); return tmpBounds.centerX() }
    private fun pathCenterY(p: Path): Float { p.computeBounds(tmpBounds, true); return tmpBounds.centerY() }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactive) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                var best = -1
                var bestD = 44 * dp
                for ((i, p) in PORTS.withIndex()) {
                    val d = hypot(px(p.x) - event.x, py(p.y) - event.y)
                    if (d < bestD) { bestD = d; best = i }
                }
                if (best >= 0) {
                    selected = best
                    onSelect?.invoke(best)
                    performClick()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** Плавно двигает корабль от порта [from] к [to] на участке пути [f0]..[f1]. */
    fun animateVoyage(from: Int, to: Int, f0: Float, f1: Float, durationMs: Long,
                      onProgress: (Float) -> Unit, onEnd: () -> Unit) {
        val a = PORTS[from]
        val b = PORTS[to]
        animator?.cancel()
        animator = ValueAnimator.ofFloat(f0, f1).apply {
            duration = durationMs.coerceAtLeast(1)
            interpolator = LinearInterpolator()
            addUpdateListener {
                val f = it.animatedValue as Float
                ship = PointF(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
                onProgress(f)
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!cancelled) onEnd()
                }
            })
            start()
        }
    }

    fun stopVoyage() {
        animator?.cancel()
        animator = null
        ship = null
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
