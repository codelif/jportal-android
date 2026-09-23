package `in`.codelif.jportal.spike

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import `in`.codelif.ktjiit.api.Portal
import `in`.codelif.ktjiit.auth.Auth
import `in`.codelif.ktjiit.auth.PortalConfig
import `in`.codelif.ktjiit.auth.Session
import `in`.codelif.ktjiit.http.Transport
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * m1 spike. B: our own gsi page served at the portal origin, credential goes to
 * kotlin. A: the real portal with an xhr hook that sniffs the login response.
 * everything interesting also lands in logcat under "spike".
 */
class SpikeActivity : ComponentActivity() {
    private val portalOrigin = "https://webportal.jiit.ac.in:6011"
    private val signinPath = "/studentportal/jportal-signin"
    private val transport = Transport()
    private val auth = Auth(transport)
    private var config: PortalConfig? = null

    private lateinit var logView: TextView
    private lateinit var stage: FrameLayout
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; fitsSystemWindows = true }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun button(label: String, action: () -> Unit) = Button(this).apply { text = label; textSize = 11f; setOnClickListener { action() } }
        bar.addView(button("B") { loadB(silent = false) })
        bar.addView(button("B silent") { loadB(silent = true) })
        bar.addView(button("A portal") { loadA() })
        bar.addView(button("forget") { forget() })
        logView = TextView(this).apply { textSize = 10f; setTextColor(Color.DKGRAY); setTextIsSelectable(true) }
        val logScroll = ScrollView(this).apply { addView(logView) }
        stage = FrameLayout(this)
        root.addView(bar)
        root.addView(logScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(stage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 3f))
        setContentView(root)

        web = newWebView(isPopup = false)
        stage.addView(web)
        installBridge(web)
        report()

        File(filesDir, "session.json").takeIf { it.exists() }?.let { log("stored session from ${it.lastModified()}") }
        lifecycleScope.launch {
            config = runCatching { auth.config() }.onFailure { log("config failed: $it") }.getOrNull()
            log("config: google=${config?.googleEnabled} login=${config?.loginUrl}")
        }
    }

    private fun log(m: String) {
        Log.i("spike", m)
        runOnUiThread { logView.append(m + "\n") }
    }

    private fun report() {
        val pkg = WebViewCompat.getCurrentWebViewPackage(this)
        log("webview ${pkg?.packageName} ${pkg?.versionName}")
        log("features: ua_metadata=${WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)} " +
            "xrw_allowlist=${WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)} " +
            "msg_listener=${WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)} " +
            "doc_start=${WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)}")
        log("ua: ${web.settings.userAgentString}")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun newWebView(isPopup: Boolean): WebView = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        // google refuses embedded browsers it can spot: "; wv" and the Version/4.0 token give it away
        settings.userAgentString = settings.userAgentString.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+ "), "")
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            val meta = WebSettingsCompat.getUserAgentMetadata(settings)
            val brands = meta.brandVersionList.map { b ->
                if (b.brand.contains("WebView")) {
                    UserAgentMetadata.BrandVersion.Builder().setBrand("Google Chrome")
                        .setMajorVersion(b.majorVersion).setFullVersion(b.fullVersion).build()
                } else b
            }
            WebSettingsCompat.setUserAgentMetadata(settings, UserAgentMetadata.Builder(meta).setBrandVersionList(brands).build())
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webViewClient = client
        webChromeClient = chrome
        if (isPopup) setBackgroundColor(Color.WHITE)
    }

    private val client = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url
            if ("${url.scheme}://${url.authority}" == portalOrigin && url.path == signinPath) {
                log("serving our page for ${url.path}")
                return WebResourceResponse("text/html", "utf-8", assets.open("signin.html"))
            }
            return null
        }

        override fun onPageFinished(view: WebView, url: String) {
            log("loaded ${Uri.parse(url).host}${Uri.parse(url).path}")
        }
    }

    private val chrome = object : WebChromeClient() {
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            log("popup requested dialog=$isDialog gesture=$isUserGesture")
            val popup = newWebView(isPopup = true)
            stage.addView(popup, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            (resultMsg.obj as WebView.WebViewTransport).webView = popup
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView) {
            log("popup closed")
            stage.removeView(window)
            window.destroy()
        }

        override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
            Log.d("spike-console", "${message.message()} @${message.sourceId()}:${message.lineNumber()}")
            return true
        }
    }

    private fun installBridge(view: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            log("no web message listener, spike can't continue")
            return
        }
        WebViewCompat.addWebMessageListener(view, "jportal", setOf(portalOrigin)) { _, message, origin, _, _ ->
            log("message from $origin")
            val obj = runCatching { Transport.json.parseToJsonElement(message.data ?: "").jsonObject }.getOrNull() ?: return@addWebMessageListener
            fun str(k: String) = obj[k]?.jsonPrimitive?.contentOrNull.orEmpty()
            when (str("type")) {
                "log" -> log("page: ${str("m")}")
                "credential" -> exchange(str("credential"), str("by"))
                "login" -> sniffed(str("body"))
            }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(view, HOOK, setOf(portalOrigin))
        }
    }

    private fun loadB(silent: Boolean) {
        val cid = config?.googleClientId
        if (cid.isNullOrEmpty()) return log("no client id yet")
        web.loadUrl("$portalOrigin$signinPath?cid=${Uri.encode(cid)}&silent=${if (silent) 1 else 0}")
    }

    private fun loadA() = web.loadUrl("$portalOrigin/studentportal/")

    private fun forget() {
        CookieManager.getInstance().removeAllCookies(null)
        File(filesDir, "session.json").delete()
        web.clearCache(true)
        log("cookies, cache and stored session cleared")
    }

    private fun exchange(credential: String, by: String) {
        log("got google credential (${credential.length} chars, select_by=$by), exchanging")
        lifecycleScope.launch {
            runCatching { auth.exchangeGoogleToken(credential, config) }
                .onSuccess { done(it, "B") }
                .onFailure { log("exchange failed: $it") }
        }
    }

    private fun sniffed(body: String) {
        log("sniffed login response (${body.length} chars)")
        runCatching {
            val response = Transport.json.parseToJsonElement(body).jsonObject["response"]!!
            auth.sessionFrom(response)
        }.onSuccess { done(it, "A") }.onFailure { log("sniff parse failed: $it") }
    }

    private fun done(session: Session, how: String) {
        File(filesDir, "session.json").writeText(Transport.json.encodeToString(Session.serializer(), session))
        log("[$how] signed in: ${session.name}, token exp ${session.expiresAt}, skew ${transport.clock.skew}")
        lifecycleScope.launch {
            runCatching { Portal(session, transport).attendanceMeta() }
                .onSuccess { log("[$how] attendance meta ok: ${it.semesters.size} semesters") }
                .onFailure { log("[$how] api call failed: $it") }
        }
    }

    companion object {
        // wraps xhr and fetch so the portal's own login response reaches us
        private val HOOK = """
            (function () {
              if (window.__jp) return; window.__jp = true;
              var hit = function (u) { return String(u).indexOf('generate-token-google-signin') >= 0; };
              var post = function (b) { try { jportal.postMessage(JSON.stringify({ type: 'login', body: b })); } catch (e) {} };
              var open = XMLHttpRequest.prototype.open, send = XMLHttpRequest.prototype.send;
              XMLHttpRequest.prototype.open = function (m, u) { this.__jpu = u; return open.apply(this, arguments); };
              XMLHttpRequest.prototype.send = function () {
                if (hit(this.__jpu)) { var x = this; x.addEventListener('load', function () { post(x.responseText); }); }
                return send.apply(this, arguments);
              };
              var f = window.fetch;
              if (f) window.fetch = function (u) {
                var p = f.apply(this, arguments);
                if (hit(u && u.url || u)) p.then(function (r) { r.clone().text().then(post); });
                return p;
              };
            })();
        """.trimIndent()
    }
}
