package `in`.codelif.jportal.debug

import android.content.Context
import android.os.Build
import `in`.codelif.jportal.BuildConfig
import `in`.codelif.ktjiit.http.PortalException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * what goes into "copy debug report". errors only, no payloads, and anything
 * that looks like a token, id or enrollment number is masked before it's kept.
 */
object DebugLog {
    private const val MAX = 60
    private val lines = ArrayDeque<String>()
    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    private val JWT = Regex("""eyJ[\w-]+\.[\w-]+\.[\w-]+""")
    private val LONG_ID = Regex("""\b[A-Z0-9]{10,}\b""")
    private val DIGITS = Regex("""\b\d{6,}\b""")

    fun redact(s: String): String = s.replace(JWT, "<jwt>").replace(LONG_ID, "<id>").replace(DIGITS, "<n>")

    @Synchronized
    fun record(where: String, e: Throwable) {
        val kind = when (e) {
            is PortalException.PortalError -> "PortalError(${e.code}) ${e.errors.joinToString("; ")}"
            is PortalException.ServerUnavailable -> "ServerUnavailable(${e.code}, ${e.shape})"
            is PortalException -> e.javaClass.simpleName
            else -> "${e.javaClass.simpleName}: ${e.message}"
        }
        lines.addLast("${stamp.format(Date())} $where ${redact(kind)}")
        if (BuildConfig.DEBUG) android.util.Log.w("JPortal", lines.last())
        while (lines.size > MAX) lines.removeFirst()
    }

    @Synchronized
    fun report(context: Context, extra: Map<String, String>): String = buildString {
        appendLine("JPortal ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.FLAVOR}")
        appendLine("Android ${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
        runCatching {
            val wv = androidx.webkit.WebViewCompat.getCurrentWebViewPackage(context)
            appendLine("WebView ${wv?.packageName} ${wv?.versionName}")
        }
        extra.forEach { (k, v) -> appendLine("$k: ${redact(v)}") }
        appendLine()
        if (lines.isEmpty()) appendLine("no errors recorded") else lines.forEach { appendLine(it) }
    }
}
