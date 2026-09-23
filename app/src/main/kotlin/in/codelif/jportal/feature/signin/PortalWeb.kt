package `in`.codelif.jportal.feature.signin

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import `in`.codelif.ktjiit.http.Transport
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** what the sign-in pages tell kotlin */
sealed interface WebSignal {
    data class Credential(val token: String, val auto: Boolean) : WebSignal
    data class LoginResponse(val body: String) : WebSignal
    data object Ready : WebSignal
    data class NoAuto(val why: String) : WebSignal
    data class Failed(val why: String) : WebSignal
}

/**
 * google refuses to sign in inside anything it recognises as a webview, and
 * the portal's client id only trusts the portal's origin. so: look like
 * chrome, and serve our own tiny page from the portal's origin.
 * everything here was proven on a real device in the m1 spike.
 */
object PortalWeb {
    const val ORIGIN = "https://webportal.jiit.ac.in:6011"
    private const val SIGNIN_PATH = "/studentportal/jportal-signin"
    const val PORTAL_URL = "$ORIGIN/studentportal/"

    fun signinUrl(clientId: String, silent: Boolean, dark: Boolean, widthPx: Int) =
        "$ORIGIN$SIGNIN_PATH?cid=${Uri.encode(clientId)}&mode=${if (silent) "silent" else "button"}" +
            "&theme=${if (dark) "dark" else "light"}&w=$widthPx"

    // wraps xhr and fetch so the portal's own login response reaches us (approach a)
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

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(web: WebView) = web.apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
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
        setBackgroundColor(Color.TRANSPARENT)
    }

    /**
     * a webview wired for sign-in. [onPopup] gets google's popup window (or
     * null when it closes), the caller decides where to show it.
     */
    fun create(context: Context, onSignal: (WebSignal) -> Unit, onPopup: (WebView?) -> Unit): WebView {
        val web = configure(WebView(context))
        val client = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val u = request.url
                if ("${u.scheme}://${u.authority}" == ORIGIN && u.path == SIGNIN_PATH) {
                    return WebResourceResponse("text/html", "utf-8", view.context.assets.open("signin.html"))
                }
                return null
            }
        }
        val chrome = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                val popup = configure(WebView(view.context)).apply {
                    setBackgroundColor(Color.WHITE)
                    webViewClient = WebViewClient()
                    webChromeClient = object : WebChromeClient() {
                        override fun onCloseWindow(window: WebView) {
                            onPopup(null)
                        }
                    }
                }
                (resultMsg.obj as WebView.WebViewTransport).webView = popup
                resultMsg.sendToTarget()
                onPopup(popup)
                return true
            }

            override fun onCloseWindow(window: WebView) = onPopup(null)
        }
        web.webViewClient = client
        web.webChromeClient = chrome

        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(web, "jportal", setOf(ORIGIN)) { _, message, _, _, _ ->
                val obj = runCatching { Transport.json.parseToJsonElement(message.data.orEmpty()).jsonObject }.getOrNull()
                    ?: return@addWebMessageListener
                fun s(k: String) = obj[k]?.jsonPrimitive?.contentOrNull.orEmpty()
                when (s("type")) {
                    "credential" -> onSignal(WebSignal.Credential(s("credential"), s("by").startsWith("auto")))
                    "login" -> onSignal(WebSignal.LoginResponse(s("body")))
                    "ready" -> onSignal(WebSignal.Ready)
                    "no_auto" -> onSignal(WebSignal.NoAuto(s("why")))
                    "fail" -> onSignal(WebSignal.Failed(s("why")))
                }
            }
        } else {
            onSignal(WebSignal.Failed("webview_too_old"))
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, HOOK, setOf(ORIGIN))
        }
        return web
    }

    /** google's session cookie is what makes silent re-auth possible later, don't lose it */
    fun persistCookies() = CookieManager.getInstance().flush()
}
