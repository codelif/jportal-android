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
    /** the rest of the next paper's day, after it */
    val then: List<Paper>,
    val days: List<ExamDay>,
    val done: List<Paper>,
    /** paper key to the paper it overlaps */
    val clashes: Map<String, Paper>,
    /** every exam day of the events still running, for the strip */
    val run: List<LocalDate>,
    val runDone: Int,
    val runTotal: Int,
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
                then = next?.let { n -> upcoming.filter { it !== n && it.day == n.day && n.day != null } }.orEmpty(),
                days = days,
                done = done.sortedWith(compareByDescending(nullsFirst()) { it.start ?: it.day?.atStartOfDay() }),
                clashes = clashes,
                run = runPapers.mapNotNull { it.day }.distinct().sorted(),
                runDone = runPapers.count { it.status(now) == Paper.Status.Done },
                runTotal = runPapers.size,
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

data class Countdown(val big: String, val small: String)

fun countdown(p: Paper, now: LocalDateTime, time: (LocalDateTime) -> String): Countdown {
    val start = p.start
    val day = p.day ?: return Countdown("Soon", "date not out yet")
    val days = ChronoUnit.DAYS.between(now.toLocalDate(), day)
    if (p.status(now) == Paper.Status.Live) {
        val left = p.end?.let { Duration.between(now, it).toMinutes() }
        return Countdown("Now", left?.let { "${minutes(it)} left" } ?: "good luck")
    }
    return when {
        days >= 2 -> Countdown("$days", "days to go")
        days == 1L -> Countdown("Tomorrow", start?.let(time) ?: "time not out yet")
        start == null -> Countdown("Today", "time not out yet")
        else -> {
            val m = Duration.between(now, start).toMinutes()
            if (m >= 60) Countdown("${m / 60}h ${m % 60}m", "to go") else Countdown("${m}m", "to go")
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
