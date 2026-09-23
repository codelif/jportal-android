package `in`.codelif.jportal.ui.nav

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

/** everything you can land on. tabs are roots, the rest stack on top of them */
sealed interface Route {
    val key: String

    sealed interface Tab : Route
    data object Attendance : Tab { override val key = "attendance" }
    data object Exams : Tab { override val key = "exams" }
    data object Academics : Tab { override val key = "academics" }
    data object Me : Tab { override val key = "me" }

    /** codes, not portal ids, so any semester's subject opens the same way */
    data class Subject(val semesterCode: String, val subjectCode: String) : Route {
        override val key = "subject/$semesterCode/$subjectCode"
    }
    data class GradeCard(val code: String) : Route { override val key = "gradecard/$code" }
    data class Marks(val code: String) : Route { override val key = "marks/$code" }
    data object Profile : Route { override val key = "profile" }
    data object Fees : Route { override val key = "fees" }
    data object Bank : Route { override val key = "bank" }
    data object Hostel : Route { override val key = "hostel" }
    data object Feedback : Route { override val key = "feedback" }
    data object Settings : Route { override val key = "settings" }
    data object About : Route { override val key = "about" }

    companion object {
        val tabs: List<Tab> = listOf(Attendance, Exams, Academics, Me)
    }
}

/** a plain back stack. no navigation library, the whole thing is this list */
@Stable
class Navigator(start: Route.Tab = Route.Attendance) {
    val stack: SnapshotStateList<Route> = mutableStateListOf(start)

    val current: Route get() = stack.last()
    val tab: Route.Tab get() = stack.first() as Route.Tab
    val canPop: Boolean get() = stack.size > 1 || tab != Route.Attendance

    fun push(route: Route) {
        if (current != route) stack.add(route)
    }

    /** back from a tab root goes home first, like every well behaved android app */
    fun pop(): Boolean = when {
        stack.size > 1 -> { stack.removeAt(stack.lastIndex); true }
        tab != Route.Attendance -> { stack[0] = Route.Attendance; true }
        else -> false
    }

    fun switchTab(tab: Route.Tab) {
        if (stack.size == 1 && stack[0] == tab) return
        stack.clear()
        stack.add(tab)
    }

    /** where pop() would land, drawn underneath during predictive back */
    val previous: Route?
        get() = when {
            stack.size > 1 -> stack[stack.lastIndex - 1]
            tab != Route.Attendance -> Route.Attendance
            else -> null
        }

    fun depthOf(route: Route): Int = stack.indexOf(route).let { if (it < 0) 0 else it }
}
