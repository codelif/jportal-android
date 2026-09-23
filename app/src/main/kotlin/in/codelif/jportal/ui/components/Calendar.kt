package `in`.codelif.jportal.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.R
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
        HorizontalPager(pager, Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { page ->
            MonthGrid(months[page], marks, selected, onSelect)
        }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, marks: Map<LocalDate, DayMark>, selected: LocalDate?, onSelect: (LocalDate) -> Unit) {
    val lead = month.atDay(1).dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    val rows = (lead + days + 6) / 7
    val today = LocalDate.now()
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        repeat(rows) { r ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { c ->
                    val n = r * 7 + c - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                        if (n in 1..days) {
                            val date = month.atDay(n)
                            DayCell(date, marks[date], date == selected, date == today) { if (marks[date] != null) onSelect(date) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, mark: DayMark?, selected: Boolean, today: Boolean, onClick: () -> Unit) {
    val extra = LocalExtraColors.current
    val scheme = MaterialTheme.colorScheme
    val present = extra.goodContainer
    val absent = scheme.errorContainer
    val scale by animateFloatAsState(if (selected) 1f else 0.86f, label = "day")
    val label = when (mark) {
        DayMark.Present -> "present"
        DayMark.Absent -> "absent"
        DayMark.Mixed -> "partly present"
        null -> "no class"
    }
    Surface(
        onClick = onClick,
        enabled = mark != null,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = Modifier.fillMaxSize().semantics { contentDescription = "$date, $label" },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(20.dp * (1 - scale)).clip(CircleShape)) {
                when (mark) {
                    DayMark.Present -> drawCircle(present)
                    DayMark.Absent -> drawCircle(absent)
                    DayMark.Mixed -> {
                        drawArc(present, 90f, 180f, true, Offset.Zero, Size(size.width, size.height))
                        drawArc(absent, -90f, 180f, true, Offset.Zero, Size(size.width, size.height))
                    }
                    null -> if (today) drawCircle(scheme.outlineVariant, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                }
                if (selected) drawCircle(scheme.primary, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx()))
            }
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.labelLarge,
                color = when (mark) {
                    DayMark.Present -> if (extra.goodContainer.luminance() > 0.5f) Color(0xFF0B3B1A) else Color(0xFFCDEFD6)
                    DayMark.Absent -> scheme.onErrorContainer
                    DayMark.Mixed -> scheme.onSurface
                    null -> scheme.onSurfaceVariant.copy(alpha = 0.6f)
                },
            )
        }
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
