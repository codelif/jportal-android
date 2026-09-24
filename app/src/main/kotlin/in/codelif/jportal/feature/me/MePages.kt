package `in`.codelif.jportal.feature.me

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.R
import `in`.codelif.jportal.data.Store
import `in`.codelif.jportal.feature.attendance.titleCase
import `in`.codelif.jportal.ui.LocalNavigator
import `in`.codelif.jportal.ui.components.Frog
import `in`.codelif.jportal.ui.components.Group
import `in`.codelif.jportal.ui.components.GroupScope
import `in`.codelif.jportal.ui.components.Ic
import `in`.codelif.jportal.ui.components.MessageState
import `in`.codelif.jportal.ui.components.ScreenScaffold
import `in`.codelif.jportal.ui.components.SectionHeader
import `in`.codelif.jportal.ui.components.StaleNotice
import `in`.codelif.jportal.ui.components.resourceStates
import `in`.codelif.jportal.ui.theme.NumberStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** label over value, a tap copies [copy] (the value unless told otherwise) */
@Composable
fun InfoRow(label: String, value: String?, copy: String? = value, trailing: @Composable (() -> Unit)? = null) {
    if (value.isNullOrBlank()) return
    val clip = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = !copy.isNullOrBlank()) {
                scope.launch { clip.setClipEntry(android.content.ClipData.newPlainText(label, copy).toClipEntry()) }
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                // android 13 and up show their own copied chip
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
            }
            .padding(start = 20.dp, end = if (trailing != null) 8.dp else 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        trailing?.invoke()
    }
}

/** a group of info rows, blank ones dropped so the corners land on real rows */
@Composable
private fun InfoGroup(vararg rows: Pair<String, String?>, modifier: Modifier = Modifier, extra: GroupScope.() -> Unit = {}) {
    Group(modifier) {
        rows.filter { !it.second.isNullOrBlank() }.forEach { (k, v) -> row { InfoRow(k, v) } }
        extra()
    }
}

@Composable
private fun <T> Page(title: String, store: Store<T>, content: androidx.compose.foundation.lazy.LazyListScope.(T) -> Unit) {
    val nav = LocalNavigator.current
    val res by store.state.collectAsState()
    LaunchedEffect(store) { store.refresh() }
    val refresh = { store.refresh(force = true); Unit }
    ScreenScaffold(title, onBack = { nav.pop() }, refreshing = res.refreshing && res.data != null, onRefresh = refresh) {
        item("stale") { StaleNotice(res, refresh) }
        @Suppress("UNCHECKED_CAST")
        if (resourceStates(res, refresh)) content(res.data as T)
    }
}

@Composable
fun ProfileScreen() {
    val graph = LocalGraph.current
    Page("Profile", graph.repo.personal) { p ->
        val g = p.general
        item("head") {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(p.photo.photo, g.name, 112)
                Spacer(Modifier.height(12.dp))
                Text(g.name.titleCase(), style = MaterialTheme.typography.headlineSmall)
                Text(g.enrollmentNo, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Tap to copy", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item("acad-h") { SectionHeader("Academics") }
        item("acad") {
            InfoGroup(
                "Program" to listOf(g.program, g.branch).filter { it.isNotBlank() }.joinToString(" · "),
                "Semester" to g.semester.takeIf { it > 0 }?.toString(),
                "Batch" to listOf(g.batch, g.section).filter { it.isNotBlank() }.joinToString(" · "),
                "Admitted" to g.admissionYear,
                "APAAR ID" to g.apaarId,
            )
        }
        item("contact-h") { SectionHeader("Contact") }
        item("contact") {
            InfoGroup(
                "College email" to g.collegeEmail,
                "Personal email" to g.personalEmail,
                "Phone" to g.phone,
                "Address" to listOf(g.currentAddress1, g.currentAddress3, g.currentCity, g.currentState, g.currentPin).filter { it.isNotBlank() }.joinToString(", "),
            )
        }
        item("family-h") { SectionHeader("Family") }
        item("family") {
            InfoGroup(
                "Father" to g.fatherName.titleCase(),
                "Mother" to g.motherName.titleCase(),
                "Parent phone" to g.parentPhone,
                "Parent email" to g.parentEmail,
            )
        }
        item("personal-h") { SectionHeader("Personal") }
        item("personal") {
            InfoGroup(
                "Date of birth" to g.dateOfBirth,
                "Blood group" to g.bloodGroup,
                "Gender" to g.gender,
                "Category" to g.category,
                "Nationality" to g.nationality,
            )
        }
        if (p.qualifications.isNotEmpty()) {
            item("q-h") { SectionHeader("Before JIIT") }
            item("q") {
                InfoGroup(
                    *p.qualifications.map { q ->
                        "${q.code} · ${q.board} · ${q.year}" to (if (q.percent > 0) "%.1f%%".format(q.percent) else if (q.cgpa > 0) "CGPA ${q.cgpa}" else null)
                    }.toTypedArray(),
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
        }
    }
}

private fun rupees(v: Double) = "₹${"%,.0f".format(v)}"

@Composable
fun FeesScreen() {
    Page("Fees", LocalGraph.current.repo.fees) { f ->
        if (f.heads.isEmpty()) {
            item("none") { MessageState(Frog.Sleep, "No fee records") }
            return@Page
        }
        item("heads") {
            Group(Modifier.padding(top = 8.dp, bottom = 16.dp)) {
                f.heads.sortedByDescending { it.semester }.forEach { h ->
                    row {
                        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Semester ${h.semester}", style = MaterialTheme.typography.titleMedium)
                                // academicyear is the same stale value on every head, so it stays out
                                Text(
                                    listOfNotNull(
                                        h.type.takeIf { it.isNotBlank() },
                                        h.waived.takeIf { it > 0 }?.let { "${rupees(it)} waived" },
                                        h.refunded.takeIf { it > 0 }?.let { "${rupees(it)} refunded" },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${rupees(h.paid)} paid", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Text("of ${rupees(h.fee)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (h.due > 0) Text("${rupees(h.due)} due", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
        f.advance.firstOrNull()?.takeIf { it.amount > 0 }?.let {
            item("advance") {
                Text(
                    "${rupees(it.amount)} held as advance",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
    }
}

/** "•••• 4821": enough to recognise it, not enough to read it off over a shoulder */
private fun masked(account: String) = if (account.length > 4) "•••• ${account.takeLast(4)}" else account

@Composable
fun BankScreen() {
    // plain remember: leaving the page always masks it again
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(shown) {
        if (shown) {
            delay(30_000)
            shown = false
        }
    }
    Page("Bank details", LocalGraph.current.repo.bank) { b ->
        item("rows") {
            Group(Modifier.padding(top = 8.dp)) {
                row {
                    InfoRow("Account number", if (shown) b.account else masked(b.account), copy = b.account) {
                        IconButton(onClick = { shown = !shown }) {
                            Ic(if (shown) R.drawable.ic_visibility_off else R.drawable.ic_visibility, if (shown) "Hide" else "Show")
                        }
                    }
                }
                listOf(
                    "IFSC" to b.ifsc,
                    "Bank" to b.bank.titleCase(),
                    "Account holder" to b.holder.titleCase(),
                    "Branch" to listOf(b.address, b.city, b.state, b.pin).filter { it.isNotBlank() }.joinToString(", "),
                    "Status" to if (b.frozen == "Y") "Locked by the accounts office" else "Editable on the portal",
                ).filter { it.second.isNotBlank() }.forEach { (k, v) -> row { InfoRow(k, v) } }
            }
        }
        item("hint") {
            Text(
                "Tap to copy",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}

@Composable
fun HostelScreen() {
    Page("Hostel", LocalGraph.current.repo.hostel) { h ->
        if (h == null) {
            item("none") { MessageState(Frog.Sleep, "No hostel allotted", "Day scholar, or the allotment isn't in yet.") }
        } else {
            item("hero") {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(h.hostel.titleCase(), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text("Room", style = MaterialTheme.typography.labelLarge)
                        Text(h.room.ifBlank { "–" }, style = NumberStyle)
                        val chips = listOf(
                            h.floor.takeIf { it.isNotBlank() }?.let { "Floor $it" },
                            h.bed.takeIf { it.isNotBlank() },
                            h.roomType.takeIf { it.isNotBlank() }?.titleCase(),
                            h.hostelType.takeIf { it.isNotBlank() }?.titleCase(),
                        ).filterNotNull()
                        if (chips.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                chips.forEach { c ->
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)) {
                                        Text(c, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item("dates") {
                InfoGroup(
                    "Allotted" to listOf(h.from, h.until).filter { it.isNotBlank() }.joinToString(" to "),
                    "Left on" to h.leftOn,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
