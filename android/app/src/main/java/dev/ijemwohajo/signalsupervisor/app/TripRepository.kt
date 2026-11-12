package dev.ijemwohajo.signalsupervisor.app

import dev.ijemwohajo.signalsupervisor.core.*
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class Settings(val clipLimit: Int = 50, val sound: Boolean = false, val dark: Boolean? = null)

data class SavedState(
    val reports: List<TripReport> = emptyList(),
    val clips: List<Clip> = emptyList(),
    val settings: Settings = Settings(),
    val sampleTrips: List<WeekTrip> = emptyList(),
)

/** state.json 한 파일에 org.json 으로 저장. 파일이 없거나 깨졌으면 빈 상태. */
open class TripRepository(private val file: File) {
    fun load(): SavedState = try {
        if (!file.exists()) SavedState() else decode(JSONObject(file.readText()))
    } catch (e: Exception) {
        // 덮어써 지우지 않도록 깨진 파일은 보관한다
        file.renameTo(File(file.path + ".corrupt-" + System.currentTimeMillis()))
        SavedState()
    }

    open fun save(s: SavedState) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(encode(s).toString())
        if (!tmp.renameTo(file)) { // Windows(테스트)에서는 대상이 있으면 실패한다
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun <T> JSONArray.mapObjects(f: (JSONObject) -> T) = (0 until length()).map { f(getJSONObject(it)) }
    private fun <T> Iterable<T>.toJson(f: (T) -> Any) = JSONArray().also { a -> forEach { a.put(f(it)) } }
    private fun JSONObject.optDir(k: String): Dir? = if (isNull(k) || !has(k)) null else Dir.valueOf(getString(k))

    private fun encode(s: SavedState) = JSONObject()
        .put("reports", s.reports.toJson(::report))
        .put("clips", s.clips.toJson(::clip))
        .put("settings", JSONObject().put("clipLimit", s.settings.clipLimit).put("sound", s.settings.sound)
            .put("dark", s.settings.dark ?: JSONObject.NULL))
        .put("sampleTrips", s.sampleTrips.toJson { t ->
            JSONObject().put("date", t.date).put("distanceKm", t.distanceKm)
                .put("score", t.score ?: JSONObject.NULL).put("grades", grades(t.grades))
        })

    private fun decode(o: JSONObject): SavedState {
        val st = o.optJSONObject("settings")
        return SavedState(
            reports = o.optJSONArray("reports")?.mapObjects(::report) ?: emptyList(),
            clips = o.optJSONArray("clips")?.mapObjects(::clip) ?: emptyList(),
            settings = if (st == null) Settings() else Settings(
                st.optInt("clipLimit", 50), st.optBoolean("sound", false),
                if (st.isNull("dark")) null else st.getBoolean("dark"),
            ),
            sampleTrips = o.optJSONArray("sampleTrips")?.mapObjects { t ->
                WeekTrip(
                    t.getString("date"), t.getDouble("distanceKm"),
                    if (t.isNull("score")) null else t.getInt("score"), grades(t.getJSONObject("grades")),
                )
            } ?: emptyList(),
        )
    }

    private fun grades(m: Map<Grade, Int>) = JSONObject().also { o -> m.forEach { (g, n) -> o.put(g.name, n) } }
    private fun grades(o: JSONObject): Map<Grade, Int> = o.keys().asSequence().associate { Grade.valueOf(it) to o.getInt(it) }

    private fun report(r: TripReport) = JSONObject().put("id", r.id).put("startT", r.startT).put("endT", r.endT)
        .put("distanceKm", r.distanceKm).put("verdicts", r.verdicts.toJson(::verdict)).put("grades", grades(r.grades))
        .put("cautions", r.cautions).put("score", r.score ?: JSONObject.NULL).put("date", r.date)
        .put("scenarioId", r.scenarioId ?: JSONObject.NULL).put("sync", r.sync.name)

    private fun report(o: JSONObject) = TripReport(
        o.getString("id"), o.getDouble("startT"), o.getDouble("endT"), o.getDouble("distanceKm"),
        o.getJSONArray("verdicts").mapObjects(::verdict), grades(o.getJSONObject("grades")), o.getInt("cautions"),
        if (o.isNull("score")) null else o.getInt("score"), o.getString("date"),
        if (o.isNull("scenarioId")) null else o.getString("scenarioId"), SyncState.valueOf(o.getString("sync")),
    )

    private fun verdict(v: Verdict) = JSONObject().put("id", v.id).put("grade", v.grade.name).put("caution", v.caution)
        .put("dir", v.dir.name).put("maneuverType", v.maneuverType?.name ?: JSONObject.NULL).put("keyT", v.keyT)
        .put("completionT", v.completionT).put("sources", v.sources.toJson { it }).put("estimated", v.estimated)
        .put("road", v.road.name).put("requiredM", v.requiredM).put("onDistanceM", v.onDistanceM)
        .put("onDurationSec", v.onDurationSec).put("offEarly", v.offEarly).put("bsm", v.bsm.name)
        .put("penalty", v.penalty).put("toast", v.toast).put("clipId", v.clipId ?: JSONObject.NULL)
        .put("clipStatus", v.clipStatus.name).put("pinSuggested", v.pinSuggested)

    private fun verdict(o: JSONObject) = Verdict(
        o.getString("id"), Grade.valueOf(o.getString("grade")), o.getBoolean("caution"), Dir.valueOf(o.getString("dir")),
        if (o.isNull("maneuverType")) null else ManeuverType.valueOf(o.getString("maneuverType")),
        o.getDouble("keyT"), o.getDouble("completionT"),
        o.getJSONArray("sources").let { a -> (0 until a.length()).map { a.getString(it) } }, o.getBoolean("estimated"),
        Road.valueOf(o.getString("road")), o.getDouble("requiredM"), o.getDouble("onDistanceM"),
        o.getDouble("onDurationSec"), o.getBoolean("offEarly"), BsmStatus.valueOf(o.getString("bsm")),
        o.getInt("penalty"), o.getString("toast"), if (o.isNull("clipId")) null else o.getString("clipId"),
        ClipStatus.valueOf(o.getString("clipStatus")), o.getBoolean("pinSuggested"),
    )

    private fun clip(c: Clip) = JSONObject().put("id", c.id).put("verdictId", c.verdictId).put("keyT", c.keyT)
        .put("dir", c.dir.name).put("grade", c.grade.name).put("pinned", c.pinned).put("views", c.views.toJson { it })
        .put("frames", c.frames.toJson(::frame))

    private fun clip(o: JSONObject) = Clip(
        o.getString("id"), o.getString("verdictId"), o.getDouble("keyT"), Dir.valueOf(o.getString("dir")),
        Grade.valueOf(o.getString("grade")), o.getJSONArray("frames").mapObjects(::frame), o.getBoolean("pinned"),
        o.getJSONArray("views").let { a -> (0 until a.length()).map { a.getString(it) } },
    )

    private fun frame(f: Frame) = JSONObject().put("t", f.t).put("odo", f.odo).put("laneCross", f.laneCross?.name ?: JSONObject.NULL)
        .put("state", f.state.let { s ->
            JSONObject().put("speed", s.speed).put("steer", s.steer).put("signal", s.signal.name).put("road", s.road.name)
                .put("bsmL", s.bsmL).put("bsmR", s.bsmR).put("naviTurnNear", s.naviTurnNear).put("bsmSupported", s.bsmSupported)
        })

    private fun frame(o: JSONObject): Frame {
        val s = o.getJSONObject("state")
        return Frame(
            o.getDouble("t"), o.getDouble("odo"),
            VehicleState(
                s.getInt("speed"), s.getDouble("steer"), Signal.valueOf(s.getString("signal")), Road.valueOf(s.getString("road")),
                s.getBoolean("bsmL"), s.getBoolean("bsmR"), s.getBoolean("naviTurnNear"), s.getBoolean("bsmSupported"),
            ),
            o.optDir("laneCross"),
        )
    }
}
