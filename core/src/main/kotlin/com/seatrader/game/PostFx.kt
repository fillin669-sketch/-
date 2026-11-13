package com.seatrader.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable

/**
 * Постобработка: сцена рисуется в кадровый буфер, затем
 * свечение ярких мест (bloom), цветокоррекция, виньетка и сглаживание FXAA.
 */
class PostFx : Disposable {
    private var scene: FrameBuffer? = null
    private var bloomA: FrameBuffer? = null
    private var bloomB: FrameBuffer? = null
    private var w = 0
    private var h = 0
    private val quad: Mesh = Mesh(true, 4, 6, VertexAttribute.Position(), VertexAttribute.TexCoords(0)).also {
        it.setVertices(floatArrayOf(-1f, -1f, 0f, 0f, 0f, 1f, -1f, 0f, 1f, 0f, 1f, 1f, 0f, 1f, 1f, -1f, 1f, 0f, 0f, 1f))
        it.setIndices(shortArrayOf(0, 1, 2, 2, 3, 0))
    }
    private val bright = program(BRIGHT)
    private val blur = program(BLUR)
    private val composite = program(COMPOSITE)

    // параметры цветокоррекции (меняются по времени суток)
    var exposure = 1.0f
    var saturation = 1.12f
    var contrast = 1.06f
    var warmth = 0f
    var bloom = 0.55f
    var vignette = 0.35f
    /** Доля разрешения экрана для сцены (на телефоне меньше 1 — экономит видеокарту). */
    var scale = 1f
    private var outW = 0
    private var maxTex = 0
    private var outH = 0

    private fun program(frag: String): ShaderProgram {
        ShaderProgram.pedantic = false
        val p = ShaderProgram(VERT, frag)
        if (!p.isCompiled) Gdx.app.error("PostFx", p.log)
        return p
    }

    private fun ensure() {
        outW = Gdx.graphics.backBufferWidth; outH = Gdx.graphics.backBufferHeight
        if (maxTex == 0) {
            val buf = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(16)
            Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_SIZE, buf)
            maxTex = buf.get(0).coerceAtLeast(2048)
        }
        val sc = minOf(scale, maxTex.toFloat() / outW, maxTex.toFloat() / outH)
        val nw = (outW * sc).toInt().coerceAtLeast(64); val nh = (outH * sc).toInt().coerceAtLeast(64)
        if (nw == w && nh == h && scene != null) return
        dispose(false)
        w = nw; h = nh
        scene = FrameBuffer(Pixmap.Format.RGBA8888, w, h, true).also {
            it.colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        }
        val bw = (w / 4).coerceAtLeast(16); val bh = (h / 4).coerceAtLeast(16)
        bloomA = FrameBuffer(Pixmap.Format.RGBA8888, bw, bh, false).also { it.colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
        bloomB = FrameBuffer(Pixmap.Format.RGBA8888, bw, bh, false).also { it.colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
    }

    /** Начать рисование сцены в буфер. */
    fun begin() {
        ensure()
        scene!!.begin()
    }

    /** Закончить сцену и вывести её на экран с эффектами. */
    fun end() {
        val sc = scene!!; val a = bloomA!!; val b = bloomB!!
        sc.end()
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        // 1) яркие места в уменьшенный буфер
        a.begin()
        sc.colorBufferTexture.bind(0)
        bright.bind(); bright.setUniformi("u_tex", 0); bright.setUniformf("u_threshold", 0.72f)
        quad.render(bright, GL20.GL_TRIANGLES)
        a.end()
        // 2) размытие по горизонтали и вертикали
        val px = 1f / a.width; val py = 1f / a.height
        b.begin(); a.colorBufferTexture.bind(0)
        blur.bind(); blur.setUniformi("u_tex", 0); blur.setUniformf("u_dir", px * 1.5f, 0f)
        quad.render(blur, GL20.GL_TRIANGLES); b.end()
        a.begin(); b.colorBufferTexture.bind(0)
        blur.bind(); blur.setUniformi("u_tex", 0); blur.setUniformf("u_dir", 0f, py * 1.5f)
        quad.render(blur, GL20.GL_TRIANGLES); a.end()
        // 3) сборка на экран
        Gdx.gl.glViewport(0, 0, outW, outH)
        a.colorBufferTexture.bind(1)
        sc.colorBufferTexture.bind(0)
        composite.bind()
        composite.setUniformi("u_tex", 0)
        composite.setUniformi("u_bloom", 1)
        composite.setUniformf("u_texel", 1f / w, 1f / h)
        composite.setUniformf("u_exposure", exposure)
        composite.setUniformf("u_saturation", saturation)
        composite.setUniformf("u_contrast", contrast)
        composite.setUniformf("u_warmth", warmth)
        composite.setUniformf("u_bloomAmt", bloom)
        composite.setUniformf("u_vignette", vignette)
        quad.render(composite, GL20.GL_TRIANGLES)
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)
    }

    private fun dispose(all: Boolean) {
        scene?.dispose(); bloomA?.dispose(); bloomB?.dispose()
        scene = null; bloomA = null; bloomB = null
        if (all) { quad.dispose(); bright.dispose(); blur.dispose(); composite.dispose() }
    }

    override fun dispose() = dispose(true)

    companion object {
        private const val VERT = """
attribute vec3 a_position;
attribute vec2 a_texCoord0;
varying vec2 v_uv;
void main() { v_uv = a_texCoord0; gl_Position = vec4(a_position.xy, 0.0, 1.0); }
"""
        private const val HEAD = """
#ifdef GL_ES
precision mediump float;
#endif
varying vec2 v_uv;
uniform sampler2D u_tex;
"""
        private const val BRIGHT = HEAD + """
uniform float u_threshold;
void main() {
    vec2 o = vec2(0.0015, 0.0015);
    vec3 c = (texture2D(u_tex, v_uv + o).rgb + texture2D(u_tex, v_uv - o).rgb
            + texture2D(u_tex, v_uv + vec2(o.x, -o.y)).rgb + texture2D(u_tex, v_uv - vec2(o.x, -o.y)).rgb) * 0.25;
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    gl_FragColor = vec4(c * smoothstep(u_threshold, u_threshold + 0.25, l), 1.0);
}
"""
        private const val BLUR = HEAD + """
uniform vec2 u_dir;
void main() {
    vec3 c = texture2D(u_tex, v_uv).rgb * 0.227;
    c += (texture2D(u_tex, v_uv + u_dir).rgb + texture2D(u_tex, v_uv - u_dir).rgb) * 0.316;
    c += (texture2D(u_tex, v_uv + u_dir * 2.3).rgb + texture2D(u_tex, v_uv - u_dir * 2.3).rgb) * 0.07;
    gl_FragColor = vec4(c, 1.0);
}
"""
        private const val COMPOSITE = HEAD + """
uniform sampler2D u_bloom;
uniform vec2 u_texel;
uniform float u_exposure;
uniform float u_saturation;
uniform float u_contrast;
uniform float u_warmth;
uniform float u_bloomAmt;
uniform float u_vignette;
// сглаживание краёв (упрощённый FXAA)
vec3 fxaa(vec2 uv) {
    vec3 rgbNW = texture2D(u_tex, uv + vec2(-1.0, -1.0) * u_texel).rgb;
    vec3 rgbNE = texture2D(u_tex, uv + vec2(1.0, -1.0) * u_texel).rgb;
    vec3 rgbSW = texture2D(u_tex, uv + vec2(-1.0, 1.0) * u_texel).rgb;
    vec3 rgbSE = texture2D(u_tex, uv + vec2(1.0, 1.0) * u_texel).rgb;
    vec3 rgbM = texture2D(u_tex, uv).rgb;
    vec3 luma = vec3(0.299, 0.587, 0.114);
    float lNW = dot(rgbNW, luma); float lNE = dot(rgbNE, luma);
    float lSW = dot(rgbSW, luma); float lSE = dot(rgbSE, luma); float lM = dot(rgbM, luma);
    float lMin = min(lM, min(min(lNW, lNE), min(lSW, lSE)));
    float lMax = max(lM, max(max(lNW, lNE), max(lSW, lSE)));
    vec2 dir = vec2(-((lNW + lNE) - (lSW + lSE)), ((lNW + lSW) - (lNE + lSE)));
    float reduce = max((lNW + lNE + lSW + lSE) * 0.03125, 0.0078125);
    float rcp = 1.0 / (min(abs(dir.x), abs(dir.y)) + reduce);
    dir = clamp(dir * rcp, vec2(-8.0), vec2(8.0)) * u_texel;
    vec3 a = 0.5 * (texture2D(u_tex, uv + dir * (1.0 / 3.0 - 0.5)).rgb + texture2D(u_tex, uv + dir * (2.0 / 3.0 - 0.5)).rgb);
    vec3 b = a * 0.5 + 0.25 * (texture2D(u_tex, uv + dir * -0.5).rgb + texture2D(u_tex, uv + dir * 0.5).rgb);
    float lB = dot(b, luma);
    return (lB < lMin || lB > lMax) ? a : b;
}
void main() {
    vec3 c = fxaa(v_uv);
    c += texture2D(u_bloom, v_uv).rgb * u_bloomAmt;
    c *= u_exposure;
    // мягкая плёночная кривая
    c = c / (1.0 + c * 0.18) * 1.18;
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(l), c, u_saturation);
    c = (c - 0.5) * u_contrast + 0.5;
    // тёплые света и холодные тени
    c += vec3(0.06, 0.02, -0.05) * u_warmth * l + vec3(-0.02, 0.0, 0.03) * (1.0 - l) * (0.5 + 0.5 * u_warmth);
    vec2 d = v_uv - 0.5;
    c *= 1.0 - dot(d, d) * u_vignette * 1.6;
    gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""
    }
}
