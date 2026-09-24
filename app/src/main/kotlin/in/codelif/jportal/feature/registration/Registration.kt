package `in`.codelif.jportal.feature.registration

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.R
import `in`.codelif.jportal.data.Resource
import `in`.codelif.jportal.data.Store
import `in`.codelif.jportal.feature.attendance.nameCase
import `in`.codelif.jportal.feature.attendance.titleCase
import `in`.codelif.jportal.feature.subject.byCode
import `in`.codelif.jportal.feature.subject.fmt
import `in`.codelif.jportal.ui.LocalNavigator
import `in`.codelif.jportal.ui.components.group
import `in`.codelif.jportal.ui.components.Ic
import `in`.codelif.jportal.ui.components.ScreenScaffold
import `in`.codelif.jportal.ui.components.SectionHeader
import `in`.codelif.jportal.ui.components.SemesterPicker
import `in`.codelif.jportal.ui.components.StaleNotice
import `in`.codelif.jportal.ui.components.resourceStates
import `in`.codelif.ktjiit.model.MoocRequest
import `in`.codelif.ktjiit.model.MoocStage
import `in`.codelif.ktjiit.model.MoocStatus
import `in`.codelif.ktjiit.model.Semester
import `in`.codelif.ktjiit.model.SubjectChoice
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** a semester list, the one picked (newest until you pick), and what the portal says for it */
class Picked<T>(
    val semesters: List<Semester>,
    val semester: Semester?,
    val res: Resource<T>,
    val pick: (String) -> Unit,
    val refresh: () -> Unit,
)

@Composable
fun <T> rememberPicked(sems: Store<List<Semester>>, empty: T, of: (Semester) -> Store<T>): Picked<T> {
    val list by sems.state.collectAsState()
    LaunchedEffect(sems) { sems.refresh() }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val all = list.data.orEmpty()
    val sem = all.byCode(picked) ?: all.firstOrNull()
    val store = sem?.let { s -> remember(s.id) { of(s) } }
    LaunchedEffect(store) { store?.refresh() }
    // no semesters at all is an answer too
    val res = store?.state?.collectAsState()?.value
        ?: if (list.data != null) Resource(data = empty, fetchedAt = list.fetchedAt, checked = true) else list.map { empty }
    return Picked(all, sem, res, { picked = it }, { sems.refresh(force = true); store?.refresh(force = true) })
}

private fun ordinal(n: Int) = "$n" + when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"
    n % 10 == 2 -> "nd"
    n % 10 == 3 -> "rd"
    else -> "th"
}

private val FROZE_IN = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a", java.util.Locale.ENGLISH)
private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy")
private val STAMP = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")

// subject choices

@Composable
fun ChoicesScreen() {
    val repo = LocalGraph.current.repo
    val nav = LocalNavigator.current
    val v = rememberPicked(repo.choiceSemesters, emptyList()) { repo.choices(it) }
    val frozen = v.res.data?.firstNotNullOfOrNull { c -> runCatching { LocalDateTime.parse(c.frozenAt.trim(), FROZE_IN) }.getOrNull() }
    ScreenScaffold(
        "Subject choices",
        subtitle = frozen?.let { "Frozen on ${it.format(DAY)}" },
        onBack = { nav.pop() },
        refreshing = v.res.refreshing && v.res.data != null,
        onRefresh = v.refresh,
    ) {
        item("pick") { SemesterPicker(v.semesters, v.semester, v.pick, v.res.fetchedAt) }
        item("stale") { StaleNotice(v.res, v.refresh) }
        if (!resourceStates(v.res, v.refresh, empty = { it.isEmpty() }, emptyTitle = "No choices this semester")) return@ScreenScaffold
        // baskets in the portal's order, picks in yours. 0 is what you got without picking, it goes first
        v.res.data!!.groupBy { it.basket }.forEach { (code, list) ->
            val ranked = list.any { it.isElective }
            val rows = if (ranked) list.sortedBy { it.preference } else list
            item("h-$code") {
                SectionHeader(list.first().basketName.ifBlank { code }) {
                    if (ranked) Text(
                        "${list.count { it.isAllotted }} of ${list.size} allotted",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            group("g-$code") { rows.forEach { c -> row { ChoiceRow(c, ranked) } } }
        }
        item("end") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ChoiceRow(c: SubjectChoice, ranked: Boolean) {
    val got = c.isAllotted
    Row(
        Modifier.fillMaxWidth().alpha(if (ranked && !got) 0.6f else 1f).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ranked) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (got) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (got) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (c.preference > 0) ordinal(c.preference) else "–", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
            Spacer(Modifier.width(14.dp))
        } else Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name.titleCase(), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(c.code, "${fmt(c.credits)} credits".takeIf { c.credits > 0 }, "audit".takeIf { c.isAudit }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (ranked && got) {
            Spacer(Modifier.width(8.dp))
            Ic(R.drawable.ic_check_circle, null, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            Text("Allotted", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// mooc

@Composable
fun MoocScreen() {
    val repo = LocalGraph.current.repo
    val nav = LocalNavigator.current
    val v = rememberPicked(repo.moocSemesters, MoocStatus()) { repo.mooc(it) }
    ScreenScaffold(
        "MOOC status",
        onBack = { nav.pop() },
        refreshing = v.res.refreshing && v.res.data != null,
        onRefresh = v.refresh,
    ) {
        item("pick") { SemesterPicker(v.semesters, v.semester, v.pick, v.res.fetchedAt) }
        item("stale") { StaleNotice(v.res, v.refresh) }
        if (!resourceStates(v.res, v.refresh, empty = { it.requests.isEmpty() && it.rejected.isEmpty() }, emptyTitle = "No MOOC requests")) return@ScreenScaffold
        val m = v.res.data!!
        if (m.dues > 0) item("dues") {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ic(R.drawable.ic_payments, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("₹${"%,.0f".format(m.dues)} due", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        m.requests.forEach { r -> item("r-${r.code}") { MoocCard(r, rejected = false) } }
        m.rejected.forEach { r -> item("x-${r.code}") { MoocCard(r, rejected = true) } }
        item("end") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun MoocCard(r: MoocRequest, rejected: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(r.name.titleCase(), style = MaterialTheme.typography.titleMedium)
                    Text(r.code, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
                when {
                    rejected -> Badge(R.drawable.ic_cancel, "Rejected", MaterialTheme.colorScheme.error)
                    r.isApproved -> Badge(R.drawable.ic_check_circle, "Approved", MaterialTheme.colorScheme.primary)
                    else -> Badge(R.drawable.ic_schedule, "In review", MaterialTheme.colorScheme.tertiary)
                }
            }
            r.replaces?.let { (code, name) ->
                Spacer(Modifier.height(10.dp))
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(
                        "In place of ${name.titleCase()} · $code",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            val stages = r.stages
            if (stages.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                stages.forEachIndexed { i, st -> StageRow(st, last = i == stages.lastIndex, nextDone = stages.getOrNull(i + 1)?.done == true) }
            }
        }
    }
}

@Composable
private fun Badge(icon: Int, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Ic(icon, null, Modifier.size(18.dp), color)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/** done stages are filled with a tick, waiting ones hollow, the line between them fills once both ends are done */
@Composable
private fun StageRow(st: MoocStage, last: Boolean, nextDone: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    Row(Modifier.height(IntrinsicSize.Min)) {
        Column(Modifier.width(20.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(2.dp))
            if (st.done) {
                Box(Modifier.size(18.dp).background(primary, CircleShape), contentAlignment = Alignment.Center) {
                    Ic(R.drawable.ic_done, null, Modifier.size(12.dp), MaterialTheme.colorScheme.onPrimary)
                }
            } else {
                Box(Modifier.size(18.dp).border(2.dp, MaterialTheme.colorScheme.outline, CircleShape))
            }
            if (!last) {
                Box(
                    Modifier.padding(vertical = 3.dp).width(2.dp).weight(1f)
                        .background(if (st.done && nextDone) primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.padding(bottom = if (last) 0.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(st.title, style = MaterialTheme.typography.titleSmall)
            val line = listOfNotNull(st.at?.format(STAMP), st.by?.nameCase()).joinToString(" · ")
            Text(
                line.ifEmpty { if (st.done) "Done" else "Waiting" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
