package com.seatrader.game

import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider

/**
 * Шейдер моделей на основе стандартного, но с высокой точностью (на телефонах
 * mediump давал полосы «теневых акне»), смещением глубины и мягкими тенями (PCF 3×3).
 */
object Shaders {
    /** Номера материалов (кодируются в альфе цвета вершины). */
    const val NONE = 0; const val PLANKS = 1; const val BRICK = 2; const val TILES = 3; const val STONE = 4
    const val ROCK = 5; const val SAND = 6; const val GRASS = 7; const val CLOTH = 8; const val HULL = 9; const val METAL = 10; const val GLASS = 11

    private const val PATTERN = """
varying vec3 v_lpos;
varying vec3 v_lnrm;
float ph(vec2 p) { vec3 p3 = fract(vec3(p.xyx) * 0.1031); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.x + p3.y) * p3.z); }
float pn(vec2 p) {
    vec2 i = floor(p); vec2 f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(ph(i), ph(i + vec2(1.0, 0.0)), f.x), mix(ph(i + vec2(0.0, 1.0)), ph(i + vec2(1.0, 1.0)), f.x), f.y);
}
float groove(float t, float w) { return smoothstep(0.0, w, t) * smoothstep(0.0, w, 1.0 - t); }
float planks(vec2 uv, float w, float len) {
    float r = floor(uv.y / w);
    float t = fract(uv.y / w);
    float sh = ph(vec2(r, 1.7)) * len;
    float seg = floor((uv.x + sh) / len);
    float e = fract((uv.x + sh) / len);
    float tint = 0.86 + 0.22 * ph(vec2(r, seg));
    float grain = 0.92 + 0.08 * sin(uv.x * 7.0 + pn(uv * vec2(0.7, 14.0)) * 6.0 + r * 3.0);
    return tint * grain * (0.5 + 0.5 * groove(t, 0.09)) * (0.62 + 0.38 * groove(e, 0.012));
}
vec3 pattern(float id, vec3 p, vec3 n) {
    vec3 an = abs(n);
    bool top = an.y > 0.72;
    float hz = an.x > an.z ? p.z : p.x;
    vec2 side = vec2(hz, p.y);
    vec2 uv = top ? p.xz : side;
    float k = 1.0;
    if (id < 1.5) {
        k = planks(top ? p.xz : side, 0.3, 2.6);
    } else if (id < 2.5) {
        if (top) k = 0.9 + 0.15 * pn(p.xz * 4.0);
        else {
            float bh = 0.21; float row = floor(p.y / bh);
            float x = hz / 0.46 + 0.5 * mod(row, 2.0);
            float mortar = groove(fract(p.y / bh), 0.14) * groove(fract(x), 0.06);
            float tint = (0.78 + 0.3 * ph(vec2(floor(x), row))) * (0.9 + 0.16 * pn(side * 11.0));
            k = mix(1.28, tint, mortar);
        }
    } else if (id < 3.5) {
        float row = floor(p.y / 0.3); float t = fract(p.y / 0.3);
        float x = hz / 0.34 + 0.5 * mod(row, 2.0);
        k = (0.6 + 0.52 * t) * (0.72 + 0.28 * groove(fract(x), 0.2)) * (0.86 + 0.24 * ph(vec2(floor(x), row)));
    } else if (id < 4.5) {
        vec2 q = top ? p.xz / vec2(1.1, 1.1) : vec2(hz / 1.05, p.y / 0.55);
        float row = floor(q.y); q.x += 0.5 * mod(row, 2.0);
        float mortar = groove(fract(q.y), 0.08) * groove(fract(q.x), 0.05);
        float tint = 0.8 + 0.28 * ph(floor(q)) ;
        k = mix(0.58, tint * (0.85 + 0.25 * pn(uv * 3.0)), mortar);
    } else if (id < 5.5) {
        k = 0.72 + 0.42 * (pn(uv * 0.9) * 0.55 + pn(uv * 3.3) * 0.3 + pn(uv * 10.0) * 0.15);
    } else if (id < 6.5) {
        k = 0.92 + 0.1 * pn(p.xz * 7.0) + 0.04 * sin(p.x * 2.6 + pn(p.xz * 0.6) * 7.0);
    } else if (id < 7.5) {
        k = 0.8 + 0.26 * pn(uv * 0.6) + 0.12 * pn(uv * 4.5) - 0.06 * pn(uv * 16.0);
    } else if (id < 8.5) {
        k = (0.88 + 0.12 * groove(fract(p.x / 0.9), 0.05)) * (0.97 + 0.03 * sin(p.y * 40.0));
    } else if (id < 9.5) {
        k = top ? planks(vec2(p.z, p.x), 0.26, 3.4) : planks(side, 0.24, 3.8);
    } else {
        k = 0.88 + 0.18 * pn(uv * 3.0);
    }
    return vec3(k);
}
"""
    fun fragment(): String {
        var f = DefaultShader.getDefaultFragmentShader()
        f = f.replace("precision mediump float;", "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif")
        val a = f.indexOf("float getShadowness(vec2 offset)")
        val b = f.indexOf("#endif //shadowMapFlag")
        if (a > 0 && b > a) {
            f = f.substring(0, a) + """
float getShadowness(vec2 offset)
{
    const vec4 bitShifts = vec4(1.0, 1.0 / 255.0, 1.0 / 65025.0, 1.0 / 16581375.0);
    float d = dot(texture2D(u_shadowTexture, v_shadowMapUv.xy + offset), bitShifts);
    return step(v_shadowMapUv.z - 0.001, d);
}

float getShadow()
{
    vec2 uv = v_shadowMapUv.xy;
    if (uv.x < 0.01 || uv.y < 0.01 || uv.x > 0.99 || uv.y > 0.99) return 1.0;
    float o = u_shadowPCFOffset * 1.6;
    float s = 0.0;
    for (int y = -1; y <= 1; y++)
        for (int x = -1; x <= 1; x++)
            s += getShadowness(vec2(float(x), float(y)) * o);
    s /= 9.0;
    // мягкий край к границе карты теней
    vec2 e = min(uv, 1.0 - uv);
    float fade = clamp(min(e.x, e.y) * 12.0, 0.0, 1.0);
    return mix(1.0, s, fade);
}
""" + f.substring(b)
        }
        // процедурная фактура материалов (доски, кирпич, черепица, камень…)
        val m = f.lastIndexOf("void main() {")
        if (m > 0) f = f.substring(0, m) + PATTERN + f.substring(m)
        val e = f.indexOf("vec4 emissive = texture2D(u_emissiveTexture, v_emissiveUV) * u_emissiveColor;")
        val e0 = if (e > 0) f.lastIndexOf("#if", e) else -1
        if (e0 > 0) f = f.substring(0, e0) + """float glassGlow = 0.0;
#ifdef colorFlag
	float pid = floor((1.0 - v_color.a) * 32.0 + 0.5);
	if (pid > 10.5 && pid < 11.5) {
		// стекло: отражение неба, блик, ночью — тёплый свет изнутри
		float night = 0.0;
#ifdef fogFlag
		night = 1.0 - smoothstep(0.12, 0.45, dot(u_fogColor.rgb, vec3(0.299, 0.587, 0.114)));
#endif
		vec3 gn = normalize(v_lnrm);
		float band = fract((v_lpos.x + v_lpos.z) * 0.55 + v_lpos.y * 0.85);
		float refl = smoothstep(0.0, 0.06, band) * (1.0 - smoothstep(0.14, 0.26, band));
		float up = clamp(gn.y * 0.5 + 0.5, 0.0, 1.0);
		vec3 skyRefl = mix(vec3(0.07, 0.12, 0.16), vec3(0.42, 0.58, 0.7), 0.35 + 0.4 * up + 0.25 * fract(v_lpos.y * 0.37));
		diffuse.rgb = skyRefl + refl * vec3(0.6, 0.66, 0.7) * (1.0 - night);
		float lit = step(0.3, ph(floor(v_lpos.xz * 0.6) + floor(v_lpos.y * 0.5)));
		glassGlow = night * (0.35 + 0.65 * lit);
		diffuse.rgb = mix(diffuse.rgb, vec3(0.95, 0.66, 0.32), glassGlow * 0.6);
		diffuse.a = 1.0;
	} else if (pid > 0.5) {
		// вдали мелкий рисунок мерцает — плавно гасим
		float far = smoothstep(60.0, 175.0, 1.0 / gl_FragCoord.w);
		diffuse.rgb *= mix(pattern(pid, v_lpos, normalize(v_lnrm)), vec3(1.0), far * 0.8);
		diffuse.a = 1.0;
	}
#endif
	""" + f.substring(e0)
        val fogAt = f.indexOf("#ifdef fogFlag\n\t\tgl_FragColor.rgb = mix(")
        if (fogAt > 0) f = f.substring(0, fogAt) + "gl_FragColor.rgb += glassGlow * vec3(1.0, 0.68, 0.32);\n\t" + f.substring(fogAt)
        return f
    }

    fun vertex(): String {
        val v = DefaultShader.getDefaultVertexShader()
        // смещение точки по нормали при выборке тени — убирает «сетку» акне на стенах
        var vv = v.replace("precision mediump float;", "precision highp float;")
        val m = vv.lastIndexOf("void main() {")
        if (m > 0) vv = vv.substring(0, m) + "varying vec3 v_lpos;\nvarying vec3 v_lnrm;\n" + "void main() {\n\tv_lpos = a_position;\n#ifdef normalFlag\n\tv_lnrm = a_normal;\n#else\n\tv_lnrm = vec3(0.0, 1.0, 0.0);\n#endif\n" + vv.substring(m + "void main() {".length)
        return vv
            .replace("vec4 spos = u_shadowMapProjViewTrans * pos;",
                "#ifdef normalFlag\n\t\tvec3 sn = normalize((u_worldTrans * vec4(a_normal, 0.0)).xyz);\n\t\tvec4 spos = u_shadowMapProjViewTrans * (pos + vec4(sn * 0.22, 0.0));\n#else\n\t\tvec4 spos = u_shadowMapProjViewTrans * pos;\n#endif")
    }

    fun batch(): ModelBatch {
        val cfg = DefaultShader.Config(vertex(), fragment())
        cfg.numDirectionalLights = 3
        cfg.numPointLights = 0
        cfg.numSpotLights = 0
        return ModelBatch(DefaultShaderProvider(cfg))
    }
}
