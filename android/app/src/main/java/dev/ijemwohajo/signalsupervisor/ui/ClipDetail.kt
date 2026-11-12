package dev.ijemwohajo.signalsupervisor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ijemwohajo.signalsupervisor.R
import dev.ijemwohajo.signalsupervisor.core.BsmStatus
import dev.ijemwohajo.signalsupervisor.core.Clip
import dev.ijemwohajo.signalsupervisor.core.ClipStatus
import dev.ijemwohajo.signalsupervisor.core.Dir
import dev.ijemwohajo.signalsupervisor.core.Frame
import dev.ijemwohajo.signalsupervisor.core.Signal
import dev.ijemwohajo.signalsupervisor.core.Verdict
import dev.ijemwohajo.signalsupervisor.ui.theme.Pal
import dev.ijemwohajo.signalsupervisor.ui.theme.Corner
import dev.ijemwohajo.signalsupervisor.ui.theme.corner
import dev.ijemwohajo.signalsupervisor.ui.theme.cq
import dev.ijemwohajo.signalsupervisor.ui.theme.pal
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** 기록 상세. clip 이 null 이면(정상 판정·보관함 가득·FIFO 삭제) 사유만 보여준다. 이 화면을 벗어나면 재생이 멈춘다. */
@Composable
fun ClipDetail(v: Verdict, clip: Clip?, modifier: Modifier, onBack: () -> Unit, onPin: (String, Boolean) -> Unit) {
    val p = pal
    val t = tone(v)
    Column(modifier.verticalScroll(rememberScrollState()), Arrangement.spacedBy(cq(0.9f))) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(cq(0.9f))) {
            AppButton("목록", onBack, icon = R.drawable.pleos_arrowleft)
            Badge(toneLabel(v) + if (v.penalty != 0) " −${v.penalty}" else "", t, gradeIcon(t))
            Txt(maneuverName(v) + if (v.estimated) " (추정)" else "", weight = FontWeight.Bold)
            if (v.pinSuggested && clip != null && !clip.pinned) Badge("보관 권장", "danger")
        }
        Txt(COACH.getValue(t), 1.1f, p.muted)
        if (clip != null) ClipPlayer(v, clip, onPin) else {
            val why = when (v.clipStatus) {
                ClipStatus.NONE -> "정상 판정은 영상을 저장하지 않아요"
                ClipStatus.FULL -> "보관함이 가득 차 저장하지 못했어요"
                else -> "영상 없음 (FIFO로 삭제됨)"
            }
            Column(Modifier.fillMaxWidth().aspectRatio(32f / 9f).clip(corner(Corner.M)).background(p.surface), Arrangement.spacedBy(cq(0.5f), Alignment.CenterVertically), Alignment.CenterHorizontally) {
                PIcon(R.drawable.pleos_video, 3f, p.muted)
                Txt(why, color = p.muted)
            }
        }
        if (v.maneuverType != null) {
            val bsm = when (v.bsm) { BsmStatus.WARNING -> "경고 중"; BsmStatus.CLEAR -> "경고 없음"; BsmStatus.UNSUPPORTED -> "미지원" }
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(cq(0.7f))) {
                Metric("켠 거리 / 기준", "${v.onDistanceM.f()}m / ${v.requiredM.f()}m", Modifier.weight(1f))
                Metric("점등 지속", v.onDurationSec.f() + "s" + if (v.offEarly) " (완료 전 끔)" else "", Modifier.weight(1f))
                Metric("BSM ${dirName(v.dir)}", bsm, Modifier.weight(1f))
                Metric("감지 근거", v.sources.joinToString("+"), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(corner(Corner.M)).background(pal.surface).padding(horizontal = cq(1f), vertical = cq(0.7f))) {
        Txt(label, 0.9f, pal.muted)
        Txt(value, 1.4f, weight = FontWeight.Bold)
    }
}

@Composable
private fun ClipPlayer(v: Verdict, clip: Clip, onPin: (String, Boolean) -> Unit) {
    val p = pal
    val frames = clip.frames
    val n = frames.size
    var i by remember(clip.id) { mutableIntStateOf(0) }
    var playing by remember(clip.id) { mutableStateOf(false) }
    // 차선 넘김 전후 1초(±10프레임)에 걸쳐 가로로 이동
    val lane = remember(clip.id) {
        val crosses = frames.mapIndexedNotNull { idx, f -> f.laneCross?.let { idx to if (it == Dir.LEFT) -1f else 1f } }
        FloatArray(n) { idx -> crosses.sumOf { (c, d) -> (d * ((idx - c + 10) / 20f).coerceIn(0f, 1f)).toDouble() }.toFloat() }
    }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        if (i >= n - 1) i = 0
        while (true) {
            delay(100)
            if (i < n - 1) i += 1
            if (i >= n - 1) { playing = false; break }
        }
    }
    val fr = frames[i.coerceIn(0, maxOf(0, n - 1))]
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(cq(0.9f))) {
        Box(Modifier.weight(1f).aspectRatio(16f / 9f).clip(corner(Corner.M)).background(Color.Black)) {
            Canvas(Modifier.fillMaxSize()) { drawFront(fr, lane[i.coerceIn(0, n - 1)]) }
            Cap("${clip.views.getOrElse(0) { "" }} · 모의 영상")
        }
        Box(Modifier.weight(1f).aspectRatio(16f / 9f).clip(corner(Corner.M)).background(Color.Black)) {
            Canvas(Modifier.fillMaxSize()) { drawSide(fr, v.dir) }
            Cap("${clip.views.getOrElse(1) { "" }} · 모의 영상")
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(cq(0.9f))) {
        AppButton(
            if (playing) "일시정지" else if (i >= n - 1) "다시 보기" else "재생", { playing = !playing }, primary = true,
            icon = if (playing) R.drawable.pleos_playerpause else R.drawable.pleos_playerplay,
        )
        Slider(
            i.toFloat(), { i = it.roundToInt() }, Modifier.weight(1f).semantics { contentDescription = "영상 위치" }, valueRange = 0f..maxOf(1, n - 1).toFloat(),
            colors = SliderDefaults.colors(thumbColor = p.ink, activeTrackColor = p.ink, inactiveTrackColor = p.surface2),
        )
        val rel = fr.t - v.keyT
        Txt((if (rel >= 0) "+" else "−") + "%.1fs".format(abs(rel)), color = p.muted)
        AppButton(
            if (clip.pinned) "보관 중" else "보관(핀)", { onPin(clip.id, !clip.pinned) },
            icon = if (clip.pinned) R.drawable.pleos_pin else R.drawable.pleos_pin_line,
        )
    }
    Timeline(frames, v, i, p)
    FlowRow(Modifier.fillMaxWidth(), Arrangement.spacedBy(cq(1.3f))) {
        Legend(p.signal, "깜빡이 ${dirName(v.dir)}")
        Legend(p.danger, "BSM ${dirName(v.dir)} 경고")
        Legend(p.ink, "조작 시점")
        Legend(p.ok, "조작 완료")
    }
}

@Composable
private fun Cap(text: String) =
    Txt(text, 0.9f, Color.White, modifier = Modifier.padding(cq(0.6f)).clip(CircleShape).background(Color(0x8C000000)).padding(horizontal = cq(0.6f), vertical = 1.dp))

@Composable
private fun Legend(c: Color, text: String) =
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(cq(0.4f))) {
        Box(Modifier.size(cq(1.1f), cq(0.7f)).clip(corner(Corner.XS)).background(c))
        Txt(text, 0.95f, pal.muted)
    }

@Composable
private fun Timeline(frames: List<Frame>, v: Verdict, cur: Int, p: Pal) {
    val n = frames.size
    val sig = if (v.dir == Dir.LEFT) Signal.LEFT else Signal.RIGHT
    val mw = cq(0.22f)
    val hw = cq(0.18f)
    Canvas(Modifier.fillMaxWidth().height(cq(3.4f)).clip(corner(Corner.M)).background(p.surface2)) {
        val step = size.width / maxOf(1, n - 1)
        fun bands(top: Float, color: Color, pred: (Frame) -> Boolean) {
            var s = -1
            for (idx in 0..n) {
                val on = idx < n && pred(frames[idx])
                if (on && s < 0) s = idx
                if (!on && s >= 0) { drawRect(color, Offset(s * step, top), Size((idx - s) * step, size.height / 2)); s = -1 }
            }
        }
        bands(0f, p.signal.copy(alpha = 0.75f)) { it.state.signal == sig }
        bands(size.height / 2, p.danger.copy(alpha = 0.75f)) { if (v.dir == Dir.LEFT) it.state.bsmL else it.state.bsmR }
        val mwPx = mw.toPx()
        val hwPx = hw.toPx()
        frames.indexOfFirst { abs(it.t - v.keyT) < 1e-6 }.takeIf { it >= 0 }?.let { drawRect(p.ink, Offset(it * step, 0f), Size(mwPx, size.height)) }
        frames.indexOfFirst { abs(it.t - v.completionT) < 1e-6 }.takeIf { it >= 0 }?.let { drawRect(p.ok, Offset(it * step, 0f), Size(mwPx, size.height)) }
        drawRect(Color.Black, Offset(cur * step - 1f, 0f), Size(hwPx + 2f, size.height))
        drawRect(Color.White, Offset(cur * step, 0f), Size(hwPx, size.height))
    }
}
