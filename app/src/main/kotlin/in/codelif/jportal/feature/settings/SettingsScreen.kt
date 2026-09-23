package `in`.codelif.jportal.feature.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import `in`.codelif.jportal.ui.components.AppMark
import `in`.codelif.jportal.ui.components.Group
import `in`.codelif.jportal.ui.theme.isDark
import `in`.codelif.jportal.ui.theme.jportalDark
import `in`.codelif.jportal.ui.theme.jportalLight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.R
import `in`.codelif.jportal.data.AppIcon
import `in`.codelif.jportal.data.Palette
import `in`.codelif.jportal.data.ThemeMode
import `in`.codelif.jportal.feature.attendance.TargetSheet
import `in`.codelif.jportal.feature.me.Entry
import `in`.codelif.jportal.ui.LocalNavigator
import `in`.codelif.jportal.ui.components.ScreenScaffold
import `in`.codelif.jportal.ui.components.SectionHeader

/** flips which launcher alias is enabled, the launcher picks up the new icon on its own */
fun applyIcon(context: Context, icon: AppIcon) {
    val pm = context.packageManager
    fun set(alias: String, on: Boolean) = pm.setComponentEnabledSetting(
        ComponentName(context, "in.codelif.jportal.$alias"),
        if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP,
    )
    // enable first so there's never a moment with no launcher entry
    if (icon == AppIcon.Classic) { set("LauncherClassic", true); set("Launcher", false) }
    else { set("Launcher", true); set("LauncherClassic", false) }
}

@Composable
fun SettingsScreen() {
    val graph = LocalGraph.current
    val prefs = graph.prefs
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val mode by prefs.themeModeState.collectAsState()
    val palette by prefs.paletteState.collectAsState()
    val amoled by prefs.amoledState.collectAsState()
    val target by prefs.targetState.collectAsState()
    val icon by prefs.iconState.collectAsState()
    var editTarget by remember { mutableStateOf(false) }
    var confirmOut by remember { mutableStateOf(false) }

    val dark = isDark(mode)

    ScreenScaffold("Settings", onBack = { nav.pop() }) {
        item("look-h") { SectionHeader("Look") }
        item("look") {
            Group {
                row { Choice("Theme", ThemeMode.entries, mode, { it.name }) { prefs.setThemeMode(it) } }
                row {
                    val dynamicOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    Tiles("Colours") {
                        PaletteTile(Palette.Wallpaper, (if (dynamicOk) palette else Palette.JPortal) == Palette.Wallpaper, dark, enabled = dynamicOk) { prefs.setPalette(it) }
                        PaletteTile(Palette.JPortal, !dynamicOk || palette == Palette.JPortal, dark, enabled = true) { prefs.setPalette(it) }
                    }
                }
                // pure black only means something once it's dark
                if (dark) row { Toggle("Pure black", "Fully black backgrounds", amoled) { prefs.setAmoled(it) } }
                row {
                    Tiles("App icon") {
                        AppIcon.entries.forEach { i ->
                            Tile(if (i == AppIcon.Classic) "YR Special" else "JeJe", icon == i, onClick = { prefs.setIcon(i); applyIcon(context, i) }) {
                                AppMark(56.dp, only = i)
                            }
                        }
                    }
                }
            }
        }
        item("att-h") { SectionHeader("Attendance") }
        item("att") { Group { row { Entry(R.drawable.ic_flag, "Attendance goal", "$target%") { editTarget = true } } } }
        item("acc-h") { SectionHeader("Account") }
        item("acc") {
            Group(Modifier.padding(bottom = 16.dp)) {
                row { Entry(R.drawable.ic_logout, "Sign out", "Clears the session and everything cached on this phone") { confirmOut = true } }
            }
        }
    }
    if (editTarget) TargetSheet(target) { prefs.setTarget(it); editTarget = false }
    if (confirmOut) {
        AlertDialog(
            onDismissRequest = { confirmOut = false },
            title = { Text("Sign out?") },
            text = { Text("Your cached attendance and marks go too.") },
            confirmButton = { TextButton(onClick = { confirmOut = false; graph.signOut() }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmOut = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun <T> Choice(label: String, options: List<T>, selected: T, name: (T) -> String, enabled: Boolean = true, onPick: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, o ->
                SegmentedButton(
                    selected = o == selected,
                    onClick = { onPick(o) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(name(o)) }
            }
        }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(onClick = { onChange(!checked) }, color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked, onChange)
        }
    }
}

@Composable
private fun Tiles(label: String, content: @Composable RowScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** a pickable square: a preview on top, a name under it, a ring when it's the one */
@Composable
private fun RowScope.Tile(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit, preview: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = if (selected) scheme.secondaryContainer else scheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(2.dp, scheme.primary) else null,
        modifier = Modifier.weight(1f).alpha(if (enabled) 1f else 0.4f),
    ) {
        Column(Modifier.padding(vertical = 14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            preview()
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** the palette's own colours as three overlapping dots, in the mode you're in */
@Composable
private fun RowScope.PaletteTile(p: Palette, selected: Boolean, dark: Boolean, enabled: Boolean, onPick: (Palette) -> Unit) {
    val context = LocalContext.current
    val scheme = when {
        p == Palette.JPortal -> if (dark) jportalDark else jportalLight
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> MaterialTheme.colorScheme
    }
    Tile(if (p == Palette.Wallpaper) "Wallpaper" else "JPortal", selected, enabled, onClick = { onPick(p) }) {
        Row(Modifier.height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(scheme.surfaceContainerHighest, scheme.primary, scheme.tertiary).forEachIndexed { i, c ->
                Box(
                    Modifier.offset(x = (-10 * i).dp).size(34.dp).clip(CircleShape).background(c)
                        .border(2.dp, if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                )
            }
        }
    }
}
