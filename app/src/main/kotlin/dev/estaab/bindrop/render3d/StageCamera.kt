package dev.estaab.bindrop.render3d

/** Eye height, as a fraction of H measured down from the top screen edge. Above the bin mouths so we see into them. */
private const val EYE_SCREEN_Y_FRAC = 0.08f
/** Eye distance from the play plane, as a fraction of H. Larger = flatter perspective. */
private const val EYE_DISTANCE_FRAC = 1.6f
private const val NEAR_FRAC = 0.05f
private const val FAR_FRAC = 12f

/**
 * The 3D camera, in world units of screen pixels: x = screen x, y = −screen y (y is up), z points toward the viewer.
 *
 * The frustum is off-axis so that the plane z = 0 (the "play plane") maps exactly onto the screen rectangle:
 * a point (sx, −sy, 0) is drawn at screen pixel (sx, sy). [dev.estaab.bindrop.game.GameWorld] therefore keeps
 * working in screen pixels, unchanged, and touches hit what they appear to hit. Things nearer or farther than the
 * play plane get real perspective, and the eye sits above the bins so their open mouths are visible.
 */
class StageCamera(val width: Float, val height: Float) {
    val eyeX: Float = width / 2
    /** World y of the eye (negative: screen y grows downward). */
    val eyeY: Float = -height * EYE_SCREEN_Y_FRAC
    val eyeZ: Float = height * EYE_DISTANCE_FRAC

    val projection: FloatArray = FloatArray(16)
    val view: FloatArray = Mat4.identity()
    val viewProjection: FloatArray = FloatArray(16)
    /** Maps world (sx, −sy) at any z straight to the screen, for flat overlay passes (sky, wipe mask). */
    val screenOrtho: FloatArray = FloatArray(16)

    init {
        require(width > 0f && height > 0f) { "StageCamera needs a positive size, got ${width}x$height" }
        val near = height * NEAR_FRAC
        val far = height * FAR_FRAC
        val k = near / eyeZ
        Mat4.frustum(
            projection,
            left = (0f - eyeX) * k,
            right = (width - eyeX) * k,
            bottom = (-height - eyeY) * k,
            top = (0f - eyeY) * k,
            near = near,
            far = far,
        )
        view[12] = -eyeX
        view[13] = -eyeY
        view[14] = -eyeZ
        Mat4.multiply(viewProjection, projection, view)
        Mat4.ortho(screenOrtho, 0f, width, -height, 0f, -1f, 1f)
    }

    /** Ratio of on-screen size at depth [z] to on-screen size at the play plane. */
    fun scaleAt(z: Float): Float = eyeZ / (eyeZ - z)

    /** World x of the point at depth [z] that is drawn at screen x [sx]. */
    fun rayX(sx: Float, z: Float): Float = eyeX + (sx - eyeX) / scaleAt(z)

    /** World y of the point at depth [z] that is drawn at screen y [sy]. */
    fun rayY(sy: Float, z: Float): Float = eyeY + (-sy - eyeY) / scaleAt(z)

    /** Screen y at which the horizontal plane at world y [worldY] appears at depth [z]. */
    fun screenYOfPlane(worldY: Float, z: Float): Float = -(eyeY + (worldY - eyeY) * scaleAt(z))
}
