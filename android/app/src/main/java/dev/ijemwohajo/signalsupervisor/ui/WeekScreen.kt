package dev.ijemwohajo.signalsupervisor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.ijemwohajo.signalsupervisor.R
import dev.ijemwohajo.signalsupervisor.core.JudgeConfig
import dev.ijemwohajo.signalsupervisor.core.Scoring
import dev.ijemwohajo.signalsupervisor.core.TripReport
import dev.ijemwohajo.signalsupervisor.core.WeekTrip
import dev.ijemwohajo.signalsupervisor.ui.theme.Corner
import dev.ijemwohajo.signalsupervisor.ui.theme.corner
import dev.ijemwohajo.signalsupervisor.ui.theme.cq
import dev.ijemwohajo.signalsupervisor.ui.theme.pal
import java.time.LocalDate

@Composable
fun WeekScreen(reports: List<TripReport>, sample: List<WeekTrip>) {
    val p = pal
    val keys = remember { (0..6).map { LocalDate.now().minusDays(6L - it).toString() } }
    val trips = reports.filter { it.date in keys }.map { WeekTrip(it.date, it.distanceKm, it.score, it.grades) } + sample.filter { it.date in keys }
    if (trips.isEmpty()) {
        Empty(R.drawable.pleos_calendar_line, if (dev.ijemwohajo.signalsupervisor.BuildConfig.CAR) "최근 7일 기록이 없어요." else "최근 7일 기록이 없어요.\n시뮬레이터의 \"최근 7일 샘플 기록 채우기\"를 눌러 보세요.")
        return
    }
    val sum = Scoring.weekSummary(trips)
    Row(Modifier.fillMaxSize(), Arrangement.spacedBy(cq(1.2f))) {
        ScoreCard("최근 7일 깜빡이 점수", { ScoreRing(sum.weekScore, sum.weekScore?.f() ?: "–", "거리 가중 평균") }) {
            GradeCounts(sum.distribution, 0)
            Txt("운행 ${trips.size}회 · 판정 없음 운행은 평균 제외", 1f, p.muted)
        }
        Column(Modifier.weight(1f).fillMaxHeight(), Arrangement.spacedBy(cq(1.2f))) {
            Column(Modifier.weight(1f).fillMaxWidth().card()) {
                Txt("일별 점수", color = p.muted)
                Row(Modifier.weight(1f).fillMaxWidth().padding(top = cq(1f)), Arrangement.spacedBy(cq(1f)), Alignment.Bottom) {
                    keys.forEach { k ->
                        val day = trips.filter { it.date == k && it.score != null }
                        val dist = day.sumOf { it.distanceKm }
                        val sc = if (dist > 0) Scoring.round1(day.sumOf { it.score!! * it.distanceKm } / dist) else null
                        val frac = if (sc == null) 0.04f else ((sc - 60) * 2.5).coerceAtLeast(6.0).coerceAtMost(100.0).toFloat() / 100f
                        Column(Modifier.weight(1f).fillMaxHeight(), Arrangement.spacedBy(cq(0.4f)), Alignment.CenterHorizontally) {
                            Txt(sc?.f() ?: "–", 1.1f, weight = FontWeight.Bold)
                            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.BottomCenter) {
                                val shape = RoundedCornerShape(topStart = cq(Corner.S.u), topEnd = cq(Corner.S.u), bottomStart = cq(Corner.XS.u), bottomEnd = cq(Corner.XS.u))
                                Box(Modifier.fillMaxWidth(0.62f).fillMaxHeight(frac).background(if (sc == null) p.surface2 else p.ink.copy(alpha = 0.85f), shape))
                            }
                            Txt(dayLabel(k), 0.9f, p.muted)
                        }
                    }
                }
            }
            val c = sum.coaching
            Row(Modifier.fillMaxWidth().card(), Arrangement.spacedBy(cq(1.2f)), Alignment.CenterVertically) {
                if (c != null) {
                    val t = c.name.lowercase()
                    PIcon(gradeIcon(t), 3.4f, p.grade(t))
                    Column {
                        Txt("이번 주 코칭 포인트", color = p.muted)
                        Txt("${c.label} · 감점 ${(sum.distribution[c] ?: 0) * (JudgeConfig().penalty[c] ?: 0)}점", weight = FontWeight.Bold)
                        Txt(COACH.getValue(t))
                    }
                } else {
                    PIcon(R.drawable.pleos_star_line, 3.4f, p.ink)
                    Txt("감점 없이 완벽한 한 주예요!", weight = FontWeight.Bold)
                }
            }
        }
    }
}
