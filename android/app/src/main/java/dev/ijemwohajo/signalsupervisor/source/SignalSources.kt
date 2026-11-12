package dev.ijemwohajo.signalsupervisor.source

import dev.ijemwohajo.signalsupervisor.core.*

/** 기획서 §3 신호 공급 인터페이스. Pleos SDK가 공개되면 실제 구현으로 바꾼다. */
interface VehicleSignalSource { fun current(): VehicleState }
interface NaviSignalSource { fun turnNear(): Boolean } // 회전 구간 임박 여부
interface LaneCameraSource { fun laneCross(): Dir? } // 이번 틱의 차선 넘김(없으면 null, 읽으면 비워진다)
interface CameraClipSource { fun views(dir: Dir): List<String> } // 클립에 담을 영상 시점
interface ScoreSyncAdapter { fun send(r: TripReport): SyncState }

/** 시뮬레이터 상태를 담아 각 인터페이스에 제공한다. */
class MockSignalHub : VehicleSignalSource, NaviSignalSource, LaneCameraSource, CameraClipSource {
    @Volatile var state = VehicleState(0, 0.0, Signal.OFF, Road.CITY, false, false, false)
    @Volatile var pendingCross: Dir? = null

    override fun current(): VehicleState = state
    override fun turnNear(): Boolean = state.naviTurnNear
    override fun laneCross(): Dir? = pendingCross.also { pendingCross = null }
    override fun views(dir: Dir): List<String> = listOf("빌트인캠", if (dir == Dir.LEFT) "좌측 사이드미러" else "우측 사이드미러")
}

class MockScoreSync : ScoreSyncAdapter {
    override fun send(r: TripReport): SyncState = SyncState.SENT
}
