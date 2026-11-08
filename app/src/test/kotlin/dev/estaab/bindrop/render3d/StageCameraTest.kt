package dev.estaab.bindrop.render3d

import dev.estaab.bindrop.game.Scenes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val W = 1920f
private const val H = 1080f
private const val PX_TOLERANCE = 0.01f

class StageCameraTest {
    private val cam = StageCamera(W, H)

    /** Projects a world point to screen pixels (y down). */
    private fun toScreen(x: Float, y: Float, z: Float): Pair<Float, Float> {
        val clip = Mat4.transform(cam.viewProjection, x, y, z)
        val ndcX = clip[0] / clip[3]
        val ndcY = clip[1] / clip[3]
        return Pair((ndcX + 1f) / 2f * W, (1f - ndcY) / 2f * H)
    }

    @Test
    fun playPlaneMapsExactlyOntoTheScreen() {
        for ((sx, sy) in listOf(0f to 0f, W to H, W / 2 to H / 2, 123f to 987f, 1800f to 40f)) {
            val (px, py) = toScreen(sx, -sy, 0f)
            assertEquals(sx, px, PX_TOLERANCE)
            assertEquals(sy, py, PX_TOLERANCE)
        }
    }

    @Test
    fun rayPointsProjectToTheirScreenPixelAtAnyDepth() {
        for (z in floatArrayOf(-3000f, -500f, 0f, 100f, 400f)) {
            val (px, py) = toScreen(cam.rayX(300f, z), cam.rayY(700f, z), z)
            assertEquals(300f, px, PX_TOLERANCE)
            assertEquals(700f, py, PX_TOLERANCE)
        }
    }

    @Test
    fun nearerThingsLookBigger() {
        assertTrue(cam.scaleAt(100f) > 1f)
        assertTrue(cam.scaleAt(-100f) < 1f)
        assertEquals(1f, cam.scaleAt(0f), 0f)
    }

    @Test
    fun lawnRecedesTowardAHorizonAboveTheGround() {
        val groundY = H * 0.86f
        val back = cam.screenYOfPlane(-groundY, -H)
        assertTrue("horizon $back should be above the ground line", back < groundY)
        assertTrue("horizon $back should be below the eye", back > -cam.eyeY)
        assertEquals(groundY, cam.screenYOfPlane(-groundY, 0f), PX_TOLERANCE)
    }

    @Test
    fun eyeIsAboveTheBinMouthsSoWeSeeIntoThem() {
        val mouthScreenY = H * (0.86f - 0.42f)
        assertTrue(-cam.eyeY < mouthScreenY)
    }
}

class SceneLightsTest {
    @Test
    fun everySceneHasALight() {
        for (scene in Scenes.all) SceneLights.of(scene)
        assertEquals(Scenes.all.map { it.name }.toSet(), SceneLights.byName.keys)
    }

    @Test
    fun directionsAreUnitLength() {
        for (light in SceneLights.byName.values) {
            val d = light.direction
            assertEquals(1f, d[0] * d[0] + d[1] * d[1] + d[2] * d[2], 1e-5f)
        }
    }
}

class ShapesTest {
    private fun assertValid(mesh: MeshData) {
        assertTrue(mesh.indices.isNotEmpty())
        assertEquals(0, mesh.indices.size % 3)
        assertTrue(mesh.indices.all { it in 0 until mesh.vertexCount })
        for (v in 0 until mesh.vertexCount) {
            val o = v * VERTEX_FLOATS
            val n = mesh.vertices[o + 3] * mesh.vertices[o + 3] + mesh.vertices[o + 4] * mesh.vertices[o + 4] + mesh.vertices[o + 5] * mesh.vertices[o + 5]
            assertEquals("normal of vertex $v", 1f, n, 1e-3f)
        }
    }

    @Test
    fun shapesAreWellFormed() {
        listOf(
            Shapes.quad(),
            Shapes.disc(16),
            Shapes.disc(16, 180f, 180f),
            Shapes.sphere(8, 12),
            Shapes.cylinderX(12),
            Shapes.torusArc(20f, 140f, 0.2f, 12, 8, 0.6f),
            Shapes.roundedSlab(0.2f, 0.5f),
            Shapes.floorQuad(),
        ).forEach(::assertValid)
        val (outer, inner, rim) = Shapes.binShell(0.07f, 0.22f, 0.035f, 0.045f, 0.02f, 0.05f)
        listOf(outer, inner, rim).forEach(::assertValid)
    }

    @Test
    fun binShellFitsItsUnitBox() {
        val (outer, _, _) = Shapes.binShell(0.07f, 0.22f, 0.035f, 0.045f, 0.02f, 0.05f)
        for (v in 0 until outer.vertexCount) {
            val o = v * VERTEX_FLOATS
            assertTrue(outer.vertices[o] in -0.5001f..0.5001f)
            assertTrue(outer.vertices[o + 1] in 0f..1f)
            assertTrue(outer.vertices[o + 2] in -1.0001f..0.0001f)
        }
    }
}
