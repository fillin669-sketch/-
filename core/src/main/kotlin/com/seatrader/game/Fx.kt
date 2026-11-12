package com.seatrader.game

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g3d.decals.CameraGroupStrategy
import com.badlogic.gdx.graphics.g3d.decals.Decal
import com.badlogic.gdx.graphics.g3d.decals.DecalBatch
import com.badlogic.gdx.utils.Disposable
import kotlin.random.Random

/** Частицы на декалях: всё рисуется одной пачкой. */
class Fx(private val cam: Camera, private val disc: TextureRegion, private val square: TextureRegion) : Disposable {
    enum class Mode { SHRINK, PUFF, FOAM }

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
    private val balls = ArrayList<Decal>()
    private var ballCount = 0
    var time = 0f

    val FOAM = Color(1f, 1f, 1f, 0.55f)
    val SPLASH = Color(0.92f, 0.97f, 1f, 0.9f)
    val SMOKE = Color(0.69f, 0.69f, 0.69f, 0.75f)
    val DARK = Color(0.23f, 0.23f, 0.23f, 0.8f)
    val FIRE = Color(1f, 0.54f, 0.12f, 1f)
    val FIRE2 = Color(1f, 0.82f, 0.29f, 1f)
    val WOOD = Color(0.42f, 0.27f, 0.14f, 1f)

    fun spawn(c: Color, x: Float, y: Float, z: Float, vx: Float, vy: Float, vz: Float, size: Float, life: Float,
              mode: Mode = Mode.SHRINK, g: Float = 0f, flat: Boolean = false, sq: Boolean = false) {
        if (list.size > 700) return
        val p = if (pool.isEmpty()) P().also { it.decal = Decal.newDecal(1f, 1f, disc, true) } else pool.removeAt(pool.size - 1)
        p.decal.textureRegion = if (sq) square else disc
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
            if (p.life <= 0 || (p.g > 0 && p.y < 0)) {
                list[i] = list[list.size - 1]
                list.removeAt(list.size - 1)
                pool.add(p)
                continue
            }
            i++
        }
    }

    /** Ядро в полёте: рисуется только в этом кадре. */
    fun ball(x: Float, y: Float, z: Float) {
        if (ballCount >= balls.size) balls.add(Decal.newDecal(1f, 1f, disc, true).also { it.setColor(0.08f, 0.08f, 0.08f, 1f) })
        val d = balls[ballCount++]
        d.setPosition(x, y, z)
        d.setDimensions(1.1f, 1.1f)
        d.lookAt(cam.position, cam.up)
        batch.add(d)
    }

    fun render() {
        for (p in list) {
            val f = p.life / p.max
            val s = when (p.mode) {
                Mode.PUFF -> p.size * (0.5f + 1.6f * (1 - f)) * minOf(1f, f * 3)
                Mode.FOAM -> p.size * (1 + 1.8f * (1 - f)) * minOf(1f, f * 2)
                Mode.SHRINK -> p.size * f
            }
            val d = p.decal
            d.setDimensions(s * 2, s * 2)
            if (p.flat) {
                d.setPosition(p.x, waveHeight(p.x, p.z, time) * 0.55f + 0.55f, p.z)
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
        ballCount = 0
    }

    // ---------- Готовые эффекты ----------
    private fun r(a: Float, b: Float) = a + Random.nextFloat() * (b - a)

    fun splash(x: Float, z: Float, big: Float = 1f) {
        repeat((7 * big).toInt()) {
            spawn(SPLASH, x + r(-1f, 1f), 0.5f, z + r(-1f, 1f), r(-3f, 3f), r(7f, 13f) * big, r(-3f, 3f), r(0.4f, 0.8f) * big, r(0.6f, 0.9f), Mode.SHRINK, 26f)
        }
        spawn(FOAM, x, 0.7f, z, 0f, 0f, 0f, 1.8f * big, 1.1f, Mode.FOAM, flat = true)
    }

    fun explosion(x: Float, y: Float, z: Float, big: Float = 1f) {
        repeat((6 * big).toInt()) { i ->
            spawn(if (i % 2 == 0) FIRE else FIRE2, x + r(-1.5f, 1.5f), y + r(0f, 2f), z + r(-1.5f, 1.5f), r(-2f, 2f), r(1f, 4f), r(-2f, 2f), r(1f, 2f) * big, r(0.3f, 0.55f), Mode.PUFF)
        }
        repeat((4 * big).toInt()) {
            spawn(DARK, x + r(-1f, 1f), y + 1, z + r(-1f, 1f), r(-1f, 1f), r(3f, 6f), r(-1f, 1f), r(1.2f, 2f) * big, r(0.8f, 1.3f), Mode.PUFF)
        }
        repeat((4 * big).toInt()) {
            spawn(WOOD, x, y + 1, z, r(-8f, 8f), r(6f, 14f), r(-8f, 8f), r(0.3f, 0.6f), r(0.8f, 1.3f), Mode.SHRINK, 25f, sq = true)
        }
    }

    fun muzzle(x: Float, z: Float, dx: Float, dz: Float) {
        spawn(FIRE2, x, 2.4f, z, dx * 4, 0f, dz * 4, 0.9f, 0.15f)
        repeat(2) {
            spawn(SMOKE, x + dx * 1.5f, 2.6f, z + dz * 1.5f, dx * r(3f, 6f), r(0.5f, 2f), dz * r(3f, 6f), r(1f, 1.6f), r(0.7f, 1.1f), Mode.PUFF)
        }
    }

    override fun dispose() = batch.dispose()
}
