package `in`.codelif.jportal.feature.grades

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.feature.attendance.titleCase
import `in`.codelif.jportal.feature.subject.ScoreChip
import `in`.codelif.jportal.feature.subject.fmt
import `in`.codelif.jportal.ui.LocalNavigator
import `in`.codelif.jportal.ui.components.CenteredLoading
import `in`.codelif.jportal.ui.components.ScreenScaffold
import `in`.codelif.jportal.ui.components.StaleNotice
import `in`.codelif.jportal.ui.components.prettySemester
import `in`.codelif.jportal.ui.components.resourceStates
import `in`.codelif.jportal.ui.nav.Route
import `in`.codelif.jportal.ui.theme.LocalExtraColors
import `in`.codelif.ktjiit.marks.Score
import `in`.codelif.ktjiit.marks.SubjectMarks

@Composable
fun MarksScreen(route: Route.Marks) {
    val repo = LocalGraph.current.repo
    val nav = LocalNavigator.current
    val sems by repo.marksSemesters.state.collectAsState()
    val sem = sems.data?.firstOrNull { it.id == route.semesterId }
    if (sem == null) {
        ScreenScaffold("Marks", onBack = { nav.pop() }) { item { CenteredLoading() } }
        return
    }
    val store = remember(sem.id) { repo.marks(sem) }
    val res by store.state.collectAsState()
    LaunchedEffect(store) { store.refresh() }
    val refresh = { store.refresh(force = true); Unit }

    ScreenScaffold(
        title = "Marks",
        subtitle = prettySemester(sem.code),
        onBack = { nav.pop() },
        refreshing = res.refreshing && res.data != null,
        onRefresh = refresh,
    ) {
        item("stale") { StaleNotice(res, refresh) }
        if (resourceStates(res, refresh, empty = { it.subjects.isEmpty() }, emptyTitle = "No marks uploaded yet")) {
            items(res.data!!.subjects, key = { it.code }) { SubjectMarksCard(it) }
        }
    }
}

@Composable
private fun SubjectMarksCard(s: SubjectMarks) {
    val shown = s.scores.filter { it.marks != Score.NotApplicable || it.weighted != Score.NotApplicable }
    val weighted = shown.mapNotNull { it.weighted as? Score.Value }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(s.name.titleCase(), style = MaterialTheme.typography.titleMedium)
                    Text(s.code, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (weighted.isNotEmpty()) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(fmt(weighted.sumOf { it.obtained }), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                        Text("of ${fmt(weighted.sumOf { it.max })} so far", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (shown.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    shown.forEach { ScoreChip(it) }
                }
            }
        }
    }
}

@Composable
fun GradeCardScreen(route: Route.GradeCard) {
    val repo = LocalGraph.current.repo
    val nav = LocalNavigator.current
    val sems by repo.gradeSemesters.state.collectAsState()
    val sem = sems.data?.firstOrNull { it.id == route.semesterId }
    if (sem == null) {
        ScreenScaffold("Grade card", onBack = { nav.pop() }) { item { CenteredLoading() } }
        return
    }
    val store = remember(sem.id) { repo.gradeCard(sem) }
    val res by store.state.collectAsState()
    LaunchedEffect(store) { store.refresh() }
    val refresh = { store.refresh(force = true); Unit }

    ScreenScaffold(
        title = "Grade card",
        subtitle = prettySemester(sem.code),
        onBack = { nav.pop() },
        refreshing = res.refreshing && res.data != null,
        onRefresh = refresh,
    ) {
        item("stale") { StaleNotice(res, refresh) }
        if (resourceStates(res, refresh, empty = { it.entries.isEmpty() }, emptyTitle = "Grades aren't out yet")) {
            val entries = res.data!!.entries
            item("sum") {
                val credits = entries.sumOf { it.credits }
                Text(
                    "${entries.size} courses · ${fmt(credits)} credits",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            items(entries, key = { it.subjectId + it.code }) { e ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    GradeBadge(e.grade)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.name.titleCase(), style = MaterialTheme.typography.titleMedium)
                        Text("${e.code} · ${fmt(e.credits)} credits", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeBadge(grade: String) {
    val extra = LocalExtraColors.current
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (grade.trim().uppercase()) {
        "A+", "A" -> extra.goodContainer to (if (extra.goodContainer.red > 0.5f) Color(0xFF0B3B1A) else Color(0xFFCDEFD6))
        "B+", "B" -> scheme.primaryContainer to scheme.onPrimaryContainer
        "C+", "C" -> scheme.secondaryContainer to scheme.onSecondaryContainer
        "F", "I", "X" -> scheme.errorContainer to scheme.onErrorContainer
        else -> scheme.surfaceContainerHighest to scheme.onSurface
    }
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape, color = bg, modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(grade.ifBlank { "–" }, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = fg)
            }
        }
    }
}
