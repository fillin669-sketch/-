package com.seatrader.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.Disposable

/** Звуковые эффекты и музыка с плавным переходом «море ↔ бой». */
class Sfx : Disposable {
    var sfxVolume = 0.9f
    var musicVolume = 0.6f
    private val sounds = HashMap<String, Sound?>()
    private val sea = load("music/sea.ogg")
    private val battle = load("music/battle.ogg")
    private val rain = load("music/rain.ogg")
    private var battleMix = 0f
    private var rainMix = 0f

    init {
        for (name in listOf("cannon", "splash", "hit", "coin", "gem", "sink", "boost", "quest", "click",
            "thunder", "roar", "repair", "fire", "chain", "treasure")) {
            sounds[name] = try { Gdx.audio.newSound(Gdx.files.internal("sfx/$name.wav")) } catch (e: Exception) { null }
        }
        for (m in listOf(sea, battle, rain)) m?.let { it.isLooping = true; it.volume = 0f; it.play() }
    }

    private fun load(path: String): Music? = try { Gdx.audio.newMusic(Gdx.files.internal(path)) } catch (e: Exception) { null }

    fun play(name: String, volume: Float = 1f) {
        val v = volume * sfxVolume
        if (v <= 0.01f) return
        sounds[name]?.play(v.coerceIn(0f, 1f), 1f + (Math.random().toFloat() - 0.5f) * 0.1f, 0f)
    }

    /** combat — идёт ли бой; raining — сила дождя 0..1. */
    fun update(dt: Float, combat: Boolean, raining: Float, paused: Boolean) {
        battleMix += ((if (combat) 1f else 0f) - battleMix) * minOf(1f, dt * 0.8f)
        rainMix += (raining - rainMix) * minOf(1f, dt)
        val k = if (paused) 0.4f else 1f
        sea?.volume = musicVolume * (1 - battleMix) * 0.7f * k
        battle?.volume = musicVolume * battleMix * 0.75f * k
        rain?.volume = sfxVolume * rainMix * 0.6f * k
    }

    fun pauseAll() { for (m in listOf(sea, battle, rain)) m?.pause() }
    fun resumeAll() { for (m in listOf(sea, battle, rain)) m?.play() }

    override fun dispose() {
        sounds.values.forEach { it?.dispose() }
        for (m in listOf(sea, battle, rain)) m?.dispose()
    }
}
