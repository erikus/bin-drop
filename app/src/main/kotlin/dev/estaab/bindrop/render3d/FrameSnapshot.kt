package dev.estaab.bindrop.render3d

import dev.estaab.bindrop.game.GameWorld
import dev.estaab.bindrop.game.ItemState
import dev.estaab.bindrop.game.Palette
import dev.estaab.bindrop.game.ParticleKind

class BinSnap(
    val index: Int,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val sinceCapture: Float,
    val sinceWiggle: Float,
    val blinking: Boolean,
    val happy: Boolean,
) {
    val centerX: Float get() = left + width / 2
    val bottom: Float get() = top + height
}

class ItemSnap(
    val id: Int,
    val drawable: Int,
    val x: Float,
    val y: Float,
    val radius: Float,
    val scale: Float,
    val rotation: Float,
    val state: ItemState,
    val onGround: Boolean,
    val sinkAge: Float,
    /** Index of the bin an item is flying to or sinking into, or -1. */
    val binIndex: Int,
)

class ParticleSnap(
    val kind: ParticleKind,
    val x: Float,
    val y: Float,
    val size: Float,
    val rotation: Float,
    val t: Float,
    val age: Float,
    val color: Long,
    val sprite: Int,
)

/**
 * An immutable copy of everything the 3D renderer draws for one frame. [GameWorld] is stepped on the main thread
 * and the GL thread draws, so the renderer never touches the live world; it only ever sees a finished snapshot.
 */
class FrameSnapshot(
    val width: Float,
    val height: Float,
    val time: Float,
    val groundY: Float,
    val sceneIndex: Int,
    val sceneReveal: Float,
    val sceneRevealX: Float,
    val sceneRevealY: Float,
    val palette: Palette,
    val bins: List<BinSnap>,
    val items: List<ItemSnap>,
    val particles: List<ParticleSnap>,
    /** Screen position the bins' eyes look at, or null to look straight ahead. */
    val gazeX: Float?,
    val gazeY: Float?,
) {
    companion object {
        fun of(world: GameWorld, palette: Palette): FrameSnapshot {
            val gaze = world.gazeTarget()
            return FrameSnapshot(
                width = world.width,
                height = world.height,
                time = world.time,
                groundY = world.groundY,
                sceneIndex = world.sceneIndex,
                sceneReveal = world.sceneReveal,
                sceneRevealX = world.sceneRevealX,
                sceneRevealY = world.sceneRevealY,
                palette = palette,
                bins = world.bins.map {
                    BinSnap(it.index, it.left, it.top, it.width, it.height, it.sinceCapture, it.sinceWiggle, it.blinkPhase > 0f, it.happy)
                },
                items = world.items.map {
                    ItemSnap(
                        it.id, it.spec.drawable, it.x, it.y, it.radius, it.scale, it.rotation, it.state, it.onGround,
                        it.sinkAge, it.targetBin?.index ?: -1,
                    )
                },
                particles = world.particles.list.map {
                    ParticleSnap(it.kind, it.x, it.y, it.size, it.rotation, it.t, it.age, it.color, it.sprite)
                },
                gazeX = gaze?.x,
                gazeY = gaze?.y,
            )
        }
    }
}
