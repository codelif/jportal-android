package `in`.codelif.jportal.feature.attendance

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import `in`.codelif.jportal.ui.components.FitText
import `in`.codelif.jportal.ui.components.Loading
import `in`.codelif.jportal.ui.components.Placeholder
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

/**
 * a subject row. the class list is what makes the numbers exact, the portal's
 * own percentage is only a fallback for when that list can't be had.
 */
class SubjectLine(val subject: SubjectAttendance, daily: Resource<DailyAttendance>) {
    val tally: Tally? = daily.data?.let { AttendanceMath.tally(it.classes) }?.takeIf { it.total > 0 }

    /** class list still on its way, nothing final to show yet */
    val counting: Boolean = daily.data == null && daily.error == null

    val started: Boolean = tally != null || (!counting && (subject.percent ?: 0.0) > 0.0)
    val percent: Float = tally?.percent?.toFloat() ?: (subject.percent ?: 0.0).toFloat()

    /** the last few classes, oldest first, true for present */
    val recent: List<Boolean> = daily.data?.classes.orEmpty().filter { it.date != null }
        .sortedWith(compareBy({ it.date }, { it.start })).takeLast(7).map { it.isPresent }
}

/** the attendance answer plus one line per subject. the resource isn't [Resource.checked] until every class list was looked for on disk */
@Composable
fun rememberAttendance(repo: Repository, meta: Resource<*>, sem: Semester?, semesterNumber: String): Pair<Resource<*>, List<SubjectLine>> {
    if (sem == null) return meta to emptyList()
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
    val lines = subjects.mapIndexed { i, s -> SubjectLine(s, dailies[i].value) }
    // cached class lists arrive a few ms after the detail, drawing in between would flash placeholders
    return detail.copy(checked = detail.checked && dailies.all { it.value.checked }) to lines
}

@Composable
fun AttendanceScreen() {
    val graph = LocalGraph.current
    val nav = LocalNavigator.current
    val sel = rememberSemester()
    val meta by graph.repo.attendanceMeta.state.collectAsState()
    val target by graph.prefs.targetState.collectAsState()
    val semNumber = meta.data?.header?.semesterNumber.orEmpty()
    val (detail, lines) = rememberAttendance(graph.repo, meta, sel.selected, semNumber)
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
        if (resourceStates(detail as Resource<Any>, refresh, empty = { lines.isEmpty() }, emptyTitle = "No attendance for this semester yet") && detail.checked) {
            item("overview") { Overview(lines, target, onTarget = { editTarget = true }) }
            // no item animations: the order never changes, and springing cards come apart from the overview
            items(lines, key = { it.subject.subjectId }, contentType = { "subject" }) { line ->
                SubjectCard(line, target) {
                    sel.selected?.let { nav.push(Route.Subject(it.code, line.subject.code)) }
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
    // one number, shown once every subject is in, so it never climbs while lists land
    val waiting = lines.count { it.counting }
    val uncounted = lines.count { it.started && it.tally == null }
    val percent = when {
        waiting > 0 -> 0f
        total.total > 0 -> total.percent.toFloat()
        else -> lines.filter { it.started }.map { it.percent }.average().toFloat().takeIf { !it.isNaN() } ?: 0f
    }
    val low = lines.count { it.started && it.percent < target }
    val extra = LocalExtraColors.current

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            AttendanceRing(percent, target, size = 116.dp, stroke = 12.dp) {
                if (waiting > 0) {
                    Loading(size = 40.dp)
                } else {
                    // the ring can't grow with the font, so its inside shrinks to fit instead
                    Column(Modifier.padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        FitText("${percent.roundToInt()}", NumberStyle.copy(fontSize = 36.sp, lineHeight = 38.sp))
                        FitText("percent", MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    when {
                        waiting > 0 -> "Counting classes"
                        lines.none { it.started } -> "No classes yet"
                        low == 0 -> "All clear"
                        low == 1 -> "1 subject below target"
                        else -> "$low subjects below target"
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                // one line at normal sizes in every state, so the card keeps its height while lists land
                Text(
                    when {
                        waiting > 0 -> "${lines.size - waiting} of ${lines.size} subjects in"
                        total.total == 0 -> "Nothing marked this semester"
                        uncounted > 0 -> "${total.attended} of ${total.total} classes, $uncounted not counted"
                        else -> "${total.attended} of ${total.total} classes attended"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val started = line.started
            val ring = if (line.counting) Modifier.pulsing() else Modifier
            AttendanceRing(if (started) line.percent else 0f, target, ring.shared("ring-${s.subjectId}"), size = 60.dp, stroke = 6.dp) {
                if (!line.counting) {
                    FitText(
                        if (started) "${line.percent.roundToInt()}" else "–",
                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        Modifier.padding(horizontal = 8.dp).semantics { contentDescription = if (started) "${line.percent.roundToInt()} percent" else "not started" },
                        color = if (started) androidx.compose.ui.graphics.Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                    when {
                        line.counting -> Placeholder(34.dp, 12.dp)
                        line.tally != null -> {
                            Text(
                                "${line.tally.attended}/${line.tally.total}",
                                modifier = Modifier.semantics { contentDescription = "${line.tally.attended} of ${line.tally.total} classes" },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            RecentDots(line.recent, Modifier.padding(start = 4.dp))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                // exactly one line in every state, a card never changes height when its list lands
                val label = MaterialTheme.typography.labelLarge
                val t = line.tally
                when {
                    line.counting -> Box(Modifier.height(with(LocalDensity.current) { label.lineHeight.toDp() }), contentAlignment = Alignment.CenterStart) {
                        Placeholder(84.dp, 12.dp)
                    }
                    t != null -> {
                        val miss = t.canMiss(target)
                        val need = t.mustAttend(target)
                        Text(
                            when {
                                need > 0 -> "Attend the next $need"
                                miss > 0 -> "Can miss $miss"
                                else -> "Right on the edge, don't miss"
                            },
                            style = label,
                            color = when {
                                need > 0 -> MaterialTheme.colorScheme.error
                                miss > 0 -> extra.good
                                else -> extra.warn
                            },
                        )
                    }
                    started -> Text("Portal's %, class list didn't load", style = label, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    else -> Text("No classes marked yet", style = label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * one mark per recent class, newest on the right: a filled dot for present,
 * a cross for absent. the shape carries it, the colour only backs it up.
 */
@Composable
private fun RecentDots(recent: List<Boolean>, modifier: Modifier = Modifier) {
    if (recent.isEmpty()) return
    val good = LocalExtraColors.current.good
    val bad = MaterialTheme.colorScheme.error
    val present = recent.count { it }
    Canvas(
        modifier.size(width = (recent.size * 11 - 3).dp, height = 8.dp)
            .semantics { contentDescription = "last ${recent.size} classes: $present present, ${recent.size - present} absent" },
    ) {
        val h = size.height
        val step = 11.dp.toPx()
        recent.forEachIndexed { i, p ->
            val x = i * step
            if (p) {
                drawCircle(good, h / 2 * 0.85f, Offset(x + h / 2, h / 2))
            } else {
                val w = 1.8.dp.toPx()
                val m = w / 2
                drawLine(bad, Offset(x + m, m), Offset(x + h - m, h - m), w, StrokeCap.Round)
                drawLine(bad, Offset(x + h - m, m), Offset(x + m, h - m), w, StrokeCap.Round)
            }
        }
    }
}

/** same breathing as [Placeholder], for things that aren't boxes. alpha lives in the layer, nothing recomposes */
@Composable
private fun Modifier.pulsing(): Modifier {
    val a = rememberInfiniteTransition(label = "pulse").animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "a")
    return graphicsLayer { alpha = a.value }
}

@Composable
fun ComponentTags(s: SubjectAttendance) {
    listOfNotNull(
        s.lectureComponent?.let { "L" to s.lecturePercent },
        s.tutorialComponent?.let { "T" to s.tutorialPercent },
        s.practicalComponent?.let { "P" to s.practicalPercent },
    ).forEach { (k, _) ->
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.semantics { contentDescription = `in`.codelif.jportal.feature.academics.COMPONENT[k] ?: k },
        ) {
            Text(k, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
        }
    }
}

private val ROMAN = Regex("(?i)^(i{1,3}|iv|v|vi{0,3}|ix|x)(-\\w+)?$")
private val WORDS = setOf("AND", "OF", "THE", "FOR", "IN", "TO", "LAB")
private val SMALL = setOf("And", "Of", "The", "For", "In", "To", "Using")
private val titled = java.util.concurrent.ConcurrentHashMap<String, String>()

/** "DATA STRUCTURES LAB" -> "Data Structures Lab", keeps roman numerals and short acronyms. memoised, lists call it every frame they compose */
fun String.titleCase(): String = titled.getOrPut(this) {
    split(' ').joinToString(" ") { w ->
        when {
            w.isEmpty() -> w
            w.matches(ROMAN) -> w.uppercase()
            w.length <= 3 && w.none { it.isLowerCase() } && w.any { it.isLetter() } && w !in WORDS && !w.endsWith('.') -> w
            w.any { it.isLowerCase() } -> w
            else -> w.lowercase().replaceFirstChar { it.uppercase() }.let { if (it in SMALL) it.lowercase() else it }
        }
    }.replaceFirstChar { it.uppercase() }
}
