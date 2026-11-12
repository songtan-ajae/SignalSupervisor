package dev.ijemwohajo.signalsupervisor.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ijemwohajo.signalsupervisor.R
import dev.ijemwohajo.signalsupervisor.core.Dir
import dev.ijemwohajo.signalsupervisor.core.Grade
import dev.ijemwohajo.signalsupervisor.core.ManeuverType
import dev.ijemwohajo.signalsupervisor.core.Verdict
import dev.ijemwohajo.signalsupervisor.ui.theme.cq
import dev.ijemwohajo.signalsupervisor.ui.theme.cs
import dev.ijemwohajo.signalsupervisor.ui.theme.pal
import kotlinx.coroutines.delay
import java.time.LocalDate

// ---- 문구·등급 (mockup/app.js COACH, GRADE_ICON, DIR, TYPE) ----

val COACH = mapOf(
    "ok" to "기준 거리 전에 켜고 끝까지 유지했어요.",
    "short" to "조작이 끝날 때까지, 2초 이상 켜 두세요.",
    "late" to "일반도로 30m·고속도로 100m 전에 켜 주세요.",
    "missed" to "회전·차선 변경 전에는 꼭 깜빡이를 켜 주세요.",
    "danger" to "옆 차로에 차가 있었어요. 미리 켜고 한 번 더 확인해요.",
    "linger" to "조작이 끝나면 깜빡이를 꺼 주세요.",
    "caution" to "깜빡이는 잘 켰지만 옆 차로에 차가 있었어요.",
)

fun dirName(d: Dir) = if (d == Dir.LEFT) "좌측" else "우측"
fun tone(v: Verdict) = if (v.caution) "caution" else v.grade.name.lowercase()
fun toneLabel(v: Verdict) = if (v.caution) "정상·주의" else v.grade.label
fun maneuverName(v: Verdict) = when (v.maneuverType) {
    null -> "${dirName(v.dir)} 깜빡이 켜둠"
    ManeuverType.LANE -> "${dirName(v.dir)} 차선 변경"
    ManeuverType.TURN -> "${dirName(v.dir)} 회전"
}

/** JS 숫자처럼 정수면 소수점 없이 */
fun Double.f(): String = if (this % 1.0 == 0.0) toLong().toString() else "%.1f".format(this)

fun dayLabel(date: String): String =
    runCatching { LocalDate.parse(date).let { "${it.monthValue}/${it.dayOfMonth}" } }.getOrDefault(date)

@DrawableRes
fun gradeIcon(tone: String): Int = when (tone) {
    "ok" -> R.drawable.pleos_checkcircle
    "short", "late" -> R.drawable.pleos_clock_line
    "missed" -> R.drawable.pleos_annotationalert
    "danger" -> R.drawable.pleos_annotationalerttri
    "linger" -> R.drawable.pleos_arrowright
    else -> R.drawable.pleos_annotationinfo
}

// ---- 기본 부품 ----

/** 글자 크기는 목업 cqw 단위 (기본 1.15 = 본문) */
@Composable
fun Txt(
    text: String, size: Float = 1.15f, color: Color = pal.ink, weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier, align: TextAlign? = null,
) = Text(text, modifier, color = color, fontSize = cs(size), fontWeight = weight, textAlign = align, lineHeight = cs(size * 1.4f))

@Composable
fun PIcon(@DrawableRes id: Int, size: Float, tint: Color = pal.ink, modifier: Modifier = Modifier, contentDescription: String? = null) =
    Icon(painterResource(id), contentDescription, modifier.size(cq(size)), tint = tint)

@Composable
fun rememberBlink(): Boolean {
    var on by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { while (true) { delay(333); on = !on } }
    return on
}

@Composable
fun Modifier.card(): Modifier = this
    .shadow(2.dp, RoundedCornerShape(cq(1.4f)))
    .background(pal.surface, RoundedCornerShape(cq(1.4f)))
    .padding(cq(1.4f))

@Composable
fun Badge(text: String, tone: String, @DrawableRes icon: Int? = null) {
    val c = pal.grade(tone)
    Row(
        Modifier.clip(CircleShape).background(c.copy(alpha = 0.16f)).padding(horizontal = cq(0.7f), vertical = cq(0.15f)),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(cq(0.35f)),
    ) {
        if (icon != null) PIcon(icon, 1.25f, c)
        Txt(text, 1f, c, FontWeight.Bold)
    }
}

@Composable
fun AppButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, @DrawableRes icon: Int? = null, label: String? = null) {
    val p = pal
    val shape = RoundedCornerShape(cq(0.9f))
    val fg = if (primary) p.btnInk else p.ink
    Row(
        modifier.clip(shape).background(if (primary) p.btnBg else Color.Transparent)
            .border(1.5.dp, if (primary) p.btnBg else p.outline, shape).clickable(onClick = onClick).semantics { if (label != null) contentDescription = label }
            .padding(horizontal = cq(1.1f), vertical = cq(0.45f)),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(cq(0.4f), Alignment.CenterHorizontally),
    ) {
        if (icon != null) PIcon(icon, 1.3f, fg)
        Txt(text, 1.1f, fg, FontWeight.Bold)
    }
}

/** 4버튼 세그먼트 등 */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, equal: Boolean = false, activeBg: Color = pal.btnBg, activeInk: Color = pal.btnInk) {
    val p = pal
    Row(modifier.clip(RoundedCornerShape(cq(1f))).background(p.surface2).padding(cq(0.3f)), horizontalArrangement = Arrangement.spacedBy(cq(0.2f))) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                (if (equal) Modifier.weight(1f) else Modifier).clip(RoundedCornerShape(cq(0.75f))).background(if (on) activeBg else Color.Transparent)
                    .clickable { onSelect(i) }.padding(horizontal = cq(1f), vertical = cq(0.5f)),
                contentAlignment = Alignment.Center,
            ) { Txt(o, 1.1f, if (on) activeInk else p.muted, if (on) FontWeight.Bold else FontWeight.Normal) }
        }
    }
}

@Composable
fun Empty(@DrawableRes icon: Int, text: String) {
    Column(Modifier.fillMaxSize(), Arrangement.spacedBy(cq(1f), Alignment.CenterVertically), Alignment.CenterHorizontally) {
        PIcon(icon, 5f, pal.muted)
        Txt(text, color = pal.muted, align = TextAlign.Center)
    }
}

@Composable
fun LockScreen() {
    Column(Modifier.fillMaxSize(), Arrangement.spacedBy(cq(1f), Alignment.CenterVertically), Alignment.CenterHorizontally) {
        PIcon(R.drawable.pleos_lock, 5f, pal.muted)
        Txt("주차 후 확인할 수 있어요", 2f, pal.ink, FontWeight.Bold)
        Txt("주행 중에는 주행 화면과 알림만 동작해요.", color = pal.muted)
    }
}

// ---- 점수 링·등급 배지·판정 행 ----

@Composable
fun ScoreRing(score: Double?, text: String, label: String) {
    val p = pal
    val color = if (score == null) p.muted else if (score >= 95) p.ok else if (score >= 90) p.short else p.missed
    Box(Modifier.size(cq(14f)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = size.width * 0.08f
            val tl = Offset(sw / 2, sw / 2)
            val sz = Size(size.width - sw, size.height - sw)
            drawArc(p.surface2, 0f, 360f, false, tl, sz, style = Stroke(sw))
            if (score != null) drawArc(color, -90f, 3.6f * score.toFloat(), false, tl, sz, style = Stroke(sw))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(text, 5f, p.ink, FontWeight.ExtraBold)
            Txt(label, 0.95f, p.muted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GradeCounts(grades: Map<Grade, Int>, cautions: Int) {
    val order = listOf(Grade.OK, Grade.SHORT, Grade.LATE, Grade.MISSED, Grade.DANGER, Grade.LINGER)
    val shown = order.filter { (grades[it] ?: 0) > 0 }
    if (shown.isEmpty() && cautions == 0) { Txt("판정 없음", color = pal.muted); return }
    FlowRow(Modifier.fillMaxWidth(), Arrangement.spacedBy(cq(0.5f), Alignment.CenterHorizontally), Arrangement.spacedBy(cq(0.4f))) {
        shown.forEach { Badge("${it.label} ${grades[it]}", it.name.lowercase()) }
        if (cautions > 0) Badge("주의 $cautions", "caution")
    }
}

@Composable
fun VerdictRow(v: Verdict, onClick: (() -> Unit)?) {
    val p = pal
    val t = tone(v)
    val shape = RoundedCornerShape(cq(1.2f))
    Row(
        Modifier.fillMaxWidth().shadow(2.dp, shape).background(p.surface, shape).clip(shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = cq(1.3f), vertical = cq(1f)),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(cq(1.1f)),
    ) {
        Badge(toneLabel(v), t, gradeIcon(t))
        Column(Modifier.weight(1f)) {
            val what = maneuverName(v) + if (v.estimated && v.maneuverType != null) " (추정)" else ""
            Txt(
                what + if (v.maneuverType != null) "  · ${v.onDistanceM.f()}m / 기준 ${v.requiredM.f()}m · ${v.onDurationSec.f()}s" else "",
                weight = FontWeight.Bold,
            )
            Txt(COACH.getValue(t), 1f, p.muted)
        }
        Txt(if (v.penalty != 0) "−${v.penalty}" else "0", 1.15f, p.grade(t), FontWeight.ExtraBold)
        if (onClick != null) PIcon(R.drawable.pleos_chevronright, 1.4f, p.muted) else Box(Modifier.alpha(0f))
    }
}
