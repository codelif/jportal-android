package `in`.codelif.jportal.domain

import `in`.codelif.jportal.feature.attendance.titleCase
import `in`.codelif.ktjiit.model.ClassRecord
import `in`.codelif.ktjiit.model.ExamEvent
import `in`.codelif.ktjiit.model.ExamSlot
import `in`.codelif.ktjiit.model.SemesterResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

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

    private val t2 = ExamEvent("E2", "TEST-2-26ODD-20MARKS")
    private fun paper(date: String, from: String, to: String, code: String = from) =
        Paper(t2, ExamSlot(date = date, from = from, until = "$from to $to", subject = "PAPER $code", code = code))
    private fun at(s: String) = LocalDateTime.parse(s)

    @Test
    fun `a paper is live through its real window, then done`() {
        val p = paper("12/10/2026", "03:30 pm", "04:30 pm")
        assertEquals(Paper.Status.Upcoming, p.status(at("2026-10-12T15:29")))
        assertEquals(Paper.Status.Live, p.status(at("2026-10-12T15:30")))
        assertEquals(Paper.Status.Done, p.status(at("2026-10-12T16:30")))
        // no end time, three hours is the benefit of the doubt
        val open = Paper(t2, ExamSlot(date = "12/10/2026", from = "03:30 pm"))
        assertEquals(Paper.Status.Live, open.status(at("2026-10-12T18:29")))
    }

    @Test
    fun `plan groups days, counts free days and flags clashes`() {
        val a = paper("12/10/2026", "03:30 pm", "04:30 pm", "A")
        val b = paper("12/10/2026", "04:00 pm", "05:00 pm", "B")
        val c = paper("15/10/2026", "01:00 pm", "02:00 pm", "C")
        val gone = paper("01/10/2026", "09:00 am", "10:00 am", "D")
        val plan = ExamPlan.of(listOf(c, b, gone, a), at("2026-10-08T12:00"))
        assertEquals(a, plan.next)
        assertEquals(listOf(0, 2), plan.days.map { it.free })
        assertEquals(b, plan.clashes[a.key])
        assertNull(plan.clashes[c.key])
        assertEquals(listOf(gone), plan.done)
        assertEquals(listOf(1, 12, 15).map { LocalDate.of(2026, 10, it) }, plan.run)
    }

    @Test
    fun `a paper becomes a calendar event with exam shaped reminders`() {
        val p = Paper(t2, ExamSlot(date = "12/10/2026", from = "09:30 am", until = "09:30 am to 11:00 am", subject = "COMPILER DESIGN", code = "15B11CI712", room = "CR-4", seat = "B12"))
        val e = p.entry()!!
        assertEquals("T2 · Compiler Design", e.title)
        assertEquals(at("2026-10-12T09:30") to at("2026-10-12T11:00"), e.begin to e.end)
        assertEquals("CR-4, seat B12", e.location)
        assertEquals("15B11CI712 · synced by JPortal", e.description)
        // 8 pm the night before is 13.5 hours ahead of 9:30
        assertEquals(listOf(810, 60), e.reminders)
        val untimed = Paper(t2, ExamSlot(date = "12/10/2026", subject = "X")).entry()!!
        assertEquals(true, untimed.allDay)
        assertEquals(at("2026-10-13T00:00"), untimed.end)
        assertEquals(listOf(240), untimed.reminders)
        assertEquals("", Paper(t2, ExamSlot(date = "12/10/2026", from = "09:30 am")).entry()!!.location)
    }

    @Test
    fun `sync adds new papers, rewrites changed ones and drops vanished ones`() {
        val now = at("2026-10-08T12:00")
        val nowMs = 1_000L
        val same = paper("12/10/2026", "03:30 pm", "04:30 pm", "A")
        val seated = paper("13/10/2026", "03:30 pm", "04:30 pm", "B")
        val fresh = paper("14/10/2026", "03:30 pm", "04:30 pm", "C")
        val past = paper("01/10/2026", "09:00 am", "10:00 am", "D")
        val synced = mapOf(
            same.key to SyncedEvent(1, same.entry()!!.hash, 5_000),
            // synced before the room came out
            seated.key to SyncedEvent(2, "old", 5_000),
            past.key to SyncedEvent(3, past.entry()!!.hash, 500),
            "gone|X|20/10/2026" to SyncedEvent(4, "x", 5_000),
            // last semester's paper, long over: history, not ours to delete
            "old|Y|01/04/2026" to SyncedEvent(5, "y", 500),
        )
        val plan = syncPlan(listOf(same, seated, fresh, past), now, nowMs, synced)
        assertEquals(listOf(fresh.key), plan.add.map { it.first.key })
        assertEquals(listOf(2L), plan.change.map { it.first })
        assertEquals(listOf("gone|X|20/10/2026"), plan.drop)
    }

    @Test
    fun `event codes read like people say them`() {
        assertEquals("T2", t2.short)
        assertEquals(20, t2.outOf)
        assertEquals("T1", ExamEvent("E1", "TEST-1").short)
        assertEquals("End sem", ExamEvent("E3", "END TERM 2026").short)
        assertNull(ExamEvent("E1", "TEST-1").outOf)
    }
}
