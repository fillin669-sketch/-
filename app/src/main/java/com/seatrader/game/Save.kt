package com.seatrader.game

import org.json.JSONArray
import org.json.JSONObject

/** Сохранение игры в JSON (хранится в SharedPreferences). */
object Save {
    fun toJson(g: Game): String = JSONObject().apply {
        put("v", 2)
        put("gold", g.gold)
        put("day", g.day)
        put("port", g.port)
        put("ship", g.shipModel)
        put("armor", g.armorLevel)
        put("spent", g.upgradeSpent)
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
        put("atSea", g.atSea)
        put("shipX", g.shipX.toDouble())
        put("shipY", g.shipY.toDouble())
        put("heading", g.heading.toDouble())
        put("dayProgress", g.dayProgress)
        put("boostUntil", g.boostUntil)
        put("cooldown", g.eventCooldown)
    }.toString()

    private fun matrix(rows: List<List<Number>>) = JSONArray().apply { rows.forEach { put(JSONArray(it)) } }

    fun fromJson(s: String): Game? = try {
        val o = JSONObject(s)
        Game().apply {
            gold = o.getInt("gold")
            day = o.getInt("day")
            port = o.getInt("port")
            shipModel = o.optInt("ship", 0).coerceIn(0, SHIPS.lastIndex)
            armorLevel = o.optInt("armor", 0).coerceIn(0, MAX_UPGRADE)
            upgradeSpent = o.optInt("spent", 0)
            holdLevel = o.getInt("hold").coerceIn(0, MAX_UPGRADE)
            sailLevel = o.getInt("sail").coerceIn(0, MAX_UPGRADE)
            cannons = o.getInt("cannons").coerceAtMost(model.cannonSlots)
            hull = o.getInt("hull").coerceAtMost(maxHull)
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
            atSea = o.optBoolean("atSea", false)
            shipX = o.optDouble("shipX", PORTS[port].x.toDouble()).toFloat()
            shipY = o.optDouble("shipY", PORTS[port].y + 0.05).toFloat()
            heading = o.optDouble("heading", -Math.PI / 2).toFloat()
            dayProgress = o.optDouble("dayProgress", 0.0)
            boostUntil = o.optInt("boostUntil", 0)
            eventCooldown = o.optInt("cooldown", 3)
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
