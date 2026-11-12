package dev.ijemwohajo.signalsupervisor.core

enum class Signal { OFF, LEFT, RIGHT, HAZARD }
enum class Dir { LEFT, RIGHT }
enum class Road { CITY, HIGHWAY }
enum class ManeuverType { TURN, LANE }
enum class BsmStatus { WARNING, CLEAR, UNSUPPORTED }
enum class ClipStatus { NONE, PENDING, SAVED, FULL }
enum class SyncState { PENDING, SENT }

enum class Grade(val label: String) {
    OK("정상"), SHORT("짧음"), LATE("늦음"), MISSED("미점등"), DANGER("위험"), LINGER("켜둠"),
}

/** 무거운 순서. 동점·중복 처리에 사용 */
val SEVERITY = listOf(Grade.DANGER, Grade.MISSED, Grade.LATE, Grade.SHORT, Grade.LINGER, Grade.OK)

data class VehicleState(
    val speed: Int,
    val steer: Double,
    val signal: Signal,
    val road: Road,
    val bsmL: Boolean,
    val bsmR: Boolean,
    val naviTurnNear: Boolean,
    val bsmSupported: Boolean = true,
)

data class Frame(val t: Double, val odo: Double, val state: VehicleState, val laneCross: Dir?)

data class Verdict(
    val id: String,
    val grade: Grade,
    val caution: Boolean,
    val dir: Dir,
    val maneuverType: ManeuverType?,
    val keyT: Double,
    val completionT: Double,
    val sources: List<String>,
    val estimated: Boolean,
    val road: Road,
    val requiredM: Double,
    val onDistanceM: Double,
    val onDurationSec: Double,
    val offEarly: Boolean,
    val bsm: BsmStatus,
    val penalty: Int,
    val toast: String,
    val clipId: String?,
    val clipStatus: ClipStatus,
    val pinSuggested: Boolean,
)

data class TripReport(
    val id: String,
    val startT: Double,
    val endT: Double,
    val distanceKm: Double,
    val verdicts: List<Verdict>,
    val grades: Map<Grade, Int>,
    val cautions: Int,
    val score: Int?,
    val date: String,
    val scenarioId: String?,
    val sync: SyncState,
)

data class Clip(
    val id: String,
    val verdictId: String,
    val keyT: Double,
    val dir: Dir,
    val grade: Grade,
    val frames: List<Frame>,
    val pinned: Boolean,
    val views: List<String>,
)
