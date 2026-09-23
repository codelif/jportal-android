package `in`.codelif.jportal.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.domain.GpaPoint
import `in`.codelif.jportal.ui.theme.LocalExtraColors
import kotlin.math.abs
import kotlin.math.roundToInt

/** smooth-ish line through points using midpoint quadratics, no overshoot */
private fun smooth(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        val p = points[i - 1]
        val c = points[i]
        val mid = Offset((p.x + c.x) / 2, (p.y + c.y) / 2)
        quadraticTo(p.x, p.y, mid.x, mid.y)
        if (i == points.lastIndex) lineTo(c.x, c.y)
    }
    if (points.size == 1) lineTo(points[0].x, points[0].y)
}

/** running attendance % over the semester with the goal as a dashed line */
@Composable
fun TrendChart(values: List<Float>, target: Int, modifier: Modifier = Modifier) {
    if (values.size < 2) return
    val color = MaterialTheme.colorScheme.primary
    val goal = MaterialTheme.colorScheme.outline
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(values) { reveal.snapTo(0f); reveal.animateTo(1f, tween(900)) }
    val last = values.last().roundToInt()
    Canvas(
        modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = "Attendance trend, now $last percent, goal $target percent" },
    ) {
        val lo = minOf(values.min(), target.toFloat()) - 5f
        val hi = 100f
        fun y(v: Float) = size.height - (v - lo) / (hi - lo) * size.height
        val step = size.width / (values.size - 1)
        val pts = values.mapIndexed { i, v -> Offset(i * step, y(v)) }
        val shown = pts.take((pts.size * reveal.value).toInt().coerceAtLeast(2))
        val gy = y(target.toFloat())
        drawLine(goal, Offset(0f, gy), Offset(size.width, gy), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
        val line = smooth(shown)
        val fill = Path().apply {
            addPath(line)
            lineTo(shown.last().x, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
        drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, 5.dp.toPx(), shown.last())
    }
}

/**
 * sgpa (green) and cgpa (blue) per semester, the jportal chart. semesters
 * without results yet are ghosts you can drag up and down to see where your
 * cgpa would land.
 */
@Composable
fun GpaChart(
    points: List<GpaPoint>,
    futureSlots: List<Int>,
    onProject: (semester: Int, sgpa: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extra = LocalExtraColors.current
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val label = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant)
    val haptics = LocalHapticFeedback.current
    val project by rememberUpdatedState(onProject)
    // every drag step changes points, the gesture handlers must outlive that or the list steals the drag
    val pts by rememberUpdatedState(points)
    val slots by rememberUpdatedState(futureSlots)
    var dragging by remember { mutableStateOf<Int?>(null) }
    val allSems = (points.map { it.semester } + futureSlots).distinct().sorted()
    if (allSems.isEmpty()) return
    val lo = 4.0
    val hi = 10.0

    Canvas(
        modifier.fillMaxWidth().height(220.dp)
            .semantics { contentDescription = "SGPA and CGPA by semester. Latest CGPA ${points.lastOrNull { !it.projected }?.cgpa ?: 0.0}" }
            .pointerInput(Unit) {
                val pad = 28.dp.toPx()
                fun sems() = (pts.map { it.semester } + slots).distinct().sorted()
                fun semAt(x: Float): Int? {
                    val all = sems()
                    val step = (size.width - 2 * pad) / (all.size - 1).coerceAtLeast(1)
                    return all.indices.minByOrNull { abs(pad + it * step - x) }?.let { all[it] }
                }
                fun valueAt(yPx: Float): Double {
                    val top = 16.dp.toPx()
                    val bottom = size.height - 24.dp.toPx()
                    val v = hi - (yPx - top) / (bottom - top) * (hi - lo)
                    return (v.coerceIn(lo, hi) * 10).roundToInt() / 10.0
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // only future semesters are draggable, anywhere else the page scrolls as usual
                    val s = semAt(down.position.x)?.takeIf { s -> s in slots || pts.any { it.semester == s && it.projected } } ?: return@awaitEachGesture
                    val slop = awaitVerticalTouchSlopOrCancellation(down.id) { c, _ -> c.consume() } ?: return@awaitEachGesture
                    dragging = s
                    fun move(y: Float) {
                        val v = valueAt(y)
                        val old = pts.firstOrNull { it.semester == s }?.sgpa
                        if (old == null || (old * 2).roundToInt() != (v * 2).roundToInt()) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        project(s, v)
                    }
                    move(slop.position.y)
                    verticalDrag(slop.id) { c -> c.consume(); move(c.position.y) }
                    dragging = null
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val pad = 28.dp.toPx()
                    val all = (pts.map { it.semester } + slots).distinct().sorted()
                    val step = (size.width - 2 * pad) / (all.size - 1).coerceAtLeast(1)
                    val s = all.indices.minByOrNull { abs(pad + it * step - o.x) }?.let { all[it] } ?: return@detectTapGestures
                    if (s in slots && pts.none { it.semester == s }) {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        project(s, pts.lastOrNull()?.sgpa ?: 8.0)
                    }
                }
            },
    ) {
        val pad = 28.dp.toPx()
        val top = 16.dp.toPx()
        val bottom = size.height - 24.dp.toPx()
        fun x(sem: Int) = pad + allSems.indexOf(sem) * (size.width - 2 * pad) / (allSems.size - 1).coerceAtLeast(1)
        fun y(v: Double) = (bottom - (v - lo) / (hi - lo) * (bottom - top)).toFloat()

        for (g in 5..10) {
            drawLine(scheme.outlineVariant.copy(alpha = 0.4f), Offset(pad, y(g.toDouble())), Offset(size.width - pad, y(g.toDouble())), 1f)
            drawLabel(measurer, "$g", Offset(0f, y(g.toDouble()) - 7.dp.toPx()), label)
        }
        allSems.forEach { s -> drawLabel(measurer, "S$s", Offset(x(s) - 7.dp.toPx(), bottom + 6.dp.toPx()), label) }
        // empty future slots: faint targets you can tap
        futureSlots.filter { s -> points.none { it.semester == s } }.forEach { s ->
            drawCircle(scheme.outlineVariant, 6.dp.toPx(), Offset(x(s), y(8.0)), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
        }
        series(points.map { Offset(x(it.semester), y(it.cgpa)) }, points.map { it.projected }, extra.cgpa)
        series(points.map { Offset(x(it.semester), y(it.sgpa)) }, points.map { it.projected }, extra.sgpa)
        dragging?.let { s ->
            points.firstOrNull { it.semester == s }?.let { p ->
                drawLabel(measurer, "%.1f".format(p.sgpa), Offset(x(s) - 10.dp.toPx(), y(p.sgpa) - 26.dp.toPx()), label.copy(color = extra.sgpa))
            }
        }
    }
}

private fun DrawScope.series(pts: List<Offset>, projected: List<Boolean>, color: Color) {
    if (pts.isEmpty()) return
    val realEnd = projected.indexOfFirst { it }.let { if (it < 0) pts.size else it }
    val real = pts.take(realEnd)
    if (real.size > 1) drawPath(smooth(real), color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    if (realEnd < pts.size) {
        val ghost = pts.subList((realEnd - 1).coerceAtLeast(0), pts.size)
        drawPath(smooth(ghost), color.copy(alpha = 0.6f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
    }
    pts.forEachIndexed { i, p ->
        if (projected[i]) {
            drawCircle(color, 7.dp.toPx(), p, style = Stroke(2.5.dp.toPx()))
        } else {
            drawCircle(color, 4.5.dp.toPx(), p)
        }
    }
}

private fun DrawScope.drawLabel(m: TextMeasurer, text: String, at: Offset, style: androidx.compose.ui.text.TextStyle) {
    drawText(m, text, at, style)
}
