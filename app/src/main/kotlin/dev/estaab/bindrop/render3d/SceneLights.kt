package dev.estaab.bindrop.render3d

import dev.estaab.bindrop.game.Scene
import dev.estaab.bindrop.game.Scenes
import kotlin.math.sqrt

/** Directional key light plus ambient fill for one background. Direction points toward the light. */
class SceneLight(dirX: Float, dirY: Float, dirZ: Float, val color: FloatArray, val ambient: FloatArray) {
    val direction: FloatArray = run {
        val len = sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ)
        floatArrayOf(dirX / len, dirY / len, dirZ / len)
    }
}

/** Light for each scene in [Scenes.all], keyed by scene name. Every scene must have an entry (checked by a test). */
object SceneLights {
    // Sun scenes light from the upper left (where the sun is), moon scenes from the upper right.
    // Key + ambient stay near 1.0 on a face that points at the light, so lit colours don't wash out.
    private val SUN = SceneLight(-0.5f, 0.75f, 0.6f, floatArrayOf(0.78f, 0.75f, 0.68f), floatArrayOf(0.46f, 0.48f, 0.53f))

    val byName: Map<String, SceneLight> = mapOf(
        "Day" to SUN,
        "Sunset" to SceneLight(-0.7f, 0.35f, 0.6f, floatArrayOf(0.85f, 0.6f, 0.42f), floatArrayOf(0.44f, 0.37f, 0.48f)),
        "Night" to SceneLight(0.5f, 0.75f, 0.6f, floatArrayOf(0.45f, 0.5f, 0.68f), floatArrayOf(0.4f, 0.43f, 0.56f)),
        "Snow" to SceneLight(-0.3f, 0.8f, 0.6f, floatArrayOf(0.62f, 0.64f, 0.68f), floatArrayOf(0.5f, 0.53f, 0.58f)),
        "Spring" to SUN,
        "Ocean" to SceneLight(-0.2f, 0.9f, 0.5f, floatArrayOf(0.62f, 0.72f, 0.75f), floatArrayOf(0.42f, 0.52f, 0.58f)),
        "Dusk" to SceneLight(0.5f, 0.75f, 0.6f, floatArrayOf(0.55f, 0.5f, 0.68f), floatArrayOf(0.42f, 0.4f, 0.55f)),
    )

    fun of(scene: Scene): SceneLight = byName[scene.name] ?: error("No 3D light for scene ${scene.name}")
}
