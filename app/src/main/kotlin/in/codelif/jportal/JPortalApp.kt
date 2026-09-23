package `in`.codelif.jportal

import android.app.Application
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import `in`.codelif.jportal.data.Cache
import `in`.codelif.jportal.data.Prefs
import `in`.codelif.jportal.data.Repository
import `in`.codelif.jportal.session.SessionManager
import `in`.codelif.jportal.session.SessionStore
import `in`.codelif.ktjiit.http.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** the app's few singletons, built once and handed down through [LocalGraph] */
class AppGraph(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val prefs = Prefs(context)
    val transport = Transport()
    val sessions = SessionManager(SessionStore(context), transport)
    val repo = Repository(Cache(context), sessions, scope)

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
        graph = AppGraph(this)
    }
}

val Context.graph: AppGraph get() = (applicationContext as JPortalApp).graph

val LocalGraph = staticCompositionLocalOf<AppGraph> { error("no app graph") }
