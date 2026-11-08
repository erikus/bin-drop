package dev.estaab.bindrop.render3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Floats per vertex: position xyz, normal xyz, texture uv. */
const val VERTEX_FLOATS = 8

private const val TWO_PI = (2 * PI).toFloat()
private const val DEG_TO_RAD = (PI / 180.0).toFloat()

/** CPU-side triangle mesh. Pure Kotlin so shapes can be built and checked without a GL context. */
class MeshData(val vertices: FloatArray, val indices: IntArray) {
    val vertexCount: Int get() = vertices.size / VERTEX_FLOATS
}

class MeshBuilder {
    private val v = ArrayList<Float>()
    private val i = ArrayList<Int>()
    val vertexCount: Int get() = v.size / VERTEX_FLOATS

    fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float = 0f, w: Float = 0f): Int {
        v.add(x); v.add(y); v.add(z); v.add(nx); v.add(ny); v.add(nz); v.add(u); v.add(w)
        return vertexCount - 1
    }

    fun triangle(a: Int, b: Int, c: Int) {
        i.add(a); i.add(b); i.add(c)
    }

    fun quad(a: Int, b: Int, c: Int, d: Int) {
        triangle(a, b, c)
        triangle(a, c, d)
    }

    fun build(): MeshData = MeshData(v.toFloatArray(), i.toIntArray())
}

/** One point of a closed 2D outline with its outward normal. */
private class OutlinePoint(val x: Float, val y: Float, val nx: Float, val ny: Float)

/**
 * Procedural shapes. Faces are not culled when drawn (the shader lights both sides), so winding is not significant.
 */
object Shapes {
    /** Unit square in the XY plane, centred, facing +Z; uv (0,0) is the top-left so images are upright. */
    fun quad(): MeshData {
        val b = MeshBuilder()
        val a = b.vertex(-0.5f, -0.5f, 0f, 0f, 0f, 1f, 0f, 1f)
        val c = b.vertex(0.5f, -0.5f, 0f, 0f, 0f, 1f, 1f, 1f)
        val d = b.vertex(0.5f, 0.5f, 0f, 0f, 0f, 1f, 1f, 0f)
        val e = b.vertex(-0.5f, 0.5f, 0f, 0f, 0f, 1f, 0f, 0f)
        b.quad(a, c, d, e)
        return b.build()
    }

    /** Flat disc sector of radius 1 in the XY plane facing +Z, from [startDeg] counter-clockwise by [sweepDeg]. */
    fun disc(segments: Int, startDeg: Float = 0f, sweepDeg: Float = 360f): MeshData {
        val b = MeshBuilder()
        val centre = b.vertex(0f, 0f, 0f, 0f, 0f, 1f, 0.5f, 0.5f)
        val start = startDeg * DEG_TO_RAD
        val sweep = sweepDeg * DEG_TO_RAD
        val rim = IntArray(segments + 1) { s ->
            val a = start + sweep * s / segments
            b.vertex(cos(a), sin(a), 0f, 0f, 0f, 1f, 0.5f + 0.5f * cos(a), 0.5f - 0.5f * sin(a))
        }
        for (s in 0 until segments) b.triangle(centre, rim[s], rim[s + 1])
        return b.build()
    }

    /** UV sphere of radius 1. */
    fun sphere(rings: Int, segments: Int): MeshData {
        val b = MeshBuilder()
        for (r in 0..rings) {
            val phi = PI.toFloat() * r / rings
            val y = cos(phi)
            val ring = sin(phi)
            for (s in 0..segments) {
                val theta = TWO_PI * s / segments
                val x = ring * cos(theta)
                val z = ring * sin(theta)
                b.vertex(x, y, z, x, y, z, s.toFloat() / segments, r.toFloat() / rings)
            }
        }
        val stride = segments + 1
        for (r in 0 until rings) {
            for (s in 0 until segments) {
                val a = r * stride + s
                b.quad(a, a + stride, a + stride + 1, a + 1)
            }
        }
        return b.build()
    }

    /** Capped cylinder of radius 1 along the X axis, from x = −0.5 to 0.5. */
    fun cylinderX(segments: Int): MeshData {
        val b = MeshBuilder()
        val side = Array(2) { end ->
            val x = end - 0.5f
            IntArray(segments + 1) { s ->
                val a = TWO_PI * s / segments
                b.vertex(x, cos(a), sin(a), 0f, cos(a), sin(a))
            }
        }
        for (s in 0 until segments) b.quad(side[0][s], side[1][s], side[1][s + 1], side[0][s + 1])
        for (end in 0..1) {
            val x = end - 0.5f
            val nx = if (end == 0) -1f else 1f
            val centre = b.vertex(x, 0f, 0f, nx, 0f, 0f)
            val rim = IntArray(segments + 1) { s ->
                val a = TWO_PI * s / segments
                b.vertex(x, cos(a), sin(a), nx, 0f, 0f)
            }
            for (s in 0 until segments) b.triangle(centre, rim[s], rim[s + 1])
        }
        return b.build()
    }

    /**
     * A round tube following an elliptical arc in the XY plane: centre-line radii 1 (x) and [aspectY] (y), tube
     * radius [tubeRatio], from [startDeg] counter-clockwise by [sweepDeg], with rounded ends. Used for drawn
     * "strokes" like smiles; the tube keeps its thickness however flat the ellipse is.
     */
    fun torusArc(startDeg: Float, sweepDeg: Float, tubeRatio: Float, segments: Int, tubeSegments: Int, aspectY: Float = 1f): MeshData {
        val b = MeshBuilder()
        val start = startDeg * DEG_TO_RAD
        val sweep = sweepDeg * DEG_TO_RAD
        val stride = tubeSegments + 1
        for (s in 0..segments) {
            val a = start + sweep * s / segments
            val cx = cos(a)
            val cy = aspectY * sin(a)
            // Outward normal of the ellipse at this point.
            val ex = aspectY * cos(a)
            val ey = sin(a)
            val el = sqrt(ex * ex + ey * ey)
            for (t in 0..tubeSegments) {
                val p = TWO_PI * t / tubeSegments
                val nx = cos(p) * ex / el
                val ny = cos(p) * ey / el
                val nz = sin(p)
                b.vertex(cx + tubeRatio * nx, cy + tubeRatio * ny, tubeRatio * nz, nx, ny, nz)
            }
        }
        for (s in 0 until segments) {
            for (t in 0 until tubeSegments) {
                val a = s * stride + t
                b.quad(a, a + stride, a + stride + 1, a + 1)
            }
        }
        // Round end caps: small spheres at both ends of the centre line.
        for (a in floatArrayOf(start, start + sweep)) appendSphere(b, cos(a), aspectY * sin(a), 0f, tubeRatio, tubeSegments / 2, tubeSegments)
        return b.build()
    }

    private fun appendSphere(b: MeshBuilder, cx: Float, cy: Float, cz: Float, r: Float, rings: Int, segments: Int) {
        val base = b.vertexCount
        for (ring in 0..rings) {
            val phi = PI.toFloat() * ring / rings
            for (s in 0..segments) {
                val theta = TWO_PI * s / segments
                val x = sin(phi) * cos(theta)
                val y = cos(phi)
                val z = sin(phi) * sin(theta)
                b.vertex(cx + r * x, cy + r * y, cz + r * z, x, y, z)
            }
        }
        val stride = segments + 1
        for (ring in 0 until rings) {
            for (s in 0 until segments) {
                val a = base + ring * stride + s
                b.quad(a, a + stride, a + stride + 1, a + 1)
            }
        }
    }

    /**
     * Closed rounded-rectangle outline centred on the origin, counter-clockwise, with smooth per-point normals.
     * [cornerRadius] is clamped to fit.
     */
    private fun roundedRectOutline(halfW: Float, halfH: Float, cornerRadius: Float, cornerSegments: Int): List<OutlinePoint> {
        val r = cornerRadius.coerceIn(0f, minOf(halfW, halfH))
        val corners = listOf(
            Triple(halfW - r, halfH - r, 0f),          // top-right, sweeping from 0°
            Triple(-halfW + r, halfH - r, 90f),        // top-left
            Triple(-halfW + r, -halfH + r, 180f),      // bottom-left
            Triple(halfW - r, -halfH + r, 270f),       // bottom-right
        )
        val out = ArrayList<OutlinePoint>()
        for ((cx, cy, startDeg) in corners) {
            for (s in 0..cornerSegments) {
                val a = (startDeg + 90f * s / cornerSegments) * DEG_TO_RAD
                out.add(OutlinePoint(cx + r * cos(a), cy + r * sin(a), cos(a), sin(a)))
            }
        }
        return out
    }

    /**
     * Rounded-rectangle slab: the outline lies in XY (width and height 1, centred) and is extruded along Z from
     * −0.5 to 0.5, with flat front and back faces. [cornerFrac] is the corner radius as a fraction of the shorter
     * side after scaling by [aspect] (= height / width of the final slab), so corners stay round when scaled.
     */
    fun roundedSlab(cornerFrac: Float, aspect: Float, cornerSegments: Int = 6): MeshData {
        val b = MeshBuilder()
        // Build in a frame where the slab is aspect-correct (1 × aspect), then squash y back to unit height.
        val outline = roundedRectOutline(0.5f, aspect / 2, cornerFrac * minOf(1f, aspect), cornerSegments)
        val n = outline.size
        val inv = 1f / aspect
        val ring = Array(2) { end ->
            val z = end - 0.5f
            IntArray(n) { k ->
                val p = outline[k]
                // Normal under y-scale by 1/aspect transforms by the inverse-transpose: (nx, ny·aspect).
                val ny = p.ny * aspect
                val len = sqrt(p.nx * p.nx + ny * ny)
                b.vertex(p.x, p.y * inv, z, p.nx / len, ny / len, 0f)
            }
        }
        for (k in 0 until n) {
            val k1 = (k + 1) % n
            b.quad(ring[0][k], ring[1][k], ring[1][k1], ring[0][k1])
        }
        for (end in 0..1) {
            val z = end - 0.5f
            val nz = if (end == 0) -1f else 1f
            val centre = b.vertex(0f, 0f, z, 0f, 0f, nz, 0.5f, 0.5f)
            val face = IntArray(n) { k ->
                val p = outline[k]
                b.vertex(p.x, p.y * inv, z, 0f, 0f, nz, 0.5f + p.x, 0.5f - p.y * inv)
            }
            for (k in 0 until n) b.triangle(centre, face[k], face[(k + 1) % n])
        }
        return b.build()
    }

    /**
     * A wheelie-bin shell in bin-local units: x ∈ [−0.5, 0.5] at the rim, y ∈ [0, 1] bottom to top, z ∈ [−1, 0]
     * back to front. The outline is a rounded rectangle; the body narrows by [taper] per side at the bottom.
     * Returns (outer walls + bottom, inner walls + floor, rim band). The mouth is open.
     */
    fun binShell(taper: Float, cornerFrac: Float, wall: Float, rimDepth: Float, rimOutset: Float, floorY: Float): Triple<MeshData, MeshData, MeshData> {
        val corner = cornerFrac * 0.5f
        val outline = roundedRectOutline(0.5f, 0.5f, corner, 6)
        val n = outline.size
        // Outline y maps to depth z: outline y ∈ [−0.5, 0.5] → z ∈ [−1, 0].
        fun xAt(p: OutlinePoint, y: Float, inset: Float): Float {
            val widthScale = 1f - 2f * taper * (1f - y)
            return (p.x - p.nx * inset) * widthScale
        }
        fun zAt(p: OutlinePoint, inset: Float): Float = (p.y - p.ny * inset) - 0.5f

        // Outer wall and bottom.
        val outer = MeshBuilder()
        val bottomRing = IntArray(n) { k -> val p = outline[k]; outer.vertex(xAt(p, 0f, 0f), 0f, zAt(p, 0f), p.nx, 0f, p.ny) }
        val topRing = IntArray(n) { k -> val p = outline[k]; outer.vertex(xAt(p, 1f, 0f), 1f, zAt(p, 0f), p.nx, 0f, p.ny) }
        for (k in 0 until n) {
            val k1 = (k + 1) % n
            outer.quad(bottomRing[k], bottomRing[k1], topRing[k1], topRing[k])
        }
        val bottomCentre = outer.vertex(0f, 0f, -0.5f, 0f, -1f, 0f)
        val bottomCap = IntArray(n) { k -> val p = outline[k]; outer.vertex(xAt(p, 0f, 0f), 0f, zAt(p, 0f), 0f, -1f, 0f) }
        for (k in 0 until n) outer.triangle(bottomCentre, bottomCap[k], bottomCap[(k + 1) % n])

        // Inner wall (normals point inward) and floor.
        val inner = MeshBuilder()
        val innerLow = IntArray(n) { k -> val p = outline[k]; inner.vertex(xAt(p, floorY, wall), floorY, zAt(p, wall), -p.nx, 0f, -p.ny) }
        val innerHigh = IntArray(n) { k -> val p = outline[k]; inner.vertex(xAt(p, 1f, wall), 1f, zAt(p, wall), -p.nx, 0f, -p.ny) }
        for (k in 0 until n) {
            val k1 = (k + 1) % n
            inner.quad(innerLow[k], innerLow[k1], innerHigh[k1], innerHigh[k])
        }
        val floorCentre = inner.vertex(0f, floorY, -0.5f, 0f, 1f, 0f)
        val floorRing = IntArray(n) { k -> val p = outline[k]; inner.vertex(xAt(p, floorY, wall), floorY, zAt(p, wall), 0f, 1f, 0f) }
        for (k in 0 until n) inner.triangle(floorCentre, floorRing[k], floorRing[(k + 1) % n])

        // Rim: an outset band around the top, plus the flat top between the band and the inner wall.
        val rim = MeshBuilder()
        val yLow = 1f - rimDepth
        val bandLow = IntArray(n) { k -> val p = outline[k]; rim.vertex(xAt(p, yLow, -rimOutset), yLow, zAt(p, -rimOutset), p.nx, 0f, p.ny) }
        val bandHigh = IntArray(n) { k -> val p = outline[k]; rim.vertex(xAt(p, 1f, -rimOutset), 1f, zAt(p, -rimOutset), p.nx, 0f, p.ny) }
        for (k in 0 until n) {
            val k1 = (k + 1) % n
            rim.quad(bandLow[k], bandLow[k1], bandHigh[k1], bandHigh[k])
        }
        val underOut = IntArray(n) { k -> val p = outline[k]; rim.vertex(xAt(p, yLow, -rimOutset), yLow, zAt(p, -rimOutset), 0f, -1f, 0f) }
        val underIn = IntArray(n) { k -> val p = outline[k]; rim.vertex(xAt(p, yLow, 0f), yLow, zAt(p, 0f), 0f, -1f, 0f) }
        for (k in 0 until n) {
            val k1 = (k + 1) % n
            rim.quad(underIn[k], underIn[k1], underOut[k1], underOut[k])
        }
        val topOut = IntArray(n) { k -> val p = outline[k]; rim.vertex(xAt(p, 1f, -rimOutset), 1f, zAt(p, -rimOutset), 0f, 1f, 0f) }
        val topIn = IntArray(n) { k -> val p = outline[k]; rim.vertex(xAt(p, 1f, wall), 1f, zAt(p, wall), 0f, 1f, 0f) }
        for (k in 0 until n) {
            val k1 = (k + 1) % n
            rim.quad(topIn[k], topIn[k1], topOut[k1], topOut[k])
        }
        return Triple(outer.build(), inner.build(), rim.build())
    }

    /** Flat horizontal rectangle at y = 0 facing +Y, x ∈ [−0.5, 0.5], z ∈ [−0.5, 0.5], uv spanning 0..1. */
    fun floorQuad(): MeshData {
        val b = MeshBuilder()
        val a = b.vertex(-0.5f, 0f, -0.5f, 0f, 1f, 0f, 0f, 0f)
        val c = b.vertex(0.5f, 0f, -0.5f, 0f, 1f, 0f, 1f, 0f)
        val d = b.vertex(0.5f, 0f, 0.5f, 0f, 1f, 0f, 1f, 1f)
        val e = b.vertex(-0.5f, 0f, 0.5f, 0f, 1f, 0f, 0f, 1f)
        b.quad(a, c, d, e)
        return b.build()
    }
}
