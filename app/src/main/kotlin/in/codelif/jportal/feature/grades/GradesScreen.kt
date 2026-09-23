package `in`.codelif.jportal.feature.grades

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.R
import `in`.codelif.jportal.domain.Gpa
import `in`.codelif.jportal.ui.LocalBottomInset
import `in`.codelif.jportal.ui.LocalNavigator
import `in`.codelif.jportal.ui.components.GpaChart
import `in`.codelif.jportal.ui.components.Ic
import `in`.codelif.jportal.ui.components.ScreenScaffold
import `in`.codelif.jportal.ui.components.SectionHeader
import `in`.codelif.jportal.ui.components.StaleNotice
import `in`.codelif.jportal.ui.components.prettySemester
import `in`.codelif.jportal.ui.components.resourceStates
import `in`.codelif.jportal.ui.nav.Route
import `in`.codelif.jportal.ui.theme.LocalExtraColors
import `in`.codelif.jportal.ui.theme.NumberStyle
import `in`.codelif.ktjiit.model.Semester

@Composable
fun GradesScreen() {
    val graph = LocalGraph.current
    val repo = graph.repo
    val nav = LocalNavigator.current
    val meta by repo.attendanceMeta.state.collectAsState()
    val current = meta.data?.header?.semesterNumber.orEmpty()
    val resultsStore = remember(current) { repo.results(current) }
    val results by resultsStore.state.collectAsState()
    val credits by repo.credits.state.collectAsState()
    val program by repo.program.state.collectAsState()
    val cards by repo.gradeSemesters.state.collectAsState()
    val marks by repo.marksSemesters.state.collectAsState()
    LaunchedEffect(current) {
        if (current.isNotEmpty()) resultsStore.refresh()
        repo.credits.refresh(); repo.program.refresh(); repo.gradeSemesters.refresh(); repo.marksSemesters.refresh()
    }
    // projected sgpa per semester, survives rotation and tab switches
    val projections = rememberSaveable(saver = mapSaver) { mutableStateMapOf() }

    val real = results.data.orEmpty()
    val maxSem = program.data?.maxSemesters?.takeIf { it > 0 } ?: 8
    val nextCredits = credits.data?.registeredCredits?.toDouble()?.takeIf { it > 0 } ?: real.lastOrNull()?.courseCredits ?: 20.0
    val points = Gpa.points(real, projections, nextCredits, maxSem)
    val future = ((real.maxOfOrNull { it.semester } ?: 0) + 1..maxSem).toList()
    val refresh = {
        resultsStore.refresh(force = true); repo.credits.refresh(force = true)
        repo.gradeSemesters.refresh(force = true); repo.marksSemesters.refresh(force = true)
        Unit
    }

    ScreenScaffold(
        title = "Grades",
        refreshing = results.refreshing && results.data != null,
        onRefresh = refresh,
        bottomPadding = LocalBottomInset.current,
    ) {
        item("stale") { StaleNotice(results, refresh) }
        if (resourceStates(results, refresh, empty = { it.isEmpty() }, emptyTitle = "No results yet, first semester?")) {
            item("hero") {
                val latest = real.maxByOrNull { it.semester }
                val projected = points.lastOrNull()?.takeIf { it.projected }
                Headline(latest?.cgpa, latest?.sgpa, projected?.cgpa, credits.data?.earned, credits.data?.required)
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
            item("sems-h") { SectionHeader("Semesters") }
            items(real.sortedByDescending { it.semester }, key = { "r${it.semester}" }) { r ->
                SemesterRow(r.semester, r.sgpa, r.cgpa, r.earnedCredits)
            }
        }
        cards.data?.takeIf { it.isNotEmpty() }?.let { list ->
            item("gc-h") { SectionHeader("Grade cards") }
            items(list, key = { "gc-${it.id}" }) { s -> LinkRow(s, R.drawable.ic_school) { nav.push(Route.GradeCard(s.id)) } }
        }
        marks.data?.takeIf { it.isNotEmpty() }?.let { list ->
            item("mk-h") { SectionHeader("Marks") }
            items(list, key = { "mk-${it.id}" }) { s -> LinkRow(s, R.drawable.ic_menu_book) { nav.push(Route.Marks(s.id)) } }
        }
    }
}

private val mapSaver = androidx.compose.runtime.saveable.Saver<androidx.compose.runtime.snapshots.SnapshotStateMap<Int, Double>, Map<String, Double>>(
    save = { m -> m.mapKeys { it.key.toString() } },
    restore = { m -> mutableStateMapOf<Int, Double>().apply { m.forEach { (k, v) -> put(k.toInt(), v) } } },
)

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
                Text("$earned", style = NumberStyle)
                Text(" / $required credits", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { earned.toFloat() / required },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small),
            )
        }
    }
}

@Composable
private fun Big(label: String, value: Double?, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value?.let { "%.2f".format(it) } ?: "–", style = NumberStyle, color = color)
    }
}

@Composable
private fun SemesterRow(sem: Int, sgpa: Double, cgpa: Double, credits: Double) {
    val extra = LocalExtraColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Semester $sem", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text("%.1f".format(sgpa), style = MaterialTheme.typography.titleMedium, color = extra.sgpa)
        Spacer(Modifier.width(16.dp))
        Text("%.2f".format(cgpa), style = MaterialTheme.typography.titleMedium, color = extra.cgpa)
        Spacer(Modifier.width(16.dp))
        Text("${credits.toInt()} cr", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LinkRow(s: Semester, icon: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Ic(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Text(prettySemester(s.code), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Ic(R.drawable.ic_chevron_right, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
