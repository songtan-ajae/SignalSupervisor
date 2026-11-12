package dev.ijemwohajo.signalsupervisor.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class ScenarioTest {
    private val file = ScenarioJson.parse(File("../../shared/scenarios.json").readText())

    private fun check(id: String) {
        val sc = file.scenarios.first { it.id == id }
        val out = runScenario(sc)
        assertEquals("정산 1회", 1, out.reports.size)
        val r = out.reports[0]
        assertEquals(sc.expected.score, r.score)
        assertEquals(sc.expected.grades, out.verdicts.groupingBy { it.grade }.eachCount())
        assertEquals(sc.expected.grades, r.verdicts.groupingBy { it.grade }.eachCount())
        assertEquals("클립 수", sc.expected.clips, out.clips.size)
        sc.expected.cautions?.let { assertEquals(it, r.cautions) }
        // 리포트의 판정에 클립 연결이 반영돼야 한다
        assertEquals(sc.expected.clips, r.verdicts.count { it.clipStatus == ClipStatus.SAVED && it.clipId != null })
    }

    @Test fun s1() = check("S1")
    @Test fun s2() = check("S2")
    @Test fun s3() = check("S3")
    @Test fun s4() = check("S4")

    @Test fun parsesAllScenariosAndWeekSample() {
        assertEquals(listOf("S1", "S2", "S3", "S4"), file.scenarios.map { it.id })
        assertEquals(7, file.weekSample.size)
    }
}
