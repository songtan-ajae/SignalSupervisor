package dev.ijemwohajo.signalsupervisor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ClipStoreTest {
    private fun clip(id: String) = Clip(id, "v-$id", 0.0, Dir.LEFT, Grade.MISSED, emptyList(), false, emptyList())

    @Test
    fun fifoSkipsPinned() {
        val s = ClipStore(2)
        val a = clip("a"); val b = clip("b"); val c = clip("c")
        s.add(a); s.add(b)
        s.setPinned("a", true)
        val r = s.add(c)
        assertEquals(b, r.evicted)
        assertEquals(listOf("a", "c"), s.clips.map { it.id })
    }

    @Test
    fun fullOfPinsRejects() {
        val s = ClipStore(2)
        s.add(clip("a")); s.add(clip("b"))
        s.setPinned("a", true)
        s.add(clip("c"))
        s.setPinned("c", true)
        val r = s.add(clip("d"))
        assertFalse(r.saved)
        assertEquals(listOf("a", "c"), s.clips.map { it.id })
    }
}
