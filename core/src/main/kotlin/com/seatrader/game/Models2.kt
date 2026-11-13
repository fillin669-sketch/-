package com.seatrader.game

import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.math.Vector3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Модели для базы игрока, айсбергов, затонувших руин. */
object Models2 {
    /** Плоский остров под базу: песок, трава, тропинки. Верх травы на высоте 3.7. */
    fun baseIsland(r: Float): Model {
        val g = Geo()
        val rnd = Random(99)
        val j = FloatArray(24) { 1 + (rnd.nextFloat() - 0.5f) * 0.12f }
        g.mat(Shaders.SAND).color(0xbfa77a).push().at(0f, -1.6f, 0f).cylinder(r * 1.06f, r * 1.12f, 3.2f, 24, j).pop()
        g.color(0xe6d3a3).push().at(0f, 0.2f, 0f).cylinder(r * 0.97f, r * 1.05f, 2.2f, 24, j).pop()
        g.mat(Shaders.GRASS).color(0x6fae45).push().at(0f, 2.2f, 0f).cylinder(r * 0.84f, r * 0.92f, 3f, 24, j).pop()
        // площадь в центре
        g.mat(Shaders.STONE).color(0xb9a585).push().at(0f, 3.72f, 0f).cylinder(r * 0.22f, r * 0.24f, 0.1f, 14).pop()
        g.mat(-1)
        // пальмы по краю
        for (k in 0 until 7) {
            val a = k * 0.9f + 0.4f
            val x = cos(a) * r * 0.86f; val z = sin(a) * r * 0.86f
            g.color(0x8d6e4a).beam(Vector3(x, 3f, z), Vector3(x * 1.03f, 10f, z * 1.03f + 0.6f), 0.5f)
            g.color(0x2e7d32)
            for (l in 0 until 5) {
                val la = l * 1.25f
                g.beam(Vector3(x * 1.03f, 10f, z * 1.03f + 0.6f), Vector3(x * 1.03f + cos(la) * 3.2f, 8.6f, z * 1.03f + 0.6f + sin(la) * 3.2f), 0.5f)
            }
        }
        return g.build()
    }

    /** Постройка базы [kind] уровня [lvl] (1..3). Начало координат — на земле. */
    fun building(kind: String, lvl: Int): Model {
        val g = Geo()
        val s = 0.85f + lvl * 0.15f
        when (kind) {
            "dock" -> {
                g.color(0x8d6e4a)
                val len = 10f + lvl * 4f
                g.push().at(0f, -0.4f, len / 2).box(4f, 0.4f, len).pop()
                g.color(0x5d4037)
                var z = 1f
                while (z < len) { for (sx in intArrayOf(-1, 1)) g.push().at(sx * 2f, -2.5f, z).box(0.4f, 4.5f, 0.4f).pop(); z += 3f }
                if (lvl >= 2) { g.color(0x8d6e4a).push().at(4f, -0.4f, len - 2f).box(5f, 0.4f, 3f).pop() }
                if (lvl >= 3) { g.color(0x3e2723).push().at(-2f, 1.5f, len - 1f).box(0.3f, 4f, 0.3f).pop(); g.color(0xffd27a).push().at(-2f, 3.7f, len - 1f).box(0.6f, 0.6f, 0.6f).pop() }
            }
            "store" -> {
                g.color(0x9e7b55).push().at(0f, 2f * s, 0f).box(7f * s, 4f * s, 5f * s).pop()
                g.color(0x6d4c2f).push().at(0f, 4.3f * s, 0f).rotZ(0f).box(7.6f * s, 0.5f * s, 5.6f * s).pop()
                g.color(0x5d4037).push().at(0f, 1.5f * s, 2.55f * s).box(2.4f, 3f, 0.1f).pop()
                g.color(0x6d4c2f)
                for (k in 0 until lvl + 1) g.push().at(-4.5f * s + k * 1.3f, 0.6f, 3.6f).cylinder(0.55f, 0.55f, 1.2f, 7).pop()
                g.color(0xd7c29e)
                for (k in 0 until lvl) g.push().at(4.4f * s, 0.5f + k * 0.9f, 2.8f).box(1.2f, 0.85f, 1.2f).pop()
            }
            "tavern" -> {
                g.mat(Shaders.BRICK).color(0xc98a5e).push().at(0f, 2.2f * s, 0f).box(7f * s, 4.4f * s, 6f * s).pop()
                g.mat(-1)
                g.color(0xa1442b).push().at(0f, 5.2f * s, 0f).cone(5.6f * s, 3f * s, 4).pop()
                g.color(0x5d4037).push().at(0f, 1.4f, 3.05f * s).box(1.6f, 2.8f, 0.1f).pop()
                g.color(0xffd27a)
                for (sx in intArrayOf(-1, 1)) g.push().at(sx * 2.2f * s, 2.6f * s, 3.05f * s).box(1.1f, 1f, 0.1f).pop()
                g.color(0x3e2723).push().at(3.9f * s, 3f, 3.2f * s).box(0.15f, 0.15f, 1.6f).pop()
                g.color(0xc9a227).push().at(3.9f * s, 2.3f, 3.9f * s).box(0.12f, 1.2f, 1.2f).pop()
                if (lvl >= 2) { g.color(0x8d6e4a); for (k in 0..1) g.push().at(-1.5f + k * 3f, 0.5f, 5.2f * s).cylinder(0.9f, 0.9f, 0.15f, 8).pop() }
                if (lvl >= 3) { g.color(0x9e9e9e).push().at(-2.2f * s, 6.5f * s, -1f).box(1f, 2.6f, 1f).pop() }
            }
            "farm" -> {
                val rows = 2 + lvl
                for (r in 0 until rows) {
                    g.color(0x7a5a3a).push().at(0f, 0.15f, -4f + r * 2.6f).box(10f, 0.3f, 1.4f).pop()
                    g.color(if (r % 2 == 0) 0x388e3c else 0x558b2f)
                    for (k in 0 until 5) g.push().at(-4f + k * 2f, 0.9f, -4f + r * 2.6f).ico(0.75f).pop()
                }
                g.color(0xd7b98a).push().at(6.6f, 1.5f, 0f).box(3f, 3f, 3f).pop()
                g.color(0x8d6e4a).push().at(6.6f, 3.6f, 0f).cone(2.6f, 1.6f, 4).pop()
            }
            "lighthouse" -> {
                val h = 10f + lvl * 4f
                g.color(0x9e9e9e).push().at(0f, 0.6f, 0f).cylinder(3f, 3.4f, 1.2f, 10).pop()
                var y = 1.2f; var band = 0
                while (y < h) {
                    val f = y / h
                    g.color(if (band % 2 == 0) 0xf5f5f5 else 0xc62828).push().at(0f, y + 1f, 0f).cylinder(2.2f - f * 0.8f, 2.4f - f * 0.8f, 2f, 10).pop()
                    y += 2f; band++
                }
                g.color(0x37474f).push().at(0f, y + 0.2f, 0f).cylinder(1.9f, 1.9f, 0.4f, 10).pop()
                g.color(0xfff59d).push().at(0f, y + 1.3f, 0f).cylinder(1.1f, 1.1f, 1.8f, 8).pop()
                g.color(0x37474f).push().at(0f, y + 2.8f, 0f).cone(1.5f, 1.4f, 8).pop()
            }
            "battery" -> {
                g.mat(Shaders.STONE).color(0x8a8478)
                for (k in -2..2) g.push().at(k * 2.6f, 1f, 0f).box(2.5f, 2f, 1.6f).pop()
                g.color(0x6d675e)
                for (k in -2..2 step 2) g.push().at(k * 2.6f, 2.4f, 0f).box(1.6f, 0.9f, 1.6f).pop()
                g.color(0x222222)
                for (k in 0 until lvl + 1) {
                    val x = (k - lvl / 2f) * 2.6f
                    g.push().at(x + 1.3f, 2.3f, 1.6f).rotX(90f).cylinder(0.32f, 0.42f, 2.6f, 7).pop()
                }
                g.color(0x5d4037).push().at(0f, 4.5f, -1f).box(0.2f, 5f, 0.2f).pop()
                g.color(0x111111).push().at(1f, 6.4f, -1f).box(2f, 1.2f, 0.08f).pop()
            }
            "yard" -> {
                g.color(0x8d6e4a).push().at(0f, 0.3f, 0f).box(4f, 0.6f, 12f).pop()
                g.color(0xa1887f)
                for (k in 0 until 4 + lvl) {
                    val z = -5f + k * (10f / (3 + lvl))
                    g.beam(Vector3(-2.4f, 0.6f, z), Vector3(-1.6f, 3.4f, z), 0.3f)
                    g.beam(Vector3(2.4f, 0.6f, z), Vector3(1.6f, 3.4f, z), 0.3f)
                }
                g.color(0x5d4037).push().at(0f, 0.8f, 0f).box(0.6f, 0.6f, 11f).pop()
                // кран
                g.color(0x6d4c2f).push().at(4f, 4f, 3f).box(0.5f, 8f, 0.5f).pop()
                g.color(0x6d4c2f).beam(Vector3(4f, 8f, 3f), Vector3(0.5f, 8f, 1f), 0.35f)
                if (lvl >= 3) { g.color(0xf5f0dc).push().at(0f, 5.5f, -1f).sail(3f, 3f, 0.6f).pop() }
            }
        }
        return g.build()
    }

    /** Плавучий айсберг. */
    fun iceberg(seed: Int): Model {
        val g = Geo()
        val rnd = Random(seed)
        g.mat(Shaders.ROCK).color(0xbfe3f2).push().scale(1.4f, 0.55f, 1.2f).ico(6f).pop()
        g.color(0xeef7fb)
        repeat(4) {
            val a = rnd.nextFloat() * 6.28f
            g.push().at(cos(a) * 2.5f, 2.5f + rnd.nextFloat() * 2f, sin(a) * 2.5f).scale(1f, 1.6f, 1f).ico(2.2f + rnd.nextFloat() * 1.4f).pop()
        }
        g.color(0x8fcde6).push().at(0f, -1.5f, 0f).scale(1.6f, 0.4f, 1.4f).ico(6f).pop()
        return g.build()
    }

    /** Колонны древнего храма, торчащие из воды. */
    fun sunkenRuins(seed: Int): Model {
        val g = Geo()
        val rnd = Random(seed)
        for (k in 0 until 7) {
            val a = k / 7f * 6.28f; val d = 9f + rnd.nextFloat() * 3f
            val h = 2f + rnd.nextFloat() * 6f
            val x = cos(a) * d; val z = sin(a) * d
            g.color(0xa89f8c).push().at(x, h / 2 - 2f, z).rotX(rnd.nextFloat() * 12 - 6).cylinder(0.9f, 1f, h + 2f, 8).pop()
            if (rnd.nextBoolean()) g.color(0x8f8673).push().at(x, h - 1.6f, z).box(2.4f, 0.6f, 2.4f).pop()
        }
        g.color(0x6e8f6a).push().at(0f, -1.2f, 0f).cylinder(5f, 6f, 1f, 10).pop()
        g.color(0xc9a227).push().at(0f, 0.2f, 0f).ico(1.2f).pop()
        return g.build()
    }

    // ===================== Портовые места =====================
    // Локальные координаты: +Z — от берега в море, начало — у кромки воды.
    private fun platform(g: Geo, x0: Float, x1: Float, z0: Float, z1: Float, y: Float = 2.2f) {
        g.color(0x9c7449).push().at((x0 + x1) / 2, y, (z0 + z1) / 2).box(x1 - x0, 0.5f, z1 - z0).pop()
        g.color(0x6d4c2f)
        var px = x0 + 0.5f
        while (px <= x1) {
            var pz = z0 + 0.5f
            while (pz <= z1) { g.color(0x4e342e).push().at(px, y - 2.6f, pz).cylinder(0.32f, 0.32f, 5f, 6).pop(); pz += 4f }
            px += 4f
        }
    }

    private fun roof(g: Geo, cx: Float, y: Float, cz: Float, w: Float, d: Float, col: Int) {
        g.mat(Shaders.TILES).color(col)
        for (s in intArrayOf(-1, 1)) g.push().at(cx + s * w * 0.24f, y + w * 0.12f, cz).rotZ(-s * 28f).box(w * 0.58f, 0.35f, d + 0.8f).pop()
        g.mat(-1).color(darkC(col)).push().at(cx, y + w * 0.26f, cz).box(0.4f, 0.4f, d + 0.9f).pop()
    }

    private fun darkC(c: Int): Int {
        val r = (c shr 16 and 255) * 7 / 10; val gg = (c shr 8 and 255) * 7 / 10; val b = (c and 255) * 7 / 10
        return (r shl 16) or (gg shl 8) or b
    }

    private fun lantern(g: Geo, x: Float, y: Float, z: Float) {
        g.color(0x3e2723).push().at(x, y + 1.6f, z).box(0.2f, 3.2f, 0.2f).pop()
        g.color(0xffd27a).push().at(x, y + 3.4f, z).box(0.55f, 0.6f, 0.55f).pop()
        g.color(0x3e2723).push().at(x, y + 3.85f, z).cone(0.45f, 0.4f, 4).pop()
    }

    private fun barrel(g: Geo, x: Float, y: Float, z: Float, col: Int = 0x6d4c2f) {
        g.color(col).push().at(x, y + 0.5f, z).cylinder(0.45f, 0.45f, 1f, 8).pop()
        g.color(0x37474f).push().at(x, y + 0.75f, z).cylinder(0.47f, 0.47f, 0.08f, 8).pop()
    }

    fun station(kind: String): Model {
        val g = Geo()
        val y = 2.2f
        when (kind) {
            "yard" -> {
                platform(g, -12f, 12f, -5f, 3f)
                // эллинг
                g.color(0xb08a5e).push().at(-5f, y + 3f, -1.5f).box(9f, 6f, 6f).pop()
                roof(g, -5f, y + 6f, -1.5f, 9.6f, 6f, 0x7b3f2a)
                g.color(0x3e2723).push().at(-5f, y + 2f, 1.55f).box(4f, 4f, 0.1f).pop()
                // два пирса — док для корабля
                for (sx in intArrayOf(-1, 1)) {
                    g.color(0x9c7449).push().at(sx * 7.2f, y - 0.3f, 18f).box(2.6f, 0.45f, 32f).pop()
                    var z = 3f
                    while (z < 34f) {
                        g.color(0x4e342e).push().at(sx * 7.2f, y - 2.8f, z).cylinder(0.3f, 0.3f, 5f, 6).pop()
                        // леса вдоль борта
                        if (z < 28f && sx < 0) {
                            g.color(0x8d6e4a).push().at(sx * 6.2f, y + 3f, z).box(0.25f, 6.4f, 0.25f).pop()
                            g.color(0x8d6e4a).push().at(sx * 6.2f, y + 2.6f, z + 2f).box(0.2f, 0.2f, 4f).pop()
                            g.color(0x8d6e4a).push().at(sx * 6.2f, y + 5.4f, z + 2f).box(0.2f, 0.2f, 4f).pop()
                        }
                        z += 4f
                    }
                    g.color(0x263238)
                    for (k in 0..2) g.push().at(sx * 7.6f, y + 0.4f, 8f + k * 10f).cylinder(0.3f, 0.4f, 0.8f, 6).pop()
                }
                // кран
                g.color(0x6d4c2f).push().at(-10.5f, y + 8f, 6f).box(0.7f, 16f, 0.7f).pop()
                g.color(0x6d4c2f).beam(Vector3(-10.5f, y + 15.5f, 6f), Vector3(-3f, y + 15.5f, 10f), 0.45f)
                g.color(0x3e2723).beam(Vector3(-10.5f, y + 9f, 6f), Vector3(-5f, y + 15.4f, 9.2f), 0.18f)
                g.color(0x212121).push().at(-3.2f, y + 12f, 10f).box(0.08f, 7f, 0.08f).pop()
                g.color(0x8d6e4a).push().at(-3.2f, y + 8.4f, 10f).box(1.6f, 1f, 1.6f).pop()
                // штабеля брёвен и бочки
                g.color(0xa1887f)
                for (k in 0 until 6) g.push().at(5.5f + (k % 3) * 0.9f, y + 0.6f + (k / 3) * 0.8f, -3f).rotX(90f).cylinder(0.42f, 0.42f, 4.5f, 6).pop()
                barrel(g, 2f, y, 1.5f); barrel(g, 3f, y, 1.8f); barrel(g, 2.5f, y, 0.6f, 0x8d6e4a)
                lantern(g, 6f, y, 2.5f); lantern(g, -6f, y + -0.3f, 33.5f); lantern(g, 6f, y - 0.3f, 33.5f)
            }
            "market" -> {
                platform(g, -14f, 14f, -5f, 9f)
                val aw = listOf(0xc62828 to 0xf5f0dc, 0x6a1b9a to 0xffe082, 0x1565c0 to 0xf5f5f5, 0xef6c00 to 0xfff3e0, 0xad1457 to 0xf8bbd0)
                for (k in 0 until 5) {
                    val cx = -10.8f + k * 5.4f
                    // прилавок
                    g.color(0x8d6e4a).push().at(cx, y + 0.8f, 4f).box(4.2f, 1.4f, 1.4f).pop()
                    g.color(0xa1887f).push().at(cx, y + 1.55f, 4f).box(4.4f, 0.15f, 1.6f).pop()
                    // столбы
                    g.color(0x5d4037)
                    for (sx in intArrayOf(-1, 1)) for (sz in intArrayOf(0, 1)) g.push().at(cx + sx * 2.1f, y + 2f, 1.2f + sz * 3.4f).box(0.22f, 4f, 0.22f).pop()
                    // полосатый навес
                    val (c1, c2) = aw[k]
                    for (st in 0 until 6) g.mat(Shaders.CLOTH).color(if (st % 2 == 0) c1 else c2).push().at(cx - 2.1f + 0.35f + st * 0.7f, y + 4.25f, 2.9f).rotX(-14f).box(0.7f, 0.14f, 4.4f).pop()
                    for (st in 0 until 6) g.mat(Shaders.CLOTH).color(if (st % 2 == 0) c1 else c2).push().at(cx - 2.1f + 0.35f + st * 0.7f, y + 3.7f, 5.15f).box(0.7f, 0.9f, 0.08f).pop()
                    g.mat(-1)
                    // товар
                    when (k) {
                        0 -> { g.color(0xd7b98a); for (m in 0..2) g.push().at(cx - 1.2f + m * 1.2f, y + 2.05f, 4f).scale(1f, 1.2f, 1f).ico(0.45f).pop() }
                        1 -> { barrel(g, cx - 1f, y + 1.6f, 4f); g.color(0x1b5e20); for (m in 0..2) g.push().at(cx + 0.6f + m * 0.45f, y + 2f, 4f).cylinder(0.14f, 0.16f, 0.8f, 6).pop() }
                        2 -> { val cs = intArrayOf(0xc62828, 0x1565c0, 0xf9a825, 0x2e7d32); for (m in 0..3) g.color(cs[m]).push().at(cx - 1.4f + m * 0.9f, y + 1.95f, 4f).rotX(90f).cylinder(0.35f, 0.35f, 1.3f, 6).pop() }
                        3 -> { val cs = intArrayOf(0xe65100, 0xb71c1c, 0xfbc02d); for (m in 0..2) { g.color(0x6d4c2f).push().at(cx - 1.2f + m * 1.2f, y + 1.8f, 4f).cylinder(0.5f, 0.35f, 0.3f, 8).pop(); g.color(cs[m]).push().at(cx - 1.2f + m * 1.2f, y + 2f, 4f).scale(1f, 0.5f, 1f).ico(0.45f).pop() } }
                        else -> { val cs = intArrayOf(0x8e24aa, 0xec407a, 0x26c6da); for (m in 0..2) g.color(cs[m]).push().at(cx - 1f + m * 1f, y + 2.6f, 4.2f).box(0.8f, 1.8f, 0.1f).pop() }
                    }
                    // ящики позади
                    g.color(0x8d6e4a).push().at(cx + 1.2f, y + 0.6f, 0.4f).box(1.1f, 1f, 1.1f).pop()
                }
                barrel(g, -13f, y, 7.5f); barrel(g, 13f, y, 7.5f, 0x5d4037)
                lantern(g, -13.3f, y, 1f); lantern(g, 13.3f, y, 1f); lantern(g, 0f, y, 8.4f)
            }
            "tavern" -> {
                platform(g, -9f, 9f, -5f, 9f)
                g.mat(Shaders.STONE).color(0x8a8379).push().at(0f, y + 0.4f, -0.5f).box(11.4f, 0.8f, 7.4f).pop()
                g.mat(Shaders.BRICK).color(0xc98a5e).push().at(0f, y + 4f, -0.5f).box(11f, 6.4f, 7f).pop()
                g.mat(-1)
                g.color(0x6d4c2f)
                for (sx in intArrayOf(-1, 1)) g.push().at(sx * 5.5f, y + 3.6f, 3f).box(0.4f, 7.2f, 0.4f).pop()
                g.color(0x6d4c2f).push().at(0f, y + 3.6f, 3f).box(11.2f, 0.35f, 0.4f).pop()
                roof(g, 0f, y + 7.2f, -0.5f, 11.8f, 7f, 0xa1442b)
                g.color(0x9e9e9e).push().at(3.5f, y + 9.5f, -2f).box(1.2f, 3f, 1.2f).pop()
                g.color(0x3e2723).push().at(0f, y + 1.5f, 3.05f).box(1.8f, 3f, 0.1f).pop()
                g.color(0xffcf6b)
                for (sx in intArrayOf(-1, 1)) { g.push().at(sx * 3f, y + 2f, 3.05f).box(1.6f, 1.3f, 0.1f).pop(); g.push().at(sx * 3f, y + 5.4f, 3.05f).box(1.4f, 1.1f, 0.1f).pop() }
                g.push().at(0f, y + 5.4f, 3.05f).box(1.4f, 1.1f, 0.1f).pop()
                // вывеска с кружкой
                g.color(0x3e2723).push().at(6.2f, y + 4.2f, 3.4f).box(1.8f, 0.2f, 0.2f).pop()
                g.color(0x6d4c2f).push().at(6.6f, y + 3.1f, 3.4f).box(0.2f, 1.8f, 1.8f).pop()
                g.color(0xffc107).push().at(6.75f, y + 3.0f, 3.4f).box(0.1f, 0.9f, 0.7f).pop()
                g.color(0xffffff).push().at(6.75f, y + 3.55f, 3.4f).box(0.12f, 0.25f, 0.8f).pop()
                // столы и бочки
                for (k in 0..1) {
                    val tx = -4f + k * 8f
                    g.color(0x6d4c2f).push().at(tx, y + 0.9f, 6.2f).cylinder(1.1f, 1.1f, 0.15f, 8).pop()
                    g.color(0x4e342e).push().at(tx, y + 0.45f, 6.2f).cylinder(0.15f, 0.15f, 0.9f, 5).pop()
                    g.color(0xffc107).push().at(tx + 0.3f, y + 1.15f, 6.2f).cylinder(0.15f, 0.15f, 0.35f, 6).pop()
                    for (sx in intArrayOf(-1, 1)) g.color(0x6d4c2f).push().at(tx + sx * 1.6f, y + 0.5f, 6.2f).box(0.5f, 0.2f, 1.6f).pop()
                }
                barrel(g, -7.5f, y, 2f); barrel(g, -7.5f, y, 3.1f); barrel(g, -7.5f, y + 1f, 2.5f)
                lantern(g, -8.4f, y, 8.3f); lantern(g, 8.4f, y, 8.3f)
            }
            "arsenal" -> {
                platform(g, -10f, 10f, -5f, 8f)
                g.mat(Shaders.STONE).color(0x8a8478).push().at(0f, y + 2.5f, 0f).box(16f, 5f, 8f).pop()
                g.color(0x6d675e)
                for (k in -3..3) g.push().at(k * 2.3f, y + 5.5f, 3.6f).box(1.2f, 1f, 0.8f).pop()
                g.mat(-1).color(0x5d5850).push().at(0f, y + 1.6f, 4.05f).box(3f, 3.2f, 0.1f).pop()
                g.color(0x3e2723).push().at(0f, y + 1.5f, 4.1f).box(2.4f, 3f, 0.1f).pop()
                // пушки на стене
                for (k in -2..2 step 2) {
                    g.color(0x222222).push().at(k * 2.3f + 1.15f, y + 5.6f, 4.4f).rotX(90f).cylinder(0.35f, 0.48f, 2.8f, 7).pop()
                    g.color(0x5d4037).push().at(k * 2.3f + 1.15f, y + 5.2f, 3.4f).box(1f, 0.5f, 1.4f).pop()
                }
                // мортира, ядра, порох
                g.color(0x263238).push().at(-6f, y + 0.9f, 6f).rotX(-30f).cylinder(0.9f, 0.7f, 1.6f, 8).pop()
                g.color(0x5d4037).push().at(-6f, y + 0.3f, 6f).box(1.8f, 0.6f, 1.8f).pop()
                g.color(0x262626)
                for (row in 0 until 3) for (k in 0 until 3 - row) g.push().at(4f + (k - (3 - row - 1) / 2f) * 0.6f, y + 0.55f + row * 0.5f, 6f).ico(0.3f).pop()
                barrel(g, 7.5f, y, 5.5f, 0x4e342e); barrel(g, 8.4f, y, 6.4f, 0x4e342e)
                g.color(0xc62828).push().at(7.95f, y + 1.05f, 5.95f).box(1.8f, 0.1f, 0.1f).pop()
                // флаг
                g.color(0x3e2723).push().at(-7.5f, y + 8f, -2.5f).box(0.25f, 11f, 0.25f).pop()
                g.color(0xc62828).push().at(-6.1f, y + 12.6f, -2.5f).box(2.6f, 1.6f, 0.08f).pop()
            }
            "fleet" -> {
                platform(g, -6f, 6f, -5f, 2f)
                g.color(0x9c7449).push().at(0f, y - 0.3f, 22f).box(4f, 0.45f, 40f).pop()
                var z = 3f
                while (z < 42f) {
                    for (sx in intArrayOf(-1, 1)) {
                        g.color(0x4e342e).push().at(sx * 1.8f, y - 2.8f, z).cylinder(0.3f, 0.3f, 5f, 6).pop()
                        g.color(0x263238).push().at(sx * 1.6f, y + 0.4f, z + 1f).cylinder(0.25f, 0.35f, 0.7f, 6).pop()
                    }
                    z += 6f
                }
                // будка смотрителя
                g.color(0x90a4ae).push().at(-2.5f, y + 1.8f, -2f).box(4f, 3.6f, 3.6f).pop()
                roof(g, -2.5f, y + 3.6f, -2f, 4.6f, 3.6f, 0x37474f)
                g.color(0xffcf6b).push().at(-2.5f, y + 2f, -0.15f).box(1f, 0.9f, 0.1f).pop()
                lantern(g, 1.6f, y - 0.3f, 41f); lantern(g, -1.6f, y - 0.3f, 20f)
                g.color(0xffffff).push().at(3f, y + 0.3f, 0f).cylinder(0.6f, 0.6f, 0.15f, 10).pop()
            }
            "paint" -> {
                platform(g, -10f, 10f, -5f, 7f)
                g.color(0xc8b28a).push().at(-4f, y + 3f, -1.5f).box(8f, 6f, 6f).pop()
                roof(g, -4f, y + 6f, -1.5f, 8.6f, 6f, 0x5d4037)
                g.color(0x3e2723).push().at(-4f, y + 1.6f, 1.55f).box(2.4f, 3.2f, 0.1f).pop()
                // верёвки с сушащимися парусами
                val cols = intArrayOf(0xc62828, 0x64b5f6, 0xffc107, 0x2e7d32, 0x262626, 0x6a1b9a)
                for (row in 0..1) {
                    val zz = 3f + row * 2.6f
                    for (sx in intArrayOf(0, 1)) g.color(0x4e342e).push().at(1f + sx * 8f, y + 3f, zz).box(0.25f, 6f, 0.25f).pop()
                    g.color(0x3e2723).beam(Vector3(1f, y + 5.8f, zz), Vector3(9f, y + 5.8f, zz), 0.06f)
                    for (k in 0..2) g.color(cols[row * 3 + k]).push().at(2.4f + k * 2.6f, y + 4.4f, zz).sail(2.2f, 2.8f, 0.3f).pop()
                }
                g.color(0xc62828).push().at(-8.5f, y + 0.4f, 4f).cylinder(0.4f, 0.35f, 0.8f, 8).pop()
                g.color(0x1565c0).push().at(-7.4f, y + 0.4f, 4.6f).cylinder(0.4f, 0.35f, 0.8f, 8).pop()
                g.color(0xffc107).push().at(-8f, y + 0.4f, 5.6f).cylinder(0.4f, 0.35f, 0.8f, 8).pop()
            }
        }
        return g.build()
    }
}
