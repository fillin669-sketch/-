package com.seatrader.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sin
import kotlin.random.Random

/**
 * Построитель low-poly геометрии: каждый треугольник со своей нормалью (плоское освещение)
 * и цветом вершин. Вся модель — один меш и один вызов отрисовки.
 */
class Geo {
    private val data = com.badlogic.gdx.utils.FloatArray(8192)
    private var packed = Color.WHITE.toFloatBits()
    val m = Matrix4()
    private val stack = ArrayList<Matrix4>()

    private val pa = Vector3()
    private val pb = Vector3()
    private val pc = Vector3()
    private val n = Vector3()
    private val t1 = Vector3()
    private val t2 = Vector3()

    fun color(rgb: Int): Geo {
        packed = Color((rgb shl 8) or 0xff).toFloatBits()
        return this
    }

    fun push(): Geo { stack.add(Matrix4(m)); return this }
    fun pop(): Geo { m.set(stack.removeAt(stack.size - 1)); return this }
    fun at(x: Float, y: Float, z: Float): Geo { m.translate(x, y, z); return this }
    fun rotX(deg: Float): Geo { m.rotate(Vector3.X, deg); return this }
    fun rotY(deg: Float): Geo { m.rotate(Vector3.Y, deg); return this }
    fun rotZ(deg: Float): Geo { m.rotate(Vector3.Z, deg); return this }
    fun scale(x: Float, y: Float, z: Float): Geo { m.scale(x, y, z); return this }

    /** Треугольник в локальных координатах; нормаль направлена от точки [center]. */
    fun tri(a: Vector3, b: Vector3, c: Vector3, center: Vector3?) {
        t1.set(b).sub(a)
        t2.set(c).sub(a)
        n.set(t1).crs(t2)
        if (n.len2() < 1e-10f) return
        n.nor()
        if (center != null) {
            t1.set(a).add(b).add(c).scl(1f / 3f).sub(center)
            if (n.dot(t1) < 0) n.scl(-1f)
        }
        pa.set(a).mul(m); pb.set(b).mul(m); pc.set(c).mul(m)
        n.rot(m).nor()
        for (p in arrayOf(pa, pb, pc)) {
            data.add(p.x, p.y, p.z)
            data.add(n.x, n.y, n.z)
            data.add(packed)
        }
    }

    private val ctr = Vector3()
    private fun quad(a: Vector3, b: Vector3, c: Vector3, d: Vector3, center: Vector3?) {
        tri(a, b, c, center); tri(a, c, d, center)
    }

    fun box(w: Float, h: Float, d: Float): Geo {
        val x = w / 2; val y = h / 2; val z = d / 2
        val p = Array(8) { i -> Vector3(if (i and 1 == 0) -x else x, if (i and 2 == 0) -y else y, if (i and 4 == 0) -z else z) }
        ctr.setZero()
        quad(p[0], p[1], p[3], p[2], ctr); quad(p[4], p[5], p[7], p[6], ctr)
        quad(p[0], p[1], p[5], p[4], ctr); quad(p[2], p[3], p[7], p[6], ctr)
        quad(p[0], p[2], p[6], p[4], ctr); quad(p[1], p[3], p[7], p[5], ctr)
        return this
    }

    /** Цилиндр/конус вдоль оси Y с центром в начале координат. [jitter] — множители радиуса по сегментам. */
    fun cylinder(rTop: Float, rBot: Float, h: Float, seg: Int, jitter: FloatArray? = null, caps: Boolean = true): Geo {
        val top = Array(seg) { i ->
            val a = 2 * PI * i / seg; val j = jitter?.get(i) ?: 1f
            Vector3((cos(a) * rTop * j).toFloat(), h / 2, (sin(a) * rTop * j).toFloat())
        }
        val bot = Array(seg) { i ->
            val a = 2 * PI * i / seg; val j = jitter?.get(i) ?: 1f
            Vector3((cos(a) * rBot * j).toFloat(), -h / 2, (sin(a) * rBot * j).toFloat())
        }
        ctr.setZero()
        val ct = Vector3(0f, h / 2, 0f)
        val cb = Vector3(0f, -h / 2, 0f)
        for (i in 0 until seg) {
            val k = (i + 1) % seg
            quad(bot[i], bot[k], top[k], top[i], ctr)
            if (caps) {
                if (rTop > 0) tri(ct, top[i], top[k], Vector3(0f, -1f, 0f))
                if (rBot > 0) tri(cb, bot[i], bot[k], Vector3(0f, 1f, 0f))
            }
        }
        return this
    }

    fun cone(r: Float, h: Float, seg: Int) = cylinder(0f, r, h, seg)

    fun ico(r: Float): Geo {
        val t = ((1 + Math.sqrt(5.0)) / 2).toFloat()
        val v = arrayOf(
            Vector3(-1f, t, 0f), Vector3(1f, t, 0f), Vector3(-1f, -t, 0f), Vector3(1f, -t, 0f),
            Vector3(0f, -1f, t), Vector3(0f, 1f, t), Vector3(0f, -1f, -t), Vector3(0f, 1f, -t),
            Vector3(t, 0f, -1f), Vector3(t, 0f, 1f), Vector3(-t, 0f, -1f), Vector3(-t, 0f, 1f),
        )
        for (p in v) p.nor().scl(r)
        val f = intArrayOf(0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11, 1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8,
            3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9, 4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1)
        ctr.setZero()
        for (i in f.indices step 3) tri(v[f[i]], v[f[i + 1]], v[f[i + 2]], ctr)
        return this
    }

    /** Призма по контуру в плоскости XZ (x, z), от y0 до y1. */
    fun extrude(outline: List<Vector2>, y0: Float, y1: Float, capColor: Int, sideColor: Int): Geo {
        var cx = 0f; var cz = 0f
        for (p in outline) { cx += p.x; cz += p.y }
        cx /= outline.size; cz /= outline.size
        val center = Vector3(cx, (y0 + y1) / 2, cz)
        val topC = Vector3(cx, y1, cz)
        color(capColor)
        for (i in outline.indices) {
            val a = outline[i]; val b = outline[(i + 1) % outline.size]
            tri(topC, Vector3(a.x, y1, a.y), Vector3(b.x, y1, b.y), Vector3(cx, y1 - 1f, cz))
        }
        color(sideColor)
        for (i in outline.indices) {
            val a = outline[i]; val b = outline[(i + 1) % outline.size]
            quad(Vector3(a.x, y0, a.y), Vector3(b.x, y0, b.y), Vector3(b.x, y1, b.y), Vector3(a.x, y1, a.y), center)
        }
        return this
    }

    /** Выгнутый парус в плоскости XY, выпуклостью к -Z. */
    fun sail(w: Float, h: Float, bulge: Float): Geo {
        val sx = 6; val sy = 3
        val pts = Array(sy + 1) { j ->
            Array(sx + 1) { i ->
                val x = i.toFloat() / sx; val y = j.toFloat() / sy
                Vector3((x - 0.5f) * w, (y - 0.5f) * h, (-sin(x * PI) * bulge * (0.6 + 0.4 * sin(y * PI))).toFloat())
            }
        }
        val back = Vector3(0f, -h, 50f)
        for (j in 0 until sy) for (i in 0 until sx) quad(pts[j][i], pts[j][i + 1], pts[j + 1][i + 1], pts[j + 1][i], back)
        return this
    }

    fun flatRing(rIn: Float, rOut: Float, seg: Int): Geo {
        val below = Vector3(0f, -1f, 0f)
        for (i in 0 until seg) {
            val a0 = 2 * PI * i / seg; val a1 = 2 * PI * (i + 1) / seg
            val p0 = Vector3((cos(a0) * rIn).toFloat(), 0f, (sin(a0) * rIn).toFloat())
            val p1 = Vector3((cos(a0) * rOut).toFloat(), 0f, (sin(a0) * rOut).toFloat())
            val p2 = Vector3((cos(a1) * rOut).toFloat(), 0f, (sin(a1) * rOut).toFloat())
            val p3 = Vector3((cos(a1) * rIn).toFloat(), 0f, (sin(a1) * rIn).toFloat())
            quad(p0, p1, p2, p3, below)
        }
        return this
    }

    fun triangle(a: Vector3, b: Vector3, c: Vector3) { tri(a, b, c, Vector3(0f, -50f, 50f)) }

    /** Тонкая балка (канат, рея) между точками a и b. */
    fun beam(a: Vector3, b: Vector3, t: Float): Geo {
        val dir = Vector3(b).sub(a)
        val len = dir.len()
        if (len < 1e-4f) return this
        dir.scl(1f / len)
        val q = com.badlogic.gdx.math.Quaternion().setFromCross(Vector3.Y, dir)
        push()
        m.translate((a.x + b.x) / 2, (a.y + b.y) / 2, (a.z + b.z) / 2)
        m.rotate(q)
        box(t, len, t)
        pop()
        return this
    }

    val isEmpty get() = data.size == 0

    fun mesh(): Mesh {
        val count = data.size / 7
        val mesh = Mesh(true, max(count, 3), 0, VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.ColorPacked())
        mesh.setVertices(data.items, 0, data.size)
        return mesh
    }

    val vertexCount get() = data.size / 7

    fun build(material: Material = solidMaterial()): Model {
        val count = data.size / 7
        val mesh = Mesh(true, count, 0, VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.ColorPacked())
        mesh.setVertices(data.items, 0, data.size)
        val mb = ModelBuilder()
        mb.begin()
        mb.part("m", mesh, GL20.GL_TRIANGLES, 0, count, material)
        return mb.end()
    }

    companion object {
        fun solidMaterial() = Material(ColorAttribute.createDiffuse(Color.WHITE), IntAttribute.createCullFace(GL20.GL_NONE))
        fun transparentMaterial(opacity: Float) = Material(
            ColorAttribute.createDiffuse(Color.WHITE), IntAttribute.createCullFace(GL20.GL_NONE),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, opacity),
        )
    }
}

// ======================== Модели ========================

object Models {

    private fun bezier(out: MutableList<Vector2>, p0: Vector2, c: Vector2, p1: Vector2, steps: Int) {
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val u = 1 - t
            out += Vector2(u * u * p0.x + 2 * u * t * c.x + t * t * p1.x, u * u * p0.y + 2 * u * t * c.y + t * t * p1.y)
        }
    }

    /** Контур корпуса (вид сверху), нос в -Z. */
    private fun hull(len: Float, w: Float): List<Vector2> {
        val l = len
        val pts = ArrayList<Vector2>()
        val bow = Vector2(0f, -l / 2)
        pts += bow
        bezier(pts, bow, Vector2(w * 0.5f, -l * 0.28f), Vector2(w * 0.5f, l * 0.08f), 6)
        pts += Vector2(w * 0.42f, l / 2)
        pts += Vector2(-w * 0.42f, l / 2)
        pts += Vector2(-w * 0.5f, l * 0.08f)
        bezier(pts, Vector2(-w * 0.5f, l * 0.08f), Vector2(-w * 0.5f, -l * 0.28f), bow, 5)
        return pts
    }

    /** Высота палубы корабля над водой (для попаданий и огней). */
    fun deckTop(look: Look) = look.width * 0.62f * 0.6f

    fun ship(look: Look): Model {
        val g = Geo()
        val L = look.len; val W = look.width; val H = W * 0.62f
        val top = H * 0.6f
        val deck = 0xc89a62
        val dark = darker(look.hull, 0.78f)
        g.extrude(hull(L, W), top - H, top, deck, look.hull)
        // Полосы обшивки
        g.extrude(hull(L * 1.008f, W * 1.008f), top - H * 0.55f, top - H * 0.42f, dark, dark)
        g.extrude(hull(L * 1.008f, W * 1.008f), top - H * 0.95f, top - H * 0.8f, dark, dark)
        g.extrude(hull(L * 1.03f, W * 1.03f), top - 0.7f, top - 0.25f, look.trim, look.trim)
        g.extrude(hull(L, W), top, top + 0.6f, look.hull, look.hull)
        g.extrude(hull(L * 0.92f, W * 0.86f), top + 0.02f, top + 0.63f, deck, deck)

        // Пушечные порты
        val ports = max(2, minOf(7, round(L / 3.6f).toInt()))
        g.color(0x1d1410)
        for (sx in intArrayOf(-1, 1)) for (i in 0 until ports) {
            val z = -L * 0.28f + L * 0.56f * i / max(1, ports - 1)
            val half = halfWidthAt(L, W, z)
            g.push().at(sx * (half + 0.02f), top - H * 0.28f, z).box(0.1f, 0.55f, 0.65f).pop()
        }
        // Леера (столбики по борту)
        g.color(darker(look.hull, 0.9f))
        var z = -L * 0.38f
        while (z < L * 0.42f) {
            val half = halfWidthAt(L, W, z)
            for (sx in intArrayOf(-1, 1)) g.push().at(sx * half * 0.97f, top + 0.95f, z).box(0.14f, 0.7f, 0.14f).pop()
            z += 1.3f
        }
        // Кормовая надстройка с окнами
        g.color(look.hull).push().at(0f, top + H * 0.35f, L * 0.36f).box(W * 0.72f, H * 0.7f, L * 0.2f).pop()
        g.color(look.trim).push().at(0f, top + H * 0.72f, L * 0.36f).box(W * 0.78f, 0.25f, L * 0.22f).pop()
        g.color(0xffd27a)
        for (sx in intArrayOf(-1, 1)) g.push().at(sx * W * 0.365f, top + H * 0.38f, L * 0.36f).box(0.06f, H * 0.22f, L * 0.06f).pop()
        val sternZ = L * 0.5f
        for (k in -1..1) g.push().at(k * W * 0.18f, top + H * 0.05f, sternZ + 0.02f).box(W * 0.12f, H * 0.22f, 0.08f).pop()
        // Фонарь на корме и руль
        g.color(0xffe08a).push().at(0f, top + H * 0.72f + 0.9f, L * 0.44f).box(0.5f, 0.7f, 0.5f).pop()
        g.color(0x3b2a1e).push().at(0f, top + H * 0.72f + 0.4f, L * 0.44f).box(0.12f, 0.6f, 0.12f).pop()
        g.color(dark).push().at(0f, top - H * 0.55f, L * 0.5f + 0.25f).box(0.3f, H * 0.9f, 0.9f).pop()
        // Носовая фигура
        g.color(look.trim).push().at(0f, top - 0.2f, -L * 0.5f - 0.3f).rotX(-30f).box(0.35f, 1.1f, 0.7f).pop()

        // Бушприт
        val bowTip = Vector3(0f, top + 2.6f, -L * 0.72f)
        g.color(look.hull).beam(Vector3(0f, top + 0.6f, -L * 0.42f), bowTip, 0.32f)

        val n = look.masts
        val mastH = L * 0.62f + 3
        val tops = ArrayList<Vector3>()
        for (i in 0 until n) {
            val mz = mastZ(look, i)
            val h = mastH * (if (i == 0 && n > 2) 0.88f else 1f)
            g.color(look.hull).push().at(0f, top + h / 2, mz).cylinder(0.16f, 0.26f, h, 6).pop()
            val sw = W * (if (n == 1) 1.9f else 1.6f)
            for (k in 0..1) {
                val sh = h * (if (k == 0) 0.36f else 0.26f)
                val sy = top + h * (if (k == 0) 0.36f else 0.72f)
                val ww = sw * (if (k == 0) 1f else 0.8f)
                g.color(look.hull).push().at(0f, sy + sh / 2, mz - 0.1f).rotZ(90f).cylinder(0.1f, 0.1f, ww * 1.05f, 5).pop()
                g.color(look.hull).push().at(0f, sy - sh / 2, mz - 0.1f).rotZ(90f).cylinder(0.07f, 0.07f, ww * 0.95f, 4).pop()
            }
            g.color(look.hull).push().at(0f, top + h * 0.86f, mz).cylinder(0.7f, 0.55f, 0.5f, 6).pop()
            tops += Vector3(0f, top + h, mz)
            if (i == n - 1) g.color(look.flag).push().at(0f, top + h + 0.5f, mz + 1.1f).box(0.08f, 1.3f, 2.2f).pop()
        }
        // Такелаж: штаги между мачтами и ванты к бортам
        g.color(0x2b2118)
        g.beam(bowTip, tops[0], 0.07f)
        for (i in 0 until tops.size - 1) g.beam(tops[i], tops[i + 1], 0.07f)
        g.beam(tops.last(), Vector3(0f, top + 1.6f, L * 0.45f), 0.07f)
        for (t in tops) for (sx in intArrayOf(-1, 1)) {
            val hw = halfWidthAt(L, W, t.z)
            g.beam(Vector3(t.x, t.y * 0.86f, t.z), Vector3(sx * hw, top + 0.7f, t.z + 0.8f), 0.05f)
        }
        // Пушки
        val guns = max(2, minOf(6, round(L / 4).toInt()))
        g.color(0x222222)
        for (sx in intArrayOf(-1, 1)) for (i in 0 until guns) {
            g.push().at(sx * (W * 0.5f + 0.2f), top + 0.15f, -L * 0.25f + L * 0.5f * i / max(1, guns - 1))
                .rotZ(90f).cylinder(0.28f, 0.36f, 1.6f, 6).pop()
        }

        // Паруса — отдельные узлы, чтобы надуваться от ветра
        val mb = ModelBuilder()
        mb.begin()
        val mat = Geo.solidMaterial()
        mb.node().id = "hull"
        mb.part("hull", g.mesh(), GL20.GL_TRIANGLES, 0, g.vertexCount, mat)
        var idx = 0
        for (i in 0 until n) {
            val mz = mastZ(look, i)
            val h = mastH * (if (i == 0 && n > 2) 0.88f else 1f)
            val sw = W * (if (n == 1) 1.9f else 1.6f)
            for (k in 0..1) {
                val sh = h * (if (k == 0) 0.36f else 0.26f)
                val sy = top + h * (if (k == 0) 0.36f else 0.72f)
                val ww = sw * (if (k == 0) 1f else 0.8f)
                val sg = Geo().color(look.sail).sail(ww, sh, W * 0.28f)
                val node = mb.node()
                node.id = "sail${idx++}"
                node.translation.set(0f, sy, mz - 0.25f)
                mb.part("s$idx", sg.mesh(), GL20.GL_TRIANGLES, 0, sg.vertexCount, mat)
            }
        }
        val jib = Geo().color(look.sail)
        jib.triangle(Vector3(0f, top + 1f, -L * 0.62f), Vector3(0f, top + mastH * 0.75f, -L * 0.2f), Vector3(0f, top + 1.2f, -L * 0.26f))
        mb.node().id = "jib"
        mb.part("jib", jib.mesh(), GL20.GL_TRIANGLES, 0, jib.vertexCount, mat)
        return mb.end()
    }

    fun sailCount(look: Look) = look.masts * 2

    fun mastZ(look: Look, i: Int): Float {
        val n = look.masts; val L = look.len
        return if (n == 1) -L * 0.05f else -L * 0.28f + L * 0.5f * i / (n - 1)
    }

    /** Полуширина корпуса на расстоянии z от центра (для портов и попаданий). */
    fun halfWidthAt(L: Float, W: Float, z: Float): Float {
        val t = (z / L + 0.5f).coerceIn(0f, 1f)
        return when {
            t < 0.58f -> W * 0.5f * Math.sqrt((t / 0.58f).toDouble()).toFloat()
            else -> W * (0.5f - 0.08f * (t - 0.58f) / 0.42f)
        }
    }

    private fun darker(c: Int, k: Float): Int {
        val r = ((c shr 16 and 255) * k).toInt(); val gg = ((c shr 8 and 255) * k).toInt(); val b = ((c and 255) * k).toInt()
        return (r shl 16) or (gg shl 8) or b
    }

    private fun jitter(seg: Int, rnd: Random, amount: Float) = FloatArray(seg) { 1 + (rnd.nextFloat() - 0.5f) * amount }

    private fun tree(g: Geo, x: Float, y: Float, z: Float, rnd: Random) {
        val s = 0.8f + rnd.nextFloat() * 0.6f
        g.push().at(x, y, z).scale(s, s, s)
        g.color(0x6b4423).push().at(0f, 1.5f, 0f).cylinder(0.35f, 0.5f, 3f, 5).pop()
        g.color(intArrayOf(0x2f7d32, 0x388e3c, 0x2e6b30)[rnd.nextInt(3)])
        for (k in 0..1) g.push().at(0f, 4f + k * 1.8f, 0f).cone(2.4f - k * 0.7f, 3.6f, 6).pop()
        g.pop()
    }

    private fun palm(g: Geo, x: Float, y: Float, z: Float, rnd: Random) {
        val s = 0.8f + rnd.nextFloat() * 0.4f
        g.push().at(x, y, z).scale(s, s, s)
        g.color(0x8d6e4a).push().at(0f, 3f, 0f).rotZ((rnd.nextFloat() - 0.5f) * 20f).cylinder(0.25f, 0.4f, 6f, 5).pop()
        g.color(0x43a047)
        for (i in 0 until 5) {
            g.push().at(0f, 6f, 0f).rotY(i * 72f + rnd.nextFloat() * 40f).at(1.6f, 0f, 0f).rotZ(-75f).cone(0.7f, 4f, 4).pop()
        }
        g.pop()
    }

    private fun house(g: Geo, x: Float, y: Float, z: Float, angle: Float, rnd: Random) {
        val w = 6 + rnd.nextFloat() * 4; val d = 5 + rnd.nextFloat() * 3; val hh = 4 + rnd.nextFloat() * 2
        g.push().at(x, y, z).rotY(angle)
        g.color(intArrayOf(0x8d5b3a, 0x9c6b45, 0xd7c4a3)[rnd.nextInt(3)]).push().at(0f, hh / 2, 0f).box(w, hh, d).pop()
        val mx = max(w, d)
        g.color(intArrayOf(0x9b2226, 0xae2012, 0x7f1d1d)[rnd.nextInt(3)])
            .push().at(0f, hh + 2f, 0f).scale(w / mx, 1f, d / mx).rotY(45f).cone(mx * 0.78f, 4f, 4).pop()
        g.color(0x3e2412).push().at(0f, 1.2f, d / 2 + 0.06f).box(1.4f, 2.4f, 0.2f).pop()
        g.pop()
    }

    /** Модель острова и точки ночных огней (окна, маяк). */
    fun island(r: Float, seed: Int, port: Boolean, lighthouse: Boolean, lights: MutableList<Vector3>): Model {
        val rnd = Random(seed)
        val g = Geo()
        g.color(0xe6d3a3).push().at(0f, -1.2f, 0f).cylinder(r * 0.98f, r * 1.08f, 5f, 14, jitter(14, rnd, 0.22f)).pop()
        g.color(0x67a83d).push().at(0f, 2.2f, 0f).cylinder(r * 0.82f, r * 0.9f, 3f, 14, jitter(14, rnd, 0.2f)).pop()
        if (!port || rnd.nextFloat() < 0.5f) {
            g.color(0x5b9634).push()
                .at((rnd.nextFloat() - 0.5f) * r * 0.3f, 3.7f + r * 0.12f, if (port) -r * 0.4f else (rnd.nextFloat() - 0.5f) * r * 0.3f)
                .cylinder(r * 0.35f, r * 0.55f, r * 0.25f, 9, jitter(9, rnd, 0.3f)).pop()
        }
        val occupied = ArrayList<FloatArray>()
        fun free(px: Float, pz: Float, rad: Float) = occupied.all { hypot(it[0] - px, it[1] - pz) > it[2] + rad }

        if (port) {
            // Причал на южном берегу
            val len = r * 0.45f
            g.push().at(0f, 0f, r * 0.72f)
            g.color(0xa57744).push().at(0f, 2.2f, len / 2).box(7f, 0.6f, len).pop()
            g.color(0x5c3b1e)
            for (i in 0..3) for (sx in intArrayOf(-1, 1)) g.push().at(sx * 3.4f, 0.8f, len * i / 3).cylinder(0.35f, 0.35f, 5f, 5).pop()
            g.color(0xa57744).push().at(0f, 2.2f, len).box(14f, 0.6f, 4f).pop()
            g.pop()
            occupied += floatArrayOf(0f, r * 0.6f, 8f)
            val houses = 3 + rnd.nextInt(3)
            var tries = 0
            while (occupied.size < houses + 1 && tries++ < houses * 6) {
                val a = rnd.nextFloat() * 2 * PI
                val d = r * (0.25f + rnd.nextFloat() * 0.42f)
                val px = (cos(a) * d).toFloat(); val pz = (sin(a) * d).toFloat()
                if (!free(px, pz, 6f)) continue
                house(g, px, 3.7f, pz, Math.toDegrees(atan2(px, pz).toDouble()).toFloat() + (rnd.nextFloat() - 0.5f) * 34f, rnd)
                occupied += floatArrayOf(px, pz, 6f)
                lights += Vector3(px, 6.5f, pz)
            }
            if (lighthouse) {
                val lx = -r * 0.5f; val lz = r * 0.35f
                g.push().at(lx, 3.7f, lz)
                g.color(0xf1f1f1).push().at(0f, 8f, 0f).cylinder(2f, 2.8f, 16f, 8).pop()
                g.color(0xc62828)
                for (k in 0..2) g.push().at(0f, 3f + k * 5, 0f).cylinder(2.2f - k * 0.2f, 2.4f - k * 0.2f, 1.5f, 8).pop()
                g.color(0xfff3b0).push().at(0f, 17f, 0f).cylinder(1.6f, 1.6f, 2f, 8).pop()
                g.color(0xc62828).push().at(0f, 19.2f, 0f).cone(2.2f, 2.4f, 8).pop()
                g.pop()
                occupied += floatArrayOf(lx, lz, 4f)
                lights += Vector3(lx, 20.7f, lz)
            }
            lights += Vector3(0f, 4.5f, r * 0.72f + r * 0.45f)
        }

        val trees = (r / 4).toInt() + 3
        for (i in 0 until trees * 3) {
            val a = rnd.nextFloat() * 2 * PI
            val d = r * (0.15f + rnd.nextFloat() * 0.62f)
            val px = (cos(a) * d).toFloat(); val pz = (sin(a) * d).toFloat()
            if (!free(px, pz, 2.5f)) continue
            if (rnd.nextFloat() < 0.4f) palm(g, px, 3.7f, pz, rnd) else tree(g, px, 3.7f, pz, rnd)
            occupied += floatArrayOf(px, pz, 2.5f)
        }
        val rocks = 4 + rnd.nextInt(5)
        for (i in 0 until rocks) {
            val a = rnd.nextFloat() * 2 * PI
            if (port && Math.abs(sin(a) - 1) < 0.25) continue
            val d = r * (0.95f + rnd.nextFloat() * 0.15f)
            val s = 1.5f + rnd.nextFloat() * 3
            g.color(intArrayOf(0x6d6d6d, 0x5a5a5a, 0x7b7b7b)[rnd.nextInt(3)])
                .push().at((cos(a) * d).toFloat(), 0.5f, (sin(a) * d).toFloat())
                .rotX(rnd.nextFloat() * 180).rotY(rnd.nextFloat() * 180).scale(1f, 0.7f + rnd.nextFloat() * 0.5f, 1f).ico(s).pop()
        }
        return g.build()
    }

    fun foam(r: Float): Model = Geo().color(0xffffff).flatRing(r * 1.02f, r * 1.2f, 28).build(Geo.transparentMaterial(0.35f))

    fun portRing(): Model = Geo().color(0xffffff).flatRing(9f, 10.6f, 40).build(Geo.transparentMaterial(0.75f))

    fun chest(): Model {
        val g = Geo()
        g.color(0x8b5a2b).box(2.4f, 1.4f, 1.6f)
        g.color(0x9c6b30).push().at(0f, 0.7f, 0f).rotZ(90f).cylinder(0.8f, 0.8f, 2.4f, 6).pop()
        g.color(0xffc93c).push().at(0f, 0.2f, 0f).box(2.5f, 0.3f, 1.7f).pop()
        g.color(0xffd54f).push().at(0f, 1.55f, 0f).cylinder(0.7f, 0.7f, 0.25f, 8).pop()
        return g.build()
    }

    fun serpentHead(): Model {
        val g = Geo()
        g.color(0x1f7a6d).push().at(0f, 0f, -1.5f).scale(1f, 0.8f, 1.6f).ico(2.4f).pop()
        g.color(0x9ad1a8).push().at(0f, -1.1f, -3.2f).box(2.6f, 0.7f, 3.2f).pop()
        g.color(0x155e54)
        for (sx in intArrayOf(-1, 1)) g.push().at(sx * 1.3f, 1.6f, -0.4f).rotZ(sx * -25f).cone(0.5f, 2.2f, 5).pop()
        g.color(0xffd54f)
        for (sx in intArrayOf(-1, 1)) g.push().at(sx * 1.25f, 0.8f, -2.6f).box(0.5f, 0.45f, 0.5f).pop()
        g.color(0xffffff)
        for (sx in intArrayOf(-1, 1)) g.push().at(sx * 0.7f, -0.6f, -4.5f).rotX(180f).cone(0.25f, 0.8f, 4).pop()
        return g.build()
    }

    fun serpentBody(): Model {
        val g = Geo()
        g.color(0x22897a).ico(2.2f)
        g.color(0x9ad1a8).push().at(0f, -1.2f, 0f).scale(1f, 0.4f, 1f).ico(1.8f).pop()
        g.color(0x155e54).push().at(0f, 2.1f, 0f).rotX(90f).scale(0.3f, 1f, 1f).cone(1.1f, 1.6f, 3).pop()
        return g.build()
    }

    fun treasure(): Model {
        val g = Geo()
        g.color(0xb71c1c)
        g.push().rotY(45f).box(0.5f, 0.15f, 4f).pop()
        g.push().rotY(-45f).box(0.5f, 0.15f, 4f).pop()
        g.color(0x8b5a2b).push().at(0f, 0.8f, 0f).box(2f, 1.2f, 1.4f).pop()
        g.color(0xffc93c).push().at(0f, 1.0f, 0f).box(2.1f, 0.25f, 1.5f).pop()
        g.color(0x9c6b30).push().at(0f, 1.45f, 0f).rotZ(90f).cylinder(0.7f, 0.7f, 2f, 6).pop()
        g.color(0x6d4c2f).push().at(1.6f, 1.5f, 0.3f).rotZ(-20f).box(0.15f, 3f, 0.15f).pop()
        g.color(0x9e9e9e).push().at(1.95f, 0.1f, 0.3f).box(0.6f, 0.8f, 0.1f).pop()
        return g.build()
    }

    fun gem(): Model {
        val g = Geo().color(0xe040fb)
        val v = arrayOf(Vector3(0f, 1.6f, 0f), Vector3(0f, -1.6f, 0f), Vector3(1.1f, 0f, 0f), Vector3(-1.1f, 0f, 0f), Vector3(0f, 0f, 1.1f), Vector3(0f, 0f, -1.1f))
        val f = intArrayOf(0, 2, 4, 0, 4, 3, 0, 3, 5, 0, 5, 2, 1, 2, 4, 1, 4, 3, 1, 3, 5, 1, 5, 2)
        for (i in f.indices step 3) g.tri(v[f[i]], v[f[i + 1]], v[f[i + 2]], Vector3.Zero)
        return g.build()
    }
}
