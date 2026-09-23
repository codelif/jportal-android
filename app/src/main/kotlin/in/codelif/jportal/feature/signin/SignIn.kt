package `in`.codelif.jportal.feature.signin

import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.R
import `in`.codelif.ktjiit.auth.Session
import `in`.codelif.ktjiit.http.Transport

/** base64url(session json), what "copy session code" produces and this accepts */
fun decodeSessionCode(code: String): Session? = runCatching {
    val json = String(Base64.decode(code.trim(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
    Transport.json.decodeFromString(Session.serializer(), json).takeIf { it.token.isNotBlank() && it.instituteId.isNotBlank() }
}.getOrNull()

@Composable
private fun SessionCodeForm(onDone: () -> Unit = {}) {
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
            onClick = {
                val s = decodeSessionCode(code)
                if (s == null) error = "That code doesn't look right" else { graph.sessions.signedIn(s); onDone() }
            },
            enabled = code.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continue") }
    }
}

@Composable
fun SignInScreen() {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            androidx.compose.foundation.Image(painterResource(R.drawable.ic_launcher_classic_foreground), null, Modifier.size(160.dp))
            Text("JPortal", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Your attendance, marks and grades, without the portal.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            SessionCodeForm()
            Spacer(Modifier.height(16.dp))
            Text(
                "Google sign-in lands here once the sign-in spike checks out.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** shown over cached data when the token died and silent re-auth couldn't fix it */
@Composable
fun SignInSheet() {
    val graph = LocalGraph.current
    ModalBottomSheet(onDismissRequest = { }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sign in again", style = MaterialTheme.typography.headlineSmall)
            Text(
                "The portal logs everyone out every two hours. Everything you see is still here, it just can't refresh until you're back in.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SessionCodeForm()
            TextButton(onClick = { graph.signOut() }, modifier = Modifier.align(Alignment.End)) { Text("Sign out instead") }
        }
    }
}

/** hidden re-auth, the google half arrives with the m1 result. until then it gives up straight away */
@Composable
fun ReauthHost() {
    val graph = LocalGraph.current
    LaunchedEffect(Unit) { graph.sessions.reauthFailed() }
}
