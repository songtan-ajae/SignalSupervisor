package dev.ijemwohajo.signalsupervisor.core

/** 판정 기준값. engine.js DEFAULT_CONFIG와 같다. */
data class JudgeConfig(
    val tickSec: Double = 0.1,
    val requiredDistanceCityM: Double = 30.0,
    val requiredDistanceHighwayM: Double = 100.0,
    val minOnSec: Double = 2.0,
    val lingerSec: Double = 10.0,
    val bsmWindowSec: Double = 1.0,
    val penalty: Map<Grade, Int> = mapOf(
        Grade.OK to 0, Grade.SHORT to 2, Grade.LATE to 3,
        Grade.MISSED to 5, Grade.DANGER to 10, Grade.LINGER to 1,
    ),
    val settleStopSec: Double = 300.0,
    val clipBeforeSec: Double = 10.0,
    val clipAfterSec: Double = 5.0,
    val clipLimit: Int = 50,
    val turnMinDeg: Double = 90.0,
    val turnMinHoldSec: Double = 1.5,
    val laneChangeMinDeg: Double = 5.0,
    val laneChangeMaxDeg: Double = 45.0,
    val laneChangeWindowSec: Double = 6.0,
    val centerToleranceDeg: Double = 3.0,
)
