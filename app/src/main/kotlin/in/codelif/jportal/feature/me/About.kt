package `in`.codelif.jportal.feature.me

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.BuildConfig
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.R
import `in`.codelif.jportal.debug.DebugLog
import `in`.codelif.jportal.ui.LocalNavigator
import `in`.codelif.jportal.ui.components.ScreenScaffold
import `in`.codelif.jportal.ui.components.SectionHeader

private fun open(context: android.content.Context, url: String) =
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

@Composable
fun AboutScreen() {
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val graph = LocalGraph.current
    val clip = LocalClipboard.current
    val scope = rememberCoroutineScope()
    ScreenScaffold("About", onBack = { nav.pop() }) {
        item("head") {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.foundation.Image(painterResource(R.drawable.ic_launcher_classic_foreground), null, Modifier.size(120.dp))
                Text("JPortal", style = MaterialTheme.typography.headlineMedium)
                Text("${BuildConfig.VERSION_NAME} · ${BuildConfig.FLAVOR}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Unofficial. Not made, endorsed or checked by JIIT. Your token never leaves this phone except to talk to the portal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        item("people-h") { SectionHeader("Made possible by") }
        item("jportal") { Entry(R.drawable.ic_favorite, "JPortal by codeblech", "The original web app, its design and the hand-drawn icon") { open(context, "https://github.com/codeblech/jportal") } }
        item("jsjiit") { Entry(R.drawable.ic_code, "jsjiit", "ISC, portal protocol groundwork") { open(context, "https://github.com/codeblech/jsjiit") } }
        item("pyjiit") { Entry(R.drawable.ic_code, "pyjiit", "MIT, where it all started") { open(context, "https://github.com/codelif/pyjiit") } }
        item("src") { Entry(R.drawable.ic_open_in_new, "Source code", "GPL-3.0, github.com/codelif/jportal-android") { open(context, "https://github.com/codelif/jportal-android") } }
        item("lic-h") { SectionHeader("Licenses") }
        item("lic") {
            Column {
                InfoRow("JPortal for Android, ktjiit", "GNU General Public License v3.0")
                InfoRow("Google Sans Flex", "SIL Open Font License 1.1")
                InfoRow("Material Symbols", "Apache License 2.0")
                InfoRow("AndroidX, Jetpack Compose", "Apache License 2.0")
                InfoRow("Kotlin, kotlinx.serialization, kotlinx.coroutines", "Apache License 2.0")
            }
        }
        item("debug-h") { SectionHeader("Something broken?") }
        item("debug") {
            Entry(R.drawable.ic_content_copy, "Copy debug report", "App, device and recent errors. No tokens, names or ids.") {
                val report = DebugLog.report(context, mapOf("clock skew" to graph.transport.clock.skew.toString()))
                scope.launch { clip.setClipEntry(android.content.ClipData.newPlainText("JPortal debug report", report).toClipEntry()) }
                Toast.makeText(context, "Copied, paste it into a GitHub issue", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
