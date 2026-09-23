package `in`.codelif.jportal.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.ui.theme.LocalExtraColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * jportal's attendance ring, grown up. the arc is wavy while you're safely
 * above target and flattens out once you're below it, so the shape says it
 * before the number does. a small notch marks the target on the track.
 */
@Composable
fun AttendanceRing(
    percent: Float,
    target: Int,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    stroke: Dp = 6.dp,
    animate: Boolean = true,
    content: @Composable () -> Unit = {},
) {
    val extra = LocalExtraColors.current
    val scheme = MaterialTheme.colorScheme
    val safe = percent >= target
    val color = when {
        safe -> scheme.primary
        percent >= target - 10 -> extra.warn
        else -> scheme.error
    }

    val sweep = remember { Animatable(0f) }
    LaunchedEffect(percent) {
        sweep.animateTo(percent.coerceIn(0f, 100f) / 100f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessVeryLow))
    }
    val amp = remember { Animatable(0f) }
    LaunchedEffect(safe) { amp.animateTo(if (safe) 1f else 0f, tween(600)) }

    val phase = if (animate && safe) {
        val t = rememberInfiniteTransition(label = "wave")
        val p by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "phase")
        p
    } else 0f

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            drawRing(sweep.value, target / 100f, amp.value, phase, color, scheme.surfaceContainerHighest, scheme.onSurfaceVariant, stroke.toPx())
        }
        content()
    }
}

private fun DrawScope.drawRing(
    fraction: Float,
    target: Float,
    amplitude: Float,
    phase: Float,
    color: Color,
    track: Color,
    notch: Color,
    strokePx: Float,
) {
    val waveAmp = strokePx * 0.24f * amplitude
    val r = min(size.width, size.height) / 2f - strokePx / 2f - waveAmp
    val cx = size.width / 2f
    val cy = size.height / 2f
    // gap between arc ends and the track, in radians, scaled to stroke so it reads at every size
    val gap = (strokePx * 1.6f / r).coerceAtMost(0.5f)
    val start = -PI.toFloat() / 2f
    val sweep = fraction * 2f * PI.toFloat()
    val cap = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // track: the rest of the circle, flat
    if (fraction < 0.999f) {
        val trackStart = start + sweep + if (fraction > 0.001f) gap else 0f
        val trackEnd = start + 2f * PI.toFloat() - if (fraction > 0.001f) gap else 0f
        if (trackEnd > trackStart) {
            drawArc(
                track, Math.toDegrees(trackStart.toDouble()).toFloat(), Math.toDegrees((trackEnd - trackStart).toDouble()).toFloat(),
                false, topLeft = androidx.compose.ui.geometry.Offset(cx - r, cy - r),
                size = androidx.compose.ui.geometry.Size(2 * r, 2 * r), style = cap,
            )
        }
    }

    // target notch, only when the arc isn't sitting on it
    val tAngle = start + target * 2f * PI.toFloat()
    if (strokePx >= 10f * density && target in 0.01f..0.99f && (tAngle > start + sweep + gap)) {
        val inner = r - strokePx * 0.15f
        val outer = r + strokePx * 0.15f
        drawLine(
            notch.copy(alpha = 0.7f),
            androidx.compose.ui.geometry.Offset(cx + inner * cos(tAngle), cy + inner * sin(tAngle)),
            androidx.compose.ui.geometry.Offset(cx + outer * cos(tAngle), cy + outer * sin(tAngle)),
            strokeWidth = strokePx * 0.35f, cap = StrokeCap.Round,
        )
    }

    if (fraction <= 0.001f) return
    // wavy progress arc, sampled; wave count grows with the ring so bumps keep a constant size
    val waves = max(8f, (2f * PI.toFloat() * r) / (strokePx * 4.6f))
    val path = Path()
    val steps = max(24, (sweep * r / 2f).toInt())
    for (i in 0..steps) {
        val a = start + sweep * i / steps
        // taper the wave to zero at both ends so caps stay round and centred
        val edge = min(1f, min(i, steps - i) / (steps * 0.08f + 1f))
        val rr = r + waveAmp * edge * sin(waves * (a - start) + phase)
        val x = cx + rr * cos(a)
        val y = cy + rr * sin(a)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = cap)
}
