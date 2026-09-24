package `in`.codelif.jportal.feature.subject

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.data.Resource
import `in`.codelif.jportal.feature.me.InfoRow
import `in`.codelif.jportal.ui.components.GradeLetter
import `in`.codelif.jportal.ui.components.Group
import androidx.compose.material3.TextButton
import `in`.codelif.jportal.R
import `in`.codelif.jportal.domain.AttendanceMath
import `in`.codelif.jportal.feature.attendance.titleCase
import `in`.codelif.jportal.ui.LocalNavigator
import `in`.codelif.jportal.ui.components.AttendanceCalendar
import `in`.codelif.jportal.ui.components.AttendanceRing
import `in`.codelif.jportal.ui.components.Ic
import `in`.codelif.jportal.ui.components.ScreenScaffold
import `in`.codelif.jportal.ui.components.SectionHeader
import `in`.codelif.jportal.ui.components.StaleNotice
import `in`.codelif.jportal.ui.components.TrendChart
import `in`.codelif.jportal.ui.components.prettySemester
import `in`.codelif.jportal.ui.nav.Route
import `in`.codelif.jportal.ui.nav.shared
import `in`.codelif.jportal.ui.theme.LocalExtraColors
import `in`.codelif.jportal.ui.theme.NumberStyle
import `in`.codelif.ktjiit.marks.Score
import `in`.codelif.ktjiit.model.ClassRecord
import `in`.codelif.ktjiit.model.Semester
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

fun List<Semester>.byCode(code: String?): Semester? = firstOrNull { it.code == code }

@Composable
fun SubjectScreen(route: Route.Subject) {
    val graph = LocalGraph.current
    val repo = graph.repo
    val nav = LocalNavigator.current
    val target by graph.prefs.targetState.collectAsState()

    // attendance, when the portal still keeps it for this semester
    val meta by repo.attendanceMeta.state.collectAsState()
    val attSem = meta.data?.semesters?.byCode(route.semesterCode)
    val semNumber = meta.data?.header?.semesterNumber.orEmpty()
    val detailStore = attSem?.let { s -> remember(s.id) { repo.attendance(s, semNumber) } }
    val detail = detailStore?.state?.collectAsState()?.value
    LaunchedEffect(detailStore) { detailStore?.refresh() }
    val subject = detail?.data?.subjects?.firstOrNull { it.code == route.subjectCode }
    val dailyStore = if (attSem != null && subject != null) remember(attSem.id, subject.subjectId) { repo.daily(attSem, subject) } else null
    val daily = dailyStore?.state?.collectAsState()?.value ?: Resource()
    LaunchedEffect(dailyStore) { dailyStore?.refresh() }

    val classes = daily.data?.classes.orEmpty()
    val tally = remember(classes) { AttendanceMath.tally(classes) }
    val newestFirst = remember(classes) { classes.sortedWith(compareByDescending<ClassRecord> { it.date }.thenByDescending { it.start }) }
    val percent = if (tally.total > 0) tally.percent.toFloat() else (subject?.percent ?: 0.0).toFloat()
    val calendar = remember(classes) { AttendanceMath.calendar(classes) }
    val trend = remember(classes) { AttendanceMath.trend(classes).map { it.second.toFloat() } }
    var day by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedDay = day?.let(LocalDate::parse)

    // teachers and credits live under the subject registrations
    val subjSems by repo.subjectSemesters.state.collectAsState()
    LaunchedEffect(Unit) { repo.subjectSemesters.refresh() }
    val faculty = subjSems.data?.byCode(route.semesterCode)?.let { s ->
        val st = remember(s.id) { repo.subjects(s) }
        LaunchedEffect(st) { st.refresh() }
        st.state.collectAsState().value.data?.rows?.filter { it.code == route.subjectCode }
    }.orEmpty()

    // marks for this subject from the semester's marks pdf
    val marksSems by repo.marksSemesters.state.collectAsState()
    LaunchedEffect(Unit) { repo.marksSemesters.refresh() }
    val marks = marksSems.data?.byCode(route.semesterCode)?.let { s ->
        val st = remember(s.id) { repo.marks(s) }
        LaunchedEffect(st) { st.refresh() }
        st.state.collectAsState().value.data?.subjects?.firstOrNull { it.code == route.subjectCode }
    }

    // and the grade, once the semester is done
    val gradeSems by repo.gradeSemesters.state.collectAsState()
    LaunchedEffect(Unit) { repo.gradeSemesters.refresh() }
    val grade = gradeSems.data?.byCode(route.semesterCode)?.let { s ->
        val st = remember(s.id) { repo.gradeCard(s) }
        LaunchedEffect(st) { st.refresh() }
        st.state.collectAsState().value.data?.entries?.firstOrNull { it.code == route.subjectCode }
    }

    val name = subject?.name ?: faculty.firstOrNull()?.name ?: grade?.name ?: marks?.name ?: return Placeholder { nav.pop() }
    val credits = faculty.firstOrNull()?.credits ?: grade?.credits

    ScreenScaffold(
        title = name.titleCase(),
        subtitle = "${route.subjectCode} · ${prettySemester(route.semesterCode)}",
        onBack = { nav.pop() },
        refreshing = daily.refreshing,
        onRefresh = { dailyStore?.refresh(force = true); detailStore?.refresh(force = true) },
    ) {
        item("stale") { StaleNotice(daily, { dailyStore?.refresh(force = true) }) }
        item("hero") {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (subject != null) {
                    // same rule as the list card: nothing counted and nothing from the portal means it hasn't started
                    val started = tally.total > 0 || (subject.percent ?: 0.0) > 0.0
                    AttendanceRing(if (started) percent else 0f, target, Modifier.shared("ring-${subject.subjectId}"), size = 184.dp, stroke = 16.dp, fill = false) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (started) "${percent.roundToInt()}%" else "–", style = NumberStyle, color = if (started) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
                            if (tally.total > 0) Text("${tally.attended} of ${tally.total}", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (tally.total > 0) Verdict(tally.canMiss(target), tally.mustAttend(target), target)
                    else if (!started) Text("No classes marked yet", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    grade?.grade?.takeIf { it.isNotBlank() }?.let { g ->
                        Spacer(Modifier.height(12.dp))
                        Text("Grade $g", style = MaterialTheme.typography.titleMedium, color = LocalExtraColors.current.grade(g))
                    }
                } else {
                    // no attendance kept for this semester any more, the grade is the headline
                    GradeLetter(grade?.grade.orEmpty(), size = 120.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        listOfNotNull(
                            credits?.let { "${fmt(it)} credits" },
                            grade?.gradePoint?.takeIf { it > 0 }?.let { "${fmt(it)} grade points" },
                        ).joinToString(" · ").ifEmpty { "Grade not out yet" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (subject != null) {
            item("components") { Components(subject, faculty.associate { it.component to it.facultyName }) }
        } else if (faculty.isNotEmpty()) {
            item("teachers-h") { SectionHeader("Taught by") }
            item("teachers") {
                Group {
                    faculty.filter { it.facultyName.isNotBlank() }.distinctBy { it.component }.forEach { f ->
                        row { InfoRow(`in`.codelif.jportal.feature.academics.COMPONENT[f.component] ?: f.component, f.facultyName.titleCase()) }
                    }
                }
            }
        }
        if (calendar.isNotEmpty()) {
            item("cal-h") { SectionHeader("Calendar") }
            item("cal") {
                AttendanceCalendar(calendar, selectedDay, { day = if (it == selectedDay) null else it.toString() }, Modifier.padding(horizontal = 8.dp))
            }
            item("day") {
                AnimatedVisibility(selectedDay != null) {
                    DayClasses(classes.filter { it.date == selectedDay })
                }
            }
        }
        if (trend.size >= 3) {
            item("trend-h") { SectionHeader("How it's been going") }
            item("trend") { TrendChart(trend, target, Modifier.padding(horizontal = 20.dp)) }
        }
        if (marks != null && marks.scores.any { it.marks != Score.NotApplicable }) {
            item("marks-h") {
                SectionHeader("Marks") {
                    TextButton(onClick = { nav.push(Route.Marks(route.semesterCode)) }) { Text("All marks") }
                }
            }
            item("marks") { MarksStrip(marks.scores.filter { it.marks != Score.NotApplicable || it.weighted != Score.NotApplicable }) }
        }
        if (subject != null && faculty.isNotEmpty()) {
            item("credits") {
                val c = faculty.first().credits
                Text(
                    "${fmt(c)} credits" + if (faculty.first().isAudit) " · audit" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
        if (classes.isNotEmpty()) {
            item("classes-h") { SectionHeader("Every class") }
            items(newestFirst, key = { it.datetime + it.present + it.takenBy }, contentType = { "class" }) { c ->
                ClassRow(c)
            }
        }
    }
}

@Composable
private fun Placeholder(onBack: () -> Unit) = ScreenScaffold(title = "", onBack = onBack) {
    item { `in`.codelif.jportal.ui.components.CenteredLoading() }
}

@Composable
private fun Verdict(miss: Int, need: Int, target: Int) {
    val extra = LocalExtraColors.current
    val (text, color) = when {
        need == Int.MAX_VALUE -> "Can't reach $target% any more this semester" to MaterialTheme.colorScheme.error
        need > 0 -> "Attend the next $need ${if (need == 1) "class" else "classes"} to reach $target%" to MaterialTheme.colorScheme.error
        miss > 0 -> "You can miss $miss ${if (miss == 1) "class" else "classes"} and stay above $target%" to extra.good
        else -> "Exactly on $target%, the next absence drops you under" to extra.warn
    }
    Surface(shape = CircleShape, color = color.copy(alpha = 0.14f)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

@Composable
private fun Components(s: `in`.codelif.ktjiit.model.SubjectAttendance, faculty: Map<String, String>) {
    val rows = listOfNotNull(
        s.lectureComponent?.let { Triple("L", "Lecture", s.lecturePercent) },
        s.tutorialComponent?.let { Triple("T", "Tutorial", s.tutorialPercent) },
        s.practicalComponent?.let { Triple("P", "Practical", s.practicalPercent) },
    )
    if (rows.isEmpty()) return
    SectionHeader("Components")
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (k, name, pct) ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(36.dp)) {
                        Text(k, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 7.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleSmall)
                        faculty[k]?.let { Text(it.titleCase(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    pct?.let { Text("${it.roundToInt()}%", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) }
                }
            }
        }
    }
    Text(
        "Component percentages are the portal's own, updated once a day.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

private val DAY = DateTimeFormatter.ofPattern("EEE, d MMM")
private val TIME = DateTimeFormatter.ofPattern("h:mm a")

@Composable
private fun DayClasses(list: List<ClassRecord>) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        list.forEach { ClassRow(it, compact = true) }
    }
}

@Composable
private fun ClassRow(c: ClassRecord, compact: Boolean = false) {
    val extra = LocalExtraColors.current
    val color = if (c.isPresent) extra.good else MaterialTheme.colorScheme.error
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(horizontal = if (compact) 4.dp else 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(if (c.isPresent) R.drawable.ic_check_circle else R.drawable.ic_cancel, if (c.isPresent) "Present" else "Absent", Modifier.size(22.dp), color)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(c.date?.format(DAY) ?: c.datetime, style = MaterialTheme.typography.bodyLarge)
            val time = listOfNotNull(c.start?.format(TIME), c.end?.format(TIME)).joinToString(" – ")
            Text(
                listOf(time, c.classType.takeIf { it.isNotBlank() && it != "Regular" }, c.takenBy.titleCase().takeIf { it.isNotBlank() })
                    .filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MarksStrip(scores: List<`in`.codelif.ktjiit.marks.EventScore>) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        scores.forEach { e -> ScoreChip(e) }
    }
}

@Composable
fun ScoreChip(e: `in`.codelif.ktjiit.marks.EventScore, color: Color = MaterialTheme.colorScheme.surfaceContainerLow) {
    val extra = LocalExtraColors.current
    val (main, sub, tint) = when (val m = e.marks) {
        is Score.Value -> Triple(fmt(m.obtained), "of ${fmt(m.max)}", if (m.max > 0 && m.obtained / m.max >= 0.5) extra.good else MaterialTheme.colorScheme.error)
        Score.Absent -> Triple("A", "absent", MaterialTheme.colorScheme.error)
        else -> Triple("–", "", MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Surface(shape = MaterialTheme.shapes.medium, color = color, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(e.event, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(main, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = if (tint == MaterialTheme.colorScheme.onSurfaceVariant) Color.Unspecified else tint)
                Spacer(Modifier.width(4.dp))
                Text(sub, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

fun fmt(d: Double): String = if (d % 1.0 == 0.0) d.toInt().toString() else "%.1f".format(d)
