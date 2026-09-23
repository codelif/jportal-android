package `in`.codelif.jportal.session

import `in`.codelif.ktjiit.api.Portal
import `in`.codelif.ktjiit.auth.Auth
import `in`.codelif.ktjiit.auth.PortalConfig
import `in`.codelif.ktjiit.auth.Session
import `in`.codelif.ktjiit.http.PortalException
import `in`.codelif.ktjiit.http.Transport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration

sealed interface AuthState {
    data object SignedOut : AuthState
    data class SignedIn(val session: Session) : AuthState
}

/** thrown when only a visible sign in can fix things, the ui answers with the sheet */
class SignInRequired : Exception("sign in required")

/**
 * one place that knows whether we have a usable token. tokens live 2h and
 * refresh is unreliable, so an expired session asks the ui for a silent google
 * re-auth (hidden webview, auto_select) and waits for it.
 */
class SessionManager(private val store: SessionStore, val transport: Transport) {
    val auth = Auth(transport)

    private val _state = MutableStateFlow<AuthState>(store.load()?.let { AuthState.SignedIn(it) } ?: AuthState.SignedOut)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /** non-null while a silent re-auth is wanted, the ui hosts the hidden webview for it */
    private val _reauth = MutableStateFlow<CompletableDeferred<Session?>?>(null)
    val reauth: StateFlow<CompletableDeferred<Session?>?> = _reauth.asStateFlow()

    /** set when silent re-auth gave up: show the sign-in sheet, keep showing cached data */
    private val _needsSheet = MutableStateFlow(false)
    val needsSheet: StateFlow<Boolean> = _needsSheet.asStateFlow()

    @Volatile
    var config: PortalConfig? = null
        private set

    private val mutex = Mutex()

    val session: Session? get() = (state.value as? AuthState.SignedIn)?.session

    suspend fun loadConfig(): PortalConfig? = config ?: runCatching { auth.config() }.getOrNull()?.also { config = it }

    fun signedIn(session: Session) {
        store.save(session)
        _state.value = AuthState.SignedIn(session)
        _needsSheet.value = false
        _reauth.value?.complete(session)
        _reauth.value = null
    }

    fun reauthFailed() {
        _reauth.value?.complete(null)
        _reauth.value = null
    }

    fun signOut() {
        store.clear()
        _state.value = AuthState.SignedOut
        _needsSheet.value = false
        reauthFailed()
    }

    private fun fresh(s: Session): Boolean {
        val exp = s.expiresAt ?: return true
        return transport.clock.now().plus(MARGIN).isBefore(exp)
    }

    private suspend fun valid(forceRenew: Boolean): Session {
        val current = session ?: throw SignInRequired()
        if (!forceRenew && fresh(current)) return current
        return mutex.withLock {
            val now = session ?: throw SignInRequired()
            // another caller renewed while we waited on the lock
            if (now.token != current.token && fresh(now)) return@withLock now
            val waiter = CompletableDeferred<Session?>()
            _reauth.value = waiter
            val renewed = withTimeoutOrNull(REAUTH_TIMEOUT) { waiter.await() }
            if (renewed == null) {
                if (_reauth.value === waiter) _reauth.value = null
                _needsSheet.value = true
                throw SignInRequired()
            }
            renewed
        }
    }

    /** runs [block] with a live portal, renewing once if the server says the token died */
    suspend fun <T> call(block: suspend (Portal) -> T): T {
        val s = valid(forceRenew = false)
        return try {
            block(Portal(s, transport))
        } catch (e: PortalException.SessionExpired) {
            block(Portal(valid(forceRenew = true), transport))
        }
    }

    private companion object {
        val MARGIN: Duration = Duration.ofSeconds(90)
        const val REAUTH_TIMEOUT = 30_000L
    }
}
