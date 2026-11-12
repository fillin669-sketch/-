package com.seatrader.game

import com.badlogic.gdx.Preferences

/** Хранилище настроек в памяти: нужно для экспорта и импорта сохранения кодом. */
class MapPrefs : Preferences {
    val map = LinkedHashMap<String, Any>()

    override fun putBoolean(key: String, v: Boolean): Preferences { map[key] = v; return this }
    override fun putInteger(key: String, v: Int): Preferences { map[key] = v; return this }
    override fun putLong(key: String, v: Long): Preferences { map[key] = v; return this }
    override fun putFloat(key: String, v: Float): Preferences { map[key] = v; return this }
    override fun putString(key: String, v: String): Preferences { map[key] = v; return this }
    override fun put(vals: MutableMap<String, *>): Preferences { vals.forEach { (k, v) -> if (v != null) map[k] = v }; return this }
    override fun getBoolean(key: String) = getBoolean(key, false)
    override fun getInteger(key: String) = getInteger(key, 0)
    override fun getLong(key: String) = getLong(key, 0L)
    override fun getFloat(key: String) = getFloat(key, 0f)
    override fun getString(key: String) = getString(key, "")
    override fun getBoolean(key: String, d: Boolean) = (map[key] as? Boolean) ?: d
    override fun getInteger(key: String, d: Int) = (map[key] as? Number)?.toInt() ?: d
    override fun getLong(key: String, d: Long) = (map[key] as? Number)?.toLong() ?: d
    override fun getFloat(key: String, d: Float) = (map[key] as? Number)?.toFloat() ?: d
    override fun getString(key: String, d: String) = (map[key] as? String) ?: d
    override fun get(): MutableMap<String, *> = map
    override fun contains(key: String) = map.containsKey(key)
    override fun clear() = map.clear()
    override fun remove(key: String) { map.remove(key) }
    override fun flush() {}

    /** Текстовый код: ключ, тип и значение в base64. */
    fun encode(): String {
        val sb = StringBuilder("SW1|")
        for ((k, v) in map) {
            val t = when (v) { is Boolean -> "b"; is Int -> "i"; is Long -> "l"; is Float -> "f"; else -> "s" }
            sb.append(k).append('\u0001').append(t).append('\u0001').append(v.toString()).append('\u0002')
        }
        return java.util.Base64.getEncoder().encodeToString(sb.toString().toByteArray(Charsets.UTF_8))
    }

    companion object {
        fun decode(code: String): MapPrefs? = try {
            val text = String(java.util.Base64.getDecoder().decode(code.trim()), Charsets.UTF_8)
            if (!text.startsWith("SW1|")) null else MapPrefs().also { mp ->
                for (e in text.removePrefix("SW1|").split('\u0002')) {
                    val parts = e.split('\u0001')
                    if (parts.size != 3) continue
                    val (k, t, v) = parts
                    mp.map[k] = when (t) { "b" -> v.toBoolean(); "i" -> v.toInt(); "l" -> v.toLong(); "f" -> v.toFloat(); else -> v }
                }
            }
        } catch (e: Exception) { null }
    }
}
