package `in`.codelif.jportal.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.R
import `in`.codelif.ktjiit.model.Semester

/** "2026ODDSEM" -> "Odd 2026" */
fun prettySemester(code: String): String {
    val m = Regex("""(\d{4})(ODD|EVE|EVEN|SUM|SUMMER)SEM""", RegexOption.IGNORE_CASE).find(code) ?: return code
    val kind = when (m.groupValues[2].uppercase()) {
        "ODD" -> "Odd"
        "EVE", "EVEN" -> "Even"
        else -> "Summer"
    }
    return "$kind ${m.groupValues[1]}"
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
