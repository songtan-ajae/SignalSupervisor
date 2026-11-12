package dev.ijemwohajo.signalsupervisor.app

import dev.ijemwohajo.signalsupervisor.core.*
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRepositoryTest {
    @Test fun roundTrip() {
        val file = ScenarioJson.parse(File("../../shared/scenarios.json").readText())
        val out = runScenario(file.scenarios.first { it.id == "S3" })
        assertEquals(1, out.reports.size)
        assertEquals(2, out.clips.size)
        val state = SavedState(
            reports = out.reports,
            clips = listOf(out.clips[0].copy(pinned = true), out.clips[1]),
            settings = Settings(clipLimit = 30, sound = true, dark = false),
            sampleTrips = listOf(WeekTrip("2026-11-10", 12.5, 97, mapOf(Grade.OK to 3))),
        )
        val f = File.createTempFile("state", ".json")
        f.deleteOnExit()
        TripRepository(f).save(state)
        assertEquals(state, TripRepository(f).load())
    }

    @Test fun corruptFileIsBackedUp() {
        val dir = java.nio.file.Files.createTempDirectory("repo").toFile()
        val f = File(dir, "state.json")
        f.writeText("{garbage")
        assertEquals(SavedState(), TripRepository(f).load())
        val bak = dir.listFiles()!!.filter { it.name.startsWith("state.json.corrupt-") }
        assertEquals(1, bak.size)
        assertEquals("{garbage", bak[0].readText())
    }

    @Test fun pinnedClipsBeyondLimitSurviveReload() {
        val file = ScenarioJson.parse(File("../../shared/scenarios.json").readText())
        val c = runScenario(file.scenarios.first { it.id == "S3" }).clips[0]
        val clips = (1..3).map { c.copy(id = "c$it", pinned = true) }
        val f = File.createTempFile("state", ".json").also { it.deleteOnExit() }
        TripRepository(f).save(SavedState(clips = clips, settings = Settings(clipLimit = 2)))
        val g = AppGraph(file, TripRepository(f), {}, { 0L })
        assertEquals(3, g.clips.value.size)
    }

    @Test fun missingFileGivesEmptyState() {
        val s = TripRepository(File("no-such-dir/none.json")).load()
        assertTrue(s.reports.isEmpty() && s.clips.isEmpty())
        assertEquals(Settings(), s.settings)
    }
}
