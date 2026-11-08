package dev.estaab.bindrop.render3d

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

private const val BYTES_PER_FLOAT = 4
private const val BYTES_PER_INT = 4
private const val EGL_OPENGL_ES3_BIT_KHR = 0x40
private const val MSAA_SAMPLES = 4
private const val DEPTH_BITS = 24
private const val STENCIL_BITS = 8
private const val EMOJI_SAMPLE_SIZE = 2          // 512 px source → 256 px texture

private const val ATTR_POSITION = 0
private const val ATTR_NORMAL = 1
private const val ATTR_UV = 2

/** How a fragment's colour and coverage are produced; see the fragment shader. Values match `uMode`. */
enum class FragMode(val id: Int) { SOLID(0), TEXTURE(1), SOFT_DISC(2), DISC(3), RING(4), GRADIENT(5) }

private const val VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aUv;
uniform mat4 uViewProj;
uniform mat4 uModel;
uniform mat3 uNormalMat;
uniform float uForceDepth;
out vec3 vWorld;
out vec3 vNormal;
out vec2 vUv;
void main() {
    vec4 world = uModel * vec4(aPos, 1.0);
    vWorld = world.xyz;
    vNormal = uNormalMat * aNormal;
    vUv = aUv;
    gl_Position = uViewProj * world;
    if (uForceDepth >= 0.0) gl_Position.z = uForceDepth * gl_Position.w;
}
"""

private const val FRAGMENT_SHADER = """#version 300 es
precision highp float;
in vec3 vWorld;
in vec3 vNormal;
in vec2 vUv;
uniform int uMode;
uniform vec4 uColor;
uniform vec4 uColor2;
uniform sampler2D uTex;
uniform float uAlphaCut;
uniform float uRingInner;
uniform float uLit;
uniform float uSpecular;
uniform vec3 uLightDir;
uniform vec3 uLightColor;
uniform vec3 uAmbient;
uniform vec3 uEye;
uniform vec4 uFog;
out vec4 fragColor;
void main() {
    vec4 base = uColor;
    float r = length(vUv * 2.0 - 1.0);
    if (uMode == 1) {
        vec4 t = texture(uTex, vUv);
        if (t.a < uAlphaCut) discard;
        base = vec4(t.rgb / max(t.a, 0.0001), t.a) * uColor;
    } else if (uMode == 2) {
        float a = clamp(1.0 - r, 0.0, 1.0);
        base.a *= a * a;
    } else if (uMode == 3) {
        float fw = fwidth(r);
        base.a *= 1.0 - smoothstep(1.0 - 1.5 * fw, 1.0, r);
    } else if (uMode == 4) {
        float fw = fwidth(r);
        base.a *= smoothstep(uRingInner - fw, uRingInner, r) * (1.0 - smoothstep(1.0 - fw, 1.0, r));
    } else if (uMode == 5) {
        base = mix(uColor, uColor2, clamp(vUv.y, 0.0, 1.0));
    }
    if (base.a <= 0.002) discard;
    vec3 rgb = base.rgb;
    if (uLit > 0.0) {
        vec3 n = normalize(vNormal);
        vec3 v = normalize(uEye - vWorld);
        if (dot(n, v) < 0.0) n = -n;   // two-sided: faces are not culled
        float diffuse = max(dot(n, uLightDir), 0.0);
        vec3 h = normalize(uLightDir + v);
        float spec = pow(max(dot(n, h), 0.0), 40.0) * uSpecular;
        vec3 ambient = uAmbient * (0.85 + 0.15 * n.y);
        vec3 lit = rgb * (ambient + uLightColor * diffuse) + uLightColor * spec;
        rgb = mix(rgb, lit, uLit);
    }
    rgb = mix(rgb, uFog.rgb, uFog.a);
    fragColor = vec4(rgb * base.a, base.a);   // premultiplied alpha
}
"""

/** The single shader program used for everything, with typed setters for its uniforms. */
class StageShader {
    val program: Int = link(compile(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER), compile(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER))
    private val uViewProj = uniform("uViewProj")
    private val uModel = uniform("uModel")
    private val uNormalMat = uniform("uNormalMat")
    private val uForceDepth = uniform("uForceDepth")
    private val uMode = uniform("uMode")
    private val uColor = uniform("uColor")
    private val uColor2 = uniform("uColor2")
    private val uTex = uniform("uTex")
    private val uAlphaCut = uniform("uAlphaCut")
    private val uRingInner = uniform("uRingInner")
    private val uLit = uniform("uLit")
    private val uSpecular = uniform("uSpecular")
    private val uLightDir = uniform("uLightDir")
    private val uLightColor = uniform("uLightColor")
    private val uAmbient = uniform("uAmbient")
    private val uEye = uniform("uEye")
    private val uFog = uniform("uFog")
    private val normal = FloatArray(9)

    fun use() {
        GLES30.glUseProgram(program)
        GLES30.glUniform1i(uTex, 0)
        forceDepth(null)
        alphaCut(0.5f)
        lit(0f)
        specular(0f)
        fog(0f, 0f, 0f, 0f)
    }

    fun viewProjection(m: FloatArray) = GLES30.glUniformMatrix4fv(uViewProj, 1, false, m, 0)

    fun model(m: FloatArray) {
        GLES30.glUniformMatrix4fv(uModel, 1, false, m, 0)
        GLES30.glUniformMatrix3fv(uNormalMat, 1, false, Mat4.normalMatrix(normal, m), 0)
    }

    /** Pins every vertex to NDC depth [depth] (for backdrops), or null for normal depth. */
    fun forceDepth(depth: Float?) = GLES30.glUniform1f(uForceDepth, depth ?: -1f)
    fun mode(mode: FragMode) = GLES30.glUniform1i(uMode, mode.id)
    fun color(argb: Long, alpha: Float = 1f, shade: Float = 1f) {
        val c = Rgba(argb)
        GLES30.glUniform4f(uColor, c.r * shade, c.g * shade, c.b * shade, c.a * alpha)
    }
    fun color(r: Float, g: Float, b: Float, a: Float) = GLES30.glUniform4f(uColor, r, g, b, a)
    fun color2(argb: Long) {
        val c = Rgba(argb)
        GLES30.glUniform4f(uColor2, c.r, c.g, c.b, c.a)
    }
    fun alphaCut(cut: Float) = GLES30.glUniform1f(uAlphaCut, cut)
    fun ringInner(fraction: Float) = GLES30.glUniform1f(uRingInner, fraction)
    /** 0 = flat colour, 1 = fully lit, in between blends the two. */
    fun lit(amount: Float) = GLES30.glUniform1f(uLit, amount)
    fun specular(amount: Float) = GLES30.glUniform1f(uSpecular, amount)
    fun light(dir: FloatArray, color: FloatArray, ambient: FloatArray) {
        GLES30.glUniform3fv(uLightDir, 1, dir, 0)
        GLES30.glUniform3fv(uLightColor, 1, color, 0)
        GLES30.glUniform3fv(uAmbient, 1, ambient, 0)
    }
    fun eye(x: Float, y: Float, z: Float) = GLES30.glUniform3f(uEye, x, y, z)
    fun fog(r: Float, g: Float, b: Float, amount: Float) = GLES30.glUniform4f(uFog, r, g, b, amount)

    private fun uniform(name: String): Int {
        val loc = GLES30.glGetUniformLocation(program, name)
        check(loc >= 0) { "Uniform $name not found in the stage shader" }
        return loc
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, ok, 0)
        check(ok[0] != 0) { "Shader compile failed: ${GLES30.glGetShaderInfoLog(shader)}" }
        return shader
    }

    private fun link(vs: Int, fs: Int): Int {
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, vs)
        GLES30.glAttachShader(p, fs)
        GLES30.glLinkProgram(p)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { "Shader link failed: ${GLES30.glGetProgramInfoLog(p)}" }
        return p
    }
}

/** ARGB long (the game's colour format) split into 0..1 floats. */
class Rgba(argb: Long) {
    val a: Float = ((argb ushr 24) and 0xFF) / 255f
    val r: Float = ((argb ushr 16) and 0xFF) / 255f
    val g: Float = ((argb ushr 8) and 0xFF) / 255f
    val b: Float = (argb and 0xFF) / 255f
}

/** A mesh uploaded to the GPU: one VAO with interleaved position / normal / uv and 32-bit indices. */
class GlMesh(data: MeshData) {
    private val vao: Int
    private val indexCount = data.indices.size

    init {
        val ids = IntArray(2)
        val vaos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        vao = vaos[0]
        GLES30.glGenBuffers(2, ids, 0)
        GLES30.glBindVertexArray(vao)

        val vb = ByteBuffer.allocateDirect(data.vertices.size * BYTES_PER_FLOAT).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vb.put(data.vertices).position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.vertices.size * BYTES_PER_FLOAT, vb, GLES30.GL_STATIC_DRAW)

        val ib = ByteBuffer.allocateDirect(data.indices.size * BYTES_PER_INT).order(ByteOrder.nativeOrder()).asIntBuffer()
        ib.put(data.indices).position(0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ids[1])
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, data.indices.size * BYTES_PER_INT, ib, GLES30.GL_STATIC_DRAW)

        val stride = VERTEX_FLOATS * BYTES_PER_FLOAT
        GLES30.glEnableVertexAttribArray(ATTR_POSITION)
        GLES30.glVertexAttribPointer(ATTR_POSITION, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(ATTR_NORMAL)
        GLES30.glVertexAttribPointer(ATTR_NORMAL, 3, GLES30.GL_FLOAT, false, stride, 3 * BYTES_PER_FLOAT)
        GLES30.glEnableVertexAttribArray(ATTR_UV)
        GLES30.glVertexAttribPointer(ATTR_UV, 2, GLES30.GL_FLOAT, false, stride, 6 * BYTES_PER_FLOAT)
        GLES30.glBindVertexArray(0)
    }

    fun draw() {
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_INT, 0)
    }
}

/** Emoji textures keyed by drawable id, mipmapped. Must be created on the GL thread. */
class TextureCache(resources: Resources, drawables: Collection<Int>) {
    private val map = HashMap<Int, Int>()

    init {
        val opts = BitmapFactory.Options().apply { inSampleSize = EMOJI_SAMPLE_SIZE }
        val ids = IntArray(drawables.size)
        GLES30.glGenTextures(ids.size, ids, 0)
        for ((k, drawable) in drawables.withIndex()) {
            val bitmap = BitmapFactory.decodeResource(resources, drawable, opts)
                ?: error("Could not decode drawable ${resources.getResourceEntryName(drawable)} (0x${drawable.toString(16)})")
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[k])
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
            bitmap.recycle()
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
            map[drawable] = ids[k]
        }
    }

    fun bind(drawable: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, map.getValue(drawable))
    }
}

/**
 * Picks an RGB888 config with depth and stencil (the stencil masks the circular scene wipe), preferring 4× MSAA
 * for smooth 3D edges and falling back to no multisampling.
 */
class StageConfigChooser : GLSurfaceView.EGLConfigChooser {
    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
        for (samples in intArrayOf(MSAA_SAMPLES, 0)) {
            val attribs = intArrayOf(
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, DEPTH_BITS,
                EGL10.EGL_STENCIL_SIZE, STENCIL_BITS,
                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
                EGL10.EGL_SAMPLE_BUFFERS, if (samples > 0) 1 else 0,
                EGL10.EGL_SAMPLES, samples,
                EGL10.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            if (egl.eglChooseConfig(display, attribs, configs, 1, count) && count[0] > 0) return configs[0]!!
        }
        error("No EGL config with OpenGL ES 3, depth $DEPTH_BITS and stencil $STENCIL_BITS")
    }
}
