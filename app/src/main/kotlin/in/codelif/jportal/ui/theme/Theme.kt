package `in`.codelif.jportal.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.platform.LocalContext
import `in`.codelif.jportal.data.Palette
import `in`.codelif.jportal.data.ThemeMode

/** colors outside the material roles: jportal's sgpa green / cgpa blue, grade letters and attendance verdicts */
@Immutable
data class ExtraColors(
    val sgpa: Color,
    val cgpa: Color,
    val credits: Color,
    val good: Color,
    val onGood: Color,
    val goodContainer: Color,
    val bad: Color,
    val warn: Color,
    private val grades: Map<String, Color> = emptyMap(),
    private val ungraded: Color = Color.Unspecified,
    private val marks: List<Color> = emptyList(),
    private val marksInk: List<Color> = emptyList(),
) {
    /** jportal's grade colours: greens for a, yellows for b and c+, oranges below, red for the ones that hurt */
    fun grade(letter: String): Color = grades[letter.trim().uppercase()] ?: ungraded

    /** jportal's marks bands for a score out of 1: green from 80%, yellow from 60%, orange from 40%, red below */
    fun marks(fraction: Double): Color = marks.getOrElse(band(fraction)) { ungraded }

    /** [marks] for text, darker on light where the bar's yellow can't be read */
    fun marksInk(fraction: Double): Color = marksInk.getOrElse(band(fraction)) { ungraded }

    private fun band(f: Double) = when {
        f >= 0.8 -> 0
        f >= 0.6 -> 1
        f >= 0.4 -> 2
        else -> 3
    }
}

val LocalExtraColors = staticCompositionLocalOf {
    ExtraColors(Color(0xFF4ADE80), Color(0xFF60A5FA), Color(0xFF60A5FA), Color(0xFF2E7D32), Color.White, Color(0xFFC8E6C9), Color(0xFFC62828), Color(0xFFF9A825))
}

// tailwind 400/500/600 on dark like jportal, two steps darker on light so letters hold contrast on white
private val gradesDark = mapOf(
    "A+" to 0xFF4ADE80, "A" to 0xFF22C55E, "B+" to 0xFFFACC15, "B" to 0xFFEAB308, "C+" to 0xFFCA8A04,
    "C" to 0xFFF97316, "D" to 0xFFEA580C, "F" to 0xFFEF4444, "I" to 0xFFEF4444, "X" to 0xFFEF4444,
)
// jportal's marks-outstanding/good/average/poor
private val marksDark = listOf(0xFF00C950, 0xFFF0B000, 0xFFFF6A00, 0xFFEF4444)
private val marksLight = listOf(0xFF16A249, 0xFFF5C73D, 0xFFF58A3D, 0xFFEF4444)
private val marksLightInk = listOf(0xFF15803D, 0xFFA16207, 0xFFC2410C, 0xFFDC2626)
private val gradesLight = mapOf(
    "A+" to 0xFF16A34A, "A" to 0xFF15803D, "B+" to 0xFFCA8A04, "B" to 0xFFA16207, "C+" to 0xFF854D0E,
    "C" to 0xFFC2410C, "D" to 0xFF9A3412, "F" to 0xFFDC2626, "I" to 0xFFDC2626, "X" to 0xFFDC2626,
)

private fun extras(scheme: ColorScheme, dark: Boolean, exact: Boolean): ExtraColors {
    // the jportal palette gets jportal's hues as is, wallpaper themes get them pulled toward the scheme so they don't clash
    fun tune(c: Color) = if (exact) c else lerp(c, scheme.primary, 0.12f)
    val sgpa = if (dark) Color(0xFF4ADE80) else Color(0xFF16A34A)
    val cgpa = if (dark) Color(0xFF60A5FA) else Color(0xFF2563EB)
    return ExtraColors(
        sgpa = tune(sgpa),
        cgpa = tune(cgpa),
        credits = tune(cgpa),
        good = tune(if (dark) Color(0xFF4ADE80) else Color(0xFF15803D)),
        onGood = if (dark) Color(0xFF052E16) else Color.White,
        goodContainer = if (dark) Color(0xFF14532D) else Color(0xFFDCFCE7),
        bad = scheme.error,
        warn = tune(if (dark) Color(0xFFEAB308) else Color(0xFFA16207)),
        grades = (if (dark) gradesDark else gradesLight).mapValues { tune(Color(it.value)) },
        ungraded = scheme.onSurfaceVariant,
        marks = (if (dark) marksDark else marksLight).map { tune(Color(it)) },
        marksInk = (if (dark) marksDark else marksLightInk).map { tune(Color(it)) },
    )
}

private fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = lerp(Color.Black, surfaceContainerLow, 0.5f),
    surfaceContainer = lerp(Color.Black, surfaceContainer, 0.6f),
    surfaceContainerHigh = lerp(Color.Black, surfaceContainerHigh, 0.7f),
    surfaceContainerHighest = lerp(Color.Black, surfaceContainerHighest, 0.8f),
)

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun JPortalTheme(
    mode: ThemeMode = ThemeMode.System,
    palette: Palette = Palette.Wallpaper,
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isDark(mode)
    val context = LocalContext.current
    val dynamic = palette == Palette.Wallpaper && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = remember(dynamic, dark, amoled, context) {
        val base = when {
            dynamic && dark -> dynamicDarkColorScheme(context)
            dynamic -> dynamicLightColorScheme(context)
            dark -> jportalDark
            else -> jportalLight
        }
        if (dark && amoled) base.amoled() else base
    }
    // system bars and the window follow the app's theme, not the phone's. the window shows through
    // every crossfade, left on the xml colour a forced dark theme flashes white between pages
    val activity = LocalActivity.current
    val view = LocalView.current
    val window = scheme.surface.toArgb()
    SideEffect {
        activity?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            w.setBackgroundDrawable(ColorDrawable(window))
        }
    }
    val extra = remember(scheme, dark, dynamic) { extras(scheme, dark, exact = !dynamic) }
    CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(
            colorScheme = scheme,
            typography = JPortalTypography,
            shapes = JPortalShapes,
            content = content,
        )
    }
}
