package dev.estaab.bindrop.game

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val CONFETTI_GRAVITY_FRAC = 1.6f   // H/s²
private const val CONFETTI_DRAG = 1.2f           // per second
private const val CONFETTI_LIFE = 1.6f
private const val CONFETTI_SPEED_FRAC = 0.9f     // H/s
private const val CONFETTI_SIZE_FRAC = 0.018f    // of H
private const val STAR_RING_COUNT = 10
private const val STAR_RING_SPEED_FRAC = 0.55f
private const val STAR_RING_LIFE = 0.9f
private const val STAR_SIZE_FRAC = 0.05f
private const val RING_LIFE = 0.5f
private const val RING_SPEED_FRAC = 0.7f
private const val POP_LIFE = 1.1f
private const val POP_RISE_FRAC = 0.25f
private const val RAIN_LIFE = 3.2f
private const val RAIN_GRAVITY_FRAC = 0.4f
private const val MAX_PARTICLES = 600

private val CONFETTI_COLORS = longArrayOf(
    0xFFFF5252, 0xFFFFB300, 0xFFFFEB3B, 0xFF69F0AE, 0xFF40C4FF, 0xFF7C4DFF, 0xFFFF4081, 0xFFFFFFFF,
)

enum class ParticleKind { CONFETTI, SPRITE, RING, POP }

class Particle(
    val kind: ParticleKind,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val life: Float,
    val color: Long,
    val size: Float,
    var rotation: Float,
    val angularVel: Float,
    val gravity: Float,
    val drag: Float,
    /** Drawable resource id for SPRITE / POP particles. */
    val sprite: Int = 0,
) {
    var age = 0f
    val alive: Boolean get() = age < life
    /** 0 → 1 over the particle's life. */
    val t: Float get() = (age / life).coerceIn(0f, 1f)
}

/** Simple particle pool. Coordinates are canvas px; sizes derive from the canvas height so effects scale with the screen. */
class Particles(private val random: Random = Random.Default) {
    val list = ArrayList<Particle>()

    fun step(dt: Float) {
        val it = list.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.age += dt
            if (!p.alive) { it.remove(); continue }
            p.vy += p.gravity * dt
            val damp = 1f - (p.drag * dt).coerceIn(0f, 1f)
            p.vx *= damp
            p.vy *= damp
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.rotation += p.angularVel * dt
        }
    }

    fun clear() = list.clear()

    private fun add(p: Particle) {
        if (list.size >= MAX_PARTICLES) list.removeAt(0)
        list.add(p)
    }

    /** Burst of confetti squares/strips flying up and out of (x, y). */
    fun confettiBurst(x: Float, y: Float, h: Float, count: Int) {
        repeat(count) {
            val angle = (-PI / 2 + random.nextDouble(-0.9, 0.9)).toFloat()
            val speed = h * CONFETTI_SPEED_FRAC * random.nextDouble(0.4, 1.0).toFloat()
            add(
                Particle(
                    kind = ParticleKind.CONFETTI,
                    x = x, y = y,
                    vx = cos(angle) * speed, vy = sin(angle) * speed,
                    life = CONFETTI_LIFE * random.nextDouble(0.7, 1.0).toFloat(),
                    color = CONFETTI_COLORS[random.nextInt(CONFETTI_COLORS.size)],
                    size = h * CONFETTI_SIZE_FRAC * random.nextDouble(0.6, 1.3).toFloat(),
                    rotation = random.nextFloat() * 360f,
                    angularVel = random.nextDouble(-540.0, 540.0).toFloat(),
                    gravity = h * CONFETTI_GRAVITY_FRAC,
                    drag = CONFETTI_DRAG,
                )
            )
        }
    }

    /** Ring of star sprites expanding from (x, y). */
    fun starRing(x: Float, y: Float, h: Float, sprite: Int) {
        repeat(STAR_RING_COUNT) { i ->
            val angle = (2 * PI * i / STAR_RING_COUNT).toFloat()
            val speed = h * STAR_RING_SPEED_FRAC
            add(
                Particle(
                    kind = ParticleKind.SPRITE,
                    x = x, y = y,
                    vx = cos(angle) * speed, vy = sin(angle) * speed,
                    life = STAR_RING_LIFE,
                    color = 0xFFFFFFFF,
                    size = h * STAR_SIZE_FRAC,
                    rotation = 0f,
                    angularVel = random.nextDouble(-360.0, 360.0).toFloat(),
                    gravity = 0f,
                    drag = 2.5f,
                    sprite = sprite,
                )
            )
        }
    }

    /** Expanding stroke ring (a shockwave) at (x, y). */
    fun ring(x: Float, y: Float, h: Float, color: Long) {
        add(
            Particle(
                kind = ParticleKind.RING,
                x = x, y = y, vx = 0f, vy = 0f,
                life = RING_LIFE,
                color = color,
                size = h * RING_SPEED_FRAC,   // radius growth per second
                rotation = 0f, angularVel = 0f, gravity = 0f, drag = 0f,
            )
        )
    }

    /** A sprite that pops up (scale in, rise, fade). */
    fun pop(x: Float, y: Float, h: Float, sprite: Int, sizeFrac: Float) {
        add(
            Particle(
                kind = ParticleKind.POP,
                x = x, y = y, vx = 0f, vy = -h * POP_RISE_FRAC,
                life = POP_LIFE,
                color = 0xFFFFFFFF,
                size = h * sizeFrac,
                rotation = random.nextDouble(-15.0, 15.0).toFloat(),
                angularVel = 0f, gravity = 0f, drag = 1.5f,
                sprite = sprite,
            )
        )
    }

    /** Confetti rain across the whole width from above the top edge. */
    fun rain(w: Float, h: Float, count: Int) {
        repeat(count) {
            add(
                Particle(
                    kind = ParticleKind.CONFETTI,
                    x = random.nextFloat() * w,
                    y = -random.nextFloat() * h * 0.8f,
                    vx = random.nextDouble(-0.05, 0.05).toFloat() * h,
                    vy = h * random.nextDouble(0.15, 0.35).toFloat(),
                    life = RAIN_LIFE,
                    color = CONFETTI_COLORS[random.nextInt(CONFETTI_COLORS.size)],
                    size = h * CONFETTI_SIZE_FRAC * random.nextDouble(0.7, 1.4).toFloat(),
                    rotation = random.nextFloat() * 360f,
                    angularVel = random.nextDouble(-300.0, 300.0).toFloat(),
                    gravity = h * RAIN_GRAVITY_FRAC,
                    drag = 0.8f,
                )
            )
        }
    }
}
