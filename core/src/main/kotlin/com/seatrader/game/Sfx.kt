package com.seatrader.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.Disposable

class Sfx : Disposable {
    var enabled = true
    private val sounds = HashMap<String, Sound?>()

    init {
        for (name in listOf("cannon", "splash", "hit", "coin", "gem", "sink", "boost", "quest", "click")) {
            sounds[name] = try { Gdx.audio.newSound(Gdx.files.internal("sfx/$name.wav")) } catch (e: Exception) { null }
        }
    }

    fun play(name: String, volume: Float = 1f) {
        if (!enabled || volume <= 0.01f) return
        sounds[name]?.play(volume.coerceIn(0f, 1f), 1f + (Math.random().toFloat() - 0.5f) * 0.1f, 0f)
    }

    override fun dispose() = sounds.values.forEach { it?.dispose() }
}
