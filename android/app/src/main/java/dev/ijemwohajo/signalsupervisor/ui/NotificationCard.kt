package dev.ijemwohajo.signalsupervisor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ijemwohajo.signalsupervisor.app.NotificationCard
import dev.ijemwohajo.signalsupervisor.ui.theme.cq
import dev.ijemwohajo.signalsupervisor.ui.theme.pal

/** Pleos 시스템 알림 카드 모양. 표시 시간(2.5초)은 AppGraph.cards 가 정한다. */
@Composable
fun NotificationCardView(card: NotificationCard, width: Dp) {
    val p = pal
    val shape = RoundedCornerShape(8.dp)
    val state = remember(card.id) { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(state, enter = slideInVertically(tween(150)) { -it / 2 } + fadeIn(tween(100))) {
        Column(
            Modifier.width(width).shadow(8.dp, shape).background(p.notiBg, shape).border(1.dp, p.appLine, shape)
                .padding(horizontal = cq(1.8f), vertical = cq(1.5f)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(cq(0.6f))) {
                PIcon(gradeIcon(card.tone), 1.6f, p.grade(card.tone))
                Txt("시그널 감독관", 1.35f, p.notiInk, FontWeight.Bold)
            }
            Txt(card.text, 1.15f, p.notiMuted, modifier = Modifier.padding(top = cq(0.6f)))
        }
    }
}
