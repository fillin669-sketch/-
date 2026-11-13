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
    private val seg = 240
    private val mesh: Mesh
    private val shader: ShaderProgram
    private val islandBuf = FloatArray(12 * 3)

    init {
        // сетка гуще у камеры (клетка ~1,4 м) и реже к краям (~13 м)
        fun warp(u: Float) = u * (0.25f + 0.75f * u * u)
        val verts = FloatArray((seg + 1) * (seg + 1) * 3)
        var k = 0
        for (j in 0..seg) for (i in 0..seg) {
            verts[k++] = warp(i.toFloat() / seg * 2 - 1) * size / 2
            verts[k++] = 0f
            verts[k++] = warp(j.toFloat() / seg * 2 - 1) * size / 2
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
               shadowTex: com.badlogic.gdx.graphics.GLTexture? = null, shadowPV: com.badlogic.gdx.math.Matrix4? = null,
               refl: com.badlogic.gdx.graphics.GLTexture? = null, screenW: Int = 1, screenH: Int = 1) {
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
        if (refl != null) {
            refl.bind(2)
            shader.setUniformi("u_refl", 2)
            shader.setUniformf("u_reflOn", 1f)
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)
        } else shader.setUniformf("u_reflOn", 0f)
        shader.setUniformf("u_screen", screenW.toFloat(), screenH.toFloat())
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
varying float v_chop;
// мелкие волны Герстнера: острые гребни (только для вида)
vec3 gerstner(vec2 p, vec2 d, float k, float a, float sp, inout vec3 dn) {
    float ph = dot(d, p) * k + u_time * sp;
    float c = cos(ph); float s = sin(ph);
    dn += vec3(-d.x * k * a * c, 0.0, -d.y * k * a * c);
    return vec3(d.x * 0.7 * a * c, a * s, d.y * 0.7 * a * c);
}
void main() {
    vec3 w = a_position + vec3(u_offset.x, 0.0, u_offset.y);
    float a1 = w.x * 0.08 + u_time * 1.1;
    float a2 = w.z * 0.11 + u_time * 0.9;
    float a3 = (w.x + w.z) * 0.05 + u_time * 0.7;
    float a4 = (w.x - w.z) * 0.031 + u_time * 0.55;
    float h = sin(a1) * 0.35 + sin(a2) * 0.3 + sin(a3) * 0.4 + sin(a4) * 0.35;
    float dx = (cos(a1) * 0.028 + cos(a3) * 0.02 + cos(a4) * 0.01085) * u_amp;
    float dz = (cos(a2) * 0.033 + cos(a3) * 0.02 - cos(a4) * 0.01085) * u_amp;
    vec3 dn = vec3(-dx * 5.0, 0.0, -dz * 5.0);
    vec3 g = vec3(0.0);
    float ca = 0.55 + 0.45 * u_amp;
    g += gerstner(w.xz, normalize(vec2(0.86, 0.5)), 0.42, 0.16 * ca, 1.9, dn);
    g += gerstner(w.xz, normalize(vec2(-0.45, 0.89)), 0.61, 0.1 * ca, 2.3, dn);
    g += gerstner(w.xz, normalize(vec2(0.2, -0.98)), 0.83, 0.07 * ca, 2.8, dn);
    g += gerstner(w.xz, normalize(vec2(-0.9, -0.3)), 1.15, 0.045 * ca, 3.3, dn);
    w.y = h * u_amp + g.y;
    w.xz += g.xz;
    v_chop = g.y / (0.38 * ca);
    v_normal = normalize(vec3(dn.x, 1.0, dn.z));
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
uniform sampler2D u_refl;
uniform float u_reflOn;
uniform vec2 u_screen;
varying vec3 v_world;
varying vec3 v_normal;
varying float v_h;
varying float v_shoreD;
varying vec3 v_shadowUv;
varying float v_chop;
float shadowAt(vec2 uv, float z) {
    vec4 p = texture2D(u_shadowTex, uv);
    float d = dot(p, vec4(1.0, 1.0 / 255.0, 1.0 / 65025.0, 1.0 / 16581375.0));
    return step(z, d + 0.0015);
}
float shadow() {
    if (u_shadowOn < 0.5) return 1.0;
    vec3 s = v_shadowUv;
    if (s.x < 0.0 || s.x > 1.0 || s.y < 0.0 || s.y > 1.0 || s.z > 1.0) return 1.0;
    float o = 1.0 / 2048.0;
    float r = 0.0;
    for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) r += shadowAt(s.xy + vec2(float(x), float(y)) * o * 1.5, s.z);
    return r / 9.0;
}
// хэш без синуса: устойчив к точности на телефонах
float hash(vec2 p) { vec3 p3 = fract(vec3(p.xyx) * 0.1031); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.x + p3.y) * p3.z); }
vec2 hash2(vec2 p) { vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.103, 0.0973)); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.xx + p3.yz) * p3.zy); }
float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}
// ячейки Вороного: кружево пены
float cells(vec2 p, float t) {
    vec2 i = floor(p); vec2 f = fract(p);
    float d1 = 8.0; float d2 = 8.0;
    for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
        vec2 b = vec2(float(x), float(y));
        vec2 o = hash2(i + b);
        o = 0.5 + 0.45 * sin(t + 6.2831 * o);
        float d = length(b + o - f);
        if (d < d1) { d2 = d1; d1 = d; } else if (d < d2) d2 = d;
    }
    return d2 - d1;
}
void main() {
    vec2 p = v_world.xz + vec2(5000.0);
    float tn = u_time;
    // рябь: несколько систем мелких волн разных направлений
    vec2 gsum = vec2(0.0);
    vec2 dirs[6];
    dirs[0] = vec2(0.94, 0.34); dirs[1] = vec2(-0.6, 0.8); dirs[2] = vec2(0.17, -0.98);
    dirs[3] = vec2(-0.87, -0.5); dirs[4] = vec2(0.71, 0.71); dirs[5] = vec2(-0.26, 0.97);
    float fr = 0.9; float am = 0.11;
    for (int i = 0; i < 6; i++) {
        float ph = dot(dirs[i], p) * fr + tn * (1.2 + fr * 0.9) + float(i) * 1.7;
        gsum += dirs[i] * (am * fr * cos(ph));
        fr *= 1.42; am *= 0.7;
    }
    float rn = noise(p * 0.35 + vec2(tn * 0.6, tn * 0.4));
    float e = 0.15;
    float nx = noise(p * 2.2 + vec2(tn * 0.7, 0.0)) - noise(p * 2.2 + vec2(e + tn * 0.7, 0.0));
    float nz = noise(p * 2.2 + vec2(tn * 0.7, 0.0)) - noise(p * 2.2 + vec2(tn * 0.7, e));
    float detail = 0.75 + 0.6 * u_storm;
    vec3 n = normalize(v_normal + vec3(-gsum.x + nx * 0.6, 0.0, -gsum.y + nz * 0.6) * detail);
    vec3 sun = normalize(u_sunDir);
    vec3 v = normalize(u_cam - v_world);
    float ndv = max(dot(n, v), 0.0);

    // мелководье и прибой
    float d0 = v_shoreD;
    float patches = noise(p * 0.03 + vec2(tn * 0.02, 0.0));
    float shore = smoothstep(34.0, 2.0, d0);
    vec3 deep = mix(u_deep * 0.82, vec3(0.16, 0.25, 0.32), u_storm);
    vec3 mid = mix(u_mid, vec3(0.24, 0.34, 0.42), u_storm);
    vec3 shallow = vec3(0.22, 0.82, 0.80);
    vec3 col = mix(deep, mid, smoothstep(0.3, 0.85, patches) * 0.8 + 0.2 * smoothstep(-0.6, 1.0, v_h));
    col = mix(col, shallow, shore * 0.88);
    // песчаное дно просвечивает у самого берега
    col = mix(col, vec3(0.62, 0.78, 0.66), smoothstep(10.0, 0.0, d0) * 0.35 * u_day);

    // каустика на мелководье
    float c1 = noise(p * 0.45 + vec2(tn * 0.35, tn * 0.2));
    float c2 = noise(p * 0.45 * 1.37 - vec2(tn * 0.28, -tn * 0.31));
    float caus = smoothstep(0.18, 0.0, abs(c1 - c2)) * smoothstep(0.3, 0.9, shore);
    col += vec3(0.24, 0.3, 0.26) * caus * 0.4 * u_day * (1.0 - u_storm);

    // свет проходит сквозь гребни (подповерхностное рассеяние)
    vec2 sunH = normalize(sun.xz + vec2(0.0001));
    float back = pow(clamp(dot(normalize(-v.xz + vec2(0.0001)), sunH) * 0.5 + 0.5, 0.0, 1.0), 2.0);
    float sss = clamp(v_chop * 0.8 + v_h * 0.25, 0.0, 1.0) * (0.4 + 0.6 * back);
    col += vec3(0.03, 0.32, 0.27) * sss * 0.55 * (0.25 + 0.75 * u_day) * (1.0 - shore * 0.6);

    // освещение
    float l = clamp(dot(n, sun), 0.0, 1.0);
    col *= (0.5 + 0.5 * u_day) * (0.82 + 0.3 * l);
    float cloud = smoothstep(0.45, 0.75, noise(p * 0.006 + vec2(tn * 0.012, tn * 0.006)));
    col *= 1.0 - cloud * (0.18 + 0.25 * u_storm);
    float sh = shadow();
    col *= 0.6 + 0.4 * sh;

    // отражение: зеркало сцены (острова, корабли) или небо
    float fres = 0.02 + 0.98 * pow(1.0 - ndv, 5.0);
    vec3 skyc = mix(u_fog * 1.06, vec3(0.6, 0.76, 0.94) * (0.25 + 0.75 * u_day), 0.4 * (1.0 - u_storm));
    vec3 refl = skyc;
    float rmix = clamp(fres * 2.2 + 0.1, 0.0, 0.75);
    if (u_reflOn > 0.5) {
        vec2 suv = gl_FragCoord.xy / u_screen + n.xz * vec2(0.035, 0.035) * (1.0 - shore * 0.5);
        vec4 rc = texture2D(u_refl, clamp(suv, 0.002, 0.998));
        refl = mix(skyc, rc.rgb, rc.a);
        rmix = mix(rmix, clamp(fres * 2.6 + 0.32, 0.0, 0.8), rc.a);
    }
    col = mix(col, refl, rmix);

    // солнечная (лунная) дорожка и искры
    vec3 hv = normalize(sun + v);
    float nh = max(dot(n, hv), 0.0);
    float spec = pow(nh, 420.0) * 4.0 + pow(nh, 60.0) * 0.25;
    vec2 gp = floor(p * 3.0);
    float glint = step(0.985, hash(gp + floor(tn * 6.0))) * pow(nh, 30.0) * 2.5;
    col += u_sunCol * (spec + glint) * (0.12 + 0.88 * u_day) * (1.0 - u_storm * 0.85) * sh;

    // пена: кружево на гребнях и полосы прибоя
    float lace = smoothstep(0.08, 0.0, cells(p * 0.55 + vec2(tn * 0.05, tn * 0.03), tn * 0.8));
    float lace2 = smoothstep(0.12, 0.0, cells(p * 1.3 - vec2(tn * 0.07, 0.0), tn * 1.1));
    float crest = smoothstep(0.55, 1.05, v_chop + v_h * 0.25 + u_storm * 0.4);
    float wavesIn = sin(d0 * 0.55 - tn * 2.0 + patches * 6.0);
    float surfBand = smoothstep(11.0, 0.0, d0) * smoothstep(0.35, 0.95, wavesIn);
    float edge = smoothstep(2.8, -0.8, d0);
    float foam = clamp(crest * (lace * 0.9 + lace2 * 0.5) * (0.6 + u_storm) + surfBand * (0.45 + 0.55 * lace2) + edge * (0.7 + 0.3 * lace), 0.0, 1.0);
    col = mix(col, vec3(0.94, 0.97, 1.0) * (0.55 + 0.45 * u_day), foam * 0.9);

    float d = distance(u_cam, v_world);
    col = mix(col, u_fog, smoothstep(u_fogRange.x, u_fogRange.y, d));
    gl_FragColor = vec4(col, 1.0);
}
"""
    }
}
