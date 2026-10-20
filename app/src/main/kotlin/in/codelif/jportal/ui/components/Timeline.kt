package `in`.codelif.jportal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// a day as a timeline, shared by the exam date sheet and the planner: clock on the left, a rail through the rows

/** vertical padding of a timeline row, the rail's dot sits this far down plus half the clock's line */
val RAIL_PAD = 12.dp

private val CLOCK = DateTimeFormatter.ofPattern("h:mm")
private val TIME = DateTimeFormatter.ofPattern("h:mm a")
private val MERIDIEM = DateTimeFormatter.ofPattern("a")
private val WEEKDAY = DateTimeFormatter.ofPattern("EEEEE")

/** every row gets the widest clock's width, or the rail jogs sideways from card to card */
@Composable
fun rememberClockWidth(): Dp {
    val bold = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
    val small = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(bold, small, density) {
        val start = measurer.measure(face(WIDEST, small), bold).size.width
        val end = measurer.measure(until(LocalTime.MIDNIGHT, WIDEST.plusHours(12)), small).size.width
        val px = maxOf(start, end)
        with(density) { px.toDp() }
    }
}

// sans flex digits aren't tabular, 0 and A are its widest, so this beats 12:00 PM. ClockTest holds it to that
internal val WIDEST: LocalTime = LocalTime.of(10, 0)

/** the start as drawn, a big h:mm and a small am/pm */
internal fun face(start: LocalTime, small: TextStyle) = buildAnnotatedString {
    append(start.format(CLOCK))
    withStyle(SpanStyle(fontSize = small.fontSize, fontWeight = small.fontWeight)) { append(" " + start.format(MERIDIEM).uppercase()) }
}

/** across noon the end needs its own am/pm */
internal fun until(start: LocalTime, end: LocalTime) =
    "to " + (if ((start.hour < 12) == (end.hour < 12)) end.format(CLOCK) else end.format(TIME))

/** big start over a small end. [active] off drops the weight, for rows the planner's dot isn't on */
@Composable
fun Clock(start: LocalTime, end: LocalTime?, active: Boolean = true) {
    val small = MaterialTheme.typography.labelMedium
    val big = MaterialTheme.typography.titleLarge.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
    Text(
        face(start, small),
        style = big,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
    )
    if (end != null) {
        Text(
            until(start, end),
            style = if (active) small else small.copy(fontWeight = FontWeight.Normal),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * the rail beside a row. [up] and [down] join it to the rows around, [accent]
 * gets a big dot and a thick tail down the row. [over] draws on top, the
 * planner's scroll dot.
 */
@Composable
fun Rail(accent: Boolean, up: Boolean, down: Boolean, modifier: Modifier = Modifier, over: (DrawScope.(x: Float) -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    val line = scheme.outlineVariant
    val tint = scheme.tertiary
    val clock = MaterialTheme.typography.titleLarge.lineHeight
    Box(
        modifier.width(28.dp).fillMaxHeight().drawBehind {
            val x = size.width / 2
            val y = RAIL_PAD.toPx() + clock.toPx() / 2
            val thin = 2.dp.toPx()
            if (up) drawLine(line, Offset(x, 0f), Offset(x, y), thin)
            if (down) drawLine(line, Offset(x, y), Offset(x, size.height), thin)
            if (accent) {
                // the tail only leads somewhere when a row follows
                if (down) drawLine(tint, Offset(x, y), Offset(x, size.height - RAIL_PAD.toPx()), 6.dp.toPx(), StrokeCap.Round)
                drawCircle(tint, 7.dp.toPx(), Offset(x, y))
            } else {
                drawCircle(line, 5.dp.toPx(), Offset(x, y))
            }
            over?.invoke(this, x)
        },
    )
}

/**
 * a day on a date strip: weekday letter, date, up to three dots under it.
 * [dim] fades it for days with nothing worth tapping, [today] rings it.
 */
@Composable
fun DayPill(
    day: LocalDate,
    container: Color,
    content: Color,
    dots: Int,
    dim: Boolean,
    today: Boolean,
    said: String,
    onClick: (() -> Unit)?,
) {
    val shape = MaterialTheme.shapes.large
    val cell = Modifier.widthIn(min = 48.dp).heightIn(min = 72.dp)
        .then(if (today) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
        .clearAndSetSemantics { contentDescription = said }
    val body: @Composable () -> Unit = {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 10.dp).alpha(if (dim) 0.6f else 1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(day.format(WEEKDAY), style = MaterialTheme.typography.labelSmall)
            Text("${day.dayOfMonth}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(4.dp))
            Row(Modifier.height(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(dots.coerceAtMost(3)) { Box(Modifier.size(6.dp).background(content, CircleShape)) }
            }
        }
    }
    if (onClick != null) Surface(onClick = onClick, color = container, contentColor = content, shape = shape, modifier = cell) { body() }
    else Surface(color = container, contentColor = content, shape = shape, modifier = cell) { body() }
}
