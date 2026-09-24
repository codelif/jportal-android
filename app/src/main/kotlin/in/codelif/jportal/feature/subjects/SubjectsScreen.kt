package `in`.codelif.jportal.feature.subjects

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.R
import `in`.codelif.jportal.feature.academics.Academics
import `in`.codelif.jportal.feature.academics.COMPONENT
import `in`.codelif.jportal.feature.academics.Figure
import `in`.codelif.jportal.feature.academics.SemesterInfo
import `in`.codelif.jportal.feature.academics.SubjectInfo
import `in`.codelif.jportal.feature.academics.rememberAcademics
import `in`.codelif.jportal.feature.attendance.nameCase
import `in`.codelif.jportal.feature.attendance.titleCase
import `in`.codelif.jportal.feature.me.Entry
import `in`.codelif.jportal.feature.registration.rememberPicked
import `in`.codelif.jportal.feature.subject.fmt
import `in`.codelif.jportal.ui.LocalBottomInset
import `in`.codelif.jportal.ui.LocalNavigator
import `in`.codelif.jportal.ui.components.CenteredLoading
import `in`.codelif.jportal.ui.components.Frog
import `in`.codelif.jportal.ui.components.GradeLetter
import `in`.codelif.jportal.ui.components.Group
import `in`.codelif.jportal.ui.components.group
import `in`.codelif.jportal.ui.components.Ic
import `in`.codelif.jportal.ui.components.MessageState
import `in`.codelif.jportal.ui.components.ScreenScaffold
import `in`.codelif.jportal.ui.components.SectionHeader
import `in`.codelif.jportal.ui.components.prettySemester
import `in`.codelif.jportal.ui.nav.Route
import `in`.codelif.jportal.ui.theme.LocalExtraColors
import `in`.codelif.ktjiit.model.MoocStatus
import kotlinx.coroutines.flow.drop

enum class SubjectFilter(val label: String) { Current("This semester"), Labs("Labs"), Graded("Graded") }

private val filterSaver = Saver<MutableState<Set<SubjectFilter>>, List<String>>(
    save = { st -> st.value.map { it.name } },
    restore = { l -> mutableStateOf(l.map { SubjectFilter.valueOf(it) }.toSet()) },
)

/** everything ever registered, searchable */
@Composable
fun SubjectsScreen() {
    val data = rememberAcademics(LocalGraph.current.repo)
    var query by rememberSaveable { mutableStateOf("") }
    var filters by rememberSaveable(saver = filterSaver) { mutableStateOf(emptySet()) }
    val list = rememberLazyListState()
    val focus = LocalFocusManager.current
    // reading the list, not typing any more
    LaunchedEffect(list.isScrollInProgress) { if (list.isScrollInProgress) focus.clearFocus() }
    // a narrower search shouldn't leave you staring at the bottom of a shorter list
    LaunchedEffect(Unit) { snapshotFlow { query to filters }.drop(1).collect { list.scrollToItem(0) } }

    ScreenScaffold(
        title = "Subjects",
        refreshing = data.results.refreshing && data.results.data != null,
        onRefresh = data.refresh,
        listState = list,
        bottomPadding = LocalBottomInset.current,
        pinned = { SubjectSearch(query, { query = it }, filters, { filters = it }) },
    ) { subjects(data, query, filters) }
}

@Composable
private fun SubjectSearch(q: String, onQuery: (String) -> Unit, f: Set<SubjectFilter>, onFilters: (Set<SubjectFilter>) -> Unit) {
    val focus = LocalFocusManager.current
    Column {
        TextField(
            value = q,
            onValueChange = onQuery,
            placeholder = { Text("Search subjects, teachers, codes") },
            leadingIcon = { Ic(R.drawable.ic_search, null) },
            trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Ic(R.drawable.ic_close, "Clear") } },
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SubjectFilter.entries.forEach { x ->
                FilterChip(
                    selected = x in f,
                    onClick = { onFilters(if (x in f) f - x else f + x) },
                    label = { Text(x.label) },
                    leadingIcon = if (x in f) ({ Ic(R.drawable.ic_done, null) }) else null,
                )
            }
        }
    }
}

private fun matches(s: SemesterInfo, sub: SubjectInfo, q: String, f: Set<SubjectFilter>) =
    (SubjectFilter.Current !in f || s.current) &&
        (SubjectFilter.Labs !in f || sub.lab) &&
        (SubjectFilter.Graded !in f || sub.grade != null) &&
        (q.isEmpty() || sub.name.lowercase().contains(q) || sub.code.lowercase().contains(q) ||
            sub.teachers.any { it.second.lowercase().contains(q) })

/** an item per header and per subject, filtering then slides rows instead of redrawing one tall column */
private fun LazyListScope.subjects(data: Academics, query: String, f: Set<SubjectFilter>) {
    val q = query.trim().lowercase()
    val groups = data.semesters.map { s -> s to s.subjects.filter { matches(s, it, q, f) } }.filter { it.second.isNotEmpty() }
    when {
        groups.isEmpty() && data.loading -> item("loading") { CenteredLoading() }
        groups.isEmpty() -> item("none") {
            MessageState(
                Frog.Look,
                when {
                    q.isNotEmpty() -> "Nothing matches “${query.trim()}”"
                    f.isNotEmpty() -> "Nothing matches these filters"
                    else -> "No subjects yet"
                },
            )
        }
    }
    // registration is about this semester's paperwork, not a subject, so it steps aside while you search
    if (q.isEmpty() && f.isEmpty()) {
        item("reg-h") { SectionHeader("Registration", Modifier.animateItem()) }
        item("reg") { RegistrationRows(Modifier.animateItem()) }
    }
    groups.forEach { (s, list) ->
        item("h-${s.code}") {
            SectionHeader(
                listOfNotNull(s.number?.let { "Semester $it" }, prettySemester(s.code)).joinToString(" · "),
                Modifier.animateItem(),
                trailing = if (s.current) ({ NowBadge() }) else null,
            )
        }
        group("g-${s.code}", contentType = "subject", animate = true) {
            list.forEach { sub ->
                row(sub.code) {
                    val nav = LocalNavigator.current
                    SubjectRow(sub, q) { nav.push(Route.Subject(sub.semesterCode, sub.code)) }
                }
            }
        }
    }
}

@Composable
private fun RegistrationRows(modifier: Modifier) {
    val repo = LocalGraph.current.repo
    val nav = LocalNavigator.current
    val choices = rememberPicked(repo.choiceSemesters, emptyList()) { repo.choices(it) }
    val mooc = rememberPicked(repo.moocSemesters, MoocStatus()) { repo.mooc(it) }
    val electives = choices.res.data?.filter { it.isElective }?.map { it.basket }?.distinct()?.size
    val moocLine = mooc.res.data?.let { m ->
        val approved = m.requests.count { it.isApproved }
        val waiting = m.requests.size - approved
        listOfNotNull("$approved approved".takeIf { approved > 0 }, "$waiting in review".takeIf { waiting > 0 }, "${m.rejected.size} rejected".takeIf { m.rejected.isNotEmpty() })
            .joinToString(" · ").ifEmpty { "No requests" }
    }
    Group(modifier) {
        row {
            Entry(
                R.drawable.ic_checklist, "Subject choices",
                choices.semester?.let { s -> listOfNotNull(prettySemester(s.code), electives?.takeIf { it > 0 }?.let { "$it ${if (it == 1) "elective" else "electives"}" }).joinToString(" · ") },
            ) { nav.push(Route.Choices) }
        }
        row {
            Entry(R.drawable.ic_cast_for_education, "MOOC status", mooc.semester?.let { s -> listOfNotNull(prettySemester(s.code), moocLine).joinToString(" · ") }) {
                nav.push(Route.Mooc)
            }
        }
    }
}

@Composable
private fun NowBadge() {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Text("Now", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

/** bold and tinted where the query hit, so you can see why a row is here */
@Composable
private fun hit(text: String, q: String): AnnotatedString {
    if (q.isEmpty()) return AnnotatedString(text)
    val style = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    val lower = text.lowercase()
    return buildAnnotatedString {
        append(text)
        var i = lower.indexOf(q)
        while (i >= 0) {
            addStyle(style, i, i + q.length)
            i = lower.indexOf(q, i + q.length)
        }
    }
}

@Composable
private fun SubjectRow(s: SubjectInfo, q: String, onClick: () -> Unit) {
    val extra = LocalExtraColors.current
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(hit(s.name.titleCase(), q), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    hit(s.code + if (s.audit) " · audit" else "", q),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                s.teachers.forEach { (k, name) ->
                    Text(
                        hit("${COMPONENT[k] ?: k}: ${name.nameCase()}", q),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            // graded subjects show the grade, running ones show credits big, the way jportal's subjects page did
            if (s.grade != null) {
                GradeLetter(s.grade, size = 44.dp)
            } else {
                Figure(fmt(s.credits), "credits", extra.credits)
            }
        }
    }
}
