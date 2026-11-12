package dev.ijemwohajo.signalsupervisor

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import dev.ijemwohajo.signalsupervisor.app.AppGraph
import dev.ijemwohajo.signalsupervisor.app.SettlementNotifier
import dev.ijemwohajo.signalsupervisor.app.TripRepository
import dev.ijemwohajo.signalsupervisor.core.ScenarioFile
import dev.ijemwohajo.signalsupervisor.core.ScenarioJson
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** AppGraph 1개를 만들어 틱 루프를 돌린다. 화면은 graph 의 StateFlow만 읽는다. */
class SignalApp : Application() {
    lateinit var graph: AppGraph
        private set
    lateinit var scenarios: ScenarioFile
        private set

    override fun onCreate() {
        super.onCreate()
        scenarios = ScenarioJson.parse(assets.open("scenarios.json").bufferedReader().use { it.readText() })
        val notifier = SettlementNotifier(this)
        graph = AppGraph(scenarios, TripRepository(File(filesDir, "state.json")), notifier::notify, System::currentTimeMillis) { Log.w("SignalSupervisor", "상태 저장 실패", it) }
        // 오디오가 없는 기기에서 생성이 실패해도 앱이 죽으면 안 된다. 첫 위험 알림음 때 만든다.
        var tone: ToneGenerator? = null
        var toneFailed = false
        graph.onDangerSound = {
            if (tone == null && !toneFailed) {
                tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }
                    .onFailure { toneFailed = true; Log.w("SignalSupervisor", "알림음을 만들 수 없어요", it) }.getOrNull()
            }
            runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 150) }
        }
        graph.start(CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
}
