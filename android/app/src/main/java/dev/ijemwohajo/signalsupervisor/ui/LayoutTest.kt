package dev.ijemwohajo.signalsupervisor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import dev.ijemwohajo.signalsupervisor.app.AppGraph
import dev.ijemwohajo.signalsupervisor.app.TripRepository
import dev.ijemwohajo.signalsupervisor.core.ScenarioFile
import java.io.File

// Task 5 에서 눈으로 확인하는 레이아웃 미리보기 (계측 테스트 없음). 저장 파일이 없으면 빈 상태로 시작한다.
@Composable
private fun PreviewApp() {
    val empty = ScenarioFile(emptyList(), emptyList())
    val graph = remember { AppGraph(empty, TripRepository(File("preview-state.json")), {}, System::currentTimeMillis) }
    AppScaffold(graph, empty)
}

@Preview(widthDp = 840, heightDp = 1000)
@Composable
private fun Layout840() = PreviewApp()

@Preview(widthDp = 1682, heightDp = 1100)
@Composable
private fun Layout1682() = PreviewApp()
