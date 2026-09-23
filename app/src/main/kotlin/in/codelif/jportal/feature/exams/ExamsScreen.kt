package `in`.codelif.jportal.feature.exams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.R
import `in`.codelif.jportal.data.Resource
import `in`.codelif.jportal.feature.attendance.titleCase
import `in`.codelif.jportal.feature.subject.byCode
import `in`.codelif.jportal.ui.LocalBottomInset
import `in`.codelif.jportal.ui.components.Ic
import `in`.codelif.jportal.ui.components.MessageState
import `in`.codelif.jportal.ui.components.ScreenScaffold
import `in`.codelif.jportal.ui.components.SectionHeader
import `in`.codelif.jportal.ui.components.StaleNotice
import `in`.codelif.jportal.ui.components.rememberSemester
import `in`.codelif.jportal.ui.components.resourceStates
import `in`.codelif.jportal.ui.theme.NumberStyle
import `in`.codelif.ktjiit.model.ExamEvent
import `in`.codelif.ktjiit.model.ExamSlot
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private data class Paper(val event: ExamEvent, val slot: ExamSlot) {
    val start: LocalDateTime? get() = slot.day?.let { d -> slot.start?.let { d.atTime(it) } ?: d.atStartOfDay() }
}

@Composable
fun ExamsScreen() {
    val repo = LocalGraph.current.repo
    val sel = rememberSemester()
    val semsStore = repo.examSemesters
    val sems by semsStore.state.collectAsState()
    LaunchedEffect(Unit) { semsStore.refresh() }
    val sem = sems.data?.let { it.byCode(sel.selected?.code) ?: it.firstOrNull() }

    val eventsStore = sem?.let { s -> remember(s.id) { repo.examEvents(s) } }
    // no exam semester at all is an answer too, not a reason to spin forever
    val events = eventsStore?.state?.collectAsState()?.value
        ?: if (sems.data != null) Resource(data = emptyList(), fetchedAt = sems.fetchedAt) else sems.map { emptyList<ExamEvent>() }
    LaunchedEffect(eventsStore) { eventsStore?.refresh() }

    val papers = events.data.orEmpty().flatMap { ev ->
        key(ev.id) {
            val st = remember { repo.examSchedule(ev) }
            LaunchedEffect(st) { st.refresh() }
            st.state.collectAsState().value.data.orEmpty().map { Paper(ev, it) }
        }
    }
    val now = LocalDateTime.now()
    val upcoming = papers.filter { (it.start ?: LocalDateTime.MAX) >= now.minusHours(3) }.sortedBy { it.start }
    val past = papers.filter { (it.start ?: LocalDateTime.MAX) < now.minusHours(3) }.sortedByDescending { it.start }

    val refresh = {
        semsStore.refresh(force = true)
        eventsStore?.refresh(force = true)
        events.data.orEmpty().forEach { repo.examSchedule(it).refresh(force = true) }
        Unit
    }

    ScreenScaffold(
        title = "Exams",
        subtitle = sem?.code?.let { `in`.codelif.jportal.ui.components.prettySemester(it) },
        refreshing = events.refreshing && events.data != null,
        onRefresh = refresh,
        bottomPadding = LocalBottomInset.current,
    ) {
        item("stale") { StaleNotice(events, refresh) }
        if (resourceStates(events, refresh)) {
            if (papers.isEmpty()) {
                item("none") {
                    MessageState(
                        R.drawable.ic_celebration, "No exams on the calendar",
                        events.data.orEmpty().takeIf { it.isNotEmpty() }?.joinToString("\n") { it.description.ifBlank { it.code } }
                            ?.let { "Events this semester:\n$it" } ?: "Enjoy it while it lasts.",
                    )
                }
            }
            upcoming.firstOrNull()?.let { next -> item("next") { NextExam(next) } }
            if (upcoming.size > 1) {
                item("up-h") { SectionHeader("Coming up") }
                upcoming.drop(1).groupBy { it.slot.day }.forEach { (day, list) ->
                    item("day-$day") { DayHeader(day) }
                    items(list, key = { "u-${it.event.id}-${it.slot.code}-${it.slot.date}" }) { PaperRow(it) }
                }
            }
            if (past.isNotEmpty()) {
                item("past-h") { SectionHeader("Done") }
                items(past, key = { "p-${it.event.id}-${it.slot.code}-${it.slot.date}" }) { PaperRow(it, faded = true) }
            }
        }
    }
}

private val DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM")
private val TIME = DateTimeFormatter.ofPattern("h:mm a")

@Composable
private fun NextExam(p: Paper) {
    // ticks once a minute so "in 2h 14m" stays honest while the screen is open
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
                start?.let { Pill(R.drawable.ic_schedule, "${it.format(DateTimeFormatter.ofPattern("EEE d MMM"))} · ${p.slot.start?.format(TIME) ?: p.slot.from}") }
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
    Text(
        day?.format(DAY) ?: "Date to be announced",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun PaperRow(p: Paper, faded: Boolean = false) {
    val alpha = if (faded) 0.6f else 1f
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(p.slot.day?.dayOfMonth?.toString() ?: "–", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                    Text(p.slot.day?.format(DateTimeFormatter.ofPattern("MMM"))?.uppercase() ?: "", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.slot.subjectName.titleCase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
                Text(
                    listOfNotNull(
                        p.event.code,
                        p.slot.start?.let { s -> s.format(TIME) + (p.slot.end?.let { " – " + it.format(TIME) } ?: "") },
                        p.slot.room.takeIf { it.isNotBlank() }?.let { r -> r + (p.slot.seat.takeIf { it.isNotBlank() }?.let { " · seat $it" } ?: "") },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
