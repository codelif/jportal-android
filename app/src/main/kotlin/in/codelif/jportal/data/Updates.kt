package `in`.codelif.jportal.data

import android.content.Context
import `in`.codelif.jportal.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

data class Release(val version: String, val url: String)

/** github's latest release, or null for drafts, prereleases and anything unreadable */
fun parseRelease(body: String): Release? = runCatching {
    val o = Json.parseToJsonElement(body).jsonObject
    if (o["draft"]?.jsonPrimitive?.boolean == true || o["prerelease"]?.jsonPrimitive?.boolean == true) return null
    Release(o.getValue("tag_name").jsonPrimitive.content.removePrefix("v"), o.getValue("html_url").jsonPrimitive.content)
}.getOrNull()

/** numeric x.y.z compare, anything after a dash ignored */
fun isNewer(candidate: String, installed: String): Boolean {
    fun parts(v: String) = v.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    val a = parts(candidate)
    val b = parts(installed)
    for (i in 0 until maxOf(a.size, b.size)) {
        val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
        if (d != 0) return d > 0
    }
    return false
}

/**
 * github builds only: at most one look at the releases page a day, and
 * never a word about it failing.
 */
class Updates(context: Context, private val scope: CoroutineScope) {
    private val sp = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val latest = MutableStateFlow(stored())

    /** only ever a release newer than what's installed, so updating clears it */
    val available: StateFlow<Release?> = latest.asStateFlow()

    private fun stored(): Release? {
        val v = sp.getString("version", null) ?: return null
        val url = sp.getString("url", null) ?: return null
        return Release(v, url).takeIf { isNewer(v, BuildConfig.VERSION_NAME) }
    }

    fun check() {
        if (!BuildConfig.UPDATE_CHECK) return
        val now = System.currentTimeMillis()
        if (now - sp.getLong("checked", 0) < DAY) return
        // debug builds can point at any repo with releases, to see the ui without shipping one
        val repo = (if (BuildConfig.DEBUG) sp.getString("debug_repo", null) else null) ?: REPO
        scope.launch(Dispatchers.IO) {
            // offline counts as not checked, any answer from github (404 before the first release too) counts
            val body = runCatching { fetch("https://api.github.com/repos/$repo/releases/latest") }.getOrElse { return@launch }
            val edit = sp.edit().putLong("checked", now)
            parseRelease(body)?.let { edit.putString("version", it.version).putString("url", it.url) }
            edit.apply()
            latest.value = stored()
        }
    }

    private fun fetch(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "JPortal-Android/${BuildConfig.VERSION_NAME}")
            if (c.responseCode != 200) return ""
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private companion object {
        const val REPO = "codelif/jportal-android"
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
