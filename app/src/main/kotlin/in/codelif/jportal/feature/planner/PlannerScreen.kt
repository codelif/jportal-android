package `in`.codelif.jportal.feature.planner

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.R
import `in`.codelif.jportal.data.Resource
import `in`.codelif.jportal.domain.BatchPath
import `in`.codelif.jportal.domain.DayPlan
import `in`.codelif.jportal.domain.Klass
import `in`.codelif.jportal.domain.Paper
import `in`.codelif.jportal.domain.Timetable
import `in`.codelif.jportal.domain.detect
import `in`.codelif.jportal.domain.electives
import `in`.codelif.jportal.domain.minutes
import `in`.codelif.jportal.domain.prefill
import `in`.codelif.jportal.domain.week
import `in`.codelif.jportal.feature.academics.COMPONENT
import `in`.codelif.jportal.feature.academics.rememberMinute
import `in`.codelif.jportal.feature.attendance.titleCase
import `in`.codelif.jportal.ui.LocalNavigator
import `in`.codelif.jportal.ui.LocalBottomInset
import `in`.codelif.jportal.ui.components.Clock
import `in`.codelif.jportal.ui.components.DayPill
import `in`.codelif.jportal.ui.components.Frog
import `in`.codelif.jportal.ui.components.Ic
import `in`.codelif.jportal.ui.components.MessageState
import `in`.codelif.jportal.ui.components.RAIL_PAD
import `in`.codelif.jportal.ui.components.Rail
import `in`.codelif.jportal.ui.components.ScreenScaffold
import `in`.codelif.jportal.ui.components.StaleNotice
import `in`.codelif.jportal.ui.components.dropdown
import `in`.codelif.jportal.ui.components.group
import `in`.codelif.jportal.ui.components.rememberClockWidth
import `in`.codelif.jportal.ui.components.resourceStates
import `in`.codelif.jportal.ui.nav.Route
import kotlinx.coroutines.flow.collectLatest
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** one batch's week off the cdn timetable, a day at a time */
class PlannerView(
    val timetable: Resource<Timetable>,
    val path: BatchPath?,
    /** what the portal says, null when it can't be matched */
    val detected: BatchPath?,
    val batch: Timetable.Batch?,
    /** elective category to the subjects on offer in [batch] */
    val catalog: Map<String, List<String>>,
    val picks: Map<String, Set<String>>,
    /** classes a weekday has, after picks */
    val counts: Map<DayOfWeek, Int>,
    val days: List<LocalDate>,
    val day: LocalDate,
    val today: LocalDate,
    val plan: DayPlan,
    /** today's papers not yet over, for the banner */
    val exams: List<Paper>,
    val timeline: Boolean,
    val dot: Dot,
    /** "batch", or "k:<row>" for a class on [plan] */
    val sheet: String?,
    val openSheet: (String?) -> Unit,
    val pickDay: (LocalDate) -> Unit,
    /** the picker's choice, null to go back to what the portal says */
    val pin: (BatchPath?) -> Unit,
    val setPicks: (Map<String, Set<String>>) -> Unit,
    /** picks from what's registered on the portal, false when that couldn't be read */
    val matchPortal: suspend () -> Boolean,
    val refresh: () -> Unit,
)

@Composable
fun rememberPlanner(list: LazyListState): PlannerView {
    val graph = LocalGraph.current
    val repo = graph.repo
    val prefs = graph.prefs
    val res by graph.timetable.state.collectAsState()
    val personal by repo.personal.state.collectAsState()
    val pinned by prefs.plannerBatchState.collectAsState()
    val allPicks by prefs.electivesState.collectAsState()
    val timeline by prefs.timelineState.collectAsState()
    LaunchedEffect(Unit) {
        graph.timetable.refresh()
        repo.personal.refresh()
    }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var picked by rememberSaveable { mutableStateOf<Long?>(null) }
    val now = rememberMinute()

    val tt = res.data
    val detected = remember(tt, personal.data) { personal.data?.general?.let { g -> tt?.detect(g.batch, g.semester, g.program) } }
    val path = BatchPath.parse(pinned)?.takeIf { tt?.batch(it) != null } ?: detected
    val batch = path?.let { tt?.batch(it) }
    val catalog = remember(batch) { batch?.electives().orEmpty() }
    val picks = path?.let { allPicks[it.phaseKey] }

    val matchPortal: suspend () -> Boolean = {
        val sem = repo.subjectSemesters.await().data?.firstOrNull()
        val rows = sem?.let { repo.subjects(it).await().data?.rows }
        if (batch != null && path != null && rows != null) {
            prefs.setElectives(path.phaseKey, batch.prefill(rows.map { it.code to it.name }))
            true
        } else false
    }
    // a phase nobody has picked for yet starts from the portal's registrations
    LaunchedEffect(path?.phaseKey, catalog.isNotEmpty(), picks == null) {
        if (path != null && catalog.isNotEmpty() && picks == null) matchPortal()
    }

    val week = remember(batch, picks) { batch?.week(picks.orEmpty()).orEmpty() }
    val counts = remember(week) { week.groupingBy { it.day }.eachCount() }
    val today = now.toLocalDate()
    // sunday has no column unless something's on it, so a sunday looks at the week ahead
    val sunday = DayOfWeek.SUNDAY in counts
    val monday = if (today.dayOfWeek == DayOfWeek.SUNDAY && !sunday) today.plusDays(1) else today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val days = (0L until if (sunday) 7L else 6L).map { monday.plusDays(it) }
    val day = picked?.let(LocalDate::ofEpochDay)?.takeIf { it in days } ?: today.takeIf { it in days } ?: days.first()
    val plan = remember(week, day, now) {
        DayPlan.of(week.filter { it.day == day.dayOfWeek }, if (day == today) now.toLocalTime() else null)
    }

    val sheetPapers by produceState<List<Paper>?>(null, today) { value = repo.examSheet() }
    val exams = sheetPapers.orEmpty().filter { it.day == today && it.status(now) != Paper.Status.Done }.sortedWith(compareBy(nullsLast()) { it.start })

    val dot = rememberDot(list, "tl-$day", plan.rows.size)

    return PlannerView(
        res, path, detected, batch, catalog, picks.orEmpty(), counts, days, day, today, plan, exams, timeline, dot,
        sheet, { sheet = it }, { picked = it.toEpochDay() },
        { p -> prefs.setPlannerBatch(p?.takeIf { it != detected }?.key) },
        { p -> path?.let { prefs.setElectives(it.phaseKey, p) } },
        matchPortal,
        { graph.timetable.refresh(force = true); repo.personal.refresh(force = true) },
    )
}

@Composable
fun PlannerScreen() {
    val list = rememberLazyListState()
    val view = rememberPlanner(list)
    ScreenScaffold(
        title = "Planner",
        refreshing = view.timetable.refreshing && view.timetable.data != null,
        onRefresh = view.refresh,
        listState = list,
        bottomPadding = LocalBottomInset.current,
        actions = { BatchChip(view) },
    ) { plannerContent(view) }
    PlannerSheets(view)
}

fun LazyListScope.plannerContent(view: PlannerView) {
    item("stale") { StaleNotice(view.timetable, view.refresh) }
    if (!resourceStates(view.timetable, view.refresh)) return
    if (view.batch == null) {
        item("pick") {
            MessageState(
                Frog.Look, "Which batch are you in?",
                "Couldn't match yours from the portal. Pick it once and your week shows up here.",
                "Pick batch",
            ) { view.openSheet("batch") }
        }
        return
    }
    if (view.exams.isNotEmpty()) item("exams") { ExamBanner(view.exams) }
    item("days") { DayStrip(view) }
    item("day-h") { DayHeader(view.day, view.today) }
    if (view.plan.rows.isEmpty()) {
        item("free") {
            val unpicked = view.catalog.isNotEmpty() && view.picks.values.all { it.isEmpty() }
            if (unpicked) MessageState(Frog.Look, "Nothing on, so far", "No electives picked yet, they might be hiding here.", "Pick electives") { view.openSheet("batch") }
            else MessageState(Frog.Party, "Free day", "Nothing on the timetable.")
        }
        return
    }
    val rows = view.plan.rows
    group("tl-${view.day}", bottom = 16.dp) {
        rows.forEachIndexed { i, r ->
            row(i) {
                when (r) {
                    is DayPlan.Row.Class -> ClassRow(view, r.k, i, up = i > 0, down = i < rows.lastIndex)
                    is DayPlan.Row.Free -> FreeRow(view, r.minutes, i)
                }
            }
        }
    }
}

private val DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM")

@Composable
private fun BatchChip(view: PlannerView) {
    val batch = view.batch ?: return
    FilterChip(
        selected = view.path != view.detected,
        onClick = { view.openSheet("batch") },
        modifier = Modifier.padding(end = 8.dp).dropdown("Batch, ${batch.name}") { view.openSheet("batch") },
        label = { Text(batch.name) },
        trailingIcon = { Ic(R.drawable.ic_expand_more, null, Modifier.size(18.dp)) },
    )
}

/** classes still show on an exam day, this says why they might not matter */
@Composable
private fun ExamBanner(papers: List<Paper>) {
    val nav = LocalNavigator.current
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = { nav.push(Route.Exams) },
        shape = MaterialTheme.shapes.large,
        color = scheme.tertiaryContainer,
        contentColor = scheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Ic(R.drawable.ic_edit_calendar, null, Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(if (papers.size == 1) "Exam today" else "${papers.size} exams today", style = MaterialTheme.typography.labelLarge)
                Text(
                    papers.joinToString(" · ") { p -> listOfNotNull(p.slot.subjectName.titleCase(), p.slot.start?.format(SPOKEN)).joinToString(", ") },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Ic(R.drawable.ic_chevron_right, null)
        }
    }
}

@Composable
private fun DayStrip(view: PlannerView) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    LazyRow(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(view.days, key = { it.toEpochDay() }) { d ->
            val n = view.counts[d.dayOfWeek] ?: 0
            val on = d == view.day
            val said = listOfNotNull(
                d.format(DAY),
                "today".takeIf { d == view.today },
                "selected".takeIf { on },
                if (n == 0) "free day" else if (n == 1) "1 class" else "$n classes",
            ).joinToString(", ")
            DayPill(
                d,
                if (on) scheme.secondaryContainer else Color.Transparent,
                if (on) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                dots = n,
                dim = n == 0 && !on,
                today = d == view.today,
                said = said,
            ) {
                if (!on) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                view.pickDay(d)
            }
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate, today: LocalDate) {
    val d = ChronoUnit.DAYS.between(today, day)
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() }.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(day.format(DAY), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        val rel = when (d) {
            0L -> "Today"
            1L -> "Tomorrow"
            -1L -> "Yesterday"
            else -> null
        }
        if (rel != null) Text(rel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

// the scroll dot, after jpoop-planner: as far down the day as the list is scrolled down the page

/** where the dot is, in the list's viewport, and which timeline row it's on */
@Stable
class Dot internal constructor(val list: LazyListState) {
    internal var prefix by mutableStateOf("")
    internal var rows by mutableIntStateOf(0)
    /** a row's dot height from its top, where the dot settles on the first and last row */
    internal var anchor by mutableFloatStateOf(0f)
    /** space between group rows, every row but the first starts this far into its item */
    internal var gap by mutableFloatStateOf(0f)
    val y = Animatable(Float.NaN)

    internal fun inset(row: Int) = if (row == 0) 0f else gap

    private fun index(key: Any): Int? = (key as? String)?.takeIf { it.startsWith("$prefix/") }?.substringAfterLast('/')?.toIntOrNull()

    internal fun target(): Float {
        val info = list.layoutInfo
        val items = info.visibleItemsInfo
        val tl = items.filter { index(it.key) != null }
        if (tl.isEmpty() || rows == 0) return Float.NaN
        val h = info.viewportSize.height.toFloat()
        val p = when {
            !list.canScrollBackward -> 0f
            !list.canScrollForward -> 1f
            else -> {
                // lazy lists don't know their full height, the average row gets close enough under a spring
                val avg = items.sumOf { it.size }.toFloat() / items.size
                val total = avg * info.totalItemsCount + info.afterContentPadding
                ((list.firstVisibleItemIndex * avg + list.firstVisibleItemScrollOffset) / (total - h).coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
        }
        val first = tl.first()
        val last = tl.last()
        val lo = if (index(first.key) == 0) first.offset + anchor else Float.NEGATIVE_INFINITY
        val hi = if (index(last.key) == rows - 1) last.offset + inset(rows - 1) + anchor else Float.POSITIVE_INFINITY
        return (p * h).coerceIn(lo, maxOf(lo, hi))
    }

    /** the timeline row under the dot, -1 for none */
    val row: Int by derivedStateOf {
        val v = y.value
        if (v.isNaN()) -1
        else list.layoutInfo.visibleItemsInfo.firstOrNull { v >= it.offset && v < it.offset + it.size && index(it.key) != null }?.let { index(it.key) } ?: -1
    }

    /** the dot's height inside [row]'s rail, null when it's on another row */
    internal fun local(row: Int): Float? {
        val v = y.value
        if (v.isNaN()) return null
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "$prefix/$row" } ?: return null
        val at = v - item.offset - inset(row)
        return at.takeIf { it >= 0f && it <= item.size - inset(row) }
    }
}

@Composable
private fun rememberDot(list: LazyListState, prefix: String, rows: Int): Dot {
    val dot = remember(list) { Dot(list) }
    val density = LocalDensity.current
    val clock = MaterialTheme.typography.titleLarge.lineHeight
    dot.prefix = prefix
    dot.rows = rows
    dot.anchor = with(density) { RAIL_PAD.toPx() + clock.toPx() / 2 }
    dot.gap = with(density) { 3.dp.toPx() }
    LaunchedEffect(dot) {
        snapshotFlow { dot.target() }.collectLatest { t ->
            if (t.isNaN() || dot.y.value.isNaN()) dot.y.snapTo(t)
            else dot.y.animateTo(t, spring(stiffness = Spring.StiffnessLow))
        }
    }
    return dot
}

/** drawn over the rail of the row it's on */
@Composable
private fun dotPainter(view: PlannerView, row: Int): DrawScope.(Float) -> Unit {
    val fill = MaterialTheme.colorScheme.primary
    val ring = MaterialTheme.colorScheme.surfaceContainerLow
    return { x ->
        view.dot.local(row)?.let { y ->
            drawCircle(ring, 9.dp.toPx(), Offset(x, y))
            drawCircle(fill, 6.dp.toPx(), Offset(x, y))
        }
    }
}

private fun kind(type: String) = COMPONENT[type] ?: type

/** one sentence for talkback, what a glance at the row gets */
private fun speak(k: Klass, live: Boolean) = listOfNotNull(
    k.subject,
    kind(k.type),
    "${k.start.format(SPOKEN)} to ${k.end.format(SPOKEN)}",
    k.venue.takeIf { it.isNotBlank() }?.let { "in $it" },
    k.teacher.takeIf { it.isNotBlank() },
    "on now".takeIf { live },
).joinToString(", ")

private val SPOKEN = DateTimeFormatter.ofPattern("h:mm a")

@Composable
private fun ClassRow(view: PlannerView, k: Klass, row: Int, up: Boolean, down: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val live = k in view.plan.now
    val active = !view.timeline || view.dot.row == row
    // big fonts get no room for the subject beside the room, so the room drops under it
    val stacked = LocalDensity.current.fontScale > 1.3f
    Surface(
        onClick = { view.openSheet("k:$row") },
        color = if (live) scheme.tertiaryContainer.copy(alpha = 0.6f) else Color.Transparent,
    ) {
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                .clearAndSetSemantics { contentDescription = speak(k, live) }
                .padding(start = 8.dp, end = 12.dp),
        ) {
            Column(Modifier.padding(vertical = RAIL_PAD).width(rememberClockWidth()), horizontalAlignment = Alignment.End) {
                Clock(k.start, k.end, active)
            }
            if (view.timeline) Rail(live, up, down, over = dotPainter(view, row)) else Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).padding(vertical = RAIL_PAD)) {
                Text(k.subject, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(kind(k.type), k.teacher.takeIf { it.isNotBlank() }).joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (stacked && k.venue.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Venue(k.venue)
                }
            }
            if (!stacked && k.venue.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.padding(vertical = RAIL_PAD).align(Alignment.CenterVertically)) { Venue(k.venue) }
            }
        }
    }
}

@Composable
private fun Venue(venue: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
        Box(Modifier.widthIn(min = 56.dp).padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
            Text(venue, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }
}

/** a stretch with nothing on, the rail runs through it dashed */
@Composable
private fun FreeRow(view: PlannerView, free: Long, row: Int) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val line = MaterialTheme.colorScheme.outlineVariant
    val over = dotPainter(view, row)
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).semantics(mergeDescendants = true) {}.padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(rememberClockWidth()))
        if (view.timeline) {
            Box(
                Modifier.width(28.dp).fillMaxHeight().drawBehind {
                    val x = size.width / 2
                    drawLine(line, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
                    over(x)
                },
            )
        } else Spacer(Modifier.width(16.dp))
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Ic(R.drawable.ic_bed, null, Modifier.size(16.dp), tint = muted)
            Spacer(Modifier.width(6.dp))
            Text("${minutes(free)} free", style = MaterialTheme.typography.labelMedium, color = muted)
        }
    }
}
