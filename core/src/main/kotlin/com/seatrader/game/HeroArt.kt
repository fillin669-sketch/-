package com.seatrader.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion

/** Портреты капитанов (картинки в assets/captains). */
object HeroArt {
    private val tex = HashMap<String, Texture>()

    fun texture(h: Hero): Texture = tex.getOrPut(h.id) {
        Texture(Gdx.files.internal("captains/${h.id}.jpg")).also { it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
    }

    fun full(h: Hero) = TextureRegion(texture(h))

    fun face(h: Hero): TextureRegion {
        val t = texture(h)
        val f = h.face
        return TextureRegion(t, (f[0] * t.width).toInt(), (f[1] * t.height).toInt(), (f[2] * t.width).toInt(), (f[2] * t.width).toInt())
    }
}
