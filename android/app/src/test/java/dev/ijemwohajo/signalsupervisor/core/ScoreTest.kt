package dev.ijemwohajo.signalsupervisor.core

import dev.ijemwohajo.signalsupervisor.core.Grade.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoreTest {
    private val cfg = JudgeConfig()

    @Test
    fun floorZero() = assertEquals(0, Scoring.scoreFromGrades(mapOf(DANGER to 11), cfg))

    @Test
    fun noManeuverIsNull() = assertNull(Scoring.scoreFromGrades(mapOf(LINGER to 0), cfg))

    @Test
    fun weekSample() {
        fun t(km: Double, g: Map<Grade, Int>) = WeekTrip("", km, Scoring.scoreFromGrades(g, cfg), g)
        val trips = listOf(
            t(12.0, mapOf(OK to 3, LATE to 1)),
            t(8.0, mapOf(OK to 2, MISSED to 1)),
            t(20.0, mapOf(OK to 4, DANGER to 1)),
            t(5.0, emptyMap()),
            t(15.0, mapOf(OK to 3, SHORT to 1, LINGER to 1)),
            t(10.0, mapOf(OK to 4)),
            t(30.0, mapOf(OK to 5, LATE to 1, MISSED to 1)),
        )
        assertEquals(listOf(97, 95, 90, null, 97, 100, 92), trips.map { it.score })
        val w = Scoring.weekSummary(trips, cfg)
        assertEquals(94.1, w.weekScore!!, 1e-9)
        assertEquals(mapOf(OK to 21, SHORT to 1, LATE to 2, MISSED to 2, DANGER to 1, LINGER to 1), w.distribution)
        assertEquals(DANGER, w.coaching)
    }
}
