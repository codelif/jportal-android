package `in`.codelif.jportal.domain

import java.time.DayOfWeek
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
