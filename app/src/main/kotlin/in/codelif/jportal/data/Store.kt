package `in`.codelif.jportal.data

import `in`.codelif.jportal.debug.DebugLog
import `in`.codelif.jportal.session.SessionManager
import `in`.codelif.ktjiit.api.Portal
import `in`.codelif.ktjiit.http.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.KSerializer

/**
 * one cached portal answer. starts from disk, refreshes over the network,
 * never throws the old data away on failure. concurrent refreshes collapse
 * into one request.
 */
class Store<T>(
    private val key: String,
    private val serializer: KSerializer<T>,
    private val cache: Cache,
    private val sessions: SessionManager,
    private val scope: CoroutineScope,
    private val maxAgeMs: Long,
    private val limiter: Semaphore,
    private val fetch: suspend (Portal) -> T,
) {
    private val _state = MutableStateFlow<Resource<T>>(Resource())
    val state: StateFlow<Resource<T>> = _state.asStateFlow()

    private var loaded = false
    private var inflight: Job? = null

    private suspend fun loadDisk() {
        if (loaded) return
        loaded = true
        // a marks report or a semester of class lists is a lot of json, decoding it on main drops frames
        val (entry, value) = withContext(Dispatchers.IO) {
            val e = cache.get(key)
            e to e?.let { runCatching { Transport.json.decodeFromString(serializer, it.json) }.getOrNull() }
        }
        _state.update {
            if (it.data == null && value != null) it.copy(data = value, fetchedAt = entry?.fetchedAt, checked = true) else it.copy(checked = true)
        }
    }

    /** paint from disk, then hit the network if stale or [force] */
    fun refresh(force: Boolean = false): Job {
        inflight?.takeIf { it.isActive }?.let { return it }
        return scope.launch {
            loadDisk()
            val age = state.value.fetchedAt?.let { System.currentTimeMillis() - it }
            if (!force && age != null && age < maxAgeMs) return@launch
            _state.update { it.copy(refreshing = true) }
            try {
                val value = limiter.withPermit { sessions.call(fetch) }
                val now = System.currentTimeMillis()
                withContext(Dispatchers.IO) { cache.put(key, Transport.json.encodeToString(serializer, value), now) }
                _state.value = Resource(value, now, refreshing = false, error = null, checked = true)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                DebugLog.record(key.substringBefore(':'), e)
                _state.update { it.copy(refreshing = false, error = e) }
            }
        }.also { inflight = it }
    }

    suspend fun await(force: Boolean = false): Resource<T> {
        refresh(force).join()
        return state.value
    }
}
