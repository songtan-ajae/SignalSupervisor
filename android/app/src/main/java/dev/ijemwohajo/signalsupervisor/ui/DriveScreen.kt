package dev.ijemwohajo.signalsupervisor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ijemwohajo.signalsupervisor.R
import dev.ijemwohajo.signalsupervisor.app.LiveState
import dev.ijemwohajo.signalsupervisor.core.Dir
import dev.ijemwohajo.signalsupervisor.core.Road
import dev.ijemwohajo.signalsupervisor.core.Signal
import dev.ijemwohajo.signalsupervisor.ui.theme.cq
import dev.ijemwohajo.signalsupervisor.ui.theme.pal
import kotlin.math.ceil

@Composable
fun DriveScreen(live: LiveState) {
    val st = live.state
    val blink = rememberBlink()
    val gap = cq(1.2f)
    Row(Modifier.fillMaxSize(), Arrangement.spacedBy(gap)) {
        SidePanel(Dir.LEFT, st.signal == Signal.LEFT || st.signal == Signal.HAZARD, blink, st.bsmSupported, st.bsmL, Modifier.weight(1f))
        Column(Modifier.weight(1.3f).fillMaxHeight(), Arrangement.spacedBy(gap)) {
            SpeedCard(st.speed, st.road, Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(gap)) {
                val stop = if (live.tripActive && st.speed == 0) "${ceil(live.stopRemainingSec).toInt()}초" else "-"
                Stat((if (live.tripActive) live.tripEvents else 0).toString(), "이번 운행 판정", Modifier.weight(1f))
                Stat(stop, "정산까지 정차", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(cq(0.6f))) {
                Source("카메라 차선", live.tripActive || st.speed > 0)
                Source("내비", st.naviTurnNear)
                Source("핸들", true)
            }
        }
        SidePanel(Dir.RIGHT, st.signal == Signal.RIGHT || st.signal == Signal.HAZARD, blink, st.bsmSupported, st.bsmR, Modifier.weight(1f))
    }
}

@Composable
private fun SidePanel(dir: Dir, signalOn: Boolean, blink: Boolean, bsmSupported: Boolean, bsm: Boolean, modifier: Modifier) {
    val p = pal
    val left = dir == Dir.LEFT
    val warn = bsmSupported && bsm
    val shape = RoundedCornerShape(cq(1.2f))
    Column(modifier.fillMaxHeight().card(), Arrangement.SpaceBetween, Alignment.CenterHorizontally) {
        PIcon(
            if (left) R.drawable.pleos_arrowleft else R.drawable.pleos_arrowright, 6f,
            if (signalOn) p.signal else p.muted, Modifier.alpha(if (signalOn && blink) 1f else 0.25f),
            if (left) "좌측 깜빡이" else "우측 깜빡이",
        )
        Column(
            Modifier.fillMaxWidth().clip(shape)
                .background(if (warn) p.danger.copy(alpha = 0.14f).compositeOver(p.surface2) else p.surface2)
                .border(2.dp, if (warn) p.danger else androidx.compose.ui.graphics.Color.Transparent, shape)
                .alpha(if (bsmSupported) 1f else 0.5f)
                .padding(horizontal = cq(0.6f), vertical = cq(1.2f)),
            Arrangement.spacedBy(cq(0.5f)), Alignment.CenterHorizontally,
        ) {
            val c = if (warn) p.danger else p.muted
            PIcon(if (left) R.drawable.pleos_sidemirrorleft else R.drawable.pleos_sidemirrorright, 3.2f, c)
            val side = if (left) "좌" else "우"
            Txt(
                if (!bsmSupported) "BSM 미지원" else if (bsm) "${side}측 접근 차량" else "BSM $side · 이상 없음",
                1.05f, c, if (warn) FontWeight.Bold else FontWeight.Normal, align = TextAlign.Center,
            )
        }
        Txt(if (left) "좌측" else "우측", 1.1f, p.muted)
    }
}

@Composable
private fun SpeedCard(speed: Int, road: Road, modifier: Modifier) {
    val p = pal
    Column(modifier.fillMaxWidth().card(), Arrangement.Center, Alignment.CenterHorizontally) {
        Txt(speed.toString(), 7.5f, p.ink, FontWeight.ExtraBold)
        Txt("km/h", 1.2f, p.muted)
        Txt(
            if (road == Road.HIGHWAY) "고속도로 · 기준 100m" else "일반도로 · 기준 30m", 1f,
            modifier = Modifier.padding(top = cq(0.8f)).clip(CircleShape).background(p.surface2).padding(horizontal = cq(0.8f), vertical = cq(0.25f)),
        )
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    val shape = RoundedCornerShape(cq(1.2f))
    Column(modifier.shadow(2.dp, shape).background(pal.surface, shape).padding(horizontal = cq(1.3f), vertical = cq(1f))) {
        Txt(value, 2.6f, weight = FontWeight.Bold)
        Txt(label, 1f, pal.muted)
    }
}

@Composable
private fun Source(label: String, on: Boolean) {
    val p = pal
    Box(Modifier.clip(CircleShape).border(1.dp, if (on) p.outline else p.appLine, CircleShape).padding(horizontal = cq(0.8f), vertical = cq(0.25f))) {
        Txt(label, 1f, if (on) p.ink else p.muted)
    }
}
