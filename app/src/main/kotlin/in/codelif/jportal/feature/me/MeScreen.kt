package `in`.codelif.jportal.feature.me

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.R
import `in`.codelif.jportal.feature.attendance.titleCase
import `in`.codelif.jportal.ui.LocalBottomInset
import `in`.codelif.jportal.ui.LocalNavigator
import `in`.codelif.jportal.ui.components.BigEnergy
import `in`.codelif.jportal.ui.components.Ic
import `in`.codelif.jportal.ui.components.ScreenScaffold
import `in`.codelif.jportal.ui.components.SectionHeader
import `in`.codelif.jportal.ui.nav.Route
import `in`.codelif.jportal.ui.nav.sharedBounds

@Composable
fun MeScreen() {
    val graph = LocalGraph.current
    val repo = graph.repo
    val nav = LocalNavigator.current
    val session = graph.sessions.session
    val personal by repo.personal.state.collectAsState()
    val fees by repo.fees.state.collectAsState()
    val feedback by repo.feedback.state.collectAsState()
    val hostel by repo.hostel.state.collectAsState()
    LaunchedEffect(Unit) { repo.personal.refresh(); repo.fees.refresh(); repo.feedback.refresh(); repo.hostel.refresh() }
    val info = personal.data?.general

    ScreenScaffold(
        title = "Me",
        bottomPadding = LocalBottomInset.current,
        refreshing = personal.refreshing && personal.data != null,
        onRefresh = { repo.personal.refresh(true); repo.fees.refresh(true); repo.feedback.refresh(true); repo.hostel.refresh(true) },
    ) {
        item("card") {
            Surface(
                onClick = { nav.push(Route.Profile) },
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).sharedBounds("profile-card"),
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(personal.data?.photo?.photo, (info?.name ?: session?.name).orEmpty(), 64)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text((info?.name ?: session?.name).orEmpty().titleCase(), style = MaterialTheme.typography.titleLarge)
                        Text(session?.enrollmentNo.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        info?.let {
                            Text(
                                listOf(it.program, it.branch, it.semester.takeIf { s -> s > 0 }?.let { s -> "Sem $s" }).filterNotNull().filter { s -> s.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                            )
                        }
                    }
                    Ic(R.drawable.ic_chevron_right, null)
                }
            }
        }
        item("money-h") { SectionHeader("Money and stay") }
        item("fees") {
            val due = fees.data?.totalDue ?: 0.0
            Entry(R.drawable.ic_payments, "Fees", if (fees.data == null) null else if (due > 0) "₹${"%,.0f".format(due)} due" else "Nothing due", alert = due > 0) { nav.push(Route.Fees) }
        }
        item("bank") { Entry(R.drawable.ic_account_balance, "Bank details", "Hidden until you tap") { nav.push(Route.Bank) } }
        item("hostel") {
            Entry(R.drawable.ic_bed, "Hostel", hostel.data?.let { h -> listOf(h.hostel, h.room.takeIf { it.isNotBlank() }?.let { "Room $it" }).filterNotNull().joinToString(" · ") }) { nav.push(Route.Hostel) }
        }
        item("college-h") { SectionHeader("College") }
        item("feedback") {
            val open = feedback.data.orEmpty().size
            Entry(R.drawable.ic_rate_review, "Feedback", if (open > 0) "$open open, fill it in a tap" else "No feedback window open", alert = open > 0) { nav.push(Route.Feedback) }
        }
        item("app-h") { SectionHeader("App") }
        item("settings") { Entry(R.drawable.ic_settings, "Settings", "Theme, goal, icon") { nav.push(Route.Settings) } }
        item("about") { Entry(R.drawable.ic_info, "About JPortal", "Credits, licenses, debug report") { nav.push(Route.About) } }
        item("energy") { BigEnergy(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) }
    }
}

@Composable
fun Avatar(base64: String?, name: String, sizeDp: Int) {
    val bitmap = remember(base64) {
        base64?.takeIf { it.length > 32 }?.let {
            runCatching { Base64.decode(it.substringAfter(","), Base64.DEFAULT) }.getOrNull()
                ?.let { b -> BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }
        }
    }
    if (bitmap != null) {
        Image(bitmap, "Photo", Modifier.size(sizeDp.dp).clip(CircleShape), contentScale = ContentScale.Crop)
    } else {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(sizeDp.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
fun Entry(icon: Int, title: String, subtitle: String?, alert: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (alert) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Ic(icon, null, tint = if (alert) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
