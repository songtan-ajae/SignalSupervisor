package dev.ijemwohajo.signalsupervisor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRecorderTest {
    private val base: Map<String, Any> = mapOf(
        "speed" to 36, "steer" to 0, "signal" to "off", "road" to "city",
        "bsmL" to false, "bsmR" to false, "naviTurnNear" to false,
    )

    private fun set(t: Double, vararg p: Pair<String, Any>) = Keyframe(t, mapOf(*p), null)
    private fun cross(t: Double, d: Dir) = Keyframe(t, null, d)
    private fun init(vararg extra: Pair<String, Any>) = Keyframe(0.0, base + mapOf(*extra), null)

    private fun run(kf: List<Keyframe>, endSec: Double = 340.0) =
        runScenario(Scenario("T", "", "", endSec, kf, Expected(null, emptyMap(), 0, null)))

    private fun laneChangeRight(signalAt: Double, crossAt: Double, offAt: Double, vararg extra: Pair<String, Any>) = mutableListOf(
        init(*extra),
        set(signalAt, "signal" to "right"),
        set(crossAt - 0.5, "steer" to 10),
        cross(crossAt, Dir.RIGHT),
        set(crossAt + 0.5, "steer" to -8),
        set(crossAt + 1.5, "steer" to 0),
        set(offAt, "signal" to "off"),
        set(crossAt + 5, "speed" to 0),
    )

    @Test fun boundary30m() {
        val ok = run(laneChangeRight(5.0, 8.0, 10.0)).verdicts[0]
        assertEquals(30.0, ok.onDistanceM, 0.0)
        assertEquals(Grade.OK, ok.grade)
        val late = run(laneChangeRight(5.1, 8.0, 10.0)).verdicts[0]
        assertEquals(29.0, late.onDistanceM, 0.0)
        assertEquals(Grade.LATE, late.grade)
    }

    @Test fun highway100m() {
        val v = run(laneChangeRight(5.0, 8.0, 10.0, "road" to "highway")).verdicts[0]
        assertEquals(100.0, v.requiredM, 0.0)
        assertEquals(Grade.LATE, v.grade)
    }

    @Test fun offEarlyIsShort() {
        fun kf(offAt: Double) = listOf(
            init("speed" to 120), set(5.0, "signal" to "right"), set(5.5, "steer" to 10), cross(6.0, Dir.RIGHT),
            set(6.5, "steer" to -8), set(7.0, "steer" to 0), set(offAt, "signal" to "off"), set(12.0, "speed" to 0),
        )
        assertEquals(Grade.OK, run(kf(7.5)).verdicts[0].grade)
        val short = run(kf(6.5)).verdicts[0]
        assertEquals(Grade.SHORT, short.grade)
        assertTrue(short.offEarly)
    }

    @Test fun duration19vs20() {
        fun kf(completeAt: Double) = listOf(
            init("speed" to 120), set(5.0, "signal" to "right"), set(5.5, "steer" to 10), cross(6.0, Dir.RIGHT),
            set(6.2, "steer" to -8), set(completeAt, "steer" to 0), set(9.0, "signal" to "off"), set(12.0, "speed" to 0),
        )
        assertEquals(Grade.SHORT, run(kf(6.9)).verdicts[0].grade)
        assertEquals(Grade.OK, run(kf(7.0)).verdicts[0].grade)
    }

    @Test fun oppositeSignalIsMissed() {
        val kf = laneChangeRight(5.0, 8.0, 10.0)
        kf[1] = set(5.0, "signal" to "left")
        assertEquals(Grade.MISSED, run(kf).verdicts[0].grade)
    }

    @Test fun bsmUnsupported() {
        val kf = listOf(
            init("bsmSupported" to false, "bsmR" to true), set(7.5, "steer" to 10), cross(8.0, Dir.RIGHT),
            set(8.5, "steer" to -8), set(9.5, "steer" to 0), set(13.0, "speed" to 0),
        )
        val v = run(kf).verdicts[0]
        assertEquals(Grade.MISSED, v.grade)
        assertEquals(BsmStatus.UNSUPPORTED, v.bsm)
    }

    @Test fun steeringOnlyEstimated() {
        val kf = listOf(
            init(), set(7.0, "steer" to -12), set(8.0, "steer" to 9), set(9.0, "steer" to 0), set(13.0, "speed" to 0),
        )
        val v = run(kf).verdicts[0]
        assertEquals(ManeuverType.LANE, v.maneuverType)
        assertEquals(Dir.LEFT, v.dir)
        assertTrue(v.estimated)
        assertEquals(listOf("핸들"), v.sources)
    }

    @Test fun naviTurn() {
        val kf = listOf(
            init("naviTurnNear" to true), set(3.0, "signal" to "left"), set(7.0, "steer" to -100), set(10.0, "steer" to 0),
            set(10.5, "signal" to "off"), set(13.0, "speed" to 0),
        )
        val v = run(kf).verdicts[0]
        assertEquals(ManeuverType.TURN, v.maneuverType)
        assertEquals(listOf("내비", "핸들"), v.sources)
        assertEquals(Grade.OK, v.grade)
    }

    @Test fun settle299vs300() {
        val kf = listOf(init(), set(5.0, "speed" to 0), set(304.0, "speed" to 36), set(310.0, "speed" to 0))
        val out = run(kf, 620.0)
        assertEquals(1, out.reports.size)
        assertNull("조작 없는 운행은 판정 없음", out.reports[0].score)
        assertEquals(610.0, out.reports[0].endT, 0.0)
    }

    @Test fun lingerIgnoresStops() {
        fun withStop(offAt: Double): MutableList<Keyframe> {
            val kf = laneChangeRight(5.0, 8.0, offAt)
            kf.add(6, set(12.0, "speed" to 0))
            kf.add(7, set(30.0, "speed" to 36))
            kf[kf.size - 1] = set(45.0, "speed" to 0)
            return kf
        }
        // 완료 9.5 → 12까지 2.5초 + 30~40 10초 = 12.5초 > 10
        val a = run(withStop(40.0), 400.0).reports[0].verdicts.groupingBy { it.grade }.eachCount()
        assertEquals(mapOf(Grade.OK to 1, Grade.LINGER to 1), a)
        // 2.5 + 5 = 7.5초
        val b = run(withStop(35.0), 400.0).reports[0].verdicts.groupingBy { it.grade }.eachCount()
        assertEquals(mapOf(Grade.OK to 1), b)
    }

    @Test fun idsUniqueAcrossRecorders() {
        val file = ScenarioJson.parse(java.io.File("../../shared/scenarios.json").readText())
        val sc = file.scenarios.first { it.id == "S2" }
        fun go(prefix: String): Pair<TripReport, List<Clip>> {
            lateinit var rep: TripReport
            val clips = mutableListOf<Clip>()
            val rec = TripRecorder(JudgeConfig(), ClipStore(50), prefix, object : RecorderListener {
                override fun onSettle(r: TripReport) { rep = r }
                override fun onClip(c: Clip, saved: Boolean, evicted: Clip?) { clips.add(c) }
            })
            ScenarioPlayer(sc, rec).finish()
            return rep to clips
        }
        val (ra, ca) = go("a-")
        val (rb, cb) = go("b-")
        assertNotEquals(ra.id, rb.id)
        assertTrue(ra.id.startsWith("a-"))
        assertTrue(ca.isNotEmpty() && cb.isNotEmpty())
        assertTrue((ca + cb).map { it.id }.toSet().size == ca.size + cb.size)
        assertTrue(ca.all { it.id.startsWith("a-") })
    }
}
