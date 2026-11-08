package dev.estaab.bindrop.render3d

import android.content.res.Resources
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import dev.estaab.bindrop.game.Ambient
import dev.estaab.bindrop.game.BinColors
import dev.estaab.bindrop.game.ItemCatalog
import dev.estaab.bindrop.game.ItemState
import dev.estaab.bindrop.game.ParticleKind
import dev.estaab.bindrop.game.Scene
import dev.estaab.bindrop.game.Scenes
import dev.estaab.bindrop.game.SkyObject
import dev.estaab.bindrop.game.Sprites
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Colours (ARGB), matching the 2D version.
private const val SUN = 0xFFFFE066
private const val SUN_GLOW = 0xFFFFE066
private const val LOW_SUN = 0xFFFF8A3D
private const val LOW_SUN_GLOW = 0xFFFFB347
private const val MOON = 0xFFF6F1D3
private const val MOON_GLOW = 0xFFF6F1D3
private const val MOON_CRATER = 0xFFD9D2B0
private const val STAR = 0xFFFFFFFF
private const val SNOWFLAKE = 0xFFFFFFFF
private const val FIREFLY = 0xFFDFFF6A
private const val BUBBLE = 0xFFFFFFFF
private const val PETAL = 0xFFFFB7D5
private val RAINBOW_COLORS = longArrayOf(0xFFFF5E5E, 0xFFFFA943, 0xFFFFE666, 0xFF7ED957, 0xFF5AB4FF, 0xFF9B7BFF)
private const val SHADOW = 0xFF000000
private const val INTERIOR = 0xFF1B1E24
private const val WHEEL = 0xFF2B2B2B
private const val HUB = 0xFF9A9A9A
private const val EYE_WHITE = 0xFFFFFFFF
private const val PUPIL = 0xFF1B1E24
private const val MOUTH = 0xFF1B1E24
private const val TONGUE = 0xFFE8637A

// Stage depths (world z), as fractions of H. The play plane z = 0 maps exactly to the screen; see StageCamera.
private const val BIN_FRONT_Z_FRAC = 0.03f        // bins stand just behind the play plane, so items pass in front
private const val FLOOR_FRONT_Z_FRAC = 0.06f      // front edge of the lawn; the dirt face below it faces the viewer
private const val FLOOR_BACK_Z_FRAC = 0.9f        // the lawn ends here, behind the hills
private const val FLOOR_FACE_DEPTH_FRAC = 0.6f    // how far the dirt face extends below the lawn
private const val FLOOR_WIDTH_FACTOR = 6f         // of W, so the lawn reaches past both screen edges at any depth
private const val LIP_R_FRAC = 0.012f             // rounded grass edge at the lawn front
private const val HILL_FRONT_Z_FRAC = 0.95f
private const val HILL_BACK_Z_FRAC = 1.25f
private const val HILL_COUNT = 7
private const val HILL_RX_FRAC = 0.42f
private const val HILL_RY_FRAC = 0.2f
private const val HILL_RZ_FRAC = 0.18f
private const val HILL_BACK_FOG = 0.3f
private const val SKY_Z_FRAC = 6f                 // sun, moon, stars
private const val RAINBOW_Z_FRAC = 4f
private const val CLOUD_Z_FRAC = 2.5f
private const val AMBIENT_NEAR_Z_FRAC = 0.35f     // snow, petals, bubbles, fireflies fill this depth range
private const val AMBIENT_FAR_Z_FRAC = 0.85f
private const val SKY_DEPTH = 0.99999f            // NDC depth of the sky backdrop

// Layout fractions shared with the 2D look (of W or H).
private const val CLOUD_COUNT = 3
private const val CLOUD_SPEED_FRAC = 0.02f
private const val SUN_X_FRAC = 0.09f
private const val SUN_Y_FRAC = 0.16f
private const val SUN_R_FRAC = 0.07f
private const val LOW_SUN_X_FRAC = 0.26f
private const val LOW_SUN_ABOVE_HORIZON_FRAC = 0.09f   // of H: the low sun peeks over the hills
private const val LOW_SUN_R_FRAC = 0.13f
private const val RAINBOW_R_FRAC = 0.75f
private const val RAINBOW_BAND_FRAC = 0.035f
private const val RAINBOW_ALPHA = 0.8f
private const val STAR_COUNT = 40
private const val SNOW_COUNT = 70
private const val SNOW_FALL_FRAC = 0.08f
private const val FIREFLY_COUNT = 18
private const val BUBBLE_COUNT = 24
private const val BUBBLE_RISE_FRAC = 0.06f
private const val PETAL_COUNT = 30
private const val PETAL_FALL_FRAC = 0.05f
private const val PETAL_DRIFT_FRAC = 0.03f
private const val AMBIENT_SIZE_FRAC = 0.012f
private const val TWO_PI = (2 * Math.PI).toFloat()

// Bin shape, as fractions of bin width (bw) or height (bh).
private const val BIN_DEPTH_FACTOR = 1.0f         // of bw
private const val BIN_TAPER_FRAC = 0.07f          // of bw, per side, at the bottom
private const val BIN_CORNER_FRAC = 0.22f         // corner radius, of the outline's half-size
private const val BIN_WALL_FRAC = 0.035f          // wall thickness, of the unit outline
private const val BIN_LIP_FRAC = 0.045f           // of bh
private const val BIN_RIM_OUTSET = 0.02f          // of the unit outline
private const val BIN_FLOOR_FRAC = 0.05f          // of bh, inner floor height
private const val LID_THICK_FRAC = 0.04f          // of bh
private const val LID_OVERHANG_FRAC = 0.04f       // of bw, per side
private const val LID_PANEL_INSET_FRAC = 0.1f     // of bw
private const val LID_OPEN_DEG = 105f             // lid swung back past upright
private const val LID_FLAP_DEG = 50f              // how far a capture flaps the lid shut
private const val WHEEL_R_FRAC = 0.085f           // of bh
private const val WHEEL_W_FACTOR = 0.7f           // of wheel radius
private const val HANDLE_Y_FRAC = 0.93f           // of bh
private const val HANDLE_R_FRAC = 0.025f          // of bw
private const val HANDLE_LEN_FRAC = 0.7f          // of bw
private const val EYE_Y_FRAC = 0.22f
private const val EYE_DX_FRAC = 0.18f
private const val EYE_RX_FRAC = 0.085f
private const val EYE_RY_FRAC = 0.11f
private const val EYE_BULGE_FACTOR = 0.5f         // of eye x radius
private const val PUPIL_R_FRAC = 0.045f
private const val PUPIL_TRAVEL_FRAC = 0.035f
private const val MOUTH_Y_FRAC = 0.37f
private const val MOUTH_W_FRAC = 0.26f
private const val LABEL_Y_FRAC = 0.48f
private const val LABEL_W_FRAC = 0.62f
private const val LABEL_H_FRAC = 0.2f
private const val LABEL_THICK_FRAC = 0.02f        // of bw
private const val BIN_ASPECT = 0.42f / 0.23f      // bh / bw, as laid out by GameWorld; only shapes the label's corners
private const val DECAL_LIFT = 0.6f               // px between stacked front decals
private const val SQUASH_AMOUNT = 0.16f
private const val WIGGLE_DEG = 9f
private const val LID_FLAP_AMOUNT = 0.45f

// Stroke shapes (tube radius / centre-line radius), derived from the 2D stroke widths.
private const val HAPPY_EYE_TUBE = 0.206f         // 0.035 bw stroke on a 0.085 bw arc
private const val HAPPY_EYE_ASPECT = 0.906f       // 0.7 · EYE_RY / EYE_RX
private const val SMILE_TUBE = 0.115f             // 0.03 bw stroke on a 0.13 bw arc
private const val SMILE_ASPECT = 0.5f

// Items.
private const val ITEM_THICKNESS_FACTOR = 0.14f   // of item diameter
private const val ITEM_LAYERS = 7                 // stacked alpha-cut sprites give the emoji a solid edge
private const val ITEM_EDGE_SHADE = 0.6f
private const val ITEM_LIT = 0.35f
private const val ITEM_YAW_DEG = 18f              // gentle turn so the thickness shows
private const val ITEM_YAW_REST_FACTOR = 0.35f
private const val HELD_SCALE_DELTA = 0.15f        // GameWorld grows a held item to 1.15×
private const val HELD_LIFT_Z_FRAC = 0.12f        // held items come toward the camera along the finger's ray
private const val SINK_Z_TIME = 0.15f             // seconds for a captured item to move back into the bin
private const val SHADOW_LIFT = 0.5f              // px above the lawn
private const val ITEM_SHADOW_ALPHA = 0.4f
private const val BIN_SHADOW_ALPHA = 0.35f
private const val BIN_SHADOW_SPREAD = 1.5f

// Particles.
private const val CONFETTI_Z_FRAC = 0.03f
private const val CONFETTI_Z_SPREAD_FRAC = 0.1f
private const val CONFETTI_FLIP_FACTOR = 1.3f
private const val SPRITE_Z_FRAC = 0.06f

/** Cheap deterministic pseudo-random in [0,1) so decorations need no stored state. */
private fun hash01(i: Int, salt: Int): Float {
    var x = i * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFFFF) / 16777216f
}

private fun easeOutQuad(t: Float): Float = 1f - (1f - t) * (1f - t)

private fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Every mesh the stage uses, uploaded once per GL context. */
private class StageMeshes {
    val quad = GlMesh(Shapes.quad())
    val sphere = GlMesh(Shapes.sphere(18, 28))
    val sphereLow = GlMesh(Shapes.sphere(8, 12))
    val cylinder = GlMesh(Shapes.cylinderX(24))
    val disc = GlMesh(Shapes.disc(48))
    val lowerHalfDisc = GlMesh(Shapes.disc(24, 180f, 180f))
    val floor = GlMesh(Shapes.floorQuad())
    val happyEye = GlMesh(Shapes.torusArc(20f, 140f, HAPPY_EYE_TUBE, 16, 10, HAPPY_EYE_ASPECT))
    val smile = GlMesh(Shapes.torusArc(200f, 140f, SMILE_TUBE, 18, 10, SMILE_ASPECT))
    val binOuter: GlMesh
    val binInner: GlMesh
    val binRim: GlMesh
    val lid = GlMesh(Shapes.roundedSlab(0.25f, aspect = 1f / (1f + 2f * LID_OVERHANG_FRAC)))
    val label = GlMesh(Shapes.roundedSlab(0.2f, aspect = LABEL_H_FRAC * BIN_ASPECT / LABEL_W_FRAC))
    /** One half-torus per rainbow band; each band's tube is half the band width at that band's radius. */
    val rainbow: List<GlMesh> = RAINBOW_COLORS.indices.map { i ->
        val r = RAINBOW_R_FRAC - RAINBOW_BAND_FRAC * i - RAINBOW_BAND_FRAC / 2
        GlMesh(Shapes.torusArc(0f, 180f, RAINBOW_BAND_FRAC / 2 / r, 64, 8))
    }

    init {
        val (outer, inner, rim) = Shapes.binShell(
            taper = BIN_TAPER_FRAC,
            cornerFrac = BIN_CORNER_FRAC,
            wall = BIN_WALL_FRAC,
            rimDepth = BIN_LIP_FRAC,
            rimOutset = BIN_RIM_OUTSET,
            floorY = BIN_FLOOR_FRAC,
        )
        binOuter = GlMesh(outer)
        binInner = GlMesh(inner)
        binRim = GlMesh(rim)
    }
}

/**
 * Draws [FrameSnapshot]s as a lit 3D stage: wheelie bins with open lids and faces, emoji items as thick tokens with
 * ground shadows, a lawn with hills, a sky per scene, and confetti that tumbles in 3D. Runs on the GL thread;
 * [publish] hands it the latest snapshot from the main thread.
 */
class StageRenderer(private val resources: Resources) : GLSurfaceView.Renderer {
    private val latest = AtomicReference<FrameSnapshot?>(null)

    private lateinit var shader: StageShader
    private lateinit var meshes: StageMeshes
    private lateinit var textures: TextureCache
    private var camera: StageCamera? = null
    private val stack = MatrixStack(24)

    /** Safe to call from any thread; the next drawn frame uses it. */
    fun publish(snapshot: FrameSnapshot) = latest.set(snapshot)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // A new context: everything GPU-side is rebuilt.
        shader = StageShader()
        meshes = StageMeshes()
        textures = TextureCache(resources, (ItemCatalog.all.map { it.drawable } + Sprites.all).distinct())
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glDepthMask(true)
        GLES30.glStencilMask(0xFF)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT or GLES30.GL_STENCIL_BUFFER_BIT)
        val s = latest.get() ?: return
        if (s.width <= 0f || s.height <= 0f) return
        val cam = camera?.takeIf { it.width == s.width && it.height == s.height } ?: StageCamera(s.width, s.height).also { camera = it }

        shader.use()
        shader.eye(cam.eyeX, cam.eyeY, cam.eyeZ)
        drawBackgrounds(s, cam)

        // Foreground light blends from the previous scene to the new one during the wipe.
        val current = Scenes.all[s.sceneIndex]
        val previous = Scenes.all[(s.sceneIndex + Scenes.all.size - 1) % Scenes.all.size]
        setBlendedLight(SceneLights.of(previous), SceneLights.of(current), if (s.sceneReveal >= 1f) 1f else easeOutQuad(s.sceneReveal))
        shader.viewProjection(cam.viewProjection)
        stack.reset()

        drawShadows(s, cam)
        for (bin in s.bins) drawBin(s, bin, s.palette.bins[bin.index % s.palette.bins.size])
        for (item in s.items) if (item.state == ItemState.SINKING) drawItem(s, cam, item)
        for (item in s.items) if (item.state != ItemState.SINKING) drawItem(s, cam, item)
        drawParticles(s)
    }

    // ------------------------------------------------------------------ lighting

    private val lightDir = FloatArray(3)
    private val lightColor = FloatArray(3)
    private val ambient = FloatArray(3)

    private fun setBlendedLight(a: SceneLight, b: SceneLight, t: Float) {
        for (k in 0 until 3) {
            lightDir[k] = lerp(a.direction[k], b.direction[k], t)
            lightColor[k] = lerp(a.color[k], b.color[k], t)
            ambient[k] = lerp(a.ambient[k], b.ambient[k], t)
        }
        val len = sqrt(lightDir[0] * lightDir[0] + lightDir[1] * lightDir[1] + lightDir[2] * lightDir[2])
        for (k in 0 until 3) lightDir[k] /= len
        shader.light(lightDir, lightColor, ambient)
    }

    private fun setLight(light: SceneLight) = shader.light(light.direction, light.color, light.ambient)

    // ------------------------------------------------------------------ background

    private fun drawBackgrounds(s: FrameSnapshot, cam: StageCamera) {
        val scenes = Scenes.all
        val current = scenes[s.sceneIndex]
        if (s.sceneReveal >= 1f) {
            drawBackground(s, cam, current)
            return
        }
        // Circular wipe: the previous scene stays underneath while the new one grows out of the bin. The new scene
        // is drawn only where a stencil disc has been stamped.
        drawBackground(s, cam, scenes[(s.sceneIndex + scenes.size - 1) % scenes.size])
        val w = s.width
        val h = s.height
        val maxR = hypot(max(s.sceneRevealX, w - s.sceneRevealX), max(s.sceneRevealY, h - s.sceneRevealY))
        val r = maxR * easeOutQuad(s.sceneReveal)

        GLES30.glEnable(GLES30.GL_STENCIL_TEST)
        GLES30.glStencilFunc(GLES30.GL_ALWAYS, 1, 0xFF)
        GLES30.glStencilOp(GLES30.GL_KEEP, GLES30.GL_KEEP, GLES30.GL_REPLACE)
        GLES30.glColorMask(false, false, false, false)
        GLES30.glDepthMask(false)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        shader.viewProjection(cam.screenOrtho)
        shader.mode(FragMode.SOLID)
        shader.lit(0f)
        shader.color(1f, 1f, 1f, 1f)
        stack.reset()
        stack.translate(s.sceneRevealX, -s.sceneRevealY, 0f)
        stack.scale(r, r, 1f)
        drawMesh(meshes.disc)
        GLES30.glColorMask(true, true, true, true)
        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glStencilFunc(GLES30.GL_EQUAL, 1, 0xFF)
        GLES30.glStencilOp(GLES30.GL_KEEP, GLES30.GL_KEEP, GLES30.GL_KEEP)
        drawBackground(s, cam, current)
        GLES30.glDisable(GLES30.GL_STENCIL_TEST)
    }

    private fun drawBackground(s: FrameSnapshot, cam: StageCamera, scene: Scene) {
        val h = s.height
        setLight(SceneLights.of(scene))
        val horizonY = cam.screenYOfPlane(-s.groundY, -h * FLOOR_BACK_Z_FRAC)
        drawSky(s, cam, scene, horizonY)

        shader.viewProjection(cam.viewProjection)
        stack.reset()
        when (scene.sky) {
            SkyObject.SUN -> drawSun(s, cam)
            SkyObject.LOW_SUN -> drawGlowingBall(
                cam, s.width * LOW_SUN_X_FRAC, horizonY - h * LOW_SUN_ABOVE_HORIZON_FRAC, h * LOW_SUN_R_FRAC,
                -h * SKY_Z_FRAC, LOW_SUN, LOW_SUN_GLOW, glowAlpha = 0.5f, glowScale = 2.2f,
            )
            SkyObject.MOON -> drawMoon(s, cam)
            SkyObject.RAINBOW -> {
                drawSun(s, cam)
                drawRainbow(s, cam)
            }
            SkyObject.NONE -> Unit
        }
        if (scene.ambient == Ambient.CLOUDS || scene.ambient == Ambient.PETALS) drawClouds(s, cam, scene.cloud)
        drawHills(s, cam, scene)
        drawLawn(s, cam, scene)
        drawAmbient(s, cam, scene.ambient)
    }

    private fun drawSky(s: FrameSnapshot, cam: StageCamera, scene: Scene, horizonY: Float) {
        val w = s.width
        val h = s.height
        shader.viewProjection(cam.screenOrtho)
        shader.forceDepth(SKY_DEPTH)
        GLES30.glDepthFunc(GLES30.GL_ALWAYS)   // also resets depth inside the wipe circle
        shader.lit(0f)
        shader.mode(FragMode.GRADIENT)
        shader.color(scene.skyTop)
        shader.color2(scene.skyBottom)
        stack.reset()
        stack.translate(w / 2, -horizonY / 2, 0f)
        stack.scale(w, horizonY, 1f)
        drawMesh(meshes.quad)
        shader.mode(FragMode.SOLID)
        shader.color(scene.skyBottom)
        stack.reset()
        stack.translate(w / 2, -(horizonY + h) / 2, 0f)
        stack.scale(w, h - horizonY, 1f)
        drawMesh(meshes.quad)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        shader.forceDepth(null)
        shader.viewProjection(cam.viewProjection)
    }

    /** Moves the stack origin to the point at depth [z] that appears at screen ([sx], [sy]). */
    private fun moveTo(cam: StageCamera, sx: Float, sy: Float, z: Float) = stack.translate(cam.rayX(sx, z), cam.rayY(sy, z), z)

    /** World units per screen pixel at depth [z]. */
    private fun worldPerPx(cam: StageCamera, z: Float): Float = 1f / cam.scaleAt(z)

    private fun drawSun(s: FrameSnapshot, cam: StageCamera) {
        val h = s.height
        drawGlowingBall(cam, s.width * SUN_X_FRAC, h * SUN_Y_FRAC, h * SUN_R_FRAC, -h * SKY_Z_FRAC, SUN, SUN_GLOW, glowAlpha = 0.45f, glowScale = 2.1f)
    }

    /** An unlit ball with a soft halo behind it, sized in screen pixels. */
    private fun drawGlowingBall(cam: StageCamera, sx: Float, sy: Float, rPx: Float, z: Float, color: Long, glow: Long, glowAlpha: Float, glowScale: Float) {
        val r = rPx * worldPerPx(cam, z)
        shader.lit(0f)
        stack.push()
        moveTo(cam, sx, sy, z)
        stack.scale(r)
        shader.mode(FragMode.SOLID)
        shader.color(color)
        drawMesh(meshes.sphere)
        stack.translate(0f, 0f, -1.05f)
        stack.scale(glowScale * 2f)
        shader.mode(FragMode.SOFT_DISC)
        shader.color(glow, glowAlpha)
        GLES30.glDepthMask(false)
        drawMesh(meshes.quad)
        GLES30.glDepthMask(true)
        stack.pop()
    }

    private fun drawMoon(s: FrameSnapshot, cam: StageCamera) {
        val h = s.height
        val z = -h * SKY_Z_FRAC
        drawGlowingBall(cam, s.width * (1f - SUN_X_FRAC), h * SUN_Y_FRAC, h * SUN_R_FRAC, z, MOON, MOON_GLOW, glowAlpha = 0.25f, glowScale = 1.9f)
        // Craters: shallow bumps on the face toward the viewer (offsets from the 2D moon, y flipped).
        stack.push()
        moveTo(cam, s.width * (1f - SUN_X_FRAC), h * SUN_Y_FRAC, z)
        stack.scale(h * SUN_R_FRAC * worldPerPx(cam, z))
        shader.mode(FragMode.SOLID)
        shader.color(MOON_CRATER)
        shader.lit(0f)
        for ((cx, cy, cr) in listOf(Triple(-0.3f, 0.2f, 0.22f), Triple(0.35f, -0.25f, 0.14f), Triple(0.05f, -0.45f, 0.1f))) {
            stack.push()
            stack.translate(cx, cy, sqrt(1f - cx * cx - cy * cy) * 0.97f)
            stack.scale(cr, cr, cr * 0.3f)
            drawMesh(meshes.sphereLow)
            stack.pop()
        }
        stack.pop()
    }

    private fun drawRainbow(s: FrameSnapshot, cam: StageCamera) {
        val h = s.height
        val z = -h * RAINBOW_Z_FRAC
        val k = h * worldPerPx(cam, z)
        shader.mode(FragMode.SOLID)
        shader.lit(0.25f)
        GLES30.glDepthMask(false)
        for ((i, color) in RAINBOW_COLORS.withIndex()) {
            val r = RAINBOW_R_FRAC - RAINBOW_BAND_FRAC * i - RAINBOW_BAND_FRAC / 2
            stack.push()
            moveTo(cam, s.width * 0.5f, s.groundY + h * 0.15f, z)
            stack.scale(r * k)
            shader.color(color, RAINBOW_ALPHA)
            drawMesh(meshes.rainbow[i])
            stack.pop()
        }
        GLES30.glDepthMask(true)
    }

    /** Puffy clouds made of lit spheres, drifting at the same screen positions and speeds as the 2D clouds. */
    private fun drawClouds(s: FrameSnapshot, cam: StageCamera, color: Long) {
        val w = s.width
        val h = s.height
        shader.mode(FragMode.SOLID)
        shader.color(color)
        shader.lit(0.55f)
        shader.specular(0f)
        for (i in 0 until CLOUD_COUNT) {
            val z = -h * CLOUD_Z_FRAC * (1f + 0.15f * i)
            val cloudW = h * (0.18f + 0.05f * i)
            val span = w + cloudW * 2
            val x = ((w * 0.3f * i + s.time * w * CLOUD_SPEED_FRAC * (1f + 0.3f * i)) % span) - cloudW
            val y = h * (0.12f + 0.11f * i)
            val r = cloudW * 0.22f
            stack.push()
            moveTo(cam, x, y, z)
            stack.scale(r * worldPerPx(cam, z))
            for ((px, py, pr) in listOf(Triple(0f, 0f, 1f), Triple(1.1f, 0.3f, 1.3f), Triple(2.3f, 0f, 1f))) {
                stack.push()
                stack.translate(px, py, 0f)
                stack.scale(pr, pr, pr * 0.8f)
                drawMesh(meshes.sphere)
                stack.pop()
            }
            stack.push()
            stack.translate(1.15f, -0.35f, 0f)
            stack.scale(1.75f, 0.6f, 0.8f)
            drawMesh(meshes.sphere)
            stack.pop()
            stack.pop()
        }
    }

    /** Two rows of rounded hills along the back of the lawn; the back row is hazier. */
    private fun drawHills(s: FrameSnapshot, cam: StageCamera, scene: Scene) {
        val h = s.height
        val skyBottom = Rgba(scene.skyBottom)
        shader.mode(FragMode.SOLID)
        shader.lit(0.85f)
        for (row in 0..1) {
            val back = row == 0
            val z = -h * (if (back) HILL_BACK_Z_FRAC else HILL_FRONT_Z_FRAC)
            val left = cam.rayX(0f, z)
            val right = cam.rayX(s.width, z)
            val spacing = (right - left) / (HILL_COUNT - 1)
            shader.color(if (back) scene.grassDark else scene.grass)
            shader.fog(skyBottom.r, skyBottom.g, skyBottom.b, if (back) HILL_BACK_FOG else 0f)
            for (i in 0 until HILL_COUNT) {
                val jitter = hash01(i, 11 + row) - 0.5f
                val x = left + spacing * (i + (if (back) 0.5f else 0f) + jitter * 0.4f)
                val size = 0.75f + 0.5f * hash01(i, 21 + row)
                stack.push()
                stack.translate(x, -s.groundY, z)
                stack.scale(h * HILL_RX_FRAC * size, h * HILL_RY_FRAC * size * (if (back) 1.25f else 1f), h * HILL_RZ_FRAC)
                drawMesh(meshes.sphere)
                stack.pop()
            }
        }
        shader.fog(0f, 0f, 0f, 0f)
    }

    /** The lawn the bins stand on, ending at the front in a grass lip over a dirt face (the 2D grass/dirt bands). */
    private fun drawLawn(s: FrameSnapshot, cam: StageCamera, scene: Scene) {
        val h = s.height
        val gy = s.groundY
        val front = h * FLOOR_FRONT_Z_FRAC
        val back = -h * FLOOR_BACK_Z_FRAC
        val width = s.width * FLOOR_WIDTH_FACTOR
        shader.mode(FragMode.SOLID)
        shader.lit(1f)
        shader.specular(0f)

        shader.color(scene.grass)
        stack.push()
        stack.translate(cam.eyeX, -gy, (front + back) / 2)
        stack.scale(width, 1f, front - back)
        drawMesh(meshes.floor)
        stack.pop()

        shader.color(scene.dirt)
        stack.push()
        stack.translate(cam.eyeX, -gy - h * FLOOR_FACE_DEPTH_FRAC / 2, front)
        stack.scale(width, h * FLOOR_FACE_DEPTH_FRAC, 1f)
        drawMesh(meshes.quad)
        stack.pop()

        shader.color(scene.grassDark)
        stack.push()
        stack.translate(cam.eyeX, -gy, front)
        stack.scale(width, h * LIP_R_FRAC, h * LIP_R_FRAC)
        drawMesh(meshes.cylinder)
        stack.pop()
    }

    private fun drawAmbient(s: FrameSnapshot, cam: StageCamera, ambient: Ambient) {
        val w = s.width
        val h = s.height
        val gy = s.groundY
        val time = s.time
        val base = h * AMBIENT_SIZE_FRAC
        // Depth-spread decorations keep one world size, so nearer ones look bigger.
        val midZ = -h * (AMBIENT_NEAR_Z_FRAC + AMBIENT_FAR_Z_FRAC) / 2
        val baseWorld = base * worldPerPx(cam, midZ)
        fun depth(i: Int): Float = -h * lerp(AMBIENT_NEAR_Z_FRAC, AMBIENT_FAR_Z_FRAC, hash01(i, 6))

        shader.lit(0f)
        GLES30.glDepthMask(false)
        when (ambient) {
            Ambient.CLOUDS -> Unit
            Ambient.STARS -> {
                val z = -h * SKY_Z_FRAC
                shader.mode(FragMode.DISC)
                for (i in 0 until STAR_COUNT) {
                    val twinkle = 0.5f + 0.5f * sin(time * (1.5f + hash01(i, 3) * 2f) + hash01(i, 4) * TWO_PI)
                    val r = base * (0.3f + 0.5f * hash01(i, 5)) * worldPerPx(cam, z)
                    shader.color(STAR, 0.3f + 0.7f * twinkle)
                    billboard(cam, w * hash01(i, 1), gy * 0.7f * hash01(i, 2), z, r)
                }
            }
            Ambient.SNOW -> {
                shader.mode(FragMode.DISC)
                shader.color(SNOWFLAKE, 0.85f)
                for (i in 0 until SNOW_COUNT) {
                    val speed = h * SNOW_FALL_FRAC * (0.7f + 0.6f * hash01(i, 3))
                    val y = (h * hash01(i, 2) + time * speed) % (h + base * 2) - base
                    val x = w * hash01(i, 1) + sin(time * 0.8f + hash01(i, 4) * TWO_PI) * base * 3
                    billboard(cam, x, y, depth(i), baseWorld * (0.35f + 0.5f * hash01(i, 5)))
                }
            }
            Ambient.FIREFLIES -> for (i in 0 until FIREFLY_COUNT) {
                val phase = hash01(i, 4) * TWO_PI
                val x = w * hash01(i, 1) + sin(time * 0.5f + phase) * w * 0.04f
                val y = gy * (0.35f + 0.6f * hash01(i, 2)) + cos(time * 0.7f + phase) * h * 0.03f
                val glow = (0.5f + 0.5f * sin(time * (2f + hash01(i, 3) * 3f) + phase)).let { it * it }
                val z = depth(i)
                shader.mode(FragMode.SOFT_DISC)
                shader.color(FIREFLY, 0.6f * glow)
                billboard(cam, x, y, z, baseWorld * 2.6f)
                shader.mode(FragMode.DISC)
                shader.color(FIREFLY, 0.3f + 0.7f * glow)
                billboard(cam, x, y, z, baseWorld * 0.55f)
            }
            Ambient.BUBBLES -> for (i in 0 until BUBBLE_COUNT) {
                val speed = h * BUBBLE_RISE_FRAC * (0.6f + 0.8f * hash01(i, 3))
                val span = h + base * 6
                val y = span - ((h * hash01(i, 2) + time * speed) % span)
                val x = w * hash01(i, 1) + sin(time * 1.2f + hash01(i, 4) * TWO_PI) * base * 2
                val r = baseWorld * (0.6f + 1.4f * hash01(i, 5))
                val z = depth(i)
                shader.mode(FragMode.DISC)
                shader.color(BUBBLE, 0.4f)
                billboard(cam, x, y, z, r)
                // Highlight, offset in screen pixels then converted at the bubble's depth.
                val off = r * 0.35f / worldPerPx(cam, z)
                shader.color(BUBBLE, 0.6f)
                billboard(cam, x - off, y - off, z + 0.5f, r * 0.25f)
            }
            Ambient.PETALS -> {
                GLES30.glDepthMask(true)
                shader.mode(FragMode.SOLID)
                shader.color(PETAL)
                shader.lit(0.7f)
                for (i in 0 until PETAL_COUNT) {
                    val fall = h * PETAL_FALL_FRAC * (0.7f + 0.6f * hash01(i, 3))
                    val y = (h * hash01(i, 2) + time * fall) % (h + base * 2) - base
                    val x = (w * hash01(i, 1) + time * w * PETAL_DRIFT_FRAC + sin(time * 1.5f + hash01(i, 4) * TWO_PI) * base * 4) % (w + base * 4) - base * 2
                    val rot = time * 120f * (hash01(i, 5) - 0.5f)
                    stack.push()
                    moveTo(cam, x, y, depth(i))
                    stack.rotateZ(-rot)
                    stack.rotateX(rot * 0.7f + hash01(i, 7) * 360f)
                    stack.scale(baseWorld * 1.1f, baseWorld * 0.6f, baseWorld * 0.2f)
                    drawMesh(meshes.sphereLow)
                    stack.pop()
                }
            }
        }
        GLES30.glDepthMask(true)
    }

    /** A camera-facing quad of radius [r] (world units) centred where screen point ([sx], [sy]) is at depth [z]. */
    private fun billboard(cam: StageCamera, sx: Float, sy: Float, z: Float, r: Float) {
        stack.push()
        moveTo(cam, sx, sy, z)
        stack.scale(r * 2f, r * 2f, 1f)
        drawMesh(meshes.quad)
        stack.pop()
    }

    // ------------------------------------------------------------------ foreground

    private fun drawShadows(s: FrameSnapshot, cam: StageCamera) {
        val h = s.height
        val gy = s.groundY
        shader.mode(FragMode.SOFT_DISC)
        shader.lit(0f)
        GLES30.glDepthMask(false)
        for (bin in s.bins) {
            val depth = bin.width * BIN_DEPTH_FACTOR
            shader.color(SHADOW, BIN_SHADOW_ALPHA)
            stack.push()
            stack.translate(bin.centerX, -gy + SHADOW_LIFT, -h * BIN_FRONT_Z_FRAC - depth / 2)
            stack.rotateX(-90f)
            stack.scale(bin.width * BIN_SHADOW_SPREAD, depth * BIN_SHADOW_SPREAD, 1f)
            drawMesh(meshes.quad)
            stack.pop()
        }
        for (item in s.items) {
            if (item.state == ItemState.SINKING) continue
            val p = itemPosition(s, cam, item)
            val heightAbove = (gy - item.radius - item.y).coerceAtLeast(0f)
            val k = (1f - heightAbove / (gy * 1.2f)).coerceIn(0.3f, 1f)
            val d = item.radius * 0.9f * k * 2f * 1.6f
            shader.color(SHADOW, ITEM_SHADOW_ALPHA * k)
            stack.push()
            // A lifted item can be nearer than the lawn's front edge; keep its shadow on the grass.
            val shadowZ = min(p[2], h * FLOOR_FRONT_Z_FRAC - d / 2)
            stack.translate(p[0], -gy + SHADOW_LIFT * 2, shadowZ)
            stack.rotateX(-90f)
            stack.scale(d, d, 1f)
            drawMesh(meshes.quad)
            stack.pop()
        }
        GLES30.glDepthMask(true)
    }

    private fun drawBin(s: FrameSnapshot, bin: BinSnap, colors: BinColors) {
        val bw = bin.width
        val bh = bin.height
        val bd = bw * BIN_DEPTH_FACTOR

        // Capture bounce: squash then a damped wobble. Tap: rotational wiggle. Same curves as the 2D bins.
        val tc = bin.sinceCapture
        val squash = if (tc < 1f) SQUASH_AMOUNT * exp(-5f * tc) * cos(18f * tc) else 0f
        val tw = bin.sinceWiggle
        val wiggle = if (tw < 1.2f) WIGGLE_DEG * exp(-3f * tw) * sin(25f * tw) else 0f
        val lidFlap = if (tc < 0.8f) 1f - LID_FLAP_AMOUNT * exp(-4f * tc) * sin(14f * tc).coerceAtLeast(0f) else 1f

        stack.push()
        stack.translate(bin.centerX, -bin.bottom, -s.height * BIN_FRONT_Z_FRAC)
        stack.rotateZ(-wiggle)   // screen-clockwise in 2D is negative about +z here
        stack.scale(1f + squash, 1f - squash, 1f + squash)

        // Body shell, rim and dark interior.
        shader.mode(FragMode.SOLID)
        shader.lit(1f)
        shader.specular(0.35f)
        stack.push()
        stack.scale(bw, bh, bd)
        shader.color(colors.body)
        drawMesh(meshes.binOuter)
        shader.color(colors.lid)
        drawMesh(meshes.binRim)
        shader.color(INTERIOR)
        shader.specular(0f)
        shader.lit(0.6f)
        drawMesh(meshes.binInner)
        stack.pop()

        drawWheelsAndHandle(bw, bh, bd, colors)
        drawLid(bw, bh, bd, lidFlap, colors)
        drawLabel(bw, bh, bin.index, colors)
        drawFace(s, bin, bw, bh)
        stack.pop()
    }

    private fun drawWheelsAndHandle(bw: Float, bh: Float, bd: Float, colors: BinColors) {
        val wr = bh * WHEEL_R_FRAC
        val ww = wr * WHEEL_W_FACTOR
        val bottomHalf = bw * (0.5f - BIN_TAPER_FRAC)
        val wheelZ = -bd + wr * 1.2f
        shader.mode(FragMode.SOLID)
        shader.lit(1f)
        shader.specular(0.15f)
        shader.color(WHEEL)
        stack.push()
        stack.translate(0f, wr, wheelZ)
        stack.scale(bottomHalf * 2f + ww, wr * 0.18f, wr * 0.18f)
        drawMesh(meshes.cylinder)
        stack.pop()
        for (side in floatArrayOf(-1f, 1f)) {
            shader.color(WHEEL)
            stack.push()
            stack.translate(side * (bottomHalf + ww * 0.55f), wr, wheelZ)
            stack.scale(ww, wr, wr)
            drawMesh(meshes.cylinder)
            stack.pop()
            shader.color(HUB)
            stack.push()
            stack.translate(side * (bottomHalf + ww * 0.6f), wr, wheelZ)
            stack.scale(ww * 1.05f, wr * 0.4f, wr * 0.4f)
            drawMesh(meshes.cylinder)
            stack.pop()
        }
        // Handle bar across the back, under the hinge.
        shader.color(colors.lid)
        stack.push()
        stack.translate(0f, bh * HANDLE_Y_FRAC, -bd - bw * HANDLE_R_FRAC * 2f)
        stack.scale(bw * HANDLE_LEN_FRAC, bw * HANDLE_R_FRAC, bw * HANDLE_R_FRAC)
        drawMesh(meshes.cylinder)
        stack.pop()
    }

    /** The lid hinges at the back top edge and stands open; a capture flaps it shut briefly. */
    private fun drawLid(bw: Float, bh: Float, bd: Float, lidFlap: Float, colors: BinColors) {
        val overhang = bw * LID_OVERHANG_FRAC
        val lidW = bw + 2f * overhang
        val lidD = bd + 2f * overhang
        val lt = bh * LID_THICK_FRAC
        val angle = LID_OPEN_DEG - (1f - lidFlap) / LID_FLAP_AMOUNT * LID_FLAP_DEG
        stack.push()
        stack.translate(0f, bh, -bd)
        stack.rotateX(-angle)
        // Closed pose relative to the hinge: lying on the rim, reaching forward to the front edge.
        stack.push()
        stack.translate(0f, lt / 2, bd / 2)
        stack.rotateX(-90f)   // slab outline (XY) becomes the lid's top (XZ); its thickness becomes Y
        stack.scale(lidW, lidD, lt)
        shader.color(colors.lid)
        shader.specular(0.3f)
        drawMesh(meshes.lid)
        stack.pop()
        // Inner panel on the underside, which faces the viewer while the lid is open.
        val inset = bw * LID_PANEL_INSET_FRAC
        val lid = Rgba(colors.lid)
        val label = Rgba(colors.label)
        shader.color(lerp(lid.r, label.r, 0.3f), lerp(lid.g, label.g, 0.3f), lerp(lid.b, label.b, 0.3f), 1f)
        stack.push()
        stack.translate(0f, -lt * 0.15f, bd / 2)
        stack.rotateX(-90f)
        stack.scale(lidW - 2f * inset, lidD - 2f * inset, lt * 0.3f)
        drawMesh(meshes.lid)
        stack.pop()
        stack.pop()
    }

    private fun drawLabel(bw: Float, bh: Float, binIndex: Int, colors: BinColors) {
        val labelW = bw * LABEL_W_FRAC
        val labelH = bh * LABEL_H_FRAC
        val thick = bw * LABEL_THICK_FRAC
        val cy = bh - bh * LABEL_Y_FRAC - labelH / 2
        shader.mode(FragMode.SOLID)
        shader.lit(1f)
        shader.specular(0.2f)
        shader.color(colors.label)
        stack.push()
        stack.translate(0f, cy, thick / 2)
        stack.scale(labelW, labelH, thick)
        drawMesh(meshes.label)
        stack.pop()
        val symbol = when (binIndex) {
            0 -> Sprites.RECYCLE
            2 -> Sprites.SEEDLING
            else -> 0
        }
        if (symbol != 0) {
            textures.bind(symbol)
            shader.mode(FragMode.TEXTURE)
            shader.color(1f, 1f, 1f, 1f)
            shader.lit(0.3f)
            shader.specular(0f)
            shader.alphaCut(0.5f)
            stack.push()
            stack.translate(0f, cy, thick + DECAL_LIFT)
            stack.scale(labelH * 0.8f)
            drawMesh(meshes.quad)
            stack.pop()
        }
    }

    private fun drawFace(s: FrameSnapshot, bin: BinSnap, bw: Float, bh: Float) {
        val eyeY = bh - bh * EYE_Y_FRAC
        val rx = bw * EYE_RX_FRAC
        val ry = bw * EYE_RY_FRAC
        val bulge = rx * EYE_BULGE_FACTOR
        val blink = if (bin.blinking) 0.1f else 1f
        val travel = bw * PUPIL_TRAVEL_FRAC
        val pr = bw * PUPIL_R_FRAC
        shader.mode(FragMode.SOLID)
        for (side in floatArrayOf(-1f, 1f)) {
            val ex = side * bw * EYE_DX_FRAC
            if (bin.happy) {
                // Happy eyes: arches.
                shader.color(PUPIL)
                shader.lit(0.5f)
                shader.specular(0.4f)
                stack.push()
                stack.translate(ex, eyeY - 0.1f * ry, rx * HAPPY_EYE_TUBE * 0.4f)
                stack.scale(rx)
                drawMesh(meshes.happyEye)
                stack.pop()
                continue
            }
            shader.color(EYE_WHITE)
            shader.lit(0.6f)
            shader.specular(0.5f)
            stack.push()
            stack.translate(ex, eyeY, 0f)
            stack.scale(rx, ry * blink, bulge)
            drawMesh(meshes.sphere)
            stack.pop()
            if (blink < 1f) continue

            // Pupils look toward the gaze target, sitting on the bulge of the eye.
            var px = 0f
            var py = 0f
            val gx = s.gazeX
            val gy = s.gazeY
            if (gx != null && gy != null) {
                val dx = gx - (bin.centerX + ex)
                val dy = gy - (bin.top + bh * EYE_Y_FRAC)
                val d = hypot(dx, dy)
                if (d > 1f) {
                    px = dx / d * travel
                    py = -dy / d * travel
                }
            }
            val pz = bulge * sqrt(max(0f, 1f - (px / rx) * (px / rx) - (py / ry) * (py / ry)))
            shader.color(PUPIL)
            shader.lit(0.6f)
            shader.specular(0.8f)
            stack.push()
            stack.translate(ex + px, eyeY + py, pz)
            stack.scale(pr, pr, pr * 0.45f)
            drawMesh(meshes.sphere)
            stack.pop()
            shader.color(EYE_WHITE)
            shader.lit(0f)
            stack.push()
            stack.translate(ex + px - bw * 0.012f, eyeY + py + bw * 0.014f, pz + pr * 0.45f)
            stack.scale(pr * 0.35f, pr * 0.35f, pr * 0.15f)
            drawMesh(meshes.sphereLow)
            stack.pop()
        }

        val mouthY = bh - bh * MOUTH_Y_FRAC
        val mw = bw * MOUTH_W_FRAC
        if (bin.happy) {
            shader.lit(0f)
            shader.color(MOUTH)
            stack.push()
            stack.translate(0f, mouthY, DECAL_LIFT)
            stack.scale(0.5f * mw, 0.35f * mw, 1f)
            drawMesh(meshes.lowerHalfDisc)
            stack.pop()
            shader.color(TONGUE)
            stack.push()
            stack.translate(0f, mouthY - 0.175f * mw, DECAL_LIFT * 2f)
            stack.scale(0.25f * mw, 0.175f * mw, 1f)
            drawMesh(meshes.lowerHalfDisc)
            stack.pop()
        } else {
            shader.color(MOUTH)
            shader.lit(0.5f)
            shader.specular(0.4f)
            stack.push()
            stack.translate(0f, mouthY + 0.05f * mw, 0.5f * mw * SMILE_TUBE * 0.4f)
            stack.scale(0.5f * mw)
            drawMesh(meshes.smile)
            stack.pop()
        }
        shader.specular(0f)
    }

    /**
     * World position of an item. Free items sit on the play plane, so they are drawn exactly where [GameWorld] puts
     * them. A held item is pulled toward the camera along the ray through its screen point, so it still sits under
     * the finger but looks lifted and casts its shadow behind. A captured item moves back into the bin.
     */
    private fun itemPosition(s: FrameSnapshot, cam: StageCamera, item: ItemSnap): FloatArray {
        if (item.state == ItemState.SINKING) {
            val bd = (s.bins.getOrNull(item.binIndex)?.width ?: 0f) * BIN_DEPTH_FACTOR
            val z = -(s.height * BIN_FRONT_Z_FRAC + bd / 2) * smoothstep(0f, SINK_Z_TIME, item.sinkAge)
            return floatArrayOf(item.x, -item.y, z, 1f)
        }
        val lift = ((item.scale - 1f) / HELD_SCALE_DELTA).coerceIn(0f, 1f)
        val z = lift * s.height * HELD_LIFT_Z_FRAC
        return floatArrayOf(cam.rayX(item.x, z), cam.rayY(item.y, z), z, 1f / cam.scaleAt(z))
    }

    /** An emoji as a solid token: a stack of alpha-cut layers, the inner ones darker so the edge reads as a side. */
    private fun drawItem(s: FrameSnapshot, cam: StageCamera, item: ItemSnap) {
        val p = itemPosition(s, cam, item)
        val size = item.radius * 2f * item.scale * p[3]
        val thick = size * ITEM_THICKNESS_FACTOR
        val yawAmp = when {
            item.state == ItemState.HELD -> 0f
            item.onGround -> ITEM_YAW_DEG * ITEM_YAW_REST_FACTOR
            else -> ITEM_YAW_DEG
        }
        val yaw = yawAmp * sin(s.time * 1.3f + item.id * 1.7f)
        textures.bind(item.drawable)
        shader.mode(FragMode.TEXTURE)
        shader.lit(ITEM_LIT)
        shader.specular(0.15f)
        shader.alphaCut(0.5f)
        stack.push()
        stack.translate(p[0], p[1], p[2])
        stack.rotateZ(-item.rotation)
        stack.rotateY(yaw)
        for (layer in ITEM_LAYERS - 1 downTo 0) {
            val t = layer.toFloat() / (ITEM_LAYERS - 1)
            val shade = if (layer == ITEM_LAYERS - 1 || layer == 0) 1f else ITEM_EDGE_SHADE
            shader.color(shade, shade, shade, 1f)
            stack.push()
            stack.translate(0f, 0f, (t - 0.5f) * thick)
            stack.scale(size, size, 1f)
            drawMesh(meshes.quad)
            stack.pop()
        }
        stack.pop()
        shader.specular(0f)
    }

    private fun drawParticles(s: FrameSnapshot) {
        val h = s.height
        GLES30.glDepthMask(false)
        for (p in s.particles) {
            val t = p.t
            // Particle size never changes, so it seeds a stable per-particle depth.
            val seed = java.lang.Float.floatToIntBits(p.size)
            when (p.kind) {
                ParticleKind.CONFETTI -> {
                    val alpha = if (t > 0.7f) (1f - t) / 0.3f else 1f
                    val z = h * (CONFETTI_Z_FRAC + CONFETTI_Z_SPREAD_FRAC * hash01(seed, 9))
                    shader.mode(FragMode.SOLID)
                    shader.lit(0.7f)
                    shader.specular(0.4f)
                    shader.color(p.color, alpha)
                    stack.push()
                    stack.translate(p.x, -p.y, z)
                    stack.rotateZ(-p.rotation)
                    stack.rotateX(p.rotation * CONFETTI_FLIP_FACTOR)
                    stack.scale(p.size, p.size * 0.6f, 1f)
                    drawMesh(meshes.quad)
                    stack.pop()
                }
                ParticleKind.SPRITE -> {
                    val alpha = if (t > 0.6f) (1f - t) / 0.4f else 1f
                    drawSprite(p.sprite, p.x, p.y, h * SPRITE_Z_FRAC, p.size * (1f - 0.4f * t), p.rotation, alpha)
                }
                ParticleKind.RING -> {
                    val radius = p.size * p.age
                    val stroke = p.size * 0.06f * (1f - t) + 2f
                    val outer = radius + stroke / 2
                    shader.mode(FragMode.RING)
                    shader.lit(0f)
                    shader.ringInner(((radius - stroke / 2) / outer).coerceAtLeast(0f))
                    shader.color(p.color, 1f - t)
                    stack.push()
                    stack.translate(p.x, -p.y, h * SPRITE_Z_FRAC)
                    stack.scale(outer * 2f, outer * 2f, 1f)
                    drawMesh(meshes.quad)
                    stack.pop()
                }
                ParticleKind.POP -> {
                    val grow = easeOutBack(min(1f, t * 3f))
                    val alpha = if (t > 0.65f) (1f - t) / 0.35f else 1f
                    drawSprite(p.sprite, p.x, p.y, h * SPRITE_Z_FRAC, p.size * grow, p.rotation + sin(p.age * 12f) * 6f, alpha)
                }
            }
        }
        shader.specular(0f)
        GLES30.glDepthMask(true)
    }

    private fun drawSprite(sprite: Int, x: Float, y: Float, z: Float, size: Float, rotation: Float, alpha: Float) {
        textures.bind(sprite)
        shader.mode(FragMode.TEXTURE)
        shader.lit(0f)
        shader.alphaCut(0.01f)
        shader.color(1f, 1f, 1f, alpha)
        stack.push()
        stack.translate(x, -y, z)
        stack.rotateZ(-rotation)
        stack.scale(size, size, 1f)
        drawMesh(meshes.quad)
        stack.pop()
        shader.alphaCut(0.5f)
    }

    private fun drawMesh(mesh: GlMesh) {
        shader.model(stack.current)
        mesh.draw()
    }
}
