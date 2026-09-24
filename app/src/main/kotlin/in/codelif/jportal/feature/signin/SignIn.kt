package `in`.codelif.jportal.feature.signin

import android.util.Base64
import android.webkit.WebView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.R
import `in`.codelif.jportal.debug.DebugLog
import `in`.codelif.jportal.ui.components.Ic
import `in`.codelif.jportal.ui.components.Loading
import `in`.codelif.jportal.ui.components.describe
import `in`.codelif.jportal.ui.theme.isDark
import `in`.codelif.ktjiit.auth.Session
import `in`.codelif.ktjiit.http.Transport
import kotlinx.coroutines.launch

/** base64url(session json), the last-resort way in */
fun decodeSessionCode(code: String): Session? = runCatching {
    val json = String(Base64.decode(code.trim(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
    Transport.json.decodeFromString(Session.serializer(), json).takeIf { it.token.isNotBlank() && it.instituteId.isNotBlank() }
}.getOrNull()

/** turns what the web page gave us into a signed-in session, or an error message */
@Composable
private fun rememberSignalHandler(onError: (String) -> Unit, onBusy: (Boolean) -> Unit): (WebSignal) -> Unit {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    return remember {
        { signal ->
            when (signal) {
                is WebSignal.Credential -> scope.launch {
                    onBusy(true)
                    runCatching { graph.sessions.auth.exchangeGoogleToken(signal.token, graph.sessions.config) }
                        .onSuccess { PortalWeb.persistCookies(); graph.sessions.signedIn(it) }
                        .onFailure { DebugLog.record("signin", it); onError(describe(it)); onBusy(false) }
                }
                is WebSignal.LoginResponse -> runCatching {
                    val response = Transport.json.parseToJsonElement(signal.body).let { it as kotlinx.serialization.json.JsonObject }["response"]!!
                    graph.sessions.auth.sessionFrom(response)
                }.onSuccess { PortalWeb.persistCookies(); graph.sessions.signedIn(it) }
                    .onFailure { DebugLog.record("signin-a", it); onError("The portal signed you in, but JPortal couldn't read it") }
                is WebSignal.Failed -> onError(
                    if (signal.why == "webview_too_old") "Update Android System WebView from the Play Store, then try again"
                    else "Google sign-in didn't load, check your connection",
                )
                else -> {}
            }
        }
    }
}

/** full screen host for google's sign-in popup */
@Composable
private fun PopupHost(popup: WebView?, onDismiss: () -> Unit) {
    if (popup == null) return
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                IconButton(onClick = onDismiss, modifier = Modifier.padding(4.dp)) { Ic(R.drawable.ic_close, "Cancel") }
                AndroidView({ popup }, Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * google's own button, rendered by google inside a small transparent webview
 * so it sits in our layout like any other control.
 */
@Composable
fun GoogleButton(modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val dark = isDark(graph.prefs.themeModeState.value)
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var popup by remember { mutableStateOf<WebView?>(null) }
    var attempt by remember { mutableStateOf(0) }
    // null while loading, then the config or a failure
    val config by produceState<Result<`in`.codelif.ktjiit.auth.PortalConfig>?>(graph.sessions.config?.let { Result.success(it) }, attempt) {
        value = graph.sessions.loadConfig()?.let { Result.success(it) } ?: Result.failure(IllegalStateException("config"))
    }
    val onSignal = rememberSignalHandler({ error = it }, { busy = it })

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
            val cid = config?.getOrNull()?.googleClientId
            val widthPx = maxWidth.coerceAtMost(400.dp).value.toInt()
            when {
                busy || config == null -> Loading(size = 40.dp)
                cid.isNullOrEmpty() -> TextButton(onClick = { attempt++ }) { Text("Can't reach the portal, retry") }
                else -> {
                    if (!ready) Loading(size = 40.dp)
                    AndroidView(
                        factory = { ctx ->
                            PortalWeb.create(ctx, onSignal = { s -> if (s is WebSignal.Ready) ready = true else onSignal(s) }, onPopup = { popup = it })
                                .apply { loadUrl(PortalWeb.signinUrl(cid, silent = false, dark = dark, widthPx = widthPx)) }
                        },
                        onRelease = { it.destroy() },
                        modifier = Modifier.fillMaxSize().alpha(if (ready) 1f else 0f),
                    )
                }
            }
        }
        AnimatedVisibility(error != null) {
            Text(
                error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    PopupHost(popup) { popup?.destroy(); popup = null }
}

/** approach a: the real portal login page, we just read its answer. for when google blocks our own page */
@Composable
private fun PortalPageSignIn(onClose: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    var popup by remember { mutableStateOf<WebView?>(null) }
    val onSignal = rememberSignalHandler({ error = it }, {})
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Ic(R.drawable.ic_close, "Close") }
                    Text("Sign in on the portal", style = MaterialTheme.typography.titleMedium)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
                AndroidView(
                    factory = { ctx -> PortalWeb.create(ctx, onSignal, onPopup = { popup = it }).apply { loadUrl(PortalWeb.PORTAL_URL) } },
                    onRelease = { it.destroy() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
    PopupHost(popup) { popup?.destroy(); popup = null }
}

@Composable
private fun SessionCodeForm() {
    val graph = LocalGraph.current
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = code,
            onValueChange = { code = it; error = null },
            label = { Text("Session code") },
            isError = error != null,
            supportingText = { Text(error ?: "From another device running JPortal") },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { decodeSessionCode(code)?.let { graph.sessions.signedIn(it) } ?: run { error = "That code doesn't look right" } },
            enabled = code.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continue") }
    }
}

/** the other ways in, tucked under the google button */
@Composable
private fun OtherWays() {
    var portal by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = { portal = true }) { Text("Having trouble? Use the portal page") }
        AnimatedContent(code, label = "code") { open ->
            if (open) SessionCodeForm() else TextButton(onClick = { code = true }) { Text("I have a session code") }
        }
    }
    if (portal) PortalPageSignIn { portal = false }
}

@Composable
fun SignInScreen() {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(56.dp))
                `in`.codelif.jportal.ui.components.AppMark(144.dp)
                Spacer(Modifier.height(20.dp))
                Text("JPortal", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Your attendance, marks and grades, without the portal.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(48.dp))
                GoogleButton()
                Spacer(Modifier.height(8.dp))
                Text(
                    "Use your JIIT Google account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(24.dp))
                OtherWays()
            }
            TextButton(
                onClick = { scope.launch { graph.startDemo() } },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
            ) { Text("Demo") }
        }
    }
}

/** shown over cached data when the token died and silent re-auth couldn't fix it */
@Composable
fun SignInSheet() {
    val graph = LocalGraph.current
    ModalBottomSheet(onDismissRequest = {}) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Sign in again", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Your portal session ran out.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            GoogleButton()
            OtherWays()
            TextButton(onClick = { graph.signOut() }) { Text("Sign out instead") }
        }
    }
}

/**
 * silent re-auth: an invisible webview running one tap with auto_select.
 * google hands back a credential without any ui when it still has a session.
 */
@Composable
fun ReauthHost() {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val config by produceState(graph.sessions.config) { value = graph.sessions.loadConfig() }
    val cid = config?.googleClientId
    LaunchedEffect(config) { if (config != null && cid.isNullOrEmpty()) graph.sessions.reauthFailed() }
    if (cid.isNullOrEmpty()) return
    var done by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { if (!done) graph.sessions.reauthFailed() } }
    // needs real size to render the one tap iframe, so it's full size but invisible and untouchable
    Box(Modifier.fillMaxSize().alpha(0f)) {
        AndroidView(
            factory = { ctx ->
                PortalWeb.create(
                    ctx,
                    onSignal = { s ->
                        when (s) {
                            is WebSignal.Credential -> scope.launch {
                                runCatching { graph.sessions.auth.exchangeGoogleToken(s.token, config) }
                                    .onSuccess { done = true; PortalWeb.persistCookies(); graph.sessions.signedIn(it) }
                                    .onFailure { DebugLog.record("reauth", it); done = true; graph.sessions.reauthFailed() }
                            }
                            is WebSignal.NoAuto, is WebSignal.Failed -> { done = true; graph.sessions.reauthFailed() }
                            else -> {}
                        }
                    },
                    // a popup means google wants the user, that's the sheet's job
                    onPopup = { p -> p?.destroy(); if (p != null) { done = true; graph.sessions.reauthFailed() } },
                ).apply {
                    isFocusable = false
                    // invisible, so talkback mustn't land on it either
                    importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    loadUrl(PortalWeb.signinUrl(cid, silent = true, dark = false, widthPx = 320))
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
