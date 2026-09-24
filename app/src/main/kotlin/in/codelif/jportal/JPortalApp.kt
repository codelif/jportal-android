package `in`.codelif.jportal

import android.app.Application
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import `in`.codelif.jportal.data.Cache
import `in`.codelif.jportal.demo.Demo
import `in`.codelif.jportal.data.Prefs
import `in`.codelif.jportal.data.Repository
import `in`.codelif.jportal.data.Updates
import `in`.codelif.jportal.session.SessionManager
import `in`.codelif.jportal.session.SessionStore
import `in`.codelif.ktjiit.http.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

/** the app's few singletons, built once and handed down through [LocalGraph] */
class AppGraph(context: Context, demo: Boolean = BuildConfig.DEMO) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val prefs = Prefs(context)
    val transport = Transport()
    val sessions = SessionManager(SessionStore(context), transport, if (demo) Demo.session else null)
    val cache = Cache(context)
    val repo = Repository(cache, sessions, scope) { !this.demo }
    val updates = Updates(context, scope)

    /** the made up student is signed in, nothing may touch the portal */
    val demo: Boolean get() = Demo.owns(sessions.session)

    /** signs the made up student in, from the sign-in screen's button */
    suspend fun startDemo() {
        withContext(Dispatchers.IO) { Demo.seed(cache) }
        sessions.signedIn(Demo.session)
    }

    fun signOut() {
        sessions.signOut()
        repo.wipe()
        prefs.clearAccountBits()
    }
}

class JPortalApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        val fresh = BuildConfig.DEMO && !getDatabasePath("cache.db").exists()
        graph = AppGraph(this)
        if (fresh) Demo.seed(graph.cache)
    }
}

val Context.graph: AppGraph get() = (applicationContext as JPortalApp).graph

val LocalGraph = staticCompositionLocalOf<AppGraph> { error("no app graph") }
