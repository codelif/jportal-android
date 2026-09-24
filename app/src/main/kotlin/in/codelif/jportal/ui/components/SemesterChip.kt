package `in`.codelif.jportal.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.R
import `in`.codelif.ktjiit.model.Semester

private val SEMESTER = Regex("""(\d{4})(ODD|EVE|EVEN|SUM|SUMMER)SEM""", RegexOption.IGNORE_CASE)

/** "2026ODDSEM" -> "Odd 2026" */
fun prettySemester(code: String): String {
    val m = SEMESTER.find(code) ?: return code
    val kind = when (m.groupValues[2].uppercase()) {
        "ODD" -> "Odd"
        "EVE", "EVEN" -> "Even"
        else -> "Summer"
    }
    return "$kind ${m.groupValues[1]}"
}

/** a chip that opens a menu reads as one: what it picks, what's picked, and that it's a list */
fun Modifier.dropdown(label: String, onOpen: () -> Unit): Modifier = clearAndSetSemantics {
    contentDescription = label
    role = Role.DropdownList
    onClick { onOpen(); true }
}

class SemesterSelection(val all: List<Semester>, val selected: Semester?, val isCurrent: Boolean)

/**
 * the one semester every screen follows. the portal lists newest first in the
 * attendance registrations, so "current" is the head of that list unless the
 * user pinned something else.
 */
@Composable
fun rememberSemester(): SemesterSelection {
    val graph = LocalGraph.current
    val meta by graph.repo.attendanceMeta.state.collectAsState()
    val pinned by graph.prefs.semesterState.collectAsState()
    LaunchedEffect(Unit) { graph.repo.attendanceMeta.refresh() }
    val all = meta.data?.semesters.orEmpty()
    val chosen = all.firstOrNull { it.code == pinned } ?: all.firstOrNull()
    return SemesterSelection(all, chosen, chosen == all.firstOrNull())
}

@Composable
fun SemesterChip(sel: SemesterSelection, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    var open by remember { mutableStateOf(false) }
    val current = sel.selected ?: return
    Box(modifier) {
        FilterChip(
            selected = !sel.isCurrent,
            onClick = { open = true },
            modifier = Modifier.dropdown("Semester, ${prettySemester(current.code)}") { open = true },
            label = { Text(prettySemester(current.code)) },
            trailingIcon = { Ic(R.drawable.ic_expand_more, null, Modifier.size(18.dp)) },
        )
        DropdownMenu(open, onDismissRequest = { open = false }) {
            sel.all.forEachIndexed { i, s ->
                DropdownMenuItem(
                    text = { Text(prettySemester(s.code) + if (i == 0) "  ·  current" else "") },
                    onClick = {
                        open = false
                        graph.prefs.setSemester(if (i == 0) null else s.code)
                    },
                    trailingIcon = if (s.code == current.code) ({ Ic(R.drawable.ic_done, null) }) else null,
                )
            }
        }
    }
}

/** a view's own semester, for lists the portal keeps apart from attendance's (marks, exams) */
@Composable
fun SemesterPicker(semesters: List<Semester>, selected: Semester?, onPick: (String) -> Unit, fetchedAt: Long?) {
    val sem = selected ?: return
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            FilterChip(
                selected = true,
                onClick = { open = true },
                modifier = Modifier.dropdown("Semester, ${prettySemester(sem.code)}") { open = true },
                label = { Text(prettySemester(sem.code)) },
                trailingIcon = { Ic(R.drawable.ic_expand_more, null, Modifier.size(18.dp)) },
            )
            DropdownMenu(open, onDismissRequest = { open = false }) {
                semesters.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(prettySemester(s.code)) },
                        onClick = { open = false; onPick(s.code) },
                        trailingIcon = if (s.code == sem.code) ({ Ic(R.drawable.ic_done, null) }) else null,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        fetchedAt?.let {
            Text("Updated ${ago(it)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
