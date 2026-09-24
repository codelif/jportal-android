package `in`.codelif.jportal.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextDecoration
import java.time.format.DateTimeFormatter
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import `in`.codelif.jportal.R
import `in`.codelif.jportal.data.AppClock
import `in`.codelif.jportal.domain.DayMark
import `in`.codelif.jportal.ui.theme.LocalExtraColors
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * jportal's green/red calendar: full green day, full red day, and a split
 * disc when a day had both. swipe between months that had classes.
 */
@Composable
fun AttendanceCalendar(
    marks: Map<LocalDate, DayMark>,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (marks.isEmpty()) return
    val first = YearMonth.from(marks.keys.min())
    val last = YearMonth.from(marks.keys.max())
    val months = generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(last) }.toList()
    val pager = rememberPagerState(initialPage = months.lastIndex) { months.size }
    val scope = rememberCoroutineScope()

    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val m = months[pager.currentPage]
            Text(
                "${m.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${m.year}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, enabled = pager.currentPage > 0) {
                Ic(R.drawable.ic_chevron_right, "Previous month", Modifier.rotate(180f))
            }
            IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, enabled = pager.currentPage < months.lastIndex) {
                Ic(R.drawable.ic_chevron_right, "Next month")
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            DayOfWeek.entries.forEach { d ->
                Text(
                    d.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // the same 31 numbers on every month of every subject, laid out once when first drawn instead of a text node per cell
        val measurer = rememberTextMeasurer(cacheSize = 0)
        val style = MaterialTheme.typography.labelLarge
        val density = LocalDensity.current
        val numbers = remember(style, density) {
            if (dayNumbers.size > 4) dayNumbers.clear()
            dayNumbers.getOrPut(style to density) { arrayOfNulls(31) }
        }
        val number = remember(numbers, measurer) { { n: Int -> numbers[n - 1] ?: measurer.measure("$n", style).also { numbers[n - 1] = it } } }
        HorizontalPager(pager, Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { page ->
            MonthGrid(months[page], marks, selected, number, onSelect)
        }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, marks: Map<LocalDate, DayMark>, selected: LocalDate?, number: (Int) -> TextLayoutResult, onSelect: (LocalDate) -> Unit) {
    val lead = month.atDay(1).dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    val rows = (lead + days + 6) / 7
    val today = AppClock.today()
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        repeat(rows) { r ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { c ->
                    val n = r * 7 + c - lead + 1
                    val cell = Modifier.weight(1f).aspectRatio(1f).padding(3.dp)
                    if (n in 1..days) {
                        val date = month.atDay(n)
                        DayCell(date, marks[date], date == selected, date == today, { number(n) }, cell) { if (marks[date] != null) onSelect(date) }
                    } else {
                        Spacer(cell)
                    }
                }
            }
        }
    }
}

private val dayNumbers = HashMap<Pair<androidx.compose.ui.text.TextStyle, androidx.compose.ui.unit.Density>, Array<TextLayoutResult?>>()

private val SPOKEN = DateTimeFormatter.ofPattern("EEEE d MMMM")
private val resting: State<Float> = mutableFloatStateOf(0.86f)

@Composable
private fun DayCell(date: LocalDate, mark: DayMark?, selected: Boolean, today: Boolean, number: () -> TextLayoutResult, modifier: Modifier, onClick: () -> Unit) {
    val extra = LocalExtraColors.current
    val scheme = MaterialTheme.colorScheme
    val present = extra.goodContainer
    val absent = scheme.errorContainer
    // read in draw only, a tap animates two cells without recomposing the month
    val scale = if (mark != null) animateFloatAsState(if (selected) 1f else 0.86f, label = "day") else resting
    val label = when (mark) {
        DayMark.Present -> "present"
        DayMark.Absent -> "absent"
        DayMark.Mixed -> "partly present"
        null -> "no class"
    }
    val ink = when (mark) {
        DayMark.Present -> if (extra.goodContainer.luminance() > 0.5f) Color(0xFF0B3B1A) else Color(0xFFCDEFD6)
        DayMark.Absent -> scheme.onErrorContainer
        DayMark.Mixed -> scheme.onSurface
        null -> scheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    Box(
        modifier.clip(CircleShape)
            .then(if (mark != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics {
                contentDescription = "${date.format(SPOKEN)}, $label"
                if (mark != null) this.selected = selected
            }
            .drawBehind {
                val inset = 20.dp.toPx() * (1 - scale.value)
                val r = size.minDimension / 2 - inset
                val box = Size(r * 2, r * 2)
                val corner = Offset(center.x - r, center.y - r)
                when (mark) {
                    DayMark.Present -> drawCircle(present, r)
                    DayMark.Absent -> drawCircle(absent, r)
                    DayMark.Mixed -> {
                        drawArc(present, 90f, 180f, true, corner, box)
                        drawArc(absent, -90f, 180f, true, corner, box)
                    }
                    null -> if (today) drawCircle(scheme.outlineVariant, r, style = Stroke(1.5.dp.toPx()))
                }
                if (selected) drawCircle(scheme.primary, r - 1.25.dp.toPx(), style = Stroke(2.5.dp.toPx()))
                // struck through when missed, so absent never rests on red alone.
                // None, not null: null keeps whatever the shared layout's paint drew last
                val text = number()
                drawText(
                    text, ink,
                    Offset(center.x - text.size.width / 2f, center.y - text.size.height / 2f),
                    textDecoration = if (mark == DayMark.Absent) TextDecoration.LineThrough else TextDecoration.None,
                )
            },
    )
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
