package `in`.codelif.jportal.data

import android.content.Context
import `in`.codelif.jportal.BuildConfig
import `in`.codelif.jportal.debug.DebugLog
import `in`.codelif.jportal.domain.Klass
import `in`.codelif.jportal.domain.Timetable
import `in`.codelif.ktjiit.http.PortalException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.CborArray
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.zip.GZIPInputStream

// planner.cbor.gz, format 1. see generate_cache.py in jiit-planner-cdn

@Serializable
private class Wire(val format: Int, val version: String, val strings: List<String>, val events: List<WireEvent>, val courses: List<WireCourse>)

/** text fields are indices into strings, -1 for none. times are minutes from midnight */
@OptIn(ExperimentalSerializationApi::class)
@CborArray
@Serializable
private class WireEvent(
    val day: Int,
    val start: Int,
    val end: Int,
    val subject: Int,
    val code: Int,
    val teacher: Int,
    val venue: Int,
    val type: Int,
    val category: Int,
    val elective: Int,
    val batches: List<Int>,
)

@Serializable
private class WireCourse(val id: String, val name: String, val semesters: List<WireSemester>)

@Serializable
private class WireSemester(val id: String, val name: String, val phases: List<WirePhase>)

@Serializable
private class WirePhase(val id: String, val name: String, val batches: List<WireBatch>)

@Serializable
private class WireBatch(val id: String, val name: String, val events: List<Int>)

@OptIn(ExperimentalSerializationApi::class)
private val cbor = Cbor { ignoreUnknownKeys = true }

class UnknownFormat(format: Int) : IOException("timetable format $format")

/** the gzipped cbor straight off the cdn */
@OptIn(ExperimentalSerializationApi::class)
fun decodeTimetable(gz: ByteArray): Timetable {
    val w = cbor.decodeFromByteArray(Wire.serializer(), GZIPInputStream(gz.inputStream()).use { it.readBytes() })
    if (w.format != 1) throw UnknownFormat(w.format)
    fun s(i: Int) = if (i < 0) "" else w.strings[i]
    fun t(m: Int) = LocalTime.of(m / 60 % 24, m % 60)
    val events = w.events.map { e ->
        Klass(
            DayOfWeek.of(e.day + 1), t(e.start), t(e.end), s(e.subject), s(e.code), s(e.teacher), s(e.venue), s(e.type),
            s(e.category).ifEmpty { null }, e.elective != 0, e.batches.map(::s),
        )
    }
    return Timetable(w.version, w.courses.map { c ->
        Timetable.Course(c.id, c.name, c.semesters.map { sem ->
            Timetable.Semester(sem.id, sem.name, sem.phases.map { p ->
                Timetable.Phase(p.id, p.name, p.batches.map { b -> Timetable.Batch(b.id, b.name, b.events.map(events::get)) })
            })
        })
    })
}

/**
 * the planner's timetable. it's the same file for every student, so it sits in
 * its own file instead of the cache and outlives sign out. paints from disk,
 * asks github at most once a day, and only downloads when the etag moved.
 * [stand] stands in for the network, the demo student's made up week.
 */
class TimetableSource(context: Context, private val scope: CoroutineScope, private val stand: () -> Timetable?) {
    private val file = File(context.filesDir, "planner.cbor.gz")
    private val sp = context.getSharedPreferences("planner", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<Resource<Timetable>>(Resource())
    val state: StateFlow<Resource<Timetable>> = _state.asStateFlow()

    // which one we painted, the demo's week or the file, so signing in or out of the demo repaints
    private var painted: Boolean? = null
    private var inflight: Job? = null

    private suspend fun loadDisk() {
        val fake = stand()
        if (painted == (fake != null)) return
        painted = fake != null
        if (fake != null) {
            _state.value = Resource(fake, AppClock.millis(), checked = true)
            return
        }
        val t = withContext(Dispatchers.IO) { runCatching { if (file.exists()) decodeTimetable(file.readBytes()) else null }.getOrNull() }
        _state.value = Resource(t, sp.getLong("checked", 0).takeIf { t != null && it > 0 }, checked = true)
    }

    fun refresh(force: Boolean = false): Job {
        inflight?.takeIf { it.isActive }?.let { return it }
        return scope.launch {
            loadDisk()
            if (painted == true) return@launch
            val have = state.value.data != null
            if (!force && have && System.currentTimeMillis() - sp.getLong("checked", 0) < DAY) return@launch
            _state.update { it.copy(refreshing = true) }
            try {
                val fresh = withContext(Dispatchers.IO) { fetch(have) }
                val now = System.currentTimeMillis()
                sp.edit().putLong("checked", now).apply()
                _state.update { Resource(fresh ?: it.data, now, refreshing = false, error = null, checked = true) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                DebugLog.record("planner", e)
                _state.update { it.copy(refreshing = false, error = e) }
            }
        }.also { inflight = it }
    }

    /** null when the etag still matches */
    private fun fetch(have: Boolean): Timetable? {
        val c = URL(SOURCE).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.setRequestProperty("User-Agent", "JPortal-Android/${BuildConfig.VERSION_NAME}")
            // an etag with no file behind it would get a 304 and nothing to draw
            if (have) sp.getString("etag", null)?.let { c.setRequestProperty("If-None-Match", it) }
            val code = try {
                c.responseCode
            } catch (e: IOException) {
                // so the notice says offline and shows the right icon
                throw PortalException.Network(e)
            }
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED) return null
            if (code != HttpURLConnection.HTTP_OK) throw IOException("github answered $code")
            val bytes = c.inputStream.use { it.readBytes() }
            // decoded before it's saved, a broken download never replaces a good file
            val t = decodeTimetable(bytes)
            val tmp = File(file.path + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) throw IOException("couldn't save the timetable")
            sp.edit().putString("etag", c.getHeaderField("ETag")).apply()
            return t
        } finally {
            c.disconnect()
        }
    }

    private companion object {
        const val SOURCE = "https://raw.githubusercontent.com/codelif/jiit-planner-cdn/main/planner.cbor.gz"
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
