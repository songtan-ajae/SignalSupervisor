package dev.ijemwohajo.signalsupervisor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ijemwohajo.signalsupervisor.R
import dev.ijemwohajo.signalsupervisor.core.Clip
import dev.ijemwohajo.signalsupervisor.core.TripReport
import dev.ijemwohajo.signalsupervisor.ui.theme.Corner
import dev.ijemwohajo.signalsupervisor.ui.theme.corner
import dev.ijemwohajo.signalsupervisor.ui.theme.cq
import dev.ijemwohajo.signalsupervisor.ui.theme.pal

@Composable
fun LogScreen(
    reports: List<TripReport>, clips: List<Clip>, selectedReport: String?, selectedVerdict: String?,
    onSelect: (reportId: String, verdictId: String?) -> Unit, onPin: (clipId: String, pinned: Boolean) -> Unit,
) {
    if (reports.isEmpty()) { Empty(R.drawable.pleos_video, "운행 기록이 없어요."); return }
    val p = pal
    val r = reports.find { it.id == selectedReport } ?: reports.first()
    val v = r.verdicts.find { it.id == selectedVerdict }
    Row(Modifier.fillMaxSize(), Arrangement.spacedBy(cq(1.2f))) {
        Column(Modifier.width(cq(19f)).fillMaxHeight().verticalScroll(rememberScrollState()), Arrangement.spacedBy(cq(0.7f))) {
            reports.forEach { t ->
                val shape = corner(Corner.L)
                Row(
                    Modifier.fillMaxWidth().clip(shape).background(p.surface).border(1.5.dp, if (t.id == r.id) p.ink else Color.Transparent, shape)
                        .clickable { onSelect(t.id, null) }.padding(horizontal = cq(1.1f), vertical = cq(0.9f)),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(cq(1f)),
                ) {
                    Column(Modifier.weight(1f)) {
                        Txt("${dayLabel(t.date)} 운행${t.scenarioId?.let { " · $it" } ?: ""}", weight = FontWeight.Bold)
                        Txt("${t.distanceKm.f()} km · 판정 ${t.verdicts.size}개 · 영상 ${t.verdicts.count { v -> clips.any { it.id == v.clipId } }}개", 0.95f, p.muted)
                    }
                    Txt(t.score?.toString() ?: "–", 1.9f, weight = FontWeight.Bold)
                }
            }
        }
        if (v != null) {
            ClipDetail(v, clips.find { it.id == v.clipId }, Modifier.weight(1f).fillMaxHeight(), { onSelect(r.id, null) }, onPin)
        } else {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), Arrangement.spacedBy(cq(0.8f))) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(cq(0.6f))) {
                    Txt("${dayLabel(r.date)} 운행 이벤트", 1.6f, weight = FontWeight.ExtraBold)
                    Txt(r.score?.let { "${it}점" } ?: "판정 없음", 1f, p.muted)
                }
                if (r.verdicts.isEmpty()) Txt("판정할 조작이 없었어요.", color = p.muted, modifier = Modifier.padding(top = cq(0.5f)))
                r.verdicts.forEach { x -> VerdictRow(x) { onSelect(r.id, x.id) } }
            }
        }
    }
}
