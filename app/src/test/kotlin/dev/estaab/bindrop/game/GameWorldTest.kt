package dev.estaab.bindrop.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

private const val W = 1920f
private const val H = 1080f
private const val DT = 1f / 60f
private const val POINTER = 7L

class GameWorldTest {
    private fun world(easy: Boolean = false): GameWorld = GameWorld(Random(1)).apply {
        easyMode = easy
        resize(W, H)
    }

    private fun GameWorld.run(seconds: Float) {
        var t = 0f
        while (t < seconds) {
            step(DT)
            t += DT
        }
    }

    private fun GameWorld.settle() = run(3f)

    @Test
    fun `starts with five items resting between the left bin and the right bins`() {
        val w = world()
        w.settle()
        assertEquals(5, w.items.size)
        for (item in w.items) {
            assertTrue("item ${item.spec.name} at ${item.x} should be inside ${w.itemZoneLeft}..${w.itemZoneRight}",
                item.x > w.itemZoneLeft && item.x < w.itemZoneRight)
            assertTrue(item.onGround)
        }
    }

    @Test
    fun `items raining in beside a bin are not captured until the child drops them`() {
        val w = world(easy = true)
        w.settle()
        assertEquals(0, w.captures)
        // Items that spawn next to the left bin fall past it untouched by the magnet.
        repeat(6) {
            val item = w.items.first { it.state == ItemState.FREE }
            w.pointerDown(POINTER, item.x, item.y)
            w.step(DT)
            w.pointerMove(POINTER, w.bins[1].centerX, H * 0.3f)
            w.step(DT)
            w.pointerUp(POINTER)
            w.run(2.5f)
        }
        assertEquals("only the six deliberate drops count", 6, w.captures)
    }

    @Test
    fun `one bin sits at the left edge and the others at the right edge`() {
        val w = world()
        val left = w.bins.filter { it.centerX < W / 2 }
        val right = w.bins.filter { it.centerX > W / 2 }
        assertEquals(1, left.size)
        assertEquals(2, right.size)
        assertTrue(left.single().left < W * 0.1f)
        assertTrue(right.maxOf { it.right } > W * 0.9f)
        assertTrue("room for items", w.itemZoneRight - w.itemZoneLeft > W * 0.4f)
    }

    @Test
    fun `an item dropped between the left bin and the screen edge is pushed back into play`() {
        val w = world()
        w.settle()
        val bin = w.bins.minBy { it.left }
        val item = w.items.minBy { it.x }
        w.pointerDown(POINTER, item.x, item.y)
        w.step(DT)
        w.pointerMove(POINTER, bin.left + item.radius * 0.2f, bin.top + bin.height * 0.5f)
        w.step(DT)
        w.pointerUp(POINTER)
        w.settle()
        assertEquals(ItemState.FREE, item.state)
        assertTrue("item at ${item.x} should rest right of the left bin", item.x >= bin.right)
    }

    @Test
    fun `three quick taps on a bin ask for the next palette`() {
        val w = world()
        w.settle()
        val bin = w.bins[1]
        val x = bin.centerX
        val y = bin.top + bin.height * 0.5f
        repeat(2) {
            w.pointerDown(POINTER, x, y)
            w.pointerUp(POINTER)
            w.run(0.2f)
        }
        assertEquals("the first tap speaks, the second is inside the voice cooldown",
            listOf(GameEvent.Speak(1)), w.events.toList())
        w.events.clear()
        w.pointerDown(POINTER, x, y)
        w.pointerUp(POINTER)
        assertEquals("palette changes first so the bin announces its new colour",
            listOf(GameEvent.CyclePalette, GameEvent.Speak(1)), w.events.toList())
    }

    @Test
    fun `a bin says its colour when it catches an item`() {
        val w = world(easy = true)
        w.settle()
        val item = w.items.first()
        w.pointerDown(POINTER, item.x, item.y)
        w.step(DT)
        w.pointerUp(POINTER)
        w.run(2f)
        assertEquals(1, w.captures)
        val bin = w.bins.single { it.count == 1 }
        assertEquals(listOf(GameEvent.Speak(bin.index)), w.events.toList())
    }

    @Test
    fun `every spoken colour has a voice clip for every bin`() {
        // Sounds.kt is Android-only; this mirrors its startup check so a new palette word fails here first.
        val words = Palettes.all.flatMap { it.bins }.map { it.spoken }.toSet()
        for (word in words) assertTrue(word in ColorWord.values())
        assertEquals(3, Palettes.all.first().bins.size)
    }

    @Test
    fun `slow taps on a bin do not change the palette`() {
        val w = world()
        w.settle()
        val bin = w.bins[2]
        val x = bin.centerX
        val y = bin.top + bin.height * 0.5f
        repeat(4) {
            w.pointerDown(POINTER, x, y)
            w.pointerUp(POINTER)
            w.run(1f)
        }
        assertEquals("each slow tap just speaks", List(4) { GameEvent.Speak(2) }, w.events.toList())
    }

    @Test
    fun `dragging an item above a bin and releasing captures it and spawns a replacement`() {
        val w = world()
        w.settle()
        val item = w.items.first()
        val bin = w.bins[1]
        w.pointerDown(POINTER, item.x, item.y)
        assertEquals(ItemState.HELD, item.state)
        // Slow drag straight over the bin mouth, then let go.
        repeat(30) { i ->
            val t = (i + 1) / 30f
            w.pointerMove(POINTER, item.x + (bin.centerX - item.x) * t, item.y + (bin.top - H * 0.2f - item.y) * t)
            w.step(DT)
        }
        w.pointerUp(POINTER)
        w.run(1.5f)
        assertEquals(1, w.captures)
        assertEquals(1, bin.count)
        assertFalse("captured item should be gone", w.items.contains(item))
        assertTrue(w.sounds.contains(Sfx.CHIME))
        assertTrue("effects should be playing", w.particles.list.isNotEmpty())
        w.run(2f)
        assertEquals("a replacement spawns", 5, w.items.size)
    }

    @Test
    fun `releasing an item away from the bins drops it on the ground`() {
        val w = world()
        w.settle()
        val item = w.items.first()
        w.pointerDown(POINTER, item.x, item.y)
        w.pointerMove(POINTER, W * 0.2f, H * 0.3f)
        w.step(DT)
        w.pointerUp(POINTER)
        w.run(3f)
        assertEquals(0, w.captures)
        assertTrue(item.onGround)
        assertEquals(ItemState.FREE, item.state)
    }

    @Test
    fun `easy mode tap flies the item into a bin`() {
        val w = world(easy = true)
        w.settle()
        val item = w.items.first()
        w.pointerDown(POINTER, item.x, item.y)
        w.step(DT)
        w.pointerUp(POINTER)
        assertEquals(ItemState.FLYING, item.state)
        w.run(2f)
        assertEquals(1, w.captures)
    }

    @Test
    fun `normal mode tap only hops the item`() {
        val w = world()
        w.settle()
        val item = w.items.first()
        w.pointerDown(POINTER, item.x, item.y)
        w.step(DT)
        w.pointerUp(POINTER)
        assertEquals(ItemState.FREE, item.state)
        assertTrue("hop goes up", item.vy < 0f)
        w.run(3f)
        assertEquals(0, w.captures)
    }

    @Test
    fun `a drag that starts on empty ground picks up the first item it crosses`() {
        val w = world()
        w.settle()
        val item = w.items.minBy { it.x }
        // Start well above the ground, clear of every item, then sweep down through the item's centre.
        w.pointerDown(POINTER, item.x, H * 0.1f)
        assertTrue(w.items.none { it.state == ItemState.HELD })
        w.step(DT)
        w.pointerMove(POINTER, item.x, item.y)
        assertEquals(ItemState.HELD, item.state)
        assertEquals(POINTER, item.heldBy)
    }

    @Test
    fun `a fast sweep between two samples still picks up the item it jumped over`() {
        val w = world()
        w.settle()
        val item = w.items.minBy { it.x }
        w.pointerDown(POINTER, item.x - item.radius * 3, item.y)
        w.step(DT)
        w.pointerMove(POINTER, item.x + item.radius * 3, item.y)
        assertEquals(ItemState.HELD, item.state)
    }

    @Test
    fun `a finger already holding an item does not pick up a second one`() {
        val w = world()
        w.settle()
        val first = w.items.minBy { it.x }
        w.pointerDown(POINTER, first.x, first.y)
        w.step(DT)
        for (other in w.items.filter { it !== first }) {
            w.pointerMove(POINTER, other.x, other.y)
            w.step(DT)
        }
        assertEquals(1, w.items.count { it.state == ItemState.HELD })
        assertEquals(ItemState.HELD, first.state)
    }

    @Test
    fun `a resting finger does not grab an item that lands on it`() {
        val w = world()
        w.settle()
        val item = w.items.minBy { it.x }
        // Finger goes down beside the item, then stays put while the item hops and comes back down onto it.
        w.pointerDown(POINTER, item.x + item.radius * 2, item.y)
        item.x = item.x + item.radius * 2
        for (i in 0 until 10) {
            w.pointerMove(POINTER, item.x, item.y)
            w.step(DT)
        }
        assertEquals(ItemState.FREE, item.state)
    }

    @Test
    fun `a drag-through pickup released at once is a drag, not an easy-mode tap`() {
        val w = world(easy = true)
        w.settle()
        val item = w.items.minBy { it.x }
        w.pointerDown(POINTER, item.x, H * 0.1f)
        w.step(DT)
        w.pointerMove(POINTER, item.x, item.y)
        w.pointerUp(POINTER)
        w.run(0.3f)
        assertTrue(item.state == ItemState.FREE || item.state == ItemState.SINKING)
        assertFalse(item.state == ItemState.FLYING)
    }

    @Test
    fun `lost pointers release their items`() {
        val w = world()
        w.settle()
        val item = w.items.first()
        w.pointerDown(POINTER, item.x, item.y)
        w.releaseAllExcept(emptySet())
        assertEquals(ItemState.FREE, item.state)
    }

    @Test
    fun `every third capture changes the scene with a wipe from that bin`() {
        val w = world(easy = true)
        w.settle()
        assertEquals(0, w.sceneIndex)
        assertEquals(1f, w.sceneReveal)
        repeat(3) {
            w.sounds.clear()
            val item = w.items.first { it.state == ItemState.FREE }
            w.pointerDown(POINTER, item.x, item.y)
            w.step(DT)
            w.pointerUp(POINTER)
            w.run(1.2f)
        }
        assertEquals(3, w.captures)
        assertEquals(1, w.sceneIndex)
        assertTrue("wipe in progress", w.sceneReveal in 0f..0.99f)
        assertTrue(w.sounds.contains(Sfx.MAGIC))
        val bin = w.bins.first { it.count > 0 && it.centerX == w.sceneRevealX }
        assertEquals(bin.mouthY, w.sceneRevealY)
        w.run(2f)
        assertEquals(1f, w.sceneReveal)
    }

    @Test
    fun `a scene change swaps the resting items for the new scene's set`() {
        val w = world(easy = true)
        w.settle()
        val dayNames = ItemCatalog.forScene(Scenes.all[0]).map { it.name }.toSet()
        assertTrue(w.items.all { it.spec.name in dayNames })
        repeat(3) {
            val item = w.items.first { it.state == ItemState.FREE }
            w.pointerDown(POINTER, item.x, item.y)
            w.step(DT)
            w.pointerUp(POINTER)
            w.run(1.2f)
        }
        assertEquals(1, w.sceneIndex)
        w.run(6f)
        val sunsetNames = ItemCatalog.forScene(Scenes.all[1]).map { it.name }.toSet()
        assertEquals(5, w.items.size)
        for (item in w.items) {
            assertTrue("${item.spec.name} is not a Sunset item", item.spec.name in sunsetNames)
            assertTrue(item.onGround)
        }
    }

    @Test
    fun `spawned items avoid duplicates while the pool allows`() {
        val w = world()
        w.settle()
        assertEquals(5, w.items.map { it.spec.name }.toSet().size)
    }

    @Test
    fun `every scene has an item set`() {
        for (scene in Scenes.all) assertTrue(scene.name, ItemCatalog.forScene(scene).size >= 5)
    }

    @Test
    fun `every fifth capture plays the big win`() {
        val w = world(easy = true)
        w.settle()
        repeat(5) {
            w.sounds.clear()
            val item = w.items.first { it.state == ItemState.FREE }
            w.pointerDown(POINTER, item.x, item.y)
            w.step(DT)
            w.pointerUp(POINTER)
            w.run(2f)
        }
        assertEquals(5, w.captures)
        assertTrue(w.sounds.contains(Sfx.BIGWIN))
    }
}
