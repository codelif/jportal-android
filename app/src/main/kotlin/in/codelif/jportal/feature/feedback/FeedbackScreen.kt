package `in`.codelif.jportal.feature.feedback

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.R
import `in`.codelif.jportal.demo.Demo
import `in`.codelif.jportal.feature.academics.COMPONENT
import `in`.codelif.jportal.feature.attendance.nameCase
import `in`.codelif.jportal.feature.attendance.titleCase
import `in`.codelif.jportal.ui.LocalNavigator
import `in`.codelif.jportal.ui.components.CenteredLoading
import `in`.codelif.jportal.ui.components.Frog
import `in`.codelif.jportal.ui.components.group
import `in`.codelif.jportal.ui.components.Ic
import `in`.codelif.jportal.ui.components.MessageState
import `in`.codelif.jportal.ui.components.ScreenScaffold
import `in`.codelif.jportal.ui.components.SectionHeader
import `in`.codelif.jportal.ui.components.StaleNotice
import `in`.codelif.jportal.ui.components.describe
import `in`.codelif.jportal.ui.components.resourceStates
import `in`.codelif.ktjiit.model.FeedbackEvent
import `in`.codelif.ktjiit.model.FeedbackRow
import `in`.codelif.ktjiit.model.Rating
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val Rating.label: String
    get() = when (this) {
        Rating.UNSATISFIED -> "Unsatisfied"
        Rating.SATISFIED -> "Satisfied"
        Rating.GOOD -> "Good"
        Rating.VERY_GOOD -> "Very good"
        Rating.EXCELLENT -> "Excellent"
    }

class Form(val event: FeedbackEvent, val row: FeedbackRow) {
    val key = "${event.id}/${row.key}"
}

sealed interface Sending {
    data object Busy : Sending
    data object Sent : Sending
    data class Failed(val why: String) : Sending
}

/** every form of every open event, one rating each, sent one after another */
class FeedbackSession(val forms: List<Form>, private val scope: CoroutineScope, private val send: suspend (Form, Rating) -> Unit) {
    var everyone by mutableStateOf(Rating.VERY_GOOD)
    /** picked by hand, the everyone control leaves these alone */
    val custom = mutableStateMapOf<String, Rating>()
    val status = mutableStateMapOf<String, Sending>()
    var busy by mutableStateOf(false)
        private set

    fun rating(f: Form) = custom[f.key] ?: everyone
    val left get() = forms.filter { status[it.key] != Sending.Sent }
    val done get() = forms.isNotEmpty() && left.isEmpty()
    val failed get() = status.values.any { it is Sending.Failed }

    /** stops at the first failure, nothing retries on its own. already sent forms are skipped next time */
    fun submit() {
        if (busy) return
        busy = true
        // the app's scope, so backing out mid-way can't cut a write in half
        scope.launch {
            try {
                for (f in left) {
                    status[f.key] = Sending.Busy
                    try {
                        send(f, rating(f))
                        status[f.key] = Sending.Sent
                    } catch (e: CancellationException) {
                        status.remove(f.key)
                        throw e
                    } catch (e: Throwable) {
                        status[f.key] = Sending.Failed(describe(e))
                        break
                    }
                }
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun FeedbackScreen() {
    val graph = LocalGraph.current
    val nav = LocalNavigator.current
    val store = graph.repo.feedback
    val events by store.state.collectAsState()
    LaunchedEffect(store) { store.refresh() }

    // the grid is fetched fresh every visit, it's what we're about to write against
    var grid by remember { mutableStateOf<Result<List<Form>>?>(null) }
    var attempt by remember { mutableStateOf(0) }
    val list = events.data
    LaunchedEffect(list, attempt) {
        if (list.isNullOrEmpty()) return@LaunchedEffect
        grid = null
        grid = runCatching {
            if (graph.demo) list.flatMap { e -> Demo.feedbackRows().map { Form(e, it) } }
            else graph.sessions.call { p -> list.flatMap { e -> p.feedbackGrid(e).map { Form(e, it) } } }
        }
    }
    val session = grid?.getOrNull()?.let { forms ->
        remember(forms) {
            // the demo goes through the motions, nothing leaves the phone
            FeedbackSession(forms, graph.scope) { f, r -> if (graph.demo) delay(400) else graph.sessions.call { it.submitFeedback(f.event, f.row, r) } }
        }
    }
    // refetching the grid would wipe picked ratings, so a pull only rechecks the events
    val refresh = { store.refresh(force = true); Unit }

    Box(Modifier.fillMaxSize()) {
        ScreenScaffold(
            "Feedback",
            subtitle = session?.forms?.map { it.event }?.distinctBy { it.id }?.joinToString(" · ") { it.description.ifBlank { it.code } },
            onBack = { nav.pop() },
            refreshing = events.refreshing && events.data != null,
            onRefresh = { if (session?.busy != true) refresh() },
            bottomPadding = if (session != null && !session.done) 96.dp else 0.dp,
        ) {
            item("stale") { StaleNotice(events, refresh) }
            when {
                session != null -> form(session)
                !resourceStates(events, refresh) -> {}
                list.isNullOrEmpty() -> item("none") { MessageState(Frog.Sleep, "No feedback window open") }
                grid == null -> item("loading") { CenteredLoading() }
                else -> item("error") { MessageState(Frog.Plunger, describe(grid?.exceptionOrNull()), "Pull down or tap retry.", "Retry") { attempt++ } }
            }
        }
        if (session != null && !session.done && session.forms.isNotEmpty()) {
            SubmitBar(session, Modifier.align(Alignment.BottomCenter))
        }
    }
    // once everything's in, the me tab's "open" count should catch up
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(session?.done) {
        if (session?.done == true) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            store.refresh(force = true)
        }
    }
    LaunchedEffect(session?.failed) { if (session?.failed == true) haptics.performHapticFeedback(HapticFeedbackType.Reject) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.form(s: FeedbackSession) {
    if (s.forms.isEmpty()) {
        item("empty") { MessageState(Frog.Party, "Nothing left to fill") }
        return
    }
    if (s.done) {
        item("done") { MessageState(Frog.Party, "Feedback sent", "${s.forms.size} ${if (s.forms.size == 1) "form" else "forms"}") }
        return
    }
    item("everyone") { Everyone(s) }
    item("rows-h") { SectionHeader("${s.forms.size} ${if (s.forms.size == 1) "form" else "forms"}") }
    group("rows", bottom = 16.dp) {
        s.forms.forEach { f -> row(f.key) { FormRow(s, f) } }
    }
}

@Composable
private fun Everyone(s: FeedbackSession) {
    val haptics = LocalHapticFeedback.current
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp)) {
            Text("Rate everyone", style = MaterialTheme.typography.labelLarge)
            AnimatedContent(
                s.everyone,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith (slideOutVertically { if (up) -it else it } + fadeOut())
                },
                label = "everyone",
            ) { r -> Text(r.label, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)) }
            Slider(
                value = s.everyone.ordinal.toFloat(),
                onValueChange = { v ->
                    val r = Rating.entries[v.toInt().coerceIn(0, 4)]
                    if (r != s.everyone) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        s.everyone = r
                    }
                },
                valueRange = 0f..4f,
                steps = 3,
                enabled = !s.busy,
                modifier = Modifier.semantics { stateDescription = s.everyone.label },
            )
            Row {
                Text(Rating.UNSATISFIED.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Text(Rating.EXCELLENT.label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun FormRow(s: FeedbackSession, f: Form) {
    val st = s.status[f.key]
    Row(
        Modifier.fillMaxWidth().alpha(if (st == Sending.Sent) 0.6f else 1f).padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(f.row.facultyName.nameCase(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(f.row.subjectName.titleCase().ifBlank { null }, COMPONENT[f.row.component] ?: f.row.component.ifBlank { null }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (st is Sending.Failed) {
                Text(st.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.width(8.dp))
        Crossfade(st, label = "status") { now ->
            when (now) {
                null -> RatingChip(s, f)
                Sending.Busy -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp) }
                Sending.Sent -> Status(R.drawable.ic_check_circle, "Sent", MaterialTheme.colorScheme.primary)
                is Sending.Failed -> Status(R.drawable.ic_cancel, "Failed", MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun Status(icon: Int, text: String, color: Color) {
    Row(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Ic(icon, null, Modifier.size(20.dp), color)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
private fun RatingChip(s: FeedbackSession, f: Form) {
    var open by remember { mutableStateOf(false) }
    val mine = s.custom[f.key]
    Box {
        // filled once it's been picked by hand, outlined while it follows everyone
        FilterChip(
            selected = mine != null,
            onClick = { open = true },
            enabled = !s.busy,
            label = { Text(s.rating(f).label) },
            trailingIcon = { Ic(R.drawable.ic_expand_more, null, Modifier.size(18.dp)) },
        )
        DropdownMenu(open, onDismissRequest = { open = false }) {
            Rating.entries.reversed().forEach { r ->
                DropdownMenuItem(
                    text = { Text(r.label) },
                    onClick = { open = false; s.custom[f.key] = r },
                    trailingIcon = if (r == s.rating(f)) ({ Ic(R.drawable.ic_done, null) }) else null,
                )
            }
            if (mine != null) {
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Same as everyone") }, onClick = { open = false; s.custom.remove(f.key) })
            }
        }
    }
}

@Composable
private fun SubmitBar(s: FeedbackSession, modifier: Modifier) {
    var review by remember { mutableStateOf(false) }
    val left = s.left.size
    val sent = s.forms.size - left
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Button(
            onClick = { review = true },
            enabled = !s.busy,
            modifier = Modifier.navigationBarsPadding().fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(56.dp),
        ) {
            Text(
                when {
                    s.busy -> "Sending ${sent + 1} of ${s.forms.size}"
                    s.failed -> "Retry the $left left"
                    else -> "Review $left ${if (left == 1) "form" else "forms"}"
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
    if (review) Review(s, onDismiss = { review = false }) {
        review = false
        s.submit()
    }
}

@Composable
private fun Review(s: FeedbackSession, onDismiss: () -> Unit, onSubmit: () -> Unit) {
    val left = s.left
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            Text("Submit ${left.size} ${if (left.size == 1) "form" else "forms"}", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            left.groupBy { s.rating(it) }.toSortedMap(compareByDescending { it.ordinal }).forEach { (r, forms) ->
                Row(Modifier.padding(vertical = 6.dp)) {
                    Text(r.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${forms.size}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    forms.joinToString(", ") { it.row.facultyName.nameCase() + (COMPONENT[it.row.component]?.let { c -> " ($c)" } ?: "") },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = onSubmit) { Text("Submit") }
            }
        }
    }
}
