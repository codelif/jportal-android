package `in`.codelif.jportal.feature.me

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.R
import `in`.codelif.jportal.data.Store
import `in`.codelif.jportal.feature.attendance.titleCase
import `in`.codelif.jportal.ui.LocalNavigator
import `in`.codelif.jportal.ui.components.Ic
import `in`.codelif.jportal.ui.components.MessageState
import `in`.codelif.jportal.ui.components.ScreenScaffold
import `in`.codelif.jportal.ui.components.SectionHeader
import `in`.codelif.jportal.ui.components.StaleNotice
import `in`.codelif.jportal.ui.components.resourceStates
import `in`.codelif.jportal.ui.nav.sharedBounds
import `in`.codelif.jportal.ui.theme.NumberStyle

/** label over value, long press copies the value */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    val clip = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    Column(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = {
                scope.launch { clip.setClipEntry(android.content.ClipData.newPlainText(label, value).toClipEntry()) }
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            })
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
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
        if (resourceStates(res, refresh)) content(res.data as T)
    }
}

@Composable
fun ProfileScreen() {
    val graph = LocalGraph.current
    Page("Profile", graph.repo.personal) { p ->
        val g = p.general
        item("head") {
            Column(Modifier.fillMaxWidth().padding(16.dp).sharedBounds("profile-card"), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(p.photo.photo, g.name, 112)
                Spacer(Modifier.height(12.dp))
                Text(g.name.titleCase(), style = MaterialTheme.typography.headlineSmall)
                Text(g.enrollmentNo, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item("acad-h") { SectionHeader("Academics") }
        item("acad") {
            Column {
                InfoRow("Program", listOf(g.program, g.branch).filter { it.isNotBlank() }.joinToString(" · "))
                InfoRow("Semester", g.semester.takeIf { it > 0 }?.toString())
                InfoRow("Batch", listOf(g.batch, g.section).filter { it.isNotBlank() }.joinToString(" · "))
                InfoRow("Admitted", g.admissionYear)
                InfoRow("APAAR ID", g.apaarId)
            }
        }
        item("contact-h") { SectionHeader("Contact") }
        item("contact") {
            Column {
                InfoRow("College email", g.collegeEmail)
                InfoRow("Personal email", g.personalEmail)
                InfoRow("Phone", g.phone)
                InfoRow("Address", listOf(g.currentAddress1, g.currentAddress3, g.currentCity, g.currentState, g.currentPin).filter { it.isNotBlank() }.joinToString(", "))
            }
        }
        item("family-h") { SectionHeader("Family") }
        item("family") {
            Column {
                InfoRow("Father", g.fatherName.titleCase())
                InfoRow("Mother", g.motherName.titleCase())
                InfoRow("Parent phone", g.parentPhone)
                InfoRow("Parent email", g.parentEmail)
            }
        }
        item("personal-h") { SectionHeader("Personal") }
        item("personal") {
            Column {
                InfoRow("Date of birth", g.dateOfBirth)
                InfoRow("Blood group", g.bloodGroup)
                InfoRow("Gender", g.gender)
                InfoRow("Category", g.category)
                InfoRow("Nationality", g.nationality)
            }
        }
        if (p.qualifications.isNotEmpty()) {
            item("q-h") { SectionHeader("Before JIIT") }
            items(p.qualifications, key = { "q" + it.code + it.year }) { q ->
                InfoRow("${q.code} · ${q.board} · ${q.year}", if (q.percent > 0) "%.1f%%".format(q.percent) else if (q.cgpa > 0) "CGPA ${q.cgpa}" else null)
            }
        }
        item("hint") {
            Text(
                "Long press anything to copy it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}

@Composable
fun FeesScreen() {
    Page("Fees", LocalGraph.current.repo.fees) { f ->
        item("due") {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Due right now", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("₹${"%,.0f".format(f.totalDue)}", style = NumberStyle, color = if (f.totalDue > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                f.advance.firstOrNull()?.takeIf { it.amount > 0 }?.let {
                    Text("₹${"%,.0f".format(it.amount)} in advance", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item("heads-h") { SectionHeader("By semester") }
        items(f.heads.sortedByDescending { it.semester }, key = { "${it.semester}-${it.academicYear}-${it.type}" }) { h ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Semester ${h.semester}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(h.type, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Money("Fee", h.fee)
                        Money("Paid", h.paid)
                        if (h.waived > 0) Money("Waived", h.waived)
                        Money("Due", h.due, alert = h.due > 0)
                    }
                }
            }
        }
    }
}

@Composable
private fun Money(label: String, v: Double, alert: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "₹${"%,.0f".format(v)}",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun BankScreen() {
    var shown by rememberSaveable { mutableStateOf(false) }
    Page("Bank details", LocalGraph.current.repo.bank) { b ->
        item("body") {
            AnimatedContent(shown, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "reveal") { visible ->
                if (!visible) {
                    MessageState(R.drawable.ic_visibility_off, "Hidden", "Your account number stays off screen until you ask.", "Show details") { shown = true }
                } else {
                    Column {
                        InfoRow("Bank", b.bank.titleCase())
                        InfoRow("Account number", b.account)
                        InfoRow("IFSC", b.ifsc)
                        InfoRow("Account holder", b.holder.titleCase())
                        InfoRow("Branch", listOf(b.address, b.city, b.state, b.pin).filter { it.isNotBlank() }.joinToString(", "))
                        InfoRow("Status", if (b.frozen == "Y") "Locked by the accounts office" else "Editable on the portal")
                        Row(Modifier.padding(20.dp)) {
                            FilledTonalButton(onClick = { shown = false }) {
                                Ic(R.drawable.ic_visibility_off, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Hide")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HostelScreen() {
    Page("Hostel", LocalGraph.current.repo.hostel) { h ->
        if (h == null) {
            item("none") { MessageState(R.drawable.ic_bed, "No hostel allotted", "Day scholar, or the allotment isn't in yet.") }
        } else {
            item("big") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(h.hostel.titleCase(), style = MaterialTheme.typography.headlineSmall)
                    Text("Room ${h.room}", style = NumberStyle)
                }
            }
            item("rows") {
                Column {
                    InfoRow("Floor", h.floor)
                    InfoRow("Bed", h.bed)
                    InfoRow("Type", h.hostelType)
                    InfoRow("Allotted", listOf(h.from, h.until).filter { it.isNotBlank() }.joinToString(" to "))
                    InfoRow("Left on", h.leftOn)
                }
            }
        }
    }
}

@Composable
fun FeedbackScreen() {
    Page("Feedback", LocalGraph.current.repo.feedback) { events ->
        if (events.isEmpty()) {
            item("none") {
                MessageState(
                    R.drawable.ic_rate_review, "No feedback window open",
                    "When the college opens one, it shows up here and on your home screen. JPortal can fill the whole form for you, and you review it before anything is sent.",
                )
            }
        } else {
            items(events, key = { it.id }) { e ->
                Entry(R.drawable.ic_rate_review, e.description.ifBlank { e.code }, "Open on the portal for now") {}
            }
        }
    }
}
