package dev.ijemwohajo.signalsupervisor.core

data class WeekTrip(val date: String, val distanceKm: Double, val score: Int?, val grades: Map<Grade, Int>)

data class WeekSummary(val weekScore: Double?, val distribution: Map<Grade, Int>, val coaching: Grade?)

object Scoring {
    private val MANEUVER = listOf(Grade.OK, Grade.SHORT, Grade.LATE, Grade.MISSED, Grade.DANGER)

    fun round1(x: Double): Double = Math.round(x * 10 + 1e-9) / 10.0

    /** 판정 대상 기동이 하나도 없으면 null. 0점 미만은 0점. */
    fun scoreFromGrades(grades: Map<Grade, Int>, cfg: JudgeConfig = JudgeConfig()): Int? {
        if (MANEUVER.sumOf { grades[it] ?: 0 } == 0) return null
        val sum = grades.entries.sumOf { (cfg.penalty[it.key] ?: 0) * it.value }
        return maxOf(0, 100 - sum)
    }

    fun weekSummary(trips: List<WeekTrip>, cfg: JudgeConfig = JudgeConfig()): WeekSummary {
        var wSum = 0.0
        var dSum = 0.0
        val dist = Grade.entries.associateWith { 0 }.toMutableMap()
        for (t in trips) {
            for ((g, n) in t.grades) dist[g] = (dist[g] ?: 0) + n
            val s = t.score ?: continue
            wSum += s * t.distanceKm
            dSum += t.distanceKm
        }
        val weekScore = if (dSum > 0) round1(wSum / dSum) else null
        var coaching: Grade? = null
        var best = 0
        for (g in SEVERITY) {
            if (g == Grade.OK) continue
            val pts = (dist[g] ?: 0) * (cfg.penalty[g] ?: 0)
            if (pts > best) { best = pts; coaching = g }
        }
        return WeekSummary(weekScore, dist, coaching)
    }
}
