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
        g.color(0xbfa77a).push().at(0f, -1.6f, 0f).cylinder(r * 1.06f, r * 1.12f, 3.2f, 24, j).pop()
        g.color(0xe6d3a3).push().at(0f, 0.2f, 0f).cylinder(r * 0.97f, r * 1.05f, 2.2f, 24, j).pop()
        g.color(0x6fae45).push().at(0f, 2.2f, 0f).cylinder(r * 0.84f, r * 0.92f, 3f, 24, j).pop()
        // площадь в центре
        g.color(0xb9a585).push().at(0f, 3.72f, 0f).cylinder(r * 0.22f, r * 0.24f, 0.1f, 14).pop()
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
                g.color(0xe8dcc0).push().at(0f, 2.2f * s, 0f).box(7f * s, 4.4f * s, 6f * s).pop()
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
                g.color(0x8a8478)
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
        g.color(0xbfe3f2).push().scale(1.4f, 0.55f, 1.2f).ico(6f).pop()
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
}
