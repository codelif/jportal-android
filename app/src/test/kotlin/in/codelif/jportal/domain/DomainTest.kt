package `in`.codelif.jportal.domain

import `in`.codelif.jportal.feature.attendance.titleCase
import `in`.codelif.ktjiit.model.ClassRecord
import `in`.codelif.ktjiit.model.SemesterResult
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DomainTest {
    @Test
    fun `can miss and must attend match jportal`() {
        // 18/24 = 75%: exactly on the line
        assertEquals(0, Tally(18, 24).canMiss(75))
        assertEquals(0, Tally(18, 24).mustAttend(75))
        // 20/24: 20/(24+2) = 76.9, 20/27 = 74.07
        assertEquals(2, Tally(20, 24).canMiss(75))
        // 15/24 = 62.5: (15+n)/(24+n) >= .75 -> n = 12
        assertEquals(12, Tally(15, 24).mustAttend(75))
        assertEquals(0, Tally(0, 0).canMiss(75))
    }

    @Test
    fun `extra classes only count when attended`() {
        fun c(p: Boolean, type: String) = ClassRecord("01/09/2026 (09:00:AM - 09:50 AM)", if (p) "Present" else "Absent", classType = type)
        // 3 regular (2 present), extra present: 3 of 3, not 3 of 4
        assertEquals(Tally(3, 3), AttendanceMath.tally(listOf(c(true, "Regular"), c(true, "Regular"), c(false, "Regular"), c(true, "Extra"))))
        // a missed extra changes nothing
        assertEquals(Tally(2, 3), AttendanceMath.tally(listOf(c(true, "Regular"), c(true, "Regular"), c(false, "Regular"), c(false, "Extra"))))
        // attended can pass total, the verdict still holds
        assertEquals(2, Tally(4, 3).canMiss(75))
    }

    @Test
    fun `drift flags a count the portal would not agree with`() {
        fun rows(regular: Int, present: Int, extra: Int, extraPresent: Int) =
            List(regular) { ClassRecord("01/09/2026 (09:00:AM - 09:50 AM)", if (it < present) "Present" else "Absent", classType = "Regular") } +
                List(extra) { ClassRecord("02/09/2026 (09:00:AM - 09:50 AM)", if (it < extraPresent) "Present" else "Absent", classType = "Extra") }
        // 37/39 = 94.87, the portal sent 94.9
        assertEquals(null, AttendanceMath.drift(94.9, rows(39, 34, 3, 3)))
        // the old all-rows count would have read 88.1
        assertEquals(true, AttendanceMath.drift(88.1, rows(39, 34, 3, 3))!! > 0.1)
        assertEquals(null, AttendanceMath.drift(null, rows(39, 34, 3, 3)))
        assertEquals(null, AttendanceMath.drift(50.0, emptyList()))
    }

    @Test
    fun `calendar marks split days`() {
        fun c(d: String, t: String, p: Boolean) = ClassRecord("$d ($t)", if (p) "Present" else "Absent")
        val marks = AttendanceMath.calendar(
            listOf(
                c("01/09/2026", "09:00:AM - 09:50 AM", true),
                c("01/09/2026", "10:00:AM - 10:50 AM", false),
                c("02/09/2026", "09:00:AM - 09:50 AM", true),
                c("03/09/2026", "09:00:AM - 09:50 AM", false),
            ),
        )
        assertEquals(DayMark.Mixed, marks[LocalDate.of(2026, 9, 1)])
        assertEquals(DayMark.Present, marks[LocalDate.of(2026, 9, 2)])
        assertEquals(DayMark.Absent, marks[LocalDate.of(2026, 9, 3)])
    }

    @Test
    fun `projected cgpa is credit weighted`() {
        val real = listOf(
            SemesterResult(1, sgpa = 8.0, cgpa = 8.0, courseCredits = 20.0),
            SemesterResult(2, sgpa = 9.0, cgpa = 8.5, courseCredits = 20.0),
        )
        val pts = Gpa.points(real, mapOf(3 to 10.0), projectedCredits = 20.0, maxSemesters = 8)
        assertEquals(3, pts.size)
        // real semesters keep the portal's own cgpa
        assertEquals(8.5, pts[1].cgpa, 1e-9)
        // (160 + 180 + 200) / 60
        assertEquals(9.0, pts[2].cgpa, 1e-9)
        assertEquals(true, pts[2].projected)
    }

    @Test
    fun `title case keeps acronyms, not short words`() {
        assertEquals("Blockchain and Its Applications", "BLOCKCHAIN AND ITS APPLICATIONS".titleCase())
        assertEquals("Database Systems and Web", "DATABASE SYSTEMS AND WEB".titleCase())
        assertEquals("Fundamentals of IOT Analytics Lab", "FUNDAMENTALS OF IOT ANALYTICS LAB".titleCase())
        assertEquals("Digital Signal Processing (DSP)", "DIGITAL SIGNAL PROCESSING (DSP)".titleCase())
        assertEquals("An Introduction to DBMS", "AN INTRODUCTION TO DBMS".titleCase())
        assertEquals("Summer Training - II", "SUMMER TRAINING - II".titleCase())
        assertEquals("Indian Constitution & Traditional Knowledge", "INDIAN CONSTITUTION & TRADITIONAL KNOWLEDGE".titleCase())
        // the portal already cased it, leave it alone
        assertEquals("Artificial Intelligence and Machine Learning", "Artificial Intelligence and Machine Learning".titleCase())
    }

    @Test
    fun `sgpa needed for a target`() {
        val pts = listOf(GpaPoint(1, 8.0, 20.0, 8.0, false))
        // (8.5 * 40 - 160) / 20 = 9.0
        assertEquals(9.0, Gpa.needed(pts, 8.5, 1, 20.0)!!, 1e-9)
    }
}
