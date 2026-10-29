package com.xcluice.jetstrike.engine

import android.opengl.GLES20

/** A compiled GLSL program that rebuilds itself after the GL context is lost. */
class Program(private val vsSrc: String, private val fsSrc: String) {
    var id = 0
        private set
    private var epoch = -1
    private val uniforms = HashMap<String, Int>()
    private val attribs = HashMap<String, Int>()

    fun use() {
        if (id == 0 || epoch != GL.epoch) build()
        GLES20.glUseProgram(id)
    }

    fun u(name: String): Int = uniforms.getOrPut(name) { GLES20.glGetUniformLocation(id, name) }
    fun a(name: String): Int = attribs.getOrPut(name) { GLES20.glGetAttribLocation(id, name) }

    private fun build() {
        uniforms.clear(); attribs.clear()
        val vs = compile(GLES20.GL_VERTEX_SHADER, vsSrc)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fsSrc)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs)
        GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(p)
            GLES20.glDeleteProgram(p)
            throw RuntimeException("Shader link failed: $log")
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        id = p
        epoch = GL.epoch
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(s)
            GLES20.glDeleteShader(s)
            throw RuntimeException("Shader compile failed: $log")
        }
        return s
    }
}

object Shaders {
    /** Lit, vertex-coloured geometry: sun + hemisphere ambient + distance fog. */
    const val LIT_VS = """
uniform mat4 uMVP;
uniform mat4 uModel;
uniform mat3 uNormal;
attribute vec3 aPos;
attribute vec3 aNormal;
attribute vec4 aColor;
varying vec4 vColor;
varying vec3 vNormal;
varying vec3 vWorld;
void main() {
    vWorld = (uModel * vec4(aPos, 1.0)).xyz;
    vNormal = uNormal * aNormal;
    vColor = aColor;
    gl_Position = uMVP * vec4(aPos, 1.0);
}
"""

    const val LIT_FS = """
precision mediump float;
uniform vec3 uSunDir;
uniform vec3 uSunColor;
uniform vec3 uSky;
uniform vec3 uGround;
uniform vec4 uTint;
uniform float uEmissive;
uniform vec3 uCam;
uniform vec3 uFogColor;
uniform vec2 uFog;
varying vec4 vColor;
varying vec3 vNormal;
varying vec3 vWorld;
void main() {
    vec3 n = normalize(vNormal);
    float ndl = max(dot(n, uSunDir), 0.0);
    vec3 amb = mix(uGround, uSky, n.y * 0.5 + 0.5);
    vec4 base = vColor * uTint;
    vec3 lit = base.rgb * (amb + uSunColor * ndl) + base.rgb * uEmissive;
    float f = clamp((length(vWorld - uCam) - uFog.x) / (uFog.y - uFog.x), 0.0, 1.0);
    gl_FragColor = vec4(mix(lit, uFogColor, f), base.a);
}
"""

    /** Round, soft, size-attenuated point sprites for sparks, smoke, muzzle flashes and jetpack fire. */
    const val POINT_VS = """
uniform mat4 uVP;
uniform float uScale;
attribute vec3 aPos;
attribute vec4 aColor;
attribute float aSize;
varying vec4 vColor;
void main() {
    gl_Position = uVP * vec4(aPos, 1.0);
    gl_PointSize = max(1.0, aSize * uScale / gl_Position.w);
    vColor = aColor;
}
"""

    const val POINT_FS = """
precision mediump float;
varying vec4 vColor;
void main() {
    vec2 c = gl_PointCoord - vec2(0.5);
    float a = clamp(1.0 - dot(c, c) * 4.0, 0.0, 1.0);
    gl_FragColor = vec4(vColor.rgb, vColor.a * a * a);
}
"""

    const val BG_VS = """
attribute vec2 aPos;
varying vec2 vUv;
void main() {
    vUv = vec2(aPos.x * 0.5 + 0.5, 1.0 - (aPos.y * 0.5 + 0.5));
    gl_Position = vec4(aPos, 0.999, 1.0);
}
"""

    const val BG_FS = """
precision mediump float;
uniform sampler2D uTex;
varying vec2 vUv;
void main() {
    gl_FragColor = vec4(texture2D(uTex, vUv).rgb, 1.0);
}
"""

    const val SKY_VS = """
attribute vec2 aPos;
varying float vY;
void main() {
    vY = aPos.y * 0.5 + 0.5;
    gl_Position = vec4(aPos, 0.999, 1.0);
}
"""

    const val SKY_FS = """
precision mediump float;
uniform vec3 uTop;
uniform vec3 uBottom;
varying float vY;
void main() {
    gl_FragColor = vec4(mix(uBottom, uTop, clamp(vY, 0.0, 1.0)), 1.0);
}
"""
}
