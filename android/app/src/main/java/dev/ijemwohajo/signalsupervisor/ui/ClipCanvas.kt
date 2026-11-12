package dev.ijemwohajo.signalsupervisor.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import dev.ijemwohajo.signalsupervisor.core.Dir
import dev.ijemwohajo.signalsupervisor.core.Frame
import dev.ijemwohajo.signalsupervisor.core.Road
import dev.ijemwohajo.signalsupervisor.core.Signal

// mockup/app.js drawFront / drawSide 이식. 목업 캔버스는 480x270 이라 고정 픽셀 값은 k = 너비/480 으로 줄인다.

private fun blinkOn(fr: Frame) = Math.floor(fr.t / 0.33).toInt() % 2 == 0

private fun DrawScope.text(s: String, x: Float, y: Float, px: Float, color: Color) = drawIntoCanvas {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb(); textSize = px; typeface = Typeface.DEFAULT_BOLD }
    it.nativeCanvas.drawText(s, x, y, paint)
}

fun DrawScope.drawFront(fr: Frame, laneX: Float) {
    val w = size.width
    val h = size.height
    val k = w / 480f
    drawRect(Brush.verticalGradient(listOf(Color(0xFF3A4250), Color(0xFF6B7280)), 0f, h * 0.45f), size = size)
    drawPath(Path().apply {
        moveTo(w * 0.46f, h * 0.42f); lineTo(w * 0.54f, h * 0.42f); lineTo(w * 1.2f, h); lineTo(-w * 0.2f, h); close()
    }, Color(0xFF3A3631))
    val phase = ((fr.odo / 6) % 1).toFloat()
    for (lane in -2..3) {
        val off = lane - 0.5f - laneX
        for (s in 0 until 8) {
            val z0 = (s + phase) / 8
            val z1 = (s + phase + 0.45f) / 8
            val y0 = h * 0.42f + h * 0.58f * z0 * z0
            val y1 = h * 0.42f + h * 0.58f * z1 * z1
            val x0 = w / 2 + off * (w * 0.06f + w * 0.55f * z0 * z0)
            val x1 = w / 2 + off * (w * 0.06f + w * 0.55f * z1 * z1)
            drawLine(Color(0xD9EEE8DE), Offset(x0, y0), Offset(x1, y1), (1 + 5 * z1) * k)
        }
    }
    drawRect(Color(0xFF7D7368), Offset(w / 2 - 22 * k - laneX * 40 * k, h * 0.5f), Size(44 * k, 26 * k))
    drawRect(Color(0x80000000), Offset(0f, h - 34 * k), Size(w, 34 * k))
    val s = fr.state
    text("${s.speed} km/h", 12 * k, h - 11 * k, 16 * k, Color.White)
    text(if (s.road == Road.HIGHWAY) "고속도로" else "일반도로", 110 * k, h - 11 * k, 16 * k, Color.White)
    val on = blinkOn(fr)
    val lit = if (on) Color(0xFFC99A4E) else Color(0xFF5A4A30)
    val off = Color(0xFF4A453E)
    text("◀", w - 76 * k, h - 9 * k, 22 * k, if (s.signal == Signal.LEFT || s.signal == Signal.HAZARD) lit else off)
    text("▶", w - 40 * k, h - 9 * k, 22 * k, if (s.signal == Signal.RIGHT || s.signal == Signal.HAZARD) lit else off)
    if (fr.laneCross != null) text("차선 넘김", w / 2 - 30 * k, 24 * k, 14 * k, Color(0xE6FFFFFF))
}

fun DrawScope.drawSide(fr: Frame, dir: Dir) {
    val w = size.width
    val h = size.height
    val k = w / 480f
    val left = dir == Dir.LEFT
    drawRect(Color(0xFF1A1714), size = size)
    val oval = Path().apply { addOval(Rect(w / 2 - w * 0.46f, h / 2 - h * 0.42f, w / 2 + w * 0.46f, h / 2 + h * 0.42f)) }
    val bsm = if (left) fr.state.bsmL else fr.state.bsmR
    clipPath(oval) {
        drawRect(Color(0xFF6B7280), size = size)
        drawRect(Color(0xFF3A3631), Offset(0f, h * 0.45f), Size(w, h))
        val mirror = if (left) 1f else -1f
        val vpX = if (left) w * 0.8f else w * 0.2f
        drawLine(
            Color(0xCCEEE8DE), Offset(vpX, h * 0.45f), Offset(w / 2 - mirror * w * 0.05f, h), 3 * k,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(24 * k, 18 * k), ((fr.odo * 4) % 42).toFloat() * k),
        )
        drawRect(Color(0xFF5E554B), Offset(if (left) w * 0.86f else 0f, h * 0.3f), Size(w * 0.14f, h * 0.7f))
        if (bsm) {
            val cx = if (left) w * 0.42f else w * 0.58f
            drawRect(Color(0xFFB9AD9C), Offset(cx - 55 * k, h * 0.5f), Size(110 * k, 64 * k))
            drawRect(Color(0xFF2A2621), Offset(cx - 45 * k, h * 0.53f), Size(90 * k, 22 * k))
        }
    }
    if (bsm) {
        val bx = if (left) w * 0.82f else w * 0.18f
        drawPath(Path().apply {
            moveTo(bx, h * 0.16f); lineTo(bx + 16 * k, h * 0.16f + 28 * k); lineTo(bx - 16 * k, h * 0.16f + 28 * k); close()
        }, Color(0xFFCD5F4F))
        text("BSM", bx - 14 * k, h * 0.16f + 45 * k, 13 * k, Color.White)
    }
}
