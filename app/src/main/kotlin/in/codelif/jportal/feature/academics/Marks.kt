package `in`.codelif.jportal.feature.academics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.data.Resource
import `in`.codelif.jportal.feature.attendance.titleCase
import `in`.codelif.jportal.feature.subject.ScoreChip
import `in`.codelif.jportal.feature.subject.byCode
import `in`.codelif.jportal.feature.subject.fmt
import `in`.codelif.jportal.ui.LocalNavigator
import `in`.codelif.jportal.ui.components.ScreenScaffold
import `in`.codelif.jportal.ui.components.SemesterPicker
import `in`.codelif.jportal.ui.components.StaleNotice
import `in`.codelif.jportal.ui.components.resourceStates
import `in`.codelif.jportal.ui.nav.Route
import `in`.codelif.jportal.ui.theme.NumberStyle
import `in`.codelif.ktjiit.marks.MarksReport
import `in`.codelif.ktjiit.marks.Score
import `in`.codelif.ktjiit.marks.SubjectMarks
import `in`.codelif.ktjiit.model.Semester

/** which semester's marks are up, and the marks themselves */
class MarksView(
    val semesters: List<Semester>,
    val semester: Semester?,
    val report: Resource<MarksReport>,
    val pick: (String) -> Unit,
    val refresh: () -> Unit,
)

@Composable
fun rememberMarks(startAt: String?): MarksView {
    val repo = LocalGraph.current.repo
    val sems by repo.marksSemesters.state.collectAsState()
    LaunchedEffect(Unit) { repo.marksSemesters.refresh() }
    var picked by rememberSaveable(startAt) { mutableStateOf(startAt) }
    val list = sems.data.orEmpty()
    val sem = list.byCode(picked) ?: list.firstOrNull()
    val store = sem?.let { s -> remember(s.id) { repo.marks(s) } }
    LaunchedEffect(store) { store?.refresh() }
    // no marks semesters at all is an answer too, not something to wait on
    val report = store?.state?.collectAsState()?.value
        ?: if (sems.data != null) Resource(data = MarksReport(`in`.codelif.ktjiit.marks.MarksStudent(), emptyList(), emptyList()), checked = true)
        else sems.map { MarksReport(`in`.codelif.ktjiit.marks.MarksStudent(), emptyList(), emptyList()) }
    return MarksView(list, sem, report, { picked = it }, { repo.marksSemesters.refresh(force = true); store?.refresh(force = true) })
}

/**
 * the marks pdf, read into something you'd actually want to look at. exam
 * columns keep the portal's own names and order, the college's scheme is the
 * college's business.
 */
fun LazyListScope.marksContent(view: MarksView) {
    item("pick") { SemesterPicker(view.semesters, view.semester, view.pick, view.report.fetchedAt) }
    item("stale") { StaleNotice(view.report, view.refresh) }
    if (!resourceStates(view.report, view.refresh, empty = { it.subjects.isEmpty() }, emptyTitle = "No marks uploaded yet")) return
    val report = view.report.data!!
    item("summary") { Summary(report) }
    items(report.subjects, key = { it.code }) { s -> SubjectMarksCard(s, view.semester?.code) }
}

private fun weighted(s: SubjectMarks) = s.scores.mapNotNull { it.weighted as? Score.Value }

@Composable
private fun Summary(report: MarksReport) {
    val all = report.subjects.flatMap(::weighted)
    val got = all.sumOf { it.obtained }
    val max = all.sumOf { it.max }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("So far, all subjects", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(fmt(got), style = NumberStyle)
                Text(" of ${fmt(max)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                Spacer(Modifier.weight(1f))
                if (max > 0) Text("${(got * 100 / max).toInt()}%", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun SubjectMarksCard(s: SubjectMarks, semesterCode: String?) {
    val nav = LocalNavigator.current
    val shown = s.scores.filter { it.marks != Score.NotApplicable || it.weighted != Score.NotApplicable }
    val w = weighted(s)
    val got = w.sumOf { it.obtained }
    val max = w.sumOf { it.max }
    Surface(
        onClick = { semesterCode?.let { nav.push(Route.Subject(it, s.code)) } },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(s.name.titleCase(), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(s.code, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (max > 0) {
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(fmt(got), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                        Text("of ${fmt(max)} so far", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (max > 0) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (got / max).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    drawStopIndicator = {},
                )
            }
            if (shown.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    shown.forEach { ScoreChip(it, MaterialTheme.colorScheme.surfaceContainerHigh) }
                }
            }
        }
    }
}

/** a subject's "all marks" button lands here, the same view as the tab */
@Composable
fun MarksScreen(route: Route.Marks) {
    val nav = LocalNavigator.current
    val view = rememberMarks(route.code)
    ScreenScaffold(
        title = "Marks",
        onBack = { nav.pop() },
        refreshing = view.report.refreshing && view.report.data != null,
        onRefresh = view.refresh,
    ) { marksContent(view) }
}
