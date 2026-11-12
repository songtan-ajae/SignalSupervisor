package dev.ijemwohajo.signalsupervisor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.ijemwohajo.signalsupervisor.R
import dev.ijemwohajo.signalsupervisor.core.SyncState
import dev.ijemwohajo.signalsupervisor.core.TripReport
import dev.ijemwohajo.signalsupervisor.ui.theme.cq
import dev.ijemwohajo.signalsupervisor.ui.theme.pal

@Composable
fun ScoreCard(title: String, ring: @Composable () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.width(cq(19f)).fillMaxHeight().card(), Arrangement.spacedBy(cq(0.8f), Alignment.CenterVertically), Alignment.CenterHorizontally) {
        Txt(title, color = pal.muted)
        ring()
        content()
    }
}

@Composable
fun ReportScreen(reports: List<TripReport>, selectedId: String?, onOpen: (reportId: String, verdictId: String) -> Unit) {
    val r = reports.find { it.id == selectedId } ?: reports.firstOrNull()
    if (r == null) { Empty(R.drawable.pleos_playerlist_line, if (dev.ijemwohajo.signalsupervisor.BuildConfig.CAR) "아직 정산된 운행이 없어요.\n운행을 마치고 5분 이상 정차하면 정산돼요." else "아직 정산된 운행이 없어요.\n시뮬레이터에서 시나리오를 실행해 보세요."); return }
    val p = pal
    Row(Modifier.fillMaxSize(), Arrangement.spacedBy(cq(1.4f))) {
        ScoreCard("이번 운행 깜빡이 점수", { ScoreRing(r.score?.toDouble(), r.score?.toString() ?: "–", "/ 100") }) {
            GradeCounts(r.grades, r.cautions)
            Txt("${r.distanceKm.f()} km · ${dayLabel(r.date)}${r.scenarioId?.let { " · $it" } ?: ""}", 1f, p.muted)
            val sent = r.sync == SyncState.SENT
            Txt(if (sent) "✓ 점수 연동 전송 완료 (모의)" else "점수 연동 전송 중… (모의)", 0.95f, if (sent) p.ok else p.muted)
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), Arrangement.spacedBy(cq(0.8f))) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(cq(0.6f))) {
                Txt("정산 리포트", 1.6f, weight = FontWeight.ExtraBold)
                Txt("이벤트를 누르면 영상과 상세를 볼 수 있어요", 1f, p.muted)
            }
            if (r.verdicts.isEmpty()) Txt("회전·차선 변경이 없었어요.", color = p.muted)
            r.verdicts.forEach { v -> VerdictRow(v) { onOpen(r.id, v.id) } }
        }
    }
}
