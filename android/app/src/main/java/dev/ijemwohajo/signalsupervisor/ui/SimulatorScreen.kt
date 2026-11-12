package dev.ijemwohajo.signalsupervisor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import dev.ijemwohajo.signalsupervisor.app.AppGraph
import dev.ijemwohajo.signalsupervisor.app.LiveState
import dev.ijemwohajo.signalsupervisor.core.Dir
import dev.ijemwohajo.signalsupervisor.core.Road
import dev.ijemwohajo.signalsupervisor.core.Scenario
import dev.ijemwohajo.signalsupervisor.core.Signal
import dev.ijemwohajo.signalsupervisor.ui.theme.cq
import dev.ijemwohajo.signalsupervisor.ui.theme.cs
import dev.ijemwohajo.signalsupervisor.ui.theme.pal
import kotlin.math.roundToInt

/** 개발용 시뮬레이터. 주행 중 잠금에서 제외된다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimulatorScreen(graph: AppGraph, scenarios: List<Scenario>, live: LiveState, onGo: (String) -> Unit) {
    val p = pal
    val mul by graph.speedMul.collectAsState()
    val auto by graph.autoFastForward.collectAsState()
    val st = live.state
    var confirmReset by remember { mutableStateOf(false) }
    val muls = listOf(1, 2, 4, 10)
    Row(Modifier.verticalScroll(rememberScrollState()), Arrangement.spacedBy(cq(1.4f))) {
        Column(Modifier.weight(1f), Arrangement.spacedBy(cq(1f))) {
            Txt("시뮬레이터 개발용", 1.6f, weight = FontWeight.ExtraBold)
            Section("시나리오") {
                scenarios.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(cq(0.7f))) {
                        row.forEach { sc ->
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(cq(1f))).background(p.surface2)
                                    .clickable { graph.startScenario(sc.id); onGo("drive") }.padding(cq(0.9f)),
                            ) {
                                Txt("${sc.id} ${sc.title}", 1.1f, weight = FontWeight.Bold)
                                Txt(sc.subtitle, 0.95f, p.muted)
                                Txt(sc.expected.score?.let { "기대 ${it}점" } ?: "기대 판정 없음", 0.95f, weight = FontWeight.Bold)
                            }
                        }
                        if (row.size == 1) Column(Modifier.weight(1f)) {}
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(cq(0.8f))) {
                    Txt("재생 속도", 1f, p.muted)
                    Segmented(muls.map { "${it}×" }, muls.indexOf(mul).coerceAtLeast(0), { graph.speedMul.value = muls[it] })
                }
                Check("정차 시 5분 자동 빨리 감기", auto) { graph.autoFastForward.value = it }
                Row(horizontalArrangement = Arrangement.spacedBy(cq(0.8f))) {
                    AppButton("정지", { graph.stop() })
                    AppButton("5분 빨리 감기", { graph.fastForward() }, primary = true)
                }
            }
            Section("기록") {
                Row(horizontalArrangement = Arrangement.spacedBy(cq(0.8f))) {
                    AppButton("최근 7일 샘플 기록 채우기", { graph.fillWeekSample(); onGo("week") }, primary = true)
                    AppButton("기록 초기화", { confirmReset = true })
                }
            }
        }
        Column(Modifier.weight(1f), Arrangement.spacedBy(cq(1f))) {
            Section("수동 조작") {
                AppButton("수동 주행 시작", { graph.startManual(); onGo("drive") }, primary = true)
                Txt("속도 ${st.speed} km/h", 1.05f)
                Slider(
                    st.speed.toFloat(), { v -> graph.setManual { it.copy(speed = v.roundToInt()) } }, valueRange = 0f..130f, steps = 12,
                    colors = sliderColors(),
                )
                Txt("핸들 ${st.steer.roundToInt()}°", 1.05f)
                Slider(
                    st.steer.toFloat(), { v -> graph.setManual { it.copy(steer = v.roundToInt().toDouble()) } }, valueRange = -150f..150f,
                    colors = sliderColors(),
                )
                val sigs = listOf(Signal.LEFT, Signal.OFF, Signal.RIGHT, Signal.HAZARD)
                Segmented(listOf("좌", "끔", "우", "비상"), sigs.indexOf(st.signal), { graph.setManual { s -> s.copy(signal = sigs[it]) } }, Modifier.fillMaxWidth(), equal = true, activeBg = p.signal, activeInk = p.appBg)
                Row(horizontalArrangement = Arrangement.spacedBy(cq(0.8f))) {
                    AppButton("좌 차선 넘김", { graph.crossLane(Dir.LEFT) })
                    AppButton("우 차선 넘김", { graph.crossLane(Dir.RIGHT) })
                }
                FlowRow(Modifier.fillMaxWidth(), Arrangement.spacedBy(cq(1f))) {
                    Check("BSM 좌", st.bsmL) { b -> graph.setManual { it.copy(bsmL = b) } }
                    Check("BSM 우", st.bsmR) { b -> graph.setManual { it.copy(bsmR = b) } }
                    Check("내비 회전 지점", st.naviTurnNear) { b -> graph.setManual { it.copy(naviTurnNear = b) } }
                    Check("고속도로", st.road == Road.HIGHWAY) { b -> graph.setManual { it.copy(road = if (b) Road.HIGHWAY else Road.CITY) } }
                    Check("BSM 지원 차량", st.bsmSupported) { b -> graph.setManual { it.copy(bsmSupported = b) } }
                }
            }
        }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        containerColor = p.surface, titleContentColor = p.ink, textContentColor = p.ink,
        title = { Text("기록 초기화", fontSize = cs(1.4f)) },
        text = { Text("운행 기록, 클립, 7일 샘플을 모두 지울까요?", fontSize = cs(1.15f)) },
        confirmButton = { TextButton({ confirmReset = false; graph.reset() }) { Text("지우기", fontSize = cs(1.1f)) } },
        dismissButton = { TextButton({ confirmReset = false }) { Text("취소", fontSize = cs(1.1f)) } },
    )
}

@Composable
private fun sliderColors() = SliderDefaults.colors(thumbColor = pal.ink, activeTrackColor = pal.ink, inactiveTrackColor = pal.surface2)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().card(), Arrangement.spacedBy(cq(0.7f))) {
        Txt(title, 1.2f, weight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun Check(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = pal
    Row(Modifier.toggleable(checked, role = Role.Checkbox, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, null, colors = CheckboxDefaults.colors(checkedColor = p.btnBg, checkmarkColor = p.btnInk, uncheckedColor = p.outline))
        Txt(label, 1.05f)
    }
}
