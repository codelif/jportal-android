package `in`.codelif.jportal.feature.academics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.R
import `in`.codelif.jportal.data.Resource
import `in`.codelif.jportal.feature.attendance.titleCase
import `in`.codelif.jportal.feature.subject.byCode
import `in`.codelif.jportal.ui.components.Frog
import `in`.codelif.jportal.ui.components.Group
import `in`.codelif.jportal.ui.components.Ic
import `in`.codelif.jportal.ui.components.MessageState
import `in`.codelif.jportal.ui.components.CenteredLoading
import `in`.codelif.jportal.ui.components.SectionHeader
import `in`.codelif.jportal.ui.components.SemesterPicker
import `in`.codelif.jportal.ui.components.StaleNotice
import `in`.codelif.jportal.ui.components.resourceStates
import `in`.codelif.jportal.ui.theme.NumberStyle
import `in`.codelif.ktjiit.model.ExamEvent
import `in`.codelif.ktjiit.model.ExamSlot
import `in`.codelif.ktjiit.model.Semester
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class Paper(val event: ExamEvent, val slot: ExamSlot) {
    val start: LocalDateTime? get() = slot.day?.let { d -> slot.start?.let { d.atTime(it) } ?: d.atStartOfDay() }
}

/** the date sheet for one exam semester, every event's papers flattened */
class ExamsView(
    val semesters: List<Semester>,
    val semester: Semester?,
    val events: Resource<List<ExamEvent>>,
    val papers: List<Paper>,
    /** events are in but some date sheets aren't, so no papers doesn't mean no exams yet */
    val loadingPapers: Boolean,
    val pick: (String) -> Unit,
    val refresh: () -> Unit,
)

@Composable
fun rememberExams(startAt: String?): ExamsView {
    val repo = LocalGraph.current.repo
    val sems by repo.examSemesters.state.collectAsState()
    LaunchedEffect(Unit) { repo.examSemesters.refresh() }
    var picked by rememberSaveable(startAt) { mutableStateOf(startAt) }
    val list = sems.data.orEmpty()
    val sem = list.byCode(picked) ?: list.firstOrNull()
    val store = sem?.let { s -> remember(s.id) { repo.examEvents(s) } }
    LaunchedEffect(store) { store?.refresh() }
    // no exam semester at all is an answer too, not a reason to spin forever
    val events = store?.state?.collectAsState()?.value
        ?: if (sems.data != null) Resource(data = emptyList(), fetchedAt = sems.fetchedAt, checked = true) else sems.map { emptyList<ExamEvent>() }

    val sheets = events.data.orEmpty().map { ev ->
        key(ev.id) {
            val st = remember { repo.examSchedule(ev) }
            LaunchedEffect(st) { st.refresh() }
            ev to st.state.collectAsState().value
        }
    }
    val papers = sheets.flatMap { (ev, r) -> r.data.orEmpty().map { Paper(ev, it) } }
    val loadingPapers = sheets.any { (_, r) -> r.data == null && r.error == null }

    val refresh = {
        repo.examSemesters.refresh(force = true)
        store?.refresh(force = true)
        events.data.orEmpty().forEach { repo.examSchedule(it).refresh(force = true) }
        Unit
    }
    return ExamsView(list, sem, events, papers, loadingPapers, { picked = it }, refresh)
}

fun LazyListScope.examsContent(view: ExamsView) {
    item("pick") { SemesterPicker(view.semesters, view.semester, view.pick, view.events.fetchedAt) }
    item("stale") { StaleNotice(view.events, view.refresh) }
    if (!resourceStates(view.events, view.refresh)) return
    if (view.papers.isEmpty()) {
        if (view.loadingPapers) item("loading") { CenteredLoading() }
        else item("none") { MessageState(Frog.Sleep, "No exams on the calendar", "Enjoy it while it lasts.") }
        return
    }
    // a paper stays "up next" through its sitting, three hours covers the longest one
    val cutoff = LocalDateTime.now().minusHours(3)
    val (upcoming, past) = view.papers.partition { (it.start ?: LocalDateTime.MAX) >= cutoff }
    val ahead = upcoming.sortedWith(compareBy(nullsLast()) { it.start })

    ahead.firstOrNull()?.let { next -> item("next") { NextExam(next) } }
    if (ahead.size > 1) {
        item("up-h") { SectionHeader("Coming up") }
        ahead.drop(1).groupBy { it.slot.day }.forEach { (day, list) ->
            item("day-$day") { DayHeader(day) }
            item("papers-$day") { Group { list.forEach { p -> row { PaperRow(p, done = false) } } } }
        }
    }
    if (past.isNotEmpty()) {
        item("past-h") { SectionHeader("Done") }
        item("past") { Group { past.sortedByDescending { it.start }.forEach { p -> row { PaperRow(p, done = true) } } } }
    }
}

private val DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM")
private val SHORT = DateTimeFormatter.ofPattern("EEE d MMM")
private val TIME = DateTimeFormatter.ofPattern("h:mm a")
private val CLOCK = DateTimeFormatter.ofPattern("h:mm")
private val MERIDIEM = DateTimeFormatter.ofPattern("a")
private val MONTH = DateTimeFormatter.ofPattern("MMM")

@Composable
private fun NextExam(p: Paper) {
    // ticks twice a minute so "in 2h 14m" stays honest while the screen is open
    val now by produceState(LocalDateTime.now()) { while (true) { delay(30_000); value = LocalDateTime.now() } }
    val start = p.start
    val (big, small) = countdown(now, start)
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(24.dp)) {
            Text("Next up · ${p.event.code}", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text(p.slot.subjectName.titleCase(), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(big, style = NumberStyle)
                Spacer(Modifier.width(8.dp))
                Text(small, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                start?.let { Pill(R.drawable.ic_schedule, "${it.format(SHORT)} · ${p.slot.start?.format(TIME) ?: p.slot.from}") }
                if (p.slot.room.isNotBlank()) Pill(R.drawable.ic_meeting_room, p.slot.room + if (p.slot.seat.isNotBlank()) " · ${p.slot.seat}" else "")
            }
        }
    }
}

private fun countdown(now: LocalDateTime, start: LocalDateTime?): Pair<String, String> {
    if (start == null) return "?" to "date not announced"
    val d = Duration.between(now, start)
    val days = ChronoUnit.DAYS.between(now.toLocalDate(), start.toLocalDate())
    return when {
        d.isNegative -> "Now" to "good luck"
        days >= 2 -> "$days" to "days to go"
        days == 1L -> "Tomorrow" to start.toLocalTime().format(TIME)
        d.toHours() >= 1 -> "${d.toHours()}h ${d.toMinutes() % 60}m" to "to go"
        else -> "${d.toMinutes()}m" to "to go"
    }
}

@Composable
private fun Pill(icon: Int, text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Ic(icon, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate?) {
    val days = day?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            day?.format(DAY) ?: "Date to be announced",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        days?.let {
            Text(
                when {
                    it <= 0L -> "Today"
                    it == 1L -> "Tomorrow"
                    else -> "in $it days"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** coming up rows lead with the time, the day's already above them. done rows lead with the date */
@Composable
private fun PaperRow(p: Paper, done: Boolean) {
    val s = p.slot
    Row(
        Modifier.fillMaxWidth().alpha(if (done) 0.6f else 1f).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.widthIn(min = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val bold = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            when {
                done -> {
                    Text(s.day?.dayOfMonth?.toString() ?: "–", style = bold, color = MaterialTheme.colorScheme.primary)
                    Text(s.day?.format(MONTH)?.uppercase().orEmpty(), style = MaterialTheme.typography.labelSmall)
                }
                s.start != null -> {
                    Text(s.start!!.format(CLOCK), style = bold, color = MaterialTheme.colorScheme.primary)
                    Text(s.start!!.format(MERIDIEM).uppercase(), style = MaterialTheme.typography.labelSmall)
                }
                else -> Text("–", style = bold, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.subjectName.titleCase(), style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(
                    p.event.code,
                    if (done) s.start?.format(TIME) else s.end?.let { "till " + it.format(TIME) },
                    s.room.takeIf { it.isNotBlank() }?.let { r -> r + (s.seat.takeIf { it.isNotBlank() }?.let { " · seat $it" } ?: "") },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
