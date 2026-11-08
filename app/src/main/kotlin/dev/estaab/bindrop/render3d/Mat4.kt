package dev.estaab.bindrop.render3d

import kotlin.math.cos
import kotlin.math.sin

private const val DEG_TO_RAD = (Math.PI / 180.0).toFloat()

/**
 * Minimal column-major 4×4 matrix maths (the layout OpenGL expects). Pure Kotlin rather than
 * `android.opengl.Matrix` so the camera maths can be unit-tested off-device.
 */
object Mat4 {
    fun identity(out: FloatArray = FloatArray(16)): FloatArray {
        out.fill(0f)
        out[0] = 1f; out[5] = 1f; out[10] = 1f; out[15] = 1f
        return out
    }

    /** out = a × b. [out] may not alias [a] or [b]. */
    fun multiply(out: FloatArray, a: FloatArray, b: FloatArray): FloatArray {
        for (col in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) sum += a[k * 4 + row] * b[col * 4 + k]
                out[col * 4 + row] = sum
            }
        }
        return out
    }

    /** Off-axis perspective frustum, same convention as glFrustum. */
    fun frustum(out: FloatArray, left: Float, right: Float, bottom: Float, top: Float, near: Float, far: Float): FloatArray {
        out.fill(0f)
        out[0] = 2f * near / (right - left)
        out[5] = 2f * near / (top - bottom)
        out[8] = (right + left) / (right - left)
        out[9] = (top + bottom) / (top - bottom)
        out[10] = -(far + near) / (far - near)
        out[11] = -1f
        out[14] = -2f * far * near / (far - near)
        return out
    }

    /** Orthographic projection, same convention as glOrtho. */
    fun ortho(out: FloatArray, left: Float, right: Float, bottom: Float, top: Float, near: Float, far: Float): FloatArray {
        out.fill(0f)
        out[0] = 2f / (right - left)
        out[5] = 2f / (top - bottom)
        out[10] = -2f / (far - near)
        out[12] = -(right + left) / (right - left)
        out[13] = -(top + bottom) / (top - bottom)
        out[14] = -(far + near) / (far - near)
        out[15] = 1f
        return out
    }

    /** Transforms the point (x, y, z, 1); returns (x, y, z, w) without dividing. */
    fun transform(m: FloatArray, x: Float, y: Float, z: Float): FloatArray = floatArrayOf(
        m[0] * x + m[4] * y + m[8] * z + m[12],
        m[1] * x + m[5] * y + m[9] * z + m[13],
        m[2] * x + m[6] * y + m[10] * z + m[14],
        m[3] * x + m[7] * y + m[11] * z + m[15],
    )

    /**
     * Normal matrix (3×3, column-major) for model matrix [m]: the cofactor matrix of its upper-left 3×3, which is
     * the inverse-transpose up to a positive scale (the shader renormalises). Valid for any invertible [m] with a
     * positive determinant, including non-uniform scale.
     */
    fun normalMatrix(out: FloatArray, m: FloatArray): FloatArray {
        val a = m[0]; val b = m[4]; val c = m[8]
        val d = m[1]; val e = m[5]; val f = m[9]
        val g = m[2]; val h = m[6]; val i = m[10]
        // Column-major cofactor matrix.
        out[0] = e * i - f * h; out[3] = -(d * i - f * g); out[6] = d * h - e * g
        out[1] = -(b * i - c * h); out[4] = a * i - c * g; out[7] = -(a * h - b * g)
        out[2] = b * f - c * e; out[5] = -(a * f - c * d); out[8] = a * e - b * d
        return out
    }
}

/**
 * A model-matrix stack: [push]/[pop] around nested transforms, each op post-multiplies the current matrix
 * (so transforms apply to geometry in reverse call order, like the classic GL matrix stack).
 */
class MatrixStack(depth: Int = 16) {
    private val stack = Array(depth) { Mat4.identity() }
    private var top = 0
    private val tmp = FloatArray(16)
    private val op = FloatArray(16)

    val current: FloatArray get() = stack[top]

    fun reset() {
        top = 0
        Mat4.identity(stack[0])
    }

    fun push() {
        check(top + 1 < stack.size) { "MatrixStack overflow (depth ${stack.size})" }
        stack[top].copyInto(stack[top + 1])
        top++
    }

    fun pop() {
        check(top > 0) { "MatrixStack underflow" }
        top--
    }

    private fun apply(m: FloatArray) {
        Mat4.multiply(tmp, stack[top], m)
        tmp.copyInto(stack[top])
    }

    fun translate(x: Float, y: Float, z: Float) {
        Mat4.identity(op)
        op[12] = x; op[13] = y; op[14] = z
        apply(op)
    }

    fun scale(x: Float, y: Float, z: Float) {
        Mat4.identity(op)
        op[0] = x; op[5] = y; op[10] = z
        apply(op)
    }

    fun scale(s: Float) = scale(s, s, s)

    /** Rotation about the X axis, counter-clockwise looking down the axis toward the origin. */
    fun rotateX(deg: Float) {
        val r = deg * DEG_TO_RAD
        val c = cos(r); val s = sin(r)
        Mat4.identity(op)
        op[5] = c; op[6] = s; op[9] = -s; op[10] = c
        apply(op)
    }

    fun rotateY(deg: Float) {
        val r = deg * DEG_TO_RAD
        val c = cos(r); val s = sin(r)
        Mat4.identity(op)
        op[0] = c; op[2] = -s; op[8] = s; op[10] = c
        apply(op)
    }

    fun rotateZ(deg: Float) {
        val r = deg * DEG_TO_RAD
        val c = cos(r); val s = sin(r)
        Mat4.identity(op)
        op[0] = c; op[1] = s; op[4] = -s; op[5] = c
        apply(op)
    }
}
