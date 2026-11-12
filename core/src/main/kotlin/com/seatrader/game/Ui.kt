package com.seatrader.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

fun rgb(hex: Int, a: Float = 1f) = Color(((hex shl 8) or 0xff)).also { it.a = a }

/** Шрифты, текстуры и стили интерфейса. Все размеры — в dp. */
class Ui(val scale: Float) : Disposable {
    private val textures = ArrayList<Texture>()

    val small: BitmapFont
    val mid: BitmapFont
    val big: BitmapFont
    val huge: BitmapFont

    val white: TextureRegion
    val disc: TextureRegion
    val softDisc: TextureRegion
    val ring: TextureRegion
    val coin: TextureRegion
    val gem: TextureRegion
    val triangle: TextureRegion
    val sails: TextureRegion
    val vignette: TextureRegion
    val dark: TextureRegion
    val streak: TextureRegion
    val gull: TextureRegion
    val fireIcon: TextureRegion
    val chainIcon: TextureRegion
    val repairIcon: TextureRegion
    val arrowIcon: TextureRegion
    val xmark: TextureRegion
    val hand: TextureRegion
    val mortarIcon: TextureRegion
    val rocketIcon: TextureRegion
    val mineIcon: TextureRegion
    val aimIcon: TextureRegion
    val swordsIcon: TextureRegion
    val squareIcon: TextureRegion
    val cloud: TextureRegion
    val cloud2: TextureRegion
    val roundPatch: NinePatch
    val pillPatch: NinePatch

    init {
        val gen = FreeTypeFontGenerator(Gdx.files.internal("fonts/DejaVuSans-Bold.ttf"))
        val chars = FreeTypeFontGenerator.DEFAULT_CHARS +
            "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя«»—–…№·×☠⚓⚔★☆✓▶↺⚙❤⬆✕♪♫"
        fun font(dp: Float, border: Float): BitmapFont {
            val p = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
                size = (dp * scale).toInt()
                characters = chars
                borderWidth = border * scale
                borderColor = rgb(0x1b2a3a)
                shadowOffsetY = (1.5f * scale).toInt()
                shadowColor = Color(0f, 0f, 0f, 0.35f)
                minFilter = Texture.TextureFilter.Linear
                magFilter = Texture.TextureFilter.Linear
                kerning = true
            }
            return gen.generateFont(p).also {
                it.data.setScale(1f / scale)
                it.setUseIntegerPositions(false)
            }
        }
        small = font(13f, 1f)
        mid = font(16f, 1.3f)
        big = font(22f, 1.8f)
        huge = font(28f, 2.2f)
        gen.dispose()

        white = region(pix(4, 4) { it.setColor(Color.WHITE); it.fill() })
        disc = region(pix(128, 128) { it.setColor(Color.WHITE); it.fillCircle(64, 64, 62) })
        softDisc = region(pix(64, 64) { p ->
            for (y in 0 until 64) for (x in 0 until 64) {
                val d = hypot(x - 31.5f, y - 31.5f) / 31.5f
                if (d < 1) { p.setColor(1f, 1f, 1f, (1 - d * d).coerceIn(0f, 1f)); p.drawPixel(x, y) }
            }
        })
        ring = region(pix(256, 256) { p ->
            for (y in 0 until 256) for (x in 0 until 256) {
                val d = hypot(x - 127.5f, y - 127.5f)
                val a = (1f - Math.abs(d - 121f) / 5f).coerceIn(0f, 1f)
                if (a > 0) { p.setColor(1f, 1f, 1f, a); p.drawPixel(x, y) }
            }
        })
        coin = region(pix(64, 64) { p ->
            p.setColor(rgb(0xc47f00)); p.fillCircle(32, 32, 31)
            p.setColor(rgb(0xf5a400)); p.fillCircle(32, 32, 27)
            p.setColor(rgb(0xffd23f)); p.fillCircle(30, 29, 21)
            p.setColor(rgb(0xfff3b0)); p.fillCircle(24, 22, 6)
        })
        gem = region(pix(64, 56) { p ->
            p.setColor(rgb(0x9c27b0)); p.fillTriangle(0, 18, 64, 18, 32, 56)
            p.setColor(rgb(0xe040fb)); p.fillTriangle(12, 0, 52, 0, 64, 18); p.fillTriangle(12, 0, 0, 18, 64, 18)
            p.setColor(rgb(0xffb3ff)); p.fillTriangle(16, 4, 30, 4, 22, 16)
            p.setColor(rgb(0xc158dc)); p.fillTriangle(16, 18, 48, 18, 32, 52)
        })
        triangle = region(pix(64, 64) { p -> p.setColor(Color.WHITE); p.fillTriangle(32, 0, 0, 63, 63, 63) })
        sails = region(pix(100, 100) { p ->
            p.setColor(Color.WHITE)
            p.fillTriangle(18, 78, 40, 28, 46, 70)
            p.fillTriangle(40, 72, 60, 24, 66, 66)
            p.fillTriangle(62, 64, 80, 22, 86, 60)
        })
        vignette = region(pix(64, 64) { p ->
            for (y in 0 until 64) for (x in 0 until 64) {
                val dx = Math.abs(x - 31.5f) / 31.5f; val dy = Math.abs(y - 31.5f) / 31.5f
                val d = maxOf(dx, dy)
                val a = ((d - 0.7f) / 0.3f).coerceIn(0f, 1f); p.setColor(1f, 0f, 0f, a * a * 0.6f); p.drawPixel(x, y)
            }
        })
        dark = region(pix(64, 64) { p ->
            for (y in 0 until 64) for (x in 0 until 64) {
                val d = hypot(x - 31.5f, y - 31.5f) / 31.5f
                val a = ((d - 0.6f) / 0.6f).coerceIn(0f, 1f)
                p.setColor(0f, 0.02f, 0.06f, a * a * 0.55f); p.drawPixel(x, y)
            }
        })
        streak = region(pix(8, 64) { p ->
            for (y in 0 until 64) { p.setColor(1f, 1f, 1f, y / 64f); p.drawLine(3, y, 4, y) }
        })
        gull = region(pix(64, 32) { p ->
            p.setColor(Color.WHITE)
            for (k in -2..2) {
                p.drawLine(2, 6 + k, 22, 18 + k); p.drawLine(22, 18 + k, 32, 12 + k)
                p.drawLine(32, 12 + k, 42, 18 + k); p.drawLine(42, 18 + k, 62, 6 + k)
            }
            p.setColor(rgb(0x37474f)); p.fillCircle(32, 14, 3)
        })
        fireIcon = region(pix(64, 64) { p ->
            p.setColor(rgb(0xff6d00)); p.fillCircle(32, 40, 18); p.fillTriangle(14, 40, 50, 40, 32, 2)
            p.setColor(rgb(0xffc400)); p.fillCircle(32, 44, 11); p.fillTriangle(21, 44, 43, 44, 32, 16)
            p.setColor(rgb(0xfff59d)); p.fillCircle(32, 48, 5)
        })
        chainIcon = region(pix(64, 64) { p ->
            p.setColor(rgb(0x263238)); p.fillCircle(14, 32, 12); p.fillCircle(50, 32, 12)
            p.setColor(rgb(0xb0bec5))
            for (k in 0..4) { val x = 24 + k * 4; p.drawCircle(x, 32 + if (k % 2 == 0) -2 else 2, 4) }
            p.setColor(rgb(0x546e7a)); p.fillCircle(11, 29, 4); p.fillCircle(47, 29, 4)
        })
        repairIcon = region(pix(64, 64) { p ->
            p.setColor(rgb(0x8d6e63))
            for (k in -3..3) p.drawLine(14 + k, 54, 42 + k, 18)
            p.setColor(rgb(0xb0bec5)); p.fillRectangle(30, 8, 24, 14); p.fillTriangle(30, 8, 30, 22, 22, 15)
            p.setColor(rgb(0x66bb6a)); p.fillRectangle(42, 38, 6, 20); p.fillRectangle(35, 45, 20, 6)
        })
        arrowIcon = region(pix(32, 32) { p -> p.setColor(Color.WHITE); p.fillTriangle(16, 0, 4, 31, 28, 31) })
        xmark = region(pix(32, 32) { p ->
            p.setColor(rgb(0xe53935)); for (k in -3..3) { p.drawLine(4 + k, 4, 28 + k, 28); p.drawLine(28 + k, 4, 4 + k, 28) }
        })
        hand = region(pix(64, 64) { p ->
            p.setColor(Color.WHITE); p.fillCircle(30, 40, 16); p.fillRectangle(24, 6, 10, 34); p.fillCircle(29, 8, 5)
            p.setColor(rgb(0x1b2a3a)); p.drawCircle(30, 40, 16)
        })
        mortarIcon = region(pix(64, 64) { p ->
            p.setColor(rgb(0x37474f)); p.fillRectangle(14, 36, 36, 20); p.fillCircle(32, 36, 16)
            p.setColor(rgb(0x212121)); p.fillCircle(32, 30, 9)
            p.setColor(rgb(0xffc400)); p.fillCircle(44, 12, 5); p.setColor(rgb(0xff6d00)); p.fillCircle(44, 12, 2)
            p.setColor(rgb(0x8d6e63)); p.drawLine(36, 24, 44, 14)
        })
        rocketIcon = region(pix(64, 64) { p ->
            p.setColor(rgb(0xd32f2f)); p.fillTriangle(32, 4, 22, 22, 42, 22)
            p.setColor(rgb(0xeeeeee)); p.fillRectangle(24, 22, 16, 22)
            p.setColor(rgb(0xd32f2f)); p.fillTriangle(24, 36, 14, 50, 24, 46); p.fillTriangle(40, 36, 50, 50, 40, 46)
            p.setColor(rgb(0xffa000)); p.fillTriangle(26, 44, 38, 44, 32, 62)
        })
        mineIcon = region(pix(64, 64) { p ->
            p.setColor(rgb(0x90a4ae)); p.fillRectangle(29, 4, 6, 56); p.fillRectangle(4, 29, 56, 6)
            p.setColor(rgb(0x263238)); p.fillCircle(32, 32, 17)
            p.setColor(rgb(0xe53935)); p.fillCircle(32, 32, 5)
        })
        aimIcon = region(pix(64, 64) { p ->
            p.setColor(Color.WHITE)
            for (k in 0..2) p.drawCircle(32, 32, 20 + k)
            p.fillRectangle(30, 2, 4, 18); p.fillRectangle(30, 44, 4, 18); p.fillRectangle(2, 30, 18, 4); p.fillRectangle(44, 30, 18, 4)
            p.setColor(rgb(0xff5252)); p.fillCircle(32, 32, 4)
        })
        swordsIcon = region(pix(64, 64) { p ->
            p.setColor(rgb(0xcfd8dc))
            for (k in -2..2) { p.drawLine(10 + k, 8, 50 + k, 48); p.drawLine(54 + k, 8, 14 + k, 48) }
            p.setColor(rgb(0x8d6e63)); p.fillCircle(52, 52, 6); p.fillCircle(12, 52, 6)
            p.setColor(rgb(0xffc93c)); p.fillRectangle(40, 40, 12, 4); p.fillRectangle(12, 40, 12, 4)
        })
        squareIcon = region(pix(16, 16) { p -> p.setColor(Color.WHITE); p.fillRectangle(1, 1, 14, 14) })
        fun cloudTex(seed: Int) = region(pix(256, 128) { p ->
            val r = java.util.Random(seed.toLong())
            val acc = FloatArray(256 * 128)
            repeat(26) {
                val cx = 50 + r.nextInt(156); val cy = 40 + r.nextInt(48); val rad = 18 + r.nextInt(30)
                for (y in maxOf(0, cy - rad) until minOf(128, cy + rad)) for (x in maxOf(0, cx - rad) until minOf(256, cx + rad)) {
                    val d = hypot((x - cx).toFloat(), (y - cy).toFloat()) / rad
                    if (d < 1) acc[y * 256 + x] += (1 - d * d) * 0.55f
                }
            }
            for (y in 0 until 128) for (x in 0 until 256) {
                val edge = minOf(minOf(x, 255 - x) / 40f, minOf(y, 127 - y) / 24f).coerceIn(0f, 1f)
                val a = (acc[y * 256 + x] * edge).coerceIn(0f, 1f)
                if (a > 0.01f) { p.setColor(1f, 1f, 1f, a); p.drawPixel(x, y) }
            }
        })
        cloud = cloudTex(3)
        cloud2 = cloudTex(11)
        val rt = pix(64, 64) { p ->
            p.setColor(Color.WHITE)
            p.fillRectangle(16, 0, 32, 64); p.fillRectangle(0, 16, 64, 32)
            p.fillCircle(16, 16, 16); p.fillCircle(47, 16, 16); p.fillCircle(16, 47, 16); p.fillCircle(47, 47, 16)
        }
        roundPatch = NinePatch(rt, 20, 20, 20, 20).also { it.scale(0.5f, 0.5f) }
        pillPatch = NinePatch(rt, 20, 20, 20, 20).also { it.scale(0.2f, 0.2f) }
    }

    private fun pix(w: Int, h: Int, draw: (Pixmap) -> Unit): Texture {
        val p = Pixmap(w, h, Pixmap.Format.RGBA8888)
        p.blending = Pixmap.Blending.None
        p.setColor(0f, 0f, 0f, 0f); p.fill()
        p.blending = Pixmap.Blending.SourceOver
        draw(p)
        val t = Texture(p, false)
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        p.dispose()
        textures += t
        return t
    }

    private fun region(t: Texture) = TextureRegion(t)

    // ---------- Scene2D ----------
    fun panel(color: Int, a: Float = 1f) = NinePatchDrawable(roundPatch).tint(rgb(color, a))
    fun flat(color: Int, a: Float = 1f) = TextureRegionDrawable(white).tint(rgb(color, a))

    fun label(text: String, font: BitmapFont = mid, color: Color = Color.WHITE, wrap: Boolean = false, align: Int = Align.left) =
        Label(text, Label.LabelStyle(font, color)).also { it.wrap = wrap; it.setAlignment(align) }

    fun button(text: String, color: Int, down: Int, font: BitmapFont = mid): TextButton {
        val st = TextButton.TextButtonStyle(panel(color), panel(down), panel(color), font)
        st.fontColor = Color.WHITE
        st.disabled = panel(0x3b5368)
        st.disabledFontColor = rgb(0xc9d6e2)
        return TextButton(text, st).also { it.pad(10f, 14f, 10f, 14f) }
    }

    fun green(text: String) = button(text, 0x2fbf6e, 0x22a95c)
    fun blue(text: String) = button(text, 0x2f8ff0, 0x1b6fd1)
    fun gray(text: String) = button(text, 0x3b5368, 0x31475a)

    val scrollStyle = ScrollPane.ScrollPaneStyle()

    fun slider(): com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle {
        val bg = NinePatchDrawable(pillPatch).tint(rgb(0x0b2238))
        bg.minHeight = 10f
        val before = NinePatchDrawable(pillPatch).tint(rgb(0x3ddc84))
        before.minHeight = 10f
        val knob = TextureRegionDrawable(disc).tint(Color.WHITE)
        knob.minWidth = 28f; knob.minHeight = 28f
        return com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle(bg, knob).also { it.knobBefore = before }
    }

    // ---------- Рисование HUD ----------
    private val layout = GlyphLayout()

    fun rect(b: Batch, x: Float, y: Float, w: Float, h: Float, c: Color) {
        b.color = c; b.draw(white, x, y, w, h)
    }

    fun round(b: Batch, x: Float, y: Float, w: Float, h: Float, c: Color) {
        b.color = c; roundPatch.draw(b, x, y, w, h)
    }

    /** Скругление для тонких полосок. */
    fun pill(b: Batch, x: Float, y: Float, w: Float, h: Float, c: Color) {
        b.color = c; pillPatch.draw(b, x, y, w, h)
    }

    fun text(b: Batch, font: BitmapFont, s: String, x: Float, y: Float, align: Int = Align.left, width: Float = 0f, wrap: Boolean = false, c: Color = Color.WHITE): GlyphLayout {
        font.color = c
        layout.setText(font, s, c, width, align, wrap)
        font.draw(b, layout, x, y)
        return layout
    }

    fun measure(font: BitmapFont, s: String): Float {
        layout.setText(font, s); return layout.width
    }

    fun icon(b: Batch, r: TextureRegion, cx: Float, cy: Float, w: Float, h: Float, rotDeg: Float = 0f, c: Color = Color.WHITE) {
        b.color = c
        b.draw(r, cx - w / 2, cy - h / 2, w / 2, h / 2, w, h, 1f, 1f, rotDeg)
    }

    /** Сектор перезарядки (доля [p] закрашена) из треугольников. */
    fun pie(b: Batch, cx: Float, cy: Float, r: Float, p: Float, c: Color) {
        if (p <= 0) return
        b.color = c
        val steps = 40
        val n = (steps * p).toInt().coerceAtLeast(1)
        for (i in 0 until n) {
            val a0 = Math.PI / 2 - 2 * Math.PI * i / steps
            val a1 = Math.PI / 2 - 2 * Math.PI * (i + 1) / steps
            val x0 = cx + (cos(a0) * r).toFloat(); val y0 = cy + (sin(a0) * r).toFloat()
            val x1 = cx + (cos(a1) * r).toFloat(); val y1 = cy + (sin(a1) * r).toFloat()
            triangleFill(b, cx, cy, x0, y0, x1, y1)
        }
    }

    private val verts = FloatArray(20)
    private fun triangleFill(b: Batch, x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
        val col = b.color.toFloatBits()
        val u = white.u; val v = white.v
        val xs = floatArrayOf(x0, x1, x2, x2); val ys = floatArrayOf(y0, y1, y2, y2)
        for (i in 0 until 4) {
            verts[i * 5] = xs[i]; verts[i * 5 + 1] = ys[i]; verts[i * 5 + 2] = col
            verts[i * 5 + 3] = u; verts[i * 5 + 4] = v
        }
        b.draw(white.texture, verts, 0, 20)
    }

    override fun dispose() {
        textures.forEach { it.dispose() }
        small.dispose(); mid.dispose(); big.dispose(); huge.dispose()
    }
}
