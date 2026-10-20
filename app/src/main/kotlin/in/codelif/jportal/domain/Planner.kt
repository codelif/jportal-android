package `in`.codelif.jportal.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

/** one slot on the weekly timetable, shared by every batch it lists */
data class Klass(
    val day: DayOfWeek,
    val start: LocalTime,
    val end: LocalTime,
    val subject: String,
    val code: String,
    val teacher: String,
    val venue: String,
    /** L, T or P */
    val type: String,
    val category: String?,
    val elective: Boolean,
    val batches: List<String>,
)

/** every course's weekly timetable, as the planner cdn publishes it */
class Timetable(val version: String, val courses: List<Course>) {
    class Course(val id: String, val name: String, val semesters: List<Semester>)
    class Semester(val id: String, val name: String, val phases: List<Phase>)
    class Phase(val id: String, val name: String, val batches: List<Batch>)
    /** [classes] already holds every elective the batch could sit, picks narrow it down */
    class Batch(val id: String, val name: String, val classes: List<Klass>)

    fun batch(path: BatchPath): Batch? =
        courses.firstOrNull { it.id == path.course }
            ?.semesters?.firstOrNull { it.id == path.semester }
            ?.phases?.firstOrNull { it.id == path.phase }
            ?.batches?.firstOrNull { it.id == path.batch }
}

data class BatchPath(val course: String, val semester: String, val phase: String, val batch: String) {
    val key: String get() = "$course/$semester/$phase/$batch"

    /** electives are picked per phase, a batch change inside it keeps them */
    val phaseKey: String get() = "$course/$semester/$phase"

    companion object {
        fun parse(key: String?): BatchPath? = key?.split('/')?.takeIf { it.size == 4 }?.let { (c, s, p, b) -> BatchPath(c, s, p, b) }
    }
}

/**
 * the student's batch, from what the portal calls it: the one batch with that
 * id in their semester. batch ids don't repeat across courses within a
 * semester, so [program] only breaks a tie, the portal spells it its own way
 * ("B.T"). none or still a tie means the picker asks.
 */
fun Timetable.detect(batch: String, semester: Int, program: String): BatchPath? {
    val want = batch.trim()
    if (want.isEmpty() || semester <= 0) return null
    val hits = courses.flatMap { c ->
        c.semesters.filter { it.id == "sem$semester" }.flatMap { s ->
            s.phases.flatMap { p -> p.batches.filter { it.id.equals(want, ignoreCase = true) }.map { BatchPath(c.id, s.id, p.id, it.id) } }
        }
    }
    if (hits.size <= 1) return hits.singleOrNull()
    val bca = program.contains("BCA", ignoreCase = true)
    return hits.filter { it.course.startsWith("bca") == bca }.singleOrNull()
}

/** what the picker offers: elective category to its subjects */
fun Timetable.Batch.electives(): Map<String, List<String>> =
    classes.filter { it.elective }.groupBy { it.category.orEmpty() }
        .mapValues { (_, l) -> l.map { it.subject }.distinct().sorted() }
        .toSortedMap()

/** picks from the subjects registered on the portal, matched on code, then on name */
fun Timetable.Batch.prefill(registered: List<Pair<String, String>>): Map<String, Set<String>> {
    val codes = registered.map { it.first.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
    val names = registered.map { it.second.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
    return classes.filter { it.elective && (it.code.trim().uppercase() in codes || it.subject.trim().lowercase() in names) }
        .groupBy { it.category.orEmpty() }
        .mapValues { (_, l) -> l.map { it.subject }.toSet() }
}

/** the batch's week: everything that isn't an elective, and the electives picked in their category */
fun Timetable.Batch.week(picks: Map<String, Set<String>>): List<Klass> =
    classes.filter { !it.elective || it.subject in picks[it.category.orEmpty()].orEmpty() }
        .sortedWith(compareBy({ it.day }, { it.start }, { it.end }))

/** one day of the week as the planner draws it, classes with the free stretches between them */
class DayPlan(val rows: List<Row>, val now: Set<Klass>) {
    sealed interface Row {
        data class Class(val k: Klass) : Row
        data class Free(val minutes: Long) : Row
    }

    companion object {
        const val MIN_FREE = 30L

        /** [classes] sorted by start. [now] only on the day it is, null on every other */
        fun of(classes: List<Klass>, now: LocalTime?): DayPlan {
            val rows = mutableListOf<Row>()
            // measured from the latest end so far, two overlapping slots never fake a gap
            var until: LocalTime? = null
            for (k in classes) {
                until?.let { u -> Duration.between(u, k.start).toMinutes().takeIf { it >= MIN_FREE }?.let { rows += Row.Free(it) } }
                rows += Row.Class(k)
                until = until?.let { maxOf(it, k.end) } ?: k.end
            }
            val live = if (now == null) emptySet() else classes.filter { now >= it.start && now < it.end }.toSet()
            return DayPlan(rows, live)
        }
    }
}
