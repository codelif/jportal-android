package `in`.codelif.jportal.feature.academics

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.R
import `in`.codelif.jportal.data.AppClock
import `in`.codelif.jportal.data.Resource
import `in`.codelif.jportal.domain.Countdown
import `in`.codelif.jportal.domain.ExamPlan
import `in`.codelif.jportal.domain.Paper
import `in`.codelif.jportal.domain.countdown
import `in`.codelif.jportal.domain.minutes
import `in`.codelif.jportal.domain.outOf
import `in`.codelif.jportal.domain.short
import `in`.codelif.jportal.feature.attendance.titleCase
import `in`.codelif.jportal.feature.subject.byCode
import `in`.codelif.jportal.ui.LocalNavigator
import `in`.codelif.jportal.ui.components.CenteredLoading
import `in`.codelif.jportal.ui.components.Frog
import `in`.codelif.jportal.ui.components.Ic
import `in`.codelif.jportal.ui.components.MessageState
import `in`.codelif.jportal.ui.components.SectionHeader
import `in`.codelif.jportal.ui.components.SemesterPicker
import `in`.codelif.jportal.ui.components.StaleNotice
import `in`.codelif.jportal.ui.components.group
import `in`.codelif.jportal.ui.components.resourceStates
import `in`.codelif.jportal.ui.nav.Route
import `in`.codelif.jportal.ui.theme.NumberStyle
import `in`.codelif.ktjiit.model.ExamEvent
import `in`.codelif.ktjiit.model.Semester
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** the date sheet for one exam semester, every event's papers flattened */
class ExamsView(
    val semesters: List<Semester>,
    val semester: Semester?,
    val events: Resource<List<ExamEvent>>,
    val papers: List<Paper>,
    /** events are in but some date sheets aren't, so no papers doesn't mean no exams yet */
    val loadingPapers: Boolean,
    /** events still ahead whose date sheet the portal hasn't put out */
    val pending: List<ExamEvent>,
    /** papers whose room or seat showed up while the screen was open */
    val fresh: Set<String>,
    val plan: ExamPlan,
    val showDone: Boolean,
    val toggleDone: () -> Unit,
    /** "p:<paper key>" or "d:<date>", what the sheet is showing */
    val sheet: String?,
    val openSheet: (String?) -> Unit,
    val pick: (String) -> Unit,
    val refresh: () -> Unit,
)

// room and seat land about a day out, so look harder then instead of waiting out the hour long cache
private const val SEAT_POLL_MS = 10 * 60_000L

/** what the disk had before the network answered, to tell when a seat just landed */
private class Seen { var blank: Set<String>? = null }

@Composable
fun rememberExams(startAt: String?): ExamsView {
    val repo = LocalGraph.current.repo
    val sems by repo.examSemesters.state.collectAsState()
    LaunchedEffect(Unit) { repo.examSemesters.refresh() }
    var picked by rememberSaveable(startAt) { mutableStateOf(startAt) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val now = rememberMinute()
    val list = sems.data.orEmpty()
    val sem = list.byCode(picked) ?: list.firstOrNull()
    val store = sem?.let { s -> remember(s.id) { repo.examEvents(s) } }
    LaunchedEffect(store) { store?.refresh() }
    // no exam semester at all is an answer too, not a reason to spin forever
    val events = store?.state?.collectAsState()?.value
        ?: if (sems.data != null) Resource(data = emptyList(), fetchedAt = sems.fetchedAt, checked = true) else sems.map { emptyList<ExamEvent>() }

    val today = now.toLocalDate()
    val sheets = events.data.orEmpty().map { ev ->
        key(ev.id) {
            val st = remember { repo.examSchedule(ev) }
            val r = st.state.collectAsState().value
            LaunchedEffect(st) { st.refresh() }
            val due = r.data.orEmpty().any { s -> !s.seated && s.day?.let { it >= today && it <= today.plusDays(2) } == true }
            LaunchedEffect(st, due, r.fetchedAt) {
                if (due && r.fetchedAt?.let { System.currentTimeMillis() - it > SEAT_POLL_MS } != false) st.refresh(force = true)
            }
            val seen = remember(st) { Seen() }
            if (seen.blank == null && r.data != null) seen.blank = r.data.orEmpty().filter { !it.seated }.map { Paper(ev, it).key }.toSet()
            Triple(ev, r, seen)
        }
    }
    val papers = sheets.flatMap { (ev, r) -> r.data.orEmpty().map { Paper(ev, it) } }
    val loadingPapers = sheets.any { (_, r) -> r.data == null && r.error == null }
    val fresh = sheets.flatMap { (ev, r, seen) ->
        r.data.orEmpty().filter { it.seated }.map { Paper(ev, it).key }.filter { it in seen.blank.orEmpty() }
    }.toSet()
    val pending = sheets.filter { (ev, r) -> r.data?.isEmpty() == true && ev.from?.let { dayOf(it) >= today } == true }.map { it.first }
    val plan = remember(papers, now) { ExamPlan.of(papers, now) }

    val refresh = {
        repo.examSemesters.refresh(force = true)
        store?.refresh(force = true)
        events.data.orEmpty().forEach { repo.examSchedule(it).refresh(force = true) }
        Unit
    }
    return ExamsView(
        list, sem, events, papers, loadingPapers, pending, fresh, plan,
        showDone, { showDone = !showDone }, sheet, { sheet = it }, { picked = it }, refresh,
    )
}

/** now, to the minute, ticking on the minute. every countdown on screen reads this one clock */
@Composable
private fun rememberMinute(): LocalDateTime {
    val now by produceState(AppClock.now().truncatedTo(ChronoUnit.MINUTES)) {
        while (true) {
            val t = AppClock.now()
            delay(60_050L - t.second * 1000L - t.nano / 1_000_000)
            value = AppClock.now().truncatedTo(ChronoUnit.MINUTES)
        }
    }
    return now
}

private fun dayOf(epochMs: Long): LocalDate = Instant.ofEpochMilli(epochMs).atZone(AppClock.clock.zone).toLocalDate()

fun LazyListScope.examsContent(view: ExamsView) {
    item("pick") { SemesterPicker(view.semesters, view.semester, view.pick, view.events.fetchedAt) }
    item("stale") { StaleNotice(view.events, view.refresh) }
    if (!resourceStates(view.events, view.refresh)) return
    val plan = view.plan
    if (view.papers.isEmpty()) {
        val first = view.pending.minByOrNull { it.from ?: Long.MAX_VALUE }
        when {
            view.loadingPapers -> item("loading") { CenteredLoading() }
            first != null -> item("pending") {
                MessageState(
                    Frog.Look, "Date sheet's not out yet",
                    "${first.short} starts around ${first.from?.let { dayOf(it).format(SHORT) }}. It shows up here once the portal has it.",
                )
            }
            else -> item("none") { MessageState(Frog.Sleep, "No exams on the calendar", "Enjoy it while it lasts.") }
        }
        return
    }

    val next = plan.next
    if (next != null) item("next") { NextUp(next, view) }
    else item("wrap") { MessageState(Frog.Party, "That's a wrap", "Every paper on this sheet is done.") }
    if (next != null && plan.run.size >= 2) item("run") { RunStrip(view) }

    if (plan.days.isNotEmpty()) {
        item("up-h") { SectionHeader("Date sheet") }
        plan.days.forEach { d ->
            if (d.free > 0) item("gap-${d.date}") { Gap(d.free) }
            item("day-${d.date}") { DayHeader(d.date, plan.today) }
            group("papers-${d.date}", animate = true) { d.papers.forEach { p -> row(p.key) { PaperRow(p, view) } } }
        }
    }
    if (view.pending.isNotEmpty()) {
        group("pending", top = 12.dp) { view.pending.forEach { ev -> row(ev.id) { PendingRow(ev) } } }
    }
    if (plan.done.isNotEmpty()) {
        item("done-h") { DoneHeader(plan.done.size, view.showDone, view.toggleDone) }
        if (view.showDone) group("done", bottom = 16.dp, animate = true) { plan.done.forEach { p -> row(p.key) { PaperRow(p, view) } } }
    }
}

private val DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM")
private val SHORT = DateTimeFormatter.ofPattern("EEE d MMM")
private val DATE = DateTimeFormatter.ofPattern("d MMM")
private val TIME = DateTimeFormatter.ofPattern("h:mm a")
private val CLOCK = DateTimeFormatter.ofPattern("h:mm")
private val MERIDIEM = DateTimeFormatter.ofPattern("a")
private val MONTH = DateTimeFormatter.ofPattern("MMM")
private val WEEKDAY = DateTimeFormatter.ofPattern("EEEEE")

/** 3:30 – 4:30 PM, or 11:30 AM – 1:00 PM across noon. the portal's own text when it won't parse */
private fun window(p: Paper): String? {
    val s = p.slot.start ?: return p.slot.until.ifBlank { p.slot.from }.trim().takeIf { it.isNotEmpty() }
    val e = p.slot.end ?: return s.format(TIME)
    val start = if ((s.hour < 12) == (e.hour < 12)) s.format(CLOCK) else s.format(TIME)
    return "$start – ${e.format(TIME)}"
}

private fun relative(day: LocalDate, today: LocalDate): String {
    val d = ChronoUnit.DAYS.between(today, day)
    return when {
        d == -1L -> "Yesterday"
        d < 0L -> "${-d} days ago"
        d == 0L -> "Today"
        d == 1L -> "Tomorrow"
        else -> "in $d days"
    }
}

/** "4 days to go", "Tomorrow · 3:30 PM", "Now · 22m left" */
private fun Countdown.line() = if (big.first().isDigit()) "$big $small" else "$big · $small"

private fun subjectOf(p: Paper) = p.slot.subjectName.titleCase()

/** one sentence for talkback, what a sighted glance at the row gets */
private fun speak(p: Paper, plan: ExamPlan): String {
    val s = p.slot
    return listOfNotNull(
        subjectOf(p),
        p.event.short,
        p.day?.format(DAY),
        window(p)?.replace(" – ", " to "),
        when {
            s.seated -> listOfNotNull(s.room.takeIf { it.isNotBlank() }?.let { "room $it" }, s.seat.takeIf { it.isNotBlank() }?.let { "seat $it" }).joinToString(", ")
            plan.status(p) != Paper.Status.Done -> "room and seat not out yet"
            else -> null
        },
        plan.clashes[p.key]?.let { "clashes with ${subjectOf(it)}" },
        "next up".takeIf { p.key == plan.next?.key },
    ).joinToString(", ")
}

@Composable
private fun NextUp(p: Paper, view: ExamsView) {
    val plan = view.plan
    val now = plan.now
    val live = plan.status(p) == Paper.Status.Live
    val c = countdown(p, now) { it.format(TIME) }
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.tertiaryContainer,
        contentColor = scheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(
                "${p.event.short} · " + when {
                    live -> "In progress"
                    p.day == plan.today -> "Today"
                    else -> "Next up"
                },
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(subjectOf(p), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Bottom) {
                AnimatedContent(c.big, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "countdown") {
                    Text(it, style = NumberStyle)
                }
                Spacer(Modifier.width(8.dp))
                Text(c.small, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
            }
            val start = p.start
            val over = p.over
            if (live && start != null && over != null) {
                val total = Duration.between(start, over).toMinutes().coerceAtLeast(1)
                val gone = Duration.between(start, now).toMinutes().coerceIn(0, total)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { gone.toFloat() / total },
                    color = scheme.tertiary,
                    trackColor = scheme.onTertiaryContainer.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ic(R.drawable.ic_schedule, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    listOfNotNull(p.day?.format(SHORT), window(p) ?: "time not out yet", p.duration?.let { minutes(it.toMinutes()) }).joinToString(" · "),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(Modifier.height(12.dp))
            SeatTiles(p, now, p.key in view.fresh)
            if (plan.then.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Then " + plan.then.joinToString(", ") { t -> subjectOf(t) + (t.slot.start?.let { " at " + it.format(TIME).replace(' ', '\u00A0') } ?: "") },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(20.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!live) {
                    Button(
                        onClick = { haptics.performHapticFeedback(HapticFeedbackType.Confirm); addToCalendar(context, p) },
                        colors = ButtonDefaults.buttonColors(containerColor = scheme.tertiary, contentColor = scheme.onTertiary),
                    ) {
                        Ic(R.drawable.ic_edit_calendar, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add to calendar")
                    }
                }
                view.semester?.let { sem ->
                    OutlinedButton(
                        onClick = { nav.push(Route.Subject(sem.code, p.slot.code)) },
                        border = BorderStroke(1.dp, scheme.onTertiaryContainer.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onTertiaryContainer),
                    ) { Text("Subject") }
                }
            }
        }
    }
}

/** room and seat are what you hunt for at the hall door, so they get the biggest type after the countdown */
@Composable
private fun SeatTiles(p: Paper, now: LocalDateTime, fresh: Boolean) {
    val s = p.slot
    val tile = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    if (!s.seated) {
        val soon = p.start?.let { Duration.between(now, it).toHours() < 24 } ?: (p.day == now.toLocalDate())
        Surface(color = tile, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Ic(R.drawable.ic_event_seat, null, Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    if (soon) "Room and seat should be out any time now" else "Room and seat usually come out a day before",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile("Room", s.room.ifBlank { "soon" }, fresh, tile, Modifier.weight(1f))
        Tile("Seat", s.seat.ifBlank { "soon" }, fresh, tile, Modifier.weight(1f))
    }
}

@Composable
private fun Tile(label: String, value: String, fresh: Boolean, color: Color, modifier: Modifier) {
    Surface(color = color, shape = MaterialTheme.shapes.large, modifier = modifier.semantics(mergeDescendants = true) {}) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(if (fresh) "$label · just in" else label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.alpha(0.8f))
            Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun addToCalendar(context: Context, p: Paper) {
    val begin = p.start ?: p.day?.atStartOfDay() ?: return
    val end = p.over ?: begin.plusHours(3)
    fun ms(t: LocalDateTime) = t.atZone(AppClock.clock.zone).toInstant().toEpochMilli()
    val s = p.slot
    val where = listOfNotNull(s.room.takeIf { it.isNotBlank() }, s.seat.takeIf { it.isNotBlank() }?.let { "seat $it" }).joinToString(", ")
    val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, "${p.event.short}: ${subjectOf(p)}")
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, ms(begin))
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, ms(end))
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, p.start == null)
        .putExtra(CalendarContract.Events.EVENT_LOCATION, where)
        .putExtra(CalendarContract.Events.DESCRIPTION, s.code + if (where.isEmpty()) ". Room and seat come out about a day before." else "")
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No calendar app to add it to", Toast.LENGTH_SHORT).show()
    }
}

// the run strip: every day from the first paper to the last, free days left visible

@Composable
private fun RunStrip(view: ExamsView) {
    val plan = view.plan
    val first = plan.run.first()
    val last = plan.run.last()
    // a week out, today joins the strip so the prep days before the first paper show too
    val from = if (plan.today < first && ChronoUnit.DAYS.between(plan.today, first) <= 7) plan.today else first
    val span = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= last }.toList()
    val running = plan.days.flatMap { d -> d.papers.map { it.event.id } }.toSet()
    val byDay = view.papers.filter { it.event.id in running }.groupBy { it.day }
    val nextDay = plan.next?.day
    val haptics = LocalHapticFeedback.current
    val start = span.indexOfFirst { it >= plan.today }.let { if (it < 0) span.lastIndex else it }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (start - 1).coerceAtLeast(0))
    val events = plan.days.flatMap { d -> d.papers.map { it.event } }.distinctBy { it.id }.joinToString(" & ") { it.short }
    val range = if (first.month == last.month) "${first.dayOfMonth} to ${last.format(DATE)}" else "${first.format(DATE)} to ${last.format(DATE)}"
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$events · $range",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${plan.runDone} of ${plan.runTotal} done",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(span, key = { it.toEpochDay() }) { d ->
                val papers = byDay[d].orEmpty()
                DayCell(d, papers, plan, next = d == nextDay) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    view.openSheet("d:$d")
                }
            }
        }
    }
}

@Composable
private fun DayCell(d: LocalDate, papers: List<Paper>, plan: ExamPlan, next: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val exam = papers.isNotEmpty()
    val done = exam && papers.all { plan.status(it) == Paper.Status.Done }
    val today = d == plan.today
    val (bg, fg) = when {
        next -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        exam && !done -> scheme.secondaryContainer to scheme.onSecondaryContainer
        exam -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
        else -> Color.Transparent to scheme.onSurfaceVariant
    }
    val said = listOfNotNull(
        d.format(DAY),
        "today".takeIf { today },
        when {
            !exam -> "free day"
            done -> "done"
            else -> papers.size.let { if (it == 1) "1 exam" else "$it exams" }
        },
    ).joinToString(", ")
    val shape = MaterialTheme.shapes.large
    val cell = Modifier.widthIn(min = 48.dp).heightIn(min = 72.dp)
        .then(if (today) Modifier.border(2.dp, scheme.primary, shape) else Modifier)
        .clearAndSetSemantics { contentDescription = said }
    val body: @Composable () -> Unit = {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 10.dp).alpha(if (exam && !done) 1f else 0.6f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(d.format(WEEKDAY), style = MaterialTheme.typography.labelSmall)
            Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(4.dp))
            Row(Modifier.height(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                papers.take(3).forEach { _ -> Box(Modifier.size(6.dp).background(fg, CircleShape)) }
            }
        }
    }
    if (exam) Surface(onClick = onClick, color = bg, contentColor = fg, shape = shape, modifier = cell) { body() }
    else Surface(color = bg, contentColor = fg, shape = shape, modifier = cell) { body() }
}

@Composable
private fun Gap(free: Int) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.width(12.dp))
        Ic(R.drawable.ic_bed, null, Modifier.size(16.dp), tint = muted)
        Spacer(Modifier.width(6.dp))
        Text(if (free == 1) "1 day free" else "$free days free", style = MaterialTheme.typography.labelMedium, color = muted)
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun DayHeader(day: LocalDate?, today: LocalDate) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() }.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            day?.format(DAY) ?: "Date not out yet",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (day != null) Text(relative(day, today), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PaperRow(p: Paper, view: ExamsView) {
    val plan = view.plan
    val done = plan.status(p) == Paper.Status.Done
    val said = speak(p, plan)
    // big fonts get no room for the subject beside the clock and the seat, so it drops below them
    val stacked = LocalDensity.current.fontScale > 1.3f
    Surface(onClick = { view.openSheet("p:${p.key}") }, color = Color.Transparent) {
        val box = Modifier.fillMaxWidth().alpha(if (done) 0.6f else 1f).clearAndSetSemantics { contentDescription = said }
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)
        if (stacked) {
            Column(box) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeBlock(p, done, Alignment.Start)
                    Spacer(Modifier.weight(1f))
                    if (!done) SeatBadge(p, p.key in view.fresh)
                }
                Spacer(Modifier.height(8.dp))
                PaperText(p, plan, done)
            }
        } else {
            Row(box, verticalAlignment = Alignment.CenterVertically) {
                TimeBlock(p, done, Alignment.CenterHorizontally)
                Spacer(Modifier.width(12.dp))
                PaperText(p, plan, done, Modifier.weight(1f))
                if (!done) {
                    Spacer(Modifier.width(8.dp))
                    SeatBadge(p, p.key in view.fresh)
                }
            }
        }
    }
}

@Composable
private fun PaperText(p: Paper, plan: ExamPlan, done: Boolean, modifier: Modifier = Modifier) {
    val clash = plan.clashes[p.key]
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                subjectOf(p),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            when {
                plan.status(p) == Paper.Status.Live -> Tag("Now")
                p.key == plan.next?.key -> Tag("Next")
            }
        }
        val s = p.slot
        Text(
            if (done) listOfNotNull(s.start?.format(TIME), p.event.short, s.room.takeIf { it.isNotBlank() }).joinToString(" · ")
            else listOfNotNull(p.duration?.let { minutes(it.toMinutes()) }, p.event.short, s.code.takeIf { it.isNotBlank() }).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (clash != null && !done) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ic(R.drawable.ic_warning, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(4.dp))
                Text("Clashes with ${subjectOf(clash)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** upcoming rows lead with the clock, the day's already above them. done rows lead with the date */
@Composable
private fun TimeBlock(p: Paper, done: Boolean, align: Alignment.Horizontal) {
    val s = p.slot
    val primary = MaterialTheme.colorScheme.primary
    val bold = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
    val small = MaterialTheme.typography.labelSmall
    Column(Modifier.widthIn(min = 64.dp), horizontalAlignment = align) {
        val start = s.start
        val end = s.end
        when {
            done -> {
                Text(s.day?.dayOfMonth?.toString() ?: "?", style = bold, color = primary)
                Text(s.day?.format(MONTH)?.uppercase().orEmpty(), style = small)
            }
            start != null -> {
                Text(
                    buildAnnotatedString {
                        withStyle(bold.toSpanStyle()) { append(start.format(CLOCK)) }
                        withStyle(SpanStyle(fontSize = small.fontSize, fontWeight = small.fontWeight)) { append(" " + start.format(MERIDIEM).uppercase()) }
                    },
                    color = primary,
                    maxLines = 1,
                )
                if (end != null) {
                    val sameHalf = (start.hour < 12) == (end.hour < 12)
                    Text(
                        "to " + (if (sameHalf) end.format(CLOCK) else end.format(TIME)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // the portal sent something we can't read, show it as is rather than a dash
            s.from.isNotBlank() -> Text(s.from.trim(), style = MaterialTheme.typography.labelLarge, color = primary, maxLines = 2)
            else -> Text("TBA", style = bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Tag(text: String) {
    Spacer(Modifier.width(8.dp))
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
private fun SeatBadge(p: Paper, fresh: Boolean) {
    val s = p.slot
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val inner = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
    if (s.seated) {
        Surface(shape = shape, color = if (fresh) scheme.tertiaryContainer else scheme.secondaryContainer) {
            Column(Modifier.widthIn(min = 56.dp).then(inner), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.room.ifBlank { "Room ?" }, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                if (s.seat.isNotBlank()) Text("seat ${s.seat}", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                if (fresh) Text("just in", style = MaterialTheme.typography.labelSmall, color = scheme.tertiary)
            }
        }
    } else {
        Column(Modifier.widthIn(min = 56.dp).dashed(scheme.outline, 12.dp).then(inner), horizontalAlignment = Alignment.CenterHorizontally) {
            Ic(R.drawable.ic_event_seat, null, Modifier.size(16.dp), tint = scheme.onSurfaceVariant)
            Text("seat soon", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

private fun Modifier.dashed(color: Color, radius: Dp) = drawBehind {
    val w = 1.dp.toPx()
    drawRoundRect(
        color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

@Composable
private fun PendingRow(ev: ExamEvent) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.widthIn(min = 64.dp), contentAlignment = Alignment.Center) {
            Ic(R.drawable.ic_calendar_month, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${ev.short} date sheet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Not out yet" + (ev.from?.let { " · starts around ${dayOf(it).format(SHORT)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DoneHeader(count: Int, open: Boolean, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    Surface(onClick = onToggle, color = Color.Transparent, modifier = Modifier.padding(top = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().semantics {
                heading()
                stateDescription = if (open) "expanded" else "collapsed"
            }.padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Done · ${if (count == 1) "1 paper" else "$count papers"}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Ic(R.drawable.ic_expand_more, null, Modifier.rotate(turn), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

// the sheet: one paper, or every paper of a day tapped on the strip

@Composable
fun ExamSheet(view: ExamsView) {
    val target = view.sheet ?: return
    val papers = when {
        target.startsWith("p:") -> view.papers.filter { it.key == target.removePrefix("p:") }
        target.startsWith("d:") -> view.papers.filter { it.day?.toString() == target.removePrefix("d:") }
        else -> emptyList()
    }.sortedWith(compareBy(nullsLast()) { it.start })
    // a refresh can take the paper away under an open sheet
    if (papers.isEmpty()) return
    ModalBottomSheet(onDismissRequest = { view.openSheet(null) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            papers.forEachIndexed { i, p ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 20.dp))
                PaperDetail(p, view)
            }
        }
    }
}

@Composable
private fun PaperDetail(p: Paper, view: ExamsView) {
    val plan = view.plan
    val s = p.slot
    val status = plan.status(p)
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val haptics = LocalHapticFeedback.current
    Text(
        listOfNotNull(p.event.short, p.event.outOf?.let { "out of $it" }, "done".takeIf { status == Paper.Status.Done }).joinToString(" · "),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(4.dp))
    Text(subjectOf(p), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    if (s.code.isNotBlank()) Text(s.code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Fact(R.drawable.ic_calendar_month, p.day?.format(DAY) ?: "Date not out yet", p.day?.let { relative(it, plan.today) })
        Fact(R.drawable.ic_schedule, window(p) ?: "Time not out yet", p.duration?.toMinutes()?.let(::spokenLength))
        if (status != Paper.Status.Done) Fact(R.drawable.ic_timer, countdown(p, plan.now) { it.format(TIME) }.line(), null)
        if (s.seated) {
            Fact(R.drawable.ic_meeting_room, s.room.takeIf { it.isNotBlank() }?.let { "Room $it" } ?: "Room not out yet", null)
            Fact(R.drawable.ic_event_seat, s.seat.takeIf { it.isNotBlank() }?.let { "Seat $it" } ?: "Seat not out yet", null)
        } else if (status != Paper.Status.Done) {
            Fact(R.drawable.ic_event_seat, "Room and seat not out yet", "They usually come out a day before. This page checks more often as the day gets close.")
        }
        plan.clashes[p.key]?.takeIf { status != Paper.Status.Done }?.let {
            Fact(R.drawable.ic_warning, "Clashes with ${subjectOf(it)}", window(it), MaterialTheme.colorScheme.error)
        }
    }
    Spacer(Modifier.height(20.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (status == Paper.Status.Upcoming && p.day != null) {
            Button(onClick = { haptics.performHapticFeedback(HapticFeedbackType.Confirm); addToCalendar(context, p) }) {
                Ic(R.drawable.ic_edit_calendar, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add to calendar")
            }
        }
        view.semester?.let { sem ->
            OutlinedButton(onClick = { view.openSheet(null); nav.push(Route.Subject(sem.code, s.code)) }) { Text("Subject") }
        }
    }
}

private fun spokenLength(m: Long) = when {
    m < 60 -> "$m minutes"
    m == 60L -> "1 hour"
    m % 60 == 0L -> "${m / 60} hours"
    else -> "${minutes(m)} long"
}

@Composable
private fun Fact(icon: Int, text: String, sub: String?, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
        Ic(icon, null, Modifier.padding(top = 2.dp).size(20.dp), tint = tint)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
