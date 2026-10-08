package `in`.codelif.jportal.domain

import `in`.codelif.jportal.feature.attendance.titleCase
import `in`.codelif.ktjiit.model.ExamEvent
import `in`.codelif.ktjiit.model.ExamSlot
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class Paper(val event: ExamEvent, val slot: ExamSlot) {
    val key: String get() = "${event.id}|${slot.code}|${slot.date}"
    val day: LocalDate? get() = slot.day
    val start: LocalDateTime? get() = day?.let { d -> slot.start?.let(d::atTime) }
    val end: LocalDateTime? get() = day?.let { d -> slot.end?.let(d::atTime) }
    val duration: Duration? get() = start?.let { s -> end?.let { Duration.between(s, it) } }

    /** when it's surely over. no end time, three hours covers the longest sitting */
    val over: LocalDateTime? get() = end ?: start?.plusHours(3) ?: day?.plusDays(1)?.atStartOfDay()

    fun status(now: LocalDateTime): Status {
        val s = start
        val o = over ?: return Status.Upcoming
        return when {
            now >= o -> Status.Done
            s != null && now >= s -> Status.Live
            else -> Status.Upcoming
        }
    }

    enum class Status { Upcoming, Live, Done }
}

/** a day on the date sheet, [free] is the clear days since the exam day before it */
class ExamDay(val date: LocalDate?, val papers: List<Paper>, val free: Int)

/** the flat paper list turned into what the exams view draws, so the ui holds no date math */
class ExamPlan(
    val now: LocalDateTime,
    val next: Paper?,
    val days: List<ExamDay>,
    val done: List<Paper>,
    /** paper key to the paper it overlaps */
    val clashes: Map<String, Paper>,
    /** every exam day of the events still running, for the strip */
    val run: List<LocalDate>,
) {
    val today: LocalDate get() = now.toLocalDate()

    fun status(p: Paper) = p.status(now)

    companion object {
        fun of(papers: List<Paper>, now: LocalDateTime): ExamPlan {
            val (done, ahead) = papers.partition { it.status(now) == Paper.Status.Done }
            val upcoming = ahead.sortedWith(compareBy(nullsLast()) { it.start ?: it.day?.atStartOfDay() })
            val next = upcoming.firstOrNull()
            var prev: LocalDate? = null
            val days = upcoming.groupBy { it.day }.map { (date, list) ->
                val free = if (prev != null && date != null) (ChronoUnit.DAYS.between(prev, date) - 1).toInt().coerceAtLeast(0) else 0
                if (date != null) prev = date
                ExamDay(date, list, free)
            }
            val clashes = buildMap {
                papers.groupBy { it.day }.forEach { (day, list) ->
                    if (day == null) return@forEach
                    for (a in list) for (b in list) if (a !== b && overlaps(a, b)) putIfAbsent(a.key, b)
                }
            }
            // the strip spans the events still running, done days included so you see how far in you are
            val running = upcoming.map { it.event.id }.toSet()
            val runPapers = papers.filter { it.event.id in running && it.day != null }
            return ExamPlan(
                now = now,
                next = next,
                days = days,
                done = done.sortedWith(compareByDescending(nullsFirst()) { it.start ?: it.day?.atStartOfDay() }),
                clashes = clashes,
                run = runPapers.mapNotNull { it.day }.distinct().sorted(),
            )
        }

        /** both windows known: they overlap. otherwise only the same start counts, a guessed end would cry wolf */
        private fun overlaps(a: Paper, b: Paper): Boolean {
            val (s1, s2) = (a.start ?: return false) to (b.start ?: return false)
            val (e1, e2) = a.end to b.end
            return if (e1 != null && e2 != null) s1 < e2 && s2 < e1 else s1 == s2
        }
    }
}

fun minutes(m: Long): String = when {
    m < 60 -> "${m}m"
    m % 60 == 0L -> "${m / 60}h"
    else -> "${m / 60}h ${m % 60}m"
}

private val TEST = Regex("""\b(?:TEST|T)[-\s]?(\d+)""")
private val MARKS = Regex("""(\d+)\s*-?\s*MARKS""")

/** TEST-2-26ODD-20MARKS -> T2 */
val ExamEvent.short: String
    get() {
        val c = code.ifBlank { description }.uppercase()
        TEST.find(c)?.let { return "T${it.groupValues[1]}" }
        return when {
            "END" in c -> "End sem"
            "MID" in c -> "Mid sem"
            else -> code.ifBlank { description }.titleCase()
        }
    }

/** the paper's weight when the event code carries it, TEST-2-26ODD-20MARKS -> 20 */
val ExamEvent.outOf: Int? get() = MARKS.find(code.uppercase())?.groupValues?.get(1)?.toIntOrNull()
