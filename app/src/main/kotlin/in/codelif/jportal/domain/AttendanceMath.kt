package `in`.codelif.jportal.domain

import `in`.codelif.ktjiit.model.ClassRecord
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor

/** attended / total with the target-driven verdicts jportal users know */
data class Tally(val attended: Int, val total: Int) {
    val percent: Double get() = if (total == 0) 0.0 else attended * 100.0 / total

    /** classes you can skip and still sit at or above [target]% */
    fun canMiss(target: Int): Int {
        if (target <= 0 || total == 0) return 0
        return floor((100.0 * attended - target * total) / target).toInt().coerceAtLeast(0)
    }

    /** classes in a row you need to climb back to [target]% */
    fun mustAttend(target: Int): Int {
        if (target >= 100) return if (attended < total) Int.MAX_VALUE else 0
        return ceil((target * total - 100.0 * attended) / (100 - target)).toInt().coerceAtLeast(0)
    }

    companion object {
        val ZERO = Tally(0, 0)
    }
}

enum class DayMark { Present, Absent, Mixed }

object AttendanceMath {
    fun tally(classes: List<ClassRecord>) = Tally(classes.count { it.isPresent }, classes.size)

    /** per day: all present, all absent, or a split day */
    fun calendar(classes: List<ClassRecord>): Map<LocalDate, DayMark> =
        classes.filter { it.date != null }.groupBy { it.date!! }.mapValues { (_, day) ->
            val p = day.count { it.isPresent }
            when (p) {
                day.size -> DayMark.Present
                0 -> DayMark.Absent
                else -> DayMark.Mixed
            }
        }

    /** running percentage after each class, oldest first, for the trend line */
    fun trend(classes: List<ClassRecord>): List<Pair<LocalDate, Double>> {
        val sorted = classes.filter { it.date != null }.sortedWith(compareBy({ it.date }, { it.start }))
        var present = 0
        return sorted.mapIndexed { i, c ->
            if (c.isPresent) present++
            c.date!! to present * 100.0 / (i + 1)
        }
    }
}
