package com.seatrader.game

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g3d.decals.CameraGroupStrategy
import com.badlogic.gdx.graphics.g3d.decals.Decal
import com.badlogic.gdx.graphics.g3d.decals.DecalBatch
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import kotlin.random.Random

/** Частицы и «одноразовые» спрайты в 3D на декалях: всё рисуется пачкой. */
class Fx(
    private val cam: Camera,
    private val disc: TextureRegion,
    private val square: TextureRegion,
    private val streak: TextureRegion,
    private val gull: TextureRegion,
) : Disposable {
    enum class Mode { SHRINK, PUFF, FOAM, STREAK }

    private class P {
        lateinit var decal: Decal
        var x = 0f; var y = 0f; var z = 0f
        var vx = 0f; var vy = 0f; var vz = 0f
        var size = 1f; var life = 1f; var max = 1f; var g = 0f
        var mode = Mode.SHRINK
        var flat = false
        var alpha = 1f
    }

    private val batch = DecalBatch(CameraGroupStrategy(cam))
    private val list = ArrayList<P>()
    private val pool = ArrayList<P>()
    private val frameDecals = ArrayList<Decal>()
    private var frameCount = 0
    var time = 0f
    var limit = 900

    val FOAM = Color(1f, 1f, 1f, 0.55f)
    val SPLASH = Color(0.92f, 0.97f, 1f, 0.9f)
    val SMOKE = Color(0.75f, 0.75f, 0.75f, 0.7f)
    val TRAIL = Color(0.85f, 0.85f, 0.85f, 0.45f)
    val DARK = Color(0.23f, 0.23f, 0.23f, 0.8f)
    val FIRE = Color(1f, 0.54f, 0.12f, 1f)
    val FIRE2 = Color(1f, 0.82f, 0.29f, 1f)
    val WOOD = Color(0.42f, 0.27f, 0.14f, 1f)
    val DUST = Color(0.72f, 0.62f, 0.45f, 0.8f)
    val RAIN = Color(0.8f, 0.88f, 1f, 0.45f)
    val CHAIN = Color(0.55f, 0.6f, 0.65f, 1f)

    fun spawn(c: Color, x: Float, y: Float, z: Float, vx: Float, vy: Float, vz: Float, size: Float, life: Float,
              mode: Mode = Mode.SHRINK, g: Float = 0f, flat: Boolean = false, sq: Boolean = false) {
        if (list.size > limit) return
        val p = if (pool.isEmpty()) P().also { it.decal = Decal.newDecal(1f, 1f, disc, true) } else pool.removeAt(pool.size - 1)
        p.decal.textureRegion = when { mode == Mode.STREAK -> streak; sq -> square; else -> disc }
        p.decal.setColor(c.r, c.g, c.b, c.a)
        p.alpha = c.a
        p.x = x; p.y = y; p.z = z; p.vx = vx; p.vy = vy; p.vz = vz
        p.size = size; p.life = life; p.max = life; p.g = g; p.mode = mode; p.flat = flat
        list.add(p)
    }

    fun update(dt: Float) {
        var i = 0
        while (i < list.size) {
            val p = list[i]
            p.life -= dt
            p.vy -= p.g * dt
            p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt
            if (p.life <= 0 || ((p.g > 0 || p.mode == Mode.STREAK) && p.y < 0)) {
                list[i] = list[list.size - 1]
                list.removeAt(list.size - 1)
                pool.add(p)
                continue
            }
            i++
        }
    }

    private val q = Quaternion()
    private val q2 = Quaternion()

    /** Спрайт только на этот кадр. flat — лежит на воде с поворотом yawDeg. */
    fun frame(region: TextureRegion, x: Float, y: Float, z: Float, w: Float, h: Float, c: Color,
              flat: Boolean = false, yawDeg: Float = 0f) {
        if (frameCount >= frameDecals.size) frameDecals.add(Decal.newDecal(1f, 1f, region, true))
        val d = frameDecals[frameCount++]
        d.textureRegion = region
        d.setPosition(x, y, z)
        d.setDimensions(w, h)
        d.setColor(c.r, c.g, c.b, c.a)
        if (flat) {
            q.set(Vector3.Y, yawDeg); q2.set(Vector3.X, -90f); q.mul(q2)
            d.setRotation(q)
        } else d.lookAt(cam.position, cam.up)
        batch.add(d)
    }

    private val ballColor = Color(0.08f, 0.08f, 0.08f, 1f)
    fun ball(x: Float, y: Float, z: Float, c: Color = ballColor) = frame(disc, x, y, z, 1.1f, 1.1f, c)

    fun glow(x: Float, y: Float, z: Float, size: Float, c: Color) = frame(disc, x, y, z, size, size, c)

    fun shadow(x: Float, z: Float, w: Float, l: Float, yawDeg: Float, a: Float) =
        frame(disc, x, waveHeight(x, z, time) + 0.45f, z, w, l, shadowColor.set(0f, 0.05f, 0.12f, a), true, yawDeg)

    private val shadowColor = Color()
    private val gullColor = Color(1f, 1f, 1f, 0.95f)
    fun gull(x: Float, y: Float, z: Float, flap: Float) = frame(gull, x, y, z, 2.6f, 1.3f * (0.5f + 0.5f * flap), gullColor)

    fun render() {
        for (p in list) {
            val f = p.life / p.max
            val s = when (p.mode) {
                Mode.PUFF -> p.size * (0.5f + 1.6f * (1 - f)) * minOf(1f, f * 3)
                Mode.FOAM -> p.size * (1 + 1.8f * (1 - f)) * minOf(1f, f * 2)
                Mode.SHRINK -> p.size * f
                Mode.STREAK -> p.size
            }
            val d = p.decal
            if (p.mode == Mode.STREAK) d.setDimensions(s * 0.12f, s * 2.2f) else d.setDimensions(s * 2, s * 2)
            if (p.flat) {
                d.setPosition(p.x, waveHeight(p.x, p.z, time) + 0.55f, p.z)
                d.setRotationX(-90f)
                d.color.a = p.alpha * minOf(1f, f * 1.5f)
                d.setColor(d.color)
            } else {
                d.setPosition(p.x, p.y, p.z)
                d.lookAt(cam.position, cam.up)
            }
            batch.add(d)
        }
        batch.flush()
        frameCount = 0
    }

    // ---------- Готовые эффекты ----------
    private fun r(a: Float, b: Float) = a + Random.nextFloat() * (b - a)

    fun splash(x: Float, z: Float, big: Float = 1f) {
        repeat((7 * big).toInt().coerceAtLeast(2)) {
            spawn(SPLASH, x + r(-1f, 1f), 0.5f, z + r(-1f, 1f), r(-3f, 3f), r(7f, 13f) * big, r(-3f, 3f), r(0.4f, 0.8f) * big, r(0.6f, 0.9f), Mode.SHRINK, 26f)
        }
        spawn(FOAM, x, 0.7f, z, 0f, 0f, 0f, 1.8f * big, 1.1f, Mode.FOAM, flat = true)
    }

    fun dust(x: Float, y: Float, z: Float) {
        repeat(6) { spawn(DUST, x + r(-1f, 1f), y, z + r(-1f, 1f), r(-3f, 3f), r(2f, 6f), r(-3f, 3f), r(0.8f, 1.5f), r(0.6f, 1f), Mode.PUFF) }
        repeat(4) { spawn(WOOD, x, y + 1, z, r(-6f, 6f), r(5f, 10f), r(-6f, 6f), r(0.25f, 0.45f), 1f, Mode.SHRINK, 25f, sq = true) }
    }

    fun explosion(x: Float, y: Float, z: Float, big: Float = 1f) {
        repeat((6 * big).toInt().coerceAtLeast(2)) { i ->
            spawn(if (i % 2 == 0) FIRE else FIRE2, x + r(-1.5f, 1.5f), y + r(0f, 2f), z + r(-1.5f, 1.5f), r(-2f, 2f), r(1f, 4f), r(-2f, 2f), r(1f, 2f) * big, r(0.3f, 0.55f), Mode.PUFF)
        }
        repeat((4 * big).toInt().coerceAtLeast(1)) {
            spawn(DARK, x + r(-1f, 1f), y + 1, z + r(-1f, 1f), r(-1f, 1f), r(3f, 6f), r(-1f, 1f), r(1.2f, 2f) * big, r(0.8f, 1.3f), Mode.PUFF)
        }
        repeat((5 * big).toInt().coerceAtLeast(2)) {
            spawn(WOOD, x, y + 1, z, r(-8f, 8f), r(6f, 14f), r(-8f, 8f), r(0.3f, 0.6f), r(0.8f, 1.3f), Mode.SHRINK, 25f, sq = true)
        }
    }

    fun muzzle(x: Float, y: Float, z: Float, dx: Float, dz: Float) {
        spawn(FIRE2, x, y, z, dx * 6, 0f, dz * 6, 1.1f, 0.14f)
        repeat(3) {
            spawn(SMOKE, x + dx * 1.5f, y + 0.2f, z + dz * 1.5f, dx * r(4f, 8f), r(0.5f, 2f), dz * r(4f, 8f), r(1f, 1.7f), r(0.8f, 1.3f), Mode.PUFF)
        }
    }

    fun rain(cx: Float, cz: Float, count: Int) {
        repeat(count) {
            spawn(RAIN, cx + r(-90f, 90f), r(40f, 90f), cz + r(-110f, 70f), -6f, -85f, 0f, 1.6f, 1.2f, Mode.STREAK)
        }
    }

    override fun dispose() = batch.dispose()
}
