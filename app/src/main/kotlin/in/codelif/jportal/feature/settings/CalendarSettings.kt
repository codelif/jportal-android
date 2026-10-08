package `in`.codelif.jportal.feature.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.R
import `in`.codelif.jportal.data.PhoneCalendar
import `in`.codelif.jportal.feature.me.Entry
import `in`.codelif.jportal.ui.components.Group
import kotlinx.coroutines.launch

/** the settings group for exam sync: the switch, the calendar it writes to, and why it's off */
@Composable
fun ExamSyncGroup() {
    val graph = LocalGraph.current
    val sync = graph.calendar
    val prefs = graph.prefs
    val context = LocalContext.current
    val on by prefs.calendarSyncState.collectAsState()
    val calId by prefs.calendarState.collectAsState()
    val denied by prefs.calendarDeniedState.collectAsState()
    var calendars by remember { mutableStateOf(emptyList<PhoneCalendar>()) }
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(on, calId) { if (on) calendars = sync.calendars() }

    // graph scope, so leaving settings halfway doesn't leave half a calendar behind
    fun pick() = graph.scope.launch {
        calendars = sync.calendars()
        picking = true
    }
    fun turnOn() = graph.scope.launch {
        prefs.setCalendarDenied(false)
        val all = sync.calendars()
        calendars = all
        val saved = calId?.takeIf { id -> all.any { it.id == id } }
        if (saved != null) sync.enable(saved) else picking = true
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.isNotEmpty() && r.values.all { it }) turnOn() else prefs.setCalendarDenied(true)
    }

    Group {
        row {
            Toggle(
                "Sync to calendar",
                when {
                    on -> "Upcoming papers, with reminders the evening before and an hour before"
                    denied -> "Calendar access denied. Allow it in system settings"
                    else -> "Puts upcoming papers in a calendar you pick and keeps room and seat current"
                },
                on,
            ) { want ->
                when {
                    !want -> graph.scope.launch { sync.disable() }
                    sync.hasAccess() -> turnOn()
                    else -> ask.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                }
            }
        }
        if (on) row {
            val cal = calendars.firstOrNull { it.id == calId }
            Entry(R.drawable.ic_calendar_month, "Calendar", cal?.let(::label) ?: "Pick one") { pick() }
        }
        // a second "no" from the system dialog stops it showing, only app info can grant it then
        if (denied && !on) row {
            Entry(R.drawable.ic_open_in_new, "Open app settings", "Allow calendar there, then turn sync on") {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            }
        }
    }
    if (picking) CalendarPicker(calendars, calId, onDismiss = { picking = false }) { id ->
        picking = false
        graph.scope.launch { sync.enable(id) }
    }
}

/** google names your main calendar after the account, no point saying it twice */
private fun label(c: PhoneCalendar) = if (c.name == c.account || c.account.isBlank()) c.name else "${c.name} · ${c.account}"

@Composable
private fun CalendarPicker(calendars: List<PhoneCalendar>, selected: Long?, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sync exams to") },
        text = {
            if (calendars.isEmpty()) {
                Text("No calendar on this phone takes new events. Add an account in your calendar app first.")
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    calendars.forEach { c ->
                        Row(
                            Modifier.fillMaxWidth().selectable(c.id == selected, role = Role.RadioButton) { onPick(c.id) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(c.id == selected, null)
                            Spacer(Modifier.width(12.dp))
                            Box(Modifier.size(12.dp).background(Color(c.color), CircleShape))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.bodyLarge)
                                if (c.account.isNotBlank() && c.account != c.name) {
                                    Text(c.account, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
