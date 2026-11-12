package com.seatrader.game

import org.json.JSONArray
import org.json.JSONObject

/** Сохранение игры в JSON (хранится в SharedPreferences). */
object Save {
    fun toJson(g: Game): String = JSONObject().apply {
        put("v", 1)
        put("gold", g.gold)
        put("day", g.day)
        put("port", g.port)
        put("hold", g.holdLevel)
        put("sail", g.sailLevel)
        put("cannons", g.cannons)
        put("hull", g.hull)
        put("pirates", g.piratesBeaten)
        put("voyages", g.voyages)
        put("over", g.over)
        put("sunk", g.sunk)
        put("cargo", JSONArray(g.cargo.toList()))
        put("paid", JSONArray(g.paid.toList()))
        put("price", matrix(g.price.map { it.toList() }))
        put("baseEq", matrix(g.baseEq.map { it.toList() }))
        put("shock", matrix(g.shock.map { it.toList() }))
        put("shockUntil", matrix(g.shockUntil.map { it.toList() }))
        put("news", JSONArray(g.news))
    }.toString()

    private fun matrix(rows: List<List<Number>>) = JSONArray().apply { rows.forEach { put(JSONArray(it)) } }

    fun fromJson(s: String): Game? = try {
        val o = JSONObject(s)
        Game().apply {
            gold = o.getInt("gold")
            day = o.getInt("day")
            port = o.getInt("port")
            holdLevel = o.getInt("hold")
            sailLevel = o.getInt("sail")
            cannons = o.getInt("cannons")
            hull = o.getInt("hull")
            piratesBeaten = o.optInt("pirates")
            voyages = o.optInt("voyages")
            over = o.getBoolean("over")
            sunk = o.getBoolean("sunk")
            val c = o.getJSONArray("cargo")
            val pd = o.getJSONArray("paid")
            for (i in cargo.indices) {
                cargo[i] = c.getInt(i)
                paid[i] = pd.getDouble(i)
            }
            readDoubles(o.getJSONArray("price"), price)
            readDoubles(o.getJSONArray("baseEq"), baseEq)
            readDoubles(o.getJSONArray("shock"), shock)
            val su = o.getJSONArray("shockUntil")
            for (p in shockUntil.indices) {
                val row = su.getJSONArray(p)
                for (gi in shockUntil[p].indices) shockUntil[p][gi] = row.getInt(gi)
            }
            news.clear()
            val n = o.getJSONArray("news")
            for (i in 0 until n.length()) news += n.getString(i)
        }
    } catch (e: Exception) {
        null
    }

    private fun readDoubles(arr: JSONArray, target: Array<DoubleArray>) {
        for (p in target.indices) {
            val row = arr.getJSONArray(p)
            for (g in target[p].indices) target[p][g] = row.getDouble(g)
        }
    }
}
