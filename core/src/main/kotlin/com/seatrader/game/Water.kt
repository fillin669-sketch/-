package com.seatrader.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable

/** Море: сетка, которая едет за камерой; волны и освещение считаются в шейдере. */
class Water : Disposable {
    private val size = 1300f
    private val seg = 120
    private val mesh: Mesh
    private val shader: ShaderProgram

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

    fun render(cam: Camera, cx: Float, cz: Float, time: Float, fogColor: FloatArray, fogNear: Float, fogFar: Float) {
        val ox = Math.round(cx / 10f) * 10f
        val oz = Math.round(cz / 10f) * 10f
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        shader.bind()
        shader.setUniformMatrix("u_projTrans", cam.combined)
        shader.setUniformf("u_offset", ox, oz)
        shader.setUniformf("u_time", time)
        shader.setUniformf("u_cam", cam.position.x, cam.position.y, cam.position.z)
        shader.setUniformf("u_fog", fogColor[0], fogColor[1], fogColor[2])
        shader.setUniformf("u_fogRange", fogNear, fogFar)
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
varying vec3 v_world;
varying vec3 v_normal;
void main() {
    vec3 w = a_position + vec3(u_offset.x, 0.0, u_offset.y);
    float a1 = w.x * 0.08 + u_time * 1.1;
    float a2 = w.z * 0.11 + u_time * 0.9;
    float a3 = (w.x + w.z) * 0.05 + u_time * 0.7;
    w.y = sin(a1) * 0.35 + sin(a2) * 0.3 + sin(a3) * 0.4;
    float dx = cos(a1) * 0.028 + cos(a3) * 0.02;
    float dz = cos(a2) * 0.033 + cos(a3) * 0.02;
    v_normal = normalize(vec3(-dx * 6.0, 1.0, -dz * 6.0));
    v_world = w;
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
varying vec3 v_world;
varying vec3 v_normal;
float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}
void main() {
    vec3 n = normalize(v_normal);
    vec3 light = normalize(vec3(0.4, 1.0, 0.3));
    float l = clamp(dot(n, light), 0.0, 1.0);
    vec2 p = mod(v_world.xz, 4000.0);
    float patches = noise(p * 0.025 + vec2(u_time * 0.02, 0.0)) * 0.6 + noise(p * 0.09) * 0.4;
    vec3 deep = vec3(0.122, 0.455, 0.722);
    vec3 shallow = vec3(0.192, 0.580, 0.839);
    vec3 col = mix(deep, shallow, smoothstep(0.35, 0.8, patches));
    col *= 0.82 + 0.28 * l;
    vec3 v = normalize(u_cam - v_world);
    float spec = pow(max(dot(reflect(-light, n), v), 0.0), 40.0);
    col += vec3(spec * 0.22);
    float sparkle = smoothstep(0.95, 0.995, noise(p * 0.35 + vec2(u_time * 0.3, -u_time * 0.2))) * 0.22;
    col += vec3(sparkle);
    float d = distance(u_cam, v_world);
    col = mix(col, u_fog, smoothstep(u_fogRange.x, u_fogRange.y, d));
    gl_FragColor = vec4(col, 1.0);
}
"""
    }
}
