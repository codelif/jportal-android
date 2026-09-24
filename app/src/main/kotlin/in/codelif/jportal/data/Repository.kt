package `in`.codelif.jportal.data

import `in`.codelif.jportal.session.SessionManager
import `in`.codelif.ktjiit.api.Portal
import `in`.codelif.ktjiit.marks.MarksReport
import `in`.codelif.ktjiit.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable

/**
 * every portal answer the app shows, as a cached [Store]. keys carry the
 * registration id so semesters never bleed into each other.
 */
class Repository(
    private val cache: Cache,
    val sessions: SessionManager,
    private val scope: CoroutineScope,
    /** false while the demo student is in, whose stores only ever read the disk */
    private val live: () -> Boolean = { true },
) {
    private val stores = HashMap<String, Store<*>>()

    // the portal gets cranky with a dozen parallel calls, 6 fills a semester's class lists in two rounds
    private val limiter = Semaphore(6)

    @Suppress("UNCHECKED_CAST")
    private fun <T> store(key: String, serializer: KSerializer<T>, maxAgeMin: Long, fetch: suspend (Portal) -> T): Store<T> =
        stores.getOrPut(key) { Store(key, serializer, cache, sessions, scope, maxAgeMin * 60_000, limiter, live, fetch) } as Store<T>

    // attendance

    val attendanceMeta get() = store("att_meta", AttendanceMeta.serializer(), 12 * 60) { it.attendanceMeta() }

    fun attendance(sem: Semester, semesterNumber: String) =
        store("att:${sem.id}", AttendanceDetail.serializer(), 10) { it.attendance(sem, semesterNumber) }

    fun daily(sem: Semester, subject: SubjectAttendance) =
        store("daily:${sem.id}:${subject.subjectId}", DailyAttendance.serializer(), 10) { it.dailyAttendance(sem, subject) }

    fun dailyComponent(sem: Semester, subject: SubjectAttendance, component: String) =
        store("dailyc:${sem.id}:${subject.subjectId}:$component", DailyAttendance.serializer(), 30) {
            it.dailyAttendance(sem, subject, listOf(component))
        }

    // subjects

    val subjectSemesters get() = store("subj_sems", ListSerializer(Semester.serializer()), 12 * 60) { it.subjectSemesters() }

    fun subjects(sem: Semester) = store("subj:${sem.id}", RegisteredSubjects.serializer(), 6 * 60) { it.registeredSubjects(sem) }

    val credits get() = store("credits", Credits.serializer(), 6 * 60) { it.credits() }

    // exams

    val examSemesters get() = store("exam_sems", ListSerializer(Semester.serializer()), 6 * 60) { it.examSemesters() }

    fun examEvents(sem: Semester) = store("exam_ev:${sem.id}", ListSerializer(ExamEvent.serializer()), 60) { it.examEvents(sem) }

    fun examSchedule(ev: ExamEvent) = store("exam_sch:${ev.id}", ListSerializer(ExamSlot.serializer()), 60) { it.examSchedule(ev) }

    // marks and grades

    val marksSemesters get() = store("marks_sems", ListSerializer(Semester.serializer()), 6 * 60) { it.marksSemesters() }

    fun marks(sem: Semester) = store("marks:${sem.id}", MarksReport.serializer(), 30) { it.marks(sem) }

    val gradeSemesters get() = store("gc_sems", ListSerializer(Semester.serializer()), 12 * 60) { it.gradeCardSemesters() }

    val program get() = store("program", StudentProgram.serializer(), 24 * 60) { it.program() }

    fun gradeCard(sem: Semester) = store("gc:${sem.id}", GradeCard.serializer(), 12 * 60) { p -> p.gradeCard(sem, p.program()) }

    fun results(currentSemester: String) =
        store("results", ListSerializer(SemesterResult.serializer()), 12 * 60) { it.semesterResults(currentSemester) }

    // me

    val personal get() = store("personal", PersonalInfo.serializer(), 24 * 60) { it.personalInfo() }
    val bank get() = store("bank", BankInfo.serializer(), 24 * 60) { it.bankInfo() }
    val hostel get() = store("hostel", HostelInfo.serializer().nullable, 24 * 60) { it.hostel() }
    val fees get() = store("fees", FeeSummary.serializer(), 6 * 60) { it.fees() }
    val feedback get() = store("feedback", ListSerializer(FeedbackEvent.serializer()), 60) { it.feedbackEvents() }

    // registration, read only

    val choiceSemesters get() = store("choice_sems", ListSerializer(Semester.serializer()), 6 * 60) { it.choiceSemesters() }
    fun choices(sem: Semester) = store("choices:${sem.id}", ListSerializer(SubjectChoice.serializer()), 6 * 60) { it.subjectChoices(sem) }
    val moocSemesters get() = store("mooc_sems", ListSerializer(Semester.serializer()), 6 * 60) { it.moocSemesters() }
    fun mooc(sem: Semester) = store("mooc:${sem.id}", MoocStatus.serializer(), 6 * 60) { it.moocStatus(sem) }

    fun wipe() {
        stores.clear()
        cache.clear()
    }
}
