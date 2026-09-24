package `in`.codelif.jportal.demo

import `in`.codelif.jportal.data.AppClock
import `in`.codelif.jportal.data.Cache
import `in`.codelif.ktjiit.auth.Session
import `in`.codelif.ktjiit.http.Transport
import `in`.codelif.ktjiit.marks.EventScore
import `in`.codelif.ktjiit.marks.MarksReport
import `in`.codelif.ktjiit.marks.MarksStudent
import `in`.codelif.ktjiit.marks.Score
import `in`.codelif.ktjiit.marks.SubjectMarks
import `in`.codelif.ktjiit.model.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * a made up student for benchmarks and screenshot tests, never in a shipped
 * build. written straight into the cache under the keys the repository reads,
 * with dates counted from [AppClock] so pinned tests draw the same days.
 */
object Demo {
    val session = Session(
        token = "demo",
        username = "demo",
        instituteId = "demo",
        enrollmentNo = "99103042",
        name = "YASH MALIK",
    )

    private class Subj(
        val id: String,
        val code: String,
        val name: String,
        val parts: String,
        /** chance of being present, per mille */
        val odds: Int,
        val days: List<DayOfWeek>,
        val teachers: Map<Char, String>,
        val credits: Double,
        val audit: Boolean = false,
    )

    private val MON = DayOfWeek.MONDAY
    private val TUE = DayOfWeek.TUESDAY
    private val WED = DayOfWeek.WEDNESDAY
    private val THU = DayOfWeek.THURSDAY
    private val FRI = DayOfWeek.FRIDAY

    private val current = listOf(
        Subj("S1", "15B11CI712", "COMPILER DESIGN", "LT", 930, listOf(MON, WED, FRI), mapOf('L' to "DR. NEHA KAPOOR", 'T' to "RAHUL VERMA"), 4.0),
        Subj("S2", "15B11CI713", "MACHINE LEARNING AND DATA MINING", "LT", 840, listOf(MON, TUE, THU), mapOf('L' to "DR. ANKIT SAXENA", 'T' to "PRIYA NAIR"), 4.0),
        Subj("S3", "15B1NCI731", "CLOUD COMPUTING", "LT", 700, listOf(TUE, THU, FRI), mapOf('L' to "DR. KAVITA RAO", 'T' to "KAVITA RAO"), 4.0),
        Subj("S4", "15B17CI771", "MACHINE LEARNING LAB", "P", 1000, listOf(WED), mapOf('P' to "SUMIT BHATIA"), 1.0),
        Subj("S5", "15B17CI772", "CLOUD COMPUTING LAB", "P", 760, listOf(FRI), mapOf('P' to "PRIYA NAIR"), 1.0),
        Subj("S6", "16B1NHS732", "MARKETING MANAGEMENT", "L", 880, listOf(MON, THU), mapOf('L' to "DR. ISHA MALHOTRA"), 3.0),
        Subj("S7", "15B11HS711", "ENVIRONMENTAL STUDIES", "L", 0, emptyList(), mapOf('L' to "DR. VIVEK SINGH"), 0.0, audit = true),
    )

    // newest first, like the portal
    private val past = listOf(
        listOf(
            "15B11CI611" to "SOFTWARE ENGINEERING", "15B11CI612" to "DISTRIBUTED SYSTEMS", "15B11CI613" to "INFORMATION SECURITY",
            "15B17CI671" to "SOFTWARE ENGINEERING LAB", "15B1NCI632" to "NATURAL LANGUAGE PROCESSING", "16B1NHS631" to "PRINCIPLES OF MANAGEMENT",
        ),
        listOf(
            "15B11CI412" to "OPERATING SYSTEMS AND SYSTEMS PROGRAMMING", "15B11CI515" to "COMPUTER NETWORKS AND INTERNET OF THINGS", "15B11CI514" to "ARTIFICIAL INTELLIGENCE",
            "15B17CI574" to "INFORMATION SECURITY LAB", "15B17CI575" to "COMPUTER NETWORKS LAB", "16B1NHS434" to "ECONOMICS OF DIGITAL PLATFORMS",
        ),
        listOf(
            "15B11CI411" to "ALGORITHMS AND PROBLEM SOLVING", "15B11CI413" to "THEORY OF COMPUTATION", "15B11MA411" to "DISCRETE MATHEMATICS",
            "15B17CI471" to "ALGORITHMS LAB", "15B11CI414" to "COMPUTER ORGANISATION AND ARCHITECTURE", "15B11HS311" to "LIFE SKILLS",
        ),
        listOf(
            "15B11CI311" to "DATA STRUCTURES", "15B11CI312" to "DATABASE SYSTEMS AND WEB", "15B11MA301" to "PROBABILITY AND RANDOM PROCESSES",
            "15B17CI371" to "DATA STRUCTURES LAB", "15B11EC311" to "DIGITAL SYSTEMS", "15B11HS211" to "ECONOMICS",
        ),
        listOf(
            "15B11PH211" to "PHYSICS II", "15B11MA211" to "MATHEMATICS II", "15B11EC211" to "ELECTRICAL SCIENCE II",
            "15B17PH271" to "PHYSICS LAB II", "15B11CI211" to "SOFTWARE DEVELOPMENT FUNDAMENTALS II", "15B11GE211" to "ENGINEERING DRAWING",
        ),
        listOf(
            "15B11PH111" to "PHYSICS I", "15B11MA111" to "MATHEMATICS I", "15B11EC111" to "ELECTRICAL SCIENCE I",
            "15B17PH171" to "PHYSICS LAB I", "15B11CI111" to "SOFTWARE DEVELOPMENT FUNDAMENTALS I", "15B11HS111" to "ENGLISH",
        ),
    )
    private val grades = listOf(
        listOf("A+", "A", "A", "A+", "B+", "A"),
        listOf("A", "B+", "A", "A+", "A", "A+"),
        listOf("A", "A+", "B+", "A", "B", "A+"),
        listOf("A+", "B+", "A", "A+", "B", "A"),
        listOf("B+", "A", "B", "A", "A+", "B+"),
        listOf("A", "B+", "C+", "A+", "A", "B"),
    )
    private val points = mapOf("A+" to 10.0, "A" to 9.0, "B+" to 8.0, "B" to 7.0, "C+" to 6.0)

    // current first, the portal's order
    private val sems = listOf(
        Semester("D26O", "2026ODDSEM"), Semester("D26E", "2026EVESEM"), Semester("D25O", "2025ODDSEM"),
        Semester("D25E", "2025EVESEM"), Semester("D24O", "2024ODDSEM"), Semester("D24E", "2024EVESEM"),
        Semester("D23O", "2023ODDSEM"),
    )

    fun seed(cache: Cache) {
        val at = AppClock.millis() - 5 * 60_000
        fun <T> put(key: String, s: KSerializer<T>, v: T) = cache.put(key, Transport.json.encodeToString(s, v), at)
        val today = AppClock.today()
        val now = sems[0]
        val semList = ListSerializer(Semester.serializer())

        put(
            "att_meta", AttendanceMeta.serializer(),
            AttendanceMeta(listOf(AttendanceHeader("Computer Science and Engineering", "B.Tech", session.name, "7")), sems),
        )
        put("att:${now.id}", AttendanceDetail.serializer(), AttendanceDetail(current.map(::attendanceRow)))
        current.forEachIndexed { i, s ->
            put("daily:${now.id}:${s.id}", DailyAttendance.serializer(), DailyAttendance(classes(s, today, i)))
        }

        put("subj_sems", semList, sems)
        put("subj:${now.id}", RegisteredSubjects.serializer(), RegisteredSubjects(current.flatMap(::registration)))
        past.forEachIndexed { i, list ->
            val sem = sems[i + 1]
            put("subj:${sem.id}", RegisteredSubjects.serializer(), RegisteredSubjects(list.mapIndexed { j, (code, name) ->
                SubjectFaculty("P$i$j", code, name, if (name.endsWith("LAB") || name.contains("LAB ")) "P" else "L", PEOPLE[(i + j) % PEOPLE.size], credits = credits(name))
            }))
        }
        put("program", StudentProgram.serializer(), StudentProgram(program = "B.Tech", branch = "Computer Science and Engineering", maxSemesters = 8))

        // results for semesters 1 to 6, the past ones oldest first
        val graded = past.indices.reversed().map { i -> sems[i + 1] to past[i].zip(grades[i]) }
        put("gc_sems", semList, graded.map { it.first }.reversed())
        var gp = 0.0
        var cr = 0.0
        val results = graded.mapIndexed { n, (sem, subjects) ->
            val entries = subjects.map { (s, g) ->
                GradeEntry(code = s.first, name = s.second, grade = g, gradePoint = points.getValue(g), credits = credits(s.second), earned = credits(s.second), semester = n + 1)
            }
            put("gc:${sem.id}", GradeCard.serializer(), GradeCard(entries))
            val c = entries.sumOf { it.credits }
            val p = entries.sumOf { it.gradePoint * it.credits }
            gp += p
            cr += c
            SemesterResult(n + 1, sgpa = round1(p / c), cgpa = round2(gp / cr), courseCredits = c, earnedCredits = c, cumulativeCredits = cr)
        }
        put("results", ListSerializer(SemesterResult.serializer()), results)
        put("credits", Credits.serializer(), Credits(registeredCredits = current.sumOf { it.credits }.toInt(), required = 160, earned = cr.toInt()))

        // exams: t1 is done, t2 starts in two days
        val t1 = ExamEvent("E1", "T1", "T1 examination", semesterId = now.id)
        val t2 = ExamEvent("E2", "T2", "T2 examination", semesterId = now.id)
        put("exam_sems", semList, sems.take(2))
        put("exam_ev:${now.id}", ListSerializer(ExamEvent.serializer()), listOf(t2, t1))
        val theory = current.filter { 'L' in it.parts && !it.audit }
        put("exam_sch:${t1.id}", ListSerializer(ExamSlot.serializer()), theory.mapIndexed { i, s -> slot(s, today.minusDays(40L - i), if (i % 2 == 0) "09:00" to "10:00" else "14:00" to "15:00", i) })
        put("exam_sch:${t2.id}", ListSerializer(ExamSlot.serializer()), theory.mapIndexed { i, s -> slot(s, today.plusDays(2L + i / 2), if (i % 2 == 0) "09:30" to "11:00" else "14:30" to "16:00", i) })

        put("marks_sems", semList, sems.take(2))
        put("marks:${now.id}", MarksReport.serializer(), marks(theory))

        put("personal", PersonalInfo.serializer(), PersonalInfo(
            GeneralInfo(
                name = session.name, enrollmentNo = session.enrollmentNo, program = "B.Tech", branch = "CSE", batch = "F7", section = "F",
                semester = 7, admissionYear = "2023", dateOfBirth = "14-02-2005", gender = "Male", bloodGroup = "B+", nationality = "Indian",
                category = "General", collegeEmail = "99103042@mail.jiit.ac.in", personalEmail = "yash.malik@example.com", phone = "90000 12345",
                fatherName = "RAJESH MALIK", motherName = "SUNITA MALIK", currentCity = "Noida", currentState = "Uttar Pradesh",
            ),
            listOf(Qualification("XII", "CBSE", "2023", percent = 92.4), Qualification("X", "CBSE", "2021", percent = 95.2)),
        ))
        put("bank", BankInfo.serializer(), BankInfo("STATE BANK OF INDIA", "30211456789", "SBIN0001234", "YASH MALIK", "SECTOR 62", "NOIDA", "UTTAR PRADESH", "201309", frozen = "Y"))
        put("hostel", HostelInfo.serializer().nullable, HostelInfo("GIRIRAJ BHAWAN", "H3", "B-214", "2", "Bed 1", "TWO SEATER", "BOYS HOSTEL", "15-07-2026", "31-12-2026"))
        put("fees", FeeSummary.serializer(), FeeSummary(listOf(
            FeeHead(7, type = "Regular", fee = 198000.0, paid = 148000.0, due = 50000.0),
            FeeHead(6, type = "Regular", fee = 190000.0, paid = 190000.0),
            FeeHead(5, type = "Regular", fee = 190000.0, paid = 180000.0, waived = 10000.0),
        )))
        put("feedback", ListSerializer(FeedbackEvent.serializer()), emptyList())
    }

    private val PEOPLE = listOf("DR. MEERA IYER", "ARJUN KHANNA", "DR. SNEHA GUPTA", "VIKRAM SETHI", "DR. POOJA DAS", "NIKHIL JAIN")

    private val HSS = setOf("ECONOMICS", "ENGLISH", "LIFE SKILLS", "ECONOMICS OF DIGITAL PLATFORMS", "PRINCIPLES OF MANAGEMENT")

    private fun credits(name: String) = if (name.contains("LAB")) 1.0 else if (name in HSS) 3.0 else 4.0

    private fun round1(v: Double) = Math.round(v * 10) / 10.0
    private fun round2(v: Double) = Math.round(v * 100) / 100.0

    private fun title(s: Subj) = "${s.name}(${s.code})"

    private fun attendanceRow(s: Subj): SubjectAttendance {
        // the portal's own percentages lag a day behind and round, close enough to the class list
        val pct = s.odds / 10.0
        return SubjectAttendance(
            subjectId = s.id, code = s.code, title = title(s),
            combinedPercent = if ('L' in s.parts) pct else null,
            lecturePercent = if ('L' in s.parts) pct else null,
            tutorialPercent = if ('T' in s.parts) minOf(100.0, pct + 4) else null,
            practicalPercent = if ('P' in s.parts) pct else null,
            lectureComponent = if ('L' in s.parts) "${s.id}L" else null,
            tutorialComponent = if ('T' in s.parts) "${s.id}T" else null,
            practicalComponent = if ('P' in s.parts) "${s.id}P" else null,
        )
    }

    private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /** nine weeks of classes up to yesterday, on the subject's weekdays */
    private fun classes(s: Subj, today: LocalDate, seed: Int): List<ClassRecord> {
        if (s.days.isEmpty()) return emptyList()
        val rnd = Random(seed * 31 + 7)
        val slots = listOf("09:00:AM - 09:50 AM", "10:00:AM - 10:50 AM", "11:00:AM - 11:50 AM", "02:00:PM - 02:50 PM", "03:00:PM - 04:50 PM")
        val start = today.minusWeeks(9)
        return generateSequence(start) { it.plusDays(1) }.takeWhile { it < today }
            .filter { it.dayOfWeek in s.days }
            .mapIndexed { i, d ->
                val lab = s.parts == "P"
                val kind = if (!lab && i % 4 == 3 && 'T' in s.parts) 'T' else s.parts.first()
                ClassRecord(
                    datetime = "${d.format(DATE)} (${slots[if (lab) 4 else (seed + i) % 4]})",
                    present = if (rnd.nextInt(1000) < s.odds) "Present" else "Absent",
                    takenBy = s.teachers[kind] ?: s.teachers.values.first(),
                    classType = "Regular",
                )
            }.toList()
    }

    private fun registration(s: Subj): List<SubjectFaculty> = s.parts.map { c ->
        SubjectFaculty(s.id, s.code, s.name, c.toString(), s.teachers[c].orEmpty(), credits = s.credits, audit = if (s.audit) "Y" else "N")
    }

    private fun slot(s: Subj, day: LocalDate, time: Pair<String, String>, i: Int) = ExamSlot(
        date = day.format(DATE), from = time.first, until = time.second,
        subject = title(s), code = s.code, room = listOf("CR-4", "LT-2", "G-7", "CR-11")[i % 4], seat = "B${12 + i * 3}",
    )

    private fun marks(theory: List<Subj>): MarksReport {
        val t1 = listOf(17.5 to 20.0, 13.0 to 20.0, 9.0 to 20.0, 15.0 to 20.0, 18.0 to 20.0)
        return MarksReport(
            MarksStudent(session.name, session.enrollmentNo, "B.Tech", "CSE", "7", "2026ODDSEM"),
            listOf("T1", "T2", "T3"),
            theory.mapIndexed { i, s ->
                val (got, max) = t1[i % t1.size]
                SubjectMarks(
                    s.code, s.name,
                    listOf(
                        EventScore("T1", Score.Value(got, max), Score.Value(got * 0.75, 15.0)),
                        EventScore("T2", Score.NotApplicable, Score.NotApplicable),
                        EventScore("T3", Score.NotApplicable, Score.NotApplicable),
                    ),
                )
            },
        )
    }
}
