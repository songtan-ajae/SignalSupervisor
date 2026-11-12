package dev.ijemwohajo.signalsupervisor.app

import dev.ijemwohajo.signalsupervisor.core.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class AppGraphTest {
    private val scenarios = ScenarioJson.parse(File("../../shared/scenarios.json").readText())
    private var now = 1_800_000_000_000L
    private val notified = mutableListOf<TripReport>()
    private val repoFile = File.createTempFile("graph", ".json").also { it.delete(); it.deleteOnExit() }

    private fun graph() = AppGraph(scenarios, TripRepository(repoFile), { notified.add(it) }, { now })

    @Test fun settlesWhileBackground() {
        val g = graph()
        g.appInForeground.value = false
        g.startScenario("S2")
        g.fastForward()
        assertEquals(92, g.reports.value.first().score)
        assertEquals(1, notified.size)
        assertNull(g.cards.value)
        g.tick()
        assertNull(g.cards.value)
    }

    @Test fun saveFailedFlagFollowsRepo() {
        var fail = true
        val repo = object : TripRepository(repoFile) {
            override fun save(s: SavedState) { if (fail) throw java.io.IOException("disk") else super.save(s) }
        }
        val g = AppGraph(scenarios, repo, { notified.add(it) }, { now })
        assertFalse(g.saveFailed.value)
        g.setSound(false)
        assertTrue(g.saveFailed.value)
        fail = false
        g.setSound(true)
        assertFalse(g.saveFailed.value)
    }

    @Test fun engineErrorStopsRunInsteadOfCrashing() {
        val errors = mutableListOf<Throwable>()
        val g = AppGraph(scenarios, TripRepository(repoFile), { throw IllegalStateException("boom") }, { now }, onError = { errors.add(it) })
        g.startScenario("S2")
        repeat(3000) { g.tick() }
        assertEquals(1, errors.size)
        assertEquals(0, g.live.value.state.speed)
    }

    @Test fun scenarioStartSettlesActiveManualTrip() {
        val g = graph()
        g.startManual()
        g.setManual { it.copy(speed = 50) }
        repeat(20) { g.tick() }
        assertTrue(g.live.value.tripActive)
        g.startScenario("S2")
        assertEquals(1, g.reports.value.size)
        assertEquals("수동", g.reports.value.first().scenarioId)
        assertFalse(g.live.value.tripActive)
    }

    @Test fun foregroundCardsQueued() {
        val g = graph()
        g.startScenario("S2")
        g.fastForward()
        val texts = mutableListOf<String>()
        repeat(6) {
            g.cards.value?.let { texts.add(it.text) }
            now += 2500
            g.tick()
        }
        assertEquals(listOf("깜빡 깜빡! 중요해요", "조금만 더 일찍 깜빡!", "운행 정산 완료 · 이번 운행 92점"), texts)
        assertNull(g.cards.value)
    }

    @Test fun weekSample() {
        val g = graph()
        g.fillWeekSample()
        g.fillWeekSample()
        assertEquals(7, g.sampleTrips.value.size)
        assertEquals(94.1, Scoring.weekSummary(g.sampleTrips.value).weekScore!!, 1e-9)
    }

    @Test fun settleFillsMetaAndPersists() {
        val g = graph()
        g.startScenario("S3")
        g.fastForward()
        val r = g.reports.value.first()
        assertEquals("S3", r.scenarioId)
        assertTrue(Regex("\\d{4}-\\d{2}-\\d{2}").matches(r.date))
        assertEquals(SyncState.SENT, r.sync)
        // 위험 판정에 클립이 연결돼 저장된다
        val danger = r.verdicts.first { it.grade == Grade.DANGER }
        assertEquals(ClipStatus.SAVED, danger.clipStatus)
        assertNotNull(danger.clipId)
        val loaded = TripRepository(repoFile).load()
        assertEquals(g.reports.value, loaded.reports)
        assertEquals(2, loaded.clips.size)
    }

    @Test fun lateClipPatchesStoredReport() {
        val g = graph()
        g.startScenario("S3")
        g.fastForward()
        val v = g.reports.value.first().verdicts.first { it.grade == Grade.DANGER }
        val late = g.clips.value.first().copy(id = "late-clip", verdictId = v.id)
        g.recorderListener.onClip(late, true, null)
        val patched = g.reports.value.first().verdicts.first { it.id == v.id }
        assertEquals("late-clip", patched.clipId)
        assertEquals(ClipStatus.SAVED, patched.clipStatus)
        assertEquals("late-clip", TripRepository(repoFile).load().reports.first().verdicts.first { it.id == v.id }.clipId)
        g.recorderListener.onClip(late, false, null)
        assertEquals(ClipStatus.FULL, g.reports.value.first().verdicts.first { it.id == v.id }.clipStatus)
    }

    @Test fun autoFastForwardAfterLastKeyframe() {
        val g = graph()
        g.startScenario("S1")
        var n = 0
        while (g.reports.value.isEmpty() && n++ < 100_000) g.tick()
        assertEquals(1, g.reports.value.size)
        assertTrue("자동 빨리 감기로 5분 정차를 기다리지 않는다: $n", n < 3000)
    }

    @Test fun noAutoFastForwardWhenDisabled() {
        val g = graph()
        g.autoFastForward.value = false
        g.startScenario("S1")
        repeat(3000) { g.tick() }
        assertTrue(g.reports.value.isEmpty())
        g.fastForward()
        assertEquals(1, g.reports.value.size)
    }

    @Test fun dangerSoundOnlyWhenEnabled() {
        val g = graph()
        var beeps = 0
        g.onDangerSound = { beeps++ }
        g.startScenario("S3"); g.fastForward()
        assertEquals(0, beeps)
        g.setSound(true)
        g.startScenario("S3"); g.fastForward()
        assertEquals(1, beeps)
    }

    @Test fun saveFailureDoesNotBreakSettling() {
        val failing = object : TripRepository(repoFile) { override fun save(s: SavedState) = throw java.io.IOException("disk") }
        var errors = 0
        val g = AppGraph(scenarios, failing, { notified.add(it) }, { now }, onError = { errors++ })
        g.startScenario("S2"); g.fastForward()
        assertEquals(92, g.reports.value.first().score)
        assertTrue(errors > 0)
    }

    @Test fun stopInManualModeStopsTicking() {
        val g = graph()
        g.startManual()
        g.setManual { it.copy(speed = 50) }
        g.tick()
        g.stop()
        val t = g.live.value.simTime
        g.tick()
        assertEquals(t, g.live.value.simTime, 0.0)
    }

    @Test fun resetClearsRecords() {
        val g = graph()
        g.startScenario("S2"); g.fastForward(); g.fillWeekSample()
        g.reset()
        assertTrue(g.reports.value.isEmpty() && g.clips.value.isEmpty() && g.sampleTrips.value.isEmpty())
        assertTrue(TripRepository(repoFile).load().reports.isEmpty())
    }
}
