package dev.ijemwohajo.signalsupervisor.source

import android.content.Context
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import dev.ijemwohajo.signalsupervisor.app.AppGraph
import dev.ijemwohajo.signalsupervisor.core.Road
import dev.ijemwohajo.signalsupervisor.core.Signal

/**
 * 실차 시험용: Pleos Vehicle SDK 2.3.2 로 깜빡이·핸들 각도·기어를 받아 수동 주행 상태에 넣는다.
 * 차속·BSM·카메라·내비는 공개 API가 없어 모의로 보충한다(화면에 "추후 지원 예정" 표시).
 */
object VehicleBridge {
    // shortcut: 차속 API가 없어 D단이면 일반도로 30 km/h로 가정한다. 차속 API가 공개되면 실제 값으로 바꾼다.
    private const val MOCK_SPEED = 30
    private val DRIVE = setOf(
        VehicleGear.GEAR_DRIVE, VehicleGear.GEAR_1, VehicleGear.GEAR_2, VehicleGear.GEAR_3, VehicleGear.GEAR_4,
        VehicleGear.GEAR_5, VehicleGear.GEAR_6, VehicleGear.GEAR_7, VehicleGear.GEAR_8, VehicleGear.GEAR_9,
    )
    private var vehicle: Vehicle? = null

    fun start(ctx: Context, graph: AppGraph, onError: (Throwable) -> Unit) {
        graph.startManual()
        graph.setManual { it.copy(speed = 0, road = Road.CITY, bsmSupported = false, bsmL = false, bsmR = false, naviTurnNear = false) }
        val v = try { Vehicle(ctx.applicationContext).also { it.initialize() } } catch (e: Exception) { onError(e); return }
        vehicle = v
        // 각 도메인 등록이 실패해도 나머지는 계속 받는다
        runCatching {
            v.getTurnSignal().registerTurnSignalState(object : TurnSignalStateListener {
                override fun onTurnSignalStateUpdated(value: TurnSignalValue?) {
                    val s = when (value) { TurnSignalValue.LEFT -> Signal.LEFT; TurnSignalValue.RIGHT -> Signal.RIGHT; else -> Signal.OFF }
                    graph.setManual { it.copy(signal = s) }
                }
                override fun onFailed(e: Exception) = onError(e)
            })
        }.onFailure(onError)
        runCatching {
            // VHAL 규칙과 같이 왼쪽이 음수인 각도(도)를 엔진에 그대로 넘긴다
            v.getSteeringWheel().registerSteeringWheelAngle(object : SteeringWheelAngleListener {
                override fun onSteeringWheelAngleUpdated(angle: Float?) { if (angle != null) graph.setManual { it.copy(steer = angle.toDouble()) } }
                override fun onFailed(e: Exception) = onError(e)
            })
        }.onFailure(onError)
        runCatching {
            v.getGear().registerGearSelection(object : GearSelectionListener {
                override fun onSelected(gear: VehicleGear?) { graph.setManual { it.copy(speed = if (gear in DRIVE) MOCK_SPEED else 0) } }
                override fun onFailed(e: Exception) = onError(e)
            })
        }.onFailure(onError)
    }
}
