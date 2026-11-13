package com.seatrader.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.Disposable

/** Морские песни: название и файл. Первая — народная, остальные написаны для игры. */
class Shanty(val title: String, val file: String, val desc: String)

val SHANTIES = listOf(
    Shanty("Пьяный матрос", "music/shanty1.ogg", "Народная английская шанти XIX века."),
    Shanty("Ветер в парусах", "music/shanty2.ogg", "Бодрая песня на подъём парусов."),
    Shanty("Домой, ребята", "music/shanty3.ogg", "Протяжная песня у кабестана."),
    Shanty("Веллерман", "music/shanty4.ogg", "Новозеландская китобойная песня 1860-х годов."),
    Shanty("Хей, тяни, Джо", "music/shanty5.ogg", "Народная «тянульная» шанти: на каждое «Джо» — рывок каната."),
    Shanty("Сбей его с ног", "music/shanty6.ogg", "Народная песня для подъёма якоря, в ритме вальса."),
    Shanty("Испанские леди", "music/shanty7.ogg", "Старинная английская матросская песня XVIII века."),
    Shanty("Прощай, Джонни", "music/shanty8.ogg", "Медленная песня последнего дня плавания."),
)

/**
 * Звук: эффекты, музыка и звуки природы.
 * Музыка плавно переключается: спокойная тема, песни команды или бой.
 * Природа: шум моря, ветер (от скорости и шторма), дождь, гром, чайки, скрип корпуса.
 */
class Sfx : Disposable {
    var sfxVolume = 0.9f
    var musicVolume = 0.6f
    private val sounds = HashMap<String, Sound?>()
    private val sea = load("music/sea.ogg")
    private val battle = load("music/battle.ogg")
    private val rain = load("music/rain.ogg")
    private val wind = load("music/wind.ogg")
    private val ambient = load("music/sea_ambient.ogg")
    private var shanty: Music? = null
    // все песни загружены заранее: смена песни без загрузки файла во время игры (без фризов)
    private val shantyMusic = arrayOfNulls<Music>(SHANTIES.size)
    var shantyIdx = -1
        private set
    private var shantyVol = 0f
    private var battleMix = 0f
    private var seaMix = 1f
    private var rainMix = 0f
    private var windMix = 0f

    init {
        for (name in listOf("cannon", "splash", "hit", "coin", "gem", "sink", "boost", "quest", "click",
            "thunder", "thunder_far", "roar", "repair", "fire", "chain", "treasure", "gull1", "gull2", "creak")) {
            sounds[name] = loadSound(name)
        }
        for (m in listOf(sea, battle, rain, wind, ambient)) m?.let { it.isLooping = true; it.volume = 0f; it.play() }
        for (i in SHANTIES.indices) shantyMusic[i] = load(SHANTIES[i].file)?.also { it.isLooping = false; it.volume = 0f }
    }

    private fun load(path: String): Music? = try { Gdx.audio.newMusic(Gdx.files.internal(path)) } catch (e: Exception) { null }

    private fun loadSound(name: String): Sound? {
        for (ext in listOf("wav", "ogg")) {
            val f = Gdx.files.internal("sfx/$name.$ext")
            if (f.exists()) return try { Gdx.audio.newSound(f) } catch (e: Exception) { null }
        }
        return null
    }

    private val lastPlay = HashMap<String, Long>()

    fun play(name: String, volume: Float = 1f, pitchVar: Float = 0.06f) {
        val v = volume * sfxVolume
        if (v <= 0.01f) return
        // один и тот же звук не чаще раза в 70 мс (иначе монеты и выстрелы сливаются в шум)
        val now = System.nanoTime()
        val last = lastPlay[name] ?: 0L
        if (now - last < 70_000_000L) return
        lastPlay[name] = now
        sounds[name]?.play(v.coerceIn(0f, 1f), 1f + (Math.random().toFloat() - 0.5f) * pitchVar, 0f)
    }

    /**
     * combat — идёт бой; raining — сила дождя 0..1; windy — сила ветра в ушах 0..1;
     * wantShanty — индекс песни, которую должна петь команда (или -1).
     */
    fun update(dt: Float, combat: Boolean, raining: Float, windy: Float, wantShanty: Int, paused: Boolean) {
        battleMix += ((if (combat) 1f else 0f) - battleMix) * minOf(1f, dt * 0.8f)
        rainMix += (raining - rainMix) * minOf(1f, dt)
        windMix += (windy - windMix) * minOf(1f, dt * 0.7f)
        val singing = wantShanty >= 0 && !combat
        // смена песни: сначала затихает старая
        if (shanty != null && (wantShanty != shantyIdx || !singing)) {
            shantyVol -= dt * 0.6f
            if (shantyVol <= 0f) { shanty?.pause(); shanty = null; shantyIdx = -1; shantyVol = 0f }
        } else if (shanty == null && singing) {
            shanty = shantyMusic.getOrNull(wantShanty)?.also { it.volume = 0f; try { it.position = 0f } catch (_: Exception) { }; it.play() }
            shantyIdx = wantShanty
            shantyVol = 0f
        } else if (shanty != null) {
            shantyVol = minOf(1f, shantyVol + dt * 0.5f)
        }
        val shantyOn = shanty != null && shantyVol > 0f
        seaMix += ((if (shantyOn || combat) 0f else 1f) - seaMix) * minOf(1f, dt * 0.5f)
        val k = if (paused) 0.4f else 1f
        sea?.volume = musicVolume * seaMix * (1 - battleMix) * 0.65f * k
        battle?.volume = musicVolume * battleMix * 0.75f * k
        shanty?.volume = musicVolume * shantyVol * (1 - battleMix) * 0.95f * k
        rain?.volume = sfxVolume * rainMix * 0.75f * k
        wind?.volume = sfxVolume * (0.06f + windMix * 0.32f) * k
        ambient?.volume = sfxVolume * 0.5f * k
    }

    /** Песня закончилась — можно ставить следующую. */
    val shantyFinished get() = shanty?.let { !it.isPlaying && shantyVol >= 1f } ?: false

    fun pauseAll() { for (m in listOf(sea, battle, rain, wind, ambient, shanty)) m?.pause() }
    fun resumeAll() { for (m in listOf(sea, battle, rain, wind, ambient, shanty)) m?.play() }

    override fun dispose() {
        sounds.values.forEach { it?.dispose() }
        for (m in listOf(sea, battle, rain, wind, ambient)) m?.dispose()
        for (m in shantyMusic) m?.dispose()
    }
}
