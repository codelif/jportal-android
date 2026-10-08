package `in`.codelif.jportal.domain

import `in`.codelif.jportal.feature.attendance.titleCase
import java.time.Duration
import java.time.LocalDateTime

/** a paper the way it lands in the calendar. no start time means an all day event */
data class ExamEntry(
    val title: String,
    val begin: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    val location: String,
    val description: String,
    /** minutes before [begin] */
    val reminders: List<Int>,
) {
    /** changes whenever anything the calendar shows does, so untouched papers skip the write */
    val hash: String get() = Integer.toHexString(toString().hashCode())
}

fun Paper.entry(): ExamEntry? {
    val day = day ?: return null
    val s = slot
    val start = start
    // 8 pm the evening before, to revise, and an hour before, to leave
    val eve = day.minusDays(1).atTime(20, 0)
    return ExamEntry(
        title = "${event.short} · ${s.subjectName.titleCase()}",
        begin = start ?: day.atStartOfDay(),
        end = if (start != null) over ?: start.plusHours(3) else day.plusDays(1).atStartOfDay(),
        allDay = start == null,
        location = listOfNotNull(s.room.takeIf { it.isNotBlank() }, s.seat.takeIf { it.isNotBlank() }?.let { "seat $it" }).joinToString(", "),
        description = listOfNotNull(s.code.takeIf { it.isNotBlank() }, "synced by JPortal").joinToString(" · "),
        reminders = if (start != null) listOf(Duration.between(eve, start).toMinutes().toInt(), 60) else listOf(Duration.between(eve, day.atStartOfDay()).toMinutes().toInt()),
    )
}

/** an event this app wrote, [over] in epoch ms */
data class SyncedEvent(val id: Long, val hash: String, val over: Long)

class SyncPlan(
    val add: List<Pair<Paper, ExamEntry>>,
    val change: List<Triple<Long, Paper, ExamEntry>>,
    /** paper keys whose events go */
    val drop: List<String>,
)

/**
 * what to write so the calendar matches the sheet. upcoming papers get added or
 * updated, a paper gone from the sheet loses its event. past events are left
 * alone, a new semester's sheet shouldn't wipe the last one's history.
 */
fun syncPlan(sheet: List<Paper>, now: LocalDateTime, nowMs: Long, synced: Map<String, SyncedEvent>): SyncPlan {
    val want = sheet.filter { it.status(now) != Paper.Status.Done }.mapNotNull { p -> p.entry()?.let { p to it } }
    val known = sheet.map { it.key }.toSet()
    return SyncPlan(
        add = want.filter { (p, _) -> p.key !in synced },
        change = want.mapNotNull { (p, e) -> synced[p.key]?.takeIf { it.hash != e.hash }?.let { Triple(it.id, p, e) } },
        drop = synced.filter { (k, s) -> k !in known && s.over > nowMs }.keys.toList(),
    )
}
