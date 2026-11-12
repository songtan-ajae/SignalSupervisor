package dev.ijemwohajo.signalsupervisor.app

import dev.ijemwohajo.signalsupervisor.core.*
import dev.ijemwohajo.signalsupervisor.source.MockScoreSync
import dev.ijemwohajo.signalsupervisor.source.MockSignalHub
import dev.ijemwohajo.signalsupervisor.source.ScoreSyncAdapter
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LiveState(
    val state: VehicleState,
    val tripEvents: Int,
    val simTime: Double,
    val tripActive: Boolean,
    val stopRemainingSec: Double,
)

private val cardSeq = java.util.concurrent.atomic.AtomicLong()

/**
 * tone: 등급 이름 소문자(ok·short·late·missed·danger·linger) 또는 caution. 정산 카드는 ok·short·missed.
 * id 는 같은 내용의 카드가 연달아 와도 슬라이드 인을 다시 재생하게 하는 고유 번호
 */
data class NotificationCard(val tone: String, val text: String, val id: Long = cardSeq.incrementAndGet())

private enum class Mode { IDLE, SCENARIO, MANUAL }

private const val CARD_MS = 2500L

/** 앱 상태 전체. Android 타입에 의존하지 않아 JVM에서 테스트한다. 모든 변경은 lock 안에서 한다. */
class AppGraph(
    private val scenarios: ScenarioFile,
    private val repo: TripRepository,
    private val notifier: (TripReport) -> Unit,
    private val clock: () -> Long,
    private val sync: ScoreSyncAdapter = MockScoreSync(),
    private val onError: (Throwable) -> Unit = {},
) {
    private val cfg = JudgeConfig()
    private val lock = Any()
    val hub = MockSignalHub()

    private val saved = repo.load()
    private var clipStore = ClipStore(saved.settings.clipLimit).also { it.restore(saved.clips) }

    private val _live = MutableStateFlow(LiveState(hub.state, 0, 0.0, false, 0.0))
    val live: StateFlow<LiveState> = _live
    private val _reports = MutableStateFlow(saved.reports)
    val reports: StateFlow<List<TripReport>> = _reports
    private val _clips = MutableStateFlow(clipStore.clips.toList())
    val clips: StateFlow<List<Clip>> = _clips
    private val _cards = MutableStateFlow<NotificationCard?>(null)
    val cards: StateFlow<NotificationCard?> = _cards
    private val _saveFailed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = _saveFailed
    private val _settings = MutableStateFlow(saved.settings)
    val settings: StateFlow<Settings> = _settings
    private val _sample = MutableStateFlow(saved.sampleTrips)
    val sampleTrips: StateFlow<List<WeekTrip>> = _sample
    val appInForeground = MutableStateFlow(true)
    val speedMul = MutableStateFlow(1)
    val autoFastForward = MutableStateFlow(true)

    /** 위험 판정이 났고 소리 설정이 켜져 있을 때만 호출된다 */
    var onDangerSound: () -> Unit = {}

    private var mode = Mode.IDLE
    private var scenarioId: String? = null
    private var player: ScenarioPlayer? = null
    private var tripEvents = 0
    private val cardQueue = ArrayDeque<NotificationCard>()
    private var cardUntil = 0L

    val recorderListener = object : RecorderListener {
        override fun onTripStart(id: String) { tripEvents = 0 }

        override fun onVerdict(v: Verdict) {
            tripEvents += 1
            queueCard(if (v.caution) "caution" else v.grade.name.lowercase(), v.toast)
            if (v.grade == Grade.DANGER && _settings.value.sound) onDangerSound()
        }

        override fun onClip(c: Clip, saved: Boolean, evicted: Clip?) {
            if (!saved) queueCard("missed", "보관함이 가득 찼어요 (핀 해제 필요)")
            // onVerdict 시점 verdict 는 PENDING 이라, 이미 정산된 보고서에는 여기서 클립 연결을 반영한다
            val patched = _reports.value.map { r ->
                if (r.verdicts.none { it.id == c.verdictId }) r
                else r.copy(verdicts = r.verdicts.map {
                    if (it.id != c.verdictId) it
                    else it.copy(clipId = if (saved) c.id else null, clipStatus = if (saved) ClipStatus.SAVED else ClipStatus.FULL)
                })
            }
            _reports.value = patched
            publishClips()
            persist()
        }

        override fun onSettle(r: TripReport) {
            val done = r.copy(date = today(), scenarioId = scenarioId, sync = sync.send(r))
            _reports.value = listOf(done) + _reports.value
            persist()
            notifier(done)
            val tone = if (done.score == null || done.score >= 95) "ok" else if (done.score >= 90) "short" else "missed"
            val text = if (done.score == null) "운행 정산 완료 · 판정 없음" else "운행 정산 완료 · 이번 운행 ${done.score}점"
            queueCard(tone, text)
        }
    }

    private var rec = makeRecorder()

    private fun makeRecorder() = TripRecorder(cfg, clipStore, clock().toString(36) + "-", recorderListener)

    // ---- 명령 ----

    fun startScenario(id: String) = synchronized(lock) {
        val sc = scenarios.scenarios.first { it.id == id }
        cardQueue.clear()
        // 진행 중인 수동 운행은 버리지 않고 정산해 보고서로 남긴다
        if (rec.tripActive) rec.fastForwardStop(cfg.settleStopSec + cfg.tickSec, hub.state)
        rec = makeRecorder()
        mode = Mode.SCENARIO
        scenarioId = id
        player = ScenarioPlayer(sc, rec)
        tripEvents = 0
        housekeeping()
    }

    fun stop() = synchronized(lock) {
        endScenario()
        mode = Mode.IDLE
        hub.state = hub.state.copy(speed = 0)
        housekeeping()
    }

    fun fastForward() = synchronized(lock) {
        val p = player
        if (mode == Mode.SCENARIO && p != null) {
            p.finish()
            endScenario()
        } else if (rec.tripActive) {
            hub.state = hub.state.copy(speed = 0)
            rec.fastForwardStop(300.2, hub.state)
        }
        housekeeping()
    }

    fun startManual() = synchronized(lock) {
        if (mode == Mode.SCENARIO) endScenario()
        mode = Mode.MANUAL
        scenarioId = "수동"
        housekeeping()
    }

    fun setManual(f: (VehicleState) -> VehicleState) = synchronized(lock) {
        hub.state = f(hub.state)
        housekeeping()
    }

    fun crossLane(dir: Dir) = synchronized(lock) { if (mode == Mode.MANUAL) hub.pendingCross = dir }

    fun fillWeekSample() = synchronized(lock) {
        val base = java.time.LocalDate.ofInstant(Instant.ofEpochMilli(clock()), ZoneId.systemDefault())
        _sample.value = scenarios.weekSample.map {
            WeekTrip(base.plusDays(it.dayOffset.toLong()).toString(), it.distanceKm, Scoring.scoreFromGrades(it.grades, cfg), it.grades)
        }
        persist()
    }

    fun reset() = synchronized(lock) {
        clipStore = ClipStore(_settings.value.clipLimit)
        rec = makeRecorder()
        endScenario()
        _reports.value = emptyList()
        _sample.value = emptyList()
        publishClips()
        persist()
    }

    fun setPinned(clipId: String, pinned: Boolean) = synchronized(lock) {
        clipStore.setPinned(clipId, pinned)
        publishClips()
        persist()
    }

    fun setClipLimit(n: Int) = synchronized(lock) {
        clipStore.setLimit(n)
        _settings.value = _settings.value.copy(clipLimit = n)
        publishClips()
        persist()
    }

    fun setSound(b: Boolean) = synchronized(lock) { _settings.value = _settings.value.copy(sound = b); persist() }
    fun setDark(b: Boolean?) = synchronized(lock) { _settings.value = _settings.value.copy(dark = b); persist() }

    // ---- 시뮬레이션 ----

    /** 테스트용 수동 틱: 시뮬레이션 0.1초 + 카드 큐 갱신 */
    fun tick() = guarded {
        step()
        housekeeping()
    }

    /** 실시간 100ms마다 speedMul 틱씩 진행 */
    fun start(scope: CoroutineScope) = scope.launch {
        while (isActive) {
            delay(100)
            guarded {
                repeat(speedMul.value) { step() }
                housekeeping()
            }
        }
    }

    // 엔진 예외가 앱을 죽이면 안 된다: 보고하고 현재 실행을 IDLE 로 멈춰 매 틱 반복 예외를 막는다
    private fun guarded(body: () -> Unit) = synchronized(lock) {
        try { body() } catch (e: Exception) {
            onError(e)
            try {
                endScenario()
                mode = Mode.IDLE
                hub.state = hub.state.copy(speed = 0)
                housekeeping()
            } catch (e2: Exception) { onError(e2) }
        }
    }

    private fun step() {
        val p = player
        if (mode == Mode.SCENARIO && p != null) {
            p.step()
            // 마지막 키프레임 + 6초 뒤에도 정차 중이면 5분 정차를 기다리지 않고 정산
            if (autoFastForward.value && p.index > p.lastKeyframeIndex() + 60 && p.state.speed == 0) { p.finish() }
            if (p.done) endScenario()
        } else if (mode == Mode.MANUAL) {
            rec.tick(hub.current(), hub.laneCross())
        }
    }

    private fun endScenario() {
        player?.let { hub.state = it.state.copy(speed = 0) }
        player = null
        if (mode == Mode.SCENARIO) mode = Mode.IDLE
    }

    private fun housekeeping() {
        val p = player
        val st = if (mode == Mode.SCENARIO && p != null) p.state else hub.state
        _live.value = LiveState(
            st, tripEvents, rec.t, rec.tripActive,
            if (rec.tripActive) maxOf(0.0, cfg.settleStopSec - rec.stopTicks * cfg.tickSec) else 0.0,
        )
        if (!appInForeground.value) { cardQueue.clear(); _cards.value = null; return }
        val now = clock()
        if (_cards.value != null && now < cardUntil) return
        val next = cardQueue.removeFirstOrNull()
        _cards.value = next
        if (next != null) cardUntil = now + CARD_MS
    }

    private fun queueCard(tone: String, text: String) {
        if (!appInForeground.value) return
        cardQueue.addLast(NotificationCard(tone, text))
        if (_cards.value == null) housekeeping()
    }

    private fun publishClips() { _clips.value = clipStore.clips.toList() }

    // 저장 실패가 틱 루프를 죽이면 안 된다. 메모리 상태는 유지되고 다음 persist 때 다시 시도한다.
    private fun persist() {
        try {
            repo.save(SavedState(_reports.value, clipStore.clips.toList(), _settings.value, _sample.value))
            _saveFailed.value = false
        } catch (e: Exception) {
            _saveFailed.value = true
            onError(e)
        }
    }

    private fun today() = Instant.ofEpochMilli(clock()).atZone(ZoneId.systemDefault()).toLocalDate().toString()
}
