package `in`.codelif.jportal.domain

import `in`.codelif.ktjiit.model.SemesterResult

/** one point on the gpa chart, real or projected */
data class GpaPoint(
    val semester: Int,
    val sgpa: Double,
    val credits: Double,
    val cgpa: Double,
    val projected: Boolean,
)

/**
 * port of jportal's gpaCalculations: cgpa is credit weighted sgpa over every
 * semester so far. projections are extra semesters the user drags around.
 */
object Gpa {
    fun points(results: List<SemesterResult>, projections: Map<Int, Double>, projectedCredits: Double, maxSemesters: Int): List<GpaPoint> {
        val real = results.sortedBy { it.semester }.map { GpaPoint(it.semester, it.sgpa, creditsOf(it), it.cgpa, false) }
        val next = (real.maxOfOrNull { it.semester } ?: 0) + 1
        val future = (next..maxSemesters).mapNotNull { s ->
            projections[s]?.let { GpaPoint(s, it, projectedCredits, 0.0, true) }
        }
        return recompute(real.map { p -> projections[p.semester]?.let { p.copy(sgpa = it, projected = true) } ?: p } + future, keepReal = true)
    }

    private fun creditsOf(r: SemesterResult) = r.courseCredits.takeIf { it > 0 } ?: r.registeredCredits

    /** recomputes cgpa down the line; real untouched semesters keep the portal's own figure */
    fun recompute(points: List<GpaPoint>, keepReal: Boolean): List<GpaPoint> {
        var gp = 0.0
        var cr = 0.0
        var anyProjected = false
        return points.map { p ->
            gp += p.sgpa * p.credits
            cr += p.credits
            anyProjected = anyProjected || p.projected
            if (keepReal && !anyProjected) p else p.copy(cgpa = if (cr > 0) gp / cr else 0.0)
        }
    }

    /** the sgpa needed over [remaining] semesters of [credits] each to land on [targetCgpa] */
    fun needed(current: List<GpaPoint>, targetCgpa: Double, remaining: Int, credits: Double): Double? {
        if (remaining <= 0) return null
        val gp = current.sumOf { it.sgpa * it.credits }
        val cr = current.sumOf { it.credits }
        return (targetCgpa * (cr + remaining * credits) - gp) / (remaining * credits)
    }
}
