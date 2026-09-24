package `in`.codelif.jportal.feature.me

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import `in`.codelif.jportal.data.Release
import `in`.codelif.jportal.debug.DebugLog
import `in`.codelif.jportal.ui.LocalNavigator
import `in`.codelif.jportal.ui.components.group
import `in`.codelif.jportal.ui.components.Ic
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
    val update by graph.updates.available.collectAsState()
    ScreenScaffold("About", onBack = { nav.pop() }) {
        item("head") {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                `in`.codelif.jportal.ui.components.AppMark(96.dp)
                Text("JPortal", style = MaterialTheme.typography.headlineMedium)
                Text("${BuildConfig.VERSION_NAME} · ${BuildConfig.FLAVOR}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Unofficial. Not made, endorsed or checked by JIIT.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        update?.let { r -> item("update") { UpdateCard(r) { open(context, r.url) } } }
        item("people-h") { SectionHeader("Made possible by") }
        group("people") {
            row { Entry(R.drawable.ic_favorite, "JPortal", "Big 🍆 Energy lives on in Yash Malik") { open(context, "https://github.com/codeblech/jportal") } }
            row { Entry(R.drawable.ic_code, "jsjiit", "ISC, portal protocol groundwork") { open(context, "https://github.com/codeblech/jsjiit") } }
            row { Entry(R.drawable.ic_code, "pyjiit", "MIT, where it all started") { open(context, "https://github.com/codelif/pyjiit") } }
            row { Entry(R.drawable.ic_open_in_new, "Source code", "GPL-3.0, github.com/codelif/jportal-android") { open(context, "https://github.com/codelif/jportal-android") } }
        }
        item("lic-h") { SectionHeader("Licenses") }
        group("lic") {
            row { InfoRow("JPortal for Android, ktjiit", "GNU General Public License v3.0") }
            row { InfoRow("Google Sans Flex", "SIL Open Font License 1.1") }
            row { InfoRow("Material Symbols", "Apache License 2.0") }
            row { InfoRow("AndroidX, Jetpack Compose", "Apache License 2.0") }
            row { InfoRow("Kotlin, kotlinx.serialization, kotlinx.coroutines", "Apache License 2.0") }
        }
        item("debug-h") { SectionHeader("Something broken?") }
        group("debug", bottom = 16.dp) {
            row {
                Entry(R.drawable.ic_content_copy, "Copy debug report", "App, device and recent errors. No tokens, names or ids.") {
                    val report = DebugLog.report(context, mapOf("clock skew" to graph.transport.clock.skew.toString()))
                    scope.launch { clip.setClipEntry(android.content.ClipData.newPlainText("JPortal debug report", report).toClipEntry()) }
                    Toast.makeText(context, "Copied, paste it into a GitHub issue", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
private fun UpdateCard(r: Release, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Ic(R.drawable.ic_system_update, null, Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Update to v${r.version}", style = MaterialTheme.typography.titleMedium)
                Text("You have v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
            }
            Ic(R.drawable.ic_open_in_new, null, Modifier.size(20.dp))
        }
    }
}
