package dev.ijemwohajo.signalsupervisor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** mockup/styles.css 의 다크·라이트 토큰 값을 그대로 옮긴 팔레트 */
@Immutable
class Pal(
    val dark: Boolean,
    val appBg: Color, val surface: Color, val surface2: Color, val appLine: Color,
    val ink: Color, val muted: Color, val btnBg: Color, val btnInk: Color, val outline: Color,
    val notiBg: Color, val notiInk: Color, val notiMuted: Color,
    val signal: Color, val ok: Color, val short: Color, val late: Color, val missed: Color,
    val danger: Color, val linger: Color, val caution: Color,
) {
    /** 등급 이름 소문자(ok·short·late·missed·danger·linger) 또는 caution */
    fun grade(tone: String): Color = when (tone) {
        "ok" -> ok; "short" -> short; "late" -> late; "missed" -> missed
        "danger" -> danger; "linger" -> linger; "caution" -> caution; else -> ink
    }
}

val DarkPal = Pal(
    true,
    Color(0xFF1C1C1E), Color(0xFF2C2C2E), Color(0xFF3A3A3C), Color(0xFF3F3F42),
    Color(0xFFF2F2F4), Color(0xFFA3A3A8), Color(0xFFE8E8EA), Color(0xFF131417), Color(0xFF8E8E93),
    Color(0xFF2C2C2E), Color(0xFFF2F2F4), Color(0xFFC7C7CC),
    Color(0xFFC99A4E), Color(0xFF9AAB6E), Color(0xFFCDB886), Color(0xFFC69048), Color(0xFFCF8660),
    Color(0xFFCD5F4F), Color(0xFF8EA3B8), Color(0xFFA894B0),
)

val LightPal = Pal(
    false,
    Color(0xFFF4F4F6), Color(0xFFFFFFFF), Color(0xFFECECEF), Color(0xFFDCDCE0),
    Color(0xFF1B1C1E), Color(0xFF626268), Color(0xFF131417), Color(0xFFFFFFFF), Color(0xFF8E8E93),
    Color(0xFFFFFFFF), Color(0xFF1B1C1E), Color(0xFF4A4A4F),
    Color(0xFFA06E28), Color(0xFF5B6D38), Color(0xFF86703C), Color(0xFF95601F), Color(0xFFA65736),
    Color(0xFF96392D), Color(0xFF4F6982), Color(0xFF725F84),
)

val LocalPal = staticCompositionLocalOf { DarkPal }

/** mockup 의 cqw 에 해당하는 길이(dp). 앱 창 너비 / 66.65, 9~20dp 로 제한 */
val LocalU = staticCompositionLocalOf { 16f }

val pal: Pal @Composable get() = LocalPal.current

/** 목업 cqw 값을 dp 로 */
@Composable fun cq(x: Float): Dp = (LocalU.current * x).dp

/**
 * 모서리 R값 토큰(cq 단위). 앱 전체가 이 4단계만 쓴다.
 * 안쪽 요소 R = 바깥 R − 안쪽 여백(0.3): M(1.0) 그룹 안의 선택 칸은 S(0.7).
 * XS 작은 표식 · S 선택 칸 · M 버튼·타일·영상 · L 카드·패널·알림 카드
 */
enum class Corner(val u: Float) { XS(0.2f), S(0.7f), M(1f), L(1.4f) }

@Composable fun corner(c: Corner) = androidx.compose.foundation.shape.RoundedCornerShape(cq(c.u))

/** 목업 cqw 글자 크기를 sp 로 */
@Composable fun cs(x: Float): TextUnit = (LocalU.current * x).sp

@Composable
fun SignalTheme(dark: Boolean, u: Float, content: @Composable () -> Unit) {
    val p = if (dark) DarkPal else LightPal
    val scheme = if (dark) darkColorScheme(background = p.appBg, surface = p.surface, onSurface = p.ink, primary = p.btnBg, onPrimary = p.btnInk)
    else lightColorScheme(background = p.appBg, surface = p.surface, onSurface = p.ink, primary = p.btnBg, onPrimary = p.btnInk)
    CompositionLocalProvider(LocalPal provides p, LocalU provides u) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
