package com.seatrader.game

import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider

/**
 * Шейдер моделей на основе стандартного, но с высокой точностью (на телефонах
 * mediump давал полосы «теневых акне»), смещением глубины и мягкими тенями (PCF 3×3).
 */
object Shaders {
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
        return f
    }

    fun vertex(): String {
        val v = DefaultShader.getDefaultVertexShader()
        // смещение точки по нормали при выборке тени — убирает «сетку» акне на стенах
        return v.replace("precision mediump float;", "precision highp float;")
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
