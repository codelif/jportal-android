package `in`.codelif.jportal.feature.academics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import `in`.codelif.jportal.data.Repository
import `in`.codelif.jportal.data.Resource
import `in`.codelif.ktjiit.model.GradeEntry
import `in`.codelif.ktjiit.model.SemesterResult
import `in`.codelif.ktjiit.model.SubjectFaculty

/** one semester as the academics tab sees it, stitched from results, grade cards and registrations */
data class SemesterInfo(
    val code: String,
    val number: Int?,
    val result: SemesterResult?,
    val grades: List<GradeEntry>,
    val subjects: List<SubjectInfo>,
    val current: Boolean,
)

/** a registered subject, its L/T/P rows folded into one */
data class SubjectInfo(
    val semesterCode: String,
    val code: String,
    val name: String,
    val credits: Double,
    /** component letter to teacher, in L, T, P order */
    val teachers: List<Pair<String, String>>,
    val audit: Boolean,
    val grade: String?,
) {
    val lab: Boolean get() = teachers.isNotEmpty() && teachers.all { it.first == "P" }
}

class Academics(
    val semesters: List<SemesterInfo>,
    val results: Resource<List<SemesterResult>>,
    val currentCode: String?,
    /** true while any of the lists behind this are still on their way with nothing cached */
    val loading: Boolean,
    /** forces every store behind this */
    val refresh: () -> Unit,
) {
    fun semester(code: String) = semesters.firstOrNull { it.code == code }
}

private fun fold(semesterCode: String, rows: List<SubjectFaculty>, grades: List<GradeEntry>): List<SubjectInfo> =
    rows.groupBy { it.code }.map { (code, parts) ->
        val order = listOf("L", "T", "P")
        SubjectInfo(
            semesterCode = semesterCode,
            code = code,
            name = parts.first().name,
            credits = parts.maxOf { it.credits },
            teachers = parts.filter { it.facultyName.isNotBlank() }.sortedBy { order.indexOf(it.component).let { i -> if (i < 0) 9 else i } }
                .map { it.component to it.facultyName }.distinct(),
            audit = parts.any { it.isAudit },
            grade = grades.firstOrNull { it.code == code }?.grade?.takeIf { it.isNotBlank() },
        )
    }

/** where a regular term sits in time: 2025EVESEM (january) comes right before 2025ODDSEM (july). summer terms get no number */
fun term(code: String): Int? {
    val m = Regex("""(\d{4})(ODD|EVE|EVEN)SEM""").find(code.uppercase()) ?: return null
    return m.groupValues[1].toInt() * 2 + if (m.groupValues[2] == "ODD") 1 else 0
}

/**
 * everything the academics tab and the grade card page need. every grade card
 * is fetched so the subject list can show grades, they're small and cached
 * for half a day.
 */
@Composable
fun rememberAcademics(repo: Repository): Academics {
    val meta by repo.attendanceMeta.state.collectAsState()
    val currentNumber = meta.data?.header?.semesterNumber.orEmpty()
    val currentCode = meta.data?.semesters?.firstOrNull()?.code
    val resultsStore = remember(currentNumber) { repo.results(currentNumber) }
    val results by resultsStore.state.collectAsState()
    val gradeSems by repo.gradeSemesters.state.collectAsState()
    val subjSems by repo.subjectSemesters.state.collectAsState()
    LaunchedEffect(currentNumber) {
        repo.attendanceMeta.refresh()
        if (currentNumber.isNotEmpty()) resultsStore.refresh()
        repo.gradeSemesters.refresh()
        repo.subjectSemesters.refresh()
    }

    val gradeStores = gradeSems.data.orEmpty().map { s -> key("gcs", s.id) { remember { repo.gradeCard(s) } } }
    val regStores = subjSems.data.orEmpty().map { s -> key("rss", s.id) { remember { repo.subjects(s) } } }
    val cards = gradeSems.data.orEmpty().associate { s ->
        s.code to key("gc", s.id) {
            val st = remember { repo.gradeCard(s) }
            LaunchedEffect(st) { st.refresh() }
            st.state.collectAsState().value
        }
    }
    val regs = subjSems.data.orEmpty().associate { s ->
        s.code to key("rs", s.id) {
            val st = remember { repo.subjects(s) }
            LaunchedEffect(st) { st.refresh() }
            st.state.collectAsState().value
        }
    }

    val byNumber = results.data.orEmpty().associateBy { it.semester }
    // the grade card's stynumber is just today's semester on every card, so count terms back from the current one instead
    val anchor = currentCode?.let(::term)
    val anchorNumber = currentNumber.toIntOrNull()
    fun numberOf(code: String): Int? {
        val t = term(code) ?: return null
        return if (anchor != null && anchorNumber != null) (anchorNumber - (anchor - t)).takeIf { it > 0 } else null
    }
    val codes = (listOfNotNull(currentCode) + subjSems.data.orEmpty().map { it.code } + gradeSems.data.orEmpty().map { it.code }).distinct()
    val semesters = codes.map { code ->
        val grades = cards[code]?.data?.entries.orEmpty()
        val number = numberOf(code)
        SemesterInfo(
            code = code,
            number = number,
            result = number?.let { byNumber[it] },
            grades = grades,
            subjects = fold(code, regs[code]?.data?.rows.orEmpty(), grades),
            current = code == currentCode,
        )
    }.sortedWith(compareByDescending<SemesterInfo> { it.current }.thenByDescending { term(it.code) ?: 0 })

    val loading = listOf(meta, gradeSems, subjSems).any { it.data == null && it.error == null } ||
        (regs.values + cards.values).any { it.data == null && it.error == null }
    val refresh = {
        repo.attendanceMeta.refresh(force = true)
        resultsStore.refresh(force = true)
        repo.gradeSemesters.refresh(force = true)
        repo.subjectSemesters.refresh(force = true)
        (gradeStores + regStores).forEach { it.refresh(force = true) }
        Unit
    }
    return Academics(semesters, results, currentCode, loading, refresh)
}
