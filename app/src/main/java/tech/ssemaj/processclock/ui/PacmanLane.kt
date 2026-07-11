package tech.ssemaj.processclock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import tech.ssemaj.processclock.ui.theme.BlinkyRed
import tech.ssemaj.processclock.ui.theme.DotPeach
import tech.ssemaj.processclock.ui.theme.PacYellow
import tech.ssemaj.processclock.ui.theme.PinkyPink

/**
 * One lap of the maze corridor = one minute of wall-clock time.
 *
 * Pac-Man's position, his mouth, and every dot are pure functions of
 * [secondsOfMinute] — the lane only ever moves when a kernel tick lands.
 * There is deliberately no continuous animation here: if Pac-Man chomps,
 * a timerfd fired on the boundary. A frozen Pac-Man means no ticks.
 */
@Composable
fun PacmanLane(
    secondsOfMinute: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        val margin = 24.dp.toPx()
        val laneWidth = size.width - 2 * margin
        val cy = size.height / 2

        val progress = secondsOfMinute / 60f
        val pacX = margin + progress * laneWidth
        val pacR = 14.dp.toPx()

        // Dots ahead of Pac-Man; the ones behind him are eaten this minute.
        val dotCount = 30
        for (i in 0 until dotCount) {
            val dx = margin + (i + 0.5f) / dotCount * laneWidth
            if (dx > pacX + pacR) {
                drawCircle(DotPeach, radius = 3.dp.toPx(), center = Offset(dx, cy))
            }
        }

        // Mouth open on even seconds, shut on odd — a 0.5 Hz chomp driven
        // purely by tick arrival.
        val mouthHalf = if (secondsOfMinute % 2 == 0) 32f else 6f
        drawArc(
            color = PacYellow,
            startAngle = mouthHalf,
            sweepAngle = 360f - 2 * mouthHalf,
            useCenter = true,
            topLeft = Offset(pacX - pacR, cy - pacR),
            size = androidx.compose.ui.geometry.Size(pacR * 2, pacR * 2),
        )

        // Two ghosts in pursuit; they trail in from the left as the minute runs.
        drawGhost(BlinkyRed, x = pacX - 4.2f * pacR, cy = cy, r = pacR * 0.9f, margin = margin)
        drawGhost(PinkyPink, x = pacX - 6.6f * pacR, cy = cy, r = pacR * 0.9f, margin = margin)
    }
}

private fun DrawScope.drawGhost(color: Color, x: Float, cy: Float, r: Float, margin: Float) {
    if (x < margin) return
    val top = cy - r
    val bottom = cy + r
    val body = Path().apply {
        moveTo(x - r, bottom)
        lineTo(x - r, cy)
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(x - r, top, x + r, cy + r),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 180f,
            forceMoveTo = false,
        )
        lineTo(x + r, bottom)
        // Classic three-point skirt.
        val w = r / 1.5f
        lineTo(x + r - w * 0.5f, bottom - w * 0.6f)
        lineTo(x + r - w * 1.0f, bottom)
        lineTo(x + r - w * 1.5f, bottom - w * 0.6f)
        lineTo(x + r - w * 2.0f, bottom)
        lineTo(x + r - w * 2.5f, bottom - w * 0.6f)
        close()
    }
    drawPath(body, color)
    val eyeY = cy - r * 0.25f
    val eyeR = r * 0.26f
    for (ex in listOf(x - r * 0.4f, x + r * 0.4f)) {
        drawCircle(Color.White, radius = eyeR, center = Offset(ex, eyeY))
        drawCircle(Color(0xFF1A1AFF), radius = eyeR * 0.55f, center = Offset(ex + eyeR * 0.4f, eyeY))
    }
}
