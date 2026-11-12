package com.seatrader.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable

/** Освещение и погода, общие для воды и сцены. */
class Sky {
    val sunDir = floatArrayOf(0.4f, 1f, 0.3f)
    val sunCol = floatArrayOf(1f, 0.95f, 0.85f)
    val fog = floatArrayOf(0.56f, 0.77f, 0.91f)
    var day = 1f        // 1 — день, 0 — ночь
    var storm = 0f      // 0..1
    var fogNear = 300f
    var fogFar = 560f
    val deep = floatArrayOf(0.11f, 0.42f, 0.70f)
    val mid = floatArrayOf(0.19f, 0.57f, 0.83f)
}

/** Море: сетка едет за камерой; волны, пена, мелководье и свет считаются в шейдере. */
class Water : Disposable {
    private val size = 1300f
    private val seg = 130
    private val mesh: Mesh
    private val shader: ShaderProgram
    private val islandBuf = FloatArray(12 * 3)

    init {
        val verts = FloatArray((seg + 1) * (seg + 1) * 3)
        var k = 0
        for (j in 0..seg) for (i in 0..seg) {
            verts[k++] = (i.toFloat() / seg - 0.5f) * size
            verts[k++] = 0f
            verts[k++] = (j.toFloat() / seg - 0.5f) * size
        }
        val idx = ShortArray(seg * seg * 6)
        k = 0
        for (j in 0 until seg) for (i in 0 until seg) {
            val a = (j * (seg + 1) + i).toShort()
            val b = (a + 1).toShort()
            val c = (a + seg + 1).toShort()
            val d = (c + 1).toShort()
            idx[k++] = a; idx[k++] = c; idx[k++] = b
            idx[k++] = b; idx[k++] = c; idx[k++] = d
        }
        mesh = Mesh(true, verts.size / 3, idx.size, VertexAttribute.Position())
        mesh.setVertices(verts)
        mesh.setIndices(idx)

        ShaderProgram.pedantic = false
        shader = ShaderProgram(VERT, FRAG)
        if (!shader.isCompiled) Gdx.app.error("Water", shader.log)
    }

    /** [islands] — ближайшие острова: x, z, r (до 12 штук). */
    fun render(cam: Camera, cx: Float, cz: Float, time: Float, sky: Sky, islands: List<FloatArray>,
               shadowTex: com.badlogic.gdx.graphics.GLTexture? = null, shadowPV: com.badlogic.gdx.math.Matrix4? = null) {
        val ox = Math.round(cx / 10f) * 10f
        val oz = Math.round(cz / 10f) * 10f
        islandBuf.fill(0f)
        for ((i, isl) in islands.take(12).withIndex()) {
            islandBuf[i * 3] = isl[0]; islandBuf[i * 3 + 1] = isl[1]; islandBuf[i * 3 + 2] = isl[2]
        }
        // пустые слоты — далеко за пределами мира
        for (i in islands.size until 12) { islandBuf[i * 3] = 99999f; islandBuf[i * 3 + 2] = 1f }
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        shader.bind()
        shader.setUniformMatrix("u_projTrans", cam.combined)
        shader.setUniformf("u_offset", ox, oz)
        shader.setUniformf("u_time", time)
        shader.setUniformf("u_amp", waveAmp)
        shader.setUniformf("u_cam", cam.position.x, cam.position.y, cam.position.z)
        shader.setUniformf("u_fog", sky.fog[0], sky.fog[1], sky.fog[2])
        shader.setUniformf("u_fogRange", sky.fogNear, sky.fogFar)
        shader.setUniformf("u_sunDir", sky.sunDir[0], sky.sunDir[1], sky.sunDir[2])
        shader.setUniformf("u_sunCol", sky.sunCol[0], sky.sunCol[1], sky.sunCol[2])
        shader.setUniformf("u_day", sky.day)
        shader.setUniformf("u_storm", sky.storm)
        shader.setUniformf("u_deep", sky.deep[0], sky.deep[1], sky.deep[2])
        shader.setUniformf("u_mid", sky.mid[0], sky.mid[1], sky.mid[2])
        shader.setUniform3fv("u_isl", islandBuf, 0, islandBuf.size)
        if (shadowTex != null && shadowPV != null) {
            shadowTex.bind(1)
            shader.setUniformi("u_shadowTex", 1)
            shader.setUniformMatrix("u_shadowPV", shadowPV)
            shader.setUniformf("u_shadowOn", 1f)
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)
        } else shader.setUniformf("u_shadowOn", 0f)
        mesh.render(shader, GL20.GL_TRIANGLES)
    }

    override fun dispose() {
        mesh.dispose()
        shader.dispose()
    }

    companion object {
        private const val VERT = """
attribute vec3 a_position;
uniform mat4 u_projTrans;
uniform vec2 u_offset;
uniform float u_time;
uniform float u_amp;
uniform vec3 u_isl[12];
uniform mat4 u_shadowPV;
varying vec3 v_world;
varying vec3 v_normal;
varying float v_h;
varying float v_shoreD;
varying vec3 v_shadowUv;
void main() {
    vec3 w = a_position + vec3(u_offset.x, 0.0, u_offset.y);
    float a1 = w.x * 0.08 + u_time * 1.1;
    float a2 = w.z * 0.11 + u_time * 0.9;
    float a3 = (w.x + w.z) * 0.05 + u_time * 0.7;
    float a4 = (w.x - w.z) * 0.031 + u_time * 0.55;
    float h = sin(a1) * 0.35 + sin(a2) * 0.3 + sin(a3) * 0.4 + sin(a4) * 0.35;
    w.y = h * u_amp;
    float dx = (cos(a1) * 0.028 + cos(a3) * 0.02 + cos(a4) * 0.01085) * u_amp;
    float dz = (cos(a2) * 0.033 + cos(a3) * 0.02 - cos(a4) * 0.01085) * u_amp;
    v_normal = normalize(vec3(-dx * 5.0, 1.0, -dz * 5.0));
    v_world = w;
    v_h = h;
    float sd = 1000.0;
    for (int i = 0; i < 12; i++) {
        vec3 isl = u_isl[i];
        sd = min(sd, length(w.xz - isl.xy) - isl.z);
    }
    v_shoreD = sd;
    vec4 sp = u_shadowPV * vec4(w, 1.0);
    v_shadowUv = (sp.xyz / sp.w) * 0.5 + 0.5;
    gl_Position = u_projTrans * vec4(w, 1.0);
}
"""
        private const val FRAG = """
#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif
uniform float u_time;
uniform vec3 u_cam;
uniform vec3 u_fog;
uniform vec2 u_fogRange;
uniform vec3 u_sunDir;
uniform vec3 u_sunCol;
uniform float u_day;
uniform float u_storm;
uniform vec3 u_deep;
uniform vec3 u_mid;
uniform sampler2D u_shadowTex;
uniform float u_shadowOn;
varying vec3 v_world;
varying vec3 v_normal;
varying float v_h;
varying float v_shoreD;
varying vec3 v_shadowUv;
float shadowAt(vec2 uv, float z) {
    vec4 p = texture2D(u_shadowTex, uv);
    float d = dot(p, vec4(1.0, 1.0 / 255.0, 1.0 / 65025.0, 1.0 / 16581375.0));
    return step(z, d + 0.002);
}
float shadow() {
    if (u_shadowOn < 0.5) return 1.0;
    vec3 s = v_shadowUv;
    if (s.x < 0.0 || s.x > 1.0 || s.y < 0.0 || s.y > 1.0 || s.z > 1.0) return 1.0;
    float o = 1.0 / 1024.0;
    return (shadowAt(s.xy, s.z) + shadowAt(s.xy + vec2(o, 0.0), s.z) + shadowAt(s.xy - vec2(o, 0.0), s.z)
          + shadowAt(s.xy + vec2(0.0, o), s.z) + shadowAt(s.xy - vec2(0.0, o), s.z)) / 5.0;
}
float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}
void main() {
    vec2 p = mod(v_world.xz, 4000.0);
    float tn = u_time;
    // мелкая рябь в нормали
    float rn = noise(p * 0.35 + vec2(tn * 0.6, tn * 0.4));
    float rn2 = noise(p * 1.1 - vec2(tn * 0.9, tn * 0.3));
    vec3 n = normalize(v_normal + vec3(rn - 0.5 + (rn2 - 0.5) * 0.6, 0.0, 0.5 - rn + (0.5 - rn2) * 0.4) * 0.32);
    vec3 sun = normalize(u_sunDir);

    // Мелководье и прибой возле островов (расстояние до берега — из вершинного шейдера)
    float d0 = v_shoreD;
    float patches = noise(p * 0.03 + vec2(tn * 0.02, 0.0));
    float shore = smoothstep(30.0, 2.0, d0);
    float wave = sin(d0 * 0.6 - tn * 2.2 + patches * 5.0);
    float surf = smoothstep(9.0, 0.0, d0) * smoothstep(0.4, 1.0, wave) * 0.8 + smoothstep(2.5, -1.0, d0);

    vec3 deep = mix(u_deep, vec3(0.20, 0.30, 0.38), u_storm);
    vec3 mid = mix(u_mid, vec3(0.26, 0.36, 0.44), u_storm);
    vec3 shallow = vec3(0.25, 0.80, 0.82);
    vec3 col = mix(deep, mid, smoothstep(0.35, 0.8, patches));
    col = mix(col, shallow, shore * 0.85);

    // Каустика: световая рябь на мелководье
    float c1 = noise(p * 0.45 + vec2(tn * 0.35, tn * 0.2));
    float c2 = noise(p * 0.45 * 1.37 - vec2(tn * 0.28, -tn * 0.31));
    float caus = smoothstep(0.18, 0.0, abs(c1 - c2)) * smoothstep(0.35, 0.9, shore);
    col += vec3(0.20, 0.27, 0.24) * caus * 0.35 * u_day * (1.0 - u_storm);

    // Отражение неба (Френель): у горизонта небо светлее
    vec3 v = normalize(u_cam - v_world);
    float fres = pow(1.0 - max(dot(n, v), 0.0), 3.0);
    vec3 skyc = mix(u_fog * 1.08, vec3(0.62, 0.78, 0.95) * (0.3 + 0.7 * u_day), 0.35 * (1.0 - u_storm));
    col = mix(col, skyc, fres * 0.6);
    // подсветка гребней: свет проходит сквозь тонкую воду
    col += vec3(0.02, 0.22, 0.20) * smoothstep(0.1, 1.2, v_h) * 0.45 * u_day * (1.0 - shore * 0.5);

    // Освещение
    float l = clamp(dot(n, sun), 0.0, 1.0);
    col *= (0.55 + 0.45 * u_day) * (0.85 + 0.25 * l);

    // Тени облаков
    float cloud = smoothstep(0.45, 0.75, noise(p * 0.006 + vec2(tn * 0.012, tn * 0.006)));
    col *= 1.0 - cloud * (0.18 + 0.25 * u_storm);

    // Тени кораблей и островов
    float sh = shadow();
    col *= 0.62 + 0.38 * sh;

    // Солнечные блики
    float spec = pow(max(dot(reflect(-sun, n), v), 0.0), 60.0);
    col += u_sunCol * spec * (0.45 * u_day) * (1.0 - u_storm * 0.8) * sh;
    col += vec3(smoothstep(0.93, 0.99, rn) * 0.15 * u_day);

    // Пена на гребнях волн и у берега
    float crest = smoothstep(0.75, 1.25, v_h) * (0.35 + 0.65 * rn);
    float foam = clamp(crest * (0.5 + u_storm * 0.8) + surf, 0.0, 1.0);
    col = mix(col, vec3(0.93, 0.97, 1.0) * (0.6 + 0.4 * u_day), foam * 0.85);

    float d = distance(u_cam, v_world);
    col = mix(col, u_fog, smoothstep(u_fogRange.x, u_fogRange.y, d));
    gl_FragColor = vec4(col, 1.0);
}
"""
    }
}
