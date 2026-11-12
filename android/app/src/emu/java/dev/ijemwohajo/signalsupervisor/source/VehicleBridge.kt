package dev.ijemwohajo.signalsupervisor.source

import android.content.Context
import dev.ijemwohajo.signalsupervisor.app.AppGraph

/** 에뮬레이터 시연용: 차량 신호는 시뮬레이터 탭이 넣으므로 할 일이 없다. */
object VehicleBridge {
    @Suppress("UNUSED_PARAMETER")
    fun start(ctx: Context, graph: AppGraph, onError: (Throwable) -> Unit) = Unit
}
