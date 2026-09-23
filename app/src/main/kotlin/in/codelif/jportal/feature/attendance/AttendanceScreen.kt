package `in`.codelif.jportal.feature.attendance

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.data.Repository
import `in`.codelif.jportal.data.Resource
import `in`.codelif.jportal.domain.AttendanceMath
import `in`.codelif.jportal.domain.Tally
import `in`.codelif.jportal.ui.LocalBottomInset
import `in`.codelif.jportal.ui.LocalNavigator
import `in`.codelif.jportal.ui.components.AttendanceRing
import `in`.codelif.jportal.ui.components.ScreenScaffold
import `in`.codelif.jportal.ui.components.SemesterChip
import `in`.codelif.jportal.ui.components.StaleNotice
import `in`.codelif.jportal.ui.components.ago
import `in`.codelif.jportal.ui.components.rememberSemester
import `in`.codelif.jportal.ui.components.resourceStates
import `in`.codelif.jportal.ui.nav.Route
import `in`.codelif.jportal.ui.nav.shared
import `in`.codelif.jportal.ui.theme.LocalExtraColors
import `in`.codelif.jportal.ui.theme.NumberStyle
import `in`.codelif.ktjiit.model.DailyAttendance
import `in`.codelif.ktjiit.model.Semester
import `in`.codelif.ktjiit.model.SubjectAttendance
import kotlin.math.roundToInt

/** a subject row: the portal's percentage straight away, exact counts once the class list lands */
data class SubjectLine(val subject: SubjectAttendance, val tally: Tally?) {
    val percent: Float get() = tally?.takeIf { it.total > 0 }?.percent?.toFloat() ?: (subject.percent ?: 0.0).toFloat()
}

@Composable
fun rememberAttendance(repo: Repository, sem: Semester?, semesterNumber: String): Pair<Resource<*>, List<SubjectLine>> {
    if (sem == null) return Resource<Unit>(refreshing = true) to emptyList()
    val store = remember(sem.id) { repo.attendance(sem, semesterNumber) }
    val detail by store.state.collectAsState()
    LaunchedEffect(store) { store.refresh() }
    val subjects = detail.data?.subjects.orEmpty()
    // the class lists are what make the numbers exact, fetch them all behind the portal's percentages
    val dailies: List<State<Resource<DailyAttendance>>> = subjects.map { s ->
        key(sem.id, s.subjectId) {
            val d = remember { repo.daily(sem, s) }
            LaunchedEffect(d, detail.fetchedAt) { d.refresh() }
            d.state.collectAsState()
        }
    }
    val lines = subjects.mapIndexed { i, s ->
        SubjectLine(s, dailies[i].value.data?.let { AttendanceMath.tally(it.classes) }?.takeIf { it.total > 0 })
    }
    return detail to lines
}

@Composable
fun AttendanceScreen() {
    val graph = LocalGraph.current
    val nav = LocalNavigator.current
    val sel = rememberSemester()
    val meta by graph.repo.attendanceMeta.state.collectAsState()
    val target by graph.prefs.targetState.collectAsState()
    val semNumber = meta.data?.header?.semesterNumber.orEmpty()
    val (detail, lines) = rememberAttendance(graph.repo, sel.selected, semNumber)
    var editTarget by remember { mutableStateOf(false) }

    val refresh = {
        graph.repo.attendanceMeta.refresh(force = true)
        sel.selected?.let { s ->
            graph.repo.attendance(s, semNumber).refresh(force = true)
            lines.forEach { graph.repo.daily(s, it.subject).refresh(force = true) }
        }
        Unit
    }

    ScreenScaffold(
        title = "Attendance",
        subtitle = detail.fetchedAt?.let { "Updated ${ago(it)}" },
        refreshing = detail.refreshing && detail.data != null,
        onRefresh = refresh,
        bottomPadding = LocalBottomInset.current,
        actions = { SemesterChip(sel, Modifier.padding(end = 8.dp)) },
    ) {
        item("stale") { StaleNotice(detail, refresh) }
        @Suppress("UNCHECKED_CAST")
        if (resourceStates(detail as Resource<Any>, refresh, empty = { lines.isEmpty() }, emptyTitle = "No attendance for this semester yet")) {
            item("overview") { Overview(lines, target, onTarget = { editTarget = true }) }
            items(lines, key = { it.subject.subjectId }) { line ->
                SubjectCard(line, target, Modifier.animateItem()) {
                    sel.selected?.let { nav.push(Route.Subject(it.id, line.subject.subjectId)) }
                }
            }
        }
    }
    if (editTarget) TargetSheet(target, onDone = { graph.prefs.setTarget(it); editTarget = false })
}

@Composable
private fun Overview(lines: List<SubjectLine>, target: Int, onTarget: () -> Unit) {
    val counted = lines.mapNotNull { it.tally }
    val total = Tally(counted.sumOf { it.attended }, counted.sumOf { it.total })
    val percent = if (total.total > 0) total.percent.toFloat() else lines.map { it.percent }.average().toFloat().takeIf { !it.isNaN() } ?: 0f
    val low = lines.count { (it.tally != null || (it.subject.percent ?: 0.0) > 0.0) && it.percent < target }
    val extra = LocalExtraColors.current

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            AttendanceRing(percent, target, size = 116.dp, stroke = 12.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${percent.roundToInt()}", style = NumberStyle.copy(fontSize = 36.sp, lineHeight = 38.sp))
                    Text("percent", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    when {
                        lines.isEmpty() -> "No classes yet"
                        low == 0 -> "All clear"
                        low == 1 -> "1 subject below target"
                        else -> "$low subjects below target"
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                if (total.total > 0) {
                    Text("${total.attended} of ${total.total} classes attended", style = MaterialTheme.typography.bodyMedium)
                }
                Surface(
                    onClick = onTarget,
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                ) {
                    Text(
                        "Target $target%",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (low == 0) extra.good else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SubjectCard(line: SubjectLine, target: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val s = line.subject
    val extra = LocalExtraColors.current
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
    ) {
        Row(Modifier.padding(16.dp).animateContentSize(), verticalAlignment = Alignment.CenterVertically) {
            val started = line.tally != null || (s.percent ?: 0.0) > 0.0
            AttendanceRing(if (started) line.percent else 0f, target, Modifier.shared("ring-${s.subjectId}"), size = 60.dp, stroke = 6.dp) {
                Text(
                    if (started) "${line.percent.roundToInt()}" else "–",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (started) androidx.compose.ui.graphics.Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    s.name.titleCase(), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.shared("name-${s.subjectId}"),
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ComponentTags(s)
                    line.tally?.let {
                        Text("${it.attended}/${it.total}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!started) {
                    Spacer(Modifier.height(4.dp))
                    Text("No classes marked yet", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                line.tally?.let { t ->
                    Spacer(Modifier.height(4.dp))
                    val miss = t.canMiss(target)
                    val need = t.mustAttend(target)
                    Text(
                        when {
                            need > 0 -> "Attend the next $need"
                            miss > 0 -> "Can miss $miss"
                            else -> "Right on the edge, don't miss"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = when {
                            need > 0 -> MaterialTheme.colorScheme.error
                            miss > 0 -> extra.good
                            else -> extra.warn
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun ComponentTags(s: SubjectAttendance) {
    listOfNotNull(
        s.lectureComponent?.let { "L" to s.lecturePercent },
        s.tutorialComponent?.let { "T" to s.tutorialPercent },
        s.practicalComponent?.let { "P" to s.practicalPercent },
    ).forEach { (k, _) ->
        Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(k, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
        }
    }
}

/** "DATA STRUCTURES LAB" -> "Data Structures Lab", keeps roman numerals and short acronyms */
fun String.titleCase(): String = split(' ').joinToString(" ") { w ->
    when {
        w.isEmpty() -> w
        w.matches(Regex("(?i)^(i{1,3}|iv|v|vi{0,3}|ix|x)(-\\w+)?$")) -> w.uppercase()
        w.length <= 3 && w.none { it.isLowerCase() } && w.any { it.isLetter() } && w !in setOf("AND", "OF", "THE", "FOR", "IN", "TO") -> w
        w.any { it.isLowerCase() } -> w
        else -> w.lowercase().replaceFirstChar { it.uppercase() }.let { if (it in setOf("And", "Of", "The", "For", "In", "To", "Using")) it.lowercase() else it }
    }
}.replaceFirstChar { it.uppercase() }
