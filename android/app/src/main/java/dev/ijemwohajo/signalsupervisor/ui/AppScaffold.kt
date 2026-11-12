package dev.ijemwohajo.signalsupervisor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ijemwohajo.signalsupervisor.R
import dev.ijemwohajo.signalsupervisor.app.AppGraph
import dev.ijemwohajo.signalsupervisor.core.ScenarioFile
import dev.ijemwohajo.signalsupervisor.ui.theme.SignalTheme
import dev.ijemwohajo.signalsupervisor.ui.theme.Corner
import dev.ijemwohajo.signalsupervisor.ui.theme.corner
import dev.ijemwohajo.signalsupervisor.ui.theme.cq
import dev.ijemwohajo.signalsupervisor.ui.theme.pal

private val TABS = listOf("drive" to "주행", "report" to "리포트", "log" to "기록", "week" to "7일", "settings" to "설정", "sim" to "시뮬레이터")
    .filter { !dev.ijemwohajo.signalsupervisor.BuildConfig.CAR || it.first != "sim" } // 실차용은 시뮬레이터를 숨긴다
private val LOCKABLE = setOf("report", "log", "week", "settings")

@Composable
fun AppScaffold(graph: AppGraph, scenarios: ScenarioFile) {
    val settings by graph.settings.collectAsState()
    val dark = settings.dark ?: isSystemInDarkTheme()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 목업의 cqw: 목업 앱 영역이 화면 너비의 66.65% 이므로 창 너비를 그만큼으로 본다
        val u = (maxWidth.value / 66.65f).coerceIn(9f, 20f)
        val cardWidth = minOf(560.dp, maxWidth * 0.6f)
        SignalTheme(dark, u) { Body(graph, scenarios, cardWidth) }
    }
}

@Composable
private fun Body(graph: AppGraph, scenarios: ScenarioFile, cardWidth: androidx.compose.ui.unit.Dp) {
    val p = pal
    val live by graph.live.collectAsState()
    val reports by graph.reports.collectAsState()
    val clips by graph.clips.collectAsState()
    val saveFailed by graph.saveFailed.collectAsState()
    val settings by graph.settings.collectAsState()
    val sample by graph.sampleTrips.collectAsState()
    val card by graph.cards.collectAsState()

    var view by rememberSaveable { mutableStateOf("drive") }
    var reportId by rememberSaveable { mutableStateOf<String?>(null) }
    var verdictId by rememberSaveable { mutableStateOf<String?>(null) }
    val moving = live.state.speed > 0

    // 정산이 끝나면(앱이 화면에 있을 때) 리포트 탭을 연다
    val latest = reports.firstOrNull()?.id
    var seen by rememberSaveable { mutableStateOf(latest) }
    LaunchedEffect(latest) {
        if (latest == seen) return@LaunchedEffect
        seen = latest
        if (latest != null && graph.appInForeground.value) { reportId = latest; verdictId = null; view = "report" }
    }

    Box(Modifier.fillMaxSize().background(p.appBg)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.padding(start = cq(1.8f), end = cq(1.8f), top = cq(1.1f), bottom = cq(0.9f)),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(cq(1f)),
            ) {
                AppMark()
                Txt("시그널 감독관", 1.5f, weight = FontWeight.ExtraBold)
                Txt(if (dev.ijemwohajo.signalsupervisor.BuildConfig.CAR) "실차 신호 · 일부 모의" else "모의 신호", 0.95f, p.muted, modifier = Modifier.clip(CircleShape).background(p.surface).padding(horizontal = cq(0.8f), vertical = cq(0.25f)))
                if (saveFailed) Txt("저장 실패 · 다시 시도 중", 0.95f, p.danger, modifier = Modifier.clip(CircleShape).background(p.surface).padding(horizontal = cq(0.8f), vertical = cq(0.25f)))
                Box(Modifier.weight(1f))
                Row(Modifier.clip(corner(Corner.M)).background(p.surface).padding(cq(0.3f)), horizontalArrangement = Arrangement.spacedBy(cq(0.2f))) {
                    TABS.forEach { (id, label) ->
                        val on = id == view
                        Box(
                            Modifier.clip(corner(Corner.S)).background(if (on) p.btnBg else androidx.compose.ui.graphics.Color.Transparent)
                                .selectable(on, role = Role.Tab) { view = id }.padding(horizontal = cq(0.95f), vertical = cq(0.5f)),
                        ) {
                            val c = if (on) p.btnInk else p.muted
                            Txt(label, 1.05f, c, if (on) FontWeight.Bold else FontWeight.Normal)
                            // 잠금 표시는 글자 오른쪽 위에 겹쳐 그려 탭 폭이 변하지 않게 한다(좁은 창에서도 한 줄 유지)
                            if (moving && id in LOCKABLE) PIcon(R.drawable.pleos_lock, 0.75f, c, Modifier.align(Alignment.TopEnd).offset(x = cq(0.75f), y = -cq(0.45f)), "잠김")
                        }
                    }
                }
                val state = if (moving) "주행 중" else if (live.tripActive) "정차 중" else "주차"
                Box(Modifier.width(cq(5f)), contentAlignment = Alignment.Center) {
                    Txt(
                        state, 0.9f, if (moving) p.btnInk else p.ink, if (moving) FontWeight.Bold else FontWeight.Normal,
                        Modifier.clip(CircleShape).background(if (moving) p.btnBg else p.surface).padding(horizontal = cq(0.8f), vertical = cq(0.25f)),
                    )
                }
                Txt("%.1fs".format(live.simTime), 0.95f, p.muted, modifier = Modifier.width(cq(4.4f)), align = androidx.compose.ui.text.style.TextAlign.End)
            }
            Box(Modifier.weight(1f).fillMaxSize().padding(start = cq(1.8f), end = cq(1.8f), top = cq(0.6f), bottom = cq(1.6f))) {
                if (moving && view in LOCKABLE) LockScreen()
                else when (view) {
                    "drive" -> DriveScreen(live)
                    "report" -> ReportScreen(reports, reportId) { r, v -> reportId = r; verdictId = v; view = "log" }
                    "log" -> LogScreen(reports, clips, reportId, verdictId, { r, v -> reportId = r; verdictId = v }, graph::setPinned)
                    "week" -> WeekScreen(reports, sample)
                    "settings" -> SettingsScreen(graph, settings, clips)
                    else -> SimulatorScreen(graph, scenarios.scenarios, live) { view = it }
                }
                // 알림 카드: 헤더 바로 아래, 내용 영역의 왼쪽 위
                card?.let { c -> NotificationCardView(c, cardWidth) }
            }
        }
    }
}

@Composable
private fun AppMark() {
    val blink = rememberBlink()
    val p = pal
    Row(horizontalArrangement = Arrangement.spacedBy(cq(0.35f))) {
        Box(Modifier.size(cq(0.9f)).clip(CircleShape).background(p.signal.copy(alpha = if (blink) 1f else 0.35f)))
        Box(Modifier.size(cq(0.9f)).clip(CircleShape).background(p.signal.copy(alpha = if (blink) 0.35f else 1f)))
    }
}
