package `in`.codelif.jportal.feature.schedule

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import `in`.codelif.ktjiit.auth.Session
import `in`.codelif.ktjiit.model.GeneralInfo
import `in`.codelif.ktjiit.model.PersonalInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScheduleLoaderTest {

    private val sampleJson = """
        {
          "btech-128_sem3_phase1_f7": {
            "classes": {
              "Monday": [
                {
                  "day": "Monday",
                  "subject": "Unix Programming Lab",
                  "subject_code": "15B17CI371",
                  "start": "09:00 AM",
                  "end": "10:50 AM",
                  "venue": "CL01",
                  "type": "Practical"
                }
              ]
            }
          },
          "btech-62_sem1_phase1_b3": {
            "classes": {
              "Monday": [
                {
                  "day": "Monday",
                  "subject": "Physics",
                  "subject_code": "15B11PH111",
                  "start": "09:00 AM",
                  "end": "09:50 AM",
                  "venue": "LT1",
                  "type": "Lecture"
                }
              ]
            }
          }
        }
    """.trimIndent()

    @Before
    fun setUp() {
        ScheduleLoader.testRoot = Json.parseToJsonElement(sampleJson).jsonObject
    }

    @After
    fun tearDown() {
        ScheduleLoader.testRoot = null
    }

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

    @Test
    fun testClassItemSanitizationFixes8AmEndingAt850Pm() {
        val bugged = ClassItem(
            subject = "Operating Systems",
            start = "08:00 AM",
            end = "08:50 PM",
        )
        val fixed = bugged.sanitized()
        assertEquals("08:50 AM", fixed.end)
        assertEquals("08:00 AM", fixed.start)

        val regular = ClassItem(
            subject = "Physics",
            start = "09:00 AM",
            end = "09:50 AM",
        )
        val regularFixed = regular.sanitized()
        assertEquals("09:50 AM", regularFixed.end)
    }

    @Test
    fun testDetectCourseForBcaAndBtech() {
        val bcaPersonal = PersonalInfo(general = GeneralInfo(program = "BCA", branch = "BCA"))
        val btechPersonal = PersonalInfo(general = GeneralInfo(program = "B.Tech", branch = "CSE"))

        assertEquals("bca-62", ScheduleLoader.detectCourse(null, bcaPersonal, "62"))
        assertEquals("btech-128", ScheduleLoader.detectCourse(null, btechPersonal, "128"))
        assertEquals("btech-62", ScheduleLoader.detectCourse(null, btechPersonal, "62"))
    }

    @Test
    fun testComputeDateForDay() {
        // Assume reference date is Monday 2026-10-19
        val refDate = java.time.LocalDate.of(2026, 10, 19)
        assertEquals(java.time.LocalDate.of(2026, 10, 19), computeDateForDay("Monday", refDate))
        assertEquals(java.time.LocalDate.of(2026, 10, 20), computeDateForDay("Tuesday", refDate))
        assertEquals(java.time.LocalDate.of(2026, 10, 21), computeDateForDay("Wednesday", refDate))
        assertEquals(java.time.LocalDate.of(2026, 10, 22), computeDateForDay("Thursday", refDate))
        assertEquals(java.time.LocalDate.of(2026, 10, 23), computeDateForDay("Friday", refDate))
        assertEquals(java.time.LocalDate.of(2026, 10, 24), computeDateForDay("Saturday", refDate))
        assertEquals(java.time.LocalDate.of(2026, 10, 25), computeDateForDay("Sunday", refDate))
    }

    @Test
    fun testBatchExamMatchingLogic() {
        // When user has no batch
        val nullUserBatch: String? = null
        val selectedBatch = "F7"
        val isMatchNullUser = nullUserBatch != null && selectedBatch.equals(nullUserBatch, ignoreCase = true)
        assertEquals(false, isMatchNullUser)

        // When user batch differs from selected batch
        val userBatch = "F7"
        val otherSelectedBatch = "F30"
        val isMatchDifferent = userBatch.equals(otherSelectedBatch, ignoreCase = true)
        assertEquals(false, isMatchDifferent)

        // When user batch matches selected batch
        val sameBatch = "f7"
        val isMatchSame = userBatch.equals(sameBatch, ignoreCase = true)
        assertEquals(true, isMatchSame)
    }

    @Test
    fun testFilterElectivesWithPortalMatch() {
        val electiveA = ClassItem(
            subject = "Machine Learning",
            subjectCode = "15B11CI511",
            start = "09:00 AM",
            end = "09:50 AM",
            isElective = true,
        )
        val electiveB = ClassItem(
            subject = "Cloud Computing",
            subjectCode = "15B11CI512",
            start = "09:00 AM",
            end = "09:50 AM",
            isElective = true,
        )
        val regularClass = ClassItem(
            subject = "Operating Systems",
            subjectCode = "15B11CI411",
            start = "10:00 AM",
            end = "10:50 AM",
            isElective = false,
        )

        val classes = listOf(electiveA, electiveB, regularClass)

        // Portal has Elective B
        val filtered = filterElectivesForDay(
            classes = classes,
            batch = "F7",
            day = "Monday",
            portalCodes = setOf("15B11CI512"),
            portalNames = emptySet(),
        )

        assertEquals(2, filtered.size)
        assertEquals(electiveB.subjectCode, filtered[0].subjectCode)
        assertEquals(regularClass.subjectCode, filtered[1].subjectCode)
    }

    @Test
    fun testFilterElectivesWithoutPortalMatchDefaultsToFirst() {
        val electiveA = ClassItem(
            subject = "Elective 1",
            subjectCode = "EL101",
            start = "11:00 AM",
            end = "11:50 AM",
            isElective = true,
        )
        val electiveB = ClassItem(
            subject = "Elective 2",
            subjectCode = "EL102",
            start = "11:00 AM",
            end = "11:50 AM",
            isElective = true,
        )

        val classes = listOf(electiveA, electiveB)

        // No portal match and no user choice in memory
        ElectiveMemoryStore.slotChoices.clear()
        ElectiveMemoryStore.chosenSubjects.clear()

        val filtered = filterElectivesForDay(
            classes = classes,
            batch = "F7",
            day = "Monday",
            portalCodes = emptySet(),
            portalNames = emptySet(),
        )

        assertEquals(1, filtered.size)
        assertEquals("EL101", filtered[0].subjectCode)
    }

    @Test
    fun testFilterElectivesWithMemoryStoreSelection() {
        val electiveA = ClassItem(
            subject = "Elective 1",
            subjectCode = "EL101",
            start = "11:00 AM",
            end = "11:50 AM",
            day = "Tuesday",
            isElective = true,
        )
        val electiveB = ClassItem(
            subject = "Elective 2",
            subjectCode = "EL102",
            start = "11:00 AM",
            end = "11:50 AM",
            day = "Tuesday",
            isElective = true,
        )

        val classes = listOf(electiveA, electiveB)

        ElectiveMemoryStore.slotChoices.clear()
        ElectiveMemoryStore.chosenSubjects.clear()

        // User chooses Elective 2
        ElectiveMemoryStore.setChoice(
            batch = "F7",
            day = "Tuesday",
            start = "11:00 AM",
            end = "11:50 AM",
            chosen = electiveB,
        )

        val filtered = filterElectivesForDay(
            classes = classes,
            batch = "F7",
            day = "Tuesday",
            portalCodes = emptySet(),
            portalNames = emptySet(),
        )

        assertEquals(1, filtered.size)
        assertEquals("EL102", filtered[0].subjectCode)
    }
}

