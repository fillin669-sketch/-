package com.seatrader.game

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Порт как живая сцена: на берегу каждого порта стоят верфь, рынок, таверна,
 * арсенал, причал флота и парусная мастерская. В порту камера облетает остров
 * и показывает выбранное место, а меню — панель поверх сцены.
 */
class PortView(private val g: SeaGame) {
    /** Места: парусная, верфь, арсенал, рынок, таверна, причал флота. */
    private val kinds = listOf("paint", "yard", "arsenal", "market", "tavern", "fleet")
    private val angles = floatArrayOf(-112f, -72f, -36f, 32f, 66f, 100f)
    private class Spot(val port: Int, val kind: Int, val pos: Vector3, val angle: Float, val inst: ModelInstance)
    private val spots = ArrayList<Spot>()

    var active = false
        private set
    private var port = -1
    private var base = false
    private var station = 1
    private var variant = 0
    var orbit = 0f
    private val camP = Vector3()
    private val camL = Vector3()
    private val wantP = Vector3()
    private val wantL = Vector3()
    private var snap = false

    fun build(models: MutableList<Model>) {
        val mdl = kinds.map { k -> Models2.station(k).also { models += it } }
        for ((pi, p) in PORTS.withIndex()) for (k in kinds.indices) {
            val a = angles[k] * MathUtils.degreesToRadians
            val pos = Vector3(p.x + sin(a) * p.r * 1.0f, 0f, p.z + cos(a) * p.r * 1.0f)
            val inst = ModelInstance(mdl[k])
            inst.transform.setToTranslation(pos).rotate(Vector3.Y, angles[k])
            spots += Spot(pi, k, pos, a, inst)
        }
    }

    private fun spot(kind: String) = spots.first { it.port == port && it.kind == kinds.indexOf(kind) }

    /** Вход в порт: корабль встаёт в док верфи. */
    fun enter(i: Int) {
        port = i; base = false; active = true
        orbit = 0f
        startCam()
        select(0)
        moorShips()
    }

    fun enterBase() {
        port = -1; base = true; active = true
        orbit = 0f
        startCam()
        moorShips()
    }

    private fun startCam() {
        camP.set(g.cam.position)
        val p = g.player
        camL.set(p?.x ?: 0f, 0f, p?.z ?: 0f)
        snap = false
    }

    /** Выход в море: корабль у входного круга, носом от берега. */
    fun leave() {
        if (!active) return
        active = false
        val p = g.player ?: return
        if (base) place(p, g.living.ringX, g.living.ringZ, PI.toFloat())
        else if (port >= 0) { val pt = g.ports[port]; place(p, pt.rx, pt.rz, PI.toFloat()) }
        p.vx = 0f; p.vz = 0f; p.angVel = 0f
    }

    /** Вкладка меню → место на острове. */
    fun select(tab: Int) {
        if (base) { station = -1; variant = tab; return }
        station = when (tab) { 0, 1, 6 -> 1; 2 -> 2; 3 -> 3; 4 -> 5; 5 -> 4; else -> 1 }
        variant = tab
    }

    private fun place(s: Ship, x: Float, z: Float, h: Float) {
        s.x = x; s.z = z; s.h = h; s.desired = h
        s.vx = 0f; s.vz = 0f; s.angVel = 0f
    }

    private fun moorShips() {
        val p = g.player ?: return
        if (base) {
            val lv = g.living
            place(p, lv.baseX + 8f, lv.baseZ + lv.baseR + 8f + p.len * 0.3f, PI.toFloat())
        } else {
            val y = spot("yard")
            val dx = sin(y.angle); val dz = cos(y.angle)
            val d = 4f + p.len * 0.5f + 1f
            place(p, y.pos.x + dx * d, y.pos.z + dz * d, PI.toFloat() - y.angle)
        }
        var k = 0
        for (s in g.ships) {
            if (!s.ally || s.sinking) continue
            if (base) {
                val lv = g.living
                place(s, lv.baseX - 10f - k * 14f, lv.baseZ + lv.baseR + 14f, PI.toFloat())
            } else {
                val f = spot("fleet")
                val dx = sin(f.angle); val dz = cos(f.angle); val rx = cos(f.angle); val rz = -sin(f.angle)
                val side = if (k % 2 == 0) 1f else -1f
                val along = 10f + s.len * 0.5f + (k / 2) * (s.len + 4f)
                val off = side * (2.4f + s.width * 0.7f)
                place(s, f.pos.x + dx * along + rx * off, f.pos.z + dz * along + rz * off, PI.toFloat() - f.angle)
            }
            k++
        }
        for (s in g.ships) if (s.ally || s === p) for (n in s.sails) n.scale.set(1f, 1f, 0.6f)
        for (s in g.ships) if (s.ally || s === p) s.inst.calculateTransforms()
    }

    /** Покачивание кораблей у причала и полёт камеры. */
    fun update(dt: Float) {
        if (!active) return
        val p = g.player
        // корабль мог быть пересоздан (покупка, улучшение) — снова ставим в док
        if (p != null && !base) {
            val y = spot("yard")
            val d = 4f + p.len * 0.5f + 1f
            val tx = y.pos.x + sin(y.angle) * d; val tz = y.pos.z + cos(y.angle) * d
            if (hypot(p.x - tx, p.z - tz) > 0.5f) moorShips()
        }
        for (s in g.ships) {
            if (!(s.ally || s === p) || s.sinking) continue
            val t = g.now
            s.y = waveHeight(s.x, s.z, t) * 0.5f - 0.1f
            val roll = sin(t * 1.3f + s.id) * 0.03f
            val pitch = sin(t * 0.9f + s.id * 2) * 0.015f
            s.inst.transform.setToTranslation(s.x, s.y, s.z)
                .rotate(Vector3.Y, -s.h * MathUtils.radiansToDegrees)
                .rotate(Vector3.X, pitch * MathUtils.radiansToDegrees)
                .rotate(Vector3.Z, roll * MathUtils.radiansToDegrees)
        }
        computeTarget()
        val k = 1 - exp(-dt * 2.6f)
        camP.lerp(wantP, k); camL.lerp(wantL, k)
    }

    private val focus = Vector3()

    private fun computeTarget() {
        val p = g.player
        var dist: Float
        var el = 0.42f
        var side = 0.62f
        if (base) {
            val lv = g.living
            focus.set(lv.baseX, 6f, lv.baseZ + 6f)
            dist = 100f; el = 0.55f
            side = 0.5f + g.now * 0.04f
            val dirA = side + orbit
            setCam(dist, el, sin(dirA), cos(dirA))
            return
        }
        val sp = spots.first { it.port == port && it.kind == station }
        val dx = sin(sp.angle); val dz = cos(sp.angle)
        when (kinds[station]) {
            "yard" -> {
                val L = p?.len ?: 12f
                val mastH = L * 0.62f + 3
                focus.set(p?.x ?: sp.pos.x, 1.5f + mastH * (if (variant == 6) 0.42f else 0.3f), p?.z ?: sp.pos.z)
                dist = 22f + L * 1.75f
                side = 1.1f; el = 0.36f
                if (variant == 6) { side = 0.75f; el = 0.28f }
                if (variant == 0) { side = 1.35f; el = 0.42f }
            }
            "fleet" -> { focus.set(sp.pos.x + dx * 20f, 3f, sp.pos.z + dz * 20f); dist = 58f; side = 0.9f; el = 0.5f }
            "market" -> { focus.set(sp.pos.x + dx * 3f, 4.5f, sp.pos.z + dz * 3f); dist = 36f; side = 0.15f; el = 0.36f }
            else -> { focus.set(sp.pos.x + dx * 2f, 5f, sp.pos.z + dz * 2f); dist = 34f; side = 0.35f; el = 0.36f }
        }
        val a = sp.angle + side + orbit
        setCam(dist, el, sin(a), cos(a))
    }

    private fun setCam(dist: Float, el: Float, hx: Float, hz: Float) {
        wantP.set(focus.x + hx * dist * cos(el), focus.y + dist * sin(el), focus.z + hz * dist * cos(el))
        // объект — в свободной от панели части экрана
        val landscape = g.wDp > g.hDp
        wantL.set(focus)
        if (landscape) {
            // справа от объекта: объект смещается влево
            wantL.add(hz * dist * 0.38f, -dist * 0.08f, -hx * dist * 0.38f)
        } else wantL.add(0f, -dist * 0.24f, 0f)
    }

    fun applyCamera(cam: Camera) {
        if (!snap) { snap = true }
        cam.position.set(camP)
        cam.up.set(0f, 1f, 0f)
        cam.lookAt(camL)
        cam.update()
    }

    fun render(batch: ModelBatch, env: Environment, lookX: Float, lookZ: Float) {
        for (s in spots) if (hypot(s.pos.x - lookX, s.pos.z - lookZ) < 520) batch.render(s.inst, env)
    }

    fun renderShadows(batch: ModelBatch, lookX: Float, lookZ: Float) {
        for (s in spots) if (hypot(s.pos.x - lookX, s.pos.z - lookZ) < 240) batch.render(s.inst)
    }

    /** Огни фонарей ночью. */
    fun lanterns(out: (Float, Float, Float) -> Unit, lookX: Float, lookZ: Float) {
        for (s in spots) {
            if (hypot(s.pos.x - lookX, s.pos.z - lookZ) > 450) continue
            val rx = cos(s.angle); val rz = -sin(s.angle); val dx = sin(s.angle); val dz = cos(s.angle)
            val pts = when (kinds[s.kind]) {
                "market" -> listOf(-13.3f to 1f, 13.3f to 1f, 0f to 8.4f)
                "tavern" -> listOf(-8.4f to 8.3f, 8.4f to 8.3f)
                "yard" -> listOf(6f to 2.5f)
                "fleet" -> listOf(1.6f to 41f, -1.6f to 20f)
                else -> emptyList()
            }
            for ((lx, lz) in pts) out(s.pos.x + rx * lx + dx * lz, 5.6f, s.pos.z + rz * lx + dz * lz)
        }
    }
}
