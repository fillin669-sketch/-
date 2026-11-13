package com.seatrader.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.badlogic.gdx.utils.Align
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Отрисовка 3D-модели корабля в текстуру для меню верфи. */
object PreviewRenderer {
    private var fbo: FrameBuffer? = null
    private var batch: ModelBatch? = null
    private val env = Environment().also {
        it.set(ColorAttribute(ColorAttribute.AmbientLight, 0.55f, 0.55f, 0.6f, 1f))
        it.add(DirectionalLight().set(0.85f, 0.8f, 0.7f, -0.5f, -0.8f, -0.35f))
        it.add(DirectionalLight().set(0.2f, 0.25f, 0.35f, 0.6f, -0.2f, 0.5f))
    }
    private val cam = PerspectiveCamera(32f, 1f, 1f)

    fun render(inst: ModelInstance, look: Look, angle: Float, w: Int, h: Int): com.badlogic.gdx.graphics.Texture {
        var f = fbo
        if (f == null || f.width != w || f.height != h) {
            f?.dispose()
            f = FrameBuffer(Pixmap.Format.RGBA8888, w, h, true)
            fbo = f
        }
        val mb = batch ?: ModelBatch().also { batch = it }
        val L = look.len
        val mastH = L * 0.62f + 3
        val dist = L * 2.1f + 16f
        val cy = mastH * 0.3f
        cam.viewportWidth = w.toFloat(); cam.viewportHeight = h.toFloat()
        cam.position.set(sin(angle) * dist, cy + dist * 0.22f, cos(angle) * dist)
        cam.up.set(0f, 1f, 0f)
        cam.lookAt(0f, cy, 0f)
        cam.near = 1f; cam.far = 300f
        cam.update()
        inst.transform.idt()
        f.begin()
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        mb.begin(cam)
        mb.render(inst, env)
        mb.end()
        f.end()
        return f.colorBufferTexture
    }
}

/** Вращающийся корабль на стапеле. Можно крутить пальцем. */
class ShipPreview(private val g: SeaGame, private val inst: ModelInstance, private val look: Look) : Actor() {
    private var angle = 1.0f
    private var dragV = 0f
    private var dragging = false

    init {
        addListener(object : DragListener() {
            override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                dragging = true
                angle -= deltaX * 0.012f
            }
            override fun dragStop(event: InputEvent?, x: Float, y: Float, pointer: Int) { dragging = false }
        })
        dragV = 0f
    }

    override fun act(delta: Float) {
        super.act(delta)
        if (!dragging) angle += delta * 0.35f
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        val ui = g.ui
        // фон: небо, вода в доке, стапель
        ui.round(batch, x, y, width, height, rgb(0x0b2238))
        ui.rect(batch, x + 6, y + height * 0.35f, width - 12, height * 0.62f, rgb(0x5d87a8))
        ui.rect(batch, x + 6, y + height * 0.62f, width - 12, height * 0.35f, rgb(0x86b3d1))
        ui.rect(batch, x + 6, y + 6, width - 12, height * 0.31f, rgb(0x2c5d7c))
        ui.rect(batch, x + 6, y + height * 0.33f, width - 12, 5f, rgb(0x6d4c2f))
        for (k in 0 until 6) ui.rect(batch, x + 20 + k * (width - 40) / 5f - 4, y + 6, 8f, height * 0.3f, rgb(0x4e342e))
        val sc = g.scale
        val wPx = max(64, (width * sc).toInt()); val hPx = max(64, (height * sc).toInt())
        batch.end()
        val tex = PreviewRenderer.render(inst, look, angle, wPx, hPx)
        batch.begin()
        batch.color = Color.WHITE
        batch.draw(tex, x, y, width, height, 0, 0, wPx, hPx, false, true)
        ui.text(batch, ui.small, "↺ проведите пальцем, чтобы повернуть", x, y + 22, Align.center, width, c = rgb(0xe3f2fd, 0.85f))
    }
}

/** Торговая лавка: навес, прилавок, товар и ценник. */
class Stall(private val ui: Ui, private val gi: Int, private val buy: Int, private val sell: Int, private val tag: String, private val tagCol: Int, private val held: Int) : Actor() {
    private val awnings = listOf(0xc62828 to 0xf5f0dc, 0x6a1b9a to 0xffe082, 0x1565c0 to 0xf5f5f5, 0xef6c00 to 0xfff3e0, 0xad1457 to 0xf8bbd0, 0x37474f to 0xffcc80)
    private val c = Color()
    private fun col(h: Int, a: Float = 1f) = c.set(rgb(h, a))

    override fun draw(batch: Batch, parentAlpha: Float) {
        val w = width; val h = height
        // стена лавки
        ui.round(batch, x, y, w, h, col(0x3e2a1a))
        ui.rect(batch, x + 6, y + 30, w - 12, h - 60, col(0x6d4c2f))
        for (k in 0 until 5) ui.rect(batch, x + 6, y + 34 + k * (h - 64) / 5f, w - 12, 2f, col(0x5d4037))
        // продавец за прилавком
        val sx = x + w * 0.62f
        ui.icon(batch, ui.disc, sx, y + 60, 34f, 30f, 0f, col(0x8d6e63))
        ui.icon(batch, ui.disc, sx, y + 82, 20f, 22f, 0f, col(0xe0ac80))
        ui.icon(batch, ui.disc, sx, y + 92, 24f, 10f, 0f, col(awnings[gi % awnings.size].first))
        // столбы
        ui.rect(batch, x + 8, y + 22, 7f, h - 46, col(0x4e342e))
        ui.rect(batch, x + w - 15, y + 22, 7f, h - 46, col(0x4e342e))
        // навес в полоску с фестонами
        val (c1, c2) = awnings[gi % awnings.size]
        val n = 9
        val sw = (w - 8) / n
        val ay = y + h - 30
        for (k in 0 until n) ui.rect(batch, x + 4 + k * sw, ay, sw + 0.5f, 26f, col(if (k % 2 == 0) c1 else c2))
        for (k in 0 until n) ui.icon(batch, ui.disc, x + 4 + k * sw + sw / 2, ay, sw, 14f, 0f, col(if (k % 2 == 0) c1 else c2))
        ui.rect(batch, x + 4, y + h - 6, w - 8, 3f, col(0x3e2723))
        // прилавок
        ui.rect(batch, x + 4, y + 4, w - 8, 30f, col(0x8d6e4a))
        ui.rect(batch, x + 4, y + 30, w - 8, 5f, col(0xa1887f))
        // товар на прилавке
        drawGoods(batch, x + 22, y + 34)
        // вывеска с названием
        val name = GOODS[gi].name
        val nw = ui.measure(ui.mid, name) + 20
        ui.round(batch, x + 22 - 4, ay - 30, nw, 26f, col(0xf3e5c4))
        ui.text(batch, ui.mid, name, x + 18, ay - 9, Align.center, nw, c = col(0x3e2723))
        // ценник
        val tw0 = 128f
        val tx = x + w - tw0 - 18; val ty = y + 40
        ui.rect(batch, tx + tw0 / 2, ty + 56, 2f, 16f, col(0x3e2723))
        ui.round(batch, tx, ty, tw0, 58f, col(0xf3e5c4))
        ui.text(batch, ui.small, "купить", tx + 8, ty + 50, Align.left, tw0 - 16, c = col(0x5d4037))
        ui.text(batch, ui.mid, "$buy", tx + 8, ty + 52, Align.right, tw0 - 16, c = col(0x1b5e20))
        ui.text(batch, ui.small, "продать", tx + 8, ty + 25, Align.left, tw0 - 16, c = col(0x5d4037))
        ui.text(batch, ui.mid, "$sell", tx + 8, ty + 27, Align.right, tw0 - 16, c = col(0xb71c1c))
        if (tag.isNotEmpty()) {
            val tw = ui.measure(ui.small, tag) + 12
            ui.round(batch, tx + tw0 - tw, ty - 20, tw, 18f, col(tagCol))
            ui.text(batch, ui.small, tag, tx + tw0 - tw, ty - 5, Align.center, tw, c = col(0x1b1b1b))
        }
        ui.text(batch, ui.small, "в трюме: $held", x + 12, y + 22, Align.left, 160f, c = col(0xfff8e1))
        batch.color = Color.WHITE
    }

    private fun drawGoods(b: Batch, gx: Float, gy: Float) {
        when (gi) {
            0 -> { // мешки зерна
                for (k in 0..2) {
                    val cx = gx + 14 + k * 26f
                    ui.icon(b, ui.disc, cx, gy + 14, 26f, 30f, 0f, col(0xd7b98a))
                    ui.icon(b, ui.disc, cx, gy + 30, 10f, 8f, 0f, col(0xa1887f))
                    ui.icon(b, ui.disc, cx, gy + 30, 12f, 6f, 0f, col(0xf9e79f))
                }
            }
            1 -> { // бочка рома и бутылки
                ui.icon(b, ui.disc, gx + 20, gy + 18, 34f, 36f, 0f, col(0x6d4c2f))
                ui.rect(b, gx + 4, gy + 9, 32f, 3f, col(0x37474f)); ui.rect(b, gx + 4, gy + 26, 32f, 3f, col(0x37474f))
                for (k in 0..2) {
                    ui.round(b, gx + 44 + k * 13f, gy, 10f, 26f, col(0x1b5e20))
                    ui.rect(b, gx + 47 + k * 13f, gy + 26, 4f, 7f, col(0x1b5e20))
                }
            }
            2 -> { // рулоны ткани
                val cs = listOf(0xc62828, 0x1565c0, 0xf9a825, 0x2e7d32)
                for (k in 0..3) {
                    val row = k / 2; val i = k % 2
                    ui.round(b, gx + i * 34f + row * 16f, gy + row * 16f, 34f, 16f, col(cs[k]))
                    ui.icon(b, ui.disc, gx + i * 34f + row * 16f + 34f, gy + row * 16f + 8f, 10f, 16f, 0f, col(cs[k]))
                }
            }
            3 -> { // специи в мисках
                val cs = listOf(0xe65100, 0xb71c1c, 0xfbc02d)
                for (k in 0..2) {
                    val cx = gx + 14 + k * 28f
                    ui.icon(b, ui.disc, cx, gy + 14, 22f, 18f, 0f, col(cs[k]))
                    ui.rect(b, cx - 13, gy, 26f, 12f, col(0x8d6e4a))
                }
            }
            4 -> { // шёлк
                val cs = listOf(0x8e24aa, 0xec407a, 0x26c6da)
                for (k in 0..2) {
                    ui.round(b, gx + k * 22f, gy, 18f, 40f - k * 6f, col(cs[k]))
                    ui.rect(b, gx + k * 22f + 4, gy + 6, 3f, 26f - k * 6f, col(0xffffff, 0.4f))
                }
            }
            else -> { // порох
                for (k in 0..1) {
                    ui.icon(b, ui.disc, gx + 16 + k * 32f, gy + 16, 28f, 32f, 0f, col(0x4e342e))
                    ui.rect(b, gx + 3 + k * 32f, gy + 14, 26f, 4f, col(0xc62828))
                }
                for (k in 0..2) ui.icon(b, ui.disc, gx + 74 + k * 9f, gy + 5, 9f, 9f, 0f, col(0x212121))
            }
        }
    }
}

/** Портрет офицера как элемент интерфейса. */
class PortraitActor(private val ui: Ui, private val o: Officer) : Actor() {
    override fun draw(batch: Batch, parentAlpha: Float) = Portraits.draw(batch, ui, o, x, y, min(width, height))
}

/** Шкала репутации от -100 до 100. */
class RepBar(private val ui: Ui, private val v: Int) : Actor() {
    override fun draw(batch: Batch, parentAlpha: Float) {
        val cy = y + height / 2
        ui.pill(batch, x, cy - 5, width, 10f, rgb(0x0b2238))
        val mid = x + width / 2
        val f = v / 100f
        val col = if (v >= 0) rgb(0x3ddc84) else rgb(0xff5a5f)
        if (v > 0) ui.pill(batch, mid, cy - 5, max(10f, width / 2 * f), 10f, col)
        if (v < 0) { val w = max(10f, width / 2 * -f); ui.pill(batch, mid - w, cy - 5, w, 10f, col) }
        ui.rect(batch, mid - 1, cy - 8, 2f, 16f, rgb(0xffffff, 0.7f))
        batch.color = Color.WHITE
    }
}
