package `in`.codelif.jportal.feature.academics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.feature.attendance.titleCase
import `in`.codelif.jportal.feature.subject.fmt
import `in`.codelif.jportal.ui.LocalNavigator
import `in`.codelif.jportal.ui.components.CenteredLoading
import `in`.codelif.jportal.ui.components.Frog
import `in`.codelif.jportal.ui.components.GradeLetter
import `in`.codelif.jportal.ui.components.group
import `in`.codelif.jportal.ui.components.MessageState
import `in`.codelif.jportal.ui.components.ScreenScaffold
import `in`.codelif.jportal.ui.components.prettySemester
import `in`.codelif.jportal.ui.nav.Route
import `in`.codelif.jportal.ui.theme.LocalExtraColors
import `in`.codelif.jportal.ui.theme.NumberStyle

/** one semester's grade card: the results up top, then every subject with its grade and credits, jportal's two columns */
@Composable
fun GradeCardScreen(route: Route.GradeCard) {
    val repo = LocalGraph.current.repo
    val nav = LocalNavigator.current
    val data = rememberAcademics(repo)
    val sem = data.semester(route.code)
    val entries = sem?.grades.orEmpty()

    ScreenScaffold(
        title = "Grade card",
        subtitle = listOfNotNull(sem?.number?.let { "Semester $it" }, prettySemester(route.code)).joinToString(" · "),
        onBack = { nav.pop() },
        onRefresh = { data.refresh() },
    ) {
        when {
            entries.isEmpty() && data.loading -> item("loading") { CenteredLoading() }
            entries.isEmpty() -> item("none") { MessageState(Frog.Sleep, "Grades aren't out yet") }
            else -> {
                item("head") { Header(sem, entries.sumOf { it.credits }) }
                group("rows", top = 8.dp, bottom = 16.dp) {
                    entries.forEach { e ->
                        row { GradeRow(e.name, e.code, e.grade, e.credits) { nav.push(Route.Subject(route.code, e.code)) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(sem: SemesterInfo?, credits: Double) {
    val extra = LocalExtraColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        sem?.result?.let { r ->
            Stat("SGPA", "%.1f".format(r.sgpa), extra.sgpa)
            Stat("CGPA", "%.2f".format(r.cgpa), extra.cgpa)
        }
        Stat("Credits", fmt(sem?.result?.earnedCredits?.takeIf { it > 0 } ?: credits), extra.credits)
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = NumberStyle, color = color)
    }
}

@Composable
private fun GradeRow(name: String, code: String, grade: String, credits: Double, onClick: () -> Unit) {
    val extra = LocalExtraColors.current
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name.titleCase(), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(code, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            GradeLetter(grade, size = 44.dp)
            Spacer(Modifier.width(16.dp))
            Figure(fmt(credits), "credits", extra.credits)
        }
    }
}
