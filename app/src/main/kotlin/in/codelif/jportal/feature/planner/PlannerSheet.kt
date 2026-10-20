package `in`.codelif.jportal.feature.planner

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.R
import `in`.codelif.jportal.domain.BatchPath
import `in`.codelif.jportal.domain.DayPlan
import `in`.codelif.jportal.domain.Klass
import `in`.codelif.jportal.domain.minutes
import `in`.codelif.jportal.feature.academics.COMPONENT
import `in`.codelif.jportal.feature.academics.Fact
import `in`.codelif.jportal.ui.components.Ic
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun PlannerSheets(view: PlannerView) {
    val target = view.sheet ?: return
    when {
        target == "batch" -> PickerSheet(view)
        target.startsWith("k:") -> {
            // a refresh or a new pick can take the class away under an open sheet
            val k = (view.plan.rows.getOrNull(target.removePrefix("k:").toIntOrNull() ?: -1) as? DayPlan.Row.Class)?.k ?: return
            ClassSheet(k) { view.openSheet(null) }
        }
    }
}

// the picker: course, semester, phase and batch walked top down, then the electives of the batch picked

@Composable
private fun PickerSheet(view: PlannerView) {
    val tt = view.timetable.data ?: return
    val path = view.path
    var course by rememberSaveable { mutableStateOf(path?.course) }
    var sem by rememberSaveable { mutableStateOf(path?.semester) }
    var phase by rememberSaveable { mutableStateOf(path?.phase) }
    val c = tt.courses.firstOrNull { it.id == course }
    val s = c?.semesters?.firstOrNull { it.id == sem }
    val p = s?.phases?.firstOrNull { it.id == phase }
    val here = path != null && path.course == course && path.semester == sem && path.phase == phase
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = { view.openSheet(null) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Your timetable", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            val detected = view.detected
            if (detected != null && path != detected) {
                TextButton(onClick = {
                    view.pin(null)
                    course = detected.course
                    sem = detected.semester
                    phase = detected.phase
                }) { Text("Go back to the portal's batch, ${tt.batch(detected)?.name}") }
            } else if (detected == null) {
                Text(
                    "The portal's batch didn't match one here, so it's up to you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Choices("Course", tt.courses.map { it.id to it.name }, course) { id ->
                course = id
                val only = tt.courses.first { it.id == id }.semesters.singleOrNull()
                sem = only?.id
                phase = only?.phases?.singleOrNull()?.id
            }
            if (c != null) Choices("Semester", c.semesters.map { it.id to it.name }, sem) { id ->
                sem = id
                phase = c.semesters.first { it.id == id }.phases.singleOrNull()?.id
            }
            // nearly always a single phase, no point asking
            if (s != null && s.phases.size > 1) Choices("Phase", s.phases.map { it.id to it.name }, phase) { phase = it }
            if (c != null && s != null && p != null) {
                Choices("Batch", p.batches.map { it.id to it.name }, path?.batch?.takeIf { here }) { id ->
                    view.pin(BatchPath(c.id, s.id, p.id, id))
                }
            }
            if (here && view.catalog.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(top = 24.dp, bottom = 16.dp))
                Text("Electives", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(
                    "Pick the ones you take, the rest stay off your week.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                view.catalog.forEach { (category, subjects) ->
                    val on = view.picks[category].orEmpty()
                    Picks(category.ifBlank { "Electives" }, subjects, on) { subject ->
                        val next = if (subject in on) on - subject else on + subject
                        view.setPicks(view.picks + (category to next))
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = {
                    scope.launch {
                        if (!view.matchPortal()) Toast.makeText(context, "Couldn't read your subjects off the portal", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Ic(R.drawable.ic_sync, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Match my portal subjects")
                }
            }
        }
    }
}

@Composable
private fun Choices(label: String, options: List<Pair<String, String>>, selected: String?, onPick: (String) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Label(label)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        options.forEach { (id, name) ->
            FilterChip(
                selected = id == selected,
                onClick = {
                    if (id != selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onPick(id)
                },
                label = { Text(name) },
            )
        }
    }
}

@Composable
private fun Picks(label: String, subjects: List<String>, on: Set<String>, onToggle: (String) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Label(label)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        subjects.forEach { subject ->
            val picked = subject in on
            FilterChip(
                selected = picked,
                onClick = {
                    haptics.performHapticFeedback(if (picked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    onToggle(subject)
                },
                label = { Text(subject) },
                leadingIcon = if (picked) ({ Ic(R.drawable.ic_done, null, Modifier.size(18.dp)) }) else null,
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}

// one class, tapped on the timeline

private val CLOCK = DateTimeFormatter.ofPattern("h:mm")
private val TIME = DateTimeFormatter.ofPattern("h:mm a")

@Composable
private fun ClassSheet(k: Klass, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                listOfNotNull(COMPONENT[k.type] ?: k.type, k.category?.takeIf { k.elective }).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(k.subject, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            if (k.code.isNotBlank()) Text(k.code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val start = if ((k.start.hour < 12) == (k.end.hour < 12)) k.start.format(CLOCK) else k.start.format(TIME)
                Fact(
                    R.drawable.ic_schedule,
                    "${k.day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}, $start – ${k.end.format(TIME)}",
                    minutes(Duration.between(k.start, k.end).toMinutes()),
                )
                if (k.venue.isNotBlank()) Fact(R.drawable.ic_meeting_room, k.venue, null)
                if (k.teacher.isNotBlank()) Fact(R.drawable.ic_person, k.teacher, null)
                if (k.batches.isNotEmpty()) Fact(R.drawable.ic_groups, k.batches.joinToString(", "), if (k.batches.size == 1) "batch" else "batches together")
            }
        }
    }
}
