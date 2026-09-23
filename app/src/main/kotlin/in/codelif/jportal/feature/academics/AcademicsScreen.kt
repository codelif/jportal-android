package `in`.codelif.jportal.feature.academics

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.R
import `in`.codelif.jportal.domain.Gpa
import `in`.codelif.jportal.feature.attendance.titleCase
import `in`.codelif.jportal.feature.subject.fmt
import `in`.codelif.jportal.ui.LocalBottomInset
import `in`.codelif.jportal.ui.LocalNavigator
import `in`.codelif.jportal.ui.components.Frog
import `in`.codelif.jportal.ui.components.GpaChart
import `in`.codelif.jportal.ui.components.GradeLetter
import `in`.codelif.jportal.ui.components.Group
import `in`.codelif.jportal.ui.components.Ic
import `in`.codelif.jportal.ui.components.MessageState
import `in`.codelif.jportal.ui.components.ScreenScaffold
import `in`.codelif.jportal.ui.components.SectionHeader
import `in`.codelif.jportal.ui.components.StaleNotice
import `in`.codelif.jportal.ui.components.prettySemester
import `in`.codelif.jportal.ui.components.rememberSemester
import `in`.codelif.jportal.ui.components.resourceStates
import `in`.codelif.jportal.ui.nav.Route
import `in`.codelif.jportal.ui.theme.LocalExtraColors
import `in`.codelif.jportal.ui.theme.NumberStyle
import `in`.codelif.ktjiit.model.Credits

enum class AcademicsView(val label: String) { Overview("Overview"), Marks("Marks"), Subjects("Subjects") }

@Composable
fun AcademicsScreen() {
    val repo = LocalGraph.current.repo
    val data = rememberAcademics(repo)
    val credits by repo.credits.state.collectAsState()
    val program by repo.program.state.collectAsState()
    LaunchedEffect(Unit) { repo.credits.refresh(); repo.program.refresh() }
    var view by rememberSaveable { mutableStateOf(AcademicsView.Overview) }
    // each view keeps its own scroll
    val lists = AcademicsView.entries.associateWith { rememberLazyListState() }
    // projected sgpa per semester, survives rotation and tab switches
    val projections = rememberSaveable(saver = mapSaver) { mutableStateMapOf() }

    // marks start on whatever semester the rest of the app is on
    val marks = rememberMarks(rememberSemester().selected?.code)
    var query by rememberSaveable { mutableStateOf("") }
    var filters by rememberSaveable(saver = filterSaver) { mutableStateOf(emptySet()) }

    val refresh = {
        data.refresh()
        repo.credits.refresh(force = true); repo.program.refresh(force = true)
        if (view == AcademicsView.Marks) marks.refresh()
    }

    ScreenScaffold(
        title = "Academics",
        refreshing = if (view == AcademicsView.Marks) marks.report.refreshing && marks.report.data != null
        else data.results.refreshing && data.results.data != null,
        onRefresh = refresh,
        listState = lists.getValue(view),
        bottomPadding = LocalBottomInset.current,
        pinned = {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                AcademicsView.entries.forEachIndexed { i, v ->
                    SegmentedButton(
                        selected = view == v,
                        onClick = { view = v },
                        shape = SegmentedButtonDefaults.itemShape(i, AcademicsView.entries.size),
                        icon = {},
                    ) { Text(v.label) }
                }
            }
        },
    ) {
        when (view) {
            AcademicsView.Overview -> overview(data, credits.data, program.data?.maxSemesters, projections, refresh)
            AcademicsView.Marks -> marksContent(marks)
            AcademicsView.Subjects -> subjects(data, query, { query = it }, filters, { filters = it })
        }
    }
}

private val mapSaver = androidx.compose.runtime.saveable.Saver<androidx.compose.runtime.snapshots.SnapshotStateMap<Int, Double>, Map<String, Double>>(
    save = { m -> m.mapKeys { it.key.toString() } },
    restore = { m -> mutableStateMapOf<Int, Double>().apply { m.forEach { (k, v) -> put(k.toInt(), v) } } },
)

// overview: the jportal grades page, headline numbers and the what-if chart

private fun LazyListScope.overview(
    data: Academics,
    credits: Credits?,
    maxSemesters: Int?,
    projections: androidx.compose.runtime.snapshots.SnapshotStateMap<Int, Double>,
    refresh: () -> Unit,
) {
    val results = data.results
    item("stale") { StaleNotice(results, refresh) }
    if (!resourceStates(results, refresh, empty = { it.isEmpty() }, emptyTitle = "No results yet, first semester?")) return
    val real = results.data.orEmpty()
    val maxSem = maxSemesters?.takeIf { it > 0 } ?: 8
    val nextCredits = credits?.registeredCredits?.toDouble()?.takeIf { it > 0 } ?: real.lastOrNull()?.courseCredits ?: 20.0
    val points = Gpa.points(real, projections, nextCredits, maxSem)
    val future = ((real.maxOfOrNull { it.semester } ?: 0) + 1..maxSem).toList()
    item("hero") {
        val latest = real.maxByOrNull { it.semester }
        val projected = points.lastOrNull()?.takeIf { it.projected }
        Headline(latest?.cgpa, latest?.sgpa, projected?.cgpa, credits?.earned, credits?.required)
    }
    item("chart") {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(Modifier.padding(vertical = 16.dp, horizontal = 8.dp)) {
                GpaChart(points, future, onProject = { s, v -> projections[s] = v })
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (projections.isEmpty()) "Tap a future semester, then drag to see where your CGPA lands"
                        else "Dashed lines are what-ifs, not results",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (projections.isNotEmpty()) TextButton(onClick = { projections.clear() }) { Text("Reset") }
                }
            }
        }
    }
    // semesters and grade cards used to be two lists of the same thing, now one row per semester opens its card
    val graded = data.semesters.filter { it.result != null }
    if (graded.isNotEmpty()) {
        item("sems-h") { SectionHeader("Semesters") }
        item("sems") {
            val nav = LocalNavigator.current
            Group(Modifier.padding(bottom = 16.dp)) {
                graded.forEach { s -> row { SemesterRow(s) { nav.push(Route.GradeCard(s.code)) } } }
            }
        }
    }
}

@Composable
private fun Headline(cgpa: Double?, sgpa: Double?, projected: Double?, earned: Int?, required: Int?) {
    val extra = LocalExtraColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Big("CGPA", cgpa, extra.cgpa)
            Big("SGPA", sgpa, extra.sgpa)
            if (projected != null) Big("Projected", projected, MaterialTheme.colorScheme.tertiary)
        }
        if (earned != null && required != null && required > 0) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$earned", style = NumberStyle, color = extra.credits)
                Text(" / $required credits", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { earned.toFloat() / required },
                color = extra.credits,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small),
            )
        }
    }
}

@Composable
private fun Big(label: String, value: Double?, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value?.let { "%.2f".format(it) } ?: "–", style = NumberStyle, color = color)
    }
}

@Composable
private fun SemesterRow(s: SemesterInfo, onClick: () -> Unit) {
    val extra = LocalExtraColors.current
    val r = s.result ?: return
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.number?.let { "Semester $it" } ?: prettySemester(s.code), style = MaterialTheme.typography.titleMedium)
                Text(
                    "${prettySemester(s.code)} · ${fmt(r.earnedCredits)} credits",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Figure("%.1f".format(r.sgpa), "SGPA", extra.sgpa)
            Spacer(Modifier.width(16.dp))
            Figure("%.2f".format(r.cgpa), "CGPA", extra.cgpa)
            Spacer(Modifier.width(4.dp))
            Ic(R.drawable.ic_chevron_right, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** jportal's value-over-label column */
@Composable
fun Figure(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// subjects: everything ever registered, searchable

enum class SubjectFilter(val label: String) { Current("This semester"), Labs("Labs"), Graded("Graded") }

private val filterSaver = androidx.compose.runtime.saveable.Saver<androidx.compose.runtime.MutableState<Set<SubjectFilter>>, List<String>>(
    save = { st -> st.value.map { it.name } },
    restore = { l -> mutableStateOf(l.map { SubjectFilter.valueOf(it) }.toSet()) },
)

private fun LazyListScope.subjects(data: Academics, q: String, onQuery: (String) -> Unit, f: Set<SubjectFilter>, onFilters: (Set<SubjectFilter>) -> Unit) {
    item("search") { SubjectSearch(q, onQuery, f, onFilters) }
    item("results") { SubjectResults(data, q, f) }
}

@Composable
private fun SubjectSearch(q: String, onQuery: (String) -> Unit, f: Set<SubjectFilter>, onFilters: (Set<SubjectFilter>) -> Unit) {
    Column(Modifier.padding(top = 4.dp)) {
        TextField(
            value = q,
            onValueChange = onQuery,
            placeholder = { Text("Search subjects, teachers, codes") },
            leadingIcon = { Ic(R.drawable.ic_search, null) },
            trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Ic(R.drawable.ic_close, "Clear") } },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubjectFilter.entries.forEach { x ->
                FilterChip(selected = x in f, onClick = { onFilters(if (x in f) f - x else f + x) }, label = { Text(x.label) })
            }
        }
    }
}

@Composable
private fun SubjectResults(data: Academics, query: String, f: Set<SubjectFilter>) {
    val nav = LocalNavigator.current
    val q = query.trim().lowercase()
    val groups = data.semesters.map { s ->
        s to s.subjects.filter { sub ->
            (SubjectFilter.Current !in f || s.current) &&
                (SubjectFilter.Labs !in f || sub.lab) &&
                (SubjectFilter.Graded !in f || sub.grade != null) &&
                (q.isEmpty() || sub.name.lowercase().contains(q) || sub.code.lowercase().contains(q) ||
                    sub.teachers.any { it.second.lowercase().contains(q) })
        }
    }.filter { it.second.isNotEmpty() }
    Column {
        when {
            groups.isEmpty() && data.loading -> `in`.codelif.jportal.ui.components.CenteredLoading()
            groups.isEmpty() -> MessageState(Frog.Look, if (q.isEmpty()) "Nothing here" else "Nothing matches “${query.trim()}”")
        }
        groups.forEach { (s, list) ->
            SectionHeader(listOfNotNull(s.number?.let { "Semester $it" }, prettySemester(s.code), "now".takeIf { s.current }).joinToString(" · "))
            Group {
                list.forEach { sub -> row { SubjectRow(sub) { nav.push(Route.Subject(sub.semesterCode, sub.code)) } } }
            }
        }
    }
}

@Composable
private fun SubjectRow(s: SubjectInfo, onClick: () -> Unit) {
    val extra = LocalExtraColors.current
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(s.name.titleCase(), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    s.code + if (s.audit) " · audit" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                s.teachers.forEach { (k, name) ->
                    Text(
                        "${COMPONENT[k] ?: k}: ${name.titleCase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            // graded subjects show the grade, running ones show credits big, the way jportal's subjects page did
            if (s.grade != null) {
                GradeLetter(s.grade, size = 44.dp)
            } else {
                Figure(fmt(s.credits), "credits", extra.credits)
            }
        }
    }
}

val COMPONENT = mapOf("L" to "Lecture", "T" to "Tutorial", "P" to "Practical")
