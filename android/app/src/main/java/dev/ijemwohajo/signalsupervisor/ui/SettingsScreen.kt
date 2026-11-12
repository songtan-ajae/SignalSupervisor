package dev.ijemwohajo.signalsupervisor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.ijemwohajo.signalsupervisor.app.AppGraph
import dev.ijemwohajo.signalsupervisor.app.Settings
import dev.ijemwohajo.signalsupervisor.core.Clip
import dev.ijemwohajo.signalsupervisor.ui.theme.cq
import dev.ijemwohajo.signalsupervisor.ui.theme.pal

@Composable
fun SettingsScreen(graph: AppGraph, settings: Settings, clips: List<Clip>) {
    val p = pal
    Column(Modifier.widthIn(max = cq(52f)).verticalScroll(rememberScrollState()), Arrangement.spacedBy(cq(0.9f))) {
        Txt("설정", 1.6f, weight = FontWeight.ExtraBold)
        SettingRow("클립 보관 개수", "넘으면 가장 오래된 클립부터 삭제(핀 제외) · 현재 ${clips.size}개, 핀 ${clips.count { it.pinned }}개") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(cq(0.7f))) {
                AppButton("−", { graph.setClipLimit((settings.clipLimit - 5).coerceIn(5, 200)) }, label = "줄이기")
                Txt(settings.clipLimit.toString(), 1.7f, weight = FontWeight.Bold)
                AppButton("+", { graph.setClipLimit((settings.clipLimit + 5).coerceIn(5, 200)) }, label = "늘리기")
            }
        }
        SettingRow("위험 알림음", "위험 등급에서만 짧게 울려요 · 차량 자체 차선 변경 경고음과 겹칠 수 있어 기본은 꺼짐", Modifier.toggleable(settings.sound, role = Role.Switch) { graph.setSound(it) }) {
            Switch(
                settings.sound, null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = p.btnInk, checkedTrackColor = p.btnBg, checkedBorderColor = p.btnBg,
                    uncheckedThumbColor = p.muted, uncheckedTrackColor = p.surface2, uncheckedBorderColor = p.surface2,
                ),
            )
        }
        SettingRow("화면 테마", "기본은 차량 시스템 설정을 따라요") {
            Segmented(listOf("시스템", "다크", "라이트"), when (settings.dark) { null -> 0; true -> 1; false -> 2 }, {
                graph.setDark(when (it) { 1 -> true; 2 -> false; else -> null })
            })
        }
        SettingRow("판정 기준", "일반도로 30m · 고속도로 100m 전 · 2초 이상 · 조작 완료까지 유지 · 완료 후 10초 넘게 켜두면 켜둠 · 5분 정차 시 정산") {}
    }
}

@Composable
private fun SettingRow(title: String, desc: String, modifier: Modifier = Modifier, control: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth().card(), Arrangement.spacedBy(cq(1.6f)), Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(title, weight = FontWeight.Bold)
            Txt(desc, 1f, pal.muted)
        }
        control()
    }
}
