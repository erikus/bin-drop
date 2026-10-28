package dev.estaab.bindrop.game

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// Layout, as fractions of the canvas height (H) or width (W). Everything is laid out in px on resize().
private const val GROUND_Y_FRAC = 0.86f
private const val LEFT_BINS = 1                    // bins at the left edge; the rest sit at the right edge
private const val BIN_GAP_FRAC = 0.06f             // of H, between bins and between a bin and the screen edge
private const val BIN_WIDTH_FRAC = 0.23f           // of H
private const val BIN_HEIGHT_FRAC = 0.42f          // of H
private const val ITEM_RADIUS_FRAC = 0.09f
private const val ITEM_RADIUS_FRAC_EASY = 0.105f

// Physics (H/s, H/s², or dimensionless).
private const val GRAVITY_FRAC = 2.4f
private const val LIFT_FACTOR = 0.6f               // of item radius: how far the held item sits above the finger
private const val HIT_RADIUS_FACTOR = 1.4f
private const val GROUND_BOUNCE = 0.35f
private const val GROUND_FRICTION = 4f             // per second
private const val WALL_BOUNCE = 0.5f
private const val REST_SPEED_FRAC = 0.06f
private const val MAX_THROW_FRAC = 0.7f
private const val THROW_SCALE = 0.3f               // finger velocity → item velocity (low: toddlers flick)
private const val SPIN_PER_VX = 0.25f              // deg/s of spin per px/s of horizontal speed
private const val MAGNET_MARGIN_FACTOR = 0.6f      // of item radius, beyond the mouth edges (normal mode)
private const val MAGNET_MARGIN_FACTOR_EASY = 2.2f
private const val MAGNET_PULL = 8f                 // per-second spring toward the mouth centre
private const val MAGNET_PULL_EASY = 16f
private const val SEPARATION_FACTOR = 0.85f        // resting items are pushed apart below this many radii
private const val SEPARATION_RATE = 6f             // per second

// Timings.
private const val SINK_TIME = 0.55f
private const val SINK_SPEED_FRAC = 0.8f
private const val FLIGHT_TIME = 0.75f
private const val FLIGHT_ARC_FRAC = 0.35f          // of H
private const val SPAWN_DELAY = 0.6f
private const val TAP_MAX_TIME = 0.3f
private const val BIN_TAP_GAP = 0.6f               // max seconds between taps for them to count as consecutive
private const val BIN_TAPS_TO_CYCLE_PALETTE = 3
private const val TAP_SLOP_FACTOR = 0.5f           // of item radius
private const val HELD_SCALE = 1.15f
private const val SCALE_RATE = 12f                 // per second, approach rate for item scale
private const val HOP_SPEED_FRAC = 0.75f
private const val IDLE_HOP_INTERVAL = 8f
private const val IDLE_HOP_MIN_IDLE = 6f
private const val VELOCITY_WINDOW = 0.1f           // seconds of finger history used for throw velocity

// Gameplay.
private const val TARGET_ITEMS = 5
private const val BIG_WIN_EVERY = 5
private const val SCENE_EVERY = 3                  // captures per background change
private const val SCENE_REVEAL_TIME = 1.1f         // seconds for the new background to wipe in
private const val SCENE_RESPAWN_DELAY = 0.9f       // pause before the new scene's items start raining in
private const val POOF_SIZE_FRAC = 0.1f            // of H, sparkle shown where an old-scene item vanished
private const val CONFETTI_PER_CAPTURE = 40
private const val CONFETTI_RAIN = 160
private const val BIN_HAPPY_TIME = 1.2f
private const val STAR_POP_SIZE_FRAC = 0.12f
private const val PARTY_POP_SIZE_FRAC = 0.2f

enum class Sfx { POP, DROP, CHIME, BOING, BIGWIN, MAGIC }

/** Things the game asks the UI layer to do. */
enum class GameEvent { CYCLE_PALETTE }

enum class ItemState { FREE, HELD, FLYING, SINKING }

class Item(val id: Int, val spec: ItemSpec, var x: Float, var y: Float, var radius: Float) {
    var vx = 0f
    var vy = 0f
    var rotation = 0f          // degrees
    var spin = 0f              // deg/s
    var scale = 1f
    var state = ItemState.FREE
    var heldBy: Long = NO_POINTER
    var onGround = false
    /** True once the child has let go of this item. Only dropped items feel the bin magnet and get captured. */
    var dropped = false

    // HELD: finger history for throw velocity, and tap detection.
    val history = ArrayDeque<FingerSample>()
    var grabTime = 0f
    var grabX = 0f
    var grabY = 0f
    var moved = false

    // FLYING (easy-mode tap) / SINKING.
    var flightT = 0f
    var flightFromX = 0f
    var flightFromY = 0f
    var targetBin: Bin? = null
    var sinkAge = 0f

    companion object {
        const val NO_POINTER = -1L
    }
}

class FingerSample(val x: Float, val y: Float, val time: Float)

class Bin(val index: Int) {
    var left = 0f
    var top = 0f
    var width = 0f
    var height = 0f
    val right: Float get() = left + width
    val bottom: Float get() = top + height
    val centerX: Float get() = left + width / 2
    val mouthY: Float get() = top

    /** Seconds since the last capture, drives squash/lid/happy face. Large when idle. */
    var sinceCapture = 100f
    var sinceWiggle = 100f
    var tapCount = 0
    var lastTapTime = -100f
    var blinkTimer = 0f
    var blinkPhase = 0f
    val happy: Boolean get() = sinceCapture < BIN_HAPPY_TIME
    var count = 0
}

/**
 * The whole game: layout, physics, input and effects, in canvas pixels. No Compose or Android types so it
 * can be unit-tested. The UI calls [resize], [step] every frame, the pointer methods, and drains [sounds].
 */
class GameWorld(private val random: Random = Random.Default) {
    var width = 0f
        private set
    var height = 0f
        private set
    var easyMode = false
        set(value) {
            field = value
            for (item in items) item.radius = itemRadius()
        }

    val bins: List<Bin> = List(3) { Bin(it) }
    val items = ArrayList<Item>()
    val particles = Particles(random)
    /** Sound events produced since the UI last drained them. */
    val sounds = ArrayDeque<Sfx>()
    /** Requests to the UI produced since it last drained them. */
    val events = ArrayDeque<GameEvent>()

    var time = 0f
        private set
    var captures = 0
        private set
    /** Index into [Scenes.all] of the background currently (or being) shown. */
    var sceneIndex = 0
        private set
    /** 0..1 progress of the circular wipe from the previous scene to [sceneIndex]; 1 = settled. */
    var sceneReveal = 1f
        private set
    /** Where the wipe grows from (the bin that triggered the change). */
    var sceneRevealX = 0f
        private set
    var sceneRevealY = 0f
        private set
    val groundY: Float get() = height * GROUND_Y_FRAC
    /** Items spawn and rest between the left-edge bin(s) and the right-edge bins. */
    val itemZoneLeft: Float get() = bins[LEFT_BINS - 1].right
    val itemZoneRight: Float get() = bins[LEFT_BINS].left

    private var spawnTimer = 0f
    private var nextId = 1
    private var lastInteraction = 0f
    private var idleHopTimer = 0f
    private var nextFlightBin = 0
    private var poofPending = false

    fun resize(w: Float, h: Float) {
        if (w == width && h == height) return
        val sx = if (width > 0f) w / width else 0f
        val sy = if (height > 0f) h / height else 0f
        width = w
        height = h
        layoutBins()
        val r = itemRadius()
        for (item in items) {
            item.radius = r
            if (sx > 0f && sy > 0f) {
                item.x *= sx
                item.y *= sy
            }
        }
        if (items.isEmpty()) repeat(TARGET_ITEMS) { spawn(initial = true) }
    }

    /** One bin hugs the left edge and the rest the right edge, so either hand has a bin close by. */
    private fun layoutBins() {
        val binW = height * BIN_WIDTH_FRAC
        val binH = height * BIN_HEIGHT_FRAC
        val gap = height * BIN_GAP_FRAC
        val rightBins = bins.size - LEFT_BINS
        for ((i, bin) in bins.withIndex()) {
            bin.width = binW
            bin.height = binH
            bin.top = groundY - binH
            bin.left = if (i < LEFT_BINS) {
                gap + i * (binW + gap)
            } else {
                width - (rightBins - (i - LEFT_BINS)) * (binW + gap)
            }
        }
    }

    private fun itemRadius(): Float = height * (if (easyMode) ITEM_RADIUS_FRAC_EASY else ITEM_RADIUS_FRAC)

    private fun spawn(initial: Boolean) {
        val r = itemRadius()
        val x = itemZoneLeft + r + random.nextFloat() * (itemZoneRight - itemZoneLeft - 2 * r)
        val y = if (initial) groundY - r - random.nextFloat() * height * 0.5f else -2 * r
        // Prefer kinds not already on screen so the set stays varied.
        val pool = ItemCatalog.forScene(Scenes.all[sceneIndex])
        val fresh = pool.filter { spec -> items.none { it.spec === spec } }
        val choices = if (fresh.isEmpty()) pool else fresh
        val spec = choices[random.nextInt(choices.size)]
        val item = Item(nextId++, spec, x, y, r)
        item.vx = (random.nextFloat() - 0.5f) * height * 0.2f
        item.spin = (random.nextFloat() - 0.5f) * 90f
        item.rotation = (random.nextFloat() - 0.5f) * 30f
        items.add(item)
    }

    // ---------------------------------------------------------------- input

    /** Last known position of each pressed pointer, so a move can test the whole swept segment. */
    private val pointerLast = HashMap<Long, FingerSample>()

    fun pointerDown(pointerId: Long, x: Float, y: Float) {
        lastInteraction = time
        pointerLast[pointerId] = FingerSample(x, y, time)
        val r = itemRadius()
        val item = nearestFreeItem(x, y, x, y)
        if (item != null) {
            grab(item, pointerId, x, y, dragging = false)
            return
        }
        // Tapping a bin makes it wiggle; three quick taps on the same bin swap the bin colours.
        for (bin in bins) {
            if (x in bin.left - r * 0.3f..bin.right + r * 0.3f && y in bin.top - r..bin.bottom) {
                bin.sinceWiggle = 0f
                sounds.add(Sfx.BOING)
                bin.tapCount = if (time - bin.lastTapTime <= BIN_TAP_GAP) bin.tapCount + 1 else 1
                bin.lastTapTime = time
                if (bin.tapCount >= BIN_TAPS_TO_CYCLE_PALETTE) {
                    bin.tapCount = 0
                    events.add(GameEvent.CYCLE_PALETTE)
                }
                return
            }
        }
    }

    /**
     * The grabbable item nearest the segment from (x0, y0) to (x1, y1), within the hit radius, or null.
     * The last-drawn (topmost) item breaks ties by iterating in reverse.
     */
    private fun nearestFreeItem(x0: Float, y0: Float, x1: Float, y1: Float): Item? {
        var best: Item? = null
        var bestDist = Float.MAX_VALUE
        for (i in items.indices.reversed()) {
            val item = items[i]
            if (item.state != ItemState.FREE) continue
            val d = distanceToSegment(item.x, item.y, x0, y0, x1, y1)
            if (d <= item.radius * HIT_RADIUS_FACTOR && d < bestDist) {
                best = item
                bestDist = d
            }
        }
        return best
    }

    /**
     * @param dragging true when the finger was already moving when it reached the item (a drag-through pickup),
     *   so the release can never count as a tap.
     */
    private fun grab(item: Item, pointerId: Long, x: Float, y: Float, dragging: Boolean) {
        item.state = ItemState.HELD
        item.heldBy = pointerId
        item.onGround = false
        item.vx = 0f
        item.vy = 0f
        item.spin = 0f
        item.grabTime = time
        item.grabX = x
        item.grabY = y
        item.moved = dragging
        item.history.clear()
        item.history.addLast(FingerSample(x, y, time))
        // Move the held item to the end so it draws on top.
        items.remove(item)
        items.add(item)
        sounds.add(Sfx.POP)
    }

    fun pointerMove(pointerId: Long, x: Float, y: Float) {
        val prev = pointerLast[pointerId]
        pointerLast[pointerId] = FingerSample(x, y, time)
        var item = heldBy(pointerId)
        if (item == null) {
            // An empty finger that sweeps across an item picks it up mid-drag. Only one item per finger: once
            // holding, the finger keeps that item until it lifts, however many others it crosses. A finger that
            // is not moving does not grab, so an item landing on a resting finger stays free.
            if (prev == null || (prev.x == x && prev.y == y)) return
            item = nearestFreeItem(prev.x, prev.y, x, y) ?: return
            grab(item, pointerId, x, y, dragging = true)
        }
        lastInteraction = time
        val r = item.radius
        item.x = x.coerceIn(r, width - r)
        item.y = (y - r * LIFT_FACTOR).coerceIn(r, height - r)
        if (!item.moved && hypot(x - item.grabX, y - item.grabY) > r * TAP_SLOP_FACTOR) item.moved = true
        item.history.addLast(FingerSample(x, y, time))
        while (item.history.size > 1 && time - item.history.first().time > VELOCITY_WINDOW) item.history.removeFirst()
    }

    fun pointerUp(pointerId: Long) {
        pointerLast.remove(pointerId)
        val item = heldBy(pointerId) ?: return
        release(item)
    }

    /** Releases everything held by a pointer that is no longer pressed (lost cancel events, etc). */
    fun releaseAllExcept(pressed: Set<Long>) {
        pointerLast.keys.retainAll(pressed)
        for (item in items) {
            if (item.state == ItemState.HELD && item.heldBy !in pressed) release(item)
        }
    }

    private fun heldBy(pointerId: Long): Item? =
        items.firstOrNull { it.state == ItemState.HELD && it.heldBy == pointerId }

    private fun release(item: Item) {
        val isTap = !item.moved && time - item.grabTime <= TAP_MAX_TIME
        item.heldBy = Item.NO_POINTER
        item.dropped = true
        if (isTap) {
            if (easyMode) {
                startFlight(item)
            } else {
                // Normal mode: a tap makes the item hop, so a tap still does something visible.
                item.state = ItemState.FREE
                item.vy = -height * HOP_SPEED_FRAC
                item.vx = (random.nextFloat() - 0.5f) * height * 0.3f
                item.spin = item.vx * SPIN_PER_VX
            }
            return
        }
        item.state = ItemState.FREE
        val first = item.history.firstOrNull()
        val last = item.history.lastOrNull()
        if (first != null && last != null && last.time > first.time) {
            val dt = last.time - first.time
            val maxV = height * MAX_THROW_FRAC
            item.vx = ((last.x - first.x) / dt * THROW_SCALE).coerceIn(-maxV, maxV)
            item.vy = ((last.y - first.y) / dt * THROW_SCALE).coerceIn(-maxV, maxV)
        }
        item.spin = item.vx * SPIN_PER_VX
        item.history.clear()
    }

    private fun startFlight(item: Item) {
        item.state = ItemState.FLYING
        item.flightT = 0f
        item.flightFromX = item.x
        item.flightFromY = item.y
        item.targetBin = bins[nextFlightBin]
        nextFlightBin = (nextFlightBin + 1) % bins.size
        item.spin = 360f
        sounds.add(Sfx.POP)
    }

    // ---------------------------------------------------------------- simulation

    fun step(dt: Float) {
        if (width <= 0f || height <= 0f) return
        time += dt
        val g = height * GRAVITY_FRAC
        val it = items.iterator()
        while (it.hasNext()) {
            val item = it.next()
            when (item.state) {
                ItemState.HELD -> stepHeld(item, dt)
                ItemState.FREE -> stepFree(item, dt, g)
                ItemState.FLYING -> stepFlying(item, dt)
                ItemState.SINKING -> {
                    item.sinkAge += dt
                    item.y += height * SINK_SPEED_FRAC * dt
                    if (item.sinkAge >= SINK_TIME) it.remove()
                }
            }
        }
        if (poofPending) poofRestingItems()
        separateRestingItems(dt)
        stepSpawn(dt)
        stepIdleHop(dt)
        for (bin in bins) {
            bin.sinceCapture += dt
            bin.sinceWiggle += dt
            bin.blinkTimer -= dt
            if (bin.blinkTimer <= 0f) {
                bin.blinkTimer = 2.5f + random.nextFloat() * 4f
                bin.blinkPhase = 0.18f
            }
            if (bin.blinkPhase > 0f) bin.blinkPhase -= dt
        }
        particles.step(dt)
        if (sceneReveal < 1f) sceneReveal = min(1f, sceneReveal + dt / SCENE_REVEAL_TIME)
    }

    private fun stepHeld(item: Item, dt: Float) {
        item.scale += (HELD_SCALE - item.scale) * min(1f, SCALE_RATE * dt)
        item.rotation += (0f - item.rotation) * min(1f, 6f * dt)
    }

    private fun stepFree(item: Item, dt: Float, g: Float) {
        item.scale += (1f - item.scale) * min(1f, SCALE_RATE * dt)
        val r = item.radius
        val prevY = item.y
        item.vy += g * dt
        applyMagnet(item, dt)
        item.x += item.vx * dt
        item.y += item.vy * dt
        item.rotation += item.spin * dt

        if (checkCapture(item, prevY)) return

        // Bin bodies are solid from the side: push items that dip below the mouth back out, toward the nearer
        // side unless that side has no room between the bin and the screen edge.
        for (bin in bins) {
            if (item.y > bin.mouthY && item.x + r * 0.5f > bin.left && item.x - r * 0.5f < bin.right) {
                val outLeft = bin.left - r * 0.5f
                val outRight = bin.right + r * 0.5f
                val leftFits = outLeft >= r
                val rightFits = outRight <= width - r
                val toLeft = item.x - bin.left
                val toRight = bin.right - item.x
                item.x = if ((toLeft < toRight && leftFits) || !rightFits) outLeft else outRight
                item.vx = -item.vx * WALL_BOUNCE
            }
        }

        // Ground.
        val floor = groundY - r
        if (item.y >= floor) {
            item.y = floor
            if (item.vy > height * REST_SPEED_FRAC) {
                item.vy = -item.vy * GROUND_BOUNCE
                item.onGround = false
            } else {
                item.vy = 0f
                item.onGround = true
            }
            val damp = max(0f, 1f - GROUND_FRICTION * dt)
            item.vx *= damp
            item.spin *= damp
            if (item.onGround && abs(item.vx) < height * 0.01f) item.vx = 0f
        } else {
            item.onGround = false
        }

        // Walls.
        if (item.x < r) {
            item.x = r
            item.vx = abs(item.vx) * WALL_BOUNCE
        } else if (item.x > width - r) {
            item.x = width - r
            item.vx = -abs(item.vx) * WALL_BOUNCE
        }
    }

    /** Pulls a falling item toward the nearest bin mouth when it is close enough horizontally and above it. */
    private fun applyMagnet(item: Item, dt: Float) {
        if (!item.dropped || item.vy <= 0f) return
        val bin = magnetBin(item) ?: return
        if (item.y > bin.mouthY) return
        val pull = if (easyMode) MAGNET_PULL_EASY else MAGNET_PULL
        item.vx += (bin.centerX - item.x) * pull * dt
        item.vx *= max(0f, 1f - 3f * dt)
    }

    private fun magnetBin(item: Item): Bin? {
        val margin = item.radius * (if (easyMode) MAGNET_MARGIN_FACTOR_EASY else MAGNET_MARGIN_FACTOR)
        var best: Bin? = null
        var bestDist = Float.MAX_VALUE
        for (bin in bins) {
            val d = abs(bin.centerX - item.x)
            if (item.x >= bin.left - margin && item.x <= bin.right + margin && d < bestDist) {
                best = bin
                bestDist = d
            }
        }
        return best
    }

    private fun checkCapture(item: Item, prevY: Float): Boolean {
        if (!item.dropped || item.vy <= 0f) return false
        val bin = magnetBin(item) ?: return false
        if (prevY < bin.mouthY && item.y >= bin.mouthY) {
            capture(item, bin)
            return true
        }
        return false
    }

    private fun stepFlying(item: Item, dt: Float) {
        val bin = item.targetBin ?: run { item.state = ItemState.FREE; return }
        item.flightT += dt / FLIGHT_TIME
        val t = item.flightT.coerceIn(0f, 1f)
        val targetY = bin.mouthY - item.radius * 0.2f
        item.x = item.flightFromX + (bin.centerX - item.flightFromX) * t
        item.y = item.flightFromY + (targetY - item.flightFromY) * t - 4f * t * (1f - t) * height * FLIGHT_ARC_FRAC
        item.rotation += item.spin * dt
        item.scale += (1f - item.scale) * min(1f, SCALE_RATE * dt)
        if (t >= 1f) {
            item.y = bin.mouthY + 1f
            item.vy = height * SINK_SPEED_FRAC
            capture(item, bin)
        }
    }

    private fun capture(item: Item, bin: Bin) {
        item.state = ItemState.SINKING
        item.sinkAge = 0f
        item.x = bin.centerX + (item.x - bin.centerX).coerceIn(-bin.width * 0.25f, bin.width * 0.25f)
        item.vx = 0f
        item.spin = 0f
        item.targetBin = bin
        bin.sinceCapture = 0f
        bin.count++
        captures++
        spawnTimer = SPAWN_DELAY
        lastInteraction = time

        val h = height
        particles.confettiBurst(bin.centerX, bin.mouthY, h, CONFETTI_PER_CAPTURE)
        particles.starRing(bin.centerX, bin.mouthY, h, Sprites.STAR)
        particles.ring(bin.centerX, bin.mouthY, h, 0xFFFFFFFF)
        particles.pop(bin.centerX, bin.mouthY - item.radius, h, Sprites.STAR, STAR_POP_SIZE_FRAC)
        sounds.add(Sfx.DROP)
        sounds.add(Sfx.CHIME)
        if (captures % BIG_WIN_EVERY == 0) {
            particles.rain(width, h, CONFETTI_RAIN)
            val sprite = if (captures % (BIG_WIN_EVERY * 2) == 0) Sprites.HEART else Sprites.PARTY
            particles.pop(width * 0.25f, height * 0.4f, h, sprite, PARTY_POP_SIZE_FRAC)
            particles.pop(width * 0.75f, height * 0.35f, h, Sprites.SPARKLES, PARTY_POP_SIZE_FRAC)
            sounds.add(Sfx.BIGWIN)
        }
        if (captures % SCENE_EVERY == 0) changeScene(bin)
    }

    private fun changeScene(from: Bin) {
        sceneIndex = (sceneIndex + 1) % Scenes.all.size
        sceneReveal = 0f
        sceneRevealX = from.centerX
        sceneRevealY = from.mouthY
        for (b in bins) b.sinceWiggle = 0f
        sounds.add(Sfx.MAGIC)
        poofPending = true
    }

    /** Resting items belong to the old scene: poof them so the new set rains in. Runs outside item iteration. */
    private fun poofRestingItems() {
        poofPending = false
        val leaving = items.filter { it.state == ItemState.FREE }
        for (item in leaving) {
            particles.pop(item.x, item.y, height, Sprites.SPARKLES, POOF_SIZE_FRAC)
            particles.ring(item.x, item.y, height, 0xFFFFFFFF)
        }
        items.removeAll(leaving)
        spawnTimer = SCENE_RESPAWN_DELAY
    }

    /** Keeps resting items from piling into one spot so each stays grabbable. */
    private fun separateRestingItems(dt: Float) {
        for (i in items.indices) {
            val a = items[i]
            if (a.state != ItemState.FREE || !a.onGround) continue
            for (j in i + 1 until items.size) {
                val b = items[j]
                if (b.state != ItemState.FREE || !b.onGround) continue
                val minDist = (a.radius + b.radius) * SEPARATION_FACTOR
                val dx = b.x - a.x
                if (abs(dx) >= minDist) continue
                val push = (minDist - abs(dx)) * min(1f, SEPARATION_RATE * dt) / 2
                val dir = if (dx >= 0f) 1f else -1f
                a.x -= push * dir
                b.x += push * dir
            }
        }
        for (item in items) {
            if (item.state == ItemState.FREE) item.x = item.x.coerceIn(item.radius, width - item.radius)
        }
    }

    private fun stepSpawn(dt: Float) {
        val live = items.count { it.state != ItemState.SINKING }
        if (live >= TARGET_ITEMS) return
        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            spawn(initial = false)
            spawnTimer = SPAWN_DELAY
        }
    }

    /** After a quiet spell, a resting item hops now and then to invite a touch. */
    private fun stepIdleHop(dt: Float) {
        if (time - lastInteraction < IDLE_HOP_MIN_IDLE) {
            idleHopTimer = IDLE_HOP_INTERVAL
            return
        }
        idleHopTimer -= dt
        if (idleHopTimer > 0f) return
        idleHopTimer = IDLE_HOP_INTERVAL
        val resting = items.filter { it.state == ItemState.FREE && it.onGround }
        if (resting.isEmpty()) return
        val item = resting[random.nextInt(resting.size)]
        item.vy = -height * HOP_SPEED_FRAC * 0.6f
        item.onGround = false
    }

    /** Position the bins' eyes look at: the held item, else the nearest airborne item, else null. */
    fun gazeTarget(): Item? {
        items.lastOrNull { it.state == ItemState.HELD }?.let { return it }
        return items.filter { it.state == ItemState.FREE && !it.onGround }.minByOrNull { it.y }
    }
}

/** Distance from point (px, py) to the segment (x0, y0)-(x1, y1); a zero-length segment degrades to a point. */
internal fun distanceToSegment(px: Float, py: Float, x0: Float, y0: Float, x1: Float, y1: Float): Float {
    val dx = x1 - x0
    val dy = y1 - y0
    val lengthSq = dx * dx + dy * dy
    val t = if (lengthSq == 0f) 0f else (((px - x0) * dx + (py - y0) * dy) / lengthSq).coerceIn(0f, 1f)
    return hypot(px - (x0 + t * dx), py - (y0 + t * dy))
}
