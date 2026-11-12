package dev.ijemwohajo.signalsupervisor.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

interface RecorderListener {
    fun onTripStart(id: String) {}
    fun onVerdict(v: Verdict) {}
    fun onClip(c: Clip, saved: Boolean, evicted: Clip?) {}
    fun onSettle(r: TripReport) {}
}

val TOAST = mapOf(
    Grade.OK to "깜빡이 굿! 👍 안전 운전 중이에요",
    Grade.SHORT to "끝날 때까지 깜빡! 조금만 더 길게",
    Grade.LATE to "조금만 더 일찍 깜빡!",
    Grade.MISSED to "깜빡 깜빡! 중요해요",
    Grade.DANGER to "깜빡 깜빡! 옆 차로에 차가 있었어요",
    Grade.LINGER to "깜빡이를 꺼주세요 🙂",
)
const val TOAST_CAUTION = "옆 차로를 한 번 더 확인해요"

private const val EPS = 1e-6

/** engine.js TripRecorder 1:1 이식. */
class TripRecorder(
    val cfg: JudgeConfig = JudgeConfig(),
    private val clipStore: ClipStore,
    private val idPrefix: String = "",
    private val listener: RecorderListener,
) {
    private class Trip(val id: String, val startT: Double, val startOdo: Double) {
        val verdicts = mutableListOf<Verdict>()
        var stopTicks = 0
    }

    private class Episode(val startT: Double, val firstSign: Double, var firstPeak: Double, var firstPeakT: Double, var maxAbs: Double) {
        var reversed = false
        var laneCross = false
        var aboveSince: Double? = null
        var aboveSign = 0.0
        var turnKey = false
    }

    private class Maneuver(
        val id: String, val type: ManeuverType, val dir: Dir, val keyT: Double,
        val sources: List<String>, val estimated: Boolean, var completionT: Double?, val episode: Episode?,
    )

    private class Segment(val dir: Dir, val startT: Double, val startOdo: Double, var endT: Double? = null)
    private class Linger(val dir: Dir, var ticks: Int = 0)
    private class PendingClip(val verdict: Verdict, val keyT: Double, val trip: Trip?)

    private var n = 0
    private var odo = 0.0
    private val history = ArrayDeque<Frame>()
    private val segments = mutableListOf<Segment>()
    private var prevSignal = Signal.OFF
    private var episode: Episode? = null
    private val pending = mutableListOf<Maneuver>()
    private val pendingClips = mutableListOf<PendingClip>()
    private var linger: Linger? = null
    private var trip: Trip? = null
    private var seq = 0

    val t: Double get() = Math.round(n * cfg.tickSec * 1e6) / 1e6
    val tripActive: Boolean get() = trip != null
    val stopTicks: Int get() = trip?.stopTicks ?: 0

    fun tick(s: VehicleState, laneCross: Dir?) {
        val t = this.t

        // 1) 운행 시작
        if (trip == null && s.speed > 0) {
            val tr = Trip(idPrefix + "trip-" + ++seq, t, odo)
            trip = tr
            listener.onTripStart(tr.id)
        }

        // 2) 이력 기록
        history.addLast(Frame(t, odo, s, laneCross))
        val keepSec = cfg.clipBeforeSec + cfg.clipAfterSec + cfg.lingerSec + 30
        while (history.isNotEmpty() && history.first().t < t - keepSec) history.removeFirst()

        // 3) 깜빡이 구간
        if (s.signal != prevSignal) {
            segments.firstOrNull { it.endT == null }?.endT = t
            if (s.signal == Signal.LEFT || s.signal == Signal.RIGHT) {
                segments.add(Segment(if (s.signal == Signal.LEFT) Dir.LEFT else Dir.RIGHT, t, odo))
            }
            prevSignal = s.signal
            while (segments.size > 50) segments.removeAt(0)
        }

        // 4) 카메라 차선 넘김
        if (laneCross != null) {
            val ep = episode
            val id = idPrefix + "m-" + ++seq
            val m = if (ep != null) {
                ep.laneCross = true
                Maneuver(id, ManeuverType.LANE, laneCross, t, listOf("카메라", "핸들"), false, null, ep)
            } else {
                Maneuver(id, ManeuverType.LANE, laneCross, t, listOf("카메라"), false, t, null)
            }
            onNewManeuver(m)
        }

        // 5) 핸들 구간
        updateEpisode(t, s)

        // 6) 대기 판정 확정
        for (m in pending.toList()) {
            val c = m.completionT ?: continue
            if (t + EPS >= max(c, m.keyT + cfg.bsmWindowSec)) {
                pending.remove(m)
                finalize(m)
            }
        }

        // 7) 켜둠 감시
        linger?.let { lg ->
            if (sigDir(s.signal) != lg.dir) linger = null
            else if (s.speed > 0) {
                lg.ticks += 1
                if (lg.ticks * cfg.tickSec > cfg.lingerSec + EPS) {
                    val v = Verdict(
                        id = idPrefix + "v-" + ++seq, grade = Grade.LINGER, caution = false, dir = lg.dir,
                        maneuverType = null, keyT = t, completionT = t, sources = emptyList(), estimated = false,
                        road = s.road, requiredM = 0.0, onDistanceM = 0.0, onDurationSec = 0.0, offEarly = false,
                        bsm = BsmStatus.UNSUPPORTED, penalty = cfg.penalty[Grade.LINGER] ?: 0,
                        toast = TOAST.getValue(Grade.LINGER), clipId = null, clipStatus = ClipStatus.NONE,
                        pinSuggested = false,
                    )
                    linger = null
                    record(v)
                }
            }
        }

        // 8) 클립 캡처
        for (pc in pendingClips.toList()) {
            if (t + EPS >= pc.keyT + cfg.clipAfterSec) {
                pendingClips.remove(pc)
                captureClip(pc)
            }
        }

        // 9) 정차 → 정산
        trip?.let { tr ->
            if (s.speed == 0) {
                tr.stopTicks += 1
                if ((tr.stopTicks - 1) * cfg.tickSec + EPS >= cfg.settleStopSec && pending.isEmpty()) settle(t)
            } else tr.stopTicks = 0
        }

        // 10) 거리 적분
        odo += (s.speed / 3.6) * cfg.tickSec
        n += 1
    }

    /** 시뮬레이터 빨리 감기: 정차 상태로 sec초 진행 */
    fun fastForwardStop(sec: Double, s: VehicleState) {
        val ticks = Math.round(sec / cfg.tickSec).toInt()
        repeat(ticks) { tick(s.copy(speed = 0), null) }
    }

    private fun onNewManeuver(m: Maneuver) {
        if (linger?.dir == m.dir) linger = null
        pending.add(m)
    }

    private fun updateEpisode(t: Double, s: VehicleState) {
        val a = abs(s.steer)
        val sg = sign(s.steer)
        var ep = episode
        if (ep == null && a > cfg.centerToleranceDeg) {
            ep = Episode(t, sg, a, t, a)
            episode = ep
        }
        if (ep == null) return

        if (a > cfg.centerToleranceDeg) {
            ep.maxAbs = max(ep.maxAbs, a)
            if (!ep.reversed && sg == ep.firstSign && a > ep.firstPeak) {
                ep.firstPeak = a
                ep.firstPeakT = t
            }
            if (sg != 0.0 && sg != ep.firstSign) ep.reversed = true
            if (a >= cfg.turnMinDeg) {
                if (ep.aboveSince == null) {
                    ep.aboveSince = t
                    ep.aboveSign = sg
                }
                val since = ep.aboveSince!!
                if (!ep.turnKey && t - since + cfg.tickSec + EPS >= cfg.turnMinHoldSec) {
                    ep.turnKey = true
                    val at = histAt(since)
                    onNewManeuver(
                        Maneuver(
                            idPrefix + "m-" + ++seq, ManeuverType.TURN, if (ep.aboveSign < 0) Dir.LEFT else Dir.RIGHT, since,
                            if (at != null && at.state.naviTurnNear) listOf("내비", "핸들") else listOf("핸들"),
                            false, null, ep,
                        ),
                    )
                }
            } else ep.aboveSince = null
            return
        }

        // 핸들 복귀 → 구간 종료
        episode = null
        for (m in pending) if (m.episode === ep && m.completionT == null) m.completionT = t
        if (ep.turnKey || ep.laneCross) return
        val dur = t - ep.startT
        if (ep.reversed && ep.firstPeak >= cfg.laneChangeMinDeg && ep.maxAbs <= cfg.laneChangeMaxDeg &&
            dur <= cfg.laneChangeWindowSec + EPS
        ) {
            onNewManeuver(
                Maneuver(
                    idPrefix + "m-" + ++seq, ManeuverType.LANE, if (ep.firstSign < 0) Dir.LEFT else Dir.RIGHT,
                    ep.firstPeakT, listOf("핸들"), true, t, null,
                ),
            )
        }
    }

    private fun histAt(t: Double): Frame? {
        var best: Frame? = null
        for (h in history) {
            if (h.t <= t + EPS) best = h else break
        }
        return best
    }

    private fun sigDir(s: Signal): Dir? = when (s) { Signal.LEFT -> Dir.LEFT; Signal.RIGHT -> Dir.RIGHT; else -> null }

    private fun finalize(m: Maneuver) {
        val completionT = m.completionT!!
        val key = histAt(m.keyT)
        val road = key?.state?.road ?: Road.CITY
        val requiredM = if (road == Road.HIGHWAY) cfg.requiredDistanceHighwayM else cfg.requiredDistanceCityM
        var grade: Grade
        var onDistanceM = 0.0
        var onDurationSec = 0.0
        var offEarly = false
        if (key == null || sigDir(key.state.signal) != m.dir) {
            grade = Grade.MISSED
        } else {
            val seg = segments.asReversed().first {
                val e = it.endT
                it.dir == m.dir && it.startT <= m.keyT + EPS && (e == null || e > m.keyT + EPS)
            }
            onDistanceM = Scoring.round1(key.odo - seg.startOdo)
            val end = seg.endT
            offEarly = end != null && end < completionT - EPS
            val until = if (end != null) min(end, completionT) else completionT
            onDurationSec = Scoring.round1(until - seg.startT)
            grade = if (onDistanceM + EPS < requiredM) Grade.LATE
            else if (offEarly || onDurationSec + EPS < cfg.minOnSec) Grade.SHORT
            else Grade.OK
        }

        val bsmSupported = key?.state?.bsmSupported ?: true
        var bsmHit = false
        if (bsmSupported) {
            for (h in history) {
                if (h.t < m.keyT - cfg.bsmWindowSec - EPS || h.t > m.keyT + cfg.bsmWindowSec + EPS) continue
                if ((m.dir == Dir.LEFT && h.state.bsmL) || (m.dir == Dir.RIGHT && h.state.bsmR)) bsmHit = true
            }
        }
        var caution = false
        if (bsmHit && grade != Grade.OK) grade = Grade.DANGER
        else if (bsmHit && grade == Grade.OK) caution = true

        val needsClip = grade != Grade.OK || caution
        val v = Verdict(
            id = idPrefix + "v-" + ++seq, grade = grade, caution = caution, dir = m.dir, maneuverType = m.type,
            keyT = m.keyT, completionT = completionT, sources = m.sources, estimated = m.estimated, road = road,
            requiredM = requiredM, onDistanceM = onDistanceM, onDurationSec = onDurationSec, offEarly = offEarly,
            bsm = if (!bsmSupported) BsmStatus.UNSUPPORTED else if (bsmHit) BsmStatus.WARNING else BsmStatus.CLEAR,
            penalty = cfg.penalty[grade] ?: 0, toast = if (caution) TOAST_CAUTION else TOAST.getValue(grade),
            clipId = null, clipStatus = if (needsClip) ClipStatus.PENDING else ClipStatus.NONE,
            pinSuggested = grade == Grade.DANGER,
        )
        record(v)

        val atCompletion = histAt(completionT)
        if (atCompletion != null && sigDir(atCompletion.state.signal) == m.dir && sigDir(prevSignal) == m.dir) {
            val lg = Linger(m.dir)
            linger = lg
            // 완료 시각 이후 이미 지난 주행 틱 반영 (현재 틱 제외)
            for (h in history) {
                if (h.t > completionT + EPS && h.t < this.t - EPS && sigDir(h.state.signal) == m.dir && h.state.speed > 0) lg.ticks += 1
            }
        }
        if (needsClip) pendingClips.add(PendingClip(v, m.keyT, trip))
    }

    private fun captureClip(pc: PendingClip) {
        val v = pc.verdict
        val frames = history.filter { it.t >= pc.keyT - cfg.clipBeforeSec - EPS && it.t <= pc.keyT + cfg.clipAfterSec + EPS }
        val clip = Clip(
            id = idPrefix + "clip-" + ++seq, verdictId = v.id, keyT = pc.keyT, dir = v.dir, grade = v.grade,
            frames = frames, pinned = false,
            views = listOf("빌트인캠", if (v.dir == Dir.LEFT) "좌측 사이드미러" else "우측 사이드미러"),
        )
        val res = clipStore.add(clip)
        val updated = v.copy(
            clipStatus = if (res.saved) ClipStatus.SAVED else ClipStatus.FULL,
            clipId = if (res.saved) clip.id else null,
        )
        pc.trip?.let { tr ->
            val i = tr.verdicts.indexOfFirst { it.id == v.id }
            if (i >= 0) tr.verdicts[i] = updated
        }
        listener.onClip(clip, res.saved, res.evicted)
    }

    private fun record(v: Verdict) {
        trip?.verdicts?.add(v)
        listener.onVerdict(v)
    }

    private fun settle(t: Double) {
        val tr = trip!!
        trip = null
        val grades = LinkedHashMap<Grade, Int>()
        for (v in tr.verdicts) grades[v.grade] = (grades[v.grade] ?: 0) + 1
        listener.onSettle(
            TripReport(
                id = tr.id, startT = tr.startT, endT = t, distanceKm = Scoring.round1((odo - tr.startOdo) / 1000),
                verdicts = tr.verdicts.toList(), grades = grades, cautions = tr.verdicts.count { it.caution },
                score = Scoring.scoreFromGrades(grades, cfg), date = "", scenarioId = null, sync = SyncState.PENDING,
            ),
        )
    }
}
