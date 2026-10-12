package `in`.codelif.jportal.feature.schedule

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import `in`.codelif.ktjiit.auth.Session
import `in`.codelif.ktjiit.model.GeneralInfo
import `in`.codelif.ktjiit.model.PersonalInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScheduleLoaderTest {

    @Test
    fun testCampusDetection() {
        val s128 = Session(token = "tok", username = "u", instituteId = "128", instituteName = "JIIT Sector 128")
        val s62 = Session(token = "tok", username = "u", instituteId = "62", instituteName = "JIIT Sector 62")
        val p128 = PersonalInfo(general = GeneralInfo(batch = "F7"))
        val p62 = PersonalInfo(general = GeneralInfo(batch = "B3"))

        assertEquals("128", ScheduleLoader.detectCampus(s128, null))
        assertEquals("62", ScheduleLoader.detectCampus(s62, null))
        assertEquals("128", ScheduleLoader.detectCampus(null, p128))
        assertEquals("62", ScheduleLoader.detectCampus(null, p62))
    }

    @Test
    fun testLoadScheduleFromAssetsForSector128() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val session = Session(token = "demo", username = "demo", instituteId = "demo", enrollmentNo = "99103042")
        val personal = PersonalInfo(general = GeneralInfo(batch = "F7", semester = 3))

        val schedule = ScheduleLoader.loadSchedule(context, session, personal)
        assertNotNull(schedule)
        assertEquals("128", schedule!!.campus)
        assertEquals("F7", schedule.batch)
        assertTrue(schedule.classesByDay.containsKey("Monday"))

        val mondayClasses = schedule.classesByDay["Monday"]!!
        assertTrue(mondayClasses.isNotEmpty())
        assertEquals("Unix Programming Lab", mondayClasses[0].subject)
        assertEquals("09:00 AM", mondayClasses[0].start)
    }

    @Test
    fun testLoadScheduleFromAssetsForSector62() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val session = Session(token = "tok", username = "u", instituteId = "62", instituteName = "JIIT Sector 62")
        val personal = PersonalInfo(general = GeneralInfo(batch = "B3", semester = 1))

        val schedule = ScheduleLoader.loadSchedule(context, session, personal)
        assertNotNull(schedule)
        assertEquals("62", schedule!!.campus)
        assertEquals("B3", schedule.batch)
        assertTrue(schedule.classesByDay.containsKey("Monday"))
    }

    @Test
    fun testClassItemProgressCalculation() {
        val item = ClassItem(
            subject = "OOP",
            start = "02:00 PM",
            end = "03:50 PM",
        )

        val start = LocalTime.of(14, 0)
        val mid = LocalTime.of(14, 55)
        val end = LocalTime.of(15, 50)
        val before = LocalTime.of(13, 30)
        val after = LocalTime.of(16, 0)

        assertTrue(item.isOngoingAt(mid))
        assertTrue(item.isUpcomingAt(before))
        assertTrue(item.isFinishedAt(after))

        assertEquals(0.5f, item.progressAt(mid), 0.01f)
        assertEquals(0.0f, item.progressAt(start), 0.01f)
        assertEquals(1.0f, item.progressAt(end), 0.01f)
        assertEquals(55L, item.minutesRemainingAt(mid))
        assertEquals(30L, item.minutesUntilAt(before))
    }

    @Test
    fun testNaturalSort() {
        val unsorted = listOf("B10", "B1", "B2", "A10", "A1", "A2", "F7", "F1", "F12")
        val sorted = ScheduleLoader.naturalSort(unsorted)
        assertEquals(listOf("A1", "A2", "A10", "B1", "B2", "B10", "F1", "F7", "F12"), sorted)
    }

    @Test
    fun testBuildTimelineStandardLunch() {
        val classes = listOf(
            ClassItem(subject = "Class 1", start = "12:00 PM", end = "12:50 PM"),
            ClassItem(subject = "Class 2", start = "02:00 PM", end = "02:50 PM"),
        )
        val timeline = buildTimeline(classes)
        assertEquals(3, timeline.size)
        assertTrue(timeline[0] is ScheduleTimelineItem.ClassSlot)
        assertTrue(timeline[1] is ScheduleTimelineItem.LunchBreakSlot)
        assertTrue(timeline[2] is ScheduleTimelineItem.ClassSlot)

        val lunch = timeline[1] as ScheduleTimelineItem.LunchBreakSlot
        assertEquals(LocalTime.of(12, 50), lunch.start)
        assertEquals(LocalTime.of(14, 0), lunch.end)
        assertEquals(70L, lunch.durationMinutes)
    }

    @Test
    fun testBuildTimelineExtendedLunchSplit() {
        // e.g. Friday: 12:50 PM -> 03:00 PM: 1h 10m lunch + 1h regular break
        val classes = listOf(
            ClassItem(subject = "Unix Lab", start = "11:00 AM", end = "12:50 PM"),
            ClassItem(subject = "Data Structures", start = "03:00 PM", end = "03:50 PM"),
        )
        val timeline = buildTimeline(classes)
        assertEquals(4, timeline.size)
        assertTrue(timeline[0] is ScheduleTimelineItem.ClassSlot)
        assertTrue(timeline[1] is ScheduleTimelineItem.LunchBreakSlot)
        assertTrue(timeline[2] is ScheduleTimelineItem.BreakSlot)
        assertTrue(timeline[3] is ScheduleTimelineItem.ClassSlot)

        val lunch = timeline[1] as ScheduleTimelineItem.LunchBreakSlot
        assertEquals(LocalTime.of(12, 50), lunch.start)
        assertEquals(LocalTime.of(14, 0), lunch.end)
        assertEquals(70L, lunch.durationMinutes)

        val regularBreak = timeline[2] as ScheduleTimelineItem.BreakSlot
        assertEquals(LocalTime.of(14, 0), regularBreak.start)
        assertEquals(LocalTime.of(15, 0), regularBreak.end)
        assertEquals(60L, regularBreak.durationMinutes)
    }

    @Test
    fun testBuildTimeline12to1LunchWhenFree() {
        val classes = listOf(
            ClassItem(subject = "Physics", start = "11:00 AM", end = "11:50 AM"),
            ClassItem(subject = "Math", start = "01:00 PM", end = "01:50 PM"),
        )
        val timeline = buildTimeline(classes)
        assertEquals(3, timeline.size)
        assertTrue(timeline[0] is ScheduleTimelineItem.ClassSlot)
        assertTrue(timeline[1] is ScheduleTimelineItem.LunchBreakSlot)
        assertTrue(timeline[2] is ScheduleTimelineItem.ClassSlot)

        val lunch = timeline[1] as ScheduleTimelineItem.LunchBreakSlot
        assertEquals(LocalTime.of(11, 50), lunch.start)
        assertEquals(LocalTime.of(13, 0), lunch.end)
        assertEquals(70L, lunch.durationMinutes)
    }

    @Test
    fun testBuildTimelineBothFreeTakes12to1LunchAndRestBreak() {
        val classes = listOf(
            ClassItem(subject = "Physics", start = "11:00 AM", end = "11:50 AM"),
            ClassItem(subject = "Math", start = "02:00 PM", end = "02:50 PM"),
        )
        val timeline = buildTimeline(classes)
        assertEquals(4, timeline.size)
        assertTrue(timeline[0] is ScheduleTimelineItem.ClassSlot)
        assertTrue(timeline[1] is ScheduleTimelineItem.LunchBreakSlot)
        assertTrue(timeline[2] is ScheduleTimelineItem.BreakSlot)
        assertTrue(timeline[3] is ScheduleTimelineItem.ClassSlot)

        val lunch = timeline[1] as ScheduleTimelineItem.LunchBreakSlot
        assertEquals(LocalTime.of(11, 50), lunch.start)
        assertEquals(LocalTime.of(13, 0), lunch.end)
        assertEquals(70L, lunch.durationMinutes)

        val restBreak = timeline[2] as ScheduleTimelineItem.BreakSlot
        assertEquals(LocalTime.of(13, 0), restBreak.start)
        assertEquals(LocalTime.of(14, 0), restBreak.end)
        assertEquals(60L, restBreak.durationMinutes)
    }

    @Test
    fun testBuildTimelineBothOccupiedNoLunchBreak() {
        val classes = listOf(
            ClassItem(subject = "Class 1", start = "12:00 PM", end = "12:50 PM"),
            ClassItem(subject = "Class 2", start = "01:00 PM", end = "01:50 PM"),
            ClassItem(subject = "Class 3", start = "03:00 PM", end = "03:50 PM"),
        )
        val timeline = buildTimeline(classes)
        // Class 1 -> (passing 10m) -> Class 2 -> (break 70m) -> Class 3
        assertEquals(4, timeline.size)
        assertTrue(timeline[0] is ScheduleTimelineItem.ClassSlot)
        assertTrue(timeline[1] is ScheduleTimelineItem.ClassSlot)
        assertTrue(timeline[2] is ScheduleTimelineItem.BreakSlot)
        assertTrue(timeline[3] is ScheduleTimelineItem.ClassSlot)

        val regularBreak = timeline[2] as ScheduleTimelineItem.BreakSlot
        assertEquals(LocalTime.of(13, 50), regularBreak.start)
        assertEquals(LocalTime.of(15, 0), regularBreak.end)
        assertEquals(70L, regularBreak.durationMinutes)
    }

    @Test
    fun testBuildTimelinePassingGapNoLine() {
        val classes = listOf(
            ClassItem(subject = "Class 1", start = "09:00 AM", end = "09:50 AM"),
            ClassItem(subject = "Class 2", start = "10:00 AM", end = "10:50 AM"),
        )
        val timeline = buildTimeline(classes)
        assertEquals(2, timeline.size)
        assertTrue(timeline[0] is ScheduleTimelineItem.ClassSlot)
        assertTrue(timeline[1] is ScheduleTimelineItem.ClassSlot)
    }
}
