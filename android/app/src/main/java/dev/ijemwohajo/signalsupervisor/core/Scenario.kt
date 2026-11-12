package dev.ijemwohajo.signalsupervisor.core

import org.json.JSONObject

data class Keyframe(val t: Double, val set: Map<String, Any>?, val laneCross: Dir?)
data class Expected(val score: Int?, val grades: Map<Grade, Int>, val clips: Int, val cautions: Int?)
data class Scenario(
    val id: String, val title: String, val subtitle: String, val endSec: Double,
    val keyframes: List<Keyframe>, val expected: Expected,
)
data class WeekSampleTrip(val dayOffset: Int, val distanceKm: Double, val road: Road, val grades: Map<Grade, Int>)
data class ScenarioFile(val scenarios: List<Scenario>, val weekSample: List<WeekSampleTrip>)

object ScenarioJson {
    private fun grades(o: JSONObject?): Map<Grade, Int> =
        o?.keys()?.asSequence()?.associate { Grade.valueOf(it.uppercase()) to o.getInt(it) } ?: emptyMap()

    fun parse(json: String): ScenarioFile {
        val root = JSONObject(json)
        val scs = root.getJSONArray("scenarios")
        val scenarios = (0 until scs.length()).map { i ->
            val s = scs.getJSONObject(i)
            val kfs = s.getJSONArray("keyframes")
            val e = s.getJSONObject("expected")
            Scenario(
                s.getString("id"), s.optString("title"), s.optString("subtitle"), s.getDouble("endSec"),
                (0 until kfs.length()).map { j ->
                    val k = kfs.getJSONObject(j)
                    val set = k.optJSONObject("set")
                    Keyframe(
                        k.getDouble("t"),
                        set?.keys()?.asSequence()?.associateWith { set.get(it) },
                        if (k.has("laneCross")) Dir.valueOf(k.getString("laneCross").uppercase()) else null,
                    )
                },
                Expected(
                    if (e.isNull("score")) null else e.getInt("score"), grades(e.optJSONObject("grades")),
                    e.getInt("clips"), if (e.has("cautions")) e.getInt("cautions") else null,
                ),
            )
        }
        val trips = root.optJSONObject("weekSample")?.getJSONArray("trips")
        val week = trips?.let { a ->
            (0 until a.length()).map { i ->
                val t = a.getJSONObject(i)
                WeekSampleTrip(
                    t.getInt("dayOffset"), t.getDouble("distanceKm"),
                    Road.valueOf(t.getString("road").uppercase()), grades(t.optJSONObject("grades")),
                )
            }
        } ?: emptyList()
        return ScenarioFile(scenarios, week)
    }
}

class ScenarioPlayer(private val sc: Scenario, private val rec: TripRecorder) {
    private val tick = rec.cfg.tickSec
    private val total = Math.round(sc.endSec / tick).toInt()
    private val sets = HashMap<Int, MutableMap<String, Any>>()
    private val cross = HashMap<Int, Dir>()
    private val cur = HashMap<String, Any>()

    var index = 0
        private set
    val done: Boolean get() = index >= total
    var state = build()
        private set

    init {
        for (k in sc.keyframes) {
            val idx = Math.round(k.t / tick).toInt()
            k.set?.let { sets.getOrPut(idx) { mutableMapOf() }.putAll(it) }
            k.laneCross?.let { cross[idx] = it }
        }
    }

    private fun build() = VehicleState(
        speed = (cur["speed"] as? Number)?.toInt() ?: 0,
        steer = (cur["steer"] as? Number)?.toDouble() ?: 0.0,
        signal = (cur["signal"] as? String)?.let { Signal.valueOf(it.uppercase()) } ?: Signal.OFF,
        road = (cur["road"] as? String)?.let { Road.valueOf(it.uppercase()) } ?: Road.CITY,
        bsmL = cur["bsmL"] as? Boolean ?: false,
        bsmR = cur["bsmR"] as? Boolean ?: false,
        naviTurnNear = cur["naviTurnNear"] as? Boolean ?: false,
        bsmSupported = cur["bsmSupported"] as? Boolean ?: true,
    )

    fun step() {
        if (done) return
        sets[index]?.let { cur.putAll(it) }
        state = build()
        rec.tick(state, cross[index])
        index += 1
    }

    fun finish() { while (!done) step() }

    fun lastKeyframeIndex(): Int = sc.keyframes.maxOf { Math.round(it.t / tick).toInt() }
}

data class RunResult(val verdicts: List<Verdict>, val reports: List<TripReport>, val clips: List<Clip>)

fun runScenario(sc: Scenario, cfg: JudgeConfig = JudgeConfig()): RunResult {
    val verdicts = mutableListOf<Verdict>()
    val reports = mutableListOf<TripReport>()
    val clips = mutableListOf<Clip>()
    val rec = TripRecorder(cfg, ClipStore(cfg.clipLimit), "", object : RecorderListener {
        override fun onVerdict(v: Verdict) { verdicts.add(v) }
        override fun onSettle(r: TripReport) { reports.add(r) }
        override fun onClip(c: Clip, saved: Boolean, evicted: Clip?) { if (saved) clips.add(c) }
    })
    ScenarioPlayer(sc, rec).finish()
    return RunResult(verdicts, reports, clips)
}
