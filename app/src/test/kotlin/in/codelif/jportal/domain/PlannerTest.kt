package `in`.codelif.jportal.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.MONDAY
import java.time.LocalTime

class PlannerTest {
    private fun k(start: String, end: String, subject: String = "S$start", day: DayOfWeek = MONDAY, code: String = "", category: String? = null) =
        Klass(day, LocalTime.parse(start), LocalTime.parse(end), subject, code, "T", "V", "L", category, category != null, emptyList())

    private fun tt(vararg courses: Pair<String, List<Pair<String, String>>>) = Timetable(
        "v",
        courses.map { (course, sems) ->
            Timetable.Course(course, course, sems.groupBy({ it.first }, { it.second }).map { (sem, ids) ->
                Timetable.Semester(sem, sem, listOf(Timetable.Phase("phase1", "1", ids.map { Timetable.Batch(it, it.uppercase(), emptyList()) })))
            })
        },
    )

    @Test
    fun `detect finds the one batch in the semester`() {
        val t = tt(
            "btech-128" to listOf("sem7" to "f7", "sem5" to "f7", "sem7" to "e1"),
            "btech-62" to listOf("sem7" to "a7"),
        )
        assertEquals(BatchPath("btech-128", "sem7", "phase1", "f7"), t.detect("F7", 7, "B.T"))
        assertEquals(BatchPath("btech-62", "sem7", "phase1", "a7"), t.detect(" a7 ", 7, ""))
        assertNull(t.detect("F7", 3, "B.T"))
        assertNull(t.detect("", 7, "B.T"))
        assertNull(t.detect("F7", 0, "B.T"))
    }

    @Test
    fun `program only breaks a tie, and a tie it can't break asks`() {
        val t = tt("btech-128" to listOf("sem1" to "b1"), "bca-62" to listOf("sem1" to "b1"))
        assertEquals("bca-62", t.detect("B1", 1, "BCA")?.course)
        assertEquals("btech-128", t.detect("B1", 1, "B.T")?.course)
        assertNull(tt("btech-128" to listOf("sem1" to "b1"), "btech-62" to listOf("sem1" to "b1")).detect("B1", 1, "B.T"))
    }

    @Test
    fun `electives show only when picked in their category`() {
        val core = k("09:00", "09:50")
        val graph = k("10:00", "10:50", "Graph Theory", category = "ELECTIVE")
        val mining = k("10:00", "10:50", "Data Mining", category = "ELECTIVE")
        val lab = k("11:00", "12:40", "Data Mining", category = "ELECTIVE LAB")
        val b = Timetable.Batch("f7", "F7", listOf(lab, mining, graph, core))
        assertEquals(mapOf("ELECTIVE" to listOf("Data Mining", "Graph Theory"), "ELECTIVE LAB" to listOf("Data Mining")), b.electives())
        // nothing picked is nothing shown
        assertEquals(listOf(core), b.week(emptyMap()))
        // a pick counts in its own category only
        assertEquals(listOf(core, mining), b.week(mapOf("ELECTIVE" to setOf("Data Mining"))))
    }

    @Test
    fun `prefill matches code first, then name`() {
        val b = Timetable.Batch(
            "f7", "F7",
            listOf(
                k("10:00", "10:50", "Graph Theory", code = "26B12CS313", category = "ELECTIVE"),
                k("11:00", "11:50", "Cloud Computing", code = "X", category = "DE-2"),
                k("12:00", "12:50", "Soft Computing", code = "Y", category = "DE-2"),
                k("09:00", "09:50", "Compilers", code = "26B11CI712"),
            ),
        )
        val picks = b.prefill(listOf("26b12cs313" to "GRAPH THEORY AND APPS", "OTHER" to "CLOUD COMPUTING", "26B11CI712" to "COMPILERS"))
        assertEquals(mapOf("ELECTIVE" to setOf("Graph Theory"), "DE-2" to setOf("Cloud Computing")), picks)
    }

    @Test
    fun `free rows count from the latest end, so overlaps make no gap`() {
        val a = k("09:00", "10:40")
        val b = k("09:00", "09:50")
        val c = k("11:00", "11:50")
        val d = k("13:00", "13:50")
        // 10:40 to 11:00 is under half an hour, 11:50 to 13:00 isn't
        val rows = DayPlan.of(listOf(a, b, c, d), null).rows
        assertEquals(listOf(DayPlan.Row.Class(a), DayPlan.Row.Class(b), DayPlan.Row.Class(c), DayPlan.Row.Free(70), DayPlan.Row.Class(d)), rows)
        assertEquals(DayPlan.Row.Free(30), DayPlan.of(listOf(b, k("10:20", "11:00")), null).rows[1])
    }

    @Test
    fun `now is whatever runs at that minute, today only`() {
        val a = k("09:00", "10:40")
        val b = k("09:00", "09:50")
        val c = k("11:00", "11:50")
        assertEquals(setOf(a, b), DayPlan.of(listOf(a, b, c), LocalTime.of(9, 30)).now)
        // ends are exclusive
        assertEquals(setOf(a), DayPlan.of(listOf(a, b, c), LocalTime.of(9, 50)).now)
        assertEquals(emptySet<Klass>(), DayPlan.of(listOf(a, b, c), LocalTime.of(10, 50)).now)
        assertEquals(emptySet<Klass>(), DayPlan.of(listOf(a, b, c), null).now)
    }

    @Test
    fun `batch keys round trip`() {
        val p = BatchPath("btech-128", "sem7", "phase1", "f7")
        assertEquals(p, BatchPath.parse(p.key))
        assertEquals("btech-128/sem7/phase1", p.phaseKey)
        assertNull(BatchPath.parse("btech-128/sem7"))
        assertNull(BatchPath.parse(null))
    }
}
