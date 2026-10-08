package `in`.codelif.jportal.data

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import `in`.codelif.jportal.debug.DebugLog
import `in`.codelif.jportal.domain.ExamEntry
import `in`.codelif.jportal.domain.Paper
import `in`.codelif.jportal.domain.SyncedEvent
import `in`.codelif.jportal.domain.syncPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneOffset

/** a calendar on the phone that takes new events */
data class PhoneCalendar(val id: Long, val name: String, val account: String, val color: Int)

/**
 * keeps the current semester's upcoming papers in a calendar the user picked.
 * runs whenever the exams data does, there's no background job.
 */
class CalendarSync(context: Context, private val prefs: Prefs, private val repo: Repository, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    private val resolver: ContentResolver get() = app.contentResolver
    // paper key -> "event id|hash|over ms"
    private val sp = app.getSharedPreferences("calendar", Context.MODE_PRIVATE)
    private val lock = Mutex()

    private val _synced = MutableStateFlow(read().keys)
    /** paper keys that have an event, for the sheet's "in your calendar" */
    val synced: StateFlow<Set<String>> = _synced.asStateFlow()

    fun hasAccess() = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        .all { app.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    suspend fun calendars(): List<PhoneCalendar> = withContext(Dispatchers.IO) {
        if (!hasAccess()) return@withContext emptyList()
        val cols = arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.CALENDAR_COLOR)
        val where = "${Calendars.CALENDAR_ACCESS_LEVEL} >= ${Calendars.CAL_ACCESS_CONTRIBUTOR} AND ${Calendars.VISIBLE} = 1"
        buildList {
            resolver.query(Calendars.CONTENT_URI, cols, where, null, "${Calendars.ACCOUNT_NAME}, ${Calendars.CALENDAR_DISPLAY_NAME}")?.use { c ->
                while (c.moveToNext()) add(PhoneCalendar(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getInt(3)))
            }
        }
    }

    /** turns sync on into [calendar], moving what's already synced if it was elsewhere */
    suspend fun enable(calendar: Long) {
        lock.withLock {
            if (prefs.calendarState.value != calendar) clear()
            prefs.setCalendar(calendar)
            prefs.setCalendarSync(true)
            prefs.setCalendarDenied(false)
        }
        sync()
    }

    /** off, and every event it wrote goes with it */
    suspend fun disable() = lock.withLock {
        prefs.setCalendarSync(false)
        clear()
    }

    /** sign out: the next account's sync starts from nothing */
    fun forget() {
        val was = prefs.calendarSyncState.value
        prefs.setCalendarSync(false)
        prefs.setCalendar(null)
        if (was || read().isNotEmpty()) scope.launch { lock.withLock { clear() } }
    }

    /** brings the calendar in line with the current semester's sheet. quiet no-op while off */
    suspend fun sync() {
        if (!prefs.calendarSyncState.value) return
        if (!hasAccess()) {
            // revoked from system settings, settings shows why it flipped off
            prefs.setCalendarSync(false)
            prefs.setCalendarDenied(true)
            return
        }
        val cal = prefs.calendarState.value ?: return
        val sheet = sheet() ?: return
        lock.withLock {
            if (!prefs.calendarSyncState.value) return
            withContext(Dispatchers.IO) {
                try {
                    write(cal, sheet)
                } catch (e: Exception) {
                    DebugLog.record("calendar", e)
                }
            }
        }
    }

    /** every paper of the current exam semester, or null while any part of it is missing */
    private suspend fun sheet(): List<Paper>? {
        val sem = repo.examSemesters.await().data?.firstOrNull() ?: return null
        val events = repo.examEvents(sem).await().data ?: return null
        return events.flatMap { ev -> repo.examSchedule(ev).await().data?.map { Paper(ev, it) } ?: return null }
    }

    private fun write(cal: Long, sheet: List<Paper>) {
        val zone = AppClock.clock.zone
        val synced = read()
        // gone from the calendar app or deleted there: the next write adds it back fresh
        val alive = alive(cal, synced.values.map { it.id })
        val live = synced.filterValues { it.id in alive }
        val now = AppClock.now()
        val plan = syncPlan(sheet, now, now.atZone(zone).toInstant().toEpochMilli(), live)
        val out = sp.edit()
        synced.keys.filter { it !in live }.forEach { out.remove(it) }
        plan.drop.forEach { k ->
            live[k]?.let { resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, it.id), null, null) }
            out.remove(k)
        }
        plan.add.forEach { (p, e) ->
            val id = resolver.insert(Events.CONTENT_URI, values(e).apply { put(Events.CALENDAR_ID, cal) })?.let(ContentUris::parseId) ?: return@forEach
            remind(id, e)
            out.putString(p.key, "$id|${e.hash}|${overMs(e)}")
        }
        plan.change.forEach { (id, p, e) ->
            resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, id), values(e), null, null)
            resolver.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID} = ?", arrayOf("$id"))
            remind(id, e)
            out.putString(p.key, "$id|${e.hash}|${overMs(e)}")
        }
        out.apply()
        _synced.value = read().keys
    }

    private suspend fun clear() = withContext(Dispatchers.IO) {
        val ids = read().values.map { it.id }
        if (ids.isNotEmpty() && hasAccess()) {
            try {
                ids.forEach { resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, it), null, null) }
            } catch (e: Exception) {
                DebugLog.record("calendar", e)
            }
        }
        sp.edit().clear().apply()
        _synced.value = emptySet()
    }

    private fun alive(cal: Long, ids: List<Long>): Set<Long> {
        if (ids.isEmpty()) return emptySet()
        val where = "${Events.CALENDAR_ID} = ? AND ${Events.DELETED} = 0 AND ${Events._ID} IN (${ids.joinToString(",")})"
        return buildSet {
            resolver.query(Events.CONTENT_URI, arrayOf(Events._ID), where, arrayOf("$cal"), null)?.use { c ->
                while (c.moveToNext()) add(c.getLong(0))
            }
        }
    }

    private fun values(e: ExamEntry) = ContentValues().apply {
        // all day events live in utc midnights, the provider rejects anything else
        val zone = if (e.allDay) ZoneOffset.UTC else AppClock.clock.zone
        put(Events.TITLE, e.title)
        put(Events.DTSTART, ms(e.begin, zone))
        put(Events.DTEND, ms(e.end, zone))
        put(Events.ALL_DAY, if (e.allDay) 1 else 0)
        put(Events.EVENT_TIMEZONE, zone.id)
        put(Events.EVENT_LOCATION, e.location)
        put(Events.DESCRIPTION, e.description)
        put(Events.HAS_ALARM, 1)
    }

    private fun remind(id: Long, e: ExamEntry) = e.reminders.forEach { m ->
        resolver.insert(Reminders.CONTENT_URI, ContentValues().apply {
            put(Reminders.EVENT_ID, id)
            put(Reminders.MINUTES, m)
            put(Reminders.METHOD, Reminders.METHOD_ALERT)
        })
    }

    private fun overMs(e: ExamEntry) = ms(e.end, AppClock.clock.zone)

    private fun ms(t: LocalDateTime, zone: java.time.ZoneId) = t.atZone(zone).toInstant().toEpochMilli()

    private fun read(): Map<String, SyncedEvent> = sp.all.mapNotNull { (k, v) ->
        val parts = (v as? String)?.split('|') ?: return@mapNotNull null
        val id = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        k to SyncedEvent(id, parts.getOrNull(1).orEmpty(), parts.getOrNull(2)?.toLongOrNull() ?: 0L)
    }.toMap()
}
