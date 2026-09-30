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
    fun `starts with five items resting left of the bins`() {
        val w = world()
        w.settle()
        assertEquals(5, w.items.size)
        val binsLeft = w.bins.first().left
        for (item in w.items) {
            assertTrue("item ${item.spec.name} at ${item.x} should be left of ${binsLeft}", item.x < binsLeft)
            assertTrue(item.onGround)
        }
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
    fun `lost pointers release their items`() {
        val w = world()
        w.settle()
        val item = w.items.first()
        w.pointerDown(POINTER, item.x, item.y)
        w.releaseAllExcept(emptySet())
        assertEquals(ItemState.FREE, item.state)
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
