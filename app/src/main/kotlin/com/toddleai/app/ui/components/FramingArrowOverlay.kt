package com.toddleai.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.toddleai.app.analysis.FramingGuidance
import com.toddleai.app.analysis.FramingHint

/**
 * Real-time camera-aiming guidance: draws a pulsing arrow toward the edge the parent should move the
 * phone (tilt up/down, pan left/right), framing brackets for distance (step back / move closer) and
 * for searching, plus a short instruction. Driven by [FramingGuidance] from the live pose pipeline.
 */
@Composable
fun FramingArrowOverlay(
    guidance: FramingGuidance,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "framing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "framingPhase",
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val alpha = 0.55f + 0.45f * phase
            val accent = Amber.copy(alpha = alpha)
            when (guidance.hint) {
                FramingHint.AIM_UP ->
                    drawArrow(Offset(0f, -1f), Offset(size.width / 2f, size.height * 0.17f), accent, phase)
                FramingHint.AIM_DOWN ->
                    drawArrow(Offset(0f, 1f), Offset(size.width / 2f, size.height * 0.71f), accent, phase)
                FramingHint.PAN_LEFT ->
                    drawArrow(Offset(-1f, 0f), Offset(size.width * 0.15f, size.height / 2f), accent, phase)
                FramingHint.PAN_RIGHT ->
                    drawArrow(Offset(1f, 0f), Offset(size.width * 0.85f, size.height / 2f), accent, phase)
                FramingHint.MOVE_BACK ->
                    drawBrackets(Offset(size.width / 2f, size.height * 0.45f), accent, expanding = true, phase = phase)
                FramingHint.MOVE_CLOSER ->
                    drawBrackets(Offset(size.width / 2f, size.height * 0.45f), accent, expanding = false, phase = phase)
                FramingHint.SEARCHING ->
                    drawBrackets(Offset(size.width / 2f, size.height * 0.45f), Amber.copy(alpha = 0.45f + 0.25f * phase), expanding = false, phase = 0.5f)
                FramingHint.NONE -> Unit
            }
        }

        Crossfade(
            targetState = guidance,
            animationSpec = tween(durationMillis = 220),
            label = "framingMessage",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 150.dp, start = 24.dp, end = 24.dp),
        ) { current ->
            if (current.message.isNotEmpty()) {
                val isGood = current.hint == FramingHint.NONE
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isGood) {
                        GoodGreen.copy(alpha = 0.92f)
                    } else {
                        Color.Black.copy(alpha = 0.62f)
                    },
                ) {
                    Text(
                        text = if (isGood) "✓ ${current.message}" else current.message,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/** Draws a bobbing double-chevron pointing along [dir] (a unit vector), centered at [base]. */
private fun DrawScope.drawArrow(
    dir: Offset,
    base: Offset,
    color: Color,
    phase: Float,
) {
    val perp = Offset(-dir.y, dir.x)
    val half = 46.dp.toPx()
    val depth = 34.dp.toPx()
    val gap = 30.dp.toPx()
    val bob = (phase - 0.5f) * 2f * 12.dp.toPx()
    val stroke = 10.dp.toPx()
    val center = Offset(base.x + dir.x * bob, base.y + dir.y * bob)

    drawChevron(center, dir, perp, half, depth, color, stroke)
    drawChevron(
        Offset(center.x - dir.x * gap, center.y - dir.y * gap),
        dir, perp, half, depth, color.copy(alpha = color.alpha * 0.55f), stroke,
    )
}

private fun DrawScope.drawChevron(
    center: Offset,
    dir: Offset,
    perp: Offset,
    half: Float,
    depth: Float,
    color: Color,
    stroke: Float,
) {
    val apex = Offset(center.x + dir.x * depth / 2f, center.y + dir.y * depth / 2f)
    val tail = depth / 2f
    val left = Offset(center.x + perp.x * half - dir.x * tail, center.y + perp.y * half - dir.y * tail)
    val right = Offset(center.x - perp.x * half - dir.x * tail, center.y - perp.y * half - dir.y * tail)
    drawLine(color, left, apex, strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color, right, apex, strokeWidth = stroke, cap = StrokeCap.Round)
}

/** Four corner brackets of a square, used for distance (step back / move closer) and searching. */
private fun DrawScope.drawBrackets(
    center: Offset,
    color: Color,
    expanding: Boolean,
    phase: Float,
) {
    val baseHalf = 70.dp.toPx()
    val amp = 16.dp.toPx()
    val half = if (expanding) baseHalf + phase * amp else baseHalf + (1f - phase) * amp
    val arm = 26.dp.toPx()
    val stroke = 9.dp.toPx()

    val l = center.x - half
    val r = center.x + half
    val t = center.y - half
    val b = center.y + half

    // Top-left
    drawLine(color, Offset(l, t), Offset(l + arm, t), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color, Offset(l, t), Offset(l, t + arm), strokeWidth = stroke, cap = StrokeCap.Round)
    // Top-right
    drawLine(color, Offset(r, t), Offset(r - arm, t), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color, Offset(r, t), Offset(r, t + arm), strokeWidth = stroke, cap = StrokeCap.Round)
    // Bottom-left
    drawLine(color, Offset(l, b), Offset(l + arm, b), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color, Offset(l, b), Offset(l, b - arm), strokeWidth = stroke, cap = StrokeCap.Round)
    // Bottom-right
    drawLine(color, Offset(r, b), Offset(r - arm, b), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color, Offset(r, b), Offset(r, b - arm), strokeWidth = stroke, cap = StrokeCap.Round)
}

private val Amber = Color(0xFFF6B73C)
private val GoodGreen = Color(0xFF4E8E66)
